package com.karapp.drawguidance

import android.content.Context
import android.view.View
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.content.shapes.ShapeTemplates
import com.karapp.drawguidance.data.local.AppDatabase
import com.karapp.drawguidance.data.local.LanguageSettingsDao
import com.karapp.drawguidance.i18n.AppLanguage
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.i18n.LanguageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** v34 ilk açılışta dil: önce sistemin dili, sonra ülkesi, ikisi de eşleşmezse İngilizce. */
class V34LanguageDetectTest {
    @Test
    fun detectsBySystemLanguageThenCountryElseEnglish() {
        assertEquals(AppLanguage.TR, AppLanguage.detect("tr", "TR"))
        assertEquals(AppLanguage.EN, AppLanguage.detect("en", "TR"))
        assertEquals(AppLanguage.ZH, AppLanguage.detect("zh", "TW"))
        assertEquals(AppLanguage.JA, AppLanguage.detect("ja", "JP"))
        assertEquals(AppLanguage.RU, AppLanguage.detect("ru", "UA"))
        assertEquals(AppLanguage.KO, AppLanguage.detect("ko", "KR"))
        assertEquals(AppLanguage.AR, AppLanguage.detect("ar", "EG"))
        // Dil desteklenmiyor: ülkeye göre
        assertEquals(AppLanguage.TR, AppLanguage.detect("de", "TR"))
        assertEquals(AppLanguage.JA, AppLanguage.detect("fr", "JP"))
        assertEquals(AppLanguage.AR, AppLanguage.detect("fa", "AE"))
        assertEquals(AppLanguage.RU, AppLanguage.detect("uk", "KZ"))
        // SIM/şebeke ülkesi de bakılır
        assertEquals(AppLanguage.KO, AppLanguage.detect("es", "", "kr"))
        // Hiçbiri eşleşmez: İngilizce
        assertEquals(AppLanguage.EN, AppLanguage.detect("de", "DE"))
        assertEquals(AppLanguage.EN, AppLanguage.detect("es", "MX", null))
        assertEquals(AppLanguage.EN, AppLanguage.detect(null))
    }
}

/** v34 koddaki Türkçe metinlerin çeviri tabloları: yükleme, kapsam, yer tutucular ve arama kuralları. */
class V34I18nTablesTest {
    @After fun reset() = I18n.setLanguage("tr")

    @Test
    fun tablesCoverCatalogNamesInEveryLanguage() {
        val names = (ShapeTemplates.all.map { it.name } + Catalog3D.all.map { it.name }).toSet()
        assertTrue("adlar: ${names.size}", names.size > 300)
        for (lang in AppLanguage.entries.filter { it != AppLanguage.TR }) {
            val table = I18n.load(lang.code)
            assertTrue("${lang.code} tablosu: ${table.size}", table.size > 2500)
            I18n.setLanguage(lang.code)
            // Latin olmayan yazılarda her ad değişmeli; İngilizce/Rusçada da neredeyse hepsi
            val same = names.filter { I18n.tr(it) == it }
            val allowed = if (lang == AppLanguage.EN) names.size / 50 else names.size / 200
            assertTrue("${lang.code} çevrilmeyen adlar (${same.size}): ${same.take(20)}", same.size <= allowed)
        }
    }

    @Test
    fun placeholdersSurviveAndLookupRules() {
        val ph = Regex("\\{\\d+\\}|%(\\d+\\$)?[sdf]")
        for (lang in AppLanguage.entries.filter { it != AppLanguage.TR }) {
            I18n.load(lang.code).forEach { (k, v) ->
                assertEquals("${lang.code}: $k", ph.findAll(k).map { it.value }.sorted().toList(), ph.findAll(v).map { it.value }.sorted().toList())
            }
        }
        I18n.setLanguage("en")
        val en = I18n.load("en")
        assertEquals(en.getValue("Katman {0}").replace("{0}", "3"), I18n.tr("Katman 3"))
        assertEquals(en.getValue("Ok (sağa)"), I18n.tr("Ok (sağa)"))
        assertEquals(I18n.tr("Ok (sağa)"), I18n.tr("  Ok (sağa) ").trim())
        assertEquals(I18n.trf("{0} sayfa", 4), en.getValue("{0} sayfa").replace("{0}", "4"))
        assertTrue(I18n.trf("{0} sayfa", 4).contains("4"))
        // Kullanıcının yazdığı metin değişmez
        assertEquals("Benim tuvalim xyz", I18n.tr("Benim tuvalim xyz"))
        I18n.setLanguage("tr")
        assertEquals("Ok (sağa)", I18n.tr("Ok (sağa)"))
        assertEquals("3 sayfa", I18n.trf("{0} sayfa", 3))
    }
}

/**
 * v34 dil ayarı: ilk açılışta veritabanına yazılır; Ayarlar'daki dil kartında yedi dil; seçilen dil veritabanına ve
 * ayar dosyasına yazılır, uygulama yeni dille açılır (İngilizce metinler, Arapçada sağdan sola). Logo ve karşılama
 * adı da dile göre.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = HomeNoWelcomeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class V34LanguageUiTest : V33EditorBase() {
    private fun dao(): LanguageSettingsDao = GlobalContext.get().get<AppDatabase>().languageSettingsDao()

    @After fun resetLanguage() {
        I18n.setLanguage("tr")
        LanguageManager.write(ApplicationProvider.getApplicationContext<Context>(), "tr", auto = true)
    }

    @Test
    fun settingsPickerStoresLanguageAndRelaunchesInIt() {
        // İlk açılış: Türkçe (testler), veritabanında otomatik kayıt
        waitFor { runBlocking { dao().get() } != null }
        val first = runBlocking { dao().get()!! }
        assertEquals("tr", first.languageCode)
        assertTrue(first.auto)
        waitFor { compose.onAllNodesWithText("Ayarlar").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Ayarlar").onFirst().performClick()
        waitFor { exists("language_card") }
        compose.onNodeWithTag("language_card").performScrollTo()
        // v36 dil seçimi açılan kutu: dokununca 7 dil listelenir
        assertTrue(compose.onAllNodes(hasTestTag("language_current") and hasText("Türkçe", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("language_select").performClick()
        waitFor { exists("language_menu") }
        AppLanguage.entries.forEach { assertTrue(it.code, exists("language_${it.code}")) }
        shot("v34-language-card")
        // İngilizce seçilir → onay → yeniden açılış İngilizce
        compose.onNodeWithTag("language_en").performClick()
        waitFor { exists("language_confirm") }
        compose.onNodeWithTag("language_confirm").performClick()
        waitFor(15_000) { runBlocking { dao().get()?.languageCode } == "en" && compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
        val row = runBlocking { dao().get()!! }
        assertFalse(row.auto)
        assertEquals("en", I18n.language)
        assertEquals("en", LanguageManager.current(ApplicationProvider.getApplicationContext()).code)
        assertEquals("English", compose.activity.resources.configuration.locales[0].displayLanguage)
        // Koddaki içerik adları da çevrilir (hazır şekil adı)
        assertNotEquals("Ok (sağa)", I18n.tr("Ok (sağa)"))
        shot("v34-settings-english")
        // Arapça: sağdan sola
        compose.onNodeWithTag("language_card").performScrollTo()
        compose.onNodeWithTag("language_select").performClick()
        waitFor { exists("language_menu") }
        compose.onNodeWithTag("language_ar").performClick()
        waitFor { exists("language_confirm") }
        compose.onNodeWithTag("language_confirm").performClick()
        waitFor(15_000) { runBlocking { dao().get()?.languageCode } == "ar" && compose.activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL }
        assertEquals("دليل الرسم", compose.activity.getString(R.string.welcome_title))
        shot("v34-settings-arabic")
    }
}
