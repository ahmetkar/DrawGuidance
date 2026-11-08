package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.HarmonySpec
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.scene.HarmonyRenderer
import com.karapp.drawguidance.ui.drawing.scene.SceneAnalyzer
import com.karapp.drawguidance.ui.drawing.scene.ScenePlacement
import com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ElementOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * v43 (6) Arkaplan ile Nesneyi Uyumla ve (7) Nesneyi Mekâna Yerleştir: cihazda çözümleme (ışık yönü, renk, zemin,
 * ufuk/kaçış noktası), yıkıcı olmayan çizim, önizleme → Uygula/İptal, geri al/yinele, elle düzeltme.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V43SceneTest : V33EditorBase() {

    private fun solid(w: Int, h: Int, color: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    /** Saydam zeminde dolu daire (nesne). */
    private fun blob(size: Int, color: Int): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        Canvas(this).drawCircle(size / 2f, size / 2f, size * 0.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    // ------------------------------------------------------------------ çözümleme ve çizim

    @Test
    fun analysisFindsLightColourAndGround() {
        // Arka plan: solu parlak, sağı koyu, sıcak (turuncu); alt yarıda koyu zemin (yatay kenar)
        val w = 200; val h = 200
        val bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            val k = 1f - x / w.toFloat()
            val base = if (y >= 150) 0.35f else 0.55f + 0.4f * k
            bg.setPixel(x, y, android.graphics.Color.rgb((255 * base).toInt(), (170 * base).toInt(), (90 * base).toInt()))
        }
        val obj = blob(100, 0xFF3060E0.toInt())
        val region = Rect(0f, 0f, 200f, 200f)
        val objBounds = Rect(70f, 70f, 130f, 150f)
        val a = SceneAnalyzer.analyze(bg, region, objBounds, obj)
        assertTrue("ışık soldan: ${a.light}", a.light.x < -0.3f)
        assertNotNull("zemin çizgisi", a.groundY)
        assertEquals(150f, a.groundY!!, 6f)
        val spec = SceneAnalyzer.suggest(a, SceneAnalyzer.Options())
        // Mavi nesne sıcak sahneye: kırmızı artar, mavi azalır; gölge sağa yatar
        assertTrue(spec.gainR > 1f && spec.gainB < 1f)
        assertTrue(spec.castSkew < 0f)
        assertTrue(spec.shadowOpacity in 0.15f..0.55f)
        val off = SceneAnalyzer.suggest(a, SceneAnalyzer.Options(shadow = false, color = false, edge = false))
        assertFalse(off.shadow || off.color || off.edge)
    }

    @Test
    fun rendererIsNonDestructiveAndCached() {
        val src = blob(64, 0xFF808080.toInt())
        val copy = src.copy(Bitmap.Config.ARGB_8888, false)
        val warm = HarmonySpec(gainR = 1.3f, gainG = 1f, gainB = 0.75f, lift = 0f, saturation = 1f, edge = false, shadow = false)
        val out = HarmonyRenderer.process(src, warm)
        val c = out.getPixel(32, 32)
        assertTrue(android.graphics.Color.red(c) > android.graphics.Color.blue(c) + 40)
        assertTrue("özgün değişmez", src.sameAs(copy))
        // Yoğunluk 0: değişiklik yok
        val none = HarmonyRenderer.process(src, warm.copy(intensity = 0f))
        assertEquals(src.getPixel(32, 32), none.getPixel(32, 32))
        // Kenar yumuşatma kenardaki alfayı düşürür, içi aynı kalır
        val soft = HarmonyRenderer.process(src, HarmonySpec(color = false, shadow = false, edge = true, feather = 0.06f, wrap = 0f))
        assertTrue(android.graphics.Color.alpha(soft.getPixel(32, 32)) > 250)
        val edgeX = 32 + (64 * 0.4f).toInt() - 1
        assertTrue(android.graphics.Color.alpha(soft.getPixel(edgeX, 32)) < android.graphics.Color.alpha(src.getPixel(edgeX, 32)))
        // Ayak çizgisi: dairenin alt ucu
        assertEquals(32f + 64 * 0.4f, HarmonyRenderer.footY(src), 2f)
        // Önbellek: aynı ayarlar aynı nesne
        val p1 = HarmonyRenderer.processed(src, "k", warm)
        assertTrue(p1 === HarmonyRenderer.processed(src, "k", warm))
        // Gölgeli çizim: nesnenin altına koyu iz düşer
        val target = solid(200, 200, 0xFFFFFFFF.toInt())
        val m = android.graphics.Matrix().apply { setTranslate(68f, 40f) }
        HarmonyRenderer.draw(Canvas(target), src, "k2", HarmonySpec(color = false, edge = false, shadow = true, shadowOpacity = 0.6f, castSkew = -0.8f), m, 255)
        val under = target.getPixel(100, 40 + (32 + 64 * 0.4f).toInt() + 1)
        assertTrue("temas gölgesi", android.graphics.Color.red(under) < 245)
    }

    @Test
    fun perspectiveFindsVanishingPointOrFallsBack() {
        val w = 320; val h = 240
        // Kaçış noktası (160, 84) = %35: oraya giden yol kenarları ve zemin çizgileri
        val bmp = solid(w, h, 0xFFFFFFFF.toInt())
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt(); strokeWidth = 3f }
        for (x in listOf(-200f, 0f, 80f, 240f, 320f, 520f)) cv.drawLine(160f, 84f, x, 240f, p)
        for (y in listOf(0f, 40f)) cv.drawLine(160f, 84f, if (y == 0f) 0f else 320f, y, p)
        val r = SceneAnalyzer.perspective(bmp, 1000f, 750f)
        assertNotNull("kaçış noktası", r.vanishing)
        assertEquals(84f / 240f * 750f, r.horizonY, 40f)
        assertTrue(r.confidence >= 0.5f)
        // Düz renk: varsayılan göz hizası, düşük güven
        val blank = SceneAnalyzer.perspective(solid(w, h, 0xFF7799BB.toInt()), 1000f, 750f)
        assertNull(blank.vanishing)
        assertEquals(300f, blank.horizonY, 1f)
        assertEquals(0.2f, blank.confidence, 1e-3f)
        // Yalnız uzun yatay çizgi (deniz ufku)
        val sea = solid(w, h, 0xFFFFFFFF.toInt())
        Canvas(sea).drawRect(0f, 100f, w.toFloat(), h.toFloat(), Paint().apply { color = 0xFF204080.toInt() })
        val s = SceneAnalyzer.perspective(sea, 1000f, 750f)
        assertEquals(100f / 240f * 750f, s.horizonY, 15f)
    }

    @Test
    fun placementScalesByDistanceToHorizon() {
        // Ufkun üstünde (havada) nesne zemine iner
        val r = ScenePlacement.place(Rect(400f, 100f, 500f, 300f), horizonY = 400f, canvasH = 1000f, refs = emptyList())
        assertTrue(r.footY > 400f)
        assertEquals(400f + 0.35f * 600f, r.footY, 1f)
        // Referans: ayağı 800'de boyu 200 olan nesne → k = 200/400 = 0.5; ayak 600'de → boy 100
        val ref = ScenePlacement.place(Rect(100f, 400f, 200f, 600f), 400f, 1000f, listOf(ScenePlacement.Reference(800f, 200f)))
        assertEquals(1, ref.references)
        assertEquals(0.5f, ref.k, 1e-3f)
        assertEquals(100f / 200f, ref.scale, 1e-3f)
        // Uzaklaştıkça küçülür (elle ayak hizası), ölçek sınırlı
        val far = ScenePlacement.place(Rect(100f, 400f, 200f, 600f), 400f, 1000f, emptyList(), footOverride = 450f)
        assertTrue(far.scale < 1f && far.scale >= ScenePlacement.MIN_SCALE)
        val big = ScenePlacement.place(Rect(100f, 590f, 110f, 600f), 400f, 1000f, emptyList(), footOverride = 1000f, scaleMul = 2f)
        assertTrue(big.scale <= ScenePlacement.MAX_SCALE)
    }

    // ------------------------------------------------------------------ düzenleyicide

    private fun openWithObject(kind: CanvasKind): CanvasEditViewModel {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { GlobalContext.get().get<CanvasRepository>().createCanvas("Sahne", 1000, 1000, "#CCDDEE", aiEnabled = false, kind = kind) }
        waitForText("Sahne")
        compose.onAllNodesWithText("Sahne")[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == "Sahne" }
        val f = File(ApplicationProviderHolder.context().cacheDir, "nesne.png")
        f.outputStream().use { blob(200, 0xFF2050C0.toInt()).compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnIdle { vm().insertPackImage(f, 200, 200) }
        waitFor(15_000) { vm().canvas.value!!.strokes.any { it.brushType == BrushType.IMAGE } }
        val img = vm().canvas.value!!.strokes.first { it.brushType == BrushType.IMAGE }
        waitFor(15_000) { com.karapp.drawguidance.data.images.CanvasImageCache.get(img.assetId) != null }
        compose.runOnIdle { vm().finishAction(); vm().clearSelection(); vm().toggleElement(img.id) }
        waitFor { vm().selectedIds.value.isNotEmpty() }
        return vm()
    }

    @Test
    fun harmonizePreviewsAppliesUndoesAndRemoves() {
        val vm = openWithObject(CanvasKind.ART)
        val id = vm.canvas.value!!.strokes.first { it.brushType == BrushType.IMAGE }.id
        assertTrue(vm.sceneToolsAvailable() && vm.hasSceneTarget())
        compose.runOnIdle { assertTrue(vm.openHarmony()) }
        waitFor(15_000) { vm.harmonyDraft.value?.busy == false }
        assertNotNull(vm.harmonyDraft.value!!.analysis)
        waitFor { exists("harmonize_sheet") }
        // Önizleme: tuvalde uyum var, geçmişe girmedi
        assertNotNull(vm.canvas.value!!.strokes.first { it.id == id }.harmony)
        // İptal: özgün
        compose.runOnIdle { vm.cancelHarmony() }
        assertNull(vm.canvas.value!!.strokes.first { it.id == id }.harmony)
        // Seçenekler + Uygula (seçim korunur)
        assertTrue(vm.hasSceneTarget())
        compose.runOnIdle { vm.openHarmony() }
        waitFor(15_000) { vm.harmonyDraft.value?.busy == false }
        compose.runOnIdle { vm.setHarmonyOptions(SceneAnalyzer.Options(shadow = true, color = false, edge = true, intensity = 0.5f)) }
        compose.runOnIdle { vm.applyHarmony() }
        val applied = vm.canvas.value!!.strokes.first { it.id == id }.harmony
        assertNotNull(applied)
        assertFalse(applied!!.color)
        assertEquals(0.5f, applied.intensity, 1e-4f)
        assertNull(vm.harmonyDraft.value)
        // Geri al / yinele
        compose.runOnIdle { vm.undo() }
        assertNull(vm.canvas.value!!.strokes.first { it.id == id }.harmony)
        compose.runOnIdle { vm.redo() }
        assertNotNull(vm.canvas.value!!.strokes.first { it.id == id }.harmony)
        // Kaldır
        compose.runOnIdle { if (vm.selectedIds.value.isEmpty()) vm.toggleElement(id); vm.openHarmony() }
        waitFor(15_000) { vm.harmonyDraft.value?.busy == false }
        assertTrue(vm.harmonyDraft.value!!.hadHarmony)
        compose.runOnIdle { vm.removeHarmony() }
        assertNull(vm.canvas.value!!.strokes.first { it.id == id }.harmony)
    }

    @Test
    fun placeInSceneMovesAndScalesWithManualFallback() {
        val vm = openWithObject(CanvasKind.COMIC)
        val before = vm.canvas.value!!.strokes.first { it.brushType == BrushType.IMAGE }
        val b0 = ElementOps.selectionBounds(listOf(before), setOf(before.id))!!
        compose.runOnIdle { assertTrue(vm.openPlace()) }
        waitFor(15_000) { vm.placeDraft.value?.busy == false }
        waitFor { exists("place_sheet") }
        val d = vm.placeDraft.value!!
        // Düz renk kağıt: varsayılan ufuk (%40), düşük güven
        assertEquals(400f, d.horizonY, 1f)
        // Elle: ufuk %30, ayak %90 → nesne ayakları 900'de, boy büyür; oran korunur
        compose.runOnIdle { vm.setPlaceManual(horizonY = 300f, footY = 900f) }
        val moved = vm.canvas.value!!.strokes.first { it.id == before.id }
        val b1 = ElementOps.selectionBounds(listOf(moved), setOf(moved.id))!!
        assertEquals(900f, b1.bottom, 2f)
        assertNotEquals(b0.height, b1.height, 1f)
        assertEquals(b0.width / b0.height, b1.width / b1.height, 0.02f)
        compose.runOnIdle { vm.applyPlace() }
        val placed = ElementOps.selectionBounds(vm.canvas.value!!.strokes.filter { it.id == before.id }, setOf(before.id))!!
        assertEquals(900f, placed.bottom, 2f)
        compose.runOnIdle { vm.undo() }
        val back = ElementOps.selectionBounds(vm.canvas.value!!.strokes.filter { it.id == before.id }, setOf(before.id))!!
        assertEquals(b0.bottom, back.bottom, 1f)
        assertTrue(abs(back.height - b0.height) < 1f)
    }
}

private object ApplicationProviderHolder {
    fun context(): android.content.Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
}
