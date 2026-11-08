package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tuval açma ve üst menü düğmelerinin adlarını okuma (v38 yatay/dikey testleri). */
abstract class V38LabelsBase : V33EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    protected fun openKind(name: String, kind: CanvasKind) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { repo().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    /** [row] satırındaki düğmelerin adları (erişilebilirlik adı olan düğmeler). */
    protected fun buttons(row: String): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription) and hasAnyAncestor(hasTestTag(row)))
            .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }

    /** Altında adı yazan düğmeler. */
    protected fun labelled(): Set<String> =
        compose.onAllNodes(SemanticsMatcher("button label") { n -> n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("button_label:") == true }, useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag].removePrefix("button_label:") }.toSet()

    protected fun assertAllLabelled(row: String, extra: List<String> = HEADER) {
        val names = buttons(row)
        assertTrue("$row düğmeleri bulunmalı", names.size >= 5)
        val shown = labelled()
        val missing = (names + extra).filter { it !in shown }
        assertTrue("adı yazmayan düğmeler: $missing", missing.isEmpty())
        // ⋮ ve kaydet menü kaydırılsa da ekranda kalır
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        for (name in HEADER) {
            val b = compose.onNode(hasContentDescription(name)).fetchSemanticsNode().boundsInRoot
            assertTrue("$name ekranda: ${b.right} > $width", b.left >= 0f && b.right <= width + 0.5f && b.width > 20f)
        }
    }

    companion object {
        /** Başlıktaki ⋮ ve Resim olarak kaydet. */
        val HEADER = listOf("Diğer seçenekler", "Resim olarak kaydet")
    }
}

/**
 * v38 (2) yatay ekranda üst menü düğmelerinin adı dokunmadan görünür: profesyonel modda en üstteki menünün bütün
 * düğmeleri (Çiz satırı ve moda özel satırlar, ⋮ ve kaydet), basit ve çocuk modunda bütün düğmeler (seçili araç da
 * hap yerine adıyla, çökük ve seçili).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w891dp-h411dp-land-xhdpi")
class V38LandscapeLabelsTest : V38LabelsBase() {
    @Test
    fun professionalTopMenuShowsNames() {
        openKind("Resim", CanvasKind.ART)
        waitFor { exists("tool_row_draw") }
        assertAllLabelled("tool_row_draw")
        assertTrue(labelled().containsAll(listOf("Geri al", "Fırça Seç")))
        // v47 Katmanlar (Elemanlar, Özellikler ile) sağdaki rayda, adı yazılı
        assertTrue(compose.onAllNodes(hasContentDescription("Katmanlar") and hasAnyAncestor(hasTestTag("panel_rail"))).fetchSemanticsNodes().isNotEmpty())
        shot("v38-land-art-draw")
        // Moda özel satır (Ekle) da adlarıyla
        mode("Ekle")
        waitFor { exists("tool_row_context") }
        assertAllLabelled("tool_row_context")
        assertTrue("Şekil ekle" in labelled())
        shot("v38-land-art-add")
    }

    @Test
    fun simpleMenuShowsAllNames() {
        openKind("Basit", CanvasKind.SIMPLE_ART)
        waitFor { exists("simple_tool_row") }
        assertAllLabelled("simple_tool_row")
        // Seçili araç hap değil: adı altında, kendisi seçili
        val selected = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true) and hasAnyAncestor(hasTestTag("simple_tool_row")))
            .fetchSemanticsNodes()
        assertEquals(1, selected.size)
        assertTrue(selected.single().config[SemanticsProperties.ContentDescription].single() in labelled())
        shot("v38-land-simple")
    }

    @Test
    fun kidsMenuShowsAllNames() {
        openKind("Çocuk", CanvasKind.KIDS)
        waitFor { exists("simple_tool_row") }
        assertAllLabelled("simple_tool_row")
        shot("v38-land-kids")
    }
}

/** v38 (2) dikey ekranda adlar yazmaz (yalnız seçili araç ya da dokunulan düğme adını gösterir, eskisi gibi). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V38PortraitLabelsTest : V38LabelsBase() {
    @Test
    fun professionalHasNoPermanentNames() {
        openKind("Resim", CanvasKind.ART)
        waitFor { exists("tool_row_draw") }
        assertTrue(labelled().isEmpty())
    }

    @Test
    fun simpleHasNoPermanentNames() {
        openKind("Basit", CanvasKind.SIMPLE_ART)
        waitFor { exists("simple_tool_row") }
        assertTrue(labelled().isEmpty())
    }
}

/** v38 (2) tablet yatay (geniş başlık satırı) da adlarıyla. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w1280dp-h800dp-land-mdpi")
class V38TabletLandscapeLabelsTest : V38LabelsBase() {
    @Test
    fun tabletTopMenuShowsNames() {
        openKind("Resim", CanvasKind.ART)
        waitFor { exists("tool_row_draw") }
        assertAllLabelled("tool_row_draw")
        shot("v38-land-tablet-art")
    }
}

/** v38 (2) düğme altındaki kısa ad: parantez içi açıklama atılır. */
class V38ShortLabelTest {
    @Test
    fun dropsParenthesis() {
        assertEquals("Kalem aracı", com.karapp.drawguidance.ui.components.shortLabel("Kalem aracı (yol)"))
        assertEquals("Katmanlar", com.karapp.drawguidance.ui.components.shortLabel("Katmanlar"))
        assertEquals("ペン", com.karapp.drawguidance.ui.components.shortLabel("ペン（パス）"))
        assertEquals("(x)", com.karapp.drawguidance.ui.components.shortLabel("(x)"))
    }
}
