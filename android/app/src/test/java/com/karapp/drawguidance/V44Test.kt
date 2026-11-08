package com.karapp.drawguidance

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.comic.ComicFilter
import com.karapp.drawguidance.content.image.ImageLook
import com.karapp.drawguidance.data.billing.Entitlements
import com.karapp.drawguidance.data.billing.ExemptAccounts
import com.karapp.drawguidance.data.billing.FeatureKeys
import com.karapp.drawguidance.data.billing.PremiumFeature
import com.karapp.drawguidance.data.billing.PremiumRules
import com.karapp.drawguidance.data.comicassets.ComicAssetKind
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.ComicCatalog
import com.karapp.drawguidance.data.kids.KidsLockRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.CanvasEntity
import com.karapp.drawguidance.data.local.KidsLockEntity
import com.karapp.drawguidance.data.local.LocalUserEntity
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.StrokePoint
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.settings.ServerConfig
import com.karapp.drawguidance.ui.premium.PremiumAccess
import com.karapp.drawguidance.ui.screens.canvasedit.EditMode
import com.karapp.drawguidance.ui.screens.canvasedit.components.CanvasPick
import com.karapp.drawguidance.ui.screens.canvasedit.editor.InkFill
import com.karapp.drawguidance.ui.screens.canvasedit.PanelTab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v44 kurallar (JVM/Robolectric, arayüzsüz): çocuk kilidi (şifre, bekleme, yaş), 22 → 23 göçü, Premium ayrımındaki
 * değişiklikler (3B dışa aktarma, resim görünümleri ve ayarları, filtreler, çizgi roman karakter/mekân/nesne/efekt,
 * şablonlar), istisna hesabı, sunucu ayarının kalkması ve kalemle çizilen kapalı şeklin içinin boyanması.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V44RulesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var now = 1_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close(); org.koin.core.context.stopKoin() }

    private fun repo() = KidsLockRepository(db.kidsLockDao(), db.localUserDao(), clock = { now })

    private fun user(uid: String, birth: String?, signedIn: Boolean = true) =
        LocalUserEntity("id-$uid", uid, "Ad", "Ad", "Soyad", "$uid@example.com", true, null, 1L, birth, signedIn, 1, 1)

    private suspend fun KidsLockRepository.awaitEnabled(on: Boolean) = withTimeout(5_000) { state.first { it.enabled == on } }

    // ------------------------------------------------------------------ 7 çocuk kilidi

    @Test
    fun pinEnablesAndDisablesWithBlockAfterWrongTries() = runBlocking {
        val r = repo()
        assertEquals(KidsLockRepository.Result.NO_PIN, r.disable("1234"))
        assertEquals(KidsLockRepository.Result.INVALID, r.enable("12a4"))
        assertEquals(KidsLockRepository.Result.INVALID, r.enable("123"))
        assertEquals(KidsLockRepository.Result.OK, r.enable("1234"))
        r.awaitEnabled(true)
        assertTrue(r.locked.let { withTimeout(5_000) { it.first { v -> v } } })
        // Şifrenin kendisi saklanmaz (özet ve tuz)
        val row = db.kidsLockDao().get()!!
        assertEquals(KidsLockEntity.SOURCE_MANUAL, row.source)
        assertNotNull(row.pinHash)
        assertNotNull(row.pinSalt)
        assertFalse(row.pinHash!!.contains("1234"))
        // Aynı şifre iki kez farklı özet verir (rastgele tuz)
        val r2 = KidsLockRepository(db.kidsLockDao(), null, clock = { now })
        assertEquals(KidsLockRepository.Result.OK, r2.enable("1234"))
        assertFalse(row.pinHash == db.kidsLockDao().get()!!.pinHash)
        // 4 yanlış: WRONG, 5.de bekleme
        repeat(KidsLockRepository.MAX_ATTEMPTS - 1) { assertEquals(KidsLockRepository.Result.WRONG, r.disable("0000")) }
        assertEquals(KidsLockRepository.Result.BLOCKED, r.disable("0000"))
        // Beklemedeyken doğru şifre de kabul edilmez
        assertEquals(KidsLockRepository.Result.BLOCKED, r.disable("1234"))
        assertTrue(db.kidsLockDao().get()!!.enabled)
        now += KidsLockRepository.BLOCK_MS + 1
        assertEquals(KidsLockRepository.Result.OK, r.disable("1234"))
        r.awaitEnabled(false)
        assertEquals(0, db.kidsLockDao().get()!!.failedAttempts)
    }

    @Test
    fun changePinNeedsOldPin() = runBlocking {
        val r = repo()
        r.enable("1234")
        assertEquals(KidsLockRepository.Result.WRONG, r.changePin("9999", "5678"))
        assertEquals(KidsLockRepository.Result.INVALID, r.changePin("1234", "56"))
        assertEquals(KidsLockRepository.Result.OK, r.changePin("1234", "5678"))
        assertEquals(KidsLockRepository.Result.WRONG, r.disable("1234"))
        assertEquals(KidsLockRepository.Result.OK, r.disable("5678"))
    }

    @Test
    fun underTwelveLocksOncePerAccountAndParentSetsPin() = runBlocking {
        val r = repo()
        val today = LocalDate.of(2026, 10, 1)
        // Yetişkin ve girişsiz kullanıcı: kilit açılmaz
        r.applyAge(user("adult", "1990-01-01"), today)
        r.applyAge(user("out", "2020-01-01", signedIn = false), today)
        r.applyAge(null, today)
        assertNull(db.kidsLockDao().get())
        // 12 yaşından küçük (11 yaş 11 ay): kendiliğinden açılır, şifre yok
        r.applyAge(user("kid", "2014-11-01"), today)
        r.awaitEnabled(true)
        val row = db.kidsLockDao().get()!!
        assertEquals(KidsLockEntity.SOURCE_AGE, row.source)
        assertNull(row.pinHash)
        assertFalse(r.state.value.hasPin)
        // Tam 12 yaş: açılmaz
        assertFalse(com.karapp.drawguidance.data.settings.KidsPrefs.isUnder("2014-10-01", KidsLockRepository.LOCK_AGE, today))
        // Ebeveyn şifre belirler; ikinci kez belirlenemez (değiştirme eski şifreyle)
        assertEquals(KidsLockRepository.Result.OK, r.setPin("4321"))
        assertEquals(KidsLockRepository.Result.WRONG, r.setPin("1111"))
        assertTrue(db.kidsLockDao().get()!!.enabled)
        assertEquals(KidsLockRepository.Result.OK, r.disable("4321"))
        // Ebeveyn kapattı: aynı hesapta yeniden açılmaz
        r.applyAge(user("kid", "2014-11-01"), today)
        assertFalse(db.kidsLockDao().get()!!.enabled)
        // Başka bir çocuk hesabı: yeniden açılır
        r.applyAge(user("kid2", "2018-05-05"), today)
        r.awaitEnabled(true)
        Unit
    }

    @Test
    fun migration22to23CreatesKidsLockTable() {
        val name = "mig23-test.db"
        context.deleteDatabase(name)
        Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(*AppDatabase.ALL_MIGRATIONS).build().apply {
            runBlocking { canvasDao().upsert(CanvasEntity("c1", "Eski", 100, 100, "#FFFFFF", false, ByteArray(0), 1, 2)) }
            openHelper.writableDatabase.apply {
                execSQL("DROP TABLE `kids_lock`")
                version = 22
            }
            close()
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        runBlocking {
            assertEquals("Eski", migrated.canvasDao().getByLocalId("c1")!!.name)
            assertNull(migrated.kidsLockDao().get())
            migrated.kidsLockDao().upsert(KidsLockEntity(enabled = true, source = KidsLockEntity.SOURCE_MANUAL))
            assertTrue(migrated.kidsLockDao().get()!!.enabled)
        }
        assertEquals(23, migrated.openHelper.readableDatabase.version)
        assertEquals(1, AppDatabase.ALL_MIGRATIONS.count { it.startVersion == 22 && it.endVersion == 23 })
        migrated.close()
        context.deleteDatabase(name)
    }

    // ------------------------------------------------------------------ 2-4 Premium ayrımı

    @Test
    fun model3dExportIsPremium() {
        assertTrue(FeatureKeys.MODEL3D_EXPORT in FeatureKeys.PREMIUM)
        assertEquals(FeatureKeys.MODEL3D_EXPORT, PremiumFeature.MODEL3D_EXPORT.key(null))
        assertTrue(PremiumAccess(Entitlements.FREE, null) {}.locked(PremiumFeature.MODEL3D_EXPORT))
        assertFalse(PremiumAccess(Entitlements.premium(), null) {}.locked(PremiumFeature.MODEL3D_EXPORT))
    }

    @Test
    fun imageLooksFiltersAndAdjustments() {
        // Parıltı, yağlı boya, karakalem, kabartma, piksel, iki ton, afiş Free; soğuk, sıcak, eski fotoğraf Premium
        listOf(ImageLook.GLOW, ImageLook.OIL, ImageLook.SKETCH, ImageLook.EMBOSS, ImageLook.PIXELATE, ImageLook.DUOTONE, ImageLook.POSTERIZE)
            .forEach { assertFalse(it.name, PremiumRules.isAdvancedLook(it)) }
        listOf(ImageLook.COOL, ImageLook.WARM, ImageLook.VINTAGE).forEach { assertTrue(it.name, PremiumRules.isAdvancedLook(it)) }
        // Bulanıklık, keskinlik, solukluk Free
        listOf("blur", "sharpen", "fade").forEach { assertFalse(it, it in PremiumRules.ADVANCED_ADJUSTMENTS) }
        // Manga filtresi Free
        assertFalse(PremiumRules.isAdvancedFilter(ComicFilter.MANGA))
        assertFalse(PremiumRules.isAdvancedFilter(ComicFilter.COMIC))
        assertTrue(PremiumRules.isAdvancedFilter(ComicFilter.HALFTONE))
    }

    private val catalog: ComicCatalog by lazy { ComicAssets.parse(File("src/main/assets", ComicAssets.CATALOG).readText()) }

    @Test
    fun comicCharactersBackgroundsPropsAndTemplates() {
        // Kovboy, genç büyücü, samuray, elf okçu Premium
        listOf("kovboy_riza", "buyucu_efe", "samuray_kenji", "samuray_aiko", "elf_okcu_silvan").forEach { id ->
            val c = catalog.characters.first { it.id == id }
            assertEquals(id, PremiumFeature.COMIC_CHARACTERS_ADVANCED, PremiumRules.characterFeature(c))
        }
        // Terk edilmiş fabrika, yıkık bina merdiveni, kutup araştırma üssü, okul çatısı Free
        PremiumRules.FREE_BACKGROUNDS.forEach { id ->
            val b = catalog.backgrounds.first { it.id == id }
            assertNull(id, PremiumRules.backgroundFeature(b))
        }
        // Bilim kurgu, askeri ve fantastik kategorilerinden 4'er nesne Free (kategorinin ötekiler Premium)
        for (cat in listOf("bilimkurgu", "silah_zirh", "buyu")) {
            val items = catalog.objects.filter { it.category == cat }
            assertEquals(cat, 4, items.count { PremiumRules.propFeature(ComicAssetKind.OBJECT, it) == null })
            assertTrue(cat, items.any { PremiumRules.propFeature(ComicAssetKind.OBJECT, it) != null })
        }
        // Portal ve büyü, enerji ve aura, atış ve namlu alevi efektlerinden 2'şer Free
        for (cat in listOf("portal", "enerji", "silah")) {
            val items = catalog.effects.filter { it.category == cat }
            assertEquals(cat, 2, items.count { PremiumRules.propFeature(ComicAssetKind.EFFECT, it) == null })
        }
        // Şablonların hepsi Free
        com.karapp.drawguidance.content.comic.ComicTemplates.all.forEach { assertFalse(it.id, PremiumRules.isAdvancedTemplate(it)) }
    }

    // ------------------------------------------------------------------ 5 istisna hesabı

    @Test
    fun exemptAccountHasEveryFeatureButKeepsPlan() {
        assertTrue(ExemptAccounts.isExempt("ahmetkar8040@gmail.com"))
        assertTrue(ExemptAccounts.isExempt(" AhmetKar8040@gmail.com "))
        assertFalse(ExemptAccounts.isExempt("ahmetkar2346@gmail.com"))
        assertFalse(ExemptAccounts.isExempt(null))
        val exempt = Entitlements.FREE.copy(exempt = true)
        FeatureKeys.PREMIUM.forEach { assertTrue(it, exempt.has(it)) }
        // Abonelik ekranı gerçek planı gösterir
        assertFalse(exempt.isPremium)
        PremiumFeature.entries.forEach { assertFalse(it.name, PremiumAccess(exempt, CanvasKind.COMIC) {}.locked(it)) }
    }

    // ------------------------------------------------------------------ 6 sunucu ayarı yok

    @Test
    fun releaseServerCannotBeChanged() {
        val release = ServerConfig(context, ServerConfig.REMOTE_URL, allowOverride = false)
        assertEquals("https://drawapi.ahmetkar.com/", release.baseUrl.value)
        listOf("192.168.1.20", "10.0.0.5:4747", "http://kotu.ornek.com/").forEach { assertFalse(it, release.update(it)) }
        assertEquals(ServerConfig.REMOTE_URL, release.baseUrl.value)
        // Kayıtlı eski değer okunmaz
        context.getSharedPreferences("server_config", Context.MODE_PRIVATE).edit().putString("base_url", "http://192.168.1.9:4747/").putString("mode", "LOCAL").commit()
        assertEquals(ServerConfig.REMOTE_URL, ServerConfig(context, ServerConfig.REMOTE_URL, allowOverride = false).baseUrl.value)
        // Hata ayıklamada (testler) bellekte değişir
        val debug = ServerConfig(context, ServerConfig.REMOTE_URL, allowOverride = true)
        assertTrue(debug.update("127.0.0.1"))
        assertEquals("http://127.0.0.1:4747/", debug.baseUrl.value)
        // Yerel ağ tarayıcısı ve sunucu seçimi kartı kaldırıldı; yayında yalnız https
        assertNull(runCatching { Class.forName("com.karapp.drawguidance.data.remote.LanScanner") }.getOrNull())
        assertNull(runCatching { Class.forName("com.karapp.drawguidance.ui.screens.settings.ServerChoiceCardKt") }.getOrNull())
        val xml = File("src/main/res/xml/network_security_config.xml").readText()
        assertTrue(xml.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(xml.contains("domain-config"))
    }

    // ------------------------------------------------------------------ 8.1 kalemle kapalı şekil

    private fun ink(id: String, pts: List<StrokePoint>) = com.karapp.drawguidance.data.model.Stroke(
        id = id, points = pts, colorHex = "#000000", brushType = com.karapp.drawguidance.data.model.BrushType.PENCIL, sizePx = 6f, opacity = 1f, order = 0,
    )

    private fun circle(cx: Float, cy: Float, r: Float, n: Int = 40, sweep: Double = 2 * PI) =
        (0..n).map { i -> val a = i / n.toDouble() * sweep; StrokePoint(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat()) }

    @Test
    fun closedInkGetsFillUnderneath() {
        val closed = ink("a", circle(200f, 200f, 80f))
        val open = ink("b", circle(600f, 200f, 80f, sweep = PI))
        val line = ink("c", (0..20).map { StrokePoint(100f + it * 20f, 600f) })
        assertTrue(InkFill.isClosed(closed))
        assertFalse(InkFill.isClosed(open))
        assertFalse(InkFill.isClosed(line))
        val strokes = listOf(closed, open, line)
        assertEquals(closed.id, InkFill.closedAt(strokes, Offset(200f, 200f), emptySet())?.id)
        assertNull(InkFill.closedAt(strokes, Offset(600f, 210f), emptySet()))
        assertNull(InkFill.closedAt(strokes, Offset(200f, 200f), setOf("a")))
        val filled = InkFill.apply(strokes, setOf("a", "b"), "#FF0000")
        // Dolgu çizginin hemen altında, aynı grupta; açık çizgiye dolgu yok
        assertEquals(4, filled.size)
        assertEquals(InkFill.PREFIX + "a", filled[0].id)
        assertEquals("a", filled[1].id)
        assertNotNull(filled[1].groupId)
        assertEquals(filled[1].groupId, filled[0].groupId)
        assertEquals("#FF0000", filled[0].shape!!.style.fillColorHex)
        assertEquals(closed.points, filled[1].points)
        // Yeniden boyama aynı dolgunun rengini değiştirir
        val again = InkFill.apply(filled, setOf("a"), "#00FF00")
        assertEquals(4, again.size)
        assertEquals("#00FF00", again[0].shape!!.style.fillColorHex)
    }
}

/**
 * v44 düzenleyici: basit modlarda üst menüde dikdörtgenle seçim, serbest seçim ve damlalık (Seç penceresinde değil);
 * çocuk modunda Fırça ayarları ve Elemanlar boyamanın ardında; boyama aracı kalemle çizilmiş kapalı şeklin içini
 * boyar; çocuk görevinde çizim önerisi yok.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V44EditorTest : V38ChallengeBase() {
    private fun open(name: String, kind: CanvasKind, ai: Boolean = false) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { GlobalContext.get().get<CanvasRepository>().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = ai, kind = kind) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    // v45 kaydırılıp görünmeyen düğmenin kırpılmış sınırı boş olur; sıra için kırpılmamış konum kullanılır
    private fun left(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.x

    @Test
    fun simpleTopMenuHasSelectToolsAndEyedropper() {
        open("Basit", CanvasKind.SIMPLE_ART)
        waitFor { exists("simple_tool_rect_select") && exists("simple_tool_eyedropper") }
        assertTrue(compose.onAllNodes(hasContentDescription("Dikdörtgenle seçim yap")).fetchSemanticsNodes().isNotEmpty())
        // v48 basit modlarda serbest seçim (kement) yok
        assertFalse(exists("simple_tool_free_select"))
        // Seç penceresinde artık yoklar
        compose.onNodeWithTag("simple_tool_select").performScrollTo().performClick()
        waitFor { vm().mode.value == EditMode.SELECT }
        assertTrue(compose.onAllNodes(hasContentDescription("Dikdörtgen seçim")).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasContentDescription("Kalemle alan")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("simple_tool_rect_select").performScrollTo().performClick()
        waitFor { vm().canvasPick.value == CanvasPick.RECT_SELECT }
        compose.onNodeWithTag("simple_tool_eyedropper").performScrollTo().performClick()
        waitFor { vm().canvasPick.value == CanvasPick.EYEDROPPER }
    }

    @Test
    fun kidsTopMenuHasBrushSettingsNextToElements() {
        open("Çocuk", CanvasKind.KIDS)
        waitFor { exists("simple_tool_brush_settings") && exists("simple_tool_elements") }
        assertFalse(exists("simple_tool_rect_select"))
        // v47 dikeyde iki satır; Elemanlar ve Özellikler (eski Fırça ayarları) öndeki düğmelerden (kalem, silgi, boya,
        // şekil, görev, kalıp, yazı) sonra
        fun at(tag: String): Float = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.let { it.y * 10000f + it.x }
        val order = listOf("paint", "shape", "text", "elements", "brush_settings").map { at("simple_tool_$it") }
        assertEquals(order.toString(), order.sorted(), order)
        compose.onNodeWithTag("simple_tool_brush_settings").performScrollTo().performClick()
        waitFor { vm().panel.value == PanelTab.BRUSH }
        // v47 başka araç seçilince Özellikler açık kalır, o aracın ayarlarını gösterir (kovanın ayarı yok)
        compose.onNodeWithTag("simple_tool_paint").performScrollTo().performClick()
        waitFor { exists("properties_empty") }
        assertEquals(PanelTab.BRUSH, vm().panel.value)
    }

    @Test
    fun paintFillsInsideClosedInk() {
        open("Boya", CanvasKind.SIMPLE_ART)
        compose.runOnIdle {
            vm().onStrokeFinished((0..40).map { i -> val a = i / 40.0 * 2 * PI; StrokePoint(500f + 150f * cos(a).toFloat(), 500f + 150f * sin(a).toFloat()) })
        }
        waitFor { vm().canvas.value!!.strokes.size == 1 }
        val inkId = vm().canvas.value!!.strokes.single().id
        compose.runOnIdle {
            vm().setRegionColor("#1E88E5")
            vm().paintAt(Offset(500f, 500f))
        }
        waitFor { vm().canvas.value!!.strokes.size == 2 }
        val strokes = vm().canvas.value!!.strokes
        assertEquals(InkFill.PREFIX + inkId, strokes[0].id)
        assertEquals("#1E88E5", strokes[0].shape!!.style.fillColorHex)
        assertEquals(inkId, strokes[1].id)
        // Tek geri alma adımı
        compose.runOnIdle { vm().undo() }
        waitFor { vm().canvas.value!!.strokes.size == 1 }
        // Dışına dokunmak boyamaz
        compose.runOnIdle { vm().paintAt(Offset(900f, 900f)) }
        compose.waitForIdle()
        assertEquals(1, vm().canvas.value!!.strokes.size)
    }

    @Test
    fun noSuggestionsDuringChallenge() {
        openKids(ai = true)
        compose.runOnIdle { assertTrue(ctl().start()) }
        waitFor { ctl().active.value }
        quiet()
        val size = canvasSize()
        val r = size.x * 0.12f
        val before = strokes().size
        wobblyCircleAt(Offset(size.x * 0.3f, size.y * 0.3f), r, 3)
        wobblyCircleAt(Offset(size.x * 0.3f + r * 2.2f, size.y * 0.3f), r, 4)
        waitFor { strokes().size == before + 2 }
        advanceMain(900)
        tick(3000)
        assertNull(vm().lineSuggestion.value)
        assertNull(vm().shapeSuggestion.value)
        assertNull(vm().textSuggestion.value)
        assertFalse(exists("kids_mascot_suggest"))
        // Görev bitince öneriler yeniden
        compose.runOnIdle { ctl().stop() }
        waitFor { !ctl().active.value }
        wobblyCircleAt(Offset(size.x * 0.6f, size.y * 0.7f), r, 5)
        wobblyCircleAt(Offset(size.x * 0.6f + r * 2.2f, size.y * 0.7f), r, 6)
        advanceMain(900)
        waitFor(15_000) { vm().lineSuggestion.value != null || vm().shapeSuggestion.value != null }
    }
}

/**
 * v44 çocuk kilidi arayüzü: Ayarlar'dan şifreyle açılır; açıkken Ana sayfada yalnız çocuk çizimi ve görev kartı,
 * tuval oluşturma ve Premium kartı yok; alt çubukta Resimlerim ve 3B modeller yok; Tuvallerim'de yalnız çocuk
 * tuvalleri. Şifreyle kapanır.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HomeNoWelcomeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V44KidsLockUiTest : V33EditorBase() {
    private fun lock() = GlobalContext.get().get<KidsLockRepository>()
    private fun text(res: Int) = compose.activity.getString(res)
    private fun hasText(t: String) = compose.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()

    private fun pin(code: String) = code.forEach { compose.onNodeWithTag("kids_pin_key_$it").performClick(); compose.waitForIdle() }

    private fun settings() {
        compose.onAllNodesWithText("Ayarlar").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("kids_lock_card") }
    }

    @Test
    fun lockFromSettingsShowsOnlyKidsAndUnlocksWithPin() {
        runBlocking {
            val repo = GlobalContext.get().get<CanvasRepository>()
            repo.createCanvas("Büyük resim", 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.ART)
            repo.createCanvas("Çocuk resmi", 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.KIDS)
        }
        waitFor { exists("home_new_simple") }
        assertTrue(hasText(text(R.string.drawer_my_images)))
        settings()
        compose.onNodeWithTag("kids_lock_switch").performScrollTo().performClick()
        waitFor { exists("kids_pin_sheet") }
        pin("2580")
        waitFor { compose.onAllNodesWithText(text(R.string.v44_pin_repeat)).fetchSemanticsNodes().isNotEmpty() }
        // Tekrar farklı: yeniden istenir
        pin("1111")
        waitFor { exists("kids_pin_error") }
        pin("2580"); pin("2580")
        waitFor { lock().locked.value && !exists("kids_pin_sheet") }
        // Abonelik satırı ve öteki sekmeler kapalı
        assertFalse(hasText(text(R.string.drawer_my_images)))
        assertFalse(hasText(text(R.string.drawer_models3d)))

        compose.onNodeWithTag("tab_home").performSemanticsAction(SemanticsActions.OnClick)
        waitFor { exists("kids_lock_home") }
        assertTrue(exists("home_kids_card"))
        assertFalse(exists("home_kids_dismiss"))
        assertFalse(exists("home_new_simple") || exists("home_new_advanced"))

        compose.onAllNodesWithText("Tuvallerim").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { hasText("Çocuk resmi") }
        assertFalse(hasText("Büyük resim"))

        // Şifreyle kapanır (yanlış şifrede ileti)
        settings()
        compose.onNodeWithTag("kids_lock_switch").performScrollTo().performClick()
        waitFor { exists("kids_pin_sheet") }
        pin("0000")
        waitFor { exists("kids_pin_error") }
        assertTrue(lock().locked.value)
        pin("2580")
        waitFor { !lock().locked.value && !exists("kids_pin_sheet") }
        waitFor { hasText(text(R.string.drawer_my_images)) }
        compose.onAllNodesWithText("Tuvallerim").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        waitFor { hasText("Büyük resim") && hasText("Çocuk resmi") }
    }

    @Test
    fun ageLockedHomeAsksParentPin() {
        runBlocking { lock().applyAge(LocalUserEntity("i", "kid-uid", "Ece", "Ece", "K", "e@example.com", true, null, 1L, LocalDate.now().minusYears(8).toString(), true, 1, 1)) }
        waitFor { exists("kids_lock_home") }
        assertTrue(exists("kids_lock_set_pin"))
        compose.onNodeWithTag("kids_lock_set_pin").performScrollTo().performClick()
        waitFor { exists("kids_pin_sheet") }
        pin("4321"); pin("4321")
        waitFor { lock().state.value.hasPin && !exists("kids_pin_sheet") }
        assertFalse(exists("kids_lock_set_pin"))
        assertTrue(lock().locked.value)
        runBlocking { assertEquals(KidsLockRepository.Result.OK, lock().disable("4321")) }
        waitFor { !exists("kids_lock_home") && exists("home_new_simple") }
    }
}
