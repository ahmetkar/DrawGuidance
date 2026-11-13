package com.karapp.drawguidance.server.user;

import com.karapp.drawguidance.server.account.DeletionRequestService;
import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.security.TokenRevocations;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Kullanıcı kaydı: {@code firebaseUid} ile tek komutta ekle-ya-da-güncelle ({@code findAndModify}, upsert).
 *
 * <p>Kayıt varsa {@code _id} ve {@code createdAt} korunur (gövdedeki {@code id} yok sayılır), alanlar güncellenir,
 * {@code version} artar. Yoksa {@code _id} = gövdedeki UUID. Aynı kullanıcı için eşzamanlı ilk kayıtlarda
 * {@code firebaseUid} benzersiz dizini ikinci eklemeyi reddeder; o istek güncelleme olarak yinelenir, sonunda tek
 * belge kalır.
 */
@Service
public class UserService {
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    /** Dünyada tarihin en ileride olduğu saat dilimi: herhangi bir yerde "bugün" olan doğum tarihi kabul edilir. */
    private static final ZoneOffset LATEST_ZONE = ZoneOffset.ofHours(14);
    private static final String UID = "firebaseUid";

    /** v40 silinen hesabın saklanan abonelik belgesinin kimlik öneki (Firebase uid'lerinde ':' olmaz). */
    public static final String DELETED_PREFIX = "deleted:";

    private final MongoTemplate mongo;
    private final DeletionRequestService deletionRequests;
    private final TokenRevocations revocations;

    public UserService(MongoTemplate mongo, DeletionRequestService deletionRequests, TokenRevocations revocations) {
        this.mongo = mongo;
        this.deletionRequests = deletionRequests;
        this.revocations = revocations;
    }

    /**
     * v40 Firebase jetonundaki e-posta ({@code email}) ve doğrulanmışlığı ({@code email_verified}). Profildeki e-posta
     * ve {@code emailVerified} bunlardan yazılır; gövdedeki değerlere güvenilmez (yoksa biri profiline başkasının
     * e-postasını "doğrulanmış" yazıp hesabını silerek o e-postanın bekleyen silme taleplerini kapatabilirdi).
     */
    public record TokenEmail(String email, boolean verified) {
        public static final TokenEmail NONE = new TokenEmail(null, false);

        public static TokenEmail of(Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) return NONE;
            return new TokenEmail(email.trim(), Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")));
        }

        /** Yalnız doğrulanmışsa e-posta; değilse {@code null}. */
        public String verifiedEmail() { return verified ? email : null; }
    }

    /** Firebase'in desteklediği giriş sağlayıcılarından bu uygulamanın kullandıkları (Google ve Apple). */
    static final java.util.Set<String> SIGN_IN_PROVIDERS = java.util.Set.of("google.com", "apple.com");

    /**
     * Jetondaki giriş sağlayıcısı ({@code firebase.sign_in_provider}: {@code google.com} ya da {@code apple.com});
     * başka/eksikse {@code null}. Firebase jetonu imzalı olduğundan istemci bunu değiştiremez.
     */
    public static String signInProvider(Jwt jwt) {
        Object firebase = jwt.getClaims().get("firebase");
        if (!(firebase instanceof java.util.Map<?, ?> map)) return null;
        Object provider = map.get("sign_in_provider");
        return provider instanceof String p && SIGN_IN_PROVIDERS.contains(p) ? p : null;
    }

    private static Query byUid(String uid) { return Query.query(Criteria.where(UID).is(uid)); }

    public User get(String uid) {
        User user = mongo.findOne(byUid(uid), User.class, Stores.USERS);
        if (user == null) throw ApiException.notFound("user_not_found", "Bu Firebase hesabı için kullanıcı kaydı yok");
        return user;
    }

    /**
     * v40 hesabı ve kullanıcıya bağlı bütün verileri siler ({@code DELETE /api/v1/users/me}; uygulamadaki "Hesabı sil"
     * ve web sitesindeki oturumlu silme aynı ucu çağırır). Tekrarlanabilir: veri yoksa da başarılı.
     *
     * <ul>
     *   <li><b>Silinir:</b> {@code users} (profil, e-posta, ad, doğum tarihi, onay kayıtları), {@code challenge_progress}
     *       (Çizim Görevi ilerlemesi), {@code error_reports} içinde bu kullanıcının kimliğiyle gelen raporlar.</li>
     *   <li><b>Saklanır ama kullanıcıdan koparılır (yasal/vergi kayıtları):</b> {@code subscriptions} belgesi
     *       {@code _id = "deleted:<rastgele>"} ile yeniden yazılır, satın alma jetonu {@code retainedPurchaseToken}
     *       alanına taşınır (aynı satın alma sonradan başka/yeni hesapla doğrulanabilsin); {@code subscription_transactions}
     *       hareketlerinde {@code userId} boşaltılır. Sipariş numarası, ürün, tutar dönemi ve tarihler kalır. Aboneliğin
     *       kendisi Google Play'de sürer: kullanıcı Play'den iptal etmelidir.</li>
     *   <li><b>Dokunulmaz:</b> {@code billing_webhook_events} (kullanıcı kimliği yok; 180 günde TTL ile silinir),
     *       kullanıcısız hata raporu grupları, {@code support_messages} (730 gün TTL).</li>
     *   <li>Aynı e-postanın bekleyen web silme talepleri ({@code deletion_requests}) {@code COMPLETED} olur.</li>
     * </ul>
     *
     * <p>Sunucuda tuval/çizim koleksiyonu yoktur (çizimler cihazda kalır). Kullanıcıya bağlı yeni bir koleksiyon
     * eklenirse buraya da eklenmeli; {@code UserApiTest} bütün koleksiyonlarda uid kalmadığını denetler.
     *
     * <p>v40 silmeden sonra bu uid'nin o ana kadar verilmiş jetonları iptal edilir ({@link TokenRevocations}): elde
     * kalan eski jetonla profil ya da ilerleme yeniden yazılamaz. Bekleyen web talepleri yalnız doğrulanmış e-postayla
     * (jetondaki ya da profilde jetondan yazılmış ve doğrulanmış) kapatılır.
     *
     * @param tokenEmail jetondaki doğrulanmış e-posta (bekleyen web taleplerini kapatmak için; yoksa {@code null})
     */
    public void delete(String uid, String tokenEmail) {
        revocations.revoke(uid);
        User existing = mongo.findOne(byUid(uid), User.class, Stores.USERS);
        mongo.remove(byUid(uid), Stores.USERS);
        mongo.getCollection(Stores.CHALLENGE_PROGRESS).deleteMany(Filters.or(Filters.eq("_id", uid), Filters.eq(UID, uid)));
        mongo.getCollection(Stores.ERROR_REPORTS).deleteMany(Filters.eq("uid", uid));
        anonymizeBilling(uid);
        deletionRequests.completeFor(tokenEmail);
        if (existing != null && existing.email() != null && Boolean.TRUE.equals(existing.emailVerified())
                && !existing.email().equalsIgnoreCase(String.valueOf(tokenEmail))) {
            deletionRequests.completeFor(existing.email());
        }
    }

    /** Abonelik kaydını ve hareketleri saklar ama kullanıcı kimliğinden koparır (bkz. {@link #delete}). */
    private void anonymizeBilling(String uid) {
        MongoCollection<Document> subs = mongo.getCollection(Stores.SUBSCRIPTIONS);
        Document current = subs.find(Filters.eq("_id", uid)).first();
        Date now = new Date();
        if (current != null) {
            Document kept = new Document(current);
            kept.put("_id", DELETED_PREFIX + java.util.UUID.randomUUID());
            Object token = kept.remove("purchaseToken");
            if (token != null) kept.put("retainedPurchaseToken", token);
            kept.put("anonymized", true);
            kept.put("anonymizedAt", now);
            subs.insertOne(kept);
            subs.deleteOne(Filters.eq("_id", uid));
        }
        mongo.getCollection(Stores.SUBSCRIPTION_TRANSACTIONS).updateMany(Filters.eq("userId", uid),
                Updates.combine(Updates.set("userId", null), Updates.set("anonymizedAt", now)));
    }

    public User upsert(String uid, UserRequest body, TokenEmail tokenEmail) {
        return upsert(uid, body, tokenEmail, null);
    }

    /** @param provider girişin yapıldığı sağlayıcı ({@link #signInProvider}); {@code null} ise kayıttaki değer kalır */
    public User upsert(String uid, UserRequest body, TokenEmail tokenEmail, String provider) {
        if (!uid.equals(body.firebaseUid())) {
            throw ApiException.forbidden("uid_mismatch", "Gövdedeki firebaseUid jetondaki kullanıcıyla aynı değil");
        }
        String birthDate = birthDate(body.birthDate());
        // v40 jetonda e-posta varsa o yazılır (gövdedeki yok sayılır); yoksa gövdedeki yazılır ama doğrulanmamış sayılır
        String email = tokenEmail.email() != null ? tokenEmail.email() : body.email();
        boolean emailVerified = tokenEmail.email() != null && tokenEmail.verified();
        String newId = body.id() != null && UUID.matcher(body.id().trim()).matches() ? body.id().trim().toLowerCase(Locale.ROOT) : null;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        FindAndModifyOptions returnNew = FindAndModifyOptions.options().returnNew(true);

        if (newId == null) {
            // Geçersiz id yalnız kayıt varsa yok sayılabilir (id zaten değişmez)
            User existing = mongo.findAndModify(byUid(uid), fields(body, email, emailVerified, birthDate, now, provider), returnNew, User.class, Stores.USERS);
            if (existing == null) throw ApiException.badRequest("invalid_id", "id UUID biçiminde olmalı (ör. 0f8fad5b-d9cb-469f-a165-70867728950e)");
            return existing;
        }
        Update insertOrUpdate = fields(body, email, emailVerified, birthDate, now, provider).setOnInsert("_id", newId).setOnInsert("createdAt", now);
        try {
            return mongo.findAndModify(byUid(uid), insertOrUpdate, FindAndModifyOptions.options().returnNew(true).upsert(true), User.class, Stores.USERS);
        } catch (DuplicateKeyException e) {
            // Aynı uid için eşzamanlı ilk kayıt: diğer istek önce ekledi → güncelleme olarak yinele
            User again = mongo.findAndModify(byUid(uid), fields(body, email, emailVerified, birthDate, now, provider), returnNew, User.class, Stores.USERS);
            if (again == null) throw ApiException.conflict("id_conflict", "Bu id başka bir kullanıcı kaydında kullanılıyor");
            return again;
        }
    }

    /** Gövdedeki alanların tamamı yazılır (PUT: verilmeyen alan null olur). */
    private static Update fields(UserRequest b, String email, boolean emailVerified, String birthDate, Instant now, String provider) {
        Update update = new Update();
        if (provider != null) update.set("signInProvider", provider);
        // v36 onay bilgisi yalnız gönderildiyse yazılır (eski istemciler var olan onayı silmesin)
        if (b.termsAcceptedAt() != null) update.set("termsAcceptedAt", b.termsAcceptedAt());
        if (b.termsVersion() != null) update.set("termsVersion", b.termsVersion());
        return update
                .set("displayName", b.displayName())
                .set("givenName", b.givenName())
                .set("familyName", b.familyName())
                .set("email", email)
                .set("emailVerified", emailVerified)
                .set("photoUrl", b.photoUrl())
                .set("accountCreatedAt", b.accountCreatedAt())
                .set("birthDate", birthDate)
                .set("clientUpdatedAt", b.clientUpdatedAt())
                .set("updatedAt", now)
                .inc("version", 1L);
    }

    /** {@code yyyy-MM-dd} ya da boş; geleceğe ait tarih 400. */
    static String birthDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        LocalDate date;
        try {
            date = LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("invalid_birth_date", "birthDate yyyy-MM-dd biçiminde olmalı (ör. 1990-05-17)");
        }
        if (date.isAfter(LocalDate.now(LATEST_ZONE))) {
            throw ApiException.badRequest("invalid_birth_date", "birthDate gelecekte bir tarih olamaz");
        }
        return date.toString();
    }
}
