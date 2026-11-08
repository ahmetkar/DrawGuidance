package com.karapp.drawguidance.content.shapes

import org.junit.Assume
import org.junit.Test
import java.io.File
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * v27 küçük sinir ağının eğitimi (yalnız SHAPENET_TRAIN=1 iken çalışır): şablonların el çizimi benzetimleri (normal ve
 * uzak) → 83 boyutlu girdi → 96 ReLU → şablon olasılıkları. Adam, mini toplu iniş, L2. Sonuç int8'e çevrilip
 * `src/main/resources/shapenet/shape_net.bin` dosyasına yazılır.
 */
class ShapeNetTrainer {
    @Test
    fun train() {
        Assume.assumeTrue(System.getenv("SHAPENET_TRAIN") == "1")
        val templates = ShapeTemplates.all
        val keys = templates.map { it.key }
        val k = keys.size
        val inDim = ShapeNet.IN_DIM
        val hidden = 96
        val rnd = java.util.Random(2027)
        val xs = ArrayList<FloatArray>(); val ys = ArrayList<Int>()
        val per = (System.getenv("SHAPENET_PER")?.toIntOrNull() ?: 70)
        templates.forEachIndexed { ti, t ->
            repeat(per) { i ->
                val strong = i % 3 == 2
                val q = ShapeAugment.handDrawn(t.strokes, rnd, amount = 0.6f + rnd.nextFloat() * 0.8f, strong = strong)
                ShapeNet.inputOf(q)?.let { xs += it; ys += ti }
            }
        }
        val vrnd = java.util.Random(99)
        val vx = ArrayList<FloatArray>(); val vy = ArrayList<Int>()
        templates.forEachIndexed { ti, t -> repeat(3) { ShapeNet.inputOf(ShapeAugment.handDrawn(t.strokes, vrnd))?.let { vx += it; vy += ti } } }
        println("ShapeNet: ${xs.size} örnek, $k sınıf")

        // Parametreler
        val w1 = FloatArray(hidden * inDim) { (rnd.nextGaussian() * sqrt(2.0 / inDim)).toFloat() }
        val b1 = FloatArray(hidden)
        val w2 = FloatArray(k * hidden) { (rnd.nextGaussian() * sqrt(2.0 / hidden)).toFloat() }
        val b2 = FloatArray(k)
        val params = listOf(w1, b1, w2, b2)
        val m = params.map { FloatArray(it.size) }; val v = params.map { FloatArray(it.size) }
        val grads = params.map { FloatArray(it.size) }
        var step = 0
        val epochs = System.getenv("SHAPENET_EPOCHS")?.toIntOrNull() ?: 40
        val batch = 64
        val idx = IntArray(xs.size) { it }
        val h = FloatArray(hidden); val out = FloatArray(k); val dh = FloatArray(hidden)
        for (epoch in 0 until epochs) {
            // karıştır
            for (i in idx.size - 1 downTo 1) { val j = rnd.nextInt(i + 1); val tmp = idx[i]; idx[i] = idx[j]; idx[j] = tmp }
            val lr = 0.002f * (if (epoch < epochs * 0.6) 1f else if (epoch < epochs * 0.85) 0.3f else 0.1f)
            var loss = 0.0
            var b = 0
            while (b < idx.size) {
                grads.forEach { it.fill(0f) }
                val end = minOf(idx.size, b + batch)
                for (ii in b until end) {
                    val x = xs[idx[ii]]; val y = ys[idx[ii]]
                    for (j in 0 until hidden) {
                        var s = b1[j]; val o = j * inDim
                        for (i in 0 until inDim) s += w1[o + i] * x[i]
                        h[j] = if (s > 0f) s else 0f
                    }
                    var mx = Float.NEGATIVE_INFINITY
                    for (c in 0 until k) {
                        var s = b2[c]; val o = c * hidden
                        for (j in 0 until hidden) s += w2[o + j] * h[j]
                        out[c] = s; if (s > mx) mx = s
                    }
                    var sum = 0f
                    for (c in 0 until k) { out[c] = exp(out[c] - mx); sum += out[c] }
                    for (c in 0 until k) out[c] /= sum
                    loss += -ln(out[y].toDouble().coerceAtLeast(1e-9))
                    out[y] -= 1f
                    dh.fill(0f)
                    for (c in 0 until k) {
                        val g = out[c]
                        if (g == 0f) continue
                        grads[3][c] += g
                        val o = c * hidden
                        for (j in 0 until hidden) { grads[2][o + j] += g * h[j]; dh[j] += g * w2[o + j] }
                    }
                    for (j in 0 until hidden) {
                        if (h[j] <= 0f) continue
                        val g = dh[j]
                        grads[1][j] += g
                        val o = j * inDim
                        for (i in 0 until inDim) grads[0][o + i] += g * x[i]
                    }
                }
                val n = (end - b).toFloat()
                step++
                val b1c = 1f - Math.pow(0.9, step.toDouble()).toFloat(); val b2c = 1f - Math.pow(0.999, step.toDouble()).toFloat()
                for (p in params.indices) {
                    val w = params[p]; val g = grads[p]; val mm = m[p]; val vv = v[p]
                    val decay = if (p == 0 || p == 2) 1e-4f else 0f
                    for (i in w.indices) {
                        val gi = g[i] / n + decay * w[i]
                        mm[i] = 0.9f * mm[i] + 0.1f * gi
                        vv[i] = 0.999f * vv[i] + 0.001f * gi * gi
                        w[i] -= lr * (mm[i] / b1c) / (sqrt(vv[i] / b2c) + 1e-8f)
                    }
                }
                b = end
            }
            if (epoch % 5 == 4 || epoch == epochs - 1) {
                val net = ShapeNet.quantize(keys, inDim, hidden, w1, b1, w2, b2)
                val acc = vx.indices.count { i -> net.probs(vx[i]).let { p -> p.indices.maxBy { p[it] } } == vy[i] } / vx.size.toFloat()
                println("dönem ${epoch + 1}: kayıp %.3f, doğrulama ilk-1 (int8) %.3f".format(loss / xs.size, acc))
            }
        }
        val net = ShapeNet.quantize(keys, inDim, hidden, w1, b1, w2, b2)
        val file = File("src/main/resources/shapenet/shape_net.bin")
        file.parentFile.mkdirs()
        file.writeBytes(net.encode())
        println("yazıldı: ${file.absolutePath} (${file.length()} bayt)")
    }
}
