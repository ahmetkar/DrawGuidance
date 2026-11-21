package com.karapp.drawguidance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.model.BrushParams
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.GrainSpec
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.model.V56Selection
import com.karapp.drawguidance.data.model.V56Textures
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.settings.BrushTuning
import com.karapp.drawguidance.ui.drawing.GrainRenderer
import com.karapp.drawguidance.ui.drawing.StrokeRenderer
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v56 aşama 5 saf kurallar: hazır doku paketi, gren yerleşimi, fırça ayarı, kement doku dolgusu. */
class V56GrainRulesTest {
    @Test fun texturesAreDeterministicTileableAndDistinct() {
        for (id in V56Textures.BUILTIN) {
            val t = V56Textures.tile(id)
            assertEquals(V56Textures.SIZE * V56Textures.SIZE, t.size)
            assertTrue(id, t.all { it in 0f..1f })
            assertTrue("$id boşluk içermeli", t.any { it < 0.5f } && t.any { it > 0.9f })
            // Döşenebilir: karo kenarından taşan nokta başa döner
            assertEquals(V56Textures.value(id, 5, 9), V56Textures.value(id, 5 + V56Textures.SIZE, 9 - V56Textures.SIZE), 0f)
            assertEquals(t[9 * V56Textures.SIZE + 5], V56Textures.value(id, 5, 9), 0f)
        }
        assertFalse(V56Textures.tile("builtin:paper").contentEquals(V56Textures.tile("builtin:charcoal")))
        // iOS V56TexturesTests ile aynı örnek değerler (bit bit aynı formül)
        assertEquals(1f, V56Textures.value("builtin:hatch", 0, 0), 0f)
        assertEquals(0.15f, V56Textures.value("builtin:hatch", 6, 0), 0f)
        assertEquals(1f, V56Textures.value("builtin:dots", 7, 7), 0f)
        assertEquals(0.15f, V56Textures.value("builtin:dots", 0, 0), 1e-6f)
        assertEquals(1f, V56Textures.value("bilinmeyen", 3, 3), 0f)
        SAMPLES.forEach { (id, x, y, v) -> assertEquals("$id $x,$y", v, V56Textures.value(id, x, y), 1e-6f) }
    }

    @Test fun grainAnchorsScaleAndStrength() {
        val sliding = GrainSpec("builtin:hatch")
        val fixed = sliding.copy(moving = false)
        // Kayar: doku tuvale sabit, çizginin başlangıcından bağımsız
        assertEquals(V56Textures.sample(sliding, 6f, 0f, 0f, 0f), V56Textures.sample(sliding, 6f, 0f, 50f, 50f), 0f)
        // Sabit değil: doku çizgiyle birlikte taşınır
        assertEquals(V56Textures.sample(fixed, 6f, 0f, 0f, 0f), V56Textures.sample(fixed, 56f, 50f, 50f, 50f), 0f)
        // Ölçek 2: karo iki kat büyür
        assertEquals(V56Textures.sample(sliding, 6f, 0f), V56Textures.sample(sliding.copy(scale = 2f), 12f, 0f), 0f)
        assertEquals(1f, V56Textures.alpha(0.2f, 0f), 0f)
        assertEquals(0.2f, V56Textures.alpha(0.2f, 1f), 1e-6f)
        assertEquals(0.6f, V56Textures.alpha(0.2f, 0.5f), 1e-6f)
    }

    @Test fun tuningCarriesGrainAndImageTip() {
        val t = BrushTuning(grain = GrainSpec("builtin:paper", scale = 2f), stampAssetId = "uc")
        assertEquals("builtin:paper", t.params(BrushType.PENCIL)?.grain?.textureId)
        assertEquals("uc", t.params(BrushType.PENCIL)?.stampAssetId)
        // Piksel fırçasında gren var, resim ucu yok; silgide ikisi de yok
        assertNotNull(t.params(BrushType.OIL_PAINT)?.grain)
        assertNull(t.params(BrushType.OIL_PAINT)?.stampAssetId)
        assertNull(t.params(BrushType.ERASER))
        assertNull(BrushTuning().params(BrushType.PENCIL))
        // Doku dolgusu kement dolgu elemanıdır ve V11 biçimi ister
        val fill = V56Selection.lassoFill("f", V56Textures.pageRect(100f, 50f), "#112233", 0.5f, 0, grain = GrainSpec("builtin:dots"))
        assertEquals(BrushType.LASSO_FILL, fill.brushType)
        assertEquals(4, fill.points.size)
        assertEquals("builtin:dots", fill.params?.grain?.textureId)
    }

    companion object {
        /** Gürültü dokularından örnekler (iOS testinde de aynı değerler). */
        val SAMPLES: List<Sample> = listOf(
            Sample("builtin:paper", 0, 0, 0.1244707f), Sample("builtin:paper", 13, 7, 1f), Sample("builtin:paper", 77, 101, 0.50449765f),
            Sample("builtin:canvas", 0, 0, 0.36003512f), Sample("builtin:canvas", 13, 7, 0.7779521f), Sample("builtin:canvas", 77, 101, 0.8011633f),
            Sample("builtin:charcoal", 0, 0, 0f), Sample("builtin:charcoal", 77, 101, 1f),
            Sample("builtin:noise", 0, 0, 0.32896167f), Sample("builtin:noise", 13, 7, 0.61222017f), Sample("builtin:noise", 77, 101, 0.17517483f),
        )
    }

    data class Sample(val id: String, val x: Int, val y: Int, val v: Float)
}

/** v56 aşama 5 çizim ve arayüz: grenli iz, resim ucu damgaları, Gren bölümü, Dokuyla doldur. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56GrainRenderTest {
    @After fun tearDown() = stopKoin()

    private fun square(params: BrushParams?) = Stroke(
        "s", listOf(StrokePoint(0f, 0f), StrokePoint(64f, 0f), StrokePoint(64f, 64f), StrokePoint(0f, 64f)),
        "#000000", BrushType.LASSO_FILL, 1f, 1f, 0, params = params,
    )

    private fun alphaAt(strokes: List<Stroke>, x: Int, y: Int): Int {
        val bmp = StrokeRenderer.renderViewportCache(strokes, 80, 80, 1f, Offset.Zero, Rect(0f, 0f, 80f, 80f))
        return android.graphics.Color.alpha(bmp.asAndroidBitmap().getPixel(x, y))
    }

    @Test
    fun grainMultipliesFillWithTexture() {
        val plain = listOf(square(null))
        val hatch = listOf(square(BrushParams(grain = GrainSpec("builtin:hatch"))))
        assertEquals(255, alphaAt(plain, 6, 1))
        // Tarama: (x + y) % 12 < 3 dolu, aksi %15
        assertTrue("çizgi üstü dolu: ${alphaAt(hatch, 1, 0)}", alphaAt(hatch, 1, 0) > 200)
        val gap = alphaAt(hatch, 7, 0)
        assertEquals("boşluk %15", 0.15 * 255, gap.toDouble(), 12.0)
        // Güç yarıya inince boşluk dolar
        val half = alphaAt(listOf(square(BrushParams(grain = GrainSpec("builtin:hatch", strength = 0.5f)))), 7, 0)
        assertTrue("güç 0.5: $half", half in 130..170)
        // Kayar değilken doku çizginin ilk noktasına bağlı (ilk nokta 0,0: aynı yer)
        val fixed = alphaAt(listOf(square(BrushParams(grain = GrainSpec("builtin:hatch", moving = false)))), 7, 0)
        assertEquals(gap.toDouble(), fixed.toDouble(), 2.0)
    }

    @Test
    fun imageTipStampsAlongPath() {
        // Uç: sol yarısı dolu, sağ yarısı boş 20×20 resim
        val src = android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
        src.eraseColor(android.graphics.Color.WHITE)
        for (y in 0 until 20) for (x in 0 until 10) src.setPixel(x, y, android.graphics.Color.BLACK)
        val tip = GrainRenderer.tipFrom(src)
        // Boş (beyaz) sağ yarı kırpılır; koyu yer boya
        assertEquals(10, tip.width)
        assertEquals(255, android.graphics.Color.alpha(tip.getPixel(2, 2)))
        CanvasImageCache.put("v56-tip", tip.asImageBitmap())
        val stroke = Stroke(
            "i", listOf(StrokePoint(10f, 40f), StrokePoint(40f, 40f), StrokePoint(70f, 40f)), "#FF0000", BrushType.MARKER, 20f, 1f, 0,
            params = BrushParams(stampAssetId = "v56-tip"),
        )
        val bmp = StrokeRenderer.renderViewportCache(listOf(stroke), 80, 80, 1f, Offset.Zero, Rect(0f, 0f, 80f, 80f)).asAndroidBitmap()
        val c = bmp.getPixel(40, 40)
        assertTrue("damga boyalı", android.graphics.Color.alpha(c) > 200)
        assertEquals("fırça rengine boyanır", 255, android.graphics.Color.red(c))
        assertEquals(0, android.graphics.Color.green(c))
        // Uç 20 px genişliğinde, yüksekliği oranında (2:1 → 40): y = 40 ± 20 içinde, uzağı boş
        assertEquals(0, android.graphics.Color.alpha(bmp.getPixel(40, 75)))
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V56GrainUiTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openKind(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    @Test
    fun grainSettingsGoIntoNewStrokes() {
        openKind("Gren", CanvasKind.ART)
        compose.runOnIdle {
            vm().setMode(EditMode.DRAW)
            vm().selectBrush(BrushType.PENCIL)
            vm().setGrainTexture("builtin:canvas")
            vm().setGrain { it.copy(scale = 99f, strength = 0.4f, moving = false) }
        }
        val g = vm().brushSettings.value.tuning.grain
        assertNotNull(g)
        assertEquals("ölçek sınırlanır", V56Textures.MAX_SCALE, g!!.scale, 0f)
        assertEquals(0.4f, g.strength, 1e-6f)
        assertEquals("builtin:canvas", vm().brushSettings.value.params?.grain?.textureId)
        // Gren kapatılır
        compose.runOnIdle { vm().setGrainTexture(null) }
        assertNull(vm().brushSettings.value.tuning.grain)
        // Not almada gren yok
        assertFalse(com.karapp.drawguidance.data.model.V56Flags.enabled(com.karapp.drawguidance.data.model.V56Flags.Feature.GRAIN, CanvasKind.NOTES))
    }

    @Test
    fun textureFillAreaAndPageAreOneUndoStep() {
        openKind("Doku", CanvasKind.ART)
        compose.runOnIdle {
            vm().setMode(EditMode.DRAW)
            vm().updateBrushColor("#336699")
            assertTrue(vm().textureFill("builtin:paper"))
        }
        waitFor { vm().canvas.value?.strokes?.any { it.brushType == BrushType.LASSO_FILL } == true }
        val fill = vm().canvas.value!!.strokes.last()
        assertEquals("builtin:paper", fill.params?.grain?.textureId)
        assertEquals("seçim yokken bütün sayfa", 1000f, fill.points.maxOf { it.x }, 0f)
        assertEquals("#336699", fill.colorHex.uppercase())
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value?.strokes?.none { it.brushType == BrushType.LASSO_FILL } == true }
        // Ekle menüsünde Doku düğmesi; doku seçicisi açılır ve seçilen doku sayfayı doldurur
        mode("Ekle")
        waitFor { exists("tool_row_context") }
        val doku = androidx.compose.ui.test.hasContentDescription("Doku") and androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag("tool_row_context"))
        waitFor { compose.onAllNodes(doku).fetchSemanticsNodes().isNotEmpty() }
        runCatching { compose.onNode(doku).performScrollTo() }
        compose.onNode(doku).performClick()
        waitFor { exists("v56_texture_picker") }
        compose.onNodeWithTag("v56_texture_dots").performClick()
        waitFor { vm().canvas.value?.strokes?.lastOrNull()?.params?.grain?.textureId == "builtin:dots" }
    }
}
