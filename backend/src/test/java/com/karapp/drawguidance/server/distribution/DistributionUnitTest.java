package com.karapp.drawguidance.server.distribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karapp.drawguidance.server.billing.play.ServiceAccountCredentials;
import com.karapp.drawguidance.server.distribution.cli.Args;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** v43 dağıtımın veritabanı gerektirmeyen parçaları: cihaz şifrelemesi, GCS V4 imzası, girdi denetimleri, CLI argümanları. */
class DistributionUnitTest {

    @Test
    void deviceCryptoRoundTripAndBinding() {
        DeviceCrypto c = DeviceCrypto.fromBase64(DeviceCrypto.newKey());
        String key = c.deviceKey("device-1234567890abcdef");
        assertEquals(64, key.length());
        assertEquals(key, c.deviceKey("device-1234567890abcdef"), "aynı cihaz aynı anahtar");
        assertNotEquals(key, c.deviceKey("device-1234567890abcdeg"));
        String sealed = c.encrypt("{\"model\":\"Pixel 8\"}", key);
        assertFalse(sealed.contains("Pixel"), "düz metin görünmez");
        assertNotEquals(sealed, c.encrypt("{\"model\":\"Pixel 8\"}", key), "her şifrelemede yeni IV");
        assertEquals("{\"model\":\"Pixel 8\"}", c.decrypt(sealed, key));
        // Başka cihazın belgesine kopyalanan şifreli alan çözülmez (AAD)
        assertNull(c.decrypt(sealed, c.deviceKey("baska-cihaz-1234567890")));
        // Başka anahtarla çözülmez, bozuk veri null
        assertNull(DeviceCrypto.fromBase64(DeviceCrypto.newKey()).decrypt(sealed, key));
        assertNull(c.decrypt(sealed.substring(0, sealed.length() - 3) + "AAA", key));
        assertThrows(IllegalArgumentException.class, () -> new DeviceCrypto(new byte[16]));
    }

    @Test
    void signedUrlIsValidV4Signature() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        ServiceAccountCredentials creds = new ServiceAccountCredentials("dg-storage@drawguidance.iam.gserviceaccount.com", "k1", null, kp.getPrivate(), "drawguidance");
        GcsClient gcs = new GcsClient(creds, new ObjectMapper(), HttpClient.newHttpClient(), null);
        Instant now = Instant.parse("2026-10-25T12:00:00Z");
        String url = gcs.signedGetUrl("dgpackets", "base/base-comic/comic/objects/şapka kırmızı.webp", Duration.ofHours(24), now);

        URI u = URI.create(url);
        assertEquals("storage.googleapis.com", u.getHost());
        assertEquals("/dgpackets/base/base-comic/comic/objects/%C5%9Fapka%20k%C4%B1rm%C4%B1z%C4%B1.webp", u.getRawPath());
        Map<String, String> q = new LinkedHashMap<>();
        for (String p : u.getRawQuery().split("&")) {
            int eq = p.indexOf('=');
            q.put(p.substring(0, eq), p.substring(eq + 1));
        }
        assertEquals("GOOG4-RSA-SHA256", q.get("X-Goog-Algorithm"));
        assertEquals("20261025T120000Z", q.get("X-Goog-Date"));
        assertEquals("86400", q.get("X-Goog-Expires"));
        assertEquals("dg-storage@drawguidance.iam.gserviceaccount.com/20261025/auto/storage/goog4_request",
                URLDecoder.decode(q.get("X-Goog-Credential"), StandardCharsets.UTF_8));
        // İmzayı açık anahtarla doğrula (Google'ın kurduğu kanonik istekle aynı biçim)
        String query = u.getRawQuery().substring(0, u.getRawQuery().indexOf("&X-Goog-Signature="));
        String canonical = "GET\n" + u.getRawPath() + "\n" + query + "\nhost:storage.googleapis.com\n\nhost\nUNSIGNED-PAYLOAD";
        String toSign = "GOOG4-RSA-SHA256\n20261025T120000Z\n20261025/auto/storage/goog4_request\n" + GcsClient.sha256Hex(canonical);
        Signature v = Signature.getInstance("SHA256withRSA");
        v.initVerify(kp.getPublic());
        v.update(toSign.getBytes(StandardCharsets.UTF_8));
        assertTrue(v.verify(HexFormat.of().parseHex(q.get("X-Goog-Signature"))));
        // En çok 7 gün
        assertTrue(gcs.signedGetUrl("dgpackets", "a.png", Duration.ofDays(30), now).contains("X-Goog-Expires=604800"));
    }

    @Test
    void storageResolvesOnlyOwnBucket() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        ServiceAccountCredentials creds = new ServiceAccountCredentials("a@b.iam.gserviceaccount.com", null, null, g.generateKeyPair().getPrivate());
        DistributionConfig.Storage s = new DistributionConfig.Storage(new GcsClient(creds, new ObjectMapper(), HttpClient.newHttpClient(), null), "dgpackets", Duration.ofHours(1));
        Instant now = Instant.now();
        assertEquals("https://example.com/a.png", s.resolve("https://example.com/a.png", now));
        assertTrue(s.resolve("gs://dgpackets/x/y.webp", now).startsWith("https://storage.googleapis.com/dgpackets/x/y.webp?"));
        assertNull(s.resolve("gs://baska-bucket/x.webp", now), "başka bucket imzalanmaz");
        assertNull(new DistributionConfig.Storage(null, "dgpackets", Duration.ofHours(1)).resolve("gs://dgpackets/x.webp", now));
    }

    @Test
    void inputValidation() {
        assertEquals("https://cdn.example.com/a%20b.png", ContentService.url("https://cdn.example.com/a%20b.png"));
        assertEquals("gs://dgpackets/base/a.webp", ContentService.url("gs://dgpackets/base/a.webp"));
        for (String bad : List.of("http://example.com/a.png", "javascript:alert(1)", "gs://dgpackets/../x", "gs://bucket", "file:///etc/passwd",
                "https://user:pass@example.com/a.png", "ftp://x/y")) {
            assertThrows(IllegalArgumentException.class, () -> ContentService.url(bad), bad);
        }
        assertEquals("Merhaba dünya", ContentService.line("başlık", " Merhaba\u0000\n dünya‮ ", 120, true));
        assertEquals("satır 1\nsatır 2", ContentService.text("açıklama", "satır 1\r\nsatır 2\u0007", 100, true));
        assertThrows(IllegalArgumentException.class, () -> ContentService.line("başlık", "x".repeat(121), 120, true));
        assertThrows(IllegalArgumentException.class, () -> ContentService.checkId("Büyük Harf!"));
        assertEquals("COMIC", Dist.mode("çizgi-roman"));
        assertEquals("NOTES", Dist.mode("not"));
        assertEquals("objects", Dist.window("COMIC", "Objects"));
        assertThrows(IllegalArgumentException.class, () -> Dist.window("ART", "objects"));
        assertEquals(Dist.Kind.ANNOUNCEMENT, Dist.Kind.parse("duyuru"));
        assertEquals(Dist.Kind.APP_UPDATE, Dist.Kind.parse("surum"));
    }

    @Test
    void cliArgs() {
        Args a = new Args(new String[]{"pkg_1", "--ad", "Uzay", "--oge", "objects|Roket|https://x/r.png", "--oge=effects|Duman|https://x/d.png|Sis", "--herkes"},
                Set.of("herkes"));
        assertEquals("pkg_1", a.pos(0));
        assertEquals("Uzay", a.opt("ad"));
        assertEquals(2, a.all("oge").size());
        assertTrue(a.flag("herkes"));
        assertTrue(a.unknown().isEmpty());
        Args b = new Args(new String[]{"--yanlis", "x"}, Set.of());
        assertEquals(List.of("--yanlis"), b.unknown());
        assertEquals("Sis", com.karapp.drawguidance.server.distribution.cli.DistributionCliAccess.items(List.of("effects|Duman|https://x/d.png|Sis")).get(0).category());
        assertThrows(IllegalArgumentException.class, () -> com.karapp.drawguidance.server.distribution.cli.DistributionCliAccess.items(List.of("eksik|alan")));
    }
}
