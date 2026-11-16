package com.karapp.drawguidance.platform

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * v18 cihaz yetenekleri: uygulama Android 5.0'dan (API 21) Android 16'ya (API 36) kadar kurulur; sürüme ve cihazın
 * belleğine göre değişen sınırlar ve seçimler tek yerden okunur.
 *
 * - Android 8.0 öncesinde (API < 26) resimlerin pikselleri Java yığınında durur: yığın küçükse büyük resim ve önbellek
 *   bellek taşmasına yol açar. Bu cihazlarda kaydetme boyutu ve resim önbelleği yığına göre küçültülür.
 * - Düşük RAM'li (Android Go / ~1 GB) cihazlarda da sınırlar küçülür.
 * - Tek bir resmin çizilebilecek en büyük boyutu Android 14'e kadar 100 MB, Android 15+ 150 MB'dır.
 *
 * [init] çağrılmadıysa (ör. saf JVM testleri) varsayılan, sınırsız cihaz değerleri döner.
 */
object DeviceCaps {

    /** Tek bir resim için platformun çizim sınırı (bayt). */
    val platformBitmapLimit: Long
        get() = if (Build.VERSION.SDK_INT >= 35) 150L * 1024 * 1024 else 100L * 1024 * 1024

    /** Android 8.0 öncesi: resim pikselleri Java yığınında. */
    val bitmapsOnJavaHeap: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O

    @Volatile var lowRam: Boolean = false
        private set

    /** Uygulamanın Java yığını (MB); 0 = bilinmiyor. */
    @Volatile var heapMb: Int = 0
        private set

    /** Cihazın toplam belleği (MB); 0 = bilinmiyor. */
    @Volatile var totalRamMb: Long = 0
        private set

    fun init(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val goFlag = runCatching { am.isLowRamDevice }.getOrDefault(false)
        val large = context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0
        heapMb = runCatching { if (large) am.largeMemoryClass else am.memoryClass }.getOrDefault(0)
        totalRamMb = runCatching {
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem / (1024 * 1024)
        }.getOrDefault(0L)
        // v55 Android Go bayrağı olmayan ama ≤ 2 GB belleği olan cihazlar da düşük RAM sayılır
        lowRam = isLowRam(goFlag, totalRamMb)
    }

    /** v25 cihaz sınıfı (3B düzenleyicinin ayrıntı ve kalite bütçeleri için). */
    enum class Tier { LOW, MID, HIGH }

    /**
     * Düşük: düşük RAM'li (Go) cihaz, ≤ 3 GB bellek, Android 7.0 öncesi ya da ≤ 4 çekirdek; yüksek: ≥ 6 GB bellek,
     * ≥ 8 çekirdek ve Android 9+. Bellek bilinmiyorsa (testler) orta.
     */
    val tier: Tier
        get() {
            if (Build.FINGERPRINT == "robolectric") return Tier.MID
            val cores = Runtime.getRuntime().availableProcessors()
            return when {
                lowRam || Build.VERSION.SDK_INT < 24 -> Tier.LOW
                totalRamMb <= 0 -> Tier.MID
                totalRamMb < 3200 || cores <= 4 -> Tier.LOW
                totalRamMb >= 5600 && cores >= 8 && Build.VERSION.SDK_INT >= 28 -> Tier.HIGH
                else -> Tier.MID
            }
        }

    /**
     * v40 cihaz sınıfı yalnız biliniyorsa: [init] çağrılmadıysa ya da bellek okunamadıysa (ve sürümden/düşük RAM'den
     * anlaşılamıyorsa) ya da testte null (açılış canlandırması bu durumda normal sürede).
     */
    val knownTier: Tier?
        get() {
            if (Build.FINGERPRINT == "robolectric") return null
            if (!lowRam && Build.VERSION.SDK_INT >= 24 && totalRamMb <= 0) return null
            return tier
        }

    /** Resimleri tutabileceğimiz bellek bütçesi (bayt); null = bilinmiyor (sınır uygulanmaz). */
    private fun budget(fraction: Float): Long? {
        if (bitmapsOnJavaHeap) {
            if (heapMb <= 0) return null
            return (heapMb * fraction * 1024 * 1024).toLong()
        }
        // Android 8.0+: pikseller yerel bellekte; toplam RAM'in küçük bir kısmı
        if (totalRamMb <= 0) return null
        return (totalRamMb * fraction * 0.5f * 1024 * 1024).toLong()
    }

    /**
     * Kaydetmede (PNG/JPEG/PDF) en uzun kenar: normalde 4096 px; düşük bellekli cihazlarda, tek resim yığının
     * dörtte birini ve platform sınırını aşmayacak kadar.
     */
    val exportMaxSide: Int
        get() {
            var side = DEFAULT_EXPORT_SIDE
            if (lowRam) side = min(side, 2048)
            val bytes = min(platformBitmapLimit, budget(0.25f) ?: Long.MAX_VALUE)
            // Kare varsayımıyla (en kötü durum) ARGB_8888: 4 bayt / piksel
            val fit = sqrt(bytes / 4.0).toInt()
            side = min(side, fit)
            return (side / 256 * 256).coerceIn(1024, DEFAULT_EXPORT_SIDE)
        }

    /** Tuvaldeki resimlerin bellek önbelleği (bayt). */
    val imageCacheBytes: Int
        get() {
            var bytes = 64L * 1024 * 1024
            if (lowRam) bytes = min(bytes, 24L * 1024 * 1024)
            // Taban 24 MB: en büyük saklanan resim (1600 px, ~10 MB) iki tane sığsın, önbellek boşa dönmesin
            budget(0.125f)?.let { bytes = min(bytes, max(it, 24L * 1024 * 1024)) }
            return bytes.toInt()
        }

    /**
     * v52 ikincil çizim önbelleklerinin (piksel fırça sonuçları, uyum işlenmiş resimler) bütçesi: düşük bellekli
     * cihazlarda küçük. Eskiden sabit 64 MB + 32 MB'tı; 2-3 GB'lık cihazlarda uzun kullanımda bellek taşmasına katkı
     * veriyordu.
     */
    fun secondaryCacheBytes(max: Int): Int {
        var bytes = max.toLong()
        if (lowRam) bytes = min(bytes, 12L * 1024 * 1024)
        budget(0.06f)?.let { bytes = min(bytes, max(it, 8L * 1024 * 1024)) }
        return bytes.toInt()
    }

    /**
     * v55 geri alma geçmişinin uzunluğu: düşük RAM'li (Android Go, ≤ 2 GB) cihazda her adım bellekte bir belge
     * kopyası tuttuğundan kısaltılır (normalin %40'ı, en az 20 adım); diğer cihazlarda değişmez.
     */
    fun historyLimit(normal: Int): Int = historyLimit(normal, lowRam)

    internal fun historyLimit(normal: Int, lowRam: Boolean): Int =
        if (lowRam) (normal * 2 / 5).coerceAtLeast(minOf(20, normal)) else normal

    /** v55 düşük RAM'li cihaz mı (Android Go ya da toplam bellek ≤ 2 GB). */
    fun isLowRam(lowRamFlag: Boolean, totalRamMb: Long): Boolean = lowRamFlag || (totalRamMb in 1..LOW_RAM_MB)

    /** Bellek yetersiz uyarısı gelince bırakılabilecek önbellekler bu dinleyicilerle temizlenir. */
    private val trimListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun onTrim(listener: () -> Unit) { trimListeners += listener }

    fun trimMemory() { trimListeners.forEach { runCatching { it() } } }

    /**
     * v53 düzenleyici kapanınca boşaltılacak çizim önbellekleri: kapatılan tuvalin çizimleri, resimleri ve yazı
     * düzenleri bellekte tutulmaz; yalnız açık olan tuvalin verileri bellekte kalır.
     */
    private val closeListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun onEditorClosed(listener: () -> Unit) { closeListeners += listener }

    private val openEditors = java.util.concurrent.atomic.AtomicInteger(0)

    fun editorOpened() { openEditors.incrementAndGet() }

    /** Kayıt bitsin diye kısa bir süre sonra; o sırada başka tuval açık değilse boşaltılır. */
    fun editorClosed() {
        openEditors.updateAndGet { (it - 1).coerceAtLeast(0) }
        val run = Runnable { if (openEditors.get() == 0) closeListeners.forEach { runCatching { it() } } }
        runCatching { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(run, 2_000) }.onFailure { run.run() }
    }

    /** Önbelleği hem bellek uyarısına hem (çizim önbelleğiyse) düzenleyicinin kapanmasına bağlar. */
    fun cache(editor: Boolean, evict: () -> Unit) {
        onTrim(evict)
        if (editor) onEditorClosed(evict)
    }

    const val DEFAULT_EXPORT_SIDE = 4096

    /** v55 bu kadar ya da daha az toplam bellek düşük RAM sayılır (2 GB; çekirdeğin ayırdığı pay için biraz üstü). */
    const val LOW_RAM_MB = 2200L
}
