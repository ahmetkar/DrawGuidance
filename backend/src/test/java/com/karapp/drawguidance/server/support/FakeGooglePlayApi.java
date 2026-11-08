package com.karapp.drawguidance.server.support;

import com.karapp.drawguidance.server.billing.play.GooglePlayApi;
import com.karapp.drawguidance.server.billing.play.PlayApiException;
import com.karapp.drawguidance.server.billing.play.PlaySubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * v39 testlerde Google Play Developer API: jeton → {@code subscriptionsv2} yanıtı (gerçek yanıtın biçiminde JSON;
 * sunucunun ayrıştırıcısı da sınanır). {@link #configured} kapatılabilir, {@link #failing} jetonları 500 döner.
 */
public class FakeGooglePlayApi implements GooglePlayApi {
    private static final ObjectMapper JSON = new ObjectMapper();

    public volatile boolean configured = true;
    public final Map<String, ObjectNode> subscriptions = new ConcurrentHashMap<>();
    public final Set<String> failing = ConcurrentHashMap.newKeySet();
    public final List<String> acknowledged = new CopyOnWriteArrayList<>();

    @Override
    public boolean configured() { return configured; }

    @Override
    public PlaySubscription getSubscription(String packageName, String purchaseToken) throws PlayApiException {
        if (!configured) throw new PlayApiException(503, "ayarlı değil");
        if (failing.contains(purchaseToken)) throw new PlayApiException(500, "Play geçici hatası");
        ObjectNode n = subscriptions.get(purchaseToken);
        if (n == null) throw new PlayApiException(404, "purchaseToken bulunamadı");
        return PlaySubscription.parse(n.deepCopy());
    }

    @Override
    public void acknowledge(String packageName, String productId, String purchaseToken) throws PlayApiException {
        ObjectNode n = subscriptions.get(purchaseToken);
        if (n == null) throw new PlayApiException(404, "purchaseToken bulunamadı");
        n.put("acknowledgementState", "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED");
        acknowledged.add(purchaseToken);
    }

    /** {@code subscriptionsv2} yanıtı. {@code accountHash} ya da {@code linked} {@code null} ise alan konmaz. */
    public static ObjectNode sub(String state, String productId, String basePlanId, String orderId, Instant start, Instant expiry,
                                 boolean autoRenew, boolean acknowledged, String accountHash, String linked) {
        ObjectNode n = JSON.createObjectNode();
        n.put("kind", "androidpublisher#subscriptionPurchaseV2");
        n.put("regionCode", "TR");
        n.put("startTime", start.toString());
        n.put("subscriptionState", state);
        n.put("latestOrderId", orderId);
        n.put("acknowledgementState", acknowledged ? "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED" : "ACKNOWLEDGEMENT_STATE_PENDING");
        if (linked != null) n.put("linkedPurchaseToken", linked);
        if (accountHash != null) n.putObject("externalAccountIdentifiers").put("obfuscatedExternalAccountId", accountHash);
        n.putObject("testPurchase");
        ObjectNode li = n.putArray("lineItems").addObject();
        li.put("productId", productId);
        li.put("expiryTime", expiry.toString());
        li.put("latestSuccessfulOrderId", orderId);
        li.putObject("autoRenewingPlan").put("autoRenewEnabled", autoRenew);
        li.putObject("offerDetails").put("basePlanId", basePlanId).putArray("offerTags");
        return n;
    }
}
