package com.karapp.drawguidance

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.inbox.InboxRepository
import com.karapp.drawguidance.data.local.SyncEventDao
import com.karapp.drawguidance.data.local.SyncEventEntity
import com.karapp.drawguidance.data.permissions.PermissionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v43 uygulama düzeyi: gelen duyuru ana sayfada (kapatılınca kalkar), yeni sürüm için güncelleme penceresi (Play içi
 * güncelleme; desteklenmezse mağaza), ilk açılış izinlerinin veritabanına yazılması.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = V43UpdatesApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V43AppUiTest : V33EditorBase() {
    private fun dao() = GlobalContext.get().get<SyncEventDao>()

    private fun incoming(kind: String, refId: String, title: String, body: String, version: Int = 1) = runBlocking {
        val now = System.currentTimeMillis()
        dao().insertIncoming(
            SyncEventEntity(
                type = SyncEventEntity.TYPE_INBOX, payload = "DELIVERED", dedupeKey = null, status = SyncEventEntity.STATUS_DONE,
                createdAt = now, updatedAt = now, direction = SyncEventEntity.DIR_IN, kind = kind, remoteId = "d-$refId", refId = refId,
                refVersion = version, isNew = true, infoAcked = true, dataReceived = true, deliveredAcked = true, title = title, body = body,
            ),
        )
    }

    @Test
    fun announcementShowsOnHomeAndCloses() {
        compose.waitForIdle()
        incoming(InboxRepository.KIND_ANNOUNCEMENT, "a1", "Hoş geldin", "Yeni paketler geldi")
        waitFor { exists("announcement_a1") }
        // İlk gösterimde "ana sayfada gösterildi" işaretlenir
        waitFor { runBlocking { dao().incoming("d-a1")!!.handled } }
        waitForText("Yeni paketler geldi")
        compose.onNodeWithTag("announcement_close_a1").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { !exists("announcement_a1") }
        assertTrue(runBlocking { dao().incoming("d-a1")!!.dismissed })
    }

    @Test
    fun newVersionShowsUpdateDialogAndOpensStoreWithoutPlay() {
        compose.waitForIdle()
        incoming(InboxRepository.KIND_APP_UPDATE, "99", "9.9", "Yeni özellikler", version = 99)
        waitFor { exists("app_update_dialog") }
        waitForText("Yeni özellikler", substring = true)
        compose.onNodeWithTag("app_update_now").performSemanticsAction(SemanticsActions.OnClick)
        // Robolectric'te Play yok: mağaza sayfası açılır
        waitFor { shadowOf(compose.activity).peekNextStartedActivity() != null }
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertTrue(intent.dataString.orEmpty().contains("com.karapp.drawguidance"))
    }

    @Test
    fun oldVersionDoesNotAskToUpdate() {
        compose.waitForIdle()
        incoming(InboxRepository.KIND_APP_UPDATE, "1", "0.1", "Eski", version = 1)
        compose.waitForIdle()
        assertFalse(exists("app_update_dialog"))
    }

    @Test
    fun permissionsAreRecorded() = runBlocking {
        val repo = GlobalContext.get().get<PermissionRepository>()
        val tiramisu = PermissionRepository(compose.activity, GlobalContext.get().get(), sdkInt = 33)
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), tiramisu.required())
        assertTrue(PermissionRepository(compose.activity, GlobalContext.get().get(), sdkInt = 30).required().isEmpty())
        // Pencerenin sonucu kaydedilir
        tiramisu.record(Manifest.permission.POST_NOTIFICATIONS, granted = false, canAskAgain = false)
        val denied = repo.get(Manifest.permission.POST_NOTIFICATIONS)!!
        assertFalse(denied.granted)
        assertTrue(denied.permanentlyDenied)
        // Kullanıcı sonradan ayarlardan izin verdi: sistemin durumu kayda yansır
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(tiramisu.granted(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(repo.get(Manifest.permission.POST_NOTIFICATIONS)!!.granted)
    }
}

/** v43 Ana sayfadan başlayan, güncelleme penceresi açık uygulama (karşılama ekranı yok). */
class V43UpdatesApp : TestDrawGuidanceApp() {
    override val startOnHome: Boolean = true
    override val showWelcome: Boolean = false
    override val showAppUpdates: Boolean get() = true
}
