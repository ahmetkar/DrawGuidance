package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.ui.drawing.BrushPainter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/** v21 yeni fırçaların ve akışkanlığın görsel örnekleri (DG_SHOTS verilince). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class V21BrushShotTest {
    private fun save(bmp: Bitmap, name: String) {
        val dir = System.getenv("DG_SHOTS") ?: return
        java.io.File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun newBrushesAndFlow() {
        if (System.getenv("DG_SHOTS") == null) return
        val types = listOf(
            BrushType.INK_TRAIL, BrushType.DRIP_TRAIL, BrushType.PAINT_TRAIL,
            BrushType.PATTERN_CARPET, BrushType.PATTERN_WALLPAPER, BrushType.PATTERN_WOODPAINT,
            BrushType.TEXTURE_SKIN, BrushType.TEXTURE_FUR, BrushType.TEXTURE_PLANT,
            BrushType.TEXTURE_WOOD, BrushType.TEXTURE_IRON, BrushType.TEXTURE_METAL,
        )
        val colors = listOf(0xFF1A237E.toInt(), 0xFFC62828.toInt(), 0xFF2E7D32.toInt(), 0xFFB71C1C.toInt(), 0xFF5E35B1.toInt(), 0xFF00897B.toInt(),
            0xFFE0AC8C.toInt(), 0xFF8D6E63.toInt(), 0xFF43A047.toInt(), 0xFFA1887F.toInt(), 0xFF546E7A.toInt(), 0xFF90A4AE.toInt())
        val rowH = 150; val w = 1000
        val bmp = Bitmap.createBitmap(w, rowH * types.size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFFFFF.toInt())
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = 0xFF333333.toInt() }
        types.forEachIndexed { r, t ->
            val n = 70
            val xs = FloatArray(n) { 190f + 780f * it / (n - 1) }
            val ys = FloatArray(n) { r * rowH + 65f + 30f * sin(it / 8f) }
            val size = if (t.name.startsWith("PATTERN_CARPET") || t.name.startsWith("PATTERN_WALL") || t.name.startsWith("PATTERN_WOOD") || t.name.startsWith("TEXTURE")) 44f else 22f
            BrushPainter.paint(c, xs, ys, n, colors[r], t, size, 1f, r + 3, 1f, 1f)
            c.drawText(t.name, 8f, r * rowH + 75f, label)
        }
        save(bmp, "v21-brushes")

        // Akışkanlık: 1, 0.6, 0.3 (kalem, marker, yumuşak fırça, tarama)
        val flowTypes = listOf(BrushType.PENCIL, BrushType.MARKER, BrushType.INK_PEN, BrushType.SOFT_ROUND, BrushType.PATTERN_HATCH, BrushType.TEXTURE_WOOD)
        val flows = listOf(1f, 0.6f, 0.3f)
        val fb = Bitmap.createBitmap(1000, 120 * flowTypes.size, Bitmap.Config.ARGB_8888)
        val fc = Canvas(fb)
        fc.drawColor(0xFFFFFFFF.toInt())
        flowTypes.forEachIndexed { r, t ->
            flows.forEachIndexed { k, f ->
                val n = 30
                val x0 = 180f + k * 270f
                val xs = FloatArray(n) { x0 + 240f * it / (n - 1) }
                val ys = FloatArray(n) { r * 120f + 60f + 18f * sin(it / 5f) }
                BrushPainter.paint(fc, xs, ys, n, 0xFF1A237E.toInt(), t, if (t == BrushType.PENCIL) 10f else 24f, 1f, 5, f, 1f)
            }
            fc.drawText(t.name, 8f, r * 120f + 65f, label)
        }
        save(fb, "v21-flow")
    }
}

/** v21 gölgeli 3B cisimler (DG_SHOTS verilince). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class V21SolidShotTest {
    @Test
    fun solids() {
        val dir = System.getenv("DG_SHOTS") ?: return
        val kinds = com.karapp.drawguidance.ui.drawing.SolidShapes
        val bmp = Bitmap.createBitmap(1000, 460, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF7F4EC.toInt())
        kinds.forEachIndexed { i, k ->
            val col = i % 5; val row = i / 5
            val x = 30f + col * 195f; val y = 30f + row * 215f
            val fill = listOf("#E53935", "#1E88E5", null, "#FDD835", "#43A047")[i % 5]
            val style = com.karapp.drawguidance.data.model.ShapeStyle(strokeColorHex = "#263238", strokeWidth = 3f, fillColorHex = fill)
            val s = com.karapp.drawguidance.ui.drawing.ShapeFactory.build(k.name, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + 160f, y + 180f), style, id = "s$i", order = i)!!
            com.karapp.drawguidance.ui.drawing.ShapeRenderer.draw(c, s)
        }
        java.io.File(dir, "v21-solids.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** v21 3B çizim modu: düzlemlere yatırılmış daire/kare ve derinlik (DG_SHOTS verilince). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class V21Draw3dShotTest {
    @Test
    fun planesAndDepth() {
        val dir = System.getenv("DG_SHOTS") ?: return
        val bmp = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFFFFF.toInt())
        val planes = listOf(com.karapp.drawguidance.data.model.Plane3D.FRONT, com.karapp.drawguidance.data.model.Plane3D.SIDE, com.karapp.drawguidance.data.model.Plane3D.TOP)
        val types = listOf(BrushType.PENCIL, BrushType.MARKER, BrushType.INK_TRAIL, BrushType.TEXTURE_WOOD)
        val colors = listOf(0xFF1A237E.toInt(), 0xFFC62828.toInt(), 0xFF2E7D32.toInt(), 0xFF8D6E63.toInt())
        planes.forEachIndexed { pi, plane ->
            types.forEachIndexed { ti, t ->
                val cx = 130f + pi * 320f; val cy = 110f + ti * 150f
                val anchor = androidx.compose.ui.geometry.Offset(cx + 55f, cy)
                // Kare (açık uçlu çizgi) ve daire
                val sq = listOf(0f to 0f, 110f to 0f, 110f to 110f, 0f to 110f, 0f to 0f).flatMap { (x, y) ->
                    (0 until 12).map { k -> x to y } }
                val n = 60
                val xs = FloatArray(n); val ys = FloatArray(n)
                for (i in 0 until n) {
                    val a = i / (n - 1f) * 2f * Math.PI.toFloat()
                    val p = com.karapp.drawguidance.ui.drawing.Iso3D.project(plane, anchor, anchor.x + 55f * kotlin.math.cos(a) - 55f, anchor.y + 45f * kotlin.math.sin(a))
                    xs[i] = p.x; ys[i] = p.y
                }
                val params = com.karapp.drawguidance.data.model.BrushParams(depth3d = com.karapp.drawguidance.data.model.Depth3D(plane, 0.6f))
                BrushPainter.paint(c, xs, ys, n, colors[ti], t, if (t == BrushType.TEXTURE_WOOD) 16f else 8f, 1f, 3, 1f, 1f, params = params)
            }
        }
        java.io.File(dir, "v21-draw3d.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
