package com.karapp.drawguidance

import com.karapp.drawguidance.data.model.BackgroundSpec
import com.karapp.drawguidance.data.model.PageAlign
import com.karapp.drawguidance.data.model.PageBackground
import com.karapp.drawguidance.data.model.PageNumberFormat
import com.karapp.drawguidance.data.model.PageSettings
import com.karapp.drawguidance.data.model.PageTextSize
import com.karapp.drawguidance.data.repository.decodeBackground
import com.karapp.drawguidance.data.repository.decodePageBackgrounds
import com.karapp.drawguidance.data.repository.decodePageSettings
import com.karapp.drawguidance.data.repository.encodeBackground
import com.karapp.drawguidance.data.repository.withPageSettings
import com.karapp.drawguidance.ui.drawing.DiagramContrast
import com.karapp.drawguidance.ui.drawing.DiagramThemes
import com.karapp.drawguidance.ui.screens.canvasedit.components.PageScrub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v40 sayfa ayarları (yazı satırları, kayıt), sayfa kaydırıcısı hesabı, diyagram zemini ve yazı karşıtlığı (saf JVM). */
class V40PageAndDiagramTest {

    @Test
    fun pageSettingsLines() {
        assertTrue(PageSettings().lines(0, 3).isEmpty())
        val n = PageSettings(pageNumbers = true)
        assertEquals(listOf(PageSettings.Line(false, PageAlign.CENTER, PageTextSize.MEDIUM, "2")), n.lines(1, 3))
        assertEquals("2 / 3", n.copy(numberFormat = PageNumberFormat.NUMBER_OF_TOTAL).lines(1, 3).single().text)
        // Üst bilgi + alt bilgi + farklı hizada numara: üç satır
        val all = PageSettings(
            pageNumbers = true, numberAlign = PageAlign.RIGHT, header = " Fizik ", headerAlign = PageAlign.LEFT,
            footer = "Okul", footerSize = PageTextSize.SMALL,
        )
        val lines = all.lines(0, 5)
        assertEquals(3, lines.size)
        assertEquals(PageSettings.Line(true, PageAlign.LEFT, PageTextSize.MEDIUM, "Fizik"), lines[0])
        assertEquals(PageSettings.Line(false, PageAlign.RIGHT, PageTextSize.SMALL, "1"), lines[2])
        // Numara alt bilgiyle aynı hizadaysa aynı satırda (üst üste binmez)
        val same = all.copy(numberAlign = PageAlign.CENTER).lines(0, 5)
        assertEquals(2, same.size)
        assertEquals("Okul   1", same[1].text)
        // Üstte numara üst bilgiyle aynı hizada
        assertEquals("Fizik   4", all.copy(numberAtTop = true, numberAlign = PageAlign.LEFT).lines(3, 5).single { it.top }.text)
    }

    @Test
    fun pageSettingsLiveInTheBackgroundJsonAndSurviveBackgroundChanges() {
        val settings = PageSettings(pageNumbers = true, header = "Başlık", footerAlign = PageAlign.RIGHT)
        // Arka plan yokken yalnız sayfa ayarları
        val only = withPageSettings("", settings)
        assertEquals(settings, decodePageSettings(only))
        assertTrue(decodeBackground(only).isEmpty)
        // Sayfa arka planlarıyla birlikte: ikisi de korunur
        val spec = BackgroundSpec(patternId = "grid", patternColorHex = "#336699")
        val pages = listOf(null, PageBackground(paperHex = "#FFEEDD"))
        val both = withPageSettings(encodeBackground(spec, pages), settings)
        assertEquals(settings, decodePageSettings(both))
        assertEquals(spec, decodeBackground(both))
        assertEquals("#FFEEDD", decodePageBackgrounds(both, 2)[1]?.paperHex)
        // Kapatınca anahtar kalkar, arka plan kalır; hiçbir şey kalmazsa boş
        val cleared = withPageSettings(both, PageSettings())
        assertFalse(cleared.contains("pageSettings"))
        assertEquals(spec, decodeBackground(cleared))
        assertEquals("", withPageSettings(only, PageSettings()))
        // Eski/bozuk kayıt: boş ayarlar
        assertEquals(PageSettings(), decodePageSettings("{bozuk"))
        assertEquals(PageSettings(), decodePageSettings(encodeBackground(spec)))
    }

    @Test
    fun scrubberMapping() {
        assertEquals(48f, PageScrub.step(1000f, 4, 10f, 48f))
        assertEquals(10f, PageScrub.step(1000f, 1000, 10f, 48f))
        assertEquals(25f, PageScrub.step(1000f, 40, 10f, 48f))
        assertEquals(5, PageScrub.pageFor(3, 50f, 25f, 40))
        assertEquals(0, PageScrub.pageFor(3, -500f, 25f, 40))
        assertEquals(39, PageScrub.pageFor(3, 5000f, 25f, 40))
        assertEquals(3, PageScrub.pageFor(3, 10f, 25f, 40))
        assertEquals(0..3, PageScrub.window(0, 10))
        assertEquals(6..9, PageScrub.window(9, 10))
        assertEquals(0..0, PageScrub.window(0, 1))
    }

    @Test
    fun diagramBackgroundChoiceLivesInTheThemeId() {
        assertTrue(DiagramThemes.hasBackground(DiagramThemes.EDITORIAL))
        assertEquals(DiagramThemes.EDITORIAL_CLEAR, DiagramThemes.withBackground(DiagramThemes.EDITORIAL, false))
        assertEquals(DiagramThemes.EDITORIAL, DiagramThemes.withBackground(DiagramThemes.EDITORIAL_CLEAR, true))
        assertFalse(DiagramThemes.hasBackground(DiagramThemes.EDITORIAL_CLEAR))
        // Renkli tema: dolgusuz, çizgi ve yazı temanın kenar rengi
        val pastel = DiagramThemes.find("pastel_clear")!!
        assertTrue(pastel.fills.isEmpty())
        assertEquals("#546E7A", pastel.text)
        assertFalse(pastel.shadow)
        // Koyu editoryal zeminsiz: editoryal ama saydam kağıt
        val dark = DiagramThemes.find("editorial_dark_clear")!!
        assertTrue(dark.editorial!!.clear)
        assertTrue(DiagramThemes.isEditorial("editorial_dark_clear"))
        // Klasik zeminsiz ("_clear"): tema yok (kalem rengi), zemin yok
        assertNull(DiagramThemes.find(DiagramThemes.withBackground("", false)))
        assertFalse(DiagramThemes.hasBackground(DiagramThemes.withBackground("", false)))
        assertEquals("", DiagramThemes.withBackground("_clear", true))
        assertEquals("pastel", DiagramThemes.withBackground("pastel_clear", true))
    }

    @Test
    fun diagramTextContrastFollowsWcag() {
        val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt()
        assertEquals(21.0, DiagramContrast.ratio(black, white), 0.01)
        // Koyu dolguda koyu yazı → beyaz; açık dolguda koyu yazı kalır
        assertEquals(white, DiagramContrast.readable(0xFF1C1B19.toInt(), 0xFF263238.toInt()))
        assertEquals(0xFF1C1B19.toInt(), DiagramContrast.readable(0xFF1C1B19.toInt(), 0xFFFAF8F4.toInt()))
        // Canlı turuncuda beyaz yazı yetersiz → siyah
        assertEquals(black, DiagramContrast.readable(white, 0xFFFFA726.toInt()))
        // Yazının saydamlığı korunur
        assertEquals(0xBFFFFFFF.toInt(), DiagramContrast.readable(0xBF1C1B19.toInt(), 0xFF101010.toInt()))
        // Saydam ya da yarıdan az örten dolgu zemini belirlemez; dolgu yoksa dokunulmaz
        assertEquals(0xFF222222.toInt(), DiagramContrast.readable(0xFF222222.toInt(), 0xFF000000.toInt(), fillAlpha = 0.2f))
        assertEquals(0xFF222222.toInt(), DiagramContrast.readable(0xFF222222.toInt(), null))
        assertTrue(DiagramContrast.ratio(DiagramContrast.readable(0xFF333333.toInt(), 0xFF3A3A3A.toInt()), 0xFF3A3A3A.toInt()) >= DiagramContrast.MIN_RATIO)
    }
}
