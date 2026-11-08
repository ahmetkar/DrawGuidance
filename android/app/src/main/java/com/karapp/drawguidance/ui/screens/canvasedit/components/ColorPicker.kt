package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.ui.common.toHex
import com.karapp.drawguidance.ui.theme.NeuFieldShape
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic

/**
 * HSV renk paleti: üstte doygunluk (yatay) × parlaklık (dikey) alanı, altında ton çubuğu.
 * Dokunarak ya da sürükleyerek seçilir; seçilen renk "#RRGGBB" olarak bildirilir.
 */
@Composable
fun HsvColorPicker(
    colorHex: String,
    onColorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    // v27 ilk karede doğru renk (önce siyah çizilip yeniden oluşturulmasın)
    val initial = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(runCatching { android.graphics.Color.parseColor(colorHex) }.getOrDefault(0), it) }
    }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var brightness by remember { mutableFloatStateOf(initial[2]) }
    var lastEmitted by remember { mutableStateOf<String?>(null) }
    // v30 dokunma işleyicileri bir kez kurulur: her zaman güncel geri çağrı kullanılsın (eski kapanış eski ayarlarla
    // renk yaymasın)
    val currentOnChange by androidx.compose.runtime.rememberUpdatedState(onColorChange)

    // Dışarıdan (kayıtlı renk seçimi vb.) gelen renk değişikliklerinde HSV'yi yeniden hesapla;
    // kendi yaydığımız rengi geri alınca tonu kaybetmemek için atla (gri tonlarda ton belirsizdir).
    LaunchedEffect(colorHex) {
        if (!colorHex.equals(lastEmitted, ignoreCase = true)) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(runCatching { android.graphics.Color.parseColor(colorHex) }.getOrDefault(0), hsv)
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
        }
    }

    fun emit() {
        val hex = Color.hsv(hue, saturation, brightness).toHex()
        lastEmitted = hex
        currentOnChange(hex)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .neumorphic(colors, NeuFieldShape, elevation = 4.dp, inset = true)
                .padding(6.dp),
        ) {
            fun pickSv(position: Offset, size: IntSize) {
                saturation = (position.x / size.width).coerceIn(0f, 1f)
                brightness = 1f - (position.y / size.height).coerceIn(0f, 1f)
                emit()
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("color_sv_area")
                    .pointerInput(Unit) { detectTapGestures { pickSv(it, size) } }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragStart = { pickSv(it, size) }) { change, _ ->
                            change.consume(); pickSv(change.position, size)
                        }
                    },
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val center = Offset(saturation * size.width, (1f - brightness) * size.height)
                drawCircle(Color.Black.copy(alpha = 0.35f), radius = 12.dp.toPx(), center = center, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(2.5.dp.toPx()))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .neumorphic(colors, NeuPillShape, elevation = 3.dp, inset = true)
                .padding(5.dp),
        ) {
            fun pickHue(x: Float, width: Int) {
                hue = (x / width).coerceIn(0f, 1f) * 360f
                // Siyah/gri bir renkteyken ton seçmek görünür bir değişiklik yapmaz;
                // bu durumda seçilen tonun canlı hâline geçilir.
                if (saturation < 0.05f) saturation = 1f
                if (brightness < 0.2f) brightness = 1f
                emit()
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(NeuPillShape)
                    .testTag("color_hue_bar")
                    .pointerInput(Unit) { detectTapGestures { pickHue(it.x, size.width) } }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragStart = { pickHue(it.x, size.width) }) { change, _ ->
                            change.consume(); pickHue(change.position.x, size.width)
                        }
                    },
            ) {
                drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                val x = hue / 360f * size.width
                val center = Offset(x, size.height / 2f)
                drawCircle(Color.White, radius = size.height / 2f + 1.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.3f), radius = size.height / 2f + 3.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
            }
        }

        // v43 sayısal renk: RGB ya da HSV kaydırıcıları ve onaltılık kod (sabit renklerle sınırlı kalınmaz)
        ColorChannels(hue, saturation, brightness) { h, sat, v ->
            hue = h; saturation = sat; brightness = v
            emit()
        }
    }
}

/** v43 renk kanalları: RGB (0–255) ya da HSV (ton 0–360°, doygunluk ve parlaklık %) ve #RRGGBB kodu. */
object ColorMath {
    fun rgb(h: Float, s: Float, v: Float): IntArray {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        return intArrayOf(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c))
    }

    fun hsv(r: Int, g: Int, b: Int): FloatArray =
        FloatArray(3).also { android.graphics.Color.RGBToHSV(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), it) }

    fun hex(r: Int, g: Int, b: Int): String = "#%02X%02X%02X".format(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    /** "#RGB", "#RRGGBB" ya da başında # olmadan; geçersizse null. */
    fun parseHex(text: String): IntArray? {
        var t = text.trim().removePrefix("#")
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val v = t.toInt(16)
        return intArrayOf((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
    }
}

@Composable
private fun ColorChannels(hue: Float, saturation: Float, brightness: Float, onHsv: (Float, Float, Float) -> Unit) {
    val colors = NeuTheme.colors
    var rgbMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val rgb = ColorMath.rgb(hue, saturation, brightness)
    val hex = ColorMath.hex(rgb[0], rgb[1], rgb[2])
    Column(Modifier.fillMaxWidth().testTag("color_channels"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            com.karapp.drawguidance.ui.components.NeuChip("HSV", !rgbMode, { rgbMode = false }, Modifier.testTag("color_mode_hsv"))
            com.karapp.drawguidance.ui.components.NeuChip("RGB", rgbMode, { rgbMode = true }, Modifier.testTag("color_mode_rgb"))
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            // Onaltılık kod: geçerli kod yazılınca renk değişir
            var text by remember(hex) { mutableStateOf(hex) }
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { t ->
                    text = t.take(7).uppercase()
                    ColorMath.parseHex(text)?.let { c -> val h = ColorMath.hsv(c[0], c[1], c[2]); onHsv(h[0], h[1], h[2]) }
                },
                singleLine = true,
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(color = colors.content),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                modifier = Modifier.width(96.dp).neumorphic(colors, NeuPillShape, elevation = 3.dp, inset = true)
                    .padding(horizontal = 12.dp, vertical = 8.dp).testTag("color_hex"),
            )
        }
        if (rgbMode) {
            val labels = listOf("R", "G", "B")
            for (i in 0 until 3) {
                Channel(labels[i], rgb[i].toFloat(), 0f..255f, rgb[i].toString(), "color_rgb_$i") { x ->
                    val next = rgb.copyOf().also { it[i] = x.toInt() }
                    val h = ColorMath.hsv(next[0], next[1], next[2])
                    // Gri tonlarda ton korunur
                    onHsv(if (h[1] < 1e-3f) hue else h[0], h[1], h[2])
                }
            }
        } else {
            Channel("H", hue, 0f..360f, "${hue.toInt()}°", "color_hsv_0") { onHsv(it, saturation, brightness) }
            Channel("S", saturation * 100f, 0f..100f, "%${(saturation * 100).toInt()}", "color_hsv_1") { onHsv(hue, it / 100f, brightness) }
            Channel("V", brightness * 100f, 0f..100f, "%${(brightness * 100).toInt()}", "color_hsv_2") { onHsv(hue, saturation, it / 100f) }
        }
    }
}

@Composable
private fun Channel(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, tag: String, onChange: (Float) -> Unit) {
    val colors = NeuTheme.colors
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = colors.accent, modifier = Modifier.width(18.dp))
        com.karapp.drawguidance.ui.components.NeuSlider(value, onChange, range, Modifier.weight(1f).testTag(tag))
        androidx.compose.material3.Text(shown, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = colors.contentMuted, modifier = Modifier.width(44.dp))
    }
}
