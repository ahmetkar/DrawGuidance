package com.karapp.drawguidance.server.billing.play;

import com.karapp.drawguidance.server.billing.SubscriptionStatus;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * v39 Google Play {@code purchases.subscriptionsv2.get} yanıtının kullandığımız alanları ve ham JSON'u
 * ({@code raw}: {@code subscription_transactions.snapshot} olarak saklanır).
 *
 * <p>Birden çok satır kalemi varsa (yükseltme/düşürme sırasında) bitişi en geç olan alınır. {@code latestOrderId}:
 * kalemdeki {@code latestSuccessfulOrderId}, yoksa (eski yanıt) üst düzeydeki {@code latestOrderId}.
 */
public record PlaySubscription(String state, SubscriptionStatus status, String productId, String basePlanId, String offerId,
                               Instant startTime, Instant expiryTime, boolean autoRenewing, boolean acknowledged,
                               String latestOrderId, String linkedPurchaseToken, String obfuscatedAccountId,
                               String regionCode, boolean testPurchase, JsonNode raw,
                               Instant cancelTime, String cancelReason, Instant autoResumeTime) {

    /** v40 iptal nedenleri ({@code canceledStateContext}'in dolu alanından). */
    public static final String CANCEL_USER = "USER", CANCEL_SYSTEM = "SYSTEM", CANCEL_DEVELOPER = "DEVELOPER", CANCEL_REPLACEMENT = "REPLACEMENT";

    /** v39 alanlarıyla (iptal ve duraklatma bilgisi olmadan). */
    public PlaySubscription(String state, SubscriptionStatus status, String productId, String basePlanId, String offerId,
                            Instant startTime, Instant expiryTime, boolean autoRenewing, boolean acknowledged,
                            String latestOrderId, String linkedPurchaseToken, String obfuscatedAccountId,
                            String regionCode, boolean testPurchase, JsonNode raw) {
        this(state, status, productId, basePlanId, offerId, startTime, expiryTime, autoRenewing, acknowledged, latestOrderId,
                linkedPurchaseToken, obfuscatedAccountId, regionCode, testPurchase, raw, null, null, null);
    }

    /** Onay bayrağı değişmiş kopya. */
    public PlaySubscription acknowledgedCopy() {
        return new PlaySubscription(state, status, productId, basePlanId, offerId, startTime, expiryTime, autoRenewing, true,
                latestOrderId, linkedPurchaseToken, obfuscatedAccountId, regionCode, testPurchase, raw, cancelTime, cancelReason, autoResumeTime);
    }

    public static PlaySubscription parse(JsonNode n) {
        JsonNode item = null;
        Instant expiry = null;
        for (JsonNode li : n.path("lineItems")) {
            Instant e = time(li.path("expiryTime"));
            if (item == null || (e != null && (expiry == null || e.isAfter(expiry)))) {
                item = li;
                expiry = e;
            }
        }
        if (item == null) item = com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        String state = text(n.path("subscriptionState"));
        String order = text(item.path("latestSuccessfulOrderId"));
        if (order == null) order = text(n.path("latestOrderId"));
        return new PlaySubscription(state, SubscriptionStatus.fromPlay(state), text(item.path("productId")),
                text(item.path("offerDetails").path("basePlanId")), text(item.path("offerDetails").path("offerId")),
                time(n.path("startTime")), expiry,
                item.path("autoRenewingPlan").path("autoRenewEnabled").asBoolean(false),
                "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(text(n.path("acknowledgementState"))),
                order, text(n.path("linkedPurchaseToken")),
                text(n.path("externalAccountIdentifiers").path("obfuscatedExternalAccountId")),
                text(n.path("regionCode")), n.has("testPurchase"), n,
                cancelTime(n.path("canceledStateContext")), cancelReason(n.path("canceledStateContext")),
                time(n.path("pausedStateContext").path("autoResumeTime")));
    }

    /**
     * v40 iptal zamanı: Play yalnız kullanıcı iptalinde ({@code userInitiatedCancellation.cancelTime}) zaman verir;
     * öteki iptallerde {@code null}.
     */
    static Instant cancelTime(JsonNode ctx) {
        return time(ctx.path("userInitiatedCancellation").path("cancelTime"));
    }

    /** v40 iptal nedeni: USER, SYSTEM (ör. ödeme), DEVELOPER, REPLACEMENT (plan değişikliği); iptal yoksa {@code null}. */
    static String cancelReason(JsonNode ctx) {
        if (ctx == null || ctx.isMissingNode() || !ctx.isObject()) return null;
        if (ctx.has("userInitiatedCancellation")) return CANCEL_USER;
        if (ctx.has("systemInitiatedCancellation")) return CANCEL_SYSTEM;
        if (ctx.has("developerInitiatedCancellation")) return CANCEL_DEVELOPER;
        if (ctx.has("replacementCancellation")) return CANCEL_REPLACEMENT;
        return null;
    }

    private static String text(JsonNode n) {
        return n == null || !n.isTextual() || n.asText().isBlank() ? null : n.asText();
    }

    private static Instant time(JsonNode n) {
        String s = text(n);
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Onaylanmalı mı: henüz onaylanmamış ve ödenmiş (etkin ya da ek sürede). */
    public boolean needsAcknowledge() {
        return !acknowledged && (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.IN_GRACE_PERIOD);
    }
}
