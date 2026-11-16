package com.karapp.drawguidance.server;

import com.karapp.drawguidance.server.distribution.ImageSafety;
import com.karapp.drawguidance.server.distribution.cli.CliAuth;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/** v54 yönetim şifresi ve dağıtılan resimlerin güvenlik denetimi. */
class V54SecurityTest {

    static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(1, 1, 0xFF336699);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    static byte[] jpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** IEND'den önce tEXt parçası ekler. */
    static byte[] withText(byte[] png, String text) {
        byte[] data = ("Comment\0" + text).getBytes(StandardCharsets.ISO_8859_1);
        byte[] chunk = new byte[12 + data.length];
        chunk[0] = (byte) (data.length >>> 24); chunk[1] = (byte) (data.length >>> 16); chunk[2] = (byte) (data.length >>> 8); chunk[3] = (byte) data.length;
        System.arraycopy("tEXt".getBytes(StandardCharsets.US_ASCII), 0, chunk, 4, 4);
        System.arraycopy(data, 0, chunk, 8, data.length);
        CRC32 crc = new CRC32();
        crc.update(chunk, 4, 4 + data.length);
        long c = crc.getValue();
        chunk[8 + data.length] = (byte) (c >>> 24); chunk[9 + data.length] = (byte) (c >>> 16); chunk[10 + data.length] = (byte) (c >>> 8); chunk[11 + data.length] = (byte) c;
        int iend = png.length - 12;
        byte[] out = new byte[png.length + chunk.length];
        System.arraycopy(png, 0, out, 0, iend);
        System.arraycopy(chunk, 0, out, iend, chunk.length);
        System.arraycopy(png, iend, out, iend + chunk.length, 12);
        return out;
    }

    @Test
    void cleanImagesPass() throws Exception {
        ImageSafety.Info p = ImageSafety.check(png(40, 30), "a.png");
        assertEquals("image/png", p.mime());
        assertEquals(40, p.width());
        assertEquals(30, p.height());
        ImageSafety.Info j = ImageSafety.check(jpeg(64, 48), "b.jpg");
        assertEquals("image/jpeg", j.mime());
        assertEquals(64, j.width());
        assertEquals(48, j.height());
        // Zararsız metin üst verisi
        ImageSafety.check(withText(png(20, 20), "Çizim Rehberi ile yapıldı"), "c.png");
    }

    @Test
    void codeAndPolyglotsRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ImageSafety.check(withText(png(20, 20), "<script>alert(1)</script>"), "x.png"));
        assertThrows(IllegalArgumentException.class, () -> ImageSafety.check(withText(png(20, 20), "<?php system($_GET['c']); ?>"), "x.png"));
        assertThrows(IllegalArgumentException.class, () -> ImageSafety.check(concat(png(20, 20), "<html><body>x</body></html>".getBytes()), "x.png"));
        assertThrows(IllegalArgumentException.class, () -> ImageSafety.check(concat(jpeg(20, 20), new byte[]{'P', 'K', 3, 4, 20, 0, 0, 0}), "x.jpg"));
        assertThrows(IllegalArgumentException.class, () -> ImageSafety.check(concat(jpeg(20, 20), new byte[]{'M', 'Z', 0, 0}), "x.jpg"));
        // Uzantı ve imza uyuşmazlığı, resim olmayan dosya, kesik PNG
        assertThrows(IllegalArgumentException.class, () -> ImageSafety.check(png(20, 20), "x.jpg"));
        assertThrows(IllegalArgumentException.class, () -> ImageSafety.check("<svg onload=alert(1)>".getBytes(), "x.svg"));
        byte[] p = png(20, 20);
        byte[] cut = java.util.Arrays.copyOf(p, p.length - 20);
        assertThrows(IllegalArgumentException.class, () -> ImageSafety.check(cut, "x.png"));
    }

    @Test
    void cliPasswordHashAndVerify() throws Exception {
        String h = CliAuth.hash("cok-gizli-sifre".toCharArray());
        assertTrue(h.startsWith("pbkdf2-sha256:"));
        assertFalse(h.contains("$"), "compose .env'de $ değişken sayılır");
        assertTrue(CliAuth.verify("cok-gizli-sifre".toCharArray(), h));
        assertFalse(CliAuth.verify("cok-gizli-sifrE".toCharArray(), h));
        assertFalse(CliAuth.verify("cok-gizli-sifre".toCharArray(), "bozuk"));
        assertFalse(CliAuth.verify(null, h));
        // Sır dosyasından
        Path f = Files.createTempFile("cli", ".hash");
        Files.writeString(f, h + "\n");
        assertEquals(h, CliAuth.storedHash(Map.of("CLI_PASSWORD_HASH_FILE", f.toString())));
        assertEquals(h, CliAuth.storedHash(Map.of("CLI_PASSWORD_HASH", h)));
        assertNull(CliAuth.storedHash(Map.of()));
    }
}
