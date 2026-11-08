package com.karapp.drawguidance.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.CmykFormat
import com.karapp.drawguidance.data.print.BlackGeneration
import com.karapp.drawguidance.data.print.CmykConverter
import com.karapp.drawguidance.data.print.CmykOptions
import com.karapp.drawguidance.data.print.SoftProof
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** v40 prova sonucu (önizleme resimleri ve soluklaşan alan oranı). */
class ProofPreview(val screen: ImageBitmap, val print: ImageBitmap, val warning: ImageBitmap, val outOfGamut: Float)

/** v40 [source] küçük resminden ekran / baskı / gamut uyarısı önizlemeleri (arka planda). */
suspend fun proofPreview(source: Bitmap, options: CmykOptions): ProofPreview = withContext(Dispatchers.Default) {
    val w = source.width; val h = source.height
    val px = IntArray(w * h)
    source.getPixels(px, 0, w, 0, 0, w, h)
    // Saydam alanlar kağıt beyazı gibi gösterilir
    for (i in px.indices) {
        val a = px[i] ushr 24
        if (a < 255) {
            fun mix(c: Int) = (c * a + 255 * (255 - a)) / 255
            px[i] = (0xFF shl 24) or (mix((px[i] shr 16) and 0xFF) shl 16) or (mix((px[i] shr 8) and 0xFF) shl 8) or mix(px[i] and 0xFF)
        }
    }
    val result = SoftProof.proof(px, CmykConverter(options))
    ProofPreview(
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(),
        Bitmap.createBitmap(result.pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(),
        Bitmap.createBitmap(result.warning, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(),
        result.outOfGamut,
    )
}

/**
 * v40 kaydetme penceresinin renk modu bölümü: RGB (ekran) ya da CMYK (baskı). CMYK'de dosya türü (JPEG/TIFF/PDF),
 * siyah üretimi, toplam mürekkep sınırı ve basit ekran provası (ekran ↔ baskı, gamut uyarısı, soluklaşacak alan).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CmykSection(
    cmyk: CmykOptions?,
    format: CmykFormat,
    onChange: (CmykOptions?, CmykFormat) -> Unit,
    preview: (suspend () -> Bitmap?)? = null,
    /** PDF penceresinde dosya türü sorulmaz. */
    showFormat: Boolean = true,
) {
    val colors = NeuTheme.colors
    Column(Modifier.fillMaxWidth().testTag("cmyk_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(stringResource(R.string.v40_color_mode))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuChip(stringResource(R.string.v40_color_rgb), cmyk == null, { onChange(null, format) }, modifier = Modifier.testTag("color_rgb"))
            NeuChip(stringResource(R.string.v40_color_cmyk), cmyk != null, { onChange(cmyk ?: CmykOptions(), format) }, modifier = Modifier.testTag("color_cmyk"))
        }
        if (cmyk == null) return@Column
        Text(stringResource(R.string.v40_cmyk_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        if (showFormat) {
            Label(stringResource(R.string.v40_cmyk_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CmykFormat.entries.forEach { f ->
                    NeuChip(f.label, format == f, { onChange(cmyk, f) }, modifier = Modifier.testTag("cmyk_format_${f.name}"))
                }
            }
        }
        Label(stringResource(R.string.v40_cmyk_black))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BlackGeneration.entries.forEach { g ->
                val label = stringResource(
                    when (g) { BlackGeneration.LIGHT -> R.string.v40_black_light; BlackGeneration.MEDIUM -> R.string.v40_black_medium; BlackGeneration.HEAVY -> R.string.v40_black_heavy },
                )
                NeuChip(label, cmyk.blackGeneration == g, { onChange(cmyk.copy(blackGeneration = g), format) })
            }
        }
        Label(stringResource(R.string.v40_ink_limit))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CmykOptions.InkLimits.forEach { limit ->
                val label = if (limit == 0) stringResource(R.string.v40_ink_none) else stringResource(R.string.v40_ink_value, limit)
                NeuChip(label, cmyk.inkLimit == limit, { onChange(cmyk.copy(inkLimit = limit), format) })
            }
        }
        if (preview != null) ProofBox(cmyk, preview)
    }
}

@Composable
private fun ProofBox(cmyk: CmykOptions, preview: suspend () -> Bitmap?) {
    val colors = NeuTheme.colors
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var proof by remember { mutableStateOf<ProofPreview?>(null) }
    var warning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { source = runCatching { preview() }.getOrNull() }
    LaunchedEffect(source, cmyk) {
        val s = source ?: return@LaunchedEffect
        proof = runCatching { proofPreview(s, cmyk) }.getOrNull()
    }
    val p = proof ?: return
    Label(stringResource(R.string.v40_proof_title))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(R.string.v40_proof_screen to p.screen, R.string.v40_proof_print to if (warning) p.warning else p.print).forEach { (title, image) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Image(
                    image, stringResource(title),
                    Modifier.fillMaxWidth().heightIn(max = 160.dp).border(1.dp, colors.contentMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
                Text(stringResource(title), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
            }
        }
    }
    val percent = stringResource(R.string.v40_ink_value, kotlin.math.round(p.outOfGamut * 100f).toInt())
    Text(
        stringResource(R.string.v40_proof_gamut, percent),
        style = MaterialTheme.typography.bodySmall,
        color = if (p.outOfGamut > 0.05f) colors.danger else colors.accent,
        modifier = Modifier.testTag("cmyk_gamut"),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.v40_proof_warning), style = MaterialTheme.typography.bodyMedium, color = colors.content, modifier = Modifier.weight(1f))
        NeuSwitch(warning, { warning = it }, Modifier.testTag("cmyk_gamut_warning"))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted)
}
