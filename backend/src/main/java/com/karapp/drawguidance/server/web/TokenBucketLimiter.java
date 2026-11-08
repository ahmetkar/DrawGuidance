package com.karapp.drawguidance.server.web;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v40 bellekte jeton kovası: her anahtarın (ör. {@code deletion:1.2.3.4}) {@code capacity} jetonluk kovası vardır,
 * her istek bir jeton harcar; kova {@code refill} süresinde boştan tama düzgün hızla dolar (5/saat: 12 dakikada bir
 * jeton). Sunucu başınadır ve yeniden başlatınca sıfırlanır; herkese açık formların kötüye kullanımını yavaşlatmak için
 * yeterli.
 *
 * <p>Bellek sınırlı: tamamen dolmuş kovalar ara ara silinir (dolu kova yeni kovayla aynıdır); silindikten sonra da
 * {@link #MAX_KEYS} doluysa yeni anahtarlar reddedilir.
 */
public class TokenBucketLimiter {
    /** Sonuç: izin verildi mi; verilmediyse kaç saniye sonra bir jeton olur ({@code Retry-After}). */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private record Bucket(double tokens, long updatedAt) {}

    static final int MAX_KEYS = 100_000;
    private static final int CLEANUP_EVERY = 1024;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final int capacity;
    private final double perMilli;
    private final Clock clock;

    public TokenBucketLimiter(int capacity, Duration refill, Clock clock) {
        this.capacity = Math.max(1, capacity);
        this.perMilli = this.capacity / (double) Math.max(1, refill.toMillis());
        this.clock = clock;
    }

    public Decision acquire(String key) {
        long now = clock.millis();
        if (calls.incrementAndGet() % CLEANUP_EVERY == 0 || buckets.size() >= MAX_KEYS) cleanup(now);
        if (buckets.size() >= MAX_KEYS && !buckets.containsKey(key)) return new Decision(false, retryAfter(0));
        boolean[] allowed = new boolean[1];
        Bucket b = buckets.compute(key, (k, old) -> {
            double tokens = old == null ? capacity : refilled(old, now);
            allowed[0] = tokens >= 1;
            return new Bucket(allowed[0] ? tokens - 1 : tokens, now);
        });
        return allowed[0] ? new Decision(true, 0) : new Decision(false, retryAfter(b.tokens()));
    }

    private double refilled(Bucket b, long now) {
        return Math.min(capacity, b.tokens() + Math.max(0, now - b.updatedAt()) * perMilli);
    }

    /** Bir jeton olana kadar geçecek süre (saniye, en az 1). */
    private long retryAfter(double tokens) {
        double missingMs = (1 - tokens) / perMilli;
        return Math.max(1, (long) Math.ceil(missingMs / 1000));
    }

    /** Tamamen dolmuş kovaları siler. */
    void cleanup(long now) { buckets.values().removeIf(b -> refilled(b, now) >= capacity); }

    int size() { return buckets.size(); }
}
