package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.ExportOptions
import com.karapp.drawguidance.data.images.ImageExporter
import com.karapp.drawguidance.data.settings.ExportSettings
import com.karapp.drawguidance.data.settings.ImageFormat
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/** Çözünürlük katı etiketi: 0,5× · 1× · 2× · 4× */
fun scaleLabel(scale: Float): String =
    if (scale == scale.roundToInt().toFloat()) "${scale.roundToInt()}×" else "${scale.toString().replace('.', ',')}×"

/**
 * "Resim olarak kaydet" penceresi (alttan açılan): dosya adı, biçim (PNG/JPEG ve JPEG kalitesi), çözünürlük
 * (tuval boyutunun katı, sonuç piksel boyutuyla), sayfalar (çok sayfalı tuvallerde),
 * kılavuz çizgileri ve kaydedileceği yer.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ImageSaveOptionsDialog(
    initial: ExportOptions,
    pageCount: Int,
    hasGuides: Boolean,
    locationLabel: String,
    outputSize: (Float) -> Pair<Int, Int>,
    onSave: (ExportOptions) -> Unit,
    onDismiss: () -> Unit,
    /** v40 CMYK provası için tuvalin küçük resmi (null: prova gösterilmez). */
    preview: (suspend () -> android.graphics.Bitmap?)? = null,
    /** v40 CMYK PDF'in varsayılan yeri (İndirilenler). */
    documentLocationLabel: String = locationLabel,
    /** v42 birleşik Kaydet penceresinin içinde (kendi alt penceresi yok; başlık yerine [header]). */
    inline: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    var fileName by remember { mutableStateOf(initial.fileName) }
    var format by remember { mutableStateOf(initial.format) }
    var quality by remember { mutableIntStateOf(initial.quality) }
    var scale by remember { mutableFloatStateOf(initial.scale) }
    var allPages by remember { mutableStateOf(initial.allPages && pageCount > 1) }
    var includeGuides by remember { mutableStateOf(initial.includeGuides) }
    // v40 baskıya hazır (CMYK) kayıt ve kaydetme yeri
    var cmyk by remember { mutableStateOf(initial.cmyk) }
    var cmykFormat by remember { mutableStateOf(initial.cmykFormat) }
    var target by remember { mutableStateOf(initial.target) }
    val scales = remember(initial.scale) { (ExportSettings.Scales + initial.scale).distinct().sorted() }
    val (outW, outH) = outputSize(scale)
    val (fullW, fullH) = outputSize(1f)
    val limited = (fullW * scale).roundToInt() > outW + 1 || (fullH * scale).roundToInt() > outH + 1

    com.karapp.drawguidance.ui.components.NeuSheetOrInline(inline, onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("image_save_options_dialog")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (header != null) header() else Text(stringResource(R.string.save_options_title), style = MaterialTheme.typography.headlineSmall)
            NeuTextField(
                value = fileName,
                onValueChange = { fileName = it.take(80) },
                label = stringResource(R.string.save_file_name),
                supportingText = "${ImageExporter.safeName(fileName.ifBlank { initial.fileName })}.${if (cmyk != null) cmykFormat.extension else format.extension}",
                modifier = Modifier.fillMaxWidth(),
            )
            if (cmyk == null) {
                Label(stringResource(R.string.save_format))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageFormat.entries.forEach { f ->
                        NeuChip(f.label, selected = format == f, onClick = { format = f })
                    }
                }
            }
            // v40 renk modu: RGB ya da CMYK (baskıya hazır JPEG/TIFF/PDF, prova)
            CmykSection(cmyk, cmykFormat, { c, f -> cmyk = c; cmykFormat = f }, preview)
            if ((cmyk == null && format == ImageFormat.JPEG) || (cmyk != null && cmykFormat == com.karapp.drawguidance.data.images.CmykFormat.JPEG)) {
                Text(stringResource(R.string.save_quality, quality), style = MaterialTheme.typography.bodyMedium, color = colors.content)
                NeuSlider(value = quality.toFloat(), onValueChange = { quality = it.roundToInt() }, valueRange = 50f..100f)
            }
            Label(stringResource(R.string.save_resolution))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scales.forEach { s ->
                    NeuChip(scaleLabel(s), selected = scale == s, onClick = { scale = s })
                }
            }
            Text(
                if (limited) stringResource(R.string.save_resolution_limited, outW, outH, ImageExporter.MAX_SIDE)
                else stringResource(R.string.save_resolution_result, outW, outH),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accent,
                modifier = Modifier.testTag("save_resolution_result"),
            )
            if (pageCount > 1) {
                Label(stringResource(R.string.save_pages))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuChip(stringResource(R.string.save_this_page), selected = !allPages, onClick = { allPages = false })
                    NeuChip(stringResource(R.string.save_all_pages, pageCount), selected = allPages, onClick = { allPages = true })
                }
            }
            if (hasGuides) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.save_include_guides),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.content,
                        modifier = Modifier.weight(1f),
                    )
                    NeuSwitch(checked = includeGuides, onCheckedChange = { includeGuides = it })
                }
            }
            val chosen = initial.copy(
                fileName = fileName.trim().ifEmpty { initial.fileName },
                format = format,
                quality = quality,
                scale = scale,
                allPages = allPages && pageCount > 1,
                includeGuides = includeGuides && hasGuides,
                target = target,
                cmyk = cmyk,
                cmykFormat = cmykFormat,
            )
            // v40 "Konum seç": klasör ya da tek dosya (birden çok resim dosyasında yalnız klasör)
            val single = !chosen.allPages || chosen.isDocument && chosen.cmykFormat == com.karapp.drawguidance.data.images.CmykFormat.PDF
            SaveLocationSection(
                defaultLabel = if (chosen.isDocument && cmykFormat == com.karapp.drawguidance.data.images.CmykFormat.PDF) documentLocationLabel else locationLabel,
                target = if (!single && target is com.karapp.drawguidance.data.images.SaveTarget.Document) com.karapp.drawguidance.data.images.SaveTarget.Default else target,
                onTarget = { target = it },
                saveAsName = if (single) "${ImageExporter.safeName(chosen.fileName)}.${chosen.extension}" else null,
                saveAsMime = chosen.mime,
                onSaveAs = { onSave(chosen.copy(target = it)) },
            )
            Text(stringResource(R.string.save_location_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                NeuButton(
                    stringResource(R.string.save_button),
                    onClick = { onSave(chosen) },
                    modifier = Modifier.weight(1f).testTag("save_options_confirm"),
                    primary = true,
                    icon = Icons.Filled.SaveAlt,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = NeuTheme.colors.contentMuted)
}
