package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.TubeMaterial
import com.karapp.drawguidance.data.model.TubeParams
import com.karapp.drawguidance.ui.drawing.BrushPainter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/** v20 3B fırça görsel örnekleri (DG_SHOTS verilince): malzemeler, ışık yönleri, gölge. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class V20TubeShotTest {
    @Test
    fun tubeSheet() {
        val dir = System.getenv("DG_SHOTS") ?: return
        val colors = listOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt())
        val rows = listOf(
            TubeParams(material = TubeMaterial.MATTE),
            TubeParams(material = TubeMaterial.PLASTIC),
            TubeParams(material = TubeMaterial.METAL),
            TubeParams(material = TubeMaterial.GLASS),
            TubeParams(material = TubeMaterial.PLASTIC, lightAngle = 45f, relief = 1f, gloss = 1f, shadow = 0.8f, shadowSoftness = 0.2f),
            TubeParams(material = TubeMaterial.PLASTIC, relief = 0.3f, gloss = 0.2f, shadow = 0f),
        )
        val w = 1100; val rowH = 170
        val bmp = Bitmap.createBitmap(w, rowH * rows.size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF7F4EC.toInt())
        rows.forEachIndexed { r, tube ->
            val n = 80
            val xs = FloatArray(n) { 40f + 1000f * it / (n - 1) }
            val ys = FloatArray(n) { r * rowH + 85f + 45f * sin(it / 9f) }
            BrushPainter.paint(c, xs, ys, n, colors[r % colors.size], BrushType.BRUSH_3D, 40f, 1f, r, 1f, 1f, params = BrushParams(tube = tube))
        }
        // Kendi üstünden geçen tüp (sonraki geçiş üstte)
        java.io.File(dir, "v20-3d-materials.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val loop = Bitmap.createBitmap(600, 420, Bitmap.Config.ARGB_8888)
        val lc = Canvas(loop)
        lc.drawColor(0xFFFFFFFF.toInt())
        val n = 120
        val xs = FloatArray(n) { 300f + 190f * kotlin.math.cos(it / 18f) + it * 1.5f - 90f }
        val ys = FloatArray(n) { 210f + 150f * sin(it / 18f) }
        BrushPainter.paint(lc, xs, ys, n, 0xFF8E24AA.toInt(), BrushType.BRUSH_3D, 46f, 1f, 7, 1f, 1f)
        java.io.File(dir, "v20-3d-loop.png").outputStream().use { loop.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val big = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val bc = Canvas(big)
        bc.drawColor(0xFFFFFFFF.toInt())
        val bx = FloatArray(20) { 60f + it * 15f }
        val by = FloatArray(20) { 100f + it * 1.5f }
        BrushPainter.paint(bc, bx, by, 20, 0xFF1E88E5.toInt(), BrushType.BRUSH_3D, 70f, 1f, 7, 1f, 1f)
        java.io.File(dir, "v20-3d-big.png").outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
