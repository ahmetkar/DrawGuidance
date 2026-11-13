package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Functions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.comic.ComicPanels
import com.karapp.drawguidance.content.comic.ComicSfx
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.GradientSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.ui.drawing.DiagramThemes
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v32 eğitimlerin içeriği: her tuval türünde (çizim, not alma, çizgi roman) araç grupları ve adımları. Her grup boş bir
 * sayfada başlar ve adım adım bitmiş, profesyonel görünen bir çizim ortaya çıkarır: gün batımı manzarası, fizik ders
 * notu, çizgi roman sayfası. Yapay zekâ gruplarında gerçek öneriler (şekil düzeltme, şablon, tümünü düzelt, el yazısı)
 * gösterilir; öneri gelmezse aynı sonuç doğrudan çizilir.
 */
object TutorialScripts {

    fun groups(kind: CanvasKind): List<TutorialGroup> = when (kind) {
        // v40 her gelişmiş türde bu sürümün yenilikleri ayrı grupta
        // v46 yenilikler (iki parçalı üst menü, seçim menüsü, Panel ekle, formülle çizilen şekiller, mekân ve karakterler)
        // v48 yenilikler (yeni mod menüsü, Özellikler, ayrı Taşı/Genişlet, üç basit menü, çocuk sırası)
        CanvasKind.ART -> listOf(artBasics(), artAdd(), artAi(), TutorialV40.art, TutorialV46.art, TutorialV48.advanced(kind), TutorialV50.advanced(kind))
        CanvasKind.NOTES -> listOf(notesWrite(), notesFormula(), notesAi(), TutorialV40.notes, TutorialV46.notes, TutorialV48.advanced(kind))
        CanvasKind.COMIC -> listOf(comicPanels(), comicText(), comicAi(), TutorialV40.comic, TutorialV46.comic, TutorialV48.advanced(kind), TutorialV50.advanced(kind))
        // v40 basit resim ve basit notta kısa, sade dilli tanıtım (v46 yenilikler ayrı grupta)
        CanvasKind.SIMPLE_ART -> listOf(TutorialV40.simpleArt, TutorialV46.simpleArt, TutorialV48.simple(kind), TutorialV50.simpleArt)
        CanvasKind.SIMPLE_NOTES -> listOf(TutorialV40.simpleNotes, TutorialV46.simpleNotes, TutorialV48.simple(kind))
        // v37 çocuklar için çizimde maskot Boyacık araçları tanıtır ve örnek resim yapar (v40 yenilikleri ayrıca)
        CanvasKind.KIDS -> listOf(TutorialKids.intro, TutorialKids.whatsNew, TutorialV48.kids, TutorialV50.kids)
    }

    /** v40 eğitimlerin sürümü: eski sürümün eğitimini bitirmiş kullanıcıya [whatsNew] bir kez gösterilir (v48). */
    const val VERSION = 50

    /** Bu türde sürümün yenilikleri (v48: yeni menü sistemi). */
    fun whatsNew(kind: CanvasKind): TutorialGroup? = when (kind) {
        // v50 çizim modlarında "Resimden çizim" (vektörleştirme ve kalıpla çizme), not türlerinde v48 menüleri
        CanvasKind.ART, CanvasKind.COMIC -> TutorialV50.advanced(kind)
        CanvasKind.NOTES -> TutorialV48.advanced(kind)
        CanvasKind.SIMPLE_ART -> TutorialV50.simpleArt
        CanvasKind.SIMPLE_NOTES -> TutorialV48.simple(kind)
        CanvasKind.KIDS -> TutorialV50.kids
    }

    // ------------------------------------------------------------------ ortak

    private const val INK = "#2B2F4A"
    private const val PENCIL_GREY = "#37474F"

    /** Kontrol noktalarından geçen kırık çizginin x'teki yüksekliği (oran). */
    private fun yAt(points: List<Pair<Float, Float>>, x: Float): Float {
        for (i in 0 until points.size - 1) {
            val (x0, y0) = points[i]; val (x1, y1) = points[i + 1]
            if (x in x0..x1) return y0 + (y1 - y0) * ((x - x0) / (x1 - x0).coerceAtLeast(1e-4f))
        }
        return points.last().second
    }

    private fun TutorialDemo.path(points: List<Pair<Float, Float>>) = points.map { (x, y) -> p(x, y) }

    /** Çizimin düzgün geometrisi (öneri gelmediyse). */
    private fun TutorialDemo.fixStroke(s: Stroke) {
        val f = host.fit(s) ?: return
        host.mutate { list -> list.map { if (it.id == s.id) it.copy(points = f.points) else it } }
    }

    /**
     * v38 birden çok çizgi: çizgi önerisi (her çizgi düzgün haline, yakın uçlar birleşir; tek düğmeyle hepsi). Tek
     * çizgiye öneri yapılmadığından çizimler en az iki çizgidir. Öneri gelmezse her çizgi kendi düzgün geometrisine.
     */
    private suspend fun TutorialDemo.aiLines(strokes: List<List<Offset>>, brush: BrushType, color: String, width: Float, speed: Float = 1.8f) {
        val drawn = strokes.map { stroke(it, brush, color, width, suggest = true, speed = speed) }
        val ids = drawn.mapTo(HashSet()) { it.id }
        aiLines(
            R.string.tut_hint_lines,
            accept = { sug -> sug.strokeIds.size >= 2 && sug.strokeIds.all(ids::contains) },
            fallback = { drawn.forEach { fixStroke(it) } },
        )
    }

    /** v39 tek ama büyük çizgi (elle çizilmiş çember): tek çizgi önerisi; gelmezse çizgi kendi düzgün geometrisine. */
    private suspend fun TutorialDemo.aiSingle(c: Offset, r: Float, brush: BrushType, color: String, width: Float, seed: Long) {
        val drawn = stroke(TutorialArt.roughCircle(c, r, seed), brush, color, width, suggest = true, speed = 1.4f)
        aiLines(R.string.tut40_hint_single, accept = { sug -> drawn.id in sug.strokeIds }, fallback = { fixStroke(drawn) })
    }

    /** Çemberin iki yayı (v38 çizgi önerisi örneği: iki çizgi, uçları birleşir). */
    private fun arcs(c: Offset, r: Float, seed: Long): List<List<Offset>> {
        val full = TutorialArt.roughCircle(c, r, seed, overshoot = 1f)
        val half = full.size / 2
        return listOf(full.subList(0, half + 1), full.subList(half + 2, full.size))
    }

    /** Dört ayrı kenar (köşelerde küçük boşluk ve taşmayla; v38 çizgi önerisi köşeleri birleştirir). */
    private fun sides(l: Float, t: Float, r: Float, b: Float, gap: Float): List<List<Offset>> = listOf(
        listOf(Offset(l + gap, t), Offset(r - gap * 0.4f, t + gap * 0.3f)),
        listOf(Offset(r + gap * 0.2f, t + gap), Offset(r, b - gap * 0.6f)),
        listOf(Offset(r - gap, b + gap * 0.2f), Offset(l + gap * 0.5f, b)),
        listOf(Offset(l - gap * 0.3f, b - gap), Offset(l, t + gap * 0.7f)),
    ).mapIndexed { i, (a, z) -> TutorialArt.roughPoly(listOf(a, z), 70L + i, closed = false, amp = gap * 0.25f) }

    /** Birkaç çizgiyle çizilen nesne: şablon önerisi ([key]); gelmezse şablonun düzgün çizgileri konur. */
    private suspend fun TutorialDemo.aiTemplate(key: String, center: Offset, size: Float, brush: BrushType, color: String, width: Float, seed: Long) {
        val drawn = TutorialArt.handDrawnTemplate(key, center, size, seed).map { stroke(it, brush, color, width, suggest = true, speed = 1.6f) }
        val ids = drawn.mapTo(HashSet()) { it.id }
        aiShape(
            R.string.tut_hint_template,
            accept = { sug -> sug.templates.any { it.key == key } },
            apply = { sug -> host.acceptTemplate(sug.templates.indexOfFirst { it.key == key }) },
            fallback = {
                val clean = TutorialArt.cleanTemplate(key, center, size)
                val base = drawn.first()
                val group = java.util.UUID.randomUUID().toString()
                host.mutate { list ->
                    list.filter { it.id !in ids } + clean.map { pts ->
                        base.copy(id = java.util.UUID.randomUUID().toString(), points = pts.map { StrokePoint(it.x, it.y) }, groupId = group)
                    }
                }
            },
        )
    }

    private fun triangle(c: Offset, hw: Float, hh: Float) = listOf(Offset(c.x, c.y - hh), Offset(c.x + hw, c.y + hh), Offset(c.x - hw, c.y + hh))

    /** Kuş: iki kanat yayı. */
    private fun bird(c: Offset, s: Float) = TutorialArt.smooth(
        listOf(Offset(c.x - s, c.y + 0.1f * s), Offset(c.x - 0.55f * s, c.y - 0.38f * s), Offset(c.x, c.y + 0.12f * s), Offset(c.x + 0.55f * s, c.y - 0.38f * s), Offset(c.x + s, c.y + 0.1f * s)),
        samples = 8,
    )

    // ------------------------------------------------------------------ Çizim: araçlar (gün batımı manzarası)

    private val ridge = listOf(0f to .60f, .10f to .50f, .18f to .54f, .30f to .38f, .40f to .50f, .50f to .44f, .62f to .30f, .74f to .46f, .86f to .40f, 1f to .50f)
    private val hillLine = listOf(0f to .74f, .18f to .68f, .36f to .72f, .55f to .66f, .75f to .71f, 1f to .66f)

    private fun artBasics() = TutorialGroup(
        id = "art_basics", kind = CanvasKind.ART, title = R.string.tut_art_basics, summary = R.string.tut_art_basics_text,
        icon = Icons.Filled.Brush, ai = false,
        steps = listOf(
            TutorialStep(TutorialAnchor.BRUSH, R.string.tut_art_b1, R.string.tut_art_b1_text) {
                val line = u * 0.0065f
                stroke(TutorialArt.smooth(TutorialArt.wobble(path(ridge), u * 0.0018f, 1)), BrushType.INK_PEN, INK, line)
                stroke(TutorialArt.smooth(path(listOf(.26f to .43f, .29f to .405f, .31f to .44f, .345f to .41f))), BrushType.INK_PEN, INK, line * 0.7f, speed = 0.8f)
                stroke(TutorialArt.smooth(path(listOf(.58f to .355f, .61f to .335f, .63f to .37f, .66f to .345f))), BrushType.INK_PEN, INK, line * 0.7f, speed = 0.8f)
                stroke(TutorialArt.smooth(TutorialArt.wobble(path(hillLine), u * 0.0015f, 2)), BrushType.INK_PEN, INK, line)
            },
            TutorialStep(TutorialAnchor.BRUSH_SETTINGS, R.string.tut_art_b2, R.string.tut_art_b2_text) {
                // Geniş, yarı saydam marker: dağlar ve tepeler açıktan koyuya katman katman (bindirmeler renkleri yumuşatır)
                val mountain = listOf("#A595DA", "#9483CF", "#8371C4", "#715FB6", "#624FA6", "#544394", "#473881", "#3B2F6E", "#30275D")
                mountain.forEachIndexed { i, color ->
                    val d = 0.02f + i * 0.034f
                    val pts = (0..24).map { k ->
                        val x = k / 24f
                        x to minOf(yAt(ridge, x) + d, yAt(hillLine, x) - 0.004f)
                    }
                    stroke(TutorialArt.smooth(path(pts), 4), BrushType.MARKER, color, u * 0.056f, opacity = 0.5f, speed = 2.6f, taper = false)
                }
                val hills = listOf("#8FD49A", "#78C487", "#62B275", "#4FA165", "#418F58", "#357D4C", "#2B6B41", "#235B38", "#1C4C2F", "#173F28")
                hills.forEachIndexed { i, color ->
                    val d = 0.018f + i * 0.036f
                    val pts = (0..24).map { k -> val x = k / 24f; x to minOf(yAt(hillLine, x) + d, 0.985f) }
                    stroke(TutorialArt.smooth(path(pts), 4), BrushType.MARKER, color, u * 0.07f, opacity = 0.58f, speed = 2.6f, taper = false)
                }
            },
            TutorialStep(TutorialAnchor.FILL, R.string.tut_art_b3, R.string.tut40_art_b3_text) {
                tap(p(.5f, .14f))
                host.setPaperGradient(GradientSpec(listOf("#2E2A6B", "#C2457A", "#FFB066"), 90f))
                pause(500)
            },
            TutorialStep(TutorialAnchor.ERASER, R.string.tut_art_b4, R.string.tut_art_b4_text) {
                val river = TutorialArt.smooth(path(listOf(.54f to .665f, .5f to .74f, .56f to .82f, .47f to .91f, .51f to 1f)), 10)
                stroke(river, BrushType.ERASER, "#000000", u * 0.032f, speed = 0.8f, taper = false)
            },
            TutorialStep(TutorialAnchor.PEN, R.string.tut_art_b5, R.string.tut_art_b5_text) {
                stroke(bird(p(.2f, .2f), u * 0.035f), BrushType.INK_PEN, INK, u * 0.005f, speed = 0.6f)
                stroke(bird(p(.29f, .15f), u * 0.026f), BrushType.INK_PEN, INK, u * 0.0045f, speed = 0.6f)
                stroke(bird(p(.35f, .23f), u * 0.02f), BrushType.INK_PEN, INK, u * 0.004f, speed = 0.6f)
            },
            TutorialStep(TutorialAnchor.UNDO, R.string.tut_art_b6, R.string.tut_art_b6_text) {
                tapAnchor(TutorialAnchor.UNDO)
                host.undo()
                pause(700)
                tapAnchor(TutorialAnchor.REDO)
                host.redo()
                pause(400)
            },
            TutorialStep(TutorialAnchor.LAYERS, R.string.tut_art_b7, R.string.tut_art_b7_text) {
                tapAnchor(TutorialAnchor.LAYERS)
            },
        ),
    )

    // ------------------------------------------------------------------ Çizim: şekil, yazı, düzenleme (vektör manzara)

    private fun Stroke?.list() = listOfNotNull(this)

    private fun artAdd() = TutorialGroup(
        id = "art_add", kind = CanvasKind.ART, title = R.string.tut_art_add, summary = R.string.tut_art_add_text,
        icon = Icons.Filled.Category, ai = false,
        steps = listOf(
            TutorialStep(TutorialAnchor.mode(EditMode.ADD), R.string.tut_art_a1, R.string.tut_art_a1_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.mode(EditMode.ADD))
            },
            TutorialStep(TutorialAnchor.add("shape"), R.string.tut_art_a2, R.string.tut_art_a2_text, mode = EditMode.ADD) {
                host.setPaperGradient(GradientSpec(listOf("#4B3A8C", "#C8578E", "#FF9F7A", "#FFD39A"), 90f))
                tapAnchor(TutorialAnchor.add("shape"))
                val w = u * 0.003f
                drag(p(.38f, .22f), p(.62f, .46f)) { a, b ->
                    TutorialArt.shape("CIRCLE", a, b, shapeStyle("#FFB74D", w, fill = "#FFB74D", gradient = GradientSpec(listOf("#FFF3B0", "#FFB74D", "#FF7043"), 90f))).list()
                }
                // Dağlar: gradyanlı üçgenler ve tepelerinde aynı açıyla kar
                val back = GradientSpec(listOf("#A897DD", "#6A58AE", "#3E3275"), 90f)
                val front = GradientSpec(listOf("#8B78CC", "#4F3F93", "#2B2358"), 90f)
                listOf(
                    Triple(Offset(-.05f, .36f), Offset(.55f, .78f), back),
                    Triple(Offset(.30f, .27f), Offset(.98f, .78f), front),
                    Triple(Offset(.70f, .42f), Offset(1.12f, .78f), back),
                ).forEach { (tl, br, g) ->
                    drag(p(tl.x, tl.y), p(br.x, br.y), 420) { a, b2 -> TutorialArt.shape("TRIANGLE", a, b2, shapeStyle("#3A2F6B", w, fill = g.colors[1], gradient = g)).list() }
                    val cx = (tl.x + br.x) / 2f; val hw = (br.x - tl.x) / 2f; val hh = br.y - tl.y
                    place(null, TutorialArt.shape("TRIANGLE", p(cx - hw * 0.24f, tl.y), p(cx + hw * 0.24f, tl.y + hh * 0.24f), shapeStyle("#FFFFFF", w * 0.5f, fill = "#F7F4FF")).list())
                }
                // Tepeler: gradyanlı elipsler
                drag(p(-.25f, .66f), p(.75f, 1.3f), 450) { a, b2 ->
                    TutorialArt.shape("ELLIPSE", a, b2, shapeStyle("#2E6B45", w, fill = "#5FAE6E", gradient = GradientSpec(listOf("#9BDBA0", "#4E9E63", "#2A6A43"), 90f))).list()
                }
                drag(p(.25f, .7f), p(1.3f, 1.32f), 450) { a, b2 ->
                    TutorialArt.shape("ELLIPSE", a, b2, shapeStyle("#24563A", w, fill = "#3E8E5A", gradient = GradientSpec(listOf("#7CC98A", "#3E8E5A", "#1F4D33"), 90f))).list()
                }
                drag(p(.06f, .10f), p(.32f, .22f), 450) { a, b -> TutorialArt.shape("CLOUD", a, b, shapeStyle("#FFFFFF", w, fill = "#FFFFFF", fillOpacity = 0.85f)).list() }
                drag(p(.66f, .14f), p(.86f, .23f), 450) { a, b -> TutorialArt.shape("CLOUD", a, b, shapeStyle("#FFFFFF", w, fill = "#FFFFFF", fillOpacity = 0.7f)).list() }
                drag(p(.10f, .70f), p(.19f, .92f), 380) { a, b -> TutorialArt.shape("PINE", a, b, shapeStyle("#16382A", w, fill = "#1F4D3A")).list() }
                drag(p(.80f, .68f), p(.90f, .90f), 380) { a, b -> TutorialArt.shape("PINE", a, b, shapeStyle("#16382A", w, fill = "#1F4D3A")).list() }
                drag(p(.42f, .16f), p(.50f, .20f), 300) { a, b -> TutorialArt.shape("BIRD", a, b, shapeStyle(INK, w * 1.4f)).list() }
            },
            TutorialStep(TutorialAnchor.add("text"), R.string.tut_art_a3, R.string.tut_art_a3_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.add("text"))
                val size = u * 0.075f
                place(p(.08f, .05f), listOf(TutorialArt.text("Gün Batımı", w * 0.06f, h * 0.035f, size, "#FFFFFF", TextFont.PLAYFAIR, bold = true)))
                pause(300)
            },
            TutorialStep(TutorialAnchor.mode(EditMode.SELECT), R.string.tut_art_a4, R.string.tut_art_a4_text, mode = EditMode.SELECT) {
                val sun = host.canvas()?.strokes?.firstOrNull { it.shape?.kind == "CIRCLE" } ?: return@TutorialStep
                tap(p(.5f, .34f))
                host.select(setOf(sun.id))
                pause(600)
            },
            TutorialStep(TutorialAnchor.mode(EditMode.EDIT_PICK), R.string.tut_art_a5, R.string.tut40_art_a5_text, mode = EditMode.MOVE) {
                val sun = host.canvas()?.strokes?.firstOrNull { it.shape?.kind == "CIRCLE" } ?: return@TutorialStep
                host.select(setOf(sun.id))
                val total = h * 0.14f
                val n = if (fast) 1 else 24
                repeat(n) { host.moveSelected(0f, total / n); pause(30) }
                pause(300)
            },
            TutorialStep(TutorialAnchor.mode(EditMode.EDIT_PICK), R.string.tut_art_a6, R.string.tut_art_a6_text, mode = EditMode.EDIT_PICK) {
                val sun = host.canvas()?.strokes?.firstOrNull { it.shape?.kind == "CIRCLE" } ?: return@TutorialStep
                host.select(setOf(sun.id))
                val n = if (fast) 1 else 16
                val step = Math.pow(1.3, 1.0 / n).toFloat()
                repeat(n) { host.scaleSelected(step); pause(35) }
                pause(300)
            },
        ),
    )

    // ------------------------------------------------------------------ Çizim: yapay zekâ

    private fun artAi() = TutorialGroup(
        id = "art_ai", kind = CanvasKind.ART, title = R.string.tut_art_ai, summary = R.string.tut40_art_ai_text,
        icon = Icons.Filled.AutoAwesome, ai = true,
        steps = listOf(
            TutorialStep(TutorialAnchor.MORE, R.string.tut_ai_more, R.string.tut_ai_more_text_v38, ai = true) {
                tapAnchor(TutorialAnchor.MORE)
            },
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_art_ai2_v38, R.string.tut40_art_ai2_text, ai = true) {
                aiLines(arcs(p(.78f, .2f), u * 0.085f, 7), BrushType.PENCIL, PENCIL_GREY, u * 0.005f)
            },
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_art_ai3, R.string.tut_art_ai3_text_v38, ai = true) {
                aiTemplate("yapi.ev.besik", p(.3f, .58f), u * 0.32f, BrushType.PENCIL, PENCIL_GREY, u * 0.005f, 21)
            },
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_lines_v38, R.string.tut_art_ai4_text_v38, ai = true) {
                val hw = w * 0.055f; val hh = h * 0.075f
                val trees = listOf(p(.70f, .76f), p(.80f, .74f), p(.90f, .77f)).mapIndexed { i, c ->
                    TutorialArt.roughPoly(triangle(c, hw, hh), 40L + i, amp = u * 0.004f)
                }
                aiLines(trees, BrushType.PENCIL, "#2E5E3E", u * 0.005f)
            },
            // v39 tek ama büyük çizgiye de öneri gelir
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_single, R.string.tut40_single_text, ai = true) {
                aiSingle(p(.5f, .24f), u * 0.15f, BrushType.PENCIL, "#E07A1F", u * 0.005f, 31)
            },
            TutorialStep(TutorialAnchor.mode(EditMode.SELECT), R.string.tut_ai_select, R.string.tut_ai_select_text_v38, ai = true),
        ),
    )

    // ------------------------------------------------------------------ Not alma: yazı ve kalemler (fizik notu)

    private const val NOTE_INK = "#1C1B19"
    private const val BALLPOINT = "#1E3A8A"

    private fun notesWrite() = TutorialGroup(
        id = "notes_write", kind = CanvasKind.NOTES, title = R.string.tut_notes_write, summary = R.string.tut_notes_write_text,
        icon = Icons.Filled.EditNote, ai = false,
        steps = listOf(
            TutorialStep(TutorialAnchor.add("text"), R.string.tut_notes_w1, R.string.tut_notes_w1_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.add("text"))
                val size = u * 0.06f
                val title = "Newton'un Hareket Yasaları"
                place(p(.1f, .07f), listOf(TutorialArt.text(title, w * 0.07f, h * 0.05f, size, NOTE_INK, TextFont.MERRIWEATHER, bold = true)))
                val (_, th) = TutorialArt.measure(title, size, TextFont.MERRIWEATHER, bold = true)
                pause(250)
                place(null, listOf(TutorialArt.text("Fizik · Kuvvet ve hareket", w * 0.07f, h * 0.05f + th + u * 0.015f, u * 0.032f, "#6E6A63", TextFont.LORA, italic = true)))
                pause(300)
            },
            TutorialStep(TutorialAnchor.BRUSH, R.string.tut_notes_w2, R.string.tut_notes_w2_text) {
                val top = h * 0.05f + u * 0.16f
                val word = TutorialArt.handwriting("kütle", 5, Rect(w * 0.08f, top, w * 0.6f, top + u * 0.075f))
                word.forEach { stroke(it, BrushType.NOTE_BALLPOINT, BALLPOINT, u * 0.0045f, speed = 1.6f) }
                val ys = word.flatten().maxOf { it.y }
                val xs = word.flatten().let { it.minOf { o -> o.x } to it.maxOf { o -> o.x } }
                stroke(TutorialArt.wobble(listOf(Offset(xs.first, ys + u * 0.012f), Offset(xs.second, ys + u * 0.016f)), u * 0.001f, 3), BrushType.NOTE_BALLPOINT, BALLPOINT, u * 0.004f)
                val word2 = TutorialArt.handwriting("ivme", 2, Rect(w * 0.08f, top + u * 0.11f, w * 0.6f, top + u * 0.11f + u * 0.075f))
                word2.forEach { stroke(it, BrushType.NOTE_BALLPOINT, BALLPOINT, u * 0.0045f, speed = 1.6f) }
            },
            TutorialStep(TutorialAnchor.BRUSH, R.string.tut_notes_w3, R.string.tut_notes_w3_text) {
                val size = u * 0.06f
                val (tw, th) = TutorialArt.measure("Newton'un Hareket Yasaları", size, TextFont.MERRIWEATHER, bold = true)
                val y = h * 0.05f + th * 0.62f
                stroke(listOf(Offset(w * 0.06f, y), Offset(w * 0.07f + tw + u * 0.01f, y + u * 0.002f)), BrushType.NOTE_HIGHLIGHTER, "#FFE066", u * 0.05f, opacity = 0.55f, speed = 0.9f, taper = false)
            },
            TutorialStep(TutorialAnchor.PAGES, R.string.tut_notes_w4, R.string.tut40_notes_w4_text) {
                tapAnchor(TutorialAnchor.PAGES)
            },
        ),
    )

    private fun notesFormula() = TutorialGroup(
        id = "notes_formula", kind = CanvasKind.NOTES, title = R.string.tut_notes_formula, summary = R.string.tut_notes_formula_text,
        icon = Icons.Filled.Functions, ai = false,
        steps = listOf(
            TutorialStep(TutorialAnchor.mode(EditMode.ADD), R.string.tut_notes_f1, R.string.tut_notes_f1_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.mode(EditMode.ADD))
            },
            TutorialStep(TutorialAnchor.add("formula"), R.string.tut_notes_f2, R.string.tut_notes_f2_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.add("formula"))
                place(p(.3f, .1f), TutorialArt.formula("physics.newton2", w * 0.1f, h * 0.06f, u * 0.09f, NOTE_INK).list())
                pause(350)
                place(p(.3f, .2f), TutorialArt.formula("physics.velocity", w * 0.1f, h * 0.06f + u * 0.16f, u * 0.07f, "#2F6BD8").list())
                pause(300)
            },
            TutorialStep(TutorialAnchor.add("diagram"), R.string.tut_notes_f3, R.string.tut_notes_f3_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.add("diagram"))
                val top = h * 0.06f + u * 0.34f
                val rect = Rect(w * 0.08f, top, w * 0.92f, top + minOf(h * 0.42f, u * 0.75f))
                drag(Offset(rect.left, rect.top), Offset(rect.right, rect.bottom), 700) { a, b ->
                    listOf(TutorialArt.diagram(
                        DiagramKind.FLOW, Rect(a, b), shapeStyle(NOTE_INK, u * 0.003f), DiagramThemes.EDITORIAL,
                        listOf("Kuvvet uygulanır", "İvme oluşur", "Hız değişir", "Cisim hareket eder", "Sonuç"),
                    ))
                }
            },
            TutorialStep(TutorialAnchor.add("shape"), R.string.tut_notes_f4, R.string.tut_notes_f4_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.add("shape"))
                // Hız denkleminin altından diyagramın ilk adımına
                val strokes = host.canvas()?.strokes.orEmpty()
                val formulaBottom = strokes.filter { it.brushType == BrushType.FORMULA }.flatMap { it.points }.maxOfOrNull { it.y } ?: (h * 0.25f)
                val diagram = strokes.firstOrNull { it.brushType == BrushType.DIAGRAM }
                val dTop = diagram?.points?.minOf { it.y } ?: (formulaBottom + u * 0.12f)
                val dCx = diagram?.points?.let { p -> (p.minOf { it.x } + p.maxOf { it.x }) / 2f } ?: (w * 0.5f)
                val from = Offset(w * 0.24f, formulaBottom + u * 0.02f)
                val to = Offset(dCx - u * 0.13f, dTop + u * 0.035f)
                drag(from, to, 600) { a, b -> TutorialArt.shape("ARROW", a, b, shapeStyle("#D9572B", u * 0.006f)).list() }
            },
        ),
    )

    private fun notesAi() = TutorialGroup(
        id = "notes_ai", kind = CanvasKind.NOTES, title = R.string.tut_notes_ai, summary = R.string.tut40_notes_ai_text,
        icon = Icons.Filled.AutoAwesome, ai = true,
        steps = listOf(
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_notes_ai1, R.string.tut_notes_ai1_text, ai = true) {
                val box = Rect(w * 0.1f, h * 0.2f, w * 0.7f, h * 0.2f + u * 0.085f)
                val drawn = TutorialArt.handwriting("kuvvet", 3, box).map { stroke(it, BrushType.NOTE_BALLPOINT, BALLPOINT, u * 0.0045f, suggest = true, speed = 1.8f) }
                val ids = drawn.mapTo(HashSet()) { it.id }
                aiText(R.string.tut_hint_text) {
                    host.mutate { list -> list.filter { it.id !in ids } + TutorialArt.text("kuvvet", box.left, box.top, box.height / 0.72f * 0.72f, BALLPOINT) }
                }
            },
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_lines_v38, R.string.tut_notes_ai2_text_v38, ai = true) {
                val l = w * 0.1f; val t = h * 0.2f + u * 0.2f; val r = w * 0.62f; val b = t + u * 0.16f
                aiLines(sides(l, t, r, b, u * 0.012f), BrushType.NOTE_FELT, NOTE_INK, u * 0.005f)
            },
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_lines_v38, R.string.tut_notes_ai3_text_v38, ai = true) {
                val y = h * 0.2f + u * 0.56f
                val r = u * 0.07f
                val rings = listOf(w * 0.26f, w * 0.26f + r * 1.7f, w * 0.26f + r * 3.4f).mapIndexed { i, x -> TutorialArt.roughCircle(Offset(x, y), r, 50L + i) }
                aiLines(rings, BrushType.NOTE_FELT, "#2F6BD8", u * 0.005f)
            },
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_single, R.string.tut40_single_text, ai = true) {
                aiSingle(Offset(w * 0.5f, h * 0.82f), u * 0.16f, BrushType.NOTE_FELT, "#C2410C", u * 0.005f, 32)
            },
            TutorialStep(TutorialAnchor.MORE, R.string.tut_ai_more, R.string.tut_ai_more_text_v38, ai = true) {
                tapAnchor(TutorialAnchor.MORE)
            },
        ),
    )

    // ------------------------------------------------------------------ Çizgi roman

    private const val COMIC_INK = "#111111"

    /** Panel düzeninin kareleri (tuval koordinatı). */
    internal fun TutorialDemo.panelRects(layoutId: String): List<Rect> {
        val layout = ComicPanels.byId(layoutId) ?: return emptyList()
        return ComicPanels.rects(layout, w, h).map { Rect(it[0], it[1], it[2], it[3]) }
    }

    internal fun TutorialDemo.panels(layoutId: String): List<Stroke> {
        val line = (u * 0.006f).coerceIn(2f, 14f)
        return panelRects(layoutId).mapNotNull { r -> TutorialArt.shape("RECTANGLE", r.topLeft, r.bottomRight, shapeStyle(COMIC_INK, line, fill = "#FFFFFF")) }
    }

    /** Kahraman (pelerinli, yumruğu havada) — mürekkep kalemiyle. */
    private suspend fun TutorialDemo.hero(c: Offset, s: Float) {
        val wl = u * 0.006f
        fun o(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
        fun circle(cx: Float, cy: Float, rx: Float, ry: Float, n: Int = 40) = (0..n).map { i ->
            val a = i / n.toFloat() * 2 * PI.toFloat() - PI.toFloat() / 2
            o(cx + rx * cos(a), cy + ry * sin(a))
        }
        // Pelerin (arkada)
        stroke(TutorialArt.smooth(listOf(o(-.35f, -.25f), o(-.75f, .55f), o(-.95f, 1.25f), o(-.3f, 1.05f), o(.25f, 1.2f), o(.75f, 1.1f), o(.42f, .4f), o(.35f, -.25f))), BrushType.INK_PEN, "#C62828", wl * 1.1f)
        // Baş, saç, gözler, ağız
        stroke(circle(0f, -.75f, .42f, .46f), BrushType.INK_PEN, COMIC_INK, wl)
        stroke(
            TutorialArt.smooth(listOf(o(-.43f, -.82f), o(-.4f, -1.1f), o(-.26f, -1.0f), o(-.17f, -1.24f), o(-.05f, -1.04f), o(.06f, -1.28f), o(.17f, -1.05f), o(.3f, -1.2f), o(.34f, -1.0f), o(.45f, -1.06f), o(.43f, -.8f)), 5),
            BrushType.INK_PEN, COMIC_INK, wl, speed = 0.9f,
        )
        stroke(circle(-.16f, -.78f, .07f, .1f, 16), BrushType.INK_PEN, COMIC_INK, wl * 0.9f, speed = 0.6f)
        stroke(circle(.16f, -.78f, .07f, .1f, 16), BrushType.INK_PEN, COMIC_INK, wl * 0.9f, speed = 0.6f)
        stroke(TutorialArt.smooth(listOf(o(-.18f, -.55f), o(0f, -.45f), o(.18f, -.55f)), 8), BrushType.INK_PEN, COMIC_INK, wl, speed = 0.6f)
        // Gövde ve göğüs amblemi
        stroke(listOf(o(-.3f, -.3f), o(-.34f, .55f), o(.34f, .55f), o(.3f, -.3f), o(-.3f, -.3f)), BrushType.INK_PEN, COMIC_INK, wl)
        stroke(listOf(o(0f, -.12f), o(.13f, .08f), o(0f, .28f), o(-.13f, .08f), o(0f, -.12f)), BrushType.INK_PEN, "#FBC02D", wl * 1.2f, speed = 0.7f)
        // Kollar: sağ yumruk havada, sol bel
        stroke(TutorialArt.smooth(listOf(o(.3f, -.25f), o(.55f, -.7f), o(.62f, -1.1f)), 6), BrushType.INK_PEN, COMIC_INK, wl)
        stroke(circle(.63f, -1.2f, .1f, .1f, 16), BrushType.INK_PEN, COMIC_INK, wl, speed = 0.6f)
        stroke(TutorialArt.smooth(listOf(o(-.3f, -.25f), o(-.58f, .1f), o(-.32f, .35f)), 6), BrushType.INK_PEN, COMIC_INK, wl)
        // Bacaklar
        stroke(listOf(o(-.2f, .55f), o(-.28f, 1.25f), o(-.42f, 1.3f)), BrushType.INK_PEN, COMIC_INK, wl)
        stroke(listOf(o(.2f, .55f), o(.28f, 1.25f), o(.42f, 1.3f)), BrushType.INK_PEN, COMIC_INK, wl)
    }

    private fun comicPanels() = TutorialGroup(
        id = "comic_panels", kind = CanvasKind.COMIC, title = R.string.tut_comic_panels, summary = R.string.tut_comic_panels_text,
        icon = Icons.Filled.Dashboard, ai = false,
        steps = listOf(
            TutorialStep(TutorialAnchor.mode(EditMode.ADD), R.string.tut_comic_p1, R.string.tut40_comic_p1_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.mode(EditMode.ADD))
            },
            // v37 panel düzenleri Şablonlar'da
            TutorialStep(TutorialAnchor.add("templates"), R.string.tut_comic_p2, R.string.tut_comic_p2_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.add("templates"))
                panels("big_top").forEach { place(null, listOf(it)); pause(260) }
            },
            TutorialStep(TutorialAnchor.BRUSH, R.string.tut_comic_p3, R.string.tut_comic_p3_text) {
                val top = panelRects("big_top").firstOrNull() ?: Rect(0f, 0f, w, h * 0.5f)
                hero(Offset(top.center.x, top.center.y + top.height * 0.04f), top.height * 0.26f)
            },
            TutorialStep(TutorialAnchor.add("effects"), R.string.tut_comic_p4, R.string.tut_comic_p4_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.add("effects"))
                val top = panelRects("big_top").firstOrNull() ?: return@TutorialStep
                val inner = top.deflate(u * 0.012f)
                place(null, host.comicLines(ComicLinesKind.FOCUS, 0.7f, inner, COMIC_INK))
                pause(300)
            },
        ),
    )

    private fun comicText() = TutorialGroup(
        id = "comic_text", kind = CanvasKind.COMIC, title = R.string.tut_comic_text, summary = R.string.tut_comic_text_text,
        icon = Icons.Filled.ChatBubble, ai = false,
        steps = listOf(
            TutorialStep(TutorialAnchor.add("balloon"), R.string.tut_comic_t1, R.string.tut_comic_t1_text, mode = EditMode.ADD) {
                // Sahne: paneller ve kahraman hazır gelir
                if (host.canvas()?.strokes.isNullOrEmpty()) {
                    host.commitElements(panels("big_top"))
                    val top = panelRects("big_top").firstOrNull()
                    if (top != null) {
                        val wasFast = fast
                        hero(Offset(top.center.x - top.width * 0.12f, top.center.y + top.height * 0.04f), top.height * 0.24f)
                        if (!wasFast) pause(200)
                    }
                }
                tapAnchor(TutorialAnchor.add("balloon"))
                val top = panelRects("big_top").firstOrNull() ?: Rect(0f, 0f, w, h * 0.5f)
                val at = Offset(top.left + top.width * 0.74f, top.top + top.height * 0.24f)
                place(at, host.balloon(BalloonStyle.SPEECH, 1, at, "Bu şehir güvende!").list())
                pause(300)
            },
            TutorialStep(TutorialAnchor.add("effects"), R.string.tut_comic_t2, R.string.tut_comic_t2_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.add("effects"))
                val top = panelRects("big_top").firstOrNull() ?: Rect(0f, 0f, w, h * 0.5f)
                val at = Offset(top.left + top.width * 0.78f, top.top + top.height * 0.72f)
                val preset = ComicSfx.presets.firstOrNull { it.text == "POW!" } ?: ComicSfx.presets.first()
                place(at, host.sfx(preset, at, u * 0.11f).list())
                pause(300)
            },
            TutorialStep(TutorialAnchor.add("balloon"), R.string.tut_comic_t3, R.string.tut_comic_t3_text, mode = EditMode.ADD) {
                tapAnchor(TutorialAnchor.add("balloon"))
                val rects = panelRects("big_top")
                val second = rects.getOrNull(1) ?: Rect(0f, h * 0.6f, w * 0.5f, h)
                val at = Offset(second.center.x, second.top + second.height * 0.2f)
                place(at, host.balloon(BalloonStyle.NARRATION, 0, at, "Bir gece, şehrin üstünde…").list())
                pause(300)
            },
        ),
    )

    private fun comicAi() = TutorialGroup(
        id = "comic_ai", kind = CanvasKind.COMIC, title = R.string.tut_comic_ai, summary = R.string.tut40_comic_ai_text,
        icon = Icons.Filled.AutoAwesome, ai = true,
        steps = listOf(
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_comic_ai1, R.string.tut_comic_ai1_text, ai = true) {
                aiTemplate("roman.konusma", p(.5f, .2f), u * 0.36f, BrushType.INK_PEN, COMIC_INK, u * 0.005f, 4)
            },
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_comic_ai2, R.string.tut_comic_ai2_text, ai = true) {
                aiTemplate("yuz.ifade.gulen", p(.3f, .55f), u * 0.3f, BrushType.INK_PEN, COMIC_INK, u * 0.005f, 5)
            },
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_lines_v38, R.string.tut_comic_ai3_text_v38, ai = true) {
                val r = u * 0.05f
                val y = h * 0.55f
                val bubbles = listOf(w * 0.64f, w * 0.64f + r * 1.7f, w * 0.64f + r * 3.4f).mapIndexed { i, x -> TutorialArt.roughCircle(Offset(x, y - i * r * 0.6f), r * (1f - i * 0.12f), 60L + i) }
                aiLines(bubbles, BrushType.INK_PEN, "#29B6F6", u * 0.005f)
            },
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_single, R.string.tut40_single_text, ai = true) {
                aiSingle(Offset(w * 0.5f, h * 0.84f), u * 0.14f, BrushType.INK_PEN, COMIC_INK, u * 0.005f, 33)
            },
            TutorialStep(TutorialAnchor.MORE, R.string.tut_ai_more, R.string.tut_ai_more_text_v38, ai = true) {
                tapAnchor(TutorialAnchor.MORE)
            },
        ),
    )
}
