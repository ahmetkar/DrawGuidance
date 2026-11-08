package com.karapp.drawguidance

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.images.SavedImageDao
import com.karapp.drawguidance.data.images.SavedImageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Resim görüntüleyicide WhatsApp / Instagram / Pinterest / diğer paylaşım (Android paylaşma niyeti, API yok). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
abstract class ImageShareTestBase : EditorTestBase() {

    protected fun installShareTarget(pkg: String) {
        val pm = shadowOf(compose.activity.packageManager)
        val component = ComponentName(pkg, "$pkg.ShareActivity")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_SEND).apply { addCategory(Intent.CATEGORY_DEFAULT); addDataType("image/*") })
    }

    protected fun openSavedImage(): SavedImageEntity {
        val context = compose.activity
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DrawGuidance").apply { mkdirs() }
        val file = File(dir, "Deniz_Feneri.png")
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 90, 160)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val entity = SavedImageEntity(canvasLocalId = "c", title = "Deniz Feneri", uri = Uri.fromFile(file).toString(), width = 120, height = 80, createdAt = 1, location = file.absolutePath)
        runBlocking { GlobalContext.get().get<SavedImageDao>().insert(entity) }

        compose.onAllNodesWithText("Kaydedilenler").fetchSemanticsNodes()
        compose.onNodeWithText("Kaydedilenler").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Deniz Feneri")
        compose.onNodeWithText("Deniz Feneri").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Paylaş")
        return entity
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImageShareTest : ImageShareTestBase() {
    @Test
    fun sharesToWhatsappInstagramPinterestAndOtherApps() {
        shadowOf(compose.activity.application).checkActivities(true)
        installShareTarget("com.whatsapp")
        installShareTarget("com.pinterest")
        openSavedImage()
        val shadow = shadowOf(compose.activity)
        shadow.clearNextStartedActivities()

        // WhatsApp yüklü: resim içerik sağlayıcı adresiyle ve okuma izniyle WhatsApp'a gider
        compose.onNodeWithContentDescription("WhatsApp ile paylaş").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val whatsapp = requireNotNull(shadow.nextStartedActivity)
        assertEquals(Intent.ACTION_SEND, whatsapp.action)
        assertEquals("com.whatsapp", whatsapp.`package`)
        assertEquals("image/png", whatsapp.type)
        @Suppress("DEPRECATION")
        val stream = whatsapp.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull(stream)
        assertEquals("content", stream!!.scheme)
        assertEquals("${compose.activity.packageName}.fileprovider", stream.authority)
        assertTrue(whatsapp.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        // Paylaşılan adres kaydedilen dosyayı gösterir (FileProvider "pictures" yolu)
        assertEquals("/pictures/DrawGuidance/Deniz_Feneri.png", stream.path)

        // Pinterest yüklü
        compose.onNodeWithContentDescription("Pinterest ile paylaş").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("com.pinterest", shadow.nextStartedActivity.`package`)

        // Instagram yüklü değil: uyarı ve Play Store bağlantısı
        compose.onNodeWithContentDescription("Instagram ile paylaş").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Instagram bu cihazda yüklü değil.")
        assertNull(shadow.nextStartedActivity)
        shadowOf(compose.activity.application).checkActivities(false)
        compose.onNodeWithText("Play Store'da aç").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val store = shadow.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, store.action)
        assertEquals("market://details?id=com.instagram.android", store.dataString)

        // Diğer: sistem paylaşım seçicisi
        compose.onNodeWithContentDescription("Diğer ile paylaş").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val chooser = shadow.nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val inner = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertNull(inner.`package`)
    }
}

// Koin her test sınıfında temiz JVM ister (forkEvery = 1)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ImageShareMissingFileTest : ImageShareTestBase() {
    @Test
    fun missingFileIsReportedInsteadOfSharingNothing() {
        val entity = openSavedImage()
        File(Uri.parse(entity.uri).path!!).delete()
        compose.onNodeWithContentDescription("WhatsApp ile paylaş").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("Resim dosyası bulunamadı", substring = true)
        assertTrue(compose.onAllNodesWithText("yüklü değil", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
