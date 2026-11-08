package com.karapp.drawguidance.server.privacy;

import com.karapp.drawguidance.server.common.Stores;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v49.2 eski kayıtlardaki düz metin cihaz bilgisini şifreli alana taşır (açılışta, bir kez; sonraki açılışlarda
 * yapacak iş kalmaz):
 *
 * <ul>
 *   <li>{@code error_reports.device} → {@code deviceEnc}</li>
 *   <li>{@code feedback.device.model} → {@code device.modelEnc}</li>
 *   <li>{@code support_messages.userAgent} ve {@code deletion_requests.userAgent} → {@code userAgentEnc}</li>
 * </ul>
 *
 * <p>Her belge "düz alan hâlâ aynı değerde" koşuluyla güncellenir: aynı anda iki süreç (sunucu ve CLI) çalışsa da bir
 * belge iki kez şifrelenmez. Anahtar ayarlı değilse ({@code DEVICE_DATA_KEY} yok, geçici anahtar) hiçbir şey yapılmaz;
 * yoksa veriler yeniden başlatınca okunamaz hâle gelirdi.
 */
@Component
public class DeviceDataMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DeviceDataMigration.class);
    static final int BATCH = 500;

    /** Taşınacak alan: koleksiyon, düz alan, şifreli alan, AAD öneki. */
    public record Field(String collection, String plain, String sealed, String kind) {}

    public static final List<Field> FIELDS = List.of(
            new Field(Stores.ERROR_REPORTS, "device", "deviceEnc", PersonalDataCipher.REPORT),
            new Field(Stores.FEEDBACK, "device.model", "device.modelEnc", PersonalDataCipher.FEEDBACK),
            new Field(Stores.SUPPORT_MESSAGES, "userAgent", "userAgentEnc", PersonalDataCipher.SUPPORT),
            new Field(Stores.DELETION_REQUESTS, "userAgent", "userAgentEnc", PersonalDataCipher.DELETION));

    private final MongoTemplate mongo;
    private final PersonalDataCipher cipher;

    public DeviceDataMigration(MongoTemplate mongo, PersonalDataCipher cipher) {
        this.mongo = mongo;
        this.cipher = cipher;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!cipher.persistent()) {
            log.info("Cihaz bilgisi şifreleme: DEVICE_DATA_KEY ayarlı değil, eski kayıtlar şifrelenmedi");
            return;
        }
        try {
            Map<String, Integer> done = migrate();
            if (done.values().stream().anyMatch(n -> n > 0)) log.info("Cihaz bilgisi şifreleme: eski kayıtlar şifrelendi {}", done);
        } catch (DataAccessException e) {
            log.warn("Cihaz bilgisi şifreleme yapılamadı (sonraki açılışta yeniden denenir): {}", e.getMessage());
        }
    }

    /** Bütün alanları taşır; koleksiyon.alan → şifrelenen belge sayısı. */
    public Map<String, Integer> migrate() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Field f : FIELDS) out.put(f.collection() + "." + f.plain(), migrate(f));
        return out;
    }

    int migrate(Field f) {
        int total = 0;
        while (true) {
            Query q = Query.query(Criteria.where(f.plain()).type(2)).limit(BATCH); // 2: BSON string
            q.fields().include("_id").include(f.plain());
            List<Document> docs = mongo.find(q, Document.class, f.collection());
            if (docs.isEmpty()) return total;
            int progress = 0;
            for (Document d : docs) {
                String id = String.valueOf(d.get("_id"));
                String value = value(d, f.plain());
                if (value == null) continue;
                Update u = new Update().unset(f.plain());
                String sealed = cipher.seal(f.kind(), id, value);
                if (sealed != null) u.set(f.sealed(), sealed);
                long n = mongo.updateFirst(Query.query(Criteria.where("_id").is(d.get("_id")).and(f.plain()).is(value)), u, f.collection())
                        .getModifiedCount();
                total += (int) n;
                progress += (int) n;
            }
            if (progress == 0) return total; // başka süreç taşıyor ya da ilerleme yok: döngüden çık
        }
    }

    /** "a.b" yolundaki yazı (yoksa null). */
    static String value(Document d, String path) {
        Object cur = d;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Document doc)) return null;
            cur = doc.get(part);
        }
        return cur instanceof String s ? s : null;
    }
}
