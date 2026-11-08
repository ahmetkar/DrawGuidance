package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Palette
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.screens.canvasedit.TextDraft
import com.karapp.drawguidance.ui.screens.canvasedit.TextSettings
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlin.math.roundToInt

/**
 * Çizim alanının içinde yazma alanı: etrafı aralıklı noktalarla çevrili bir kutuda yazılır, altta alçak ve geniş
 * geçici yazı çubuğu görünür (v16: iki satır; yazı alanı çubuğun altında kalırsa görünüm yukarı kayar).
 * Pencere açılmaz; yazı olduğu yerde düzenlenir.
 */
@Composable
fun BoxScope.InlineTextEditor(
    draft: TextDraft,
    settings: TextSettings,
    colorHex: String,
    anchorScreen: Offset,
    scale: Float,
    onSettingsChange: (TextSettings) -> Unit,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    /** Yazılan her değişiklik (başka yere dokunulunca ya da mod değişince yazı kaybolmasın diye). */
    onTextChange: (String) -> Unit = {},
    /** Not tuvali: başlık ve liste biçimleri gösterilir. */
    notes: Boolean = false,
    /** v16: yazı alanı ayar çubuğunun altında kalıyor: görünüm bu kadar (ekran pikseli) yukarı kaydırılmalı. */
    onAvoid: (Float) -> Unit = {},
    /** v24 kağıt rengi (kutu deseninin tonları için). */
    paperHex: String = "#FFFFFF",
    /**
     * v28 yazının şimdiki hali (yazılan metinle; tuvaldeki yeri, biçimi, kutusu, efektleri, ölçek ve yönüyle). Verilirse
     * yazarken yazı tuvaldeki gibi kendi yerinde çizilir, giriş alanı onun üstüne oturur.
     */
    preview: ((String) -> com.karapp.drawguidance.data.model.Stroke?)? = null,
    /** v28 tuvalin (0, 0) noktasının ekrandaki yeri. */
    canvasOrigin: Offset = Offset.Zero,
    /** v29 harf rengi (Biçim panelinde; null ise gösterilmez). */
    onColor: ((String) -> Unit)? = null,
    /** v29 düzenlenen yazının kutu eni/boyu (not tuvali). */
    onBoxSize: ((Float?, Float?, Boolean, Boolean) -> Unit)? = null,
    /** v37 kutu tutamağından taşıma bitti (tuval birimi); null ise tutamak yok. */
    onMove: ((Float, Float) -> Unit)? = null,
    /** v37 not tuvalinde kutu türü: paragraf (eni sabit) ya da yazıya göre (kısa yazı, başlık). */
    onParagraphBox: ((Boolean) -> Unit)? = null,
    /**
     * v42 "Yol üzerinde": yazı kaydedilir ve kalem aracıyla çizilmiş bir yola yerleştirilir (null: düğme yok).
     * [textOnPathEnabled] false ise sayfada yol yoktur (düğme kapalı, ipucu gösterilir).
     */
    onTextOnPath: ((String) -> Unit)? = null,
    textOnPathEnabled: Boolean = true,
) {
    val colors = NeuTheme.colors
    val density = LocalDensity.current
    var value by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(draft.content, androidx.compose.ui.text.TextRange(draft.content.length)))
    }
    val text = value.text
    val focus = remember { FocusRequester() }
    var editorBottom by remember { mutableStateOf(Float.NaN) }
    LaunchedEffect(draft) { runCatching { focus.requestFocus() } }

    val spec = settings.spec
    fun setText(newText: String, cursor: Int) {
        value = androidx.compose.ui.text.input.TextFieldValue(newText, androidx.compose.ui.text.TextRange(cursor.coerceIn(0, newText.length)))
        onTextChange(newText)
    }
    // Paragraf biçimi değişince liste işaretleri yazıya uygulanır
    val changeParagraph: (com.karapp.drawguidance.data.model.TextParagraph) -> Unit = { p ->
        val applied = com.karapp.drawguidance.data.text.TextLists.apply(text, p)
        if (applied != text) setText(applied, applied.length)
        onSettingsChange(settings.copy(spec = spec.copy(paragraph = p)))
    }
    // v28 yazarken önizleme: yazı tuvaldeki yerinde ve kendi biçimiyle çizilir; giriş alanı (yazısı saydam, imleci
    // görünür) aynı düzenle tam üstüne oturur. Yazı yerinden oynamaz, yazı tipi/kenarlık/gölge/gradyan kaybolmaz.
    val live = remember(text, settings, colorHex, draft) { preview?.let { f -> runCatching { f(text) }.getOrNull() } }
        ?.takeIf { it.points.size >= 4 && it.text != null }
    // v37 kutu tutamağıyla sürüklenirken yazı parmakla birlikte kayar (ekran pikseli); bırakınca yeni yerine konur
    var shift by remember { mutableStateOf(Offset.Zero) }
    if (live != null) {
        LiveTextEditor(
            stroke = live, value = value, colorHex = colorHex, scale = scale, canvasOrigin = canvasOrigin + shift, focus = focus,
            onValueChange = { next ->
                if (next.text == value.text) {
                    value = next
                } else {
                    val edit = com.karapp.drawguidance.data.text.TextLists.onEdit(value.text, next.text, next.selection.end, spec.paragraph)
                    if (edit.text == next.text) {
                        value = next
                        onTextChange(next.text)
                    } else {
                        setText(edit.text, edit.cursor)
                    }
                }
            },
            onBottom = { editorBottom = it },
        )
    }
    val fontSizePx = (settings.sizePx * spec.paragraph.sizeFactor * scale).coerceIn(10f, 600f)
    val fontSize = with(density) { fontSizePx.toSp() }
    val ink = hexToColor(colorHex)
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f) }
    // v24 yazı kutusunun arka planı (renk, desen, hazır resim) yazarken de görünür; kutu eni/boyu verilmişse alan o
    // ölçüdedir, yazı tuvaldeki yerinden (kutunun iç boşluğundan) başlar
    val box = spec.box
    val boxed = box != null && (box.hasBackground || box.width != null)
    val pads = remember(spec, settings.sizePx) { com.karapp.drawguidance.ui.drawing.TextRenderer.pads(spec, settings.sizePx) }
    val paper = remember(paperHex) { runCatching { android.graphics.Color.parseColor(paperHex) }.getOrDefault(-1) }
    val boxWidth = box?.width
    val sizeModifier = when {
        boxWidth != null -> Modifier.width(with(density) { (boxWidth * scale).coerceIn(48f, 6000f).toDp() })
        // v24 eni verilmemiş arka planlı kutu yazıya göre (tuvaldeki gibi) genişler
        boxed -> Modifier.width(IntrinsicSize.Max).widthIn(min = 64.dp, max = 320.dp)
        else -> Modifier.widthIn(min = 120.dp, max = 320.dp)
    }
    val minHeight = box?.height?.let { h -> with(density) { (h * scale).coerceIn(0f, 6000f).toDp() } } ?: 0.dp
    val padding = if (boxed) {
        with(density) {
            Modifier.padding(
                start = (pads[0] * scale).toDp(), top = (pads[1] * scale).toDp(),
                end = (pads[2] * scale).toDp(), bottom = (pads[3] * scale).toDp(),
            )
        }
    } else Modifier.padding(6.dp)

    if (live == null) Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset((anchorScreen.x + shift.x).roundToInt(), (anchorScreen.y + shift.y).roundToInt()) }
            .then(sizeModifier)
            .heightIn(min = minHeight)
            .drawBehind {
                if (box != null && box.hasBackground && scale > 0f) {
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val save = native.save()
                        native.scale(scale, scale)
                        com.karapp.drawguidance.ui.drawing.BackgroundRenderer.drawBox(
                            native, box, size.width / scale, size.height / scale, paper, 1f, coverage = false,
                        )
                        native.restoreToCount(save)
                    }
                }
                this.drawRoundRect(
                    color = colors.accent,
                    style = Stroke(width = 2f, pathEffect = dash),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                )
            }
            .then(padding)
            .onGloballyPositioned { editorBottom = it.boundsInRoot().bottom }
            .testTag("inline_text_editor"),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { next ->
                if (next.text == value.text) {
                    value = next
                } else {
                    // Listede Enter bir sonraki işareti ekler, numaralar sıralanır
                    val edit = com.karapp.drawguidance.data.text.TextLists.onEdit(value.text, next.text, next.selection.end, spec.paragraph)
                    if (edit.text == next.text) {
                        value = next
                        onTextChange(next.text)
                    } else {
                        setText(edit.text, edit.cursor)
                    }
                }
            },
            textStyle = TextStyle(
                color = ink,
                fontSize = fontSize,
                fontFamily = composeFamily(spec.font),
                fontWeight = if (spec.bold || spec.paragraph.heading) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (spec.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (spec.underline) TextDecoration.Underline else null,
                textAlign = when (spec.align) {
                    TextAlignKind.LEFT -> TextAlign.Start
                    TextAlignKind.CENTER -> TextAlign.Center
                    TextAlignKind.RIGHT -> TextAlign.End
                },
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }

    if (onMove != null) {
        val corner = live?.points?.get(0)?.let { p -> Offset(canvasOrigin.x + p.x * scale, canvasOrigin.y + p.y * scale) } ?: anchorScreen
        TextMoveHandle(corner + shift, onDrag = { shift += it }, onDragEnd = {
            // Yeni yerinde düzenleyici yeniden kurulur (kayma sıfırlanır); yazılan korunur
            if (shift != Offset.Zero && scale > 0f) onMove(shift.x / scale, shift.y / scale)
        })
    }

    // Geçici yazı tipi çubuğu (v16): alçak ve geniş, çizim alanının en altında; yazıyı örtmez. v29 "Biçim" ile
    // çubuğun üstünde yazı biçiminin geri kalanı açılır (renk ve kenarlık, efektler, kutu); çubuktakiler tekrarlanmaz.
    var barTop by remember { mutableStateOf(Float.NaN) }
    var formatOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .widthIn(max = 960.dp)
            .onGloballyPositioned { barTop = it.boundsInRoot().top },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
    if (formatOpen) {
        NeuSurface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).testTag("inline_format_panel"),
            elevation = 10.dp,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextFormatPanel(
                    settings = settings,
                    onChange = onSettingsChange,
                    notes = notes,
                    fillColorHex = if (onColor != null) colorHex else null,
                    onFill = onColor,
                    onBoxSize = onBoxSize,
                    tabs = listOf(TextFormatTab.COLOR, TextFormatTab.EFFECTS, TextFormatTab.BOX),
                )
            }
        }
    }
    NeuSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inline_font_bar"),
        elevation = 10.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (notes && onParagraphBox != null) {
                TextKindRow(TextKind.of(spec.paragraph, draft.boxWidth)) { kind ->
                    when {
                        kind == TextKind.HEADING && !spec.paragraph.heading -> changeParagraph(com.karapp.drawguidance.data.model.TextParagraph.H1)
                        kind != TextKind.HEADING && spec.paragraph.heading -> changeParagraph(com.karapp.drawguidance.data.model.TextParagraph.NORMAL)
                    }
                    onParagraphBox(kind == TextKind.PARAGRAPH)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Yazı tipi: kendi harfleriyle gösterilen seçici (dokununca liste açılır)
                FontPickerButton(spec.font, { onSettingsChange(settings.copy(spec = spec.copy(font = it))) }, Modifier.weight(1f))
                com.karapp.drawguidance.ui.components.NeuSlider(
                    value = settings.sizePx,
                    onValueChange = { onSettingsChange(settings.copy(sizePx = it.roundToInt().toFloat())) },
                    valueRange = com.karapp.drawguidance.ui.screens.canvasedit.CanvasEditViewModel.MIN_TEXT_SIZE..240f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${settings.sizePx.roundToInt()} px", style = MaterialTheme.typography.labelMedium, color = colors.contentMuted,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeuIconButton(Icons.Filled.FormatBold, stringResource(R.string.text_bold), { onSettingsChange(settings.copy(spec = spec.copy(bold = !spec.bold))) }, size = 36.dp, selected = spec.bold)
                    NeuIconButton(Icons.Filled.FormatItalic, stringResource(R.string.text_italic), { onSettingsChange(settings.copy(spec = spec.copy(italic = !spec.italic))) }, size = 36.dp, selected = spec.italic)
                    NeuIconButton(Icons.Filled.FormatUnderlined, stringResource(R.string.text_underline), { onSettingsChange(settings.copy(spec = spec.copy(underline = !spec.underline))) }, size = 36.dp, selected = spec.underline)
                    NeuIconButton(Icons.AutoMirrored.Filled.FormatAlignLeft, stringResource(R.string.text_align_left), { onSettingsChange(settings.copy(spec = spec.copy(align = TextAlignKind.LEFT))) }, size = 36.dp, selected = spec.align == TextAlignKind.LEFT)
                    NeuIconButton(Icons.Filled.FormatAlignCenter, stringResource(R.string.text_align_center), { onSettingsChange(settings.copy(spec = spec.copy(align = TextAlignKind.CENTER))) }, size = 36.dp, selected = spec.align == TextAlignKind.CENTER)
                    NeuIconButton(Icons.AutoMirrored.Filled.FormatAlignRight, stringResource(R.string.text_align_right), { onSettingsChange(settings.copy(spec = spec.copy(align = TextAlignKind.RIGHT))) }, size = 36.dp, selected = spec.align == TextAlignKind.RIGHT)
                    if (notes) {
                        Spacer(Modifier.width(4.dp))
                        ParagraphInlineControls(
                            paragraph = spec.paragraph,
                            onChange = changeParagraph,
                            onIndent = { delta ->
                                val edit = com.karapp.drawguidance.data.text.TextLists.indent(text, value.selection.end, spec.paragraph, delta)
                                setText(edit.text, edit.cursor)
                            },
                        )
                    }
                }
                if (onTextOnPath != null) {
                    NeuIconButton(
                        Icons.Filled.Gesture,
                        stringResource(if (textOnPathEnabled) R.string.v42_text_on_path else R.string.v42_text_on_path_need),
                        { onTextOnPath(value.text) }, Modifier.testTag("text_on_path"), size = 38.dp,
                        enabled = textOnPathEnabled && value.text.isNotBlank(),
                    )
                }
                NeuIconButton(
                    Icons.Filled.Palette, stringResource(R.string.text_format_more), { formatOpen = !formatOpen },
                    size = 38.dp, selected = formatOpen,
                )
                NeuIconButton(Icons.Filled.Close, stringResource(R.string.text_cancel), onCancel, size = 38.dp)
                NeuIconButton(Icons.Filled.Check, stringResource(R.string.text_done), { onConfirm(value.text) }, size = 38.dp, tint = colors.accent)
            }
        }
    }
    }
    // Yazı alanı çubuğun altında kalırsa görünüm yukarı kaydırılır: yazılan yer hep görünür
    val gap = with(density) { 12.dp.toPx() }
    LaunchedEffect(editorBottom, barTop) {
        val top = barTop
        if (!top.isNaN() && !editorBottom.isNaN() && editorBottom > top - gap) onAvoid(editorBottom - (top - gap))
    }
}

/** v37 not tuvalinde yazı kutusunun türü. */
internal enum class TextKind(val label: Int) {
    /** Büyük, kalın tek satır (sağ kenarda alt satıra geçer). */
    HEADING(R.string.text_kind_heading),
    /** Kutu yazıya göre büyür; sağ kenarda alt satıra geçer. */
    SHORT(R.string.text_kind_short),
    /** Eni sabit kutu; satırlar kutuda alt satıra geçer, kutu aşağı uzar. */
    PARAGRAPH(R.string.text_kind_paragraph);

    companion object {
        fun of(paragraph: com.karapp.drawguidance.data.model.TextParagraph, boxWidth: Float?): TextKind = when {
            paragraph.heading -> HEADING
            boxWidth != null -> PARAGRAPH
            else -> SHORT
        }
    }
}

@Composable
private fun TextKindRow(kind: TextKind, onKind: (TextKind) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("text_kind_row"),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        TextKind.entries.forEach { k ->
            com.karapp.drawguidance.ui.components.NeuChip(
                stringResource(k.label), kind == k, onClick = { if (k != kind) onKind(k) },
                modifier = Modifier.testTag("text_kind_${k.name.lowercase()}"),
                icon = when (k) {
                    TextKind.HEADING -> Icons.Filled.Title
                    TextKind.SHORT -> Icons.AutoMirrored.Filled.ShortText
                    TextKind.PARAGRAPH -> Icons.AutoMirrored.Filled.Notes
                },
            )
        }
    }
}

/** v37 yazı kutusunun sol üst köşesindeki taşıma tutamağı ([at]: kutunun köşesi, ekran pikseli). */
@Composable
private fun BoxScope.TextMoveHandle(at: Offset, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit) {
    val colors = NeuTheme.colors
    val density = LocalDensity.current
    val sizePx = with(density) { 32.dp.toPx() }
    val gap = with(density) { 4.dp.toPx() }
    val drag by androidx.compose.runtime.rememberUpdatedState(onDrag)
    val end by androidx.compose.runtime.rememberUpdatedState(onDragEnd)
    val label = stringResource(R.string.text_move)
    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset((at.x - sizePx - gap).roundToInt().coerceAtLeast(0), (at.y - sizePx - gap).roundToInt().coerceAtLeast(0)) }
            .size(32.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(colors.accent)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { end() }, onDragCancel = { end() }) { change, amount -> change.consume(); drag(amount) }
            }
            .semantics { contentDescription = label }
            .testTag("text_move_handle"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.OpenWith, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
    }
}

/**
 * v28 yazının kendisiyle düzenleme: önizleme tuval çizimiyle aynıdır ([com.karapp.drawguidance.ui.drawing.TextRenderer]);
 * giriş alanı yazının düzen kutusunda (iç boşluklar, kutu eni, hizalama, harf aralığı, yazı tipi aynı) ve yazının
 * ölçeği/yönüyle dönüştürülmüş olarak üstte durur. Alanın yazısı saydamdır: görünen yazı önizlemedir.
 */
@Composable
private fun BoxScope.LiveTextEditor(
    stroke: com.karapp.drawguidance.data.model.Stroke,
    value: androidx.compose.ui.text.input.TextFieldValue,
    colorHex: String,
    scale: Float,
    canvasOrigin: Offset,
    focus: FocusRequester,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onBottom: (Float) -> Unit,
) {
    val colors = NeuTheme.colors
    val density = LocalDensity.current
    val spec = stroke.text ?: return
    val renderer = com.karapp.drawguidance.ui.drawing.TextRenderer
    val q = stroke.points
    val (mw, mh) = remember(spec, stroke.sizePx) { renderer.measure(spec, stroke.sizePx) }
    val ux = q[1].x - q[0].x; val uy = q[1].y - q[0].y
    val vx = q[3].x - q[0].x; val vy = q[3].y - q[0].y
    val su = (kotlin.math.hypot(ux, uy) / mw.coerceAtLeast(1e-3f)).takeIf { it.isFinite() && it > 0f } ?: 1f
    val sv = (kotlin.math.hypot(vx, vy) / mh.coerceAtLeast(1e-3f)).takeIf { it.isFinite() && it > 0f } ?: 1f
    val angle = Math.toDegrees(kotlin.math.atan2(uy, ux).toDouble()).toFloat()
    val origin = Offset(canvasOrigin.x + q[0].x * scale, canvasOrigin.y + q[0].y * scale)
    val pads = remember(spec, stroke.sizePx) { renderer.pads(spec, stroke.sizePx) }
    val size = renderer.effectiveSize(spec, stroke.sizePx)
    val ink = remember(colorHex) { runCatching { android.graphics.Color.parseColor(colorHex) }.getOrDefault(0xFF1E1E1E.toInt()) }
    val accent = colors.accent
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(7f, 7f), 0f) }
    // Önizleme ve kesik çerçeve (tuvaldeki gibi)
    androidx.compose.foundation.Canvas(Modifier.matchParentSize().testTag("live_text_preview")) {
        drawIntoCanvas { c ->
            val n = c.nativeCanvas
            val save = n.save()
            n.translate(canvasOrigin.x, canvasOrigin.y)
            n.scale(scale, scale)
            renderer.draw(n, stroke, ink)
            n.restoreToCount(save)
        }
        val p = androidx.compose.ui.graphics.Path().apply {
            q.forEachIndexed { i, pt ->
                val x = canvasOrigin.x + pt.x * scale; val y = canvasOrigin.y + pt.y * scale
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(p, accent, style = Stroke(width = 2f, pathEffect = dash))
    }
    val fx = spec.box?.effects
    val caps = fx?.caps == true
    val boxWidth = spec.box?.width
    val textWidthPx = boxWidth?.let { (it - pads[0] - pads[2]).coerceAtLeast(size) }
    val style = TextStyle(
        color = androidx.compose.ui.graphics.Color.Transparent,
        fontSize = with(density) { size.coerceAtLeast(1f).toSp() },
        fontFamily = composeFamily(spec.font),
        fontWeight = if (spec.bold || spec.paragraph.heading) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (spec.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (spec.underline) TextDecoration.Underline else null,
        letterSpacing = fx?.letterSpacing?.takeIf { it != 0f }?.let { androidx.compose.ui.unit.TextUnit(it.coerceIn(-0.2f, 1f), androidx.compose.ui.unit.TextUnitType.Em) }
            ?: androidx.compose.ui.unit.TextUnit.Unspecified,
        textAlign = when (spec.align) {
            TextAlignKind.LEFT -> TextAlign.Start
            TextAlignKind.CENTER -> TextAlign.Center
            TextAlignKind.RIGHT -> TextAlign.End
        },
        // Tuvaldeki yazı düzeniyle (StaticLayout, includePad) aynı satır yüksekliği
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = true),
    )
    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
            .graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                rotationZ = angle
                scaleX = scale * su
                scaleY = scale * sv
            }
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .onGloballyPositioned { onBottom(it.boundsInRoot().bottom) }
            .testTag("inline_text_editor"),
    ) {
        with(density) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = style,
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (caps) UppercaseTransformation else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier
                    .padding(start = pads[0].toDp(), top = pads[1].toDp(), end = pads[2].toDp(), bottom = pads[3].toDp())
                    .then(
                        if (textWidthPx != null) Modifier.width(textWidthPx.toDp())
                        else Modifier.width(IntrinsicSize.Max).widthIn(min = (size * 0.6f).toDp()),
                    )
                    .focusRequester(focus),
            )
        }
    }
}

/** Büyük harf görünümü (Türkçe kurallarıyla); uzunluk değişirse dönüşüm yapılmaz (imleç kaymasın). */
private object UppercaseTransformation : androidx.compose.ui.text.input.VisualTransformation {
    private val tr = java.util.Locale("tr", "TR")
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val up = text.text.uppercase(tr)
        val out = if (up.length == text.text.length) androidx.compose.ui.text.AnnotatedString(up) else text
        return androidx.compose.ui.text.input.TransformedText(out, androidx.compose.ui.text.input.OffsetMapping.Identity)
    }
}
