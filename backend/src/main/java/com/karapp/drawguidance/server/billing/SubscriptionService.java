package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.billing.apple.AppStoreApi;
import com.karapp.drawguidance.server.billing.apple.AppStoreApiException;
import com.karapp.drawguidance.server.billing.apple.AppleStates;
import com.karapp.drawguidance.server.billing.play.GooglePlayApi;
import com.karapp.drawguidance.server.billing.play.PlayApiException;
import com.karapp.drawguidance.server.billing.play.PlaySubscription;
import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v39 abonelik kaydı: kullanıcı başına tek güncel belge ({@code subscriptions}, {@code _id} = Firebase uid) ve
 * yalnız eklenen hareket geçmişi ({@code subscription_transactions}).
 *
 * <p><b>Doğrulama</b> (uygulama satın alınca ve oturum açınca değişen bir satın alma görünce): durum her zaman Google
 * Play'den okunur ({@code subscriptionsv2.get}); uygulamanın söylediğine güvenilmez. Play kaydındaki
 * {@code obfuscatedExternalAccountId} kullanıcının {@link #accountHash} değeriyle aynı olmalı (yoksa ve
 * {@code require-account-id} kapalıysa jeton ilk doğrulayan hesaba bağlanır; başka hesaba bağlı jeton 409).
 * Onaylanmamış satın alma onaylanır (3 gün içinde onaylanmayan satın almayı Play iade eder).
 *
 * <p><b>Güncel belgenin değişmesi:</b> aynı jeton, eski jetonu {@code linkedPurchaseToken} ile devralan yeni jeton,
 * hak veren yeni jeton ya da güncel belge hak vermiyorken daha yeni başlayan jeton güncel belgeyi değiştirir. Eski bir
 * jetonun bildirimi (ör. yükseltmeden sonra eski jetonun EXPIRED'ı) yalnız harekete yazılır. Eşzamanlı yazmalarda
 * Play'den daha önce okunmuş durum daha yeni okunmuşun üstüne yazılmaz ({@code fetchedAt}).
 */
@Service
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    /** Ek süredeki aboneliğin bildirim gelmese de hakkı en çok bitiş + 30 gün sürer (Play'in en uzun ek süresi). */
    static final Duration GRACE_LIMIT = Duration.ofDays(30);
    public static final String SOURCE_VERIFY = "VERIFY", SOURCE_RTDN = "RTDN";

    private final MongoTemplate mongo;
    private final PlanService plans;
    private final GooglePlayApi play;
    /** v50 App Store (yalnız {@code /me} tazelemesi için; doğrulama {@link AppleSubscriptionService}'te). */
    private final AppStoreApi apple;
    private final DrawGuidanceProperties.Billing billing;

    public SubscriptionService(MongoTemplate mongo, PlanService plans, GooglePlayApi play, AppStoreApi apple, DrawGuidanceProperties props) {
        this.mongo = mongo;
        this.plans = plans;
        this.play = play;
        this.apple = apple;
        this.billing = props.billing();
    }

    /** Uygulamanın {@code obfuscatedAccountId} değeri: Firebase uid'sinin SHA-256 özeti (küçük harfli onaltılık). */
    public static String accountHash(String uid) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private MongoCollection<Document> subs() { return mongo.getCollection(Stores.SUBSCRIPTIONS); }

    private MongoCollection<Document> txs() { return mongo.getCollection(Stores.SUBSCRIPTION_TRANSACTIONS); }

    // ---------------------------------------------------------------- okuma

    public SubscriptionView me(String uid) {
        Document s = subs().find(Filters.eq("_id", uid)).first();
        Document fresh = refreshIfStale(uid, s, Instant.now());
        return view(uid, fresh, Instant.now());
    }

    /** v50 Play'den okunmuş durum bu süreden eskiyse {@code /me} yeniden okur (kaçan iade/iptal bildirimine karşı). */
    static final Duration REFRESH_AFTER = Duration.ofHours(12);

    /**
     * v50 hak veren (ya da yakın zamanda hak vermiş) aboneliğin Play kaydı {@link #REFRESH_AFTER} süresinden eskiyse
     * Google Play'den yeniden okunur ve uygulanır. Play'e ulaşılamazsa kayıt olduğu gibi döner (en iyi çaba).
     */
    Document refreshIfStale(String uid, Document s, Instant now) {
        if (s == null) return s;
        String token = s.getString("purchaseToken");
        if (token == null) return s;
        Instant fetched = instant(s.get("fetchedAt"));
        if (fetched != null && fetched.plus(REFRESH_AFTER).isAfter(now)) return s;
        Instant expiry = instant(s.get("expiryTime"));
        boolean relevant = status(s).grantsAccess() || (expiry != null && expiry.plus(GRACE_LIMIT).isAfter(now));
        if (!relevant) return s;
        // v50 App Store aboneliği Play'e değil App Store Server API'ye sorulur
        if (AppleStates.isAppleToken(token)) return refreshApple(uid, s, token, now);
        if (!play.configured()) return s;
        try {
            PlaySubscription state = play.getSubscription(billing.packageName(), token);
            if (state.obfuscatedAccountId() != null && !state.obfuscatedAccountId().equals(accountHash(uid))) {
                log.warn("Yenilenen satın alma kayıtlı kullanıcıya ait değil; uygulanmadı");
                return s;
            }
            Document plan = plans.planForProduct(state.productId(), state.basePlanId());
            if (plan == null) return s;
            apply(uid, token, state, plan.getString("_id"), null, SOURCE_VERIFY, null, null, now);
            Document updated = subs().find(Filters.eq("_id", uid)).first();
            return updated == null ? s : updated;
        } catch (PlayApiException e) {
            if (e.invalidToken()) {
                // Play jetonu artık tanımıyor (ör. iade sonrası silinmiş): hak kapatılır
                subs().updateOne(Filters.eq("_id", uid), Updates.combine(Updates.set("status", SubscriptionStatus.EXPIRED.name()),
                        Updates.set("fetchedAt", Date.from(now)), Updates.set("updatedAt", Date.from(now))));
                Document updated = subs().find(Filters.eq("_id", uid)).first();
                return updated == null ? s : updated;
            }
            log.debug("Abonelik yenilenemedi: {}", e.getMessage());
            return s;
        } catch (RuntimeException e) {
            log.debug("Abonelik yenilenemedi: {}", e.getMessage());
            return s;
        }
    }

    /** v50 {@link #refreshIfStale}'in App Store karşılığı (en iyi çaba; hata olursa kayıt olduğu gibi döner). */
    private Document refreshApple(String uid, Document s, String token, Instant now) {
        if (!apple.configured()) return s;
        try {
            AppStoreApi.AppleStatus st = apple.subscriptionStatus(AppleStates.originalTransactionId(token), Boolean.TRUE.equals(s.getBoolean("testPurchase")));
            java.util.UUID account = st.transaction().appAccountToken();
            if (account != null && !account.equals(AppleStates.appAccountToken(uid))) {
                log.warn("Yenilenen App Store aboneliği kayıtlı kullanıcıya ait değil; uygulanmadı");
                return s;
            }
            Document plan = plans.planForAppleProduct(st.transaction().productId());
            String planId = plan != null ? plan.getString("_id") : s.getString("planId");
            apply(uid, token, AppleStates.toState(st, null, now), planId, null, SOURCE_VERIFY, null, null, now);
            Document updated = subs().find(Filters.eq("_id", uid)).first();
            return updated == null ? s : updated;
        } catch (AppStoreApiException e) {
            if (e.invalid()) {
                // App Store aboneliği artık tanımıyor: hak kapatılır
                subs().updateOne(Filters.eq("_id", uid), Updates.combine(Updates.set("status", SubscriptionStatus.EXPIRED.name()),
                        Updates.set("fetchedAt", Date.from(now)), Updates.set("updatedAt", Date.from(now))));
                Document updated = subs().find(Filters.eq("_id", uid)).first();
                return updated == null ? s : updated;
            }
            log.debug("App Store aboneliği yenilenemedi: {}", e.getMessage());
            return s;
        } catch (RuntimeException e) {
            log.debug("App Store aboneliği yenilenemedi: {}", e.getMessage());
            return s;
        }
    }

    /** Hareket geçmişi (yeniden eskiye; ham Play anlık görüntüsü olmadan). */
    public List<Map<String, Object>> history(String uid, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Document d : txs().find(Filters.eq("userId", uid)).sort(Sorts.orderBy(Sorts.descending("at"), Sorts.descending("_id")))
                .limit(Math.max(1, Math.min(limit, 200)))) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", d.get("_id"));
            m.put("type", d.get("type"));
            m.put("status", d.get("status"));
            m.put("orderId", d.get("orderId"));
            m.put("planId", d.get("planId"));
            m.put("productId", d.get("productId"));
            m.put("basePlanId", d.get("basePlanId"));
            m.put("expiryTime", instant(d.get("expiryTime")));
            m.put("source", d.get("source"));
            m.put("at", instant(d.get("at")));
            out.add(m);
        }
        return out;
    }

    SubscriptionView view(String uid, Document s, Instant now) {
        String hash = accountHash(uid);
        if (s == null) {
            PlanService.Features f = plans.features(Plans.FREE);
            return new SubscriptionView(Plans.FREE, null, false, null, null, null, null, null, null, null, f.keys(), f.limits(), hash, null,
                    null, null, null, null, AppleStates.appAccountToken(uid).toString());
        }
        SubscriptionStatus status = status(s);
        Instant expiry = instant(s.get("expiryTime"));
        boolean active = entitled(status, expiry, now);
        SubscriptionStatus shown = status;
        if (!active && status.grantsAccess() && status != SubscriptionStatus.IN_GRACE_PERIOD) shown = SubscriptionStatus.EXPIRED;
        String planId = active ? s.getString("planId") : Plans.FREE;
        PlanService.Features f = plans.features(planId);
        return new SubscriptionView(planId, shown.name(), active, s.getString("productId"), s.getString("basePlanId"),
                instant(s.get("startTime")), expiry, s.getBoolean("autoRenewing"), s.getBoolean("acknowledged"), s.getString("orderId"),
                f.keys(), f.limits(), hash, instant(s.get("updatedAt")),
                instant(s.get("cancelTime")), s.getString("cancelReason"), instant(s.get("autoResumeTime")),
                AppleStates.storeOf(s.getString("purchaseToken")), AppleStates.appAccountToken(uid).toString());
    }

    static boolean entitled(SubscriptionStatus status, Instant expiry, Instant now) {
        if (!status.grantsAccess()) return false;
        if (expiry == null) return status != SubscriptionStatus.CANCELED;
        if (status == SubscriptionStatus.IN_GRACE_PERIOD) return expiry.plus(GRACE_LIMIT).isAfter(now);
        return expiry.isAfter(now);
    }

    private static SubscriptionStatus status(Document s) {
        try {
            return SubscriptionStatus.valueOf(s.getString("status"));
        } catch (RuntimeException e) {
            return SubscriptionStatus.PENDING;
        }
    }

    // ---------------------------------------------------------------- doğrulama

    public SubscriptionView verify(String uid, VerifyRequest req) {
        if (!play.configured()) throw notConfigured();
        if (!billing.packageName().equals(req.packageName().trim())) {
            throw ApiException.badRequest("package_mismatch", "packageName bu sunucunun uygulaması değil: " + req.packageName());
        }
        String token = req.purchaseToken().trim();
        PlaySubscription state;
        try {
            state = play.getSubscription(billing.packageName(), token);
        } catch (PlayApiException e) {
            if (e.invalidToken()) throw ApiException.badRequest("invalid_purchase_token", "Google Play bu satın alma jetonunu tanımıyor");
            log.warn("Play doğrulaması yapılamadı: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "play_unavailable", "Google Play'e şu an ulaşılamıyor; biraz sonra yeniden deneyin");
        }
        if (state.productId() != null && !state.productId().equals(req.productId().trim())) {
            throw ApiException.badRequest("product_mismatch", "Satın alma başka bir ürüne ait: " + state.productId());
        }
        Document plan = plans.planForProduct(state.productId(), state.basePlanId());
        if (plan == null) throw ApiException.badRequest("unknown_product", "Bu ürün bir plana bağlı değil: " + state.productId());
        String hash = accountHash(uid);
        if (state.obfuscatedAccountId() != null ? !state.obfuscatedAccountId().equals(hash) : billing.requireAccountId()) {
            throw ApiException.forbidden("account_mismatch", "Bu satın alma başka bir hesaba ait");
        }
        if (subs().find(Filters.and(Filters.eq("purchaseToken", token), Filters.ne("_id", uid))).first() != null) {
            throw ApiException.conflict("purchase_token_in_use", "Bu satın alma başka bir hesaba bağlı");
        }
        state = acknowledgeIfNeeded(token, state);
        apply(uid, token, state, plan.getString("_id"), null, SOURCE_VERIFY, null, null, Instant.now());
        // Uygulamadan önce gelen bildirimlerin (kullanıcısı bilinmeyen) hareketleri bu kullanıcıya bağlanır
        txs().updateMany(Filters.and(Filters.eq("purchaseToken", token), Filters.eq("userId", null)), Updates.set("userId", uid));
        return me(uid);
    }

    static ApiException notConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "billing_not_configured",
                "Abonelik doğrulaması sunucuda yapılandırılmamış (Google Play hizmet hesabı yok)");
    }

    private PlaySubscription acknowledgeIfNeeded(String token, PlaySubscription state) {
        if (!state.needsAcknowledge() || state.productId() == null) return state;
        try {
            play.acknowledge(billing.packageName(), state.productId(), token);
            return state.acknowledgedCopy();
        } catch (PlayApiException e) {
            // Bir sonraki doğrulamada yeniden denenir (acknowledged=false kalır)
            log.warn("Satın alma onaylanamadı: {}", e.getMessage());
            return state;
        }
    }

    // ---------------------------------------------------------------- bildirim

    /** Bildirimin sonucu. */
    public enum NotificationResult { APPLIED, UNKNOWN_USER, ACCOUNT_MISMATCH, UNKNOWN_PRODUCT }

    /**
     * Gerçek zamanlı bildirim: Play'den okunmuş durumu jetonun kullanıcısına uygular. Kullanıcı jetondan (güncel belge,
     * eski jeton = {@code linkedPurchaseToken}, önceki hareketler) bulunur; bulunamazsa hareket kullanıcısız yazılır,
     * uygulama doğrulayınca bağlanır.
     */
    public NotificationResult applyNotification(String token, PlaySubscription state, TransactionType type, String messageId,
                                                Integer notificationType, Instant eventTime, String orderOverride) {
        String uid = ownerOf(token, state.linkedPurchaseToken());
        Document plan = plans.planForProduct(state.productId(), state.basePlanId());
        String planId = plan == null ? null : plan.getString("_id");
        if (uid == null) {
            appendTx(null, token, state, planId, type, SOURCE_RTDN, messageId, notificationType, eventTime, orderOverride);
            return NotificationResult.UNKNOWN_USER;
        }
        if (state.obfuscatedAccountId() != null && !state.obfuscatedAccountId().equals(accountHash(uid))) {
            log.warn("Bildirimdeki satın alma kayıtlı kullanıcıya ait değil; uygulanmadı");
            return NotificationResult.ACCOUNT_MISMATCH;
        }
        if (planId == null) return NotificationResult.UNKNOWN_PRODUCT;
        state = acknowledgeIfNeeded(token, state);
        apply(uid, token, state, planId, type, SOURCE_RTDN, messageId, notificationType, eventTime, orderOverride);
        return NotificationResult.APPLIED;
    }

    String ownerOf(String token, String linked) {
        Document d = subs().find(Filters.eq("purchaseToken", token)).first();
        if (d == null && linked != null) d = subs().find(Filters.eq("purchaseToken", linked)).first();
        if (d != null) return d.getString("_id");
        Document tx = txs().find(Filters.and(Filters.eq("purchaseToken", token), Filters.ne("userId", null))).first();
        return tx == null ? null : tx.getString("userId");
    }

    // ---------------------------------------------------------------- yazma

    /** Durumu uygular ve hareketi ekler ({@code type} {@code null} ise farktan çıkarılır; fark yoksa hareket yazılmaz). */
    void apply(String uid, String token, PlaySubscription state, String planId, TransactionType type, String source,
               String messageId, Integer notificationType, Instant eventTime) {
        apply(uid, token, state, planId, type, source, messageId, notificationType, eventTime, null);
    }

    void apply(String uid, String token, PlaySubscription state, String planId, TransactionType type, String source,
               String messageId, Integer notificationType, Instant eventTime, String orderOverride) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Document existing = subs().find(Filters.eq("_id", uid)).first();
        boolean sameToken = existing != null && token.equals(existing.getString("purchaseToken"));
        if (type == null) {
            type = sameToken
                    ? TransactionType.derive(status(existing), existing.getString("orderId"), state.status(), state.latestOrderId())
                    : TransactionType.PURCHASE;
        }
        if (replaces(existing, sameToken, state, now)) write(uid, token, state, planId, source, now);
        if (type != null) appendTx(uid, token, state, planId, type, source, messageId, notificationType, eventTime, orderOverride);
    }

    private static boolean replaces(Document existing, boolean sameToken, PlaySubscription state, Instant now) {
        if (existing == null || sameToken) return true;
        if (state.linkedPurchaseToken() != null && state.linkedPurchaseToken().equals(existing.getString("purchaseToken"))) return true;
        if (entitled(state.status(), state.expiryTime(), now)) return true;
        if (entitled(status(existing), instant(existing.get("expiryTime")), now)) return false;
        Instant oldStart = instant(existing.get("startTime"));
        return oldStart == null || state.startTime() == null || !state.startTime().isBefore(oldStart);
    }

    private void write(String uid, String token, PlaySubscription s, String planId, String source, Instant now) {
        Date fetched = Date.from(now);
        Bson filter = Filters.and(Filters.eq("_id", uid),
                Filters.or(Filters.exists("fetchedAt", false), Filters.lte("fetchedAt", fetched)));
        Bson update = Updates.combine(
                Updates.set("planId", planId),
                Updates.set("status", s.status().name()),
                Updates.set("playState", s.state()),
                Updates.set("productId", s.productId()),
                Updates.set("basePlanId", s.basePlanId()),
                Updates.set("offerId", s.offerId()),
                Updates.set("purchaseToken", token),
                // v50 aboneliğin mağazası: GOOGLE_PLAY ya da APP_STORE
                Updates.set("store", AppleStates.storeOf(token)),
                Updates.set("linkedPurchaseToken", s.linkedPurchaseToken()),
                Updates.set("orderId", s.latestOrderId()),
                Updates.set("startTime", date(s.startTime())),
                Updates.set("expiryTime", date(s.expiryTime())),
                Updates.set("autoRenewing", s.autoRenewing()),
                Updates.set("acknowledged", s.acknowledged()),
                Updates.set("testPurchase", s.testPurchase()),
                Updates.set("regionCode", s.regionCode()),
                // v40 iptal (zaman ve neden) ve duraklatma (kendiliğinden devam zamanı); yoksa null
                Updates.set("cancelTime", date(s.cancelTime())),
                Updates.set("cancelReason", s.cancelReason()),
                Updates.set("autoResumeTime", date(s.autoResumeTime())),
                Updates.set("source", source),
                Updates.set("fetchedAt", fetched),
                Updates.set("updatedAt", fetched),
                Updates.setOnInsert("createdAt", fetched));
        try {
            subs().updateOne(filter, update, new UpdateOptions().upsert(true));
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) throw e;
            if (String.valueOf(e.getMessage()).contains("purchaseToken")) {
                throw ApiException.conflict("purchase_token_in_use", "Bu satın alma başka bir hesaba bağlı");
            }
            // _id çakışması: belge daha yeni bir Play okumasıyla yazılmış; eskisi yazılmaz
            log.debug("Abonelik belgesi daha yeni bir durumla güncellenmiş; atlandı");
        }
    }

    /** Hareket ekler; aynı {@code orderId} + {@code type} zaten varsa (tekrar) eklemez ve {@code false} döner. */
    boolean appendTx(String uid, String token, PlaySubscription s, String planId, TransactionType type, String source,
                     String messageId, Integer notificationType, Instant eventTime, String orderOverride) {
        Document d = new Document("_id", UUID.randomUUID().toString()).append("userId", uid).append("purchaseToken", token);
        String orderId = orderOverride != null ? orderOverride : s.latestOrderId();
        // orderId yoksa alan hiç yazılmaz (benzersiz kısmi dizin yalnız alanı olan belgelere uygulanır)
        if (orderId != null) d.append("orderId", orderId);
        d.append("type", type.name()).append("status", s.status().name()).append("planId", planId)
                .append("productId", s.productId()).append("basePlanId", s.basePlanId())
                .append("linkedPurchaseToken", s.linkedPurchaseToken())
                .append("startTime", date(s.startTime())).append("expiryTime", date(s.expiryTime()))
                .append("autoRenewing", s.autoRenewing()).append("source", source);
        if (messageId != null) d.append("messageId", messageId);
        if (notificationType != null) d.append("notificationType", notificationType);
        if (eventTime != null) d.append("eventTime", Date.from(eventTime));
        d.append("at", new Date());
        // Doğrulamanın ham anlık görüntüsü (Play yanıtı; gizli bilgi içermez)
        if (s.raw() != null) d.append("snapshot", Document.parse(s.raw().toString()));
        try {
            txs().insertOne(d);
            return true;
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) return false;
            throw e;
        }
    }

    private static Date date(Instant i) { return i == null ? null : Date.from(i); }

    static Instant instant(Object o) { return o instanceof Date d ? d.toInstant() : null; }
}
