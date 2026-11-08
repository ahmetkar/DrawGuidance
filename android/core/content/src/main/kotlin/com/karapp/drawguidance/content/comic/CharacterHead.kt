package com.karapp.drawguidance.content.comic

import com.karapp.drawguidance.content.comic.Fig.Companion.FINE
import com.karapp.drawguidance.content.comic.Fig.Companion.MID
import com.karapp.drawguidance.content.comic.Fig.Companion.OUT
import com.karapp.drawguidance.content.vector.P
import com.karapp.drawguidance.content.vector.VectorBuilder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * v37 baş çizimi (anime / çizgi roman). Baş kendi çerçevesinde çizilir: merkez kafatası, birim baş yarıçapı (R=1),
 * y aşağı; çene ucu y≈1.1. Yüz parçaları baş dönünce ([PoseSpec.turn]) o yana kayar, uzaktaki göz daralır.
 * Kaşlar saçın üstünde çizilir (anime geleneği: ifade perçemin altında kalmaz).
 */

/** Saç tutamı: kök (rx, ry) → sivri uç (tx, ty), kök genişliği [w], [bend] eğrilik (boyun oranı). */
private class Lk(val rx: Float, val ry: Float, val tx: Float, val ty: Float, val w: Float, val bend: Float = 0f)

/**
 * Saç biçiminin tarifi: [back] başın arkasındaki tutamlar (sivri siluet), [capV] tepe kütlesinin hacmi (null: tepe yok),
 * [line] alın saç çizgisi, [bangs] perçem, [side] yüzü çerçeveleyen yan tutamlar, [shine] parlak halka.
 */
private class HairDef(
    val back: List<Lk> = emptyList(),
    val capV: Float? = 0.12f,
    val capLow: Float = 0.1f,
    val line: Float = -0.45f,
    val bangs: List<Lk> = emptyList(),
    val side: List<Lk> = emptyList(),
    val shine: Boolean = true,
)

/** Kökleri başın ortasına doğru, uçları dışa bakan sivri tutamlar (diken saç). */
private fun spikes(vararg tips: Float, w: Float, k: Float = 0.33f, dy: Float = -0.2f, bend: Float = 0.08f): List<Lk> =
    List(tips.size / 2) { i -> val tx = tips[i * 2]; val ty = tips[i * 2 + 1]; Lk(tx * k, ty * k + dy, tx, ty, w, if (i % 2 == 0) bend else -bend) }

/** Perçem: uçlar verilir, kökler saç çizgisinin üstünde. */
private fun bangs(vararg tips: Float, rootY: Float = -0.66f, w: Float = 0.42f, bend: Float = 0.1f, k: Float = 0.75f): List<Lk> =
    List(tips.size / 2) { i -> val tx = tips[i * 2]; val ty = tips[i * 2 + 1]; Lk(tx * k, rootY, tx, ty, w, if (tx < 0f) -bend else bend) }

private val hairDefs: Map<Hair, HairDef> = mapOf(
    Hair.SPIKY_KID to HairDef(
        back = spikes(-1.55f, -0.2f, -1.5f, -0.78f, -1.12f, -1.32f, -0.45f, -1.72f, 0.3f, -1.75f, 0.98f, -1.45f, 1.42f, -0.95f, 1.6f, -0.3f, w = 0.82f),
        capV = 0.14f,
        bangs = bangs(-0.92f, 0.04f, -0.62f, -0.14f, -0.3f, 0f, 0.05f, -0.17f, 0.38f, -0.02f, 0.7f, -0.14f, 0.96f, 0.1f),
    ),
    Hair.PONYTAIL to HairDef(
        capV = 0.1f,
        bangs = bangs(-0.8f, -0.02f, -0.5f, 0.06f, -0.2f, 0f, 0.12f, 0.07f, 0.42f, 0f, 0.72f, -0.06f, w = 0.38f, bend = 0.06f, k = 0.8f),
        side = listOf(Lk(-0.9f, -0.45f, -0.95f, 0.95f, 0.3f, -0.06f), Lk(0.9f, -0.45f, 0.94f, 0.92f, 0.3f, 0.06f)),
    ),
    Hair.PARTED to HairDef(
        back = spikes(-1.14f, -0.5f, -0.92f, -1.08f, -0.38f, -1.32f, 0.28f, -1.34f, 0.86f, -1.14f, 1.16f, -0.55f, w = 0.62f, k = 0.45f, dy = -0.12f, bend = 0.05f),
        capV = 0.08f,
        bangs = listOf(
            Lk(0.3f, -0.8f, -0.98f, -0.1f, 0.55f, 0.12f), Lk(0.35f, -0.75f, -0.6f, -0.3f, 0.5f, 0.1f), Lk(0.4f, -0.7f, -0.25f, -0.33f, 0.45f, 0.08f),
            Lk(0.45f, -0.75f, 0.98f, -0.05f, 0.45f, -0.1f), Lk(0.5f, -0.7f, 0.72f, -0.3f, 0.35f, -0.05f),
        ),
        capLow = -0.12f,
        side = listOf(Lk(-1f, -0.35f, -0.96f, 0.08f, 0.16f), Lk(1f, -0.35f, 0.96f, 0.08f, 0.16f)),
    ),
    Hair.TUFTS to HairDef(capV = null, shine = false),
    Hair.BUN to HairDef(
        capV = 0.1f,
        bangs = listOf(
            Lk(-0.05f, -0.85f, -0.95f, -0.12f, 0.5f, -0.12f), Lk(-0.1f, -0.8f, -0.55f, -0.3f, 0.4f, -0.08f),
            Lk(0.05f, -0.85f, 0.95f, -0.12f, 0.5f, 0.12f), Lk(0.1f, -0.8f, 0.55f, -0.3f, 0.4f, 0.08f),
        ),
        side = listOf(Lk(-0.92f, -0.4f, -0.86f, 0.98f, 0.22f, -0.08f), Lk(0.92f, -0.4f, 0.86f, 0.98f, 0.22f, 0.08f)),
    ),
    Hair.WAVY to HairDef(
        capV = 0.18f,
        bangs = listOf(
            Lk(-0.45f, -0.85f, 0.95f, 0.05f, 0.62f, -0.15f), Lk(-0.4f, -0.8f, 0.5f, -0.16f, 0.5f, -0.1f),
            Lk(-0.5f, -0.8f, 0.1f, -0.28f, 0.4f, -0.05f), Lk(-0.5f, -0.8f, -0.95f, -0.05f, 0.45f, 0.1f),
        ),
        side = listOf(Lk(-0.95f, -0.4f, -1.12f, 1.25f, 0.38f, -0.12f), Lk(0.95f, -0.4f, 1.12f, 1.25f, 0.38f, 0.12f)),
    ),
    Hair.UNDER_CAP to HairDef(
        capV = 0.05f, capLow = -0.1f, shine = false,
        bangs = listOf(Lk(-0.45f, -0.55f, -0.42f, -0.15f, 0.3f), Lk(-0.05f, -0.6f, 0.02f, -0.12f, 0.32f), Lk(0.38f, -0.55f, 0.45f, -0.16f, 0.3f)),
        side = listOf(Lk(-1f, -0.45f, -1f, 0.3f, 0.25f), Lk(1f, -0.45f, 1f, 0.3f, 0.25f)),
    ),
    Hair.FORELOCK to HairDef(
        capV = 0.06f, capLow = -0.1f, shine = false,
        bangs = listOf(Lk(0f, -0.65f, 0.55f, 0.08f, 0.42f, 0.12f), Lk(0.25f, -0.65f, 0.85f, -0.02f, 0.36f, 0.1f), Lk(-0.3f, -0.6f, -0.6f, -0.2f, 0.3f, -0.05f)),
        side = listOf(Lk(-1f, -0.45f, -0.97f, 0.1f, 0.2f), Lk(1f, -0.45f, 0.98f, 0.1f, 0.2f)),
    ),
    Hair.CROPPED to HairDef(
        capV = 0.04f, capLow = -0.15f, shine = false,
        bangs = listOf(Lk(-0.3f, -0.6f, -0.3f, -0.32f, 0.3f), Lk(0.05f, -0.62f, 0.1f, -0.34f, 0.3f), Lk(0.4f, -0.6f, 0.42f, -0.33f, 0.28f)),
        side = listOf(Lk(-1f, -0.5f, -0.99f, 0.15f, 0.22f), Lk(1f, -0.5f, 0.99f, 0.15f, 0.22f)),
    ),
    Hair.SPIKY_UP to HairDef(
        back = spikes(-1.35f, -0.6f, -1.05f, -1.3f, -0.5f, -1.72f, 0.12f, -1.85f, 0.72f, -1.62f, 1.18f, -1.22f, 1.38f, -0.6f, w = 0.72f, k = 0.3f, dy = -0.25f),
        capV = 0.1f,
        bangs = listOf(
            Lk(-0.72f, -0.6f, -0.82f, -0.2f, 0.35f, -0.06f), Lk(-0.35f, -0.6f, -0.42f, -0.1f, 0.38f), Lk(0.02f, -0.62f, 0.05f, -0.18f, 0.38f),
            Lk(0.4f, -0.6f, 0.48f, -0.12f, 0.36f), Lk(0.75f, -0.6f, 0.86f, -0.24f, 0.32f, 0.06f),
        ),
    ),
    Hair.SWEPT_BACK to HairDef(
        back = spikes(-1.42f, -0.45f, -1.32f, -1.05f, -0.9f, -1.48f, -0.25f, -1.68f, 0.45f, -1.62f, 1.05f, -1.3f, 1.42f, -0.75f, 1.48f, -0.2f, w = 0.8f, k = 0.35f, bend = -0.1f),
        capV = 0.14f, line = -0.62f,
        bangs = listOf(Lk(-0.05f, -0.85f, -0.32f, -0.16f, 0.3f, -0.15f), Lk(0.2f, -0.9f, 0.78f, -0.42f, 0.36f, 0.05f), Lk(-0.3f, -0.9f, -0.86f, -0.38f, 0.36f, -0.05f)),
    ),
    Hair.BOB to HairDef(
        capV = 0.16f,
        bangs = List(7) { i -> val x = -0.8f + i * 0.267f; Lk(x * 0.9f, -0.7f, x, -0.1f + if (i % 2 == 0) 0.03f else -0.02f, 0.36f) },
        side = listOf(Lk(-0.98f, -0.45f, -1f, 0.95f, 0.42f, -0.04f), Lk(0.98f, -0.45f, 1f, 0.95f, 0.42f, 0.04f)),
    ),
    Hair.KNIGHT to HairDef(
        back = spikes(-1.38f, 0.4f, -1.45f, -0.3f, -1.2f, -0.98f, -0.62f, -1.45f, 0.1f, -1.58f, 0.78f, -1.4f, 1.25f, -0.95f, 1.45f, -0.25f, 1.35f, 0.45f, w = 0.75f, k = 0.3f, dy = -0.15f, bend = 0.06f),
        capV = 0.13f,
        bangs = bangs(-0.92f, 0.02f, -0.62f, -0.13f, -0.28f, 0.04f, 0.08f, -0.12f, 0.42f, 0.02f, 0.74f, -0.12f, 0.96f, 0.12f, k = 0.7f),
        side = listOf(Lk(-0.96f, -0.35f, -0.98f, 0.7f, 0.3f, -0.08f), Lk(0.96f, -0.35f, 0.98f, 0.7f, 0.3f, 0.08f)),
    ),
    Hair.LONG to HairDef(
        capV = 0.08f, capLow = 0.2f, shine = false,
        side = listOf(Lk(-0.98f, -0.45f, -1.15f, 1.3f, 0.36f, -0.1f), Lk(0.98f, -0.45f, 1.15f, 1.3f, 0.36f, 0.1f)),
    ),
    Hair.NONE to HairDef(capV = null, shine = false),
)

/** Tutamın kapalı biçimi (yerel çerçevede). */
private fun lockShape(l: Lk, dx: Float = 0f, dy: Float = 0f): VectorBuilder.() -> VectorBuilder.Part = {
    val rx = l.rx + dx; val ry = l.ry + dy; val tx = l.tx + dx; val ty = l.ty + dy
    val ddx = tx - rx; val ddy = ty - ry
    val len = max(1e-3f, hypot(ddx, ddy))
    val nx = -ddy / len; val ny = ddx / len
    val mx = rx + ddx * 0.5f + nx * l.bend * len; val my = ry + ddy * 0.5f + ny * l.bend * len
    path(closed = true) {
        moveTo(rx + nx * l.w / 2f, ry + ny * l.w / 2f)
        quadTo(mx + nx * l.w * 0.32f, my + ny * l.w * 0.32f, tx, ty)
        quadTo(mx - nx * l.w * 0.32f, my - ny * l.w * 0.32f, rx - nx * l.w / 2f, ry - ny * l.w / 2f)
    }
}

/** Tepe kütlesi: kafatasını [v] kadar aşar; alt kenarı ([line]) perçemin altında kalır. */
private fun capShape(v: Float, low: Float, line: Float): VectorBuilder.() -> VectorBuilder.Part = {
    smooth(
        -1.04f - v * 0.5f, low, -1.02f - v, -0.45f, -0.74f - v * 0.6f, -0.97f - v * 0.55f, 0f, -1.04f - v, 0.74f + v * 0.6f, -0.97f - v * 0.55f,
        1.02f + v, -0.45f, 1.04f + v * 0.5f, low, 0.7f, line + 0.15f, 0f, line, -0.7f, line + 0.15f, closed = true,
    )
}

/** Baş çerçevesi (birim baş yarıçapı). */
internal fun Fig.headFrame(block: VectorBuilder.() -> Unit) = frame(sk.head, sk.headAngle, R, block)

private val Fig.def: HairDef get() = hairDefs.getValue(look.hair)

/** Yüz parçalarının dönüşle kayması. */
private val Fig.fs: Float get() = pose.turn * 0.16f

// ------------------------------------------------------------------ saç: vücudun arkası

/** Vücudun arkasına düşen saç (at kuyruğu, omuz boyu dalgalı saç, uzun ak saç). */
internal fun Fig.hairBehindBody() {
    val c = look.hairColor
    when (look.hair) {
        Hair.PONYTAIL -> headFrame {
            // Tepeden arkaya, sağ omuza doğru savrulan at kuyruğu
            val tail = listOf(0.1f, -1.05f, 0.95f, -1.15f, 1.5f, -0.45f, 1.58f, 0.7f, 1.32f, 1.85f, 1.12f, 2.45f, 0.98f, 1.65f, 1.06f, 0.55f, 0.82f, -0.38f, 0.3f, -0.72f)
            union(c, OUT, listOf({ smooth(*tail.toFloatArray(), closed = true, tension = 0.5f) }))
            taper(listOf(P(0.9f, -0.85f), P(1.32f, -0.1f), P(1.32f, 1.1f), P(1.15f, 2.1f)), 0.02f, 0.06f, 0.01f, CK.shade(c))
            taper(listOf(P(0.7f, -0.7f), P(1.1f, 0f), P(1.18f, 1f)), 0.02f, 0.05f, 0.01f, CK.shade(c))
            taper(listOf(P(1.25f, -0.55f), P(1.45f, 0.2f), P(1.4f, 0.9f)), 0.02f, 0.07f, 0.01f, CK.light(c), 0.8f)
        }
        Hair.WAVY -> headFrame {
            val mass = listOf(-1.15f, -0.4f, -1.32f, 0.55f, -1.38f, 1.3f, -1.22f, 1.9f, -0.92f, 1.66f, -0.62f, 1.95f, 0f, 1.7f, 0.62f, 1.95f, 0.92f, 1.66f, 1.22f, 1.9f, 1.38f, 1.3f, 1.32f, 0.55f, 1.15f, -0.4f, 0f, -1.1f)
            union(c, OUT, listOf({ smooth(*mass.toFloatArray(), closed = true, tension = 0.5f) }))
            spot(CK.shade(c)) { smooth(-0.9f, 0f, -1.05f, 1f, -0.7f, 1.6f, 0f, 1.5f, 0.7f, 1.6f, 1.05f, 1f, 0.9f, 0f, closed = true) }
        }
        Hair.LONG -> headFrame {
            val mass = listOf(-1.15f, -0.4f, -1.45f, 0.8f, -1.55f, 2f, -1.3f, 2.5f, -0.9f, 2.3f, -0.4f, 2.5f, 0.4f, 2.5f, 0.9f, 2.3f, 1.3f, 2.5f, 1.55f, 2f, 1.45f, 0.8f, 1.15f, -0.4f, 0f, -1.1f)
            union(c, OUT, listOf({ smooth(*mass.toFloatArray(), closed = true, tension = 0.5f) }))
            for (x in listOf(-1.25f, -0.8f, 0.8f, 1.25f)) taper(listOf(P(x * 0.9f, 0.4f), P(x, 1.4f), P(x * 1.02f, 2.2f)), 0.02f, 0.05f, 0.01f, CK.shade(c))
        }
        else -> Unit
    }
}

// ------------------------------------------------------------------ baş

internal fun Fig.headPart() {
    headFrame {
        hairBehindHead()
        if (robot) { robotHead(); return@headFrame }
        ears()
        neckShadow()
        val skin = look.skin
        fill(skin, OUT, faceOutline())
        faceShade()
        if (Extra.MASK in look.extras) mask()
        foreheadShadow()
        eyes()
        nose()
        if (Extra.BEARD in look.extras) beard()
        mouth()
        cheeks()
        if (Extra.MUSTACHE in look.extras) mustache()
        if (Extra.CHEF_MUSTACHE in look.extras) chefMustache()
        hairFront()
        brows()
        if (Extra.GLASSES in look.extras || Extra.ROUND_GLASSES in look.extras) glasses()
        hats()
        overlays()
        if (Extra.SPACE_HELMET in look.extras) spaceHelmet()
    }
}

/** Yüz dış hattı: kafatası, yanaklar, çene (çene tipine göre). */
private fun Fig.faceOutline(): VectorBuilder.() -> VectorBuilder.Part {
    val cx = pose.turn * 0.1f
    val j = when (body.jaw) {
        Jaw.ROUND -> floatArrayOf(0.94f, 0.48f, 0.26f, 1.02f)
        Jaw.SOFT -> floatArrayOf(0.87f, 0.55f, 0.1f, 1.1f)
        Jaw.SHARP -> floatArrayOf(0.86f, 0.6f, 0.08f, 1.13f)
        Jaw.SQUARE -> floatArrayOf(0.91f, 0.7f, 0.3f, 1.1f)
        Jaw.OLD -> floatArrayOf(0.9f, 0.62f, 0.2f, 1.08f)
    }
    val jx = j[0]; val jy = j[1]; val cw = j[2]; val cy = j[3]
    return {
        path(closed = true) {
            moveTo(-1f, -0.05f)
            cubicTo(-1f, -0.6f, -0.56f, -1.02f, 0f, -1.02f)
            cubicTo(0.56f, -1.02f, 1f, -0.6f, 1f, -0.05f)
            cubicTo(1f, 0.22f, jx + 0.06f, jy - 0.18f, jx, jy)
            cubicTo(jx - 0.1f, jy + 0.2f, cx + cw + 0.25f, cy - 0.04f, cx + cw, cy)
            lineTo(cx - cw, cy)
            cubicTo(cx - cw - 0.25f, cy - 0.04f, -jx + 0.1f, jy + 0.2f, -jx, jy)
            cubicTo(-jx - 0.06f, jy - 0.18f, -1f, 0.22f, -1f, -0.05f)
        }
    }
}

/** Yüzün sağ yanındaki cel gölge (elmacık altından çeneye). */
private fun Fig.faceShade() {
    val cx = pose.turn * 0.1f
    spot(CK.skinShade(look.skin)) {
        path(closed = true) {
            moveTo(0.99f, 0.05f)
            cubicTo(0.99f, 0.3f, 0.92f, 0.48f, 0.84f, 0.62f)
            cubicTo(0.72f, 0.85f, cx + 0.45f, 1.02f, cx + 0.15f, 1.08f)
            quadTo(cx + 0.5f, 0.86f, 0.76f, 0.42f)
            quadTo(0.86f, 0.2f, 0.99f, 0.05f)
        }
    }.alpha(0.7f)
}

private fun Fig.ears() {
    if (Extra.SPACE_HELMET in look.extras && look.hair == Hair.BOB) return
    val skin = look.skin
    for (sx in listOf(-1f, 1f)) {
        val x = sx * 0.97f - pose.turn * 0.12f
        fill(skin, MID) { ellipse(x, 0.14f, 0.15f, 0.24f) }
        line(CK.skinShade(skin), FINE) { arc(x, 0.15f, 0.07f, 0.13f, if (sx < 0) 100f else -80f, 160f) }
    }
}

/** Çenenin boyna düşen gölgesi (boyun çerçevesinde, boyun genişliğinde). */
private fun Fig.neckShadow() {
    if (Extra.SPACE_HELMET in look.extras) return
    val nr = body.neckR / R * 0.88f
    spot(CK.skinShade(look.skin)) { path(closed = true) { moveTo(-nr, 0.4f); lineTo(nr, 0.4f); lineTo(nr, 1.18f); quadTo(0f, 1.42f, -nr, 1.18f) } }
}

/** Perçemin alna düşen gölgesi. */
private fun Fig.foreheadShadow() {
    val d = def
    if (d.capV == null || look.hair == Hair.UNDER_CAP) return
    for (l in d.bangs) if (abs(l.tx) < 0.8f) spot(CK.skinShade(look.skin), lockShape(l, 0.02f, 0.1f)).alpha(0.8f)
}

// ------------------------------------------------------------------ saç: başın arkası ve önü

private fun Fig.hairBehindHead() {
    val c = look.hairColor
    val d = def
    if (d.back.isNotEmpty()) {
        union(c, OUT, d.back.map { lockShape(it) })
        // İç gölge: arka tutamların köke yakın kısmı koyu
        for (l in d.back) taper(listOf(P(l.rx, l.ry), P(l.rx + (l.tx - l.rx) * 0.75f, l.ry + (l.ty - l.ry) * 0.75f)), 0.02f, 0.1f, 0.01f, CK.shade(c), 0.9f)
    }
    when (look.hair) {
        Hair.BUN -> {
            union(c, OUT, listOf({ circle(0.05f, -1.18f, 0.44f) }))
            taper(listOf(P(-0.2f, -1.4f), P(0.05f, -1.5f), P(0.3f, -1.38f)), 0.02f, 0.06f, 0.01f, CK.shade(c))
            fill(CK.GOLD, FINE) { poly(-0.62f, -1.5f, 0.62f, -0.88f, 0.6f, -0.84f, -0.64f, -1.46f, closed = true) }
            fill(CK.GOLD, FINE) { circle(-0.62f, -1.49f, 0.07f) }
        }
        Hair.BOB -> union(c, OUT, listOf({ smooth(-1.2f, -0.3f, -1.25f, 0.45f, -1.12f, 0.96f, -0.8f, 1.02f, 0.8f, 1.02f, 1.12f, 0.96f, 1.25f, 0.45f, 1.2f, -0.3f, 0f, -1.12f, closed = true) }))
        Hair.TUFTS -> {
            // Kulak üstünde kabarık ak tutamlar
            for (sx in listOf(-1f, 1f)) union(c, MID, listOf(
                { circle(sx * 0.98f, -0.28f, 0.2f) }, { circle(sx * 1.08f, -0.02f, 0.22f) }, { circle(sx * 0.98f, 0.22f, 0.17f) },
            ))
        }
        else -> Unit
    }
    if (Extra.HEADBAND in look.extras) {
        // Alın bandının arkada uçuşan uçları
        val a = look.accent
        fill(a, MID) { path(closed = true) { moveTo(-0.9f, -0.5f); cubicTo(-1.3f, -0.6f, -1.6f, -0.4f, -1.95f, -0.55f); cubicTo(-1.7f, -0.25f, -1.35f, -0.3f, -0.95f, -0.3f) } }
        fill(a, MID) { path(closed = true) { moveTo(-0.92f, -0.42f); cubicTo(-1.3f, -0.3f, -1.5f, 0f, -1.85f, 0.05f); cubicTo(-1.5f, 0.2f, -1.2f, 0f, -0.95f, -0.25f) } }
    }
}

private fun Fig.hairFront() {
    val c = look.hairColor
    val d = def
    val shapes = ArrayList<VectorBuilder.() -> VectorBuilder.Part>()
    d.capV?.let { shapes += capShape(it, d.capLow, d.line) }
    d.side.forEach { shapes += lockShape(it) }
    d.bangs.forEach { shapes += lockShape(it) }
    if (shapes.isEmpty()) {
        if (look.hair == Hair.TUFTS) {
            // Kel tepe: parıltı ve tek tük tel
            spot(CK.WHITE) { ellipse(-0.35f, -0.72f, 0.28f, 0.12f, -25f) }.alpha(0.55f)
            taper(listOf(P(-0.05f, -1f), P(0.05f, -1.18f), P(0.2f, -1.14f)), 0.02f, 0.04f, 0.01f, CK.INK)
            taper(listOf(P(0.1f, -1.01f), P(0.25f, -1.15f), P(0.38f, -1.08f)), 0.02f, 0.035f, 0.01f, CK.INK)
        }
        return
    }
    union(c, OUT, shapes)
    // Tutam gölgeleri: her perçem tutamının ortasında koyu ince çizgi
    for (l in d.bangs + d.side) {
        val m = P(l.rx + (l.tx - l.rx) * 0.15f, l.ry + (l.ty - l.ry) * 0.15f)
        val e = P(l.rx + (l.tx - l.rx) * 0.82f, l.ry + (l.ty - l.ry) * 0.82f)
        val mid = P((m.x + e.x) / 2f - (e.y - m.y) * l.bend * 0.6f, (m.y + e.y) / 2f + (e.x - m.x) * l.bend * 0.6f)
        taper(listOf(m, mid, e), 0.02f, l.w * 0.22f, 0.01f, CK.shade(c))
    }
    // Tutam ayrımları: perçem tutamlarının iki kenarı uçtan köke doğru incelen mürekkep çizgisi (sivri anime perçemi)
    for (l in d.bangs) {
        val ddx = l.tx - l.rx; val ddy = l.ty - l.ry
        val len = max(1e-3f, hypot(ddx, ddy))
        val nx = -ddy / len; val ny = ddx / len
        val mx = l.rx + ddx * 0.5f + nx * l.bend * len; val my = l.ry + ddy * 0.5f + ny * l.bend * len
        for (sd in listOf(1f, -1f)) {
            val ax = l.rx + sd * nx * l.w / 2f; val ay = l.ry + sd * ny * l.w / 2f
            val cx = mx + sd * nx * l.w * 0.32f; val cy = my + sd * ny * l.w * 0.32f
            val pts = (0..6).map { i ->
                val t = 1f - i * 0.09f
                val u = 1f - t
                P(u * u * ax + 2f * u * t * cx + t * t * l.tx, u * u * ay + 2f * u * t * cy + t * t * l.ty)
            }
            taper(pts, 0.012f, 0.032f, 0.004f, CK.INK, 0.9f)
        }
    }
    // Saç çizgisinde koyu gölge bandı (tepe ile perçem arası)
    if (d.capV != null && d.bangs.isNotEmpty()) taper(listOf(P(-0.75f, d.line + 0.02f), P(0f, d.line - 0.1f), P(0.75f, d.line + 0.02f)), 0.01f, 0.07f, 0.01f, CK.shade(c), 0.8f)
    // Parlak halka (anime): tepede zikzak açık bant
    if (d.shine && d.capV != null) {
        val v = d.capV
        val outer = (0..10).map { i -> val a = Math.toRadians(222.0 + i * 9.6); P((0.93f + v * 0.5f) * cos(a).toFloat(), -0.05f + (0.93f + v * 0.5f) * sin(a).toFloat()) }
        val inner = (0..10).reversed().map { i ->
            val a = Math.toRadians(222.0 + i * 9.6)
            val r = 0.83f + v * 0.5f + if (i % 2 == 0) 0.04f else -0.05f
            P(r * cos(a).toFloat(), -0.05f + r * sin(a).toFloat())
        }
        spot(CK.mix(c, "#FFFFFF", 0.38f)) { points(outer + inner, closed = true) }.alpha(0.75f)
        taper(listOf(P(-0.42f, -0.84f), P(-0.3f, -0.9f)), 0.01f, 0.04f, 0.01f, CK.WHITE, 0.9f)
        taper(listOf(P(0.18f, -0.93f), P(0.32f, -0.9f)), 0.01f, 0.035f, 0.01f, CK.WHITE, 0.9f)
    }
    when (look.hair) {
        Hair.PARTED -> taper(listOf(P(0.38f, -1.08f), P(0.34f, -0.9f), P(0.3f, -0.76f)), 0.01f, 0.03f, 0.01f, CK.deep(c))
        Hair.PONYTAIL -> if (Extra.RIBBON in look.extras) bow(0.62f, -1.0f, look.accent, 0.075f)
        Hair.WAVY -> if (Extra.HAIRCLIP in look.extras) {
            fill(look.accent, FINE) { roundRect(-0.78f, -0.6f, 0.36f, 0.1f, 0.05f) }
            fill(look.accent, FINE) { roundRect(-0.72f, -0.47f, 0.36f, 0.1f, 0.05f) }
        }
        else -> Unit
    }
}

// ------------------------------------------------------------------ gözler

/** Göz ölçüleri (yarı en, üst ve alt yükseklik, iris yarıçapları, kirpik kalınlığı). */
private class EyeGeo(val w: Float, val up: Float, val low: Float, val irx: Float, val iry: Float, val lash: Float)

private fun Fig.eyeGeo(): EyeGeo = when (look.eyes) {
    EyeStyle.FEMALE -> EyeGeo(0.3f, 0.4f, 0.22f, 0.22f, 0.3f, 0.12f)
    EyeStyle.MALE -> EyeGeo(0.29f, 0.3f, 0.17f, 0.19f, 0.25f, 0.105f)
    EyeStyle.KID -> EyeGeo(0.31f, 0.4f, 0.26f, 0.23f, 0.31f, 0.115f)
    EyeStyle.COMIC -> EyeGeo(0.27f, 0.23f, 0.14f, 0.145f, 0.19f, 0.1f)
    EyeStyle.ELDER -> EyeGeo(0.22f, 0.17f, 0.11f, 0.12f, 0.14f, 0.08f)
    EyeStyle.SCREEN -> EyeGeo(0.25f, 0.25f, 0.15f, 0.15f, 0.2f, 0.1f)
}

private const val EY = 0.22f

/** Eğri (u → v) üzerinde doğrusal ara değer; [pts] u'ya göre sıralı. */
private fun interp(pts: List<P>, u: Float): Float {
    if (u <= pts.first().x) return pts.first().y
    for (i in 1 until pts.size) if (u <= pts[i].x) {
        val a = pts[i - 1]; val c = pts[i]
        val t = if (c.x - a.x < 1e-6f) 0f else (u - a.x) / (c.x - a.x)
        return a.y + (c.y - a.y) * t
    }
    return pts.last().y
}

private fun Fig.eyes() {
    eye(false, face.eye)
    eye(true, face.eyeR ?: face.eye)
}

private fun Fig.eye(right: Boolean, shape: EyeShape) {
    val g = eyeGeo()
    val f = face
    val o = if (right) 1f else -1f
    val turn = pose.turn
    val far = (turn > 0.1f && right) || (turn < -0.1f && !right)
    val w = g.w * (if (far) 1f - 0.3f * abs(turn) else 1f)
    val ex = o * 0.42f + fs
    val ey = EY
    var up = g.up; var low = g.low; var irisK = f.iris
    if (shape == EyeShape.WIDE || shape == EyeShape.TINY) { up *= 1.15f; low *= 1.35f; irisK *= 0.78f }
    val lashC = if (look.eyes == EyeStyle.ELDER) CK.mix(CK.LASH, look.skin, 0.25f) else CK.LASH
    fun X(u: Float) = ex + o * u * w

    when (shape) {
        EyeShape.HAPPY -> {
            taper(listOf(P(X(-1f), ey + 0.06f), P(X(-0.4f), ey - 0.13f), P(X(0.35f), ey - 0.15f), P(X(1.05f), ey + 0.02f)), 0.03f, g.lash * 0.85f, 0.03f, lashC)
            taper(listOf(P(X(0.9f), ey - 0.02f), P(X(1.2f), ey - 0.1f)), 0.03f, 0.04f, 0.01f, lashC)
            return
        }
        EyeShape.CLOSED -> {
            taper(listOf(P(X(-1f), ey - 0.02f), P(X(0f), ey + 0.1f), P(X(1.05f), ey - 0.04f)), 0.03f, g.lash * 0.8f, 0.03f, lashC)
            for (i in 0..1) taper(listOf(P(X(0.45f + i * 0.3f), ey + 0.07f - i * 0.03f), P(X(0.55f + i * 0.35f), ey + 0.16f - i * 0.02f)), 0.02f, 0.025f, 0.005f, lashC)
            return
        }
        EyeShape.SQUINT -> {
            taper(listOf(P(X(1f), ey - 0.15f), P(X(-0.75f), ey + 0.02f)), 0.03f, g.lash * 0.8f, 0.05f, lashC)
            taper(listOf(P(X(-0.75f), ey + 0.02f), P(X(1f), ey + 0.17f)), 0.05f, g.lash * 0.7f, 0.02f, lashC)
            return
        }
        else -> Unit
    }

    // Göz kapakları: üst ve alt eğri (u: -1 iç köşe .. 1 dış köşe; v: göz merkezine göre)
    val upperBase = VectorBuilder.catmullRom(listOf(P(-1f, 0.05f * up), P(-0.6f, -0.8f * up), P(0.05f, -up), P(0.6f, -0.88f * up), P(1f, -0.32f * up)), false, 0.5f, 6)
    val lowerBase = VectorBuilder.catmullRom(listOf(P(-1f, 0.05f * up), P(-0.6f, 0.8f * low), P(0f, low), P(0.6f, 0.75f * low), P(1f, -0.32f * up)), false, 0.5f, 6)
    var lidIn = f.lidIn; var lidOut = f.lidOut; var lowerLid = f.lowerLid
    if (shape == EyeShape.HALF) { lidIn = max(lidIn, 0.45f); lidOut = max(lidOut, 0.45f); lowerLid = max(lowerLid, 0.2f) }
    val us = (0..16).map { -1f + it / 8f }
    val upper = us.map { u ->
        val base = interp(upperBase, u)
        val t = (u + 1f) / 2f
        val lidLine = -up * (1f - (lidIn + (lidOut - lidIn) * t))
        P(u, max(base, lidLine))
    }
    val lower = us.map { u ->
        val base = interp(lowerBase, u)
        val raised = base - low * lowerLid * (1f - u * u)
        P(u, max(raised, interp(upper, u) + 0.03f))
    }
    fun upY(u: Float) = interp(upper, u)
    fun lowY(u: Float) = interp(lower, u)
    fun clamp(x: Float, y: Float, maxY: Float = Float.MAX_VALUE, minY: Float = -Float.MAX_VALUE): P {
        val u = ((x - ex) / (o * w)).coerceIn(-1f, 1f)
        val yy = y.coerceIn(ey + upY(u), ey + lowY(u)).coerceIn(minY, maxY)
        return P(X(u), yy)
    }
    fun clipped(cx: Float, cy: Float, rx: Float, ry: Float, maxY: Float = Float.MAX_VALUE, minY: Float = -Float.MAX_VALUE): List<P> =
        (0 until 36).map { i -> val a = Math.PI * 2 * i / 36; clamp(cx + rx * cos(a).toFloat(), cy + ry * sin(a).toFloat(), maxY, minY) }

    // Akı ve üst kapak gölgesi
    val white = upper.map { P(X(it.x), ey + it.y) } + lower.reversed().map { P(X(it.x), ey + it.y) }
    spot(CK.EYE_WHITE) { points(white, closed = true) }
    spot("#D8D4EC") { points(upper.map { P(X(it.x), ey + it.y) } + upper.reversed().map { P(X(it.x), ey + min(it.y + 0.07f, interp(lower, it.x))) }, closed = true) }

    val irx = g.irx * irisK; val iry = g.iry * irisK
    val ix = ex + f.gazeX * w * 0.42f + turn * 0.05f
    val iy = ey + 0.01f + f.gazeY * 0.08f
    if (shape != EyeShape.TINY) {
        val ec = look.eyeColor
        spot(ec) { points(clipped(ix, iy, irx, iry), closed = true) }
        spot(CK.mix(ec, "#120A22", 0.5f)) { points(clipped(ix, iy, irx, iry, maxY = iy - iry * 0.12f), closed = true) }
        spot(CK.mix(ec, "#FFFFFF", 0.42f)) { points(clipped(ix, iy + iry * 0.4f, irx * 0.68f, iry * 0.48f), closed = true) }.alpha(0.9f)
        spot(CK.PUPIL) { points(clipped(ix, iy + iry * 0.04f, irx * 0.42f, iry * 0.5f), closed = true) }
        b.points(clipped(ix, iy, irx, iry), closed = true).fill("@none").stroke(CK.mix(ec, "#120A22", 0.65f)).width(0.5f)
        if (shape == EyeShape.STAR) {
            spot(CK.WHITE) { star(ix, iy, irx * 0.85f, irx * 0.22f, 4) }
            spot(CK.WHITE) { star(ix + irx * 0.55f, iy + iry * 0.55f, irx * 0.32f, irx * 0.09f, 4) }
        } else {
            spot(CK.WHITE) { points(clipped(ix - irx * 0.36f, iy - iry * 0.38f, irx * 0.36f, iry * 0.3f), closed = true) }
            spot(CK.WHITE) { points(clipped(ix + irx * 0.42f, iy + iry * 0.45f, irx * 0.14f, irx * 0.14f), closed = true) }
            if (f.tear || f.streams) spot(CK.WHITE) { points(clipped(ix + irx * 0.3f, iy - iry * 0.1f, irx * 0.16f, irx * 0.16f), closed = true) }.alpha(0.9f)
        }
    } else {
        spot(CK.PUPIL) { circle(ix, iy + 0.02f, 0.045f) }
        b.points(white, closed = true).fill("@none").stroke(CK.LASH).width(0.5f)
    }

    // Kalın üst kirpik çizgisi ve dış uçtaki kıvrım
    val lashPts = upper.filter { it.x >= -0.95f }.map { P(X(it.x), ey + it.y - 0.015f) } + P(X(1.16f), ey + upY(1f) - 0.08f)
    val lw = if (shape == EyeShape.TINY) g.lash * 0.6f else g.lash
    taper(lashPts, 0.02f, lw, 0.03f, lashC)
    if (look.eyes == EyeStyle.FEMALE) {
        taper(listOf(P(X(0.82f), ey + upY(0.82f) - 0.04f), P(X(1.05f), ey + upY(0.82f) - 0.16f)), 0.04f, 0.03f, 0.005f, lashC)
        taper(listOf(P(X(0.6f), ey + upY(0.6f) - 0.05f), P(X(0.74f), ey + upY(0.6f) - 0.17f)), 0.035f, 0.025f, 0.005f, lashC)
    }
    // Alt kirpik ve kapak çizgisi
    val lowFrom = if (look.eyes == EyeStyle.FEMALE || look.eyes == EyeStyle.KID) -0.3f else 0.25f
    taper(lower.filter { it.x >= lowFrom }.reversed().map { P(X(it.x), ey + it.y + 0.01f) }, 0.035f, 0.022f, 0.004f, lashC, 0.85f)
    if (look.eyes != EyeStyle.COMIC) taper(upper.filter { it.x >= -0.3f }.map { P(X(it.x), ey + it.y - 0.13f) }, 0.004f, 0.022f, 0.004f, CK.mix(look.skin, CK.LASH, 0.45f))
    if (look.eyes == EyeStyle.ELDER) {
        // Göz kenarında kırışık
        for (i in 0..1) taper(listOf(P(X(1.15f), ey - 0.02f + i * 0.09f), P(X(1.38f), ey - 0.06f + i * 0.14f)), 0.01f, 0.025f, 0.005f, CK.skinShade(look.skin))
    }
    if (f.sparkle && shape != EyeShape.STAR) spot(CK.WHITE) { star(X(1.25f), ey - up - 0.05f, 0.1f, 0.025f, 4) }
}

// ------------------------------------------------------------------ kaş, burun, ağız, yanak

private fun Fig.brows() {
    val g = eyeGeo()
    val f = face
    val c = if (look.hair == Hair.TUFTS || look.hair == Hair.LONG) look.hairColor else CK.mix(look.hairColor, CK.INK, 0.55f)
    val bushy = look.hair == Hair.TUFTS || look.hair == Hair.LONG
    val thick = when (look.eyes) {
        EyeStyle.FEMALE -> 0.045f; EyeStyle.COMIC -> 0.09f; EyeStyle.KID -> 0.07f; else -> 0.075f
    }
    for (right in listOf(false, true)) {
        val o = if (right) 1f else -1f
        val ex = o * 0.42f + fs
        val w = g.w
        val by = EY - g.up - 0.17f
        val raise = if (right) f.browRaiseR else 0f
        val inner = P(ex - o * w * 0.85f, by + f.browIn - raise * 0.6f)
        val outer = P(ex + o * w * 1.12f, by + f.browOut + 0.05f - raise)
        val mid = P(ex + o * w * 0.15f, by - 0.05f + (f.browIn + f.browOut) * 0.35f - raise * 1.1f)
        if (bushy) {
            union(c, MID, listOf({ smooth(inner.x, inner.y + 0.05f, mid.x, mid.y - 0.06f, outer.x + o * 0.06f, outer.y + 0.02f, mid.x, mid.y + 0.09f, closed = true) }))
            taper(listOf(P(mid.x - o * 0.05f, mid.y), P(outer.x, outer.y + 0.06f)), 0.01f, 0.025f, 0.005f, CK.shade(c))
        } else {
            taper(listOf(inner, mid, outer), thick * 0.95f, thick, thick * 0.15f, c)
        }
    }
}

private fun Fig.nose() {
    val nx = pose.turn * 0.22f
    val c = CK.mix(CK.skinShade(look.skin), CK.INK, 0.35f)
    when (look.eyes) {
        EyeStyle.COMIC -> {
            taper(listOf(P(nx + 0.07f, 0.32f), P(nx + 0.08f, 0.52f), P(nx + 0.02f, 0.62f)), 0.01f, 0.03f, 0.02f, c)
            taper(listOf(P(nx - 0.07f, 0.62f), P(nx + 0.01f, 0.65f), P(nx + 0.08f, 0.62f)), 0.01f, 0.025f, 0.01f, c)
        }
        EyeStyle.ELDER -> {
            taper(listOf(P(nx + 0.06f, 0.36f), P(nx + 0.12f, 0.6f), P(nx - 0.02f, 0.66f)), 0.01f, 0.04f, 0.01f, c)
            spot(CK.skinShade(look.skin)) { ellipse(nx + 0.04f, 0.6f, 0.1f, 0.06f) }.alpha(0.6f)
        }
        EyeStyle.KID -> spot(c) { ellipse(nx + 0.01f, 0.55f, 0.035f, 0.022f) }
        else -> {
            taper(listOf(P(nx + 0.035f, 0.47f), P(nx + 0.01f, 0.58f), P(nx - 0.05f, 0.61f)), 0.01f, 0.03f, 0.01f, c)
            spot(CK.skinShade(look.skin)) { poly(nx + 0.02f, 0.45f, nx + 0.06f, 0.58f, nx - 0.01f, 0.59f, closed = true) }.alpha(0.6f)
        }
    }
}

private fun Fig.mouth() {
    val mx = pose.turn * 0.18f
    val my = when {
        look.eyes == EyeStyle.ELDER -> 0.9f
        body.jaw == Jaw.ROUND -> 0.79f
        else -> 0.82f
    }
    val mw = when (look.eyes) { EyeStyle.FEMALE -> 0.16f; EyeStyle.COMIC -> 0.22f; else -> 0.19f }
    val lc = CK.mix(CK.MOUTH, CK.INK, 0.35f)
    fun dark(shape: VectorBuilder.() -> VectorBuilder.Part) = fill(CK.MOUTH, FINE, shape)
    when (face.mouth) {
        Mouth.SMILE -> {
            taper(listOf(P(mx - mw, my - 0.03f), P(mx, my + 0.045f), P(mx + mw, my - 0.04f)), 0.015f, 0.04f, 0.015f, lc)
            if (look.eyes == EyeStyle.FEMALE) spot(CK.TONGUE) { ellipse(mx, my + 0.07f, mw * 0.4f, 0.015f) }.alpha(0.5f)
        }
        Mouth.LINE -> taper(listOf(P(mx - mw * 0.8f, my), P(mx, my + 0.01f), P(mx + mw * 0.8f, my - 0.005f)), 0.015f, 0.035f, 0.015f, lc)
        Mouth.FROWN -> taper(listOf(P(mx - mw * 0.9f, my + 0.05f), P(mx, my - 0.02f), P(mx + mw * 0.9f, my + 0.05f)), 0.015f, 0.04f, 0.015f, lc)
        Mouth.POUT -> taper(listOf(P(mx - mw * 0.55f, my + 0.03f), P(mx, my - 0.01f), P(mx + mw * 0.55f, my + 0.03f)), 0.012f, 0.03f, 0.012f, lc)
        Mouth.WAVY -> taper(listOf(P(mx - mw, my + 0.02f), P(mx - mw * 0.5f, my - 0.03f), P(mx, my + 0.02f), P(mx + mw * 0.5f, my - 0.03f), P(mx + mw, my + 0.02f)), 0.012f, 0.03f, 0.012f, lc)
        Mouth.SMIRK -> {
            taper(listOf(P(mx - mw * 0.85f, my + 0.02f), P(mx + mw * 0.2f, my + 0.035f), P(mx + mw * 1.05f, my - 0.08f)), 0.012f, 0.04f, 0.015f, lc)
            taper(listOf(P(mx + mw * 1.05f, my - 0.11f), P(mx + mw * 1.15f, my - 0.03f)), 0.005f, 0.02f, 0.005f, lc)
        }
        Mouth.O -> {
            dark { ellipse(mx, my + 0.03f, 0.075f, 0.095f) }
            spot(CK.TONGUE) { ellipse(mx, my + 0.08f, 0.045f, 0.03f) }
        }
        Mouth.OPEN_SMILE, Mouth.LAUGH -> {
            val k = if (face.mouth == Mouth.LAUGH) 1.45f else 1f
            val hw = mw * 1.15f * k; val depth = 0.2f * k
            dark { path(closed = true) { moveTo(mx - hw, my - 0.06f); quadTo(mx, my - 0.03f, mx + hw, my - 0.06f); cubicTo(mx + hw * 0.85f, my + depth, mx - hw * 0.85f, my + depth, mx - hw, my - 0.06f) } }
            spot(CK.TONGUE) { ellipse(mx, my + depth * 0.62f, hw * 0.5f, depth * 0.22f) }
            spot(CK.TEETH) { path(closed = true) { moveTo(mx - hw * 0.8f, my - 0.05f); quadTo(mx, my - 0.025f, mx + hw * 0.8f, my - 0.05f); lineTo(mx + hw * 0.7f, my - 0.005f); quadTo(mx, my + 0.015f, mx - hw * 0.7f, my - 0.005f) } }
        }
        Mouth.GRIT -> {
            fill(CK.TEETH, FINE) { roundRect(mx - mw * 1.1f, my - 0.06f, mw * 2.2f, 0.13f, 0.04f) }
            line(lc, 0.6f) { line(mx - mw * 1.05f, my + 0.005f, mx + mw * 1.05f, my + 0.005f) }
            for (i in -1..1) line(lc, 0.5f) { line(mx + i * mw * 0.55f, my - 0.055f, mx + i * mw * 0.55f, my + 0.065f) }
        }
        Mouth.SHOUT -> {
            val top = my - 0.12f; val bot = my + 0.3f
            dark { smooth(mx - 0.33f, top, mx + 0.33f, top, mx + 0.22f, bot - 0.06f, mx, bot, mx - 0.22f, bot - 0.06f, closed = true, tension = 0.35f) }
            spot(CK.TEETH) { poly(mx - 0.28f, top + 0.015f, mx + 0.28f, top + 0.015f, mx + 0.25f, top + 0.06f, mx + 0.16f, top + 0.065f, mx + 0.13f, top + 0.11f, mx + 0.1f, top + 0.065f, mx - 0.1f, top + 0.065f, mx - 0.13f, top + 0.11f, mx - 0.16f, top + 0.065f, mx - 0.25f, top + 0.06f, closed = true) }
            spot(CK.TONGUE) { ellipse(mx, bot - 0.07f, 0.13f, 0.06f) }
        }
        Mouth.WAIL -> {
            dark { smooth(mx - 0.27f, my + 0.08f, mx - 0.12f, my - 0.06f, mx + 0.12f, my - 0.06f, mx + 0.27f, my + 0.08f, mx + 0.16f, my + 0.24f, mx - 0.16f, my + 0.24f, closed = true, tension = 0.4f) }
            spot(CK.TONGUE) { ellipse(mx, my + 0.17f, 0.11f, 0.045f) }
            spot(CK.TEETH) { poly(mx - 0.13f, my - 0.045f, mx + 0.13f, my - 0.045f, mx + 0.1f, my - 0.01f, mx - 0.1f, my - 0.01f, closed = true) }
        }
        Mouth.JAWDROP -> {
            dark { roundRect(mx - 0.1f, my - 0.06f, 0.2f, 0.3f, 0.09f) }
            spot(CK.TONGUE) { ellipse(mx, my + 0.19f, 0.07f, 0.04f) }
        }
        Mouth.OUCH -> {
            dark { smooth(mx - 0.15f, my + 0.08f, mx + 0.02f, my - 0.07f, mx + 0.17f, my + 0.06f, mx, my + 0.14f, closed = true, tension = 0.3f) }
            spot(CK.TONGUE) { ellipse(mx, my + 0.09f, 0.06f, 0.03f) }
        }
    }
}

/** Yanak: kızarma, çil, yara bandı, gözyaşı. */
private fun Fig.cheeks() {
    val f = face
    val cy = 0.56f
    if (f.blush) for (sx in listOf(-1f, 1f)) {
        val x = sx * 0.6f + fs
        spot(CK.BLUSH) { ellipse(x, cy, 0.2f, 0.085f) }.alpha(0.45f)
        for (i in -1..1) taper(listOf(P(x + i * 0.08f + 0.03f, cy - 0.05f), P(x + i * 0.08f - 0.03f, cy + 0.05f)), 0.012f, 0.022f, 0.008f, CK.mix(CK.BLUSH, CK.MOUTH, 0.4f))
    }
    if (Extra.FRECKLES in look.extras) for (sx in listOf(-1f, 1f)) for ((dx, dy) in listOf(-0.08f to 0f, 0f to -0.04f, 0.08f to 0.01f)) {
        spot(CK.mix(CK.skinShade(look.skin), "#6B3A22", 0.4f)) { circle(sx * 0.58f + dx + fs, cy - 0.02f + dy, 0.022f) }
    }
    if (Extra.BANDAID in look.extras || f.bandage) {
        val x = 0.6f + fs; val y = cy - 0.02f
        b.group(rotate = -18f, px = x, py = y) {
            fill("#F2D3A2", FINE) { roundRect(x - 0.2f, y - 0.07f, 0.4f, 0.14f, 0.06f) }
            fill("#E8BE84", 0.4f) { roundRect(x - 0.07f, y - 0.055f, 0.14f, 0.11f, 0.03f) }
        }
    }
    if (f.tear) {
        val x = 0.42f + fs + eyeGeo().w * 0.9f
        drop(x, EY + 0.3f, 0.055f)
    }
    if (f.streams) for (sx in listOf(-1f, 1f)) {
        // Şelale gözyaşı: göz altından çene hizasına inen dalgalı şerit
        val x0 = sx * 0.42f + fs
        val pts = listOf(P(x0 - 0.12f, EY + 0.12f), P(x0 + sx * 0.05f, EY + 0.12f), P(x0 + sx * 0.18f, 0.7f), P(x0 + sx * 0.3f, 1.12f), P(x0 + sx * 0.06f, 1.12f), P(x0 - sx * 0.02f, 0.7f))
        fill(CK.TEAR, FINE) { smooth(*pts.flatMap { listOf(it.x, it.y) }.toFloatArray(), closed = true, tension = 0.4f) }.alpha(0.9f)
        taper(listOf(P(x0 + sx * 0.02f, EY + 0.2f), P(x0 + sx * 0.12f, 0.75f), P(x0 + sx * 0.2f, 1.05f)), 0.01f, 0.035f, 0.01f, CK.WHITE, 0.9f)
    }
}

private fun Fig.mustache() {
    val c = look.hairColor
    union(c, MID, listOf({ smooth(-0.58f, 0.82f, -0.32f, 0.64f, 0f, 0.7f, 0.32f, 0.64f, 0.58f, 0.82f, 0.36f, 0.86f, 0f, 0.8f, -0.36f, 0.86f, closed = true) }))
    taper(listOf(P(-0.3f, 0.72f), P(-0.15f, 0.76f)), 0.005f, 0.02f, 0.005f, CK.shade(c))
    taper(listOf(P(0.15f, 0.76f), P(0.3f, 0.72f)), 0.005f, 0.02f, 0.005f, CK.shade(c))
}

private fun Fig.chefMustache() {
    val c = look.hairColor
    for (sx in listOf(-1f, 1f)) taper(listOf(P(sx * 0.02f, 0.69f), P(sx * 0.3f, 0.74f), P(sx * 0.55f, 0.66f), P(sx * 0.6f, 0.55f), P(sx * 0.5f, 0.52f)), 0.13f, 0.11f, 0.03f, c)
}

private fun Fig.beard() {
    val c = look.hairColor
    union(c, OUT, listOf({
        path(closed = true) {
            moveTo(-0.98f, 0.12f)
            cubicTo(-1.08f, 0.9f, -0.72f, 1.9f, 0.05f, 2.7f)
            cubicTo(0.76f, 1.9f, 1.08f, 0.9f, 0.98f, 0.12f)
            cubicTo(0.86f, 0.6f, 0.55f, 1.02f, 0f, 1.04f)
            cubicTo(-0.55f, 1.02f, -0.86f, 0.6f, -0.98f, 0.12f)
        }
    }))
    for ((x0, x1) in listOf(-0.6f to -0.25f, -0.2f to -0.02f, 0.25f to 0.12f, 0.62f to 0.32f)) {
        taper(listOf(P(x0, 1.15f), P((x0 + x1) / 2f, 1.75f), P(x1, 2.3f)), 0.01f, 0.04f, 0.005f, CK.shade(c))
    }
    // Bıyık (sakalla birlikte, sarkık)
    union(c, MID, listOf({ smooth(0f, 0.66f, 0.3f, 0.66f, 0.56f, 0.86f, 0.6f, 1.08f, 0.38f, 0.86f, 0f, 0.78f, -0.38f, 0.86f, -0.6f, 1.08f, -0.56f, 0.86f, -0.3f, 0.66f, closed = true) }))
}

private fun Fig.mask() {
    val c = look.second
    val fsx = fs
    fill(c, MID) {
        path(closed = true) {
            moveTo(-1.08f, -0.14f)
            cubicTo(-0.7f, -0.12f, -0.3f, -0.08f, fsx, 0.06f)
            cubicTo(0.3f, -0.08f, 0.7f, -0.12f, 1.08f, -0.14f)
            cubicTo(1.02f, 0.2f, 0.85f, 0.44f, 0.45f + fsx, 0.47f)
            cubicTo(0.24f + fsx, 0.48f, 0.12f + fsx, 0.4f, fsx, 0.32f)
            cubicTo(-0.12f + fsx, 0.4f, -0.24f + fsx, 0.48f, -0.45f + fsx, 0.47f)
            cubicTo(-0.85f, 0.44f, -1.02f, 0.2f, -1.08f, -0.14f)
        }
    }
    spot(CK.light(c)) { ellipse(-0.62f + fsx, 0.0f, 0.18f, 0.04f, -8f) }.alpha(0.5f)
}

private fun Fig.glasses() {
    val g = eyeGeo()
    val round = Extra.ROUND_GLASSES in look.extras
    val fc = if (round) "#6B5A3A" else look.accent
    for (sx in listOf(-1f, 1f)) {
        val x = sx * 0.42f + fs
        val shape: VectorBuilder.() -> VectorBuilder.Part = if (round) ({ circle(x, EY, g.w + 0.08f) }) else ({ roundRect(x - g.w - 0.08f, EY - 0.2f, (g.w + 0.08f) * 2f, 0.38f, 0.08f) })
        b.shape().fill("#D9F0FF").noStroke().alpha(0.18f)
        b.shape().fill("@none").stroke(fc).width(1.2f)
        taper(listOf(P(x - g.w * 0.5f, EY + 0.08f), P(x + g.w * 0.1f, EY - 0.12f)), 0.01f, 0.05f, 0.01f, CK.WHITE, 0.75f)
    }
    line(fc, 1.1f) { line(-0.42f + g.w + 0.08f + fs, EY - 0.04f, 0.42f - g.w - 0.08f + fs, EY - 0.04f) }
}

// ------------------------------------------------------------------ şapkalar

private fun Fig.hats() {
    val ex = look.extras
    if (Extra.HEADBAND in ex) {
        fill(look.accent, MID) { path(closed = true) { moveTo(-1.1f, -0.44f); quadTo(0f, -0.78f, 1.1f, -0.44f); lineTo(1.08f, -0.24f); quadTo(0f, -0.58f, -1.08f, -0.24f) } }
        taper(listOf(P(-0.8f, -0.43f), P(0f, -0.66f), P(0.8f, -0.43f)), 0.01f, 0.04f, 0.01f, CK.light(look.accent), 0.8f)
    }
    if (Extra.POLICE_CAP in ex) {
        val c = look.main
        fill(c, OUT) { smooth(-1.04f, -0.5f, -1.32f, -0.98f, -1.08f, -1.36f, 0f, -1.46f, 1.08f, -1.36f, 1.32f, -0.98f, 1.04f, -0.5f, 0f, -0.44f, closed = true, tension = 0.45f) }
        spot(CK.light(c)) { ellipse(-0.45f, -1.25f, 0.4f, 0.08f, -6f) }.alpha(0.5f)
        fill(CK.deep(c), MID) { path(closed = true) { moveTo(-1.06f, -0.7f); quadTo(0f, -0.82f, 1.06f, -0.7f); lineTo(1.04f, -0.46f); quadTo(0f, -0.56f, -1.04f, -0.46f) } }
        fill("#15151C", MID) { path(closed = true) { moveTo(-0.98f, -0.48f); quadTo(0f, -0.62f, 0.98f, -0.48f); quadTo(0.5f, -0.24f, 0f, -0.2f); quadTo(-0.5f, -0.24f, -0.98f, -0.48f) } }
        taper(listOf(P(-0.6f, -0.42f), P(-0.1f, -0.36f)), 0.01f, 0.04f, 0.01f, CK.WHITE, 0.6f)
        fill(CK.GOLD, FINE) { path(closed = true) { moveTo(-0.17f, -1.15f); lineTo(0.17f, -1.15f); lineTo(0.16f, -0.94f); quadTo(0f, -0.8f, -0.16f, -0.94f) } }
        spot(CK.GOLD_D) { star(0f, -1.0f, 0.08f, 0.035f, 5) }
    }
    if (Extra.FEDORA in ex) {
        val c = "#6B4F3A"
        fill(c, OUT) { ellipse(0f, -0.5f, 1.62f, 0.3f) }
        spot(CK.shade(c)) { ellipse(0.1f, -0.42f, 1.4f, 0.16f) }
        fill(c, OUT) { path(closed = true) { moveTo(-1f, -0.56f); cubicTo(-1.12f, -1.4f, -0.42f, -1.48f, 0f, -1.26f); cubicTo(0.42f, -1.48f, 1.12f, -1.4f, 1f, -0.56f); quadTo(0f, -0.48f, -1f, -0.56f) } }
        spot(CK.shade(c)) { path(closed = true) { moveTo(0.45f, -1.32f); cubicTo(0.9f, -1.3f, 1.05f, -1f, 1f, -0.56f); lineTo(0.6f, -0.53f); cubicTo(0.65f, -0.9f, 0.6f, -1.15f, 0.45f, -1.32f) } }
        fill("#2E2420", MID) { path(closed = true) { moveTo(-1.02f, -0.8f); quadTo(0f, -0.72f, 1.02f, -0.8f); lineTo(1f, -0.58f); quadTo(0f, -0.5f, -1f, -0.58f) } }
        taper(listOf(P(-0.05f, -1.25f), P(0f, -0.95f)), 0.01f, 0.04f, 0.01f, CK.deep(c))
    }
    if (Extra.CHEF_HAT in ex) {
        val c = "#FBFBF8"
        union(c, OUT, listOf({ circle(-0.6f, -1.4f, 0.46f) }, { circle(0f, -1.58f, 0.56f) }, { circle(0.6f, -1.4f, 0.46f) }, { circle(-0.3f, -1.68f, 0.44f) }, { circle(0.32f, -1.7f, 0.44f) }, { roundRect(-0.95f, -1.12f, 1.9f, 0.62f, 0.12f) }))
        fill(c, MID) { roundRect(-0.96f, -1.08f, 1.92f, 0.58f, 0.12f) }
        spot("#E3E0D6") { roundRect(0.35f, -1.06f, 0.58f, 0.54f, 0.1f) }
        for (x in listOf(-0.45f, 0f, 0.45f)) line("#C9C3B6", FINE) { line(x, -1.02f, x, -0.6f) }
        line("#C9C3B6", FINE) { arc(0f, -1.55f, 0.3f, 0.2f, 200f, 120f) }
    }
    if (Extra.WIZARD_HAT in ex) {
        val c = look.main
        fill(c, OUT) { ellipse(0f, -0.55f, 1.78f, 0.32f) }
        spot(CK.shade(c)) { ellipse(0.12f, -0.47f, 1.55f, 0.17f) }
        fill(c, OUT) {
            path(closed = true) {
                moveTo(-1.05f, -0.62f)
                cubicTo(-0.8f, -1.5f, -0.45f, -2.3f, 0.2f, -2.75f)
                cubicTo(0.6f, -2.98f, 1.1f, -2.88f, 1.42f, -2.56f)
                cubicTo(1.1f, -2.62f, 0.72f, -2.52f, 0.56f, -2.25f)
                cubicTo(0.62f, -1.6f, 0.9f, -1.0f, 1.05f, -0.62f)
                quadTo(0f, -0.5f, -1.05f, -0.62f)
            }
        }
        spot(CK.shade(c)) { path(closed = true) { moveTo(0.4f, -2.2f); cubicTo(0.5f, -1.6f, 0.8f, -1f, 1.05f, -0.62f); lineTo(0.6f, -0.58f); cubicTo(0.45f, -1.1f, 0.38f, -1.6f, 0.4f, -2.2f) } }
        fill(CK.GOLD, MID) { path(closed = true) { moveTo(-1f, -0.86f); quadTo(0f, -0.76f, 1f, -0.86f); lineTo(1.05f, -0.62f); quadTo(0f, -0.52f, -1.05f, -0.62f) } }
        fill(CK.GOLD, FINE) { star(-0.12f, -1.5f, 0.2f, 0.08f, 5) }
        fill(CK.GOLD, FINE) { star(0.4f, -2.1f, 0.13f, 0.05f, 5) }
        fill(CK.GOLD, FINE) { path(closed = true) { moveTo(-0.5f, -1.05f); arcTo(-0.42f, -1.12f, 0.13f, 0.13f, 140f, 220f); arcTo(-0.37f, -1.12f, 0.1f, 0.1f, 0f, -220f) } }
    }
}

/** Duygu işaretleri: öfke damarı, ter damlası, kara çizgiler. */
private fun Fig.overlays() {
    val f = face
    if (f.gloom) {
        spot(CK.GLOOM) { path(closed = true) { moveTo(-0.98f, 0.1f); cubicTo(-0.98f, -0.45f, -0.6f, -0.85f, 0f, -0.85f); cubicTo(0.6f, -0.85f, 0.98f, -0.45f, 0.98f, 0.1f); quadTo(0f, -0.15f, -0.98f, 0.1f) } }.alpha(0.22f)
        for (i in 0..5) {
            val x = -0.55f + i * 0.22f
            taper(listOf(P(x, -0.62f), P(x, -0.12f + (i % 2) * 0.08f)), 0.035f, 0.025f, 0.004f, CK.GLOOM, 0.85f)
        }
    }
    if (f.vein) {
        val c = P(0.6f, -0.72f)
        for (r in listOf(0f, 90f, 180f, 270f)) {
            val d = dirOf(r + 45f)
            val a = c + d * 0.08f
            val m = c + d * 0.17f + dirOf(r + 135f) * 0.05f
            val e = c + d * 0.2f + dirOf(r + 135f) * 0.13f
            taper(listOf(a, m, e), 0.04f, 0.06f, 0.02f, CK.ANGER)
        }
    }
    if (f.sweat) drop(1.12f, -0.38f, 0.12f)
}

private fun Fig.spaceHelmet() {
    b.circle(0f, -0.05f, 1.62f).fill(CK.WHITE).noStroke().alpha(0.16f)
    b.circle(0f, -0.05f, 1.62f).fill("@none").stroke(ink).width(OUT)
    taper((0..8).map { i -> val a = Math.toRadians(198.0 + i * 7.0); P(1.42f * cos(a).toFloat(), -0.05f + 1.42f * sin(a).toFloat()) }, 0.02f, 0.12f, 0.02f, CK.WHITE, 0.9f)
    taper((0..2).map { i -> val a = Math.toRadians(262.0 + i * 5.0); P(1.42f * cos(a).toFloat(), -0.05f + 1.42f * sin(a).toFloat()) }, 0.02f, 0.08f, 0.02f, CK.WHITE, 0.9f)
    spot(CK.WHITE) { ellipse(0.85f, 0.7f, 0.22f, 0.1f, -40f) }.alpha(0.35f)
}

// ------------------------------------------------------------------ robot başı

/** Robot başı: köşeli kafa, kulak modülleri, anten kanadı ve ifadeyi parlayan çizgilerle gösteren ekran. */
private fun Fig.robotHead() {
    val plate = "#D5DCE4"
    val dark = "#5B6573"
    for (sx in listOf(-1f, 1f)) {
        fill(dark, MID) { circle(sx * 1.05f, 0.05f, 0.32f) }
        fill("#F28A2E", FINE) { circle(sx * 1.05f, 0.05f, 0.14f) }
    }
    fill(plate, OUT) { poly(0.5f, -0.86f, 0.72f, -0.86f, 1.02f, -1.62f, closed = true) }
    spot(CK.GLOW) { circle(1.02f, -1.62f, 0.09f) }
    fill(plate, OUT) { roundRect(-1f, -0.98f, 2f, 1.95f, 0.42f) }
    spot(CK.shade(plate)) { path(closed = true) { moveTo(0.98f, -0.6f); lineTo(0.98f, 0.6f); quadTo(0.95f, 0.95f, 0.6f, 0.96f); lineTo(0.55f, 0.85f); quadTo(0.82f, 0.82f, 0.84f, 0.5f); lineTo(0.84f, -0.6f) } }
    fill("#F28A2E", FINE) { roundRect(-0.55f, -0.9f, 1.1f, 0.15f, 0.07f) }
    fill(dark, MID) { roundRect(-0.6f, 0.68f, 1.2f, 0.24f, 0.1f) }
    for (i in -2..2) line("#3A414C", 0.6f) { line(i * 0.18f, 0.72f, i * 0.18f, 0.88f) }
    fill(CK.SCREEN, MID) { roundRect(-0.8f, -0.62f, 1.6f, 1.24f, 0.3f) }
    spot(CK.WHITE) { roundRect(-0.7f, -0.55f, 0.5f, 0.07f, 0.035f) }.alpha(0.25f)
    robotFace()
}

/** Robot ekranındaki ifade (göz biçimi ve ağız tarifinden). */
private fun Fig.robotFace() {
    val g = CK.GLOW
    val f = face
    val ey = -0.1f
    val my = 0.32f
    val sh = pose.turn * 0.12f
    fun glowEye(x: Float, shape: EyeShape, o: Float) {
        when (shape) {
            EyeShape.HAPPY -> taper(listOf(P(x - 0.17f, ey + 0.06f), P(x, ey - 0.12f), P(x + 0.17f, ey + 0.06f)), 0.05f, 0.08f, 0.05f, g)
            EyeShape.CLOSED -> taper(listOf(P(x - 0.17f, ey - 0.03f), P(x, ey + 0.08f), P(x + 0.17f, ey - 0.03f)), 0.05f, 0.07f, 0.05f, g)
            EyeShape.SQUINT -> {
                taper(listOf(P(x - o * 0.16f, ey - 0.12f), P(x + o * 0.12f, ey)), 0.05f, 0.07f, 0.05f, g)
                taper(listOf(P(x + o * 0.12f, ey), P(x - o * 0.16f, ey + 0.12f)), 0.05f, 0.07f, 0.05f, g)
            }
            EyeShape.STAR -> spot(g) { star(x, ey, 0.2f, 0.08f, 5) }
            EyeShape.TINY -> spot(g) { circle(x, ey, 0.05f) }
            EyeShape.WIDE -> { line(g, 1.3f) { circle(x, ey, 0.17f) }; spot(g) { circle(x, ey, 0.06f) } }
            else -> {
                val h = 0.3f * (1f - max(f.lidIn, f.lidOut) * 0.5f) * (if (shape == EyeShape.HALF) 0.5f else 1f)
                spot(g) { roundRect(x - 0.1f, ey - h / 2f, 0.2f, h, 0.1f) }
                spot(CK.WHITE) { circle(x - 0.03f, ey - h * 0.25f, 0.035f) }.alpha(0.8f)
                if (f.lidIn > 0.2f) spot(CK.SCREEN) { poly(x - o * 0.14f, ey - h / 2f - 0.02f, x + o * 0.14f, ey - h / 2f - 0.02f, x - o * 0.14f, ey + 0.02f, closed = true) }
            }
        }
    }
    glowEye(-0.36f + sh, f.eye, -1f)
    glowEye(0.36f + sh, f.eyeR ?: f.eye, 1f)
    // Kaş çizgileri (öfke, üzüntü, şaşkınlık)
    if (abs(f.browIn) > 0.05f || abs(f.browOut) > 0.05f || f.browRaiseR > 0f) for (o in listOf(-1f, 1f)) {
        val x = o * 0.36f + sh
        val raise = if (o > 0) f.browRaiseR else 0f
        taper(listOf(P(x - o * 0.15f, ey - 0.24f + f.browIn * 0.6f - raise), P(x + o * 0.15f, ey - 0.26f + f.browOut * 0.6f - raise)), 0.04f, 0.05f, 0.03f, g)
    }
    val mx = sh
    when (f.mouth) {
        Mouth.SMILE -> taper(listOf(P(mx - 0.2f, my - 0.03f), P(mx, my + 0.07f), P(mx + 0.2f, my - 0.03f)), 0.04f, 0.06f, 0.04f, g)
        Mouth.LINE, Mouth.POUT -> taper(listOf(P(mx - 0.16f, my), P(mx + 0.16f, my)), 0.04f, 0.05f, 0.04f, g)
        Mouth.FROWN -> taper(listOf(P(mx - 0.18f, my + 0.05f), P(mx, my - 0.03f), P(mx + 0.18f, my + 0.05f)), 0.04f, 0.06f, 0.04f, g)
        Mouth.WAVY -> taper(listOf(P(mx - 0.2f, my), P(mx - 0.1f, my - 0.04f), P(mx, my), P(mx + 0.1f, my - 0.04f), P(mx + 0.2f, my)), 0.03f, 0.05f, 0.03f, g)
        Mouth.SMIRK -> taper(listOf(P(mx - 0.16f, my + 0.02f), P(mx + 0.06f, my + 0.03f), P(mx + 0.2f, my - 0.06f)), 0.04f, 0.05f, 0.04f, g)
        Mouth.O, Mouth.JAWDROP -> line(g, 1.3f) { ellipse(mx, my + 0.02f, 0.08f, if (f.mouth == Mouth.O) 0.08f else 0.13f) }
        Mouth.GRIT -> { line(g, 1.1f) { roundRect(mx - 0.2f, my - 0.06f, 0.4f, 0.13f, 0.03f) }; for (i in -1..1) line(g, 0.8f) { line(mx + i * 0.1f, my - 0.06f, mx + i * 0.1f, my + 0.07f) } }
        Mouth.OPEN_SMILE, Mouth.LAUGH, Mouth.SHOUT, Mouth.WAIL, Mouth.OUCH -> {
            val k = if (f.mouth == Mouth.OPEN_SMILE) 0.8f else 1f
            spot(g) { path(closed = true) { moveTo(mx - 0.22f * k, my - 0.06f); lineTo(mx + 0.22f * k, my - 0.06f); quadTo(mx + 0.2f * k, my + 0.2f * k, mx, my + 0.2f * k); quadTo(mx - 0.2f * k, my + 0.2f * k, mx - 0.22f * k, my - 0.06f) } }.alpha(0.85f)
        }
    }
    if (f.blush) for (o in listOf(-1f, 1f)) spot("#FF7AB6") { roundRect(o * 0.56f + sh - 0.09f, 0.12f, 0.18f, 0.06f, 0.03f) }
    if (f.streams) for (o in listOf(-1f, 1f)) for (i in 0..2) spot(g) { circle(o * 0.36f + sh, 0.12f + i * 0.13f, 0.035f) }
    if (f.vein) {
        val c = P(0.55f, -0.8f)
        for (r in listOf(0f, 90f, 180f, 270f)) { val d = dirOf(r + 45f); taper(listOf(c + d * 0.07f, c + d * 0.16f + dirOf(r + 135f) * 0.1f), 0.04f, 0.05f, 0.02f, CK.ANGER) }
    }
    if (f.sweat) drop(1.18f, -0.5f, 0.12f)
    if (f.gloom) for (i in 0..4) taper(listOf(P(-0.5f + i * 0.25f, -0.55f), P(-0.5f + i * 0.25f, -0.3f)), 0.03f, 0.02f, 0.004f, CK.GLOOM, 0.9f)
}
