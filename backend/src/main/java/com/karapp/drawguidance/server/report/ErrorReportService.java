package com.karapp.drawguidance.server.report;

import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.notify.SlackNotifier;
import com.karapp.drawguidance.server.privacy.PersonalDataCipher;
import com.karapp.drawguidance.server.web.TokenBucketLimiter;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * v37 uygulamanın hata/çökme raporları: gövde denetlenir ve kısaltılır ({@link #normalize}), {@code error_reports}
 * koleksiyonuna sunucunun alış zamanıyla yazılır; aynı hatanın raporları parmak iziyle ({@link #fingerprint}: tür,
 * istisna sınıfı ve en üstteki yığın satırları) {@code error_report_groups} içinde sayılır.
 *
 * <p>Raporda IP adresi saklanmaz (yalnız istek sınırında kullanılır). Kullanıcı kimliği yalnız geçerli bir Firebase
 * jetonu gönderildiyse yazılır.
 *
 * <p>v49.2 cihaz (telefon modeli) düz metin saklanmaz: {@code deviceEnc} alanında şifrelidir ({@link PersonalDataCipher},
 * AAD {@code report:<id>}).
 */
@Service
public class ErrorReportService {
    private static final Logger log = LoggerFactory.getLogger(ErrorReportService.class);

    public static final Set<String> KINDS = Set.of("CRASH", "ERROR", "USER");

    static final int MAX_MESSAGE = 4_000;
    static final int MAX_NOTE = 4_000;
    /** Yığın dökümü: baştan {@link #STACK_HEAD}, sondan {@link #STACK_TAIL} karakter kalır (arada kesildi notu). */
    static final int MAX_STACK = 64_000;
    static final int STACK_HEAD = 48_000;
    static final int STACK_TAIL = 15_000;
    static final int MAX_TYPE = 256;
    static final int MAX_FEATURE = 100;
    static final int MAX_APP_VERSION = 50;
    static final int MAX_DEVICE = 120;
    static final int MAX_LANGUAGE = 35;
    static final int MAX_BREADCRUMBS = 50;
    static final int MAX_BREADCRUMB = 300;
    /** Parmak izine giren en üst yığın satırı sayısı. */
    static final int FINGERPRINT_FRAMES = 5;

    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern CLASS_NAME = Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*(\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)*");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    /** Denetlenmiş, kısaltılmış rapor ({@code truncated}: kısaltılan alanların adları). */
    public record Normalized(String kind, String message, String stackTrace, String exceptionType, String feature,
                             String appVersion, Long versionCode, Integer sdk, String device, String language,
                             String installId, Long deviceTime, String note, List<String> breadcrumbs,
                             List<String> truncated, String fingerprint) {}

    /** Kayıt sonucu: raporun kimliği (yanıtta yalnız bu döner) ve parmak izi. */
    public record Stored(String id, String fingerprint) {}

    /** v40 aynı hatanın (parmak izi) Slack bildirimi en çok saatte bir (rapor sağanağında kanal dolmasın). */
    static final Duration SLACK_PER_FINGERPRINT = Duration.ofHours(1);

    private final MongoTemplate mongo;
    private final SlackNotifier slack;
    private final PersonalDataCipher cipher;
    private final TokenBucketLimiter slackLimit = new TokenBucketLimiter(1, SLACK_PER_FINGERPRINT, Clock.systemUTC());

    public ErrorReportService(MongoTemplate mongo, SlackNotifier slack, PersonalDataCipher cipher) {
        this.mongo = mongo;
        this.slack = slack;
        this.cipher = cipher;
    }

    /** Raporu ve parmak izi sayacını yazar. [uid]: geçerli jetonun kullanıcısı (yoksa null); [auth]: jeton durumu. */
    public Stored store(Normalized r, String uid, String auth) { return store(r, uid, auth, null); }

    /**
     * v40 [email]: jetondaki e-posta; yalnız Slack bildiriminde kısmen gizlenmiş olarak kullanılır (rapora yazılmaz).
     */
    public Stored store(Normalized r, String uid, String auth, String email) {
        String id = UUID.randomUUID().toString();
        Date now = Date.from(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        Document doc = new Document("_id", id)
                .append("kind", r.kind())
                .append("receivedAt", now)
                .append("installId", r.installId())
                .append("fingerprint", r.fingerprint())
                .append("auth", auth);
        put(doc, "uid", uid);
        put(doc, "message", r.message());
        put(doc, "stackTrace", r.stackTrace());
        put(doc, "exceptionType", r.exceptionType());
        put(doc, "feature", r.feature());
        put(doc, "appVersion", r.appVersion());
        put(doc, "versionCode", r.versionCode());
        put(doc, "sdk", r.sdk());
        put(doc, "deviceEnc", cipher.seal(PersonalDataCipher.REPORT, id, r.device()));
        put(doc, "language", r.language());
        put(doc, "deviceTime", r.deviceTime() == null ? null : new Date(r.deviceTime()));
        put(doc, "note", r.note());
        if (!r.breadcrumbs().isEmpty()) doc.append("breadcrumbs", r.breadcrumbs());
        if (!r.truncated().isEmpty()) doc.append("truncated", r.truncated());
        mongo.insert(doc, Stores.ERROR_REPORTS);
        count(r, id, now);
        notifySlack(r, id, email);
        return new Stored(id, r.fingerprint());
    }

    /**
     * v40 yeni rapor Slack'e: tür, istisna sınıfı, özellik, sürüm, not var mı ve kısmen gizlenmiş e-posta. İleti, yığın
     * dökümü ve notun kendisi gönderilmez (kişisel bilgi içerebilir). Aynı parmak izi saatte en çok bir kez.
     */
    private void notifySlack(Normalized r, String id, String email) {
        if (!slack.enabled() || !slackLimit.acquire(r.fingerprint()).allowed()) return;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Tür", r.kind());
        fields.put("İstisna", r.exceptionType());
        fields.put("Özellik", r.feature());
        fields.put("Sürüm", r.appVersion());
        fields.put("Not", r.note() == null ? "yok" : "var (" + r.note().length() + " karakter)");
        fields.put("E-posta", email == null ? null : SlackNotifier.maskEmail(email));
        fields.put("Parmak izi", r.fingerprint());
        fields.put("Rapor", id);
        slack.send("Yeni hata raporu (" + r.kind() + ")", fields);
    }

    /** Parmak izi sayacı (en iyi çaba: yazılamazsa rapor yine kabul edilir). */
    private void count(Normalized r, String id, Date now) {
        Query byId = Query.query(Criteria.where("_id").is(r.fingerprint()));
        Update update = new Update()
                .setOnInsert("firstSeen", now)
                .setOnInsert("kind", r.kind())
                .setOnInsert("exceptionType", r.exceptionType())
                .setOnInsert("feature", r.feature())
                .setOnInsert("sampleMessage", r.message())
                .max("lastSeen", now)
                .set("lastReportId", id)
                .set("lastAppVersion", r.appVersion())
                .inc("count", 1);
        try {
            try {
                mongo.upsert(byId, update, Stores.ERROR_REPORT_GROUPS);
            } catch (DuplicateKeyException e) {
                // Aynı parmak izinin eşzamanlı ilk raporu: belge artık var, güncelleme olarak yinele
                mongo.upsert(byId, update, Stores.ERROR_REPORT_GROUPS);
            }
        } catch (DataAccessException e) {
            log.warn("Hata raporu sayacı yazılamadı ({}): {}", r.fingerprint(), e.getMessage());
        }
    }

    private static void put(Document d, String key, Object value) { if (value != null) d.append(key, value); }

    // ---------------------------------------------------------------- denetim ve kısaltma

    /**
     * Gövdeyi denetler ve kısaltır. 400 hataları: {@code invalid_kind} (tür yok ya da CRASH/ERROR/USER değil),
     * {@code invalid_install_id} (UUID değil), {@code empty_report} (ileti, yığın dökümü ve not boş),
     * {@code validation} (negatif sürüm kodu ya da cihaz zamanı, 1–1000 dışında {@code sdk}). Uzun alanlar kesilir (sınırlar
     * bu sınıfın sabitleri), son {@link #MAX_BREADCRUMBS} eylem kalır.
     */
    public static Normalized normalize(ErrorReportRequest b) {
        if (b == null) throw ApiException.badRequest("bad_request", "Rapor gövdesi bir JSON nesnesi olmalı");
        String kind = b.kind() == null ? null : b.kind().trim().toUpperCase(Locale.ROOT);
        if (kind == null || !KINDS.contains(kind)) {
            throw ApiException.badRequest("invalid_kind", "kind CRASH, ERROR ya da USER olmalı");
        }
        String installId = b.installId() == null ? null : b.installId().trim();
        if (installId == null || !UUID_PATTERN.matcher(installId).matches()) {
            throw ApiException.badRequest("invalid_install_id", "installId UUID biçiminde olmalı (ör. 0f8fad5b-d9cb-469f-a165-70867728950e)");
        }
        if (blank(b.message()) && blank(b.stackTrace()) && blank(b.note())) {
            throw ApiException.badRequest("empty_report", "message, stackTrace ya da note alanlarından en az biri dolu olmalı");
        }
        if (b.versionCode() != null && b.versionCode() < 0) throw ApiException.badRequest("validation", "versionCode: negatif olamaz");
        if (b.sdk() != null && (b.sdk() < 1 || b.sdk() > 1000)) throw ApiException.badRequest("validation", "sdk: 1 ile 1000 arasında olmalı");
        if (b.deviceTime() != null && b.deviceTime() < 0) throw ApiException.badRequest("validation", "deviceTime: negatif olamaz");

        List<String> truncated = new ArrayList<>();
        String message = cut("message", nullIfBlank(b.message()), MAX_MESSAGE, truncated);
        String stack = stack(nullIfBlank(b.stackTrace()), truncated);
        String note = cut("note", nullIfBlank(b.note()), MAX_NOTE, truncated);
        String type = cut("exceptionType", line(b.exceptionType()), MAX_TYPE, truncated);
        if (type == null) type = exceptionTypeOf(stack);
        String feature = cut("feature", line(b.feature()), MAX_FEATURE, truncated);
        String appVersion = cut("appVersion", line(b.appVersion()), MAX_APP_VERSION, truncated);
        String device = cut("device", line(b.device()), MAX_DEVICE, truncated);
        String language = cut("language", line(b.language()), MAX_LANGUAGE, truncated);
        List<String> crumbs = breadcrumbs(b.breadcrumbs(), truncated);
        String fp = fingerprint(kind, type, stack, message, feature);
        return new Normalized(kind, message, stack, type, feature, appVersion, b.versionCode(), b.sdk(), device, language,
                installId.toLowerCase(Locale.ROOT), b.deviceTime(), note, crumbs, List.copyOf(truncated), fp);
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static String nullIfBlank(String s) { return blank(s) ? null : s; }

    /** Tek satırlık kısa alan: denetim karakterleri silinir, kırpılır, boşsa null. */
    private static String line(String s) {
        if (s == null) return null;
        String t = CONTROL.matcher(s).replaceAll("").trim();
        return t.isEmpty() ? null : t;
    }

    /** [max] karakteri aşan yazı "…" ile kesilir (vekil çifti bölünmez), alan adı [truncated]'a eklenir. */
    static String cut(String field, String s, int max, List<String> truncated) {
        if (s == null || s.length() <= max) return s;
        if (truncated != null) truncated.add(field);
        return s.substring(0, safeEnd(s, max - 1)) + "…";
    }

    /** Yığın dökümü sınırı aşarsa baştan ve sondan parçalar kalır ("Caused by" satırları çoğunlukla sondadır). */
    static String stack(String s, List<String> truncated) {
        if (s == null || s.length() <= MAX_STACK) return s;
        truncated.add("stackTrace");
        int head = safeEnd(s, STACK_HEAD);
        int tail = safeStart(s, s.length() - STACK_TAIL);
        return s.substring(0, head) + "\n…[" + (tail - head) + " karakter kesildi]…\n" + s.substring(tail);
    }

    private static int safeEnd(String s, int end) {
        return end > 0 && Character.isHighSurrogate(s.charAt(end - 1)) ? end - 1 : end;
    }

    private static int safeStart(String s, int start) {
        return start < s.length() && Character.isLowSurrogate(s.charAt(start)) ? start + 1 : start;
    }

    private static List<String> breadcrumbs(List<String> raw, List<String> truncated) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<String> kept = raw.stream().filter(c -> c != null && !c.isBlank()).toList();
        boolean cutAny = kept.size() > MAX_BREADCRUMBS;
        if (cutAny) kept = kept.subList(kept.size() - MAX_BREADCRUMBS, kept.size());
        List<String> out = new ArrayList<>(kept.size());
        for (String c : kept) {
            String t = cut("breadcrumbs", c, MAX_BREADCRUMB, null);
            if (!t.equals(c)) cutAny = true;
            out.add(t);
        }
        if (cutAny) truncated.add("breadcrumbs");
        return List.copyOf(out);
    }

    /** Yığın dökümünün ilk satırındaki istisna sınıfı ({@code java.lang.IllegalStateException: ileti} → sınıf). */
    static String exceptionTypeOf(String stack) {
        if (stack == null) return null;
        String first = stack.strip().lines().findFirst().orElse("").trim();
        int colon = first.indexOf(':');
        String candidate = (colon >= 0 ? first.substring(0, colon) : first).trim();
        return CLASS_NAME.matcher(candidate).matches() && candidate.length() <= MAX_TYPE ? candidate : null;
    }

    // ---------------------------------------------------------------- parmak izi

    /**
     * Aynı hatanın raporlarını birleştiren anahtar: SHA-256(tür, istisna sınıfı, en üstteki {@link #FINGERPRINT_FRAMES}
     * yığın satırı) ilk 16 baytı (32 onaltılık karakter). Satırlarda dosya/satır numarası ve {@code $1} gibi
     * derleyici adları atılır (sürümler arasında değişmesin). Yığın yoksa ileti (sayılar #) ya da özellik etiketi.
     */
    static String fingerprint(String kind, String exceptionType, String stack, String message, String feature) {
        StringBuilder key = new StringBuilder(kind).append('\n').append(exceptionType == null ? "" : exceptionType);
        List<String> frames = topFrames(stack, FINGERPRINT_FRAMES);
        if (!frames.isEmpty()) frames.forEach(f -> key.append('\n').append(f));
        else if (message != null) {
            String m = message.replaceAll("\\d+", "#");
            key.append("\nm:").append(m, 0, Math.min(200, m.length()));
        } else {
            key.append("\nf:").append(feature == null ? "" : feature);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** İlk istisnanın en üstteki yığın satırları ({@code at a.b.C.m(C.kt:12)} → {@code a.b.C.m}). */
    static List<String> topFrames(String stack, int n) {
        List<String> out = new ArrayList<>(n);
        if (stack == null) return out;
        for (String raw : (Iterable<String>) stack.lines()::iterator) {
            String t = raw.trim();
            if (t.startsWith("Caused by:") || t.startsWith("Suppressed:")) break;
            if (!t.startsWith("at ")) continue;
            String frame = t.substring(3).trim();
            int paren = frame.indexOf('(');
            if (paren > 0) frame = frame.substring(0, paren);
            out.add(frame.replaceAll("\\$\\d+", "\\$#"));
            if (out.size() >= n) break;
        }
        return out;
    }
}
