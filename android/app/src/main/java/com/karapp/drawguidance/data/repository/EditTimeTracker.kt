package com.karapp.drawguidance.data.repository

/**
 * v40 düzenleyicide geçirilen etkin süreyi ölçer (saf mantık; saat dışarıdan verilir, ms).
 * - Süre yalnız uygulama ön plandayken ve kullanıcı etkileşimdeyken sayılır: her dokunuş/tuş [onInput] ile bildirilir;
 *   son girdiden [idleMs] (öntanımlı 60 sn) sonra sayaç durur (o boşluk sayılmaz, yalnız [idleMs]'e kadarki kısım).
 * - [onForeground] (düzenleyici açıldı / uygulama öne geldi) bir girdi gibi sayacı başlatır; [onBackground] sayacı
 *   o anda (ya da daha önce boşta kalındıysa o noktada) durdurur.
 * - [drain] o ana dek birikip henüz yazılmamış süreyi verir ve sıfırlar (dönemsel ve kapanışta veritabanına eklenir).
 */
class EditTimeTracker(private val idleMs: Long = IDLE_MS) {
    private var foreground = false
    private var segmentStart = -1L
    private var lastInput = 0L
    private var closed = 0L

    val running: Boolean get() = segmentStart >= 0

    fun onForeground(now: Long) {
        foreground = true
        onInput(now)
    }

    fun onBackground(now: Long) {
        close(now)
        foreground = false
    }

    fun onInput(now: Long) {
        if (!foreground) return
        if (segmentStart >= 0 && now - lastInput > idleMs) close(now)
        if (segmentStart < 0) segmentStart = now
        lastInput = maxOf(lastInput, now)
    }

    /** Henüz alınmamış süre (sıfırlamadan). */
    fun pending(now: Long): Long = closed + openPart(now)

    /** Birikmiş süreyi verir ve sıfırlar; açık bölüm sürüyorsa bu andan devam eder. */
    fun drain(now: Long): Long {
        val total = pending(now)
        closed = 0
        if (segmentStart >= 0) {
            if (now - lastInput >= idleMs) segmentStart = -1 else segmentStart = maxOf(segmentStart, now)
        }
        return total
    }

    private fun openPart(now: Long): Long {
        if (segmentStart < 0) return 0
        val end = minOf(now, lastInput + idleMs)
        return (end - segmentStart).coerceAtLeast(0)
    }

    private fun close(now: Long) {
        closed += openPart(now)
        segmentStart = -1
    }

    companion object {
        const val IDLE_MS = 60_000L
    }
}
