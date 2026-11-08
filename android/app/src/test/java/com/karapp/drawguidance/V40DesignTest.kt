package com.karapp.drawguidance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.CanvasEntity
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.data.model.CanvasUi
import com.karapp.drawguidance.data.repository.CanvasTimeRepository
import com.karapp.drawguidance.data.repository.EditTimeTracker
import com.karapp.drawguidance.ui.components.TimeSpent
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasSizing
import com.karapp.drawguidance.ui.screens.canvaslist.components.CanvasStats
import com.karapp.drawguidance.ui.screens.canvaslist.components.SizeIssue
import com.karapp.drawguidance.ui.screens.canvaslist.components.SizeUnit
import com.karapp.drawguidance.ui.screens.home.HomeViewModel
import com.karapp.drawguidance.ui.theme.HighContrast
import com.karapp.drawguidance.ui.theme.NeuSurfaceStyle
import com.karapp.drawguidance.ui.theme.ThemeMode
import com.karapp.drawguidance.ui.theme.ThemePalette
import com.karapp.drawguidance.ui.theme.ThemePrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v40 (1) gölgesiz kutular: ince yarı saydam kenar; basılı / seçili hâller kenar ve dolguyla ayrışır. */
class V40SurfaceStyleTest {
    @Test
    fun bordersInsteadOfShadows() {
        for (palette in ThemePalette.entries) for (dark in listOf(false, true)) {
            val c = palette.colors(dark)
            val raised = NeuSurfaceStyle.of(c, inset = false, selected = false)
            val pressed = NeuSurfaceStyle.of(c, inset = true, selected = false)
            val selected = NeuSurfaceStyle.of(c, inset = false, selected = true)
            val name = "$palette koyu=$dark"
            // Kutu: zemin dolgusu + 1 dp yarı saydam içerik renginde kenar
            assertEquals(name, c.background, raised.fill)
            assertEquals(name, 1.dp, raised.borderWidth)
            assertTrue(name, raised.border.alpha in 0.05f..0.3f)
            assertEquals(name, c.content.copy(alpha = 1f), raised.border.copy(alpha = 1f))
            // Basılı: dolgu koyulaşır, kenar belirginleşir
            assertTrue(name, pressed.fill != raised.fill)
            assertTrue(name, pressed.border.alpha > raised.border.alpha)
            // Seçili: vurgu renginde daha kalın kenar
            assertEquals(name, c.accent, selected.border)
            assertTrue(name, selected.borderWidth > raised.borderWidth)
            assertTrue(name, selected.fill != raised.fill)
            // Yüzeyi olmayan (elevation 0): kenar yok
            assertEquals(0.dp, NeuSurfaceStyle.of(c, false, false, outlined = false).borderWidth)
            // Yüksek karşıtlık: opak kenar ve kalın seçim
            val hc = HighContrast.apply(c)
            val hcRaised = NeuSurfaceStyle.of(hc, inset = false, selected = false)
            assertEquals(name, 1f, hcRaised.border.alpha)
            assertTrue(name, hcRaised.borderWidth > raised.borderWidth)
            assertEquals(name, 2.5.dp, NeuSurfaceStyle.of(hc, inset = false, selected = true).borderWidth)
        }
    }
}

/** v40 (6) tema: açık / koyu / sistem ve yüksek karşıtlık (WCAG AAA yazı, AA vurgu üstü). */
class V40HighContrastTest {
    @Test
    fun themeModeResolves() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.of(null))
        assertEquals(ThemeMode.DARK, ThemeMode.of("DARK"))
        assertFalse(ThemeMode.LIGHT.isDark(true))
        assertTrue(ThemeMode.DARK.isDark(false))
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        assertFalse(ThemeMode.SYSTEM.isDark(false))
    }

    @Test
    fun highContrastPalettesMeetWcag() {
        for (palette in ThemePalette.entries) for (dark in listOf(false, true)) {
            val base = palette.colors(dark)
            val c = ThemePrefs.resolve(palette, dark, highContrast = true)
            val name = "$palette koyu=$dark"
            assertTrue(name, c.highContrast)
            assertEquals(name, dark, c.isDark)
            assertEquals("zemin korunur", base.background, c.background)
            assertEquals(name, if (dark) Color.White else Color.Black, c.content)
            assertTrue("$name yazı", HighContrast.contrast(c.background, c.content) >= 7f)
            assertTrue("$name ikincil yazı", HighContrast.contrast(c.background, c.contentMuted) >= 7f)
            assertTrue("$name vurgu", HighContrast.contrast(c.background, c.accent) >= 7f)
            assertTrue("$name vurgu üstü", HighContrast.contrast(c.accent, c.onAccent) >= 4.5f)
            assertTrue("$name hata", HighContrast.contrast(c.background, c.danger) >= 4.5f)
            // Normal paletten daha güçlü
            assertTrue(name, HighContrast.contrast(c.background, c.contentMuted) > HighContrast.contrast(base.background, base.contentMuted))
            assertFalse(ThemePrefs.resolve(palette, dark, highContrast = false).highContrast)
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V40ThemePrefsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun reset() {
        ThemePrefs.selectMode(ctx, ThemeMode.SYSTEM)
        ThemePrefs.setHighContrast(ctx, false)
    }

    @Test
    fun modeAndHighContrastPersist() {
        ThemePrefs.selectMode(ctx, ThemeMode.DARK)
        ThemePrefs.setHighContrast(ctx, true)
        assertEquals(ThemeMode.DARK, ThemePrefs.mode.value)
        assertTrue(ThemePrefs.highContrast.value)
        val p = ctx.getSharedPreferences("dg_theme", android.content.Context.MODE_PRIVATE)
        assertEquals("DARK", p.getString("mode", null))
        assertTrue(p.getBoolean("high_contrast", false))
    }
}

/** v40 (3) etkin düzenleme süresi: ön planda ve etkileşimde sayılır, 60 sn girdisizlikte ve arka planda durur. */
class V40EditTimeTrackerTest {
    @Test
    fun countsOnlyActiveForegroundTime() {
        val t = EditTimeTracker(idleMs = 60_000)
        t.onInput(0)
        assertEquals("ön planda değilken sayılmaz", 0, t.pending(10_000))
        t.onForeground(1_000)
        t.onInput(11_000)
        assertEquals(20_000, t.pending(21_000))
        // 60 sn girdisizlik: son girdiden 60 sn sonrası sayılmaz
        assertEquals(10_000L + 60_000L, t.pending(500_000))
        // Uzun aradan sonra yeni girdi yeni bölüm başlatır
        t.onInput(600_000)
        t.onInput(605_000)
        assertEquals(70_000L + 5_000L, t.pending(605_000))
        // Arka plan: o anda durur, ön plana dönmeden girdi sayılmaz
        t.onBackground(610_000)
        t.onInput(620_000)
        assertEquals(70_000L + 10_000L, t.pending(700_000))
        t.onForeground(800_000)
        assertEquals(80_000L + 30_000L, t.pending(830_000))
    }

    @Test
    fun drainResetsAndContinues() {
        val t = EditTimeTracker(idleMs = 60_000)
        t.onForeground(0)
        t.onInput(10_000)
        assertEquals(15_000, t.drain(15_000))
        assertEquals(0, t.pending(15_000))
        t.onInput(20_000)
        assertEquals(10_000, t.drain(25_000))
        // Boşta kalındıktan sonra boşaltma: yalnız 60 sn'ye kadarki kısım
        assertEquals(55_000, t.drain(200_000))
        assertFalse(t.running)
        assertEquals(0, t.drain(300_000))
        t.onBackground(400_000)
        assertEquals(0, t.drain(500_000))
    }

    @Test
    fun timeText() {
        assertNull(TimeSpent.parts(59_999))
        assertEquals(0 to 12, TimeSpent.parts(12 * 60_000L + 5_000))
        assertEquals(2 to 0, TimeSpent.parts(120 * 60_000L))
        assertEquals(1 to 5, TimeSpent.parts(65 * 60_000L))
    }
}

/** v40 (3) Ana sayfa: en çok vakit geçirilen tuvaller süreye göre; süresi olmayan ve silinen tuval yok. */
class V40HomeOrderingTest {
    private fun c(id: String, updated: Long) = CanvasUi(id, id, 100, 100, "#FFFFFF", false, emptyList(), 0, updated)

    @Test
    fun ordersByTimeSpent() {
        val canvases = listOf(c("a", 5), c("b", 4), c("c", 3), c("d", 2), c("e", 9))
        val times = mapOf("a" to 1_000L, "b" to 90_000L, "c" to 30_000L, "d" to 30_000L, "gone" to 999_999L, "e" to 0L)
        val most = HomeViewModel.mostTime(canvases, times, limit = 3)
        assertEquals(listOf("b", "c", "d"), most.map { it.canvas.localId })
        assertEquals(listOf(90_000L, 30_000L, 30_000L), most.map { it.timeSpentMs })
        assertEquals(listOf("b", "c", "d", "a"), HomeViewModel.mostTime(canvases, times, 10).map { it.canvas.localId })
        assertTrue(HomeViewModel.mostTime(canvases, emptyMap(), 10).isEmpty())
    }
}

/** v40 (2) Tuvallerim özeti. */
class V40CanvasStatsTest {
    @Test
    fun statsCountKindsWeekAndTime() {
        val day = 24 * 60 * 60 * 1000L
        val now = 100 * day
        val list = listOf(
            CanvasUi("a", "a", 1, 1, "#FFFFFF", false, emptyList(), 0, now - day, kind = CanvasKind.ART),
            CanvasUi("b", "b", 1, 1, "#FFFFFF", false, emptyList(), 0, now - 10 * day, kind = CanvasKind.NOTES),
            CanvasUi("c", "c", 1, 1, "#FFFFFF", false, emptyList(), 0, now - 2 * day, kind = CanvasKind.NOTES),
        )
        val s = CanvasStats.of(list, mapOf("a" to 60_000L, "c" to 30_000L, "x" to 5L), now)
        assertEquals(3, s.total)
        assertEquals(listOf(CanvasKind.ART to 1, CanvasKind.NOTES to 2), s.perKind)
        // v42 gruplar: resim, not, çizgi roman
        assertEquals(1, s.drawings); assertEquals(2, s.notes); assertEquals(0, s.comics)
        assertEquals(2, s.editedThisWeek)
        assertEquals(90_000L, s.totalTimeMs)
    }
}

/** v40 (5) türe uygun hazır boyutlar ve px / in / cm özel boyut. */
class V40CanvasSizingTest {
    @Test
    fun presetsSuitTheMode() {
        for (k in CanvasKind.entries) {
            val first = CanvasSizing.presets(k).first()
            assertEquals("$k öntanımlı", k.defaultSize.width to k.defaultSize.height, first.width to first.height)
            CanvasSizing.presets(k).forEach { assertNull("$k ${it.key}", CanvasSizing.validate(it.width, it.height, Long.MAX_VALUE)) }
        }
        val art = CanvasSizing.presets(CanvasKind.ART).map { it.key }
        assertTrue(art.containsAll(listOf("square", "wallpaper", "a4_300")))
        assertTrue(CanvasSizing.presets(CanvasKind.NOTES).map { it.key }.containsAll(listOf("a4", "a5", "letter")))
        val comic = CanvasSizing.presets(CanvasKind.COMIC).associateBy { it.key }
        assertEquals(1988 to 3075, comic.getValue("us_comic").let { it.width to it.height })
        assertEquals(2150 to 3035, comic.getValue("manga_b5").let { it.width to it.height })
        assertTrue(CanvasSizing.presets(CanvasKind.KIDS).size <= 3)
        assertEquals("a4_300", CanvasSizing.match(CanvasKind.ART, 2480, 3508))
        assertEquals(CanvasSizing.CUSTOM, CanvasSizing.match(CanvasKind.NOTES, 2480, 3508))
    }

    @Test
    fun unitsConvertAndValidate() {
        assertEquals(1988, CanvasSizing.toPx(6.625, SizeUnit.IN, 300))
        assertEquals(2480, CanvasSizing.toPx(21.0, SizeUnit.CM, 300))
        assertEquals(1200, CanvasSizing.toPx(1200.4, SizeUnit.PX, 72))
        assertEquals("8.5", CanvasSizing.format(CanvasSizing.fromPx(2550, SizeUnit.IN, 300), SizeUnit.IN))
        assertEquals(2.5, CanvasSizing.parse("2,5")!!, 1e-9)
        assertNull(CanvasSizing.parse("abc"))
        assertNull(CanvasSizing.parse("0"))
        assertEquals(SizeIssue.TOO_SMALL, CanvasSizing.validate(10, 500, Long.MAX_VALUE))
        assertEquals(SizeIssue.TOO_LARGE, CanvasSizing.validate(9000, 500, Long.MAX_VALUE))
        assertEquals(SizeIssue.MEMORY, CanvasSizing.validate(4000, 4000, 10_000_000L))
        assertNull(CanvasSizing.validate(4000, 4000, 16_000_000L))
        // Bellek sınırı: 256 MB yığında 16 MP, en çok 8192²
        assertEquals(16L shl 20, CanvasSizing.maxPixels(256L shl 20))
        assertEquals(8192L * 8192, CanvasSizing.maxPixels(Long.MAX_VALUE))
    }
}

/** v40 (3) Room 18 → 19: `canvas_time` tablosu; süre atomik olarak eklenir, eski veriler korunur. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V40CanvasTimeDbTest {
    @Test
    fun migration18to19CreatesCanvasTime() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "mig19-test.db"
        ctx.deleteDatabase(name)
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().build().apply {
            runBlocking { canvasDao().upsert(CanvasEntity("c1", "Eski", 100, 100, "#FFFFFF", false, ByteArray(0), 1, 2)) }
            openHelper.writableDatabase.apply {
                execSQL("DROP TABLE `canvas_time`")
                version = 18
            }
            close()
        }
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        runBlocking {
            assertEquals("Eski", db.canvasDao().getByLocalId("c1")!!.name)
            val repo = CanvasTimeRepository(db.canvasTimeDao())
            repo.add("c1", 1_500, now = 10)
            repo.add("c1", 2_500, now = 20)
            repo.add("gone", 100, now = 30)
            assertEquals(mapOf("c1" to 4_000L, "gone" to 100L), repo.observe().first())
            assertEquals(20L, db.canvasTimeDao().get("c1")!!.updatedAt)
            repo.prune()
            assertEquals(mapOf("c1" to 4_000L), repo.observe().first())
        }
        // v40 hesap dalı 19 → 20 (abonelik ayrıntıları) zincirde; göç sonrası sürüm 20
        assertEquals(23, db.openHelper.readableDatabase.version)
        db.close()
        ctx.deleteDatabase(name)
    }
}
