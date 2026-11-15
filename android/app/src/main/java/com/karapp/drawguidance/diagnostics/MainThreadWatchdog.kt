package com.karapp.drawguidance.diagnostics

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * v52 donma bekçisi: ana iş parçacığı [thresholdMs] boyunca yanıt vermezse o anki yığın izi günlüğe yazılır ve
 * (kullanıcıya sormadan) hata raporuna eklenir. Böylece "arada donuyor" şikâyetlerinde hangi işin ana iş parçacığını
 * tuttuğu görülür. Bir donma bir kez raporlanır; Robolectric testlerinde çalışmaz.
 */
object MainThreadWatchdog {
    class FreezeException(ms: Long, trace: Array<StackTraceElement>) : RuntimeException("Ana iş parçacığı ${ms} ms yanıt vermedi") {
        init { stackTrace = trace }
    }

    @Volatile private var started = false

    fun start(thresholdMs: Long = 2500L, intervalMs: Long = 500L) {
        if (started || Build.FINGERPRINT == "robolectric") return
        started = true
        val main = Handler(Looper.getMainLooper())
        Thread({
            val lastTick = java.util.concurrent.atomic.AtomicLong(SystemClock.uptimeMillis())
            val pending = java.util.concurrent.atomic.AtomicBoolean(false)
            var reported = false
            while (true) {
                if (pending.compareAndSet(false, true)) {
                    main.post { lastTick.set(SystemClock.uptimeMillis()); pending.set(false) }
                }
                try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { return@Thread }
                val blocked = SystemClock.uptimeMillis() - lastTick.get()
                if (pending.get() && blocked >= thresholdMs) {
                    if (!reported) {
                        reported = true
                        val trace = Looper.getMainLooper().thread.stackTrace
                        val e = FreezeException(blocked, trace)
                        AppLog.w("Freeze", e.message ?: "donma", e)
                        runCatching { AppErrors.report("freeze", e, userVisible = false, log = false) }
                    }
                } else if (!pending.get()) reported = false
            }
        }, "dg-freeze-watchdog").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }
}
