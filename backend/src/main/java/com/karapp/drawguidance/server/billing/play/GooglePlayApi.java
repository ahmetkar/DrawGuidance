package com.karapp.drawguidance.server.billing.play;

/**
 * v39 Google Play Developer API'nin kullandığımız iki çağrısı. Üretimde {@link HttpGooglePlayApi} (hizmet hesabı
 * JWT'siyle düz HTTP), testlerde sahte uygulama.
 */
public interface GooglePlayApi {

    /** Kimlik bilgileri yüklü mü. Değilse hiçbir doğrulama yapılmaz ({@code billing_not_configured}). */
    boolean configured();

    /**
     * {@code GET applications/{packageName}/purchases/subscriptionsv2/tokens/{token}}.
     *
     * @throws PlayApiException jeton geçersiz/bilinmiyor ({@link PlayApiException#invalidToken()}) ya da Play'e ulaşılamadı
     */
    PlaySubscription getSubscription(String packageName, String purchaseToken) throws PlayApiException;

    /** {@code POST applications/{packageName}/purchases/subscriptions/{productId}/tokens/{token}:acknowledge}. */
    void acknowledge(String packageName, String productId, String purchaseToken) throws PlayApiException;
}
