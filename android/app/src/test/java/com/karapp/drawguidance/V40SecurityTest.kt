package com.karapp.drawguidance

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.settings.ServerConfig
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** v40 istemci güvenliği: sabit yayın sunucusu, yedek dışı tutulan oturum/tanılama dosyaları, ağ kuralları, günlük. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class V40SecurityTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Uygulama her testte Koin'i yeniden başlatır. */
    @After
    fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun releaseServerConfigIgnoresSavedAndNewAddresses() {
        context.getSharedPreferences("server_config", Context.MODE_PRIVATE).edit().clear()
            .putString("base_url", "http://192.168.1.20:4747/").commit()
        val release = ServerConfig(context, "https://drawapi.ahmetkar.com/", allowOverride = false)
        // Eski sürümden/yedekten kalan adres yok sayılır, yenisi kaydedilmez
        assertEquals("https://drawapi.ahmetkar.com/", release.baseUrl.value)
        assertFalse(release.update("10.0.0.5:4747"))
        assertEquals("https", release.currentHttpUrl.scheme)
        assertEquals("http://192.168.1.20:4747/",
            context.getSharedPreferences("server_config", Context.MODE_PRIVATE).getString("base_url", null))
        // v44 hata ayıklama derlemesi de kayıtlı adresi okumaz; değişiklik yalnız bellekte
        val debug = ServerConfig(context, BuildConfig.API_BASE_URL, allowOverride = true)
        assertEquals(BuildConfig.API_BASE_URL, debug.baseUrl.value)
        assertTrue(debug.update("10.0.0.5"))
        assertEquals("http://10.0.0.5:4747/", debug.baseUrl.value)
    }

    @Test
    fun backupRulesExcludeFirebaseSessionAndDiagnostics() {
        // Firebase Auth oturum dosyasının adı: uygulama adı ve kimliğinin Base64'ü (FirebaseAuth'un kendi kuralı)
        val appId = context.getString(context.resources.getIdentifier("google_app_id", "string", context.packageName))
        val store = "com.google.firebase.auth.api.Store." +
            Base64.encodeToString("[DEFAULT]".toByteArray(), Base64.NO_WRAP) + "+" +
            Base64.encodeToString(appId.toByteArray(), Base64.NO_WRAP) + ".xml"
        val expected = setOf(
            "sharedpref:$store",
            "sharedpref:server_config.xml",
            // v43/v44 eşitleme izni ve gelen kutusu durumu cihaza özgü
            "sharedpref:sync_permit.xml",
            "sharedpref:inbox.xml",
            "sharedpref:developer_settings.xml",
            "sharedpref:error_reports.xml",
            "file:diagnostics/",
            "file:catalog/",
        )

        val legacy = xml("src/main/res/xml/backup_rules.xml")
        assertEquals(expected, excludes(legacy.documentElement))
        assertTrue(legacy.getElementsByTagName("include").length == 0)

        val modern = xml("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val el = modern.getElementsByTagName(section).item(0) as Element
            assertEquals(section, expected, excludes(el))
        }

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    @Test
    fun cleartextOnlyInDebugBuild() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("usesCleartextTraffic"))
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))

        // v44 yayın: yalnız HTTPS; hiçbir adres için düz HTTP yok (yerel ağ sunucusu kaldırıldı); kullanıcı CA'larına
        // güvenilmez
        val main = xml("src/main/res/xml/network_security_config.xml")
        val base = main.getElementsByTagName("base-config").item(0) as Element
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        assertEquals(0, main.getElementsByTagName("domain-config").length)
        val certs = main.getElementsByTagName("certificates")
        assertEquals(listOf("system"), (0 until certs.length).map { (certs.item(it) as Element).getAttribute("src") })

        // Hata ayıklama: yerel ağdaki geliştirme sunucusu (http://10.0.2.2, 192.168.x.x)
        val debug = xml("src/debug/res/xml/network_security_config.xml")
        assertEquals("true", (debug.getElementsByTagName("base-config").item(0) as Element).getAttribute("cleartextTrafficPermitted"))
    }

    @Test
    fun httpLogNeverContainsBearerToken() {
        val logging = GlobalContext.get().get<OkHttpClient>().interceptors.filterIsInstance<HttpLoggingInterceptor>().single()
        ShadowLog.clear()
        // Uygulamanın günlükleyicisi + ağa çıkmadan yanıt veren sahte uç
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .header("Set-Cookie", "sid=secret-cookie").body("{}".toResponseBody()).build()
            }
            .build()
        client.newCall(
            Request.Builder().url("http://10.0.2.2:4747/api/v1/users/me")
                .header("Authorization", "Bearer secret-token-123").header("X-Client-Version", "40").build()
        ).execute().close()

        val lines = ShadowLog.getLogsForTag("HTTP").map { it.msg }
        assertTrue(lines.toString(), lines.any { it.startsWith("X-Client-Version: 40") })
        assertTrue(lines.toString(), lines.any { it.startsWith("Authorization: ") })
        assertFalse(lines.toString(), lines.any { it.contains("secret-token-123") || it.contains("secret-cookie") })
    }

    private fun xml(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))

    private fun excludes(parent: Element): Set<String> {
        val nodes = parent.getElementsByTagName("exclude")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .map { it.getAttribute("domain") + ":" + it.getAttribute("path") }.toSet()
    }
}
