package com.karapp.drawguidance.ui.screens.model3d

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.JoinFull
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.JoinInner
import androidx.compose.material.icons.filled.JoinLeft
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Splitscreen
import com.karapp.drawguidance.content.model3d.Brush3Kind
import com.karapp.drawguidance.content.model3d.Solid3
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.model3d.Camera3
import com.karapp.drawguidance.content.model3d.Material
import com.karapp.drawguidance.content.model3d.MaterialKind
import com.karapp.drawguidance.content.model3d.Model3D
import com.karapp.drawguidance.content.model3d.ModelEdit
import com.karapp.drawguidance.content.model3d.PrimitiveKind
import com.karapp.drawguidance.content.model3d.Primitives
import com.karapp.drawguidance.data.settings.Model3DPrefs
import com.karapp.drawguidance.content.model3d.RenderOptions
import com.karapp.drawguidance.content.model3d.Renderer
import com.karapp.drawguidance.content.model3d.SceneSettings
import com.karapp.drawguidance.content.model3d.UvMode
import com.karapp.drawguidance.content.model3d.V3
import com.karapp.drawguidance.content.model3d.View3
import com.karapp.drawguidance.data.images.CanvasImageCache
import com.karapp.drawguidance.data.library.TextureRepository
import com.karapp.drawguidance.ui.components.LabeledValueSlider
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSectionTitle
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

/** 3B renk paleti. */
private val Palette3D = listOf(
    0xFFFFFFFF, 0xFFECEFF1, 0xFF9E9E9E, 0xFF37474F, 0xFF212121, 0xFFE53935, 0xFFE07A5F, 0xFFF2CC8F, 0xFFFFD54F, 0xFF81B29A,
    0xFF43A047, 0xFF6EC1E4, 0xFF4F8BD6, 0xFF3949AB, 0xFF9C6ADE, 0xFFE5989B, 0xFF8D6E63, 0xFFB98B55,
).map { it.toInt() }

@Composable
fun PanelContent(vm: Model3DEditorViewModel, panel: Panel3, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(14.dp).testTag("model3d_panel_${panel.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (panel) {
            Panel3.ADD -> AddPanel(vm)
            Panel3.PARTS -> PartsPanel(vm)
            Panel3.MATERIAL -> MaterialPanel(vm)
            Panel3.SHAPE -> ShapePanel(vm)
            Panel3.SETTINGS -> SettingsPanel(vm)
            Panel3.BRUSH -> BrushPanel(vm)
            Panel3.OPS -> OpsPanel(vm)
        }
    }
}

@Composable
private fun ColorRow(selected: Int?, onPick: (Int) -> Unit, extra: List<Int> = emptyList()) {
    val colors = NeuTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (extra + Palette3D).forEach { c ->
            val chosen = selected != null && (selected or 0xFF000000.toInt()) == (c or 0xFF000000.toInt())
            val hex = String.format("#%06X", 0xFFFFFF and c)
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                    .padding(4.dp).background(Color(c), CircleShape)
                    .clickable { onPick(c) }
                    .semantics { contentDescription = hex; if (chosen) this.selected = true },
            )
        }
    }
}

// --------------------------------------------------------------------------------------------------- Ekle

/** Hazır cisimlerin küçük resimleri (bir kez çizilir). */
private object PrimitiveThumbs {
    private val cache = HashMap<PrimitiveKind, ImageBitmap>()

    @Synchronized
    fun cached(kind: PrimitiveKind): ImageBitmap? = cache[kind]

    @Synchronized
    fun get(kind: PrimitiveKind): ImageBitmap {
        cache[kind]?.let { return it }
        val part = ModelEdit.newPart(kind, "t", 0)
        val m = Model3D("t", "t", listOf(part), scene = SceneSettings(shadow = false, ground = false, edges = true), camera = Camera3(yaw = 35f, pitch = 24f))
        val size = 112
        val f = Renderer.render(m, View3.fit(m, m.camera, size, size, 0.12f), RenderOptions(supersample = 2, ground = false, shadow = false, background = 0))
        val bmp = Bitmap.createBitmap(f.color, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
        cache[kind] = bmp
        return bmp
    }
}

@Composable
private fun AddPanel(vm: Model3DEditorViewModel) {
    // v27 GLB içe aktarma: parçalar bu modele eklenir; ayrıca 3B Şekillerim'e ayrı şekil olarak kaydedilip
    // kaydedilmeyeceği sorulur
    var pending by remember { mutableStateOf<PendingGlb?>(null) }
    val failText = stringResource(R.string.glb_import_failed)
    val pick = rememberGlbPicker(onRead = { pending = it }, onFail = { vm.message(failText.format(it)) })
    NeuButton(stringResource(R.string.glb_import_button), pick, Modifier.fillMaxWidth().testTag("model3d_import_glb"), icon = Icons.Filled.FileUpload)
    pending?.let { p ->
        GlbImportAskDialog(p.name, p.bytes.size, onImport = { tris, save -> pending = null; vm.importGlbParts(p.bytes, p.name, tris, save) }, onDismiss = { pending = null })
    }
    NeuSectionTitle(stringResource(R.string.model3d_primitives))
    Text(stringResource(R.string.model3d_add_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
    PrimitiveKind.entries.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { kind ->
                val name = ModelEdit.primitiveName(kind)
                // v27 panel açılırken çizim başlamaz; sınırlı iş parçacığında
                val thumb = com.karapp.drawguidance.ui.components.rememberKeyedThumb(kind, PrimitiveThumbs::cached, settleFrames = SETTLE_FRAMES) { PrimitiveThumbs.get(it) }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.addPart(kind) }.semantics { contentDescription = name }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(56.dp)) { thumb?.let { Image(it, null, Modifier.fillMaxSize()) } }
                    Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    // v24 hazır şekiller (ders ve çizim için)
    NeuSectionTitle(stringResource(R.string.model3d_ready_shapes))
    Text(stringResource(R.string.model3d_ready_hint), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
    CatalogGrid(onPick = { vm.addCatalog(it.id) })
}

// --------------------------------------------------------------------------------------------------- Parçalar

/**
 * Parçalar: v26 gruplar başlık satırıyla (aç/kapat, görünürlük, yeniden adlandır, grubu çöz; başlığa dokununca bütün
 * grup seçilir), üyeleri içeride. Parça satırına dokunmak yalnız o parçayı seçer (tek parçayı düzenlemek için).
 */
@Composable
private fun PartsPanel(vm: Model3DEditorViewModel) {
    val colors = NeuTheme.colors
    val model by vm.model.collectAsState()
    val selection by vm.selection.collectAsState()
    var renaming by remember { mutableStateOf<String?>(null) }
    var renamingGroup by remember { mutableStateOf<String?>(null) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val m = model ?: return
    Text(stringResource(R.string.model3d_parts_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
    val grouped = selection.parts.any { m.part(it)?.group != null }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeuButton(stringResource(R.string.model3d_group), vm::groupSelected, Modifier.weight(1f), enabled = selection.parts.size >= 2, icon = Icons.Filled.Workspaces)
        NeuButton(stringResource(R.string.model3d_ungroup), vm::ungroupSelected, Modifier.weight(1f), enabled = grouped, icon = Icons.Filled.LinkOff)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeuButton(stringResource(R.string.model3d_merge), vm::mergeSelected, Modifier.weight(1f), enabled = selection.parts.size >= 2)
        NeuButton(stringResource(R.string.model3d_duplicate), vm::duplicateSelected, Modifier.weight(1f), enabled = selection.parts.isNotEmpty())
    }
    val shown = HashSet<String>()
    // v27 çok parçalı (içe aktarılan) modellerde satırlar karelere yayılır; grup üyeleri bir kez çıkarılır (O(n))
    val groupsById = remember(m.groups) { m.groups.associateBy { it.id } }
    val membersOf = remember(m.parts) { m.parts.asReversed().filter { it.group != null }.groupBy { it.group!! } }
    val rowBudget = com.karapp.drawguidance.ui.components.rememberProgressiveCount(m.parts.size, m.id, first = 24, step = 16)
    var rows = 0
    m.parts.asReversed().forEach { p ->
        if (rows >= rowBudget) return@forEach
        val g = p.group?.let { id -> groupsById[id] }
        if (g == null) { rows++; PartRow(vm, p, selection, indent = false) { renaming = it }; return@forEach }
        if (!shown.add(g.id)) return@forEach
        val members = membersOf[g.id].orEmpty()
        rows += members.size
        val open = g.id !in collapsed
        val all = members.all { it.id in selection.parts }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .then(if (all) Modifier.background(colors.accent.copy(alpha = 0.12f)) else Modifier)
                .clickable { vm.selectGroup(g.id) }.padding(horizontal = 6.dp, vertical = 4.dp)
                .semantics { contentDescription = g.name; if (all) selected = true }
                .testTag("model3d_group_row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NeuIconButton(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, stringResource(R.string.model3d_group_expand),
                { collapsed = if (open) collapsed + g.id else collapsed - g.id }, size = 30.dp,
            )
            Icon(if (open) Icons.Filled.FolderOpen else Icons.Filled.Folder, null, tint = colors.accent, modifier = Modifier.padding(horizontal = 6.dp).size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (members.any { it.visible }) colors.content else colors.contentMuted)
                Text(stringResource(R.string.model3d_group_count, members.size), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
            }
            NeuIconButton(if (members.any { it.visible }) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, stringResource(R.string.model3d_part_visible), { vm.toggleGroupVisible(g.id) }, size = 30.dp)
            Spacer(Modifier.width(4.dp))
            NeuIconButton(Icons.Filled.Edit, stringResource(R.string.model3d_group_rename), { renamingGroup = g.id }, size = 30.dp)
            Spacer(Modifier.width(4.dp))
            NeuIconButton(Icons.Filled.LinkOff, stringResource(R.string.model3d_ungroup), { vm.ungroup(g.id) }, size = 30.dp)
        }
        if (open) members.forEach { PartRow(vm, it, selection, indent = true) { id -> renaming = id } }
    }
    renaming?.let { id ->
        com.karapp.drawguidance.ui.components.TextInputSheet(
            title = stringResource(R.string.model3d_rename), initial = m.part(id)?.name ?: "", label = stringResource(R.string.model3d_name),
            onConfirm = { vm.renamePart(id, it); renaming = null }, onDismiss = { renaming = null },
        )
    }
    renamingGroup?.let { id ->
        com.karapp.drawguidance.ui.components.TextInputSheet(
            title = stringResource(R.string.model3d_group_rename), initial = m.groups.firstOrNull { it.id == id }?.name ?: "", label = stringResource(R.string.model3d_name),
            onConfirm = { vm.renameGroup(id, it); renamingGroup = null }, onDismiss = { renamingGroup = null },
        )
    }
}

@Composable
private fun PartRow(vm: Model3DEditorViewModel, p: com.karapp.drawguidance.content.model3d.Part, selection: Selection3, indent: Boolean, onRename: (String) -> Unit) {
    val colors = NeuTheme.colors
    val chosen = p.id in selection.parts
    Row(
        Modifier.fillMaxWidth().padding(start = if (indent) 22.dp else 0.dp).clip(RoundedCornerShape(12.dp))
            .then(if (chosen) Modifier.background(colors.accent.copy(alpha = 0.12f)) else Modifier)
            .clickable { vm.selectPart(p.id) }.padding(horizontal = 6.dp, vertical = 4.dp)
            .semantics { contentDescription = p.name; if (chosen) selected = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (chosen) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, stringResource(R.string.model3d_part_multi),
            tint = if (chosen) colors.accent else colors.contentMuted,
            modifier = Modifier.size(26.dp).clickable { vm.selectPart(p.id, add = true) },
        )
        Box(Modifier.padding(horizontal = 6.dp).size(16.dp).clip(CircleShape).background(Color(p.material.color)))
        Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (p.visible) colors.content else colors.contentMuted)
        NeuIconButton(if (p.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, stringResource(R.string.model3d_part_visible), { vm.togglePartVisible(p.id) }, size = 30.dp)
        Spacer(Modifier.width(4.dp))
        NeuIconButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.layer_up), { vm.movePartInList(p.id, 1) }, size = 30.dp)
        Spacer(Modifier.width(4.dp))
        NeuIconButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.layer_down), { vm.movePartInList(p.id, -1) }, size = 30.dp)
        Spacer(Modifier.width(4.dp))
        NeuIconButton(Icons.Filled.Edit, stringResource(R.string.model3d_rename), { onRename(p.id) }, size = 30.dp)
        Spacer(Modifier.width(4.dp))
        NeuIconButton(Icons.Filled.Delete, stringResource(R.string.model3d_delete), { vm.selectPart(p.id); vm.deleteSelected() }, size = 30.dp, tint = colors.danger)
    }
}

// --------------------------------------------------------------------------------------------------- Malzeme

@Composable
private fun MaterialPanel(vm: Model3DEditorViewModel) {
    val colors = NeuTheme.colors
    val model by vm.model.collectAsState()
    val selection by vm.selection.collectAsState()
    val paint by vm.paint.collectAsState()
    val imagesVersion by CanvasImageCache.version.collectAsState()
    val tool by vm.tool.collectAsState()
    // v36 Boya aracında renk/doku yalnız fırçanın malzemesini değiştirir: seçili parça (son boyanan) bütünüyle
    // boyanmaz; boyama dokunulan yüze (kapsama göre yüzey/parça/tümü) uygulanır
    val target = remember(model, selection, tool) { if (tool == Tool3.PAINT) null else vm.selectedMaterial() }
    val mat = target ?: paint
    fun set(m: Material) { if (target != null) vm.setMaterial(m) else vm.setPaint(m) }
    var moreColor by remember { mutableStateOf(false) }
    Text(
        stringResource(if (target != null) (if (selection.faces.isNotEmpty()) R.string.model3d_material_faces else R.string.model3d_material_parts) else R.string.model3d_material_paint),
        style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
    )
    Text(stringResource(R.string.model3d_color), style = MaterialTheme.typography.labelLarge)
    ColorRow(mat.color, { set(mat.copy(color = it)) })
    NeuButton(stringResource(R.string.model3d_more_colors), { moreColor = true })
    // v27 gradyan renklendirme: rengin ikinci renge parçanın ekseni boyunca geçişi
    Text(stringResource(R.string.model3d_gradient), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("model3d_gradient"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeuChip(stringResource(R.string.model3d_gradient_none), mat.gradient == null, onClick = { set(mat.copy(gradient = null)) })
        listOf(1 to R.string.model3d_gradient_vertical, 0 to R.string.model3d_gradient_horizontal, 2 to R.string.model3d_gradient_depth).forEach { (axis, label) ->
            NeuChip(stringResource(label), mat.gradient != null && mat.gradientAxis == axis, onClick = {
                set(mat.copy(gradient = mat.gradient ?: secondColor(mat.color), gradientAxis = axis))
            })
        }
    }
    mat.gradient?.let { g ->
        Text(stringResource(R.string.model3d_gradient_second), style = MaterialTheme.typography.labelMedium, color = colors.contentMuted)
        ColorRow(g, { set(mat.copy(gradient = it)) })
    }
    Text(stringResource(R.string.model3d_texture), style = MaterialTheme.typography.labelLarge)
    val items = vm.textureItems
    // v27 doku kutuları karelere yayılır (panel hemen açılır)
    var texBudget = com.karapp.drawguidance.ui.components.rememberProgressiveCount(items.size, items.size, first = 16, step = 12)
    remember(items) { items.groupBy { it.category } }.forEach { (category, all) ->
        if (texBudget <= 0) return@forEach
        val list = all.take(texBudget)
        texBudget -= list.size
        Text(category, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (category == items.first().category) {
                TextureTile(stringResource(R.string.model3d_no_texture), mat.texture == null, null, imagesVersion) { set(mat.copy(texture = null)) }
            }
            list.forEach { t ->
                TextureTile(t.name, mat.texture == t.id, t.id, imagesVersion) { set(mat.copy(texture = t.id, gradient = null, color = if (mat.texture == null) 0xFFFFFFFF.toInt() else mat.color)) }
            }
        }
    }
    if (mat.texture != null) {
        LabeledValueSlider(stringResource(R.string.model3d_texture_scale), "${(mat.textureScale * 100).roundToInt()}%", mat.textureScale, 0.1f..4f) {
            set(mat.copy(textureScale = (it * 20).roundToInt() / 20f))
        }
    }
    Text(stringResource(R.string.model3d_material_kind), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MaterialKind.entries.forEach { k -> NeuChip(Model3DEditorViewModel.kindLabel(k), mat.kind == k, onClick = { set(mat.copy(kind = k)) }) }
    }
    LabeledValueSlider(stringResource(R.string.model3d_shine), "${(mat.shine * 100).roundToInt()}%", mat.shine, 0f..1f) { set(mat.copy(shine = it)) }
    LabeledValueSlider(stringResource(R.string.model3d_opacity), "${(mat.opacity * 100).roundToInt()}%", mat.opacity, 0.1f..1f) { set(mat.copy(opacity = it)) }
    val primary = selection.primary?.let { model?.part(it) }
    if (primary != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.model3d_smooth), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            NeuSwitch(primary.smooth, { vm.setSmooth(it) })
        }
        Text(stringResource(R.string.model3d_uv), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(UvMode.BOX to R.string.model3d_uv_box, UvMode.CYLINDER to R.string.model3d_uv_cylinder, UvMode.SPHERE to R.string.model3d_uv_sphere, UvMode.PLANAR to R.string.model3d_uv_planar)
                .forEach { (u, label) -> NeuChip(stringResource(label), primary.uv == u, onClick = { vm.setUv(u) }) }
        }
    }
    if (moreColor) {
        com.karapp.drawguidance.ui.screens.canvasedit.components.PartColorSheet(
            title = stringResource(R.string.model3d_color),
            colorHex = String.format("#%06X", 0xFFFFFF and mat.color),
            onColorChange = { hex -> set(mat.copy(color = runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(mat.color))) },
            onDismiss = { moreColor = false },
        )
    }
}

@Composable
private fun TextureTile(name: String, selected: Boolean, id: String?, version: Int, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(64.dp).clickable(onClick = onClick).semantics { contentDescription = name; if (selected) this.selected = true }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(shape).border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.contentMuted.copy(alpha = 0.3f), shape)
                .background(colors.background),
            contentAlignment = Alignment.Center,
        ) {
            if (id == null) Icon(Icons.Filled.Block, null, tint = colors.contentMuted)
            else {
                val bmp = if (version >= 0) CanvasImageCache.get(TextureRepository.KEY + id) else null
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (selected) colors.accent else colors.content)
    }
}

// --------------------------------------------------------------------------------------------------- Cisim

@Composable
private fun ShapePanel(vm: Model3DEditorViewModel) {
    val colors = NeuTheme.colors
    val model by vm.model.collectAsState()
    val selection by vm.selection.collectAsState()
    val p = selection.primary?.let { model?.part(it) }
    if (p == null) {
        Text(stringResource(R.string.model3d_select_part_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        return
    }
    Text(p.name, style = MaterialTheme.typography.titleSmall, color = colors.accent)
    val spec = p.primitive
    if (spec != null) {
        // v24 ayrıntı sınırı: her cihazda hızlı düzenleme (yumuşak gölgeleme eğrileri pürüzsüz gösterir)
        Text(stringResource(R.string.model3d_detail_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        LabeledValueSlider(stringResource(R.string.model3d_segments), "${spec.segments}", spec.segments.toFloat(), 3f..Primitives.MAX_SEGMENTS.toFloat()) {
            vm.setPrimitiveSpec(spec.copy(segments = it.roundToInt()))
        }
        if (spec.kind in setOf(PrimitiveKind.SPHERE, PrimitiveKind.DOME, PrimitiveKind.CAPSULE, PrimitiveKind.TORUS, PrimitiveKind.PLANE)) {
            LabeledValueSlider(stringResource(R.string.model3d_rings), "${spec.rings}", spec.rings.toFloat(), 2f..Primitives.MAX_RINGS.toFloat()) { vm.setPrimitiveSpec(spec.copy(rings = it.roundToInt())) }
        }
        if (spec.kind in setOf(PrimitiveKind.CYLINDER, PrimitiveKind.CONE, PrimitiveKind.PRISM3, PrimitiveKind.PRISM6, PrimitiveKind.PYRAMID)) {
            LabeledValueSlider(stringResource(R.string.model3d_top), "${(spec.top * 100).roundToInt()}%", spec.top, 0f..1.5f) { vm.setPrimitiveSpec(spec.copy(top = (it * 20).roundToInt() / 20f)) }
        }
        if (spec.kind in setOf(PrimitiveKind.TORUS, PrimitiveKind.TUBE, PrimitiveKind.STAR)) {
            LabeledValueSlider(stringResource(R.string.model3d_thickness), "${(spec.thickness * 100).roundToInt()}%", spec.thickness, 0.05f..0.9f) { vm.setPrimitiveSpec(spec.copy(thickness = it)) }
        }
    } else {
        Text(stringResource(R.string.model3d_mesh_edited), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
    }
    Text(stringResource(R.string.model3d_position), style = MaterialTheme.typography.labelLarge)
    listOf("X", "Y", "Z").forEachIndexed { i, a ->
        LabeledValueSlider(a, String.format(java.util.Locale.ROOT, "%.2f", p.position.get(i)), p.position.get(i), -6f..6f) { vm.setTransform(p.id, position = p.position.with(i, (it * 50).roundToInt() / 50f)) }
    }
    Text(stringResource(R.string.model3d_rotation), style = MaterialTheme.typography.labelLarge)
    listOf("X", "Y", "Z").forEachIndexed { i, a ->
        LabeledValueSlider(a, "${p.rotation.get(i).roundToInt()}°", p.rotation.get(i), -180f..180f) { vm.setTransform(p.id, rotation = p.rotation.with(i, it.roundToInt().toFloat())) }
    }
    Text(stringResource(R.string.model3d_scale), style = MaterialTheme.typography.labelLarge)
    listOf("X", "Y", "Z").forEachIndexed { i, a ->
        LabeledValueSlider(a, String.format(java.util.Locale.ROOT, "%.2f", p.scale.get(i)), p.scale.get(i), 0.05f..6f) { vm.setTransform(p.id, scale = p.scale.with(i, (it * 50).roundToInt() / 50f)) }
    }
}

// --------------------------------------------------------------------------------------------------- Sahne

/**
 * v24 Ayarlar: düzenli bölümler: Işık, Görünüm (gölge, kenar çizgileri, ızgara, arka plan), Kamera (perspektif, hazır
 * açılar) ve Başarım (ekran kartıyla çizim, görüntü kalitesi).
 */
@Composable
private fun SettingsPanel(vm: Model3DEditorViewModel) {
    val colors = NeuTheme.colors
    val model by vm.model.collectAsState()
    val gpuOn by vm.gpuPreference.collectAsState()
    val gpuOk by vm.gpuAvailable.collectAsState()
    val gpuInfo by vm.gpuInfo.collectAsState()
    val quality by vm.quality.collectAsState()
    val m = model ?: return
    val s = m.scene
    val l = s.light

    SettingsSection(stringResource(R.string.model3d_section_light)) {
        LabeledValueSlider(stringResource(R.string.model3d_light_yaw), "${l.yaw.roundToInt()}°", l.yaw, 0f..360f) { vm.setScene(s.copy(light = l.copy(yaw = it))) }
        LabeledValueSlider(stringResource(R.string.model3d_light_pitch), "${l.pitch.roundToInt()}°", l.pitch, 5f..90f) { vm.setScene(s.copy(light = l.copy(pitch = it))) }
        LabeledValueSlider(stringResource(R.string.model3d_light_power), "${(l.intensity * 100).roundToInt()}%", l.intensity, 0f..2f) { vm.setScene(s.copy(light = l.copy(intensity = it))) }
        LabeledValueSlider(stringResource(R.string.model3d_ambient), "${(l.ambient * 100).roundToInt()}%", l.ambient, 0f..1f) { vm.setScene(s.copy(light = l.copy(ambient = it))) }
        Text(stringResource(R.string.model3d_light_color), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
        ColorRow(l.color, { vm.setScene(s.copy(light = l.copy(color = it))) }, extra = listOf(0xFFFFF3D6.toInt(), 0xFFDDEBFF.toInt()))
    }

    SettingsSection(stringResource(R.string.model3d_section_look)) {
        SwitchRow(stringResource(R.string.model3d_shadow), s.shadow) { vm.setScene(s.copy(shadow = it), null) }
        SwitchRow(stringResource(R.string.model3d_edges), s.edges) { vm.setScene(s.copy(edges = it), null) }
        if (s.edges) ColorRow(s.edgeColor, { vm.setScene(s.copy(edgeColor = it)) })
        SwitchRow(stringResource(R.string.model3d_ground_grid), s.ground) { vm.setScene(s.copy(ground = it), null) }
        Text(stringResource(R.string.model3d_background), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuChip(stringResource(R.string.model3d_transparent), s.background == 0, onClick = { vm.setScene(s.copy(background = 0), null) })
        }
        ColorRow(s.background.takeIf { it != 0 }, { vm.setScene(s.copy(background = it or 0xFF000000.toInt()), null) })
    }

    SettingsSection(stringResource(R.string.model3d_section_camera)) {
        LabeledValueSlider(stringResource(R.string.model3d_perspective), "${(m.camera.persp * 100).roundToInt()}%", m.camera.persp, 0f..1f) { vm.setPerspective(it) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to R.string.model3d_view_iso, 1 to R.string.model3d_view_front, 2 to R.string.model3d_view_side, 3 to R.string.model3d_view_top).forEach { (k, label) ->
                NeuChip(stringResource(label), false, onClick = { vm.presetView(k) })
            }
        }
    }

    // v27 çizim önerisi (cihazda öğrenen)
    SettingsSection(stringResource(R.string.model3d_section_suggest)) {
        val suggestOn by vm.suggestEnabled.collectAsState()
        SwitchRow(stringResource(R.string.model3d_suggest_toggle_long), suggestOn) { vm.setSuggestEnabled(it) }
        Text(stringResource(R.string.model3d_suggest_info), style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
    }

    SettingsSection(stringResource(R.string.model3d_section_performance)) {
        SwitchRow(stringResource(R.string.model3d_gpu), gpuOn) { vm.setGpu(it) }
        Text(
            when {
                !gpuOn -> stringResource(R.string.model3d_gpu_off)
                gpuOk == null -> stringResource(R.string.model3d_gpu_checking)
                gpuOk == true -> stringResource(R.string.model3d_gpu_on, gpuInfo ?: "OpenGL ES 2.0")
                else -> stringResource(R.string.model3d_gpu_unavailable, gpuInfo ?: "?")
            },
            style = MaterialTheme.typography.bodySmall, color = colors.contentMuted,
            modifier = Modifier.testTag("model3d_gpu_status"),
        )
        Text(stringResource(R.string.model3d_quality), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Model3DPrefs.QUALITY_HIGH to R.string.model3d_quality_high,
                Model3DPrefs.QUALITY_BALANCED to R.string.model3d_quality_balanced,
                Model3DPrefs.QUALITY_FAST to R.string.model3d_quality_fast,
            ).forEach { (q, label) -> NeuChip(stringResource(label), quality == q, onClick = { vm.setQuality(q) }) }
        }
        Text(stringResource(R.string.model3d_quality_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
    }
}

/** Ayarlar bölümü: başlık ve çerçeveli içerik. */
@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val colors = NeuTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.accent.copy(alpha = 0.05f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.accent)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        NeuSwitch(checked, onChange)
    }
}

// --------------------------------------------------------------------------------------------------- Fırça

/**
 * v25 3B fırça: tür (Çizgi 3B çizgi çizer; Tüp, Şerit, Duvar, Torna ve Şişir izden 3B cisim oluşturur), renk, kalınlık
 * ve türe göre yükseklik, şişkinlik, uç incelmesi; yumuşatma, kalem basıncı, malzeme ve örtücülük (tuval fırçası gibi).
 */
@Composable
private fun BrushPanel(vm: Model3DEditorViewModel) {
    val colors = NeuTheme.colors
    val color by vm.brushColor.collectAsState()
    val width by vm.brushWidth.collectAsState()
    val b by vm.brush.collectAsState()
    val solid = b.kind != Brush3Kind.LINE
    Text(stringResource(R.string.model3d_brush_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
    Text(stringResource(R.string.model3d_brush_kind), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("model3d_brush_kinds"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Brush3Kind.entries.forEach { k -> NeuChip(stringResource(brushLabel(k)), b.kind == k, onClick = { vm.setBrush(b.copy(kind = k)) }) }
    }
    Text(stringResource(brushHint(b.kind)), style = MaterialTheme.typography.bodySmall, color = colors.accent)
    ColorRow(color, vm::setBrushColor)
    LabeledValueSlider(stringResource(R.string.model3d_brush_width), "${width.roundToInt()} px", width, 1f..60f) { vm.setBrushWidth(it.roundToInt().toFloat()) }
    if (b.kind == Brush3Kind.WALL) {
        LabeledValueSlider(stringResource(R.string.model3d_brush_height), String.format(java.util.Locale.ROOT, "%.2f", b.height), b.height, 0.05f..3f) {
            vm.setBrush(b.copy(height = (it * 20).roundToInt() / 20f))
        }
    }
    if (b.kind == Brush3Kind.INFLATE) {
        LabeledValueSlider(stringResource(R.string.model3d_brush_bulge), "${(b.bulge * 100).roundToInt()}%", b.bulge, 0.2f..1.5f) { vm.setBrush(b.copy(bulge = it)) }
    }
    if (b.kind == Brush3Kind.TUBE || b.kind == Brush3Kind.RIBBON || b.kind == Brush3Kind.WALL) {
        LabeledValueSlider(stringResource(R.string.model3d_brush_taper), "${(b.taper * 100).roundToInt()}%", b.taper, 0f..1f) { vm.setBrush(b.copy(taper = it)) }
    }
    if (solid) {
        LabeledValueSlider(stringResource(R.string.model3d_brush_smoothing), "${(b.smoothing * 100).roundToInt()}%", b.smoothing, 0f..1f) { vm.setBrush(b.copy(smoothing = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.model3d_brush_pressure), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            NeuSwitch(b.pressure, { vm.setBrush(b.copy(pressure = it)) })
        }
        Text(stringResource(R.string.model3d_brush_material), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialKind.entries.forEach { k -> NeuChip(Model3DEditorViewModel.kindLabel(k), b.material == k, onClick = { vm.setBrush(b.copy(material = k)) }) }
        }
        LabeledValueSlider(stringResource(R.string.model3d_brush_opacity), "${(b.opacity * 100).roundToInt()}%", b.opacity, 0.1f..1f) { vm.setBrush(b.copy(opacity = it)) }
    }
}

private fun brushLabel(k: Brush3Kind): Int = when (k) {
    Brush3Kind.LINE -> R.string.model3d_brush_line
    Brush3Kind.TUBE -> R.string.model3d_brush_tube
    Brush3Kind.RIBBON -> R.string.model3d_brush_ribbon
    Brush3Kind.WALL -> R.string.model3d_brush_wall
    Brush3Kind.LATHE -> R.string.model3d_brush_lathe
    Brush3Kind.INFLATE -> R.string.model3d_brush_inflate
}

private fun brushHint(k: Brush3Kind): Int = when (k) {
    Brush3Kind.LINE -> R.string.model3d_brush_line_hint
    Brush3Kind.TUBE -> R.string.model3d_brush_tube_hint
    Brush3Kind.RIBBON -> R.string.model3d_brush_ribbon_hint
    Brush3Kind.WALL -> R.string.model3d_brush_wall_hint
    Brush3Kind.LATHE -> R.string.model3d_brush_lathe_hint
    Brush3Kind.INFLATE -> R.string.model3d_brush_inflate_hint
}

// --------------------------------------------------------------------------------------------------- İşlemler

/**
 * v25 İşlemler: seçili parçalarda birleştir (kaynaştır), çıkar, kesişim ve grupla; düzlemle parçala (eksen ve konum,
 * düzlem sahnede gösterilir); delik aç (genişlik, önizleme çemberi); kopuk parçaları ayır. Uzun işlemler arka planda.
 */
@Composable
private fun OpsPanel(vm: Model3DEditorViewModel) {
    val colors = NeuTheme.colors
    val model by vm.model.collectAsState()
    val selection by vm.selection.collectAsState()
    val slice by vm.slice.collectAsState()
    val busy by vm.busy.collectAsState()
    val holeRatio by vm.holeRatio.collectAsState()
    val m = model ?: return
    val parts = selection.parts.mapNotNull { m.part(it) }
    val primary = parts.firstOrNull()
    val idle = busy == null
    Text(
        if (parts.isEmpty()) stringResource(R.string.model3d_ops_none) else stringResource(R.string.model3d_ops_selected, parts.joinToString { it.name }),
        style = MaterialTheme.typography.bodySmall, color = if (parts.isEmpty()) colors.contentMuted else colors.accent,
        modifier = Modifier.testTag("model3d_ops_selection"),
    )
    SettingsSection(stringResource(R.string.model3d_ops_booleans)) {
        Text(stringResource(R.string.model3d_ops_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        val two = parts.size >= 2 && idle
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuButton(stringResource(R.string.model3d_union), { vm.booleanSelected(Solid3.Op.UNION) }, Modifier.weight(1f), enabled = two, icon = Icons.Filled.JoinFull)
            NeuButton(stringResource(R.string.model3d_subtract), { vm.booleanSelected(Solid3.Op.SUBTRACT) }, Modifier.weight(1f), enabled = two, icon = Icons.Filled.JoinLeft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuButton(stringResource(R.string.model3d_intersect), { vm.booleanSelected(Solid3.Op.INTERSECT) }, Modifier.weight(1f), enabled = two, icon = Icons.Filled.JoinInner)
            NeuButton(stringResource(R.string.model3d_merge), vm::mergeSelected, Modifier.weight(1f), enabled = two, icon = Icons.Filled.CallMerge)
        }
    }
    SettingsSection(stringResource(R.string.model3d_ops_slice)) {
        Text(stringResource(R.string.model3d_ops_slice_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SliceAxis.X to "X", SliceAxis.Y to "Y", SliceAxis.Z to "Z").forEach { (a, label) -> NeuChip(label, slice.first == a, onClick = { vm.setSlice(axis = a) }) }
            NeuChip(stringResource(R.string.model3d_slice_view), slice.first == SliceAxis.VIEW, onClick = { vm.setSlice(axis = SliceAxis.VIEW) })
        }
        LabeledValueSlider(stringResource(R.string.model3d_slice_offset), "${(slice.second * 100).roundToInt()}%", slice.second, -0.49f..0.49f) {
            vm.setSlice(offset = (it * 50).roundToInt() / 50f)
        }
        NeuButton(stringResource(R.string.model3d_ops_slice), vm::sliceSelected, Modifier.fillMaxWidth(), primary = true, enabled = primary != null && idle, icon = Icons.Filled.Splitscreen)
    }
    SettingsSection(stringResource(R.string.model3d_ops_hole)) {
        Text(stringResource(R.string.model3d_ops_hole_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        LabeledValueSlider(stringResource(R.string.model3d_hole_size), "${(holeRatio * 100).roundToInt()}%", holeRatio, 0.05f..0.95f) { vm.setHoleRatio(it) }
        NeuButton(stringResource(R.string.model3d_hole), { vm.holeSelected() }, Modifier.fillMaxWidth(), enabled = primary != null && idle, icon = Icons.Filled.DonutLarge)
    }
    SettingsSection(stringResource(R.string.model3d_ops_separate)) {
        Text(stringResource(R.string.model3d_ops_separate_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        NeuButton(stringResource(R.string.model3d_ops_separate), vm::separateSelected, Modifier.fillMaxWidth(), enabled = primary != null && idle, icon = Icons.Filled.CallSplit)
    }
}

/** v27 gradyanın varsayılan ikinci rengi: rengin açık tonu (çok açık renkte koyu tonu). */
private fun secondColor(c: Int): Int {
    val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
    val lum = (0.3f * r + 0.59f * g + 0.11f * b) / 255f
    fun mix(v: Int, t: Int, k: Float) = (v + (t - v) * k).toInt().coerceIn(0, 255)
    val target = if (lum > 0.75f) 0 else 255
    val k = if (lum > 0.75f) 0.45f else 0.6f
    return (0xFF shl 24) or (mix(r, target, k) shl 16) or (mix(g, target, k) shl 8) or mix(b, target, k)
}
