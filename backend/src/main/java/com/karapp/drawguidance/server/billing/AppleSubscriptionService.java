package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.billing.apple.AppStoreApi;
import com.karapp.drawguidance.server.billing.apple.AppStoreApiException;
import com.karapp.drawguidance.server.billing.apple.AppleBillingProperties;
import com.karapp.drawguidance.server.billing.apple.AppleStates;
import com.karapp.drawguidance.server.billing.play.PlaySubscription;
import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.common.Stores;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * v50 App Store abonelikleri. Google Play'deki gibi kullanıcı başına tek güncel belge ({@code subscriptions}) ve
 * hareket geçmişi kullanılır; iki mağazanın aboneliği aynı Firebase hesabında aynı Premium hakkını verir.
 *
 * <p><b>Doğrulama</b> ({@code POST /api/v1/subscriptions/verify-apple}): uygulama StoreKit 2'deki işlemin imzalı
 * halini ({@code Transaction.jwsRepresentation}) gönderir. İmza Apple'ın kök sertifikasına kadar doğrulanır, sonra
 * aboneliğin güncel durumu App Store Server API'den okunur (uygulamanın söylediğine güvenilmez). İşlemdeki
 * {@code appAccountToken} varsa kullanıcının {@link AppleStates#appAccountToken} değeriyle aynı olmalı; yoksa (iOS'ta
 * girişsiz satın alma) abonelik ilk doğrulayan hesaba bağlanır, başka hesaba bağlı abonelik 409.
 *
 * <p><b>Bildirim</b> ({@code POST /api/v1/billing/apple-notifications}): App Store Server Notifications V2. İmza
 * doğrulanır; API anahtarı varsa durum yine API'den okunur (sıra dışı gelen eski bildirim yeni durumu ezmesin).
 */
@Service
public class AppleSubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(AppleSubscriptionService.class);
    public static final String SOURCE_APPLE_NOTIFICATION = "APPLE_NOTIFICATION";

    private final MongoTemplate mongo;
    private final PlanService plans;
    private final SubscriptionService subscriptions;
    private final AppStoreApi apple;
    private final AppleBillingProperties props;
    private final ObjectMapper json;

    public AppleSubscriptionService(MongoTemplate mongo, PlanService plans, SubscriptionService subscriptions, AppStoreApi apple,
                                    AppleBillingProperties props, ObjectMapper json) {
        this.mongo = mongo;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.apple = apple;
        this.props = props;
        this.json = json;
    }

    private MongoCollection<Document> subs() { return mongo.getCollection(Stores.SUBSCRIPTIONS); }

    private MongoCollection<Document> txs() { return mongo.getCollection(Stores.SUBSCRIPTION_TRANSACTIONS); }

    private MongoCollection<Document> events() { return mongo.getCollection(Stores.BILLING_WEBHOOK_EVENTS); }

    // ---------------------------------------------------------------- doğrulama

    public SubscriptionView verify(String uid, AppleVerifyRequest req) {
        if (!apple.configured()) throw notConfigured();
        AppStoreApi.AppleTransaction tx;
        try {
            tx = apple.verifyTransaction(req.signedTransaction().trim());
        } catch (AppStoreApiException e) {
            throw mapped(e, "invalid_transaction", "Apple bu işlemi doğrulamadı");
        }
        Document plan = plans.planForAppleProduct(tx.productId());
        if (plan == null) throw ApiException.badRequest("unknown_product", "Bu ürün bir plana bağlı değil: " + tx.productId());
        checkAccount(uid, tx.appAccountToken());
        String token = AppleStates.token(tx.originalTransactionId());
        if (subs().find(Filters.and(Filters.eq("purchaseToken", token), Filters.ne("_id", uid))).first() != null) {
            throw ApiException.conflict("purchase_token_in_use", "Bu satın alma başka bir hesaba bağlı");
        }
        AppStoreApi.AppleStatus status;
        try {
            status = apple.subscriptionStatus(tx.originalTransactionId(), tx.sandbox());
        } catch (AppStoreApiException e) {
            throw mapped(e, "invalid_transaction", "App Store bu aboneliği bulamadı");
        }
        // Durumdaki ürün de plana bağlı olmalı (ör. gruptaki başka bir ürüne geçilmiş olabilir)
        Document statusPlan = plans.planForAppleProduct(status.transaction().productId());
        if (statusPlan == null) statusPlan = plan;
        PlaySubscription state = AppleStates.toState(status, raw(status), Instant.now());
        subscriptions.apply(uid, token, state, statusPlan.getString("_id"), null, SubscriptionService.SOURCE_VERIFY, null, null, Instant.now());
        // Uygulamadan önce gelen bildirimlerin (kullanıcısı bilinmeyen) hareketleri bu kullanıcıya bağlanır
        txs().updateMany(Filters.and(Filters.eq("purchaseToken", token), Filters.eq("userId", null)), Updates.set("userId", uid));
        return subscriptions.me(uid);
    }

    private void checkAccount(String uid, UUID appAccountToken) {
        if (appAccountToken == null) {
            if (props.requireAccountToken()) throw ApiException.forbidden("account_mismatch", "Satın almada hesap bilgisi yok");
            return;
        }
        if (!appAccountToken.equals(AppleStates.appAccountToken(uid))) {
            throw ApiException.forbidden("account_mismatch", "Bu satın alma başka bir hesaba ait");
        }
    }

    static ApiException notConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "billing_not_configured",
                "App Store doğrulaması sunucuda yapılandırılmamış (App Store Server API anahtarı yok)");
    }

    private static ApiException mapped(AppStoreApiException e, String invalidCode, String invalidMessage) {
        return switch (e.kind()) {
            case INVALID -> ApiException.badRequest(invalidCode, invalidMessage);
            case NOT_CONFIGURED -> {
                log.warn("App Store doğrulaması yapılandırılmamış: {}", e.getMessage());
                yield notConfigured();
            }
            case UNAVAILABLE -> {
                log.warn("App Store'a ulaşılamadı: {}", e.getMessage());
                yield new ApiException(HttpStatus.BAD_GATEWAY, "appstore_unavailable", "App Store'a şu an ulaşılamıyor; biraz sonra yeniden deneyin");
            }
        };
    }

    /** Hareketin anlık görüntüsü: çözülmüş işlem ve yenileme bilgisi (imzalar ve gizli bilgi yok). */
    private JsonNode raw(AppStoreApi.AppleStatus s) {
        try {
            ObjectNode n = json.createObjectNode();
            n.put("store", AppleStates.STORE_APP_STORE);
            if (s.status() != null) n.put("status", s.status());
            n.set("transaction", json.valueToTree(s.transaction()));
            if (s.renewal() != null) n.set("renewal", json.valueToTree(s.renewal()));
            return n;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- bildirim

    /** Yanıt gövdesi: {@code status} = processed | ignored | duplicate. */
    public record Outcome(String notificationUuid, String status, String result) {}

    public Outcome handleNotification(String signedPayload) {
        AppStoreApi.AppleNotification n;
        try {
            n = apple.verifyNotification(signedPayload);
        } catch (AppStoreApiException e) {
            if (e.kind() == AppStoreApiException.Kind.INVALID) throw ApiException.badRequest("invalid_notification", "Bildirim Apple imzalı değil");
            // Geçici ya da ayar eksik: Apple yeniden gönderir
            log.warn("App Store bildirimi doğrulanamadı: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "appstore_unavailable", "Bildirim şu an işlenemiyor");
        }
        String id = "apple:" + (n.notificationUuid() == null ? UUID.randomUUID() : n.notificationUuid());
        Document existing = events().find(Filters.eq("_id", id)).first();
        if (existing != null && "PROCESSED".equals(existing.getString("status"))) {
            return new Outcome(n.notificationUuid(), "duplicate", existing.getString("result"));
        }
        Outcome out;
        try {
            out = process(n);
        } catch (ApiException e) {
            record(id, n, "FAILED", e.code());
            throw e;
        }
        record(id, n, "PROCESSED", out.result());
        return out;
    }

    private Outcome process(AppStoreApi.AppleNotification n) {
        if ("TEST".equals(n.type())) return new Outcome(n.notificationUuid(), "ignored", "test");
        if (n.status() == null || n.status().transaction() == null) return new Outcome(n.notificationUuid(), "ignored", "no_transaction");
        AppStoreApi.AppleTransaction tx = n.status().transaction();
        if (tx.expiresDate() == null && tx.subscriptionGroupId() == null) return new Outcome(n.notificationUuid(), "ignored", "not_subscription");
        AppStoreApi.AppleStatus status = n.status();
        if (apple.configured()) {
            try {
                status = apple.subscriptionStatus(tx.originalTransactionId(), tx.sandbox());
            } catch (AppStoreApiException e) {
                if (e.kind() == AppStoreApiException.Kind.UNAVAILABLE) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "appstore_unavailable", "App Store'a ulaşılamadı");
                }
                // Anahtar reddedildi ya da işlem bulunamadı: bildirimdeki (imzalı) durum kullanılır
                log.warn("Bildirimdeki abonelik API'den okunamadı ({}); bildirimdeki durum kullanılıyor", e.kind());
            }
        }
        String token = AppleStates.token(tx.originalTransactionId());
        Document plan = plans.planForAppleProduct(status.transaction().productId());
        String planId = plan == null ? null : plan.getString("_id");
        PlaySubscription state = AppleStates.toState(status, raw(status), Instant.now());
        TransactionType type = AppleStates.transactionType(n.type(), n.subtype());
        Instant eventTime = n.signedDate();
        String uid = subscriptions.ownerOf(token, null);
        if (uid == null) {
            subscriptions.appendTx(null, token, state, planId, type, SOURCE_APPLE_NOTIFICATION, n.notificationUuid(), null, eventTime, null);
            return new Outcome(n.notificationUuid(), "processed", SubscriptionService.NotificationResult.UNKNOWN_USER.name());
        }
        UUID accountToken = status.transaction().appAccountToken();
        if (accountToken != null && !accountToken.equals(AppleStates.appAccountToken(uid))) {
            log.warn("Bildirimdeki App Store aboneliği kayıtlı kullanıcıya ait değil; uygulanmadı");
            return new Outcome(n.notificationUuid(), "ignored", SubscriptionService.NotificationResult.ACCOUNT_MISMATCH.name());
        }
        if (planId == null) return new Outcome(n.notificationUuid(), "ignored", SubscriptionService.NotificationResult.UNKNOWN_PRODUCT.name());
        subscriptions.apply(uid, token, state, planId, type, SOURCE_APPLE_NOTIFICATION, n.notificationUuid(), null, eventTime);
        return new Outcome(n.notificationUuid(), "processed", SubscriptionService.NotificationResult.APPLIED.name());
    }

    private void record(String id, AppStoreApi.AppleNotification n, String status, String result) {
        AppStoreApi.AppleTransaction tx = n.status() == null ? null : n.status().transaction();
        Date now = new Date();
        Document ev = new Document("_id", id).append("kind", "apple").append("notificationName", n.type())
                .append("subtype", n.subtype()).append("sandbox", n.sandbox())
                .append("purchaseToken", tx == null ? null : AppleStates.token(tx.originalTransactionId()))
                .append("orderId", tx == null ? null : tx.transactionId())
                .append("eventTime", n.signedDate() == null ? null : Date.from(n.signedDate()))
                .append("status", status).append("result", result).append("receivedAt", now).append("lastAttemptAt", now);
        try {
            events().replaceOne(Filters.eq("_id", id), ev, new com.mongodb.client.model.ReplaceOptions().upsert(true));
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) throw e;
        }
    }
}
