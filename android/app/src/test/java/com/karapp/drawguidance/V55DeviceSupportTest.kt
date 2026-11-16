package com.karapp.drawguidance

import com.karapp.drawguidance.platform.DeviceCaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v55 geniş cihaz desteği: manifestte zorunlu donanım yok, cihazı eleyen izin yok, düşük RAM'li cihazlarda geri alma
 * geçmişi kısa, resim eklemenin sabit sınır metinleri kaldırıldı ve sürüm 55.
 */
class V55DeviceSupportTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun noRequiredHardwareInManifest() {
        val features = Regex("<uses-feature[^>]*>").findAll(manifest).map { it.value }.toList()
        assertTrue(features.isNotEmpty())
        features.forEach { assertTrue(it, it.contains("android:required=\"false\"")) }
        listOf("android.hardware.wifi", "android.hardware.touchscreen", "android.hardware.faketouch", "glEsVersion")
            .forEach { f -> assertTrue(f, features.any { it.contains(f) }) }
    }

    @Test fun noDeviceFilteringPermissions() {
        listOf(
            "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "BLUETOOTH", "BLUETOOTH_CONNECT",
            "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES", "READ_PHONE_STATE", "NFC",
        ).forEach { assertFalse(it, manifest.contains("android.permission.$it\"")) }
    }

    @Test fun lowRamDetection() {
        assertTrue(DeviceCaps.isLowRam(true, 8000))
        assertTrue(DeviceCaps.isLowRam(false, 1900))
        assertTrue(DeviceCaps.isLowRam(false, 2048))
        assertFalse(DeviceCaps.isLowRam(false, 3800))
        // Bellek bilinmiyorsa (0) düşük sayılmaz
        assertFalse(DeviceCaps.isLowRam(false, 0))
    }

    @Test fun lowRamShortensUndoHistory() {
        assertEquals(100, DeviceCaps.historyLimit(100, lowRam = false))
        assertEquals(40, DeviceCaps.historyLimit(100, lowRam = true))
        assertEquals(32, DeviceCaps.historyLimit(80, lowRam = true))
        assertEquals(24, DeviceCaps.historyLimit(60, lowRam = true))
        assertEquals(20, DeviceCaps.historyLimit(30, lowRam = true))
        assertEquals(10, DeviceCaps.historyLimit(10, lowRam = true))
        // Testlerde (init yok) varsayılan cihaz: kısaltma yok
        assertEquals(100, DeviceCaps.historyLimit(100))
    }

    @Test fun imageErrorsNoLongerMentionFixedLimits() {
        File("src/main/res").listFiles { f -> f.name.startsWith("values") }!!.forEach { dir ->
            val f = File(dir, "strings_v55.xml")
            if (File(dir, "strings_v54.xml").exists()) assertTrue("${dir.name} v55 metinleri", f.exists())
            if (!f.exists()) return@forEach
            val text = f.readText()
            assertTrue(dir.name, text.contains("v55_image_err_memory") && text.contains("v55_image_err_pixels"))
            assertFalse(dir.name, text.contains("10 MB") || text.contains("8192"))
        }
        val screen = File("src/main/java/com/karapp/drawguidance/ui/screens/canvasedit/CanvasEditScreen.kt").readText()
        assertTrue(screen.contains("R.string.v55_image_err_memory"))
        assertFalse(screen.contains("R.string.v54_image_err_too_large"))
    }

    @Test fun versionIs55() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode = 55"))
        assertTrue(gradle.contains("versionName = \"1.12.1\""))
    }
}
