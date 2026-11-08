package com.karapp.drawguidance

import android.window.OnBackInvokedCallback
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Robolectric'in bildirim (manifest) ayrıştırıcısı `android:enableOnBackInvokedCallback`
 * özniteliğini okumaz; cihazda bu bayrak ApplicationInfo'ya işlenir. Test ortamı cihazla aynı
 * davransın diye öznitelik kaynak bildirimde gerçekten varsa bayrağı ApplicationInfo'ya aynalar.
 * Öznitelik bildirimden kaldırılırsa bayrak da konmaz ve günlük denetimi testi başarısız olur.
 */
object ManifestFlags {
    fun declaresBackCallback(): Boolean {
        val file = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map { java.io.File(it) }.firstOrNull { it.isFile } ?: return false
        val app = Regex("<application[^>]*>", RegexOption.DOT_MATCHES_ALL).find(file.readText())?.value ?: return false
        return Regex("""android:enableOnBackInvokedCallback\s*=\s*"true"""").containsMatchIn(app)
    }

    fun mirrorBackCallbackFlag(context: android.content.Context) {
        if (!declaresBackCallback()) return
        runCatching {
            val cls = android.content.pm.ApplicationInfo::class.java
            val bit = cls.getDeclaredField("PRIVATE_FLAG_EXT_ENABLE_ON_BACK_INVOKED_CALLBACK").apply { isAccessible = true }.getInt(null)
            val field = cls.getDeclaredField("privateFlagsExt").apply { isAccessible = true }
            val infos = listOfNotNull(
                context.applicationInfo,
                runCatching { context.packageManager.getApplicationInfo(context.packageName, 0) }.getOrNull(),
            )
            infos.forEach { field.setInt(it, field.getInt(it) or bit) }
        }
    }
}

/**
 * Sistem geri hareketi, cihazdaki gibi: uygulama `enableOnBackInvokedCallback` ile öngörülü geri
 * hareketine geçtiğinde Android geri tuşunu/hareketini pencereye tuş olarak değil, en üstteki
 * pencerenin en üstteki [OnBackInvokedCallback]'ine iletir (ViewRootImpl bunu yapar). Robolectric
 * Espresso.pressBack() ile tuşu doğrudan görünüme verdiği için bu yolu atlar; burada aynı yol izlenir.
 */
object SystemBack {
    fun press() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)!!
            val roots = wmg.javaClass.getDeclaredField("mRoots").apply { isAccessible = true }.get(wmg) as List<*>
            val root = roots.lastOrNull() ?: throw AssertionError("açık pencere yok")
            val dispatcher = root.javaClass.getMethod("getOnBackInvokedDispatcher").invoke(root)!!
            val callback = dispatcher.javaClass.getMethod("getTopCallback").invoke(dispatcher) as OnBackInvokedCallback?
                ?: throw AssertionError("en üstteki pencerede geri çağrısı yok")
            callback.onBackInvoked()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
