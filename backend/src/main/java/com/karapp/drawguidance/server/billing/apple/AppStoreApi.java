package com.karapp.drawguidance.server.billing.apple;

import java.time.Instant;
import java.util.UUID;

/**
 * v50 App Store'un sunucudaki arayüzü: Apple imzalı verinin (JWS) doğrulanması ve App Store Server API. Gerçek
 * uygulama {@link LibraryAppStoreApi} (Apple'ın resmi kütüphanesi), testlerde sahte uygulama kullanılır.
 *
 * <p>Bu arayüzün döndürdüğü her değer Apple'ın imzası doğrulanmış veriden gelir; doğrulanamayan veri
 * {@link AppStoreApiException} ile reddedilir.
 */
public interface AppStoreApi {

    /** App Store Server API anahtarı yüklü mü (yoksa doğrulama 503 {@code billing_not_configured}). */
    boolean configured();

    /**
     * Uygulamanın gönderdiği imzalı işlemi ({@code Transaction.jwsRepresentation}) doğrular: Apple kök sertifikasına
     * kadar zincir, imza, paket kimliği ve ortam.
     */
    AppleTransaction verifyTransaction(String signedTransaction) throws AppStoreApiException;

    /**
     * Aboneliğin App Store'daki güncel durumu ({@code GET /inApps/v1/subscriptions/{transactionId}}).
     * {@code originalTransactionId}'ye ait son işlem ve yenileme bilgisi döner.
     */
    AppleStatus subscriptionStatus(String originalTransactionId, boolean sandbox) throws AppStoreApiException;

    /** App Store Server Notifications V2 gövdesindeki {@code signedPayload}'u doğrular ve çözer. */
    AppleNotification verifyNotification(String signedPayload) throws AppStoreApiException;

    /** Doğrulanmış işlem ({@code JWSTransactionDecodedPayload}'un kullandığımız alanları). */
    record AppleTransaction(String transactionId, String originalTransactionId, String bundleId, String productId,
                            String subscriptionGroupId, UUID appAccountToken, Instant purchaseDate, Instant originalPurchaseDate,
                            Instant expiresDate, Instant revocationDate, Integer revocationReason, boolean upgraded,
                            boolean sandbox, String storefront, String type, String transactionReason) {}

    /** Doğrulanmış yenileme bilgisi ({@code JWSRenewalInfoDecodedPayload}). */
    record AppleRenewal(String autoRenewProductId, Boolean autoRenew, Integer expirationIntent, Boolean inBillingRetry,
                        Instant gracePeriodExpiresDate, Instant renewalDate) {}

    /**
     * Aboneliğin durumu. {@code status}: Apple'ın değeri (1 etkin, 2 süresi doldu, 3 ödeme yeniden deneniyor, 4 ek süre,
     * 5 geri alındı); bilinmiyorsa {@code null}. {@code renewal} olmayabilir.
     */
    record AppleStatus(Integer status, AppleTransaction transaction, AppleRenewal renewal) {}

    /**
     * Doğrulanmış bildirim. {@code status} yalnız işlem içeren bildirimlerde dolu (ör. {@code TEST}'te {@code null}).
     */
    record AppleNotification(String notificationUuid, String type, String subtype, boolean sandbox, Instant signedDate,
                             AppleStatus status) {}
}
