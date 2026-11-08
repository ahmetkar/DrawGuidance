package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.TubeMaterial
import com.karapp.drawguidance.data.model.TubeParams
import com.karapp.drawguidance.data.settings.BrushTuning
import com.karapp.drawguidance.ui.drawing.BrushPainter
import com.karapp.drawguidance.ui.drawing.InkFilter
import com.karapp.drawguidance.ui.drawing.LiveStrokeLayer
import com.karapp.drawguidance.ui.drawing.defaultSpacing
import com.karapp.drawguidance.ui.drawing.reachFactor
import com.karapp.drawguidance.ui.screens.canvasedit.components.SizeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

/** v20 girdi süzgeci: sabitleme zikzakları düzeltir, çizim hızı mürekkebi geciktirip yetiştirir; ayarlar ve boyut ölçeği. */
class V20InkFilterTest {

    private fun P(x: Float, y: Float) = InkFilter.Point(x, y)

    @Test
    fun passesThroughWhenOff() {
        val f = InkFilter(0f, 1f, 1f)
        assertFalse(f.active)
        assertEquals(listOf(P(0f, 0f)), f.start(P(0f, 0f), 0))
        assertEquals(listOf(P(5f, 1f)), f.move(P(5f, 1f), 8))
        assertTrue(f.finish(P(5f, 1f), 16).isEmpty())
    }

    @Test
    fun stabilizerRemovesZigzag() {
        // Düz bir çizgi boyunca ±4 birimlik ard arda zikzak (titreme)
        val raw = (0..120).map { i -> P(i * 3f, if (i % 2 == 0) 4f else -4f) }
        val f = InkFilter(0.8f, 1f, 1f)
        assertTrue(f.active)
        val out = ArrayList<InkFilter.Point>()
        out += f.start(raw[0], 0)
        raw.drop(1).forEachIndexed { i, p -> out += f.move(p, (i + 1) * 8L) }
        out += f.finish(raw.last(), 1000)
        // Uçların dışında sapma çok küçük (ham zikzak ±4)
        val middle = out.filter { it.x in 30f..330f }
        assertTrue("örnek var: ${middle.size}", middle.size > 10)
        assertTrue("zikzak düzeldi: ${middle.maxOf { abs(it.y) }}", middle.maxOf { abs(it.y) } < 1.2f)
        // Yön değişimi (ardışık noktalar arası dönüş) yok denecek kadar az
        var flips = 0
        for (i in 2 until middle.size) {
            val a = middle[i - 1].y - middle[i - 2].y; val b = middle[i].y - middle[i - 1].y
            if (a * b < 0 && abs(a) > 0.3f && abs(b) > 0.3f) flips++
        }
        assertTrue("zikzak dönüşü: $flips", flips <= 2)
        // Çizgi parmağın bıraktığı yerde biter
        assertEquals(raw.last().x, out.last().x, 0.01f)
        assertEquals(raw.last().y, out.last().y, 0.01f)
    }

    @Test
    fun slowSpeedLagsThenCatchesUp() {
        val f = InkFilter(0f, 0.3f, 1f)
        assertTrue(f.needsTicks)
        f.start(P(0f, 0f), 0)
        var last = P(0f, 0f)
        for (i in 1..10) f.move(P(i * 10f, 0f), i * 10L).lastOrNull()?.let { last = it }
        assertTrue("mürekkep geride: ${last.x}", last.x < 70f)
        // Parmak dururken zaman ilerledikçe yetişir
        var t = 100L
        repeat(60) { t += 16; f.tick(t).lastOrNull()?.let { last = it } }
        assertTrue("yetişti: ${last.x}", last.x > 99f)
        // Hızlı ayarda (1) gecikme yok
        val g = InkFilter(0f, 1f, 1f)
        g.start(P(0f, 0f), 0)
        assertEquals(50f, g.move(P(50f, 0f), 5).single().x, 0.001f)
        // Bitişte son noktaya tamamlanır
        val h = InkFilter(0f, 0.1f, 1f)
        h.start(P(0f, 0f), 0)
        h.move(P(100f, 0f), 10)
        val end = h.finish(P(100f, 0f), 12)
        assertEquals(100f, end.last().x, 0.01f)
        // Aradaki noktalar sık (çizgi kopuk görünmez)
        assertTrue(end.zipWithNext().all { (a, b) -> hypot(b.x - a.x, b.y - a.y) <= 5f })
    }

    @Test
    fun tuningStoresOnlyChangedLook() {
        val t = BrushTuning()
        assertNull(t.params(BrushType.PENCIL))
        assertNull("girdi ayarları görünüme işlenmez", t.copy(speed = 0.3f, stabilizer = true).params(BrushType.PENCIL))
        assertEquals(0.8f, t.copy(spacing = 0.8f).params(BrushType.HARD_ROUND)!!.spacing)
        assertNull("aralığı olmayan fırça", t.copy(spacing = 0.8f).params(BrushType.PENCIL))
        assertNull("varsayılan aralık", t.copy(spacing = BrushType.HARD_ROUND.defaultSpacing()).params(BrushType.HARD_ROUND))
        assertNull("uç yönü yalnız yassı uçta", t.copy(nibFollow = true).params(BrushType.PENCIL))
        assertTrue(t.copy(nibFollow = true).params(BrushType.CALLIGRAPHY)!!.nibFollow)
        assertEquals(TubeMaterial.METAL, t.copy(tube = TubeParams(material = TubeMaterial.METAL)).params(BrushType.BRUSH_3D)!!.tube!!.material)
        assertNull("3B ayarı başka fırçada", t.copy(tube = TubeParams(material = TubeMaterial.METAL)).params(BrushType.PENCIL))
        assertEquals(25f, t.copy(tiltThreshold = 25f).params(BrushType.PENCIL)!!.tiltThreshold)
    }

    @Test
    fun sizeSliderIsExponentialUpTo380() {
        assertEquals(380f, SizeScale.toSize(1f))
        assertEquals(1f, SizeScale.toSize(0f))
        // Ortada küçük boyutlar (hassas): yarıda ~19 px
        assertTrue(SizeScale.toSize(0.5f) in 15f..25f)
        for (s in listOf(1f, 4f, 12f, 80f, 300f, 380f)) assertEquals(s, SizeScale.toSize(SizeScale.toPosition(s)), s * 0.02f + 0.5f)
    }
}

/** v20 fırça motoru: damga aralığı, eğim ayarları (kalınlaşma, eşik), yassı ucun yönü, 3B tüp (ışık, malzeme, gölge). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class V20EngineTest {

    private class Ink(val bitmap: Bitmap) {
        fun inked(x0: Int = 0, y0: Int = 0, x1: Int = bitmap.width, y1: Int = bitmap.height): Int {
            var n = 0
            for (y in y0 until y1) for (x in x0 until x1) if (Color.alpha(bitmap.getPixel(x, y)) > 40) n++
            return n
        }
        /** Satır boyunca mürekkepsiz sütun sayısı (damgalar arası boşluk). */
        fun gapsAlong(y: Int, x0: Int, x1: Int): Int = (x0 until x1).count { Color.alpha(bitmap.getPixel(it, y)) < 20 }
        /** Sütundaki mürekkep yüksekliği. */
        fun height(x: Int): Int = (0 until bitmap.height).count { Color.alpha(bitmap.getPixel(x, it)) > 60 }
    }

    private fun draw(
        type: BrushType, size: Float, params: BrushParams? = null, tilts: FloatArray? = null, orients: FloatArray? = null,
        n: Int = 60, w: Int = 480, h: Int = 200, color: Int = 0xFF1E1E1E.toInt(), paper: Int? = null,
    ): Ink {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        paper?.let { c.drawColor(it) }
        val xs = FloatArray(n) { 40f + (w - 80f) * it / (n - 1) }
        val ys = FloatArray(n) { h / 2f }
        BrushPainter.paint(c, xs, ys, n, color, type, size, 1f, 5, 1f, 1f, null, tilts, params, orients)
        return Ink(bmp)
    }

    @Test
    fun spacingChangesStampDistance() {
        val dense = draw(BrushType.HARD_ROUND, 16f)
        val sparse = draw(BrushType.HARD_ROUND, 16f, BrushParams(spacing = 2.5f))
        assertEquals("varsayılanda kesintisiz", 0, dense.gapsAlong(100, 60, 420))
        assertTrue("aralık büyüyünce damgalar ayrılır: ${sparse.gapsAlong(100, 60, 420)}", sparse.gapsAlong(100, 60, 420) > 60)
        val stars = draw(BrushType.STAMP_STAR, 20f).inked()
        val fewStars = draw(BrushType.STAMP_STAR, 20f, BrushParams(spacing = 3f)).inked()
        assertTrue("seyrek yıldız: $fewStars < $stars", fewStars < stars * 0.7)
    }

    @Test
    fun tiltSettingsWidenAndThreshold() {
        val tilt = FloatArray(60) { 0.5f } // 45°
        val upright = draw(BrushType.PENCIL, 20f).height(240)
        val tilted = draw(BrushType.PENCIL, 20f, tilts = tilt).height(240)
        assertTrue("varsayılan: yatık kurşun kalem geniş $tilted > $upright", tilted > upright * 1.4)
        val noWiden = draw(BrushType.PENCIL, 20f, BrushParams(tiltWidth = 0f), tilt).height(240)
        assertEquals("eğim etkisi kapalı", upright.toFloat(), noWiden.toFloat(), 2f)
        val threshold = draw(BrushType.PENCIL, 20f, BrushParams(tiltThreshold = 60f), tilt).height(240)
        assertEquals("45° < eşik 60°: etki yok", upright.toFloat(), threshold.toFloat(), 2f)
        // Varsayılanda eğimden etkilenmeyen tükenmez de ayarla genişler
        val pen = draw(BrushType.FINELINER, 20f, tilts = tilt).height(240)
        val penWide = draw(BrushType.FINELINER, 20f, BrushParams(tiltWidth = 2f), tilt).height(240)
        assertTrue("$penWide > $pen", penWide > pen * 1.5)
    }

    @Test
    fun flatNibFollowsPenDirection() {
        // Yatay çizgi: uç yatay (yön 90°: kalem sağa yatık) → ince; dikey (yön 0/180°) → kalın
        val horizontalNib = draw(BrushType.CALLIGRAPHY, 24f, BrushParams(nibFollow = true), orients = FloatArray(60) { 1f / 255f }).height(240)
        val verticalNib = draw(BrushType.CALLIGRAPHY, 24f, BrushParams(nibFollow = true), orients = FloatArray(60) { 0.25f }).height(240)
        val fixed = draw(BrushType.CALLIGRAPHY, 24f, orients = FloatArray(60) { 0.25f }).height(240)
        assertTrue("dikey uç kalın: $verticalNib > $horizontalNib", verticalNib > horizontalNib * 3)
        assertTrue("ayar kapalıyken sabit 45°: $fixed", fixed in (horizontalNib + 2)..(verticalNib - 2))
    }

    private fun lum(p: Int) = 0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)

    @Test
    fun tubeIsLitFromLightAndCastsShadow() {
        val white = 0xFFFFFFFF.toInt()
        val blue = 0xFF1E88E5.toInt()
        // Işık yukarıdan (270°): üst yarı alt yarıdan aydınlık, gölge aşağıda
        val top = draw(BrushType.BRUSH_3D, 40f, BrushParams(tube = TubeParams(lightAngle = 270f)), color = blue, paper = white).bitmap
        val mid = 240
        val column = (0 until top.height).filter { Color.alpha(top.getPixel(mid, it)) > 0 && top.getPixel(mid, it) != white }
        val inside = (60..140).filter { y -> Color.blue(top.getPixel(mid, y)) > Color.red(top.getPixel(mid, y)) + 40 }
        assertTrue("tüp var: ${inside.size}", inside.size > 30)
        val upper = inside.take(inside.size / 2).map { lum(top.getPixel(mid, it)) }.average()
        val lower = inside.drop(inside.size / 2).map { lum(top.getPixel(mid, it)) }.average()
        assertTrue("üst aydınlık: $upper > $lower", upper > lower + 10)
        val below = (inside.last() + 3..inside.last() + 14).map { lum(top.getPixel(mid, it)) }.average()
        val above = (inside.first() - 14..inside.first() - 3).map { lum(top.getPixel(mid, it)) }.average()
        assertTrue("gölge aşağıda: $below < $above", below < above - 8)
        assertTrue(column.isNotEmpty())
        // Gölge kapalıysa tüpün dışı kağıt rengi
        val flat = draw(BrushType.BRUSH_3D, 40f, BrushParams(tube = TubeParams(lightAngle = 270f, shadow = 0f)), color = blue, paper = white).bitmap
        val insideFlat = (60..140).filter { y -> Color.blue(flat.getPixel(mid, y)) > Color.red(flat.getPixel(mid, y)) + 40 }
        assertEquals(white, flat.getPixel(mid, insideFlat.last() + 6))
        // Işık aşağıdan (90°): alt yarı aydınlık
        val bottom = draw(BrushType.BRUSH_3D, 40f, BrushParams(tube = TubeParams(lightAngle = 90f, shadow = 0f)), color = blue, paper = white).bitmap
        val insideB = (60..140).filter { y -> Color.blue(bottom.getPixel(mid, y)) > Color.red(bottom.getPixel(mid, y)) + 40 }
        val up = insideB.take(insideB.size / 2).map { lum(bottom.getPixel(mid, it)) }.average()
        val down = insideB.drop(insideB.size / 2).map { lum(bottom.getPixel(mid, it)) }.average()
        assertTrue("alt aydınlık: $down > $up", down > up + 10)
    }

    @Test
    fun materialsDiffer() {
        val white = 0xFFFFFFFF.toInt()
        fun profile(m: TubeMaterial, gloss: Float = 0.6f): List<Double> {
            val b = draw(BrushType.BRUSH_3D, 40f, BrushParams(tube = TubeParams(lightAngle = 270f, material = m, gloss = gloss, shadow = 0f)), color = 0xFF2E7D32.toInt(), paper = white).bitmap
            return (60..140).map { lum(b.getPixel(240, it)) }.filter { it < 250 }
        }
        val matte = profile(TubeMaterial.MATTE)
        val plastic = profile(TubeMaterial.PLASTIC, 1f)
        val metal = profile(TubeMaterial.METAL)
        assertTrue("plastik parlar: ${plastic.max()} > ${matte.max()}", plastic.max() > matte.max() + 30)
        // Metalde aydınlık-karanlık bantlar: profilde birden çok tepe
        fun peaks(p: List<Double>) = (1 until p.size - 1).count { p[it] > p[it - 1] + 3 && p[it] >= p[it + 1] }
        assertTrue("metal bantlı: ${peaks(metal)} > ${peaks(matte)}", peaks(metal) > peaks(matte))
        // Camda kağıt görünür (gövde yarı saydam)
        val glass = draw(BrushType.BRUSH_3D, 40f, BrushParams(tube = TubeParams(material = TubeMaterial.GLASS, shadow = 0f)), color = 0xFF000000.toInt(), paper = white).bitmap
        val solid = draw(BrushType.BRUSH_3D, 40f, BrushParams(tube = TubeParams(material = TubeMaterial.MATTE, shadow = 0f)), color = 0xFF000000.toInt(), paper = white).bitmap
        assertTrue(lum(glass.getPixel(240, 100)) > lum(solid.getPixel(240, 100)) + 20)
    }

    @Test
    fun liveTubeMatchesStaticAndShadowDoesNotDoubleAtJoints() {
        val params = BrushParams(tube = TubeParams(lightAngle = 225f, shadow = 0.8f, shadowSoftness = 0.3f))
        val w = 520; val h = 240
        val n = 90
        val xs = FloatArray(n) { 40f + 440f * it / (n - 1) }
        val ys = FloatArray(n) { 120f + 50f * kotlin.math.sin(it / 10f) }
        val stat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(stat).also { it.drawColor(Color.WHITE) }.let { BrushPainter.paint(it, xs, ys, n, 0xFFE53935.toInt(), BrushType.BRUSH_3D, 36f, 1f, 9, 1f, 1f, params = params) }
        // Canlı: nokta nokta (her noktada kesinleşen parça karolara)
        val layer = LiveStrokeLayer()
        layer.start("t", BrushType.BRUSH_3D, 0xFFE53935.toInt(), 36f, 1f, 1f, 1f, 1f, 0f, 0f, w, h, params = params)
        for (i in 0 until n) layer.add(xs[i], ys[i])
        layer.finish()
        val live = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val lc = Canvas(live)
        lc.drawColor(Color.WHITE)
        layer.drawTo(lc)
        System.getenv("DG_SHOTS")?.let { dir ->
            java.io.File(dir, "v20-tube-static.png").outputStream().use { stat.compress(Bitmap.CompressFormat.PNG, 100, it) }
            java.io.File(dir, "v20-tube-live.png").outputStream().use { live.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        var diff = 0
        var big = 0
        for (y in 0 until h) for (x in 0 until w) {
            val a = stat.getPixel(x, y); val b = live.getPixel(x, y)
            val d = abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
            if (d > 30) diff++
            if (d > 90) big++
        }
        assertTrue("canlı ≈ kayıtlı: $diff farklı, $big çok farklı", diff < 40 && big < 6)
    }

    /** Sıkı kıvrımlar ve kendini kesen çizgide (kalın tüp) canlı çizimde delik (arka planın göründüğü iç nokta) olmaz. */
    @Test
    fun liveTubeHasNoHolesInTightTurns() {
        val w = 360; val h = 260
        val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
        // Zikzak (dar dönüşler) + küçük halka
        for (i in 0 until 40) { xs += 40f + i * 5f; ys += if ((i / 4) % 2 == 0) 60f + (i % 4) * 14f else 116f - (i % 4) * 14f }
        for (i in 0..70) { val a = i / 70f * 2.4f * PI.toFloat(); xs += 250f + 22f * cos(a); ys += 170f + 22f * sin(a) }
        val n = xs.size
        val px = xs.toFloatArray(); val py = ys.toFloatArray()
        for (params in listOf(BrushParams(tube = TubeParams(shadow = 0.5f)), null)) {
            val stat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(stat).also { it.drawColor(Color.WHITE) }.let { BrushPainter.paint(it, px, py, n, 0xFF1E88E5.toInt(), BrushType.BRUSH_3D, 44f, 1f, 9, 1f, 1f, params = params) }
            val layer = LiveStrokeLayer()
            layer.start("z", BrushType.BRUSH_3D, 0xFF1E88E5.toInt(), 44f, 1f, 1f, 1f, 1f, 0f, 0f, w, h, params = params)
            val live = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            var holes = 0
            for (i in 0 until n) {
                layer.add(px[i], py[i])
                // Her karede (kuyrukla birlikte) de delik olmamalı
                if (i % 7 == 3 || i == n - 1) {
                    val c = Canvas(live); c.drawColor(Color.WHITE); layer.drawTo(c)
                }
            }
            layer.finish()
            val c = Canvas(live); c.drawColor(Color.WHITE); layer.drawTo(c)
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                // Kayıtlı çizimde tüpün içi (dört komşusu da mavi) ama canlıda mavi değil
                fun blue(b: Bitmap, xx: Int, yy: Int) = b.getPixel(xx, yy).let { Color.blue(it) - Color.red(it) > 60 }
                if (blue(stat, x, y) && blue(stat, x - 1, y) && blue(stat, x + 1, y) && blue(stat, x, y - 1) && blue(stat, x, y + 1) && !blue(live, x, y)) holes++
            }
            System.getenv("DG_SHOTS")?.let { dir ->
                val tag = if (params == null) "noshadow" else "shadow"
                java.io.File(dir, "v20-tube-tight-$tag-static.png").outputStream().use { stat.compress(Bitmap.CompressFormat.PNG, 100, it) }
                java.io.File(dir, "v20-tube-tight-$tag-live.png").outputStream().use { live.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            assertEquals("delik yok ($params)", 0, holes)
            var diff = 0
            for (y in 0 until h) for (x in 0 until w) {
                val a = stat.getPixel(x, y); val b = live.getPixel(x, y)
                if (abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b)) > 30) diff++
            }
            assertTrue("dar kıvrımda canlı ≈ kayıtlı: $diff farklı", diff < 40)
        }
    }
}
