package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.canvasOf
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sqrt

/**
 * Her fırçanın gerçekten farklı çizdiğini gerçek piksel çizimiyle doğrular
 * (kullanıcının bildirdiği "tüm fırçalar kalem gibi çiziyor" hatasının regresyon testi).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class BrushRenderingTest {

    @After fun tearDown() = stopKoin()

    private val exporter = ImageExporter(ApplicationProvider.getApplicationContext())

    private fun stroke(type: BrushType, size: Float = 10f, points: List<StrokePoint> = horizontal(), color: String = "#000000", id: String = type.name) =
        Stroke(id = id, points = points, colorHex = color, brushType = type, sizePx = size, opacity = 1f, order = 0)

    private fun horizontal(y: Float = 60f) = (0..16).map { StrokePoint(20f + it * 10f, y) }

    private fun render(vararg strokes: Stroke): Bitmap = exporter.render(
        canvasOf(
            localId = "t", name = "t", width = 200, height = 120, backgroundColorHex = "#FFFFFF",
            aiEnabled = false, strokes = strokes.toList(), createdAt = 0, updatedAt = 0,
        ),
    )

    /** 0 = beyaz, 255 = tam siyah */
    private fun ink(bitmap: Bitmap, x: Int, y: Int): Int {
        val c = bitmap.getPixel(x, y)
        return 255 - (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
    }

    /** x=100 sütununda mürekkep bulunan satır sayısı (çizgi kalınlığı) */
    private fun thickness(bitmap: Bitmap) = (0 until bitmap.height).count { ink(bitmap, 100, it) > 20 }

    private fun maxInk(bitmap: Bitmap) = (0 until bitmap.height).maxOf { ink(bitmap, 100, it) }

    /** Çizginin ortasında yatay yönde mürekkep dalgalanması (doku) */
    private fun rowStdDev(bitmap: Bitmap, y: Int = 60): Double {
        val values = (40..160).map { ink(bitmap, it, y).toDouble() }
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    @Test
    fun eachBrushHasItsOwnLook() {
        val pencil = render(stroke(BrushType.PENCIL))
        val marker = render(stroke(BrushType.MARKER))
        val highlighter = render(stroke(BrushType.HIGHLIGHTER))
        val pastel = render(stroke(BrushType.PASTEL))

        val t = mapOf("pencil" to thickness(pencil), "marker" to thickness(marker), "highlighter" to thickness(highlighter), "pastel" to thickness(pastel))
        println("thickness=$t maxInk pencil=${maxInk(pencil)} marker=${maxInk(marker)} highlighter=${maxInk(highlighter)} pastel=${maxInk(pastel)}")
        println("rowStdDev pencil=${rowStdDev(pencil)} marker=${rowStdDev(marker)} pastel=${rowStdDev(pastel)}")

        // Kalem ince, marker kalın, fosforlu en geniş
        assertTrue("kalem ince olmalı: $t", t.getValue("pencil") in 3..8)
        assertTrue("marker kalemden belirgin kalın olmalı: $t", t.getValue("marker") >= t.getValue("pencil") * 2)
        assertTrue("fosforlu marker'dan geniş olmalı: $t", t.getValue("highlighter") > t.getValue("marker"))

        // Kalem ve marker koyu; fosforlu yarı saydam
        assertTrue(maxInk(pencil) > 200)
        assertTrue(maxInk(marker) > 200)
        assertTrue("fosforlu yarı saydam olmalı: ${maxInk(highlighter)}", maxInk(highlighter) in 50..150)

        // Pastel tanecikli: çizgi boyunca mürekkep yoğunluğu dalgalanır, marker düzgündür
        assertTrue("pastel dokulu olmalı: ${rowStdDev(pastel)}", rowStdDev(pastel) > 15.0)
        assertTrue("marker düz olmalı: ${rowStdDev(marker)}", rowStdDev(marker) < 5.0)

        // Hepsi birbirinden farklı görüntü üretir
        val all = listOf(pencil, marker, highlighter, pastel)
        for (i in all.indices) for (j in i + 1 until all.size) assertFalse("fırça $i ve $j aynı çiziyor", all[i].sameAs(all[j]))
    }

    @Test
    fun highlighterDarkensWhatIsUnderneath() {
        val ink = stroke(BrushType.MARKER, size = 12f, color = "#3366CC", id = "ink")
        val onlyInk = render(ink)
        val highlighted = render(ink, stroke(BrushType.HIGHLIGHTER, size = 10f, color = "#FFD400", id = "hl"))
        // Fosforlu, altındaki mavi çizginin üstünde çarpma karışımıyla daha koyu sonuç verir
        assertTrue(ink(highlighted, 100, 60) > ink(onlyInk, 100, 60))
    }

    @Test
    fun eraserReallyErasesStrokesButKeepsPaper() {
        val band = stroke(BrushType.MARKER, size = 30f, id = "band")
        val eraser = stroke(BrushType.ERASER, size = 10f, points = (0..12).map { StrokePoint(100f, it * 10f) }, id = "eraser")
        val bitmap = render(band, eraser)
        assertTrue("silinmemiş yer siyah kalmalı", ink(bitmap, 40, 60) > 200)
        assertTrue("silinen yerde kağıt (beyaz) görünmeli: ${ink(bitmap, 100, 60)}", ink(bitmap, 100, 60) < 15)
        assertTrue("silgi kağıdı şeffaflaştırmamalı", Color.alpha(bitmap.getPixel(100, 60)) == 255)
    }
}
