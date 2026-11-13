package com.karapp.drawguidance.server.billing;

import com.karapp.drawguidance.server.billing.apple.AppStoreApiException;
import com.karapp.drawguidance.server.billing.apple.AppleBillingProperties;
import com.karapp.drawguidance.server.billing.apple.LibraryAppStoreApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** v50 gerçek App Store uygulaması: pakete konan kök sertifikayla kurulur, imzasız/sahte veri reddedilir. */
class LibraryAppStoreApiTest {
    private final ObjectMapper json = new ObjectMapper();

    static AppleBillingProperties props(Long appAppleId, String keyFile) {
        return new AppleBillingProperties(null, appAppleId, keyFile == null ? null : "issuer", keyFile == null ? null : "KEYID", keyFile,
                null, null, false, null);
    }

    static String jws(String payloadJson) {
        Base64.Encoder b = Base64.getUrlEncoder().withoutPadding();
        return b.encodeToString("{\"alg\":\"ES256\",\"x5c\":[\"AAAA\",\"BBBB\",\"CCCC\"]}".getBytes(StandardCharsets.UTF_8)) + "."
                + b.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)) + "." + b.encodeToString(new byte[64]);
    }

    @Test
    void defaultsAreNotConfiguredButStillRejectForgedData() {
        AppleBillingProperties p = props(null, null);
        assertEquals(List.of(AppleBillingProperties.DEFAULT_PRODUCT), p.productIds());
        LibraryAppStoreApi api = LibraryAppStoreApi.fromProperties(p, json);
        assertFalse(api.configured());

        AppStoreApiException notJws = assertThrows(AppStoreApiException.class, () -> api.verifyTransaction("abc"));
        assertEquals(AppStoreApiException.Kind.INVALID, notJws.kind());

        // Sahte zincirli Sandbox işlemi: imza doğrulanamaz
        AppStoreApiException forged = assertThrows(AppStoreApiException.class,
                () -> api.verifyTransaction(jws("{\"environment\":\"Sandbox\",\"bundleId\":\"com.karapp.drawguidance\"}")));
        assertEquals(AppStoreApiException.Kind.INVALID, forged.kind());

        // Üretim verisi Apple ID olmadan doğrulanamaz (ayar eksik)
        AppStoreApiException prod = assertThrows(AppStoreApiException.class,
                () -> api.verifyTransaction(jws("{\"environment\":\"Production\"}")));
        assertEquals(AppStoreApiException.Kind.NOT_CONFIGURED, prod.kind());

        // Xcode'un yerel StoreKit testi Apple imzalı değildir
        AppStoreApiException xcode = assertThrows(AppStoreApiException.class,
                () -> api.verifyNotification(jws("{\"data\":{\"environment\":\"Xcode\"}}")));
        assertEquals(AppStoreApiException.Kind.INVALID, xcode.kind());
    }

    @Test
    void missingKeyFileLeavesApiUnconfigured() {
        LibraryAppStoreApi api = LibraryAppStoreApi.fromProperties(props(123L, "/yok/SubscriptionKey_X.p8"), json);
        assertFalse(api.configured());
        AppStoreApiException e = assertThrows(AppStoreApiException.class, () -> api.subscriptionStatus("1", false));
        assertEquals(AppStoreApiException.Kind.NOT_CONFIGURED, e.kind());
    }
}
