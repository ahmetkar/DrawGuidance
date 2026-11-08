package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.DiagramKind
import com.karapp.drawguidance.data.model.GradientSpec
import com.karapp.drawguidance.data.model.PageAlign
import com.karapp.drawguidance.data.model.PageNumberFormat
import com.karapp.drawguidance.data.model.PageSettings
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.drawing.DiagramThemes
import com.karapp.drawguidance.ui.drawing.TextBoxResize
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.GuideSession
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerController
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerHandle
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerPose
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerShape
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerType
import com.karapp.drawguidance.ui.screens.canvasedit.ruler.RulerUnit

/**
 * v40 eğitimleri: gelişmiş türlerde (resim, not alma, çizgi roman) bu sürümün yenilikleri ayrı bir grupta (eski
 * eğitimi bitirmiş kullanıcıya ilk girişte bir kez kendiliğinden gösterilir), basit resim ve basit not için kısa,
 * sade dilli tanıtımlar. Gösterilenler: 5 sn basılı tutup taşıma, arka planı taşıma, cetvel (tür, tutamaklar, birim,
 * ölçek, kenar boyunca çizip Bitir) ve şekil kalıbı, CMYK ve kaydetme yeri, sayfa kaydırıcısı, zeminli/zeminsiz
 * diyagram, sayfa numarası ve üst/alt bilgi, PDF/CBZ, hazır çizgi roman karakterleri ve arka planları, basit türlerde
 * yazı kutusunu boyutlandırma ve boya.
 */
internal object TutorialV40 {
    private const val INK = "#2B2F4A"
    private const val NOTE_INK = "#1C1B19"
    private const val BALLPOINT = "#1E3A8A"
    private const val COMIC_INK = "#111111"

    private fun Stroke?.list() = listOfNotNull(this)

    // ------------------------------------------------------------------ ortak gösterimler

    /** Çizimin sınır kutusunun ortası. */
    private fun center(s: Stroke): Offset {
        val xs = s.points.map { it.x }; val ys = s.points.map { it.y }
        return Offset((xs.min() + xs.max()) / 2f, (ys.min() + ys.max()) / 2f)
    }

    /**
     * 5 sn basılı tutunca Taşı: parmak elemanın üstünde basılı kalır, halka dolar; eleman seçilir, Taşı açılır ve
     * parmak kalkmadan sürüklenince eleman [by] kadar kayar.
     */
    suspend fun TutorialDemo.holdMove(stroke: Stroke, by: Offset) {
        val at = center(stroke)
        hold(at)
        host.select(setOf(stroke.id))
        mode(EditMode.MOVE)
        val n = if (fast) 1 else 26
        for (i in 1..n) {
            host.moveSelected(by.x / n, by.y / n)
            if (!fast) live(TutorialLive(pointer = at + by * (i / n.toFloat()), pressed = true))
            pause(30)
        }
        live(TutorialLive())
        pause(300)
    }

    /** Cetvel açılır (açıksa yerinde kalır); [type] seçilir (ayar eğitim bitince eski haline döner). */
    fun TutorialDemo.ruler(type: RulerType = RulerType.STRAIGHT, center: Offset = p(.5f, .5f)): RulerController? {
        val g = host.guides ?: return null
        if (g.settings.value.type != type) g.setType(type)
        if (g.session.value != GuideSession.RULER) g.openRuler(center, w * 0.9f, u * 0.075f)
        return g
    }

    /** Cetvelin duruşu [target]'a doğru yavaşça değişir; parmak [handle] tutamağını sürükler. */
    suspend fun TutorialDemo.movePose(g: RulerController, handle: RulerHandle, durationMs: Long = 800, target: (RulerPose) -> RulerPose) {
        val start = g.pose.value ?: return
        val end = target(start)
        if (!fast) {
            val gap = u * 0.035f
            RulerShape(start).handle(handle, gap)?.let { live(TutorialLive(pointer = it)); pause(260) }
            val frames = (durationMs / TutorialDemo.FRAME_MS).toInt().coerceAtLeast(2)
            for (f in 1..frames) {
                if (fast) break
                val t = f / frames.toFloat()
                val e = 1f - (1f - t) * (1f - t)
                val pose = start.copy(
                    cx = start.cx + (end.cx - start.cx) * e, cy = start.cy + (end.cy - start.cy) * e,
                    angle = start.angle + (end.angle - start.angle) * e, length = start.length + (end.length - start.length) * e,
                )
                g.setPose(pose)
                live(TutorialLive(pointer = RulerShape(pose).handle(handle, gap), pressed = true))
                pause(TutorialDemo.FRAME_MS)
            }
            live(TutorialLive())
        }
        g.setPose(end)
    }

    /** Cetvelin üst (ya da alt) kenarı boyunca çizilen düz çizgi (gerçek çizimde çizgi kenara yapışır). */
    suspend fun TutorialDemo.alongEdge(g: RulerController, brush: BrushType, color: String, width: Float, top: Boolean = true): Stroke? {
        val pose = g.pose.value ?: return null
        val shape = RulerShape(pose)
        val half = pose.length / 2f
        val y = (if (top) -1f else 1f) * (pose.thickness / 2f + width * 0.6f)
        return stroke(listOf(shape.toWorld(-half * 0.86f, y), shape.toWorld(half * 0.86f, y)), brush, color, width, taper = false, speed = 0.8f)
    }

    /** Bitir'e dokunulur: normal çizime dönülür. */
    suspend fun TutorialDemo.finishGuide(g: RulerController) {
        tapAnchor(TutorialAnchor.RULER_FINISH)
        g.finish()
    }

    /**
     * Şekil kalıbı: kütüphaneden [shape] seçilir, [center] çevresinde [size] boyunda kalıp olur ve kenarı boyunca
     * çizilerek kopyalanır. Kalıp açık kalır.
     */
    suspend fun TutorialDemo.traceTemplate(shape: String, center: Offset, size: Float, brush: BrushType, color: String, width: Float): Stroke? {
        val g = host.guides ?: return null
        if (g.session.value != GuideSession.TEMPLATE || g.template.value == null) {
            g.openTemplate()
            pause(500)
            tapAnchor(TutorialAnchor.TEMPLATE_LIBRARY)
            if (!g.placeLibrary(shape, center, size)) return null
            pause(450)
        }
        val ghost = g.template.value ?: return null
        val (pts, closed) = ghost.contours.maxByOrNull { it.first.size } ?: return null
        val path = (0 until pts.size / 2).map { Offset(pts[2 * it], pts[2 * it + 1]) }.let { if (closed && it.isNotEmpty()) it + it.first() else it }
        if (path.size < 2) return null
        return stroke(path, brush, color, width, taper = false, speed = 1.3f)
    }

    /**
     * Basit türlerde tek adımda cetvel (Cetvel düğmesi; kenar boyunca çizip Bitir) ve isteğe bağlı şekil kalıbı
     * (v40 ayrı Şekil kalıbı düğmesi).
     */
    private suspend fun TutorialDemo.simpleRuler(
        anchor: String, rulerAt: Offset, angle: Float, brush: BrushType, color: String, width: Float,
        templateShape: String? = null, templateAt: Offset = Offset.Zero, templateSize: Float = 0f,
        templateAnchor: String = TutorialAnchor.simple("template"),
    ) {
        tapAnchor(anchor)
        val g = ruler(center = rulerAt) ?: return
        movePose(g, RulerHandle.ROTATE, 600) { it.copy(cx = rulerAt.x, cy = rulerAt.y, angle = angle) }
        alongEdge(g, brush, color, width)
        pause(250)
        finishGuide(g)
        if (templateShape == null) return
        pause(300)
        tapAnchor(templateAnchor)
        traceTemplate(templateShape, templateAt, templateSize, brush, color, width)
        pause(250)
        finishGuide(g)
    }

    /** Yazı kutusunun köşe tutamağı sürüklenir: kutu büyür (basit türlerde Seç'te de tutamaklar görünür). */
    private suspend fun TutorialDemo.resizeText(id: String, by: Offset) {
        mode(EditMode.SELECT)
        host.select(setOf(id))
        pause(400)
        var cur = host.canvas()?.strokes?.firstOrNull { it.id == id } ?: return
        val start = TextBoxResize.handles(cur).firstOrNull { it.second == TextBoxResize.BOTH }?.first ?: return
        if (!fast) { live(TutorialLive(pointer = start)); pause(300) }
        val n = if (fast) 1 else 22
        for (i in 1..n) {
            val pt = start + by * (i / n.toFloat())
            val next = TextBoxResize.resize(cur, TextBoxResize.BOTH, pt)
            if (next != null) {
                host.mutate { list -> list.map { if (it.id == id) next else it } }
                cur = next
            }
            if (!fast) live(TutorialLive(pointer = pt, pressed = true))
            pause(30)
        }
        live(TutorialLive())
        pause(300)
    }

    /** Sayfa şeridine uzun basma (kaydırıcı açılır) ve sağa sola kaydırma. */
    private suspend fun TutorialDemo.longPressAnchor(anchor: String) {
        if (fast) return
        live(TutorialLive(pointerAnchor = anchor))
        pause(300)
        live(TutorialLive(pointerAnchor = anchor, pressed = true))
        pause(1_100)
        live(TutorialLive())
    }

    /** Zeminli ve zeminsiz iki diyagram alt alta. */
    private suspend fun TutorialDemo.twoDiagrams(top: Float, height: Float, left: Float, right: Float, ink: String) {
        val style = shapeStyle(ink, u * 0.003f)
        val a = Rect(left, top, right, top + height)
        drag(a.topLeft, a.bottomRight, 600) { x, y -> listOf(TutorialArt.diagram(DiagramKind.PROCESS, Rect(x, y), style, DiagramThemes.EDITORIAL)) }
        pause(300)
        val b = a.translate(0f, height + u * 0.06f)
        drag(b.topLeft, b.bottomRight, 600) { x, y ->
            listOf(TutorialArt.diagram(DiagramKind.PROCESS, Rect(x, y), style, DiagramThemes.withBackground(DiagramThemes.EDITORIAL, false)))
        }
    }

    private fun TutorialDemo.firstOf(type: BrushType): Stroke? = host.canvas()?.strokes?.firstOrNull { it.brushType == type }

    // ------------------------------------------------------------------ Resim: yenilikler

    val art: TutorialGroup by lazy {
        TutorialGroup(
            id = "art_v40", kind = CanvasKind.ART, title = R.string.tut40_art, summary = R.string.tut40_art_text,
            icon = Icons.Filled.NewReleases, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_hold, R.string.tut40_hold_text) {
                    host.setPaperGradient(GradientSpec(listOf("#BFE3FF", "#FFF1DC"), 90f))
                    val sun = drag(p(.12f, .1f), p(.36f, .34f), 450) { a, b ->
                        TutorialArt.shape("SUN", a, b, shapeStyle("#F59E0B", u * 0.004f, fill = "#FFC94D")).list()
                    }.firstOrNull() ?: return@TutorialStep
                    pause(300)
                    holdMove(sun, Offset(w * 0.46f, h * 0.06f))
                },
                TutorialStep(TutorialAnchor.BACKGROUND, R.string.tut40_bg, R.string.tut40_bg_text) {
                    tapAnchor(TutorialAnchor.BACKGROUND)
                },
                TutorialStep(TutorialAnchor.RULER, R.string.tut40_ruler, R.string.tut40_ruler_text, mode = EditMode.DRAW) {
                    tapAnchor(TutorialAnchor.RULER)
                    val g = ruler(center = p(.5f, .55f)) ?: return@TutorialStep
                    pause(400)
                    movePose(g, RulerHandle.ROTATE) { it.copy(angle = -15f) }
                    movePose(g, RulerHandle.SIZE) { it.copy(length = w * 0.7f) }
                },
                TutorialStep(TutorialAnchor.RULER_BAR, R.string.tut40_ruler_bar, R.string.tut40_ruler_bar_text, guide = true) {
                    val g = ruler(center = p(.5f, .55f)) ?: return@TutorialStep
                    tapAnchor(TutorialAnchor.RULER_TYPE)
                    g.setType(RulerType.PROTRACTOR); pause(800)
                    g.setType(RulerType.SET_SQUARE_45); pause(800)
                    g.setType(RulerType.STRAIGHT); pause(300)
                    tapAnchor(TutorialAnchor.RULER_UNIT)
                    g.setUnit(RulerUnit.MM); pause(500)
                    tapAnchor(TutorialAnchor.RULER_SCALE)
                    g.setScale(2); pause(500)
                },
                TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_ruler_draw, R.string.tut40_ruler_draw_text, guide = true) {
                    val g = ruler(center = p(.5f, .55f)) ?: return@TutorialStep
                    alongEdge(g, BrushType.INK_PEN, INK, u * 0.006f, top = true)
                    alongEdge(g, BrushType.INK_PEN, INK, u * 0.006f, top = false)
                    pause(250)
                    finishGuide(g)
                },
                TutorialStep(TutorialAnchor.TEMPLATE, R.string.tut40_template, R.string.tut40_template_text, mode = EditMode.DRAW) {
                    tapAnchor(TutorialAnchor.TEMPLATE)
                    traceTemplate("STAR", p(.74f, .8f), u * 0.3f, BrushType.INK_PEN, "#E11D48", u * 0.006f)
                },
                TutorialStep(TutorialAnchor.SAVE, R.string.tut40_save, R.string.tut40_save_text) {
                    tapAnchor(TutorialAnchor.SAVE)
                },
            ),
        )
    }

    // ------------------------------------------------------------------ Not alma: yenilikler

    val notes: TutorialGroup by lazy {
        TutorialGroup(
            id = "notes_v40", kind = CanvasKind.NOTES, title = R.string.tut40_notes, summary = R.string.tut40_notes_text,
            icon = Icons.Filled.NewReleases, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.PAGES, R.string.tut40_scrubber, R.string.tut40_scrubber_text) {
                    longPressAnchor(TutorialAnchor.PAGES)
                },
                TutorialStep(TutorialAnchor.add("diagram"), R.string.tut40_diagram_bg, R.string.tut40_diagram_bg_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("diagram"))
                    twoDiagrams(h * 0.1f, minOf(h * 0.16f, u * 0.3f), w * 0.08f, w * 0.92f, NOTE_INK)
                },
                TutorialStep(TutorialAnchor.MORE, R.string.tut40_page_settings, R.string.tut40_page_settings_text) {
                    tapAnchor(TutorialAnchor.MORE)
                    pause(300)
                    host.setPageSettings(
                        PageSettings(pageNumbers = true, numberFormat = PageNumberFormat.NUMBER_OF_TOTAL, header = I18n.tr("Ders notları"), headerAlign = PageAlign.LEFT),
                    )
                    pause(500)
                },
                TutorialStep(TutorialAnchor.RULER, R.string.tut40_ruler, R.string.tut40_ruler_text, mode = EditMode.DRAW) {
                    tapAnchor(TutorialAnchor.RULER)
                    val g = ruler(center = p(.5f, .62f)) ?: return@TutorialStep
                    pause(400)
                    movePose(g, RulerHandle.ROTATE) { it.copy(angle = 0f) }
                    movePose(g, RulerHandle.SIZE) { it.copy(length = w * 0.8f) }
                },
                TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_ruler_draw, R.string.tut40_ruler_draw_text, guide = true) {
                    val g = ruler(center = p(.5f, .62f)) ?: return@TutorialStep
                    alongEdge(g, BrushType.NOTE_BALLPOINT, BALLPOINT, u * 0.004f, top = true)
                    pause(250)
                    finishGuide(g)
                },
                TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_hold, R.string.tut40_hold_text) {
                    val diagram = firstOf(BrushType.DIAGRAM) ?: return@TutorialStep
                    holdMove(diagram, Offset(0f, h * 0.05f))
                },
                TutorialStep(TutorialAnchor.MORE, R.string.tut40_notes_pdf, R.string.tut40_notes_pdf_text) {
                    tapAnchor(TutorialAnchor.MORE)
                    pause(300)
                    tapAnchor(TutorialAnchor.SAVE)
                },
            ),
        )
    }

    // ------------------------------------------------------------------ Çizgi roman: yenilikler

    val comic: TutorialGroup by lazy {
        TutorialGroup(
            id = "comic_v40", kind = CanvasKind.COMIC, title = R.string.tut40_comic, summary = R.string.tut40_comic_text,
            icon = Icons.Filled.NewReleases, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.add("characters"), R.string.tut40_chars, R.string.tut40_chars_text, mode = EditMode.ADD) {
                    if (host.canvas()?.strokes.isNullOrEmpty()) with(TutorialScripts) { host.commitElements(panels("big_top")) }
                    pause(300)
                    tapAnchor(TutorialAnchor.add("characters"))
                },
                // v41 hazır arka planlar Ekle → Mekân arka planı'nda (panele bırakılır ya da bütün sayfa)
                TutorialStep(TutorialAnchor.add("locations"), R.string.tut40_comic_bg, R.string.tut40_comic_bg_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("locations"))
                },
                TutorialStep(TutorialAnchor.MORE, R.string.tut40_page_settings, R.string.tut40_page_settings_text) {
                    tapAnchor(TutorialAnchor.MORE)
                    pause(300)
                    host.setPageSettings(PageSettings(pageNumbers = true, header = I18n.tr("Bölüm 1"), headerAlign = PageAlign.CENTER))
                    pause(500)
                },
                TutorialStep(TutorialAnchor.MORE, R.string.tut40_comic_export, R.string.tut40_comic_export_text) {
                    tapAnchor(TutorialAnchor.MORE)
                },
                TutorialStep(TutorialAnchor.RULER, R.string.tut40_ruler, R.string.tut40_ruler_text, mode = EditMode.DRAW) {
                    tapAnchor(TutorialAnchor.RULER)
                    val g = ruler(center = p(.5f, .3f)) ?: return@TutorialStep
                    pause(400)
                    movePose(g, RulerHandle.ROTATE) { it.copy(angle = 20f) }
                    movePose(g, RulerHandle.SIZE) { it.copy(length = w * 0.6f) }
                },
                TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_ruler_draw, R.string.tut40_ruler_draw_text, guide = true) {
                    val g = ruler(center = p(.5f, .3f)) ?: return@TutorialStep
                    alongEdge(g, BrushType.INK_PEN, COMIC_INK, u * 0.005f, top = true)
                    alongEdge(g, BrushType.INK_PEN, COMIC_INK, u * 0.005f, top = false)
                    pause(250)
                    finishGuide(g)
                },
                TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_hold, R.string.tut40_hold_text) {
                    val target = host.canvas()?.strokes?.lastOrNull { it.brushType == BrushType.SHAPE } ?: firstOf(BrushType.INK_PEN) ?: return@TutorialStep
                    holdMove(target, Offset(0f, h * 0.05f))
                },
            ),
        )
    }

    // ------------------------------------------------------------------ Basit resim

    /** Evin parçalarının yeri (kâğıdın oranı; HOUSE şeklinin kutusu). */
    private val HOUSE = Rect(0.30f, 0.44f, 0.70f, 0.86f)

    private fun TutorialDemo.house(x: Float, y: Float): Offset {
        val a = p(HOUSE.left, HOUSE.top); val b = p(HOUSE.right, HOUSE.bottom)
        return Offset(a.x + (b.x - a.x) * x, a.y + (b.y - a.y) * y)
    }

    val simpleArt: TutorialGroup by lazy {
        TutorialGroup(
            id = "simple_art_intro", kind = CanvasKind.SIMPLE_ART, title = R.string.tut40_simple_art, summary = R.string.tut40_simple_art_text,
            icon = Icons.Filled.Brush, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.simple("brush"), R.string.tut40_sa_brush, R.string.tut40_sa_brush_text) {
                    tapAnchor(TutorialAnchor.simple("brush"))
                    val hill = (0..12).map { i -> p(i / 12f, .9f + 0.025f * kotlin.math.sin(i * 0.9f)) }
                    stroke(TutorialArt.smooth(hill, 6), BrushType.MARKER, "#7CB342", u * 0.03f, taper = false, speed = 1.8f)
                    stroke(TutorialArt.smooth(hill.map { it + Offset(0f, u * 0.035f) }, 6), BrushType.MARKER, "#558B2F", u * 0.03f, taper = false, speed = 1.8f)
                },
                TutorialStep(TutorialAnchor.simple("shape"), R.string.tut40_sa_shape, R.string.tut40_sa_shape_text) {
                    tapAnchor(TutorialAnchor.simple("shape"))
                    drag(p(HOUSE.left, HOUSE.top), p(HOUSE.right, HOUSE.bottom)) { a, b -> TutorialArt.shape("HOUSE", a, b, shapeStyle(INK, u * 0.005f)).list() }
                    drag(p(.08f, .08f), p(.28f, .28f), 450) { a, b -> TutorialArt.shape("SUN", a, b, shapeStyle("#F59E0B", u * 0.004f, fill = "#FFC94D")).list() }
                },
                TutorialStep(TutorialAnchor.simple("paint"), R.string.tut40_sa_paint, R.string.tut40_sa_paint_text, mode = EditMode.PAINT) {
                    listOf(
                        house(.30f, .36f) to "#E53935", house(.62f, .40f) to "#E53935", house(.22f, .90f) to "#FFD54F",
                        house(.47f, .82f) to "#8D6E63", house(.22f, .60f) to "#81D4FA", house(.70f, .60f) to "#81D4FA",
                    ).forEach { (at, color) ->
                        tap(at)
                        host.paint(at, color, false)
                        pause(250)
                    }
                },
                TutorialStep(TutorialAnchor.simple("move"), R.string.tut40_sa_move, R.string.tut40_sa_move_text) {
                    tapAnchor(TutorialAnchor.simple("select"))
                    tapAnchor(TutorialAnchor.simple("move"))
                    val sun = host.canvas()?.strokes?.firstOrNull { it.shape?.kind == "SUN" } ?: return@TutorialStep
                    holdMove(sun, Offset(w * 0.6f, 0f))
                },
                TutorialStep(TutorialAnchor.simple("text"), R.string.tut40_sa_text, R.string.tut40_sa_text_text) {
                    tapAnchor(TutorialAnchor.simple("text"))
                    val t = TutorialArt.text(I18n.tr("Merhaba!"), w * 0.08f, h * 0.06f, u * 0.06f, INK, TextFont.SANS, bold = true)
                    place(p(.2f, .09f), listOf(t))
                    resizeText(t.id, Offset(w * 0.18f, h * 0.06f))
                },
                TutorialStep(TutorialAnchor.simple("background"), R.string.tut40_sa_bg, R.string.tut40_sa_bg_text) {
                    tapAnchor(TutorialAnchor.simple("background"))
                    host.setPaperGradient(GradientSpec(listOf("#90CAF9", "#E3F2FD"), 90f))
                    pause(500)
                },
                TutorialStep(TutorialAnchor.simple("ruler"), R.string.tut40_sa_ruler, R.string.tut40_sa_ruler_text) {
                    simpleRuler(
                        TutorialAnchor.simple("ruler"), p(.5f, .38f), -8f, BrushType.MARKER, "#5E35B1", u * 0.008f,
                        templateShape = "HEART", templateAt = p(.82f, .62f), templateSize = u * 0.16f,
                    )
                },
                TutorialStep(TutorialAnchor.SAVE, R.string.tut40_sa_save, R.string.tut40_sa_save_text) {
                    tapAnchor(TutorialAnchor.SAVE)
                },
            ),
        )
    }

    // ------------------------------------------------------------------ Basit not

    val simpleNotes: TutorialGroup by lazy {
        TutorialGroup(
            id = "simple_notes_intro", kind = CanvasKind.SIMPLE_NOTES, title = R.string.tut40_simple_notes, summary = R.string.tut40_simple_notes_text,
            icon = Icons.Filled.EditNote, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.simple("text"), R.string.tut40_sn_text, R.string.tut40_sn_text_text) {
                    tapAnchor(TutorialAnchor.simple("text"))
                    val t = TutorialArt.text(I18n.tr("Günün notları"), w * 0.08f, h * 0.05f, u * 0.06f, NOTE_INK, TextFont.MERRIWEATHER, bold = true)
                    place(p(.3f, .07f), listOf(t))
                    resizeText(t.id, Offset(w * 0.12f, h * 0.02f))
                },
                TutorialStep(TutorialAnchor.simple("brush"), R.string.tut40_sn_pen, R.string.tut40_sn_pen_text) {
                    tapAnchor(TutorialAnchor.simple("brush"))
                    val top = h * 0.05f + u * 0.2f
                    TutorialArt.handwriting("ödev", 4, Rect(w * 0.08f, top, w * 0.5f, top + u * 0.075f))
                        .forEach { stroke(it, BrushType.NOTE_BALLPOINT, BALLPOINT, u * 0.0045f, speed = 1.6f) }
                },
                TutorialStep(TutorialAnchor.simple("diagram"), R.string.tut40_sn_diagram, R.string.tut40_sn_diagram_text) {
                    tapAnchor(TutorialAnchor.simple("diagram"))
                    twoDiagrams(h * 0.05f + u * 0.36f, minOf(h * 0.14f, u * 0.24f), w * 0.08f, w * 0.92f, NOTE_INK)
                },
                TutorialStep(TutorialAnchor.simple("ruler"), R.string.tut40_sn_ruler, R.string.tut40_sn_ruler_text) {
                    val y = h * 0.05f + u * 0.3f
                    simpleRuler(TutorialAnchor.simple("ruler"), Offset(w * 0.5f, y + u * 0.05f), 0f, BrushType.NOTE_BALLPOINT, BALLPOINT, u * 0.004f)
                },
                TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_hold_simple, R.string.tut40_hold_simple_text) {
                    val diagram = firstOf(BrushType.DIAGRAM) ?: return@TutorialStep
                    holdMove(diagram, Offset(0f, h * 0.04f))
                },
                TutorialStep(TutorialAnchor.PAGES, R.string.tut40_sn_pages, R.string.tut40_sn_pages_text) {
                    longPressAnchor(TutorialAnchor.PAGES)
                },
                TutorialStep(TutorialAnchor.MORE, R.string.tut40_sn_pdf, R.string.tut40_sn_pdf_text) {
                    tapAnchor(TutorialAnchor.MORE)
                    pause(300)
                    host.setPageSettings(PageSettings(pageNumbers = true, footer = I18n.tr("Günün notları"), footerAlign = PageAlign.LEFT, numberAlign = PageAlign.RIGHT))
                    pause(500)
                },
            ),
        )
    }

    /** Basit türlerde kullanılan cetvel adımı (çocuk eğitimi). */
    suspend fun TutorialDemo.kidsRuler(at: Offset, angle: Float, color: String) =
        simpleRuler(TutorialAnchor.simple("ruler"), at, angle, BrushType.MARKER, color, u * 0.014f)
}
