package com.karapp.drawguidance

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v28 görsel kontrol: menüler ve düğmeler (açık ve koyu tema). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V28NeuShotTest : V27EditorBase() {
    @Test
    fun menusAndButtons() {
        createCanvasAndOpen("Görünüm")
        shotAll("v28-01-editor")
        mode("Ekle")
        shotAll("v28-02-add-row")
    }

    @Test
    fun diagramPicker() {
        // Diyagramlar not tuvalinde
        createNotesCanvasAndOpen("Diyagram")
        addMenu("Diyagram ekle")
        waitFor { compose.onAllNodes(androidx.compose.ui.test.hasText("Diyagram", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shotAll("v28-03-diagram-picker")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-night-xxhdpi")
class V28NeuDarkShotTest : V27EditorBase() {
    @Test
    fun menusAndButtonsDark() {
        createCanvasAndOpen("Görünüm")
        shotAll("v28-04-editor-dark")
        mode("Ekle")
        shotAll("v28-05-add-row-dark")
    }
}
