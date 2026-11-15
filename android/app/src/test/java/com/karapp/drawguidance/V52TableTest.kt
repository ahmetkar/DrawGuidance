package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.ShapeStyle
import com.karapp.drawguidance.ui.drawing.RasterStrokes
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.StrokeRenderer
import com.karapp.drawguidance.ui.drawing.TableShape
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v52 not almada tablo: kurulum, satır/sütun ekleme-silme, yeniden kurulumda kutu korunur, görüntü. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V52TableTest {
    @After fun tearDown() { stopKoin(); RasterStrokes.clear() }

    private fun sample(): Map<String, String> {
        var p = TableShape.create(4, 3, header = true, headerCol = false, zebra = true, borderStyle = "all", preset = TableShape.presets[1])
        val cells = listOf(listOf("Element", "Sembol", "Kütle"), listOf("Hidrojen", "H", "1,008"), listOf("Karbon", "C", "12,011"), listOf("Oksijen", "O", "15,999"))
        cells.forEachIndexed { r, row -> row.forEachIndexed { c, t -> p = TableShape.setCell(p, r, c, t) } }
        p = TableShape.setLineProp(p, false, "a", 2, "r")
        return p
    }

    @Test fun buildsAndEdits() {
        val p = sample()
        val s = ShapeFactory.build(TableShape.KIND, Offset(40f, 40f), Offset(640f, 320f), ShapeStyle("#263238", 3f), null, "t", 0, params = p)
        assertNotNull(s)
        val box = ShapeFactory.box(s!!)!!
        assertEquals(40f, box.left, 1f); assertEquals(640f, box.right, 1f); assertEquals(320f, box.bottom, 1f)
        val labels = s.shape!!.subpaths.count { it.label != null }
        assertEquals(12, labels)
        val p2 = TableShape.insertRow(p, 1)
        assertEquals(5, TableShape.rows(p2)); assertEquals("", TableShape.cell(p2, 1, 0)); assertEquals("Hidrojen", TableShape.cell(p2, 2, 0))
        val p3 = TableShape.deleteCol(p2, 0)
        assertEquals(2, TableShape.cols(p3)); assertEquals("Sembol", TableShape.cell(p3, 0, 0)); assertEquals("r", TableShape.colProp(p3, "a", 1))
        val p4 = TableShape.resize(p3, 2, 2)
        assertTrue(p4.keys.none { it.startsWith("T.2.") })
        val rebuilt = ShapeFactory.rebuild(s, s.shape!!.style, null, p2)!!
        assertEquals(320f, ShapeFactory.box(rebuilt)!!.bottom, 1f)
    }

    @Test fun rendersPng() {
        val out = System.getProperty("tableOut") ?: return
        val styles = listOf("all" to 0, "rows" to 5, "header" to 2, "outer" to 3)
        styles.forEachIndexed { i, (b, pr) ->
            var p = sample() + ("border" to b)
            p = LinkedHashMap(p).also { TableShape.applyPreset(it, TableShape.presets[pr]) }
            if (i == 2) p = p + ("hc" to "1")
            val s = ShapeFactory.build(TableShape.KIND, Offset(20f, 20f), Offset(580f, 260f), ShapeStyle("#263238", 3f), null, "t$i", 0, params = p)!!
            val bmp = StrokeRenderer.renderViewportCache(listOf(s), 600, 280, 1f, Offset.Zero, Rect(Offset.Zero, Size(600f, 280f))).asAndroidBitmap()
            val full = android.graphics.Bitmap.createBitmap(600, 280, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(full).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
            java.io.File(out).mkdirs()
            java.io.FileOutputStream(java.io.File(out, "table_$b.png")).use { full.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
