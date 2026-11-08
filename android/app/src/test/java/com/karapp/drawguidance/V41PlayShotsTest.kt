package com.karapp.drawguidance

import androidx.activity.compose.setContent
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialScripts
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v41 Play mağazası ekran görüntüleri (DG_SHOTS verilince): her mod için eğitimin çizdiği örnek sayfa (manzara, ders
 * notu, çizgi roman sayfası, çocuk resmi, basit resim, basit not) gerçek bir tuval olarak kaydedilip eğitim kutusu
 * olmadan açılır; 3B düzenleyici ve abonelik sayfası. Telefon görüntüleri 1233×2193 px (9:16; Play sınırları içinde).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w411dp-h731dp-xxhdpi")
class V41PlayShotsTest : V32EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun open(name: String, kind: CanvasKind, width: Int = 1000, height: Int = 1414) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, width, height, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    /** Eğitim grubunu sonuna kadar oynatır; son adımın çizimini döndürür (eğitim bitince tuval eski haline döner). */
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

    /** Eğitimin çizimi tuvale yazılır ve tuval yeniden açılır; ekran görüntüsü alınır. */
    private fun keepAndShoot(name: String, snap: CanvasUi, shotName: String) {
        SystemBack.press()
        waitFor { !exists("drawing_canvas") }
        runBlocking {
            repo().saveDocument(snap.localId, snap.pages, snap.width, snap.height, true, snap.backgroundColorHex, snap.currentPage, snap.layers)
            repo().saveBackgroundSpec(snap.localId, snap.background, snap.pageBackgrounds)
        }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.strokes?.isNotEmpty() == true }
        settleFor(1200)
        shot(shotName)
    }

    private fun mode(kind: CanvasKind, name: String, groupId: String, shotName: String) {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open(name, kind)
        val snap = play(kind, groupId)
        keepAndShoot(name, snap, shotName)
    }

    @Test fun art() = mode(CanvasKind.ART, "Gün batımı", "art_basics", "play-01-resim")
    @Test fun notes() = mode(CanvasKind.NOTES, "Fizik notu", "notes_formula", "play-02-not")
    @Test fun comic() = mode(CanvasKind.COMIC, "Kahraman", "comic_panels", "play-03-cizgi-roman")
    @Test fun kids() = mode(CanvasKind.KIDS, "Boyacık", "kids_intro", "play-04-cocuk")
    @Test fun simpleArt() = mode(CanvasKind.SIMPLE_ART, "Ev", "simple_art_intro", "play-05-basit-resim")
    @Test fun simpleNotes() = mode(CanvasKind.SIMPLE_NOTES, "Liste", "simple_notes_intro", "play-06-basit-not")

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
        shot("play-07-3b")
    }

    @Test
    fun subscription() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        TestAccount.entitlements.value = null
        val repo = GlobalContext.get().get<com.karapp.drawguidance.data.billing.SubscriptionRepository>()
        runBlocking { repo.seed() }
        val subVm = com.karapp.drawguidance.ui.screens.subscription.SubscriptionViewModel(repo)
        compose.runOnUiThread {
            compose.activity.setContent {
                com.karapp.drawguidance.ui.theme.DrawGuidanceTheme {
                    com.karapp.drawguidance.ui.screens.subscription.SubscriptionScreen(onBack = {}, vm = subVm)
                }
            }
        }
        waitFor { exists("plan_premium") }
        settleFor(400)
        shot("play-08-abonelik")
        TestAccount.reset()
    }
}
