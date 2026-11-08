package com.karapp.drawguidance

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.fill.RegionClip
import com.karapp.drawguidance.content.fill.RegionFill
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.ShapeFactory
import com.karapp.drawguidance.ui.drawing.VectorEdit
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLevels
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeTool
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.KidsChallengeController
import com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick
import com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasTools
import com.karapp.drawguidance.ui.screens.canvasedit.components.EyedropperTool
import com.karapp.drawguidance.ui.screens.canvasedit.components.KidsBrushSizes
import com.karapp.drawguidance.ui.screens.canvasedit.components.KidsContrast
import com.karapp.drawguidance.ui.screens.canvasedit.components.KidsIcons
import com.karapp.drawguidance.ui.screens.canvasedit.components.KidsLabels
import com.karapp.drawguidance.ui.screens.canvasedit.components.KidsMenu
import com.karapp.drawguidance.ui.screens.canvasedit.components.KidsToolColors
import com.karapp.drawguidance.ui.screens.canvasedit.components.KidsToolSizes
import com.karapp.drawguidance.ui.screens.canvasedit.components.PaintBrushTool
import com.karapp.drawguidance.ui.screens.canvasedit.components.PaintTool
import com.karapp.drawguidance.ui.screens.canvasedit.components.SimpleTool
import com.karapp.drawguidance.ui.screens.canvasedit.components.ToolDescriptions
import com.karapp.drawguidance.ui.screens.canvasedit.editor.InkFill
import com.karapp.drawguidance.ui.screens.canvasedit.editor.KidsBrush
import com.karapp.drawguidance.ui.theme.ThemeMode
import com.karapp.drawguidance.ui.theme.ThemePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v45 boya fırçasının alan kırpması (saf hesap): fırça izi, başladığı alanın içinde kalır; alanın dışına, çizginin
 * üstüne ve alanın içindeki adacığa (delik) boya gitmez; açık şekilde alan yoktur.
 */
class V45RegionClipTest {
    /** 100..300 kare (kalınlık 6). */
    private val square = listOf(RegionFill.Line(listOf(P(100f, 100f), P(300f, 100f), P(300f, 300f), P(100f, 300f)), closed = true, width = 6f))

    private fun inAny(polys: List<List<P>>, x: Float, y: Float) = polys.any { RegionFill.contains(it, P(x, y)) }

    @Test
    fun strokeIsClippedToRegionWhereItStarted() {
        // Ortadan başlayıp sağ kenarı aşarak dışarı çıkan çizgi
        val stroke = listOf(P(150f, 200f), P(250f, 200f), P(350f, 200f), P(420f, 200f))
        val polys = RegionClip.clip(square, P(150f, 200f), stroke, radius = 10f)!!
        assertEquals(1, polys.size)
        // İçeride, fırçanın geçtiği yerler boyalı
        for (x in listOf(145f, 160f, 200f, 250f, 290f, 294f)) assertTrue("x=$x boyalı olmalı", inAny(polys, x, 200f))
        assertTrue(inAny(polys, 220f, 192f) && inAny(polys, 220f, 208f))
        // Fırçanın yarıçapı dışında kalan iç yer boyasız
        for (y in listOf(180f, 220f, 150f, 260f)) assertFalse("y=$y boyasız olmalı", inAny(polys, 220f, y))
        // Çizginin ortası ve dışı boyasız (taşma yok)
        for (x in listOf(300f, 303f, 310f, 350f, 400f)) assertFalse("x=$x dışarıda", inAny(polys, x, 200f))
        // Boya çizgiyi örtmez: sağ kenarı çizginin iç kenarında (297) en çok bir hücre içeride
        val maxX = polys.flatten().maxOf { it.x }
        assertTrue("sağ kenar $maxX", maxX in 296f..298.5f)
        // Fırçanın başı yuvarlak: başlangıcın 10 birim solu boyalı, 12 birim solu değil
        assertTrue(inAny(polys, 141.5f, 200f))
        assertFalse(inAny(polys, 137f, 200f))
    }

    @Test
    fun leavingAndReenteringGivesSeparatePieces() {
        // Kareden çıkıp karenin dışında dolaşıp geri gelen çizgi: iki ayrı boyalı parça
        val stroke = listOf(P(200f, 140f), P(200f, 60f), P(250f, 60f), P(250f, 140f))
        val polys = RegionClip.clip(square, P(200f, 140f), stroke, radius = 8f)!!
        assertEquals(2, polys.size)
        assertTrue(inAny(polys, 200f, 130f) && inAny(polys, 250f, 130f))
        assertFalse(inAny(polys, 225f, 60f))
        assertFalse(inAny(polys, 200f, 80f))
        // Parçaların arası (kare içinde ama fırçanın dışında) boyasız
        assertFalse(inAny(polys, 225f, 130f))
    }

    @Test
    fun islandInsideRegionStaysUnpainted() {
        // Karenin içinde küçük daire (göz gibi): fırça üstünden geçse de adacık boyanmaz
        val eye = RegionFill.Line((0 until 36).map { i -> val a = i / 36.0 * 2 * PI; P(200f + 20f * cos(a).toFloat(), 200f + 20f * sin(a).toFloat()) }, closed = true, width = 3f)
        val walls = square + eye
        val stroke = listOf(P(120f, 200f), P(280f, 200f))
        val polys = RegionClip.clip(walls, P(120f, 200f), stroke, radius = 30f)!!
        assertTrue(inAny(polys, 150f, 200f) && inAny(polys, 260f, 200f))
        // Adacığın içi (ve çizgisi) boyasız; adacığın çevresi boyalı
        assertFalse(inAny(polys, 200f, 200f))
        assertFalse(inAny(polys, 195f, 205f))
        assertFalse(inAny(polys, 220f, 200f))
        assertTrue(inAny(polys, 200f, 175f) && inAny(polys, 200f, 226f))
        // Boya parçası tek: deliği köprüyle bağlı
        assertEquals(1, polys.size)
    }

    @Test
    fun strokeInsideAnIslandPaintsOnlyTheIsland() {
        val eye = RegionFill.Line((0 until 36).map { i -> val a = i / 36.0 * 2 * PI; P(200f + 40f * cos(a).toFloat(), 200f + 40f * sin(a).toFloat()) }, closed = true, width = 3f)
        val polys = RegionClip.clip(square + eye, P(200f, 200f), listOf(P(200f, 200f), P(290f, 200f)), radius = 10f)!!
        assertTrue(inAny(polys, 210f, 200f) && inAny(polys, 230f, 200f))
        assertFalse(inAny(polys, 260f, 200f))
    }

    @Test
    fun openShapeAndStrokeOutsideRegion() {
        // Açık şekil (U): alan yok → null (çağıran serbest boyar)
        val open = listOf(RegionFill.Line(listOf(P(100f, 100f), P(100f, 300f), P(300f, 300f), P(300f, 100f)), closed = false, width = 6f))
        assertNull(RegionClip.clip(open, P(200f, 200f), listOf(P(200f, 200f), P(220f, 200f)), 10f))
        // Alan var ama iz alana hiç değmiyor (kırpma maskesi bulunmuş alana göre)
        val mask = RegionFill.fillMask(square, P(200f, 200f))!!
        assertTrue(RegionClip.clip(mask, listOf(P(400f, 400f), P(450f, 450f)), 10f).isEmpty())
        assertTrue(RegionClip.clip(mask, emptyList(), 10f).isEmpty())
        // Tek nokta: daire biçimli leke
        val dot = RegionClip.clip(mask, listOf(P(200f, 200f)), 10f)
        assertEquals(1, dot.size)
        assertTrue(inAny(dot, 207f, 200f) && !inAny(dot, 213f, 200f))
    }

    @Test
    fun maskMatchesBucketRegion() {
        // Fırçanın maskesi kovanın alanıyla aynı: alanı kaplayan kocaman fırça kovanın alanıyla aynı yeri boyar
        val region = RegionFill.region(square, P(200f, 200f))!!
        val all = RegionClip.clip(square, P(200f, 200f), listOf(P(200f, 200f)), radius = 1000f)!!
        assertEquals(1, all.size)
        assertEquals(RegionFill.areaOf(region), RegionFill.areaOf(all.single()), RegionFill.areaOf(region) * 0.01f)
        for (p in listOf(P(105f, 105f), P(295f, 295f), P(200f, 296f))) assertEquals(RegionFill.contains(region, p), inAny(all, p.x, p.y))
    }
}

/**
 * v45 çocuk menüsünün renkleri, karşıtlığı, boyutları ve sırası (saf hesap).
 */
class V45KidsMenuLogicTest {
    @Test
    fun wcagLuminanceAndContrast() {
        assertEquals(1.0, KidsContrast.luminance(Color.White), 1e-6)
        assertEquals(0.0, KidsContrast.luminance(Color.Black), 1e-6)
        assertEquals(21.0, KidsContrast.ratio(Color.White, Color.Black), 1e-6)
        assertEquals(1.0, KidsContrast.ratio(Color.Red, Color.Red), 1e-6)
        // Bilinen değerler: #777777 beyaza karşı 4,48; saf kırmızı 0,2126 parlaklık, beyaza karşı 4,0
        assertEquals(4.48, KidsContrast.ratio(Color(0xFF777777), Color.White), 0.01)
        assertEquals(0.2126, KidsContrast.luminance(Color(0xFFFF0000)), 1e-4)
        assertEquals(3.998, KidsContrast.ratio(Color(0xFFFF0000), Color.White), 0.01)
        // Orta gri (#808080) parlaklığı 0,2159
        assertEquals(0.2159, KidsContrast.luminance(Color(0xFF808080)), 1e-3)
        // Simetrik
        assertEquals(KidsContrast.ratio(Color(0xFF1E88E5), Color.White), KidsContrast.ratio(Color.White, Color(0xFF1E88E5)), 1e-9)
    }

    @Test
    fun inkFollowsBackground() {
        // Açık zeminde koyu yazı, koyu zeminde beyaz yazı
        assertEquals(KidsContrast.DarkInk, KidsContrast.ink(Color(0xFFFFE066)))
        assertEquals(KidsContrast.DarkInk, KidsContrast.ink(Color.White))
        assertEquals(KidsContrast.LightInk, KidsContrast.ink(Color(0xFF1E2233)))
        assertEquals(KidsContrast.LightInk, KidsContrast.ink(Color(0xFF5E35B1)))
        // Sınırdaki zeminde ikisinden karşıtlığı yüksek olanı
        for (gray in 0..255 step 5) {
            val bg = Color(gray, gray, gray)
            val ink = KidsContrast.ink(bg)
            val other = if (ink == KidsContrast.DarkInk) KidsContrast.LightInk else KidsContrast.DarkInk
            assertTrue("gri $gray", KidsContrast.ratio(ink, bg) >= KidsContrast.ratio(other, bg))
        }
        // Yetmeyen zemin gereken karşıtlığa getirilir
        val (bg, ink) = KidsContrast.readable(Color(0xFF3B82F6), 7f)
        assertTrue(KidsContrast.ratio(ink, bg) >= 7.0)
    }

    @Test
    fun everyToolColorIsReadableInEveryTheme() {
        val ids = KidsToolColors.palette.keys + "unknown_tool"
        for (dark in listOf(false, true)) for (hc in listOf(false, true)) for (id in ids) {
            val c = KidsToolColors.colors(id, dark, hc)
            val ratio = KidsContrast.ratio(c.content, c.background)
            val min = if (hc) 7.0 else 4.5
            assertTrue("$id dark=$dark hc=$hc: $ratio", ratio >= min)
            // Yazı rengi koyu mürekkep ya da beyaz
            assertTrue(c.content == KidsContrast.DarkInk || c.content == KidsContrast.LightInk)
            // Yüksek karşıtlıkta kenar opak yazı rengi
            if (hc) assertEquals(c.content, c.border)
        }
        // Açık temada araçlar renkli (gri değil) ve koyu temada koyu zeminli
        for (id in KidsToolColors.palette.keys) {
            val light = KidsToolColors.colors(id, dark = false, highContrast = false).background
            assertTrue("$id renkli", maxOf(light.red, light.green, light.blue) - minOf(light.red, light.green, light.blue) > 0.1f)
            val dark = KidsToolColors.colors(id, dark = true, highContrast = false).background
            assertTrue("$id koyu", KidsContrast.luminance(dark) < KidsContrast.luminance(light))
            assertEquals(KidsContrast.LightInk, KidsToolColors.colors(id, dark = true, highContrast = false).content)
        }
        // Boyama araçlarının renkleri birbirinden ayrı (çocuk renginden tanır)
        val painting = listOf("brush", "eraser", "paint_brush", "paint").map { KidsToolColors.palette.getValue(it) }
        assertEquals(painting.size, painting.toSet().size)
    }

    @Test
    fun tileSizesAreBiggerAndTouchSafe() {
        assertEquals(64, KidsToolSizes.tile(411, 891).value.toInt())
        assertEquals(56, KidsToolSizes.tile(891, 411).value.toInt())
        assertEquals(72, KidsToolSizes.tile(600, 960).value.toInt())
        assertEquals(72, KidsToolSizes.tile(960, 600).value.toInt())
        assertEquals(72, KidsToolSizes.tile(800, 1280).value.toInt())
        assertEquals(72, KidsToolSizes.tile(1280, 800).value.toInt())
        for ((w, h) in listOf(320 to 568, 411 to 891, 891 to 411, 600 to 960, 1280 to 800, 568 to 320)) {
            val tile = KidsToolSizes.tile(w, h)
            assertTrue("$w×$h", tile >= KidsToolSizes.MIN_TOUCH)
            // Basit menünün 36 dp düğmesinden büyük
            assertTrue(tile.value > 36f)
        }
    }

    @Test
    fun brushSizeChoices() {
        assertEquals(listOf(10f, 24f, 48f), KidsBrushSizes.sizes)
        assertEquals(10f, KidsBrushSizes.nearest(3f))
        assertEquals(24f, KidsBrushSizes.nearest(20f))
        assertEquals(48f, KidsBrushSizes.nearest(40f))
        assertEquals(48f, KidsBrushSizes.nearest(300f))
    }

    private val labels = KidsLabels(
        "Geri al", "İleri al", "Kalem", "Silgi", "Boya", "Kova", "Parçalar", "Renkler", "Şekiller", "Görev", "Zemin",
        "Resim", "Yazı", "3B", "Seç", "Renk al", "Cetvel", "Kalıp", "Büyüt",
    )

    private fun raw(paintSelected: Boolean): List<SimpleTool> = listOf("brush", "eraser", "paint", "elements", "brush_settings", "shape", "challenge", "background", "image", "text", "model3d", "select", "eyedropper", "ruler", "template", "expand")
        .map { id -> SimpleTool(id, Icons.Filled.Category, "eski $id", id == "paint" && paintSelected, {}) }

    @Test
    fun decorateInsertsPaintBrushBeforeBucketWithKidsNames() {
        var brushPicked = 0
        val out = KidsMenu.decorate(raw(paintSelected = false), labels, brushOn = false, paintMode = false) { brushPicked++ }
        val ids = out.map { it.id }
        // Kalem, Silgi, Boya, Kova; v44 sırası: Elemanlar ve Fırça ayarları kovanın hemen ardında
        assertEquals(listOf("brush", "eraser", "paint_brush", "paint", "elements", "brush_settings", "shape"), ids.take(7))
        assertTrue("eyedropper" in ids && "rect_select" !in ids && "free_select" !in ids)
        assertEquals(17, out.size)
        assertEquals(
            listOf("Kalem", "Silgi", "Boya", "Kova", "Parçalar", "Renkler", "Şekiller", "Görev", "Zemin", "Resim", "Yazı", "3B", "Seç", "Renk al", "Cetvel", "Kalıp", "Büyüt"),
            out.map { it.label },
        )
        // Simgeler: boya fırçası kendine özgü, kova kova simgesi
        assertTrue(out.first { it.id == "paint_brush" }.icon === KidsIcons.PaintBrush)
        assertEquals(Icons.Filled.FormatColorFill, out.first { it.id == "paint" }.icon)
        assertEquals("KidsPaintBrush", KidsIcons.PaintBrush.name)
        assertEquals(out.size, out.map { it.label }.toSet().size)
        out.first { it.id == "paint_brush" }.onClick()
        assertEquals(1, brushPicked)
    }

    @Test
    fun onlyOneOfBucketAndBrushSelected() {
        val brush = KidsMenu.decorate(raw(paintSelected = true), labels, brushOn = true, paintMode = true) {}
        assertTrue(brush.first { it.id == "paint_brush" }.selected)
        assertFalse(brush.first { it.id == "paint" }.selected)
        val bucket = KidsMenu.decorate(raw(paintSelected = true), labels, brushOn = false, paintMode = true) {}
        assertFalse(bucket.first { it.id == "paint_brush" }.selected)
        assertTrue(bucket.first { it.id == "paint" }.selected)
        val drawing = KidsMenu.decorate(raw(paintSelected = false), labels, brushOn = false, paintMode = false) {}
        assertTrue(drawing.none { it.selected })
    }

    @Test
    fun paintBrushToolOnlyInPaintModeWithBrush() {
        assertTrue(CanvasTools.create(EditMode.PAINT, CanvasPick.NONE, true) is PaintBrushTool)
        assertTrue(CanvasTools.create(EditMode.PAINT, CanvasPick.NONE, false) is PaintTool)
        assertFalse(CanvasTools.create(EditMode.DRAW, CanvasPick.NONE, true) is PaintBrushTool)
        // Damlalık boyamada da çalışır (fırça seçiliyken de)
        assertTrue(CanvasTools.create(EditMode.PAINT, CanvasPick.EYEDROPPER, true) is EyedropperTool)
        assertTrue(CanvasTools.create(EditMode.PAINT, CanvasPick.EYEDROPPER, false) is EyedropperTool)
    }

    @Test
    fun challengeTipsAddPaintBrushAfterBucket() {
        val house = ChallengeLevels.all[2]
        assertEquals(listOf(ChallengeTool.PAINT, ChallengeTool.UNDO), house.tools)
        assertEquals(listOf(ChallengeTool.PAINT, ChallengeTool.PAINT_BRUSH, ChallengeTool.UNDO), ChallengeTool.tips(house))
        // Çizme görevlerinde fırça ipucu yok
        val sun = ChallengeLevels.all[0]
        assertEquals(sun.tools, ChallengeTool.tips(sun))
        // Boyama içeren her seviyede kovanın hemen ardında
        ChallengeLevels.all.filter { ChallengeTool.PAINT in it.tools }.forEach { l ->
            val tips = ChallengeTool.tips(l)
            assertEquals(ChallengeTool.PAINT_BRUSH, tips[tips.indexOf(ChallengeTool.PAINT) + 1])
            assertEquals(l.tools.size + 1, tips.size)
        }
    }

    @Test
    fun paintBrushIconIsUniqueInTheApp() {
        // Simge yalnız çocuk menüsünde kullanılır
        val src = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val uses = src.walkTopDown().filter { it.extension == "kt" }.filter { f -> f.readText().contains("KidsIcons.PaintBrush") }.map { it.name }.toList()
        assertEquals(listOf("KidsToolbar.kt"), uses.distinct())
        // Uygulamanın başka hiçbir simgesi bu adla çizilmez
        val named = src.walkTopDown().filter { it.extension == "kt" }.count { it.readText().contains("\"KidsPaintBrush\"") }
        assertEquals(1, named)
    }
}

/** Ortak: kâğıt resmi, renk karşılaştırma, dikdörtgen şekil. */
abstract class V45KidsBase : V38ChallengeBase() {
    protected val red = "#E53935"
    protected val blue = "#1E88E5"
    protected val green = "#2E7D32"

    protected fun render(): Bitmap = ImageExporter(ApplicationProvider.getApplicationContext()).render(vm().canvas.value!!)

    protected fun near(pixel: Int, hex: String, tol: Int = 14): Boolean {
        val c = android.graphics.Color.parseColor(hex)
        return abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(c)) <= tol &&
            abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(c)) <= tol &&
            abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(c)) <= tol
    }

    protected fun Bitmap.at(x: Float, y: Float): Int = getPixel(x.toInt(), y.toInt())

    /** Siyah kenarlı dikdörtgen şekil (300..700) ve kenarlarının kutusu. */
    protected fun addRect(): Pair<String, Rect> {
        compose.runOnIdle {
            vm().updateBrushColor("#212121")
            vm().selectShapeId("RECTANGLE")
            vm().addShape("g-rect", Offset(300f, 300f), Offset(700f, 700f))
            vm().chooseMode(EditMode.DRAW)
        }
        waitFor { strokes().any { it.shape?.kind == "RECTANGLE" } }
        val s = strokes().first { it.shape?.kind == "RECTANGLE" }
        val pts = ShapeFactory.subPaths(s).flatMap { it.points }
        return s.id to Rect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    protected fun paintParts(id: String) = ShapeFactory.subPaths(shape(id)).filter { it.tag == KidsBrush.TAG }

    protected fun colorAt(id: String, at: Offset): String? = KidsChallengeController.colorAt(shape(id), ShapeFactory.subPaths(shape(id)), at)

    /** Boya fırçasıyla (ViewModel üzerinden) [points] boyunca bir darbe. */
    protected fun brushStroke(a: Offset, b: Offset? = null, c: Offset? = null, d: Offset? = null) {
        val points = listOfNotNull(a, b, c, d)
        compose.runOnIdle {
            assertTrue(vm().brushDown(points.first()))
            points.drop(1).forEach { vm().brushMove(it) }
            vm().brushUp()
        }
        compose.waitForIdle()
    }

    protected fun pickBrushTool() {
        compose.onNodeWithTag("simple_tool_paint_brush").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.PAINT && vm().paintBrush.value }
    }

    /** Ekranın resminde düğmenin yatay ortasında, üstünden [dyDp] aşağıdaki piksel (pencere koordinatı). */
    protected fun tilePixel(tag: String, dyDp: Float): Int {
        repeat(3) { Thread.sleep(50); compose.waitForIdle(); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }
        val b = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { root.draw(android.graphics.Canvas(bitmap)) }
        return bitmap.getPixel(b.center.x.toInt(), (b.top + dyDp * compose.density.density).toInt())
    }

    protected fun selectedOf(tag: String): Boolean =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true
}

/**
 * v45 boya fırçası: alanın içinde kalır (piksellerle), boş kâğıtta serbest boyar, darbe başına tek geri alma, kalemle
 * çizilmiş kapalı şekil, kova ve bütün şekli boyama ile birlikte, damlalık, fırça boyu ve gerçek parmak hareketi.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45KidsBrushTest : V45KidsBase() {
    @Test
    fun brushPaintsOnlyInsideTheRegionWhereItStarted() {
        openKids()
        val (id, box) = addRect()
        val sw = shape(id).shape!!.style.strokeWidth
        val cy = box.center.y
        compose.runOnIdle { vm().choosePaintBrush(); vm().setRegionColor(red); vm().updateBrushSize(24f) }
        val before = strokes().size
        // Ortadan başlayıp sağ kenarı aşarak kâğıdın sağına kadar
        brushStroke(Offset(box.center.x, cy), Offset(box.right - 40f, cy), Offset(box.right + 60f, cy), Offset(box.right + 200f, cy))
        waitFor { paintParts(id).isNotEmpty() }
        // Yeni eleman yok: boya şeklin içinde
        assertEquals(before, strokes().size)
        assertEquals(red, paintParts(id).single().fillColorHex)
        assertEquals(0f, paintParts(id).single().widthScale)
        val bmp = render()
        // İçeride fırçanın geçtiği yer kırmızı (kenarın iç tarafına kadar)
        for (x in listOf(box.center.x + 10f, box.center.x + 100f, box.right - sw / 2f - 3f)) assertTrue("x=$x kırmızı", near(bmp.at(x, cy), red))
        // Kenarın dışı (kâğıt) kırmızı değil: boya taşmadı
        for (x in listOf(box.right + sw / 2f + 3f, box.right + 60f, box.right + 150f)) {
            assertFalse("x=$x taşmış", near(bmp.at(x, cy), red))
            assertTrue("x=$x beyaz", near(bmp.at(x, cy), "#FFFFFF", 8))
        }
        // Kenar çizgisi örtülmedi
        assertTrue(near(bmp.at(box.right, cy), "#212121", 40))
        // Fırçanın dışında kalan iç yer boyasız
        assertFalse(near(bmp.at(box.center.x + 50f, cy + 40f), red))
        assertFalse(near(bmp.at(box.center.x + 50f, cy - 40f), red))
        // Görevin okuduğu renk de aynı
        assertEquals(red, colorAt(id, Offset(box.center.x + 50f, cy)))
        assertNull(colorAt(id, Offset(box.center.x + 50f, cy + 60f)))
        // Tek geri alma adımı
        compose.runOnIdle { vm().undo() }
        waitFor { paintParts(id).isEmpty() }
        assertFalse(near(render().at(box.center.x + 10f, cy), red))
        assertEquals(before, strokes().size)
    }

    @Test
    fun freePaintOnEmptyCanvasAndOutsideShapes() {
        openKids()
        compose.runOnIdle { vm().choosePaintBrush(); vm().setRegionColor(blue); vm().updateBrushSize(24f) }
        assertTrue(strokes().isEmpty())
        brushStroke(Offset(200f, 200f), Offset(300f, 220f), Offset(400f, 200f))
        waitFor { strokes().size == 1 }
        val s = strokes().single()
        assertEquals(KidsBrush.FREE_BRUSH, s.brushType)
        assertEquals(blue, s.colorHex)
        assertEquals(24f, s.sizePx)
        assertEquals(1f, s.opacity)
        assertEquals(3, s.points.size)
        val bmp = render()
        assertTrue(near(bmp.at(300f, 220f), blue, 30))
        assertTrue(near(bmp.at(300f, 300f), "#FFFFFF", 8))
        // Kısa dokunuş: tek leke
        compose.runOnIdle { assertTrue(vm().brushDown(Offset(800f, 800f))); vm().brushUp() }
        waitFor { strokes().size == 2 }
        assertTrue(near(render().at(800f, 800f), blue, 30))
        // Şeklin dışında başlayan darbe serbest (alan yok)
        val (id, box) = addRect()
        compose.runOnIdle { vm().choosePaintBrush(); vm().setRegionColor(green) }
        val n = strokes().size
        brushStroke(Offset(box.left - 100f, box.top - 100f), Offset(box.left - 150f, box.top - 120f))
        waitFor { strokes().size == n + 1 }
        assertTrue(paintParts(id).isEmpty())
        // Her darbe tek adım
        compose.runOnIdle { vm().undo() }
        waitFor { strokes().size == n }
    }

    @Test
    fun oneUndoStepPerStrokeAndRedo() {
        openKids()
        val (id, box) = addRect()
        compose.runOnIdle { vm().choosePaintBrush(); vm().setRegionColor(red) }
        val c = box.center
        brushStroke(c, c + Offset(80f, 0f))
        brushStroke(c + Offset(0f, 80f), c + Offset(80f, 80f))
        compose.runOnIdle { vm().setRegionColor(blue) }
        brushStroke(c + Offset(0f, -80f), c + Offset(80f, -80f))
        waitFor { paintParts(id).size == 3 }
        assertEquals(listOf(red, red, blue), paintParts(id).map { it.fillColorHex })
        compose.runOnIdle { vm().undo() }
        waitFor { paintParts(id).size == 2 }
        compose.runOnIdle { vm().undo() }
        waitFor { paintParts(id).size == 1 }
        compose.runOnIdle { vm().redo() }
        waitFor { paintParts(id).size == 2 }
        compose.runOnIdle { vm().undo(); vm().undo() }
        waitFor { paintParts(id).isEmpty() }
        // Şeklin kendisi yerinde
        assertNotNull(strokes().firstOrNull { it.id == id })
    }

    @Test
    fun bucketAndWholePaintWorkWithBrushPaint() {
        openKids()
        val (id, box) = addRect()
        val c = box.center
        // Önce kova mavi, sonra fırça kırmızı: fırça üstte
        compose.runOnIdle { vm().chooseMode(EditMode.PAINT); vm().setRegionColor(blue); vm().paintAt(c) }
        waitFor { ShapeFactory.subPaths(shape(id)).any { it.tag == VectorEdit.REGION } }
        compose.runOnIdle { vm().choosePaintBrush(); vm().setRegionColor(red) }
        brushStroke(c, c + Offset(60f, 0f))
        waitFor { paintParts(id).isNotEmpty() }
        assertEquals(red, colorAt(id, c + Offset(30f, 0f)))
        assertEquals(blue, colorAt(id, c + Offset(0f, 100f)))
        // Kova yeniden (yeşil): boyanan alan fırçanın üstüne çıkar, alanın her yeri yeşil
        compose.runOnIdle { vm().chooseMode(EditMode.PAINT); assertFalse(vm().paintBrush.value); vm().setRegionColor(green); vm().paintAt(c + Offset(0f, 100f)) }
        waitFor { colorAt(id, c + Offset(30f, 0f)) == green }
        assertEquals(VectorEdit.REGION, ShapeFactory.subPaths(shape(id)).last().tag)
        assertTrue(near(render().at(c.x + 30f, c.y), green, 30))
        // Geri al: fırça boyası yeniden görünür
        compose.runOnIdle { vm().undo() }
        waitFor { colorAt(id, c + Offset(30f, 0f)) == red }
        // Fırçadan sonra 2 sn basılı tutma (bütün şekil): fırça boyası da aynı renge
        compose.runOnIdle { vm().setRegionColor("#FDD835"); assertTrue(vm().paintWhole(c)) }
        waitFor { colorAt(id, c + Offset(30f, 0f)) == "#FDD835" }
        assertTrue(paintParts(id).all { it.fillColorHex == "#FDD835" })
        // Fırça boyası alan sınırı sayılmaz: kova yine bütün alanı bulur
        compose.runOnIdle { vm().undo(); vm().chooseMode(EditMode.PAINT); vm().setRegionColor(blue); vm().paintAt(c + Offset(30f, 0f)) }
        waitFor { colorAt(id, c + Offset(30f, 0f)) == blue }
        assertEquals(blue, colorAt(id, c + Offset(-150f, -150f)))
    }

    @Test
    fun brushInsideClosedInkShape() {
        openKids()
        compose.runOnIdle {
            vm().updateBrushColor("#212121")
            vm().onStrokeFinished((0..40).map { i -> val a = i / 40.0 * 2 * PI; StrokePoint(500f + 150f * cos(a).toFloat(), 500f + 150f * sin(a).toFloat()) })
        }
        waitFor { strokes().size == 1 }
        val inkId = strokes().single().id
        compose.runOnIdle { vm().choosePaintBrush(); vm().setRegionColor(red); vm().updateBrushSize(24f) }
        brushStroke(Offset(500f, 500f), Offset(600f, 500f), Offset(760f, 500f), Offset(900f, 500f))
        waitFor { strokes().size == 2 }
        val all = strokes()
        // Boya kalem çizgisinin hemen altında, aynı grupta
        assertTrue(all[0].id.startsWith(KidsBrush.PREFIX))
        assertEquals(inkId, all[1].id)
        assertNotNull(all[1].groupId)
        assertEquals(all[1].groupId, all[0].groupId)
        val bmp = render()
        assertTrue(near(bmp.at(560f, 500f), red))
        assertTrue(near(bmp.at(620f, 500f), red))
        // Çemberin dışı boyasız
        assertTrue(near(bmp.at(700f, 500f), "#FFFFFF", 8))
        assertTrue(near(bmp.at(850f, 500f), "#FFFFFF", 8))
        assertFalse(near(bmp.at(560f, 560f), red))
        // Kova bu boyayı boyanacak şekil saymaz; kalemin içini yine kova dolgusuyla boyar
        compose.runOnIdle { vm().chooseMode(EditMode.PAINT); vm().setRegionColor(blue); vm().paintAt(Offset(500f, 420f)) }
        waitFor { strokes().any { it.id == InkFill.PREFIX + inkId } }
        assertTrue(near(render().at(500f, 420f), blue, 30))
        // Tek geri alma: önce kova, sonra fırça
        compose.runOnIdle { vm().undo() }
        waitFor { strokes().size == 2 }
        compose.runOnIdle { vm().undo() }
        waitFor { strokes().size == 1 && strokes().single().id == inkId }
    }

    @Test
    fun realFingerStrokeShowsClippedPreviewThenCommits() {
        openKids()
        val (id, box) = addRect()
        pickBrushTool()
        compose.onNodeWithContentDescription("Kırmızı").performClick()
        compose.waitForIdle()
        assertEquals(red, vm().regionColor.value)
        val map = mapper()
        val c = box.center
        compose.onNodeWithTag("drawing_canvas").performTouchInput {
            down(map(c))
            moveTo(map(c + Offset(60f, 0f)))
            moveTo(map(c + Offset(150f, 0f)))
            moveTo(map(c + Offset(300f, 0f)))
        }
        compose.waitForIdle()
        // Önizleme alanla kırpılı
        waitFor { exists("kids_brush_live") }
        assertEquals("clipped:$id", stateOf("kids_brush_live"))
        assertTrue(paintParts(id).isEmpty())
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up() }
        compose.waitForIdle()
        waitFor { paintParts(id).isNotEmpty() && !exists("kids_brush_live") }
        val bmp = render()
        assertTrue(near(bmp.at(c.x + 100f, c.y), red))
        assertFalse(near(bmp.at(box.right + 60f, c.y), red))
        // Boş kâğıtta önizleme serbest
        compose.onNodeWithTag("drawing_canvas").performTouchInput { down(map(Offset(100f, 100f))); moveTo(map(Offset(200f, 120f))) }
        compose.waitForIdle()
        waitFor { exists("kids_brush_live") }
        assertEquals("free", stateOf("kids_brush_live"))
        compose.onNodeWithTag("drawing_canvas").performTouchInput { up() }
        waitFor { strokes().any { it.brushType == KidsBrush.FREE_BRUSH } }
        shot("v45-kids-brush")
    }

    @Test
    fun toolSelectionSizesAndEyedropper() {
        openKids()
        val (_, box) = addRect()
        // Menüde Boya seçilince Kova seçili görünmez; Kova seçilince fırça kapanır
        pickBrushTool()
        assertTrue(selectedOf("simple_tool_paint_brush"))
        assertFalse(selectedOf("simple_tool_paint"))
        // Fırça boyu yalnız boya fırçasında: üç boy, seçilen çocuk fırçasının boyu olur
        waitFor { exists("kids_brush_size") && exists("kids_palette") }
        compose.onNodeWithTag("kids_brush_size_2").performClick()
        waitFor { vm().brushSettings.value.sizePx == 48f }
        assertTrue(selectedOf("kids_brush_size_2"))
        compose.onNodeWithTag("kids_brush_size_0").performClick()
        waitFor { vm().brushSettings.value.sizePx == 10f }
        // Fırçanın boyu darbeye geçer
        compose.runOnIdle { vm().setRegionColor(red) }
        brushStroke(Offset(100f, 900f), Offset(200f, 900f))
        waitFor { strokes().any { it.brushType == KidsBrush.FREE_BRUSH } }
        assertEquals(10f, strokes().last().sizePx)
        // Damlalık boyamada: alınan renk boyanın rengi olur, fırça seçili kalır
        compose.runOnIdle { vm().toggleCanvasPick(CanvasPick.EYEDROPPER) }
        compose.runOnIdle { assertNotNull(vm().eyedrop(Offset(150f, 900f))) }
        assertTrue(vm().paintBrush.value)
        assertEquals(CanvasPick.NONE, vm().canvasPick.value)
        val picked = vm().regionColor.value!!
        assertTrue(near(android.graphics.Color.parseColor(picked), red, 30))
        // Kova
        compose.onNodeWithTag("simple_tool_paint").performScrollTo().performClick()
        waitFor { !vm().paintBrush.value && vm().mode.value == EditMode.PAINT }
        assertTrue(selectedOf("simple_tool_paint"))
        assertFalse(selectedOf("simple_tool_paint_brush"))
        waitFor { !exists("kids_brush_size") }
        // Başka araç fırçayı kapatır
        pickBrushTool()
        compose.onNodeWithTag("simple_tool_brush").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.DRAW && !vm().paintBrush.value }
        // Fırçayla kova dokunuşu (bütün alan) değil darbe: kısa dokunuş küçük leke bırakır
        pickBrushTool()
        compose.runOnIdle { vm().setRegionColor(blue) }
        tap(mapper()(box.center))
        waitFor { colorAt(strokes().first { it.shape?.kind == "RECTANGLE" }.id, box.center) == blue }
        assertNull(colorAt(strokes().first { it.shape?.kind == "RECTANGLE" }.id, box.center + Offset(100f, 100f)))
    }
}

/**
 * v45 boya fırçası çizim görevinde: boyama seviyesi fırçayla da geçilir, maskot kovadan sonra fırçanın ipucunu verir,
 * görev sürerken öneri yok.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45KidsChallengeBrushTest : V45KidsBase() {
    @Test
    fun paintLevelPassesWithBrushAndMascotTipsBrush() {
        openKids(ai = true)
        kidsPrefs().recordChallenge(ChallengeLevels.all[0].id, 80)
        kidsPrefs().recordChallenge(ChallengeLevels.all[1].id, 80)
        compose.runOnIdle { assertTrue(ctl().open(2)) }
        waitFor { exists("challenge_card") && exists("challenge_mascot") }
        // İpuçları: kova, boya fırçası, geri al, Bitti
        compose.onNodeWithTag("challenge_mascot_tip").performClick()
        waitFor { stateOf("challenge_mascot") == "tip_paint" }
        compose.onNodeWithTag("challenge_mascot_tip").performClick()
        waitFor { stateOf("challenge_mascot") == "tip_paint_brush" }
        compose.onNodeWithTag("challenge_mascot_tip").performClick()
        waitFor { stateOf("challenge_mascot") == "tip_undo" }
        quiet()
        val house = strokes().single { it.shape?.kind == "lib.kid_house" }
        val spots = ctl().run.value!!.target.spots()
        pickBrushTool()
        val names = mapOf("#FDD835" to "Sarı", "#E53935" to "Kırmızı", "#8D6E63" to "Kahverengi")
        val map = mapper()
        spots.forEachIndexed { i, (at, color) ->
            quiet()
            compose.onNodeWithContentDescription(names.getValue(color)).performClick()
            compose.waitForIdle()
            // Noktanın çevresinde küçük bir karalama (parmakla)
            compose.onNodeWithTag("drawing_canvas").performTouchInput {
                // Noktanın üstünde başlar (darbe başladığı alanın içinde kalır)
                down(map(at))
                moveTo(map(at + Offset(-14f, 0f)))
                moveTo(map(at + Offset(14f, 0f)))
                moveTo(map(at + Offset(0f, 10f)))
                moveTo(map(at + Offset(-10f, -8f)))
                moveTo(map(at + Offset(10f, -8f)))
                up()
            }
            compose.waitForIdle()
            waitFor { ctl().live.value?.marks?.getOrNull(i)?.done == true }
        }
        // Boya şeklin içinde (yeni eleman yok); şekil aynı
        assertEquals(house.id, strokes().single { it.shape?.kind == "lib.kid_house" }.id)
        assertEquals(1, strokes().size)
        assertEquals(spots.size, paintParts(house.id).size)
        waitFor { ctl().live.value?.score == 100 }
        // Görevde öneri yok
        advanceMain(900)
        assertNull(vm().lineSuggestion.value)
        assertNull(vm().shapeSuggestion.value)
        assertFalse(exists("kids_mascot_suggest"))
        quiet()
        shot("v45-challenge-brush")
        finishLevel()
        val r = ctl().result.value!!
        assertEquals(100, r.score)
        assertEquals(3, r.stars)
        assertTrue(r.passed)
    }

    @Test
    fun brushedOutsideTheSpotDoesNotCount() {
        openKids()
        kidsPrefs().recordChallenge(ChallengeLevels.all[0].id, 80)
        kidsPrefs().recordChallenge(ChallengeLevels.all[1].id, 80)
        compose.runOnIdle { assertTrue(ctl().open(2)) }
        waitFor { exists("challenge_card") }
        quiet()
        val spots = ctl().run.value!!.target.spots()
        val (at, color) = spots.first()
        compose.runOnIdle { vm().choosePaintBrush(); vm().setRegionColor(color) }
        // Kâğıdın köşesinde boyamak (alan dışı, serbest) noktayı boyamaz
        brushStroke(Offset(40f, 40f), Offset(120f, 60f))
        compose.waitForIdle()
        assertTrue(ctl().live.value?.marks?.first()?.done != true)
        // Yanlış renkle noktayı boyamak da saymaz
        compose.runOnIdle { vm().setRegionColor(if (color == red) blue else red) }
        brushStroke(at + Offset(-10f, 0f), at + Offset(10f, 0f))
        compose.waitForIdle()
        assertTrue(ctl().live.value?.marks?.first()?.done != true)
        // Doğru renkle sayar; geri alınca yeniden boyasız
        compose.runOnIdle { vm().setRegionColor(color) }
        brushStroke(at + Offset(-10f, 0f), at + Offset(10f, 0f))
        waitFor { ctl().live.value?.marks?.first()?.done == true }
        compose.runOnIdle { vm().undo() }
        waitFor { ctl().live.value?.marks?.first()?.done != true }
    }
}

/**
 * v45 çocuk menüsü (telefon): sıra, adlar, simgeler, büyük renkli düğmeler, araç yardımı ve tema renkleri.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45KidsMenuTest : V45KidsBase() {
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun dp(px: Float) = px / compose.density.density

    @Test
    fun orderNamesIconsAndSizes() {
        openKids()
        waitFor { exists("simple_tool_paint_brush") }
        // v47 sıra: geri al, ileri al, Kalem, Silgi, Boya, Kova, Şekiller, Görev, Kalıp, Yazı, sonra ötekiler (Parçalar,
        // Renkler…); dikeyde iki satır (önce satır, sonra soldan sağa)
        val order = listOf("undo", "redo", "brush", "eraser", "paint_brush", "paint", "shape", "challenge", "template", "text", "elements", "brush_settings")
        val lefts = order.map { compose.onNodeWithTag("simple_tool_$it").fetchSemanticsNode().positionInRoot.let { p -> p.y * 10000f + p.x } }
        assertEquals(lefts.sorted(), lefts)
        assertTrue(exists("simple_tool_eyedropper"))
        assertFalse(exists("simple_tool_rect_select") || exists("simple_tool_free_select"))
        // Çocuk adları ve her düğmenin altında adı
        val names = mapOf(
            "undo" to "Geri al", "redo" to "İleri al", "brush" to "Kalem", "eraser" to "Silgi", "paint_brush" to "Boya", "paint" to "Kova",
            "elements" to "Parçalar", "brush_settings" to "Renkler", "shape" to "Şekiller", "challenge" to "Görev", "background" to "Zemin",
            "image" to "Resim", "text" to "Yazı", "select" to "Seç", "eyedropper" to "Renk al", "ruler" to "Cetvel",
            "template" to "Kalıp", "expand" to "Büyüt",
        )
        names.forEach { (id, name) ->
            val node = compose.onNodeWithTag("simple_tool_$id").fetchSemanticsNode()
            assertEquals(id, name, node.config[SemanticsProperties.ContentDescription].single())
            assertEquals(1, compose.onAllNodesWithTag("button_label:$name", useUnmergedTree = true).fetchSemanticsNodes().size)
        }
        // Büyük düğmeler: telefonda 64 dp (basit menüde 36 dp), dokunma alanı en az 48 dp
        assertEquals("tile:64", stateOf("kids_tool_shelf"))
        for (id in names.keys) {
            if (id != "undo" && id != "redo") compose.onNodeWithTag("simple_tool_$id").performScrollTo()
            val b = bounds("simple_tool_$id")
            assertTrue("$id ${dp(b.height)}", dp(b.height) >= 63.5f)
            assertTrue("$id ${dp(b.width)}", dp(b.width) >= 63.5f)
            // Ad düğmenin içinde (kesilmez)
            val label = compose.onNodeWithTag("button_label:${names.getValue(id)}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$id adı taşıyor: $label / $b", label.left >= b.left - 0.5f && label.right <= b.right + 0.5f && label.bottom <= b.bottom + 0.5f)
        }
        // Raf ekrana sığar, başlıktaki ⋮ ve kaydet görünür
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val shelf = bounds("kids_tool_shelf")
        // v45 menü sona kaydırılınca da geri al ve yinele rafın başında görünür kalır (kaydırılan bölümün dışında)
        compose.onNodeWithTag("simple_tool_expand").performScrollTo()
        for (id in listOf("undo", "redo")) {
            val b = bounds("simple_tool_$id")
            assertTrue("$id görünmüyor: $b / $shelf", b.left >= shelf.left && b.right <= shelf.right && b.width > 0f)
        }
        val firstTool = bounds("simple_tool_brush")
        assertTrue("Kalem kaydırılıp gizlenmeli: $firstTool", firstTool.right <= bounds("simple_tool_redo").right + 0.5f)
        assertTrue(shelf.left >= 0f && shelf.right <= root.right + 0.5f)
        for (h in V38LabelsBase.HEADER) {
            val b = compose.onNode(hasContentDescription(h)).fetchSemanticsNode().boundsInRoot
            assertTrue(b.left >= 0f && b.right <= root.right + 0.5f)
        }
        shot("v45-kids-menu")
    }

    @Test
    fun holdShowsKidsDescriptions() {
        openKids()
        waitFor { exists("simple_tool_paint_brush") }
        val ctx = compose.activity
        hold("Boya", "simple_tool_row")
        waitFor { helpBox()?.contains(ctx.getString(R.string.v45_help_kids_paint_brush)) == true }
        // Basılı tutmak aracı seçmez
        assertFalse(vm().paintBrush.value)
        compose.onNodeWithTag("tool_help_box").performClick()
        hold("Kova", "simple_tool_row")
        waitFor { helpBox()?.contains(ctx.getString(R.string.v45_help_kids_bucket)) == true }
        // Dokunmak seçer
        compose.onNode(inRow("Boya", "simple_tool_row")).performScrollTo().performClick()
        waitFor { vm().paintBrush.value }
        // Menüdeki bütün düğmelerin açıklaması var
        val table = ToolDescriptions.table(ctx)
        val labels = compose.onAllNodes(hasAnyAncestor(hasTestTag("simple_tool_row")) and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].single() }
        assertTrue(labels.size >= 19)
        assertTrue("açıklamasız: ${labels.filter { it !in table }}", labels.all { it in table })
        // Adı başka düğmeyle aynı olanlar da (Kalem = kalem fırçası, Renkler, Parçalar) çocuk açıklamasını gösterir
        for ((label, res) in listOf("Kalem" to R.string.v45_help_kids_pen, "Renkler" to R.string.v45_help_kids_colors, "Parçalar" to R.string.v45_help_kids_parts, "Zemin" to R.string.v45_help_kids_background)) {
            runCatching { compose.onNodeWithTag("tool_help_box").performClick() }
            hold(label, "simple_tool_row")
            waitFor { helpBox()?.contains(ctx.getString(res)) == true }
        }
    }

    @Test
    fun tileColorsFollowTheme() {
        openKids()
        waitFor { exists("simple_tool_shape") }
        val light = KidsToolColors.colors("shape", dark = false, highContrast = false).background
        compose.onNodeWithTag("simple_tool_shape").performScrollTo()
        val px = tilePixel("simple_tool_shape", 8f)
        assertTrue(String.format("%08X", px), near(px, String.format("#%06X", light.toArgb() and 0xFFFFFF), 10))
    }
}

/** Koyu tema ve yüksek karşıtlıkta çocuk menüsü: renkler okunur kalır, raf ve düğmeler çalışır. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45KidsDarkContrastTest : V45KidsBase() {
    @Test
    fun darkHighContrastTiles() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            compose.runOnUiThread { ThemePrefs.selectMode(app, ThemeMode.DARK); ThemePrefs.setHighContrast(app, true) }
            openKids()
            waitFor { exists("simple_tool_paint_brush") }
            for (id in listOf("shape", "eraser", "paint")) {
                val want = KidsToolColors.colors(id, dark = true, highContrast = true)
                compose.onNodeWithTag("simple_tool_$id").performScrollTo()
                val px = tilePixel("simple_tool_$id", 9f)
                assertTrue("$id zemini", near(px, String.format("#%06X", want.background.toArgb() and 0xFFFFFF), 12))
                assertTrue(KidsContrast.ratio(want.content, want.background) >= 7.0)
            }
            // Fırça yüksek karşıtlıkta da çalışır
            pickBrushTool()
            compose.runOnIdle { vm().setRegionColor(red) }
            brushStroke(Offset(100f, 100f), Offset(200f, 100f))
            waitFor { strokes().size == 1 }
            shot("v45-kids-dark-hc")
        } finally {
            compose.runOnUiThread { ThemePrefs.selectMode(app, ThemeMode.SYSTEM); ThemePrefs.setHighContrast(app, false) }
        }
    }
}


/**
 * v45 çocuk menüsünün yerleşimi: telefon dikey/yatay, 7" ve 10" tablet dikey/yatay. Düğmeler en az 48 dp (ekrana göre
 * 56/64/72 dp), adları düğmenin içinde, raf ekrana ve başlığa taşmaz, ⋮ ve kaydet görünür, çizim alanı yeterli; menünün
 * son düğmesine kaydırılınca tamamen görünür.
 */
abstract class V45KidsLayoutBase : V45KidsBase() {
    protected abstract val expectedTile: Int

    @Test
    fun kidsMenuFitsWithoutClipping() {
        openKids()
        waitFor { exists("kids_tool_shelf") && exists("simple_tool_paint_brush") }
        assertEquals("tile:$expectedTile", stateOf("kids_tool_shelf"))
        val density = compose.density.density
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val shelf = compose.onNodeWithTag("kids_tool_shelf").fetchSemanticsNode().boundsInRoot
        assertTrue("raf ekranda: $shelf / $root", shelf.left >= -0.5f && shelf.right <= root.right + 0.5f && shelf.top >= 0f)
        // Raf, başlıktaki düğmelerin üstüne binmez
        for (h in V38LabelsBase.HEADER) {
            val b = compose.onNode(hasContentDescription(h)).fetchSemanticsNode().boundsInRoot
            assertTrue("$h ekranda", b.left >= 0f && b.right <= root.right + 0.5f && b.width > 20f)
            val overlap = b.left < shelf.right - 0.5f && b.right > shelf.left + 0.5f && b.top < shelf.bottom - 0.5f && b.bottom > shelf.top + 0.5f
            assertFalse("$h rafla çakışıyor: $b / $shelf", overlap)
        }
        val ids = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("tool") { n ->
            n.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("simple_tool_") && !it.startsWith("simple_tool_row") } == true
        }).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        // v47 yatayda Elemanlar, Katmanlar ve Özellikler menüde değil, sağdaki rayda
        val landscape = root.width > root.height
        // v48 çocukta 3B yok
        assertTrue(ids.toString(), ids.size >= if (landscape) 16 else 19)
        assertFalse(exists("simple_tool_model3d"))
        if (landscape) assertTrue(exists("panel_rail")) else assertTrue(exists("simple_tool_row_2"))
        for (tag in ids) {
            // v45 geri al ve yinele sabit (kaydırılmaz), ötekiler kaydırılarak görünür yapılır
            if (tag != "simple_tool_undo" && tag != "simple_tool_redo") compose.onNodeWithTag(tag).performScrollTo()
            compose.waitForIdle()
            val b = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val s = compose.onNodeWithTag("kids_tool_shelf").fetchSemanticsNode().boundsInRoot
            assertTrue("$tag ${b.height / density}", b.height / density >= expectedTile - 0.5f && b.height / density >= 48f)
            assertTrue("$tag ${b.width / density}", b.width / density >= 48f)
            // Kaydırılınca düğme rafın içinde tamamen görünür (dikeyde hiç kesilmez)
            assertTrue("$tag rafta: $b / $s", b.top >= s.top - 0.5f && b.bottom <= s.bottom + 0.5f && b.left >= s.left - 0.5f && b.right <= s.right + 0.5f)
            val label = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("label") { n ->
                n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("button_label:") == true
            } and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
            assertTrue("$tag adı kesik: $label / $b", label.left >= b.left - 0.5f && label.right <= b.right + 0.5f && label.top >= b.top - 0.5f && label.bottom <= b.bottom + 0.5f)
        }
        // Çizim alanı menüden sonra yeterince büyük
        val canvas = compose.onNodeWithTag("drawing_canvas").fetchSemanticsNode().boundsInRoot
        assertTrue("çizim alanı ${canvas.height / density}", canvas.height / density >= 200f)
        assertTrue(canvas.top >= shelf.bottom - 0.5f || canvas.left >= shelf.right - 0.5f)
        // Boyama satırı (fırça boyu ve palet) da sığar
        pickBrushTool()
        waitFor { exists("kids_brush_size") }
        val sizes = compose.onNodeWithTag("kids_brush_size").fetchSemanticsNode().boundsInRoot
        assertTrue(sizes.left >= -0.5f && sizes.right <= root.right + 0.5f)
        for (i in 0..2) {
            val chip = compose.onNodeWithTag("kids_brush_size_$i").fetchSemanticsNode().boundsInRoot
            assertTrue(chip.width / density >= 47.5f && chip.height / density >= 47.5f)
        }
        shot("v45-kids-layout-${javaClass.simpleName}")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V45KidsLayoutPhoneTest : V45KidsLayoutBase() { override val expectedTile = 64 }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V45KidsLayoutPhoneLandTest : V45KidsLayoutBase() { override val expectedTile = 56 }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w600dp-h960dp-port-hdpi")
class V45KidsLayoutTablet7Test : V45KidsLayoutBase() { override val expectedTile = 72 }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w960dp-h600dp-land-hdpi")
class V45KidsLayoutTablet7LandTest : V45KidsLayoutBase() { override val expectedTile = 72 }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w800dp-h1280dp-port-mdpi")
class V45KidsLayoutTablet10Test : V45KidsLayoutBase() { override val expectedTile = 72 }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w1280dp-h800dp-land-mdpi")
class V45KidsLayoutTablet10LandTest : V45KidsLayoutBase() { override val expectedTile = 72 }

/** v45 çocuk adları ve açıklamaları 7 dilde: gerçek çeviri, her adın açıklaması var. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V45KidsStringsTest {
    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    private val names = listOf(
        R.string.v45_kids_undo, R.string.v45_kids_redo, R.string.v45_kids_pen, R.string.v45_kids_eraser, R.string.v45_kids_paint_brush,
        R.string.v45_kids_bucket, R.string.v45_kids_parts, R.string.v45_kids_colors, R.string.v45_kids_shapes, R.string.v45_kids_challenge,
        R.string.v45_kids_background, R.string.v45_kids_image, R.string.v45_kids_text, R.string.v45_kids_model3d, R.string.v45_kids_select,
        R.string.v45_kids_eyedropper, R.string.v45_kids_ruler, R.string.v45_kids_template, R.string.v45_kids_expand,
        R.string.v45_kids_size_thin, R.string.v45_kids_size_medium, R.string.v45_kids_size_thick,
    )
    private val texts = listOf(
        R.string.v45_kids_brush_size, R.string.v45_help_kids_pen, R.string.v45_help_kids_paint_brush, R.string.v45_help_kids_bucket,
        R.string.v45_help_kids_parts, R.string.v45_help_kids_colors, R.string.v45_help_kids_shapes, R.string.v45_help_kids_background,
        R.string.v45_help_kids_brush_size, R.string.v45_challenge_tip_pen, R.string.v45_challenge_tip_paint_brush,
        R.string.v45_tut_kids_pen, R.string.v45_tut_kids_pen_text,
    )

    private val own = listOf(
        R.string.v45_kids_pen to R.string.v45_help_kids_pen, R.string.v45_kids_paint_brush to R.string.v45_help_kids_paint_brush,
        R.string.v45_kids_bucket to R.string.v45_help_kids_bucket, R.string.v45_kids_parts to R.string.v45_help_kids_parts,
        R.string.v45_kids_colors to R.string.v45_help_kids_colors, R.string.v45_kids_shapes to R.string.v45_help_kids_shapes,
        R.string.v45_kids_background to R.string.v45_help_kids_background,
    )

    private fun ctx(lang: String): android.content.Context {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val conf = android.content.res.Configuration(base.resources.configuration)
        conf.setLocale(Locale.forLanguageTag(lang))
        return base.createConfigurationContext(conf)
    }

    @Test
    fun turkishNamesAreShortAndSimple() {
        val tr = ctx("tr")
        assertEquals(
            listOf("Geri al", "İleri al", "Kalem", "Silgi", "Boya", "Kova", "Parçalar", "Renkler", "Şekiller", "Görev", "Zemin", "Resim", "Yazı", "3B", "Seç", "Renk al", "Cetvel", "Kalıp", "Büyüt", "İnce", "Orta", "Kalın"),
            names.map { tr.getString(it) },
        )
        assertEquals("Fırça boyu", tr.getString(R.string.v45_kids_brush_size))
        // Adlar kısa: en çok iki kelime, 10 harf
        names.forEach { val n = tr.getString(it); assertTrue(n, n.length <= 10 && n.split(' ').size <= 2) }
        // Çocuklara özgü açıklamalar araç kimliğiyle bağlı (adı başka düğmeyle aynı olsa da doğru açıklama çıkar)
        assertEquals(
            mapOf("brush" to R.string.v45_help_kids_pen, "paint_brush" to R.string.v45_help_kids_paint_brush, "paint" to R.string.v45_help_kids_bucket,
                "elements" to R.string.v45_help_kids_parts, "brush_settings" to R.string.v45_help_kids_colors, "shape" to R.string.v45_help_kids_shapes,
                "background" to R.string.v45_help_kids_background),
            KidsMenu.help,
        )
    }

    @Test
    fun allSevenLanguagesTranslated() {
        val tr = ctx("tr")
        for (lang in listOf("en", "ar", "ja", "ko", "ru", "zh")) {
            val c = ctx(lang)
            var same = 0
            (names + texts).forEach { id ->
                val v = c.getString(id)
                assertTrue("$lang boş", v.isNotBlank())
                if (v == tr.getString(id)) same++
            }
            // Yalnız "3B/3D" gibi sayı içeren ad aynı kalabilir
            assertTrue("$lang çevrilmemiş: $same", same == 0)
            // Kendi dilinde: Latin olmayan dillerde Latin harfli ad yok (3D dışında)
            if (lang in listOf("ar", "ja", "ko", "ru", "zh")) {
                names.map { c.getString(it) }.filter { it != "3D" }.forEach { n -> assertFalse("$lang: $n", n.all { ch -> ch.code < 128 }) }
            }
            // Bütün adların açıklaması var; boya fırçası ve kova kendi açıklamalarıyla
            val table = ToolDescriptions.table(c)
            names.forEach { id -> assertNotNull("$lang ${c.getString(id)}", table[c.getString(id)]) }
            // Çocuklara özgü açıklamalar birbirinden ayrı
            assertEquals(lang, own.size, own.map { c.getString(it.second) }.toSet().size)
            // Adlar birbirinden ayrı (aynı adlı iki düğme yok)
            val shown = names.take(19).map { c.getString(it) }
            assertEquals(lang, shown.size, shown.toSet().size)
        }
    }
}
