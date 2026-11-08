package com.karapp.drawguidance

import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.VariantGroup
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.drawing.TailDirection
import com.karapp.drawguidance.ui.screens.canvasedit.state.BalloonTextOptions
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialScripts
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v44 Google Play ekran görüntüleri (yalnız DG_SHOTS verilince): telefon, 7" ve 10" tablet. Çizimler uygulamanın kendi
 * hazır içeriğiyle (özgün karakter, mekân ve efekt resimleri, panel şablonları, balonlar) ve eğitimlerin çizdiği
 * örnek sayfalarla kurulur; internetten resim ya da başka eserlerin karakterleri kullanılmaz.
 *
 * - Telefon: 1233×2193 px (w411dp-h731dp, xxhdpi).
 * - 7" tablet: 1200×1920 px (w600dp-h960dp, xhdpi).
 * - 10" tablet: 1600×2560 px (w800dp-h1280dp, xhdpi).
 */
abstract class V44PlayShotsBase : V32EditorBase() {
    /** Dosya adlarının ön eki (telefon, tablet7, tablet10). */
    protected abstract val device: String

    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind, width: Int = 1000, height: Int = 1414) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, width, height, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun images() = vm().canvas.value!!.strokes.filter { it.brushType == BrushType.IMAGE }

    private fun strokeBounds(id: String): Rect {
        val s = vm().canvas.value!!.strokes.first { it.id == id }
        return Rect(s.points.minOf { it.x }, s.points.minOf { it.y }, s.points.maxOf { it.x }, s.points.maxOf { it.y })
    }

    /** Karakteri [panel] içine, alt kenarına basacak ve panel yüksekliğinin [height] payı kadar olacak şekilde koyar. */
    private fun character(id: String, group: VariantGroup, panel: Rect, x: Float, height: Float) {
        val c = ComicAssets.catalog.characters.first { it.id == id }
        val variant = c.variants(group).firstOrNull() ?: c.cover
        val before = images().size
        compose.runOnIdle { vm().addHere("characters", Offset(x, panel.center.y)); vm().insertCharacter(variant) }
        waitFor(20_000) { images().size == before + 1 }
        val img = images().last()
        fit(img.id, panel, x, height)
    }

    private fun fit(id: String, panel: Rect, x: Float, height: Float) {
        compose.runOnIdle {
            val b0 = strokeBounds(id)
            vm().selectOnlyAt(b0.center.x, b0.center.y, 4f)
        }
        compose.runOnIdle {
            val b0 = strokeBounds(id)
            val f = panel.height * height / b0.height
            vm().scaleSelected(f, f, b0.center)
        }
        compose.runOnIdle {
            val b = strokeBounds(id)
            vm().moveSelectionTo(x - b.width / 2f, panel.bottom - b.height - panel.height * 0.01f)
        }
        compose.waitForIdle()
    }

    private fun backdrop(id: String, leaf: Int?) {
        val b = ComicAssets.catalog.backgrounds.first { it.id == id }
        val before = images().size
        compose.runOnIdle { vm().insertLocationBackground(b, leaf) }
        waitFor(20_000) { images().size == before + 1 }
    }

    private fun balloon(at: Offset, text: String, style: BalloonStyle = BalloonStyle.SPEECH, tail: TailDirection = TailDirection.BOTTOM_LEFT) {
        val before = vm().canvas.value!!.strokes.size
        compose.runOnIdle {
            vm().addHere("balloon", at)
            vm().insertBalloon(style, 1, BalloonTextOptions(content = text, tail = tail))
        }
        waitFor { vm().canvas.value!!.strokes.size > before }
    }

    private fun clean() {
        compose.runOnIdle { vm().clearSelection() }
        // Taşı/Ölçekle eylemi ve Seç ipucu kapanır: görüntüde yalnız çizim modu
        if (vm().mode.value.kind != com.karapp.drawguidance.ui.screens.canvasedit.ModeKind.BASE) compose.runOnIdle { vm().finishAction() }
        compose.runOnIdle { vm().chooseMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.DRAW) }
        settleFor(1500)
    }

    /** Eğitim grubunu sonuna kadar oynatır; son adımın çizimini döndürür. */
    private fun play(kind: CanvasKind, groupId: String): CanvasUi {
        val group = TutorialScripts.groups(kind).first { it.id == groupId }
        compose.runOnIdle { vm().tutorial.fast = true; vm().tutorial.start(group) }
        val kids = kind == CanvasKind.KIDS
        val shown = { if (kids) exists("kids_tutorial") else calloutShown() }
        waitFor { shown() }
        var snapshot: CanvasUi? = null
        for (i in 1..group.steps.size) {
            runUntil(90_000) { vm().tutorial.ui.value?.index == i - 1 }
            awaitDemo(90_000)
            advanceMain(300)
            if (i == group.steps.size) snapshot = live()
            if (kids) {
                compose.onNodeWithTag("mascot_next", useUnmergedTree = true).performClick()
                compose.waitForIdle()
            } else next()
        }
        waitFor { !shown() }
        return snapshot!!
    }

    private fun tutorialShot(kind: CanvasKind, name: String, groupId: String, shotName: String) {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open(name, kind)
        val snap = play(kind, groupId)
        SystemBack.press()
        waitFor { !exists("drawing_canvas") }
        runBlocking {
            repo().saveDocument(snap.localId, snap.pages, snap.width, snap.height, true, snap.backgroundColorHex, snap.currentPage, snap.layers)
            repo().saveBackgroundSpec(snap.localId, snap.background, snap.pageBackgrounds)
        }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.strokes?.isNotEmpty() == true }
        settleFor(1500)
        shot("$device-$shotName")
    }

    /** Çizgi roman sayfası: üç panelli şablon, mekânlar, karakterler, balonlar ve ses efekti. */
    @Test
    fun comicPage() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open("Gece Devriyesi", CanvasKind.COMIC)
        compose.runOnIdle { vm().insertTemplate("three_big") }
        waitFor { vm().comicPanelRects().size == 3 }
        val panels = vm().comicPanelRects().entries.sortedWith(compareBy({ it.value.top }, { it.value.left }))
        val (top, left, right) = Triple(panels[0], panels[1], panels[2])
        backdrop("gunbatimi_cadde", top.key)
        backdrop("neon_cadde", left.key)
        backdrop("cati_teras", right.key)
        val t = top.value
        character("kilicci_doruk", VariantGroup.POSE, t, t.left + t.width * 0.28f, 0.86f)
        character("buyucu_leyla", VariantGroup.POSE, t, t.left + t.width * 0.72f, 0.82f)
        character("dedektif_selin", VariantGroup.EXPRESSION, left.value, left.value.center.x, 0.78f)
        character("hacker_ozan", VariantGroup.EXPRESSION, right.value, right.value.center.x, 0.78f)
        balloon(Offset(t.left + t.width * 0.30f, t.top + t.height * 0.13f), "Şehir bu gece bizim!", tail = TailDirection.BOTTOM_RIGHT)
        balloon(Offset(t.left + t.width * 0.74f, t.top + t.height * 0.16f), "Önce planı dinle.")
        balloon(Offset(left.value.center.x, left.value.top + left.value.height * 0.15f), "Bir ipucu buldum…", BalloonStyle.THOUGHT)
        balloon(Offset(right.value.center.x, right.value.top + right.value.height * 0.15f), "Sistem açıldı!", tail = TailDirection.BOTTOM_RIGHT)
        compose.runOnIdle { vm().addHere("sfx", Offset(t.left + t.width * 0.5f, t.top + t.height * 0.45f)); vm().insertSfx(com.karapp.drawguidance.content.comic.ComicSfx.presets.first()) }
        clean()
        shot("$device-01-cizgi-roman")
    }

    /** Resim modu: tam sayfa resim (mekân) üstünde karakter. */
    @Test
    fun artScene() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open("Sahil Macerası", CanvasKind.ART)
        backdrop("tropik_sahil", null)
        val page = Rect(0f, 0f, 1000f, 1414f)
        character("kasif_cem", VariantGroup.POSE, page, 640f, 0.56f)
        clean()
        shot("$device-02-resim")
    }

    @Test fun notes() = tutorialShot(CanvasKind.NOTES, "Fizik notu", "notes_formula", "03-not")
    @Test fun kids() = tutorialShot(CanvasKind.KIDS, "Boyacık", "kids_intro", "04-cocuk")
    @Test fun simpleArt() = tutorialShot(CanvasKind.SIMPLE_ART, "Ev", "simple_art_intro", "05-basit-resim")

    @Test
    fun model3d() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val k = GlobalContext.get()
        val vm = com.karapp.drawguidance.ui.screens.model3d.Model3DEditorViewModel(k.get(), k.get(), k.get(), null, null, null, k.get(), images = k.get())
        compose.runOnUiThread {
            compose.activity.setContent {
                com.karapp.drawguidance.ui.theme.DrawGuidanceTheme(darkTheme = false) {
                    com.karapp.drawguidance.ui.screens.model3d.Model3DEditorScreen(null, null, null, onBack = {}, vm = vm)
                }
            }
        }
        waitFor { exists("model3d_editor") && vm.currentView() != null }
        compose.runOnIdle { vm.addPart(com.karapp.drawguidance.content.model3d.PrimitiveKind.SPHERE) }
        settleFor(600)
        compose.runOnIdle { vm.addPart(com.karapp.drawguidance.content.model3d.PrimitiveKind.CYLINDER) }
        waitFor(90_000) { vm.busy.value == null }
        settleFor(1200)
        shot("$device-06-3b")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w411dp-h731dp-xxhdpi")
class V44PlayPhoneShotsTest : V44PlayShotsBase() { override val device = "telefon" }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w600dp-h960dp-xhdpi")
class V44PlayTablet7ShotsTest : V44PlayShotsBase() { override val device = "tablet7" }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w800dp-h1280dp-xhdpi")
class V44PlayTablet10ShotsTest : V44PlayShotsBase() { override val device = "tablet10" }
