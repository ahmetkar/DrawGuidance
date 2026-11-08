package com.karapp.drawguidance.content.trace

import kotlin.math.max
import kotlin.math.min

/**
 * Son bölge haritası: piksel başına bölge kimliği [rid] (0 until [count]) ve bölgenin palet sırası [label]
 * (-1: saydam arka plan, çizilmez). Bölgeler 4-komşulukla bağlıdır.
 */
internal class RegionMap(val w: Int, val h: Int, val rid: IntArray, val count: Int, val label: IntArray)

/**
 * Etiket görüntüsünden 4-bağlı bileşenler; küçük bölgeler (benek) ve renk geçişi (kenar yumuşatma) kalıntısı ince
 * şeritler komşularına katılır. Parça sınırı aşılırsa en küçükler katılmaya devam eder.
 */
internal object Regions {

    private const val TRANSPARENT_DIST = 200f

    fun build(
        labels: IntArray, w: Int, h: Int, palette: IntArray,
        minArea: Double, thinRatio: Float, maxParts: Int, cancel: () -> Boolean,
    ): RegionMap {
        val n = w * h
        // --- 4-bağlı bileşenler ---
        val comp = IntArray(n) { -1 }
        val stack = IntArray(n)
        val compLabel = IntList(256)
        val compArea = IntList(256)
        var nc = 0
        for (s in 0 until n) {
            if (comp[s] >= 0) continue
            val lab = labels[s]
            comp[s] = nc
            var sp = 0
            stack[sp++] = s
            var area = 0
            while (sp > 0) {
                val p = stack[--sp]
                area++
                val x = p % w
                if (x > 0 && comp[p - 1] < 0 && labels[p - 1] == lab) { comp[p - 1] = nc; stack[sp++] = p - 1 }
                if (x < w - 1 && comp[p + 1] < 0 && labels[p + 1] == lab) { comp[p + 1] = nc; stack[sp++] = p + 1 }
                if (p >= w && comp[p - w] < 0 && labels[p - w] == lab) { comp[p - w] = nc; stack[sp++] = p - w }
                if (p + w < n && comp[p + w] < 0 && labels[p + w] == lab) { comp[p + w] = nc; stack[sp++] = p + w }
            }
            compLabel.add(lab); compArea.add(area)
            nc++
        }
        checkCancel(cancel)

        // --- Komşuluk: ortak kenar uzunlukları, çevre ---
        val perim = IntArray(nc)
        var keys = LongArray(1024)
        var nk = 0
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val p = row + x
                val c = comp[p]
                if (x == 0) perim[c]++
                if (x == w - 1) perim[c]++
                if (y == 0) perim[c]++
                if (y == h - 1) perim[c]++
                if (x + 1 < w) {
                    val c2 = comp[p + 1]
                    if (c2 != c) {
                        if (nk == keys.size) keys = keys.copyOf(nk * 2)
                        keys[nk++] = (min(c, c2).toLong() shl 32) or max(c, c2).toLong()
                        perim[c]++; perim[c2]++
                    }
                }
                if (y + 1 < h) {
                    val c2 = comp[p + w]
                    if (c2 != c) {
                        if (nk == keys.size) keys = keys.copyOf(nk * 2)
                        keys[nk++] = (min(c, c2).toLong() shl 32) or max(c, c2).toLong()
                        perim[c]++; perim[c2]++
                    }
                }
            }
        }
        java.util.Arrays.sort(keys, 0, nk)
        val adj = arrayOfNulls<IntList>(nc)
        fun adjOf(c: Int): IntList = adj[c] ?: IntList(8).also { adj[c] = it }
        run {
            var i = 0
            while (i < nk) {
                val key = keys[i]
                var j = i
                while (j < nk && keys[j] == key) j++
                val a = (key ushr 32).toInt(); val b = (key and 0xFFFFFFFFL).toInt(); val len = j - i
                adjOf(a).apply { add(b); add(len) }
                adjOf(b).apply { add(a); add(len) }
                i = j
            }
        }
        checkCancel(cancel)

        // --- Birleştirme ---
        val parent = IntArray(nc) { it }
        val area = IntArray(nc) { compArea[it] }
        fun find(c: Int): Int {
            var r = c
            while (parent[r] != r) r = parent[r]
            var x = c
            while (parent[x] != r) { val nx = parent[x]; parent[x] = r; x = nx }
            return r
        }
        val acc = IntArray(nc)
        val touched = IntList(64)
        // r'nin güncel komşularını (kökler) ve ortak kenar uzunluklarını [touched]/[acc] içine toplar
        fun gather(r: Int) {
            touched.clear()
            val list = adj[r] ?: return
            var i = 0
            while (i < list.size) {
                val nb = find(list[i]); val len = list[i + 1]
                i += 2
                if (nb == r) continue
                if (acc[nb] == 0) touched.add(nb)
                acc[nb] += len
            }
        }
        fun clearGather() { for (i in 0 until touched.size) acc[touched[i]] = 0 }
        fun colorDist(a: Int, b: Int): Float {
            val la = compLabel[a]; val lb = compLabel[b]
            if (la < 0 || lb < 0) return if (la == lb) 0f else TRANSPARENT_DIST
            return rgbDist(palette[la], palette[lb])
        }
        fun mergeInto(r: Int, t: Int, shared: Int) {
            parent[r] = t
            area[t] += area[r]
            perim[t] = perim[t] + perim[r] - 2 * shared
            val from = adj[r]
            if (from != null) {
                val to = adjOf(t)
                for (i in 0 until from.size) to.add(from[i])
                adj[r] = null
            }
        }
        // En uygun komşu: ortak kenar uzun, renk yakın
        fun bestTarget(r: Int): Int {
            var best = -1; var bestScore = -1f
            for (i in 0 until touched.size) {
                val nb = touched[i]
                val score = acc[nb] / (colorDist(r, nb) + 12f)
                if (score > bestScore || (score == bestScore && nb < best)) { bestScore = score; best = nb }
            }
            return best
        }
        // İnce şerit iki komşu rengin arasında (ya da birine çok yakın) mı: kenar yumuşatma kalıntısı
        fun isBlend(r: Int): Boolean {
            val lr = compLabel[r]
            if (lr < 0) return false
            val c = palette[lr]
            val cr = ((c shr 16) and 255).toFloat(); val cg = ((c shr 8) and 255).toFloat(); val cb = (c and 255).toFloat()
            // En uzun ortak kenarlı (en çok 6) opak komşu
            val cand = (0 until touched.size).map { touched[it] }.filter { compLabel[it] >= 0 }
                .sortedWith(compareByDescending<Int> { acc[it] }.thenBy { it }).take(6)
            val nCand = cand.size
            for (i in 0 until nCand) {
                val a = palette[compLabel[cand[i]]]
                if (rgbDist(a, c) < 40f) return true
                val ar = ((a shr 16) and 255).toFloat(); val ag = ((a shr 8) and 255).toFloat(); val ab = (a and 255).toFloat()
                for (j in i + 1 until nCand) {
                    val b = palette[compLabel[cand[j]]]
                    val dr = ((b shr 16) and 255) - ar; val dg = ((b shr 8) and 255) - ag; val db = (b and 255) - ab
                    val len2 = dr * dr + dg * dg + db * db
                    if (len2 < 1f) continue
                    val t = ((cr - ar) * dr + (cg - ag) * dg + (cb - ab) * db) / len2
                    if (t < -0.15f || t > 1.15f) continue
                    val tc = t.coerceIn(0f, 1f)
                    val ex = cr - (ar + tc * dr); val ey = cg - (ag + tc * dg); val ez = cb - (ab + tc * db)
                    if (ex * ex + ey * ey + ez * ez < 36f * 36f) return true
                }
            }
            return false
        }

        val order = (0 until nc).sortedWith(compareBy<Int> { area[it] }.thenBy { it }).toIntArray()
        for ((step, r) in order.withIndex()) {
            if ((step and 1023) == 0) checkCancel(cancel)
            if (parent[r] != r) continue
            gather(r)
            if (touched.size == 0) { clearGather(); continue }
            val small = area[r] < minArea
            val thin = !small && area[r] < thinRatio * perim[r] && isBlend(r)
            if (small || thin) {
                val t = bestTarget(r)
                val shared = acc[t]
                clearGather()
                mergeInto(r, t, shared)
            } else clearGather()
        }

        // --- Parça sınırı: çizilen (opak) bölge sayısı fazlaysa en küçükleri katmaya devam et ---
        while (true) {
            val roots = (0 until nc).filter { parent[it] == it }
            val opaque = roots.filter { compLabel[it] >= 0 }
            if (opaque.size <= maxParts) break
            checkCancel(cancel)
            val sorted = roots.sortedWith(compareBy<Int> { area[it] }.thenBy { it })
            val excess = opaque.size - maxParts
            val limit = area[opaque.sortedWith(compareBy<Int> { area[it] }.thenBy { it })[min(opaque.size - 1, excess)]]
            var merged = 0
            for (r in sorted) {
                if (area[r] > limit) break
                if (parent[r] != r) continue
                gather(r)
                if (touched.size == 0) { clearGather(); continue }
                val t = bestTarget(r)
                val shared = acc[t]
                clearGather()
                mergeInto(r, t, shared)
                merged++
            }
            if (merged == 0) break
        }

        // --- Sıkıştırılmış kimlikler ---
        val newId = IntArray(nc) { -1 }
        var count = 0
        val finalLabel = IntList(256)
        val rid = IntArray(n)
        for (p in 0 until n) {
            val r = find(comp[p])
            if (newId[r] < 0) { newId[r] = count++; finalLabel.add(compLabel[r]) }
            rid[p] = newId[r]
        }
        return RegionMap(w, h, rid, count, IntArray(count) { finalLabel[it] })
    }
}
