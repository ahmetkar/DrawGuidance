package com.karapp.drawguidance.server.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v40 jeton kovası (saatte 5) ve istemci adresi seçimi ({@code X-Forwarded-For} yalnız vekil arkasında). */
class TokenBucketLimiterTest {
    /** Elle ilerletilen saat. */
    static final class MutableClock extends Clock {
        long millis = 1_700_000_000_000L;

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }

    @Test
    void fiveThenRefillsOneTokenEveryTwelveMinutes() {
        MutableClock clock = new MutableClock();
        TokenBucketLimiter limiter = new TokenBucketLimiter(5, Duration.ofHours(1), clock);
        for (int i = 0; i < 5; i++) assertTrue(limiter.acquire("a").allowed(), "istek " + (i + 1));
        TokenBucketLimiter.Decision denied = limiter.acquire("a");
        assertFalse(denied.allowed());
        assertEquals(12 * 60, denied.retryAfterSeconds());
        assertTrue(limiter.acquire("b").allowed(), "başka anahtarın kovası ayrı");

        clock.millis += Duration.ofMinutes(6).toMillis();
        TokenBucketLimiter.Decision half = limiter.acquire("a");
        assertFalse(half.allowed());
        assertEquals(6 * 60, half.retryAfterSeconds());

        clock.millis += Duration.ofMinutes(6).toMillis();
        assertTrue(limiter.acquire("a").allowed(), "12 dakikada bir jeton");
        assertFalse(limiter.acquire("a").allowed());

        clock.millis += Duration.ofHours(5).toMillis();
        for (int i = 0; i < 5; i++) assertTrue(limiter.acquire("a").allowed());
        assertFalse(limiter.acquire("a").allowed(), "kova en çok 5 jeton tutar");
    }

    @Test
    void cleanupDropsFullBuckets() {
        MutableClock clock = new MutableClock();
        TokenBucketLimiter limiter = new TokenBucketLimiter(5, Duration.ofHours(1), clock);
        limiter.acquire("a");
        limiter.acquire("b");
        assertEquals(2, limiter.size());
        clock.millis += Duration.ofMinutes(13).toMillis();
        limiter.cleanup(clock.millis());
        assertEquals(0, limiter.size());
    }

    @Test
    void forwardedForOnlyBehindTrustedProxy() {
        assertEquals("10.0.0.1", PublicForms.clientIp("10.0.0.1", "6.6.6.6", false, 1), "vekil yoksa başlık yok sayılır");
        assertEquals("10.0.0.1", PublicForms.clientIp("10.0.0.1", null, true, 1));
        assertEquals("203.0.113.9", PublicForms.clientIp("10.0.0.1", "6.6.6.6, 203.0.113.9", true, 1), "vekilin eklediği (en sağdaki)");
        assertEquals("6.6.6.6", PublicForms.clientIp("10.0.0.1", "6.6.6.6, 203.0.113.9", true, 2));
        assertEquals("6.6.6.6", PublicForms.clientIp("10.0.0.1", "6.6.6.6", true, 3), "liste kısaysa en soldaki");
    }
}
