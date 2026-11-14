package com.karapp.drawguidance.server.billing.apple;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * v50 App Store abonelikleri ({@code drawguidance.billing.apple.*}).
 *
 * <p>Anahtar dosyası, Key ID ve Issuer ID verilmezse App Store doğrulaması yapılandırılmamış sayılır:
 * {@code POST /api/v1/subscriptions/verify-apple} 503 {@code billing_not_configured} döner (sahte doğrulama yapılmaz).
 * Anahtar dosyasının içeriği bir sırdır: burada yalnız yolu tutulur, içerik günlüğe ya da yanıta yazılmaz.
 *
 * @param bundleId             uygulamanın paket kimliği (imzalı işlemdeki {@code bundleId} bununla aynı olmalı)
 * @param appAppleId           App Store Connect'teki sayısal Apple ID (Uygulama Bilgileri → Apple ID). Üretim
 *                             ortamındaki imzalı verinin doğrulaması için zorunlu; yoksa yalnız Sandbox doğrulanır
 * @param issuerId             App Store Connect → Kullanıcılar ve Erişim → Entegrasyonlar → Uygulama İçi Satın Alma:
 *                             Issuer ID
 * @param keyId                aynı sayfadaki anahtarın Key ID'si
 * @param privateKeyFile       o anahtarın {@code SubscriptionKey_XXXX.p8} dosyasının yolu (sır; depoya konmaz)
 * @param productIds           Premium planına bağlı App Store ürün kimlikleri
 * @param allowSandbox         Sandbox (TestFlight, App Review, Xcode dışı test) satın almaları kabul edilsin mi. Apple,
 *                             App Review'un üretim sunucusunda Sandbox satın alma yaptığını söyler: üretimde de
 *                             {@code true} kalmalı. Sandbox kayıtları {@code testPurchase=true} olarak saklanır
 * @param onlineChecks         imza zincirinde OCSP iptal denetimi (Apple'ın sunucusuna sorar); testlerde kapatılır
 * @param requireAccountToken  {@code true}: işlemde {@code appAccountToken} yoksa doğrulama reddedilir. {@code false}
 *                             (öntanımlı): iOS'ta girişsiz satın alma serbesttir; ilk doğrulayan hesaba bağlanır
 */
@ConfigurationProperties(prefix = "drawguidance.billing.apple")
public record AppleBillingProperties(String bundleId, Long appAppleId, String issuerId, String keyId, String privateKeyFile,
                                     List<String> productIds, Boolean allowSandbox, Boolean onlineChecks,
                                     Boolean requireAccountToken) {
    public static final String DEFAULT_BUNDLE = "com.karapp.drawguidance";
    public static final String DEFAULT_PRODUCT = "com.karapp.drawguidance.premium.monthly";

    public AppleBillingProperties {
        if (bundleId == null || bundleId.isBlank()) bundleId = DEFAULT_BUNDLE;
        if (appAppleId != null && appAppleId <= 0) appAppleId = null;
        issuerId = blankToNull(issuerId);
        keyId = blankToNull(keyId);
        privateKeyFile = blankToNull(privateKeyFile);
        productIds = productIds == null ? List.of() : productIds.stream().filter(p -> p != null && !p.isBlank()).map(String::trim).toList();
        if (productIds.isEmpty()) productIds = List.of(DEFAULT_PRODUCT);
        if (allowSandbox == null) allowSandbox = true;
        if (onlineChecks == null) onlineChecks = true;
        if (requireAccountToken == null) requireAccountToken = false;
    }

    /** Sunucu API anahtarı verildi mi (üçü de). */
    public boolean apiKeyGiven() { return issuerId != null && keyId != null && privateKeyFile != null; }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
