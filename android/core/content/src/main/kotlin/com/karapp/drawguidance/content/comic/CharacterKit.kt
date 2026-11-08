package com.karapp.drawguidance.content.comic

import com.karapp.drawguidance.content.vector.P
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

/*
 * v37 karakter çizim takımı: anime / çizgi roman stili. Karakterler özgündür (hiçbir hazır karakteri taklit etmez);
 * tasarım birimi ayakta duran bir yetişkinin boyu ~196 (ayak tabanı y=196). Çizim üç katmandan oluşur:
 * - İskelet ([Skel]): poz açıları (ya da hedef noktası; ör. el çenede) ve vücut ölçülerinden eklem noktaları. Aynı poz
 *   her vücut tipinde (çocuk, kahraman, robot) çalışır; en alttaki nokta (taban, diz) zemine oturtulur.
 * - Vücut ve giysi ([Fig], CharacterFig.kt): dolgulu kapsül uzuvlar (iki geçiş: önce kalın kenar, sonra kenarsız
 *   dolgu; birleşimde iç çizgi kalmaz), cel gölgeler (kenarsız koyu ton), inceltilmiş mürekkep çizgileri.
 * - Baş ([Fig.headPart], CharacterHead.kt): büyük anime gözleri (iris, göz bebeği, parıltılar, kalın kirpik çizgisi),
 *   tutam tutam saç, ifadeye göre kaş/ağız ve duygu işaretleri.
 * Kenar kalınlıkları tasarım biriminin katıdır (uygulamada elemanın kenar kalınlığı 1 birim).
 */

/** Çene biçimi: çocuk yuvarlak, kadın yumuşak, anime erkek sivri, çizgi roman kahramanı köşeli, yaşlı. */
internal enum class Jaw { ROUND, SOFT, SHARP, SQUARE, OLD }

/**
 * Vücut ölçüleri (tasarım birimi). Yarı genişlikler gövde çizgisinindir; eklem aralıkları ([shoulderJ], [hipJ]) uzuvların
 * başladığı noktalar. [muscle] kas çizgileri (0..1), [bust] kadın göğüs çizgisi.
 */
internal class Body(
    val headR: Float, val jaw: Jaw,
    val neckLen: Float, val neckR: Float,
    val torsoLen: Float, val shoulderJ: Float, val shoulderOut: Float, val chestW: Float, val waistW: Float, val hipW: Float, val hipJ: Float,
    val upperArm: Float, val foreArm: Float, val armR0: Float, val armR1: Float, val armR2: Float, val handR: Float,
    val thigh: Float, val shin: Float, val legR0: Float, val legR1: Float, val legR2: Float, val footLen: Float,
    val muscle: Float = 0f, val female: Boolean = false, val bust: Float = 0f,
) {
    companion object {
        /** Çocuk (~3.8 baş boyu). */
        val CHILD = Body(
            18f, Jaw.ROUND, 14f, 3.6f, 33f, 11f, 15.5f, 13.5f, 12.5f, 13f, 6.5f,
            21f, 19f, 4.3f, 3.7f, 3.1f, 3.9f, 31f, 30f, 6.3f, 4.7f, 3.6f, 10f,
        )
        /** Genç kız (öğrenci). */
        val TEEN_F = Body(
            16.5f, Jaw.SOFT, 16.5f, 3.6f, 43f, 13f, 17.5f, 15f, 11.5f, 15.5f, 8f,
            28f, 26f, 4.5f, 3.7f, 2.9f, 3.9f, 43f, 42f, 7.2f, 5.0f, 3.5f, 11f,
            female = true, bust = 0.35f,
        )
        /** Yetişkin kadın. */
        val ADULT_F = Body(
            16f, Jaw.SOFT, 17f, 3.7f, 46f, 13.5f, 18.5f, 16f, 12f, 16.5f, 8.5f,
            29f, 27f, 4.6f, 3.8f, 3.0f, 4.0f, 45f, 44f, 7.6f, 5.2f, 3.6f, 11.5f,
            female = true, bust = 0.6f,
        )
        /** Yetişkin erkek (~5.6 baş boyu). */
        val ADULT_M = Body(
            16.5f, Jaw.SHARP, 17.5f, 4.4f, 50f, 16.5f, 22.5f, 19f, 15f, 15.5f, 8f,
            32f, 30f, 5.4f, 4.4f, 3.4f, 4.6f, 46f, 45f, 8.2f, 5.9f, 4.2f, 13f,
        )
        /** Sporcu / polis / şövalye: geniş omuz, kaslı. */
        val ATHLETIC = Body(
            16f, Jaw.SQUARE, 17.5f, 4.8f, 52f, 19f, 26f, 22f, 15f, 16f, 8.5f,
            32f, 30f, 6.4f, 5.0f, 3.8f, 5.0f, 47f, 46f, 8.8f, 6.4f, 4.4f, 13.5f,
            muscle = 0.6f,
        )
        /** Çizgi roman kahramanı (~6 baş boyu, V gövde). */
        val HEROIC = Body(
            15.5f, Jaw.SQUARE, 17f, 5.2f, 56f, 22f, 30f, 26f, 15.5f, 17f, 9f,
            34f, 31f, 7.6f, 6.0f, 4.4f, 5.4f, 50f, 48f, 9.6f, 7.0f, 4.8f, 14f,
            muscle = 1f,
        )
        /** Yaşlı (biraz kısa, göbekli). */
        val ELDER = Body(
            16.5f, Jaw.OLD, 15f, 4.2f, 46f, 15f, 20.5f, 18f, 17f, 16.5f, 8f,
            29f, 27f, 5.0f, 4.2f, 3.3f, 4.4f, 42f, 41f, 7.6f, 5.6f, 4.0f, 12.5f,
        )
        /** Büyücü (uzun yaşlı). */
        val SAGE = Body(
            16.5f, Jaw.OLD, 16f, 4.2f, 50f, 15.5f, 21f, 18f, 15.5f, 16f, 8f,
            31f, 29f, 5.0f, 4.2f, 3.3f, 4.4f, 45f, 44f, 7.6f, 5.6f, 4.0f, 12.5f,
        )
        /** Robot (mekanik, kalın uzuvlar). */
        val MECHA = Body(
            16f, Jaw.SQUARE, 19f, 4.6f, 52f, 21f, 28f, 23f, 14f, 17f, 9f,
            31f, 30f, 5.0f, 5.6f, 5.4f, 5.8f, 46f, 46f, 7.0f, 7.4f, 6.6f, 15f,
            muscle = 0f,
        )
    }
}

/** Saç biçimi (her türe özgü). */
internal enum class Hair { SPIKY_KID, PONYTAIL, PARTED, TUFTS, BUN, WAVY, UNDER_CAP, FORELOCK, CROPPED, SPIKY_UP, SWEPT_BACK, BOB, KNIGHT, LONG, NONE }

/** Göz çizimi: kadın (uzun kirpik), erkek, çocuk (iri, yuvarlak), çizgi roman (keskin, küçük iris), yaşlı. */
internal enum class EyeStyle { FEMALE, MALE, KID, COMIC, ELDER, SCREEN }

/** Üst giysi (her tür kendi kıyafetini giyer). */
internal enum class Outfit { TEE, SAILOR, SUIT, CARDIGAN, BLOUSE, LABCOAT, UNIFORM, TRENCH, CHEF, JERSEY, HEROSUIT, SPACESUIT, MECHA, ARMOR, ROBE }

/** Alt giysi. */
internal enum class Legwear { PANTS, SHORTS, SKIRT, PENCIL, TIGHTS, MECHA, ROBE }

/** Ayakkabı. */
internal enum class Shoe { SNEAKER, SHOE, LOAFER, BOOT, HERO_BOOT, SPACE, MECHA, SABATON }

/** Baş, yüz ve gövde eklentileri. */
internal enum class Extra {
    GLASSES, ROUND_GLASSES, MUSTACHE, CHEF_MUSTACHE, BEARD, TIE, STETHOSCOPE, BADGE, POLICE_CAP, FEDORA, CHEF_HAT,
    HEADBAND, CAPE, MASK, SPACE_HELMET, BACKPACK, WIZARD_HAT, NECKERCHIEF, SCARF, BANDAID, FRECKLES, RIBBON, HAIRCLIP,
    WRISTBANDS, APRON,
}

/** Elde tutulan eşya. */
internal enum class Prop { SWORD, STAFF, CANE, BRIEFCASE, BOOK, POINTER, CLIPBOARD, BATON, MAGNIFIER, LADLE, PAN, BALL, BAG }

/** Pozun eşya yuvası: sakin (ayakta, yürürken), aksiyon (saldırı, zafer), işaret (gösterirken). */
internal enum class PropSlot { CALM, ACTION, POINTER }

/**
 * Karakterin görünüşü. Renkler: [main] üst giysi, [second] alt giysi, [accent] vurgu (kurdele, kravat, pelerin),
 * [trim] süs/şerit. [props] poz yuvasına göre elde tutulan eşya.
 */
internal class Look(
    val body: Body,
    val skin: String,
    val hair: Hair,
    val hairColor: String,
    val eyeColor: String,
    val eyes: EyeStyle,
    val outfit: Outfit,
    val main: String,
    val second: String,
    val legwear: Legwear,
    val shoe: Shoe,
    val shoeColor: String,
    val extras: Set<Extra> = emptySet(),
    val accent: String = "#E0483C",
    val trim: String = "#F4F1EA",
    val props: Map<PropSlot, Prop> = emptyMap(),
    /** Aura ve büyü rengi (güç toplarken). */
    val aura: String = "#7FD8FF",
)

// ------------------------------------------------------------------ poz

/** Elin biçimi. */
internal enum class Hand { RELAXED, FIST, OPEN, POINT, CLAW, VSIGN, FLAT, HIDDEN }

/** Kolun çizim katmanı: gövdenin arkası, gövdenin önü, başın önü. */
internal enum class Layer { BACK, MID, TOP }

/** Kol hedefi (el bu noktaya uzanır; dirsek ters kinematikle bulunur). */
internal enum class Mark { CHIN, MOUTH, EYE_L, EYE_R, BROW_R, HEAD_TOP, CHEST, BELLY, HIP_L, HIP_R, KNEE_L, KNEE_R, NAPE }

/**
 * Kol: [upper] ve [lower] mutlak açılar (derece; 0 aşağı, 90 ekranın sağı, 180 yukarı, -90 sol). [reach] verilirse
 * el o noktaya ([dx],[dy] kaydırmasıyla) uzanır, dirsek [bend] yönünde (+1 saat yönü) kırılır.
 */
internal class ArmSpec(
    val upper: Float, val lower: Float, val reach: Mark? = null, val dx: Float = 0f, val dy: Float = 0f, val bend: Float = 1f,
    /** Pazının görünen boyu (öne uzanan kol kısalır; ör. el çenede). */
    val upperK: Float = 1f,
)

/** Bacak: uyluk ve baldır mutlak açıları (kol ile aynı açı düzeni). */
internal class LegSpec(val thigh: Float, val shin: Float)

/** Hareket ve duygu efektleri (pozla gelir). */
internal enum class PoseFx {
    SPEED, DUST, IMPACT_HAND, IMPACT_FOOT, SWISH_KICK, SWISH_ARM, SHOUT, SPARKLE, AURA, WIND, JUMP_LINES, DIZZY, PAIN,
    SWEAT, LAUGH_MARKS, THOUGHT, WAVE_ARCS, BLOCK_ARCS, FALL_LINES, POINT_LINES, SOB, TREMBLE, BRAWL, MAGIC,
}

/** Pozun yüze etkisi (ağız/göz ifadenin üstüne yazılır). */
internal enum class FaceMod { NONE, SHOUT, LAUGH, CRY, EFFORT, FALL, HURT, WINK }

/**
 * Poz tanımı (tablo verisi; bkz. ComicCharacters.poses). [lean] gövde eğimi (+ üst gövde sağa), [headTilt] başın ek
 * eğimi, [turn] baş ve gövdenin dönüşü (-1 sol .. 1 sağ; yüz o yana bakar), [tilt] bütün vücut (düşerken), [lift]
 * zeminden yükseklik (zıplama, uçma). [crossed] kollar göğüste kavuşturulmuş, [seat] tabureye oturmuş.
 */
internal class PoseSpec(
    val armL: ArmSpec = ArmSpec(-9f, -4f),
    val armR: ArmSpec = ArmSpec(9f, 4f),
    val legL: LegSpec = LegSpec(-4f, -2f),
    val legR: LegSpec = LegSpec(4f, 2f),
    val lean: Float = 0f,
    val headTilt: Float = 0f,
    val turn: Float = 0f,
    val handL: Hand = Hand.RELAXED,
    val handR: Hand = Hand.RELAXED,
    val layerL: Layer = Layer.MID,
    val layerR: Layer = Layer.MID,
    val lift: Float = 0f,
    val tilt: Float = 0f,
    val seat: Boolean = false,
    val crossed: Boolean = false,
    val fx: Set<PoseFx> = emptySet(),
    val mod: FaceMod = FaceMod.NONE,
    val prop: PropSlot? = null,
    /** Eşyayı tutan el: true sağ (ekranın sağındaki) el. */
    val propRight: Boolean = true,
    /** Ayak açıları (null: zemindeyse düz, havadaysa baldır yönünde). */
    val footL: Float? = null,
    val footR: Float? = null,
)

// ------------------------------------------------------------------ yüz ifadesi

/** Göz biçimi. */
internal enum class EyeShape { OPEN, WIDE, HAPPY, CLOSED, SQUINT, STAR, TINY, HALF }

/** Ağız biçimi. */
internal enum class Mouth { SMILE, LINE, OPEN_SMILE, FROWN, O, WAVY, GRIT, SHOUT, SMIRK, LAUGH, WAIL, JAWDROP, OUCH, POUT }

/**
 * İfadenin çizim tarifi. Kaş: [browIn]/[browOut] iç ve dış ucun kayması (eksi yukarı, baş yarıçapı biriminde).
 * Göz kapağı: [lidIn]/[lidOut] üst kapağın iç/dış uçta inişi (0..1), [lowerLid] alt kapağın kalkması. [gaze] bakış
 * yönü (x, y; iris kayması), [iris] iris ölçeği.
 */
internal data class FaceSpec(
    val eye: EyeShape = EyeShape.OPEN,
    val browIn: Float = 0f,
    val browOut: Float = 0f,
    val lidIn: Float = 0f,
    val lidOut: Float = 0f,
    val lowerLid: Float = 0f,
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val iris: Float = 1f,
    val mouth: Mouth = Mouth.SMILE,
    val blush: Boolean = false,
    val vein: Boolean = false,
    val sweat: Boolean = false,
    val gloom: Boolean = false,
    val tear: Boolean = false,
    val sparkle: Boolean = false,
    /** Tek kaş kalkık (düşünceli, alaycı). */
    val browRaiseR: Float = 0f,
    /** Sağ gözün ayrı biçimi (göz kırpma, yaralıyken kısılmış göz). */
    val eyeR: EyeShape? = null,
    /** Şelale gözyaşı (ağlarken). */
    val streams: Boolean = false,
    /** Yanakta yara bandı (yaralı). */
    val bandage: Boolean = false,
) {
    /** Pozun yüze etkisi: ağız ve gözler ifadenin üstüne yazılır (kaşlar ifadede kalır). */
    fun with(mod: FaceMod): FaceSpec {
        val cheerful = mouth == Mouth.OPEN_SMILE || mouth == Mouth.LAUGH || mouth == Mouth.SMILE || eye == EyeShape.STAR
        return when (mod) {
            FaceMod.NONE -> this
            FaceMod.SHOUT -> copy(mouth = if (cheerful) Mouth.LAUGH else Mouth.SHOUT)
            FaceMod.LAUGH -> copy(
                eye = if (eye == EyeShape.TINY || eye == EyeShape.WIDE) eye else EyeShape.HAPPY,
                mouth = Mouth.LAUGH, tear = true, lidIn = 0f, lidOut = 0f,
            )
            FaceMod.CRY -> copy(streams = true, mouth = if (cheerful) Mouth.OPEN_SMILE else Mouth.WAIL, tear = false)
            FaceMod.EFFORT -> copy(mouth = if (mouth == Mouth.SHOUT || mouth == Mouth.LAUGH) mouth else Mouth.GRIT, sweat = true)
            FaceMod.FALL -> copy(eye = EyeShape.SQUINT, eyeR = null, mouth = Mouth.OUCH, sweat = true, sparkle = false)
            FaceMod.HURT -> copy(eyeR = EyeShape.SQUINT, bandage = true, sweat = true, mouth = if (cheerful) Mouth.SMIRK else Mouth.GRIT)
            FaceMod.WINK -> copy(eyeR = if (eye == EyeShape.OPEN || eye == EyeShape.STAR || eye == EyeShape.HALF) EyeShape.HAPPY else eyeR)
        }
    }
}

// ------------------------------------------------------------------ renk

internal object CK {
    const val INK = "#1D1A26"
    const val WHITE = "#FFFFFF"
    const val EYE_WHITE = "#FCFCFF"
    const val PUPIL = "#16121F"
    const val LASH = "#221A28"
    const val MOUTH = "#6E1F2C"
    const val TONGUE = "#E8707E"
    const val TEETH = "#FFFFFF"
    const val BLUSH = "#F27A8C"
    const val TEAR = "#7FD0FF"
    const val STEEL = "#C9D2DC"
    const val STEEL_D = "#7E8896"
    const val SCREEN = "#16202E"
    const val GLOW = "#53E0FF"
    const val GOLD = "#F2C14E"
    const val GOLD_D = "#B7862A"
    const val RED = "#E0483C"
    const val ANGER = "#E23A3A"
    const val GLOOM = "#6E66B8"

    private fun rgb(h: String) = h.removePrefix("#").take(6).toInt(16).let { intArrayOf(it shr 16 and 255, it shr 8 and 255, it and 255) }

    fun mix(a: String, b: String, t: Float): String {
        val x = rgb(a); val y = rgb(b)
        return String.format(
            java.util.Locale.ROOT, "#%02X%02X%02X",
            (x[0] + (y[0] - x[0]) * t).toInt().coerceIn(0, 255), (x[1] + (y[1] - x[1]) * t).toInt().coerceIn(0, 255), (x[2] + (y[2] - x[2]) * t).toInt().coerceIn(0, 255),
        )
    }

    /** Cel gölge tonu (soğuk mor-lacivert karışım). */
    fun shade(hex: String) = mix(hex, "#2A1F48", 0.3f)
    /** Daha koyu gölge (saçın alt katmanı, kıvrım). */
    fun deep(hex: String) = mix(hex, "#1A1430", 0.5f)
    fun light(hex: String) = mix(hex, "#FFFFFF", 0.38f)
    /** Ten gölgesi (sıcak, pembemsi). */
    fun skinShade(hex: String) = mix(hex, "#B04A5C", 0.3f)
}

// ------------------------------------------------------------------ geometri

/** Açıdan birim yön: 0 aşağı, 90 sağ, 180 yukarı. */
internal fun dirOf(deg: Float): P {
    val r = Math.toRadians(deg.toDouble())
    return P(sin(r).toFloat(), cos(r).toFloat())
}

/** [dirOf] tersi. */
internal fun angleOf(v: P): Float = Math.toDegrees(atan2(v.x.toDouble(), v.y.toDouble())).toFloat()

/** Vektörü ekranda saat yönünde [deg] derece döndürür ([com.karapp.drawguidance.content.vector.Affine.rotate] ile aynı). */
internal fun rotV(v: P, deg: Float): P {
    val r = Math.toRadians(deg.toDouble())
    val c = cos(r).toFloat(); val s = sin(r).toFloat()
    return P(c * v.x - s * v.y, s * v.x + c * v.y)
}

internal fun dist(a: P, b: P) = hypot(b.x - a.x, b.y - a.y)
internal fun lerpP(a: P, b: P, t: Float) = P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** İki kemikli ters kinematik: omuzdan [t] hedefine; dirsek ve el (hedef erişilmezse kol düz uzanır). */
internal fun ik(s: P, t: P, a: Float, b: Float, bend: Float): Pair<P, P> {
    val d = t - s
    val len0 = max(1e-3f, hypot(d.x, d.y))
    val u = P(d.x / len0, d.y / len0)
    val len = len0.coerceIn(abs(a - b) + 0.5f, a + b - 0.05f)
    val cosA = ((a * a + len * len - b * b) / (2f * a * len)).coerceIn(-1f, 1f)
    val ang = Math.toDegrees(acos(cosA).toDouble()).toFloat()
    val elbow = s + rotV(u, ang * sign(bend)) * a
    return elbow to (s + u * len)
}

/**
 * Pozun eklem noktaları (tasarım biriminde, zemine oturtulmuş). Önce leğen (60, 0) alınır; gövde, baş ve uzuvlar
 * açılardan (ya da hedeften) bulunur; sonra bütün vücut [PoseSpec.tilt] kadar döndürüldüğünde en alttaki nokta
 * y = 196 − lift olacak biçimde kaydırılır.
 */
internal class Skel(val body: Body, val pose: PoseSpec) {
    val headAngle = pose.lean + pose.headTilt
    /** Gövde dönüşü (baş tam döner, gövde daha az). */
    val bodyTurn = pose.turn * 0.6f
    val widthK = 1f - 0.22f * abs(bodyTurn)
    val ground = 196f - pose.lift

    val pelvis: P; val chest: P; val neckTop: P; val head: P
    val shoulderL: P; val shoulderR: P; val elbowL: P; val elbowR: P; val handL: P; val handR: P
    val hipL: P; val hipR: P; val kneeL: P; val kneeR: P; val ankleL: P; val ankleR: P

    init {
        val b = body
        val p0 = P(60f, 0f)
        val ch = p0 + rotV(P(bodyTurn * 2f, -b.torsoLen), pose.lean)
        val sL = ch + rotV(P(-b.shoulderJ * widthK, 0f), pose.lean)
        val sR = ch + rotV(P(b.shoulderJ * widthK, 0f), pose.lean)
        val nt = ch + rotV(P(0f, -b.neckLen), pose.lean)
        val hd = nt + rotV(P(0f, -0.72f * b.headR), headAngle)
        val hipTilt = pose.lean * 0.3f
        val hL = p0 + rotV(P(-b.hipJ * widthK, 2f), hipTilt)
        val hR = p0 + rotV(P(b.hipJ * widthK, 2f), hipTilt)
        val kL = hL + dirOf(pose.legL.thigh) * b.thigh
        val kR = hR + dirOf(pose.legR.thigh) * b.thigh
        val aL = kL + dirOf(pose.legL.shin) * b.shin
        val aR = kR + dirOf(pose.legR.shin) * b.shin

        // Kol hedefleri (ham koordinatlarda)
        fun headPt(x: Float, y: Float) = hd + rotV(P(x * b.headR, y * b.headR), headAngle)
        fun torsoPt(x: Float, y: Float) = p0 + rotV(P(x, y), pose.lean)
        fun mark(m: Mark): P = when (m) {
            Mark.CHIN -> headPt(pose.turn * 0.12f, 1.12f)
            Mark.MOUTH -> headPt(pose.turn * 0.15f, 0.8f)
            Mark.EYE_L -> headPt(-0.42f + pose.turn * 0.15f, 0.22f)
            Mark.EYE_R -> headPt(0.42f + pose.turn * 0.15f, 0.22f)
            Mark.BROW_R -> headPt(0.55f, -0.2f)
            Mark.HEAD_TOP -> headPt(0f, -1.05f)
            Mark.NAPE -> headPt(0f, 0.2f)
            Mark.CHEST -> torsoPt(0f, -b.torsoLen * 0.62f)
            Mark.BELLY -> torsoPt(0f, -b.torsoLen * 0.3f)
            Mark.HIP_L -> torsoPt(-b.waistW * widthK - 1f, -b.torsoLen * 0.2f)
            Mark.HIP_R -> torsoPt(b.waistW * widthK + 1f, -b.torsoLen * 0.2f)
            Mark.KNEE_L -> kL + P(0f, -b.legR1 - 2f)
            Mark.KNEE_R -> kR + P(0f, -b.legR1 - 2f)
        }
        fun arm(s: P, spec: ArmSpec): Pair<P, P> {
            val target = spec.reach
            return if (target != null) ik(s, mark(target) + P(spec.dx, spec.dy), b.upperArm * spec.upperK, b.foreArm, spec.bend)
            else {
                val e = s + dirOf(spec.upper) * (b.upperArm * spec.upperK)
                e to (e + dirOf(spec.lower) * b.foreArm)
            }
        }
        val (eL, wL) = arm(sL, pose.armL)
        val (eR, wR) = arm(sR, pose.armR)

        // Zemin: tabanlar, dizler (diz çökme) ve eller (yere dayanma); bütün vücut dönüşü hesaba katılır
        val sole = b.legR2 + 2.4f
        val candidates = listOf(
            aL + P(0f, sole), aR + P(0f, sole), kL + P(0f, b.legR1), kR + P(0f, b.legR1), p0 + P(0f, b.legR0),
            wL + P(0f, b.handR * 1.6f), wR + P(0f, b.handR * 1.6f),
        )
        var lowest = -Float.MAX_VALUE
        for (c in candidates) lowest = max(lowest, (p0 + rotV(c - p0, pose.tilt)).y)
        val sh = P(0f, ground - lowest)
        pelvis = p0 + sh; chest = ch + sh; neckTop = nt + sh; head = hd + sh
        shoulderL = sL + sh; shoulderR = sR + sh; elbowL = eL + sh; elbowR = eR + sh; handL = wL + sh; handR = wR + sh
        hipL = hL + sh; hipR = hR + sh; kneeL = kL + sh; kneeR = kR + sh; ankleL = aL + sh; ankleR = aR + sh
    }
}
