package com.karapp.drawguidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v45 yayın derlemesinde DEX kod iyileştirmesi açık, R8 tam kipi kapalı (Android 10'daki Ayarlar çökmesi tam kiple
 * gelmişti). Yapılandırma dosyaları birbirini bozmasın diye metin olarak denetlenir.
 */
class V45R8ConfigTest {
    private val app: File = listOf(File("."), File("app")).first { File(it, "proguard-rules.pro").exists() }.canonicalFile
    private val buildFile = File(app, "build.gradle.kts").readText()
    private val rules = File(app, "proguard-rules.pro").readLines()
    private val properties = File(app.parentFile, "gradle.properties").readLines()

    /** Yorum ve boşluk dışındaki satırlar. */
    private fun active(lines: List<String>) = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun releaseBlock(): String {
        val start = buildFile.indexOf("release {", buildFile.indexOf("buildTypes"))
        assertTrue("buildTypes içinde release bloğu", start >= 0)
        return buildFile.substring(start, buildFile.indexOf("debug {", start))
    }

    @Test fun releaseUsesOptimizingDefaultRules() {
        val release = releaseBlock()
        assertTrue(release, release.contains("getDefaultProguardFile(\"proguard-android-optimize.txt\")"))
        assertFalse("iyileştirmeyi kapatan varsayılan dosya kullanılmamalı", release.contains("\"proguard-android.txt\""))
        assertTrue(release.contains("\"proguard-rules.pro\""))
        // Küçültme ve kaynak atma açık (yalnız -PdgNoMinify tanılama derlemesinde kapanır)
        assertTrue(release.contains("isMinifyEnabled = !noMinify"))
        assertTrue(release.contains("isShrinkResources = !noMinify"))
    }

    @Test fun appRulesDoNotDisableOptimization() {
        val lines = active(rules)
        assertFalse("-dontoptimize iyileştirmeyi kapatır: $lines", lines.any { it.startsWith("-dontoptimize") })
        assertFalse(lines.any { it.startsWith("-dontshrink") || it.startsWith("-dontobfuscate") })
    }

    @Test fun r8FullModeStaysOff() {
        val props = active(properties).associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        assertEquals("Ayarlar çökmesini gideren uyumluluk kipi", "false", props["android.enableR8.fullMode"])
    }

    @Test fun reflectionAndCrashReportRulesKept() {
        val lines = active(rules)
        // Credential Manager, Play Services sağlayıcısını Android 13 ve altında sınıf adıyla yükler
        val ifAt = lines.indexOf("-if class androidx.credentials.CredentialManager")
        assertTrue(lines.toString(), ifAt >= 0)
        assertEquals("-keep class androidx.credentials.playservices.** { *; }", lines[ifAt + 1])
        // kotlinx.serialization serileştiricileri ve Retrofit arayüzleri
        assertTrue(lines.any { it.startsWith("-keepclassmembers @kotlinx.serialization.Serializable class com.karapp.drawguidance.**") })
        assertTrue(lines.any { it.startsWith("-keep,allowobfuscation,allowshrinking interface com.karapp.drawguidance.data.remote.**") })
        // Çökme raporlarında satır numaraları
        assertTrue(lines.contains("-keepattributes SourceFile,LineNumberTable"))
    }
}
