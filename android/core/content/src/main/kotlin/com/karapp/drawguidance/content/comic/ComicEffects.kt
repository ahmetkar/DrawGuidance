package com.karapp.drawguidance.content.comic

import com.karapp.drawguidance.content.library.Audience
import com.karapp.drawguidance.content.library.Fit
import com.karapp.drawguidance.content.library.LibCategory
import com.karapp.drawguidance.content.library.LibShape
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * v36 çizgi roman efektleri (Efektler penceresi): aksiyon, büyü/enerji, duygu ve atmosfer efektleri özgün vektör
 * çizimlerdir; tuvale sıradan şekil olarak eklenir (taşınır, ölçeklenir, döndürülür, kopyalanır, silinir). Ses
 * efektleri [ComicSfx] yazılarıdır.
 *
 * Renk: çizimler elemanın renklerine bağlıdır, böylece efektin rengi tek seçimle değişir:
 * - dolgu null: efektin rengi; "@shade:x" o rengin koyu/açık tonu (0.5 kendisi); beyaz parıltılar sabit.
 * - kenar null: efektin kenar rengi (çizgi efektlerinde [EffectDef.lineOnly] efektin rengi, ötekilerde koyu tonu).
 *
 * Şekil kimliği `lib.fx.<anahtar>`; seçicide listelenmez (yalnız Efektler penceresinde).
 */
enum class EffectGroup(val id: String, val title: String) {
    ACTION("fx_action", "Aksiyon"),
    MAGIC("fx_magic", "Büyü ve enerji"),
    EMOTION("fx_emotion", "Duygu"),
    ATMOSPHERE("fx_atmosphere", "Atmosfer"),
    SOUND("fx_sound", "Ses"),
}

/** Efekt: [w]×[h] tasarım biriminde çizim, varsayılan rengi ve çizgi efekti olup olmadığı. */
class EffectDef(
    val key: String,
    val title: String,
    val group: EffectGroup,
    val w: Float,
    val h: Float,
    val color: String,
    val lineOnly: Boolean,
    val tags: List<String>,
    val draw: Fx.() -> Unit,
) {
    val id: String get() = ComicEffects.ID_PREFIX + key

    fun shape(): LibShape = LibShape(id, title, Fit.BOX, aspect = w / h, tags = tags) {
        inBox(0f, 0f, 1f / w, 1f / h) { Fx(this).draw() }
    }
}

/** Efekt çizim yardımcıları (tasarım biriminde). */
class Fx internal constructor(val b: VectorBuilder) {
    /** Efektin renginde, kenarlı parça. */
    fun main(shape: VectorBuilder.() -> VectorBuilder.Part): VectorBuilder.Part = b.shape()

    /** Efekt renginin tonu ([level] 0.5 kendisi, altı koyu, üstü açık), kenarsız. */
    fun tone(level: Float, shape: VectorBuilder.() -> VectorBuilder.Part): VectorBuilder.Part =
        b.shape().fill(SHADE + String.format(java.util.Locale.US, "%.2f", level)).noStroke()

    /** Beyaz parıltı (kenarsız). */
    fun white(shape: VectorBuilder.() -> VectorBuilder.Part): VectorBuilder.Part = b.shape().fill(WHITE).noStroke()

    /** Yalnız çizgi (dolgusuz), kenar renginde. */
    fun stroke(w: Float = 1f, shape: VectorBuilder.() -> VectorBuilder.Part): VectorBuilder.Part = b.shape().fill(NO_FILL).width(w)

    /** Efekt renginde kenarsız dolgu. */
    fun flat(shape: VectorBuilder.() -> VectorBuilder.Part): VectorBuilder.Part = b.shape().noStroke()

    /** Bir uçtan ötekine kalınlaşan dolu çizgi (hız, yağmur, ışın). */
    fun wedge(x0: Float, y0: Float, x1: Float, y1: Float, w0: Float, w1: Float): VectorBuilder.Part {
        val dx = x1 - x0; val dy = y1 - y0
        val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-3f)
        val nx = -dy / len; val ny = dx / len
        return b.poly(
            x0 + nx * w0 / 2f, y0 + ny * w0 / 2f, x1 + nx * w1 / 2f, y1 + ny * w1 / 2f,
            x1 - nx * w1 / 2f, y1 - ny * w1 / 2f, x0 - nx * w0 / 2f, y0 - ny * w0 / 2f, closed = true,
        ).noStroke()
    }

    /** Pürüzlü yıldız (darbe, patlama çekirdeği): [n] uç, dış [ro], iç [ri], tohumla düzensiz. */
    fun jag(cx: Float, cy: Float, ro: Float, ri: Float, n: Int, seed: Int, rotate: Float = 0f): FloatArray {
        val rnd = Random(seed)
        val out = FloatArray(n * 4)
        for (i in 0 until n * 2) {
            val a = (rotate * PI / 180.0) + 2 * PI * (i + (rnd.nextFloat() - 0.5f) * 0.35f) / (n * 2)
            val r = if (i % 2 == 0) ro * (0.78f + rnd.nextFloat() * 0.22f) else ri * (0.85f + rnd.nextFloat() * 0.3f)
            out[i * 2] = cx + r * cos(a).toFloat(); out[i * 2 + 1] = cy + r * sin(a).toFloat()
        }
        return out
    }

    /**
     * Daireler birleşimi (bulut, duman, patlama): önce kenarlı daireler, üstüne aynı daireler kenarsız (iç kenarlar
     * kapanır, yalnız dış hat kalır). [fill] null efekt rengi.
     */
    fun puffs(circles: List<FloatArray>, fill: String? = null, outline: Float = 1f, alpha: Float = 1f) {
        if (outline > 0f) circles.forEach { c -> b.circle(c[0], c[1], c[2]).fill(fill).width(outline * 2f).alpha(alpha) }
        circles.forEach { c -> b.circle(c[0], c[1], c[2]).fill(fill).noStroke().alpha(alpha) }
    }

    fun heart(cx: Float, cy: Float, s: Float, rotate: Float = 0f): VectorBuilder.() -> VectorBuilder.Part = {
        path(closed = true) {
            val r = rotate * PI.toFloat() / 180f
            val c = cos(r); val sn = sin(r)
            fun x(px: Float, py: Float) = cx + (px * c - py * sn) * s
            fun y(px: Float, py: Float) = cy + (px * sn + py * c) * s
            moveTo(x(0f, 0.95f), y(0f, 0.95f))
            cubicTo(x(-0.55f, 0.55f), y(-0.55f, 0.55f), x(-1.05f, 0.1f), y(-1.05f, 0.1f), x(-0.95f, -0.38f), y(-0.95f, -0.38f))
            cubicTo(x(-0.85f, -0.9f), y(-0.85f, -0.9f), x(-0.2f, -1.0f), y(-0.2f, -1.0f), x(0f, -0.55f), y(0f, -0.55f))
            cubicTo(x(0.2f, -1.0f), y(0.2f, -1.0f), x(0.85f, -0.9f), y(0.85f, -0.9f), x(0.95f, -0.38f), y(0.95f, -0.38f))
            cubicTo(x(1.05f, 0.1f), y(1.05f, 0.1f), x(0.55f, 0.55f), y(0.55f, 0.55f), x(0f, 0.95f), y(0f, 0.95f))
        }
    }

    /** Damla (ucu yukarıda): ([cx],[cy]) gövdenin merkezi, [s] yarıçap. */
    fun drop(cx: Float, cy: Float, s: Float): VectorBuilder.() -> VectorBuilder.Part = {
        path(closed = true) {
            moveTo(cx, cy - s * 2.1f)
            cubicTo(cx + s * 0.35f, cy - s * 1.2f, cx + s, cy - s * 0.6f, cx + s, cy)
            arcTo(cx, cy, s, s, 0f, 180f)
            cubicTo(cx - s, cy - s * 0.6f, cx - s * 0.35f, cy - s * 1.2f, cx, cy - s * 2.1f)
        }
    }

    /** Dört köşeli parıltı yıldızı. */
    fun spark(cx: Float, cy: Float, r: Float): VectorBuilder.() -> VectorBuilder.Part = { star(cx, cy, r, r * 0.26f, 4) }

    fun pts(vararg xy: Float): VectorBuilder.() -> VectorBuilder.Part = { poly(*xy, closed = true) }

    companion object {
        const val SHADE = "@shade:"
        const val WHITE = "#FFFFFF"
        const val NO_FILL = "@none"
    }
}

object ComicEffects {
    const val ID_PREFIX = "fx."

    private fun fx(key: String, title: String, group: EffectGroup, w: Float, h: Float, color: String, tags: List<String>, lineOnly: Boolean = false, draw: Fx.() -> Unit) =
        EffectDef(key, title, group, w, h, color, lineOnly, tags, draw)

    val all: List<EffectDef> by lazy { action + magic + emotion + atmosphere }

    fun byId(id: String): EffectDef? = id.removePrefix("lib.").let { raw -> all.firstOrNull { it.id == raw } }

    fun of(group: EffectGroup): List<EffectDef> = all.filter { it.group == group }

    /** Kütüphaneye eklenen (seçicide listelenmeyen) kategoriler: şekiller kimlikleriyle bulunsun diye. */
    val categories: List<LibCategory> by lazy {
        EffectGroup.entries.filter { g -> all.any { it.group == g } }.map { g ->
            LibCategory(g.id, g.title, Audience.ART, of(g).map { it.shape() }, "Efektler", listed = false)
        }
    }

    // ---------------------------------------------------------------- aksiyon

    private val action = listOf(
        fx("speed", "Hız çizgileri", EffectGroup.ACTION, 200f, 100f, "#1D1D24", listOf("hız", "koşu", "hareket", "speed lines"), lineOnly = true) {
            val rows = listOf(10f to 0.55f, 22f to 0.8f, 33f to 0.45f, 45f to 1f, 56f to 0.7f, 67f to 0.9f, 78f to 0.5f, 90f to 0.75f)
            rows.forEachIndexed { i, (y, f) ->
                val x0 = 10f + (i * 37 % 60).toFloat()
                wedge(x0, y, x0 + 140f * f, y, 0.4f, 5.5f)
            }
        },
        fx("impact", "Darbe", EffectGroup.ACTION, 120f, 120f, "#FFD23F", listOf("darbe", "vuruş", "çarpma", "impact")) {
            main { poly(*jag(60f, 60f, 58f, 30f, 12, 3), closed = true) }
            tone(0.82f) { poly(*jag(60f, 60f, 38f, 20f, 10, 5, 12f), closed = true) }
            white { poly(*jag(60f, 60f, 18f, 9f, 8, 7, 4f), closed = true) }
        },
        fx("collision", "Çarpışma", EffectGroup.ACTION, 160f, 120f, "#FF7A2F", listOf("çarpışma", "kaza", "çarpma", "collision")) {
            // İki darbe tek siluet: önce ikisinin kenarı, sonra kenarsız dolguları (iç kenarlar kapanır)
            val left = jag(58f, 62f, 48f, 26f, 10, 11); val right = jag(104f, 56f, 52f, 28f, 11, 13, 9f)
            main { poly(*left, closed = true) }.width(2f)
            main { poly(*right, closed = true) }.width(2f)
            flat { poly(*left, closed = true) }
            flat { poly(*right, closed = true) }
            tone(0.82f) { poly(*jag(84f, 60f, 30f, 16f, 9, 17), closed = true) }
            white { poly(*jag(84f, 60f, 13f, 6f, 7, 19), closed = true) }
            // Etrafa saçılan küçük parçalar
            for ((x, y, r) in listOf(Triple(14f, 18f, 0f), Triple(146f, 16f, 30f), Triple(150f, 104f, 60f), Triple(12f, 108f, 100f))) {
                b.group(rotate = r, px = x, py = y) { main(pts(x - 6f, y - 3f, x + 6f, y - 6f, x + 2f, y + 6f)).width(0.8f) }
            }
        },
        fx("explosion", "Patlama", EffectGroup.ACTION, 140f, 130f, "#FF6A1F", listOf("patlama", "bomba", "ateş", "explosion")) {
            val outer = listOf(
                floatArrayOf(70f, 66f, 40f), floatArrayOf(36f, 72f, 26f), floatArrayOf(104f, 72f, 27f), floatArrayOf(50f, 38f, 26f),
                floatArrayOf(92f, 36f, 27f), floatArrayOf(70f, 24f, 22f), floatArrayOf(44f, 98f, 22f), floatArrayOf(96f, 100f, 22f), floatArrayOf(70f, 104f, 20f),
            )
            puffs(outer)
            tone(0.78f) { poly(*jag(70f, 64f, 44f, 26f, 11, 23), closed = true) }
            tone(0.92f) { poly(*jag(70f, 64f, 26f, 15f, 9, 29), closed = true) }
            white { poly(*jag(70f, 64f, 11f, 6f, 7, 31), closed = true) }
        },
        fx("shatter", "Parçalanma", EffectGroup.ACTION, 140f, 120f, "#9FD3EE", listOf("parçalanma", "kırılma", "cam", "shatter")) {
            val cx = 70f; val cy = 60f
            val shards = listOf(
                floatArrayOf(-50f, -40f, -28f, -46f, -34f, -22f), floatArrayOf(10f, -52f, 30f, -44f, 16f, -28f),
                floatArrayOf(44f, -20f, 62f, -6f, 40f, 2f), floatArrayOf(38f, 30f, 58f, 44f, 30f, 48f),
                floatArrayOf(-6f, 40f, 10f, 54f, -14f, 54f), floatArrayOf(-56f, 22f, -38f, 30f, -52f, 42f),
                floatArrayOf(-62f, -8f, -46f, -4f, -58f, 6f), floatArrayOf(52f, -46f, 64f, -40f, 54f, -32f),
            )
            shards.forEachIndexed { i, t ->
                val f = floatArrayOf(cx + t[0], cy + t[1], cx + t[2], cy + t[3], cx + t[4], cy + t[5])
                main { poly(*f, closed = true) }.width(0.8f)
                if (i % 2 == 0) tone(0.85f) { poly(f[0], f[1], f[2], f[3], (f[0] + f[4]) / 2f, (f[1] + f[5]) / 2f, closed = true) }
            }
            for (a in listOf(-160f, -110f, -60f, -15f, 35f, 80f, 130f)) {
                val r = a * PI.toFloat() / 180f
                stroke(1.3f) { poly(cx, cy, cx + cos(r) * 18f, cy + sin(r) * 16f, cx + cos(r + 0.2f) * 30f, cy + sin(r + 0.2f) * 26f) }
            }
            white { star(cx, cy, 9f, 3f, 4) }
        },
        fx("dust", "Toz bulutu", EffectGroup.ACTION, 160f, 90f, "#D8C6A4", listOf("toz", "bulut", "duman", "fren", "dust")) {
            val puff = listOf(
                floatArrayOf(40f, 62f, 24f), floatArrayOf(72f, 54f, 30f), floatArrayOf(108f, 60f, 26f), floatArrayOf(136f, 68f, 18f),
                floatArrayOf(20f, 72f, 15f), floatArrayOf(58f, 34f, 20f), floatArrayOf(92f, 30f, 22f),
            )
            puffs(puff)
            for (c in puff) tone(0.78f) { circle(c[0] - c[2] * 0.25f, c[1] - c[2] * 0.28f, c[2] * 0.5f) }
            for ((x, y, r) in listOf(Triple(10f, 40f, 3f), Triple(150f, 36f, 4f), Triple(144f, 18f, 2.5f), Triple(4f, 58f, 2f))) main { circle(x, y, r) }.width(0.7f)
        },
        fx("punch_trail", "Yumruk hareket izi", EffectGroup.ACTION, 180f, 110f, "#1D1D24", listOf("yumruk", "hareket izi", "vuruş", "punch"), lineOnly = true) {
            for ((i, r) in listOf(46f, 36f, 26f).withIndex()) {
                val cx = 112f - i * 24f; val cy = 55f
                flat {
                    path(closed = true) {
                        arcTo(cx, cy, r, r * 0.95f, 120f, 120f)
                        arcTo(cx + r * 0.22f, cy, r * 0.82f, r * 0.75f, 236f, -112f)
                    }
                }
            }
            for ((y, f) in listOf(40f to 0.8f, 55f to 1f, 70f to 0.7f)) wedge(4f, y, 4f + 46f * f, y, 0.4f, 4f)
            main { poly(*jag(160f, 55f, 18f, 8f, 7, 41), closed = true) }.fill("#FFFFFF").width(1.4f)
        },
    )

    // ---------------------------------------------------------------- büyü ve enerji

    private val magic = listOf(
        fx("lightning", "Şimşek", EffectGroup.MAGIC, 80f, 160f, "#FFE14D", listOf("şimşek", "yıldırım", "elektrik", "lightning")) {
            main(pts(46f, 2f, 14f, 74f, 36f, 72f, 18f, 158f, 66f, 60f, 42f, 62f, 64f, 2f))
            tone(0.9f, pts(48f, 12f, 26f, 66f, 44f, 64f, 32f, 120f, 56f, 68f, 36f, 70f, 54f, 12f))
            stroke(1.1f) { poly(22f, 110f, 8f, 120f, 12f, 132f) }
            stroke(1.1f) { poly(58f, 40f, 72f, 36f, 76f, 24f) }
        },
        fx("flame", "Alev", EffectGroup.MAGIC, 100f, 140f, "#FF7A1A", listOf("alev", "ateş", "yangın", "flame")) {
            main {
                path(closed = true) {
                    moveTo(50f, 138f)
                    cubicTo(14f, 138f, 4f, 104f, 16f, 78f); cubicTo(22f, 64f, 30f, 60f, 28f, 44f)
                    cubicTo(40f, 54f, 42f, 66f, 40f, 74f); cubicTo(44f, 50f, 54f, 30f, 46f, 2f)
                    cubicTo(70f, 22f, 86f, 52f, 76f, 78f); cubicTo(84f, 72f, 86f, 64f, 86f, 56f)
                    cubicTo(100f, 82f, 96f, 138f, 50f, 138f)
                }
            }
            tone(0.8f) {
                path(closed = true) {
                    moveTo(50f, 134f)
                    cubicTo(26f, 134f, 20f, 110f, 30f, 92f); cubicTo(36f, 82f, 44f, 76f, 44f, 62f)
                    cubicTo(60f, 76f, 66f, 92f, 62f, 104f); cubicTo(70f, 98f, 72f, 92f, 72f, 86f)
                    cubicTo(82f, 104f, 78f, 134f, 50f, 134f)
                }
            }
            tone(0.95f) {
                path(closed = true) {
                    moveTo(50f, 130f)
                    cubicTo(38f, 130f, 34f, 118f, 40f, 108f); cubicTo(44f, 102f, 48f, 98f, 50f, 90f)
                    cubicTo(60f, 102f, 64f, 112f, 62f, 118f); cubicTo(60f, 126f, 56f, 130f, 50f, 130f)
                }
            }
        },
        fx("ice", "Buz", EffectGroup.MAGIC, 140f, 120f, "#7FD3F5", listOf("buz", "kristal", "soğuk", "donma", "ice")) {
            fun crystal(cx: Float, base: Float, h: Float, wd: Float, tilt: Float) {
                b.group(rotate = tilt, px = cx, py = base) {
                    main(pts(cx - wd, base, cx - wd, base - h * 0.75f, cx, base - h, cx + wd, base - h * 0.75f, cx + wd, base))
                    tone(0.85f, pts(cx - wd, base, cx - wd, base - h * 0.75f, cx, base - h, cx, base))
                    stroke(0.7f) { line(cx, base - h, cx, base) }
                }
            }
            crystal(30f, 116f, 62f, 11f, -24f)
            crystal(110f, 116f, 58f, 10f, 26f)
            crystal(52f, 116f, 88f, 13f, -8f)
            crystal(90f, 116f, 80f, 12f, 10f)
            crystal(70f, 118f, 108f, 15f, 0f)
            white(spark(40f, 20f, 7f))
            white(spark(112f, 30f, 5f))
        },
        fx("aura", "Enerji aurası", EffectGroup.MAGIC, 120f, 160f, "#53E0FF", listOf("aura", "enerji", "güç", "ki", "energy aura")) {
            // Alevli dış hat ile iç oval arasındaki halka: ortası boş, karakterin çevresine konur
            fun outer(k: Float): List<P> = List(28 + 1) { i ->
                val a = PI / 2 + PI * i / 14
                val top = sin(a) < -0.2
                val r = if (i % 2 == 1) (if (top) 1.32f else 1.1f) else 0.96f
                P(60f + cos(a).toFloat() * 50f * k * r, 84f + sin(a).toFloat() * 66f * k * r)
            }
            fun inner(k: Float): List<P> = List(36 + 1) { i ->
                val a = PI / 2 - 2 * PI * i / 36
                P(60f + cos(a).toFloat() * 30f * k, 88f + sin(a).toFloat() * 54f * k)
            }
            b.points(outer(1f) + inner(1f), closed = true).noStroke().alpha(0.5f)
            tone(0.8f) { points(outer(0.84f) + inner(1f), closed = true) }.alpha(0.7f)
            stroke(1.2f) { points(outer(1f), closed = true) }
            for ((x, y) in listOf(20f to 40f, 102f to 30f, 14f to 110f, 108f to 120f, 60f to 4f)) white(spark(x, y, 5f))
        },
        fx("magic_circle", "Büyü çemberi", EffectGroup.MAGIC, 140f, 140f, "#A86BFF", listOf("büyü çemberi", "sihir", "rün", "magic circle"), lineOnly = true) {
            val c = 70f
            stroke(1.6f) { circle(c, c, 66f) }
            stroke(1f) { circle(c, c, 57f) }
            stroke(1f) { circle(c, c, 22f) }
            stroke(1.2f) { polygon(c, c, 57f, 3) }
            stroke(1.2f) { polygon(c, c, 57f, 3, rotate = 180f) }
            for (i in 0 until 24) {
                val a = 2 * PI * i / 24
                val r0 = if (i % 2 == 0) 58f else 60f
                stroke(0.8f) { line(c + cos(a).toFloat() * r0, c + sin(a).toFloat() * r0, c + cos(a).toFloat() * 65f, c + sin(a).toFloat() * 65f) }
            }
            for (i in 0 until 6) {
                val a = -PI / 2 + 2 * PI * i / 6
                main { circle(c + cos(a).toFloat() * 57f, c + sin(a).toFloat() * 57f, 4f) }.width(0.8f)
            }
            main { star(c, c, 12f, 5f, 5) }.width(0.8f)
        },
        fx("light_beam", "Işık huzmesi", EffectGroup.MAGIC, 120f, 160f, "#FFF2A8", listOf("ışık", "huzme", "spot", "ilahi", "light beam")) {
            flat(pts(44f, 0f, 76f, 0f, 118f, 156f, 2f, 156f)).alpha(0.35f)
            tone(0.8f, pts(50f, 0f, 70f, 0f, 96f, 156f, 24f, 156f)).alpha(0.55f)
            white(pts(56f, 0f, 64f, 0f, 76f, 156f, 44f, 156f)).alpha(0.75f)
            flat { ellipse(60f, 156f, 58f, 4f) }.alpha(0.4f)
            for ((x, y, r) in listOf(Triple(30f, 120f, 3f), Triple(86f, 96f, 2.5f), Triple(70f, 60f, 2f), Triple(44f, 80f, 2f))) white { circle(x, y, r) }
            white(spark(92f, 130f, 6f))
        },
        fx("portal_glow", "Portal parıltısı", EffectGroup.MAGIC, 140f, 140f, "#9B5CFF", listOf("portal", "geçit", "parıltı", "boyut", "portal glow")) {
            flat { ellipse(70f, 70f, 64f, 64f) }.alpha(0.25f)
            flat { ellipse(70f, 70f, 50f, 50f) }.alpha(0.45f)
            tone(0.75f) { ellipse(70f, 70f, 36f, 36f) }.alpha(0.7f)
            tone(0.92f) { ellipse(70f, 70f, 20f, 20f) }
            for (k in 0 until 4) {
                val ph = k * 90f
                b.path {
                    for (j in 0..24) {
                        val f = j / 24f
                        val a = (ph + f * 300f) * PI.toFloat() / 180f
                        val r = 14f + f * 44f
                        if (j == 0) moveTo(70f + cos(a) * r, 70f + sin(a) * r) else lineTo(70f + cos(a) * r, 70f + sin(a) * r)
                    }
                }.fill(Fx.NO_FILL).stroke(Fx.WHITE).width(1.4f).alpha(0.8f)
            }
            for ((x, y, r) in listOf(Triple(20f, 22f, 6f), Triple(122f, 30f, 5f), Triple(116f, 118f, 6f), Triple(26f, 112f, 4f))) white(spark(x, y, r))
        },
    )

    // ---------------------------------------------------------------- duygu

    private val emotion = listOf(
        fx("hearts", "Kalpler", EffectGroup.EMOTION, 140f, 110f, "#FF4F81", listOf("kalp", "aşk", "sevgi", "hearts")) {
            for ((x, y, s, r) in listOf(listOf(46f, 62f, 34f, -12f), listOf(104f, 40f, 24f, 14f), listOf(112f, 92f, 14f, 6f))) {
                main(heart(x, y, s, r))
                white { ellipse(x - s * 0.45f, y - s * 0.45f, s * 0.2f, s * 0.13f, -35f) }
            }
        },
        fx("tears", "Gözyaşları", EffectGroup.EMOTION, 100f, 120f, "#5EB7FF", listOf("gözyaşı", "ağlama", "üzüntü", "tears")) {
            for ((x, y, s) in listOf(Triple(28f, 40f, 12f), Triple(34f, 88f, 9f), Triple(74f, 52f, 11f), Triple(70f, 100f, 8f))) {
                main(drop(x, y, s))
                white { ellipse(x - s * 0.4f, y - s * 0.2f, s * 0.22f, s * 0.36f, 15f) }
            }
        },
        fx("sweat", "Ter damlaları", EffectGroup.EMOTION, 100f, 120f, "#8FD3FF", listOf("ter", "endişe", "utanma", "sweat")) {
            main(drop(56f, 74f, 28f))
            tone(0.82f) { ellipse(46f, 82f, 10f, 14f, 15f) }
            white { ellipse(44f, 62f, 6f, 10f, 20f) }
            main(drop(18f, 40f, 9f))
            main(drop(88f, 28f, 7f))
        },
        fx("anger", "Öfke işareti", EffectGroup.EMOTION, 100f, 100f, "#FF2E2E", listOf("öfke", "sinir", "kızgınlık", "damar", "anger")) {
            for (k in 0 until 4) {
                b.group(rotate = k * 90f, px = 50f, py = 50f) {
                    main {
                        path(closed = true) {
                            moveTo(42f, 8f); quadTo(40f, 38f, 8f, 42f); lineTo(10f, 30f); quadTo(28f, 28f, 30f, 10f)
                        }
                    }
                }
            }
        },
        fx("surprise", "Şaşkınlık çizgileri", EffectGroup.EMOTION, 140f, 100f, "#1D1D24", listOf("şaşkınlık", "sürpriz", "şok", "surprise"), lineOnly = true) {
            for (i in 0 until 7) {
                val a = (-160.0 + i * 23.3) * PI / 180.0
                val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                wedge(70f + ca * 40f, 92f + sa * 52f, 70f + ca * 66f, 92f + sa * 86f, 1f, 6f)
            }
        },
        fx("tremble", "Titreme çizgileri", EffectGroup.EMOTION, 140f, 100f, "#1D1D24", listOf("titreme", "korku", "üşüme", "tremble"), lineOnly = true) {
            for (side in listOf(-1f, 1f)) for (k in 0 until 3) {
                val r = 26f + k * 12f
                val start = if (side < 0) 150f else -30f
                stroke(1.8f) { arc(70f + side * 6f, 50f, r, r * 1.2f, start, 60f) }
            }
        },
    )

    // ---------------------------------------------------------------- atmosfer

    private val atmosphere = listOf(
        fx("rain", "Yağmur", EffectGroup.ATMOSPHERE, 160f, 160f, "#4F8FD8", listOf("yağmur", "fırtına", "hava", "rain"), lineOnly = true) {
            val rnd = Random(61)
            repeat(34) {
                val x = 6f + rnd.nextFloat() * 148f; val y = 4f + rnd.nextFloat() * 136f
                val l = 12f + rnd.nextFloat() * 14f
                wedge(x, y, x - l * 0.35f, y + l, 0.4f, 2.4f)
            }
        },
        fx("snow", "Kar", EffectGroup.ATMOSPHERE, 160f, 160f, "#EAF6FF", listOf("kar", "kış", "soğuk", "snow")) {
            val flakes = listOf(Triple(40f, 46f, 13f), Triple(118f, 104f, 11f), Triple(112f, 34f, 8f))
            val rnd = Random(67)
            repeat(40) {
                val x = 8f + rnd.nextFloat() * 144f; val y = 8f + rnd.nextFloat() * 144f
                val r = 2.5f + rnd.nextFloat() * 4f
                // Kar taneleri yıldızların üstüne binmez
                if (flakes.none { (fx, fy, fr) -> kotlin.math.hypot(x - fx, y - fy) < fr + r + 4f }) main { circle(x, y, r) }.width(0.7f)
            }
            for ((x, y, r) in flakes) {
                for (k in 0 until 3) {
                    val a = k * PI / 3
                    stroke(1.6f) { line(x - cos(a).toFloat() * r, y - sin(a).toFloat() * r, x + cos(a).toFloat() * r, y + sin(a).toFloat() * r) }
                }
                main { circle(x, y, r * 0.25f) }.width(0.7f)
            }
        },
        fx("fog", "Sis", EffectGroup.ATMOSPHERE, 200f, 100f, "#C9D3DA", listOf("sis", "pus", "duman", "fog")) {
            for ((i, y) in listOf(12f, 34f, 56f, 76f).withIndex()) {
                val ph = i * 37f
                flat {
                    path(closed = true) {
                        moveTo(0f, y)
                        for (k in 1..8) {
                            val x = k * 25f
                            quadTo(x - 12.5f, y - 8f + ((k + i) % 2) * 4f, x, y + ((k * 7 + ph.toInt()) % 3 - 1) * 2f)
                        }
                        lineTo(200f, y + 12f)
                        for (k in 7 downTo 0) quadTo(k * 25f + 12.5f, y + 18f, k * 25f, y + 12f)
                    }
                }.alpha(0.35f + i * 0.12f)
            }
        },
        fx("smoke", "Duman", EffectGroup.ATMOSPHERE, 110f, 160f, "#9EA7AF", listOf("duman", "baca", "buhar", "smoke")) {
            val puff = listOf(
                floatArrayOf(56f, 140f, 14f), floatArrayOf(48f, 116f, 18f), floatArrayOf(62f, 92f, 22f), floatArrayOf(44f, 66f, 26f),
                floatArrayOf(68f, 40f, 28f), floatArrayOf(40f, 30f, 22f), floatArrayOf(78f, 16f, 16f),
            )
            puffs(puff, alpha = 0.9f)
            for (c in puff) tone(0.75f) { circle(c[0] - c[2] * 0.25f, c[1] - c[2] * 0.25f, c[2] * 0.5f) }.alpha(0.8f)
        },
        fx("sparks", "Kıvılcımlar", EffectGroup.ATMOSPHERE, 140f, 140f, "#FFB627", listOf("kıvılcım", "ateş", "parıltı", "sparks")) {
            for ((x, y, r) in listOf(Triple(40f, 46f, 16f), Triple(98f, 34f, 11f), Triple(104f, 98f, 14f), Triple(46f, 108f, 9f), Triple(74f, 70f, 7f))) {
                main(spark(x, y, r))
                white(spark(x, y, r * 0.45f))
            }
            val rnd = Random(71)
            repeat(14) {
                val x = 8f + rnd.nextFloat() * 124f; val y = 8f + rnd.nextFloat() * 124f
                main { circle(x, y, 1.6f + rnd.nextFloat() * 2f) }.width(0.6f)
            }
            for ((a, b2) in listOf(P(16f, 20f) to P(28f, 30f), P(124f, 64f) to P(134f, 58f), P(18f, 80f) to P(8f, 88f))) wedge(a.x, a.y, b2.x, b2.y, 0.5f, 3f)
        },
        fx("glow", "Parlama", EffectGroup.ATMOSPHERE, 140f, 140f, "#FFE680", listOf("parlama", "ışık", "parıltı", "glow")) {
            flat { circle(70f, 70f, 62f) }.alpha(0.18f)
            flat { circle(70f, 70f, 44f) }.alpha(0.3f)
            tone(0.8f) { circle(70f, 70f, 28f) }.alpha(0.7f)
            white { star(70f, 70f, 66f, 6f, 4) }.alpha(0.9f)
            white { star(70f, 70f, 34f, 4f, 4, rotate = 45f) }.alpha(0.8f)
            white { circle(70f, 70f, 12f) }
        },
        fx("dots", "Noktacık dokusu", EffectGroup.ATMOSPHERE, 160f, 160f, "#1D1D24", listOf("noktacık", "tram", "doku", "halftone", "dots")) {
            val n = 12
            val step = 160f / n
            for (i in 0 until n) for (j in 0 until n) {
                val f = (i + j) / (2f * (n - 1))
                val r = step * 0.46f * f
                if (r < 0.6f) continue
                val x = step * (i + 0.5f) + if (j % 2 == 1) step / 2f else 0f
                if (x > 160f - r) continue
                flat { circle(x, step * (j + 0.5f), r) }
            }
        },
    )
}
