package com.karapp.drawguidance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.billing.Entitlements
import com.karapp.drawguidance.data.billing.FeatureKeys
import com.karapp.drawguidance.data.challenge.KidsEntryRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.KidsEntryDao
import com.karapp.drawguidance.data.local.KidsEntryEntity
import com.karapp.drawguidance.data.local.LocalUserDao
import com.karapp.drawguidance.data.local.LocalUserEntity
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeKind
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLevels
import com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeScore as ChallengeScoreRef
import com.karapp.drawguidance.ui.screens.home.ChallengeHomeAction
import com.karapp.drawguidance.ui.screens.home.ChallengeHomeCard
import com.karapp.drawguidance.ui.screens.home.ChallengeHomePlan
import com.karapp.drawguidance.ui.theme.DrawGuidanceTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v40 çizim görevinin 50 seviyesi: zorluk eğrisi (çizgi, parça ve renk sayısı artar), son 15 seviyenin Premium kilidi,
 * Ana sayfa kartının kararı, giriş sayacı (Room `kids_entries`, 17→18 göçü) ve 5 giriş sınırı.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V40ChallengeDataTest {
    @After fun tearDown() = stopKoin()

    private val levels = ChallengeLevels.all

    /** Seviyenin karmaşıklığı: izlenecek çizgi + boyanacak parça + farklı renk sayısı. */
    private fun complexity(i: Int): Int = levels[i].let { it.ranks.size + it.paints.size + it.paints.map { p -> p.second }.toSet().size }

    @Test
    fun fiftyLevelsGetHarderAndLastFifteenArePremium() {
        assertEquals(50, ChallengeLevels.count)
        assertEquals(35, ChallengeLevels.FREE_LEVELS)
        assertEquals(15, levels.indices.count { ChallengeLevels.isPremium(it) })
        assertFalse(ChallengeLevels.isPremium(34))
        assertTrue(ChallengeLevels.isPremium(35))
        // Üç görev türü her bölümde var
        listOf(0 until 15, 15 until 35, 35 until 50).forEach { range ->
            assertEquals(ChallengeKind.entries.toSet(), range.map { levels[it].kind }.toSet())
        }
        // Ortalama karmaşıklık bölümden bölüme artar
        val avg = listOf(0 until 15, 15 until 35, 35 until 50).map { r -> r.map(::complexity).average() }
        assertTrue("$avg", avg[0] < avg[1] && avg[1] < avg[2])
        // Yeni seviyelerde çizme görevlerinin çizgi sayısı (bir yer dışında) azalmaz: 3'ten 11'e
        val traces = levels.drop(15).filter { it.kind == ChallengeKind.TRACE }.map { ChallengeLevels.target(it, 1000, 1000).lines.size }
        assertEquals(3, traces.first())
        assertTrue("$traces", traces.drop(1).zip(traces).count { (b, a) -> b < a } <= 1)
        assertTrue(traces.last() >= 10)
        // Boyama görevlerinde renk sayısı Premium'da en çok
        val colors = levels.filter { it.kind != ChallengeKind.TRACE }.map { it.paints.map { p -> p.second }.toSet().size }
        assertEquals(7, colors.max())
        assertEquals(7, levels.last().paints.map { it.second }.toSet().size)
        // Bütün hedefler çocuk kütüphanesinden, boyanacak yerler bulunur
        levels.drop(15).forEach { level ->
            assertTrue(level.shape.startsWith("lib.kid_"))
            val t = ChallengeLevels.target(level, 1000, 1000)
            assertEquals(level.id, level.paints.size, t.probes.size)
            if (level.kind != ChallengeKind.PAINT) assertEquals(level.id, level.ranks.size, t.lines.size)
        }
    }

    @Test
    fun premiumFeatureKeyMatchesServerList() {
        assertTrue(FeatureKeys.KIDS_CHALLENGE_ADVANCED in FeatureKeys.PREMIUM)
        assertEquals("kids.challenge_advanced", FeatureKeys.KIDS_CHALLENGE_ADVANCED)
        assertTrue(Entitlements.premium().has(FeatureKeys.KIDS_CHALLENGE_ADVANCED))
        assertFalse(Entitlements.FREE.has(FeatureKeys.KIDS_CHALLENGE_ADVANCED))
    }

    private fun plan(best: Map<Int, Int>, premium: Boolean) =
        ChallengeHomePlan.of(best, 50, 35, 60, premium) { s -> if (s >= 90) 3 else if (s >= 75) 2 else if (s >= 60) 1 else 0 }

    @Test
    fun homePlanHandlesFiftyLevelsAndPremiumLock() {
        assertEquals(ChallengeHomePlan(ChallengeHomeAction.START, 0, 0, 50, 35), plan(emptyMap(), false))
        // Eski 15 seviyenin puanları: 16. seviyeden devam
        val old = (0 until 15).associateWith { 80 }
        assertEquals(ChallengeHomePlan(ChallengeHomeAction.CONTINUE, 15, 15, 50, 35), plan(old, false))
        // Free'deki 35 seviye geçildi: Premium önerisi, en az yıldızlı Free seviye yeniden
        val free = (0 until 35).associateWith { 95 } + (7 to 65) + (20 to 61)
        val p = plan(free, false)
        assertEquals(ChallengeHomeAction.PREMIUM, p.action)
        assertEquals(35, p.passed)
        assertEquals(7, p.level)
        // Premium hakkı varsa 36. seviyeden devam
        assertEquals(ChallengeHomePlan(ChallengeHomeAction.CONTINUE, 35, 35, 50, 35), plan(free, true))
        // Hepsi geçildi: yeniden oyna (Premium bitmişse Free seviyelerden)
        val all = (0 until 50).associateWith { 95 } + (40 to 70) + (3 to 80)
        assertEquals(ChallengeHomePlan(ChallengeHomeAction.ALL_DONE, 50, 40, 50, 35), plan(all, true))
        assertEquals(ChallengeHomePlan(ChallengeHomeAction.ALL_DONE, 50, 3, 50, 35), plan(all, false))
        // Kapsam dışı kayıtlar sayılmaz
        assertEquals(0, plan(mapOf(70 to 100), false).passed)
    }

    @Test
    fun entryCounterPerUserAndFiveEntryLimit() = runBlocking {
        val repo = KidsEntryRepository(GlobalContext.get().get<KidsEntryDao>(), GlobalContext.get().get<LocalUserDao>(), clock = { 42L })
        assertNull(repo.current())
        val seen = (1..7).map { repo.recordEntry().entries }
        assertEquals((1..7).toList(), seen)
        assertEquals(KidsEntryRepository.DEVICE, repo.current()!!.userKey)
        assertEquals(42L, repo.current()!!.lastEntryAt)
        // İlk 5 giriş (görev açık değilse)
        assertEquals(listOf(true, true, true, true, true, false, false), seen.map { KidsEntryRepository.shouldSuggest(it, false) })
        assertFalse(KidsEntryRepository.shouldSuggest(1, true))
        assertFalse(KidsEntryRepository.shouldSuggest(0, false))
        repo.recordSuggested()
        repo.recordAnswer(started = true)
        repo.recordAnswer(started = false)
        repo.recordAnswer(started = false)
        val row = repo.current()!!
        assertEquals(listOf(1, 1, 2), listOf(row.suggested, row.started, row.cancelled))
        // Giriş yapan kullanıcının sayacı ayrı
        GlobalContext.get().get<LocalUserDao>().upsert(LocalUserEntity("u1", "uid-x", "A", "A", "B", "a@x", true, null, null, null, true, 1, 1))
        assertEquals("uid:uid-x", repo.userKey())
        assertEquals(1, repo.recordEntry().entries)
        assertEquals(2, GlobalContext.get().get<KidsEntryDao>().count())
    }

    @Test
    fun migration17to18CreatesKidsEntries() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "mig18-test.db"
        ctx.deleteDatabase(name)
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().build().apply {
            challengeProgressDao().let { runBlocking { it.upsert(listOf(com.karapp.drawguidance.data.local.ChallengeProgressEntity(3, 77, 2, 5, false))) } }
            openHelper.writableDatabase.apply {
                execSQL("DROP TABLE `kids_entries`")
                version = 17
            }
            close()
        }
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        runBlocking {
            assertEquals(0, db.kidsEntryDao().count())
            db.kidsEntryDao().upsert(KidsEntryEntity("device", 2, 1, 0, 1, 9))
            assertEquals(2, db.kidsEntryDao().get("device")!!.entries)
            // Eski veriler korunur
            assertEquals(77, db.challengeProgressDao().get(3)!!.bestScore)
        }
        assertEquals(23, db.openHelper.readableDatabase.version)
        db.close()
        ctx.deleteDatabase(name)
    }
}

/** v40 Ana sayfa görev kartı: Premium kilidinde Premium ve yeniden oynama düğmeleri; hepsi bitince yeniden oynama. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w411dp-h891dp-xxhdpi")
class V40ChallengeHomeCardTest {
    @get:Rule val compose = createComposeRule()

    @After fun tearDown() = stopKoin()

    @Test
    fun cardShowsPremiumAndReplay() {
        var plan by androidx.compose.runtime.mutableStateOf(ChallengeHomePlan(ChallengeHomeAction.CONTINUE, 15, 15, 50, 35))
        val opened = mutableListOf<Int>()
        var premium = 0
        compose.setContent { DrawGuidanceTheme { ChallengeHomeCard(plan, onOpen = { opened += it }, onPremium = { premium++ }) } }
        compose.onNodeWithText("Kaldığın seviyeden devam et (16)").assertExists()
        compose.onNodeWithText("Harika gidiyorsun! 15 / 50 seviye geçtin.").assertExists()
        compose.onNodeWithTag("home_challenge_continue").performClick()
        assertEquals(listOf(15), opened)

        plan = ChallengeHomePlan(ChallengeHomeAction.PREMIUM, 35, 7, 50, 35)
        compose.waitForIdle()
        compose.onNodeWithText("Harika! 35 ücretsiz seviyenin hepsini geçtin. Kalan 15 seviye Premium ile açılır.").assertExists()
        compose.onNodeWithText("Premium'a bak").assertExists()
        compose.onNodeWithTag("home_challenge_premium").performClick()
        assertEquals(1, premium)
        compose.onNodeWithText("Tekrar oyna (8)").performClick()
        assertEquals(listOf(15, 7), opened)
        assertTrue(compose.onAllNodesWithTagCount("home_challenge_continue") == 0)

        plan = ChallengeHomePlan(ChallengeHomeAction.ALL_DONE, 50, 40, 50, 35)
        compose.waitForIdle()
        compose.onNodeWithText("Tekrar oyna (41)").performClick()
        assertEquals(listOf(15, 7, 40), opened)
        assertTrue(compose.onAllNodesWithTagCount("home_challenge_premium") == 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}

/** v40 düzenleyicide Premium kilidi: Free planda 36–50 taçlı, dokununca "Premium gerekli"; sıra kilidi sürer. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V40KidsChallengePremiumTest : V38ChallengeBase() {
    @Before fun free() { TestAccount.entitlements.value = Entitlements.FREE }
    @After fun reset() { TestAccount.reset() }

    private fun passFree(count: Int) = ChallengeLevels.all.take(count).forEach { kidsPrefs().recordChallenge(it.id, 90) }

    @Test
    fun freePlanLocksLastFifteenLevels() {
        openKids()
        passFree(35)
        compose.runOnIdle {
            assertTrue(ctl().reachable(34))
            assertTrue(ctl().unlocked(35))
            assertTrue(ctl().premiumLocked(35))
            assertFalse(ctl().reachable(35))
            assertFalse(ctl().open(35))
        }
        assertFalse(ctl().active.value)
        openProgress()
        assertEquals("Geçildi", stateOf("challenge_level_35"))
        assertEquals("Premium", stateOf("challenge_level_36"))
        assertEquals("Premium", stateOf("challenge_level_50"))
        assertTrue(exists("challenge_premium_note"))
        assertEquals("36–50. seviyeler Premium ile açılır", textOf("challenge_premium_note"))
        shotAll("v40-challenge-progress-premium")
        // Taçlı seviyeye dokununca "Premium gerekli"
        compose.onNodeWithTag("challenge_level_36").performScrollTo().performClick()
        waitFor { exists("premium_required_sheet") }
        assertFalse(ctl().active.value)
        assertFalse(exists("challenge_progress_sheet"))
    }

    @Test
    fun startAtPremiumLevelAsksForPremiumAndPremiumUnlocks() {
        openKids()
        passFree(35)
        openMenu()
        compose.onNodeWithTag("challenge_start").performClick()
        waitFor { exists("premium_required_sheet") }
        assertFalse(ctl().active.value)
        // Premium: sıra kilidi açık olan 36. seviye açılır, 37. hâlâ sırada kilitli
        TestAccount.entitlements.value = Entitlements.premium()
        compose.runOnIdle {
            assertFalse(ctl().premiumLocked(35))
            assertTrue(ctl().reachable(35))
            assertFalse(ctl().reachable(36))
            assertTrue(ctl().start())
        }
        waitFor { ctl().run.value?.index == 35 && exists("challenge_card") }
        assertEquals("Seviye 36 / 50", textOf("challenge_card_level"))
    }

    @Test
    fun passingLevel35OnFreeAsksForPremiumOnNext() {
        openKids()
        passFree(34)
        compose.runOnIdle { assertTrue(ctl().open(34)) }
        waitFor { exists("challenge_card") }
        quiet()
        // Yerleştir ve boya: gökkuşağı Şekiller'den kâğıdın ortasına konur (hedefle aynı kutu) ve renkli noktalar boyanır
        addKidShape("lib.kid_rainbow")
        waitFor { ctl().live.value?.drawn == true && ctl().live.value!!.marks.isNotEmpty() }
        val names = com.karapp.drawguidance.ui.screens.canvasedit.components.KidsColors.toMap()
        val marks = ctl().live.value!!.marks
        val map = mapper()
        pickPaint(names.getValue(marks.first().color))
        marks.forEachIndexed { i, m ->
            quiet()
            compose.onNodeWithContentDescription(names.getValue(m.color)).performClick()
            tap(map(m.at))
            waitFor { ctl().live.value?.marks?.getOrNull(i)?.done == true }
        }
        waitFor { (ctl().live.value?.score ?: 0) >= ChallengeScoreRef.PASS }
        finishLevel()
        assertTrue(ctl().result.value!!.passed)
        assertTrue(ctl().nextNeedsPremium())
        compose.onNodeWithTag("challenge_next").performClick()
        waitFor { exists("premium_required_sheet") }
        assertNull(ctl().result.value)
        assertEquals(34, ctl().run.value!!.index)
    }
}

/** v40 maskotun görev önerisi açık uygulama (eski testlerde kapalı). */
class SuggestTestApp : TestDrawGuidanceApp() {
    override val offerChallengeSuggestion: Boolean = true
}

/**
 * v40 çocuk tuvaline girişte maskotun görev önerisi: ilk 5 girişte balon (Başlat / İptal); Başlat geçilmemiş ilk
 * seviyeyi açar (Premium kilidine uyulur), İptal balonu kapatır; 5 girişten sonra öneri yok. Sayaç veritabanında.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = SuggestTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V40KidsChallengeSuggestTest : V38ChallengeBase() {
    @After fun reset() { TestAccount.reset() }

    private fun entries() = runBlocking { GlobalContext.get().get<KidsEntryRepository>().current() }

    @Test
    fun firstEntrySuggestsAndCancelDismisses() {
        openKids()
        waitFor { exists("challenge_suggest") }
        assertEquals("level_1", stateOf("challenge_suggest"))
        assertTrue(exists("challenge_suggest_start") && exists("challenge_suggest_cancel"))
        // Köşede tek maskot: çocuk maskotunun düğmesi öneri sürerken gizli
        assertFalse(exists("kids_mascot_toggle"))
        assertEquals(1, entries()!!.entries)
        waitFor { entries()!!.suggested == 1 }
        tick(4000)
        assertTrue(balloonText(), balloonText().contains("çizim görevi"))
        shot("v40-challenge-suggest")
        compose.onNodeWithTag("challenge_suggest_cancel").performClick()
        waitFor { !exists("challenge_suggest") }
        assertFalse(ctl().active.value)
        assertFalse(ctl().suggest.value)
        assertTrue(exists("kids_mascot_toggle"))
        waitFor { entries()!!.cancelled == 1 }
        assertEquals(0, entries()!!.started)
        // Aynı düzenleyicide giriş yeniden sayılmaz
        ctl().onKidsEntry()
        compose.waitForIdle()
        assertEquals(1, entries()!!.entries)
    }

    @Test
    fun startOpensFirstUnpassedLevel() {
        openKids()
        ChallengeLevels.all.take(2).forEach { kidsPrefs().recordChallenge(it.id, 80) }
        waitFor { exists("challenge_suggest") }
        waitFor { stateOf("challenge_suggest") == "level_3" }
        tick(4000)
        assertTrue(balloonText(), balloonText().contains("3. seviyeden"))
        compose.onNodeWithTag("challenge_suggest_start").performClick()
        // v45 seviye kurulumu yüklü makinede 10 sn'yi aşabiliyor: önce arayüzün durulması beklenir
        compose.waitForIdle()
        waitFor { ctl().active.value && ctl().run.value?.index == 2 && exists("challenge_card") }
        assertFalse(exists("challenge_suggest"))
        assertEquals("Seviye 3 / 50", textOf("challenge_card_level"))
        waitFor { entries()!!.started == 1 }
    }

    @Test
    fun startRespectsPremiumLock() {
        TestAccount.entitlements.value = Entitlements.FREE
        openKids()
        ChallengeLevels.all.take(35).forEach { kidsPrefs().recordChallenge(it.id, 80) }
        waitFor { exists("challenge_suggest") }
        waitFor { stateOf("challenge_suggest") == "premium" }
        compose.onNodeWithTag("challenge_suggest_start").performClick()
        waitFor { exists("premium_required_sheet") }
        assertFalse(ctl().active.value)
        assertFalse(exists("challenge_suggest"))
    }

    @Test
    fun noSuggestionAfterFiveEntries() {
        runBlocking { GlobalContext.get().get<KidsEntryDao>().upsert(KidsEntryEntity(KidsEntryRepository.DEVICE, 5, 5, 0, 5, 1)) }
        openKids()
        waitFor { entries()!!.entries == 6 }
        compose.waitForIdle()
        assertFalse(exists("challenge_suggest"))
        assertFalse(ctl().suggest.value)
        assertTrue(exists("kids_mascot_toggle"))
        assertEquals(5, entries()!!.suggested)
    }
}
