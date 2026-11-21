package com.karapp.drawguidance

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Mesh
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.Part
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.Transform3
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.images.ImagesRepository
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.platform.DeviceCaps
import com.karapp.drawguidance.ui.screens.model3d.Budget3D
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** v25 (3): tuvalde Ekle menüsünde ayrı "3B şekil ekle": pencerede şekil seçilir, alttaki "3B şekil ekle" düğmesi ekler. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V25CanvasAdd3DTest : V23EditorBase() {
    @Test
    fun addMenuSelectThenAdd() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        createCanvasAndOpen("3B ekle")
        if (compose.onAllNodesWithContentDescription("Ekle menüsü").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Ekle menüsü").performClick()
            waitForText("3B şekil ekle")
            settleFor(300)
            shotAll("v25-08-add-menu")
            compose.onNodeWithText("3B şekil ekle").clickInPopup()
        } else addMenu("3B şekil ekle")
        waitFor { exists("model3d_picker") }
        // Seçmeden düğme pasif
        compose.onNodeWithTag("model3d_add_button").assertIsNotEnabled()
        assertTrue(compose.onAllNodesWithText("Henüz şekil seçilmedi").fetchSemanticsNodes().isNotEmpty())
        // Hazır şekillerden biri seçilir (dokunmak eklemez)
        val item = com.karapp.drawguidance.content.model3d.Catalog3D.all.first { it.category == com.karapp.drawguidance.content.model3d.Catalog3D.categories.first() }
        compose.onNode(hasContentDescription(item.name) and hasAnyAncestor(hasTestTag("model3d_catalog"))).performScrollTo().performClick()
        waitForText("Seçili: ${item.name}")
        settleFor(400)
        assertTrue(canvas().strokes.none { it.model != null })
        compose.onNodeWithTag("model3d_add_button").assertIsEnabled()
        shotAll("v25-09-picker-selected")
        compose.onNodeWithTag("model3d_add_button").performClick()
        // v56 aşama 10: açı adımı
        waitFor { exists("model3d_angle") }
        compose.onNodeWithTag("model3d_angle_confirm").performClick()
        waitFor(30_000) { canvas().strokes.any { it.model != null } }
        waitFor { !exists("model3d_picker") && !exists("model3d_angle") }
    }
}
