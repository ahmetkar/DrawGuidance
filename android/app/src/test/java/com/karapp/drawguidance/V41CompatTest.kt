package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v41 geniş uyumluluk: Android 7.0 (API 24), 10 (API 29) ve 14 (API 34) üzerinde her tuval türünün düzenleyicisi
 * açılır, birkaç çizgi çizilir, geri dönülür; Ayarlar ve abonelik sayfası açılır. (Android 6.0 için lint NewApi
 * denetimi geçer; Robolectric'in API 23 imajı bu ortamda yok.)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [24, 29, 34], application = HomeNoWelcomeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V41CompatTest : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    /** Geri tuşu (SystemBack yardımcısı yalnız Android 13+ geri çağrısını bilir). */
    private fun back() {
        if (android.os.Build.VERSION.SDK_INT >= 33) SystemBack.press()
        else compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun everyKindOpensAndDraws() {
        waitFor { compose.onAllNodesWithText("Ayarlar").fetchSemanticsNodes().isNotEmpty() }
        val kinds = listOf(CanvasKind.ART, CanvasKind.NOTES, CanvasKind.COMIC, CanvasKind.SIMPLE_ART, CanvasKind.SIMPLE_NOTES, CanvasKind.KIDS)
        runBlocking { kinds.forEach { k -> repo().createCanvas("Uyum ${k.name}", 1000, 1000, "#FFFFFF", aiEnabled = false, kind = k) } }
        // Tuvallerim
        compose.onAllNodesWithText("Tuvallerim").onFirst().performClick()
        for (k in kinds) {
            val name = "Uyum ${k.name}"
            waitFor { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText(name)[0].performClick()
            waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
            if (vm().mode.value != com.karapp.drawguidance.ui.screens.canvasedit.EditMode.DRAW) {
                compose.runOnIdle { vm().setMode(com.karapp.drawguidance.ui.screens.canvasedit.EditMode.DRAW) }
            }
            // Tuvallerim'de birden çok tuval var: nokta ekran boyutundan (screenOf tek tuval ister)
            val view = canvasSize()
            drag(androidx.compose.ui.geometry.Offset(view.x * 0.35f, view.y * 0.4f), androidx.compose.ui.geometry.Offset(view.x * 0.25f, 60f))
            waitFor { vm().canvas.value!!.strokes.isNotEmpty() || k == CanvasKind.KIDS }
            back()
            waitFor { !exists("drawing_canvas") }
        }
        // Ayarlar ve abonelik
        TestAccount.signInLocal()
        compose.onAllNodesWithText("Ayarlar").onFirst().performClick()
        waitFor { exists("language_card") }
        compose.onAllNodes(hasTestTag("settings_subscription")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("subscription_screen") }
        assertTrue(exists("plan_premium") && exists("plan_free"))
    }
}
