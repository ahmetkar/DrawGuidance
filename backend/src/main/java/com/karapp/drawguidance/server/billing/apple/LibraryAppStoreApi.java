package com.karapp.drawguidance.server.billing.apple;

import com.apple.itunes.storekit.client.APIError;
import com.apple.itunes.storekit.client.APIException;
import com.apple.itunes.storekit.client.AppStoreServerAPIClient;
import com.apple.itunes.storekit.model.AutoRenewStatus;
import com.apple.itunes.storekit.model.Data;
import com.apple.itunes.storekit.model.Environment;
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.LastTransactionsItem;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.apple.itunes.storekit.model.StatusResponse;
import com.apple.itunes.storekit.model.SubscriptionGroupIdentifierItem;
import com.apple.itunes.storekit.verification.SignedDataVerifier;
import com.apple.itunes.storekit.verification.VerificationException;
import com.apple.itunes.storekit.verification.VerificationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * v50 {@link AppStoreApi}'nin Apple'ın resmi kütüphanesiyle ({@code app-store-server-library}) uygulaması.
 *
 * <ul>
 *   <li>İmza doğrulaması: JWS başlığındaki {@code x5c} zinciri pakete konan Apple Root CA - G3'e kadar doğrulanır, ara
 *       ve yaprak sertifikaların Apple OID'leri, imza, {@code bundleId}, ortam ve (üretimde) {@code appAppleId}
 *       denetlenir; {@code onlineChecks} açıksa OCSP ile iptal de sorulur.</li>
 *   <li>Ortam: imzalı verideki {@code environment} önce imzasız okunur yalnız doğru doğrulayıcıyı seçmek için; asıl
 *       denetimi o doğrulayıcı yapar (ortam tutmazsa red).</li>
 *   <li>Sunucu API: ES256 jetonu kütüphane üretir; üretim ve Sandbox için ayrı istemci.</li>
 * </ul>
 */
public class LibraryAppStoreApi implements AppStoreApi {
    private static final Logger log = LoggerFactory.getLogger(LibraryAppStoreApi.class);
    /** Pakete konan Apple kök sertifikaları (herkese açık; https://www.apple.com/certificateauthority/). */
    static final List<String> ROOTS = List.of("apple/AppleRootCA-G3.cer");

    private final AppleBillingProperties props;
    private final ObjectMapper json;
    private final SignedDataVerifier production;
    private final SignedDataVerifier sandbox;
    private final AppStoreServerAPIClient productionClient;
    private final AppStoreServerAPIClient sandboxClient;

    LibraryAppStoreApi(AppleBillingProperties props, ObjectMapper json, SignedDataVerifier production, SignedDataVerifier sandbox,
                       AppStoreServerAPIClient productionClient, AppStoreServerAPIClient sandboxClient) {
        this.props = props;
        this.json = json;
        this.production = production;
        this.sandbox = sandbox;
        this.productionClient = productionClient;
        this.sandboxClient = sandboxClient;
    }

    /** Ayarlardan: anahtar dosyası okunamazsa API yapılandırılmamış sayılır (imza doğrulaması yine çalışır). */
    public static LibraryAppStoreApi fromProperties(AppleBillingProperties props, ObjectMapper json) {
        SignedDataVerifier prod = props.appAppleId() == null ? null : verifier(props, Environment.PRODUCTION);
        SignedDataVerifier sb = props.allowSandbox() ? verifier(props, Environment.SANDBOX) : null;
        AppStoreServerAPIClient prodClient = null, sbClient = null;
        if (props.apiKeyGiven()) {
            try {
                String key = Files.readString(Path.of(props.privateKeyFile()), StandardCharsets.UTF_8);
                prodClient = new AppStoreServerAPIClient(key, props.keyId(), props.issuerId(), props.bundleId(), Environment.PRODUCTION);
                sbClient = new AppStoreServerAPIClient(key, props.keyId(), props.issuerId(), props.bundleId(), Environment.SANDBOX);
            } catch (IOException | RuntimeException e) {
                // İçerik değil yalnız hata türü yazılır
                log.error("App Store anahtar dosyası okunamadı ({}); App Store doğrulaması kapalı", e.getClass().getSimpleName());
            }
        }
        if (prodClient != null && prod == null) {
            log.warn("drawguidance.billing.apple.app-apple-id verilmedi: üretim (gerçek) App Store satın almaları doğrulanamaz");
        }
        return new LibraryAppStoreApi(props, json, prod, sb, prodClient, sbClient);
    }

    private static SignedDataVerifier verifier(AppleBillingProperties props, Environment env) {
        Set<InputStream> roots = new java.util.HashSet<>();
        try {
            for (String r : ROOTS) {
                InputStream in = LibraryAppStoreApi.class.getClassLoader().getResourceAsStream(r);
                if (in == null) throw new IllegalStateException("Apple kök sertifikası pakette yok: " + r);
                roots.add(in);
            }
            return new SignedDataVerifier(roots, props.bundleId(), props.appAppleId(), env, props.onlineChecks());
        } finally {
            for (InputStream in : roots) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    @Override
    public boolean configured() { return productionClient != null; }

    // ---------------------------------------------------------------- imzalı veri

    @Override
    public AppleTransaction verifyTransaction(String signedTransaction) throws AppStoreApiException {
        SignedDataVerifier v = verifierFor(peekEnvironment(signedTransaction, false));
        try {
            return transaction(v.verifyAndDecodeTransaction(signedTransaction));
        } catch (VerificationException e) {
            throw verificationFailed(e);
        }
    }

    @Override
    public AppleNotification verifyNotification(String signedPayload) throws AppStoreApiException {
        SignedDataVerifier v = verifierFor(peekEnvironment(signedPayload, true));
        try {
            ResponseBodyV2DecodedPayload p = v.verifyAndDecodeNotification(signedPayload);
            Data d = p.getData();
            AppleStatus status = null;
            boolean sb = false;
            if (d != null) {
                sb = d.getEnvironment() == Environment.SANDBOX;
                if (d.getSignedTransactionInfo() != null) {
                    AppleTransaction t = transaction(v.verifyAndDecodeTransaction(d.getSignedTransactionInfo()));
                    AppleRenewal r = d.getSignedRenewalInfo() == null ? null : renewal(v.verifyAndDecodeRenewalInfo(d.getSignedRenewalInfo()));
                    status = new AppleStatus(d.getRawStatus(), t, r);
                }
            }
            return new AppleNotification(p.getNotificationUUID(), p.getRawNotificationType(), p.getRawSubtype(), sb,
                    millis(p.getSignedDate()), status);
        } catch (VerificationException e) {
            throw verificationFailed(e);
        }
    }

    // ---------------------------------------------------------------- sunucu API

    @Override
    public AppleStatus subscriptionStatus(String originalTransactionId, boolean sandboxEnv) throws AppStoreApiException {
        AppStoreServerAPIClient client = sandboxEnv ? sandboxClient : productionClient;
        if (client == null) throw new AppStoreApiException(AppStoreApiException.Kind.NOT_CONFIGURED, "App Store Server API anahtarı yok");
        if (sandboxEnv && !props.allowSandbox()) throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "Sandbox kapalı");
        SignedDataVerifier v = verifierFor(sandboxEnv ? Environment.SANDBOX : Environment.PRODUCTION);
        StatusResponse res;
        try {
            res = client.getAllSubscriptionStatuses(originalTransactionId, null);
        } catch (APIException e) {
            throw apiFailed(e);
        } catch (IOException e) {
            throw new AppStoreApiException(AppStoreApiException.Kind.UNAVAILABLE, "App Store'a ulaşılamadı: " + e.getClass().getSimpleName(), e);
        }
        try {
            if (res.getData() != null) {
                for (SubscriptionGroupIdentifierItem g : res.getData()) {
                    if (g.getLastTransactions() == null) continue;
                    for (LastTransactionsItem item : g.getLastTransactions()) {
                        if (!originalTransactionId.equals(item.getOriginalTransactionId())) continue;
                        AppleTransaction t = transaction(v.verifyAndDecodeTransaction(item.getSignedTransactionInfo()));
                        AppleRenewal r = item.getSignedRenewalInfo() == null ? null : renewal(v.verifyAndDecodeRenewalInfo(item.getSignedRenewalInfo()));
                        return new AppleStatus(item.getRawStatus(), t, r);
                    }
                }
            }
        } catch (VerificationException e) {
            throw verificationFailed(e);
        }
        throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "App Store bu aboneliği bulamadı");
    }

    // ---------------------------------------------------------------- yardımcılar

    private SignedDataVerifier verifierFor(Environment env) throws AppStoreApiException {
        if (env == Environment.SANDBOX) {
            if (!props.allowSandbox()) throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "Sandbox satın almaları kabul edilmiyor");
            return sandbox;
        }
        if (env == Environment.PRODUCTION) {
            if (production == null) {
                throw new AppStoreApiException(AppStoreApiException.Kind.NOT_CONFIGURED, "Üretim doğrulaması için app-apple-id gerekli");
            }
            return production;
        }
        // Xcode/yerel StoreKit testi Apple tarafından imzalanmaz
        throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "Desteklenmeyen ortam: " + env);
    }

    /** JWS yükündeki ortamı imzasız okur (yalnız doğrulayıcı seçimi için). */
    Environment peekEnvironment(String jws, boolean notification) throws AppStoreApiException {
        if (jws == null) throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "İmzalı veri yok");
        String[] parts = jws.trim().split("\\.");
        if (parts.length != 3) throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "JWS biçiminde değil");
        try {
            JsonNode n = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            String env = notification ? n.path("data").path("environment").asText(null) : n.path("environment").asText(null);
            if (env == null && notification) env = n.path("summary").path("environment").asText(null);
            return Environment.fromValue(env);
        } catch (IOException | IllegalArgumentException e) {
            throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "JWS yükü okunamadı");
        }
    }

    private static AppleTransaction transaction(JWSTransactionDecodedPayload p) {
        return new AppleTransaction(p.getTransactionId(), p.getOriginalTransactionId(), p.getBundleId(), p.getProductId(),
                p.getSubscriptionGroupIdentifier(), p.getAppAccountToken(), millis(p.getPurchaseDate()), millis(p.getOriginalPurchaseDate()),
                millis(p.getExpiresDate()), millis(p.getRevocationDate()), p.getRawRevocationReason(),
                Boolean.TRUE.equals(p.getIsUpgraded()), p.getEnvironment() == Environment.SANDBOX, p.getStorefront(), p.getRawType(),
                p.getRawTransactionReason());
    }

    private static AppleRenewal renewal(JWSRenewalInfoDecodedPayload p) {
        Boolean auto = p.getAutoRenewStatus() == null ? null : p.getAutoRenewStatus() == AutoRenewStatus.ON;
        return new AppleRenewal(p.getAutoRenewProductId(), auto, p.getRawExpirationIntent(), p.getIsInBillingRetryPeriod(),
                millis(p.getGracePeriodExpiresDate()), millis(p.getRenewalDate()));
    }

    private static Instant millis(Long ms) { return ms == null ? null : Instant.ofEpochMilli(ms); }

    private static AppStoreApiException verificationFailed(VerificationException e) {
        AppStoreApiException.Kind kind = e.getStatus() == VerificationStatus.RETRYABLE_VERIFICATION_FAILURE
                ? AppStoreApiException.Kind.UNAVAILABLE : AppStoreApiException.Kind.INVALID;
        return new AppStoreApiException(kind, "Apple imzası doğrulanamadı: " + e.getStatus(), e);
    }

    private static AppStoreApiException apiFailed(APIException e) {
        APIError err = e.getApiError();
        int http = e.getHttpStatusCode();
        if (http == 401) return new AppStoreApiException(AppStoreApiException.Kind.NOT_CONFIGURED, "App Store anahtarı reddedildi (401)", e);
        if (http == 404 || err == APIError.TRANSACTION_ID_NOT_FOUND || err == APIError.INVALID_TRANSACTION_ID
                || err == APIError.ORIGINAL_TRANSACTION_ID_NOT_FOUND || err == APIError.INVALID_ORIGINAL_TRANSACTION_ID) {
            return new AppStoreApiException(AppStoreApiException.Kind.INVALID, "App Store işlemi tanımıyor (" + http + ")", e);
        }
        return new AppStoreApiException(AppStoreApiException.Kind.UNAVAILABLE, "App Store API hatası " + http, e);
    }
}
