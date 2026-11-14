package com.karapp.drawguidance

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.billing.Entitlements
import com.karapp.drawguidance.data.billing.PremiumRules
import com.karapp.drawguidance.data.comicassets.ComicAssetKind
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.ComicCatalog
import com.karapp.drawguidance.data.comicassets.ComicStyle
import com.karapp.drawguidance.data.comicassets.ComicThemeIndex
import com.karapp.drawguidance.data.comicassets.ThemeIndex
import com.karapp.drawguidance.data.comicassets.ThemePackRepository
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.CanvasEntity
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.repository.CanvasRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** v42 katalogdaki efekt/nesne resimleri ve tema paketleri (JVM; dosyalar, adlar, kategoriler, temalar). */
class V42PropsCatalogTest {
    private val root = File("src/main/assets")
    private val catalog: ComicCatalog by lazy { ComicAssets.parse(File(root, ComicAssets.CATALOG).readText()) }
    private val langs = listOf("tr", "en", "ar", "ja", "ko", "ru", "zh")

    @Test
    fun effectsAndObjectsAreListedWithFilesAndCategories() {
        assertTrue("efekt: ${catalog.effects.size}", catalog.effects.size >= 150)
        assertTrue("nesne: ${catalog.objects.size}", catalog.objects.size >= 150)
        val fxCats = catalog.effectCategories.map { it.id }.toSet()
        val objCats = catalog.objectCategories.map { it.id }.toSet()
        val themes = catalog.themes.map { it.id }.toSet()
        val ids = (catalog.effects + catalog.objects).map { it.id }
        assertEquals("kimlikler tekil", ids.size, ids.toSet().size)
        for ((list, cats) in listOf(catalog.effects to fxCats, catalog.objects to objCats)) {
            for (p in list) {
                assertTrue(p.id, p.category in cats)
                assertTrue(p.id, p.styles.isNotEmpty() && p.styles.all { s -> ComicStyle.entries.any { it.id == s } })
                assertTrue(p.id, p.themes.isNotEmpty() && p.themes.all { it in themes })
                assertTrue(p.file, File(root, p.file).isFile)
                assertTrue(p.thumb, File(root, p.thumb).isFile)
                assertTrue(p.file, maxOf(p.w, p.h) in 40..320)
            }
            // Her kategoride en az bir resim
            for (c in cats) assertTrue(c, list.any { it.category == c })
        }
        for (s in ComicStyle.entries) {
            assertTrue(s.id, catalog.effects.any { s.id in it.styles })
            assertTrue(s.id, catalog.objects.any { s.id in it.styles })
        }
        for ((id, names) in catalog.effectCategories.map { it.id to it.names } + catalog.objectCategories.map { it.id to it.names } + catalog.themes.map { it.id to it.names }) {
            for (l in langs) assertTrue("$id/$l", !names[l].isNullOrBlank())
        }
    }

    @Test
    fun everyThemePackHasCharactersAndAtLeastTwoOtherKinds() {
        val idx = ThemeIndex.fromCatalog(catalog)
        assertTrue("macera" in catalog.themes.map { it.id } && "kiyamet" in catalog.themes.map { it.id })
        for (t in catalog.themes) {
            val chars = catalog.characters.count { idx.has(ComicAssetKind.CHARACTER, it.id, t.id) }
            val bgs = catalog.backgrounds.count { idx.has(ComicAssetKind.BACKGROUND, it.id, t.id) }
            val fx = catalog.effects.count { idx.has(ComicAssetKind.EFFECT, it.id, t.id) }
            val obj = catalog.objects.count { idx.has(ComicAssetKind.OBJECT, it.id, t.id) }
            assertTrue("${t.id}: karakter $chars", chars > 0)
            // v43 "meslek" mekân, efekt ve nesne pencerelerinden kaldırıldı (yalnız karakterleri kalır; boş tema çipi görünmez)
            if (t.id == "meslek") {
                assertEquals("meslek yalnız karakter", 0, bgs + fx + obj)
                continue
            }
            assertTrue("${t.id}: mekân $bgs, efekt $fx, nesne $obj", listOf(bgs, fx, obj).count { it > 0 } >= 2)
        }
        // Karakterin v40 teması paketlerinden biri olarak kalır
        for (c in catalog.characters) assertTrue(c.id, idx.has(ComicAssetKind.CHARACTER, c.id, c.theme))
        // Mekânların hepsi en az bir pakette
        for (b in catalog.backgrounds) assertTrue(b.id, idx.packsOf(ComicAssetKind.BACKGROUND, b.id).isNotEmpty())
    }

    @Test
    fun propPremiumSplit() {
        for (p in catalog.effects) assertEquals(p.id, p.category !in PremiumRules.FREE_EFFECT_CATEGORIES && p.id !in PremiumRules.FREE_EFFECTS, PremiumRules.propFeature(ComicAssetKind.EFFECT, p) != null)
        for (p in catalog.objects) assertEquals(p.id, p.category !in PremiumRules.FREE_OBJECT_CATEGORIES && p.id !in PremiumRules.FREE_OBJECTS, PremiumRules.propFeature(ComicAssetKind.OBJECT, p) != null)
        // İki türde de Free ve Premium resim var
        for (list in listOf(catalog.effects to ComicAssetKind.EFFECT, catalog.objects to ComicAssetKind.OBJECT)) {
            assertTrue(list.first.any { PremiumRules.propFeature(list.second, it) == null })
            assertTrue(list.first.any { PremiumRules.propFeature(list.second, it) != null })
        }
    }
}

/** v42 tema paketlerinin veritabanı: Room 20 → 21 göçü, katalogdan yazma, yabancı anahtarlar ve elle bağlantılar. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V42ThemePackDbTest {
    @org.junit.After fun stopKoin() = org.koin.core.context.stopKoin()

    private val small = ComicAssets.parse(
        """
        {"version":2,
         "themes":[{"id":"okul","names":{"tr":"Okul","en":"School"}},{"id":"fantastik","names":{"tr":"Fantastik","en":"Fantasy"}},{"id":"kiyamet","names":{"tr":"Kıyamet","en":"Apocalypse"}}],
         "characters":[{"id":"ali","style":"manga","theme":"okul","themes":["okul","fantastik"],"names":{"tr":"Ali"},"variants":[{"id":"ali_p","group":"poz","file":"comic/c/ali.webp","thumb":"comic/c/t/ali.webp","w":100,"h":200}]}],
         "bgCategories":[{"id":"sehir","names":{"tr":"Şehir"}}],
         "backgrounds":[{"id":"yikik","style":"cizgi_roman","category":"sehir","themes":["kiyamet"],"names":{"tr":"Yıkık"},"file":"comic/b/y.webp","thumb":"comic/b/t/y.webp","w":1400,"h":1000}],
         "effectCategories":[{"id":"patlama","names":{"tr":"Patlama"}}],
         "effects":[{"id":"fx1","styles":["manga","cizgi_roman"],"category":"patlama","themes":["fantastik","kiyamet","bilinmeyen"],"file":"comic/effects/fx1.webp","thumb":"comic/effects/thumbs/fx1.webp","w":200,"h":180}],
         "objectCategories":[{"id":"hazine","names":{"tr":"Hazine"}}],
         "objects":[{"id":"o1","styles":["cizgi_roman"],"category":"hazine","themes":["fantastik"],"file":"comic/objects/o1.webp","thumb":"comic/objects/thumbs/o1.webp","w":120,"h":80}]}
        """.trimIndent(),
    )

    private fun db(name: String): AppDatabase {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.deleteDatabase(name)
        return Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
    }

    @Test
    fun migration20to21CreatesThemePackTables() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "mig21-test.db"
        db(name).apply {
            runBlocking { canvasDao().upsert(CanvasEntity("c1", "Eski", 100, 100, "#FFFFFF", false, ByteArray(0), 1, 2)) }
            openHelper.writableDatabase.apply {
                execSQL("DROP TABLE `theme_pack_assets`")
                execSQL("DROP TABLE `comic_assets`")
                execSQL("DROP TABLE `theme_packs`")
                version = 20
            }
            close()
        }
        val migrated = Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        runBlocking {
            assertEquals("Eski", migrated.canvasDao().getByLocalId("c1")!!.name)
            val repo = ThemePackRepository(migrated.themePackDao())
            assertEquals(6, repo.seed(small))
            assertEquals(listOf("ali"), repo.packAssets("okul", ComicAssetKind.CHARACTER))
        }
        assertEquals(23, migrated.openHelper.readableDatabase.version)
        // Yabancı anahtarlar açık
        migrated.openHelper.readableDatabase.query("PRAGMA foreign_keys").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        migrated.close()
        ctx.deleteDatabase(name)
    }

    @Test
    fun seedLinksPacksToEveryKindAndKeepsUserLinks() {
        val db = db("packs-test.db")
        val dao = db.themePackDao()
        val repo = ThemePackRepository(dao)
        runBlocking {
            // ali: okul + fantastik; yikik: kiyamet; fx1: fantastik + kiyamet (bilinmeyen paket atlanır); o1: fantastik
            assertEquals(6, repo.seed(small))
            assertEquals(listOf("okul", "fantastik", "kiyamet"), dao.packs().map { it.id })
            assertEquals(4, dao.assets().size)
            assertEquals(setOf("fantastik", "kiyamet"), dao.packsOf("effect:fx1").toSet())
            assertEquals(listOf("o1"), repo.packAssets("fantastik", ComicAssetKind.OBJECT))
            assertEquals(listOf("yikik"), repo.packAssets("kiyamet", ComicAssetKind.BACKGROUND))
            val idx = repo.load()
            assertTrue(idx.has(ComicAssetKind.CHARACTER, "ali", "fantastik"))
            assertEquals("Fantasy", idx.packs.first { it.id == "fantastik" }.names["en"])
            assertEquals(ThemeIndex.fromCatalog(small).links, idx.links)

            // Elle bağlantı (sonradan eklenen varlığı pakete bağlamak) yeniden yazmada korunur
            repo.link("okul", ComicAssetKind.OBJECT, "o1")
            repo.seed(small)
            assertEquals(setOf("fantastik", "okul"), dao.packsOf("object:o1").toSet())
            assertTrue(repo.unlink("okul", ComicAssetKind.OBJECT, "o1"))
            assertEquals(listOf("fantastik"), dao.packsOf("object:o1"))

            // Var olmayan varlığa bağlantı yabancı anahtara takılır
            val failed = runCatching { repo.link("okul", ComicAssetKind.OBJECT, "yok") }
            assertTrue("yabancı anahtar hatası bekleniyordu", failed.isFailure)

            // Katalogdan kalkan varlığın bağlantıları da silinir (CASCADE)
            repo.seed(small.copy(effects = emptyList()))
            assertTrue(dao.packsOf("effect:fx1").isEmpty())
            assertFalse(dao.assets().any { it.id == "effect:fx1" })
            // Paket silinince bağlantıları da gider; kullanıcı paketi korunur
            repo.addPack("benim", mapOf("tr" to "Benim"))
            repo.link("benim", ComicAssetKind.CHARACTER, "ali")
            repo.seed(small.copy(themes = small.themes.filter { it.id != "okul" }))
            assertEquals(setOf("fantastik", "benim"), dao.packsOf("character:ali").toSet())
            assertTrue(dao.packs().any { it.id == "benim" })
        }
        db.close()
    }

    @Test
    fun seedIfChangedSkipsSameCatalog() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = db("packs-sig.db")
        val prefs = ctx.getSharedPreferences("packs-sig-test", android.content.Context.MODE_PRIVATE).apply { edit().clear().commit() }
        val repo = ThemePackRepository(db.themePackDao(), prefs = prefs)
        runBlocking {
            assertTrue(repo.seedIfChanged(small))
            assertFalse(repo.seedIfChanged(small))
            val changed = small.copy(objects = small.objects.map { it.copy(themes = listOf("okul")) })
            assertTrue(repo.signature(small) != repo.signature(changed))
            assertTrue(repo.seedIfChanged(changed))
            assertEquals(listOf("o1"), repo.packAssets("okul", ComicAssetKind.OBJECT))
        }
        db.close()
    }

    @Test
    fun catalogFiltersFollowThemeIndex() {
        ComicAssets.reset()
        val prev = ComicAssets.opener
        ComicAssets.opener = { path -> if (path == ComicAssets.CATALOG) ComicAssetsTestJson.text.byteInputStream() else error(path) }
        try {
            ComicThemeIndex.set(ThemeIndex.fromCatalog(ComicAssets.catalog))
            assertEquals(listOf("ali"), ComicAssets.characters(ComicStyle.MANGA, "fantastik").map { it.id })
            assertTrue(ComicAssets.characters(ComicStyle.MANGA, "kiyamet").isEmpty())
            assertEquals(listOf("okul", "fantastik"), ComicAssets.themes(ComicStyle.MANGA).map { it.id })
            assertEquals(listOf("fantastik", "kiyamet"), ComicAssets.themesFor(ComicAssetKind.EFFECT, ComicStyle.COMIC).map { it.id })
            assertEquals(listOf("fx1"), ComicAssets.props(ComicAssetKind.EFFECT, ComicStyle.MANGA, null, "kiyamet").map { it.id })
            assertTrue(ComicAssets.props(ComicAssetKind.OBJECT, ComicStyle.MANGA, null, null).isEmpty())
            assertEquals(listOf("hazine"), ComicAssets.propCategories(ComicAssetKind.OBJECT, ComicStyle.COMIC).map { it.id })
            assertEquals(listOf("yikik"), ComicAssets.backgrounds(ComicStyle.COMIC, null, "kiyamet").map { it.id })
            assertTrue(ComicAssets.backgrounds(ComicStyle.COMIC, null, "okul").isEmpty())
            // Veritabanında elle eklenen bağlantı dizine yansıyınca süzgeç de değişir
            val idx = ComicThemeIndex.value
            ComicThemeIndex.set(idx.copy(links = idx.links + ("background:yikik" to setOf("kiyamet", "okul"))))
            assertEquals(listOf("yikik"), ComicAssets.backgrounds(ComicStyle.COMIC, null, "okul").map { it.id })
        } finally {
            ComicAssets.opener = prev
            ComicAssets.reset()
        }
    }
}

/** [V42ThemePackDbTest.catalogFiltersFollowThemeIndex] için küçük katalog metni. */
private object ComicAssetsTestJson {
    val text = """
        {"version":2,
         "themes":[{"id":"okul","names":{"tr":"Okul"}},{"id":"fantastik","names":{"tr":"Fantastik"}},{"id":"kiyamet","names":{"tr":"Kıyamet"}}],
         "characters":[{"id":"ali","style":"manga","theme":"okul","themes":["okul","fantastik"],"names":{"tr":"Ali"},"variants":[{"id":"ali_p","group":"poz","file":"a","thumb":"a","w":100,"h":200}]}],
         "bgCategories":[{"id":"sehir","names":{"tr":"Şehir"}}],
         "backgrounds":[{"id":"yikik","style":"cizgi_roman","category":"sehir","themes":["kiyamet"],"names":{"tr":"Yıkık"},"file":"b","thumb":"b","w":1400,"h":1000}],
         "effectCategories":[{"id":"patlama","names":{"tr":"Patlama"}}],
         "effects":[{"id":"fx1","styles":["manga","cizgi_roman"],"category":"patlama","themes":["fantastik","kiyamet"],"file":"f","thumb":"f","w":200,"h":180}],
         "objectCategories":[{"id":"hazine","names":{"tr":"Hazine"}}],
         "objects":[{"id":"o1","styles":["cizgi_roman"],"category":"hazine","themes":["fantastik"],"file":"o","thumb":"o","w":120,"h":80}]}
    """.trimIndent()
}

/** v42 çizgi romanda Nesneler penceresi ve Efektler → Resimli efektler sekmesi (tema paketi süzgeci, ekleme). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V42ComicPropsUiTest : V33EditorBase() {
    @org.junit.After fun resetAccount() { TestAccount.entitlements.value = Entitlements.premium() }

    private fun open(name: String) {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        runBlocking { GlobalContext.get().get<CanvasRepository>().createCanvas(name, 1000, 1000, "#FFFFFF", aiEnabled = false, kind = CanvasKind.COMIC) }
        waitForText(name)
        compose.onAllNodesWithText(name)[0].performClick()
        waitFor { exists("drawing_canvas") && vm().canvas.value?.name == name }
    }

    private fun images() = vm().canvas.value?.strokes.orEmpty().filter { it.brushType == BrushType.IMAGE }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun objectsSheetFiltersByThemePackAndInserts() {
        open("Nesne v42")
        addMenu("Nesneler")
        waitFor { exists("objects_sheet") }
        // v43 nesneler tek liste (manga/çizgi roman sekmesi yok)
        assertFalse(exists("obj_style_cizgi_roman"))
        // v50 nesnelerde tema paketi süzgeci yok; yalnız içerik kategorileri (bağlama uygun)
        assertFalse(exists("obj_theme"))
        val free = ComicAssets.props(ComicAssetKind.OBJECT, ComicStyle.COMIC, null).first { PremiumRules.propFeature(ComicAssetKind.OBJECT, it) == null }
        val cat = free.category
        val outside = ComicAssets.catalog.objects.first { "cizgi_roman" in it.styles && it.category != cat }
        click("obj_cat_$cat")
        waitFor { exists("obj_item_${free.id}") }
        assertFalse(exists("obj_item_${outside.id}"))
        shot("v42-01-objects-sheet")
        click("obj_item_${free.id}")
        waitFor { !exists("objects_sheet") && images().size == 1 }
        // Uzun kenarı sayfanın %26'sı
        val img = images().single()
        val w = img.points.maxOf { it.x } - img.points.minOf { it.x }
        val h = img.points.maxOf { it.y } - img.points.minOf { it.y }
        assertEquals(260f, maxOf(w, h), 3f)
        assertEquals(free.w.toFloat() / free.h, w / h, 0.05f)
    }

    @Test
    fun premiumObjectLockedOnFree() {
        TestAccount.entitlements.value = Entitlements.FREE
        open("Nesne free")
        addMenu("Nesneler")
        waitFor { exists("objects_sheet") }
        val locked = ComicAssets.props(ComicAssetKind.OBJECT, ComicStyle.COMIC, null).first { PremiumRules.propFeature(ComicAssetKind.OBJECT, it) != null }
        click("obj_cat_${locked.category}")
        waitFor { exists("obj_item_${locked.id}_locked") }
        click("obj_item_${locked.id}_locked")
        compose.waitForIdle()
        assertTrue(images().isEmpty())
    }

    @Test
    fun effectImagesTabInsertsEffect() {
        open("Efekt resmi v42")
        addMenu("Efektler")
        waitFor { exists("effects_sheet") }
        click("effects_tab_images")
        waitFor { exists("fximg") }
        click("fximg_style_manga")
        val fx = ComicAssets.props(ComicAssetKind.EFFECT, ComicStyle.MANGA, "patlama").first()
        click("fximg_cat_patlama")
        waitFor { exists("fximg_item_${fx.id}") }
        shot("v42-02-effect-images")
        click("fximg_item_${fx.id}")
        waitFor { !exists("effects_sheet") && images().size == 1 }
        val img = images().single()
        val w = img.points.maxOf { it.x } - img.points.minOf { it.x }
        val h = img.points.maxOf { it.y } - img.points.minOf { it.y }
        assertEquals(340f, maxOf(w, h), 3f)
    }

    @Test
    fun locationAndCharacterSheetsHaveThemePacks() {
        open("Tema v42")
        addMenu("Mekân arka planı")
        waitFor { exists("locations_sheet") }
        click("loc_style_cizgi_roman")
        click("loc_theme_kiyamet")
        val list = ComicAssets.backgrounds(ComicStyle.COMIC, null, "kiyamet")
        assertTrue(list.isNotEmpty())
        waitFor { exists("loc_${list.first().id}") }
        val other = ComicAssets.catalog.backgrounds.first { it.style == "cizgi_roman" && it !in list }
        assertFalse(exists("loc_${other.id}"))
        assertNotNull(ComicAssets.theme("kiyamet"))
    }
}
