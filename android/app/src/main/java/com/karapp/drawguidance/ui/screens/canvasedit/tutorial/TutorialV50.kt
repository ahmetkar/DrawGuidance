package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.trace.EdgeMode
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.TemplateGhost
import java.util.UUID

/*
 * v50 "Resimden çizim" eğitimi (çizim modları: resim, çizgi roman, basit resim; çocuk modunda yalnız kalıp):
 * hazır bir resim (demlik) eklenir, otomatik vektörleştirilir, resim kaldırılınca kalan vektör çizgiler şekil kalıbı
 * olur, kalıp yana taşınıp kenarları boyunca kalemle çizilerek kopyalanır; son adımda kütüphaneden bir kalıp (kedi)
 * seçilip çizilir. Resim uygulamanın kendi çizgi roman nesnelerinden; eğitim kayıt dışı çalışır.
 */
internal object TutorialV50 {
    /** Vektörleştirilen hazır resim (uygulamanın çizgi roman nesneleri: demlik). */
    const val IMAGE = "comic/objects/it_food_14.webp"
    private const val INK = "#2B2F4A"
    private const val COPY_INK = "#1565C0"
    private const val CAT_INK = "#C2185B"

    /** Gelişmiş türlerde (resim, çizgi roman) Ekle'deki "Resim ekle", basit türlerde tek menüdeki Resim. */
    private fun imageAnchor(simple: Boolean) = if (simple) TutorialAnchor.simple("image") else TutorialAnchor.add("image")
    private fun templateAnchor(simple: Boolean) = if (simple) TutorialAnchor.simple("template") else TutorialAnchor.TEMPLATE

    private fun TutorialDemo.imageRect(): Rect {
        val portrait = h >= w
        return if (portrait) Rect(w * .05f, h * .05f, w * .53f, h * .42f) else Rect(w * .05f, h * .1f, w * .4f, h * .85f)
    }

    /** Kalıbın kopyalanacağı yer: resmin sağı (yatayda) ya da altı ortası (dikeyde değil, sağ üst). */
    private fun TutorialDemo.copyShift(r: Rect): Offset = if (h >= w) Offset(w * .43f, 0f) else Offset(w * .5f, 0f)

    private fun TutorialDemo.image(): Stroke? = host.canvas()?.strokes?.firstOrNull { it.brushType == BrushType.IMAGE }
    private fun TutorialDemo.traced(): Stroke? = host.canvas()?.strokes?.lastOrNull { it.shape?.kind == ShapeRegistry.TRACED }

    /** Resim (yoksa) eklenir. */
    private suspend fun TutorialDemo.ensureImage(): Stroke? {
        image()?.let { return it }
        val img = host.assetImage(IMAGE, imageRect(), fill = false) ?: return null
        place(imageRect().center, listOf(img))
        pause(500)
        return image()
    }

    /** Resim vektörleştirilir (resmin üstüne; yoksa) ve vektör çizgiler döner. */
    private suspend fun TutorialDemo.ensureTraced(): Stroke? {
        traced()?.let { return it }
        val img = ensureImage() ?: return null
        return host.vectorizeImage(img.id, EdgeMode.AUTO, u * 0.005f, keepImage = true)
    }

    /** Vektör çizgilerden kalıp kurulur ve [by] kadar yana taşınır (parmak taşıma tutamağını sürükler). */
    private suspend fun TutorialDemo.templateFromTraced(by: Offset): TemplateGhost? {
        val g = host.guides ?: return null
        val traced = ensureTraced() ?: return null
        val start = TemplateGhost.of(TemplateGhost.contoursOf(listOf(traced))) ?: return null
        g.openTemplate()
        pause(350)
        tap(Offset(traced.points.map { it.x }.average().toFloat(), traced.points.map { it.y }.average().toFloat()))
        g.setTemplate(start)
        pause(400)
        val n = if (fast) 1 else 24
        val gap = u * 0.035f
        for (i in 1..n) {
            val ghost = start.moved(by.x * i / n, by.y * i / n)
            g.setTemplate(ghost)
            if (!fast) live(TutorialLive(pointer = ghost.moveHandle(gap), pressed = true))
            pause(TutorialDemo.FRAME_MS)
        }
        live(TutorialLive())
        return g.template.value
    }

    /** Kalıbın kenarları kalemle çizilir: en uzun [animated] kontur canlı, kalanlar hemen eklenir. */
    private suspend fun TutorialDemo.traceGhost(ghost: TemplateGhost, color: String, width: Float, animated: Int = 4) {
        val paths = ghost.contours
            .map { (pts, closed) -> (0 until pts.size / 2).map { Offset(pts[2 * it], pts[2 * it + 1]) }.let { if (closed && it.isNotEmpty()) it + it.first() else it } }
            .filter { it.size >= 2 }
            .sortedByDescending { p -> p.zipWithNext { a, b -> (b - a).getDistance() }.sum() }
        paths.take(animated).forEach { stroke(it, BrushType.INK_PEN, color, width, taper = false, speed = 2.2f) }
        val rest = paths.drop(animated).map { path ->
            Stroke(UUID.randomUUID().toString(), path.map { com.karapp.drawguidance.data.model.StrokePoint(it.x, it.y) }, color, BrushType.INK_PEN, width, 1f, 0)
        }
        if (rest.isNotEmpty()) host.commitElements(rest)
        pause(300)
    }

    private fun steps(simple: Boolean): List<TutorialStep> = listOf(
        // 1. Resim ekle: demlik
        TutorialStep(imageAnchor(simple), R.string.v50_tut_image_t, R.string.v50_tut_image_b, mode = if (simple) null else EditMode.ADD) {
            tapAnchor(imageAnchor(simple))
            ensureImage()
        },
        // 2. Otomatik vektörleştir (resmin üstüne çizgiler)
        TutorialStep(if (simple) TutorialAnchor.CANVAS else VECTORIZE_AUTO, R.string.v50_tut_vec_t, R.string.v50_tut_vec_b) {
            val img = ensureImage() ?: return@TutorialStep
            host.select(setOf(img.id))
            host.editImage(img.id)
            pause(500)
            if (!simple) tapAnchor(VECTORIZE_AUTO)
            if (traced() == null) host.vectorizeImage(img.id, EdgeMode.AUTO, u * 0.005f, keepImage = true)
            // Resim soluklaşır: vektör çizgiler üstünde belirgin görünür; Resmi düzenle satırı açık kalır
            host.mutate { list -> list.map { if (it.id == img.id) it.copy(opacity = 0.35f) else it } }
            host.editImage(img.id)
            pause(500)
        },
        // 3. Türler ve ayarlar; resim kaldırılır, yalnız vektör çizgiler kalır
        TutorialStep(if (simple) TutorialAnchor.CANVAS else VECTORIZE_LINES, R.string.v50_tut_types_t, R.string.v50_tut_types_b) {
            ensureTraced() ?: return@TutorialStep
            if (!simple) { tapAnchor(VECTORIZE_EDGES); tapAnchor(VECTORIZE_LINES) }
            val img = image()
            if (img != null) {
                tap(Offset(img.points[0].x + (img.points[1].x - img.points[0].x) * .9f, img.points[0].y + (img.points[3].y - img.points[0].y) * .9f))
                host.mutate { list -> list.filterNot { it.id == img.id } }
            }
            host.select(emptySet())
            mode(EditMode.DRAW)
            pause(500)
        },
        // 4. Vektörü kalıp yap ve yana taşı
        TutorialStep(templateAnchor(simple), R.string.v50_tut_pick_t, R.string.v50_tut_pick_b, mode = if (simple) null else EditMode.DRAW) {
            host.select(emptySet())
            tapAnchor(templateAnchor(simple))
            templateFromTraced(copyShift(imageRect()))
        },
        // 5. Kalıbın kenarı boyunca çiz (kalıp açık kalır)
        TutorialStep(TutorialAnchor.CANVAS, R.string.v50_tut_trace_t, R.string.v50_tut_trace_b, guide = true) {
            val g = host.guides ?: return@TutorialStep
            val ghost = g.template.value?.takeIf { g.session.value == GuideSession.TEMPLATE } ?: templateFromTraced(copyShift(imageRect())) ?: return@TutorialStep
            traceGhost(ghost, COPY_INK, u * 0.0045f)
        },
        // 6. Kütüphaneden kalıp (kedi), çizilir; Bitir
        TutorialStep(TutorialAnchor.TEMPLATE_LIBRARY, R.string.v50_tut_lib_t, R.string.v50_tut_lib_b, guide = true) {
            val g = host.guides ?: return@TutorialStep
            if (g.session.value != GuideSession.TEMPLATE) { tapAnchor(templateAnchor(simple)) }
            g.openTemplate()
            pause(400)
            tapAnchor(TutorialAnchor.TEMPLATE_LIBRARY)
            val at = if (h >= w) p(.5f, .72f) else p(.5f, .5f)
            if (!g.placeLibrary(CAT, at, u * 0.42f)) return@TutorialStep
            pause(450)
            g.template.value?.let { traceGhost(it, CAT_INK, u * 0.006f, animated = 3) }
            tapAnchor(TutorialAnchor.RULER_FINISH)
            g.finish()
        },
    )

    /** Kütüphaneden seçilen kalıp. */
    const val CAT = "lib.sketch.cat"
    private const val VECTORIZE_AUTO = "vectorize_auto"
    private const val VECTORIZE_EDGES = "vectorize_edges"
    private const val VECTORIZE_LINES = "vectorize_centerline"

    /** Resim ve çizgi roman (gelişmiş). */
    fun advanced(kind: CanvasKind): TutorialGroup = TutorialGroup(
        id = "v50_trace_${kind.name.lowercase()}", kind = kind,
        title = R.string.v50_tut_title, summary = R.string.v50_tut_summary,
        icon = Icons.Filled.Polyline, ai = false, steps = steps(simple = false),
    )

    /** Basit resim. */
    val simpleArt: TutorialGroup by lazy {
        TutorialGroup(
            id = "v50_trace_simple_art", kind = CanvasKind.SIMPLE_ART,
            title = R.string.v50_tut_title, summary = R.string.v50_tut_summary,
            icon = Icons.Filled.Polyline, ai = false, steps = steps(simple = true),
        )
    }

    /** Çocuk modu: yalnız kalıpla çizme (kütüphaneden kedi, kenarından çizilir). */
    val kids: TutorialGroup by lazy {
        TutorialGroup(
            id = "v50_trace_kids", kind = CanvasKind.KIDS,
            title = R.string.v50_tut_kids_title, summary = R.string.v50_tut_kids_summary,
            icon = Icons.Filled.Polyline, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.simple("template"), R.string.v50_tut_kids_t, R.string.v50_tut_kids_b) {
                    val g = host.guides ?: return@TutorialStep
                    tapAnchor(TutorialAnchor.simple("template"))
                    g.openTemplate()
                    pause(400)
                    tapAnchor(TutorialAnchor.TEMPLATE_LIBRARY)
                    if (!g.placeLibrary(CAT, p(.5f, .5f), u * 0.6f)) return@TutorialStep
                    pause(450)
                },
                TutorialStep(TutorialAnchor.CANVAS, R.string.v50_tut_kids2_t, R.string.v50_tut_kids2_b, guide = true) {
                    val g = host.guides ?: return@TutorialStep
                    if (g.session.value != GuideSession.TEMPLATE) {
                        g.openTemplate()
                        if (!g.placeLibrary(CAT, p(.5f, .5f), u * 0.6f)) return@TutorialStep
                    }
                    g.template.value?.let { traceGhost(it, CAT_INK, u * 0.012f, animated = 3) }
                    tapAnchor(TutorialAnchor.RULER_FINISH)
                    g.finish()
                },
            ),
        )
    }
}
