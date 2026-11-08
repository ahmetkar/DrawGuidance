package com.karapp.drawguidance.server.account;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.notify.SlackNotifier;
import com.karapp.drawguidance.server.privacy.PersonalDataCipher;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * v40 girişsiz hesap silme talepleri ({@code deletion_requests}): {@code {_id, email (küçük harfli), reason, locale,
 * status: PENDING, createdAt, ipHash, userAgentEnc, source: "web"}}. Ham IP saklanmaz. v49.2 tarayıcı
 * bilgisi (telefon modelini içerebilir) şifreli saklanır ({@link PersonalDataCipher}, AAD {@code deletion:<id>}).
 *
 * <ul>
 *   <li><b>Yineleme:</b> aynı e-postanın son {@link #DEDUPE_WINDOW} içinde bekleyen talebi varsa yenisi yazılmaz ve
 *       Slack'e bildirilmez (çağırana aynı 202 döner).</li>
 *   <li><b>Bildirim:</b> yeni talep Slack'e kısmen gizlenmiş e-postayla bildirilir (en iyi çaba).</li>
 *   <li><b>Tamamlanma:</b> kullanıcı oturum açıp hesabını sildiğinde ({@code DELETE /api/v1/users/me}) aynı e-postanın
 *       bekleyen talepleri {@code COMPLETED} olur ({@link #completeFor}).</li>
 *   <li><b>Saklama:</b> talepler işlendiğinin kanıtı olarak {@code createdAt}'ten 365 gün sonra TTL dizini ile silinir.</li>
 * </ul>
 */
@Service
public class DeletionRequestService {
    public static final String PENDING = "PENDING", COMPLETED = "COMPLETED";
    static final int MAX_REASON = 1000;
    static final Duration DEDUPE_WINDOW = Duration.ofHours(24);

    private final MongoTemplate mongo;
    private final SlackNotifier slack;
    private final PersonalDataCipher cipher;

    public DeletionRequestService(MongoTemplate mongo, SlackNotifier slack, PersonalDataCipher cipher) {
        this.mongo = mongo;
        this.slack = slack;
        this.cipher = cipher;
    }

    private MongoCollection<Document> coll() { return mongo.getCollection(Stores.DELETION_REQUESTS); }

    /** Talebi yazar; yinelenen talepse yazmaz ve {@code false} döner (yanıt yine 202). */
    public boolean submit(String email, String reason, String locale, String ipHash, String userAgent) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Document pending = coll().find(Filters.and(Filters.eq("email", email), Filters.eq("status", PENDING),
                Filters.gt("createdAt", Date.from(now.minus(DEDUPE_WINDOW))))).first();
        if (pending != null) return false;
        String id = UUID.randomUUID().toString();
        Document doc = new Document("_id", id)
                .append("email", email)
                .append("reason", reason)
                .append("locale", locale)
                .append("status", PENDING)
                .append("createdAt", Date.from(now))
                .append("ipHash", ipHash);
        String ua = cipher.seal(PersonalDataCipher.DELETION, id, userAgent);
        if (ua != null) doc.append("userAgentEnc", ua);
        doc.append("source", "web");
        coll().insertOne(doc);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("E-posta", SlackNotifier.maskEmail(email));
        fields.put("Dil", locale);
        fields.put("Gerekçe", reason == null ? "yok" : "var");
        fields.put("Talep", id);
        slack.send("Yeni hesap silme talebi (web formu)", fields);
        return true;
    }

    /** Bu e-postanın bekleyen taleplerini tamamlanmış sayar (kullanıcı hesabını kendisi sildi). */
    public long completeFor(String email) {
        if (email == null || email.isBlank()) return 0;
        return coll().updateMany(
                Filters.and(Filters.eq("email", email.trim().toLowerCase(Locale.ROOT)), Filters.eq("status", PENDING)),
                Updates.combine(Updates.set("status", COMPLETED), Updates.set("completedAt", new Date()),
                        Updates.set("completedBy", "self_service"))).getModifiedCount();
    }
}
