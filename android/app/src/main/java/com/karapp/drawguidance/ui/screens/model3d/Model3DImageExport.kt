package com.karapp.drawguidance.ui.screens.model3d

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.images.ImageSharer
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.karapp.drawguidance.ui.components.NeuSheet

/**
 * v25 3B şekli resim olarak dışa aktarma penceresi: boyut (cihazın sınırına kadar), oran, arka plan (saydam ya da renk),
 * zemin ızgarası, gölge, kenar çizgileri, açı (modele sığdır ya da şu anki görünüm), biçim (PNG/JPEG) ve kenar
 * yumuşatma; ayarlar değiştikçe küçük önizleme yenilenir. "Resimlerime kaydet" Resimlerim'e (ve ayarlardaki kayıt
 * yerine) yazar, "Paylaş" kaydedip paylaşır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageExportSheet(vm: Model3DEditorViewModel, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var e by remember { mutableStateOf(ImageExport(size = minOf(1080, Budget3D.exportMaxSide))) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var working by remember { mutableStateOf(false) }
    // v40 kaydetme yeri ("Konum seç")
    var target by remember { mutableStateOf<com.karapp.drawguidance.data.images.SaveTarget>(com.karapp.drawguidance.data.images.SaveTarget.Default) }
    val locationLabel = remember { vm.imageLocationLabel }
    val savedText = stringResource(R.string.model3d_image_saved)
    val failedText = stringResource(R.string.model3d_image_failed)
    val title = stringResource(R.string.model3d_export_image)

    // Önizleme: ayar değişince kısa beklemeden sonra küçük boyutta çizilir
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(e) {
        // v27 ilk önizleme pencerenin açılış animasyonu bittikten sonra çizilir
        delay(if (first) 360 else 120)
        first = false
        val bmp = runCatching { vm.renderImage(e, previewSide = PREVIEW) }.getOrNull()
        preview = bmp?.asImageBitmap()
    }

    fun export(share: Boolean, to: com.karapp.drawguidance.data.images.SaveTarget = target) {
        if (working) return
        working = true
        scope.launch {
            val uri = runCatching { vm.saveImage(e, to) }.getOrNull()
            working = false
            if (uri == null) { vm.message(failedText); return@launch }
            vm.message(savedText)
            if (share) runCatching {
                val send = ImageSharer.sendIntent(context, uri, null).apply { type = if (e.jpeg) "image/jpeg" else "image/png" }
                context.startActivity(android.content.Intent.createChooser(send, title).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            onDismiss()
        }
    }

    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 680.dp).verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp).testTag("model3d_image_export"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            // Önizleme (dama deseni saydamlığı gösterir)
            Box(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 260.dp).fillMaxWidth().aspectRatio(e.aspect)
                    .clip(RoundedCornerShape(14.dp)).border(1.dp, colors.contentMuted.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .then(if (e.background == 0 && !e.jpeg) Modifier.checker() else Modifier.background(Color(if (e.jpeg && e.background == 0) -1 else e.background)))
                    .semantics { contentDescription = "Önizleme" },
                contentAlignment = Alignment.Center,
            ) {
                val p = preview
                if (p != null) Image(p, null, Modifier.fillMaxSize()) else CircularProgressIndicator(Modifier.size(28.dp), color = colors.accent)
            }

            Label(stringResource(R.string.model3d_image_size))
            ChipRow {
                SIZES.filter { it <= Budget3D.exportMaxSide }.forEach { s -> NeuChip("$s px", e.size == s, onClick = { e = e.copy(size = s) }) }
            }
            Text(stringResource(R.string.model3d_image_limit, Budget3D.exportMaxSide), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)

            Label(stringResource(R.string.model3d_image_aspect))
            ChipRow {
                listOf(1f to R.string.model3d_image_square, 0.75f to R.string.model3d_image_portrait, 4f / 3f to R.string.model3d_image_landscape, 16f / 9f to R.string.model3d_image_wide)
                    .forEach { (a, label) -> NeuChip(stringResource(label), kotlin.math.abs(e.aspect - a) < 0.01f, onClick = { e = e.copy(aspect = a) }) }
            }

            Label(stringResource(R.string.model3d_image_background))
            ChipRow {
                NeuChip(stringResource(R.string.model3d_transparent), e.background == 0, onClick = { e = e.copy(background = 0) })
                BACKGROUNDS.forEach { c ->
                    val chosen = e.background == c
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                            .padding(4.dp).background(Color(c), CircleShape).clickable { e = e.copy(background = c) }
                            .semantics { contentDescription = String.format("#%06X", 0xFFFFFF and c); if (chosen) selected = true },
                    )
                }
            }
            if (e.jpeg && e.background == 0) Text(stringResource(R.string.model3d_image_jpeg_bg), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)

            SwitchLine(stringResource(R.string.model3d_ground_grid), e.ground) { e = e.copy(ground = it) }
            SwitchLine(stringResource(R.string.model3d_shadow), e.shadow) { e = e.copy(shadow = it) }
            SwitchLine(stringResource(R.string.model3d_edges), e.edges) { e = e.copy(edges = it) }

            Label(stringResource(R.string.model3d_image_angle))
            ChipRow {
                NeuChip(stringResource(R.string.model3d_image_fit), e.fit, onClick = { e = e.copy(fit = true) })
                NeuChip(stringResource(R.string.model3d_image_current), !e.fit, onClick = { e = e.copy(fit = false) })
            }

            Label(stringResource(R.string.model3d_image_format))
            ChipRow {
                NeuChip("PNG", !e.jpeg, onClick = { e = e.copy(jpeg = false) })
                NeuChip("JPEG", e.jpeg, onClick = { e = e.copy(jpeg = true) })
            }

            Label(stringResource(R.string.model3d_image_smooth))
            ChipRow {
                listOf(1 to R.string.model3d_image_smooth_low, 2 to R.string.model3d_image_smooth_mid, 3 to R.string.model3d_image_smooth_high)
                    .forEach { (k, label) -> NeuChip(stringResource(label), e.smoothness == k, onClick = { e = e.copy(smoothness = k) }) }
            }

            com.karapp.drawguidance.ui.components.SaveLocationSection(
                defaultLabel = locationLabel,
                target = target,
                onTarget = { target = it },
                saveAsName = "${com.karapp.drawguidance.data.images.ImageExporter.safeName(title)}.${if (e.jpeg) "jpg" else "png"}",
                saveAsMime = if (e.jpeg) "image/jpeg" else "image/png",
                onSaveAs = { export(false, it) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NeuButton(stringResource(R.string.model3d_image_share), { export(true) }, Modifier.weight(1f), enabled = !working, icon = Icons.Filled.Share)
                NeuButton(stringResource(R.string.model3d_image_save), { export(false) }, Modifier.weight(1f), primary = true, enabled = !working, icon = Icons.Filled.Save)
            }
            if (working) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(28.dp), color = colors.accent)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        NeuSwitch(checked, onChange)
    }
}

/** Saydam önizleme için dama deseni. */
private fun Modifier.checker(): Modifier = this.background(Color(0xFFF2F2F2)).drawBehind {
    val cell = 10.dp.toPx()
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (y in 0 until rows) for (x in 0 until cols) {
        if ((x + y) % 2 == 0) drawRect(Color(0xFFDADADA), androidx.compose.ui.geometry.Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell, cell))
    }
}

private const val PREVIEW = 300
private val SIZES = listOf(720, 1080, 1600, 2048, 3072)
private val BACKGROUNDS = listOf(0xFFFFFFFF, 0xFFF5F0E6, 0xFFECEFF1, 0xFFDDEBFF, 0xFF263238, 0xFF000000).map { it.toInt() }
