package com.karapp.drawguidance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.data.account.AuthException
import com.karapp.drawguidance.data.account.legacyError
import com.karapp.drawguidance.data.settings.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** v35 sunucu kapısı 4747: varsayılan, IP'den adres ve eski (3000) kayıtlı adresin bir kez taşınması. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class V35ServerPortTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun newDefaultPortAndOneTimeMigration() {
        assertEquals(4747, ServerConfig.DEFAULT_PORT)
        assertEquals("http://192.168.1.20:4747/", ServerConfig.normalize("192.168.1.20"))
        assertEquals("http://10.0.2.2:4747/", BuildConfig.API_BASE_URL)
        // v44 kayıtlı sunucu adresi okunmaz (sunucu ayarı kaldırıldı); hata ayıklamada değişiklik yalnız bellekte
        context.getSharedPreferences("server_config", Context.MODE_PRIVATE).edit().clear()
            .putString("base_url", "http://192.168.1.20:3000/").commit()
        assertEquals(BuildConfig.API_BASE_URL, ServerConfig(context, BuildConfig.API_BASE_URL).baseUrl.value)
        val config = ServerConfig(context, BuildConfig.API_BASE_URL, allowOverride = true)
        assertTrue(config.update("192.168.1.20:3000"))
        assertEquals("http://192.168.1.20:3000/", config.baseUrl.value)
        assertEquals(BuildConfig.API_BASE_URL, ServerConfig(context, BuildConfig.API_BASE_URL, allowOverride = true).baseUrl.value)
    }
}

/** v35 eski Google Sign-In kodları anlamlı hataya çevrilir (iptal yalnız 12501). */
class V35GoogleSignInErrorTest {
    @Test
    fun legacyCodesMapToReasons() {
        assertEquals(AuthException.Reason.CANCELLED, legacyError(12501).reason)
        assertEquals(AuthException.Reason.NETWORK, legacyError(7).reason)
        assertEquals(AuthException.Reason.FAILED, legacyError(10).reason)
        assertTrue(legacyError(10).message!!.contains("SHA-1"))
        assertTrue(legacyError(12500).message!!.contains("12500"))
        assertTrue(legacyError(8).message!!.contains("8"))
    }
}
