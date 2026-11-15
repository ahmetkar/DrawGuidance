package com.karapp.drawguidance

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.share.PeerState
import com.karapp.drawguidance.data.share.ShareEvent
import com.karapp.drawguidance.data.share.ShareLiveIn
import com.karapp.drawguidance.data.share.ShareLiveOut
import com.karapp.drawguidance.data.share.ShareManager
import com.karapp.drawguidance.data.share.SharePeer
import com.karapp.drawguidance.data.share.ShareRoute
import com.karapp.drawguidance.ui.screens.canvasedit.tutorial.TutorialScripts
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v52 mağaza görselleri (yalnız DG_SHOTS verilince; dil DG_LANG): formülle bilim şekilleri ve hesaplama, yetişkin
 * alıştırmaları, çocuk modu görevleri, tuval paylaşma ve iki cihaz arasında canlı yayın (yayın yapan ve izleyen).
 * Paylaşım ekranındaki yakındaki cihazlar ve yayın durumu test için doğrudan kurulur (ağ yok).
 */
abstract class V52StoreShotsBase : V32EditorBase() {
    protected abstract val device: String
    private val lang: String get() = System.getenv("DG_LANG")?.takeIf { it.isNotBlank() } ?: "tr"
    private fun t(key: String): String = (TEXTS[key] ?: error(key))[V50StoreShotsBase.LANGS.indexOf(lang).coerceAtLeast(0)]
    private fun repo() = GlobalContext.get().get<CanvasRepository>()
    private fun share() = GlobalContext.get().get<ShareManager>()
    private fun shotName(name: String) = "$lang-$device-$name"

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(name: String): T {
        val f = ShareManager::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(share()) as T
    }

    private fun open(name: String, kind: CanvasKind, width: Int = 1000, height: Int = 1414) {
        settleFor(800)
        runBlocking { repo().createCanvas(name, width, height, "#FFFFFF", aiEnabled = false, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    /** Eğitim grubunu oynatır; [shotAt] adımında açıklama kutusuyla görüntü alınır. */
    private fun play(kind: CanvasKind, groupId: String, shotAt: Map<Int, String> = emptyMap()) {
        val group = TutorialScripts.groups(kind).first { it.id == groupId }
        compose.runOnIdle { vm().tutorial.fast = true; vm().tutorial.start(group) }
        waitFor { calloutShown() }
        for (i in 1..group.steps.size) {
            runUntil(120_000) { vm().tutorial.ui.value?.index == i - 1 }
            awaitDemo(120_000)
            advanceMain(300)
            shotAt[i - 1]?.let { settleFor(1200); shot(shotName(it)) }
            next()
        }
        waitFor { !calloutShown() }
    }

    /** Formülden molekül, Bohr atomu, eğik atış ve hesaplanan formüller (eğitimin son adımı ve temiz sayfa). */
    @Test
    fun scienceFormulas() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open(t("science_name"), CanvasKind.NOTES)
        play(CanvasKind.NOTES, "notes_science_v52", mapOf(1 to "12-formul-bilim-adim", 4 to "12-formul-bilim"))
        settleFor(1200)
        shot(shotName("12-formul-bilim-temiz"))
    }

    /** Yetişkin çizim alıştırması (gelişmiş resim). */
    @Test
    fun practice() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open(t("practice_name"), CanvasKind.ART)
        val index = vm().practice.levels().indexOfFirst { !vm().practice.isLocked(it) }.coerceAtLeast(0) + 2
        compose.runOnIdle { vm().practice.open(index) }
        settleFor(2000)
        shot(shotName("13-alistirma"))
    }

    /** Çocuk modu çizim görevi. */
    @Test
    fun kidsChallenge() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open(t("kids_name"), CanvasKind.KIDS)
        compose.runOnIdle { vm().challenge.open(3) }
        settleFor(2000)
        shot(shotName("14-cocuk-gorev"))
    }

    /** Tuval paylaşma: yakındaki cihazlar (biri bağlı) ve gönder düğmeleri. */
    @Test
    fun shareCanvas() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        settleFor(800)
        runBlocking { repo().createCanvas(t("science_name"), 1000, 1414, "#FFFFFF", aiEnabled = false, kind = CanvasKind.NOTES) }
        waitForText(t("science_name"))
        field<MutableStateFlow<List<SharePeer>>>("_peers").value = listOf(
            SharePeer("LAN:1", ShareRoute.LAN, "1", t("peer_tablet"), "android", PeerState.CONNECTED),
            SharePeer("LAN:2", ShareRoute.LAN, "2", t("peer_phone"), "ios", PeerState.FOUND),
        )
        compose.onNodeWithTag("canvas_list_share", useUnmergedTree = true).performClick()
        waitFor { exists("share_peers") }
        settleFor(1500)
        shot(shotName("15-paylasim"))
    }

    /** Canlı yayın: yayın yapan cihazda bilgi çubuklu düzenleyici, izleyen cihazda canlı tuval. */
    @Test
    fun liveBroadcast() {
        assumeTrue(System.getenv("DG_SHOTS") != null)
        open(t("science_name"), CanvasKind.NOTES)
        play(CanvasKind.NOTES, "notes_science_v52")
        val c = vm().canvas.value!!
        field<MutableStateFlow<ShareLiveOut?>>("_liveOut").value =
            ShareLiveOut(c.localId, "LAN:1", t("peer_tablet"), startedAt = System.currentTimeMillis() - 754_000)
        settleFor(1500)
        shot(shotName("16-canli-yayin-gonderen"))
        // İzleyen taraf: aynı tuval canlı görüntüleyicide
        field<MutableStateFlow<ShareLiveOut?>>("_liveOut").value = null
        field<MutableStateFlow<ShareLiveIn?>>("_liveIn").value = ShareLiveIn(c.localId, "LAN:9", t("peer_phone"))
        field<MutableStateFlow<com.karapp.drawguidance.data.model.CanvasUi?>>("_liveCanvas").value = c
        field<MutableSharedFlow<ShareEvent>>("_events").tryEmit(ShareEvent.OpenLive(c.localId))
        waitFor { exists("share_live_screen") }
        settleFor(1500)
        shot(shotName("16-canli-yayin-izleyen"))
        field<MutableStateFlow<ShareLiveIn?>>("_liveIn").value = null
    }

    companion object {
        val TEXTS: Map<String, List<String>> = mapOf(
            "science_name" to listOf("Fen notu", "Science notes", "ملاحظات العلوم", "理科ノート", "과학 노트", "Конспект по естествознанию", "科学笔记"),
            "practice_name" to listOf("Alıştırma", "Practice", "تمرين", "練習", "연습", "Практика", "练习"),
            "kids_name" to listOf("Boyacık", "Painty", "رسّام", "ぬりえ", "색칠놀이", "Раскраска", "小画家"),
            "peer_tablet" to listOf("Ayşe'nin tableti", "Anna's tablet", "جهاز سارة اللوحي", "ユイのタブレット", "지민의 태블릿", "Планшет Анны", "小雨的平板"),
            "peer_phone" to listOf("Sınıf iPhone'u", "Classroom iPhone", "آيفون الصف", "教室のiPhone", "교실 iPhone", "iPhone класса", "教室的 iPhone"),
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = StoreShotsApp::class, qualifiers = "w393dp-h852dp-xxhdpi")
class V52StorePhoneShotsTest : V52StoreShotsBase() { override val device = "phone" }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = StoreShotsApp::class, qualifiers = "w834dp-h1194dp-xhdpi")
class V52StoreTabletShotsTest : V52StoreShotsBase() { override val device = "tablet" }
