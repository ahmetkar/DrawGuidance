package com.karapp.drawguidance.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * v37 çocuk modunun maskotu Boyacık: bereli, yuvarlak, turuncu küçük bir ressam (özgün karakter; hiçbir bilinen
 * karaktere benzemez). Compose Canvas ile ışık (sol üst), gölge, kenar ışığı ve parlamalarla oyuncak gibi üç boyutlu
 * çizilir. Her konuşmada başka bir yüz ifadesine ([MascotFace]) ve el hareketine ([MascotGesture]) bürünür; balonda yazı
 * yazılırken ağzı açılıp kapanır, boştayken göz kırpar ve hafifçe zıplar. Çizim 120×120 birimlik bir kutuda yapılır.
 */

/** Gözlerin biçimi. */
enum class MascotEyes { OPEN, WIDE, HAPPY, WINK, SPARKLE }

/** Kaşların duruşu: [lift] yukarı kalkma, [tilt] dış ucun kalkması (sol ve sağ kaş). */
enum class MascotBrows(val leftLift: Float, val leftTilt: Float, val rightLift: Float, val rightTilt: Float) {
    NORMAL(0f, 0f, 0f, 0f),
    RAISED(-3.2f, 0.4f, -3.2f, 0.4f),
    ONE_UP(0.6f, -0.6f, -4.2f, 1.6f),
    PROUD(-1.2f, 1.4f, -1.2f, 1.4f),
}

/** Ağzın biçimi (konuşurken bununla açık ağız arasında gidip gelir). */
enum class MascotMouth { SMILE, GRIN, O, SMALL, SMIRK }

/**
 * Yüz ifadeleri tablosu: göz, kaş, ağız, bakış yönü ([gazeX], [gazeY]: -1..1) ve yanak kızarıklığı. Yeni bir ifade
 * yalnız buraya bir satır eklemekle olur.
 */
enum class MascotFace(
    val eyes: MascotEyes,
    val brows: MascotBrows,
    val mouth: MascotMouth,
    val gazeX: Float,
    val gazeY: Float,
    val blush: Float,
) {
    HAPPY(MascotEyes.OPEN, MascotBrows.NORMAL, MascotMouth.SMILE, 0f, 0.1f, 0.55f),
    SURPRISED(MascotEyes.WIDE, MascotBrows.RAISED, MascotMouth.O, 0f, -0.3f, 0.35f),
    THINKING(MascotEyes.OPEN, MascotBrows.ONE_UP, MascotMouth.SMALL, 0.7f, -0.8f, 0.3f),
    PROUD(MascotEyes.HAPPY, MascotBrows.PROUD, MascotMouth.GRIN, 0f, 0f, 0.75f),
    WINKING(MascotEyes.WINK, MascotBrows.NORMAL, MascotMouth.SMIRK, 0.25f, 0f, 0.6f),
    EXPLAINING(MascotEyes.OPEN, MascotBrows.NORMAL, MascotMouth.SMILE, -0.45f, -0.7f, 0.45f),
    EXCITED(MascotEyes.SPARKLE, MascotBrows.RAISED, MascotMouth.GRIN, 0f, 0f, 0.8f),
}

/** El hareketleri: el sallama, gösterme, başparmak, fırça tutma, sevinç, düşünme, dinlenme. */
enum class MascotGesture { REST, WAVE, POINT, THUMBS_UP, BRUSH, CHEER, THINK }

/** Maskotun bir konuşmadaki görünüşü. */
data class MascotLook(val face: MascotFace, val gesture: MascotGesture) {
    companion object {
        val Idle = MascotLook(MascotFace.HAPPY, MascotGesture.REST)
    }
}

/** Maskotun o anki duruşu (çizim için): görünüş, ağız açık mı, göz kırpma (0..1), zıplama (0..1), el sallama (-1..1). */
data class MascotPose(
    val look: MascotLook,
    val mouthOpen: Boolean = false,
    val blink: Float = 0f,
    val bounce: Float = 0f,
    val swing: Float = 0f,
)

/** Testler ve erişilebilirlik için maskotun durumu: "YÜZ/EL/open|closed". */
val MascotStateKey = SemanticsPropertyKey<String>("MascotState")
var SemanticsPropertyReceiver.mascotState by MascotStateKey

fun MascotPose.describe(): String = "${look.face.name}/${look.gesture.name}/${if (mouthOpen) "open" else "closed"}"

/**
 * Maskotun kendisi. [mouthOpen] konuşurken dışarıdan verilir (yazı yazıldıkça açılıp kapanır). [faceOnly] küçük düğmede
 * yalnız yüz ve bere (yakın plan). [animate] kapalıyken (ör. ekran görüntüsünde) hareketsiz durur.
 */
@Composable
fun MascotFigure(
    look: MascotLook,
    modifier: Modifier = Modifier,
    mouthOpen: Boolean = false,
    faceOnly: Boolean = false,
    animate: Boolean = true,
) {
    val t = rememberInfiniteTransition(label = "mascot")
    val bounce by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bounce",
    )
    val blink by t.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 3800; 0f at 0; 0f at 3420; 1f at 3520; 1f at 3580; 0f at 3700 }),
        label = "blink",
    )
    val swing by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "swing")
    val pose = if (animate) MascotPose(look, mouthOpen, blink, bounce, swing) else MascotPose(look, mouthOpen)
    val state = pose.describe()
    Canvas(modifier.semantics { mascotState = state }) {
        val s = minOf(size.width, size.height) / 120f
        withTransform({
            translate((size.width - 120f * s) / 2f, (size.height - 120f * s) / 2f)
            scale(s, s, Offset.Zero)
            // Yakın plan: yüzün ortası (60, 62) kutunun ortasına, 1,7 kat
            if (faceOnly) scale(1.7f, 1.7f, Offset(60f, 66f))
        }) {
            drawMascot(pose)
        }
    }
}

// ------------------------------------------------------------------------------------------------ renkler

private object Boya {
    val bodyLight = Color(0xFFFFEDB0)
    val body = Color(0xFFFFBF3C)
    val bodyShade = Color(0xFFF38D1F)
    val bodyDeep = Color(0xFFCF5F0C)
    val belly = Color(0xFFFFF6DE)
    val beretLight = Color(0xFFD4C5FF)
    val beret = Color(0xFF8B5CF6)
    val beretDark = Color(0xFF4C1D95)
    val irisLight = Color(0xFF8A7CF5)
    val iris = Color(0xFF4637B4)
    val irisDark = Color(0xFF1C1552)
    val pupil = Color(0xFF0F0A29)
    val scleraShade = Color(0xFFD9E1EF)
    val line = Color(0xFF5B2A0B)
    val brow = Color(0xFF8C4513)
    val mouth = Color(0xFF7A2232)
    val tongue = Color(0xFFFF8DA2)
    val cheek = Color(0xFFFF6F91)
    val wood = Color(0xFFB7652A)
    val woodLight = Color(0xFFE3A35F)
    val metal = Color(0xFFB8C0CC)
    val paint = Color(0xFF3B82F6)
    val sparkle = Color(0xFFFFC928)
}

private val White = Color.White
private val Clear = Color.Transparent

// ------------------------------------------------------------------------------------------------ çizim

/** Maskotu 120×120 birimlik kutuya çizer (dönüşümü çağıran kurar). */
fun DrawScope.drawMascot(pose: MascotPose) {
    val face = pose.look.face
    // Zıplama: hafifçe yükselir, inerken azıcık basılır (oyuncak gibi esneklik)
    val lift = -1.8f * pose.bounce
    val squash = 0.014f * (1f - pose.bounce)
    groundShadow(1f - 0.12f * pose.bounce)
    withTransform({
        translate(0f, lift)
        scale(1f + squash, 1f - squash, Offset(60f, 108f))
    }) {
        feet()
        body()
        beret()
        cheeks(face.blush)
        val blink = if (face.eyes == MascotEyes.HAPPY) 0f else pose.blink
        eyes(face, blink)
        brows(face.brows)
        mouth(face.mouth, pose.mouthOpen)
        arms(pose.look.gesture, pose.swing)
        if (face == MascotFace.EXCITED || pose.look.gesture == MascotGesture.CHEER) sparkles()
    }
}

private fun DrawScope.groundShadow(strength: Float) {
    withTransform({ scale(1f, 0.2f, Offset(60f, 113f)) }) {
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.28f * strength), Clear), center = Offset(60f, 113f), radius = 38f),
            38f, Offset(60f, 113f),
        )
    }
}

private fun DrawScope.feet() {
    for (x in floatArrayOf(44f, 76f)) {
        val tl = Offset(x - 11f, 100f)
        drawOval(Boya.bodyDeep.copy(alpha = 0.6f), tl - Offset(0.8f, 0.8f), Size(23.6f, 13.6f))
        drawOval(Brush.verticalGradient(listOf(Boya.body, Boya.bodyShade), startY = 100f, endY = 112f), tl, Size(22f, 12f))
        drawOval(White.copy(alpha = 0.35f), tl + Offset(4f, 1.6f), Size(9f, 3.2f))
    }
}

private val BODY = Rect(24f, 34f, 96f, 108f)

private fun DrawScope.body() {
    // İnce koyu kenar (beyaz kâğıtta da okunsun)
    drawOval(Boya.bodyDeep.copy(alpha = 0.55f), BODY.topLeft - Offset(1f, 1f), Size(BODY.width + 2f, BODY.height + 2f))
    // Işık sol üstten: açık → ana renk → gölge → koyu kenar
    drawOval(
        Brush.radialGradient(
            0f to Boya.bodyLight, 0.4f to Boya.body, 0.8f to Boya.bodyShade, 1f to Boya.bodyDeep,
            center = Offset(46f, 52f), radius = 66f,
        ),
        BODY.topLeft, BODY.size,
    )
    // Alt kısımda ortam gölgesi
    drawOval(
        Brush.verticalGradient(0.62f to Clear, 1f to Boya.bodyDeep.copy(alpha = 0.32f), startY = BODY.top, endY = BODY.bottom),
        BODY.topLeft, BODY.size,
    )
    // Karın: daha açık, yumuşak kenarlı
    drawOval(
        Brush.radialGradient(listOf(Boya.belly.copy(alpha = 0.9f), Boya.belly.copy(alpha = 0f)), center = Offset(60f, 95f), radius = 21f),
        Offset(40f, 83f), Size(40f, 24f),
    )
    // Sağ alt kenarda yansıyan ışık (kenar ışığı)
    drawArc(
        White.copy(alpha = 0.32f), 8f, 72f, false, BODY.topLeft + Offset(3f, 3f), Size(BODY.width - 6f, BODY.height - 6f),
        style = Stroke(2.4f, cap = StrokeCap.Round),
    )
    // Parlama
    rotate(-32f, Offset(40f, 47f)) {
        drawOval(
            Brush.radialGradient(listOf(White.copy(alpha = 0.9f), White.copy(alpha = 0f)), center = Offset(40f, 47f), radius = 10f),
            Offset(30f, 42f), Size(20f, 10f),
        )
    }
    drawCircle(White.copy(alpha = 0.85f), 1.7f, Offset(51f, 41.5f))
}

private fun DrawScope.beret() {
    // Berenin başa düşen gölgesi
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.22f), Clear), center = Offset(61f, 42f), radius = 26f),
        Offset(35f, 37.5f), Size(52f, 9f),
    )
    rotate(-9f, Offset(60f, 34f)) {
        drawOval(Boya.beretDark.copy(alpha = 0.75f), Offset(30f, 24.2f), Size(60f, 20.6f))
        drawOval(
            Brush.radialGradient(0f to Boya.beretLight, 0.45f to Boya.beret, 1f to Boya.beretDark, center = Offset(49f, 28f), radius = 38f),
            Offset(31f, 25f), Size(58f, 19f),
        )
        // Kenar bandı
        drawArc(Boya.beretDark.copy(alpha = 0.55f), 18f, 144f, false, Offset(33f, 27f), Size(54f, 16f), style = Stroke(1.5f))
        // Sap
        drawCircle(Boya.beretDark, 4.3f, Offset(61f, 23.2f))
        drawCircle(
            Brush.radialGradient(listOf(Boya.beretLight, Boya.beret, Boya.beretDark), center = Offset(59.8f, 21.8f), radius = 5f),
            3.6f, Offset(61f, 23.2f),
        )
        // Boya damlaları (ressam beresi)
        drawCircle(Color(0xFFEF4444), 2.5f, Offset(45f, 32.5f))
        drawCircle(Color(0xFF22C55E), 2.1f, Offset(72f, 35f))
        drawCircle(Color(0xFF38BDF8), 1.8f, Offset(57.5f, 30f))
        for (d in listOf(Offset(44.2f, 31.6f), Offset(71.4f, 34.3f), Offset(57f, 29.4f))) drawCircle(White.copy(alpha = 0.7f), 0.7f, d)
        // Parlama
        drawOval(White.copy(alpha = 0.5f), Offset(39f, 27.2f), Size(13f, 4f))
    }
}

private fun DrawScope.cheeks(blush: Float) {
    for (c in listOf(Offset(35f, 75f), Offset(85f, 75f))) {
        drawCircle(Brush.radialGradient(listOf(Boya.cheek.copy(alpha = blush), Clear), center = c, radius = 8.5f), 8.5f, c)
    }
}

private val EYE_L = Offset(46f, 62f)
private val EYE_R = Offset(74f, 62f)

private fun DrawScope.eyes(face: MascotFace, blink: Float) {
    val gaze = Offset(face.gazeX, face.gazeY)
    when (face.eyes) {
        MascotEyes.HAPPY -> { closedHappy(EYE_L); closedHappy(EYE_R) }
        MascotEyes.WINK -> { openEye(EYE_L, gaze, blink, wide = false, sparkle = false); closedHappy(EYE_R) }
        MascotEyes.WIDE -> { openEye(EYE_L, gaze, blink, wide = true, sparkle = false); openEye(EYE_R, gaze, blink, wide = true, sparkle = false) }
        MascotEyes.SPARKLE -> { openEye(EYE_L, gaze, blink, wide = false, sparkle = true); openEye(EYE_R, gaze, blink, wide = false, sparkle = true) }
        MascotEyes.OPEN -> { openEye(EYE_L, gaze, blink, wide = false, sparkle = false); openEye(EYE_R, gaze, blink, wide = false, sparkle = false) }
    }
}

/** Mutlulukla kapanmış göz (^). */
private fun DrawScope.closedHappy(c: Offset) {
    val p = Path().apply { moveTo(c.x - 7.5f, c.y + 2.5f); quadraticTo(c.x, c.y - 7.5f, c.x + 7.5f, c.y + 2.5f) }
    drawPath(p, Boya.line, style = Stroke(3f, cap = StrokeCap.Round))
}

private fun DrawScope.openEye(c: Offset, gaze: Offset, blink: Float, wide: Boolean, sparkle: Boolean) {
    if (blink > 0.82f) {
        // Kırpılmış göz: aşağı kıvrık kapak çizgisi
        val p = Path().apply { moveTo(c.x - 7.5f, c.y + 1f); quadraticTo(c.x, c.y + 5.5f, c.x + 7.5f, c.y + 1f) }
        drawPath(p, Boya.line, style = Stroke(2.6f, cap = StrokeCap.Round))
        return
    }
    val rx = if (wide) 9.8f else 8.6f
    val ry = if (wide) 11.6f else 10.2f
    val ir = if (wide) 6.1f else 6.2f
    withTransform({ scale(1f, (1f - blink).coerceAtLeast(0.1f), c) }) {
        drawOval(Boya.line.copy(alpha = 0.7f), c - Offset(rx + 1f, ry + 1f), Size(2 * rx + 2f, 2 * ry + 2f))
        val sclera = Rect(c.x - rx, c.y - ry, c.x + rx, c.y + ry)
        drawOval(Brush.verticalGradient(listOf(White, Boya.scleraShade), startY = sclera.top, endY = sclera.bottom), sclera.topLeft, sclera.size)
        clipPath(Path().apply { addOval(sclera) }) {
            val ic = c + Offset(gaze.x * (rx - ir - 0.4f), gaze.y * (ry - ir - 0.6f) + 0.8f)
            drawCircle(
                Brush.radialGradient(0f to Boya.irisLight, 0.5f to Boya.iris, 1f to Boya.irisDark, center = ic + Offset(0f, 2.4f), radius = ir + 1f),
                ir, ic,
            )
            drawCircle(Boya.pupil, ir * 0.5f, ic)
            // Üst kapağın göze düşen gölgesi
            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.2f), 0.3f to Clear, startY = sclera.top, endY = sclera.bottom), sclera.topLeft, sclera.size)
            // Parıltılar
            if (sparkle) {
                star(ic + Offset(-2.1f, -2.4f), 3.6f, White)
                drawCircle(White.copy(alpha = 0.9f), 1.1f, ic + Offset(2.4f, 2.2f))
            } else {
                drawCircle(White, 2.5f, ic + Offset(-2.3f, -2.6f))
                drawCircle(White.copy(alpha = 0.85f), 1.1f, ic + Offset(2.3f, 2.1f))
            }
        }
    }
}

private fun DrawScope.brows(b: MascotBrows) {
    brow(EYE_L, side = -1f, lift = b.leftLift, tilt = b.leftTilt)
    brow(EYE_R, side = 1f, lift = b.rightLift, tilt = b.rightTilt)
}

/** Kaş: [side] -1 sol, 1 sağ; dış ucu gözün dışına doğru. */
private fun DrawScope.brow(eye: Offset, side: Float, lift: Float, tilt: Float) {
    val y = eye.y - 15.5f + lift
    val outer = Offset(eye.x + side * 6.2f, y + 1.2f - tilt)
    val inner = Offset(eye.x - side * 6.2f, y + 1.2f + tilt * 0.35f)
    val p = Path().apply { moveTo(outer.x, outer.y); quadraticTo(eye.x, y - 2.6f, inner.x, inner.y) }
    drawPath(p, Boya.brow, style = Stroke(2.6f, cap = StrokeCap.Round))
}

private fun DrawScope.mouth(m: MascotMouth, open: Boolean) {
    if (open) {
        // Konuşurken açık ağız (O ifadesinde daha büyük)
        val r = if (m == MascotMouth.O) Rect(53.6f, 74.6f, 66.4f, 88.4f) else Rect(53.2f, 76.4f, 66.8f, 86.6f)
        filledMouth(Path().apply { addOval(r) }, tongue = Rect(r.left + 2f, r.center.y + 1.4f, r.right - 2f, r.bottom + 2f))
        return
    }
    when (m) {
        MascotMouth.SMILE -> filledMouth(
            Path().apply { moveTo(51.5f, 78f); quadraticTo(60f, 88.5f, 68.5f, 78f); quadraticTo(60f, 81.4f, 51.5f, 78f); close() },
            tongue = Rect(55f, 81.6f, 65f, 88f),
        )
        MascotMouth.GRIN -> filledMouth(
            Path().apply { moveTo(48f, 76.5f); quadraticTo(60f, 94f, 72f, 76.5f); quadraticTo(60f, 79.8f, 48f, 76.5f); close() },
            tongue = Rect(53f, 83f, 67f, 91f), teeth = true,
        )
        MascotMouth.O -> filledMouth(Path().apply { addOval(Rect(55.2f, 76.2f, 64.8f, 87.4f)) }, tongue = Rect(55.5f, 82f, 64.5f, 88.5f))
        MascotMouth.SMALL -> {
            val p = Path().apply { moveTo(54.5f, 81.5f); quadraticTo(60f, 83f, 66f, 79.6f) }
            drawPath(p, Boya.mouth, style = Stroke(2.5f, cap = StrokeCap.Round))
        }
        MascotMouth.SMIRK -> {
            val p = Path().apply { moveTo(52.5f, 79.5f); quadraticTo(61f, 86.5f, 68.5f, 77.2f) }
            drawPath(p, Boya.mouth, style = Stroke(2.6f, cap = StrokeCap.Round))
            val d = Path().apply { moveTo(67.4f, 75.6f); quadraticTo(69.6f, 76.6f, 70.2f, 78.8f) }
            drawPath(d, Boya.mouth.copy(alpha = 0.8f), style = Stroke(1.6f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.filledMouth(path: Path, tongue: Rect, teeth: Boolean = false) {
    drawPath(path, Boya.mouth)
    clipPath(path) {
        drawOval(Boya.tongue, tongue.topLeft, tongue.size)
        if (teeth) drawRect(White, Offset(46f, 75f), Size(28f, 4.4f))
        // Ağzın içinde üstte koyuluk (derinlik)
        drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.5f to Clear, startY = 74f, endY = 92f), Offset(44f, 74f), Size(32f, 20f))
    }
}

// ------------------------------------------------------------------------------------------------ kollar ve eller

/** Elin ek biçimi: işaret parmağı, başparmak, fırça, çeneye dokunan parmak. */
private enum class HandExtra { NONE, POINT, THUMB, BRUSH, CHIN }

/** Kol: omuzdan ele (dirsek kıvrımı [bend]), elin biçimi ve gösterdiği yön. */
private class Arm(val hand: Offset, val bend: Float, val extra: HandExtra = HandExtra.NONE, val dir: Offset = Offset(0f, -1f))

private val SHOULDER_L = Offset(30f, 77f)
private val SHOULDER_R = Offset(90f, 77f)
private val REST_L = Arm(Offset(20f, 95f), -3f)
private val REST_R = Arm(Offset(100f, 95f), 3f)

/** El hareketleri tablosu: sol ve sağ kol. */
private fun armsOf(g: MascotGesture): Pair<Arm, Arm> = when (g) {
    MascotGesture.REST -> REST_L to REST_R
    MascotGesture.WAVE -> REST_L to Arm(Offset(105f, 47f), -7f)
    MascotGesture.POINT -> REST_L to Arm(Offset(103f, 46f), -6f, HandExtra.POINT, Offset(-0.25f, -1f))
    MascotGesture.THUMBS_UP -> REST_L to Arm(Offset(103f, 68f), 4f, HandExtra.THUMB)
    MascotGesture.BRUSH -> REST_L to Arm(Offset(101f, 74f), 3f, HandExtra.BRUSH)
    MascotGesture.CHEER -> Arm(Offset(14f, 44f), 7f) to Arm(Offset(106f, 44f), -7f)
    MascotGesture.THINK -> REST_L to Arm(Offset(67f, 94f), 9f, HandExtra.CHIN, Offset(-0.55f, -1f))
}

private fun DrawScope.arms(g: MascotGesture, swing: Float) {
    val (left, right) = armsOf(g)
    arm(SHOULDER_L, left)
    if (g == MascotGesture.WAVE) {
        // El sallama: kol omuz çevresinde gidip gelir, elin yanında hareket çizgileri
        rotate(swing * 13f, SHOULDER_R) {
            arm(SHOULDER_R, right)
            for ((i, r) in listOf(10.5f, 14.5f).withIndex()) {
                drawArc(
                    Boya.sparkle.copy(alpha = 0.8f - i * 0.25f), -60f, 70f, false, right.hand - Offset(r, r), Size(2 * r, 2 * r),
                    style = Stroke(1.8f, cap = StrokeCap.Round),
                )
            }
        }
    } else arm(SHOULDER_R, right)
}

private fun DrawScope.arm(shoulder: Offset, a: Arm) {
    val mid = (shoulder + a.hand) / 2f
    val d = a.hand - shoulder
    val len = hypot(d.x, d.y).coerceAtLeast(0.01f)
    val normal = Offset(-d.y / len, d.x / len)
    val ctrl = mid + normal * a.bend
    val path = Path().apply { moveTo(shoulder.x, shoulder.y); quadraticTo(ctrl.x, ctrl.y, a.hand.x, a.hand.y) }
    // Kolun kenarı, kendisi ve üstünde ışık (boru gibi yuvarlak)
    drawPath(path, Boya.bodyDeep.copy(alpha = 0.6f), style = Stroke(10f, cap = StrokeCap.Round))
    drawPath(path, Boya.body, style = Stroke(8f, cap = StrokeCap.Round))
    withTransform({ translate(-0.9f, -1.3f) }) { drawPath(path, Boya.bodyLight.copy(alpha = 0.7f), style = Stroke(2.6f, cap = StrokeCap.Round)) }
    if (a.extra == HandExtra.BRUSH) brush(a.hand)
    hand(a.hand)
    when (a.extra) {
        HandExtra.POINT, HandExtra.CHIN -> finger(a.hand, a.dir, if (a.extra == HandExtra.CHIN) 8.5f else 11.5f)
        HandExtra.THUMB -> {
            finger(a.hand + Offset(-1f, -3.5f), Offset(0f, -1f), 9f, width = 5f)
            // Yumruğun parmak çizgileri
            for (k in 0..2) {
                val y = a.hand.y - 1.5f + k * 2.6f
                drawLine(Boya.bodyDeep.copy(alpha = 0.55f), Offset(a.hand.x + 1.6f, y), Offset(a.hand.x + 5.2f, y + 0.4f), 1.1f, cap = StrokeCap.Round)
            }
        }
        else -> Unit
    }
}

private fun DrawScope.hand(c: Offset) {
    drawCircle(Boya.bodyDeep.copy(alpha = 0.6f), 7.6f, c)
    drawCircle(
        Brush.radialGradient(0f to Boya.bodyLight, 0.55f to Boya.body, 1f to Boya.bodyShade, center = c + Offset(-2.4f, -2.6f), radius = 9f),
        6.6f, c,
    )
    drawCircle(White.copy(alpha = 0.6f), 1.4f, c + Offset(-2.6f, -2.8f))
}

/** Parmak: elden [dir] yönüne yuvarlak uçlu kısa çubuk. */
private fun DrawScope.finger(hand: Offset, dir: Offset, length: Float, width: Float = 4.3f) {
    val n = hypot(dir.x, dir.y).coerceAtLeast(0.01f)
    val u = Offset(dir.x / n, dir.y / n)
    val a = hand + u * 3.5f
    val b = hand + u * length
    drawLine(Boya.bodyDeep.copy(alpha = 0.6f), a, b, width + 1.8f, cap = StrokeCap.Round)
    drawLine(Boya.body, a, b, width, cap = StrokeCap.Round)
    drawLine(Boya.bodyLight.copy(alpha = 0.75f), a + Offset(-0.7f, -0.4f), b + Offset(-0.7f, -0.4f), width * 0.35f, cap = StrokeCap.Round)
}

/** Elde tutulan ressam fırçası: tahta sap, metal bilezik, boyalı kıl ucu ve damla. */
private fun DrawScope.brush(hand: Offset) {
    val bottom = hand + Offset(-7f, 15f)
    val ferrule = hand + Offset(7.6f, -19f)
    val tipStart = hand + Offset(9.6f, -24f)
    val tip = hand + Offset(12.6f, -33f)
    drawLine(Boya.line.copy(alpha = 0.5f), bottom, ferrule, 5.6f, cap = StrokeCap.Round)
    drawLine(Boya.wood, bottom, ferrule, 4f, cap = StrokeCap.Round)
    drawLine(Boya.woodLight, bottom + Offset(-0.8f, -0.3f), ferrule + Offset(-0.8f, -0.3f), 1.2f, cap = StrokeCap.Round)
    drawLine(Boya.metal, ferrule, tipStart, 4.8f, cap = StrokeCap.Butt)
    drawLine(White.copy(alpha = 0.7f), ferrule + Offset(-1f, 0f), tipStart + Offset(-1f, 0f), 1.1f)
    val dx = tip - tipStart
    val n = hypot(dx.x, dx.y)
    val side = Offset(-dx.y / n, dx.x / n) * 2.6f
    val bristle = Path().apply {
        moveTo(tipStart.x + side.x, tipStart.y + side.y)
        quadraticTo(tip.x + side.x * 0.9f, tip.y + side.y * 0.4f + 3f, tip.x, tip.y)
        quadraticTo(tip.x - side.x * 0.9f, tip.y - side.y * 0.4f + 3f, tipStart.x - side.x, tipStart.y - side.y)
        close()
    }
    drawPath(bristle, Brush.linearGradient(listOf(Color(0xFF93C5FD), Boya.paint, Color(0xFF1D4ED8)), start = tipStart, end = tip))
    drawCircle(Boya.paint, 1.9f, tip + Offset(2.4f, 6.5f))
    drawCircle(White.copy(alpha = 0.7f), 0.6f, tip + Offset(1.9f, 5.9f))
}

private fun DrawScope.sparkles() {
    star(Offset(14f, 28f), 5f, Boya.sparkle)
    star(Offset(104f, 24f), 4f, Boya.sparkle)
    star(Offset(20f, 60f), 2.8f, Boya.sparkle.copy(alpha = 0.8f))
}

/** Dört köşeli parıltı yıldızı. */
private fun DrawScope.star(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 8) {
        val a = PI / 4 * i - PI / 2
        val rr = if (i % 2 == 0) r else r * 0.32f
        val x = c.x + (cos(a) * rr).toFloat()
        val y = c.y + (sin(a) * rr).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}
