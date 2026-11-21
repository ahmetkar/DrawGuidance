package com.karapp.drawguidance.ui.screens.sketch3d

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.karapp.drawguidance.content.model3d.Brush3D
import com.karapp.drawguidance.content.model3d.PlaneSnap
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.components.NeuTopBar
import com.karapp.drawguidance.ui.components.isWideLayout
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.model3d.ImageExportSheet
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** Açık yan panel. */
enum class SketchPanel { LAYERS, PROPS }

/**
 * v56 3B Çizim ekranı (ana sayfadaki 3B Çizimlerim'den açılır): tuval arayüzünün düzeni. Üstte ad, Katmanlar,
 * Özellikler, Kaydet ve menü (yeniden adlandır, farklı kaydet, sığdır, resim olarak kaydet); ortada sahne; altta modun
 * araç satırı ve mod çubuğu (Çiz · Düzlem · Seç · Ekle · Görünüm). Geniş ekranda panel sağda, dar ekranda altta açılır.
 */
@Composable
fun Sketch3DScreen(
    modelId: String?,
    onBack: () -> Unit,
    initialName: String? = null,
    /** v56 aşama 12 tuvalin Diğer menüsünden açıldıysa o tuval ("Tuvale ekle" doğrudan ona döner). */
    sourceCanvas: String? = null,
    /** "Tuvale ekle" ile başka bir tuval seçilince o tuvali açar. */
    onOpenCanvas: (String) -> Unit = {},
    vm: Sketch3DViewModel = koinViewModel(key = "sketch3d-$modelId") { parametersOf(modelId ?: "", initialName ?: "") },
) {
    val colors = NeuTheme.colors
    val model by vm.model.collectAsState()
    val mode by vm.mode.collectAsState()
    val dirty by vm.dirty.collectAsState()
    var panel by remember { mutableStateOf<SketchPanel?>(null) }
    var exitAsk by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var savingAs by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var imageExport by remember { mutableStateOf(false) }
    var canvasPick by remember { mutableStateOf(false) }
    var videoSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val videoSavedText = stringResource(R.string.sk3_video_saved)
    val shareTitle = stringResource(R.string.sk3_video_share)
    var message by remember { mutableStateOf<String?>(null) }
    val savedText = stringResource(R.string.model3d_saved)

    LaunchedEffect(vm) {
        vm.events.collectLatest { e ->
            when (e) {
                is SketchEvent.Saved -> message = savedText.format(e.name)
                SketchEvent.Exit -> onBack()
                is SketchEvent.Message -> message = e.text
                is SketchEvent.AddedToCanvas -> if (e.canvasId == sourceCanvas) { if (dirty) vm.save(); onBack() } else { if (dirty) vm.save(); onOpenCanvas(e.canvasId) }
                is SketchEvent.VideoSaved -> {
                    message = videoSavedText
                    runCatching {
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", e.file)
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType(e.mime)
                            .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(android.content.Intent.createChooser(send, shareTitle).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
    LaunchedEffect(message) { if (message != null) { delay(2200); message = null } }

    val tryBack = { if (dirty) exitAsk = true else onBack() }
    BackHandler { if (panel != null) panel = null else tryBack() }

    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().testTag("sketch3d_screen")) {
        NeuTopBar(
            title = model?.name ?: "",
            subtitle = stringResource(R.string.sk3_title),
            navigation = { NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tryBack) },
            actions = {
                NeuIconButton(Icons.Filled.Layers, stringResource(R.string.sk3_layers), { panel = if (panel == SketchPanel.LAYERS) null else SketchPanel.LAYERS }, size = 38.dp, selected = panel == SketchPanel.LAYERS, modifier = Modifier.testTag("sketch3d_layers"))
                NeuIconButton(Icons.Filled.Tune, stringResource(R.string.sk3_props), { panel = if (panel == SketchPanel.PROPS) null else SketchPanel.PROPS }, size = 38.dp, selected = panel == SketchPanel.PROPS, modifier = Modifier.testTag("sketch3d_props"))
                NeuIconButton(Icons.Filled.Save, stringResource(R.string.model3d_save), { vm.save() }, size = 38.dp, selected = dirty, modifier = Modifier.testTag("sketch3d_save"))
                Box {
                    NeuIconButton(Icons.Filled.MoreVert, stringResource(R.string.more_options), { menu = true }, size = 38.dp)
                    DropdownMenu(menu, { menu = false }, modifier = Modifier.background(colors.background)) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.model3d_rename)) }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.model3d_save_as)) }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; savingAs = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.model3d_fit)) }, leadingIcon = { Icon(Icons.Filled.CenterFocusStrong, null) }, onClick = { menu = false; vm.fitView() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.model3d_export_image)) }, leadingIcon = { Icon(Icons.Filled.Image, null) },
                            onClick = { menu = false; imageExport = true }, modifier = Modifier.testTag("sketch3d_export_image"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sk3_add_to_canvas)) }, leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, null) },
                            onClick = { menu = false; if (sourceCanvas != null) vm.addToCanvas(sourceCanvas) else canvasPick = true },
                            modifier = Modifier.testTag("sketch3d_add_to_canvas"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sk3_video)) }, leadingIcon = { Icon(Icons.Filled.Videocam, null) },
                            onClick = { menu = false; videoSheet = true }, modifier = Modifier.testTag("sketch3d_video"),
                        )
                    }
                }
            },
        )
        val wide = isWideLayout()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val panelMax = maxHeight * 0.5f
            val scene: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize()) {
                    Sketch3DViewport(vm, model)
                    message?.let { t ->
                        NeuSurface(Modifier.align(Alignment.TopCenter).padding(top = 10.dp), shape = NeuPillShape, elevation = 4.dp) {
                            Text(t, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    PlaneWarning(vm, mode, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { scene() }
                    panel?.let { p -> PanelFrame(p, { panel = null }, Modifier.width(320.dp).fillMaxHeight().padding(8.dp)) { PanelBody(vm, p) } }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { scene() }
                    panel?.let { p -> PanelFrame(p, { panel = null }, Modifier.fillMaxWidth().heightIn(max = panelMax).padding(horizontal = 8.dp, vertical = 4.dp)) { PanelBody(vm, p) } }
                }
            }
        }
        ToolRow(vm, mode)
        ModeBar(mode, vm::setMode)
    }

    if (exitAsk) {
        AlertDialog(
            onDismissRequest = { exitAsk = false },
            containerColor = colors.background,
            title = { Text(stringResource(R.string.model3d_exit_title)) },
            text = { Text(stringResource(R.string.model3d_exit_text)) },
            confirmButton = { TextButton(onClick = { exitAsk = false; vm.save(exitAfter = true) }) { Text(stringResource(R.string.model3d_save)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { exitAsk = false }) { Text(stringResource(R.string.cancel_button)) }
                    TextButton(onClick = { exitAsk = false; onBack() }) { Text(stringResource(R.string.model3d_discard), color = colors.danger) }
                }
            },
        )
    }
    if (renaming) {
        com.karapp.drawguidance.ui.components.TextInputSheet(
            title = stringResource(R.string.model3d_rename), initial = model?.name ?: "", label = stringResource(R.string.model3d_name),
            onConfirm = { vm.rename(it); renaming = false }, onDismiss = { renaming = false },
        )
    }
    if (savingAs) {
        com.karapp.drawguidance.ui.components.TextInputSheet(
            title = stringResource(R.string.model3d_save_as), initial = (model?.name ?: "") + " (2)", label = stringResource(R.string.model3d_name),
            onConfirm = { vm.saveAs(it); savingAs = false }, onDismiss = { savingAs = false },
        )
    }
    if (imageExport) ImageExportSheet(vm) { imageExport = false }
    if (canvasPick) CanvasPickDialog(onPick = { id -> canvasPick = false; vm.addToCanvas(id) }, onDismiss = { canvasPick = false })
    if (videoSheet) VideoDialog(vm, onStart = { gif, speed ->
        vm.exportVideo(gif, speed, java.io.File(context.cacheDir, "models3d/video"), context.applicationContext)
    }, onDismiss = { videoSheet = false })
    com.karapp.drawguidance.ui.components.LoadingOverlay(visible = model == null, message = stringResource(R.string.loading_model))
}

/** Düzlem kameraya 75°'den fazla yatıksa uyarı ve "Kameraya çevir". */
@Composable
private fun PlaneWarning(vm: Sketch3DViewModel, mode: SketchMode, modifier: Modifier) {
    val model by vm.model.collectAsState()
    val plane by vm.plane.collectAsState()
    if (mode != SketchMode.DRAW && mode != SketchMode.PLANE) return
    // model (kamera) ve düzlem değişince yeniden hesaplanır
    val tilted = remember(model?.camera, plane) { vm.planeTilted() }
    if (!tilted) return
    NeuSurface(modifier.testTag("sketch3d_tilt_warning"), shape = NeuPillShape, elevation = 4.dp) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sk3_plane_tilted), color = Color(0xFFE65100), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = vm::planeToCamera) { Text(stringResource(R.string.sk3_plane_to_camera)) }
        }
    }
}

@Composable
private fun PanelFrame(panel: SketchPanel, onClose: () -> Unit, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    NeuSurface(modifier.testTag("sketch3d_panel"), elevation = 4.dp) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (panel == SketchPanel.LAYERS) R.string.sk3_layers else R.string.sk3_props), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                NeuIconButton(Icons.Filled.Close, stringResource(R.string.close), onClose, size = 34.dp)
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }
}

@Composable
private fun PanelBody(vm: Sketch3DViewModel, panel: SketchPanel) {
    when (panel) {
        SketchPanel.LAYERS -> LayersPanel(vm)
        SketchPanel.PROPS -> PropsPanel(vm)
    }
}

@Composable
private fun LayersPanel(vm: Sketch3DViewModel) {
    val colors = NeuTheme.colors
    val model by vm.model.collectAsState()
    val active by vm.activeLayer.collectAsState()
    val selection by vm.selection.collectAsState()
    var renaming by remember { mutableStateOf<String?>(null) }
    val layers = model?.layers ?: return
    layers.asReversed().forEach { l ->
        val on = l.id == active
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(NeuPillShape)
                .background(if (on) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { vm.setActiveLayer(l.id) }.padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("sketch3d_layer_${l.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(l.name, Modifier.weight(1f), color = if (on) colors.accent else colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            NeuIconButton(if (l.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, stringResource(R.string.sk3_layer_hidden), { vm.toggleLayerVisible(l.id) }, size = 32.dp)
            NeuIconButton(if (l.locked) Icons.Filled.Lock else Icons.Filled.LockOpen, stringResource(R.string.sk3_layer_locked), { vm.toggleLayerLocked(l.id) }, size = 32.dp)
            NeuIconButton(Icons.Filled.ArrowUpward, null, { vm.moveLayer(l.id, 1) }, size = 32.dp)
            NeuIconButton(Icons.Filled.ArrowDownward, null, { vm.moveLayer(l.id, -1) }, size = 32.dp)
            NeuIconButton(Icons.Filled.Edit, stringResource(R.string.sk3_rename_layer), { renaming = l.id }, size = 32.dp)
            if (layers.size > 1) NeuIconButton(Icons.Filled.Delete, stringResource(R.string.sk3_delete_layer), { vm.deleteLayer(l.id) }, size = 32.dp)
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(Icons.Filled.Add, stringResource(R.string.sk3_add_layer), vm::addLayer, tag = "sketch3d_add_layer")
        if (selection.isNotEmpty()) Pill(Icons.Filled.Layers, stringResource(R.string.sk3_layer_to), { vm.moveSelectionToLayer(active) })
    }
    renaming?.let { id ->
        com.karapp.drawguidance.ui.components.TextInputSheet(
            title = stringResource(R.string.sk3_rename_layer), initial = layers.firstOrNull { it.id == id }?.name ?: "", label = stringResource(R.string.model3d_name),
            onConfirm = { vm.renameLayer(id, it); renaming = null }, onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun PropsPanel(vm: Sketch3DViewModel) {
    val size by vm.size.collectAsState()
    val opacity by vm.opacity.collectAsState()
    val eraser by vm.eraserSize.collectAsState()
    val finger by vm.fingerDraw.collectAsState()
    val color by vm.color.collectAsState()
    val selection by vm.selection.collectAsState()
    Label(stringResource(R.string.sk3_color))
    ColorRow(color) { c -> vm.setColor(c); if (selection.isNotEmpty()) vm.colorSelected(c) }
    Label(stringResource(R.string.sk3_size) + " · ${size.toInt()}")
    NeuSlider(size, vm::setSize, 1f..80f)
    Label(stringResource(R.string.sk3_opacity) + " · ${(opacity * 100).toInt()}%")
    NeuSlider(opacity, vm::setOpacity, 0.05f..1f)
    Label(stringResource(R.string.sk3_eraser_size) + " · ${eraser.toInt()}")
    NeuSlider(eraser, vm::setEraserSize, 6f..60f)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.sk3_finger), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.sk3_finger_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
        }
        NeuSwitch(finger, vm::setFingerDraw, Modifier.testTag("sketch3d_finger"))
    }
    Text(stringResource(R.string.sk3_hold_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
}

@Composable
private fun Label(t: String) { Text(t, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }

private val PALETTE = listOf(
    0xFF263238, 0xFF000000, 0xFFFFFFFF, 0xFF9E9E9E, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
    0xFF00ACC1, 0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFF8D6E63, 0xFFF5F1E8, 0xFFFFCCBC, 0xFFB2DFDB,
).map { it.toInt() }

@Composable
private fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    val colors = NeuTheme.colors
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PALETTE.forEach { c ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                    .clickable { onPick(c) }.testTag("sketch3d_color_${Integer.toHexString(c)}"),
            )
        }
    }
}

// --------------------------------------------------------------------------------------------------- araç satırı

@Composable
private fun ToolRow(vm: Sketch3DViewModel, mode: SketchMode) {
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp).testTag("sketch3d_tools"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeuIconButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), vm::undo, size = ROW_H, enabled = canUndo)
        Spacer(Modifier.width(6.dp))
        NeuIconButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo), vm::redo, size = ROW_H, enabled = canRedo)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            when (mode) {
                SketchMode.DRAW -> DrawTools(vm)
                SketchMode.PLANE -> PlaneTools(vm)
                SketchMode.SELECT -> SelectTools(vm)
                SketchMode.ADD -> AddTools(vm)
                SketchMode.VIEW -> ViewTools(vm)
            }
        }
    }
}

@Composable
private fun DrawTools(vm: Sketch3DViewModel) {
    val tool by vm.tool.collectAsState()
    val brush by vm.brush.collectAsState()
    val pen = tool == SketchTool.PEN
    Pill(Icons.Filled.Create, stringResource(R.string.sk3_pen), { vm.setBrush(Brush3D.PEN) }, selected = pen && brush == Brush3D.PEN, tag = "sketch3d_brush_pen")
    Pill(Icons.Filled.Edit, stringResource(R.string.sk3_ink), { vm.setBrush(Brush3D.INK) }, selected = pen && brush == Brush3D.INK, tag = "sketch3d_brush_ink")
    Pill(Icons.Filled.Brush, stringResource(R.string.sk3_marker), { vm.setBrush(Brush3D.MARKER) }, selected = pen && brush == Brush3D.MARKER, tag = "sketch3d_brush_marker")
    Pill(Icons.Filled.FormatPaint, stringResource(R.string.sk3_wide), { vm.setBrush(Brush3D.WIDE) }, selected = pen && brush == Brush3D.WIDE, tag = "sketch3d_brush_wide")
    Pill(Icons.Filled.FormatColorFill, stringResource(R.string.sk3_fill), { vm.setTool(SketchTool.FILL) }, selected = tool == SketchTool.FILL, tag = "sketch3d_fill")
    Pill(Icons.Filled.AutoFixNormal, stringResource(R.string.sk3_eraser), { vm.setTool(SketchTool.ERASER) }, selected = tool == SketchTool.ERASER, tag = "sketch3d_eraser")
    Pill(Icons.Filled.Delete, stringResource(R.string.sk3_clear), vm::clearDrawings, danger = true)
}

@Composable
private fun PlaneTools(vm: Sketch3DViewModel) {
    val plane by vm.plane.collectAsState()
    Pill(Icons.Filled.ViewInAr, stringResource(R.string.sk3_plane_front), { vm.setPlanePreset(PlaneSnap.Preset.FRONT) }, selected = plane.id == "front", tag = "sketch3d_plane_front")
    Pill(Icons.Filled.ViewInAr, stringResource(R.string.sk3_plane_side), { vm.setPlanePreset(PlaneSnap.Preset.SIDE) }, selected = plane.id == "side", tag = "sketch3d_plane_side")
    Pill(Icons.Filled.ViewInAr, stringResource(R.string.sk3_plane_top), { vm.setPlanePreset(PlaneSnap.Preset.TOP) }, selected = plane.id == "top", tag = "sketch3d_plane_top")
    Pill(Icons.Filled.CenterFocusStrong, stringResource(R.string.sk3_plane_camera), { vm.setPlanePreset(PlaneSnap.Preset.CAMERA) }, selected = plane.id == "camera", tag = "sketch3d_plane_camera")
    Pill(Icons.Filled._3dRotation, stringResource(R.string.sk3_tilt_fwd), { vm.rotatePlane(0, PlaneSnap.STEP_DEG) }, tag = "sketch3d_plane_tilt")
    Pill(Icons.Filled._3dRotation, stringResource(R.string.sk3_turn_side), { vm.rotatePlane(1, PlaneSnap.STEP_DEG) }, tag = "sketch3d_plane_turn")
    Pill(Icons.Filled.RotateRight, stringResource(R.string.sk3_spin), { vm.rotatePlane(2, PlaneSnap.STEP_DEG) }, tag = "sketch3d_plane_spin")
}

@Composable
private fun SelectTools(vm: Sketch3DViewModel) {
    val xf by vm.xform.collectAsState()
    val axis by vm.axis.collectAsState()
    val sel by vm.selection.collectAsState()
    Pill(Icons.Filled.OpenWith, stringResource(R.string.sk3_xf_move), { vm.setXform(SketchXform.MOVE) }, selected = xf == SketchXform.MOVE, tag = "sketch3d_xf_move")
    Pill(Icons.Filled.RotateRight, stringResource(R.string.sk3_xf_rotate), { vm.setXform(SketchXform.ROTATE) }, selected = xf == SketchXform.ROTATE, tag = "sketch3d_xf_rotate")
    Pill(Icons.Filled.ZoomOutMap, stringResource(R.string.sk3_xf_scale), { vm.setXform(SketchXform.SCALE) }, selected = xf == SketchXform.SCALE, tag = "sketch3d_xf_scale")
    if (xf == SketchXform.ROTATE) {
        listOf("X", "Y", "Z").forEachIndexed { i, n -> Pill(null, n, { vm.setAxis(i) }, selected = axis == i) }
        Pill(Icons.Filled.RotateRight, stringResource(R.string.sk3_snap), { vm.rotateSelected(PlaneSnap.STEP_DEG) }, enabled = sel.isNotEmpty(), tag = "sketch3d_rotate_step")
    }
    Pill(Icons.Filled.Flip, stringResource(R.string.sk3_flip), vm::flipSelected, enabled = sel.isNotEmpty())
    val model by vm.model.collectAsState()
    val selParts = model?.parts?.filter { it.id in sel }.orEmpty()
    val glowing = selParts.isNotEmpty() && selParts.all { it.material.kind == com.karapp.drawguidance.content.model3d.MaterialKind.GLOW }
    Pill(
        Icons.Filled.AutoAwesome, stringResource(R.string.sk3_glow),
        { vm.materialSelected(if (glowing) com.karapp.drawguidance.content.model3d.MaterialKind.PLASTIC else com.karapp.drawguidance.content.model3d.MaterialKind.GLOW) },
        enabled = selParts.isNotEmpty(), selected = glowing, tag = "sketch3d_glow",
    )
    Pill(Icons.Filled.ContentCopy, stringResource(R.string.sk3_duplicate), vm::duplicateSelected, enabled = sel.isNotEmpty(), tag = "sketch3d_duplicate")
    Pill(Icons.Filled.VerticalAlignBottom, stringResource(R.string.sk3_ground), vm::dropSelectedToGround, enabled = sel.isNotEmpty())
    Pill(Icons.Filled.SelectAll, stringResource(R.string.sk3_select_all), vm::selectAll)
    Pill(Icons.Filled.Delete, stringResource(R.string.sk3_delete), vm::deleteSelected, enabled = sel.isNotEmpty(), danger = true, tag = "sketch3d_delete")
}

@Composable
private fun AddTools(vm: Sketch3DViewModel) {
    listOf(
        PrimitiveKind.CUBE to R.string.sk3_cube, PrimitiveKind.CYLINDER to R.string.sk3_cylinder, PrimitiveKind.SPHERE to R.string.sk3_sphere,
        PrimitiveKind.CONE to R.string.sk3_cone, PrimitiveKind.PLANE to R.string.sk3_slab,
    ).forEach { (k, label) -> Pill(Icons.Filled.AddBox, stringResource(label), { vm.addPrimitive(k) }, tag = "sketch3d_add_${k.name.lowercase()}") }
}

@Composable
private fun ViewTools(vm: Sketch3DViewModel) {
    val model by vm.model.collectAsState()
    val scene = model?.scene ?: return
    val cam = model?.camera ?: return
    var lightOpen by remember { mutableStateOf(false) }
    val style by vm.style.collectAsState()
    listOf(
        com.karapp.drawguidance.content.model3d.RenderStyle.SOFT to R.string.sk3_style_soft,
        com.karapp.drawguidance.content.model3d.RenderStyle.TOON to R.string.sk3_style_toon,
        com.karapp.drawguidance.content.model3d.RenderStyle.LINES to R.string.sk3_style_lines,
    ).forEach { (st, label) -> Pill(null, stringResource(label), { vm.setStyle(st) }, selected = style == st, tag = "sketch3d_style_$st") }
    Pill(Icons.Filled.WbSunny, stringResource(R.string.sk3_light_dir), { lightOpen = !lightOpen }, selected = lightOpen, tag = "sketch3d_light")
    Pill(null, stringResource(R.string.sk3_shadow), { vm.setScene(scene.copy(shadow = !scene.shadow)) }, selected = scene.shadow, tag = "sketch3d_shadow")
    Pill(null, stringResource(R.string.sk3_ambient), { vm.setScene(scene.copy(light = scene.light.copy(ambient = if (scene.light.ambient > 0.3f) 0.15f else 0.38f))) }, selected = scene.light.ambient > 0.3f)
    Pill(Icons.Filled.GridOn, stringResource(R.string.sk3_grid), { vm.setScene(scene.copy(ground = !scene.ground)) }, selected = scene.ground)
    Pill(null, stringResource(R.string.sk3_iso), { vm.presetView(0) })
    Pill(null, stringResource(R.string.sk3_plane_front), { vm.presetView(1) })
    Pill(null, stringResource(R.string.sk3_plane_side), { vm.presetView(2) })
    Pill(null, stringResource(R.string.sk3_plane_top), { vm.presetView(3) })
    Pill(null, stringResource(R.string.sk3_perspective), { vm.setPerspective(if (cam.persp > 0.05f) 0f else 0.35f) }, selected = cam.persp > 0.05f)
    listOf(0, 0xFFFFFFFF.toInt(), 0xFFF5F1E8.toInt(), 0xFF263238.toInt()).forEach { bg ->
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (bg == 0) NeuTheme.colors.background else Color(bg))
                .border(if (scene.background == bg) 3.dp else 1.dp, if (scene.background == bg) NeuTheme.colors.accent else NeuTheme.colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                .clickable { vm.setScene(scene.copy(background = bg)) }
                .semantics { contentDescription = "bg" },
        )
    }
    if (lightOpen) {
        Column(Modifier.width(220.dp)) {
            Text(stringResource(R.string.sk3_light_dir), style = MaterialTheme.typography.labelMedium)
            NeuSlider(scene.light.yaw, { vm.setScene(scene.copy(light = scene.light.copy(yaw = it))) }, 0f..360f)
            Text(stringResource(R.string.sk3_light_height), style = MaterialTheme.typography.labelMedium)
            NeuSlider(scene.light.pitch, { vm.setScene(scene.copy(light = scene.light.copy(pitch = it))) }, 5f..89f)
        }
    }
}

// --------------------------------------------------------------------------------------------------- tuvale ekle / video

/** v56 aşama 12 "Tuvale ekle": hedef tuval seçimi (çocuk tuvalleri hariç, en son değişen önce). */
@Composable
private fun CanvasPickDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val repo = org.koin.compose.koinInject<com.karapp.drawguidance.data.repository.CanvasRepository>()
    val canvases by remember(repo) { repo.observeCanvases() }.collectAsState(initial = null)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.sk3_pick_canvas)) },
        text = {
            val list = canvases?.filter { it.kind != com.karapp.drawguidance.data.model.CanvasKind.KIDS }?.sortedByDescending { it.updatedAt }
            when {
                list == null -> Text(stringResource(R.string.loading_model))
                list.isEmpty() -> Text(stringResource(R.string.sk3_no_canvas))
                else -> Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).testTag("sketch3d_canvas_list")) {
                    list.forEach { c ->
                        Text(
                            c.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clip(NeuPillShape).clickable { onPick(c.localId) }.padding(horizontal = 12.dp, vertical = 12.dp)
                                .testTag("sketch3d_canvas_${c.localId}"),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) } },
    )
}

/** v56 aşama 13 dönen video: biçim (MP4 1080p / GIF), hız ve ilerleme. */
@Composable
private fun VideoDialog(vm: Sketch3DViewModel, onStart: (Boolean, Float) -> Unit, onDismiss: () -> Unit) {
    val progress by vm.videoProgress.collectAsState()
    var gif by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1f) }
    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        containerColor = NeuTheme.colors.background,
        title = { Text(stringResource(R.string.sk3_video)) },
        text = {
            Column(Modifier.testTag("sketch3d_video_sheet")) {
                Text(stringResource(R.string.sk3_video_hint), style = MaterialTheme.typography.bodyMedium)
                Label(stringResource(R.string.sk3_video_format))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(null, "MP4 1080p", { gif = false }, selected = !gif, enabled = progress == null, tag = "sketch3d_video_mp4")
                    Pill(null, "GIF", { gif = true }, selected = gif, enabled = progress == null, tag = "sketch3d_video_gif")
                }
                Label(stringResource(R.string.sk3_video_speed))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f, 1f, 2f).forEach { sp -> Pill(null, "${if (sp == 0.5f) "0.5" else sp.toInt().toString()}×", { speed = sp }, selected = speed == sp, enabled = progress == null, tag = "sketch3d_video_speed_$sp") }
                }
                progress?.let { p ->
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().testTag("sketch3d_video_progress"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(gif, speed) }, enabled = progress == null, modifier = Modifier.testTag("sketch3d_video_start")) { Text(stringResource(R.string.sk3_video_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = progress == null) { Text(stringResource(R.string.cancel_button)) } },
    )
}

/** Araç satırında düğme: simge ve kısa ad. */
@Composable
private fun Pill(icon: ImageVector?, label: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false, selected: Boolean = false, tag: String? = null) {
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
            .padding(horizontal = 12.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

private class ModeItem(val mode: SketchMode, val icon: ImageVector, val label: Int)

private val ModeItems = listOf(
    ModeItem(SketchMode.DRAW, Icons.Filled.Create, R.string.sk3_mode_draw),
    ModeItem(SketchMode.PLANE, Icons.Filled.GridOn, R.string.sk3_mode_plane),
    ModeItem(SketchMode.SELECT, Icons.Filled.OpenWith, R.string.sk3_mode_select),
    ModeItem(SketchMode.ADD, Icons.Filled.AddBox, R.string.sk3_mode_add),
    ModeItem(SketchMode.VIEW, Icons.Filled.WbSunny, R.string.sk3_mode_view),
)

@Composable
private fun ModeBar(mode: SketchMode, onMode: (SketchMode) -> Unit) {
    val colors = NeuTheme.colors
    NeuSurface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).testTag("sketch3d_modes"),
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
                        .semantics { contentDescription = label }
                        .testTag("sketch3d_mode_${item.mode.name.lowercase()}"),
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

private val ROW_H = 42.dp
