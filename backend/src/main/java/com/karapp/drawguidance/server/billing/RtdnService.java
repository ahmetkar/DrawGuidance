package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.billing.play.GooglePlayApi;
import com.karapp.drawguidance.server.billing.play.PlayApiException;
import com.karapp.drawguidance.server.billing.play.PlaySubscription;
import com.karapp.drawguidance.server.common.ApiException;
import com.karapp.drawguidance.server.common.Stores;
import com.karapp.drawguidance.server.config.DrawGuidanceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Date;

/**
 * v39 Google Play gerçek zamanlı geliştirici bildirimleri (RTDN, Pub/Sub itme). Her ileti {@code messageId} ile
 * {@code billing_webhook_events}'e yazılır (tekrar işlemeyi önler):
 *
 * <ol>
 *   <li>İlk kez gelen ileti {@code PROCESSING} olarak eklenir (60 sn kira). Aynı ileti tekrar gelirse: işlenmişse
 *       ({@code PROCESSED}/{@code IGNORED}) 200 "duplicate"; başka bir istek işliyorsa 409 (Pub/Sub sonra yeniden
 *       gönderir); önceki deneme başarısızsa ({@code FAILED}) ya da kirası dolmuşsa yeniden işlenir
 *       ({@code attempts} artar).</li>
 *   <li>Bildirimdeki durum kullanılmaz; abonelik Play'den yeniden okunur ({@code subscriptionsv2.get}) ve
 *       {@link SubscriptionService#applyNotification} ile uygulanır.</li>
 *   <li>Geçici hata (Play'e ulaşılamadı, faturalama ayarlı değil) → {@code FAILED} + 503: Pub/Sub yeniden dener.
 *       Kalıcı durum (test bildirimi, başka paket, geçersiz jeton, tek seferlik ürün) → {@code IGNORED} + 200.</li>
 * </ol>
 */
@Service
public class RtdnService {
    private static final Logger log = LoggerFactory.getLogger(RtdnService.class);
    static final Duration LEASE = Duration.ofSeconds(60);
    public static final String PROCESSING = "PROCESSING", PROCESSED = "PROCESSED", IGNORED = "IGNORED", FAILED = "FAILED";

    private final MongoTemplate mongo;
    private final GooglePlayApi play;
    private final SubscriptionService subscriptions;
    private final ObjectMapper json;
    private final String packageName;

    public RtdnService(MongoTemplate mongo, GooglePlayApi play, SubscriptionService subscriptions, ObjectMapper json, DrawGuidanceProperties props) {
        this.mongo = mongo;
        this.play = play;
        this.subscriptions = subscriptions;
        this.json = json;
        this.packageName = props.billing().packageName();
    }

    /** Yanıt gövdesi: {@code status} = processed | ignored | duplicate; {@code result} ayrıntı. */
    public record Outcome(String messageId, String status, String result) {}

    private MongoCollection<Document> events() { return mongo.getCollection(Stores.BILLING_WEBHOOK_EVENTS); }

    /** Bildirimden çıkarılan alanlar. */
    record Parsed(String kind, Integer notificationType, String notificationName, String purchaseToken, String subscriptionId,
                  String orderId, Integer productType, String packageName, Instant eventTime, JsonNode payload, String error) {}

    public Outcome handle(JsonNode envelope) {
        JsonNode msg = envelope == null ? null : envelope.path("message");
        String messageId = msg == null ? null : text(msg.path("messageId"));
        if (messageId == null && msg != null) messageId = text(msg.path("message_id"));
        if (messageId == null || messageId.length() > 200) throw ApiException.badRequest("bad_request", "Pub/Sub iletisi değil (message.messageId yok)");
        Parsed p = parse(text(msg.path("data")));

        String claim = claim(messageId, p, text(envelope.path("subscription")), time(text(msg.path("publishTime"))));
        if (claim != null) {
            if (claim.equals(PROCESSING)) {
                throw new ApiException(HttpStatus.CONFLICT, "in_progress", "Bu bildirim şu an işleniyor");
            }
            return new Outcome(messageId, "duplicate", claim.toLowerCase());
        }
        try {
            Outcome out = process(messageId, p);
            finish(messageId, out.status().equals("processed") ? PROCESSED : IGNORED, out.result());
            return out;
        } catch (ApiException e) {
            fail(messageId, e.code() + ": " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            fail(messageId, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;
        }
    }

    Parsed parse(String data) {
        if (data == null) return new Parsed("unknown", null, null, null, null, null, null, null, null, null, "data yok");
        JsonNode n;
        try {
            n = json.readTree(Base64.getDecoder().decode(data));
        } catch (IOException | IllegalArgumentException e) {
            return new Parsed("unknown", null, null, null, null, null, null, null, null, null, "data çözülemedi");
        }
        String pkg = text(n.path("packageName"));
        Instant eventTime = n.path("eventTimeMillis").canConvertToLong() || n.path("eventTimeMillis").isTextual()
                ? millis(n.path("eventTimeMillis").asText()) : null;
        if (n.has("subscriptionNotification")) {
            JsonNode s = n.get("subscriptionNotification");
            int type = s.path("notificationType").asInt(0);
            return new Parsed("subscription", type, TransactionType.notificationName(type), text(s.path("purchaseToken")),
                    text(s.path("subscriptionId")), null, null, pkg, eventTime, n, null);
        }
        if (n.has("voidedPurchaseNotification")) {
            JsonNode v = n.get("voidedPurchaseNotification");
            return new Parsed("voided", null, "VOIDED_PURCHASE", text(v.path("purchaseToken")), null, text(v.path("orderId")),
                    v.path("productType").asInt(0), pkg, eventTime, n, null);
        }
        if (n.has("testNotification")) return new Parsed("test", null, "TEST", null, null, null, null, pkg, eventTime, n, null);
        if (n.has("oneTimeProductNotification")) {
            return new Parsed("oneTimeProduct", null, "ONE_TIME_PRODUCT", text(n.path("oneTimeProductNotification").path("purchaseToken")),
                    null, null, null, pkg, eventTime, n, null);
        }
        return new Parsed("unknown", null, null, null, null, null, null, pkg, eventTime, n, null);
    }

    /** İletiyi işlemek için alır; alınamazsa var olan kaydın durumu ({@code PROCESSING}: başkası işliyor). */
    String claim(String messageId, Parsed p, String pushSubscription, Instant publishTime) {
        Date now = new Date();
        Date until = Date.from(now.toInstant().plus(LEASE));
        Document ev = new Document("_id", messageId).append("kind", p.kind()).append("notificationType", p.notificationType())
                .append("notificationName", p.notificationName()).append("purchaseToken", p.purchaseToken())
                .append("subscriptionId", p.subscriptionId()).append("orderId", p.orderId()).append("packageName", p.packageName())
                .append("eventTime", p.eventTime() == null ? null : Date.from(p.eventTime()))
                .append("publishTime", publishTime == null ? null : Date.from(publishTime))
                .append("pushSubscription", pushSubscription)
                .append("payload", p.payload() == null ? null : Document.parse(p.payload().toString()))
                .append("receivedAt", now).append("lastAttemptAt", now).append("status", PROCESSING)
                .append("leaseUntil", until).append("attempts", 1).append("duplicates", 0);
        try {
            events().insertOne(ev);
            return null;
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) throw e;
        }
        Document retry = events().findOneAndUpdate(
                Filters.and(Filters.eq("_id", messageId), Filters.or(Filters.eq("status", FAILED),
                        Filters.and(Filters.eq("status", PROCESSING), Filters.lt("leaseUntil", now)))),
                Updates.combine(Updates.set("status", PROCESSING), Updates.set("leaseUntil", until), Updates.set("lastAttemptAt", now),
                        Updates.inc("attempts", 1)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (retry != null) return null;
        Document existing = events().findOneAndUpdate(Filters.eq("_id", messageId), Updates.inc("duplicates", 1));
        return existing == null ? PROCESSING : existing.getString("status");
    }

    private void finish(String messageId, String status, String result) {
        events().updateOne(Filters.eq("_id", messageId), Updates.combine(Updates.set("status", status), Updates.set("result", result),
                Updates.set("processedAt", new Date()), Updates.unset("leaseUntil"), Updates.unset("error")));
    }

    private void fail(String messageId, String error) {
        try {
            events().updateOne(Filters.eq("_id", messageId), Updates.combine(Updates.set("status", FAILED),
                    Updates.set("error", error.length() > 500 ? error.substring(0, 500) : error), Updates.unset("leaseUntil")));
        } catch (RuntimeException e) {
            log.warn("Bildirim kaydı güncellenemedi: {}", e.getMessage());
        }
    }

    private Outcome process(String messageId, Parsed p) {
        if (p.error() != null) return new Outcome(messageId, "ignored", "malformed");
        if (p.packageName() != null && !packageName.equals(p.packageName())) return new Outcome(messageId, "ignored", "package_mismatch");
        switch (p.kind()) {
            case "test" -> { return new Outcome(messageId, "ignored", "test"); }
            case "subscription" -> {
                if (p.purchaseToken() == null) return new Outcome(messageId, "ignored", "malformed");
                return apply(messageId, p, TransactionType.fromNotification(p.notificationType()), null);
            }
            case "voided" -> {
                // productType 1: abonelik, 2: tek seferlik ürün (uygulamada yok)
                if (p.productType() == null || p.productType() != 1 || p.purchaseToken() == null) {
                    return new Outcome(messageId, "ignored", "not_subscription");
                }
                return apply(messageId, p, TransactionType.REVOKE, p.orderId());
            }
            default -> { return new Outcome(messageId, "ignored", "not_subscription"); }
        }
    }

    private Outcome apply(String messageId, Parsed p, TransactionType type, String orderOverride) {
        if (!play.configured()) throw SubscriptionService.notConfigured();
        PlaySubscription state;
        try {
            state = play.getSubscription(packageName, p.purchaseToken());
        } catch (PlayApiException e) {
            if (e.invalidToken()) return new Outcome(messageId, "ignored", "invalid_purchase_token");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "play_unavailable", "Google Play'e şu an ulaşılamıyor");
        }
        SubscriptionService.NotificationResult r = subscriptions.applyNotification(p.purchaseToken(), state, type, messageId,
                p.notificationType(), p.eventTime(), orderOverride);
        log.info("Play bildirimi {} ({}): {}", p.notificationName(), state.status(), r);
        return switch (r) {
            case APPLIED -> new Outcome(messageId, "processed", "applied");
            case UNKNOWN_USER -> new Outcome(messageId, "processed", "unknown_user");
            case ACCOUNT_MISMATCH -> new Outcome(messageId, "ignored", "account_mismatch");
            case UNKNOWN_PRODUCT -> new Outcome(messageId, "ignored", "unknown_product");
        };
    }

    private static String text(JsonNode n) { return n == null || !n.isTextual() || n.asText().isBlank() ? null : n.asText(); }

    private static Instant millis(String s) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(s.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant time(String s) {
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
