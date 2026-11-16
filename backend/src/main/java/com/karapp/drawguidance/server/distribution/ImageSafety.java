package com.karapp.drawguidance.server.distribution;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.Inflater;

/**
 * v54 sunucudan dağıtılan resimlerin (taban paket yüklemesi) denetimi; uygulamadaki {@code ImageSecurity} ile aynı
 * kurallar. Yalnız PNG, JPEG, WEBP (dosya imzasından; uzantı imzayla uyuşmalı), en çok 20 MB ve 8192 px; parça/segment
 * yapısı tutarlı; üst veri parçalarında ve bitiş işaretinden sonra betik, HTML/PHP, çalıştırılabilir ya da arşiv imzası
 * yok. Uymayan dosya yüklenmez (cihazlar da aynı denetimi yapar).
 */
public final class ImageSafety {
    public static final long MAX_BYTES = 20L * 1024 * 1024;
    public static final int MAX_SIDE = 8192;

    private static final List<byte[]> TEXT_SIGNS = java.util.stream.Stream.of(
            "<script", "</script", "<?php", "<html", "<body", "<iframe", "<object", "<embed", "<svg ", "javascript:",
            "vbscript:", "#!/bin/", "#!/usr/", "eval(", "document.cookie", "powershell", "cmd.exe", "/bin/sh")
            .map(s -> s.getBytes(StandardCharsets.US_ASCII)).toList();
    private static final List<byte[]> BIN_SIGNS = List.of(
            new byte[]{0x7F, 'E', 'L', 'F'}, new byte[]{'P', 'K', 3, 4}, "Rar!".getBytes(StandardCharsets.US_ASCII),
            "%PDF-".getBytes(StandardCharsets.US_ASCII), new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE},
            new byte[]{'d', 'e', 'x', '\n'});
    private static final byte[] ZIP_EOCD = {'P', 'K', 5, 6};
    private static final int BIN_SCAN_MAX = 256 * 1024;
    private static final int MAX_INFLATE = 1 << 20;

    private ImageSafety() {}

    /** Denetimden geçen resmin türü ve boyutu. */
    public record Info(String mime, int width, int height) {}

    /** Dosyayı denetler; uymazsa nedeniyle {@link IllegalArgumentException}. */
    public static Info check(byte[] b, String fileName) {
        if (b.length > MAX_BYTES) throw new IllegalArgumentException("Resim çok büyük (en çok 20 MB): " + fileName);
        String mime = sniff(b);
        if (mime == null) throw new IllegalArgumentException("Resim değil (yalnız PNG, JPEG, WEBP): " + fileName);
        String ext = fileName.toLowerCase(Locale.ROOT);
        boolean extOk = switch (mime) {
            case "image/png" -> ext.endsWith(".png");
            case "image/jpeg" -> ext.endsWith(".jpg") || ext.endsWith(".jpeg");
            default -> ext.endsWith(".webp");
        };
        if (!extOk) throw new IllegalArgumentException("Uzantı dosya türüyle uyuşmuyor (" + mime + "): " + fileName);
        String verdict = inspect(b, mime);
        if (verdict != null) throw new IllegalArgumentException(verdict + ": " + fileName);
        int[] size = size(b, mime);
        if (size == null || size[0] <= 0 || size[1] <= 0) throw new IllegalArgumentException("Resmin boyutu okunamadı: " + fileName);
        if (size[0] > MAX_SIDE || size[1] > MAX_SIDE) throw new IllegalArgumentException("Resim çok büyük (en çok 8192 px): " + fileName);
        return new Info(mime, size[0], size[1]);
    }

    public static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 12 && new String(b, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF")
                && new String(b, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP")) return "image/webp";
        return null;
    }

    /** Yapı ve gömülü kod denetimi: sorun yoksa null, varsa açıklama. */
    public static String inspect(byte[] b, String mime) {
        try {
            return switch (mime) {
                case "image/png" -> png(b);
                case "image/jpeg" -> jpeg(b);
                case "image/webp" -> webp(b);
                default -> "Desteklenmeyen tür";
            };
        } catch (RuntimeException e) {
            return "Resim yapısı bozuk";
        }
    }

    private static final String MALFORMED = "Resim yapısı bozuk";
    private static final String SUSPICIOUS = "Resmin içinde kod ya da başka bir dosya var";

    private static String png(byte[] b) {
        int at = 8;
        boolean first = true;
        while (true) {
            if (at + 12 > b.length) return MALFORMED;
            int len = be32(b, at);
            if (len < 0 || len > b.length - at - 12) return MALFORMED;
            String type = new String(b, at + 4, 4, StandardCharsets.ISO_8859_1);
            for (char c : type.toCharArray()) if (!Character.isLetter(c) || c > 'z') return MALFORMED;
            if (first && !type.equals("IHDR")) return MALFORMED;
            first = false;
            int data = at + 8;
            switch (type) {
                case "IEND" -> { return trailing(b, data + len + 4); }
                case "IDAT", "IHDR", "PLTE", "tRNS", "fdAT", "fcTL", "acTL" -> { }
                case "zTXt" -> {
                    int z = indexOf0(b, data, data + len);
                    if (z >= 0 && z + 2 <= data + len) {
                        if (suspicious(b, data, z - data)) return SUSPICIOUS;
                        byte[] text = inflate(b, z + 2, data + len - z - 2);
                        if (text == null) return MALFORMED;
                        if (suspicious(text, 0, text.length)) return SUSPICIOUS;
                    }
                }
                case "iTXt" -> {
                    int z = indexOf0(b, data, data + len);
                    int l1 = z >= 0 && z + 2 < data + len && b[z + 1] == 1 ? indexOf0(b, z + 3, data + len) : -1;
                    int l2 = l1 >= 0 ? indexOf0(b, l1 + 1, data + len) : -1;
                    if (l2 >= 0) {
                        if (suspicious(b, data, l2 + 1 - data)) return SUSPICIOUS;
                        byte[] text = inflate(b, l2 + 1, data + len - l2 - 1);
                        if (text == null) return MALFORMED;
                        if (suspicious(text, 0, text.length)) return SUSPICIOUS;
                    } else if (suspicious(b, data, len)) return SUSPICIOUS;
                }
                default -> { if (suspicious(b, data, len)) return SUSPICIOUS; }
            }
            at = data + len + 4;
        }
    }

    private static String jpeg(byte[] b) {
        if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) return MALFORMED;
        int at = 2;
        while (at < b.length) {
            if ((b[at] & 0xFF) != 0xFF) return MALFORMED;
            while (at < b.length && (b[at] & 0xFF) == 0xFF) at++;
            if (at >= b.length) return MALFORMED;
            int marker = b[at] & 0xFF;
            at++;
            if (marker == 0xD9) return trailing(b, at);
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) continue;
            if (at + 2 > b.length) return MALFORMED;
            int len = be16(b, at);
            if (len < 2 || at + len > b.length) return MALFORMED;
            if ((marker >= 0xE0 && marker <= 0xEF) || marker == 0xFE) {
                if (suspicious(b, at + 2, len - 2)) return SUSPICIOUS;
            }
            at += len;
            if (marker == 0xDA) {
                while (at + 1 < b.length) {
                    if ((b[at] & 0xFF) == 0xFF) {
                        int n = b[at + 1] & 0xFF;
                        if (n != 0x00 && !(n >= 0xD0 && n <= 0xD7) && n != 0xFF) break;
                    }
                    at++;
                }
                if (at + 1 >= b.length) return null;
            }
        }
        return null;
    }

    private static String webp(byte[] b) {
        if (b.length < 20) return MALFORMED;
        int riff = le32(b, 4);
        if (riff < 4 || (long) riff + 8 > b.length) return MALFORMED;
        int end = 8 + riff;
        Set<String> known = Set.of("VP8 ", "VP8L", "VP8X", "ALPH", "ANIM", "ANMF", "ICCP");
        int at = 12;
        while (at + 8 <= end) {
            String tag = new String(b, at, 4, StandardCharsets.ISO_8859_1);
            int len = le32(b, at + 4);
            if (len < 0 || at + 8L + len > end) return MALFORMED;
            if (!known.contains(tag) && suspicious(b, at + 8, len)) return SUSPICIOUS;
            at += 8 + len + (len & 1);
        }
        return trailing(b, end);
    }

    private static String trailing(byte[] b, int from) {
        if (from >= b.length) return null;
        if (from + 1 < b.length && b[from] == 'M' && b[from + 1] == 'Z') return SUSPICIOUS;
        int head = Math.min(b.length, from + 64);
        for (byte[] sig : BIN_SIGNS) if (find(b, from, head, sig)) return SUSPICIOUS;
        if (find(b, Math.max(from, b.length - 65_557), b.length, ZIP_EOCD)) return SUSPICIOUS;
        return suspicious(b, from, b.length - from) ? SUSPICIOUS : null;
    }

    static boolean suspicious(byte[] b, int off, int len) {
        if (len <= 0) return false;
        int end = Math.min(b.length, off + len);
        if (end - off <= BIN_SCAN_MAX) for (byte[] sig : BIN_SIGNS) if (find(b, off, end, sig)) return true;
        byte[] lower = new byte[end - off];
        for (int i = 0; i < lower.length; i++) {
            byte c = b[off + i];
            lower[i] = c >= 'A' && c <= 'Z' ? (byte) (c + 32) : c;
        }
        for (byte[] sig : TEXT_SIGNS) if (find(lower, 0, lower.length, sig)) return true;
        return false;
    }

    private static boolean find(byte[] b, int from, int end, byte[] sig) {
        int last = end - sig.length;
        for (int i = from; i <= last; i++) {
            if (b[i] != sig[0]) continue;
            int k = 1;
            while (k < sig.length && b[i + k] == sig[k]) k++;
            if (k == sig.length) return true;
        }
        return false;
    }

    private static byte[] inflate(byte[] b, int off, int len) {
        if (len <= 0) return new byte[0];
        Inflater inf = new Inflater();
        try {
            inf.setInput(b, off, len);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                out.write(buf, 0, n);
                if (out.size() > MAX_INFLATE) break;
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            inf.end();
        }
    }

    /** Başlıktan genişlik ve yükseklik (çözmeden). */
    static int[] size(byte[] b, String mime) {
        switch (mime) {
            case "image/png":
                return new int[]{be32(b, 16), be32(b, 20)};
            case "image/jpeg": {
                int at = 2;
                while (at + 9 < b.length) {
                    if ((b[at] & 0xFF) != 0xFF) return null;
                    int m = b[at + 1] & 0xFF;
                    int len = be16(b, at + 2);
                    // SOF0..SOF15 (DHT, JPG, DAC hariç)
                    if (m >= 0xC0 && m <= 0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) return new int[]{be16(b, at + 7), be16(b, at + 5)};
                    at += 2 + len;
                }
                return null;
            }
            default: {
                String chunk = new String(b, 12, 4, StandardCharsets.ISO_8859_1);
                if (chunk.equals("VP8X") && b.length >= 30) {
                    int w = 1 + ((b[24] & 0xFF) | (b[25] & 0xFF) << 8 | (b[26] & 0xFF) << 16);
                    int h = 1 + ((b[27] & 0xFF) | (b[28] & 0xFF) << 8 | (b[29] & 0xFF) << 16);
                    return new int[]{w, h};
                }
                if (chunk.equals("VP8L") && b.length >= 25) {
                    int bits = (b[21] & 0xFF) | (b[22] & 0xFF) << 8 | (b[23] & 0xFF) << 16 | (b[24] & 0xFF) << 24;
                    return new int[]{(bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1};
                }
                if (chunk.equals("VP8 ") && b.length >= 30) {
                    return new int[]{be16le(b, 26) & 0x3FFF, be16le(b, 28) & 0x3FFF};
                }
                return null;
            }
        }
    }

    private static int indexOf0(byte[] b, int from, int end) {
        for (int i = from; i < Math.min(end, b.length); i++) if (b[i] == 0) return i;
        return -1;
    }

    private static int be32(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }

    private static int be16(byte[] b, int at) { return (b[at] & 0xFF) << 8 | (b[at + 1] & 0xFF); }

    private static int be16le(byte[] b, int at) { return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8; }

    private static int le32(byte[] b, int at) {
        return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16 | (b[at + 3] & 0xFF) << 24;
    }
}
