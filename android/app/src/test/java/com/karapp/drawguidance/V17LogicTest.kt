package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.ToolRowKind
import com.karapp.drawguidance.ui.screens.canvasedit.editor.InkSmoothing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** v17 saf mantık: grafik formülü değişince eğri yeniden çizilir, mod aileleri/üst menü türleri, çizim yumuşatma. */
class V17LogicTest {

    private val style = ShapeStyle(strokeColorHex = "#1E1E1E", strokeWidth = 2f)

    private fun curvePoints(stroke: com.karapp.drawguidance.data.model.Stroke): List<Offset> =
        ShapeFactory.subPaths(stroke).filter { it.tag == "curve" }.flatMap { it.points }

    @Test fun changingTheFormulaOfAPresetCurveRedrawsIt() {
        val id = ShapeRegistry.CURVE_PREFIX + "parabol"
        val g0 = ShapeRegistry.defaultGraph(id)
        val s0 = ShapeFactory.build(id, Offset(0f, 0f), Offset(400f, 300f), style, g0, "g", 0)!!
        val before = curvePoints(s0)
        assertTrue(before.size > 10)
        // Parabol: ortadaki nokta uçlardan belirgin aşağıda/yukarıda (doğru değil)
        fun bend(pts: List<Offset>): Float {
            val a = pts.first(); val b = pts.last()
            return pts.maxOf { p ->
                val vx = b.x - a.x; val vy = b.y - a.y
                abs((p.x - a.x) * vy - (p.y - a.y) * vx) / kotlin.math.hypot(vx, vy)
            }
        }
        assertTrue(bend(before) > 20f)
        // Formül "x" yapılınca (hazır eğri temizlenir) doğru çizilir
        val s1 = ShapeFactory.rebuild(s0, style, g0.copy(formula = "x", preset = ""))!!
        val after = curvePoints(s1)
        assertTrue(after.isNotEmpty())
        assertTrue("doğru: ${bend(after)}", bend(after) < 2f)
        // Formül silinirse hazır eğri geri gelir
        val s2 = ShapeFactory.rebuild(s1, style, g0.copy(formula = "", preset = ""))!!
        assertTrue(bend(curvePoints(s2)) > 20f)
    }

    @Test fun modeFamiliesAndTopMenuKinds() {
        // v47 dört mod: Seç, Çiz, Ekle, Düzenle (Taşı ve Genişlet Düzenle satırında)
        assertEquals(listOf(EditMode.SELECT, EditMode.DRAW, EditMode.ADD, EditMode.EDIT_PICK), EditMode.Toolbar)
        assertEquals(ToolRowKind.DRAW, EditMode.DRAW.rowKind)
        // v18: Seç'te kopyala/sil; Taşı kendi satırında; Döndür, Ölçekle, Kırp Düzenle'nin araçları
        assertEquals(ToolRowKind.SELECT, EditMode.SELECT.rowKind)
        assertEquals(ToolRowKind.MOVE, EditMode.MOVE.rowKind)
        listOf(EditMode.ROTATE, EditMode.SCALE, EditMode.CROP).forEach { assertEquals(it.name, ToolRowKind.EDIT, it.rowKind); assertEquals(EditMode.EDIT_PICK, it.family) }
        listOf(EditMode.ADD, EditMode.SHAPE, EditMode.TEXT, EditMode.FORMULA, EditMode.DIAGRAM, EditMode.GUIDE, EditMode.PERSPECTIVE)
            .forEach { assertEquals(it.name, ToolRowKind.ADD, it.rowKind) }
        // v27 kalem aracı yalnız Çiz modunun menüsünde
        assertEquals(ToolRowKind.DRAW, EditMode.PEN.rowKind)
        assertEquals(EditMode.DRAW, EditMode.PEN.family)
        listOf(EditMode.EDIT_PICK, EditMode.SHAPE_EDIT, EditMode.DIAGRAM_EDIT, EditMode.IMAGE_EDIT).forEach { assertEquals(it.name, ToolRowKind.EDIT, it.rowKind) }
        assertEquals(ToolRowKind.FRAME, EditMode.EXPAND.rowKind)
        // Mod menüsünde alt modların ailesi seçili görünür
        assertEquals(EditMode.ADD, EditMode.SHAPE.family)
        assertEquals(EditMode.EDIT_PICK, EditMode.SHAPE_EDIT.family)
        assertEquals(EditMode.EDIT_PICK, EditMode.ROTATE.family)
        // Temel modlarda kalem/silgi etkin
        listOf(EditMode.DRAW, EditMode.SELECT, EditMode.ADD, EditMode.EDIT_PICK).forEach { assertTrue(it.allowsDrawTools) }
    }

    @Test fun inkSmoothingKeepsEndsAndReducesJitter() {
        val pts = (0..60).map { i -> StrokePoint(i * 5f, 100f + (if (i % 2 == 0) 4f else -4f) + 20f * sin(i / 10f)) }
        val out = InkSmoothing.smooth(pts)
        assertEquals(pts.size, out.size)
        assertEquals(pts.first(), out.first())
        assertEquals(pts.last(), out.last())
        fun jitter(p: List<StrokePoint>) = (1 until p.size - 1).sumOf { i -> abs(p[i].y - (p[i - 1].y + p[i + 1].y) / 2f).toDouble() }
        assertTrue(jitter(out) < jitter(pts) * 0.3)
    }
}
