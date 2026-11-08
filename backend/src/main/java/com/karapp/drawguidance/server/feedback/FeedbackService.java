package com.karapp.drawguidance.server.feedback;

import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.notify.SlackNotifier;
import com.karapp.drawguidance.server.privacy.PersonalDataCipher;
import com.karapp.drawguidance.server.web.PublicForms;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * v40 uygulamadan mod başına geri bildirim ({@code feedback}): {@code {_id, uid, mode, feature?, rating (1–5),
 * text?, consent, createdAt, device?, email?}}. <b>Cihaz bilgisi ve e-posta yalnız kullanıcı izin verdiyse</b>
 * ({@code consent=true}) saklanır; izin yoksa gövdede gelseler de atılır. Yeni geri bildirim Slack'e mod, puan ve
 * (izinliyse) kısmen gizlenmiş e-postayla bildirilir; yazının kendisi Slack'e gitmez. 730 gün sonra TTL ile silinir.
 *
 * <p>v49.2 telefon modeli düz metin saklanmaz: {@code device.modelEnc} alanında şifrelidir ({@link PersonalDataCipher},
 * AAD {@code feedback:<id>}). Android ve uygulama sürümü, dil düz kalır (kişiyi tanıtmaz, sorgu için gerekir).
 */
@Service
public class FeedbackService {
    /** Uygulamanın modları: Resim, Not, Çizgi roman, 3B, Basit resim, Basit not, Çocuk. */
    public static final Set<String> MODES = Set.of("ART", "NOTES", "COMIC", "MODEL3D", "SIMPLE_ART", "SIMPLE_NOTES", "KIDS");
    static final int MAX_TEXT = 5000;
    static final int MAX_FEATURE = 100;
    static final int MAX_DEVICE_FIELD = 120;
    private static final Pattern FEATURE = Pattern.compile("^[A-Za-z0-9_.\\-]{1,100}$");

    /** Gövde (bilinmeyen alanlar yok sayılır). */
    public record Body(String mode, String feature, Integer rating, String text, Boolean consent, Device device) {}

    /** İzinle gönderilen cihaz bilgisi. */
    public record Device(String model, String androidVersion, String appVersion, Long versionCode, String locale) {}

    private final MongoTemplate mongo;
    private final SlackNotifier slack;
    private final PersonalDataCipher cipher;

    public FeedbackService(MongoTemplate mongo, SlackNotifier slack, PersonalDataCipher cipher) {
        this.mongo = mongo;
        this.slack = slack;
        this.cipher = cipher;
    }

    /** Doğrular, saklar, Slack'e bildirir; geri bildirimin kimliği. [email]: jetondaki e-posta (yoksa null). */
    public String submit(String uid, String email, Body b) {
        if (b == null) throw ApiException.badRequest("bad_request", "Gövde bir JSON nesnesi olmalı");
        String mode = b.mode() == null ? null : b.mode().trim().toUpperCase(Locale.ROOT);
        if (mode == null || !MODES.contains(mode)) throw ApiException.badRequest("invalid_mode", "mode şunlardan biri olmalı: " + String.join(", ", MODES.stream().sorted().toList()));
        if (b.rating() == null || b.rating() < 1 || b.rating() > 5) throw ApiException.badRequest("invalid_rating", "rating 1 ile 5 arasında olmalı");
        String feature = b.feature() == null || b.feature().isBlank() ? null : b.feature().trim();
        if (feature != null && !FEATURE.matcher(feature).matches()) throw ApiException.badRequest("invalid_feature", "feature en çok " + MAX_FEATURE + " karakter: harf, rakam, . _ -");
        String text = PublicForms.text("text", b.text(), MAX_TEXT, false);
        boolean consent = Boolean.TRUE.equals(b.consent());

        String id = UUID.randomUUID().toString();
        Document doc = new Document("_id", id).append("uid", uid).append("mode", mode).append("rating", b.rating())
                .append("consent", consent).append("createdAt", Date.from(Instant.now().truncatedTo(ChronoUnit.MILLIS)));
        if (feature != null) doc.append("feature", feature);
        if (text != null) doc.append("text", text);
        String maskedEmail = null;
        if (consent) {
            Document device = device(b.device(), id);
            if (device != null) doc.append("device", device);
            if (email != null && !email.isBlank()) {
                doc.append("email", email.trim().toLowerCase(Locale.ROOT));
                maskedEmail = SlackNotifier.maskEmail(email);
            }
        }
        mongo.getCollection(Stores.FEEDBACK).insertOne(doc);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Mod", mode);
        fields.put("Özellik", feature);
        fields.put("Puan", "★".repeat(b.rating()) + " (" + b.rating() + "/5)");
        fields.put("E-posta", maskedEmail);
        fields.put("Uzunluk", text == null ? "yazı yok" : text.length() + " karakter");
        fields.put("Geri bildirim", id);
        slack.send("Yeni geri bildirim (uygulama)", fields);
        return id;
    }

    private Document device(Device d, String id) {
        if (d == null) return null;
        Document out = new Document();
        Document plain = new Document();
        put(plain, "model", d.model());
        String model = cipher.seal(PersonalDataCipher.FEEDBACK, id, plain.getString("model"));
        if (model != null) out.append("modelEnc", model);
        put(out, "androidVersion", d.androidVersion());
        put(out, "appVersion", d.appVersion());
        if (d.versionCode() != null && d.versionCode() >= 0) out.append("versionCode", d.versionCode());
        String locale = PublicForms.locale(d.locale());
        if (locale != null) out.append("locale", locale);
        return out.isEmpty() ? null : out;
    }

    private static void put(Document d, String key, String value) {
        if (value == null) return;
        String v = value.replaceAll("\\p{Cntrl}", "").trim();
        if (v.isEmpty()) return;
        d.append(key, v.length() > MAX_DEVICE_FIELD ? v.substring(0, MAX_DEVICE_FIELD) : v);
    }
}
