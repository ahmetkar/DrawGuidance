package com.karapp.drawguidance.server.billing;

/**
 * v39 abonelik durumu (Play {@code subscriptionsv2} {@code subscriptionState} karşılığı).
 *
 * <ul>
 *   <li>{@code ACTIVE}: ödenmiş, yenilenecek. Hak var.</li>
 *   <li>{@code IN_GRACE_PERIOD}: yenileme ödemesi alınamadı, ek süre (Play'in ayarı, en çok 30 gün). Hak var.</li>
 *   <li>{@code CANCELED}: kullanıcı iptal etti ama ödenen dönem sürüyor; {@code expiryTime}'a kadar hak var.</li>
 *   <li>{@code ON_HOLD}: ek süre bitti, ödeme bekleniyor (hesap askıda). Hak yok.</li>
 *   <li>{@code PAUSED}: kullanıcı duraklattı. Hak yok.</li>
 *   <li>{@code PENDING}: ödeme bekleniyor (ör. nakit/kod). Hak yok.</li>
 *   <li>{@code EXPIRED}: süresi doldu, geri alındı (revoke) ya da bekleyen satın alma iptal edildi. Hak yok.</li>
 * </ul>
 */
public enum SubscriptionStatus {
    ACTIVE, IN_GRACE_PERIOD, ON_HOLD, PAUSED, CANCELED, EXPIRED, PENDING;

    /** Play {@code SUBSCRIPTION_STATE_*} değerinden; bilinmeyen değer hak vermeyen {@code PENDING} sayılır. */
    public static SubscriptionStatus fromPlay(String state) {
        if (state == null) return PENDING;
        return switch (state) {
            case "SUBSCRIPTION_STATE_ACTIVE" -> ACTIVE;
            case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> IN_GRACE_PERIOD;
            case "SUBSCRIPTION_STATE_ON_HOLD" -> ON_HOLD;
            case "SUBSCRIPTION_STATE_PAUSED" -> PAUSED;
            case "SUBSCRIPTION_STATE_CANCELED" -> CANCELED;
            case "SUBSCRIPTION_STATE_EXPIRED", "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED" -> EXPIRED;
            default -> PENDING;
        };
    }

    /** Bu durumda (bitiş zamanı geçmemişse) Premium hakkı var mı. */
    public boolean grantsAccess() { return this == ACTIVE || this == IN_GRACE_PERIOD || this == CANCELED; }
}
