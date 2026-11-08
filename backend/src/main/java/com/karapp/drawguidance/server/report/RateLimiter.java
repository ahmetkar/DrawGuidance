package com.karapp.drawguidance.server.report;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * v37 bellekte basit istek sınırı (sabit pencere): her anahtar (ör. {@code ip:1.2.3.4}, {@code install:<uuid>}) için
 * pencere başına en çok {@code limit} istek. Sunucu başınadır (birden çok sunucuda sınır sunucu sayısıyla çarpılır),
 * yeniden başlatınca sıfırlanır; kötüye kullanımı yavaşlatmak için yeterli.
 *
 * <p>Bellek sınırlı: süresi geçen pencereler ara ara temizlenir; temizlikten sonra da {@link #MAX_KEYS} dolu ise
 * yeni anahtarlar reddedilir (saldırı sırasında var olan sayaçlar sıfırlanmaz).
 */
@Component
public class RateLimiter {
    /** Sonuç: izin verildi mi, verilmediyse kaç saniye sonra yeniden denenebilir ({@code Retry-After}). */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private record Window(long start, int count) {}

    static final int MAX_KEYS = 100_000;
    private static final int CLEANUP_EVERY = 1024;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final Clock clock;

    public RateLimiter() { this(Clock.systemUTC()); }

    RateLimiter(Clock clock) { this.clock = clock; }

    public Decision acquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long span = Math.max(1, window.toMillis());
        if (calls.incrementAndGet() % CLEANUP_EVERY == 0 || windows.size() >= MAX_KEYS) cleanup(now, span);
        if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) return new Decision(false, Math.max(1, span / 1000));
        Window w = windows.compute(key, (k, old) -> old == null || now - old.start() >= span ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (w.count() <= limit) return new Decision(true, 0);
        return new Decision(false, Math.max(1, (w.start() + span - now + 999) / 1000));
    }

    /** Süresi geçen pencereleri siler. */
    void cleanup(long now, long span) { windows.values().removeIf(w -> now - w.start() >= span); }

    int size() { return windows.size(); }
}
