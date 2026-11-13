package com.karapp.drawguidance.server.billing.apple;

import com.karapp.drawguidance.server.billing.SubscriptionStatus;
import com.karapp.drawguidance.server.billing.TransactionType;
import com.karapp.drawguidance.server.billing.play.PlaySubscription;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * v50 App Store durumunun sunucunun ortak abonelik durumuna ({@link PlaySubscription}: mağazadan bağımsız alanlar)
 * çevrilmesi, hesap bağlama jetonu ve bildirim türleri.
 *
 * <p>Abonelik belgesinde App Store aboneliğinin "satın alma jetonu" {@code apple:<originalTransactionId>}'dir: yenileme
 * ve aynı gruptaki plan değişikliklerinde değişmez, {@code purchaseToken} benzersiz dizini iki hesaba bağlanmayı önler.
 */
public final class AppleStates {
    private AppleStates() {}

    public static final String TOKEN_PREFIX = "apple:";
    public static final String STORE_APP_STORE = "APP_STORE", STORE_GOOGLE_PLAY = "GOOGLE_PLAY";

    public static String token(String originalTransactionId) { return TOKEN_PREFIX + originalTransactionId; }

    public static boolean isAppleToken(String token) { return token != null && token.startsWith(TOKEN_PREFIX); }

    public static String originalTransactionId(String token) { return isAppleToken(token) ? token.substring(TOKEN_PREFIX.length()) : null; }

    public static String storeOf(String token) { return isAppleToken(token) ? STORE_APP_STORE : STORE_GOOGLE_PLAY; }

    /**
     * Uygulamanın {@code Product.PurchaseOption.appAccountToken} değeri: Firebase uid'sinin UTF-8 SHA-256 özetinin ilk
     * 16 baytı, RFC 9562 sürüm 8 (özel) UUID biçiminde. iOS'taki {@code AppAccountToken.forUid} ile birebir aynı.
     */
    public static UUID appAccountToken(String uid) {
        byte[] h;
        try {
            h = MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        h[6] = (byte) ((h[6] & 0x0f) | 0x80);
        h[8] = (byte) ((h[8] & 0x3f) | 0x80);
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (h[i] & 0xff);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (h[i] & 0xff);
        return new UUID(msb, lsb);
    }

    /**
     * App Store durumu → ortak durum.
     *
     * <ul>
     *   <li>Geri alınmış (iade, aile paylaşımından çıkma) işlem ya da 5 REVOKED: {@code EXPIRED}, neden SYSTEM.</li>
     *   <li>1 ACTIVE: otomatik yenileme kapalıysa {@code CANCELED} (dönem sonuna kadar hak sürer, Play'deki gibi),
     *       açıksa {@code ACTIVE}.</li>
     *   <li>4 BILLING_GRACE_PERIOD: {@code IN_GRACE_PERIOD}, bitiş ek sürenin sonu.</li>
     *   <li>3 BILLING_RETRY: {@code ON_HOLD} (ek süre yok ya da bitti; hak yok).</li>
     *   <li>2 EXPIRED: {@code EXPIRED}; neden {@code expirationIntent}'ten.</li>
     *   <li>Durum bilinmiyorsa bitiş zamanından: gelecekteyse {@code ACTIVE}, değilse {@code EXPIRED}.</li>
     * </ul>
     */
    public static PlaySubscription toState(AppStoreApi.AppleStatus s, JsonNode raw, Instant now) {
        AppStoreApi.AppleTransaction t = s.transaction();
        AppStoreApi.AppleRenewal r = s.renewal();
        Boolean autoRenewFlag = r == null ? null : r.autoRenew();
        boolean autoRenew = Boolean.TRUE.equals(autoRenewFlag);
        Integer code = s.status();
        SubscriptionStatus status;
        String cancelReason = null;
        Instant expiry = t.expiresDate();
        if (t.revocationDate() != null || (code != null && code == 5)) {
            status = SubscriptionStatus.EXPIRED;
            cancelReason = PlaySubscription.CANCEL_SYSTEM;
        } else if (code == null) {
            status = expiry != null && expiry.isAfter(now) ? (Boolean.FALSE.equals(autoRenewFlag) ? SubscriptionStatus.CANCELED : SubscriptionStatus.ACTIVE)
                    : SubscriptionStatus.EXPIRED;
        } else {
            status = switch (code) {
                case 1 -> autoRenewFlag != null && !autoRenewFlag ? SubscriptionStatus.CANCELED : SubscriptionStatus.ACTIVE;
                case 2 -> SubscriptionStatus.EXPIRED;
                case 3 -> SubscriptionStatus.ON_HOLD;
                case 4 -> SubscriptionStatus.IN_GRACE_PERIOD;
                default -> SubscriptionStatus.PENDING;
            };
        }
        if (status == SubscriptionStatus.IN_GRACE_PERIOD && r != null && r.gracePeriodExpiresDate() != null) expiry = r.gracePeriodExpiresDate();
        if (status == SubscriptionStatus.CANCELED) cancelReason = PlaySubscription.CANCEL_USER;
        if (status == SubscriptionStatus.EXPIRED && cancelReason == null && r != null && r.expirationIntent() != null) {
            cancelReason = switch (r.expirationIntent()) {
                case 1, 3 -> PlaySubscription.CANCEL_USER;
                default -> PlaySubscription.CANCEL_SYSTEM;
            };
        }
        return new PlaySubscription("APPLE_STATUS_" + (code == null ? "UNKNOWN" : code), status, t.productId(), null, null,
                t.originalPurchaseDate() != null ? t.originalPurchaseDate() : t.purchaseDate(), expiry, autoRenew, true,
                t.transactionId(), null, null, t.storefront(), t.sandbox(), raw, null, cancelReason, null);
    }

    /** App Store Server Notifications V2 türü (ve alt türü) → hareket türü. */
    public static TransactionType transactionType(String type, String subtype) {
        if (type == null) return TransactionType.UPDATE;
        return switch (type) {
            case "SUBSCRIBED" -> "RESUBSCRIBE".equals(subtype) ? TransactionType.RESTART : TransactionType.PURCHASE;
            case "DID_RENEW" -> "BILLING_RECOVERY".equals(subtype) ? TransactionType.RECOVER : TransactionType.RENEWAL;
            case "DID_CHANGE_RENEWAL_STATUS" -> "AUTO_RENEW_DISABLED".equals(subtype) ? TransactionType.CANCEL : TransactionType.RESTART;
            case "DID_FAIL_TO_RENEW" -> "GRACE_PERIOD".equals(subtype) ? TransactionType.GRACE_PERIOD : TransactionType.ON_HOLD;
            case "GRACE_PERIOD_EXPIRED" -> TransactionType.ON_HOLD;
            case "EXPIRED" -> TransactionType.EXPIRE;
            case "REFUND", "REVOKE" -> TransactionType.REVOKE;
            case "PRICE_INCREASE", "PRICE_CHANGE" -> TransactionType.PRICE_CHANGE;
            default -> TransactionType.UPDATE;
        };
    }
}
