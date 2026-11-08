package com.karapp.drawguidance.server.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Testlerde v37 ikili katalog biçimini çözen bağımsız okuyucu (uygulamadaki {@code CatalogBinary.kt} çözücüsünün
 * karşılığı; sunucunun kodlayıcısını kullanmaz). Yollar {@code packPaths} biçiminden tamsayı koordinatlarla
 * ({@code round(v * 10000)}) çözülür.
 */
public final class CatalogBinaryReader {
    private CatalogBinaryReader() {}

    public record Section(int format, int section, long version, List<byte[]> items) {}

    public record Vector(int mask, String id, String title, String categoryId, String categoryTitle, String section,
                         String audience, String fit, Float aspect, List<String> tags, List<String> kinds, byte[] paths) {}

    public record ModeNet(int mask, String mode, Integer version, String format, byte[] weights) {}

    /** Bir yol: bayraklar, mutlak noktalar (x0, y0, x1, y1 … ; 4 basamaklı tamsayı) ve bayraktaki alanlar. */
    public record Path(int flags, int[] xy, String fill, String stroke, Integer width, String line, Integer alpha, String tag,
                       String label, Integer labelSize) {
        public boolean closed() { return (flags & 1) != 0; }
    }

    public static Section section(byte[] bytes) {
        Reader r = new Reader(bytes, 0, bytes.length);
        byte[] magic = {(byte) r.u8(), (byte) r.u8(), (byte) r.u8(), (byte) r.u8()};
        if (!Arrays.equals(magic, new byte[]{'D', 'G', 'C', 'B'})) throw new IllegalArgumentException("sihirli baytlar yanlış");
        int format = r.u8(), section = r.u8();
        long version = r.varlong();
        int count = r.varint();
        List<byte[]> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) items.add(r.bytes());
        if (r.pos != bytes.length) throw new IllegalArgumentException("fazladan bayt");
        return new Section(format, section, version, items);
    }

    public static Vector vector(byte[] item) {
        Reader r = new Reader(item, 0, item.length);
        int mask = r.varint();
        String id = r.string();
        String title = (mask & 1) != 0 ? r.string() : null;
        String categoryId = (mask & 2) != 0 ? r.string() : null;
        String categoryTitle = (mask & 4) != 0 ? r.string() : null;
        String section = (mask & 8) != 0 ? r.string() : null;
        String audience = (mask & 16) != 0 ? r.string() : null;
        String fit = (mask & 32) != 0 ? r.string() : null;
        Float aspect = (mask & 64) != 0 ? Float.intBitsToFloat((r.u8() << 24) | (r.u8() << 16) | (r.u8() << 8) | r.u8()) : null;
        List<String> tags = (mask & 128) != 0 ? r.strings() : null;
        List<String> kinds = (mask & 256) != 0 ? r.strings() : null;
        byte[] paths = r.bytes();
        return new Vector(mask, id, title, categoryId, categoryTitle, section, audience, fit, aspect, tags, kinds, paths);
    }

    public static ModeNet modeNet(byte[] item) {
        Reader r = new Reader(item, 0, item.length);
        int mask = r.varint();
        String mode = r.string();
        Integer version = (mask & 1) != 0 ? r.zigzag() : null;
        String format = (mask & 2) != 0 ? r.string() : null;
        return new ModeNet(mask, mode, version, format, r.bytes());
    }

    public static List<Path> paths(byte[] packed) {
        Reader r = new Reader(packed, 0, packed.length);
        if (r.u8() != 'V' || r.u8() != 1) throw new IllegalArgumentException("paketli yol değil");
        int n = r.varint();
        List<Path> out = new ArrayList<>(n);
        int px = 0, py = 0;
        for (int i = 0; i < n; i++) {
            int flags = r.varint();
            int count = r.varint();
            int[] xy = new int[count * 2];
            for (int k = 0; k < count; k++) {
                px += r.zigzag();
                py += r.zigzag();
                xy[2 * k] = px;
                xy[2 * k + 1] = py;
            }
            String fill = (flags & 2) != 0 ? r.string() : null;
            String stroke = (flags & 4) != 0 ? r.string() : null;
            Integer width = (flags & 8) != 0 ? r.zigzag() : null;
            String line = (flags & 16) != 0 ? r.string() : null;
            Integer alpha = (flags & 32) != 0 ? r.zigzag() : null;
            String tag = (flags & 64) != 0 ? r.string() : null;
            String label = (flags & 128) != 0 ? r.string() : null;
            Integer labelSize = (flags & 256) != 0 ? r.zigzag() : null;
            out.add(new Path(flags, xy, fill, stroke, width, line, alpha, tag, label, labelSize));
        }
        if (r.pos != packed.length) throw new IllegalArgumentException("fazladan bayt");
        return out;
    }

    private static final class Reader {
        private final byte[] buf;
        private int pos;
        private final int end;

        Reader(byte[] buf, int pos, int end) {
            this.buf = buf;
            this.pos = pos;
            this.end = end;
        }

        int u8() {
            if (pos >= end) throw new IllegalArgumentException("kısa veri");
            return buf[pos++] & 0xFF;
        }

        int varint() {
            int shift = 0, out = 0;
            while (true) {
                int b = u8();
                out |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return out;
                shift += 7;
                if (shift >= 35) throw new IllegalArgumentException("bozuk sayı");
            }
        }

        long varlong() {
            int shift = 0;
            long out = 0;
            while (true) {
                int b = u8();
                out |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) return out;
                shift += 7;
                if (shift >= 70) throw new IllegalArgumentException("bozuk sayı");
            }
        }

        int zigzag() {
            int v = varint();
            return (v >>> 1) ^ -(v & 1);
        }

        byte[] bytes() {
            int n = varint();
            if (n < 0 || n > end - pos) throw new IllegalArgumentException("bozuk uzunluk");
            byte[] b = Arrays.copyOfRange(buf, pos, pos + n);
            pos += n;
            return b;
        }

        String string() { return new String(bytes(), StandardCharsets.UTF_8); }

        List<String> strings() {
            int n = varint();
            List<String> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) out.add(string());
            return out;
        }
    }
}
