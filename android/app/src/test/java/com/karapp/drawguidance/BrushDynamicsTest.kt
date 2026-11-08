package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.isObject
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.model.canvasOf
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.ui.drawing.BrushCategory
import com.karapp.drawguidance.ui.drawing.PickerBrushTypes
import com.karapp.drawguidance.ui.drawing.defaultSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/** Yeni fırça kategorileri, akışkanlık ve çizim gücünün gerçek piksel çizimine etkisi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class BrushDynamicsTest {

    @After fun tearDown() = stopKoin()

    private val exporter = ImageExporter(ApplicationProvider.getApplicationContext())

    private fun wave() = (0..60).map { StrokePoint(30f + it * 4f, 80f + sin(it / 60f * 6.28f).toFloat() * 25f) }

    private fun render(type: BrushType, flow: Float = 1f, strength: Float = 1f, size: Float = type.defaultSize() * 1.4f): Bitmap {
        val stroke = Stroke("s-${type.name}", wave(), "#1B2430", type, size, 1f, 0, flow = flow, strength = strength)
        return exporter.render(
            canvasOf("t", "t", 300, 160, "#FFFFFF", false, listOf(stroke), 0, 0),
        )
    }

    private fun ink(bitmap: Bitmap, x: Int, y: Int): Int {
        val c = bitmap.getPixel(x, y)
        return 255 - (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
    }

    /** Toplam mürekkep miktarı (koyuluk × alan) */
    private fun totalInk(bitmap: Bitmap): Long {
        var sum = 0L
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) sum += ink(bitmap, x, y)
        return sum
    }

    /** Mürekkep bulunan piksel sayısı (kapladığı alan) */
    private fun coverage(bitmap: Bitmap, threshold: Int = 12): Int {
        var n = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (ink(bitmap, x, y) > threshold) n++
        return n
    }

    /** 12×8 kaba parmak izi: fırçaların görüntülerinin birbirinden farklı olduğunu karşılaştırmak için */
    private fun signature(bitmap: Bitmap): List<Int> {
        val cw = bitmap.width / 12
        val ch = bitmap.height / 8
        return (0 until 8).flatMap { row ->
            (0 until 12).map { col ->
                var s = 0
                for (y in row * ch until (row + 1) * ch step 2) for (x in col * cw until (col + 1) * cw step 2) s += ink(bitmap, x, y)
                s / 16
            }
        }
    }

    @Test fun everyBrushBelongsToExactlyOneCategoryAndEraserIsNotInThePicker() {
        // Fırça olmayan eleman türleri (silgi, resim, yazı, vektör şekil, diyagram) seçicide yoktur
        val drawable = BrushType.entries.filter { !it.isObject && it != BrushType.ERASER }
        // Her fırça, tuval türü başına en çok bir kategoride (v15: nokta izi ve 3B fırça iki türde de var)
        drawable.forEach { type ->
            assertTrue("$type bir kategoride olmalı", BrushCategory.entries.any { type in it.brushes })
            com.karapp.drawguidance.data.model.CanvasKind.entries.forEach { kind ->
                assertTrue("$type $kind içinde tek kategoride olmalı", BrushCategory.forKind(kind).count { type in it.brushes } <= 1)
            }
        }
        assertEquals(drawable.toSet(), (PickerBrushTypes + com.karapp.drawguidance.ui.drawing.NoteBrushTypes).toSet())
        assertFalse(BrushType.ERASER in PickerBrushTypes)
        // v21: İzler, Canlı dokuları ve Malzeme dokuları kategorileri eklendi
        assertEquals(12, BrushCategory.forKind(com.karapp.drawguidance.data.model.CanvasKind.ART).size)
        assertEquals(3, BrushCategory.forKind(com.karapp.drawguidance.data.model.CanvasKind.NOTES).size)
        assertEquals(9, com.karapp.drawguidance.ui.drawing.NoteBrushTypes.size)
        val shared = setOf(BrushType.DOT_PEN, BrushType.BRUSH_3D)
        assertEquals(shared, com.karapp.drawguidance.ui.drawing.NoteBrushTypes.filter { it in PickerBrushTypes }.toSet())
        assertEquals(BrushCategory.HARD, BrushCategory.of(BrushType.PASTEL))
        assertEquals(BrushCategory.HALFTONE, BrushCategory.of(BrushType.HALFTONE_LINES))
    }

    @Test fun everyPickerBrushPaintsAndLooksDifferentFromTheOthers() {
        val signatures = PickerBrushTypes.associateWith { type ->
            val bitmap = render(type)
            assertTrue("$type iz bırakmalı", coverage(bitmap) > 150)
            signature(bitmap)
        }
        // Her fırça çifti belirgin şekilde farklı olmalı (ortalama mutlak fark)
        val types = signatures.keys.toList()
        for (i in types.indices) for (j in i + 1 until types.size) {
            val a = signatures.getValue(types[i])
            val b = signatures.getValue(types[j])
            val diff = a.indices.sumOf { kotlin.math.abs(a[it] - b[it]) }.toDouble() / a.size
            assertTrue("${types[i]} ile ${types[j]} neredeyse aynı çiziyor (fark=$diff)", diff > 1.0)
        }
    }

    @Test fun lowFlowDepositsLessPaint() {
        listOf(
            BrushType.PENCIL, BrushType.MARKER, BrushType.PASTEL, BrushType.CHARCOAL, BrushType.SOFT_ROUND,
            BrushType.AIRBRUSH, BrushType.STAMP_STAR, BrushType.PATTERN_DOTS, BrushType.TEXTURE_SPONGE, BrushType.HALFTONE_DOTS,
        ).forEach { type ->
            val full = totalInk(render(type, flow = 1f))
            val low = totalInk(render(type, flow = 0.25f))
            println("FLOW $type full=$full low=$low oran=${"%.2f".format(low.toDouble() / full)}")
            assertTrue("$type: düşük akışkanlık daha az boya bırakmalı ($low / $full)", low < full * 0.8)
        }
    }

    @Test fun lowStrengthIsThinnerLighterAndLessFilled() {
        listOf(BrushType.PENCIL, BrushType.INK_PEN, BrushType.MARKER, BrushType.PASTEL, BrushType.HARD_ROUND, BrushType.HALFTONE_LINES, BrushType.TEXTURE_CANVAS)
            .forEach { type ->
                val strong = render(type, strength = 1f)
                val weak = render(type, strength = 0.2f)
                println("STRENGTH $type alan ${coverage(strong)}→${coverage(weak)} mürekkep ${totalInk(strong)}→${totalInk(weak)}")
                assertTrue("$type: zayıf baskı daha dar alan kaplamalı", coverage(weak) < coverage(strong))
                assertTrue("$type: zayıf baskı daha açık olmalı", totalInk(weak) < totalInk(strong) * 0.75)
            }
    }

    @Test fun pastelLeavesAGrainyHardEdgedMarkUnlikeTheSoftBrush() {
        fun edgeProfile(bitmap: Bitmap): Pair<Int, Int> {
            // Düz yatay çizginin ortasından dikey kesit: tam dolu ve yarı saydam (kenar geçişi) satır sayısı
            var solid = 0
            var partial = 0
            for (x in 120..180) for (y in 0 until bitmap.height) {
                val v = ink(bitmap, x, y)
                if (v > 150) solid++ else if (v > 15) partial++
            }
            return solid to partial
        }
        fun flat(type: BrushType) = exporter.render(
            canvasOf(
                "t", "t", 300, 160, "#FFFFFF", false,
                listOf(Stroke("f", (0..40).map { StrokePoint(20f + it * 6.5f, 80f) }, "#1B2430", type, 24f, 1f, 0)),
                0, 0,
            ),
        )
        val pastel = flat(BrushType.PASTEL)
        val soft = flat(BrushType.SOFT_ROUND)
        val (_, pastelPartial) = edgeProfile(pastel)
        val (softSolid, softPartial) = edgeProfile(soft)
        // Pastelin içinde tanecik boşlukları: satır boyunca dalgalanma
        val row = (40..260).map { ink(pastel, it, 80).toDouble() }
        val mean = row.average()
        val std = kotlin.math.sqrt(row.sumOf { (it - mean) * (it - mean) } / row.size)
        println("PASTEL std=$std pastelPartial=$pastelPartial softSolid=$softSolid softPartial=$softPartial")
        assertTrue("pastel tanecikli olmalı (std=$std)", std > 25)
        assertTrue("yumuşak fırçanın kenar geçişi pastelden belirgin geniş olmalı ($softPartial / $pastelPartial)", softPartial > pastelPartial * 2)
    }
}
