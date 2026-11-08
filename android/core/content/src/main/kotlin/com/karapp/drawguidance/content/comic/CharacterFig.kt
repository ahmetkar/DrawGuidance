package com.karapp.drawguidance.content.comic

import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

/**
 * v37 bir karakteri çizen bağlam: iskeleti kurar, katmanları sırayla çizer (arka efektler, pelerin ve uzun saç, arkadaki
 * kol, bacaklar, gövde, öndeki kol, baş, başın önündeki kol, ön efektler). Baş ve yüz CharacterHead.kt'de.
 */
internal class Fig(val b: VectorBuilder, val look: Look, val pose: PoseSpec, val face: FaceSpec) {
    val body = look.body
    val sk = Skel(body, pose)
    val ink = CK.INK
    val outfit = look.outfit
    val robot = outfit == Outfit.MECHA
    val T = body.torsoLen
    val R = body.headR

    /** Pozun yuvasında karakterin eşyası (yoksa null). */
    val prop: Prop? = pose.prop?.let { look.props[it] }

    companion object {
        /** Dış hat kalınlığı (görünen). */
        const val OUT = 1.6f
        /** Giysi ve iç parça kenarı. */
        const val MID = 1.2f
        /** Ayrıntı çizgisi. */
        const val FINE = 0.8f
    }

    fun draw() {
        groundShadow()
        b.group(rotate = pose.tilt, px = sk.pelvis.x, py = sk.pelvis.y) {
            backFx()
            backLayer()
            if (pose.seat) stool()
            arms(Layer.BACK)
            legs()
            lowerGarment()
            neck()
            torso()
            if (pose.crossed) crossedArms() else arms(Layer.MID)
            headPart()
            arms(Layer.TOP)
            frontFx()
        }
        groundFx()
    }

    // ------------------------------------------------------------------ temel biçimler

    fun fill(color: String, w: Float = MID, shape: VectorBuilder.() -> VectorBuilder.Part) = b.shape().fill(color).stroke(ink).width(w)
    fun spot(color: String, shape: VectorBuilder.() -> VectorBuilder.Part) = b.shape().fill(color).noStroke()
    fun line(color: String = ink, w: Float = FINE, shape: VectorBuilder.() -> VectorBuilder.Part) = b.shape().fill("@none").stroke(color).width(w)

    /** Birleşik siluet: önce kalın kenarlı, sonra kenarsız dolgu (parçalar arasında iç çizgi kalmaz). */
    fun union(color: String, w: Float = OUT, shapes: List<VectorBuilder.() -> VectorBuilder.Part>) {
        for (s in shapes) b.s().fill(color).stroke(ink).width(w * 2f)
        for (s in shapes) b.s().fill(color).noStroke()
    }

    /** Uçları yuvarlak, [r1]'den [r2]'ye incelen kapsül. */
    fun capsule(a: P, c: P, r1: Float, r2: Float = r1): VectorBuilder.Part {
        val dx = c.x - a.x; val dy = c.y - a.y
        val len = hypot(dx, dy)
        if (len < 0.01f) return b.circle(a.x, a.y, max(r1, r2))
        val ux = dx / len; val uy = dy / len
        val ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        return b.path(closed = true) {
            moveTo(a.x - uy * r1, a.y + ux * r1)
            lineTo(c.x - uy * r2, c.y + ux * r2)
            arcTo(c.x, c.y, r2, r2, ang + 90f, -180f)
            lineTo(a.x + uy * r1, a.y - ux * r1)
            arcTo(a.x, a.y, r1, r1, ang - 90f, -180f)
        }
    }

    /**
     * İnceltilmiş mürekkep çizgisi (dolgulu şerit): [pts] üzerinden geçen yumuşak eğri; kalınlık başta [w0], ortada
     * [w1], sonda [w2] (bulunulan çerçevenin biriminde).
     */
    fun taper(pts: List<P>, w0: Float, w1: Float, w2: Float, color: String = ink, alpha: Float = 1f): VectorBuilder.Part {
        val c = if (pts.size >= 3) VectorBuilder.catmullRom(pts, false, 0.5f, 8) else pts
        val n = c.size
        val lens = FloatArray(n)
        for (i in 1 until n) lens[i] = lens[i - 1] + dist(c[i - 1], c[i])
        val total = max(1e-4f, lens[n - 1])
        val left = ArrayList<P>(n); val right = ArrayList<P>(n)
        for (i in 0 until n) {
            val t = lens[i] / total
            val w = if (t < 0.5f) w0 + (w1 - w0) * (t * 2f) else w1 + (w2 - w1) * ((t - 0.5f) * 2f)
            val a = c[max(0, i - 1)]; val z = c[min(n - 1, i + 1)]
            val dl = max(1e-5f, dist(a, z))
            val nx = -(z.y - a.y) / dl; val ny = (z.x - a.x) / dl
            left += P(c[i].x + nx * w / 2f, c[i].y + ny * w / 2f)
            right += P(c[i].x - nx * w / 2f, c[i].y - ny * w / 2f)
        }
        return b.points(left + right.reversed(), closed = true).fill(color).noStroke().alpha(alpha)
    }

    fun taper(vararg pts: P, w0: Float, w1: Float, w2: Float, color: String = ink, alpha: Float = 1f): VectorBuilder.Part =
        taper(pts.toList(), w0, w1, w2, color, alpha)

    /** Yerel çerçeve: ([o]) noktasında, [deg] dönük, [s] ölçekli. */
    fun frame(o: P, deg: Float, s: Float = 1f, block: VectorBuilder.() -> Unit) =
        b.group(dx = o.x, dy = o.y, sx = s, sy = s, rotate = deg, px = 0f, py = 0f, block = block)

    /** Gövde çerçevesi: başlangıç leğen, y yukarı eksi, gövde eğimiyle dönük. */
    fun torsoFrame(block: VectorBuilder.() -> Unit) = frame(sk.pelvis, pose.lean, 1f, block)

    /** Kalça çerçevesi (gövdeden daha az eğik). */
    fun hipFrame(block: VectorBuilder.() -> Unit) = frame(sk.pelvis, pose.lean * 0.3f, 1f, block)

    /** Kapsülün cel gölgesi (sağ/alt yanda, kapsülün içinde kalır). */
    fun celSeg(a: P, c: P, ra: Float, rc: Float, color: String, alpha: Float = 1f) {
        val dx = c.x - a.x; val dy = c.y - a.y
        val len = hypot(dx, dy)
        if (len < 0.5f) return
        var nx = -dy / len; var ny = dx / len
        if (nx < -0.15f || (abs(nx) <= 0.15f && ny < 0f)) { nx = -nx; ny = -ny }
        capsule(P(a.x + nx * ra * 0.45f, a.y + ny * ra * 0.45f), P(c.x + nx * rc * 0.45f, c.y + ny * rc * 0.45f), ra * 0.5f, rc * 0.5f)
            .fill(color).noStroke().alpha(alpha)
    }

    /** İki parçalı uzuv (omuz-dirsek-el ya da kalça-diz-ayak): birleşim yerinde iç çizgi yok; cel gölgeli. */
    fun limb(a: P, m: P, c: P, r0: Float, r1: Float, r2: Float, color: String, outline: Float = OUT, shadow: Boolean = true) {
        capsule(a, m, r0, r1).fill(color).stroke(ink).width(outline * 2f)
        capsule(m, c, r1, r2).fill(color).stroke(ink).width(outline * 2f)
        capsule(a, m, r0, r1).fill(color).noStroke()
        capsule(m, c, r1, r2).fill(color).noStroke()
        if (shadow) {
            val sh = if (color == look.skin) CK.skinShade(color) else CK.shade(color)
            celSeg(a, m, r0, r1, sh)
            celSeg(m, c, r1, r2, sh)
        }
    }

    fun along(a: P, c: P, t: Float) = lerpP(a, c, t)

    // ------------------------------------------------------------------ zemin ve tabure

    private fun groundShadow() {
        val cx = (sk.ankleL.x + sk.ankleR.x) / 2f
        val wide = abs(sk.ankleL.x - sk.ankleR.x) / 2f + 14f
        val air = pose.lift > 2f
        b.ellipse(cx, 197.5f, if (air) wide * 0.7f else wide, if (air) 2.6f else 3.8f).fill(ink).noStroke().alpha(if (air) 0.12f else 0.2f)
    }

    private fun stool() {
        // Tabure: leğenin altında, ayakları zemine iner
        val wood = "#B07A4A"
        val top = sk.pelvis.y + body.legR0 - 1f
        val cx = sk.pelvis.x + 2f
        val w = body.hipW * 2.4f
        fill(CK.shade(wood)) { roundRect(cx - w * 0.38f, 178f, w * 0.76f, 5f, 2f) }
        fill(wood) { poly(cx - w / 2f + 3f, top + 6f, cx - w / 2f + 9f, top + 6f, cx - w / 2f + 8f, 196f, cx - w / 2f + 1f, 196f, closed = true) }
        fill(wood) { poly(cx + w / 2f - 9f, top + 6f, cx + w / 2f - 3f, top + 6f, cx + w / 2f - 1f, 196f, cx + w / 2f - 8f, 196f, closed = true) }
        fill(wood, OUT) { roundRect(cx - w / 2f, top, w, 9f, 3.5f) }
        spot(CK.light(wood)) { roundRect(cx - w / 2f + 4f, top + 2f, w * 0.55f, 2.2f, 1.1f) }.alpha(0.7f)
    }

    // ------------------------------------------------------------------ arka katman (pelerin, sırt çantası, uzun saç)

    private fun backLayer() {
        if (Extra.CAPE in look.extras) cape()
        if (Extra.BACKPACK in look.extras) torsoFrame {
            val c = if (outfit == Outfit.SPACESUIT) "#C9D1DA" else "#E07B39"
            val w = body.shoulderOut * sk.widthK + 3f
            fill(c, OUT) { roundRect(-w, -T - 2f, w * 2f, T * 0.85f, 7f) }
            fill(CK.shade(c)) { roundRect(-w + 2f, -T * 0.4f, 5f, T * 0.32f, 2f) }
            fill(CK.shade(c)) { roundRect(w - 7f, -T * 0.4f, 5f, T * 0.32f, 2f) }
        }
        hairBehindBody()
    }

    private fun cape() {
        // Pelerin: omuzlardan aşağı; harekette rüzgârla yana ve yukarı savrulur
        val moving = PoseFx.SPEED in pose.fx || PoseFx.WIND in pose.fx || PoseFx.SWISH_ARM in pose.fx || pose.lift > 10f
        val sL = sk.shoulderL; val sR = sk.shoulderR
        val low = max(sk.ankleL.y, sk.ankleR.y) - 22f
        val drift = if (moving) -26f - abs(pose.lean) * 0.6f else 0f
        val rise = if (PoseFx.WIND in pose.fx) -40f else if (moving) -14f else 0f
        val c = look.accent
        val bl = P(sL.x - 16f + drift, low + rise); val br = P(sR.x + 16f + drift * 0.6f, low + rise * 0.5f)
        fill(c, OUT) {
            path(closed = true) {
                moveTo(sL.x + 2f, sL.y - 4f)
                cubicTo(sL.x - 10f, sL.y + 30f, bl.x + 8f, bl.y - 40f, bl.x, bl.y)
                quadTo(bl.x + 8f, bl.y + 6f, (bl.x + br.x) / 2f - 4f, (bl.y + br.y) / 2f + 2f)
                quadTo((bl.x + br.x) / 2f + 10f, (bl.y + br.y) / 2f + 8f, br.x, br.y)
                cubicTo(br.x - 6f, br.y - 40f, sR.x + 10f, sR.y + 30f, sR.x - 2f, sR.y - 4f)
            }
        }
        // İç yüz gölgesi ve kıvrım çizgileri
        spot(CK.shade(c)) {
            path(closed = true) {
                moveTo(sR.x - 2f, sR.y + 2f)
                cubicTo(sR.x + 8f, sR.y + 30f, br.x - 8f, br.y - 30f, br.x - 2f, br.y - 2f)
                quadTo(br.x - 10f, br.y - 4f, (bl.x + br.x) / 2f + 6f, (bl.y + br.y) / 2f)
                cubicTo((bl.x + br.x) / 2f + 2f, sR.y + 40f, sR.x - 4f, sR.y + 20f, sR.x - 2f, sR.y + 2f)
            }
        }
        taper(P(sL.x + 4f, sL.y + 20f), P(bl.x + 14f, (sL.y + bl.y) / 2f + 10f), P(bl.x + 12f, bl.y - 6f), w0 = 0.2f, w1 = 1.2f, w2 = 0.2f, color = CK.deep(c))
    }

    // ------------------------------------------------------------------ bacaklar

    private fun legColor(): String = when (look.legwear) {
        Legwear.PANTS, Legwear.ROBE -> look.second
        Legwear.TIGHTS -> look.main
        Legwear.SHORTS, Legwear.SKIRT, Legwear.PENCIL -> look.skin
        Legwear.MECHA -> "#5B6573"
    }

    private fun legs() {
        // Uzak bacak önce: dönükken yüzün baktığı yandaki bacak arkada kalır
        val order = if (pose.turn > 0.2f) listOf(false, true) else listOf(true, false)
        for (left in order) leg(left)
    }

    private fun leg(left: Boolean) {
        val h = if (left) sk.hipL else sk.hipR
        val k = if (left) sk.kneeL else sk.kneeR
        val a = if (left) sk.ankleL else sk.ankleR
        val color = legColor()
        val bulk = when (look.legwear) { Legwear.PANTS, Legwear.ROBE -> 0.9f; Legwear.TIGHTS -> 0.2f; else -> 0f }
        if (robot) {
            // Robot bacağı: koyu eklemli iç, açık zırh plakaları
            limb(h, k, a, body.legR0 * 0.8f, body.legR1 * 0.7f, body.legR2 * 0.7f, "#5B6573")
            capsule(along(h, k, 0.12f), along(h, k, 0.82f), body.legR0 * 1.05f, body.legR1 * 0.95f).fill("#D5DCE4").stroke(ink).width(OUT)
            celSeg(along(h, k, 0.12f), along(h, k, 0.82f), body.legR0 * 1.05f, body.legR1 * 0.95f, CK.shade("#D5DCE4"))
            capsule(along(k, a, 0.18f), along(k, a, 0.9f), body.legR1 * 1.1f, body.legR2 * 1.05f).fill("#D5DCE4").stroke(ink).width(OUT)
            celSeg(along(k, a, 0.18f), along(k, a, 0.9f), body.legR1 * 1.1f, body.legR2 * 1.05f, CK.shade("#D5DCE4"))
            fill("#F28A2E") { circle(k.x, k.y, body.legR1 * 0.75f) }
            line(CK.STEEL_D, FINE) { line(along(k, a, 0.45f).x - 3f, along(k, a, 0.45f).y, along(k, a, 0.45f).x + 3f, along(k, a, 0.45f).y) }
        } else {
            limb(h, k, a, body.legR0 + bulk, body.legR1 + bulk, body.legR2 + bulk * 0.8f, color)
        }
        when (look.legwear) {
            Legwear.SHORTS -> {
                val e = along(h, k, 0.55f)
                capsule(h, e, body.legR0 + 2.2f, body.legR0 + 1.6f).fill(look.second).stroke(ink).width(MID)
                celSeg(h, e, body.legR0 + 2.2f, body.legR0 + 1.6f, CK.shade(look.second))
                if (outfit == Outfit.JERSEY) capsule(along(h, e, 0.3f), along(h, e, 0.98f), 1.2f).fill(look.accent).noStroke()
                // Çorap
                capsule(along(k, a, 0.55f), a, body.legR2 + 0.9f, body.legR2 + 0.8f).fill(CK.WHITE).stroke(ink).width(MID)
                if (outfit == Outfit.JERSEY) capsule(along(k, a, 0.6f), along(k, a, 0.68f), body.legR2 + 1f).fill(look.accent).noStroke()
            }
            Legwear.SKIRT -> {
                // Diz çorabı
                capsule(along(k, a, 0.08f), a, body.legR1 + 0.5f, body.legR2 + 0.7f).fill("#2A2A3A").stroke(ink).width(MID)
                celSeg(along(k, a, 0.08f), a, body.legR1 + 0.5f, body.legR2 + 0.7f, CK.deep("#2A2A3A"))
            }
            else -> Unit
        }
        if (outfit == Outfit.ARMOR) {
            // Baldır zırhı ve dizlik
            capsule(along(k, a, 0.12f), along(k, a, 0.86f), body.legR1 + 1.6f, body.legR2 + 1.8f).fill(CK.STEEL).stroke(ink).width(MID)
            celSeg(along(k, a, 0.12f), along(k, a, 0.86f), body.legR1 + 1.6f, body.legR2 + 1.8f, CK.shade(CK.STEEL))
            fill(CK.light(CK.STEEL)) { circle(k.x, k.y, body.legR1 + 1.6f) }
            spot(CK.WHITE) { circle(k.x - 1.2f, k.y - 1.4f, 1.3f) }.alpha(0.8f)
        }
        boot(k, a, left)
        shoe(a, k, left)
    }

    /** Çizme konçu (kahraman, şövalye, astronot, polis). */
    private fun boot(k: P, a: P, left: Boolean) {
        val (top, color) = when (look.shoe) {
            Shoe.BOOT -> 0.62f to look.shoeColor
            Shoe.HERO_BOOT -> 0.12f to look.shoeColor
            Shoe.SPACE -> 0.5f to look.shoeColor
            else -> return
        }
        val s = along(k, a, top)
        capsule(s, a, body.legR1 + 1.2f, body.legR2 + 1.5f).fill(color).stroke(ink).width(MID)
        celSeg(s, a, body.legR1 + 1.2f, body.legR2 + 1.5f, CK.shade(color))
        if (look.shoe == Shoe.HERO_BOOT) {
            // Konç ağzı: V kesik
            val d = a - k; val len = max(0.01f, hypot(d.x, d.y)); val n = P(-d.y / len, d.x / len)
            val r = body.legR1 + 2.4f
            fill(color) { poly(s.x + n.x * r, s.y + n.y * r, s.x - n.x * r, s.y - n.y * r, along(k, a, 0.32f).x, along(k, a, 0.32f).y, closed = true) }
        }
        if (look.shoe == Shoe.SPACE) capsule(along(k, a, top + 0.02f), along(k, a, top + 0.12f), body.legR1 + 1.6f).fill("#F28A2E").stroke(ink).width(FINE)
    }

    private fun shoe(a: P, k: P, left: Boolean) {
        val planted = a.y + body.legR2 + 2.4f >= sk.ground - 2.5f
        val shinA = angleOf(a - k)
        val footA = (if (left) pose.footL else pose.footR) ?: if (planted) 0f else shinA * 0.8f
        // Ayak ucu: dönükken baktığı yana, değilse dışa
        val toe = when {
            pose.turn > 0.25f -> 1f
            pose.turn < -0.25f -> -1f
            else -> if (left) -1f else 1f
        }
        val side = abs(pose.turn) > 0.25f || !planted
        val fl = body.footLen * (if (side) 1f else 0.78f)
        val r2 = body.legR2 + if (look.shoe == Shoe.SNEAKER || look.shoe == Shoe.SHOE || look.shoe == Shoe.LOAFER) 0.6f else 1.4f
        val sole = body.legR2 + 2.4f
        val color = look.shoeColor
        frame(a, -footA) {
            fun x(v: Float) = v * toe
            val pts = listOf(
                P(x(-r2 * 1.02f), -r2 * 0.4f), P(x(r2 * 0.95f), -r2 * 0.55f), P(x(fl * 0.42f), sole * 0.15f), P(x(fl * 0.72f), sole * 0.5f),
                P(x(fl * 0.8f), sole * 0.92f), P(x(fl * 0.5f), sole), P(x(-fl * 0.22f), sole), P(x(-r2 * 1.15f), sole * 0.55f),
            )
            fill(color, OUT) { points(VectorBuilder.catmullRom(pts, true, 0.45f, 6), closed = true) }
            when (look.shoe) {
                Shoe.SNEAKER -> {
                    spot(CK.WHITE) { poly(x(-fl * 0.24f), sole - 2f, x(fl * 0.68f), sole - 2f, x(fl * 0.55f), sole - 0.4f, x(-fl * 0.18f), sole - 0.4f, closed = true) }
                    line(ink, FINE) { line(x(-fl * 0.24f), sole - 2f, x(fl * 0.68f), sole - 2f) }
                    line(CK.WHITE, 0.9f) { line(x(r2 * 0.6f), -r2 * 0.1f, x(fl * 0.2f), sole * 0.12f) }
                }
                Shoe.MECHA -> fill("#F28A2E") { roundRect(x(fl * 0.1f) - 2f, sole * 0.25f, 4f, 3f, 1f) }
                Shoe.SABATON -> for (i in 1..2) line(CK.STEEL_D, FINE) { line(x(fl * (0.1f + i * 0.18f)), sole * 0.1f, x(fl * (0.08f + i * 0.18f)), sole * 0.95f) }
                else -> spot(CK.light(color)) { ellipse(x(fl * 0.35f), sole * 0.35f, fl * 0.16f, 1.2f) }.alpha(0.7f)
            }
        }
    }

    // ------------------------------------------------------------------ alt giysi (leğen, etek, uzun palto, cüppe)

    private fun lowerGarment() {
        val w = body.waistW * sk.widthK; val h = body.hipW * sk.widthK
        val r0 = body.legR0
        when (look.legwear) {
            Legwear.PANTS, Legwear.SHORTS, Legwear.TIGHTS, Legwear.MECHA -> hipFrame {
                val c = if (robot) "#5B6573" else if (look.legwear == Legwear.TIGHTS) legColor() else look.second
                fill(c, OUT) {
                    smooth(-w - 0.5f, -T * 0.34f, -h, -3f, -h * 0.98f, 2f + r0 * 0.55f, 0f, 2f + r0 * 0.9f, h * 0.98f, 2f + r0 * 0.55f, h, -3f, w + 0.5f, -T * 0.34f, closed = true, tension = 0.4f)
                }
                if (!robot) line(CK.shade(c), FINE) { line(0f, -T * 0.2f, 0f, 2f + r0 * 0.7f) }
                if (robot) fill("#D5DCE4") { poly(-w * 0.7f, -T * 0.3f, w * 0.7f, -T * 0.3f, w * 0.35f, 2f + r0 * 0.4f, -w * 0.35f, 2f + r0 * 0.4f, closed = true) }
            }
            Legwear.SKIRT -> skirt(pleats = true)
            Legwear.PENCIL -> skirt(pleats = false)
            Legwear.ROBE -> Unit
        }
        when (outfit) {
            Outfit.LABCOAT, Outfit.TRENCH -> coatTails()
            Outfit.ROBE -> robeSkirt()
            Outfit.ARMOR -> tabard()
            else -> Unit
        }
        if (Extra.APRON in look.extras) apron()
    }

    /** Etek: belden dizlere, bacakların açıklığına göre genişler (pileli ya da dar). */
    private fun skirt(pleats: Boolean) {
        val c = look.second
        val w = body.waistW * sk.widthK
        val kl = sk.kneeL; val kr = sk.kneeR
        val p = sk.pelvis
        // Kalça çerçevesinde dizlerin yeri
        fun loc(q: P) = rotV(q - p, -pose.lean * 0.3f)
        val lk = loc(kl); val rk = loc(kr)
        val len = if (pleats) 0.42f else 0.62f
        val yL = 2f + (lk.y - 2f) * len; val yR = 2f + (rk.y - 2f) * len
        val xL = min(-body.hipW, lk.x * len - body.legR0) - (if (pleats) 5f else 1f)
        val xR = max(body.hipW, rk.x * len + body.legR0) + (if (pleats) 5f else 1f)
        hipFrame {
            fill(c, OUT) {
                smooth(-w, -T * 0.36f, -body.hipW - 1f, -4f, xL, yL, (xL + xR) / 2f, (yL + yR) / 2f + 2f, xR, yR, body.hipW + 1f, -4f, w, -T * 0.36f, closed = true, tension = 0.35f)
            }
            spot(CK.shade(c)) { poly(xR - 6f, yR - 1f, body.hipW * 0.5f, -2f, body.hipW * 0.8f, -4f, xR - 1f, yR - 2f, closed = true) }.alpha(0.9f)
            if (pleats) for (i in -2..2) {
                val bx = (xL + xR) / 2f + i * (xR - xL) / 5.4f
                line(CK.deep(c), FINE) { line(i * w * 0.32f, -T * 0.2f, bx, (yL + yR) / 2f + 1f) }
            } else line(CK.deep(c), FINE) { line(xR * 0.4f, (yL + yR) / 2f - 7f, xR * 0.42f, (yL + yR) / 2f + 1f) }
            fill(CK.shade(c)) { roundRect(-w - 0.5f, -T * 0.38f, w * 2f + 1f, 4f, 1.5f) }
        }
    }

    /** Uzun palto/önlük etekleri: belden dizlere, iki parça. */
    private fun coatTails() {
        val c = look.main
        val w = body.waistW * sk.widthK
        val lowL = P(sk.kneeL.x - body.legR1 - 6f, sk.kneeL.y + 6f)
        val lowR = P(sk.kneeR.x + body.legR1 + 6f, sk.kneeR.y + 6f)
        val p = sk.pelvis
        val wl = p + rotV(P(-w - 1f, -T * 0.3f), pose.lean * 0.6f)
        val wr = p + rotV(P(w + 1f, -T * 0.3f), pose.lean * 0.6f)
        val mid = p + rotV(P(0f, -T * 0.3f), pose.lean * 0.6f)
        val crotchL = p + P(-1.5f, body.legR0 + 6f); val crotchR = p + P(1.5f, body.legR0 + 6f)
        fill(c, OUT) { smooth(wl.x, wl.y, wl.x - 3f, p.y + 2f, lowL.x, lowL.y, (lowL.x + crotchL.x) / 2f + 2f, max(lowL.y, crotchL.y) - 1f, crotchL.x, crotchL.y, mid.x - 1f, mid.y, closed = true, tension = 0.3f) }
        fill(c, OUT) { smooth(wr.x, wr.y, wr.x + 3f, p.y + 2f, lowR.x, lowR.y, (lowR.x + crotchR.x) / 2f - 2f, max(lowR.y, crotchR.y) - 1f, crotchR.x, crotchR.y, mid.x + 1f, mid.y, closed = true, tension = 0.3f) }
        spot(CK.shade(c)) { smooth(wr.x - 1f, wr.y + 4f, lowR.x - 1f, lowR.y - 3f, (lowR.x + crotchR.x) / 2f, lowR.y - 4f, wr.x - 6f, wr.y + 10f, closed = true) }.alpha(0.85f)
        line(CK.deep(c), FINE) { line(along(wl, lowL, 0.3f).x + 4f, along(wl, lowL, 0.3f).y, along(wl, lowL, 0.9f).x + 5f, along(wl, lowL, 0.9f).y - 2f) }
    }

    /** Cüppenin alt kısmı: belden baldırlara, bacakları örter. */
    private fun robeSkirt() {
        val c = look.main
        val p = sk.pelvis
        val lowY = max(sk.ankleL.y, sk.ankleR.y) - 12f
        val l = min(sk.kneeL.x, sk.ankleL.x) - 10f
        val r = max(sk.kneeR.x, sk.ankleR.x) + 10f
        val w = body.waistW * sk.widthK
        val wl = p + rotV(P(-w - 1f, -T * 0.3f), pose.lean * 0.5f)
        val wr = p + rotV(P(w + 1f, -T * 0.3f), pose.lean * 0.5f)
        val yl = min(lowY, sk.ankleL.y - 8f); val yr = min(lowY, sk.ankleR.y - 8f)
        fill(c, OUT) {
            path(closed = true) {
                moveTo(wl.x, wl.y)
                cubicTo(wl.x - 6f, p.y + 10f, l + 2f, yl - 30f, l, yl)
                quadTo((l + r) / 2f, max(yl, yr) + 7f, r, yr)
                cubicTo(r - 2f, yr - 30f, wr.x + 6f, p.y + 10f, wr.x, wr.y)
            }
        }
        spot(CK.shade(c)) { poly((l + r) / 2f + 6f, max(yl, yr) + 3f, r - 2f, yr, wr.x + 2f, p.y + 6f, wr.x - 4f, wr.y + 6f, closed = true) }.alpha(0.9f)
        // Ön açıklık ve etek süsü (altın şerit)
        taper(P(p.x + 1f, wl.y + 4f), P(p.x + 3f, p.y + 20f), P((l + r) / 2f + 4f, max(yl, yr) + 4f), w0 = 2.2f, w1 = 2.6f, w2 = 2.6f, color = look.trim)
        line(look.trim, 1.6f) { path { moveTo(l + 2f, yl - 3f); quadTo((l + r) / 2f, max(yl, yr) + 3f, r - 2f, yr - 3f) } }
        for ((fx, fy) in listOf(0.3f to 0.45f, 0.7f to 0.3f, 0.2f to 0.75f, 0.78f to 0.7f)) {
            val x = l + (r - l) * fx; val y = p.y + (max(yl, yr) - p.y) * fy
            spot(CK.GOLD) { star(x, y, 2.6f, 1.1f, 5) }
        }
    }

    /** Şövalye: kırmızı armalı önlük (göğüsten uyluk ortasına). */
    private fun tabard() {
        val c = look.accent
        val p = sk.pelvis
        val k = (sk.kneeL.y + sk.kneeR.y) / 2f
        val bottom = p.y + (k - p.y) * 0.55f
        val w = body.waistW * 0.62f
        frame(p, pose.lean * 0.5f) {
            fill(c, OUT) { poly(-w, -T * 0.4f, w, -T * 0.4f, w + 1f, bottom - p.y - 4f, 0f, bottom - p.y + 3f, -w - 1f, bottom - p.y - 4f, closed = true) }
            spot(CK.shade(c)) { poly(w * 0.2f, -T * 0.4f, w, -T * 0.4f, w + 1f, bottom - p.y - 4f, w * 0.3f, bottom - p.y - 1f, closed = true) }
            line(look.trim, 1.2f) { poly(-w + 1.5f, -T * 0.38f, -w + 1.5f, bottom - p.y - 5f, 0f, bottom - p.y + 1f, w - 1.5f, bottom - p.y - 5f, w - 1.5f, -T * 0.38f) }
        }
    }

    private fun apron() {
        val p = sk.pelvis
        val k = (sk.kneeL.y + sk.kneeR.y) / 2f
        val bottom = p.y + (k - p.y) * 0.85f
        val w = body.waistW + 3f
        frame(p, pose.lean * 0.4f) {
            fill("#FBFBF8", OUT) { path(closed = true) { moveTo(-w, -T * 0.32f); lineTo(w, -T * 0.32f); lineTo(w + 3f, bottom - p.y - 3f); quadTo(0f, bottom - p.y + 3f, -w - 3f, bottom - p.y - 3f) } }
            spot("#E6E2D6") { poly(w * 0.3f, -T * 0.3f, w, -T * 0.3f, w + 3f, bottom - p.y - 3f, w * 0.4f, bottom - p.y, closed = true) }
            fill(look.accent) { roundRect(-w - 0.5f, -T * 0.36f, w * 2f + 1f, 3.6f, 1.5f) }
        }
    }

    // ------------------------------------------------------------------ gövde

    /** Gövde dış hattı (gövde çerçevesinde; [hem] alt kenar, [bulk] giysi kalınlığı). */
    fun torsoOutline(hem: Float, bulk: Float = 0f): List<P> {
        val k = sk.widthK
        val s = (body.shoulderOut + bulk) * k; val c = (body.chestW + bulk) * k
        val w = (body.waistW + bulk) * k; val h = (body.hipW + bulk) * k
        val top = -T - 4.5f - bulk * 0.5f
        val n = body.neckR + 1.4f
        val shift = sk.bodyTurn * 2f
        val half = listOf(
            P(-n, top), P(-s * 0.55f, top + 1.8f), P(-s * 0.88f, top + 3.4f), P(-s, top + 8f),
            P(-c, -T + 16f), P(-w, -T * 0.36f), P(-h, hem - 4f), P(-h * 0.97f, hem),
        )
        val pts = half + half.reversed().map { P(-it.x, it.y) }
        return VectorBuilder.catmullRom(pts.map { P(it.x + shift * (-it.y / T).coerceIn(0f, 1f), it.y) }, true, 0.45f, 8)
    }

    /** Gövde hattının [y] yüksekliğindeki sol ve sağ kenarı. */
    fun edgesAt(outline: List<P>, y: Float): Pair<Float, Float> {
        var l = Float.MAX_VALUE; var r = -Float.MAX_VALUE
        for (i in outline.indices) {
            val a = outline[i]; val c = outline[(i + 1) % outline.size]
            if ((a.y - y) * (c.y - y) <= 0f && a.y != c.y) {
                val x = a.x + (c.x - a.x) * (y - a.y) / (c.y - a.y)
                l = min(l, x); r = max(r, x)
            }
        }
        if (l > r) return 0f to 0f
        return l to r
    }

    /** Gövde hattına göre yatay şerit (kemer, göğüs bandı): [y0]..[y1], kenarlardan [inset] içeride. */
    fun band(outline: List<P>, y0: Float, y1: Float, inset: Float = 0.6f): List<P> {
        val (l0, r0) = edgesAt(outline, y0); val (l1, r1) = edgesAt(outline, y1)
        return listOf(P(l0 + inset, y0), P(r0 - inset, y0), P(r1 - inset, y1), P(l1 + inset, y1))
    }

    /** Gövdenin sağ yanındaki cel gölge. */
    fun torsoShadow(outline: List<P>, color: String, from: Float, to: Float, depth: Float) {
        val right = outline.filter { it.x > 0f && it.y in from..to }.sortedBy { it.y }
        if (right.size < 2) return
        val inner = right.reversed().mapIndexed { i, p ->
            val t = i / (right.size - 1f)
            P(p.x - depth * (0.7f + 0.3f * sin(t * Math.PI.toFloat())), p.y)
        }
        spot(color) { points(right + inner, closed = true) }
    }

    private fun neck() {
        if (robot) {
            capsule(sk.chest, sk.neckTop, body.neckR * 1.1f).fill("#5B6573").stroke(ink).width(OUT)
            for (i in 1..2) line(CK.STEEL_D, FINE) { val q = along(sk.chest, sk.neckTop, i * 0.3f); line(q.x - 4f, q.y, q.x + 4f, q.y) }
            return
        }
        if (Extra.SPACE_HELMET in look.extras) return
        val skin = look.skin
        capsule(along(sk.chest, sk.neckTop, -0.1f), along(sk.chest, sk.neckTop, 0.9f), body.neckR, body.neckR * 0.92f).fill(skin).stroke(ink).width(OUT)
    }

    private fun torso() {
        when (outfit) {
            Outfit.ROBE -> robeTop()
            Outfit.MECHA -> mechaTop()
            else -> clothTop()
        }
    }

    /** Giysili gövde: taban biçim, cel gölge, kıyafete özgü ayrıntılar. */
    private fun clothTop() {
        val hem = when (outfit) {
            Outfit.TEE, Outfit.JERSEY, Outfit.CARDIGAN, Outfit.SUIT, Outfit.LABCOAT, Outfit.TRENCH, Outfit.CHEF -> 5f
            Outfit.SAILOR -> -T * 0.22f
            Outfit.SPACESUIT -> 6f
            Outfit.ARMOR -> -T * 0.1f
            else -> -T * 0.26f
        }
        val bulk = when (outfit) { Outfit.SPACESUIT -> 3f; Outfit.ARMOR -> 1.5f; Outfit.TRENCH, Outfit.LABCOAT -> 1f; else -> 0.4f }
        val base = when (outfit) { Outfit.ARMOR -> CK.STEEL; else -> look.main }
        val o = torsoOutline(hem, bulk)
        val cx = sk.bodyTurn * body.waistW * 0.3f
        torsoFrame {
            fill(base, OUT) { points(o, closed = true) }
            torsoShadow(o, CK.shade(base), -T + 12f, hem - 1f, body.waistW * 0.42f)
            topDetails(o, hem, cx)
            if (body.muscle > 0.3f && outfit == Outfit.HEROSUIT) muscles(cx)
            if (body.female && body.bust > 0f && outfit != Outfit.SPACESUIT && outfit != Outfit.LABCOAT) {
                val y = -T * 0.62f
                line(CK.deep(base), FINE) { arc(cx - body.chestW * 0.42f, y, body.chestW * 0.34f, 4f * body.bust + 1.5f, 20f, 120f) }
                line(CK.deep(base), FINE) { arc(cx + body.chestW * 0.42f, y, body.chestW * 0.34f, 4f * body.bust + 1.5f, 40f, 120f) }
            }
        }
    }

    /** Kahraman kas çizgileri: göğüs, karın. */
    private fun muscles(cx: Float) {
        val c = CK.deep(look.main)
        val y = -T * 0.62f
        taper(P(cx - body.chestW * 0.75f, y - 6f), P(cx - body.chestW * 0.4f, y + 2f), P(cx - 1f, y - 1f), w0 = 0.3f, w1 = 1.3f, w2 = 0.3f, color = c)
        taper(P(cx + 1f, y - 1f), P(cx + body.chestW * 0.4f, y + 2f), P(cx + body.chestW * 0.75f, y - 6f), w0 = 0.3f, w1 = 1.3f, w2 = 0.3f, color = c)
        for (i in 0..2) {
            val yy = -T * 0.42f + i * T * 0.1f
            taper(P(cx - 6f, yy), P(cx - 3f, yy + 1.2f), P(cx - 0.8f, yy), w0 = 0.2f, w1 = 0.9f, w2 = 0.2f, color = c, alpha = 0.8f)
            taper(P(cx + 0.8f, yy), P(cx + 3f, yy + 1.2f), P(cx + 6f, yy), w0 = 0.2f, w1 = 0.9f, w2 = 0.2f, color = c, alpha = 0.8f)
        }
        taper(P(cx, -T * 0.48f), P(cx, -T * 0.2f), P(cx, -T * 0.08f), w0 = 0.2f, w1 = 0.8f, w2 = 0.1f, color = c, alpha = 0.8f)
    }

    private fun topDetails(o: List<P>, hem: Float, cx: Float) {
        val top = -T - 4.5f
        val n = body.neckR
        val main = look.main
        when (outfit) {
            Outfit.TEE -> {
                // Göğüste geniş şerit ve yaka
                spot(look.accent) { points(band(o, -T * 0.6f, -T * 0.44f), closed = true) }
                spot(CK.shade(look.accent)) { points(band(o, -T * 0.6f, -T * 0.44f).let { listOf(P(it[1].x - 4f, it[1].y), it[1], it[2], P(it[2].x - 4f, it[2].y)) }, closed = true) }
                fill(CK.WHITE, FINE) { star(cx - 1f, -T * 0.52f, 4.2f, 1.9f, 5) }
                fill(CK.shade(main), FINE) { path(closed = true) { moveTo(cx - n - 1.5f, top + 0.5f); quadTo(cx, top + 6f, cx + n + 1.5f, top + 0.5f); quadTo(cx, top + 3.5f, cx - n - 1.5f, top + 0.5f) } }
            }
            Outfit.SAILOR -> {
                // Denizci yakası, kurdele, manşet şeritleri
                val col = look.second
                val v = -T * 0.5f
                fill(col, MID) { poly(cx - n - 2f, top, cx, v, cx + n + 2f, top, cx + body.shoulderOut * 0.86f, top + 4f, cx + body.chestW * 0.55f, top + 13f, cx, v + 6f, cx - body.chestW * 0.55f, top + 13f, cx - body.shoulderOut * 0.86f, top + 4f, closed = true) }
                line(CK.WHITE, 0.9f) { poly(cx - body.shoulderOut * 0.78f, top + 5f, cx - body.chestW * 0.5f, top + 12f, cx, v + 4f, cx + body.chestW * 0.5f, top + 12f, cx + body.shoulderOut * 0.78f, top + 5f) }
                spot(CK.skinShade(look.skin)) { poly(cx - n - 1f, top, cx, v - 1f, cx + n + 1f, top, closed = true) }.alpha(0f)
                fill(look.skin, FINE) { poly(cx - n - 0.5f, top + 0.5f, cx, v - 4f, cx + n + 0.5f, top + 0.5f, closed = true) }
                bow(cx, v + 2f, look.accent, 1f)
            }
            Outfit.SUIT -> {
                val v = -T * 0.42f
                fill(look.trim, FINE) { poly(cx - n - 1.5f, top, cx, v, cx + n + 1.5f, top, closed = true) }
                // Yakalar
                fill(CK.shade(main), MID) { poly(cx - n - 1.5f, top, cx - 1f, v + 1f, cx - 7f, v - 6f, cx - 10f, top + 12f, cx - 7f, top + 9f, cx - n - 4f, top + 1f, closed = true) }
                fill(CK.shade(main), MID) { poly(cx + n + 1.5f, top, cx + 1f, v + 1f, cx + 7f, v - 6f, cx + 10f, top + 12f, cx + 7f, top + 9f, cx + n + 4f, top + 1f, closed = true) }
                line(CK.deep(main), FINE) { line(cx, v + 1f, cx, hem - 1f) }
                fill(CK.GOLD_D, FINE) { circle(cx + 1.5f, v + 6f, 1.1f) }
                fill(CK.GOLD_D, FINE) { circle(cx + 1.5f, v + 13f, 1.1f) }
                fill(CK.WHITE, FINE) { poly(cx - body.chestW * 0.72f, -T * 0.62f, cx - body.chestW * 0.42f, -T * 0.62f, cx - body.chestW * 0.5f, -T * 0.66f, cx - body.chestW * 0.62f, -T * 0.67f, closed = true) }
                line(CK.deep(main), FINE) { line(cx - body.chestW * 0.75f, -T * 0.62f, cx - body.chestW * 0.4f, -T * 0.62f) }
            }
            Outfit.CARDIGAN -> {
                val v = -T * 0.3f
                fill(look.trim, FINE) { poly(cx - n - 1f, top, cx - 2f, v, cx + 2f, v, cx + n + 1f, top, closed = true) }
                spot(CK.shade(look.trim)) { poly(cx - n - 1f, top, cx + n + 1f, top, cx, top + 5f, closed = true) }
                line(CK.deep(main), MID) { line(cx - n - 1f, top + 1f, cx - 2.5f, hem - 1f) }
                line(CK.deep(main), MID) { line(cx + n + 1f, top + 1f, cx + 2.5f, hem - 1f) }
                for (i in 0..2) fill(CK.deep(main), FINE) { circle(cx - 4.5f, v + 4f + i * 6f, 1.1f) }
                fill(CK.shade(main), FINE) { roundRect(cx - body.waistW * 0.85f, -T * 0.2f, 8f, 7f, 1.5f) }
                fill(CK.shade(main), FINE) { roundRect(cx + body.waistW * 0.85f - 8f, -T * 0.2f, 8f, 7f, 1.5f) }
            }
            Outfit.BLOUSE -> {
                fill(look.trim, FINE) { poly(cx - n - 2f, top - 0.5f, cx - 1f, top + 7f, cx - 6f, top + 9f, cx - 9f, top + 3f, closed = true) }
                fill(look.trim, FINE) { poly(cx + n + 2f, top - 0.5f, cx + 1f, top + 7f, cx + 6f, top + 9f, cx + 9f, top + 3f, closed = true) }
                line(CK.deep(main), FINE) { line(cx, top + 7f, cx, hem) }
                for (i in 0..2) fill(look.trim, FINE) { circle(cx + 1.2f, top + 12f + i * 7f, 0.9f) }
                // Kolye
                line(CK.GOLD, 0.8f) { arc(cx, top + 2f, n + 3f, 8f, 20f, 140f) }
                fill(CK.GOLD, FINE) { circle(cx, top + 10f, 1.4f) }
            }
            Outfit.LABCOAT -> {
                val v = -T * 0.35f
                fill(look.second, FINE) { poly(cx - n - 1.5f, top, cx, v, cx + n + 1.5f, top, closed = true) }
                fill(look.skin, FINE) { poly(cx - n, top + 0.5f, cx, top + 7f, cx + n, top + 0.5f, closed = true) }
                fill(CK.shade(main), MID) { poly(cx - n - 1.5f, top, cx - 1f, v, cx - 2f, hem - 1f, cx - 6f, hem - 1f, cx - 8f, v - 4f, cx - 11f, top + 10f, closed = true) }
                fill(main, MID) { poly(cx + n + 1.5f, top, cx + 1f, v, cx + 2f, hem - 1f, cx + 6f, hem - 1f, cx + 8f, v - 4f, cx + 11f, top + 10f, closed = true) }
                fill(main, FINE) { roundRect(cx - body.chestW * 0.78f, -T * 0.62f, 8f, 7f, 1f) }
                line("#3E7CC9", 1f) { line(cx - body.chestW * 0.7f, -T * 0.62f - 3f, cx - body.chestW * 0.7f, -T * 0.62f + 2f) }
                fill(CK.WHITE, FINE) { roundRect(cx + body.chestW * 0.3f, -T * 0.58f, 7f, 9f, 1f) }
                spot("#3E7CC9") { rect(cx + body.chestW * 0.3f + 1f, -T * 0.58f + 1.5f, 5f, 2f) }
            }
            Outfit.UNIFORM -> {
                val v = -T * 0.55f
                fill(CK.light(main), FINE) { poly(cx - n - 2f, top, cx, top + 6f, cx + n + 2f, top, cx + n + 4f, top + 4f, cx, top + 9f, cx - n - 4f, top + 4f, closed = true) }
                fill(look.accent, FINE) { poly(cx - 1.5f, top + 6f, cx + 1.5f, top + 6f, cx + 2.2f, v + 10f, cx, v + 13f, cx - 2.2f, v + 10f, closed = true) }
                for (sx in listOf(-1f, 1f)) {
                    fill(CK.shade(main), FINE) { roundRect(cx + sx * body.chestW * 0.55f - 4.5f, -T * 0.66f, 9f, 3.4f, 1f) }
                    line(CK.deep(main), FINE) { roundRect(cx + sx * body.chestW * 0.55f - 4.5f, -T * 0.66f + 3.4f, 9f, 7f, 1f) }
                }
                if (Extra.BADGE in look.extras) {
                    fill(CK.GOLD, FINE) { path(closed = true) { val bx = cx - body.chestW * 0.55f; val by = -T * 0.72f; moveTo(bx - 3.2f, by - 3f); lineTo(bx + 3.2f, by - 3f); lineTo(bx + 3f, by + 1.5f); quadTo(bx, by + 4.5f, bx - 3f, by + 1.5f) } }
                    spot(CK.GOLD_D) { star(cx - body.chestW * 0.55f, -T * 0.72f, 1.6f, 0.7f, 5) }
                }
                fill(CK.WHITE, FINE) { roundRect(cx + body.chestW * 0.35f, -T * 0.76f, 7f, 2.4f, 0.8f) }
            }
            Outfit.TRENCH -> {
                val v = -T * 0.36f
                fill(look.second, FINE) { poly(cx - n - 2f, top, cx, v, cx + n + 2f, top, closed = true) }
                // Kalkık yaka ve geniş yakalar
                fill(CK.shade(main), MID) { poly(cx - n - 1f, top - 5f, cx - n - 6f, top - 2f, cx - 13f, top + 12f, cx - 2f, v + 2f, cx - n - 1f, top + 2f, closed = true) }
                fill(main, MID) { poly(cx + n + 1f, top - 5f, cx + n + 6f, top - 2f, cx + 13f, top + 12f, cx + 2f, v + 2f, cx + n + 1f, top + 2f, closed = true) }
                for (i in 0..1) for (sx in listOf(-1f, 1f)) fill(CK.deep(main), FINE) { circle(cx + sx * 5.5f, v + 5f + i * 7f, 1.2f) }
                fill(CK.shade(main), MID) { points(band(o, -T * 0.3f, -T * 0.22f, 0.2f), closed = true) }
                fill(CK.GOLD_D, FINE) { roundRect(cx - 3f, -T * 0.31f, 6f, 5f, 1f) }
            }
            Outfit.CHEF -> {
                for (i in 0..2) for (sx in listOf(-1f, 1f)) fill("#5B6270", FINE) { circle(cx + sx * 5.5f, -T * 0.68f + i * 8f, 1.4f) }
                line("#D9D4C8", FINE) { path { moveTo(cx - n - 2f, top + 2f); quadTo(cx + 3f, -T * 0.5f, cx + 2f, hem - 1f) } }
                spot("#E6E2D6") { roundRect(cx - n - 2f, top - 1f, (n + 2f) * 2f, 4f, 2f) }
            }
            Outfit.JERSEY -> {
                // V yaka, yan şeritler, numara
                fill(look.skin, FINE) { poly(cx - n - 1f, top, cx, top + 8f, cx + n + 1f, top, closed = true) }
                line(CK.WHITE, 1.4f) { poly(cx - n - 1.5f, top + 0.5f, cx, top + 9f, cx + n + 1.5f, top + 0.5f) }
                for (sx in listOf(-1f, 1f)) spot(CK.WHITE) { val (l, r) = edgesAt(o, -T * 0.4f); val x = if (sx < 0f) l + 1.2f else r - 3.6f; roundRect(x, -T + 16f, 2.4f, T * 0.7f, 1.2f) }
                spot(CK.WHITE) { poly(cx - 5f, -T * 0.68f, cx + 5f, -T * 0.68f, cx + 5f, -T * 0.62f, cx + 0.5f, -T * 0.3f, cx - 3f, -T * 0.3f, cx + 1.2f, -T * 0.615f, cx - 5f, -T * 0.615f, closed = true) }
            }
            Outfit.HEROSUIT -> {
                // Yan paneller, göğüs arması (özgün: eşkenar dörtgen içinde çift köşeli ok), altın kemer
                val panel = look.second
                for (sx in listOf(-1f, 1f)) spot(panel) {
                    val (l, r) = edgesAt(o, -T * 0.5f)
                    val e = if (sx < 0f) l else r
                    poly(e + sx * -0.4f, -T + 10f, e - sx * 6f, -T * 0.55f, e - sx * 3f, hem - 2f, e - sx * 0.6f, hem - 2f, closed = true)
                }
                val ey = -T * 0.66f
                fill(CK.GOLD, MID) { poly(cx, ey - 9f, cx + 9f, ey, cx, ey + 9f, cx - 9f, ey, closed = true) }
                fill(look.accent, FINE) { poly(cx - 5f, ey + 2f, cx, ey - 4f, cx + 5f, ey + 2f, cx + 3f, ey + 3.6f, cx, ey, cx - 3f, ey + 3.6f, closed = true) }
                fill(look.accent, FINE) { poly(cx - 5f, ey + 6f, cx, ey + 0.5f, cx + 5f, ey + 6f, cx + 3f, ey + 7f, cx, ey + 4.2f, cx - 3f, ey + 7f, closed = true) }
            }
            Outfit.SPACESUIT -> {
                fill("#E4E8EE", MID) { roundRect(cx - 9f, -T * 0.66f, 18f, 13f, 2.5f) }
                spot(CK.RED) { circle(cx - 5f, -T * 0.66f + 4f, 1.6f) }
                spot("#4FAE5A") { circle(cx, -T * 0.66f + 4f, 1.6f) }
                spot("#3E7CC9") { circle(cx + 5f, -T * 0.66f + 4f, 1.6f) }
                spot("#F28A2E") { roundRect(cx - 6f, -T * 0.66f + 8f, 12f, 2.2f, 1f) }
                // Görev arması (özgün: halkalı gezegen)
                fill("#2F4E8C", FINE) { circle(cx - body.chestW * 0.62f, -T * 0.8f, 4f) }
                fill("#F2C14E", FINE) { circle(cx - body.chestW * 0.62f, -T * 0.8f, 1.8f) }
                line(CK.WHITE, 0.6f) { ellipse(cx - body.chestW * 0.62f, -T * 0.8f, 3.4f, 1.1f, -20f) }
                // Boyun halkası
                fill("#9AA6B4", OUT) { roundRect(cx - n - 6f, top - 4f, (n + 6f) * 2f, 6f, 3f) }
                line(CK.shade("#E9EDF2"), FINE) { line(cx - body.waistW, -T * 0.2f, cx + body.waistW, -T * 0.2f) }
            }
            Outfit.ARMOR -> {
                // Göğüs zırhı: parlak kenar, orta sırt, boyunluk
                spot(CK.light(CK.STEEL)) { roundRect(cx - body.chestW * 0.75f, -T + 8f, 5f, T * 0.45f, 2.5f) }.alpha(0.9f)
                line(CK.STEEL_D, FINE) { line(cx, -T + 6f, cx, -T * 0.42f) }
                line(CK.STEEL_D, FINE) { arc(cx, -T * 0.42f, body.chestW * 0.7f, 5f, 20f, 140f) }
                fill(CK.STEEL, MID) { roundRect(cx - n - 3f, top - 2f, (n + 3f) * 2f, 6f, 2.5f) }
                fill(look.accent, MID) { poly(cx - 6f, -T * 0.62f, cx + 6f, -T * 0.62f, cx + 6f, -T * 0.5f, cx, -T * 0.43f, cx - 6f, -T * 0.5f, closed = true) }
                fill(CK.GOLD, FINE) { star(cx, -T * 0.54f, 3.6f, 1.6f, 8) }
            }
            else -> Unit
        }
        // Ortak eklentiler
        if (Extra.TIE in look.extras) {
            val c = look.accent
            fill(c, FINE) { poly(cx - 2f, top + 1f, cx + 2f, top + 1f, cx + 1.4f, top + 4.5f, cx - 1.4f, top + 4.5f, closed = true) }
            fill(c, FINE) { poly(cx - 1.4f, top + 4.5f, cx + 1.4f, top + 4.5f, cx + 3.2f, -T * 0.42f + 2f, cx, -T * 0.42f + 5.5f, cx - 3.2f, -T * 0.42f + 2f, closed = true) }
            spot(CK.shade(c)) { poly(cx + 0.2f, top + 5f, cx + 1.4f, top + 4.5f, cx + 3.2f, -T * 0.42f + 2f, cx + 0.6f, -T * 0.42f + 4.5f, closed = true) }
        }
        if (Extra.NECKERCHIEF in look.extras) {
            fill(look.accent, MID) { poly(cx - n - 3f, top - 0.5f, cx + n + 3f, top - 0.5f, cx + 2f, top + 9f, cx - 2f, top + 9f, closed = true) }
            fill(look.accent, FINE) { circle(cx, top + 3f, 2.6f) }
        }
        if (Extra.SCARF in look.extras) {
            fill(look.accent, MID) { roundRect(cx - n - 5f, top - 4f, (n + 5f) * 2f, 6.5f, 3f) }
            fill(look.accent, MID) { poly(cx + 3f, top + 1f, cx + 8f, top + 1f, cx + 9f, top + 18f, cx + 4f, top + 19f, closed = true) }
            for (i in 0..2) line(CK.light(look.accent), 0.7f) { line(cx + 4.5f + i * 1.6f, top + 19f, cx + 4.7f + i * 1.6f, top + 21f) }
        }
        if (Extra.STETHOSCOPE in look.extras) {
            line("#2B2F3A", 1.5f) { smooth(cx - n - 3f, top + 1f, cx - n - 4f, -T * 0.55f, cx - 2f, -T * 0.45f) }
            line("#2B2F3A", 1.5f) { smooth(cx + n + 3f, top + 1f, cx + n + 4f, -T * 0.6f, cx + 1f, -T * 0.52f, cx - 2f, -T * 0.45f) }
            fill(CK.STEEL, FINE) { circle(cx - 2f, -T * 0.42f, 2.8f) }
        }
        if (Extra.BADGE in look.extras && outfit != Outfit.UNIFORM) {
            fill(CK.GOLD, FINE) { star(cx - body.chestW * 0.5f, -T * 0.7f, 4f, 1.8f, 5) }
        }
        // Kemer
        if (outfit == Outfit.UNIFORM || outfit == Outfit.HEROSUIT || outfit == Outfit.BLOUSE || outfit == Outfit.ARMOR) {
            val c = when (outfit) { Outfit.HEROSUIT -> CK.GOLD; Outfit.UNIFORM -> "#1A1A22"; Outfit.ARMOR -> "#5A3A22"; else -> CK.shade(look.second) }
            val y0 = hem - 4.5f
            fill(c, MID) { points(band(o, y0, hem + 0.5f, -0.3f), closed = true) }
            val bc = if (outfit == Outfit.HEROSUIT) look.accent else CK.GOLD
            fill(bc, FINE) { roundRect(cx - 3.2f, y0 - 0.6f, 6.4f, 6f, 1.4f) }
            if (outfit == Outfit.UNIFORM) for (sx in listOf(-1f, 1f)) fill("#2A2A33", FINE) { roundRect(cx + sx * body.waistW * 0.62f - 3f, y0, 6f, 6.5f, 1.2f) }
        }
    }

    /** Kurdele/fiyonk: iki ilmek, düğüm ve iki uç. */
    fun bow(x: Float, y: Float, c: String, s: Float) {
        fill(c, FINE) { poly(x - 1f * s, y + 1f * s, x - 4f * s, y + 9f * s, x - 1.5f * s, y + 8f * s, x, y + 10f * s, closed = true) }
        fill(c, FINE) { poly(x + 1f * s, y + 1f * s, x + 4f * s, y + 9f * s, x + 1.5f * s, y + 8f * s, x, y + 10f * s, closed = true) }
        fill(c, FINE) { ellipse(x - 4.2f * s, y, 4.2f * s, 2.6f * s, -12f) }
        fill(c, FINE) { ellipse(x + 4.2f * s, y, 4.2f * s, 2.6f * s, 12f) }
        spot(CK.shade(c)) { ellipse(x + 4.8f * s, y + 0.8f * s, 2.4f * s, 1.2f * s, 12f) }
        fill(CK.shade(c), FINE) { roundRect(x - 1.6f * s, y - 1.8f * s, 3.2f * s, 3.6f * s, 1.2f * s) }
    }

    /** Cüppe üstü: omuzdan bele; sakal göğsü örter. */
    private fun robeTop() {
        val o = torsoOutline(4f, 1.5f)
        val cx = sk.bodyTurn * body.waistW * 0.3f
        torsoFrame {
            fill(look.main, OUT) { points(o, closed = true) }
            torsoShadow(o, CK.shade(look.main), -T + 12f, 3f, body.waistW * 0.45f)
            // Yaka şeridi (V), ip kemer ve püskül
            taper(P(cx - body.neckR - 3f, -T - 4f), P(cx - 2f, -T * 0.6f), P(cx, -T * 0.3f), w0 = 2.4f, w1 = 2.6f, w2 = 2.6f, color = look.trim)
            taper(P(cx + body.neckR + 3f, -T - 4f), P(cx + 2f, -T * 0.6f), P(cx, -T * 0.3f), w0 = 2.4f, w1 = 2.6f, w2 = 2.6f, color = look.trim)
            fill("#C9A15A", MID) { points(band(o, -T * 0.33f, -T * 0.26f, -0.3f), closed = true) }
            fill("#C9A15A", FINE) { circle(cx + 4f, -T * 0.29f, 2.4f) }
            taper(P(cx + 4f, -T * 0.27f), P(cx + 5.5f, -T * 0.1f), P(cx + 5f, 4f), w0 = 1.6f, w1 = 1.4f, w2 = 2.6f, color = "#C9A15A")
        }
    }

    /** Robot gövdesi: köşeli göğüs plakası, parlayan çekirdek, karın halkaları. */
    private fun mechaTop() {
        val o = torsoOutline(-T * 0.3f, 0.5f)
        val cx = sk.bodyTurn * body.waistW * 0.3f
        val plate = "#D5DCE4"
        torsoFrame {
            // Karın (koyu, halkalı)
            fill("#5B6573", OUT) { roundRect(cx - body.waistW * 0.8f, -T * 0.42f, body.waistW * 1.6f, T * 0.45f, 4f) }
            for (i in 0..2) line("#3A414C", FINE) { line(cx - body.waistW * 0.7f, -T * 0.32f + i * T * 0.1f, cx + body.waistW * 0.7f, -T * 0.32f + i * T * 0.1f) }
            fill(plate, OUT) { points(o, closed = true) }
            torsoShadow(o, CK.shade(plate), -T + 10f, -T * 0.32f, body.waistW * 0.45f)
            fill("#F28A2E", MID) { poly(cx - body.chestW * 0.62f, -T + 2f, cx - 4f, -T + 2f, cx - 6f, -T + 9f, cx - body.chestW * 0.55f, -T + 9f, closed = true) }
            fill("#F28A2E", MID) { poly(cx + body.chestW * 0.62f, -T + 2f, cx + 4f, -T + 2f, cx + 6f, -T + 9f, cx + body.chestW * 0.55f, -T + 9f, closed = true) }
            line(CK.STEEL_D, FINE) { poly(cx - body.chestW * 0.7f, -T * 0.55f, cx, -T * 0.42f, cx + body.chestW * 0.7f, -T * 0.55f) }
            fill(CK.SCREEN, MID) { circle(cx, -T * 0.66f, 6f) }
            spot(CK.GLOW) { circle(cx, -T * 0.66f, 4f) }.alpha(0.9f)
            spot(CK.WHITE) { circle(cx - 1.2f, -T * 0.66f - 1.4f, 1.5f) }
            spot(CK.light(plate)) { roundRect(cx - body.chestW * 0.78f, -T + 11f, 4f, 12f, 2f) }.alpha(0.9f)
        }
    }

    // ------------------------------------------------------------------ kollar

    private fun arms(layer: Layer) {
        if (pose.layerL == layer) arm(left = true)
        if (pose.layerR == layer) arm(left = false)
    }

    private fun crossedArms() {
        // Kollar göğüste kavuşturulmuş: önce sağ kol, sonra sol kol; sol el sağ pazının altında kalır
        arm(left = false)
        arm(left = true, handVisible = false)
        armSegment(sk.shoulderR, sk.elbowR, upperOnly = true)
    }

    /** Kolun rengi ve kalınlığı (kıyafete göre). */
    private fun sleeveColor(): String = when (outfit) {
        Outfit.TEE, Outfit.JERSEY -> look.skin
        Outfit.MECHA -> "#5B6573"
        Outfit.ARMOR -> "#4A4E5A"
        Outfit.SPACESUIT -> look.main
        Outfit.SAILOR -> "#F4F5FA"
        else -> look.main
    }

    private fun armBulk(): Float = when (outfit) {
        Outfit.TEE, Outfit.JERSEY, Outfit.HEROSUIT -> 0f
        Outfit.SPACESUIT -> 2f
        Outfit.MECHA -> -0.5f
        else -> 0.7f
    }

    private fun armSegment(s: P, e: P, upperOnly: Boolean) {
        val bulk = armBulk()
        val c = sleeveColor()
        capsule(s, e, body.armR0 + bulk, body.armR1 + bulk).fill(c).stroke(ink).width(OUT * 2f)
        capsule(s, e, body.armR0 + bulk, body.armR1 + bulk).fill(c).noStroke()
        celSeg(s, e, body.armR0 + bulk, body.armR1 + bulk, if (c == look.skin) CK.skinShade(c) else CK.shade(c))
        if (upperOnly) sleeveTop(s, e)
    }

    /** Kısa kol ve omuz parçaları (pazı üstünde). */
    private fun sleeveTop(s: P, e: P) {
        when (outfit) {
            Outfit.TEE, Outfit.JERSEY -> {
                val end = along(s, e, 0.5f)
                capsule(s, end, body.armR0 + 2f, body.armR0 + 1.6f).fill(look.main).stroke(ink).width(MID)
                celSeg(s, end, body.armR0 + 2f, body.armR0 + 1.6f, CK.shade(look.main))
                capsule(along(s, e, 0.4f), end, body.armR0 + 1.7f).fill(if (outfit == Outfit.JERSEY) CK.WHITE else look.accent).stroke(ink).width(FINE)
            }
            Outfit.ARMOR -> {
                // Omuzluk: üst üste iki plaka
                val d = e - s; val len = max(0.01f, hypot(d.x, d.y)); val u = P(d.x / len, d.y / len)
                val c1 = s + u * 2f
                fill(CK.STEEL, MID) { ellipse(c1.x, c1.y, body.armR0 + 4f, body.armR0 + 3f, angleOf(u) * -1f + 90f) }
                fill(CK.STEEL, MID) { ellipse(c1.x + u.x * 5f, c1.y + u.y * 5f, body.armR0 + 3f, body.armR0 + 1.6f, angleOf(u) * -1f + 90f) }
                spot(CK.WHITE) { circle(c1.x - 2f, c1.y - 2f, 1.4f) }.alpha(0.8f)
            }
            Outfit.MECHA -> {
                val d = e - s; val len = max(0.01f, hypot(d.x, d.y)); val u = P(d.x / len, d.y / len)
                val c1 = s + u * 1.5f
                fill("#D5DCE4", OUT) { roundRect(c1.x - body.armR0 - 3.5f, c1.y - body.armR0 - 3f, (body.armR0 + 3.5f) * 2f, (body.armR0 + 3f) * 2f, 4.5f) }
                spot(CK.shade("#D5DCE4")) { roundRect(c1.x + 1f, c1.y - body.armR0 - 2f, body.armR0 + 1.5f, (body.armR0 + 2f) * 2f, 3f) }
                fill("#F28A2E", FINE) { roundRect(c1.x - body.armR0 - 2f, c1.y - 1.5f, (body.armR0 + 2f) * 2f, 3f, 1f) }
            }
            Outfit.HEROSUIT -> {
                if (body.muscle > 0.5f) {
                    val m = along(s, e, 0.45f)
                    taper(along(s, e, 0.15f), m + rotV((e - s) * (1f / max(0.01f, dist(s, e))), 90f) * (body.armR0 * 0.4f), along(s, e, 0.75f), w0 = 0.2f, w1 = 0.8f, w2 = 0.2f, color = CK.deep(look.main), alpha = 0.6f)
                }
            }
            Outfit.SPACESUIT -> capsule(along(s, e, 0.55f), along(s, e, 0.75f), body.armR0 + 2.3f).fill("#F28A2E").stroke(ink).width(FINE)
            else -> Unit
        }
    }

    private fun arm(left: Boolean, handVisible: Boolean = true) {
        val s = if (left) sk.shoulderL else sk.shoulderR
        val e = if (left) sk.elbowL else sk.elbowR
        val h = if (left) sk.handL else sk.handR
        val holds = prop != null && pose.propRight != left
        // Eşya tutan el yumruk olur (sapı kavrar)
        val shape = if (holds) Hand.FIST else if (left) pose.handL else pose.handR
        if (holds) propBack(prop!!, h, e)
        val bulk = armBulk()
        val c = sleeveColor()
        limb(s, e, h, body.armR0 + bulk, body.armR1 + bulk, body.armR2 + bulk * 0.8f, c)
        sleeveTop(s, e)
        // Kol ucu: manşet, eldiven, yen
        when (outfit) {
            Outfit.SUIT, Outfit.CARDIGAN, Outfit.LABCOAT, Outfit.UNIFORM, Outfit.TRENCH, Outfit.CHEF, Outfit.SAILOR, Outfit.BLOUSE -> {
                val cuff = when (outfit) {
                    Outfit.SUIT -> CK.WHITE
                    Outfit.SAILOR -> look.second
                    Outfit.BLOUSE, Outfit.CHEF -> CK.shade(c)
                    else -> CK.shade(c)
                }
                val r = body.armR2 + bulk * 0.8f + 0.5f
                capsule(along(e, h, 0.82f), along(e, h, 0.97f), r).fill(cuff).stroke(ink).width(MID)
                if (outfit == Outfit.SAILOR) capsule(along(e, h, 0.86f), along(e, h, 0.88f), r + 0.1f).fill(CK.WHITE).noStroke()
            }
            Outfit.HEROSUIT -> {
                // Eldiven: ön kolun yarısından, ağzı genişleyen
                val g = look.trim
                val st = along(e, h, 0.45f)
                capsule(st, h, body.armR1 * 1.02f, body.armR2 + 0.4f).fill(g).stroke(ink).width(MID)
                celSeg(st, h, body.armR1 * 1.02f, body.armR2 + 0.4f, CK.shade(g))
                val d = h - e; val len = max(0.01f, hypot(d.x, d.y)); val n = P(-d.y / len, d.x / len)
                val r = body.armR1 + 2.2f
                fill(g, MID) { poly(st.x + n.x * r, st.y + n.y * r, st.x - n.x * r, st.y - n.y * r, along(e, h, 0.62f).x, along(e, h, 0.62f).y, closed = true) }
            }
            Outfit.ARMOR -> {
                val st = along(e, h, 0.25f)
                capsule(st, along(e, h, 0.95f), body.armR1 + 1.6f, body.armR2 + 1.6f).fill(CK.STEEL).stroke(ink).width(MID)
                celSeg(st, along(e, h, 0.95f), body.armR1 + 1.6f, body.armR2 + 1.6f, CK.shade(CK.STEEL))
                fill(CK.light(CK.STEEL), MID) { circle(e.x, e.y, body.armR1 + 1.2f) }
            }
            Outfit.MECHA -> {
                capsule(along(e, h, 0.15f), along(e, h, 0.9f), body.armR1 + 1.6f, body.armR2 + 1.2f).fill("#D5DCE4").stroke(ink).width(OUT)
                celSeg(along(e, h, 0.15f), along(e, h, 0.9f), body.armR1 + 1.6f, body.armR2 + 1.2f, CK.shade("#D5DCE4"))
                fill("#F28A2E") { circle(e.x, e.y, body.armR1 * 0.75f) }
            }
            Outfit.SPACESUIT -> capsule(along(e, h, 0.85f), along(e, h, 1f), body.armR2 + 2.6f).fill("#9AA6B4").stroke(ink).width(MID)
            Outfit.ROBE -> {
                // Geniş çan yen
                val st = along(e, h, 0.2f); val tip = along(e, h, 0.95f)
                val d = h - e; val len = max(0.01f, hypot(d.x, d.y)); val n = P(-d.y / len, d.x / len)
                val w0 = body.armR1 + 1.5f; val w1 = body.armR2 + 7f
                fill(look.main, MID) { poly(st.x + n.x * w0, st.y + n.y * w0, tip.x + n.x * w1, tip.y + n.y * w1, tip.x - n.x * w1, tip.y - n.y * w1, st.x - n.x * w0, st.y - n.y * w0, closed = true) }
                line(look.trim, 1.6f) { line(tip.x + n.x * (w1 - 0.8f), tip.y + n.y * (w1 - 0.8f), tip.x - n.x * (w1 - 0.8f), tip.y - n.y * (w1 - 0.8f)) }
            }
            Outfit.JERSEY, Outfit.TEE -> if (Extra.WRISTBANDS in look.extras) capsule(along(e, h, 0.78f), along(e, h, 0.95f), body.armR2 + 1.1f).fill(look.accent).stroke(ink).width(MID)
            else -> Unit
        }
        if (handVisible) hand(h, e, left, shape)
        if (holds) propFront(prop!!, h, e, left)
    }

    // ------------------------------------------------------------------ eller

    private fun handColor(): String = when (outfit) {
        Outfit.MECHA -> "#D5DCE4"
        Outfit.ARMOR -> CK.STEEL
        Outfit.SPACESUIT -> "#9AA6B4"
        Outfit.HEROSUIT -> look.trim
        else -> look.skin
    }

    /** El: bilekte yerel çerçeve (y ön kol yönünde), ölçek el yarıçapı. */
    fun hand(h: P, e: P, left: Boolean, shape: Hand) {
        if (shape == Hand.HIDDEN) return
        val a = angleOf(h - e)
        val c = handColor()
        val sh = if (c == look.skin) CK.skinShade(c) else CK.shade(c)
        // Başparmak gövdeye bakan yanda
        val th = if (left) 1f else -1f
        val s = body.handR * (if (shape == Hand.FIST) 1.08f else 1f)
        frame(h, -a, s) {
            val parts = ArrayList<VectorBuilder.() -> VectorBuilder.Part>()
            when (shape) {
                Hand.FIST -> {
                    parts += { roundRect(-0.95f, 0.05f, 1.9f, 1.75f, 0.62f) }
                    parts += { capsule2(th * 0.78f, 0.45f, th * 0.25f, 1.15f, 0.36f) }
                }
                Hand.POINT -> {
                    parts += { roundRect(-0.9f, 0.05f, 1.8f, 1.55f, 0.58f) }
                    parts += { capsule2(-th * 0.45f, 1.2f, -th * 0.45f, 3.0f, 0.27f) }
                    parts += { capsule2(th * 0.8f, 0.45f, th * 0.3f, 1.05f, 0.33f) }
                }
                Hand.VSIGN -> {
                    parts += { roundRect(-0.9f, 0.05f, 1.8f, 1.55f, 0.58f) }
                    parts += { capsule2(-th * 0.5f, 1.2f, -th * 0.95f, 2.9f, 0.27f) }
                    parts += { capsule2(-th * 0.05f, 1.2f, th * 0.25f, 2.95f, 0.27f) }
                    parts += { capsule2(th * 0.8f, 0.45f, th * 0.3f, 1.05f, 0.33f) }
                }
                Hand.OPEN, Hand.CLAW -> {
                    val claw = shape == Hand.CLAW
                    parts += { roundRect(-0.85f, 0.05f, 1.7f, 1.35f, 0.5f) }
                    for (i in 0..3) {
                        val x0 = -0.6f + i * 0.4f
                        val x1 = x0 * 1.45f
                        val len = if (i == 0 || i == 3) 2.05f else 2.35f
                        if (claw) parts += { capsule2(x0, 1.1f, x1, len - 0.35f, 0.22f) }
                        else parts += { capsule2(x0, 1.1f, x1, len, 0.22f) }
                    }
                    parts += { capsule2(th * 0.7f, 0.45f, th * 1.45f, 1.15f, 0.27f) }
                }
                Hand.FLAT -> {
                    parts += { roundRect(-0.82f, 0.05f, 1.64f, 2.35f, 0.62f) }
                    parts += { capsule2(th * 0.75f, 0.4f, th * 1.1f, 1.2f, 0.27f) }
                }
                Hand.RELAXED -> {
                    parts += { path(closed = true) { moveTo(-0.85f, 0.1f); lineTo(0.85f, 0.1f); cubicTo(0.95f, 0.9f, 0.75f, 1.85f, 0.1f, 1.95f); cubicTo(-0.55f, 2.0f, -0.95f, 1.2f, -0.85f, 0.1f) } }
                    parts += { capsule2(th * 0.82f, 0.4f, th * 0.62f, 1.2f, 0.3f) }
                }
                Hand.HIDDEN -> Unit
            }
            union(c, OUT, parts)
            // Parmak ayrımları ve gölge
            when (shape) {
                Hand.FIST -> {
                    for (i in -1..1) line(sh, FINE) { line(i * 0.48f, 1.25f, i * 0.48f, 1.75f) }
                    spot(sh) { roundRect(-0.85f, 1.45f, 1.7f, 0.3f, 0.15f) }.alpha(0.8f)
                    if (robot) line(CK.STEEL_D, FINE) { line(-0.9f, 0.9f, 0.9f, 0.9f) }
                }
                Hand.POINT, Hand.VSIGN -> line(sh, FINE) { line(-0.7f, 1.3f, 0.6f, 1.3f) }
                Hand.FLAT -> for (i in -1..1) line(sh, FINE) { line(i * 0.4f, 1.5f, i * 0.4f, 2.3f) }
                Hand.RELAXED -> line(sh, FINE) { line(-th * 0.15f, 1.2f, -th * 0.2f, 1.85f) }
                else -> Unit
            }
        }
    }

    /** Yerel çerçevede iki noktalı kapsül (el parmakları). */
    private fun VectorBuilder.capsule2(x0: Float, y0: Float, x1: Float, y1: Float, r: Float): VectorBuilder.Part = capsuleIn(this, P(x0, y0), P(x1, y1), r)

    private fun capsuleIn(vb: VectorBuilder, a: P, c: P, r: Float): VectorBuilder.Part {
        val dx = c.x - a.x; val dy = c.y - a.y
        val len = max(0.001f, hypot(dx, dy))
        val ux = dx / len; val uy = dy / len
        val ang = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        return vb.path(closed = true) {
            moveTo(a.x - uy * r, a.y + ux * r)
            lineTo(c.x - uy * r, c.y + ux * r)
            arcTo(c.x, c.y, r, r, ang + 90f, -180f)
            lineTo(a.x + uy * r, a.y - ux * r)
            arcTo(a.x, a.y, r, r, ang - 90f, -180f)
        }
    }

    // ------------------------------------------------------------------ eşyalar

    /** Havaya kaldırılan uzun eşya dışa yatar (çerçeveden taşmaz, zafer pozunda daha dinamik). */
    private fun outward(a: Float): Float = if (abs(a) > 120f) a - sign(a) * 28f else a

    /** Eşyanın eldeki yönü: asılı eşyalar dik, ötekiler ön kol yönünde. */
    private fun gripAngle(p: Prop, h: P, e: P): Float = when (p) {
        Prop.BRIEFCASE, Prop.BAG, Prop.BOOK, Prop.CLIPBOARD -> 0f
        Prop.CANE -> if (dist(h, e) > 0f && (h.y - e.y) > -8f) 0f else angleOf(h - e)
        // Asa: aşağıdaysa dik, kalkınca tepesindeki taş ön kol yönünde (elin ötesinde) kalır
        Prop.STAFF -> if (dist(h, e) > 0f && (h.y - e.y) > -8f) 0f else outward(angleOf(h - e)) + 180f
        Prop.SWORD -> outward(angleOf(h - e))
        else -> angleOf(h - e)
    }

    /** Elin arkasında kalan kısım (sap, asılı çanta). */
    private fun propBack(p: Prop, h: P, e: P) {
        val a = gripAngle(p, h, e)
        frame(h, -a) {
            when (p) {
                Prop.SWORD -> {
                    // Kılıç: kabza elin içinde, namlu ileri
                    fill("#5A3A22", MID) { roundRect(-1.4f, -6f, 2.8f, 9f, 1f) }
                    fill(CK.GOLD, MID) { roundRect(-7f, 3.2f, 14f, 3f, 1.2f) }
                    fill(CK.GOLD, FINE) { circle(0f, -6.5f, 2f) }
                    fill("#E4EAF0", OUT) { poly(-2.6f, 6f, 2.6f, 6f, 2.6f, 44f, 0f, 50f, -2.6f, 44f, closed = true) }
                    spot("#AEB8C4") { poly(0.3f, 7f, 2.4f, 7f, 2.4f, 44f, 0.3f, 48f, closed = true) }
                    line("#8994A2", FINE) { line(0f, 8f, 0f, 44f) }
                    if (pose.prop == PropSlot.ACTION) spot(CK.WHITE) { star(-1.5f, 30f, 4f, 0.9f, 4) }
                }
                Prop.STAFF -> {
                    val up = -36f; val down = 44f
                    fill("#7A4E2A", OUT) { roundRect(-1.8f, up, 3.6f, down - up, 1.8f) }
                    spot("#5A3A22") { rect(0.4f, up + 2f, 1.2f, down - up - 4f) }
                    fill("#7A4E2A", MID) { path(closed = true) { moveTo(-1.8f, up + 2f); cubicTo(-7f, up - 2f, -6f, up - 10f, -1f, up - 12f); cubicTo(-3f, up - 8f, -2f, up - 4f, 0f, up - 1f) } }
                    fill("#7A4E2A", MID) { path(closed = true) { moveTo(1.8f, up + 2f); cubicTo(7f, up - 2f, 6f, up - 10f, 1f, up - 12f); cubicTo(3f, up - 8f, 2f, up - 4f, 0f, up - 1f) } }
                    spot(look.aura) { circle(0f, up - 6f, 7.5f) }.alpha(0.35f)
                    fill(look.aura, MID) { circle(0f, up - 6f, 4.6f) }
                    spot(CK.WHITE) { circle(-1.4f, up - 7.6f, 1.5f) }
                }
                Prop.CANE -> {
                    // Asılıyken yere değer; havaya kalkınca bacak boyunda kalır
                    val len = if (a == 0f) (sk.ground - h.y).coerceIn(30f, body.thigh + body.shin) else (body.thigh + body.shin) * 0.55f
                    fill("#8A5A32", OUT) { roundRect(-1.4f, -2f, 2.8f, len + 2f, 1.4f) }
                    fill("#8A5A32", OUT) { path(closed = true) { moveTo(-1.4f, 0f); cubicTo(-1.4f, -9f, 9f, -9f, 9f, -2f); lineTo(6.4f, -2f); cubicTo(6.4f, -6f, 1.4f, -6f, 1.4f, 0f) } }
                }
                Prop.BRIEFCASE -> {
                    fill("#2E2A2A", MID) { path { moveTo(-4f, 2f); quadTo(-4f, -1.5f, 0f, -1.5f); quadTo(4f, -1.5f, 4f, 2f) } }
                    fill("#6B4428", OUT) { roundRect(-12f, 2f, 24f, 17f, 2.5f) }
                    spot(CK.shade("#6B4428")) { rect(1f, 2.6f, 10.5f, 16f) }
                    line("#3E2716", FINE) { line(-12f, 7f, 12f, 7f) }
                    fill(CK.GOLD, FINE) { rect(-2f, 5.5f, 4f, 3f) }
                }
                Prop.BAG -> {
                    fill("#3A2C25", MID) { path { moveTo(-5f, 3f); quadTo(-5f, -2f, 0f, -2f); quadTo(5f, -2f, 5f, 3f) } }
                    fill("#5A3D2B", OUT) { roundRect(-11f, 3f, 22f, 15f, 3.5f) }
                    fill(CK.shade("#5A3D2B"), MID) { path(closed = true) { moveTo(-11f, 4f); lineTo(11f, 4f); lineTo(11f, 9f); quadTo(0f, 12f, -11f, 9f) } }
                    fill(CK.GOLD, FINE) { roundRect(-1.5f, 8.5f, 3f, 3f, 0.8f) }
                }
                Prop.BOOK -> {
                    fill("#2F6DB5", OUT) { roundRect(-8f, -2f, 16f, 20f, 1.5f) }
                    fill("#F4EFDF", FINE) { rect(-6.5f, 16f, 14f, 2f) }
                    fill(CK.GOLD, FINE) { roundRect(-4.5f, 3f, 9f, 3f, 0.8f) }
                }
                Prop.CLIPBOARD -> {
                    fill("#A87A4E", OUT) { roundRect(-8f, -1f, 16f, 21f, 1.5f) }
                    fill(CK.WHITE, FINE) { rect(-6f, 2f, 12f, 16f) }
                    for (i in 0..3) line("#9AA6B4", 0.7f) { line(-4.5f, 5f + i * 3.4f, 4.5f, 5f + i * 3.4f) }
                    fill(CK.STEEL_D, FINE) { roundRect(-3.5f, -2.5f, 7f, 3.5f, 1f) }
                }
                Prop.POINTER -> {
                    fill("#8A5A32", MID) { poly(-0.9f, -3f, 0.9f, -3f, 0.5f, 34f, -0.5f, 34f, closed = true) }
                    fill(CK.RED, FINE) { circle(0f, 34.5f, 1.2f) }
                }
                Prop.BATON -> {
                    fill("#26262E", OUT) { roundRect(-1.8f, -5f, 3.6f, 30f, 1.8f) }
                    fill("#3A3A44", FINE) { roundRect(-2.3f, -1f, 4.6f, 3f, 1f) }
                }
                Prop.MAGNIFIER -> {
                    fill("#5A3A22", OUT) { roundRect(-1.6f, -3f, 3.2f, 13f, 1.6f) }
                    fill(CK.GOLD_D, OUT) { circle(0f, 17f, 8f) }
                    fill("#CFEFFF", MID) { circle(0f, 17f, 6f) }.alpha(0.9f)
                    line(CK.WHITE, 1.2f) { arc(0f, 17f, 4f, 4f, 200f, 70f) }
                }
                Prop.LADLE -> {
                    fill("#B8C2CE", MID) { roundRect(-1.1f, -4f, 2.2f, 24f, 1.1f) }
                    fill("#B8C2CE", OUT) { path(closed = true) { moveTo(-6f, 19f); lineTo(6f, 19f); arcTo(0f, 19f, 6f, 6f, 0f, 180f) } }
                    spot(CK.shade("#B8C2CE")) { path(closed = true) { moveTo(1f, 20f); lineTo(5.6f, 20f); arcTo(0f, 19f, 5.6f, 5.6f, 10f, 70f) } }
                }
                Prop.PAN -> {
                    fill("#26262E", MID) { roundRect(-1.6f, -4f, 3.2f, 16f, 1.4f) }
                    fill("#3A3A44", OUT) { circle(0f, 22f, 11f) }
                    spot("#55555F") { circle(-1f, 21f, 8f) }
                    spot(CK.WHITE) { ellipse(-4f, 17.5f, 3f, 1.4f, -30f) }.alpha(0.7f)
                }
                Prop.BALL -> {
                    fill("#F28A2E", OUT) { circle(0f, 9f, 9f) }
                    line(CK.deep("#F28A2E"), FINE) { line(-9f, 9f, 9f, 9f) }
                    line(CK.deep("#F28A2E"), FINE) { arc(-9f, 9f, 5f, 8f, -70f, 140f) }
                    line(CK.deep("#F28A2E"), FINE) { arc(9f, 9f, 5f, 8f, 110f, 140f) }
                    spot(CK.WHITE) { ellipse(-3.5f, 4f, 2.4f, 1.3f, -30f) }.alpha(0.7f)
                }
            }
        }
    }

    /** Elin önünde kalan kısım (kılıcın parıltısı, büyü ışığı). */
    private fun propFront(p: Prop, h: P, e: P, left: Boolean) {
        if (p == Prop.STAFF && pose.prop == PropSlot.ACTION) {
            val a = gripAngle(p, h, e)
            val tip = h + rotV(P(0f, -42f), -a)
            for ((dx, dy, r) in listOf(Triple(-9f, -6f, 3.2f), Triple(8f, -10f, 2.6f), Triple(4f, 7f, 2.2f))) spot(CK.WHITE) { star(tip.x + dx, tip.y + dy, r, r * 0.25f, 4) }
        }
    }

    // ------------------------------------------------------------------ efektler

    /** Vücudun arkasında kalan efektler (aura, hız çizgileri, savrulma izi). */
    private fun backFx() {
        val fx = pose.fx
        if (PoseFx.AURA in fx) aura()
        if (PoseFx.SPEED in fx) {
            val left = min(min(sk.shoulderL.x, sk.hipL.x), min(sk.handL.x, sk.ankleL.x)) - 6f
            val rows = listOf(0.15f to 1f, 0.3f to 0.65f, 0.45f to 0.9f, 0.6f to 0.55f, 0.75f to 0.8f)
            val top = sk.head.y; val bottom = sk.ground - 10f
            for ((t, f) in rows) {
                val y = top + (bottom - top) * t
                taper(P(left - 44f * f, y), P(left - 4f, y), w0 = 0.2f, w1 = 1.4f, w2 = 2f, color = ink, alpha = 0.85f)
            }
        }
        if (PoseFx.SWISH_KICK in fx) {
            val c = sk.hipR; val r = dist(c, sk.ankleR)
            val a0 = angleOf(sk.ankleR - c)
            for (i in 0..2) {
                val rr = r + 6f - i * 6f
                val pts = (0..8).map { j -> c + dirOf(a0 - 70f + j * 8f) * rr }
                taper(pts, 0.1f, 1.6f - i * 0.3f, 0.4f, ink, 0.75f)
            }
        }
        if (PoseFx.SWISH_ARM in fx) {
            val holdR = pose.propRight
            val s = if (holdR) sk.shoulderR else sk.shoulderL
            val h = if (holdR) sk.handR else sk.handL
            val r = dist(s, h) + if (prop != null) 34f else 6f
            val a0 = angleOf(h - s)
            val sgn = if (holdR) 1f else -1f
            for (i in 0..2) {
                val rr = r - i * 6f
                val pts = (0..9).map { j -> s + dirOf(a0 + sgn * (25f - j * 9f)) * rr }
                taper(pts, 0.1f, 1.8f - i * 0.4f, 0.3f, ink, 0.7f)
            }
        }
        if (PoseFx.WIND in fx) {
            val y0 = max(sk.ankleL.y, sk.ankleR.y) + 10f
            for ((dx, len) in listOf(-14f to 26f, -4f to 40f, 6f to 32f, 16f to 22f)) {
                val x = (sk.ankleL.x + sk.ankleR.x) / 2f + dx
                taper(P(x, y0), P(x, y0 + len), w0 = 2f, w1 = 1.2f, w2 = 0.2f, color = ink, alpha = 0.8f)
            }
            for (sx in listOf(-1f, 1f)) {
                val x = sk.pelvis.x + sx * (body.shoulderOut + 16f)
                taper(P(x, sk.chest.y), P(x + sx * 2f, sk.chest.y + 40f), w0 = 1.6f, w1 = 1f, w2 = 0.2f, color = ink, alpha = 0.6f)
            }
        }
        if (PoseFx.FALL_LINES in fx) {
            for (i in 0..3) {
                val x = sk.head.x + 18f + i * 9f; val y = sk.head.y - 26f + i * 6f
                taper(P(x, y), P(x + 6f, y - 22f), w0 = 1.8f, w1 = 1f, w2 = 0.2f, color = ink, alpha = 0.75f)
            }
        }
    }

    /** Güç toplarken alev gibi aura: vücudun çevresinde yukarı uzanan diller. */
    private fun aura() {
        val c = look.aura
        val cx = sk.pelvis.x
        val top = sk.head.y - R * 2.6f
        val bottom = sk.ground + 1f
        val half = max(abs(sk.ankleR.x - sk.ankleL.x) / 2f + 18f, body.shoulderOut + 26f)
        fun flame(scale: Float): List<P> {
            val pts = ArrayList<P>()
            val n = 13
            for (i in 0..n) {
                val t = i / n.toFloat()
                // Sol taban → tepe → sağ taban: diller yukarı sivrilir
                val side = -1f + 2f * t
                val yBase = bottom - (bottom - top) * (1f - side * side * 0.75f) * scale
                val xBase = cx + side * half * scale
                val tongue = if (i % 2 == 1) 1f else 0f
                pts += P(xBase + side * 4f * tongue, yBase - tongue * 16f * scale)
            }
            return pts
        }
        val outer = flame(1f)
        fill(CK.light(c), OUT) { smooth(*(outer.flatMap { listOf(it.x, it.y) } + listOf(cx + half * 0.7f, bottom, cx - half * 0.7f, bottom)).toFloatArray(), closed = true, tension = 0.35f) }.alpha(0.55f)
        val inner = flame(0.78f)
        spot(c) { smooth(*(inner.flatMap { listOf(it.x, it.y) } + listOf(cx + half * 0.5f, bottom, cx - half * 0.5f, bottom)).toFloatArray(), closed = true, tension = 0.35f) }.alpha(0.45f)
    }

    /** Vücudun önündeki efektler. */
    private fun frontFx() {
        val fx = pose.fx
        if (PoseFx.IMPACT_HAND in fx) impact(sk.handR + dirOf(angleOf(sk.handR - sk.elbowR)) * (body.handR * 1.8f), 15f)
        if (PoseFx.IMPACT_FOOT in fx) impact(sk.ankleR + dirOf(angleOf(sk.ankleR - sk.kneeR)) * 7f, 16f)
        if (PoseFx.SHOUT in fx) {
            val c = sk.head + rotV(P(0f, R * 0.5f), sk.headAngle)
            for (i in 0 until 9) {
                val a = 100f + i * 20f + if (i % 2 == 0) 0f else 6f
                val d = dirOf(a)
                val r0 = R * 1.75f; val r1 = R * (if (i % 2 == 0) 2.55f else 2.3f)
                taper(c + d * r0, c + d * r1, w0 = 0.4f, w1 = 2.2f, w2 = 0.4f, color = ink)
            }
            for (sx in listOf(-1f, 1f)) {
                val p0 = c + P(sx * R * 1.5f, R * 0.4f)
                taper(p0, p0 + P(sx * 9f, 3f), w0 = 0.4f, w1 = 1.8f, w2 = 0.3f, color = ink)
                taper(p0 + P(0f, 6f), p0 + P(sx * 8f, 11f), w0 = 0.4f, w1 = 1.6f, w2 = 0.3f, color = ink)
            }
        }
        if (PoseFx.SPARKLE in fx) {
            val h = if (sk.handR.y < sk.handL.y) sk.handR else sk.handL
            for ((dx, dy, r) in listOf(Triple(-13f, 0f, 5f), Triple(12f, -7f, 4f), Triple(14f, 10f, 3f))) {
                fill(CK.GOLD, FINE) { star(h.x + dx, h.y + dy, r, r * 0.3f, 4) }
            }
            fill(CK.GOLD, FINE) { star(sk.head.x - R * 1.6f, sk.head.y - R * 0.6f, 4f, 1.2f, 4) }
        }
        if (PoseFx.MAGIC in fx) {
            val h = if (pose.propRight) sk.handR else sk.handL
            for ((dx, dy, r) in listOf(Triple(-10f, -20f, 4f), Triple(12f, -26f, 3.4f), Triple(2f, -36f, 2.6f))) fill(look.aura, FINE) { star(h.x + dx, h.y + dy, r, r * 0.3f, 4) }
        }
        if (PoseFx.AURA in fx) {
            // Yükselen parçacıklar ve küçük şimşekler
            for ((dx, dy) in listOf(-30f to -10f, 28f to -30f, -22f to -60f, 34f to 20f, -36f to 40f)) {
                val p = sk.chest + P(dx * (body.shoulderOut / 22f), dy)
                fill(CK.light(look.aura), FINE) { poly(p.x, p.y - 3.4f, p.x + 1.6f, p.y, p.x, p.y + 3.4f, p.x - 1.6f, p.y, closed = true) }
            }
            for (sx in listOf(-1f, 1f)) {
                val p = sk.pelvis + P(sx * (body.hipW + 22f), -10f)
                fill(CK.WHITE, FINE) { poly(p.x, p.y - 12f, p.x + sx * 4f, p.y - 4f, p.x + sx * 1f, p.y - 3f, p.x + sx * 5f, p.y + 8f, p.x - sx * 1f, p.y - 1f, p.x + sx * 1.5f, p.y - 2f, closed = true) }
            }
        }
        if (PoseFx.DIZZY in fx) {
            val c = sk.head + rotV(P(0f, -R * 1.25f), sk.headAngle)
            line(ink, FINE) { ellipse(c.x, c.y, R * 1.1f, R * 0.32f) }.alpha(0.6f)
            for ((i, a) in listOf(0f, 120f, 240f).withIndex()) {
                val r = Math.toRadians((a + 30f).toDouble())
                fill(CK.GOLD, FINE) { star(c.x + cos(r).toFloat() * R * 1.1f, c.y + sin(r).toFloat() * R * 0.32f, 4f - i * 0.5f, 1.7f - i * 0.2f, 5) }
            }
        }
        if (PoseFx.PAIN in fx) {
            val p = sk.shoulderL + P(-8f, -6f)
            for (a in listOf(-40f, 0f, 40f)) { val d = dirOf(180f + a); taper(p + d * 3f, p + d * 9f, w0 = 0.4f, w1 = 1.6f, w2 = 0.3f, color = CK.ANGER) }
        }
        if (PoseFx.SWEAT in fx) {
            val c = sk.head + rotV(P(-R * 1.25f, -R * 0.2f), sk.headAngle)
            drop(c.x, c.y, 2.4f)
            drop(c.x - 6f, c.y + 8f, 1.7f)
        }
        if (PoseFx.LAUGH_MARKS in fx) {
            val c = sk.head
            for (sx in listOf(-1f, 1f)) for (i in 0..2) {
                val a = (if (sx > 0) 60f else -60f) + (i - 1) * 28f + 180f
                val d = dirOf(a); val p0 = c + d * (R * 1.55f)
                taper(p0, p0 + d * 7f, w0 = 0.3f, w1 = 1.6f, w2 = 0.3f, color = ink)
            }
        }
        if (PoseFx.THOUGHT in fx) {
            val c = sk.head + P(R * 1.1f, -R * 1.2f)
            fill(CK.WHITE, FINE) { circle(c.x, c.y, 2f) }
            fill(CK.WHITE, FINE) { circle(c.x + 5f, c.y - 7f, 3.2f) }
            fill(CK.WHITE, MID) { ellipse(c.x + 13f, c.y - 18f, 7f, 5f) }
            for (i in -1..1) spot(ink) { circle(c.x + 13f + i * 3.4f, c.y - 18f, 0.9f) }
        }
        if (PoseFx.WAVE_ARCS in fx) {
            val h = sk.handR
            for ((r, a0) in listOf(13f to -70f, 18f to -64f)) line(ink, 1.1f) { arc(h.x, h.y - 2f, r, r, a0, 40f) }
            line(ink, 1.1f) { arc(h.x, h.y - 2f, 13f, 13f, 205f, 40f) }
        }
        if (PoseFx.BLOCK_ARCS in fx) {
            val c = (sk.handL + sk.handR) * 0.5f
            for (i in 0..2) line(ink, 1.3f - i * 0.25f) { arc(c.x + 4f + i * 5f, c.y, 12f + i * 5f, 16f + i * 6f, -60f, 120f) }
            for ((dx, dy) in listOf(22f to -14f, 26f to 10f)) fill(CK.GOLD, FINE) { star(c.x + dx, c.y + dy, 3.2f, 1f, 4) }
        }
        if (PoseFx.POINT_LINES in fx) {
            val h = sk.handR + dirOf(angleOf(sk.handR - sk.elbowR)) * (body.handR * 3.2f)
            for (a in listOf(-35f, 0f, 35f)) { val d = dirOf(90f + a); taper(h + d * 3f, h + d * 8f, w0 = 0.3f, w1 = 1.3f, w2 = 0.3f, color = ink) }
        }
        if (PoseFx.SOB in fx || PoseFx.TREMBLE in fx) {
            for (sx in listOf(-1f, 1f)) {
                val p = (if (sx < 0) sk.shoulderL else sk.shoulderR) + P(sx * 8f, -2f)
                for (i in 0..1) line(ink, 1f) { path { moveTo(p.x + sx * i * 3.5f, p.y - 4f); quadTo(p.x + sx * (i * 3.5f + 2f), p.y, p.x + sx * i * 3.5f, p.y + 4f) } }
            }
        }
        if (PoseFx.BRAWL in fx) {
            val c = (sk.handL + sk.handR) * 0.5f + P(10f, -4f)
            for ((dx, dy, r) in listOf(Triple(8f, -18f, 4f), Triple(18f, 4f, 3.4f), Triple(4f, 16f, 3f))) fill(CK.GOLD, FINE) { star(c.x + dx, c.y + dy, r, r * 0.42f, 5) }
            for (a in listOf(-50f, -10f, 30f, 70f)) { val d = dirOf(90f + a); taper(c + d * 16f, c + d * 24f, w0 = 0.3f, w1 = 1.5f, w2 = 0.3f, color = ink) }
        }
    }

    /** Zemindeki efektler (toz, zıplama çizgileri) dönmeden çizilir. */
    private fun groundFx() {
        val fx = pose.fx
        if (PoseFx.DUST in fx) {
            val back = if (pose.turn >= 0f) min(sk.ankleL.x, sk.ankleR.x) - 8f else max(sk.ankleL.x, sk.ankleR.x) + 8f
            val dir = if (pose.turn >= 0f) -1f else 1f
            val y = 192f
            val puffs = listOf(floatArrayOf(back, y, 5f), floatArrayOf(back + dir * 7f, y - 3f, 6.5f), floatArrayOf(back + dir * 15f, y, 4.5f))
            for (p in puffs) b.circle(p[0], p[1], p[2]).fill("#EDE7DA").stroke(ink).width(FINE * 2f)
            for (p in puffs) b.circle(p[0], p[1], p[2]).fill("#EDE7DA").noStroke()
        }
        if (PoseFx.JUMP_LINES in fx) {
            val cx = (sk.ankleL.x + sk.ankleR.x) / 2f
            for ((dx, ang) in listOf(-16f to -20f, 0f to 0f, 16f to 20f)) {
                val p = P(cx + dx, 186f)
                taper(p, p + dirOf(ang) * 9f, w0 = 1.6f, w1 = 1f, w2 = 0.3f, color = ink)
            }
        }
        if (PoseFx.FALL_LINES in fx) {
            val cx = sk.pelvis.x
            for ((dx, r) in listOf(-18f to 4f, -8f to 5.5f, 4f to 4.5f)) {
                b.circle(cx + dx, 193f, r).fill("#EDE7DA").stroke(ink).width(FINE * 2f)
            }
            for ((dx, r) in listOf(-18f to 4f, -8f to 5.5f, 4f to 4.5f)) b.circle(cx + dx, 193f, r).fill("#EDE7DA").noStroke()
        }
    }

    /** Darbe yıldızı. */
    private fun impact(c: P, r: Float) {
        fun jag(ro: Float, ri: Float, n: Int, rot: Float): List<P> = (0 until n * 2).map { i ->
            val a = Math.toRadians((rot + 180.0 * i / n))
            val rr = if (i % 2 == 0) ro * (if (i % 4 == 0) 1f else 0.82f) else ri
            P(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())
        }
        fill(CK.GOLD, MID) { points(jag(r, r * 0.5f, 10, 8f), closed = true) }
        spot(CK.WHITE) { points(jag(r * 0.55f, r * 0.28f, 8, 20f), closed = true) }
    }

    /** Ter damlası (ucu yukarıda). */
    fun drop(cx: Float, cy: Float, s: Float) {
        fill(CK.TEAR, FINE) {
            path(closed = true) {
                moveTo(cx, cy - s * 2.1f)
                cubicTo(cx + s * 0.35f, cy - s * 1.2f, cx + s, cy - s * 0.6f, cx + s, cy)
                arcTo(cx, cy, s, s, 0f, 180f)
                cubicTo(cx - s, cy - s * 0.6f, cx - s * 0.35f, cy - s * 1.2f, cx, cy - s * 2.1f)
            }
        }
        spot(CK.WHITE) { ellipse(cx - s * 0.35f, cy - s * 0.2f, s * 0.25f, s * 0.4f) }
    }
}
