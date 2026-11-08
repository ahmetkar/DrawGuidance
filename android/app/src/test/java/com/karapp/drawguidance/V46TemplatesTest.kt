package com.karapp.drawguidance

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.formula.FormulaCatalog
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.images.CanvasAssetStore
import com.karapp.drawguidance.data.local.CanvasAssetDao
import com.karapp.drawguidance.data.local.ContentCodec
import com.karapp.drawguidance.data.model.BrushType
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.Stroke
import com.karapp.drawguidance.data.repository.CanvasRepository
import com.karapp.drawguidance.data.templates.CanvasTemplates
import com.karapp.drawguidance.data.templates.TemplateMeta
import com.karapp.drawguidance.ui.drawing.ShapeRegistry
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicCover
import com.karapp.drawguidance.ui.screens.canvasedit.state.NotesPanels
import com.karapp.drawguidance.ui.screens.canvasedit.state.PanelSets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Uygulamadaki şablon dosyaları (testte assets klasöründen okunur). */
private val ASSETS = listOf(File("src/main/assets"), File("app/src/main/assets")).first { File(it, "templates").exists() || it.exists() }

private fun openAsset(path: String) = File(ASSETS, path).inputStream()

/**
 * v46 (1) hazır şablonlar: katalog (5 çizgi roman + 5 not alma, 7 dilde ad ve açıklama), dosyaların eksiksizliği,
 * içeriğin yalnız uygulamanın elemanlarından oluşması ve Free içerik kullanması, kurulumda resim kimliklerinin
 * yenilenmesi ve kapak başlığının tuvalin adı olması.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V46TemplateFilesTest {
    private val langs = setOf("tr", "en", "ar", "ja", "ko", "ru", "zh")

    init {
        CanvasTemplates.opener = ::openAsset
        CanvasTemplates.reset()
    }

    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    private fun meta(id: String): TemplateMeta = CanvasTemplates.parseMeta(File(ASSETS, CanvasTemplates.metaPath(id)).readText())
    private fun pages(id: String): List<List<Stroke>> = ContentCodec.decodeDocument(File(ASSETS, CanvasTemplates.contentPath(id)).readBytes()).pages

    @Test
    fun catalogHasComicNotesAndSimpleNotesTemplatesInSevenLanguages() {
        val all = CanvasTemplates.catalog.templates
        // v48 çizgi romanda üç boş şablon (çizgi roman, manga, webtoon; v47 hikâyeleri kaldırıldı), not almada beş,
        // basit not almada üç şablon
        assertEquals(listOf("comic_blank_pages", "comic_blank_manga", "comic_blank_webtoon"), CanvasTemplates.forKind(CanvasKind.COMIC).map { it.id })
        assertEquals(5, CanvasTemplates.forKind(CanvasKind.NOTES).size)
        assertEquals(listOf("simple_lesson", "simple_todo", "simple_week"), CanvasTemplates.forKind(CanvasKind.SIMPLE_NOTES).map { it.id })
        assertEquals(11, all.size)
        for (old in OLD_COMIC) {
            assertEquals(old, null, CanvasTemplates.find(old))
            assertFalse(old, File(ASSETS, "templates/$old").exists())
        }
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (t in all) {
            assertEquals(t.id, langs, t.names.keys)
            assertEquals(t.id, langs, t.hints.keys)
            assertTrue(t.names.values.all { it.isNotBlank() } && t.hints.values.all { it.isNotBlank() })
            assertTrue(t.canvasKind in CanvasTemplates.KINDS)
        }
        // v48 boş çizgi roman şablonları: 4 sayfa, her sayfa başka panel düzeninde, her sayfada balon
        for (t in CanvasTemplates.forKind(CanvasKind.COMIC)) {
            val doc = pages(t.id)
            assertEquals(t.id, 4, t.pages)
            assertEquals(t.id, 4, doc.size)
            val layouts = doc.map { page ->
                page.filter { PanelSets.isPanel(it) }.map { p -> p.points.map { (it.x / 10f).toInt() to (it.y / 10f).toInt() }.toSet() }.toSet()
            }
            assertTrue(t.id, layouts.all { it.isNotEmpty() })
            assertEquals("${t.id} düzenler farklı", 4, layouts.toSet().size)
            for ((i, page) in doc.withIndex()) assertTrue("${t.id} sayfa $i balon", page.any { it.text?.box?.balloon != null })
        }
        // Basit modlarda ve resimde şablon yok
        for (k in listOf(CanvasKind.ART, CanvasKind.SIMPLE_ART, CanvasKind.KIDS)) assertTrue(CanvasTemplates.forKind(k).isEmpty())
    }

    @Test
    fun everyTemplateHasCoverContentAndItsImages() {
        for (t in CanvasTemplates.catalog.templates) {
            val m = meta(t.id)
            assertEquals(t.id, t.width, m.width)
            assertEquals(t.id, t.height, m.height)
            val cover = File(ASSETS, CanvasTemplates.coverPath(t.id)).readBytes()
            val bmp = android.graphics.BitmapFactory.decodeByteArray(cover, 0, cover.size)
            assertNotNull("${t.id} kapak", bmp)
            assertTrue("${t.id} kapak küçük olmalı: ${cover.size}", cover.size < 120_000)
            val doc = pages(t.id)
            assertEquals(t.id, t.pages, doc.size)
            assertTrue(t.id, m.currentPage in doc.indices)
            assertTrue("${t.id} boş", doc[m.currentPage].isNotEmpty())
            val used = doc.flatten().mapNotNull { it.assetId }.toSet()
            assertEquals(t.id, used, m.assets.map { it.id }.toSet())
            for (a in m.assets) {
                val bytes = File(ASSETS, CanvasTemplates.assetPath(t.id, a.file)).readBytes()
                assertNotNull("${t.id}/${a.file}", android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                assertTrue(a.w > 0 && a.h > 0)
            }
        }
    }

    @Test
    fun contentUsesOnlyAppElements() {
        val allowed = setOf(BrushType.TEXT, BrushType.SHAPE, BrushType.DIAGRAM, BrushType.FORMULA, BrushType.IMAGE, BrushType.INK_PEN)
        for (t in CanvasTemplates.catalog.templates) {
            for (s in pages(t.id).flatten()) {
                assertTrue("${t.id}: ${s.brushType}", s.brushType in allowed)
                s.shape?.let { sh ->
                    val known = sh.kind == PanelSets.KIND || sh.kind == NotesPanels.KIND || ShapeRegistry.isGraph(sh.kind) ||
                        ShapeRegistry.isLibrary(sh.kind) && com.karapp.drawguidance.content.library.ShapeLibrary.find(sh.kind) != null ||
                        runCatching { com.karapp.drawguidance.ui.drawing.ShapeKind.valueOf(sh.kind) }.isSuccess
                    assertTrue("${t.id}: şekil ${sh.kind}", known)
                }
                s.formula?.templateId?.takeIf { it.isNotEmpty() }?.let { assertNotNull("${t.id}: formül $it", FormulaCatalog.find(it)) }
                // Hız/odak çizgileri yalnız Free türlerden
                s.params?.comicLines?.let { cl ->
                    assertFalse("${t.id}: ${cl.kind}", com.karapp.drawguidance.data.billing.PremiumRules.isAdvancedLines(com.karapp.drawguidance.content.comic.ComicLinesKind.valueOf(cl.kind)))
                }
            }
            val kind = t.canvasKind!!
            val types = pages(t.id).flatten().map { it.brushType }.toSet()
            if (kind == CanvasKind.COMIC) {
                assertTrue("${t.id}: paneller", pages(t.id).flatten().any { PanelSets.isPanel(it) })
                // v48 arka plan ve karakter yok: yalnız paneller ve balonlar
                assertFalse("${t.id}: resim yok", BrushType.IMAGE in types)
                assertTrue("${t.id}: balonlar", pages(t.id).flatten().any { it.text?.box?.balloon != null })
                assertTrue("${t.id}: yalnız panel ve balon", pages(t.id).flatten().all { PanelSets.isPanel(it) || it.text?.box?.balloon != null })
            } else {
                assertTrue("${t.id}: yazılar", BrushType.TEXT in types)
            }
        }
        // Not şablonlarında formül, grafik, diyagram ve bilim şekli kullanılmış
        val notes = CanvasTemplates.forKind(CanvasKind.NOTES).flatMap { pages(it.id).flatten() }
        assertTrue(notes.any { it.brushType == BrushType.FORMULA })
        assertTrue(notes.any { it.brushType == BrushType.DIAGRAM })
        assertTrue(notes.any { it.shape?.kind?.let(ShapeRegistry::isGraph) == true })
        assertTrue(notes.any { it.shape?.kind?.let(ShapeRegistry::isLibrary) == true })
        assertTrue(notes.any { NotesPanels.isNotesPanel(it) })
    }

    @Test
    fun comicTemplatesHaveNoBackgroundsOrCharacters() {
        // v48 çizgi roman şablonları boş: resim (karakter, mekân) ve kâğıt arka planı yok
        for (t in CanvasTemplates.forKind(CanvasKind.COMIC)) {
            val m = meta(t.id)
            assertTrue(t.id, m.assets.isEmpty())
            assertTrue(t.id, File(ASSETS, "templates/${t.id}").listFiles()!!.none { it.name.startsWith("img_") })
        }
        // Manga sağdan sola: sayfanın en üstteki kutusu (anlatı) sağ yarıda; çizgi romanda sol yarıda
        fun topmost(id: String) = pages(id)[0].filter { it.text?.box?.balloon != null }.minBy { it.points.minOf { p -> p.y } }
        fun cx(s: Stroke) = (s.points.minOf { it.x } + s.points.maxOf { it.x }) / 2f
        assertTrue(cx(topmost("comic_blank_manga")) > 600f)
        assertTrue(cx(topmost("comic_blank_pages")) < 600f)
    }

    @Test
    fun prepareGivesNewAssetIdsAndRetitlesCover() {
        // v48 boş çizgi roman şablonunda kapak ve resim yok: kurulum içeriği değiştirmez
        val t = CanvasTemplates.find("comic_blank_pages")!!
        val m = meta(t.id)
        assertEquals(null, m.titleId)
        val p1 = CanvasTemplates.prepare(t.id, "Benim Hikâyem")!!
        assertTrue(p1.assets.isEmpty())
        assertEquals(pages(t.id).flatten().map { it.text?.content }, ContentCodec.decodeDocument(p1.content).pages.flatten().map { it.text?.content })
        // Not şablonunda başlık değişmez (kapak yok)
        val n = CanvasTemplates.prepare("notes_cornell", "Fizik")!!
        assertEquals(ContentCodec.decodeDocument(File(ASSETS, CanvasTemplates.contentPath("notes_cornell")).readBytes()).pages.flatten().map { it.text?.content },
            ContentCodec.decodeDocument(n.content).pages.flatten().map { it.text?.content })
    }

    @Test
    fun missingTemplateFallsBack() {
        assertEquals(null, CanvasTemplates.prepare("yok_boyle", "x"))
    }
}

/** v46 (1) yeni tuval formunda şablonlar: kapak kartları, seçim, şablondan tuval ve düzenleyicide devam. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestDrawGuidanceApp::class, qualifiers = "tr-w411dp-h891dp-xxhdpi")
class V46TemplateFormTest : V32EditorBase() {
    private fun repo() = GlobalContext.get().get<CanvasRepository>()

    private fun openForm() {
        waitForText("Henüz bir tuvaliniz yok", substring = true)
        compose.onNodeWithContentDescription("Yeni Tuval").performClick()
        waitForText("Yeni Tuval Oluştur")
    }

    private fun chooseKind(kind: CanvasKind) {
        compose.onNodeWithTag("form_kind_${kind.name}").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun comicAndNotesShowTemplateCardsOthersDoNot() {
        openForm()
        chooseKind(CanvasKind.ART)
        assertFalse(exists("template_picker"))
        chooseKind(CanvasKind.COMIC)
        waitFor { exists("template_picker") }
        assertTrue(exists("template_card_blank"))
        for (t in CanvasTemplates.forKind(CanvasKind.COMIC)) assertTrue(t.id, exists("template_card_${t.id}"))
        chooseKind(CanvasKind.NOTES)
        waitFor { exists("template_card_notes_cornell") }
        for (t in CanvasTemplates.forKind(CanvasKind.NOTES)) assertTrue(t.id, exists("template_card_${t.id}"))
        assertFalse(exists("template_card_comic_blank_pages"))
        // Kart adları dilde
        assertTrue(compose.onAllNodes(hasText("Cornell ders notu")).fetchSemanticsNodes().isNotEmpty())
        // v47 basit not almada üç şablon
        chooseKind(CanvasKind.SIMPLE_NOTES)
        waitFor { exists("template_card_simple_lesson") }
        for (t in CanvasTemplates.forKind(CanvasKind.SIMPLE_NOTES)) assertTrue(t.id, exists("template_card_${t.id}"))
        assertFalse(exists("template_card_notes_cornell"))
        shot("v46-template-form")
    }

    @Test
    fun choosingTemplateHidesSizeAndCreatesCanvasFromIt() {
        openForm()
        chooseKind(CanvasKind.COMIC)
        waitFor { exists("template_card_comic_blank_manga") }
        assertTrue(exists("size_presets"))
        compose.onNodeWithTag("template_card_comic_blank_manga").performScrollTo().performClick()
        waitFor { !exists("size_presets") }
        assertTrue(text("template_hint").contains("Sağdan sola"))
        compose.onNode(hasTestTag("canvas_form_submit")).performScrollTo().performClick()
        waitFor(20_000) { exists("drawing_canvas") }
        val c = vm().canvas.value!!
        val meta = CanvasTemplates.parseMeta(File(ASSETS, CanvasTemplates.metaPath("comic_blank_manga")).readText())
        assertEquals(CanvasKind.COMIC, c.kind)
        assertEquals(meta.width, c.width)
        assertEquals(meta.height, c.height)
        assertEquals(CanvasTemplates.find("comic_blank_manga")!!.pages, c.pages.size)
        assertEquals(meta.currentPage, c.currentPage)
        // Resimler yeni tuvale yeni kimliklerle kaydedildi
        val ids = c.pages.flatten().mapNotNull { it.assetId }.toSet()
        assertEquals(meta.assets.size, ids.size)
        val saved = runBlocking { GlobalContext.get().get<CanvasAssetDao>().idsForCanvas(c.localId) }.toSet()
        assertTrue(saved.containsAll(ids))
        assertTrue(ids.none { id -> meta.assets.any { it.id == id } })
        // v48 boş şablon: resim yok, balonlar var
        assertTrue(c.pages.flatten().any { it.text?.box?.balloon != null })
        // Düzenlemeye devam: geri al çalışır, yeni çizim eklenir
        val before = c.strokes.size
        compose.runOnIdle { vm().tutorialHost.commitElements(listOf(c.strokes.first { it.text?.box?.balloon != null }.copy(id = java.util.UUID.randomUUID().toString()))) }
        waitFor { vm().canvas.value!!.strokes.size == before + 1 }
        settleFor(800)
        shot("v46-template-opened")
    }

    @Test
    fun blankStaysDefaultAndNotesTemplateWorks() {
        openForm()
        chooseKind(CanvasKind.NOTES)
        waitFor { exists("template_card_notes_math") }
        compose.onNodeWithTag("template_card_notes_math").performScrollTo().performClick()
        waitFor { !exists("size_presets") }
        // Boş'a dönünce boyut ve arka plan geri gelir
        compose.onNodeWithTag("template_card_blank").performScrollTo().performClick()
        waitFor { exists("size_presets") }
        compose.onNodeWithTag("template_card_notes_mindmap").performScrollTo().performClick()
        compose.onNode(hasTestTag("canvas_form_submit")).performScrollTo().performClick()
        waitFor(20_000) { exists("drawing_canvas") }
        val c = vm().canvas.value!!
        assertEquals(CanvasKind.NOTES, c.kind)
        assertTrue(c.strokes.any { it.brushType == BrushType.DIAGRAM })
        assertEquals("note.dots", c.background.patternId)
        val all = runBlocking { repo().observeCanvases().first() }
        assertEquals(1, all.size)
    }
}

/**
 * v46 düzeltme: sayfanın sonuna eklenen panel (Panel ekle) eski son panelin alanına düştüğü için onun içeriği sayılıp
 * küçülerek içine taşınıyordu (4 kare şablonu üretilirken bulundu). Yeni panel artık kendi yerinde ve tam boyda.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V46PanelAppendTest {
    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun appendedPanelKeepsItsOwnPlaceAndContentStillFollows() {
        val w = 1200f; val h = 1800f
        val page = com.karapp.drawguidance.content.comic.ComicTemplates.byId("three_rows")!!.page
        val style = com.karapp.drawguidance.data.model.ShapeStyle("#111111", 6f)
        val panels = PanelSets.build(page, "set", w, h, style, 0)
        // Son panelin içinde bir yazı (panelle birlikte taşınmalı)
        val last = page.shapes(w, h).last().bounds()
        val inside = Stroke("t", listOf(com.karapp.drawguidance.data.model.StrokePoint(last[0] + 100f, last[1] + 100f), com.karapp.drawguidance.data.model.StrokePoint(last[0] + 300f, last[1] + 160f)),
            "#000000", BrushType.INK_PEN, 4f, 1f, panels.size)
        val strokes = panels + inside
        val found = PanelSets.find(strokes)!!
        val (next, leaf) = found.page.append()
        val added = HashMap<Int, String>()
        val out = PanelSets.apply(strokes, found, next, w, h, style, follow = true, added = added)
        val fresh = out.first { it.id == added[leaf] }
        val want = next.shapes(w, h).first { it.id == leaf }.bounds()
        val got = floatArrayOf(fresh.points.minOf { it.x }, fresh.points.minOf { it.y }, fresh.points.maxOf { it.x }, fresh.points.maxOf { it.y })
        for (i in 0..3) assertEquals("kenar $i", want[i], got[i], 1f)
        assertEquals(4, PanelSets.find(out)!!.strokes.size)
        // Yazı üçüncü panelin yeni yerine taşındı (dördüncünün içine değil)
        val third = next.shapes(w, h).first { it.id == found.page.leafIds().last() }.bounds()
        val t = out.first { it.id == "t" }
        val cy = (t.points.minOf { it.y } + t.points.maxOf { it.y }) / 2f
        assertTrue("$cy ${third.toList()}", cy in third[1]..third[3])
    }
}

/** v46 (2) Lunara kaldırıldı: uygulamanın kataloğunda yok, eski sunucudan inmiş bir paket getirse de görünmez. */
class V46LunaraRemovedTest {
    @Test
    fun lunaraNeverShows() {
        val base = ComicAssets.parse(File(ASSETS, ComicAssets.CATALOG).readText())
        assertTrue(base.characters.none { it.id.contains("lunara") })
        assertFalse(File(ASSETS, "comic/chars").listFiles()!!.any { it.name.contains("lunara") })
        fun c(id: String) = com.karapp.drawguidance.data.comicassets.ComicCharacter(id, "manga", "fantastik", mapOf("tr" to id), emptyList())
        val merged = com.karapp.drawguidance.data.comicassets.ComicPacks.merge(base, com.karapp.drawguidance.data.comicassets.ComicCatalog(characters = listOf(c("elf_lunara"), c("yeni_kahraman"))))
        assertTrue(merged.characters.none { it.id == "elf_lunara" })
        assertTrue(merged.characters.any { it.id == "yeni_kahraman" })
        // Yerine gelecek üç elf büyücü Premium
        for (id in com.karapp.drawguidance.data.billing.PremiumRules.UPCOMING_PREMIUM_CHARACTERS) {
            assertEquals(com.karapp.drawguidance.data.billing.PremiumFeature.COMIC_CHARACTERS_ADVANCED, com.karapp.drawguidance.data.billing.PremiumRules.characterFeature(c(id)))
        }
    }
}

/**
 * v46 sahibin karakterler.zip sayfalarından eklenen 13 anime karakteri: her sayfadan ifadeler (büstler) ve alt
 * satırdaki poz, aksiyon ve günlük figürleri ayrı çeşit; "premium" klasöründekiler Premium, "free"dekiler Free; 7 dilde
 * ad, saydam WebP ve küçük resim; sunucunun taban paket listesinde de var.
 */
class V46NewCharactersTest {
    private val catalog = ComicAssets.parse(File(ASSETS, ComicAssets.CATALOG).readText())
    private val premium = listOf(
        "elf_buyucu_erkek", "elf_buyucu_kadin", "suikastci_kadin", "suikastci_erkek", "kutsal_sovalye_kadin",
        "kutsal_sovalye_erkek", "orman_okcusu_erkek", "orman_okcusu_kadin", "olu_cagiran_erkek", "olu_cagiran_kadin",
    )
    private val free = listOf("simyaci_kadin", "simyaci_erkek", "orman_ozani")

    @Test
    fun charactersSplitIntoExpressionsPosesAndActions() {
        for (id in premium + free) {
            val c = catalog.characters.singleOrNull { it.id == id } ?: throw AssertionError("$id katalogda yok")
            assertEquals(id, "manga", c.style)
            assertEquals(id, setOf("tr", "en", "ar", "ja", "ko", "ru", "zh"), c.names.keys)
            assertTrue(id, "fantastik" in c.packs)
            val groups = c.variants.groupingBy { it.group }.eachCount()
            assertEquals(id, if (id == "orman_ozani") 6 else 5, groups["ifade"])
            assertEquals(id, 1, groups["poz"]); assertEquals(id, 1, groups["aksiyon"]); assertEquals(id, 1, groups["gunluk"])
            for (v in c.variants) {
                for (path in listOf(v.file, v.thumb)) {
                    val bytes = File(ASSETS, path).readBytes()
                    // WebP (RIFF....WEBP) ve saydamlık (VP8X/ALPH)
                    assertEquals(path, "RIFF", String(bytes, 0, 4)); assertEquals(path, "WEBP", String(bytes, 8, 4))
                    assertTrue(path, String(bytes, 12, 4) == "VP8X")
                }
                assertTrue("${v.file} ${v.w}x${v.h}", maxOf(v.w, v.h) in 200..448)
                // Büstler enine yakın, poz figürü uzun ve dar
                if (v.group == "poz") assertTrue("${v.file} poz uzun olmalı", v.h > v.w * 1.4)
                if (v.group == "ifade") assertTrue("${v.file} büst", v.w.toFloat() / v.h in 0.6f..1.6f)
            }
        }
        assertTrue(catalog.characters.none { it.id.contains("lunara") })
    }

    @Test
    fun premiumFolderIsPremiumFreeFolderIsFree() {
        fun c(id: String) = catalog.characters.single { it.id == id }
        for (id in premium) assertEquals(id, com.karapp.drawguidance.data.billing.PremiumFeature.COMIC_CHARACTERS_ADVANCED, com.karapp.drawguidance.data.billing.PremiumRules.characterFeature(c(id)))
        for (id in free) assertEquals(id, null, com.karapp.drawguidance.data.billing.PremiumRules.characterFeature(c(id)))
    }

    @Test
    fun serverBasePackListsTheNewVariants() {
        val root = listOf(File("../../backend"), File("../backend"), File("backend")).first { File(it, "src/main/resources/base-packages/manifest.json").exists() }
        val text = File(root, "src/main/resources/base-packages/manifest.json").readText()
        for (id in premium + free) for (v in catalog.characters.single { it.id == id }.variants) {
            assertTrue("$id-${v.id}", text.contains("\"id\": \"$id-${v.id}\""))
        }
        assertFalse(text.contains("lunara"))
    }
}

/** v47 kaldırılan eski çizgi roman şablonları. */
private val OLD_COMIC = listOf("comic" + "_action", "comic_manga", "comic_webtoon", "comic_yonkoma", "comic_dialogue", "comic_ganryujima", "comic_ronin", "comic_urashima")
