package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.data.local.FolderKind
import com.karapp.drawguidance.data.local.FolderRepository
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.ui.components.TitleSearch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v33 (4) Tuvallerim ve 3B Şekillerim'de başlıkta anlık arama (Türkçe harf farkları ve büyük/küçük harf gözetilmez). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V33SearchTest : V33EditorBase() {
    private fun visible(name: String) = compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
    /** Arama kutusuna yazar (kutu kapalıysa önce dokunup açar). */
    private fun type(tag: String, text: String) {
        val node = compose.onNodeWithTag(tag)
        if (node.fetchSemanticsNode().config.getOrNull(SemanticsActions.SetText) == null) {
            node.performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag(tag).performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(text)
        compose.waitForIdle()
    }

    private fun visibleCard(name: String) = compose.onAllNodesWithContentDescription(name, substring = true).fetchSemanticsNodes().isNotEmpty() || visible(name)

    @Test
    fun matcherIgnoresCaseAndTurkishLetters() {
        assertTrue(TitleSearch.matches("Kırmızı Şehir", "SEHIR"))
        assertTrue(TitleSearch.matches("Ağaç çizimi", "agac"))
        assertTrue(TitleSearch.matches("İstanbul Boğazı", "istanbul bogaz"))
        assertTrue(TitleSearch.matches("Gül", "GÜL"))
        assertTrue(TitleSearch.matches("Işık", "isik"))
        assertFalse(TitleSearch.matches("Deniz kıyısı", "dağ"))
        assertTrue(TitleSearch.matches("Herhangi", "   "))
    }

    @Test
    fun canvasListFiltersAsYouType() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repo = GlobalContext.get().get<CanvasRepository>()
        val folders = GlobalContext.get().get<FolderRepository>()
        runBlocking {
            val folder = folders.create("Dersler", FolderKind.CANVAS)
            repo.createCanvas("Kırmızı Şehir", 800, 600, "#FFFFFF", false)
            repo.createCanvas("Deniz kıyısı", 800, 600, "#FFFFFF", false)
            repo.createCanvas("Ağaç çizimi", 800, 600, "#FFFFFF", false)
            repo.createCanvas("Okul notu", 800, 600, "#FFFFFF", false, folderId = folder.id)
            repo.createCanvas("Şehir planı ders", 800, 600, "#FFFFFF", false, folderId = folder.id)
        }
        waitFor { visible("Deniz kıyısı") && visible("Dersler") }
        type("canvas_search", "sehir")
        waitFor { visible("Kırmızı Şehir") && !visible("Deniz kıyısı") && !visible("Ağaç çizimi") }
        // Ana listede klasörlerdeki tuvaller de aranır
        assertTrue(visible("Şehir planı ders"))
        shot("v33-search-canvases")
        type("canvas_search", "AGAC")
        waitFor { visible("Ağaç çizimi") && !visible("Kırmızı Şehir") }
        // Eşleşme yok
        type("canvas_search", "zzz")
        waitFor { exists("canvas_search_empty") }
        // × ile temizlenir: hepsi görünür
        compose.onNodeWithTag("canvas_search_clear").performClick()
        waitFor { visible("Deniz kıyısı") && visible("Kırmızı Şehir") && visible("Dersler") }
        // Klasörün içinde yalnız o klasörün tuvalleri aranır
        compose.onNodeWithText("Dersler").performClick()
        waitFor { visible("Okul notu") }
        type("canvas_search", "ŞEHİR")
        waitFor { visible("Şehir planı ders") && !visible("Okul notu") && !visible("Kırmızı Şehir") }
    }

    @Test
    fun modelListFiltersAsYouType() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repo = GlobalContext.get().get<Model3DRepository>()
        runBlocking {
            repo.save(Model3D("m1", "Kırmızı küp", listOf(ModelEdit.newPart(PrimitiveKind.CUBE, "p1", 0))))
            repo.save(Model3D("m2", "Gezegen", listOf(ModelEdit.newPart(PrimitiveKind.SPHERE, "p2", 0))))
        }
        compose.onNodeWithText("3B Şekiller").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("models3d_screen") && visibleCard("Gezegen") && visibleCard("Kırmızı küp") }
        type("models3d_search", "KUP")
        waitFor { visibleCard("Kırmızı küp") && !visibleCard("Gezegen") }
        type("models3d_search", "yok böyle")
        waitFor { exists("models3d_search_empty") }
        // Hazır şekiller sekmesinde de aynı arama: bütün kategorilerde
        compose.onNodeWithTag("models3d_search_clear").performClick()
        compose.onNodeWithText("Hazır şekiller").performClick()
        waitFor { exists("model3d_catalog") }
        type("models3d_search", "egik")
        waitFor { compose.onAllNodesWithContentDescription("Eğik düzlem").fetchSemanticsNodes().isNotEmpty() }
        type("models3d_search", "qqqq")
        waitFor { exists("model3d_catalog_empty") }
    }
}
