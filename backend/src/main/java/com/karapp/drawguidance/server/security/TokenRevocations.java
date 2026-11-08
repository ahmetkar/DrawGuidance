package com.karapp.drawguidance.server.security;

import com.karapp.drawguidance.server.common.Stores;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v40 sunucu tarafı jeton iptali. Firebase ID jetonları 1 saat geçerlidir ve imzayla doğrulanır; Firebase'in kendi
 * iptal denetimi ({@code checkRevoked}) her istekte Google'a çağrı ister. Bunun yerine hesap silindiğinde
 * ({@code DELETE /api/v1/users/me}) kullanıcı için iptal zamanı yazılır ve bu zamandan önce ({@code iat} ≤ iptal
 * saniyesi) verilmiş jetonlarla <b>yazma</b> istekleri ({@code POST/PUT/PATCH}) reddedilir
 * ({@code TokenRevocationInterceptor}, 401 {@code token_revoked}): silinen hesabın elde kalan jetonuyla profil,
 * ilerleme ya da abonelik yeniden yazılamaz. Okumalar (veri zaten silindi) ve silmenin yinelenmesi (tekrarlanabilir)
 * etkilenmez; aynı Firebase hesabıyla yeniden girişte verilen yeni jeton geçerlidir.
 *
 * <ul>
 *   <li>Kayıt ({@code token_revocations}) kullanıcı kimliğini açık tutmaz: {@code _id} = SHA-256(uid); {@link #KEEP}
 *       sonra TTL ile silinir (o zamana dek eski jetonların hepsinin süresi dolmuştur).</li>
 *   <li>Okumalar {@link #CACHE_TTL} boyunca bellekte tutulur; iptal eden sunucu kopyası kendi önbelleğini hemen
 *       günceller, öteki kopyalar en geç {@link #CACHE_TTL} sonra görür.</li>
 *   <li>Veritabanına ulaşılamazsa jeton iptal edilmemiş sayılır (veritabanı yokken yazma zaten yapılamaz).</li>
 * </ul>
 */
@Component
public class TokenRevocations {
    private static final Logger log = LoggerFactory.getLogger(TokenRevocations.class);
    /** İptal kaydının ömrü (jeton ömrü 1 saat + saat kayması payı). */
    public static final Duration KEEP = Duration.ofHours(2);
    static final Duration CACHE_TTL = Duration.ofSeconds(30);
    static final int MAX_CACHE = 50_000;

    private record Cached(Instant revokedAt, long until) {}

    private final MongoTemplate mongo;
    private final Clock clock;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public TokenRevocations(MongoTemplate mongo) { this(mongo, Clock.systemUTC()); }

    TokenRevocations(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    private MongoCollection<Document> coll() { return mongo.getCollection(Stores.TOKEN_REVOCATIONS); }

    /** Kaydın anahtarı: uid'nin SHA-256 özeti (silinen hesabın kimliği açıkça kalmasın). */
    public static String key(String uid) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Bu uid için şu ana kadar verilmiş bütün jetonları (yazma için) geçersiz sayar. */
    public void revoke(String uid) {
        Instant now = clock.instant();
        String key = key(uid);
        Document d = new Document("_id", key).append("revokedAt", Date.from(now)).append("expireAt", Date.from(now.plus(KEEP)));
        coll().replaceOne(Filters.eq("_id", key), d, new ReplaceOptions().upsert(true));
        cache.put(key, new Cached(now, clock.millis() + CACHE_TTL.toMillis()));
    }

    /** uid'nin iptal zamanı (yoksa boş). */
    Optional<Instant> revokedAt(String uid) {
        String key = key(uid);
        long now = clock.millis();
        Cached c = cache.get(key);
        if (c != null && c.until() > now) return Optional.ofNullable(c.revokedAt());
        Document d = coll().find(Filters.eq("_id", key)).first();
        Instant at = d != null && d.get("revokedAt") instanceof Date date ? date.toInstant() : null;
        if (at != null && at.plus(KEEP).isBefore(clock.instant())) at = null;
        if (cache.size() >= MAX_CACHE) cache.clear();
        cache.put(key, new Cached(at, now + CACHE_TTL.toMillis()));
        return Optional.ofNullable(at);
    }

    /** Jeton, kullanıcısının hesabı silinmeden önce mi verildi. */
    public boolean isRevoked(Jwt jwt) {
        String uid = jwt.getSubject();
        if (uid == null) return false;
        Optional<Instant> revoked;
        try {
            revoked = revokedAt(uid);
        } catch (RuntimeException e) {
            log.warn("Jeton iptal kaydı okunamadı (veritabanı?): {}", e.getClass().getSimpleName());
            return false;
        }
        Instant iat = jwt.getIssuedAt();
        return revoked.isPresent() && (iat == null || iat.getEpochSecond() <= revoked.get().getEpochSecond());
    }
}
