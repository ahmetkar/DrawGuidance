package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.data.images.SavedImageEntity
import com.karapp.drawguidance.data.local.FolderDao
import com.karapp.drawguidance.data.local.FolderEntity
import com.karapp.drawguidance.data.local.FolderKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v15 listeler: tuval boyutu, tuval klasörleri (oluştur, taşı, aç), klasör ve resimlerin çoklu seçilip silinmesi. */
abstract class V15ListBase : EditorTestBase() {

    protected fun canvases() = runBlocking { GlobalContext.get().get<CanvasRepository>().observeCanvases().first() }
    protected fun images() = runBlocking { GlobalContext.get().get<SavedImageDao>().observeAll().first() }
    protected fun folders(kind: FolderKind) = runBlocking { GlobalContext.get().get<FolderDao>().observe(kind.name).first() }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15CanvasFoldersTest : V15ListBase() {
    @Test
    fun canvasFoldersMoveOpenAndBulkDelete() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val repository = GlobalContext.get().get<CanvasRepository>()
        runBlocking {
            repository.createCanvas("Birinci", 1080, 1080, "#FFFFFF", false)
            repository.createCanvas("İkinci", 1600, 1000, "#FFFFFF", false)
            repository.createCanvas("Üçüncü", 800, 600, "#FFFFFF", false)
        }
        waitForText("Birinci")
        // Boyut: piksel ve kayıt boyutu
        waitForText("1600×1000 · ", substring = true)

        // Yeni klasör
        compose.onNodeWithContentDescription("Yeni klasör").performClick()
        waitForText("Klasör adı")
        compose.onNode(hasSetTextAction()).performTextInput("Dersler")
        compose.onNodeWithText("Tamam").performClick()
        waitFor { folders(FolderKind.CANVAS).size == 1 }
        waitForDescription("Klasör Dersler")

        // Uzun bas: tuval seçilir; klasöre taşı
        compose.onAllNodesWithText("Birinci")[0].performTouchInput { longClick() }
        waitForText("Seçili tuval")
        compose.onNodeWithContentDescription("Klasöre taşı").performClick()
        waitForText("Ana liste")
        compose.onNode(hasText("Dersler") and androidx.compose.ui.test.hasTestTag("folder_option")).performClick()
        val folderId = folders(FolderKind.CANVAS).single().id
        waitFor { canvases().single { it.name == "Birinci" }.folderId == folderId }
        waitFor { compose.onAllNodesWithText("Birinci").fetchSemanticsNodes().isEmpty() }
        waitForText("1 öğe")

        // Klasörü aç: içinde taşınan tuval; geri ile ana listeye
        compose.onNodeWithContentDescription("Klasör Dersler").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Birinci")
        assertTrue(compose.onAllNodesWithText("İkinci").fetchSemanticsNodes().isEmpty())
        SystemBack.press()
        waitForText("İkinci")

        // Klasör ve bir tuval birlikte seçilip üst menüden silinir (klasördeki tuvalle birlikte)
        compose.onNodeWithContentDescription("Klasör Dersler").performTouchInput { longClick() }
        waitForText("1 öğe seçildi")
        compose.onAllNodesWithText("İkinci")[0].performTouchInput { click() }
        waitForText("2 öğe seçildi")
        compose.onNodeWithContentDescription("Seçilenleri sil").performClick()
        waitForText("2 öğe silinsin mi?")
        compose.onNode(hasText("Seçilenleri sil") and hasClickAction()).performClick()
        waitFor { canvases().map { it.name } == listOf("Üçüncü") }
        waitFor { folders(FolderKind.CANVAS).isEmpty() }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V15ImageFoldersTest : V15ListBase() {
    @Test
    fun imagesAndFoldersAreSelectedAndDeletedFromTheTopBar() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        val dao = GlobalContext.get().get<SavedImageDao>()
        val folderDao = GlobalContext.get().get<FolderDao>()
        runBlocking {
            folderDao.upsert(FolderEntity("f1", "Not sayfaları", FolderKind.IMAGE.name, 1L, "c1"))
            dao.insert(SavedImageEntity(canvasLocalId = "c1", title = "Sayfa 1", uri = "file:///yok1.png", width = 100, height = 100, createdAt = 1L, location = "x", sizeBytes = 10, folderId = "f1"))
            dao.insert(SavedImageEntity(canvasLocalId = "c1", title = "Sayfa 2", uri = "file:///yok2.png", width = 100, height = 100, createdAt = 2L, location = "x", sizeBytes = 10, folderId = "f1"))
            dao.insert(SavedImageEntity(canvasLocalId = "c2", title = "Manzara", uri = "file:///yok3.png", width = 100, height = 80, createdAt = 3L, location = "x", sizeBytes = 10))
            dao.insert(SavedImageEntity(canvasLocalId = "c3", title = "Portre", uri = "file:///yok4.png", width = 80, height = 100, createdAt = 4L, location = "x", sizeBytes = 10))
        }
        compose.onNode(hasText("Kaydedilenler") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Manzara")
        waitForDescription("Klasör Not sayfaları")
        // Klasör sayfaları ayrı: ana listede görünmez
        assertTrue(compose.onAllNodesWithText("Sayfa 1").fetchSemanticsNodes().isEmpty())

        compose.onAllNodesWithText("Manzara")[0].performTouchInput { longClick() }
        waitForText("1 öğe seçildi")
        compose.onNodeWithContentDescription("Klasör Not sayfaları").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("2 öğe seçildi")
        compose.onNodeWithContentDescription("Seçilenleri sil").performClick()
        waitForText("2 öğe silinsin mi?")
        compose.onNode(hasText("Seçilenleri sil") and hasClickAction()).performClick()
        waitFor { images().map { it.title } == listOf("Portre") }
        waitFor { folders(FolderKind.IMAGE).isEmpty() }
        assertEquals(1, images().size)
        assertTrue(compose.onAllNodesWithContentDescription("Klasör Not sayfaları").fetchSemanticsNodes().isEmpty())
    }
}
