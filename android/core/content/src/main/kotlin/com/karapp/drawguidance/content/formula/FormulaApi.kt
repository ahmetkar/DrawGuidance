package com.karapp.drawguidance.content.formula

/**
 * Glif biçemi. Uygulama bunu serif bir Typeface'e eşler: değişkenler (Latin/küçük Yunan harfleri) İTALİK;
 * rakamlar, fonksiyon adları (sin, log…), işleçler ve \text DÜZ (ROMAN); \mathbf KALIN.
 */
enum class GlyphStyle { ROMAN, ITALIC, BOLD, BOLD_ITALIC }

/**
 * Platformun (Android Paint / testlerde Java2D) yazı ölçüleri. Boyutlar dizgi koordinatlarıyla aynı birimdedir.
 */
interface GlyphMetrics {
    /** [text]'in [size] boyutundaki ilerleme genişliği (Paint.measureText). */
    fun advance(text: String, size: Float, style: GlyphStyle): Float

    /** Yazı tipinin taban çizgisi üstündeki yüksekliği (pozitif). */
    fun ascent(size: Float): Float

    /** Yazı tipinin taban çizgisi altındaki derinliği (pozitif). */
    fun descent(size: Float): Float

    /**
     * İSTEĞE BAĞLI (varsayılan null). [text] taban çizgisi y=0 ve x=0'da çizildiğinde mürekkebin kutusu
     * `[left, top, right, bottom]` (y AŞAĞI; top taban çizgisi üstünde negatif) — Android'de doğrudan
     * `Paint.getTextBounds` sonucudur. Verilirse kesir çizgisi yazı tipinin gerçek matematik eksenine ('−' ortası),
     * kök/aksan/üs konumları gerçek harf yüksekliklerine, italik düzeltmesi gerçek taşmaya göre hesaplanır.
     * null dönerse motor tipik bir serif yazı tipi (Noto Serif) için tablodan tahmin eder.
     * Motor bunu tek dizgi başına bir kez, 100 boyutunda sorar ve oranlar.
     */
    fun inkBounds(text: String, size: Float, style: GlyphStyle): FloatArray? = null
}

/** Çizim komutları. Koordinatlar [FormulaLayout] ile aynı sistemdedir (y aşağı). */
sealed interface DrawOp {
    /** Taban çizgisi y'de olan yazı. slot = bu glifin ait olduğu düzenlenebilir yuvanın adı (yoksa null). */
    data class Glyph(
        val text: String, val x: Float, val y: Float, val size: Float, val style: GlyphStyle, val slot: String? = null,
    ) : DrawOp

    /** Düz çizgi (kesir çizgisi, üst çizgi, vektör oku gövdesi, kök üst çizgisi). Uçlar düz (butt) çizilir. */
    data class Rule(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val thickness: Float) : DrawOp

    /**
     * Çoklu çizgi (kök işareti, esneyen ayraçlar, integral, ok uçları, noktalar). filled = içi dolu çokgen olarak
     * doldur (bu durumda thickness 0'dır, kenar çizilmez); değilse yuvarlak uç/birleşimle [thickness] kalınlığında çiz.
     */
    data class Stroke(
        val points: List<Float>, /* x0,y0,x1,y1,... */
        val thickness: Float, val closed: Boolean = false, val filled: Boolean = false,
    ) : DrawOp
}

/** Düzenlenebilir yuvanın dokunma/vurgulama alanı (aynı ad birden çok yerde geçebilir → birden çok bölge). */
data class SlotRegion(val name: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Dizilmiş formül. Koordinatlar: başlangıç ana taban çizgisinin sol ucu, x sağa, y AŞAĞI (kutunun üstü = -ascent,
 * altı = +descent). İtalik harflerin mürekkebi kutudan ~0.1 em taşabilir; çizerken her yana küçük bir pay bırakın.
 */
class FormulaLayout(
    val width: Float, val ascent: Float, val descent: Float, val ops: List<DrawOp>, val slots: List<SlotRegion>,
) {
    val height: Float get() = ascent + descent

    override fun toString() = "FormulaLayout(w=$width, a=$ascent, d=$descent, ops=${ops.size}, slots=${slots.size})"
}

/** Kaynaktaki bir yuva: adı ve şimdiki (varsayılan) içeriği (TeX). */
data class Slot(val name: String, val value: String)

data class FormulaTemplate(val id: String, val title: String, val source: String)

data class FormulaCategory(val id: String, val title: String, val templates: List<FormulaTemplate>)
