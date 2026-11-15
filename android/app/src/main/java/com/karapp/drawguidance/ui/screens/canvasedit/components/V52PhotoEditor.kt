package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.comic.ComicFilter
import com.karapp.drawguidance.content.image.BackgroundOptions
import com.karapp.drawguidance.content.image.ImageAdjust
import com.karapp.drawguidance.content.image.ImageLook
import com.karapp.drawguidance.content.image.PhotoBrush
import com.karapp.drawguidance.content.image.PhotoCrop
import com.karapp.drawguidance.content.image.PhotoEdit
import com.karapp.drawguidance.content.image.PhotoLight
import com.karapp.drawguidance.content.image.PhotoOps
import com.karapp.drawguidance.content.image.PhotoStroke
import com.karapp.drawguidance.data.billing.PremiumFeature
import com.karapp.drawguidance.data.billing.PremiumRules
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSlider
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.ImageEditChoice
import com.karapp.drawguidance.ui.screens.canvasedit.state.PhotoEditController
import com.karapp.drawguidance.ui.theme.NeuTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** v52 foto düzenleme sekmeleri. */
enum class PhotoTab(val icon: ImageVector, val label: Int, val tag: String) {
    CROP(Icons.Filled.Crop, R.string.v52_photo_tab_crop, "crop"),
    LIGHT(Icons.Filled.WbSunny, R.string.v52_photo_tab_light, "light"),
    COLOR(Icons.Filled.Palette, R.string.v52_photo_tab_color, "color"),
    FILTERS(Icons.Filled.PhotoFilter, R.string.v52_photo_tab_filters, "filters"),
    EFFECTS(Icons.Filled.AutoAwesome, R.string.v52_photo_tab_effects, "effects"),
    // v53 arka plan kaldırma ve kırpma maskesi aynı menüde ("Maske")
    BACKGROUND(Icons.Filled.AutoFixHigh, R.string.v53_photo_tab_mask, "background"),
    RETOUCH(Icons.Filled.Brush, R.string.v52_photo_tab_retouch, "retouch"),
}

private val RETOUCH_COLORS = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFFF8BBD0, 0xFF8D6E63,
).map { it.toInt() }

/** v52 aspect seçenekleri (null: serbest, 0: özgün). */
private val ASPECTS: List<Pair<String?, Float?>> = listOf(null to null, "orig" to 0f, "1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f, "9:16" to 9f / 16f)

/**
 * v52 Foto düzenleme modu (tam ekran): üstte Kapat, Geri al, Yinele, Önce/sonra (basılı tut), Sıfırla, Kaydet; ortada
 * önizleme (iki parmakla yakınlaştır/kaydır); altta menülere ayrılmış bütün raster düzenlemeler.
 */
@Composable
fun PhotoEditorHost(photo: PhotoEditController) {
    val openId by photo.open.collectAsState()
    if (openId == null) return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false, dismissOnBackPress = false),
    ) {
        PhotoEditorScreen(photo)
    }
}

@Composable
private fun PhotoEditorScreen(photo: PhotoEditController) {
    val colors = NeuTheme.colors
    val edit by photo.edit.collectAsState()
    val preview by photo.preview.collectAsState()
    val original by photo.original.collectAsState()
    val loading by photo.loading.collectAsState()
    val rendering by photo.rendering.collectAsState()
    val saving by photo.saving.collectAsState()
    val history by photo.history.collectAsState()
    val cropMode by photo.cropMode.collectAsState()
    val premium = com.karapp.drawguidance.ui.premium.LocalPremium.current
    var tab by remember { mutableStateOf(PhotoTab.CROP) }
    var comparing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var aspectKey by remember { mutableStateOf<String?>(null) }
    // Rötuş
    var brush by remember { mutableStateOf(PhotoBrush.LIGHTEN) }
    var brushSize by remember { mutableStateOf(0.08f) }
    var brushStrength by remember { mutableStateOf(0.6f) }
    var brushColor by remember { mutableStateOf(RETOUCH_COLORS[2]) }
    // Arka plan dokunma aracı (true: kaldır)
    var bgRemoveTap by remember { mutableStateOf(true) }
    LaunchedEffect(tab) { photo.setCropMode(tab == PhotoTab.CROP) }
    androidx.activity.compose.BackHandler { if (history.first > 0) confirmDiscard = true else photo.close() }

    fun save() {
        // Premium: arka plan kaldırma ve gelişmiş görünüm/ayar/filtre
        if (edit.background != null && !premium.request(PremiumFeature.IMAGE_BG_REMOVAL)) return
        val advanced = PremiumRules.isAdvancedLook(edit.look) || (edit.comic?.let(PremiumRules::isAdvancedFilter) == true) ||
            edit.adjust.vignette > 0f || edit.adjust.grain > 0f
        if (advanced && !premium.request(PremiumFeature.IMAGE_EFFECTS_ADVANCED)) return
        photo.save()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF101114)).statusBarsPadding().navigationBarsPadding().testTag("photo_editor")) {
        // --- Üst çubuk
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.Filled.Close, stringResource(R.string.close), { if (history.first > 0) confirmDiscard = true else photo.close() }, Modifier.testTag("photo_close"))
            Text(
                stringResource(R.string.v52_photo_title), style = MaterialTheme.typography.titleMedium, color = Color.White,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1,
            )
            NeuIconButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), photo::undo, Modifier.testTag("photo_undo"), enabled = history.first > 0)
            Spacer(Modifier.width(4.dp))
            NeuIconButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo), photo::redo, Modifier.testTag("photo_redo"), enabled = history.second > 0)
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(if (comparing) colors.accent else Color.White.copy(alpha = 0.12f))
                    .pointerInput(Unit) { detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false }) }
                    .testTag("photo_compare"),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Compare, stringResource(R.string.v52_photo_compare), tint = Color.White) }
            Spacer(Modifier.width(4.dp))
            NeuIconButton(Icons.Filled.RestartAlt, stringResource(R.string.v52_photo_reset), { aspectKey = null; photo.reset() }, Modifier.testTag("photo_reset"), enabled = !edit.isIdentity)
            Spacer(Modifier.width(4.dp))
            NeuIconButton(Icons.Filled.Check, stringResource(R.string.save_button), ::save, Modifier.testTag("photo_save"), selected = true, enabled = !saving && !loading)
        }

        // --- Önizleme
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val shown = if (comparing) original else preview
            if (shown != null) {
                PhotoCanvas(
                    image = shown, photo = photo, edit = edit, tab = tab, cropOverlay = cropMode && !comparing,
                    aspect = ASPECTS.firstOrNull { it.first == aspectKey }?.second,
                    brush = brush, brushSize = brushSize, brushStrength = brushStrength, brushColor = brushColor, bgRemoveTap = bgRemoveTap,
                )
            }
            if (loading || saving || rendering) {
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) }
            }
            if (saving) {
                Text(
                    stringResource(R.string.v52_photo_saving), color = Color.White,
                    modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(16.dp),
                )
            }
        }

        // --- Araç paneli (seçili sekme)
        Column(
            Modifier.fillMaxWidth().heightIn(max = 250.dp).background(colors.background).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp).testTag("photo_panel_${tab.tag}"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                PhotoTab.CROP -> CropPanel(photo, edit, aspectKey) { key, ratio ->
                    aspectKey = key
                    val (w, h) = photo.sourceSize
                    photo.update { e -> e.copy(crop = if (ratio == null) PhotoCrop() else PhotoOps.cropForAspect(w, h, e, if (ratio == 0f) PhotoOps.orientedSize(w, h, e.quarter).let { it.first.toFloat() / it.second } else ratio)) }
                }
                PhotoTab.LIGHT -> LightPanel(photo, edit)
                PhotoTab.COLOR -> ColorPanel(photo, edit)
                PhotoTab.FILTERS -> FiltersPanel(photo, edit.look)
                PhotoTab.EFFECTS -> EffectsPanel(photo, edit)
                PhotoTab.BACKGROUND -> MaskPanel(photo, edit, bgRemoveTap) { bgRemoveTap = it }
                PhotoTab.RETOUCH -> RetouchPanel(
                    brush, { brush = it }, brushSize, { brushSize = it }, brushStrength, { brushStrength = it }, brushColor, { brushColor = it },
                )
            }
        }
        // --- Sekmeler
        Row(Modifier.fillMaxWidth().background(colors.background).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp)) {
            PhotoTab.entries.forEach { t ->
                val sel = t == tab
                Column(
                    Modifier.width(84.dp).clip(RoundedCornerShape(12.dp)).background(if (sel) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable(role = Role.Tab) { tab = t }.padding(vertical = 6.dp).testTag("photo_tab_${t.tag}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, null, tint = if (sel) colors.accent else colors.content)
                    Text(
                        stringResource(t.label), style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center,
                        color = if (sel) colors.accent else colors.content,
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.v52_photo_discard)) },
            confirmButton = { TextButton({ confirmDiscard = false; photo.close() }, Modifier.testTag("photo_discard_yes")) { Text(stringResource(R.string.v52_photo_discard_yes)) } },
            dismissButton = { TextButton({ confirmDiscard = false }) { Text(stringResource(R.string.cancel_button)) } },
        )
    }
}

/** Tek parmak hareketi (sekmeye göre: kırpma çerçevesi, fırça, dokunma). */
private interface SingleTouch {
    fun start(p: Offset) {}
    fun move(p: Offset) {}
    fun end(p: Offset, moved: Boolean) {}
    fun cancel() {}
}

@Composable
private fun PhotoCanvas(
    image: ImageBitmap,
    photo: PhotoEditController,
    edit: PhotoEdit,
    tab: PhotoTab,
    cropOverlay: Boolean,
    aspect: Float?,
    brush: PhotoBrush, brushSize: Float, brushStrength: Float, brushColor: Int,
    bgRemoveTap: Boolean,
) {
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val live = remember { mutableStateListOf<Offset>() }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
        val bw = constraints.maxWidth.toFloat(); val bh = constraints.maxHeight.toFloat()
        val pad = with(density) { 18.dp.toPx() }
        val ar = image.width.toFloat() / image.height
        val availW = bw - 2 * pad; val availH = bh - 2 * pad
        val (iw, ih) = if (availW / availH > ar) (availH * ar) to availH else availW to (availW / ar)
        val rect = Rect(Offset((bw - iw) / 2f, (bh - ih) / 2f), Size(iw, ih))
        val center = Offset(bw / 2f, bh / 2f)
        fun toLocal(d: Offset) = (d - pan - center) / scale + center
        fun toUv(d: Offset): Offset { val p = toLocal(d); return Offset((p.x - rect.left) / rect.width, (p.y - rect.top) / rect.height) }

        val handle = with(density) { 28.dp.toPx() }
        val touch: SingleTouch = remember(tab, edit, aspect, brush, brushSize, brushStrength, brushColor, bgRemoveTap, rect, scale, pan) {
            when (tab) {
                PhotoTab.CROP -> object : SingleTouch {
                    var mode = 0 // 1..4 köşe (sol üst, sağ üst, sağ alt, sol alt), 5..8 kenar (sol, üst, sağ, alt), 9 içi
                    var startUv = Offset.Zero
                    var startCrop = edit.crop
                    override fun start(p: Offset) {
                        val c = edit.crop
                        val uv = toUv(p)
                        startUv = uv; startCrop = c
                        val hx = handle / (rect.width * scale); val hy = handle / (rect.height * scale)
                        fun near(x: Float, y: Float) = abs(uv.x - x) < hx && abs(uv.y - y) < hy
                        mode = when {
                            near(c.left, c.top) -> 1; near(c.right, c.top) -> 2; near(c.right, c.bottom) -> 3; near(c.left, c.bottom) -> 4
                            aspect == null && abs(uv.x - c.left) < hx && uv.y in c.top..c.bottom -> 5
                            aspect == null && abs(uv.y - c.top) < hy && uv.x in c.left..c.right -> 6
                            aspect == null && abs(uv.x - c.right) < hx && uv.y in c.top..c.bottom -> 7
                            aspect == null && abs(uv.y - c.bottom) < hy && uv.x in c.left..c.right -> 8
                            uv.x in c.left..c.right && uv.y in c.top..c.bottom -> 9
                            else -> 0
                        }
                    }
                    override fun move(p: Offset) {
                        if (mode == 0) return
                        val uv = toUv(p)
                        val d = uv - startUv
                        val c0 = startCrop
                        val minS = 0.05f
                        val next = when (mode) {
                            9 -> {
                                val w = c0.right - c0.left; val h = c0.bottom - c0.top
                                val l = (c0.left + d.x).coerceIn(0f, 1f - w); val t = (c0.top + d.y).coerceIn(0f, 1f - h)
                                PhotoCrop(l, t, l + w, t + h)
                            }
                            in 1..4 -> {
                                var l = c0.left; var t = c0.top; var r = c0.right; var b = c0.bottom
                                when (mode) { 1 -> { l += d.x; t += d.y }; 2 -> { r += d.x; t += d.y }; 3 -> { r += d.x; b += d.y }; else -> { l += d.x; b += d.y } }
                                l = l.coerceIn(0f, r - minS); r = r.coerceIn(l + minS, 1f); t = t.coerceIn(0f, b - minS); b = b.coerceIn(t + minS, 1f)
                                if (aspect != null) {
                                    // Oran korunur: normalize oran = aspect × (boy/en) (kırpma önizlemesi uv'de)
                                    val k = aspect * (rect.height / rect.width)
                                    val w = r - l
                                    var h = w / k
                                    val anchorTop = mode == 3 || mode == 4
                                    if (anchorTop) { if (t + h > 1f) { h = 1f - t } } else { if (b - h < 0f) { h = b } }
                                    val ww = h * k
                                    if (mode == 1 || mode == 4) l = r - ww else r = l + ww
                                    if (anchorTop) b = t + h else t = b - h
                                    if (l < 0f || r > 1f) return
                                }
                                PhotoCrop(l, t, r, b)
                            }
                            5 -> c0.copy(left = (c0.left + d.x).coerceIn(0f, c0.right - minS))
                            6 -> c0.copy(top = (c0.top + d.y).coerceIn(0f, c0.bottom - minS))
                            7 -> c0.copy(right = (c0.right + d.x).coerceIn(c0.left + minS, 1f))
                            else -> c0.copy(bottom = (c0.bottom + d.y).coerceIn(c0.top + minS, 1f))
                        }
                        photo.update("crop") { it.copy(crop = next) }
                    }
                    override fun end(p: Offset, moved: Boolean) { photo.endGesture() }
                }
                PhotoTab.RETOUCH -> object : SingleTouch {
                    val pts = ArrayList<Float>()
                    fun add(p: Offset) {
                        val uv = toUv(p)
                        if (uv.x < -0.05f || uv.y < -0.05f || uv.x > 1.05f || uv.y > 1.05f) return
                        val (sx, sy) = photo.toSource(uv.x.coerceIn(0f, 1f), uv.y.coerceIn(0f, 1f))
                        pts += sx; pts += sy
                        live += p
                    }
                    override fun start(p: Offset) { pts.clear(); live.clear(); add(p) }
                    override fun move(p: Offset) = add(p)
                    override fun end(p: Offset, moved: Boolean) {
                        if (pts.size >= 2) {
                            val stroke = PhotoStroke(brush, pts.toList(), brushSize, brushStrength, brushColor)
                            photo.update { it.copy(strokes = it.strokes + stroke) }
                        }
                        pts.clear(); live.clear()
                    }
                    override fun cancel() { pts.clear(); live.clear() }
                }
                PhotoTab.BACKGROUND -> object : SingleTouch {
                    override fun end(p: Offset, moved: Boolean) {
                        val bg = edit.background ?: return
                        if (moved) return
                        val uv = toUv(p)
                        if (uv.x !in 0f..1f || uv.y !in 0f..1f) return
                        val pt = photo.toSource(uv.x, uv.y)
                        photo.update { it.copy(background = if (bgRemoveTap) bg.copy(removeAt = bg.removeAt + pt) else bg.copy(keepAt = bg.keepAt + pt)) }
                    }
                }
                else -> object : SingleTouch {}
            }
        }

        Box(
            Modifier.fillMaxSize()
                .pointerInput(touch) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var multi = false
                        var moved = false
                        var last = down.position
                        touch.start(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                if (!multi) { multi = true; touch.cancel() }
                                val z = ev.calculateZoom(); val pn = ev.calculatePan()
                                scale = (scale * z).coerceIn(1f, 6f)
                                pan = if (scale <= 1.001f) Offset.Zero else pan + pn
                            } else if (!multi) {
                                val p = pressed[0].position
                                if ((p - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                if (moved) touch.move(p)
                                last = p
                            }
                            ev.changes.forEach { it.consume() }
                        }
                        if (!multi) touch.end(last, moved)
                    }
                }
                .testTag("photo_canvas"),
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y }) {
                // Damalı zemin (saydamlık) ve resim
                Canvas(Modifier.fillMaxSize()) {
                    val cell = 10.dp.toPx()
                    val light = Color(0xFF3A3B40); val dark = Color(0xFF2C2D31)
                    var y = rect.top; var row = 0
                    while (y < rect.bottom) {
                        var x = rect.left; var col = 0
                        while (x < rect.right) {
                            drawRect(if ((row + col) % 2 == 0) light else dark, Offset(x, y), Size(min(cell, rect.right - x), min(cell, rect.bottom - y)))
                            x += cell; col++
                        }
                        y += cell; row++
                    }
                }
                Image(
                    image, null, contentScale = ContentScale.FillBounds,
                    modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(rect.left.toInt(), rect.top.toInt()) }
                        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
                )
                if (cropOverlay) {
                    Canvas(Modifier.fillMaxSize().testTag("photo_crop_frame")) {
                        val c = edit.crop
                        val r = Rect(rect.left + c.left * rect.width, rect.top + c.top * rect.height, rect.left + c.right * rect.width, rect.top + c.bottom * rect.height)
                        val shade = Color.Black.copy(alpha = 0.55f)
                        drawRect(shade, rect.topLeft, Size(rect.width, r.top - rect.top))
                        drawRect(shade, Offset(rect.left, r.bottom), Size(rect.width, rect.bottom - r.bottom))
                        drawRect(shade, Offset(rect.left, r.top), Size(r.left - rect.left, r.height))
                        drawRect(shade, Offset(r.right, r.top), Size(rect.right - r.right, r.height))
                        val sw = 1.5.dp.toPx() / scale
                        drawRect(Color.White, r.topLeft, r.size, style = Stroke(sw))
                        for (i in 1..2) {
                            drawLine(Color.White.copy(alpha = 0.5f), Offset(r.left + r.width * i / 3f, r.top), Offset(r.left + r.width * i / 3f, r.bottom), sw / 1.5f)
                            drawLine(Color.White.copy(alpha = 0.5f), Offset(r.left, r.top + r.height * i / 3f), Offset(r.right, r.top + r.height * i / 3f), sw / 1.5f)
                        }
                        val hl = 18.dp.toPx() / scale; val hw = 4.dp.toPx() / scale
                        listOf(r.topLeft to Offset(1f, 1f), Offset(r.right, r.top) to Offset(-1f, 1f), r.bottomRight to Offset(-1f, -1f), Offset(r.left, r.bottom) to Offset(1f, -1f)).forEach { (p, d) ->
                            drawLine(Color.White, p, p + Offset(hl * d.x, 0f), hw, StrokeCap.Round)
                            drawLine(Color.White, p, p + Offset(0f, hl * d.y), hw, StrokeCap.Round)
                        }
                    }
                }
            }
            // Fırça izi (ekran koordinatında)
            if (live.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize()) {
                    val path = Path().apply { moveTo(live[0].x, live[0].y); live.drop(1).forEach { lineTo(it.x, it.y) } }
                    val width = max(4f, brushSize * min(rect.width, rect.height) * scale)
                    val c = if (brush == PhotoBrush.PAINT) Color(brushColor).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.35f)
                    drawPath(path, c, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

@Composable
private fun PhotoSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, tag: String, photo: PhotoEditController, onChange: (Float) -> PhotoEdit.() -> PhotoEdit) {
    val colors = NeuTheme.colors
    Column(Modifier.testTag("photo_$tag")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (range.start < 0f) "%+d".format((value * 100).toInt()) else "%d".format((value * 100).toInt()),
                style = MaterialTheme.typography.labelMedium, color = colors.contentMuted,
                modifier = Modifier.clickable { photo.update { e -> onChange(if (range.start < 0f) 0f else range.start)(e) } },
            )
        }
        NeuSlider(value, { v -> photo.update(tag) { e -> onChange(v)(e) } }, range, onValueChangeFinished = photo::endGesture)
    }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, color = NeuTheme.colors.accent)

@Composable
private fun CropPanel(photo: PhotoEditController, edit: PhotoEdit, aspectKey: String?, onAspect: (String?, Float?) -> Unit) {
    Text(stringResource(R.string.v52_photo_crop_hint), style = MaterialTheme.typography.labelSmall, color = NeuTheme.colors.contentMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        // Döndürme ve çevirmede kırpma sıfırlanır (çerçeve yeni yöne göre)
        NeuButton(stringResource(R.string.v52_photo_rotate_left), { onAspect(null, null); photo.update { it.copy(rotation = (it.quarter + 3) % 4, crop = PhotoCrop()) } }, Modifier.testTag("photo_rotate_left"), icon = Icons.AutoMirrored.Filled.RotateLeft)
        NeuButton(stringResource(R.string.v52_photo_rotate_right), { onAspect(null, null); photo.update { it.copy(rotation = (it.quarter + 1) % 4, crop = PhotoCrop()) } }, Modifier.testTag("photo_rotate_right"), icon = Icons.AutoMirrored.Filled.RotateRight)
        NeuButton(stringResource(R.string.v52_photo_flip_h), { photo.update { it.copy(flipH = !it.flipH, crop = it.crop.let { c -> PhotoCrop(1f - c.right, c.top, 1f - c.left, c.bottom) }) } }, Modifier.testTag("photo_flip_h"), icon = Icons.Filled.Flip)
        NeuButton(stringResource(R.string.v52_photo_flip_v), { photo.update { it.copy(flipV = !it.flipV, crop = it.crop.let { c -> PhotoCrop(c.left, 1f - c.bottom, c.right, 1f - c.top) }) } }, Modifier.testTag("photo_flip_v"), icon = Icons.Filled.Flip)
    }
    PhotoSlider(stringResource(R.string.v52_photo_straighten), edit.straighten / 45f, -1f..1f, "straighten", photo) { v -> { copy(straighten = v * 45f) } }
    SectionTitle(stringResource(R.string.v52_photo_aspect))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        ASPECTS.forEach { (key, ratio) ->
            val label = when (key) { null -> stringResource(R.string.v52_photo_aspect_free); "orig" -> stringResource(R.string.v52_photo_aspect_original); else -> key }
            NeuChip(label, aspectKey == key, { onAspect(key, ratio) }, Modifier.testTag("photo_aspect_${key ?: "free"}"))
        }
    }
}

/** v53 menülerin alt grupları (yatay çipler). */
@Composable
private fun SubTabs(items: List<Int>, selected: Int, tag: String, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, label -> NeuChip(stringResource(label), i == selected, { onSelect(i) }, Modifier.testTag("photo_sub_${tag}_$i")) }
    }
}

@Composable
private fun LightPanel(photo: PhotoEditController, edit: PhotoEdit) {
    val l = edit.light
    var sub by rememberSaveable { mutableIntStateOf(0) }
    SubTabs(listOf(R.string.v53_photo_sub_basic, R.string.v53_photo_sub_tones, R.string.v53_photo_sub_detail), sub, "light") { sub = it }
    when (sub) {
        0 -> {
            PhotoSlider(stringResource(R.string.v52_photo_exposure), l.exposure, -1f..1f, "exposure", photo) { v -> { copy(light = light.copy(exposure = v)) } }
            PhotoSlider(stringResource(R.string.adj_brightness), edit.adjust.brightness, -1f..1f, "brightness", photo) { v -> { copy(adjust = adjust.copy(brightness = v)) } }
            PhotoSlider(stringResource(R.string.adj_contrast), edit.adjust.contrast, -1f..1f, "contrast", photo) { v -> { copy(adjust = adjust.copy(contrast = v)) } }
        }
        1 -> {
            PhotoSlider(stringResource(R.string.v52_photo_highlights), l.highlights, -1f..1f, "highlights", photo) { v -> { copy(light = light.copy(highlights = v)) } }
            PhotoSlider(stringResource(R.string.v52_photo_shadows), l.shadows, -1f..1f, "shadows", photo) { v -> { copy(light = light.copy(shadows = v)) } }
            PhotoSlider(stringResource(R.string.v52_photo_whites), l.whites, -1f..1f, "whites", photo) { v -> { copy(light = light.copy(whites = v)) } }
            PhotoSlider(stringResource(R.string.v52_photo_blacks), l.blacks, -1f..1f, "blacks", photo) { v -> { copy(light = light.copy(blacks = v)) } }
        }
        else -> {
            PhotoSlider(stringResource(R.string.v52_photo_clarity), l.clarity, -1f..1f, "clarity", photo) { v -> { copy(light = light.copy(clarity = v)) } }
            PhotoSlider(stringResource(R.string.adj_sharpen), edit.adjust.sharpen, 0f..1f, "sharpen", photo) { v -> { copy(adjust = adjust.copy(sharpen = v)) } }
        }
    }
}

@Composable
private fun ColorPanel(photo: PhotoEditController, edit: PhotoEdit) {
    val a = edit.adjust
    var sub by rememberSaveable { mutableIntStateOf(0) }
    SubTabs(listOf(R.string.v53_photo_sub_basic, R.string.v53_photo_sub_hsl, R.string.v53_photo_sub_balance), sub, "color") { sub = it }
    when (sub) {
        0 -> {
            PhotoSlider(stringResource(R.string.adj_saturation), a.saturation, -1f..1f, "saturation", photo) { v -> { copy(adjust = adjust.copy(saturation = v)) } }
            PhotoSlider(stringResource(R.string.v52_photo_vibrance), edit.light.vibrance, -1f..1f, "vibrance", photo) { v -> { copy(light = light.copy(vibrance = v)) } }
            PhotoSlider(stringResource(R.string.adj_warmth), a.warmth, -1f..1f, "warmth", photo) { v -> { copy(adjust = adjust.copy(warmth = v)) } }
            PhotoSlider(stringResource(R.string.adj_tint), a.tint, -1f..1f, "tint", photo) { v -> { copy(adjust = adjust.copy(tint = v)) } }
            PhotoSlider(stringResource(R.string.adj_fade), a.fade, 0f..1f, "fade", photo) { v -> { copy(adjust = adjust.copy(fade = v)) } }
        }
        1 -> HslPanel(photo, edit.hsl)
        else -> BalancePanel(photo, edit.balance)
    }
}

private val RANGE_NAMES = listOf(
    R.string.v53_photo_range_0, R.string.v53_photo_range_1, R.string.v53_photo_range_2, R.string.v53_photo_range_3,
    R.string.v53_photo_range_4, R.string.v53_photo_range_5, R.string.v53_photo_range_6, R.string.v53_photo_range_7,
)

/** v53 HSL: renk aralığını seç, o aralığın tonunu, doygunluğunu ve açıklığını ayarla. */
@Composable
private fun HslPanel(photo: PhotoEditController, hsl: com.karapp.drawguidance.content.image.PhotoHsl) {
    val colors = NeuTheme.colors
    var range by rememberSaveable { mutableIntStateOf(0) }
    Text(stringResource(R.string.v53_photo_hsl_hint), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        com.karapp.drawguidance.content.image.PhotoHsl.SWATCHES.forEachIndexed { i, c ->
            val sel = i == range
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c.toInt()))
                    .border(if (sel) 3.dp else 1.dp, if (sel) colors.content else Color.Black.copy(alpha = 0.15f), CircleShape)
                    .clickable(role = Role.Tab) { range = i }.testTag("photo_hsl_range_$i"),
            )
        }
    }
    Text(stringResource(RANGE_NAMES[range]), style = MaterialTheme.typography.titleSmall, color = colors.accent)
    PhotoSlider(stringResource(R.string.v53_photo_hsl_hue), hsl.hue[range], -1f..1f, "hsl_hue", photo) { v -> { copy(hsl = hsl.with(range, h = v)) } }
    PhotoSlider(stringResource(R.string.v53_photo_hsl_sat), hsl.saturation[range], -1f..1f, "hsl_sat", photo) { v -> { copy(hsl = hsl.with(range, s = v)) } }
    PhotoSlider(stringResource(R.string.v53_photo_hsl_light), hsl.lightness[range], -1f..1f, "hsl_light", photo) { v -> { copy(hsl = hsl.with(range, l = v)) } }
    if (!hsl.isIdentity) TextButton({ photo.update { it.copy(hsl = com.karapp.drawguidance.content.image.PhotoHsl()) } }, Modifier.testTag("photo_hsl_reset")) {
        Text(stringResource(R.string.v53_photo_hsl_reset))
    }
}

/** v53 renk dengesi: gölgeler / orta tonlar / vurgular; üç renk ekseni ve parlaklığı koru. */
@Composable
private fun BalancePanel(photo: PhotoEditController, b: com.karapp.drawguidance.content.image.PhotoBalance) {
    var range by rememberSaveable { mutableIntStateOf(1) }
    SubTabs(listOf(R.string.v53_photo_bal_shadows, R.string.v53_photo_bal_midtones, R.string.v53_photo_bal_highlights), range, "balance") { range = it }
    val r = b.range(range)
    PhotoSlider(stringResource(R.string.v53_photo_bal_cr), r[0], -1f..1f, "bal_cr", photo) { v -> { copy(balance = balance.with(range, 0, v)) } }
    PhotoSlider(stringResource(R.string.v53_photo_bal_mg), r[1], -1f..1f, "bal_mg", photo) { v -> { copy(balance = balance.with(range, 1, v)) } }
    PhotoSlider(stringResource(R.string.v53_photo_bal_yb), r[2], -1f..1f, "bal_yb", photo) { v -> { copy(balance = balance.with(range, 2, v)) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.v53_photo_bal_preserve), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        NeuSwitch(b.preserve, { v -> photo.update { it.copy(balance = it.balance.copy(preserve = v)) } }, Modifier.testTag("photo_bal_preserve"))
    }
}

/** v53 kırpma maskesi: renk/gradyan dolgusu yalnız resmin görünen kısmına kırpılarak karışır. */
@Composable
private fun ClipPanel(photo: PhotoEditController, clip: com.karapp.drawguidance.content.image.PhotoClip?) {
    val colors = NeuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.v53_photo_clip_on), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        NeuSwitch(clip != null, { on -> photo.update { it.copy(clip = if (on) com.karapp.drawguidance.content.image.PhotoClip() else null) } }, Modifier.testTag("photo_clip_on"))
    }
    Text(stringResource(R.string.v53_photo_clip_hint), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
    if (clip == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeuChip(stringResource(R.string.v53_photo_clip_color), clip.fill == com.karapp.drawguidance.content.image.ClipFill.COLOR,
            { photo.update { it.copy(clip = it.clip?.copy(fill = com.karapp.drawguidance.content.image.ClipFill.COLOR)) } }, Modifier.testTag("photo_clip_fill_color"))
        NeuChip(stringResource(R.string.v53_photo_clip_gradient), clip.fill == com.karapp.drawguidance.content.image.ClipFill.GRADIENT,
            { photo.update { it.copy(clip = it.clip?.copy(fill = com.karapp.drawguidance.content.image.ClipFill.GRADIENT)) } }, Modifier.testTag("photo_clip_fill_gradient"))
    }
    SectionTitle(stringResource(R.string.v53_photo_clip_color1))
    ClipSwatches(clip.color1, "c1") { c -> photo.update { it.copy(clip = it.clip?.copy(color1 = c)) } }
    if (clip.fill == com.karapp.drawguidance.content.image.ClipFill.GRADIENT) {
        SectionTitle(stringResource(R.string.v53_photo_clip_color2))
        ClipSwatches(clip.color2, "c2") { c -> photo.update { it.copy(clip = it.clip?.copy(color2 = c)) } }
        PhotoSlider(stringResource(R.string.v53_photo_clip_angle), clip.angle / 360f, 0f..1f, "clip_angle", photo) { v -> { copy(clip = this.clip?.copy(angle = v * 360f)) } }
    }
    SectionTitle(stringResource(R.string.v53_photo_clip_blend))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        com.karapp.drawguidance.content.image.ClipBlend.entries.forEach { m ->
            NeuChip(stringResource(BLEND_NAMES.getValue(m)), clip.blend == m, { photo.update { it.copy(clip = it.clip?.copy(blend = m)) } }, Modifier.testTag("photo_clip_blend_${m.name}"))
        }
    }
    PhotoSlider(stringResource(R.string.v53_photo_clip_opacity), clip.opacity, 0f..1f, "clip_opacity", photo) { v -> { copy(clip = this.clip?.copy(opacity = v)) } }
}

private val BLEND_NAMES = mapOf(
    com.karapp.drawguidance.content.image.ClipBlend.NORMAL to R.string.v53_photo_blend_normal,
    com.karapp.drawguidance.content.image.ClipBlend.MULTIPLY to R.string.v53_photo_blend_multiply,
    com.karapp.drawguidance.content.image.ClipBlend.SCREEN to R.string.v53_photo_blend_screen,
    com.karapp.drawguidance.content.image.ClipBlend.OVERLAY to R.string.v53_photo_blend_overlay,
    com.karapp.drawguidance.content.image.ClipBlend.SOFT_LIGHT to R.string.v53_photo_blend_soft_light,
    com.karapp.drawguidance.content.image.ClipBlend.COLOR to R.string.v53_photo_blend_color,
)

@Composable
private fun ClipSwatches(selected: Int, tag: String, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        RETOUCH_COLORS.forEach { c ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == selected) 3.dp else 1.dp, if (c == selected) NeuTheme.colors.accent else Color.Black.copy(alpha = 0.2f), CircleShape)
                    .clickable { onPick(c) }.testTag("photo_clip_${tag}_${Integer.toHexString(c)}"),
            )
        }
    }
}

@Composable
private fun FiltersPanel(photo: PhotoEditController, look: ImageLook) {
    val colors = NeuTheme.colors
    val premium = com.karapp.drawguidance.ui.premium.LocalPremium.current
    val advLocked = premium.locked(PremiumFeature.IMAGE_EFFECTS_ADVANCED)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ImageLook.entries.forEach { l ->
            val sel = l == look
            val locked = advLocked && PremiumRules.isAdvancedLook(l)
            Column(
                Modifier.width(84.dp).clip(RoundedCornerShape(12.dp))
                    .border(if (sel) 2.dp else 1.dp, if (sel) colors.accent else colors.contentMuted.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { if (!locked || premium.request(PremiumFeature.IMAGE_EFFECTS_ADVANCED)) photo.update { it.copy(look = l) } }
                    .padding(vertical = 10.dp, horizontal = 4.dp).testTag("photo_look_${l.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (locked) com.karapp.drawguidance.ui.premium.PremiumLockBadge()
                Text(lookLabel(l), style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center, color = if (sel) colors.accent else colors.content)
            }
        }
    }
}

@Composable
private fun EffectsPanel(photo: PhotoEditController, edit: PhotoEdit) {
    val premium = com.karapp.drawguidance.ui.premium.LocalPremium.current
    val advLocked = premium.locked(PremiumFeature.IMAGE_EFFECTS_ADVANCED)
    var sub by rememberSaveable { mutableIntStateOf(0) }
    SubTabs(listOf(R.string.v53_photo_sub_comic, R.string.v53_photo_sub_blur, R.string.v53_photo_sub_vignette), sub, "effects") { sub = it }
    val a = edit.adjust
    when (sub) {
        0 -> {
            val kinds = listOf(null to R.string.v52_photo_none, ComicFilter.COMIC to R.string.filter_comic, ComicFilter.HALFTONE to R.string.filter_halftone, ComicFilter.MANGA to R.string.filter_manga, ComicFilter.POP to R.string.filter_pop)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { (k, label) ->
                    val locked = k != null && advLocked && PremiumRules.isAdvancedFilter(k)
                    NeuChip(stringResource(label), edit.comic == k, { if (!locked || premium.request(PremiumFeature.IMAGE_EFFECTS_ADVANCED)) photo.update { it.copy(comic = k) } }, Modifier.testTag("photo_comic_${k?.name ?: "none"}"))
                }
            }
        }
        1 -> PhotoSlider(stringResource(R.string.adj_blur), a.blur, 0f..1f, "blur", photo) { v -> { copy(adjust = adjust.copy(blur = v)) } }
        else -> {
            PhotoSlider(stringResource(R.string.adj_vignette), a.vignette, 0f..1f, "vignette", photo) { v -> { copy(adjust = adjust.copy(vignette = v)) } }
            PhotoSlider(stringResource(R.string.adj_grain), a.grain, 0f..1f, "grain", photo) { v -> { copy(adjust = adjust.copy(grain = v)) } }
        }
    }
}

/** v53 Maske menüsü: arka plan kaldırma ve kırpma maskesi. */
@Composable
private fun MaskPanel(photo: PhotoEditController, edit: PhotoEdit, removeTap: Boolean, onRemoveTap: (Boolean) -> Unit) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    SubTabs(listOf(R.string.v53_photo_sub_bg, R.string.v53_photo_sub_clip), sub, "mask") { sub = it }
    if (sub == 0) BackgroundPanel(photo, edit.background, removeTap, onRemoveTap) else ClipPanel(photo, edit.clip)
}

@Composable
private fun BackgroundPanel(photo: PhotoEditController, bg: BackgroundOptions?, removeTap: Boolean, onRemoveTap: (Boolean) -> Unit) {
    val colors = NeuTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.v52_photo_bg_on), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        NeuSwitch(bg != null, { on -> photo.update { it.copy(background = if (on) BackgroundOptions() else null) } }, Modifier.testTag("photo_bg_on"))
    }
    if (bg == null) {
        Text(stringResource(R.string.bg_remove_hint), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
        return
    }
    Text(stringResource(R.string.v52_photo_bg_tap_hint), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeuChip(stringResource(R.string.bg_tap_remove), removeTap, { onRemoveTap(true) }, Modifier.weight(1f).testTag("photo_bg_tap_remove"))
        NeuChip(stringResource(R.string.bg_tap_keep), !removeTap, { onRemoveTap(false) }, Modifier.weight(1f).testTag("photo_bg_tap_keep"))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.bg_auto), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        NeuSwitch(bg.auto, { v -> photo.update { it.copy(background = it.background?.copy(auto = v)) } })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.bg_holes), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        NeuSwitch(bg.removeHoles, { v -> photo.update { it.copy(background = it.background?.copy(removeHoles = v)) } })
    }
    PhotoSlider(stringResource(R.string.bg_tolerance), bg.tolerance, 0f..1f, "bg_tolerance", photo) { v -> { copy(background = background?.copy(tolerance = v)) } }
    PhotoSlider(stringResource(R.string.bg_feather), bg.feather, 0f..1f, "bg_feather", photo) { v -> { copy(background = background?.copy(feather = v)) } }
    NeuButton(
        stringResource(R.string.bg_undo_tap),
        {
            photo.update { e ->
                val b = e.background ?: return@update e
                // Son dokunuş: hangi listeye en son eklendiği bilinmez; kaldırılanlardan başlanır
                e.copy(background = if (b.removeAt.isNotEmpty() && (removeTap || b.keepAt.isEmpty())) b.copy(removeAt = b.removeAt.dropLast(1)) else b.copy(keepAt = b.keepAt.dropLast(1)))
            }
        },
        enabled = bg.removeAt.isNotEmpty() || bg.keepAt.isNotEmpty(), icon = Icons.AutoMirrored.Filled.Undo,
    )
}

@Composable
private fun RetouchPanel(
    brush: PhotoBrush, onBrush: (PhotoBrush) -> Unit,
    size: Float, onSize: (Float) -> Unit,
    strength: Float, onStrength: (Float) -> Unit,
    color: Int, onColor: (Int) -> Unit,
) {
    val colors = NeuTheme.colors
    Text(stringResource(R.string.v52_photo_brush_hint), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
    val brushes = listOf(
        PhotoBrush.LIGHTEN to R.string.v52_photo_brush_lighten, PhotoBrush.DARKEN to R.string.v52_photo_brush_darken,
        PhotoBrush.SMOOTH to R.string.v52_photo_brush_smooth, PhotoBrush.BLUR to R.string.v52_photo_brush_blur,
        PhotoBrush.PAINT to R.string.v52_photo_brush_paint, PhotoBrush.RESTORE to R.string.v52_photo_brush_restore,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        brushes.forEach { (b, label) -> NeuChip(stringResource(label), b == brush, { onBrush(b) }, Modifier.testTag("photo_brush_${b.name.lowercase()}")) }
    }
    Column {
        Text(stringResource(R.string.v52_photo_brush_size), style = MaterialTheme.typography.bodyMedium)
        NeuSlider(size, onSize, 0.01f..0.3f)
    }
    Column {
        Text(stringResource(R.string.v52_photo_brush_strength), style = MaterialTheme.typography.bodyMedium)
        NeuSlider(strength, onStrength, 0.1f..1f)
    }
    if (brush == PhotoBrush.PAINT) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RETOUCH_COLORS.forEach { c ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == color) 3.dp else 1.dp, if (c == color) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                        .clickable { onColor(c) },
                )
            }
        }
    }
}

/**
 * v52 not almada ve çizgi romanda resim düzenlemenin yeri sorulur: gelişmiş çizimde, foto düzenlemede, (çizgi romanda)
 * burada.
 */
@Composable
fun ImageEditChoiceSheet(comic: Boolean, onPick: (ImageEditChoice) -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    com.karapp.drawguidance.ui.components.NeuSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp).testTag("image_edit_choice"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.v52_image_edit_choice_title), style = MaterialTheme.typography.headlineSmall)
            val options = buildList {
                add(Triple(ImageEditChoice.PHOTO, R.string.v52_image_edit_choice_photo to R.string.v52_image_edit_choice_photo_hint, Icons.Filled.PhotoFilter))
                add(Triple(ImageEditChoice.ADVANCED, R.string.v52_image_edit_choice_advanced to R.string.v52_image_edit_choice_advanced_hint, Icons.Filled.Brush))
                if (comic) add(Triple(ImageEditChoice.HERE, R.string.v52_image_edit_choice_here to R.string.v52_image_edit_choice_here_hint, Icons.Filled.Crop))
            }
            options.forEach { (choice, texts, icon) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.accent.copy(alpha = 0.08f))
                        .clickable(role = Role.Button) { onPick(choice) }.padding(14.dp).testTag("image_edit_choice_${choice.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = colors.accent, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(stringResource(texts.first), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(texts.second), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
