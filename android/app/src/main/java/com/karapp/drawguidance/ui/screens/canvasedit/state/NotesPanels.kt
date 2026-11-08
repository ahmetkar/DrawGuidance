package com.karapp.drawguidance.ui.screens.canvasedit.state

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.comic.PanelNode
import com.karapp.drawguidance.content.comic.PanelPage
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapePath
import java.util.UUID
import kotlin.math.min

/**
 * v45 not alma panelleri: çizim alanını çerçeveli bölümlere ayıran kare/dikdörtgen yerleşimler (sütunlar, satırlar,
 * ızgaralar, büyük alan + yan sütun, Cornell…; tam kare alanlı 2×2, 3×3 ve kare + not alanı). Geometri çizgi roman şablonlarının panel motorundan ([PanelPage])
 * gelir; görünüm not alanı gibidir: ince yuvarlak köşeli çerçeve ve hafif renk tonu (çizgi romandaki kalın siyah
 * çizgiler ve aralık yok). Her alan ayrı bir şekil elemanıdır ([KIND]): seçilir, taşınır, ölçeklenir, silinir ve
 * kayıtla birlikte saklanır. Çizgi roman şablonu değildir (PanelRef taşımaz; şablon düzenlemesi uygulanmaz).
 */
object NotesPanels {
    /** Not paneli elemanlarının şekil türü (ShapeSpec.kind). */
    const val KIND = "notes.panel"

    /** Sayfa kenar boşluğu ve alanlar arası aralık (sayfanın kısa kenarına oran). */
    const val MARGIN = 0.05f
    const val GAP = 0.024f

    /** Çerçeve ve dolgu: mavi-gri ince çizgi, çok açık mavi ton. */
    const val FRAME_COLOR = "#90A4AE"
    const val TINT_COLOR = "#E3F2FD"
    const val TINT_OPACITY = 0.45f

    /** Köşe yuvarlaklığı ve çizgi kalınlığı (sayfanın kısa kenarına oran). */
    const val CORNER = 0.018f
    const val LINE = 0.0022f

    /**
     * Bir yerleşim: kimlik, adı (string kaynağı) ve alanlarının dikdörtgenleri. Dikdörtgen yerleşimler panel motorunun
     * düzeninden ([page]) gelir; v45 kare yerleşimlerde alanlar sayfanın oranından bağımsız olarak tam karedir.
     */
    class Layout(
        val id: String,
        val title: Int,
        val areaCount: Int,
        /** Panel motorundaki düzeni (dikdörtgen yerleşimler; kare yerleşimlerde null). */
        val page: PanelPage?,
        /** Alanlar kare mi (eni boyuna eşit). */
        val square: Boolean,
        private val rectsOf: (Float, Float) -> List<FloatArray>,
    ) {
        /** [w]×[h] sayfadaki alanlar okuma sırasıyla: her biri [sol, üst, sağ, alt]. */
        fun rects(w: Float, h: Float): List<FloatArray> = rectsOf(w, h)
    }

    private class B {
        var next = 0
        fun p() = PanelNode.Leaf(next++)
        fun rows(vararg w: Float, c: B.() -> List<PanelNode>) = PanelNode.Split(true, c(), w.toList())
        fun cols(vararg w: Float, c: B.() -> List<PanelNode>) = PanelNode.Split(false, c(), w.toList())
    }

    private fun layout(id: String, title: Int, root: B.() -> PanelNode): Layout {
        val page = PanelPage(B().root(), margin = MARGIN, gutter = GAP)
        return Layout(id, title, page.leafIds().size, page, square = false) { w, h -> page.shapes(w, h).map { it.bounds() } }
    }

    /**
     * v45 [cols]×[rows] eş kare alan: kenar, kullanılabilir alana sığan en büyük kare; ızgara yatayda ortalanır ve
     * üst kenar boşluğuna yaslanır (altta kalan yer serbest yazı alanıdır).
     */
    private fun squares(id: String, title: Int, cols: Int, rows: Int) = Layout(id, title, cols * rows, null, square = true) { w, h ->
        val s = min(w, h)
        val m = s * MARGIN
        val g = s * GAP
        val side = min((w - 2 * m - (cols - 1) * g) / cols, (h - 2 * m - (rows - 1) * g) / rows)
        val left = (w - (cols * side + (cols - 1) * g)) / 2f
        List(cols * rows) { i ->
            val c = i % cols
            val r = i / cols
            val l = left + c * (side + g)
            val t = m + r * (side + g)
            floatArrayOf(l, t, l + side, t + side)
        }
    }

    /**
     * v45 bir kare alan (şekil, grafik, deney çizimi için) ve kalan yerde not alanı: dikey sayfada kare üstte (en çok
     * kullanılabilir boyun %60'ı), not alanı altında tam en; yatay sayfada kare solda, not alanı sağda.
     */
    private fun squareAndNotes(id: String, title: Int) = Layout(id, title, 2, null, square = true) { w, h ->
        val s = min(w, h)
        val m = s * MARGIN
        val g = s * GAP
        val iw = w - 2 * m
        val ih = h - 2 * m
        if (ih >= iw) {
            val side = min(iw, ih * SQUARE_SHARE)
            val l = (w - side) / 2f
            listOf(floatArrayOf(l, m, l + side, m + side), floatArrayOf(m, m + side + g, w - m, h - m))
        } else {
            val side = min(ih, iw * SQUARE_SHARE)
            val t = (h - side) / 2f
            listOf(floatArrayOf(m, t, m + side, t + side), floatArrayOf(m + side + g, m, w - m, h - m))
        }
    }

    /** Kare + not alanı yerleşiminde karenin kullanılabilir uzun kenardaki en büyük payı. */
    const val SQUARE_SHARE = 0.6f

    val layouts: List<Layout> = listOf(
        layout("two_cols", R.string.v45_panel_two_cols) { cols(1f, 1f) { listOf(p(), p()) } },
        layout("three_cols", R.string.v45_panel_three_cols) { cols(1f, 1f, 1f) { listOf(p(), p(), p()) } },
        layout("two_rows", R.string.v45_panel_two_rows) { rows(1f, 1f) { listOf(p(), p()) } },
        layout("three_rows", R.string.v45_panel_three_rows) { rows(1f, 1f, 1f) { listOf(p(), p(), p()) } },
        layout("grid_2x2", R.string.v45_panel_grid_2x2) { rows(1f, 1f) { List(2) { cols(1f, 1f) { listOf(p(), p()) } } } },
        layout("grid_2x3", R.string.v45_panel_grid_2x3) { rows(1f, 1f, 1f) { List(3) { cols(1f, 1f) { listOf(p(), p()) } } } },
        layout("main_side", R.string.v45_panel_main_side) { cols(2.2f, 1f) { listOf(p(), p()) } },
        layout("side_main", R.string.v45_panel_side_main) { cols(1f, 2.2f) { listOf(p(), p()) } },
        // Cornell: solda anahtar sözcük sütunu (%30), sağda not alanı, altta özet şeridi
        layout("cornell", R.string.v45_panel_cornell) { rows(0.78f, 0.22f) { listOf(cols(0.3f, 0.7f) { listOf(p(), p()) }, p()) } },
        layout("header_two_cols", R.string.v45_panel_header_two_cols) { rows(0.16f, 0.84f) { listOf(p(), cols(1f, 1f) { listOf(p(), p()) }) } },
        // v45 kare yerleşimler: alanlar her sayfa oranında tam kare
        squares("square_2x2", R.string.v45_panel_square_2x2, 2, 2),
        squares("square_3x3", R.string.v45_panel_square_3x3, 3, 3),
        squareAndNotes("square_notes", R.string.v45_panel_square_notes),
    )

    private val byId = layouts.associateBy { it.id }

    fun byId(id: String): Layout? = byId[id]

    fun isNotesPanel(s: Stroke): Boolean = s.shape?.kind == KIND

    /** Not alanlarının biçimi ([w]×[h] sayfaya göre). */
    fun style(w: Float, h: Float): ShapeStyle {
        val s = min(w, h)
        return ShapeStyle(
            strokeColorHex = FRAME_COLOR, strokeWidth = (s * LINE).coerceIn(1.5f, 6f),
            fillColorHex = TINT_COLOR, fillOpacity = TINT_OPACITY, cornerRadius = s * CORNER,
        )
    }

    /**
     * [layout] yerleşiminin alanları [w]×[h] sayfada: her alan dört köşeli kapalı bir şekil (okuma sırasıyla). Alanlar
     * sayfanın içindedir (kenar boşluğu [MARGIN], aralık [GAP]).
     */
    fun build(layout: Layout, w: Float, h: Float, order: Int = 0): List<Stroke> {
        val style = style(w, h)
        return layout.rects(w, h).mapIndexedNotNull { i, r ->
            // Saat yönünde dört köşe (sol üstten), kapalı yol
            val pts = listOf(Offset(r[0], r[1]), Offset(r[2], r[1]), Offset(r[2], r[3]), Offset(r[0], r[3]), Offset(r[0], r[1]))
            ShapeFactory.fromPaths(KIND, listOf(ShapePath(pts, closed = true)), style, null, UUID.randomUUID().toString(), order + i)
        }
    }
}
