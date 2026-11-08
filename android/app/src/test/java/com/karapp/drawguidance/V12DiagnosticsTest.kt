package com.karapp.drawguidance

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karapp.drawguidance.diagnostics.Diagnostics
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Yakalanmamış hata uygulama klasörüne yazılır ve önceki işleyiciye iletilir. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestDrawGuidanceApp::class)
class V12DiagnosticsTest {

    @org.junit.After fun tearDown() = org.koin.core.context.stopKoin()

    @Test
    fun uncaughtErrorsAreWrittenToTheCrashLog() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var forwarded: Throwable? = null
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, e -> forwarded = e }
            Diagnostics.clearLastCrash(context)
            Diagnostics.install(context)
            val error = IllegalStateException("deneme çökmesi")
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
            val log = Diagnostics.lastCrash(context)!!
            assertTrue(log.contains("deneme çökmesi"))
            assertTrue(log.contains("V12DiagnosticsTest"))
            assertSame("önceki işleyici de çağrılır", error, forwarded)
            Diagnostics.clearLastCrash(context)
            assertNull(Diagnostics.lastCrash(context))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
