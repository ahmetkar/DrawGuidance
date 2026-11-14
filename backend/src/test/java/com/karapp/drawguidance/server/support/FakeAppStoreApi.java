package com.karapp.drawguidance.server.support;

import com.karapp.drawguidance.server.billing.apple.AppStoreApi;
import com.karapp.drawguidance.server.billing.apple.AppStoreApiException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v50 testlerde App Store: "imzalı" veri burada yalnız bir anahtardır ({@link #signed} / {@link #notifications}
 * tablolarında yoksa imza geçersiz sayılır), abonelik durumları {@link #statuses}'ta (originalTransactionId → durum).
 */
public class FakeAppStoreApi implements AppStoreApi {
    public volatile boolean configured = true;
    public final Map<String, AppleTransaction> signed = new ConcurrentHashMap<>();
    public final Map<String, AppleStatus> statuses = new ConcurrentHashMap<>();
    public final Map<String, AppleNotification> notifications = new ConcurrentHashMap<>();
    public final Set<String> failing = ConcurrentHashMap.newKeySet();

    @Override
    public boolean configured() { return configured; }

    @Override
    public AppleTransaction verifyTransaction(String signedTransaction) throws AppStoreApiException {
        AppleTransaction t = signed.get(signedTransaction);
        if (t == null) throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "imza geçersiz");
        return t;
    }

    @Override
    public AppleStatus subscriptionStatus(String originalTransactionId, boolean sandbox) throws AppStoreApiException {
        if (!configured) throw new AppStoreApiException(AppStoreApiException.Kind.NOT_CONFIGURED, "anahtar yok");
        if (failing.contains(originalTransactionId)) throw new AppStoreApiException(AppStoreApiException.Kind.UNAVAILABLE, "geçici hata");
        AppleStatus s = statuses.get(originalTransactionId);
        if (s == null) throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "bulunamadı");
        return s;
    }

    @Override
    public AppleNotification verifyNotification(String signedPayload) throws AppStoreApiException {
        AppleNotification n = notifications.get(signedPayload);
        if (n == null) throw new AppStoreApiException(AppStoreApiException.Kind.INVALID, "imza geçersiz");
        return n;
    }
}
