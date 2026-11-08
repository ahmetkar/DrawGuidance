package com.karapp.drawguidance

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.data.images.SavedImageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** v42 Kaydedilenler: tür, varsayılan konum ayrımı (JVM). */
class V42SavedModelTest {
    @Test
    fun typesAndDefaultLocation() {
        fun e(mime: String, location: String = "x") = SavedImageEntity(canvasLocalId = "c", title = "t", uri = "u", width = 1, height = 1, createdAt = 0, location = location, mime = mime)
        assertEquals("PDF", e("application/pdf").typeLabel)
        assertEquals("CBZ", e(SavedImageEntity.CBZ_MIME).typeLabel)
        assertEquals("CBZ", e("application/octet-stream", "Download/DrawGuidance/a.cbz").typeLabel)
        assertEquals("JPEG", e("image/jpeg").typeLabel)
        assertEquals("PNG", e("image/png").typeLabel)
        assertTrue(e("image/jpeg").isImage && e("image/png").isImage)
        assertFalse(e("application/pdf").isImage || e("image/tiff").isImage || e(SavedImageEntity.CBZ_MIME).isImage)
        // Galeri, İndirilenler ve uygulama klasörü varsayılan; klasör seçici ve Farklı kaydet değil
        assertTrue(SavedImageEntity.isDefaultUri("content://media/external/images/media/12"))
        assertTrue(SavedImageEntity.isDefaultUri("content://media/external/downloads/7"))
        assertTrue(SavedImageEntity.isDefaultUri("file:///storage/emulated/0/Android/data/x/files/Pictures/DrawGuidance/a.png"))
        assertFalse(SavedImageEntity.isDefaultUri("content://com.android.externalstorage.documents/tree/primary%3ACizim/document/primary%3ACizim%2Fa.png"))
        assertFalse(SavedImageEntity.isDefaultUri("content://com.android.providers.downloads.documents/document/msf%3A55"))
    }
}

/** v42 Kaydedilenler ekranı: başlık ve simge, iki sekme, PDF/CBZ belgeleri ve "Aç". */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V42SavedUiTest : V33EditorBase() {

    private fun file(name: String, bytes: ByteArray): File {
        val dir = File(compose.activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DrawGuidance").apply { mkdirs() }
        return File(dir, name).apply { writeBytes(bytes) }
    }

    @Test
    fun listsImagesAndDocumentsInTwoTabs() {
        val png = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 60, 60)) }
        val pngFile = file("Kirmizi.png", java.io.ByteArrayOutputStream().also { png.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray())
        val pdfFile = file("Roman.pdf", "%PDF-1.4\n%%EOF\n".toByteArray())
        val dao = GlobalContext.get().get<SavedImageDao>()
        val now = System.currentTimeMillis()
        val pdf = runBlocking {
            dao.insert(SavedImageEntity(canvasLocalId = "c", title = "Kırmızı", uri = Uri.fromFile(pngFile).toString(), width = 120, height = 80, createdAt = now, location = pngFile.absolutePath))
            val id = dao.insert(
                SavedImageEntity(
                    canvasLocalId = "c", title = "Roman", uri = Uri.fromFile(pdfFile).toString(), width = 1000, height = 1414, createdAt = now + 1,
                    location = "Documents/DrawGuidance/Roman.pdf", sizeBytes = pdfFile.length(), mime = "application/pdf",
                ),
            )
            dao.insert(
                SavedImageEntity(
                    canvasLocalId = "c", title = "Arşiv", uri = "content://com.android.externalstorage.documents/document/primary%3AA%2FArsiv.cbz",
                    width = 1000, height = 1414, createdAt = now + 2, location = "A/Arsiv.cbz", sizeBytes = 2048,
                    mime = SavedImageEntity.CBZ_MIME, defaultLocation = false,
                ),
            )
            id
        }
        waitFor { compose.onAllNodes(hasText("Kaydedilenler") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Kaydedilenler") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("saved_tabs") }
        // Varsayılan klasör: resim ve PDF; diğer konumlar: CBZ
        waitForText("Varsayılan klasör (2)")
        waitForText("Diğer konumlar (1)")
        waitForText("Kırmızı")
        waitForText("Roman")
        // Belge simgesi tıklanabilir kartın içinde (birleşik ağaçta görünmez)
        assertTrue("PDF belge simgesiyle", compose.onAllNodesWithTag("doc_thumb_$pdf", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Arşiv")).fetchSemanticsNodes().isEmpty())
        shot("v42-05-saved-default")
        compose.onNodeWithTag("saved_tab_other").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Arşiv")
        assertTrue(compose.onAllNodes(hasText("Kırmızı")).fetchSemanticsNodes().isEmpty())
        shot("v42-06-saved-other")
        // Belge önizlemesi: tür, konum ve "Aç"
        compose.onNodeWithTag("saved_tab_default").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Roman")
        compose.onNodeWithText("Roman").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("saved_open") }
        waitForText("PDF belgesi", substring = true)
        compose.onNodeWithTag("saved_open").performSemanticsAction(SemanticsActions.OnClick)
        // Belge türüyle görüntüleme isteği gider (açan uygulama yoksa "saved_no_viewer" uyarısı çıkar)
        compose.waitForIdle()
        val started = org.robolectric.Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
            ?: org.robolectric.Shadows.shadowOf(compose.activity).nextStartedActivity
        assertNotNull("görüntüleme isteği", started)
        assertEquals(android.content.Intent.ACTION_VIEW, started.action)
        assertEquals("application/pdf", started.type)
        assertTrue(!exists("saved_no_viewer"))
    }
}
