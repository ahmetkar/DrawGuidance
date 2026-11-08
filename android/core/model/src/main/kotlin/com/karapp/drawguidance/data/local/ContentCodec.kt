package com.karapp.drawguidance.data.local

import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextParagraph
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.data.model.ShapeSpec
import com.karapp.drawguidance.data.model.DiagramSpec
import com.karapp.drawguidance.data.model.FormulaSpec
import com.karapp.drawguidance.data.model.Layer
import com.karapp.drawguidance.data.repository.decodeStrokes
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Tuval içeriğinin veritabanında tutulduğu kompakt, kayıpsız biçim.
 *
 * 1. İkili kodlama: UUID'ler 16 bayt, renkler 3 bayt, noktalar 1/100 birim tamsayılara
 *    çevrilip bir önceki noktaya göre fark (zigzag varint) olarak yazılır. Bir koordinat
 *    1/100'lük adımla birebir ifade edilemiyorsa o çizimin noktaları ham float bitleriyle
 *    (XOR farkı) yazılır — yani çözülen veri her zaman kodlanan veriyle bit bit aynıdır.
 * 2. Sonuç DEFLATE ile (en yüksek sıkıştırma) sıkıştırılır.
 *
 * Başlık: "DG" + sürüm. Başlıksız veri eski JSON biçimi olarak okunur.
 */
object ContentCodec {

    private const val MAGIC_0: Byte = 'D'.code.toByte()
    private const val MAGIC_1: Byte = 'G'.code.toByte()
    private const val VERSION: Byte = 1
    /** Sayfalı biçim (çalışma/not tuvallerinde birden çok sayfa). */
    private const val VERSION_PAGES: Byte = 2
    /**
     * v10: sayfalı + her çizimin sonunda ek bayrak baytı (eleman kırpması) ve yazı kenarlığı.
     * Yazılan her içerik bu sürümdedir; eski sürümler okunmaya devam eder.
     */
    private const val VERSION_V3: Byte = 3
    /** v15: belge başında katman listesi; çizimlerde katman ve formül ek bayrakları. */
    private const val VERSION_V4: Byte = 4
    /**
     * v18: kalemle çizilmiş (basınç/eğim taşıyan) bir çizim varsa bu sürüm yazılır; yoksa v4 (eski uygulamalar da
     * okuyabilsin).
     */
    private const val VERSION_V5: Byte = 5
    /** v20: fırça görünüm ayarları ([Stroke.params]) ya da kalem yönü taşıyan bir çizim varsa. */
    private const val VERSION_V6: Byte = 6
    /** v22: 3B sahnede yeri olan bir eleman varsa (eski sürümler açamaz). */
    private const val VERSION_V7: Byte = 7
    /**
     * v23: yazı kutusu (boyut ve arka plan) ya da 3B şekil içeren bir eleman varsa; her çizimde ikinci ek bayrak baytı
     * ([EXT2_TEXTBOX], [EXT2_MODEL]) bulunur.
     */
    private const val VERSION_V8: Byte = 8
    private const val EXT2_TEXTBOX = 1
    private const val EXT2_MODEL = 2
    /** v30: gizlenen eleman ([Stroke.hidden]); veri baytı yok. */
    private const val EXT2_HIDDEN = 4
    /**
     * v42: yol üzerinde yazı ([com.karapp.drawguidance.data.model.TextSpec.path]; JSON). Böyle bir eleman varsa belge
     * [VERSION_V9] yazılır: eski uygulamalar bu biti bilmediğinden belgeyi bozuk okumak yerine açmaz.
     */
    private const val EXT2_TEXTPATH = 8
    private const val VERSION_V9: Byte = 9
    private const val EXT_CLIP = 1
    /** v13: yazının paragraf biçimi (başlık/liste), 1 bayt. */
    private const val EXT_PARAGRAPH = 2
    /** v15: çizimin katmanı (belgenin katman listesindeki sırası, varint). */
    private const val EXT_LAYER = 4
    /** v15: formül kaynağı ve şablon kimliği. */
    private const val EXT_FORMULA = 8
    /** v18: nokta başına kalem basıncı ve eğimi (her biri 1 bayt, 0..255 → 0..1). */
    private const val EXT_DYNAMICS = 16
    /** v20: nokta başına kalem yönü (1 bayt, 0..255 → 0..1). */
    private const val EXT_ORIENT = 32
    /** v20: fırça görünüm ayarları (JSON). */
    private const val EXT_PARAMS = 64
    /** v22: 3B sahnedeki yer ([Stroke.space]); yalnız v7 kayıtlarda. */
    private const val EXT_SPACE = 128
    private const val QUANT = 100f

    private const val F_ID_UUID = 1
    private const val F_GROUP = 2
    private const val F_GROUP_UUID = 4
    private const val F_ASSET = 8
    private const val F_ASSET_UUID = 16
    private const val F_QUANTIZED = 32
    private const val F_COLOR_RGB = 64
    private const val F_DYNAMICS = 128

    /**
     * Yazı biçim baytının 5. biti: v2'de yazı kutusu (ShapeStyle JSON), v3'te harf kenarlığı
     * (renk + kalınlık) izler.
     */
    private const val TEXT_EXTRA = 32

    /** Şekil/diyagram tanımları JSON olarak saklanır (nokta verisi ikili kalır). */

    private val specJson = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }
    /**
     * v36 şekil/diyagram tanımı yazımı: varsayılan değerdeki alanlar yazılmaz (çok parçalı şekillerde JSON birkaç kat
     * kısalır, kodlama ve çözme hızlanır). Okuyan taraf eksik alanı varsayılanla doldurur; eski sürümler de okur.
     */
    private val specWriteJson = kotlinx.serialization.json.Json { encodeDefaults = false; ignoreUnknownKeys = true }
    /** v20 fırça ayarları: yalnız varsayılandan farklı alanlar yazılır. */
    private val paramsJson = kotlinx.serialization.json.Json { encodeDefaults = false; ignoreUnknownKeys = true }

    // Enum sırası değişse bile kayıtlı veri bozulmasın diye sabit kodlar
    private val typeCodes = mapOf(
        BrushType.PENCIL to 0, BrushType.MARKER to 1, BrushType.HIGHLIGHTER to 2,
        BrushType.PASTEL to 3, BrushType.ERASER to 4, BrushType.IMAGE to 5,
        BrushType.INK_PEN to 6, BrushType.FINELINER to 7, BrushType.CALLIGRAPHY to 8, BrushType.CHARCOAL to 9,
        BrushType.FLAT_BRUSH to 10, BrushType.HARD_ROUND to 11, BrushType.SOFT_ROUND to 12, BrushType.AIRBRUSH to 13,
        BrushType.STAMP_STAR to 14, BrushType.STAMP_HEART to 15, BrushType.STAMP_LEAF to 16,
        BrushType.PATTERN_DOTS to 17, BrushType.PATTERN_ZIGZAG to 18, BrushType.PATTERN_HATCH to 19,
        BrushType.TEXTURE_SPONGE to 20, BrushType.TEXTURE_CANVAS to 21, BrushType.TEXTURE_SPLATTER to 22,
        BrushType.HALFTONE_DOTS to 23, BrushType.HALFTONE_LINES to 24,
        BrushType.NOTE_PENCIL to 25, BrushType.NOTE_BALLPOINT to 26, BrushType.NOTE_FOUNTAIN to 27, BrushType.NOTE_GEL to 28,
        BrushType.NOTE_FINELINER to 29, BrushType.NOTE_FELT to 30, BrushType.NOTE_HIGHLIGHTER to 31,
        BrushType.TEXT to 32, BrushType.SHAPE to 33, BrushType.DIAGRAM to 34,
        BrushType.FORMULA to 35, BrushType.DOT_PEN to 36, BrushType.BRUSH_3D to 37,
        // v21
        BrushType.INK_TRAIL to 38, BrushType.DRIP_TRAIL to 39, BrushType.PAINT_TRAIL to 40,
        BrushType.PATTERN_CARPET to 41, BrushType.PATTERN_WALLPAPER to 42, BrushType.PATTERN_WOODPAINT to 43,
        BrushType.TEXTURE_SKIN to 44, BrushType.TEXTURE_FUR to 45, BrushType.TEXTURE_PLANT to 46,
        BrushType.TEXTURE_WOOD to 47, BrushType.TEXTURE_IRON to 48, BrushType.TEXTURE_METAL to 49,
    )
    private val fontCodes = mapOf(
        TextFont.SANS to 0, TextFont.SERIF to 1, TextFont.MONO to 2, TextFont.CONDENSED to 3, TextFont.HANDWRITING to 4, TextFont.CASUAL to 5,
        // v13
        TextFont.LIGHT to 6, TextFont.MEDIUM to 7, TextFont.BLACK to 8, TextFont.SMALLCAPS to 9, TextFont.SERIF_MONO to 10,
        TextFont.MONTSERRAT to 11, TextFont.POPPINS to 12, TextFont.NUNITO to 13, TextFont.RALEWAY to 14, TextFont.QUICKSAND to 15,
        TextFont.COMFORTAA to 16, TextFont.OSWALD to 17, TextFont.MERRIWEATHER to 18, TextFont.LORA to 19, TextFont.PLAYFAIR to 20,
        TextFont.ROBOTO_SLAB to 21, TextFont.CAVEAT to 22, TextFont.KALAM to 23, TextFont.PATRICK_HAND to 24,
        TextFont.DANCING_SCRIPT to 25, TextFont.PACIFICO to 26, TextFont.GREAT_VIBES to 27,
        TextFont.AMATIC to 29, TextFont.BEBAS to 30, TextFont.LOBSTER to 31, TextFont.ABRIL to 32, TextFont.RIGHTEOUS to 33,
        TextFont.PIXEL to 34, TextFont.JETBRAINS_MONO to 35, TextFont.SOURCE_CODE to 36, TextFont.TYPEWRITER to 37,
        // v27 çizgi roman
        TextFont.COMIC_RELIEF to 38, TextFont.COMIC_RELIEF_BOLD to 39, TextFont.BANGERS to 40, TextFont.LUCKIEST_GUY to 41,
        TextFont.SHANTELL to 42, TextFont.BOOGALOO to 43, TextFont.GRANDSTANDER to 44, TextFont.BUNGEE to 45,
        TextFont.BUNGEE_SHADE to 46, TextFont.RUBIK_BUBBLES to 47, TextFont.MARGARINE to 48, TextFont.MANSALVA to 49,
    )
    private val paragraphCodes = mapOf(
        TextParagraph.NORMAL to 0, TextParagraph.H1 to 1, TextParagraph.H2 to 2, TextParagraph.H3 to 3,
        TextParagraph.BULLET to 4, TextParagraph.NUMBERED to 5, TextParagraph.CHECKLIST to 6,
    )
    private val codeParagraphs = paragraphCodes.entries.associate { it.value to it.key }
    private val codeFonts = fontCodes.entries.associate { it.value to it.key }
    private val codeTypes = typeCodes.entries.associate { it.value to it.key }

    val EMPTY: ByteArray = encode(emptyList())

    /**
     * Çizim başına kodlanmış baytların önbelleği (nesne kimliğiyle). Çizimler birbirinden bağımsız
     * kodlandığı için yalnızca yeni/değişen çizimler yeniden kodlanır; çıktı önbelleksizle birebir aynıdır.
     */
    class EncodeCache {
        internal var entries = java.util.IdentityHashMap<Stroke, ByteArray>()
        /** Kodlanan baytlar katman sırasına bağlı: katmanlar değişince önbellek boşalır. */
        internal var layerKey: List<String> = emptyList()
        /** v23: belge v8 mi (her çizimde ikinci ek bayrak); değişince önbellek boşalır. */
        internal var v8: Boolean = false
    }

    /** Çözülen belge: sayfalar ve katmanlar. */
    data class Document(val pages: List<List<Stroke>>, val layers: List<Layer>)

    fun encode(strokes: List<Stroke>, cache: EncodeCache? = null): ByteArray = encodePages(listOf(strokes), cache)

    /** Sayfalı kodlama: her sayfa kendi çizim listesidir; [layers] tüm sayfalarda ortak katmanlar. */
    fun encodePages(
        pages: List<List<Stroke>>,
        cache: EncodeCache? = null,
        layers: List<Layer> = listOf(Layer.Default),
    ): ByteArray {
        val docLayers = layers.ifEmpty { listOf(Layer.Default) }
        val layerIndex = HashMap<String, Int>(docLayers.size * 2)
        docLayers.forEachIndexed { i, l -> layerIndex[l.id] = i }
        val raw = Writer()
        raw.varint(docLayers.size.toLong())
        docLayers.forEach { l ->
            raw.string(l.id); raw.string(l.name)
            raw.byte((if (l.visible) 1 else 0) or (if (l.locked) 2 else 0))
        }
        raw.varint(pages.size.coerceAtLeast(1).toLong())
        val key = docLayers.map { it.id }
        val v8 = pages.any { page -> page.any { s -> needsV8(s) } }
        if (cache != null && (cache.layerKey != key || cache.v8 != v8)) { cache.entries = java.util.IdentityHashMap(); cache.layerKey = key; cache.v8 = v8 }
        val previous = cache?.entries
        val next = if (cache != null) java.util.IdentityHashMap<Stroke, ByteArray>() else null
        for (page in pages.ifEmpty { listOf(emptyList()) }) {
            raw.varint(page.size.toLong())
            for (stroke in page) {
                if (next == null) {
                    writeStroke(raw, stroke, layerIndex, v8)
                } else {
                    val bytes = previous?.get(stroke) ?: Writer().also { writeStroke(it, stroke, layerIndex, v8) }.toByteArray()
                    next[stroke] = bytes
                    raw.bytes(bytes)
                }
            }
        }
        if (cache != null && next != null) cache.entries = next
        val dynamics = pages.any { page -> page.any { s -> hasDynamics(s) } }
        val v6 = pages.any { page -> page.any { s -> s.params != null || hasOrient(s) } }
        val v7 = pages.any { page -> page.any { s -> s.space != null } }
        val v9 = pages.any { page -> page.any { s -> needsV9(s) } }

        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(raw.raw(), 0, raw.length())
            deflater.finish()
            val out = ByteArrayOutputStream(raw.length() / 2 + 16)
            out.write(byteArrayOf(MAGIC_0, MAGIC_1, if (v9) VERSION_V9 else if (v8) VERSION_V8 else if (v7) VERSION_V7 else if (v6) VERSION_V6 else if (dynamics) VERSION_V5 else VERSION_V4))
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    fun decode(data: ByteArray): List<Stroke> = decodePages(data).firstOrNull() ?: emptyList()

    /** Sayfalı çözme; eski (tek sayfalı) kayıtlar tek sayfa olarak döner. */
    fun decodePages(data: ByteArray): List<List<Stroke>> = decodeDocument(data).pages

    /** Sayfalar ve katmanlar; v4 öncesi kayıtlar tek (varsayılan) katmanlıdır. */
    fun decodeDocument(data: ByteArray): Document {
        if (data.size < 3 || data[0] != MAGIC_0 || data[1] != MAGIC_1) {
            // Eski (v2 veritabanı) JSON içeriği
            return Document(listOf(decodeStrokes(String(data, Charsets.UTF_8))), listOf(Layer.Default))
        }
        val version = data[2]
        require(version == VERSION || version == VERSION_PAGES || version == VERSION_V3 || version == VERSION_V4 || version == VERSION_V5 || version == VERSION_V6 || version == VERSION_V7 || version == VERSION_V8 || version == VERSION_V9) {
            "Desteklenmeyen içerik sürümü: $version"
        }
        val inflater = Inflater(true)
        val body = try {
            inflater.setInput(data, 3, data.size - 3)
            val out = ByteArrayOutputStream(data.size * 4)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
        val reader = Reader(body)
        val layers = if (version >= VERSION_V4) {
            List(reader.varint().toInt()) {
                val id = reader.string()
                val name = reader.string()
                val flags = reader.byte()
                Layer(id, name, visible = flags and 1 != 0, locked = flags and 2 != 0)
            }.ifEmpty { listOf(Layer.Default) }
        } else listOf(Layer.Default)
        val pageCount = if (version == VERSION) 1 else reader.varint().toInt()
        val pages = List(pageCount) {
            val count = reader.varint().toInt()
            List(count) { readStroke(reader, version.toInt(), layers) }
        }
        return Document(pages, layers)
    }

    /** v23: v8 biçimi gerektiren çizim (yazı kutusu ya da 3B şekil). */
    private fun needsV8(s: Stroke): Boolean = s.hidden || s.model != null || (s.brushType == BrushType.TEXT && (s.text?.box != null || s.text?.path != null))

    /** v42: yol üzerinde yazı içeren eleman. */
    private fun needsV9(s: Stroke): Boolean = s.brushType == BrushType.TEXT && s.text?.path != null

    private fun writeStroke(w: Writer, s: Stroke, layerIndex: Map<String, Int>, v8: Boolean = false) {
        writeStrokeBody(w, s)
        // Ek bayraklar: eleman kırpması (4 köşe, ham float bitleri), paragraf, katman, formül
        val clip = s.clip?.takeIf { it.size == 4 }
        val paragraph = s.text?.paragraph?.takeIf { s.brushType == BrushType.TEXT && it != TextParagraph.NORMAL }
        val layer = s.layerId?.let { layerIndex[it] }?.takeIf { it > 0 }
        val formula = s.formula?.takeIf { s.brushType == BrushType.FORMULA }
        val dynamics = hasDynamics(s)
        val orient = hasOrient(s)
        val params = s.params
        val space = s.space
        w.byte(
            (if (clip != null) EXT_CLIP else 0) or (if (paragraph != null) EXT_PARAGRAPH else 0) or
                (if (layer != null) EXT_LAYER else 0) or (if (formula != null) EXT_FORMULA else 0) or
                (if (dynamics) EXT_DYNAMICS else 0) or (if (orient) EXT_ORIENT else 0) or
                (if (params != null) EXT_PARAMS else 0) or (if (space != null) EXT_SPACE else 0),
        )
        clip?.forEach { w.int32(it.x.toRawBits()); w.int32(it.y.toRawBits()) }
        paragraph?.let { w.byte(paragraphCodes.getValue(it)) }
        layer?.let { w.varint(it.toLong()) }
        formula?.let { w.string(it.source); w.string(it.templateId) }
        if (dynamics) {
            for (p in s.points) w.byte(unitByte(p.pressure))
            for (p in s.points) w.byte(unitByte(p.tilt))
        }
        if (orient) for (p in s.points) w.byte(unitByte(p.orient))
        params?.let { w.string(paramsJson.encodeToString(com.karapp.drawguidance.data.model.BrushParams.serializer(), it)) }
        space?.let { writeSpace(w, it) }
        if (v8) {
            val box = s.text?.box?.takeIf { s.brushType == BrushType.TEXT }
            val model = s.model
            val textPath = s.text?.path?.takeIf { s.brushType == BrushType.TEXT }
            w.byte(
                (if (box != null) EXT2_TEXTBOX else 0) or (if (model != null) EXT2_MODEL else 0) or (if (s.hidden) EXT2_HIDDEN else 0) or
                    (if (textPath != null) EXT2_TEXTPATH else 0),
            )
            box?.let { w.string(paramsJson.encodeToString(com.karapp.drawguidance.data.model.TextBox.serializer(), it)) }
            model?.let { w.string(it.modelId); w.string(it.dataAssetId) }
            textPath?.let { w.string(paramsJson.encodeToString(com.karapp.drawguidance.data.model.TextPathSpec.serializer(), it)) }
        }
    }

    /**
     * v22 3B yer: çerçeve 12 float (ham bitler), özet (64 bit), kamera (32 bit), düzlem noktaları 1/16 birimde
     * nicemlenip farkları zigzag varint ile (sıkıştırmayla birlikte nokta başına ~2–3 bayt).
     */
    private fun writeSpace(w: Writer, sp: com.karapp.drawguidance.data.model.Space3D) {
        sp.frame.forEach { w.int32(it.toRawBits()) }
        w.int32((sp.stamp ushr 32).toInt()); w.int32(sp.stamp.toInt())
        w.int32(sp.view)
        val n = sp.pointCount
        w.varint(n.toLong())
        var pa = 0L; var pb = 0L
        for (i in 0 until n) {
            val a = Math.round(sp.local[2 * i] * SPACE_Q).toLong(); val b = Math.round(sp.local[2 * i + 1] * SPACE_Q).toLong()
            w.varint(zigzag(a - pa)); w.varint(zigzag(b - pb))
            pa = a; pb = b
        }
    }

    private fun readSpace(r: Reader): com.karapp.drawguidance.data.model.Space3D {
        val frame = FloatArray(12) { Float.fromBits(r.int32()) }
        val hi = r.int32().toLong(); val lo = r.int32().toLong() and 0xFFFFFFFFL
        val view = r.int32()
        val n = r.varint().toInt()
        val local = FloatArray(n * 2)
        var pa = 0L; var pb = 0L
        for (i in 0 until n) {
            pa += unzigzag(r.varint()); pb += unzigzag(r.varint())
            local[2 * i] = pa / SPACE_Q; local[2 * i + 1] = pb / SPACE_Q
        }
        return com.karapp.drawguidance.data.model.Space3D(frame, local, (hi shl 32) or lo, view)
    }

    private const val SPACE_Q = 16f

    private fun writeStrokeBody(w: Writer, s: Stroke) {
        val idUuid = isCanonicalUuid(s.id)
        val groupUuid = s.groupId?.let { isCanonicalUuid(it) } ?: false
        val assetUuid = s.assetId?.let { isCanonicalUuid(it) } ?: false
        val quantized = s.points.all { isQuantized(it.x) && isQuantized(it.y) }
        val rgb = isRgb(s.colorHex)
        var flags = 0
        if (idUuid) flags = flags or F_ID_UUID
        if (s.groupId != null) flags = flags or F_GROUP
        if (groupUuid) flags = flags or F_GROUP_UUID
        if (s.assetId != null) flags = flags or F_ASSET
        if (assetUuid) flags = flags or F_ASSET_UUID
        if (quantized) flags = flags or F_QUANTIZED
        if (rgb) flags = flags or F_COLOR_RGB
        val dynamics = s.flow.toRawBits() != 1f.toRawBits() || s.strength.toRawBits() != 1f.toRawBits()
        if (dynamics) flags = flags or F_DYNAMICS
        w.byte(flags)
        w.id(s.id, idUuid)
        w.byte(typeCodes.getValue(s.brushType))
        if (rgb) {
            val v = s.colorHex.substring(1).toInt(16)
            w.byte(v shr 16 and 0xFF); w.byte(v shr 8 and 0xFF); w.byte(v and 0xFF)
        } else {
            w.string(s.colorHex)
        }
        w.int32(s.sizePx.toRawBits())
        w.int32(s.opacity.toRawBits())
        if (dynamics) { w.int32(s.flow.toRawBits()); w.int32(s.strength.toRawBits()) }
        w.varint(zigzag(s.order.toLong()))
        s.groupId?.let { w.id(it, groupUuid) }
        s.assetId?.let { w.id(it, assetUuid) }
        w.varint(s.points.size.toLong())
        if (quantized) {
            var px = 0L
            var py = 0L
            for (p in s.points) {
                val qx = (p.x * QUANT).roundToLong()
                val qy = (p.y * QUANT).roundToLong()
                w.varint(zigzag(qx - px)); w.varint(zigzag(qy - py))
                px = qx; py = qy
            }
        } else {
            var bx = 0
            var by = 0
            for (p in s.points) {
                val x = p.x.toRawBits()
                val y = p.y.toRawBits()
                w.int32(x xor bx); w.int32(y xor by)
                bx = x; by = y
            }
        }
        // Şekil ve diyagram: vektör yapısı, biçimi ve (varsa) grafik bilgisi JSON olarak
        if (s.brushType == BrushType.SHAPE) {
            w.string(s.shape?.let { specWriteJson.encodeToString(ShapeSpec.serializer(), it) } ?: "")
        }
        if (s.brushType == BrushType.DIAGRAM) {
            w.string(s.diagram?.let { specWriteJson.encodeToString(DiagramSpec.serializer(), it) } ?: "")
        }
        // Yazı elemanı: içerik + yazı tipi (yalnızca bu türde yazılır; eski kayıtlar etkilenmez)
        if (s.brushType == BrushType.TEXT) {
            val spec = s.text
            w.byte(if (spec == null) 0 else 1)
            if (spec == null) return
            w.string(spec.content)
            w.byte(fontCodes.getValue(spec.font))
            // 0-2: kalın/eğik/altı çizili, 3-4: hizalama, 5: harf kenarlığı var (renk + kalınlık izler)
            val outline = spec.outlineColorHex
            val hasOutline = outline != null && spec.outlineWidth > 0f
            w.byte(
                (if (spec.bold) 1 else 0) or (if (spec.italic) 2 else 0) or (if (spec.underline) 4 else 0) or
                    (spec.align.ordinal shl 3) or (if (hasOutline) TEXT_EXTRA else 0),
            )
            if (hasOutline) {
                w.string(outline!!)
                w.int32(spec.outlineWidth.toRawBits())
            }
        }
    }

    private fun readStroke(r: Reader, version: Int, layers: List<Layer>): Stroke {
        val stroke = readStrokeBody(r, version)
        if (version < VERSION_V3) return stroke
        val ext = r.byte()
        val clip = if (ext and EXT_CLIP != 0) List(4) { StrokePoint(Float.fromBits(r.int32()), Float.fromBits(r.int32())) } else null
        val paragraph = if (ext and EXT_PARAGRAPH != 0) codeParagraphs[r.byte()] ?: TextParagraph.NORMAL else null
        val layer = if (ext and EXT_LAYER != 0) layers.getOrNull(r.varint().toInt())?.id else null
        val formula = if (ext and EXT_FORMULA != 0) FormulaSpec(r.string(), r.string()) else null
        var out = if (clip != null) stroke.copy(clip = clip) else stroke
        if (ext and EXT_DYNAMICS != 0) {
            val n = stroke.points.size
            val pressure = FloatArray(n) { r.byte() / 255f }
            val tilt = FloatArray(n) { r.byte() / 255f }
            val orient = if (ext and EXT_ORIENT != 0) FloatArray(n) { r.byte() / 255f } else null
            out = out.copy(points = stroke.points.mapIndexed { i, p -> StrokePoint(p.x, p.y, pressure[i], tilt[i], orient?.get(i) ?: 0f) })
        }
        if (ext and EXT_PARAMS != 0) {
            val json = r.string()
            val params = runCatching { paramsJson.decodeFromString(com.karapp.drawguidance.data.model.BrushParams.serializer(), json) }.getOrNull()
            if (params != null) out = out.copy(params = params)
        }
        if (version >= VERSION_V7 && ext and EXT_SPACE != 0) out = out.copy(space = readSpace(r))
        if (version >= VERSION_V8) {
            val ext2 = r.byte()
            if (ext2 and EXT2_TEXTBOX != 0) {
                val box = runCatching { paramsJson.decodeFromString(com.karapp.drawguidance.data.model.TextBox.serializer(), r.string()) }.getOrNull()
                if (box != null && out.text != null) out = out.copy(text = out.text!!.copy(box = box))
            }
            if (ext2 and EXT2_MODEL != 0) out = out.copy(model = com.karapp.drawguidance.data.model.Model3DLink(r.string(), r.string()))
            if (ext2 and EXT2_HIDDEN != 0) out = out.copy(hidden = true)
            if (ext2 and EXT2_TEXTPATH != 0) {
                val path = runCatching { paramsJson.decodeFromString(com.karapp.drawguidance.data.model.TextPathSpec.serializer(), r.string()) }.getOrNull()
                if (path != null && out.text != null) out = out.copy(text = out.text!!.copy(path = path))
            }
        }
        if (paragraph != null && out.text != null) out = out.copy(text = out.text!!.copy(paragraph = paragraph))
        if (layer != null) out = out.copy(layerId = layer)
        if (formula != null) out = out.copy(formula = formula)
        return out
    }

    private fun readStrokeBody(r: Reader, version: Int): Stroke {
        val flags = r.byte()
        val id = r.id(flags and F_ID_UUID != 0)
        val type = codeTypes[r.byte()] ?: BrushType.PENCIL
        val color = if (flags and F_COLOR_RGB != 0) {
            val v = (r.byte() shl 16) or (r.byte() shl 8) or r.byte()
            "#" + v.toString(16).uppercase().padStart(6, '0')
        } else {
            r.string()
        }
        val size = Float.fromBits(r.int32())
        val opacity = Float.fromBits(r.int32())
        val flow = if (flags and F_DYNAMICS != 0) Float.fromBits(r.int32()) else 1f
        val strength = if (flags and F_DYNAMICS != 0) Float.fromBits(r.int32()) else 1f
        val order = unzigzag(r.varint()).toInt()
        val group = if (flags and F_GROUP != 0) r.id(flags and F_GROUP_UUID != 0) else null
        val asset = if (flags and F_ASSET != 0) r.id(flags and F_ASSET_UUID != 0) else null
        val n = r.varint().toInt()
        val points = ArrayList<StrokePoint>(n)
        if (flags and F_QUANTIZED != 0) {
            var qx = 0L
            var qy = 0L
            repeat(n) {
                qx += unzigzag(r.varint()); qy += unzigzag(r.varint())
                points += StrokePoint(qx / QUANT, qy / QUANT)
            }
        } else {
            var bx = 0
            var by = 0
            repeat(n) {
                bx = bx xor r.int32(); by = by xor r.int32()
                points += StrokePoint(Float.fromBits(bx), Float.fromBits(by))
            }
        }
        val shape = if (type == BrushType.SHAPE) r.string().takeIf { it.isNotEmpty() }?.let { specJson.decodeFromString(ShapeSpec.serializer(), it) } else null
        val diagram = if (type == BrushType.DIAGRAM) r.string().takeIf { it.isNotEmpty() }?.let { specJson.decodeFromString(DiagramSpec.serializer(), it) } else null
        val text = if (type == BrushType.TEXT && r.byte() == 1) {
            val content = r.string()
            val font = codeFonts[r.byte()] ?: TextFont.SANS
            val style = r.byte()
            var outlineColor: String? = null
            var outlineWidth = 0f
            if (style and TEXT_EXTRA != 0) {
                if (version >= VERSION_V3) {
                    outlineColor = r.string()
                    outlineWidth = Float.fromBits(r.int32())
                } else {
                    // v9 yazı kutusu: kenar rengi/kalınlığı harf kenarlığına dönüştürülür
                    val box = specJson.decodeFromString(ShapeStyle.serializer(), r.string())
                    if (box.strokeWidth > 0.05f) {
                        outlineColor = box.strokeColorHex
                        outlineWidth = box.strokeWidth
                    }
                }
            }
            TextSpec(
                content, font, bold = style and 1 != 0, italic = style and 2 != 0, underline = style and 4 != 0,
                align = TextAlignKind.entries.getOrElse(style shr 3 and 3) { TextAlignKind.LEFT },
                outlineColorHex = outlineColor,
                outlineWidth = outlineWidth,
            )
        } else null
        return Stroke(id, points, color, type, size, opacity, order, group, asset, flow, strength, text, shape, diagram)
    }

    /** Koordinat 1/100'lük tamsayıdan birebir (bit düzeyinde) geri üretilebiliyor mu? */
    private fun isQuantized(v: Float): Boolean {
        if (!v.isFinite() || kotlin.math.abs(v) > 1e7f) return false
        val q = (v * QUANT).roundToLong()
        return (q / QUANT).toRawBits() == v.toRawBits()
    }

    /** Değeri 1/100'lük adıma yuvarlar; kodlayıcı bu değerleri en kompakt biçimde yazar. */
    fun quantize(v: Float): Float = ((v * QUANT).roundToLong() / QUANT)

    /** v18: 0..1 değerin kayıttaki (1/255) adımı; kaydedilip okununca aynı değer döner. */
    fun quantizeUnit(v: Float): Float = unitByte(v) / 255f

    private fun unitByte(v: Float): Int = if (v.isNaN()) 255 else (v.coerceIn(0f, 1f) * 255f).roundToInt()

    private fun hasDynamics(s: Stroke): Boolean = s.points.any { it.hasDynamics }

    private fun hasOrient(s: Stroke): Boolean = s.points.any { it.orient != 0f }

    /**
     * Kanonik (küçük harf onaltılık, 8-4-4-4-12) UUID metni mi: UUID.fromString(value).toString() == value ile aynı
     * sonuç, nesne ve istisna üretmeden.
     */
    private fun isCanonicalUuid(value: String): Boolean {
        if (value.length != 36) return false
        for (i in 0 until 36) {
            val c = value[i]
            if (i == 8 || i == 13 || i == 18 || i == 23) { if (c != '-') return false }
            else if (!(c in '0'..'9' || c in 'a'..'f')) return false
        }
        return true
    }

    /** "#RRGGBB" (büyük harf onaltılık) mı; düzenli ifade yerine doğrudan karşılaştırma. */
    private fun isRgb(hex: String): Boolean {
        if (hex.length != 7 || hex[0] != '#') return false
        for (i in 1 until 7) { val c = hex[i]; if (!(c in '0'..'9' || c in 'A'..'F')) return false }
        return true
    }

    private fun zigzag(v: Long): Long = (v shl 1) xor (v shr 63)
    private fun unzigzag(v: Long): Long = (v ushr 1) xor -(v and 1)

    /**
     * v36 kilitsiz bayt yazıcı: büyüyen tek dizi (ByteArrayOutputStream'in her baytta kilit alan yazımı yerine).
     * Üretilen baytlar öncekiyle birebir aynıdır.
     */
    private class Writer(capacity: Int = 256) {
        private var buf = ByteArray(capacity)
        private var size = 0
        private fun ensure(extra: Int) {
            if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
        }
        fun bytes(b: ByteArray) { ensure(b.size); System.arraycopy(b, 0, buf, size, b.size); size += b.size }
        fun byte(v: Int) { if (size == buf.size) ensure(1); buf[size++] = v.toByte() }
        fun int32(v: Int) {
            ensure(4)
            buf[size] = (v shr 24).toByte(); buf[size + 1] = (v shr 16).toByte(); buf[size + 2] = (v shr 8).toByte(); buf[size + 3] = v.toByte()
            size += 4
        }
        fun varint(value: Long) {
            ensure(10)
            var v = value
            while (v and 0x7FL.inv() != 0L) { buf[size++] = ((v and 0x7F) or 0x80).toByte(); v = v ushr 7 }
            buf[size++] = v.toByte()
        }
        fun string(s: String) { val b = s.toByteArray(Charsets.UTF_8); varint(b.size.toLong()); bytes(b) }
        fun id(s: String, uuid: Boolean) {
            if (!uuid) return string(s)
            ensure(16)
            // Kanonik UUID metninden doğrudan 16 bayt (ayrıştırma nesnesi yok)
            var k = 0
            var hi = -1
            for (c in s) {
                if (c == '-') continue
                val d = if (c <= '9') c - '0' else c - 'a' + 10
                if (hi < 0) hi = d else { buf[size + k] = ((hi shl 4) or d).toByte(); k++; hi = -1 }
            }
            size += 16
        }
        /** Ham dizi ve dolu uzunluk (kopyasız sıkıştırma için). */
        fun raw(): ByteArray = buf
        fun length(): Int = size
        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    private class Reader(private val data: ByteArray) {
        private var pos = 0
        fun byte(): Int = data[pos++].toInt() and 0xFF
        fun int32(): Int = (byte() shl 24) or (byte() shl 16) or (byte() shl 8) or byte()
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                val b = byte()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        fun string(): String { val n = varint().toInt(); val s = String(data, pos, n, Charsets.UTF_8); pos += n; return s }
        fun id(uuid: Boolean): String {
            if (!uuid) return string()
            var msb = 0L
            var lsb = 0L
            repeat(8) { msb = (msb shl 8) or byte().toLong() }
            repeat(8) { lsb = (lsb shl 8) or byte().toLong() }
            return UUID(msb, lsb).toString()
        }
    }
}
