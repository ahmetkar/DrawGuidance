package com.karapp.drawguidance.ui.screens.model3d

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.JoinFull
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.model3d.Brush3Kind
import com.karapp.drawguidance.content.model3d.DrawPlane
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.ui.components.BrushIcons
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.components.isWideLayout
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 3B düzenleyici penceresi. v26 düzeni tuvaldeki gibi:
 * - üstte geri, ad, Parçalar, Ayarlar, Kaydet ve ⋮ (yeniden adlandır, farklı kaydet, resim/GLB dışa aktar, paylaş),
 * - ortada sahne (sağ üstte yakınlaştır, uzaklaştır, sığdır, hazır açılar; altta seçim çipi; yönerge birkaç saniye
 *   görünür, sonra "?" ile açılır),
 * - sahnenin altında açılan panel (sağ üstünde ✕; modu değiştirince modun paneli kapanır),
 * - modun araç satırı (solda geri al / ileri al, sonra modun alt araçları ve düğmeleri),
 * - en altta mod çubuğu: Seç · Düzenle · Çiz · Boya · Ekle.
 */
@Composable
fun Model3DEditorScreen(
    modelId: String?,
    canvasId: String?,
    strokeId: String?,
    onBack: () -> Unit,
    /** v33 yeni 3B şeklin adı (ana sayfadaki 3B tuval). */
    initialName: String? = null,
    vm: Model3DEditorViewModel = koinViewModel(key = "model3d-$modelId-$canvasId-$strokeId") { parametersOf(modelId ?: "", canvasId ?: "", strokeId ?: "", initialName ?: "") },
) {
    val colors = NeuTheme.colors
    val model by vm.model.collectAsState()
    val selection by vm.selection.collectAsState()
    val tool by vm.tool.collectAsState()
    val mode by vm.mode.collectAsState()
    val panel by vm.panel.collectAsState()
    val dirty by vm.dirty.collectAsState()
    var exitAsk by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var savingAs by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var imageExport by remember { mutableStateOf(false) }
    // v29 tuvale / çizgi romana aktarma
    var transferOpen by remember { mutableStateOf(false) }
    var transferring by remember { mutableStateOf(false) }
    var clearAsk by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val exportedText = stringResource(R.string.model3d_exported)
    val exportFailedText = stringResource(R.string.model3d_export_failed)
    // v24 GLB dışa aktarma (dosya seçicisiyle)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("model/gltf-binary")) { uri ->
        if (uri != null) scope.launch {
            val r = runCatching {
                val bytes = vm.exportGlb() ?: error("model yok")
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("dosya açılamadı") }
            }
            vm.message(if (r.isSuccess) exportedText else exportFailedText.format(r.exceptionOrNull()?.message ?: ""))
        }
    }
    val savedText = stringResource(R.string.model3d_saved)
    val savedCanvasText = stringResource(R.string.model3d_saved_canvas)

    LaunchedEffect(vm) {
        vm.events.collectLatest { e ->
            when (e) {
                is Event3.Saved -> message = if (e.toCanvas) savedCanvasText.format(e.name) else savedText.format(e.name)
                Event3.Exit -> onBack()
                is Event3.Message -> message = e.text
            }
        }
    }
    LaunchedEffect(message) {
        if (message != null) { delay(2200); message = null }
    }

    val tryBack = { if (dirty) exitAsk = true else onBack() }
    BackHandler { if (vm.op.value != null) vm.cancelOp() else if (panel != null) vm.closePanel() else tryBack() }

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().testTag("model3d_editor")) {
        NeuTopBar(
            title = model?.name ?: "",
            subtitle = model?.let { stringResource(R.string.model3d_info, it.parts.size, it.triangleCount) },
            navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tryBack) },
            actions = {
                NeuIconButton(Icons.Filled.Layers, stringResource(R.string.model3d_panel_parts), { vm.togglePanel(Panel3.PARTS) }, size = 38.dp, selected = panel == Panel3.PARTS)
                NeuIconButton(Icons.Filled.Settings, stringResource(R.string.model3d_panel_settings), { vm.togglePanel(Panel3.SETTINGS) }, size = 38.dp, selected = panel == Panel3.SETTINGS)
                NeuIconButton(Icons.Filled.Save, stringResource(R.string.model3d_save), { vm.save() }, size = 38.dp, selected = dirty)
                Box {
                    NeuIconButton(Icons.Filled.MoreVert, stringResource(R.string.more_options), { menu = true }, size = 38.dp)
                    DropdownMenu(menu, { menu = false }, modifier = Modifier.background(colors.background)) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.model3d_rename)) }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.model3d_save_as)) }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; savingAs = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.model3d_fit)) }, leadingIcon = { Icon(Icons.Filled.CenterFocusStrong, null) }, onClick = { menu = false; vm.fitView() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.model3d_export_image)) }, leadingIcon = { Icon(Icons.Filled.Image, null) },
                            onClick = { menu = false; imageExport = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transfer_title_3d)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                            onClick = { menu = false; transferOpen = true },
                            modifier = Modifier.testTag("model3d_transfer_item"),
                        )
                        // v44 GLB dışa aktarma ve paylaşma Premium
                        val exportAccess = com.karapp.drawguidance.ui.premium.LocalPremium.current
                        val exportFeature = com.karapp.drawguidance.data.billing.PremiumFeature.MODEL3D_EXPORT
                        val exportLocked = exportAccess.locked(exportFeature)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.model3d_export)) }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            trailingIcon = if (exportLocked) ({ com.karapp.drawguidance.ui.premium.PremiumLockBadge(size = 14.dp) }) else null,
                            onClick = {
                                menu = false
                                if (exportAccess.request(exportFeature)) exportLauncher.launch(((model?.name ?: "model").replace(Regex("[\\\\/:*?\"<>|]"), "_")) + ".glb")
                            },
                            modifier = Modifier.testTag("model3d_export_item"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.model3d_share)) }, leadingIcon = { Icon(Icons.Filled.Share, null) },
                            trailingIcon = if (exportLocked) ({ com.karapp.drawguidance.ui.premium.PremiumLockBadge(size = 14.dp) }) else null,
                            onClick = { menu = false; if (exportAccess.request(exportFeature)) scope.launch { shareGlb(context, vm) } },
                            modifier = Modifier.testTag("model3d_share_item"),
                        )
                    }
                }
            },
        )
        val wide = isWideLayout()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val panelMax = maxHeight * 0.5f
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { SceneArea(vm, tool, mode, selection, model, message) }
                    panel?.let { p -> PanelFrame(vm, p, Modifier.width(340.dp).fillMaxHeight().padding(8.dp)) }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { SceneArea(vm, tool, mode, selection, model, message) }
                    panel?.let { p -> PanelFrame(vm, p, Modifier.fillMaxWidth().heightIn(max = panelMax).padding(horizontal = 8.dp, vertical = 4.dp)) }
                }
            }
        }
        val op by vm.op.collectAsState()
        op?.let { OpBar(vm, it) } ?: ContextRow(vm, mode, tool, selection, onClearAll = { clearAsk = true })
        ModeBar3(mode, vm::setMode)
    }

    if (exitAsk) {
        AlertDialog(
            onDismissRequest = { exitAsk = false },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.model3d_exit_title)) },
            text = { Text(stringResource(if (vm.fromCanvas) R.string.model3d_exit_text_canvas else R.string.model3d_exit_text)) },
            confirmButton = { TextButton(onClick = { exitAsk = false; vm.save(exitAfter = true) }) { Text(stringResource(R.string.model3d_save)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { exitAsk = false }) { Text(stringResource(R.string.cancel_button)) }
                    TextButton(onClick = { exitAsk = false; onBack() }) { Text(stringResource(R.string.model3d_discard), color = colors.danger) }
                }
            },
        )
    }
    if (clearAsk) {
        AlertDialog(
            onDismissRequest = { clearAsk = false },
            containerColor = colors.background,
            modifier = Modifier.testTag("model3d_clear_dialog"),
            title = { Text(stringResource(R.string.model3d_clear_all_title)) },
            text = { Text(stringResource(R.string.model3d_clear_all_text)) },
            confirmButton = { TextButton(onClick = { clearAsk = false; vm.clearDrawings() }) { Text(stringResource(R.string.model3d_clear_confirm), color = colors.danger) } },
            dismissButton = { TextButton(onClick = { clearAsk = false }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }
    if (renaming) {
        com.karapp.drawguidance.ui.components.TextInputSheet(
            title = stringResource(R.string.model3d_rename), initial = model?.name ?: "", label = stringResource(R.string.model3d_name),
            onConfirm = { vm.rename(it); renaming = false }, onDismiss = { renaming = false },
        )
    }
    if (imageExport) ImageExportSheet(vm) { imageExport = false }
    if (transferOpen) {
        val targets by vm.transferTargets.collectAsState(initial = emptyList())
        val doneText = stringResource(R.string.transfer_done)
        val failedText = stringResource(R.string.transfer_failed)
        com.karapp.drawguidance.ui.components.TransferSheet(
            title = stringResource(R.string.transfer_title_3d),
            hint = stringResource(R.string.transfer_hint_3d),
            targets = targets,
            newKinds = listOf(com.karapp.drawguidance.data.model.CanvasKind.ART, com.karapp.drawguidance.data.model.CanvasKind.COMIC),
            working = transferring,
            onTransfer = { target, vector, _ ->
                transferring = true
                scope.launch {
                    val result = runCatching { vm.transferTo(target, vector) }.getOrNull()
                    transferring = false
                    if (result == null) vm.message(failedText) else { vm.message(doneText.format(result.name)); transferOpen = false }
                }
            },
            onDismiss = { transferOpen = false },
        )
    }
    if (savingAs) {
        com.karapp.drawguidance.ui.components.TextInputSheet(
            title = stringResource(R.string.model3d_save_as), initial = (model?.name ?: "") + " (2)", label = stringResource(R.string.model3d_name),
            onConfirm = { vm.saveAs(it); savingAs = false }, onDismiss = { savingAs = false },
        )
    }
    // v30 şeffaf yükleme perdesi (3B şekil yüklenirken)
    com.karapp.drawguidance.ui.components.LoadingOverlay(visible = model == null, message = stringResource(R.string.loading_model))
}

/** Panel çerçevesi: başlık ve sağ üstte kapatma düğmesi (✕); içerik kendi içinde kayar. */
@Composable
private fun PanelFrame(vm: Model3DEditorViewModel, panel: Panel3, modifier: Modifier) {
    NeuSurface(modifier.testTag("model3d_panel"), elevation = 6.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(panelTitle(panel)), style = MaterialTheme.typography.titleSmall, color = NeuTheme.colors.accent, modifier = Modifier.weight(1f))
                NeuIconButton(Icons.Filled.Close, stringResource(R.string.model3d_close_panel), vm::closePanel, Modifier.testTag("model3d_panel_close"), size = 34.dp)
            }
            PanelContent(vm, panel, Modifier.fillMaxWidth().weight(1f, fill = false))
        }
    }
}

private fun panelTitle(p: Panel3): Int = when (p) {
    Panel3.ADD -> R.string.model3d_panel_add
    Panel3.PARTS -> R.string.model3d_panel_parts
    Panel3.MATERIAL -> R.string.model3d_panel_material
    Panel3.SHAPE -> R.string.model3d_panel_shape
    Panel3.SETTINGS -> R.string.model3d_panel_settings
    Panel3.BRUSH -> R.string.model3d_panel_brush
    Panel3.OPS -> R.string.model3d_panel_ops
}

@Composable
private fun SceneArea(
    vm: Model3DEditorViewModel, tool: Tool3, mode: Mode3, selection: Selection3, model: com.karapp.drawguidance.content.model3d.Model3D?, message: String?,
) {
    val colors = NeuTheme.colors
    val faceMode by vm.faceMode.collectAsState()
    val busy by vm.busy.collectAsState()
    // v26 yönerge araç değişince birkaç saniye görünür, sonra "?" düğmesiyle açılır (sahne kalabalık olmasın)
    var hintOn by remember { mutableStateOf(true) }
    val paintScope by vm.paintScope.collectAsState()
    LaunchedEffect(tool, mode, faceMode, paintScope) { hintOn = true; delay(HINT_MS); hintOn = false }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))) {
        Model3DViewport(vm, model, selection, tool)
        val hint = stringResource(
            when {
                mode == Mode3.ADD -> R.string.model3d_hint_add
                tool == Tool3.PAINT -> when (paintScope) {
                    PaintScope.FACE -> R.string.model3d_hint_paint_face
                    PaintScope.SURFACE -> R.string.model3d_hint_paint_surface
                    PaintScope.PART -> R.string.model3d_hint_paint_part
                    PaintScope.ALL -> R.string.model3d_hint_paint_all
                }
                tool == Tool3.FACE && faceMode == FaceMode.SURFACE -> R.string.model3d_hint_face_surface
                else -> toolHint(tool)
            },
        )
        Row(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 10.dp, end = 58.dp), verticalAlignment = Alignment.Top) {
            AnimatedVisibility(hintOn, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.content,
                    // Dokunuşu tutmaz: alttaki sahneye çizim/silgi geçer
                    modifier = Modifier.background(colors.background.copy(alpha = 0.86f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp).widthIn(max = 340.dp).testTag("model3d_hint"),
                )
            }
            if (!hintOn) NeuIconButton(Icons.Filled.HelpOutline, stringResource(R.string.model3d_hint_show), { hintOn = true }, size = 32.dp)
        }
        // v26 seçim çipi: neyin seçili olduğu (dokununca Parçalar paneli)
        SelectionChip(vm, selection, model, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
        // v27 çizim önerisi: çizgiden sonra 1–3 cisim; dokununca çizimin yerine geçer
        SuggestionBubble(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp, start = 10.dp, end = 10.dp))
        busy?.let { label ->
            Row(
                Modifier.align(Alignment.Center).background(colors.background.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp).testTag("model3d_busy"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), color = colors.accent, strokeWidth = 2.5.dp)
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.content)
            }
        }
        if (message != null) {
            Text(
                message, style = MaterialTheme.typography.bodySmall, color = colors.onAccent,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 54.dp, start = 12.dp, end = 12.dp).background(colors.accent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        // Görünüm düğmeleri (sağ üst): yakınlaştır, uzaklaştır, sığdır, hazır açılar
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuIconButton(Icons.Filled.ZoomInMap, stringResource(R.string.model3d_zoom_in), { vm.zoom(1.25f) }, size = 36.dp)
            NeuIconButton(Icons.Filled.ZoomOutMap, stringResource(R.string.model3d_zoom_out), { vm.zoom(0.8f) }, size = 36.dp)
            NeuIconButton(Icons.Filled.FilterCenterFocus, stringResource(R.string.model3d_fit), vm::fitView, size = 36.dp)
            var views by remember { mutableStateOf(false) }
            Box {
                NeuIconButton(Icons.Filled._3dRotation, stringResource(R.string.model3d_views), { views = true }, size = 36.dp)
                DropdownMenu(views, { views = false }, modifier = Modifier.background(colors.background)) {
                    listOf(0 to R.string.model3d_view_iso, 1 to R.string.model3d_view_front, 2 to R.string.model3d_view_side, 3 to R.string.model3d_view_top).forEach { (k, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { views = false; vm.presetView(k) })
                    }
                }
            }
        }
    }
}

/** v27 öneri baloncuğu: önerilen cisimler (simge ve ad) ve kapatma; birkaç saniye sonra kendiliğinden kalkar. */
@Composable
private fun SuggestionBubble(vm: Model3DEditorViewModel, modifier: Modifier) {
    val s by vm.suggestion.collectAsState()
    val current = s
    LaunchedEffect(current) { if (current != null) { delay(SUGGEST_MS); vm.expireSuggestion(current) } }
    // v28 öneri kutusu tutamaktan sürüklenebilir (uygulama açıkken yeri hatırlanır; çift dokunuş yerine döndürür)
    val shift = Suggest3Position.offset
    AnimatedVisibility(
        current != null,
        modifier = modifier.then(Modifier.offset { androidx.compose.ui.unit.IntOffset(shift.x.toInt(), shift.y.toInt()) }),
        enter = fadeIn(), exit = fadeOut(),
    ) {
        val shown = current ?: return@AnimatedVisibility
        val colors = NeuTheme.colors
        NeuSurface(Modifier.testTag("model3d_suggest"), shape = NeuPillShape, elevation = 6.dp) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 2.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                com.karapp.drawguidance.ui.screens.canvasedit.components.SuggestDragHandle(
                    com.karapp.drawguidance.ui.screens.canvasedit.components.SuggestDrag(
                        onDrag = { d -> Suggest3Position.offset = Suggest3Position.offset + d },
                        onEnd = {},
                        onReset = { Suggest3Position.offset = androidx.compose.ui.geometry.Offset.Zero },
                    ),
                )
                Icon(Icons.Filled.AutoAwesome, stringResource(R.string.model3d_suggest_title), tint = colors.accent, modifier = Modifier.size(20.dp))
                // v39 3B öneriler gösterilir, uygulamak Premium
                val access = com.karapp.drawguidance.ui.premium.LocalPremium.current
                val feature = com.karapp.drawguidance.data.billing.PremiumFeature.MODEL3D_SUGGEST
                val locked = access.locked(feature)
                shown.kinds.forEach { k ->
                    val label = Model3DEditorViewModel.suggestName(k)
                    Row(
                        Modifier.height(38.dp).clip(NeuPillShape).background(colors.accent.copy(alpha = 0.12f))
                            .clickable(role = Role.Button) { if (access.request(feature)) vm.applySuggestion(k) }
                            .padding(horizontal = 12.dp).testTag(if (locked) "model3d_suggest_${k.name}_locked" else "model3d_suggest_${k.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(suggestIcon(k), null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.content, maxLines = 1, softWrap = false)
                        if (locked) { Spacer(Modifier.width(4.dp)); com.karapp.drawguidance.ui.premium.PremiumLockBadge(size = 14.dp) }
                    }
                }
                NeuIconButton(Icons.Filled.Close, stringResource(R.string.model3d_suggest_dismiss), vm::dismissSuggestion, size = 34.dp)
            }
        }
    }
}

/** v28 3B öneri kutusunun sürüklenmiş yeri (otomatik yerinden kayma). */
private object Suggest3Position {
    var offset by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
}

private fun suggestIcon(k: com.karapp.drawguidance.content.model3d.Suggest3Kind): ImageVector = when (k) {
    com.karapp.drawguidance.content.model3d.Suggest3Kind.SPHERE, com.karapp.drawguidance.content.model3d.Suggest3Kind.ELLIPSOID,
    com.karapp.drawguidance.content.model3d.Suggest3Kind.DOME -> Icons.Filled.Circle
    com.karapp.drawguidance.content.model3d.Suggest3Kind.CUBE, com.karapp.drawguidance.content.model3d.Suggest3Kind.BOX,
    com.karapp.drawguidance.content.model3d.Suggest3Kind.SLAB -> Icons.Filled.CropSquare
    com.karapp.drawguidance.content.model3d.Suggest3Kind.CONE, com.karapp.drawguidance.content.model3d.Suggest3Kind.PYRAMID,
    com.karapp.drawguidance.content.model3d.Suggest3Kind.PRISM3 -> Icons.Filled.ChangeHistory
    com.karapp.drawguidance.content.model3d.Suggest3Kind.TORUS -> Icons.Filled.DonutLarge
    com.karapp.drawguidance.content.model3d.Suggest3Kind.STAR -> Icons.Filled.Star
    com.karapp.drawguidance.content.model3d.Suggest3Kind.ROD -> Icons.Filled.HorizontalRule
    com.karapp.drawguidance.content.model3d.Suggest3Kind.SPRING -> Icons.Filled.Waves
    com.karapp.drawguidance.content.model3d.Suggest3Kind.TUBE, com.karapp.drawguidance.content.model3d.Suggest3Kind.RIBBON,
    com.karapp.drawguidance.content.model3d.Suggest3Kind.WALL, com.karapp.drawguidance.content.model3d.Suggest3Kind.LATHE,
    com.karapp.drawguidance.content.model3d.Suggest3Kind.INFLATE -> Icons.Filled.Brush
    else -> Icons.Filled.ViewInAr
}

/** Seçim çipi: tek parça, grup ya da birden çok parça; dokununca Parçalar paneli. */
@Composable
private fun SelectionChip(vm: Model3DEditorViewModel, selection: Selection3, model: com.karapp.drawguidance.content.model3d.Model3D?, modifier: Modifier) {
    val m = model ?: return
    val parts = selection.parts.mapNotNull { m.part(it) }
    if (parts.isEmpty()) return
    val group = parts.first().group?.takeIf { g -> parts.all { it.group == g } && parts.size == m.parts.count { it.group == g } }?.let { g -> m.groups.firstOrNull { it.id == g } }
    val text = when {
        group != null -> stringResource(R.string.model3d_selected_group, group.name, parts.size)
        parts.size == 1 -> parts.first().name
        else -> stringResource(R.string.model3d_selected_parts, parts.size)
    }
    val colors = NeuTheme.colors
    NeuSurface(
        modifier = modifier.height(32.dp).testTag("model3d_selection_chip"),
        shape = NeuPillShape, elevation = 4.dp, onClick = { vm.togglePanel(Panel3.PARTS) },
    ) {
        Row(Modifier.padding(horizontal = 12.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (group != null) Icons.Filled.Workspaces else Icons.Filled.ViewInAr, null, tint = colors.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
        }
    }
}

private fun toolHint(t: Tool3): Int = when (t) {
    Tool3.VIEW -> R.string.model3d_hint_view
    Tool3.MOVE -> R.string.model3d_hint_move
    Tool3.ROTATE -> R.string.model3d_hint_rotate
    Tool3.SCALE -> R.string.model3d_hint_scale
    Tool3.POINT -> R.string.model3d_hint_point
    Tool3.EDGE -> R.string.model3d_hint_edge
    Tool3.FACE -> R.string.model3d_hint_face
    Tool3.PAINT -> R.string.model3d_hint_paint
    Tool3.DRAW -> R.string.model3d_hint_draw
    Tool3.ERASE -> R.string.model3d_hint_erase
}

// --------------------------------------------------------------------------------------------------- mod çubuğu

private class ModeItem(val mode: Mode3, val icon: ImageVector, val label: Int)

private val ModeItems = listOf(
    ModeItem(Mode3.SELECT, Icons.Filled.TouchApp, R.string.model3d_mode_select),
    ModeItem(Mode3.EDIT, Icons.Filled.Polyline, R.string.model3d_mode_edit),
    ModeItem(Mode3.DRAW, Icons.Filled.Brush, R.string.model3d_mode_draw),
    ModeItem(Mode3.PAINT, Icons.Filled.FormatColorFill, R.string.model3d_mode_paint),
    ModeItem(Mode3.ADD, Icons.Filled.AddBox, R.string.model3d_mode_add),
)

/** v26 mod çubuğu (tuvaldeki mod seçici gibi): seçili modun adı simgesinin yanında yazar. */
@Composable
private fun ModeBar3(mode: Mode3, onMode: (Mode3) -> Unit) {
    val colors = NeuTheme.colors
    NeuSurface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).testTag("model3d_modes"),
        shape = NeuPillShape, elevation = 4.dp, inset = true,
    ) {
        Row(Modifier.fillMaxWidth().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ModeItems.forEach { item ->
                val on = item.mode == mode
                val label = stringResource(item.label)
                Box(
                    Modifier.weight(if (on) 2.2f else 1f).height(44.dp)
                        .then(if (on) Modifier.neumorphic(colors, NeuPillShape, elevation = 4.dp) else Modifier)
                        .clip(NeuPillShape)
                        .selectable(selected = on, role = Role.Tab, onClick = { onMode(item.mode) })
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(item.icon, null, tint = if (on) colors.accent else colors.contentMuted, modifier = Modifier.size(20.dp))
                        if (on) Text(label, style = MaterialTheme.typography.labelMedium, color = colors.accent, maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics { })
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------- araç satırı

/** Araç satırında düğme: simge ve kısa ad (v27: 42 dp, daha büyük simge ve yazı). */
@Composable
private fun ActionPill(
    icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false, selected: Boolean = false,
) {
    val colors = NeuTheme.colors
    val tint = when {
        !enabled -> colors.contentMuted.copy(alpha = 0.45f)
        danger -> colors.danger
        selected -> colors.accent
        else -> colors.content
    }
    Row(
        Modifier.height(ROW_H)
            .neumorphic(colors, NeuPillShape, elevation = if (selected) 3.dp else 4.dp, selected = selected)
            .clip(NeuPillShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).height(28.dp).background(NeuTheme.colors.contentMuted.copy(alpha = 0.25f)))
}

/** v27 alt araç (bölüm) seçicinin bir öğesi. */
private class Seg(val icon: ImageVector, val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * v27 alt araç seçici: modun alt araçları (Taşı · Çevir · Ölçekle, Nokta · Kenar · Yüz, Fırça · Silgi, boya kapsamı)
 * araç satırından ayrı, tam genişlikte bölümlü çubuk; seçili olan kabarık.
 */
@Composable
private fun SubToolBar(items: List<Seg>) {
    val colors = NeuTheme.colors
    val dense = items.size >= 4
    NeuSurface(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp).testTag("model3d_subtools"),
        shape = NeuPillShape, elevation = 3.dp, inset = true,
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { s ->
                Row(
                    Modifier.weight(1f).height(ROW_H)
                        .then(if (s.selected) Modifier.neumorphic(colors, NeuPillShape, elevation = 3.dp) else Modifier)
                        .clip(NeuPillShape)
                        .selectable(selected = s.selected, role = Role.Tab, onClick = s.onClick)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (s.selected) colors.accent else colors.contentMuted
                    Icon(s.icon, null, tint = tint, modifier = Modifier.size(if (dense) 18.dp else 20.dp))
                    Spacer(Modifier.width(if (dense) 4.dp else 6.dp))
                    Text(
                        s.label, color = if (s.selected) colors.accent else colors.content,
                        style = if (dense) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * v27 modun araç alanı: üstte alt araç seçici (Ekle'de yok), altında solda geri al / ileri al ve seçili alt aracın
 * düğmeleri (kayar). Seçim gerektiren düğmeler hep etkin görünür; seçim yokken dokununca ne seçileceği söylenir.
 */
@Composable
private fun ContextRow(vm: Model3DEditorViewModel, mode: Mode3, tool: Tool3, selection: Selection3, onClearAll: () -> Unit) {
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        when (mode) {
            Mode3.SELECT -> SubToolBar(
                listOf(
                    Seg(Icons.Filled.OpenWith, stringResource(R.string.model3d_tool_move), tool == Tool3.MOVE || tool == Tool3.VIEW) { vm.setTool(Tool3.MOVE) },
                    Seg(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.model3d_tool_rotate), tool == Tool3.ROTATE) { vm.setTool(Tool3.ROTATE) },
                    Seg(Icons.Filled.OpenInFull, stringResource(R.string.model3d_tool_scale), tool == Tool3.SCALE) { vm.setTool(Tool3.SCALE) },
                ),
            )
            Mode3.EDIT -> SubToolBar(
                listOf(
                    Seg(Icons.Filled.Adjust, stringResource(R.string.model3d_tool_point), tool == Tool3.POINT) { vm.setTool(Tool3.POINT) },
                    Seg(Icons.Filled.Polyline, stringResource(R.string.model3d_tool_edge), tool == Tool3.EDGE) { vm.setTool(Tool3.EDGE) },
                    Seg(Icons.Filled.CropSquare, stringResource(R.string.model3d_tool_face), tool == Tool3.FACE) { vm.setTool(Tool3.FACE) },
                ),
            )
            Mode3.DRAW -> SubToolBar(
                listOf(
                    Seg(Icons.Filled.Brush, stringResource(R.string.model3d_tool_brush), tool == Tool3.DRAW) { vm.setTool(Tool3.DRAW) },
                    Seg(BrushIcons.Eraser, stringResource(R.string.model3d_tool_eraser), tool == Tool3.ERASE) { vm.setTool(Tool3.ERASE) },
                ),
            )
            Mode3.PAINT -> {
                val scope by vm.paintScope.collectAsState()
                SubToolBar(
                    listOf(
                        Seg(Icons.Filled.CropSquare, stringResource(R.string.model3d_paint_scope_face), scope == PaintScope.FACE) { vm.setPaintScope(PaintScope.FACE) },
                        Seg(Icons.Filled.Texture, stringResource(R.string.model3d_paint_scope_surface), scope == PaintScope.SURFACE) { vm.setPaintScope(PaintScope.SURFACE) },
                        Seg(Icons.Filled.ViewInAr, stringResource(R.string.model3d_paint_scope_part), scope == PaintScope.PART) { vm.setPaintScope(PaintScope.PART) },
                        Seg(Icons.Filled.Workspaces, stringResource(R.string.model3d_paint_scope_all), scope == PaintScope.ALL) { vm.setPaintScope(PaintScope.ALL) },
                    ),
                )
            }
            Mode3.ADD -> Unit
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeuIconButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), vm::undo, size = ROW_H, enabled = canUndo)
            Spacer(Modifier.width(6.dp))
            NeuIconButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo), vm::redo, size = ROW_H, enabled = canRedo)
            Spacer(Modifier.width(8.dp))
            Divider()
            // Mod ya da alt araç değişince satır baştan görünür
            val scroll = remember(mode, tool) { androidx.compose.foundation.ScrollState(0) }
            Box(Modifier.weight(1f)) {
                Row(
                    Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp, vertical = 6.dp).testTag("model3d_actions"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (mode) {
                        Mode3.SELECT -> SelectActions(vm, selection)
                        Mode3.EDIT -> EditActions(vm, tool, selection)
                        Mode3.DRAW -> DrawActions(vm, tool, onClearAll)
                        Mode3.PAINT -> PaintActions(vm)
                        Mode3.ADD -> AddActions(vm)
                    }
                }
                // Kaydırılabilen kenarda solma (satırın devamı olduğu anlaşılsın)
                val bg = NeuTheme.colors.background
                if (scroll.canScrollBackward) Box(
                    Modifier.align(Alignment.CenterStart).width(24.dp).height(ROW_H + 4.dp)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(bg, bg.copy(alpha = 0f)))),
                )
                if (scroll.canScrollForward) Box(
                    Modifier.align(Alignment.CenterEnd).width(24.dp).height(ROW_H + 4.dp)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(bg.copy(alpha = 0f), bg))),
                )
            }
        }
    }
}

@Composable
private fun AxisChip(vm: Model3DEditorViewModel) {
    val axis by vm.axis.collectAsState()
    val name = stringResource(when (axis) { 0 -> R.string.model3d_axis_x; 1 -> R.string.model3d_axis_y; 2 -> R.string.model3d_axis_z; else -> R.string.model3d_axis_free })
    // Dokundukça Serbest → X → Y → Z
    ActionPill(Icons.Filled.Straighten, stringResource(R.string.model3d_axis_cycle, name), { vm.setAxis(if (axis >= 2) -1 else axis + 1) }, selected = axis >= 0)
}

/** v27 seçim gerektiren düğme: seçim varsa işlem, yoksa ne seçileceğini söyler (düğme hep etkin görünür). */
private fun guard(vm: Model3DEditorViewModel, ok: Boolean, need: String, action: () -> Unit): () -> Unit = { if (ok) action() else vm.message(need) }

@Composable
private fun SelectActions(vm: Model3DEditorViewModel, selection: Selection3) {
    val panel by vm.panel.collectAsState()
    val model by vm.model.collectAsState()
    val has = selection.parts.isNotEmpty()
    val grouped = model?.let { m -> selection.parts.any { m.part(it)?.group != null } } == true
    val needPart = stringResource(R.string.model3d_need_part)
    AxisChip(vm)
    ActionPill(Icons.Filled.Workspaces, stringResource(R.string.model3d_group), guard(vm, selection.parts.size >= 2, stringResource(R.string.model3d_need_two_parts), vm::groupSelected))
    if (grouped) ActionPill(Icons.Filled.LinkOff, stringResource(R.string.model3d_ungroup), vm::ungroupSelected)
    ActionPill(Icons.Filled.ContentCopy, stringResource(R.string.model3d_duplicate), guard(vm, has, needPart, vm::duplicateSelected))
    ActionPill(Icons.Filled.VerticalAlignBottom, stringResource(R.string.model3d_ground), guard(vm, has, needPart, vm::dropSelectedToGround))
    ActionPill(Icons.Filled.JoinFull, stringResource(R.string.model3d_panel_ops), { vm.togglePanel(Panel3.OPS) }, selected = panel == Panel3.OPS)
    ActionPill(Icons.Filled.Palette, stringResource(R.string.model3d_panel_material), { vm.togglePanel(Panel3.MATERIAL) }, selected = panel == Panel3.MATERIAL)
    ActionPill(Icons.Filled.Tune, stringResource(R.string.model3d_panel_shape), { vm.togglePanel(Panel3.SHAPE) }, selected = panel == Panel3.SHAPE)
    ActionPill(Icons.Filled.Delete, stringResource(R.string.model3d_delete), guard(vm, has, needPart, vm::deleteSelected), danger = true)
}

/** v27 Düzenle'de seçim durumu (kaç nokta/kenar/yüz seçili) ve seçimi bırakma. */
@Composable
private fun SelectionStatus(text: String, any: Boolean, onClear: () -> Unit) {
    val colors = NeuTheme.colors
    Row(
        Modifier.height(ROW_H).background(if (any) colors.accent.copy(alpha = 0.12f) else Color.Transparent, NeuPillShape)
            .padding(start = 12.dp, end = if (any) 2.dp else 12.dp).testTag("model3d_edit_status"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (any) colors.accent else colors.contentMuted, maxLines = 1, softWrap = false)
        if (any) NeuIconButton(Icons.Filled.Close, stringResource(R.string.clear_selection), onClear, size = 34.dp)
    }
}

@Composable
private fun EditActions(vm: Model3DEditorViewModel, tool: Tool3, selection: Selection3) {
    when (tool) {
        Tool3.POINT -> {
            // v25 Nokta: kenara dokununca nokta eklenir; seçili noktalar (sırayla) silinir, birleştirilir, kesilir
            val n = selection.vertices.size
            val need = stringResource(R.string.model3d_need_points)
            SelectionStatus(if (n > 0) stringResource(R.string.model3d_points_selected, n) else stringResource(R.string.model3d_none_points), n > 0, vm::clearEdgeSelection)
            Divider()
            ActionPill(Icons.Filled.Timeline, stringResource(R.string.model3d_point_connect), guard(vm, n >= 2, need) { vm.connectSelectedPoints() })
            ActionPill(Icons.Filled.ContentCut, stringResource(R.string.model3d_point_cut), guard(vm, n >= 3, need, vm::cutSelectedLoop))
            ActionPill(Icons.Filled.Delete, stringResource(R.string.model3d_point_delete), guard(vm, n >= 1, need, vm::deleteSelectedPoints), danger = true)
        }
        Tool3.EDGE -> {
            val n = selection.edges.size
            val has = n > 0
            val need = stringResource(R.string.model3d_need_edges)
            SelectionStatus(if (has) stringResource(R.string.model3d_edges_selected, n) else stringResource(R.string.model3d_none_edges), has || selection.vertices.isNotEmpty(), vm::clearEdgeSelection)
            Divider()
            ActionPill(Icons.Filled.Gesture, stringResource(R.string.model3d_edge_bend), guard(vm, has, need) { vm.startOp(OpKind.BEND) })
            ActionPill(Icons.Filled.SwapHoriz, stringResource(R.string.model3d_edge_stretch), guard(vm, has, need) { vm.startOp(OpKind.STRETCH) })
            ActionPill(Icons.Filled.UnfoldMore, stringResource(R.string.model3d_edge_push), guard(vm, has, need) { vm.startOp(OpKind.PUSH) })
            ActionPill(Icons.Filled.RoundedCorner, stringResource(R.string.model3d_edge_round), guard(vm, has, need) { vm.startOp(OpKind.ROUND) })
            ActionPill(Icons.Filled.AddCircleOutline, stringResource(R.string.model3d_edge_split), guard(vm, has, need, vm::splitSelectedEdges))
            AxisChip(vm)
        }
        else -> {
            val n = selection.faces.size
            val has = n > 0
            val faceMode by vm.faceMode.collectAsState()
            val need = stringResource(R.string.model3d_need_faces)
            SelectionStatus(if (has) stringResource(R.string.model3d_faces_selected, n) else stringResource(R.string.model3d_none_faces), has) {
                vm.clearFaceSelection()
            }
            // v25 seçim kipi: tek yüz ya da dokunulan yüzün bütün yüzeyi (seçim ayarı, işlemlerden ayrı)
            NeuChip(stringResource(R.string.model3d_face_single), faceMode == FaceMode.SINGLE, onClick = { vm.setFaceMode(FaceMode.SINGLE) }, modifier = Modifier.height(ROW_H), icon = Icons.Filled.CropSquare)
            NeuChip(stringResource(R.string.model3d_face_surface), faceMode == FaceMode.SURFACE, onClick = { vm.setFaceMode(FaceMode.SURFACE) }, modifier = Modifier.height(ROW_H), icon = Icons.Filled.Texture)
            Divider()
            ActionPill(Icons.Filled.Add, stringResource(R.string.model3d_extrude), guard(vm, has, need) { vm.startOp(OpKind.EXTRUDE) })
            ActionPill(Icons.Filled.CropSquare, stringResource(R.string.model3d_inset), guard(vm, has, need) { vm.startOp(OpKind.INSET) })
            ActionPill(Icons.Filled.ZoomOutMap, stringResource(R.string.model3d_face_size), guard(vm, has, need) { vm.startOp(OpKind.FACE_SCALE) })
            ActionPill(Icons.Filled.DonutLarge, stringResource(R.string.model3d_hole), guard(vm, has, need) { vm.startOp(OpKind.HOLE) })
            ActionPill(Icons.Filled.FlipToFront, stringResource(R.string.model3d_detach), guard(vm, has, need, vm::detachFaces))
            ActionPill(Icons.Filled.FormatColorFill, stringResource(R.string.model3d_paint_faces), guard(vm, has, need) { vm.paintSelection() })
            ActionPill(Icons.Filled.Delete, stringResource(R.string.model3d_delete_faces), guard(vm, has, need, vm::deleteFaces), danger = true)
            AxisChip(vm)
        }
    }
}

@Composable
private fun DrawActions(vm: Model3DEditorViewModel, tool: Tool3, onClearAll: () -> Unit) {
    val panel by vm.panel.collectAsState()
    val brush by vm.brush.collectAsState()
    val plane by vm.plane.collectAsState()
    val model by vm.model.collectAsState()
    val drawings = model?.let { Model3DEditorViewModel.hasDrawings(it) } == true
    if (tool == Tool3.ERASE) {
        val size by vm.eraserSize.collectAsState()
        listOf(14f to R.string.model3d_eraser_small, 24f to R.string.model3d_eraser_medium, 42f to R.string.model3d_eraser_large).forEach { (dp, label) ->
            NeuChip(stringResource(label), kotlin.math.abs(size - dp) < 0.5f, onClick = { vm.setEraserSize(dp) }, modifier = Modifier.height(ROW_H))
        }
    } else {
        ActionPill(Icons.Filled.Brush, stringResource(R.string.model3d_brush_kind_chip, brushLabel3(brush.kind)), { vm.togglePanel(Panel3.BRUSH) }, selected = panel == Panel3.BRUSH)
        val planes = listOf(DrawPlane.FRONT to R.string.model3d_plane_front, DrawPlane.SIDE to R.string.model3d_plane_side, DrawPlane.TOP to R.string.model3d_plane_top, DrawPlane.VIEW to R.string.model3d_plane_view, DrawPlane.FACE to R.string.model3d_plane_face)
        val i = planes.indexOfFirst { it.first == plane }.coerceAtLeast(0)
        // Dokundukça Ön → Yan → Üst → Ekran → Seçili yüz
        ActionPill(Icons.Filled.CropSquare, stringResource(R.string.model3d_plane_cycle, stringResource(planes[i].second)), { vm.setPlane(planes[(i + 1) % planes.size].first) })
        // v27 çizim önerisi açık/kapalı (çizgiden sonra öneri baloncuğu)
        val suggestOn by vm.suggestEnabled.collectAsState()
        ActionPill(Icons.Filled.AutoAwesome, stringResource(R.string.model3d_suggest_toggle), { vm.setSuggestEnabled(!suggestOn) }, selected = suggestOn)
    }
    Divider()
    ActionPill(Icons.Filled.Backspace, stringResource(R.string.model3d_delete_last), vm::deleteLastDrawing, enabled = drawings)
    ActionPill(Icons.Filled.DeleteSweep, stringResource(R.string.model3d_clear_all), onClearAll, enabled = drawings, danger = true)
}

@Composable
private fun brushLabel3(k: Brush3Kind): String = stringResource(
    when (k) {
        Brush3Kind.LINE -> R.string.model3d_brush_line
        Brush3Kind.TUBE -> R.string.model3d_brush_tube
        Brush3Kind.RIBBON -> R.string.model3d_brush_ribbon
        Brush3Kind.WALL -> R.string.model3d_brush_wall
        Brush3Kind.LATHE -> R.string.model3d_brush_lathe
        Brush3Kind.INFLATE -> R.string.model3d_brush_inflate
    },
)

@Composable
private fun PaintActions(vm: Model3DEditorViewModel) {
    val paint by vm.paint.collectAsState()
    val panel by vm.panel.collectAsState()
    Box(
        Modifier.size(ROW_H - 4.dp).clip(RoundedCornerShape(12.dp)).background(Color(paint.color)).clickable { vm.togglePanel(Panel3.MATERIAL) }
            .semantics { contentDescription = "#%06X".format(0xFFFFFF and paint.color) },
    )
    ActionPill(Icons.Filled.Palette, stringResource(R.string.model3d_panel_material), { vm.togglePanel(Panel3.MATERIAL) }, selected = panel == Panel3.MATERIAL)
}

@Composable
private fun AddActions(vm: Model3DEditorViewModel) {
    val panel by vm.panel.collectAsState()
    ActionPill(Icons.Filled.Apps, stringResource(R.string.model3d_all_shapes), { vm.togglePanel(Panel3.ADD) }, selected = panel == Panel3.ADD)
    listOf(PrimitiveKind.CUBE, PrimitiveKind.SPHERE, PrimitiveKind.CYLINDER, PrimitiveKind.CONE, PrimitiveKind.PYRAMID, PrimitiveKind.TORUS).forEach { k ->
        ActionPill(Icons.Filled.ViewInAr, ModelEdit.primitiveName(k), { vm.addPart(k) })
    }
}

/**
 * v24 kaydırıcı işlemi çubuğu: işlemin adı ve açıklaması, miktar kaydırıcısı (sürerken sonuç önizlenir), Tamam
 * (tek geri alma adımı) ve Vazgeç.
 */
@Composable
private fun OpBar(vm: Model3DEditorViewModel, op: SliderOp) {
    val colors = NeuTheme.colors
    val (title, hint) = when (op.kind) {
        OpKind.BEND -> R.string.model3d_edge_bend to R.string.model3d_op_bend
        OpKind.STRETCH -> R.string.model3d_edge_stretch to R.string.model3d_op_stretch
        OpKind.PUSH -> R.string.model3d_edge_push to R.string.model3d_op_push
        OpKind.ROUND -> R.string.model3d_edge_round to R.string.model3d_op_round
        OpKind.EXTRUDE -> R.string.model3d_extrude to R.string.model3d_op_extrude
        OpKind.INSET -> R.string.model3d_inset to R.string.model3d_op_inset
        OpKind.FACE_SCALE -> R.string.model3d_face_size to R.string.model3d_op_face_scale
        OpKind.HOLE -> R.string.model3d_hole to R.string.model3d_op_hole
    }
    NeuSurface(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).testTag("model3d_op"), elevation = 6.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = colors.accent, modifier = Modifier.weight(1f))
                Text(
                    when (op.kind) {
                        OpKind.STRETCH, OpKind.FACE_SCALE -> String.format(java.util.Locale.ROOT, "×%.2f", op.amount)
                        OpKind.HOLE -> "%${(op.amount * 100).toInt()}"
                        else -> String.format(java.util.Locale.ROOT, "%+.2f", op.amount)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                stringResource(if (op.failed) R.string.model3d_op_failed else hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (op.failed) colors.danger else colors.contentMuted,
            )
            Slider(
                value = op.amount,
                onValueChange = vm::setOpAmount,
                valueRange = op.kind.min..op.kind.max,
                modifier = Modifier.fillMaxWidth().testTag("model3d_op_slider"),
                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuButton(stringResource(R.string.model3d_op_cancel), vm::cancelOp, Modifier.weight(1f))
                NeuButton(stringResource(R.string.model3d_op_ok), vm::applyOp, Modifier.weight(1f), primary = true, enabled = !op.failed)
            }
        }
    }
}

/** v24 modeli GLB olarak paylaşır (geçici dosya, dokular gömülü). */
private suspend fun shareGlb(context: android.content.Context, vm: Model3DEditorViewModel) {
    val name = (vm.model.value?.name ?: "model").replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val r = runCatching {
        val bytes = vm.exportGlb() ?: error("model yok")
        val file = withContext(Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "models3d").apply { mkdirs() }
            java.io.File(dir, "$name.glb").apply { writeBytes(bytes) }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "model/gltf-binary"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(send, name).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    r.exceptionOrNull()?.let { vm.message(context.getString(R.string.model3d_export_failed, it.message ?: "")) }
}

/** v27 öneri baloncuğunun görünür kaldığı süre (ms). */
private const val SUGGEST_MS = 12_000L

/** v27 araç satırı ve alt araç seçicinin yüksekliği (eski 36 dp yerine). */
private val ROW_H = 42.dp

/** Yönergenin görünür kaldığı süre (ms). */
private const val HINT_MS = 4500L
