package com.karapp.drawguidance.server.billing;

/**
 * v39 {@code subscription_transactions.type}. Gerçek zamanlı bildirimde Play'in {@code notificationType} değerinden,
 * uygulamanın doğrulama isteğinde önceki ve yeni durumun farkından belirlenir.
 */
public enum TransactionType {
    /** 4 SUBSCRIPTION_PURCHASED ya da ilk doğrulama. */
    PURCHASE,
    /** 2 SUBSCRIPTION_RENEWED: yeni dönem ödendi (yeni {@code orderId}, ör. {@code GPA.1234-…..0}, {@code ..1}). */
    RENEWAL,
    /** 1 SUBSCRIPTION_RECOVERED: askı/ek süre/duraklatmadan dönüldü. */
    RECOVER,
    /** 3 SUBSCRIPTION_CANCELED: kullanıcı ya da sistem iptal etti (dönem sonuna kadar hak sürer). */
    CANCEL,
    /** 7 SUBSCRIPTION_RESTARTED: iptal geri alındı. */
    RESTART,
    /** 5 SUBSCRIPTION_ON_HOLD. */
    ON_HOLD,
    /** 6 SUBSCRIPTION_IN_GRACE_PERIOD. */
    GRACE_PERIOD,
    /** 10 SUBSCRIPTION_PAUSED. */
    PAUSE,
    /** 11 SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED. */
    PAUSE_SCHEDULE_CHANGED,
    /** 9 SUBSCRIPTION_DEFERRED: yenileme ertelendi. */
    DEFERRED,
    /** 8 SUBSCRIPTION_PRICE_CHANGE_CONFIRMED, 19 SUBSCRIPTION_PRICE_CHANGE_UPDATED. */
    PRICE_CHANGE,
    /** 12 SUBSCRIPTION_REVOKED ya da iade (voidedPurchaseNotification): hak hemen biter. */
    REVOKE,
    /** 13 SUBSCRIPTION_EXPIRED. */
    EXPIRE,
    /** 20 SUBSCRIPTION_PENDING_PURCHASE_CANCELED. */
    PENDING_CANCELED,
    /** Bilinmeyen (yeni) bildirim türü: yine durum yeniden okunur. */
    UPDATE;

    public static TransactionType fromNotification(int type) {
        return switch (type) {
            case 1 -> RECOVER;
            case 2 -> RENEWAL;
            case 3 -> CANCEL;
            case 4 -> PURCHASE;
            case 5 -> ON_HOLD;
            case 6 -> GRACE_PERIOD;
            case 7 -> RESTART;
            case 8, 19 -> PRICE_CHANGE;
            case 9 -> DEFERRED;
            case 10 -> PAUSE;
            case 11 -> PAUSE_SCHEDULE_CHANGED;
            case 12 -> REVOKE;
            case 13 -> EXPIRE;
            case 20 -> PENDING_CANCELED;
            default -> UPDATE;
        };
    }

    /** Play bildirim türünün adı (günlük ve {@code billing_webhook_events.notificationName} için). */
    public static String notificationName(int type) {
        return switch (type) {
            case 1 -> "SUBSCRIPTION_RECOVERED";
            case 2 -> "SUBSCRIPTION_RENEWED";
            case 3 -> "SUBSCRIPTION_CANCELED";
            case 4 -> "SUBSCRIPTION_PURCHASED";
            case 5 -> "SUBSCRIPTION_ON_HOLD";
            case 6 -> "SUBSCRIPTION_IN_GRACE_PERIOD";
            case 7 -> "SUBSCRIPTION_RESTARTED";
            case 8 -> "SUBSCRIPTION_PRICE_CHANGE_CONFIRMED";
            case 9 -> "SUBSCRIPTION_DEFERRED";
            case 10 -> "SUBSCRIPTION_PAUSED";
            case 11 -> "SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED";
            case 12 -> "SUBSCRIPTION_REVOKED";
            case 13 -> "SUBSCRIPTION_EXPIRED";
            case 19 -> "SUBSCRIPTION_PRICE_CHANGE_UPDATED";
            case 20 -> "SUBSCRIPTION_PENDING_PURCHASE_CANCELED";
            default -> "UNKNOWN_" + type;
        };
    }

    /** Doğrulamada önceki ve yeni durumdan hareket türü; değişiklik yoksa {@code null} (hareket yazılmaz). */
    static TransactionType derive(SubscriptionStatus before, String orderBefore, SubscriptionStatus after, String orderAfter) {
        if (before == null) return PURCHASE;
        boolean wasDown = before == SubscriptionStatus.ON_HOLD || before == SubscriptionStatus.IN_GRACE_PERIOD
                || before == SubscriptionStatus.PAUSED;
        if (orderAfter != null && !orderAfter.equals(orderBefore)) return wasDown ? RECOVER : RENEWAL;
        if (before == after) return null;
        return switch (after) {
            case CANCELED -> CANCEL;
            case EXPIRED -> EXPIRE;
            case ON_HOLD -> ON_HOLD;
            case IN_GRACE_PERIOD -> GRACE_PERIOD;
            case PAUSED -> PAUSE;
            case PENDING -> PURCHASE;
            case ACTIVE -> before == SubscriptionStatus.CANCELED ? RESTART : wasDown ? RECOVER : PURCHASE;
        };
    }
}
