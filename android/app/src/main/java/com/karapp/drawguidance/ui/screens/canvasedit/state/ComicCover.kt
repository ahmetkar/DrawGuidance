package com.karapp.drawguidance.ui.screens.canvasedit.state

import com.karapp.drawguidance.content.comic.PanelNode
import com.karapp.drawguidance.content.comic.PanelPage
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextEffects
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.ui.drawing.TextRenderer
import java.util.UUID
import kotlin.math.min

/**
 * v45 çizgi roman kapak sayfası şablonu. Kapak üç sıradan elemandan oluşur (hepsi seçilir, taşınır, düzenlenir):
 * - Sayfayı kenarına kadar kaplayan resim alanı: kenar boşluksuz tek panel (çizgi roman paneli; mekân arka planı
 *   panele sığdırılabilir, şablon seçilirse onun yerini alır).
 * - Üstte büyük başlık: çizgi roman yazı tipiyle, büyük harf, beyaz kenarlıklı (resmin üstünde de okunur).
 * - Altta isteğe bağlı alt başlık / yazar alanı (silinebilir).
 *
 * Kapak sayfa numarası, üst bilgi ve alt bilgi almaz (bkz. [com.karapp.drawguidance.data.model.PageBackground.cover]).
 */
object ComicCover {
    /** Resim alanının oranları: kenar boşluğu yok (tam taşma). */
    val ART_PAGE = PanelPage(PanelNode.Leaf(0), margin = 0f)

    /** Başlığın boyu (sayfanın kısa kenarına oran) ve kutusunun eni (sayfa enine oran). */
    const val TITLE_SIZE = 0.11f
    const val TITLE_WIDTH = 0.86f
    /** Başlık kutusunun üst kenarı (sayfa boyuna oran). */
    const val TITLE_TOP = 0.05f

    /** Alt başlığın boyu, kutusunun eni ve alt kenarının yeri (sayfa boyuna oran). */
    const val SUBTITLE_SIZE = 0.042f
    const val SUBTITLE_WIDTH = 0.7f
    const val SUBTITLE_BOTTOM = 0.95f

    /** Alt başlık alanının öntanımlı yazısı (Türkçe kaynak; resources/i18n tabloları ile çevrilir). */
    const val SUBTITLE_TEXT = "Yazar adı"
    /** Tuvalin adı boşsa başlık alanının yazısı. */
    const val TITLE_TEXT = "Başlık"

    fun subtitlePlaceholder(): String = com.karapp.drawguidance.i18n.I18n.tr(SUBTITLE_TEXT)
    fun titleFor(canvasName: String): String = canvasName.trim().ifEmpty { com.karapp.drawguidance.i18n.I18n.tr(TITLE_TEXT) }

    const val TITLE_FONT_COLOR = "#111111"
    const val OUTLINE = "#FFFFFF"

    /** Kapak elemanlarının sırası: resim alanı, başlık, alt başlık. */
    fun build(width: Int, height: Int, title: String, subtitle: String?): List<Stroke> {
        val w = width.toFloat()
        val h = height.toFloat()
        val short = min(w, h)
        val q = ContentCodec::quantize
        val out = ArrayList<Stroke>(3)
        // Resim alanı: sayfanın tamamı (çizgi roman paneli)
        val line = (short * 0.006f).coerceIn(2f, 14f)
        out += PanelSets.build(ART_PAGE, UUID.randomUUID().toString(), w, h, ShapeStyle(strokeColorHex = ComicController.PANEL_INK, strokeWidth = line), 0)
        // Başlık
        val titleSize = (short * TITLE_SIZE).coerceIn(TextController.MIN_TEXT_SIZE, TextController.MAX_TEXT_SIZE)
        val titleSpec = TextSpec(
            content = titleFor(title), font = TextFont.BANGERS, align = TextAlignKind.CENTER,
            outlineColorHex = OUTLINE, outlineWidth = titleSize * 0.06f,
            box = TextBox(width = w * TITLE_WIDTH, effects = TextEffects(caps = true, letterSpacing = 0.02f)),
        )
        val (tw, _) = TextRenderer.measure(titleSpec, titleSize)
        out += text(titleSpec, titleSize, (w - tw) / 2f, h * TITLE_TOP, q)
        // Alt başlık / yazar (isteğe bağlı)
        val sub = subtitle?.trim().orEmpty()
        if (sub.isNotEmpty()) {
            val subSize = (short * SUBTITLE_SIZE).coerceIn(TextController.MIN_TEXT_SIZE, TextController.MAX_TEXT_SIZE)
            val subSpec = TextSpec(
                content = sub, font = TextFont.COMIC_RELIEF_BOLD, align = TextAlignKind.CENTER,
                outlineColorHex = OUTLINE, outlineWidth = subSize * 0.05f,
                box = TextBox(width = w * SUBTITLE_WIDTH),
            )
            val (sw, sh) = TextRenderer.measure(subSpec, subSize)
            out += text(subSpec, subSize, (w - sw) / 2f, h * SUBTITLE_BOTTOM - sh, q)
        }
        return out.mapIndexed { i, s -> s.copy(order = i) }
    }

    /**
     * v46 şablondan oluşturulan tuvalde kapak başlığı tuvalin adı olur: aynı biçim, boyut ve üst kenar; yazı sayfada
     * yatay ortalanır. [title] başlık elemanı değilse (yazı değil) olduğu gibi döner.
     */
    fun retitle(title: Stroke, pageWidth: Int, canvasName: String): Stroke {
        val spec = title.text ?: return title
        val q = ContentCodec::quantize
        val next = spec.copy(content = titleFor(canvasName))
        val (tw, _) = TextRenderer.measure(next, title.sizePx)
        val top = title.points.minOf { it.y }
        val points = TextRenderer.quadAt(next, title.sizePx, (pageWidth - tw) / 2f, top).map { StrokePoint(q(it.x), q(it.y)) }
        return title.copy(text = next, points = points)
    }

    private fun text(spec: TextSpec, size: Float, left: Float, top: Float, q: (Float) -> Float): Stroke = Stroke(
        id = UUID.randomUUID().toString(),
        points = TextRenderer.quadAt(spec, size, left, top).map { StrokePoint(q(it.x), q(it.y)) },
        colorHex = TITLE_FONT_COLOR, brushType = BrushType.TEXT, sizePx = size, opacity = 1f, order = 0, text = spec,
    )
}
