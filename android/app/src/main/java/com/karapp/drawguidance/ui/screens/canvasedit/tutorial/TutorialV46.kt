package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.comic.ComicSfx
import com.karapp.drawguidance.content.comic.ComicTemplates
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.GradientSpec
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.TextBox
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.data.model.TextSpec
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.TextRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.SelectionMenuIds
import com.karapp.drawguidance.ui.screens.canvasedit.state.NotesPanels
import com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets
import java.util.UUID

/*
 * v46 eğitimlerin yenilikleri: v44–v46'da değişen arayüze göre (iki parçalı üst menü, boş yere dokununca ekleme
 * menüsü, seçili elemanın köşesindeki seçim menüsü, Panel ekle, formülle yeniden çizilen bilim ve element şekilleri,
 * gerçek panel şablonları, hazır mekân ve karakterler, kapak ve hazır şablonlar). Çizimler uygulamanın kendi hazır
 * elemanlarıyla kurulur: gradyanlı manzara, Cornell düzeninde ders notu, mekânlı ve karakterli çizgi roman sayfası.
 */
internal object TutorialV46 {
    private const val INK = "#2B2F4A"
    private const val NOTE_INK = "#263238"
    private const val COMIC_INK = "#111111"
    private const val ACCENT = "#1565C0"
    private const val RED = "#C62828"

    private fun Stroke?.list() = listOfNotNull(this)

    private val q = com.karapp.drawguidance.data.local.ContentCodec::quantize

    /** Kutulu yazı (satırlar [width]'e göre kayar). */
    private fun text(content: String, left: Float, top: Float, size: Float, color: String, font: TextFont, bold: Boolean = false, width: Float? = null): Stroke {
        val spec = TextSpec(content, font = font, bold = bold, box = width?.let { TextBox(width = it) })
        val points = TextRenderer.quadAt(spec, size, left, top).map { StrokePoint(q(it.x), q(it.y)) }
        return Stroke(UUID.randomUUID().toString(), points, color, BrushType.TEXT, size, 1f, 0, text = spec)
    }

    private fun Stroke.bottom(): Float = points.maxOf { it.y }

    /** Formülle çizilen bilim şekli ([params] değerleri). */
    private fun param(kind: String, r: Rect, params: Map<String, String>, id: String = UUID.randomUUID().toString(), width: Float = 3f): Stroke? =
        ShapeFactory.build(kind, r.topLeft, r.bottomRight, ShapeStyle(NOTE_INK, width), null, id, 0, params = params)

    /** Şeklin değerleri değişir; şekil aynı kutuda yeniden çizilir (formül alanına yazmak gibi). */
    private suspend fun TutorialDemo.redraw(id: String, kind: String, r: Rect, params: Map<String, String>) {
        val next = param(kind, r, params, id) ?: return
        host.mutate { list -> list.map { if (it.id == id) next.copy(order = it.order, layerId = it.layerId) else it } }
        pause(650)
    }

    private fun center(s: Stroke): Offset {
        val xs = s.points.map { it.x }; val ys = s.points.map { it.y }
        return Offset((xs.min() + xs.max()) / 2f, (ys.min() + ys.max()) / 2f)
    }

    // ------------------------------------------------------------------ Çizim

    val art: TutorialGroup by lazy {
        TutorialGroup(
            id = "art_v46", kind = CanvasKind.ART, title = R.string.v46_tut_art, summary = R.string.v46_tut_art_text,
            icon = Icons.Filled.NewReleases, ai = false,
            steps = listOf(
                // İki parçalı üst menü: gökyüzü gradyanı ve fırçayla dağ silueti
                TutorialStep(TutorialAnchor.BRUSH, R.string.v46_tut_split, R.string.v46_tut_split_text) {
                    tapAnchor(TutorialAnchor.BRUSH)
                    host.setPaperGradient(GradientSpec(listOf("#FFB88C", "#DE6262", "#3A1C71"), 90f))
                    pause(350)
                    val ridge = listOf(0f to .64f, .14f to .55f, .26f to .6f, .4f to .46f, .55f to .58f, .7f to .5f, .86f to .6f, 1f to .54f)
                    stroke(TutorialArt.smooth(ridge.map { (x, y) -> p(x, y) }, 8), BrushType.SOFT_ROUND, "#2E1A47", u * 0.02f, taper = false, speed = 1.6f)
                },
                // Boş yere dokununca ekleme menüsü: dağlar ve güneş
                TutorialStep(TutorialAnchor.mode(EditMode.ADD), R.string.v46_tut_addmenu, R.string.v46_tut_addmenu_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.mode(EditMode.ADD))
                    tap(p(.62f, .3f))
                    drag(p(.5f, .14f), p(.74f, .38f), 520) { a, b ->
                        TutorialArt.shape("CIRCLE", a, b, shapeStyle("#FFB74D", u * 0.003f, fill = "#FFB74D", gradient = GradientSpec(listOf("#FFF3B0", "#FFB74D", "#FF7043"), 90f))).list()
                    }
                    tap(p(.3f, .7f))
                    drag(p(.02f, .42f), p(.56f, .9f), 520) { a, b ->
                        TutorialArt.shape("TRIANGLE", a, b, shapeStyle("#3A2F6B", u * 0.003f, fill = "#5B4B8A", gradient = GradientSpec(listOf("#7B6BB0", "#3A2F6B"), 90f))).list()
                    }
                    drag(p(.4f, .5f), p(1f, .92f), 520) { a, b ->
                        TutorialArt.shape("TRIANGLE", a, b, shapeStyle("#24304F", u * 0.003f, fill = "#33416B", gradient = GradientSpec(listOf("#4F5D8C", "#24304F"), 90f))).list()
                    }
                },
                // Seçim menüsü: güneşin köşesinde Taşı, Ölçekle, Kopyala, Sil, Düzenle
                TutorialStep(TutorialAnchor.mode(EditMode.SELECT), R.string.v46_tut_selmenu, R.string.v46_tut_selmenu_text, mode = EditMode.SELECT) {
                    tapAnchor(TutorialAnchor.mode(EditMode.SELECT))
                    val sun = host.canvas()?.strokes?.firstOrNull { it.shape?.kind == "CIRCLE" } ?: return@TutorialStep
                    tap(center(sun))
                    host.select(setOf(sun.id))
                    pause(500)
                },
                TutorialStep(TutorialAnchor.selection(SelectionMenuIds.SCALE), R.string.v46_tut_scale, R.string.v46_tut_scale_text, mode = EditMode.SELECT) {
                    val sun = host.canvas()?.strokes?.firstOrNull { it.shape?.kind == "CIRCLE" } ?: return@TutorialStep
                    host.select(setOf(sun.id))
                    tapAnchor(TutorialAnchor.selection(SelectionMenuIds.SCALE))
                    host.scaleSelected(0.8f)
                    pause(300)
                    // Kopyala: kuşlar (fırça çizgisi kopyalanıp kaydırılır)
                    val bird = stroke(TutorialArt.smooth(listOf(p(.2f, .22f), p(.235f, .2f), p(.255f, .225f), p(.275f, .2f), p(.31f, .22f)), 8), BrushType.INK_PEN, "#2E1A47", u * 0.005f, speed = 1.4f)
                    host.select(setOf(bird.id))
                    tapAnchor(TutorialAnchor.selection(SelectionMenuIds.COPY))
                    val copies = listOf(Offset(w * 0.08f, -h * 0.03f), Offset(w * 0.15f, h * 0.02f)).map { d ->
                        bird.copy(id = UUID.randomUUID().toString(), points = bird.points.map { it.copy(x = it.x + d.x, y = it.y + d.y) })
                    }
                    place(null, copies)
                },
                TutorialStep(TutorialAnchor.selection(SelectionMenuIds.EDIT), R.string.v46_tut_edit, R.string.v46_tut_edit_text, mode = EditMode.SELECT) {
                    val sun = host.canvas()?.strokes?.firstOrNull { it.shape?.kind == "CIRCLE" } ?: return@TutorialStep
                    host.select(setOf(sun.id))
                    tapAnchor(TutorialAnchor.selection(SelectionMenuIds.EDIT))
                    pause(600)
                    host.select(emptySet())
                },
            ),
        )
    }

    // ------------------------------------------------------------------ Not alma

    /** Cornell alanları (anahtar sütun, not alanı, özet) bu sayfada. */
    private fun TutorialDemo.cornell(): List<Rect> =
        NotesPanels.byId("cornell")!!.rects(w, h).map { Rect(it[0], it[1], it[2], it[3]) }

    val notes: TutorialGroup by lazy {
        TutorialGroup(
            id = "notes_v46", kind = CanvasKind.NOTES, title = R.string.v46_tut_notes, summary = R.string.v46_tut_notes_text,
            icon = Icons.Filled.NewReleases, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.add("panels"), R.string.v46_tut_panels, R.string.v46_tut_panels_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("panels"))
                    NotesPanels.build(NotesPanels.byId("cornell")!!, w, h).forEach { place(null, listOf(it)); pause(220) }
                },
                TutorialStep(TutorialAnchor.add("text"), R.string.v46_tut_titles, R.string.v46_tut_titles_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("text"))
                    val (cue, notes) = cornell().let { it[0] to it[1] }
                    val pad = u * 0.02f
                    val title = text(I18n.tr("Kuvvet ve hareket"), notes.left + pad, notes.top + pad, u * 0.045f, NOTE_INK, TextFont.POPPINS, bold = true, width = notes.width - 2 * pad)
                    place(Offset(notes.center.x, notes.top + pad), listOf(title))
                    val q1 = text(I18n.tr("F = m·a neyi anlatır?"), cue.left + pad, title.bottom() + u * 0.06f, u * 0.032f, RED, TextFont.CAVEAT, width = cue.width - 2 * pad)
                    place(Offset(cue.center.x, q1.points.minOf { it.y }), listOf(q1))
                },
                TutorialStep(TutorialAnchor.add("shape"), R.string.v46_tut_param, R.string.v46_tut_param_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("shape"))
                    val notes = cornell()[1]
                    val r = freeBodyRect(notes)
                    val s = param(FREE_BODY, r, mapOf("m" to "4", "F" to "20", "alpha" to "0", "mu" to "0.2"), id = FREE_BODY_ID) ?: return@TutorialStep
                    drag(r.topLeft, r.bottomRight, 600) { _, _ -> listOf(s) }
                },
                TutorialStep(TutorialAnchor.CANVAS, R.string.v46_tut_values, R.string.v46_tut_values_text) {
                    val r = freeBodyRect(cornell()[1])
                    redraw(FREE_BODY_ID, FREE_BODY, r, mapOf("m" to "4", "F" to "40", "alpha" to "0", "mu" to "0.2"))
                    redraw(FREE_BODY_ID, FREE_BODY, r, mapOf("m" to "4", "F" to "40", "alpha" to "30", "mu" to "0.2"))
                },
                TutorialStep(TutorialAnchor.add("shape"), R.string.v46_tut_element, R.string.v46_tut_element_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("shape"))
                    val notes = cornell()[1]
                    val side = minOf(notes.width * 0.34f, notes.height * 0.24f)
                    val r = Rect(notes.right - side - u * 0.03f, notes.bottom - side - u * 0.03f, notes.right - u * 0.03f, notes.bottom - u * 0.03f)
                    val atom = param(BOHR, r, mapOf("z" to "11"), id = BOHR_ID, width = 2f) ?: return@TutorialStep
                    drag(r.topLeft, r.bottomRight, 500) { _, _ -> listOf(atom) }
                    pause(300)
                    redraw(BOHR_ID, BOHR, r, mapOf("z" to "17"))
                },
                TutorialStep(TutorialAnchor.add("formula"), R.string.v46_tut_formula, R.string.v46_tut_formula_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("formula"))
                    val (_, notes, summary) = cornell().let { Triple(it[0], it[1], it[2]) }
                    val r = freeBodyRect(notes)
                    val f = TutorialArt.formula("physics.newton2", notes.left + u * 0.03f, r.bottom + u * 0.02f, u * 0.045f, ACCENT)
                    place(Offset(notes.left + u * 0.1f, r.bottom + u * 0.04f), f.list())
                    val pad = u * 0.02f
                    val sum = text(I18n.tr("Özet: Net kuvvet ivmeyi belirler; sürtünme net kuvveti azaltır."), summary.left + pad, summary.top + pad, u * 0.03f, NOTE_INK, TextFont.NUNITO, width = summary.width - 2 * pad)
                    place(Offset(summary.center.x, summary.top + pad), listOf(sum))
                },
            ),
        )
    }

    private const val FREE_BODY = "lib.sci.phys.free_body"
    private const val FREE_BODY_ID = "tut46-free-body"
    private const val BOHR = "lib.sci.chem.bohr_atom"
    private const val BOHR_ID = "tut46-bohr"

    private fun TutorialDemo.freeBodyRect(notes: Rect): Rect {
        val top = notes.top + u * 0.17f
        val bw = minOf(notes.width * 0.86f, (notes.height * 0.34f) * 1.3f)
        return Rect(notes.left + u * 0.03f, top, notes.left + u * 0.03f + bw, top + bw / 1.3f)
    }

    // ------------------------------------------------------------------ Çizgi roman

    private fun comicPanels(w: Float, h: Float, u: Float): List<Stroke> {
        val t = ComicTemplates.byId("three_big") ?: return emptyList()
        val line = (u * 0.006f).coerceIn(2f, 14f) * t.line
        return PanelSets.build(t.page, UUID.randomUUID().toString(), w, h, ShapeStyle(strokeColorHex = COMIC_INK, strokeWidth = line), 0)
    }

    private fun TutorialDemo.panelBoxes(): List<Rect> =
        host.canvas()?.strokes?.filter { PanelSets.isPanel(it) }?.map { s ->
            Rect(s.points.minOf { it.x }, s.points.minOf { it.y }, s.points.maxOf { it.x }, s.points.maxOf { it.y })
        }?.sortedWith(compareBy({ (it.top / 10f).toInt() }, { it.left })).orEmpty()

    private fun bg(id: String) = ComicAssets.background(id)?.file
    private fun char(id: String, variant: String) = ComicAssets.character(id)?.variants?.firstOrNull { it.id == variant }?.file

    val comic: TutorialGroup by lazy {
        TutorialGroup(
            id = "comic_v46", kind = CanvasKind.COMIC, title = R.string.v46_tut_comic, summary = R.string.v46_tut_comic_text,
            icon = Icons.Filled.NewReleases, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.add("templates"), R.string.v46_tut_tpl, R.string.v46_tut_tpl_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("templates"))
                    comicPanels(w, h, u).forEach { place(null, listOf(it)); pause(240) }
                },
                TutorialStep(TutorialAnchor.add("locations"), R.string.v46_tut_places, R.string.v46_tut_places_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("locations"))
                    val boxes = panelBoxes()
                    listOf("cati_teras", "liman", "gunbatimi_cadde").zip(boxes).forEach { (id, r) ->
                        val path = bg(id) ?: return@forEach
                        val img = host.assetImage(path, r, fill = true) ?: return@forEach
                        // Mekân panelin arkasında (panel çizgisi üstte)
                        host.mutate { list -> listOf(img) + list }
                        pause(260)
                    }
                },
                TutorialStep(TutorialAnchor.add("characters"), R.string.v46_tut_chars, R.string.v46_tut_chars_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("characters"))
                    val b = panelBoxes()
                    if (b.size < 3) return@TutorialStep
                    val cast = listOf(
                        Triple(char("kaptan_hilal", "aksiyon_02"), b[0], Rect(b[0].left + b[0].width * 0.1f, b[0].top + b[0].height * 0.2f, b[0].left + b[0].width * 0.62f, b[0].bottom - b[0].height * 0.04f)),
                        Triple(char("dedektif_cemal", "ifade_01"), b[1], Rect(b[1].left + b[1].width * 0.12f, b[1].top + b[1].height * 0.26f, b[1].right - b[1].width * 0.12f, b[1].bottom)),
                        Triple(char("polis_murat", "ifade_02"), b[2], Rect(b[2].left + b[2].width * 0.12f, b[2].top + b[2].height * 0.26f, b[2].right - b[2].width * 0.12f, b[2].bottom)),
                    )
                    for ((path, _, r) in cast) {
                        val img = host.assetImage(path ?: continue, r, fill = false) ?: continue
                        place(r.center, listOf(img))
                    }
                },
                TutorialStep(TutorialAnchor.add("balloon"), R.string.v46_tut_balloons, R.string.v46_tut_balloons_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("balloon"))
                    val b = panelBoxes()
                    if (b.size < 3) return@TutorialStep
                    val n = Offset(b[0].left + b[0].width * 0.22f, b[0].top + b[0].height * 0.1f)
                    place(n, host.balloon(BalloonStyle.NARRATION, 0, n, I18n.tr("Şehrin çatılarında bir kovalamaca…")).list())
                    val s1 = Offset(b[1].center.x, b[1].top + b[1].height * 0.15f)
                    place(s1, host.balloon(BalloonStyle.SPEECH, 1, s1, I18n.tr("Limanda bir hareket var.")).list())
                    val s2 = Offset(b[2].center.x, b[2].top + b[2].height * 0.15f)
                    place(s2, host.balloon(BalloonStyle.SHOUT, 1, s2, I18n.tr("Hemen geliyorum!")).list())
                },
                TutorialStep(TutorialAnchor.add("effects"), R.string.v46_tut_fx, R.string.v46_tut_fx_text, mode = EditMode.ADD) {
                    tapAnchor(TutorialAnchor.add("effects"))
                    val top = panelBoxes().firstOrNull() ?: return@TutorialStep
                    val lines = host.comicLines(ComicLinesKind.SPEED_H, 0.7f, Rect(top.left, top.top + top.height * 0.3f, top.right, top.bottom), COMIC_INK)
                    // Hız çizgileri karakterin arkasında: mekânın hemen üstüne
                    host.mutate { list ->
                        val at = list.indexOfLast { it.brushType == BrushType.IMAGE && it.points.minOf { p -> p.y } <= top.top + 1f } + 1
                        list.toMutableList().also { it.addAll(at.coerceIn(0, it.size), lines) }
                    }
                    pause(300)
                    val at = Offset(top.left + top.width * 0.78f, top.top + top.height * 0.32f)
                    val preset = ComicSfx.presets.firstOrNull { it.text == "WHOOSH!" } ?: ComicSfx.presets.first()
                    place(at, host.sfx(preset, at, u * 0.1f).list())
                },
                TutorialStep(TutorialAnchor.PAGES, R.string.v46_tut_cover, R.string.v46_tut_cover_text) {
                    pause(500)
                },
            ),
        )
    }

    // ------------------------------------------------------------------ Basit resim

    val simpleArt: TutorialGroup by lazy {
        TutorialGroup(
            id = "simple_art_v46", kind = CanvasKind.SIMPLE_ART, title = R.string.v46_tut_simple, summary = R.string.v46_tut_simple_art_text,
            icon = Icons.Filled.NewReleases, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.simple("brush"), R.string.v46_tut_split, R.string.v46_tut_split_text) {
                    tapAnchor(TutorialAnchor.simple("brush"))
                    host.setPaperGradient(GradientSpec(listOf("#B3E5FC", "#E1F5FE"), 90f))
                    val hill = (0..12).map { i -> p(i / 12f, .82f + 0.03f * kotlin.math.sin(i * 0.8f)) }
                    stroke(TutorialArt.smooth(hill, 6), BrushType.MARKER, "#7CB342", u * 0.035f, taper = false, speed = 1.8f)
                },
                TutorialStep(TutorialAnchor.simple("shape"), R.string.v46_tut_s_shape, R.string.v46_tut_s_shape_text) {
                    tapAnchor(TutorialAnchor.simple("shape"))
                    drag(p(.3f, .42f), p(.7f, .8f)) { a, b -> TutorialArt.shape("HOUSE", a, b, shapeStyle(INK, u * 0.005f, fill = "#FFE0B2")).list() }
                    drag(p(.72f, .08f), p(.9f, .26f), 450) { a, b -> TutorialArt.shape("SUN", a, b, shapeStyle("#F59E0B", u * 0.004f, fill = "#FFC94D")).list() }
                },
                TutorialStep(TutorialAnchor.simple("rect_select"), R.string.v46_tut_s_select, R.string.v46_tut_s_select_text) {
                    tapAnchor(TutorialAnchor.simple("rect_select"))
                    val house = host.canvas()?.strokes?.firstOrNull { it.shape?.kind == "HOUSE" } ?: return@TutorialStep
                    drag(p(.26f, .38f), p(.74f, .84f), 600) { _, _ -> emptyList() }
                    host.select(setOf(house.id))
                    pause(400)
                    host.scaleSelected(0.9f)
                    pause(300)
                    host.select(emptySet())
                },
                TutorialStep(TutorialAnchor.simple("eyedropper"), R.string.v46_tut_s_eyedrop, R.string.v46_tut_s_eyedrop_text) {
                    tapAnchor(TutorialAnchor.simple("eyedropper"))
                    tap(p(.81f, .17f))
                    pause(300)
                },
            ),
        )
    }

    // ------------------------------------------------------------------ Basit not

    val simpleNotes: TutorialGroup by lazy {
        TutorialGroup(
            id = "simple_notes_v46", kind = CanvasKind.SIMPLE_NOTES, title = R.string.v46_tut_simple, summary = R.string.v46_tut_simple_notes_text,
            icon = Icons.Filled.NewReleases, ai = false,
            steps = listOf(
                TutorialStep(TutorialAnchor.simple("panels"), R.string.v46_tut_panels, R.string.v46_tut_panels_text) {
                    tapAnchor(TutorialAnchor.simple("panels"))
                    NotesPanels.build(NotesPanels.byId("square_notes")!!, w, h).forEach { place(null, listOf(it)); pause(220) }
                },
                TutorialStep(TutorialAnchor.simple("shape"), R.string.v46_tut_param, R.string.v46_tut_param_text) {
                    tapAnchor(TutorialAnchor.simple("shape"))
                    val r = leverRect()
                    val s = param(LEVER, r, mapOf("P" to "60", "a" to "1", "b" to "2"), id = LEVER_ID) ?: return@TutorialStep
                    drag(r.topLeft, r.bottomRight, 600) { _, _ -> listOf(s) }
                },
                TutorialStep(TutorialAnchor.CANVAS, R.string.v46_tut_values, R.string.v46_tut_values_text) {
                    val r = leverRect()
                    redraw(LEVER_ID, LEVER, r, mapOf("P" to "60", "a" to "1", "b" to "3"))
                    redraw(LEVER_ID, LEVER, r, mapOf("P" to "90", "a" to "1", "b" to "3"))
                },
            ),
        )
    }

    private const val LEVER = "lib.sci.phys.lever"
    private const val LEVER_ID = "tut46-lever"

    /** Kare + not yerleşiminin karesinde kaldıraç (1,8:1 kutu). */
    private fun TutorialDemo.leverRect(): Rect {
        val sq = NotesPanels.byId("square_notes")!!.rects(w, h)[0]
        val side = sq[2] - sq[0]
        val bw = side * 0.9f
        val bh = bw / 1.8f
        val cx = (sq[0] + sq[2]) / 2f; val cy = (sq[1] + sq[3]) / 2f
        return Rect(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
    }
}
