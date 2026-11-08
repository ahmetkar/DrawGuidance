package com.karapp.drawguidance.server.report;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v37 bellekteki istek sınırı (veritabanı gerekmez): pencere, anahtarların ayrılığı, temizlik ve bellek sınırı. */
class RateLimiterTest {
    /** Elle ilerletilen saat. */
    static final class ManualClock extends Clock {
        Instant now = Instant.parse("2026-10-19T12:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void limitsPerKeyWithinAWindow() {
        ManualClock clock = new ManualClock();
        RateLimiter limiter = new RateLimiter(clock);
        Duration minute = Duration.ofMinutes(1);
        for (int i = 0; i < 3; i++) assertTrue(limiter.acquire("install:a", 3, minute).allowed(), "istek " + i);
        RateLimiter.Decision denied = limiter.acquire("install:a", 3, minute);
        assertFalse(denied.allowed());
        assertEquals(60, denied.retryAfterSeconds());
        assertTrue(limiter.acquire("install:b", 3, minute).allowed(), "başka anahtar etkilenmez");

        clock.advance(Duration.ofSeconds(45));
        RateLimiter.Decision later = limiter.acquire("install:a", 3, minute);
        assertFalse(later.allowed());
        assertEquals(15, later.retryAfterSeconds());

        clock.advance(Duration.ofSeconds(15));
        assertTrue(limiter.acquire("install:a", 3, minute).allowed(), "yeni pencere");
    }

    @Test
    void staleWindowsAreCleanedAndKeyCountIsBounded() {
        ManualClock clock = new ManualClock();
        RateLimiter limiter = new RateLimiter(clock);
        Duration minute = Duration.ofMinutes(1);
        for (int i = 0; i < RateLimiter.MAX_KEYS; i++) limiter.acquire("ip:" + i, 10, minute);
        assertEquals(RateLimiter.MAX_KEYS, limiter.size());
        // Dolu iken yeni anahtar reddedilir, var olan anahtar sayılmaya devam eder
        assertFalse(limiter.acquire("ip:yeni", 10, minute).allowed());
        assertTrue(limiter.acquire("ip:5", 10, minute).allowed());
        // Pencere geçince temizlenir
        clock.advance(Duration.ofMinutes(2));
        assertTrue(limiter.acquire("ip:yeni", 10, minute).allowed());
        assertEquals(1, limiter.size());
    }
}
