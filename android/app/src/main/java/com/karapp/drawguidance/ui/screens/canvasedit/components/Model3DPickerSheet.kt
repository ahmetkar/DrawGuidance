package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.screens.model3d.Budget3D
import com.karapp.drawguidance.content.model3d.Catalog3D
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.data.library.Model3DRepository
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.TitleSearchField
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.model3d.PendingGlb
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Açı seçimi için önizleme çizicisi (model, yatay dönüş, dikey eğim, boyut). */
typealias Model3DPreview = suspend (Model3D, Float, Float, Int) -> android.graphics.Bitmap?

/**
 * v56 aşama 10 tuvalde "3B şekil ekle" penceresi: iki kaynak, **Kütüphane** (kategoriler ve arama) ve **GLB dosyası**
 * (dosya seç, ayrıntı düzeyi). Şekil seçilince açı adımı gelir: önizleme parmakla çevrilir, "Ekle" tuvale koyar.
 * 3B Şekillerim, kalemle düzenleme ve "Yeni 3B şekil" yoktur. Kaydırılan gövde `weight(1f)` alır, alt çubuk (Ekle)
 * her ekran yönünde görünür.
 */
@Composable
fun Model3DPickerSheet(
    loadCatalog: (String) -> Model3D?,
    loadGlb: suspend (PendingGlb, Int) -> Model3D?,
    preview: Model3DPreview,
    onInsert: (Model3D, Float, Float) -> Unit,
    onDismiss: () -> Unit,
    /** GLB dosya seçiciyi açar (null: GLB sekmesi yok, ör. basit çizim). */
    onPickGlb: (() -> Unit)? = null,
    /** Seçilmiş, eklenmeyi bekleyen GLB dosyası. */
    glb: PendingGlb? = null,
    /** v33 basit çizimde yalnız bu hazır şekiller. */
    catalogOnly: List<String>? = null,
) {
    val colors = NeuTheme.colors
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(if (glb != null && onPickGlb != null) TAB_GLB else TAB_LIBRARY) }
    LaunchedEffect(glb) { if (glb != null && onPickGlb != null) tab = TAB_GLB }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by rememberSaveable { mutableStateOf(Budget3D.importDetail) }
    var model by remember { mutableStateOf<Model3D?>(null) }
    var loading by remember { mutableStateOf(false) }
    val chosenName = when (tab) {
        TAB_LIBRARY -> chosen?.let { id -> Catalog3D.all.firstOrNull { it.id == id }?.name }
        else -> glb?.name
    }
    val next: () -> Unit = {
        if (tab == TAB_LIBRARY) chosen?.let { id -> model = loadCatalog(id) }
        else glb?.let { g ->
            loading = true
            scope.launch { model = loadGlb(g, Model3DRepository.DETAIL_LEVELS[detail]); loading = false }
        }
    }
    val m = model
    if (m != null) {
        Model3DAngleSheet(
            m, preview, title = stringResource(R.string.v56_m3_angle_title), confirm = stringResource(R.string.model3d_add_button),
            onConfirm = { yaw, pitch -> model = null; onInsert(m, yaw, pitch) }, onDismiss = { model = null },
        )
        return
    }
    NeuSheet(onDismiss, full = true) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).navigationBarsPadding().testTag("model3d_picker")) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                val columns = (maxWidth / 110.dp).toInt().coerceIn(3, 7)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.model3d_add_title), style = MaterialTheme.typography.titleLarge)
                    if (onPickGlb != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeuChip(stringResource(R.string.v56_m3_library), tab == TAB_LIBRARY, { tab = TAB_LIBRARY }, Modifier.testTag("model3d_tab_library"))
                        NeuChip(stringResource(R.string.v56_m3_glb), tab == TAB_GLB, { tab = TAB_GLB }, Modifier.testTag("model3d_tab_glb"))
                    }
                    if (tab == TAB_LIBRARY) {
                        Text(stringResource(R.string.model3d_add_pick_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                        if (catalogOnly == null) {
                            TitleSearchField(query, { query = it }, stringResource(R.string.v56_m3_search), stringResource(R.string.search_clear), tag = "model3d_search")
                        }
                        com.karapp.drawguidance.ui.screens.model3d.CatalogGrid(
                            onPick = { item -> chosen = if (chosen == item.id) null else item.id },
                            columns = columns, tile = 72.dp, selected = chosen, query = query, only = catalogOnly,
                        )
                    } else {
                        GlbSection(glb, detail, { detail = it }, onPickGlb ?: {})
                    }
                }
            }
            // Alt çubuk: seçili şekil ve "İleri" (açı seçimi) düğmesi; her zaman görünür
            HorizontalDivider(color = colors.contentMuted.copy(alpha = 0.18f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    chosenName?.let { stringResource(R.string.model3d_add_selected, it) } ?: stringResource(R.string.model3d_add_none),
                    style = MaterialTheme.typography.bodyMedium, color = if (chosenName != null) colors.accent else colors.contentMuted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (loading) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                NeuButton(
                    stringResource(R.string.v56_m3_next), next, Modifier.testTag("model3d_add_button"),
                    primary = true, enabled = chosenName != null && !loading, icon = Icons.Filled.Add,
                )
            }
        }
    }
}

@Composable
private fun GlbSection(glb: PendingGlb?, detail: Int, onDetail: (Int) -> Unit, onPick: () -> Unit) {
    val colors = NeuTheme.colors
    Text(stringResource(R.string.v56_m3_glb_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
    NeuButton(stringResource(R.string.v56_m3_glb_pick), onPick, Modifier.fillMaxWidth().testTag("model3d_import_glb"), icon = Icons.Filled.FileUpload)
    if (glb != null) {
        val bytes = glb.bytes.size
        val size = if (bytes >= 1_048_576) String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_048_576f) else "${bytes / 1024} KB"
        Text(stringResource(R.string.glb_import_file, glb.name, size), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.glb_import_detail), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(R.string.model3d_detail_low, R.string.model3d_detail_mid, R.string.model3d_detail_high).forEachIndexed { i, label ->
                NeuChip(stringResource(label).substringBefore(" ("), detail == i, { onDetail(i) }, Modifier.weight(1f).testTag("model3d_glb_detail_$i"))
            }
        }
    }
}

/**
 * Açı seçimi (ekleme ve "Açıyı değiştir"): önizleme parmakla sürüklenince döner (yatay: dönüş, dikey: eğim); hazır
 * açılar ve çubuklar. Onay modeli değiştirmez, yalnız bakış açısını verir.
 */
@Composable
fun Model3DAngleSheet(
    model: Model3D,
    preview: Model3DPreview,
    title: String,
    confirm: String,
    onConfirm: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NeuTheme.colors
    var yaw by remember(model) { mutableFloatStateOf(model.camera.yaw) }
    var pitch by remember(model) { mutableFloatStateOf(model.camera.pitch) }
    var image by remember(model) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(model, yaw, pitch) {
        delay(40)
        image = preview(model, yaw, pitch, PREVIEW)?.asImageBitmap()
    }
    NeuSheet(onDismiss, full = true) {
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).navigationBarsPadding().testTag("model3d_angle"),
        ) {
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.v56_m3_angle_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.fillMaxWidth())
                Box(
                    Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                        .background(colors.contentMuted.copy(alpha = 0.08f))
                        .pointerInput(model) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                yaw = ((yaw - drag.x * 0.5f) % 360f + 360f) % 360f
                                pitch = (pitch + drag.y * 0.4f).coerceIn(-80f, 80f)
                            }
                        }
                        .testTag("model3d_angle_preview"),
                    contentAlignment = Alignment.Center,
                ) {
                    val img = image
                    if (img != null) Image(img, model.name, Modifier.fillMaxWidth().aspectRatio(1f))
                    else CircularProgressIndicator(strokeWidth = 2.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRESETS.forEach { (label, angle) ->
                        NeuChip(stringResource(label), false, { yaw = angle.first; pitch = angle.second }, Modifier.testTag("model3d_angle_${angle.first.roundToInt()}_${angle.second.roundToInt()}"))
                    }
                }
                Text(stringResource(R.string.v56_m3_yaw, yaw.roundToInt()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth())
                NeuSlider(yaw, { yaw = it }, 0f..360f, Modifier.fillMaxWidth().testTag("model3d_angle_yaw"))
                Text(stringResource(R.string.v56_m3_pitch, pitch.roundToInt()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth())
                NeuSlider(pitch, { pitch = it }, -80f..80f, Modifier.fillMaxWidth().testTag("model3d_angle_pitch"))
            }
            HorizontalDivider(color = colors.contentMuted.copy(alpha = 0.18f))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss)
                NeuButton(confirm, { onConfirm(yaw, pitch) }, Modifier.testTag("model3d_angle_confirm"), primary = true)
            }
        }
    }
}

private const val TAB_LIBRARY = 0
private const val TAB_GLB = 1
private const val PREVIEW = 320
private val PRESETS = listOf(
    R.string.v56_m3_front to (0f to 0f),
    R.string.v56_m3_side to (90f to 0f),
    R.string.v56_m3_top to (0f to 80f),
    R.string.v56_m3_iso to (35f to 24f),
)
