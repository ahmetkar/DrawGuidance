package com.karapp.drawguidance.server.support;

import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.notify.SlackNotifier;
import com.karapp.drawguidance.server.privacy.PersonalDataCipher;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v40 destek mesajları ({@code support_messages}): {@code {_id, email (küçük harfli), subject, message, locale,
 * status: NEW, createdAt, ipHash, userAgentEnc, source: "web"}}. v49.2 tarayıcı bilgisi (telefon modelini
 * içerebilir) şifreli saklanır ({@link PersonalDataCipher}, AAD {@code support:<id>}). Ham IP saklanmaz. Yeni mesaj Slack'e kısmen gizlenmiş
 * e-posta ve konuyla bildirilir (mesajın kendisi Slack'e gitmez). {@code createdAt}'ten 730 gün sonra TTL dizini ile
 * silinir.
 */
@Service
public class SupportMessageService {
    public static final String NEW = "NEW";
    static final int MAX_SUBJECT = 150;
    static final int MAX_MESSAGE = 5000;

    private final MongoTemplate mongo;
    private final SlackNotifier slack;
    private final PersonalDataCipher cipher;

    public SupportMessageService(MongoTemplate mongo, SlackNotifier slack, PersonalDataCipher cipher) {
        this.mongo = mongo;
        this.slack = slack;
        this.cipher = cipher;
    }

    public String submit(String email, String subject, String message, String locale, String ipHash, String userAgent) {
        String id = UUID.randomUUID().toString();
        Document doc = new Document("_id", id)
                .append("email", email)
                .append("subject", subject)
                .append("message", message)
                .append("locale", locale)
                .append("status", NEW)
                .append("createdAt", Date.from(Instant.now().truncatedTo(ChronoUnit.MILLIS)))
                .append("ipHash", ipHash);
        String ua = cipher.seal(PersonalDataCipher.SUPPORT, id, userAgent);
        if (ua != null) doc.append("userAgentEnc", ua);
        doc.append("source", "web");
        mongo.getCollection(Stores.SUPPORT_MESSAGES).insertOne(doc);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("E-posta", SlackNotifier.maskEmail(email));
        fields.put("Konu", subject);
        fields.put("Dil", locale);
        fields.put("Uzunluk", message.length() + " karakter");
        fields.put("Mesaj", id);
        slack.send("Yeni destek mesajı (web formu)", fields);
        return id;
    }
}
