package com.karapp.drawguidance.diagnostics

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Günlük kaydının önemi (logcat harfleriyle). */
enum class LogLevel(val letter: Char) {
    VERBOSE('V'), DEBUG('D'), INFO('I'), WARN('W'), ERROR('E'), ASSERT('F');

    companion object {
        fun of(letter: Char): LogLevel = entries.firstOrNull { it.letter == letter } ?: INFO
    }
}

/** Bir günlük satırı. */
data class LogEntry(val time: String, val level: LogLevel, val tag: String, val message: String)

/**
 * Uygulamanın günlükleri: logcat'e yazar ve son kayıtları bellekte tutar. Geliştirici modunda Ayarlar →
 * Günlükler ekranı, uygulamanın kendi logcat kayıtlarını (hata, uyarı, StrictMode, sistem mesajları) okur;
 * logcat okunamazsa (ör. test ortamı) bellekteki kayıtları gösterir.
 */
object AppLog {
    private const val CAPACITY = 1500
    private const val MAX_LOGCAT_LINES = 4000
    private val buffer = ArrayDeque<LogEntry>()
    private val timeFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun d(tag: String, message: String) { Log.d(tag, message); add(LogLevel.DEBUG, tag, message) }
    fun i(tag: String, message: String) { Log.i(tag, message); add(LogLevel.INFO, tag, message) }
    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error); add(LogLevel.WARN, tag, message + (error?.let { "\n" + it.stackTraceToString() } ?: ""))
    }
    fun e(tag: String, message: String, error: Throwable? = null, userVisible: Boolean = false) {
        Log.e(tag, message, error); add(LogLevel.ERROR, tag, message + (error?.let { "\n" + it.stackTraceToString() } ?: ""))
        // v37 hatalar merkezi hata yönetimine de bildirilir; [userVisible] (kullanıcının işi yapılamadı) ise rapor
        // penceresi açılabilir, değilse sessizce
        if (error != null) AppErrors.report(tag, error, userVisible, log = false)
    }

    /** Bellekteki kayda ekler (logcat'e yazmadan; çökme gibi zaten yazılmış kayıtlar için). */
    fun add(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(timeFormat.get()!!.format(Date()), level, tag, message)
        synchronized(buffer) {
            buffer.addLast(entry)
            while (buffer.size > CAPACITY) buffer.removeFirst()
        }
    }

    /** Bellekteki kayıtlar (eskiden yeniye). */
    fun entries(): List<LogEntry> = synchronized(buffer) { buffer.toList() }

    /** Bellekteki kayıtları ve (okunabiliyorsa) uygulamanın logcat kayıtlarını siler. */
    fun clear() {
        synchronized(buffer) { buffer.clear() }
        runCatching {
            val process = ProcessBuilder("logcat", "-c").redirectErrorStream(true).start()
            try {
                process.outputStream.close()
                process.inputStream.use { it.readBytes() }
                process.waitFor()
            } finally {
                process.destroy()
            }
        }
    }

    /**
     * Uygulamanın kendi logcat kayıtları (yalnızca bu süreç). Okunamazsa null. Satır biçimi `threadtime`:
     * `09-21 13:22:01.123  1234  1250 W Etiket: mesaj`.
     */
    fun readLogcat(): List<LogEntry>? = runCatching {
        val pid = android.os.Process.myPid()
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=$pid", "-t", MAX_LOGCAT_LINES.toString())
            .redirectErrorStream(true)
            .start()
        try {
            // Tüm akışlar kapatılır (StrictMode "kapatılmamış kaynak" uyarısı vermesin)
            process.outputStream.close()
            val lines = process.inputStream.bufferedReader().use { it.readLines() }
            process.waitFor()
            parse(lines)
        } finally {
            process.destroy()
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private val linePattern = Regex("""^(\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+)\s+\d+\s+\d+\s+([VDIWEF])\s+(.*?)\s*:\s?(.*)$""")

    /** logcat `threadtime` satırlarını ayrıştırır; devam satırları bir öncekine eklenir. */
    fun parse(lines: List<String>): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        for (line in lines) {
            if (line.startsWith("--------- beginning of")) continue
            val m = linePattern.find(line)
            if (m != null) {
                val (time, level, tag, message) = m.destructured
                val previous = out.lastOrNull()
                // Aynı anda aynı etiketle gelen çok satırlı mesajlar (yığın izi) birleştirilir
                if (previous != null && previous.time == time && previous.tag == tag && previous.level == LogLevel.of(level[0])) {
                    out[out.size - 1] = previous.copy(message = previous.message + "\n" + message)
                } else {
                    out += LogEntry(time, LogLevel.of(level[0]), tag, message)
                }
            } else if (line.isNotBlank() && out.isNotEmpty()) {
                val previous = out.last()
                out[out.size - 1] = previous.copy(message = previous.message + "\n" + line)
            }
        }
        return out
    }

    /** Gösterilecek kayıtlar: logcat okunabiliyorsa oradan, değilse bellekten. */
    fun collect(): List<LogEntry> = readLogcat() ?: entries()

    /** Paylaşmak/kopyalamak için düz metin. */
    fun format(entries: List<LogEntry>): String =
        entries.joinToString("\n") { "${it.time} ${it.level.letter} ${it.tag}: ${it.message}" }
}
