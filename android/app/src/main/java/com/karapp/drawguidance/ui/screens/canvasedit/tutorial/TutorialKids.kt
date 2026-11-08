package com.karapp.drawguidance.ui.screens.canvasedit.tutorial

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.GradientSpec
import com.karapp.drawguidance.ui.components.MascotFace
import com.karapp.drawguidance.ui.components.MascotGesture
import com.karapp.drawguidance.ui.components.MascotLook
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v37 çocuklar için çizimin eğitimi: maskot Boyacık ilk girişte araçları (fırça, hazır resimler, boya kovası, silgi,
 * geri al, kâğıt rengi) tanıtır ve adım adım örnek bir resim yapar: güneş, ev, balonlar, çimen ve gökyüzü. Her adımda
 * maskot başka bir yüz ifadesine ve el hareketine bürünür ([lookOf]); açıklamalar onun konuşma balonunda yazılır.
 */
object TutorialKids {
    private const val INK = "#2A2A33"

    /** Örnek resmin yerleri (kâğıdın oranı): ev ve balonlar. */
    private val HOUSE = Rect(0.30f, 0.46f, 0.70f, 0.86f)
    private val BALLOONS = Rect(0.68f, 0.06f, 0.94f, 0.346f)

    /** Eğitimdeki basılı tutma sayacı (imlecin halkası her basışta baştan dolar). */
    private var holds = 1000

    /** Adımlar ve maskotun her adımdaki görünüşü (art arda iki adımda yüz de el de farklı). */
    private val table: List<Pair<TutorialStep, MascotLook>> by lazy {
        listOf(
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_kids_1, R.string.tut_kids_1_text) to
                MascotLook(MascotFace.HAPPY, MascotGesture.WAVE),
            TutorialStep(TutorialAnchor.simple("brush"), R.string.v45_tut_kids_pen, R.string.v45_tut_kids_pen_text) {
                tapAnchor(TutorialAnchor.simple("brush"))
                val c = p(.2f, .2f)
                val r = u * 0.075f
                // Güneş: kalın sarı daire ve turuncu ışınlar
                stroke(TutorialArt.roughCircle(c, r * 0.5f, 3), BrushType.MARKER, "#FDD835", r * 1.15f, taper = false, speed = 1.4f)
                stroke(TutorialArt.roughCircle(c, r, 4), BrushType.MARKER, "#FBC02D", u * 0.022f, taper = false)
                for (i in 0 until 8) {
                    val a = PI / 4 * i
                    val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                    stroke(listOf(c + d * (r * 1.4f), c + d * (r * 1.9f)), BrushType.MARKER, "#FB8C00", u * 0.018f, speed = 2.4f)
                }
            } to MascotLook(MascotFace.EXPLAINING, MascotGesture.BRUSH),
            TutorialStep(TutorialAnchor.simple("shape"), R.string.tut_kids_3, R.string.tut_kids_3_text) {
                tapAnchor(TutorialAnchor.simple("shape"))
                drag(p(HOUSE.left, HOUSE.top), p(HOUSE.right, HOUSE.bottom)) { a, b ->
                    listOfNotNull(TutorialArt.shape("lib.kid_house", a, b, shapeStyle(INK, 4f)))
                }
                drag(p(BALLOONS.left, BALLOONS.top), p(BALLOONS.right, BALLOONS.bottom), 500) { a, b ->
                    listOfNotNull(TutorialArt.shape("lib.kid_balloons", a, b, shapeStyle(INK, 4f)))
                }
            } to MascotLook(MascotFace.SURPRISED, MascotGesture.POINT),
            TutorialStep(TutorialAnchor.simple("paint"), R.string.tut_kids_4, R.string.tut_kids_4_text, mode = EditMode.PAINT) {
                // Evin parçaları: çatı, duvar, kapı, yuvarlak pencere, baca (tasarım biriminde 0..100)
                bucket(house(30f, 40f), "#E53935")
                bucket(house(30f, 84f), "#FDD835")
                bucket(house(50f, 86f), "#8D6E63")
                bucket(house(50f, 33f), "#4FC3F7")
                bucket(house(69f, 21f), "#8D6E63")
            } to MascotLook(MascotFace.EXCITED, MascotGesture.THUMBS_UP),
            TutorialStep(TutorialAnchor.simple("paint"), R.string.tut_kids_5, R.string.tut_kids_5_text, mode = EditMode.PAINT) {
                bucket(balloons(50f, 44f), "#EC407A", whole = true)
            } to MascotLook(MascotFace.WINKING, MascotGesture.POINT),
            // v46 v45'in boya fırçası: şeklin içinde başlayan boya dışarı taşmaz (duvara ışık)
            TutorialStep(TutorialAnchor.simple("paint_brush"), R.string.v46_tut_kids_brush, R.string.v46_tut_kids_brush_text, mode = EditMode.PAINT) {
                tapAnchor(TutorialAnchor.simple("paint_brush"))
                val wall = (0..8).map { i -> house(36f + i * 3.5f, if (i % 2 == 0) 62f else 70f) }
                stroke(TutorialArt.smooth(wall, 5), BrushType.MARKER, "#FFE082", u * 0.035f, opacity = 0.85f, taper = false, speed = 1.6f)
            } to MascotLook(MascotFace.EXCITED, MascotGesture.BRUSH),
            TutorialStep(TutorialAnchor.simple("eraser"), R.string.tut_kids_6, R.string.tut_kids_6_text) {
                tapAnchor(TutorialAnchor.simple("eraser"))
                val scribble = (0..10).map { i -> p(.06f + i * 0.018f, if (i % 2 == 0) .9f else .95f) }
                stroke(scribble, BrushType.MARKER, "#90A4AE", u * 0.012f, speed = 1.6f)
                pause(250)
                stroke(scribble, BrushType.ERASER, "#000000", u * 0.06f, taper = false, speed = 1.2f)
            } to MascotLook(MascotFace.EXPLAINING, MascotGesture.WAVE),
            TutorialStep(TutorialAnchor.UNDO, R.string.tut_kids_7, R.string.tut_kids_7_text) {
                tapAnchor(TutorialAnchor.UNDO)
                host.undo()
                pause(700)
                tapAnchor(TutorialAnchor.REDO)
                host.redo()
                pause(400)
            } to MascotLook(MascotFace.THINKING, MascotGesture.THINK),
            TutorialStep(TutorialAnchor.simple("background"), R.string.tut_kids_8, R.string.tut_kids_8_text) {
                tapAnchor(TutorialAnchor.simple("background"))
                host.setPaperGradient(GradientSpec(listOf("#81D4FA", "#E1F5FE"), 90f))
                pause(500)
            } to MascotLook(MascotFace.HAPPY, MascotGesture.POINT),
            // v40 cetvel: kenarından çizilen çizgi dümdüz (uçurtma ipi gibi)
            TutorialStep(TutorialAnchor.simple("ruler"), R.string.tut40_kids_ruler, R.string.tut40_kids_ruler_text) {
                with(TutorialV40) { kidsRuler(p(.42f, .3f), -24f, "#7E57C2") }
            } to MascotLook(MascotFace.EXCITED, MascotGesture.BRUSH),
            // v40 50 seviyelik çizim görevi
            TutorialStep(TutorialAnchor.simple("challenge"), R.string.tut40_kids_challenge, R.string.tut40_kids_challenge_text) {
                tapAnchor(TutorialAnchor.simple("challenge"))
                pause(400)
            } to MascotLook(MascotFace.SURPRISED, MascotGesture.THUMBS_UP),
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut_kids_9, R.string.tut40_kids_end_text) {
                // Çimen: evin altında dalgalı yeşil çizgiler
                val grass = (0..20).map { i -> p(i / 20f, .925f + if (i % 2 == 0) 0f else 0.012f) }
                stroke(TutorialArt.smooth(grass, 4), BrushType.MARKER, "#7CB342", u * 0.05f, taper = false, speed = 2.2f)
                stroke(TutorialArt.smooth(grass.map { it + Offset(0f, u * 0.04f) }, 4), BrushType.MARKER, "#558B2F", u * 0.05f, taper = false, speed = 2.2f)
                pause(300)
            } to MascotLook(MascotFace.PROUD, MascotGesture.CHEER),
        )
    }

    val intro: TutorialGroup by lazy {
        TutorialGroup(
            id = "kids_intro", kind = CanvasKind.KIDS, title = R.string.tut_kids, summary = R.string.tut_kids_text,
            icon = Icons.Filled.ChildCare, ai = false, steps = table.map { it.first },
        )
    }

    /**
     * v40 eski tanıtımı bitirmiş çocuklara yenilikler (bir kez kendiliğinden): cetvel, 5 sn basılı tutup taşıma ve
     * 50 seviyelik çizim görevi.
     */
    private val news: List<Pair<TutorialStep, MascotLook>> by lazy {
        listOf(
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_kids_hi, R.string.tut40_kids_hi_text) to
                MascotLook(MascotFace.HAPPY, MascotGesture.WAVE),
            TutorialStep(TutorialAnchor.simple("ruler"), R.string.tut40_kids_ruler, R.string.tut40_kids_ruler_text) {
                with(TutorialV40) { kidsRuler(p(.5f, .3f), -15f, "#7E57C2") }
            } to MascotLook(MascotFace.EXPLAINING, MascotGesture.BRUSH),
            TutorialStep(TutorialAnchor.CANVAS, R.string.tut40_kids_hold, R.string.tut40_kids_hold_text) {
                val balloons = drag(p(BALLOONS.left, BALLOONS.top + 0.3f), p(BALLOONS.right, BALLOONS.bottom + 0.3f), 500) { a, b ->
                    listOfNotNull(TutorialArt.shape("lib.kid_balloons", a, b, shapeStyle(INK, 4f)))
                }.firstOrNull() ?: return@TutorialStep
                pause(300)
                with(TutorialV40) { holdMove(balloons, Offset(-w * 0.5f, -h * 0.25f)) }
            } to MascotLook(MascotFace.SURPRISED, MascotGesture.POINT),
            TutorialStep(TutorialAnchor.simple("challenge"), R.string.tut40_kids_challenge, R.string.tut40_kids_challenge_text) {
                tapAnchor(TutorialAnchor.simple("challenge"))
                pause(400)
            } to MascotLook(MascotFace.EXCITED, MascotGesture.CHEER),
        )
    }

    val whatsNew: TutorialGroup by lazy {
        TutorialGroup(
            id = "kids_v40", kind = CanvasKind.KIDS, title = R.string.tut40_kids_new, summary = R.string.tut40_kids_new_text,
            icon = Icons.Filled.ChildCare, ai = false, steps = news.map { it.first },
        )
    }

    /** Maskotun bu adımdaki yüz ifadesi ve el hareketi. */
    fun lookOf(step: TutorialStep): MascotLook =
        (table.firstOrNull { it.first === step } ?: news.firstOrNull { it.first === step })?.second ?: MascotLook.Idle

    private fun TutorialDemo.house(x: Float, y: Float) = inBox(HOUSE, x, y)
    private fun TutorialDemo.balloons(x: Float, y: Float) = inBox(BALLOONS, x, y)

    /** Çizimin tasarım kutusunda (0..100) nokta → kâğıt noktası. */
    private fun TutorialDemo.inBox(box: Rect, x: Float, y: Float): Offset {
        val a = p(box.left, box.top)
        val b = p(box.right, box.bottom)
        return Offset(a.x + (b.x - a.x) * x / 100f, a.y + (b.y - a.y) * y / 100f)
    }

    /** Son imleç yeri (kova bir sonraki yere oradan kayar). */
    private var lastBucket: Offset? = null

    /**
     * Kova imleci [at] noktasına kayar, altındaki alan parlar; dokunur ([whole] ise 2 sn basılı tutar, halka dolar) ve
     * alan ya da bütün şekil [color] ile boyanır.
     */
    private suspend fun TutorialDemo.bucket(at: Offset, color: String, whole: Boolean = false) {
        if (!fast) {
            val from = lastBucket ?: (at + Offset(u * 0.15f, u * 0.1f))
            val frames = 14
            for (f in 1..frames) {
                if (fast) break
                host.paintCursor(from + (at - from) * (f / frames.toFloat()), -1, color)
                pause(TutorialDemo.FRAME_MS)
            }
            host.paintCursor(at, if (whole) ++holds else -1, color)
            pause(if (whole) 2_050 else 520)
        }
        host.paint(at, color, whole)
        lastBucket = at
        if (!fast) {
            host.paintCursor(at, -1, color)
            pause(300)
        }
        host.paintCursor(null, -1, null)
    }
}
