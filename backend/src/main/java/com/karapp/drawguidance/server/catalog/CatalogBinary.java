package com.karapp.drawguidance.server.catalog;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * v37 katalog bölümlerinin ikili aktarım biçimi ({@code application/vnd.drawguidance.catalog+bin}): vektörler ve temel
 * ağlar. Uygulamadaki {@code CatalogBinary.kt} ile <b>bayt bayt aynı</b> çıktıyı üretir (iki taraf da ortak örnek
 * dosyayla sınanır: {@code catalog-binary-fixture.json} → {@code .hex}). Biçimin tamamı {@code docs/v37-api.md}'de.
 *
 * <pre>
 * 'D' 'G' 'C' 'B', biçim sürümü (1 bayt), bölüm (1 bayt: 1 vektörler, 2 ağlar), katalog sürümü (varint64),
 * öğe sayısı (varint), her öğe: uzunluk (varint) + öğe baytları
 * </pre>
 *
 * Vektörlerin yolları (öğedeki JSON metni) uygulamanın cihaz veritabanı biçimine ({@code CatalogCodec.packPaths})
 * çevrilir: uygulamanın JSON çözücüsünün ({@code CatalogCodec.decodePaths}) okuduğu gibi okunur (sayılar
 * {@code Float.parseFloat}, eksik alanlar öntanımlı), koordinatlar {@code Math.round(v * 10000f)} ile tamsayıya
 * çevrilir. Uygulamanın JSON'dan çözemeyeceği öğe (ör. {@code id} ya da {@code paths} yazı değil, yollar bozuk,
 * ağırlıklar base64 değil) ikili yanıta konmaz; uygulama JSON yolunda da onu atlar. Çizgi biçimi ({@code "l"})
 * yorumlanmaz: {@code SOLID} dışındaki her ad yazılır (uygulama tanımadığını {@code SOLID} sayar).
 */
public final class CatalogBinary {
    private static final Logger log = LoggerFactory.getLogger(CatalogBinary.class);

    public static final String MEDIA_TYPE = "application/vnd.drawguidance.catalog+bin";
    public static final int FORMAT_VERSION = 1;
    public static final int SECTION_VECTORS = 1;
    public static final int SECTION_MODE_NETS = 2;

    /** Vektör öğesinin isteğe bağlı alanları (bayrak bitleri; uygulamadakiyle aynı). */
    static final int V_TITLE = 1, V_CATEGORY_ID = 2, V_CATEGORY_TITLE = 4, V_SECTION = 8, V_AUDIENCE = 16, V_FIT = 32,
            V_ASPECT = 64, V_TAGS = 128, V_KINDS = 256;
    /** Ağ öğesinin isteğe bağlı alanları. */
    static final int N_VERSION = 1, N_FORMAT = 2;

    /** {@code packPaths} yol bayrakları ve biçim sürümü (uygulamadaki {@code CatalogCodec} ile aynı). */
    private static final int PACK_VERSION = 1;
    private static final int F_CLOSED = 1, F_FILL = 2, F_STROKE = 4, F_WIDTH = 8, F_LINE = 16, F_ALPHA = 32, F_TAG = 64,
            F_LABEL = 128, F_LABEL_SIZE = 256;

    private static final byte[] MAGIC = {'D', 'G', 'C', 'B'};
    private static final JsonFactory JSON = new JsonFactory();
    private static final Pattern INTEGER = Pattern.compile("-?[0-9]+");

    private CatalogBinary() {}

    /** Bu bölüm ikili biçimde verilebilir mi (şablonlar yalnız JSON). */
    public static boolean supports(CatalogKind kind) { return kind == CatalogKind.VECTORS || kind == CatalogKind.MODE_NETS; }

    /** Bölümün ikili gövdesi (öğeler sunucudaki sırayla). */
    public static byte[] encode(CatalogKind kind, long version, List<Document> items) {
        return switch (kind) {
            case VECTORS -> vectors(version, items);
            case MODE_NETS -> modeNets(version, items);
            default -> throw new IllegalArgumentException("ikili biçim yok: " + kind);
        };
    }

    public static byte[] vectors(long version, List<Document> items) {
        return container(SECTION_VECTORS, version, items, CatalogBinary::vectorItem, "vektör");
    }

    public static byte[] modeNets(long version, List<Document> items) {
        return container(SECTION_MODE_NETS, version, items, CatalogBinary::modeNetItem, "ağ");
    }

    private interface ItemEncoder { byte[] encode(Document d); }

    private static byte[] container(int section, long version, List<Document> items, ItemEncoder encoder, String label) {
        List<byte[]> encoded = new ArrayList<>(items.size());
        int skipped = 0;
        for (Document d : items) {
            byte[] item;
            try {
                item = encoder.encode(d);
            } catch (RuntimeException e) {
                item = null;
            }
            if (item == null) skipped++;
            else encoded.add(item);
        }
        if (skipped > 0) log.warn("İkili katalog: uygulamanın çözemeyeceği {} {} öğesi atlandı", skipped, label);
        Out out = new Out(64 + encoded.stream().mapToInt(b -> b.length + 3).sum());
        for (byte b : MAGIC) out.write(b);
        out.write(FORMAT_VERSION);
        out.write(section);
        out.varlong(version);
        out.varint(encoded.size());
        for (byte[] item : encoded) out.bytes(item);
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- vektörler

    /** Vektör öğesi; uygulamanın JSON çözücüsü ({@code CatalogCodec.vector}) bu öğeyi atlayacaksa {@code null}. */
    static byte[] vectorItem(Document d) {
        if (!(d.get("id") instanceof String id)) return null;
        if (!(d.get("paths") instanceof String paths)) return null;
        byte[] packed = packPaths(paths);
        int mask = 0;
        String title = text(d, "title"), categoryId = text(d, "categoryId"), categoryTitle = text(d, "categoryTitle"),
                section = text(d, "section"), audience = text(d, "audience"), fit = text(d, "fit");
        Float aspect = aspect(d.get("aspect"));
        List<String> tags = primitives(d.get("tags")), kinds = primitives(d.get("kinds"));
        if (title != null) mask |= V_TITLE;
        if (categoryId != null) mask |= V_CATEGORY_ID;
        if (categoryTitle != null) mask |= V_CATEGORY_TITLE;
        if (section != null) mask |= V_SECTION;
        if (audience != null) mask |= V_AUDIENCE;
        if (fit != null) mask |= V_FIT;
        if (aspect != null) mask |= V_ASPECT;
        if (tags != null) mask |= V_TAGS;
        if (kinds != null) mask |= V_KINDS;
        Out out = new Out(64 + packed.length);
        out.varint(mask);
        out.string(id);
        if (title != null) out.string(title);
        if (categoryId != null) out.string(categoryId);
        if (categoryTitle != null) out.string(categoryTitle);
        if (section != null) out.string(section);
        if (audience != null) out.string(audience);
        if (fit != null) out.string(fit);
        if (aspect != null) out.float32(aspect);
        if (tags != null) out.strings(tags);
        if (kinds != null) out.strings(kinds);
        out.bytes(packed);
        return out.toByteArray();
    }

    private static String text(Document d, String key) { return d.get(key) instanceof String s ? s : null; }

    /**
     * Uygulama {@code aspect}'i JSON metninden {@code Float} olarak okur ({@code floatOrNull}, yoksa 1). Sayı
     * {@code float}'a yuvarlanır (JSON'daki kısa ondalık gösterimin {@code float}'ı ile aynı); yazı sayıya çevrilir.
     */
    private static Float aspect(Object v) {
        if (v == null || v instanceof Boolean) return null;
        if (v instanceof Double n) return (float) n.doubleValue();
        if (v instanceof Integer || v instanceof Long) return (float) ((Number) v).longValue();
        if (v instanceof Number n) return parseFloat(n.toString());
        if (v instanceof String s) return parseFloat(s);
        // Nesne ya da dizi: uygulama bu öğeyi çözemez
        throw new IllegalArgumentException("aspect sayı değil");
    }

    /** JSON dizisindeki ilkel değerlerin metni (uygulamadaki {@code (it as? JsonPrimitive)?.content}); dizi değilse null. */
    private static List<String> primitives(Object v) {
        if (!(v instanceof List<?> list)) return null;
        List<String> out = new ArrayList<>(list.size());
        for (Object e : list) {
            if (e == null) out.add("null");
            else if (e instanceof String || e instanceof Number || e instanceof Boolean) out.add(e.toString());
        }
        return out;
    }

    // ---------------------------------------------------------------- yollar

    /**
     * Yolların JSON metni ({@code [{"p":[x,y,…],"c":1,"f":"#..",…}]}) → uygulamanın paketli yol biçimi
     * ({@code 'V' 1}, yol sayısı; her yol için bayrak, nokta sayısı, zigzag farklar, bayraktaki alanlar). Uygulama bu
     * metni çözemiyorsa {@link IllegalArgumentException}.
     */
    public static byte[] packPaths(String json) {
        Object root = parse(json);
        if (!(root instanceof List<?> paths)) throw new IllegalArgumentException("paths bir dizi değil");
        Out w = new Out(64 + json.length() / 3);
        w.write('V');
        w.write(PACK_VERSION);
        w.varint(paths.size());
        int px = 0, py = 0;
        for (Object e : paths) {
            if (!(e instanceof Map<?, ?> o)) throw new IllegalArgumentException("yol bir nesne değil");
            if (!(o.get("p") instanceof List<?> xy)) throw new IllegalArgumentException("yolun noktaları (p) yok");
            float[] values = new float[xy.size()];
            for (int i = 0; i < values.length; i++) values[i] = orDefault(floatOrNull(primitive(xy.get(i))), 0f);
            Object c = o.get("c");
            boolean closed = false;
            if (c != null) {
                Prim p = primitive(c);
                Integer asInt = intOrNull(p);
                closed = (asInt != null && asInt == 1) || Boolean.TRUE.equals(booleanOrNull(p));
            }
            String fill = str(o, "f"), stroke = str(o, "s"), tag = str(o, "t"), label = str(o, "lb");
            float width = number(o, "w", 1f), alpha = number(o, "a", 1f), labelSize = number(o, "ls", 0f);
            String line = str(o, "l");
            if ("SOLID".equals(line)) line = null;

            int flags = 0;
            if (closed) flags |= F_CLOSED;
            if (fill != null) flags |= F_FILL;
            if (stroke != null) flags |= F_STROKE;
            if (width != 1f) flags |= F_WIDTH;
            if (line != null) flags |= F_LINE;
            if (alpha != 1f) flags |= F_ALPHA;
            if (tag != null) flags |= F_TAG;
            if (label != null) flags |= F_LABEL;
            if (labelSize != 0f) flags |= F_LABEL_SIZE;
            w.varint(flags);
            int count = values.length / 2;
            w.varint(count);
            for (int i = 0; i < count; i++) {
                int x = q4(values[2 * i]), y = q4(values[2 * i + 1]);
                w.zigzag(x - px);
                w.zigzag(y - py);
                px = x;
                py = y;
            }
            if (fill != null) w.string(fill);
            if (stroke != null) w.string(stroke);
            if ((flags & F_WIDTH) != 0) w.zigzag(q4(width));
            if (line != null) w.string(line);
            if ((flags & F_ALPHA) != 0) w.zigzag(q4(alpha));
            if (tag != null) w.string(tag);
            if (label != null) w.string(label);
            if ((flags & F_LABEL_SIZE) != 0) w.zigzag(q4(labelSize));
        }
        return w.toByteArray();
    }

    /** Uygulamadaki {@code q4}: 4 basamağa yuvarlanmış değer tamsayı olarak ({@code float} aritmetiğiyle). */
    static int q4(float v) { return Math.round(v * 10000f); }

    // ---------------------------------------------------------------- temel ağlar

    /** Ağ öğesi; uygulama ({@code CatalogCodec.modeNet}) bu öğeyi atlayacaksa {@code null}. */
    static byte[] modeNetItem(Document d) {
        if (!(d.get("mode") instanceof String mode)) return null;
        if (!(d.get("weights") instanceof String b64)) return null;
        byte[] weights = Base64.getDecoder().decode(b64);
        Integer version = intValue(d.get("version"));
        String format = text(d, "format");
        int mask = (version != null ? N_VERSION : 0) | (format != null ? N_FORMAT : 0);
        Out out = new Out(32 + weights.length);
        out.varint(mask);
        out.string(mode);
        if (version != null) out.zigzag(version);
        if (format != null) out.string(format);
        out.bytes(weights);
        return out.toByteArray();
    }

    /** Uygulamadaki {@code (o["version"] as? JsonPrimitive)?.intOrNull}: tamsayı (int aralığında) ya da tamsayı yazısı. */
    private static Integer intValue(Object v) {
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (int) l.longValue() : null;
        if (v instanceof String s) return intOrNull(new Prim(s, true));
        return null;
    }

    // ---------------------------------------------------------------- JSON (uygulamadaki kotlinx ağacının karşılığı)

    /** JSON ilkel değeri: metni (sayılar yazıldığı gibi; null için "null") ve yazı olup olmadığı. */
    record Prim(String content, boolean isString) {}

    /** Nesne → {@code LinkedHashMap} (aynı anahtar iki kez geçerse sonuncusu), dizi → {@code List}, ilkel → {@link Prim}. */
    static Object parse(String json) {
        try (JsonParser p = JSON.createParser(json)) {
            if (p.nextToken() == null) throw new IllegalArgumentException("boş JSON");
            Object value = read(p);
            if (p.nextToken() != null) throw new IllegalArgumentException("JSON'dan sonra fazladan veri");
            return value;
        } catch (IOException e) {
            throw new IllegalArgumentException("yollar okunamadı: " + e.getMessage(), e);
        }
    }

    private static Object read(JsonParser p) throws IOException {
        JsonToken t = p.currentToken();
        switch (t) {
            case START_OBJECT -> {
                Map<String, Object> m = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String key = p.currentName();
                    p.nextToken();
                    m.put(key, read(p));
                }
                return m;
            }
            case START_ARRAY -> {
                List<Object> l = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) l.add(read(p));
                return l;
            }
            case VALUE_STRING -> { return new Prim(p.getText(), true); }
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> { return new Prim(p.getText(), false); }
            case VALUE_TRUE -> { return new Prim("true", false); }
            case VALUE_FALSE -> { return new Prim("false", false); }
            case VALUE_NULL -> { return new Prim("null", false); }
            default -> throw new IllegalArgumentException("beklenmeyen JSON: " + t);
        }
    }

    /** {@code jsonPrimitive}: nesne ya da dizi ilkel değildir (uygulamada istisna). */
    private static Prim primitive(Object v) {
        if (v instanceof Prim p) return p;
        throw new IllegalArgumentException("ilkel değer bekleniyordu");
    }

    /** {@code (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content}. */
    private static String str(Map<?, ?> o, String key) {
        return o.get(key) instanceof Prim p && p.isString() ? p.content() : null;
    }

    /** {@code o[k]?.jsonPrimitive?.floatOrNull ?: def}. */
    private static float number(Map<?, ?> o, String key, float def) {
        Object v = o.get(key);
        return v == null ? def : orDefault(floatOrNull(primitive(v)), def);
    }

    /** {@code content.toFloatOrNull()}. */
    private static Float floatOrNull(Prim p) { return parseFloat(p.content()); }

    private static Float parseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code intOrNull}: tamsayı yazımı ve int aralığı. */
    private static Integer intOrNull(Prim p) {
        String s = p.content();
        if (!INTEGER.matcher(s).matches() || s.length() > 20) return null;
        try {
            long v = Long.parseLong(s);
            return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE ? (int) v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code booleanOrNull}: büyük/küçük harf duyarsız "true" / "false". */
    private static Boolean booleanOrNull(Prim p) {
        String s = p.content().toLowerCase(Locale.ROOT);
        return s.equals("true") ? Boolean.TRUE : s.equals("false") ? Boolean.FALSE : null;
    }

    private static float orDefault(Float v, float def) { return v == null ? def : v; }

    // ---------------------------------------------------------------- yazıcı

    /** Büyüyen bayt dizisi (uygulamadakiyle aynı sayı kodlaması: varint, zigzag, UTF-8 yazılar). */
    static final class Out {
        private byte[] buf;
        private int size;

        Out(int capacity) { buf = new byte[Math.max(16, capacity)]; }

        private void room(int n) {
            if (size + n > buf.length) buf = Arrays.copyOf(buf, Math.max(buf.length * 2, size + n));
        }

        void write(int v) {
            room(1);
            buf[size++] = (byte) v;
        }

        void varint(int value) {
            room(5);
            int v = value;
            while ((v & ~0x7F) != 0) {
                buf[size++] = (byte) ((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            buf[size++] = (byte) v;
        }

        void varlong(long value) {
            room(10);
            long v = value;
            while ((v & ~0x7FL) != 0L) {
                buf[size++] = (byte) ((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            buf[size++] = (byte) v;
        }

        void zigzag(int v) { varint((v << 1) ^ (v >> 31)); }

        void float32(float f) {
            int bits = Float.floatToIntBits(f);
            write(bits >>> 24);
            write(bits >>> 16);
            write(bits >>> 8);
            write(bits);
        }

        void bytes(byte[] b) {
            varint(b.length);
            room(b.length);
            System.arraycopy(b, 0, buf, size, b.length);
            size += b.length;
        }

        void string(String s) { bytes(s.getBytes(StandardCharsets.UTF_8)); }

        void strings(List<String> list) {
            varint(list.size());
            for (String s : list) string(s);
        }

        byte[] toByteArray() { return Arrays.copyOf(buf, size); }
    }
}
