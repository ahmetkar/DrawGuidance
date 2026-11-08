package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.West
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.karapp.drawguidance.R
import com.karapp.drawguidance.content.comic.ComicLines
import com.karapp.drawguidance.content.comic.ComicLinesKind
import com.karapp.drawguidance.content.comic.ComicSfx
import com.karapp.drawguidance.content.comic.SfxPreset
import com.karapp.drawguidance.data.model.BalloonSpec
import com.karapp.drawguidance.data.model.BalloonStyle
import com.karapp.drawguidance.data.model.TextAlignKind
import com.karapp.drawguidance.data.model.TextFont
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.drawing.BalloonRenderer
import com.karapp.drawguidance.ui.drawing.TailDirection
import com.karapp.drawguidance.ui.screens.canvasedit.state.BalloonTextOptions
import com.karapp.drawguidance.ui.screens.canvasedit.state.ComicController
import com.karapp.drawguidance.ui.theme.NeuCardShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic
import com.karapp.drawguidance.ui.components.NeuSheet

/** v27 balon biçimlerinin adları. */
@Composable
fun balloonLabel(style: BalloonStyle): String = stringResource(balloonLabelRes(style))

/** v43 balon biçiminin adının kaynağı (taban paket dışa aktarımı da kullanır). */
fun balloonLabelRes(style: BalloonStyle): Int = (
    when (style) {
        BalloonStyle.SPEECH -> R.string.balloon_speech
        BalloonStyle.ROUNDED -> R.string.balloon_rounded
        BalloonStyle.THOUGHT -> R.string.balloon_thought
        BalloonStyle.SHOUT -> R.string.balloon_shout
        BalloonStyle.WHISPER -> R.string.balloon_whisper
        BalloonStyle.NARRATION -> R.string.balloon_narration
        BalloonStyle.ELECTRIC -> R.string.balloon_electric
        BalloonStyle.WAVY -> R.string.balloon_wavy
        BalloonStyle.DOUBLE -> R.string.balloon_double
        BalloonStyle.ICICLE -> R.string.balloon_icicle
        BalloonStyle.DRIP -> R.string.balloon_drip
        BalloonStyle.SCALLOP -> R.string.balloon_scallop
        BalloonStyle.FLASH -> R.string.balloon_flash
        BalloonStyle.HEART -> R.string.balloon_heart
        BalloonStyle.CUT -> R.string.balloon_cut
        BalloonStyle.ROUGH -> R.string.balloon_rough
        BalloonStyle.PHONE -> R.string.balloon_phone
        BalloonStyle.FREE -> R.string.balloon_free
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComicSheetFrame(title: String, hint: String, tag: String, onDismiss: () -> Unit, content: @Composable (columns: Int) -> Unit) {
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        BoxWithConstraints(Modifier.fillMaxWidth().navigationBarsPadding().testTag(tag)) {
            val columns = (maxWidth / 104.dp).toInt().coerceIn(3, 6)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                content(columns)
            }
        }
    }
}

@Composable
internal fun ComicTile(
    label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, tag: String,
    /** v39 Premium kilidi (null: yok): kilitliyse taç gösterilir, dokununca "Premium gerekli". */
    premium: com.karapp.drawguidance.data.billing.PremiumFeature? = null,
    /** v39 yalnız taç (seçilebilir; işlem ayrıca kilitlenir, ör. karakter önizlemesi). */
    badge: com.karapp.drawguidance.data.billing.PremiumFeature? = null,
    preview: @Composable () -> Unit,
) {
    val colors = NeuTheme.colors
    val access = com.karapp.drawguidance.ui.premium.LocalPremium.current
    val locked = access.locked(premium) || access.locked(badge)
    androidx.compose.foundation.layout.Box(modifier) {
        Column(
            Modifier.fillMaxWidth().neumorphic(colors, NeuCardShape, elevation = if (selected) 3.dp else 5.dp, selected = selected)
                .clip(NeuCardShape)
                .clickable(role = Role.Button) { if (access.request(premium)) onClick() }
                .semantics(mergeDescendants = true) { contentDescription = label; if (selected) this.selected = true }
                .testTag(if (locked) "${tag}_locked" else tag)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            preview()
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) colors.accent else colors.content, maxLines = 1, textAlign = TextAlign.Center)
        }
        if (locked) com.karapp.drawguidance.ui.premium.PremiumLockBadge(Modifier.align(Alignment.TopEnd).padding(4.dp))
    }
}

@Composable
internal fun <T> Grid(items: List<T>, columns: Int, cell: @Composable (T, Modifier) -> Unit) {
    items.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { cell(it, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** v36 balon penceresinin sekmeleri: tür, yazı ve kuyruk. */
enum class BalloonTab { TYPE, TEXT, TAIL }

/** v36 ana balon türleri (istenen sırayla); ses efekti yazısı ([SFX_TYPE]) en sonda, kalan biçimler "Diğer biçimler"de. */
private val MAIN_STYLES = listOf(
    BalloonStyle.SPEECH, BalloonStyle.THOUGHT, BalloonStyle.SHOUT, BalloonStyle.WHISPER,
    BalloonStyle.NARRATION, BalloonStyle.PHONE, BalloonStyle.FREE,
)
private const val SFX_TYPE = "SFX"

/** v36 balon yazısının hızlı seçilen yazı tipleri (hepsi Türkçe harfli); tam liste yazı tipi düğmesinde. */
private val BALLOON_FONTS = listOf(
    TextFont.COMIC_RELIEF, TextFont.COMIC_RELIEF_BOLD, TextFont.BANGERS, TextFont.LUCKIEST_GUY, TextFont.SHANTELL, TextFont.BOOGALOO,
    TextFont.GRANDSTANDER, TextFont.BUNGEE, TextFont.PATRICK_HAND, TextFont.KALAM, TextFont.CAVEAT, TextFont.TYPEWRITER, TextFont.SANS,
)
private val TEXT_COLORS = listOf("#111111", "#FFFFFF", "#E53935", "#1E88E5", "#43A047", "#8E24AA", "#FF7A1A", "#5D4037", "#FFD23F")

/** v36 ana türlerin pencerede gösterilen adı (konuşma "Normal", anlatı "Anlatıcı kutusu"). */
@Composable
private fun typeLabel(type: String): String = when (type) {
    SFX_TYPE -> stringResource(R.string.balloon_sfx)
    BalloonStyle.SPEECH.name -> stringResource(R.string.balloon_normal)
    BalloonStyle.NARRATION.name -> stringResource(R.string.balloon_narrator_box)
    else -> balloonLabel(BalloonStyle.valueOf(type))
}

/** Türün önizlemedeki balonu (telefon mesajı kendi renginde; serbest yazı ve ses yazısında balon yok). */
private fun previewSpec(style: BalloonStyle, speakers: Int): BalloonSpec = when (style) {
    BalloonStyle.PHONE -> BalloonSpec(
        style = style, tails = BalloonRenderer.tailsToward(TailDirection.BOTTOM_RIGHT, speakers.coerceIn(1, 2), 0.22f, 0.7f),
        fillHex = ComicController.PHONE_FILL, strokeHex = ComicController.PHONE_BORDER,
    )
    BalloonStyle.FREE -> BalloonSpec(style = style, tails = emptyList())
    else -> BalloonSpec(style = style, tails = BalloonRenderer.defaultTails(if (style == BalloonStyle.NARRATION) 0 else speakers.coerceAtMost(2)))
}

/**
 * v27 konuşma balonu penceresi: kişi sayısı (kuyruk; 0 kuyruksuz, 2+ birden çok konuşana işaret eder) ve balon
 * biçimleri. Eklemede dokunulan biçim eklenir; seçili balonda ([current]) biçim, kişi sayısı ve renkler değişir.
 *
 * v36 üç sekme: Tür (normal, düşünce, bağırma, fısıltı, anlatıcı kutusu, telefon mesajı, serbest yazı, SFX yazısı ve
 * diğer biçimler), Yazı (yazı, yazı tipi, boyut, renk, hizalama; canlı önizleme Türkçe harflerle) ve Kuyruk (yön,
 * uzunluk, eğrilik). Yeni balonda ayarlar eklenecek balona uygulanır ([onInsert]); seçili balonda hemen değişir
 * ([onText], [onTails]).
 */
@Composable
fun BalloonSheet(
    current: BalloonSpec?,
    onPick: (style: BalloonStyle, speakers: Int) -> Unit,
    onDismiss: () -> Unit,
    onColors: ((fill: String?, stroke: String?) -> Unit)? = null,
    /** v29 seçili balonun kutusu ve yazı boyu: eni ve boyu buradan (ya da Ölçekle'deki tutamaklardan) değişir. */
    box: com.karapp.drawguidance.data.model.TextBox? = null,
    sizePx: Float = 48f,
    onSize: ((width: Float?, height: Float?, setWidth: Boolean, setHeight: Boolean) -> Unit)? = null,
    /** v36 düzenlenen balon elemanı: yazısı, yazı tipi, boyutu, rengi ve hizası pencerede gösterilir. */
    stroke: com.karapp.drawguidance.data.model.Stroke? = null,
    /** v36 yeni balon yazı ve kuyruk ayarlarıyla (verilmezse [onPick]). */
    onInsert: ((style: BalloonStyle, speakers: Int, options: BalloonTextOptions) -> Unit)? = null,
    /** v36 SFX yazısı (yeni bir efekt yazısı eklenir). */
    onSfx: ((BalloonTextOptions) -> Unit)? = null,
    onText: ((content: String?, font: TextFont?, sizePx: Float?, colorHex: String?, align: TextAlignKind?) -> Unit)? = null,
    onTails: ((dir: TailDirection, length: Float, curve: Float) -> Unit)? = null,
    /** v36 yeni balonun öntanımlı yazı tipi (yazı ayarlarındaki çizgi roman yazı tipi). */
    defaultFont: TextFont = TextFont.COMIC_RELIEF,
) {
    val colors = NeuTheme.colors
    val editing = current != null
    var speakers by remember { mutableIntStateOf(current?.tails?.size ?: 1) }
    var tab by rememberSaveable { mutableStateOf(BalloonTab.TYPE) }
    // v36 yeni balonun yazı/kuyruk ayarları; düzenlemede seçili balonunkiler
    var content by rememberSaveable { mutableStateOf(stroke?.text?.content ?: "") }
    var font by rememberSaveable { mutableStateOf(stroke?.text?.font) }
    var textSize by rememberSaveable { mutableStateOf(stroke?.sizePx) }
    var textColor by rememberSaveable { mutableStateOf(stroke?.colorHex) }
    var align by rememberSaveable { mutableStateOf(stroke?.text?.align ?: TextAlignKind.CENTER) }
    val tail0 = remember { current?.let { BalloonRenderer.tailState(it) } }
    var tailDir by rememberSaveable { mutableStateOf(tail0?.first ?: TailDirection.BOTTOM_LEFT) }
    var tailLength by rememberSaveable { mutableFloatStateOf(tail0?.second ?: BalloonTextOptions.DEFAULT_TAIL_LENGTH) }
    var tailCurve by rememberSaveable { mutableFloatStateOf(tail0?.third ?: BalloonTextOptions.DEFAULT_TAIL_CURVE) }
    var picked by rememberSaveable { mutableStateOf(BalloonStyle.SPEECH.name) }
    val options = BalloonTextOptions(content, font, textSize, textColor, align, tailDir, tailLength, tailCurve)
    val insert: (String) -> Unit = { type ->
        when {
            type == SFX_TYPE -> onSfx?.invoke(options)
            onInsert != null -> onInsert(BalloonStyle.valueOf(type), speakers, options)
            else -> onPick(BalloonStyle.valueOf(type), speakers)
        }
    }
    // Önizlenen tür: düzenlemede balonun kendisi, eklemede yazı/kuyruk sekmesinde seçilen tür
    val shownType = current?.style?.name ?: picked
    val shownSpec = current ?: if (shownType == SFX_TYPE) null else previewSpec(BalloonStyle.valueOf(shownType), speakers).let { b ->
        if (options.customTail && b.style != BalloonStyle.FREE && speakers > 0) b.copy(tails = BalloonRenderer.tailsToward(tailDir, speakers, tailLength, tailCurve)) else b
    }
    ComicSheetFrame(
        stringResource(if (current == null) R.string.balloon_title else R.string.balloon_edit_title),
        stringResource(R.string.balloon_hint), "balloon_sheet", onDismiss,
    ) { columns ->
        PillTabs(
            listOf(
                Triple(BalloonTab.TYPE, stringResource(R.string.balloon_tab_type), Icons.Filled.ChatBubble),
                Triple(BalloonTab.TEXT, stringResource(R.string.balloon_tab_text), Icons.Filled.TextFields),
                Triple(BalloonTab.TAIL, stringResource(R.string.balloon_tab_tail), Icons.AutoMirrored.Filled.CallMade),
            ),
            tab, tagPrefix = "balloon_tab_",
        ) { tab = it }
        when (tab) {
            BalloonTab.TYPE -> {
                Text(stringResource(R.string.balloon_speakers), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..4).forEach { n ->
                        NeuChip(
                            if (n == 0) stringResource(R.string.balloon_no_tail) else stringResource(R.string.balloon_people, n), speakers == n,
                            onClick = { speakers = n; if (current != null) onPick(current.style, n) },
                            modifier = Modifier.testTag("balloon_speakers_$n"),
                        )
                    }
                }
                Text(stringResource(R.string.balloon_types), style = MaterialTheme.typography.labelLarge)
                val main = MAIN_STYLES.map { it.name } + if (editing || onSfx == null) emptyList() else listOf(SFX_TYPE)
                Grid(main, columns) { type, m ->
                    ComicTile(
                        typeLabel(type), current?.style?.name == type,
                        { if (current != null) onPick(BalloonStyle.valueOf(type), speakers) else insert(type) }, m, "balloon_$type",
                    ) { TypePreview(type, speakers, font ?: defaultFont) }
                }
                Text(stringResource(R.string.balloon_more_styles), style = MaterialTheme.typography.labelLarge)
                Grid(BalloonStyle.entries.filter { it !in MAIN_STYLES }, columns) { style, m ->
                    ComicTile(
                        balloonLabel(style), current?.style == style,
                        { if (current != null) onPick(style, speakers) else insert(style.name) }, m, "balloon_${style.name}",
                    ) { BalloonPreview(previewSpec(style, speakers)) }
                }
                if (current != null && onSize != null) {
                    // v29 balonun boyutu: en (yazı satırları buna göre kaydırılır) ve boy (otomatik ya da sabit)
                    Text(stringResource(R.string.balloon_size), style = MaterialTheme.typography.labelLarge)
                    val minW = sizePx * 3f; val maxW = (sizePx * 40f).coerceAtLeast(minW + 1f)
                    com.karapp.drawguidance.ui.components.LabeledValueSlider(
                        stringResource(R.string.text_box_width), "${(box?.width ?: minW).toInt()}", (box?.width ?: minW).coerceIn(minW, maxW), minW..maxW,
                    ) { onSize(it.toInt().toFloat(), null, true, false) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.text_box_height), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        NeuChip(stringResource(R.string.text_box_auto), box?.height == null, onClick = { onSize(null, null, false, true) }, modifier = Modifier.testTag("balloon_height_auto"))
                        NeuChip(stringResource(R.string.text_box_fixed), box?.height != null, onClick = { if (box?.height == null) onSize(null, sizePx * 5f, false, true) }, modifier = Modifier.testTag("balloon_height_fixed"))
                    }
                    box?.height?.let { hgt ->
                        val minH = sizePx * 1.5f; val maxH = sizePx * 40f
                        com.karapp.drawguidance.ui.components.LabeledValueSlider(
                            stringResource(R.string.text_box_height), "${hgt.toInt()}", hgt.coerceIn(minH, maxH), minH..maxH,
                        ) { onSize(null, it.toInt().toFloat(), false, true) }
                    }
                    Text(stringResource(R.string.balloon_size_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
                if (current != null && onColors != null) {
                    Text(stringResource(R.string.balloon_fill), style = MaterialTheme.typography.labelLarge)
                    SwatchRow(current.fillHex) { onColors(it, null) }
                    Text(stringResource(R.string.balloon_border), style = MaterialTheme.typography.labelLarge)
                    SwatchRow(current.strokeHex) { onColors(null, it) }
                }
                Text(stringResource(R.string.balloon_tail_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            }
            BalloonTab.TEXT -> {
                val shownFont = font ?: defaultFont
                val shownSize = textSize ?: sizePx
                val autoInk = if (shownType == BalloonStyle.PHONE.name) "#FFFFFF" else "#111111"
                BalloonLivePreview(shownSpec, shownType == SFX_TYPE, content, shownFont, textColor ?: autoInk, align)
                com.karapp.drawguidance.ui.components.NeuTextField(
                    value = content,
                    onValueChange = { v -> content = v; if (editing && v.isNotBlank()) onText?.invoke(v, null, null, null, null) },
                    label = stringResource(R.string.balloon_text),
                    placeholder = stringResource(R.string.balloon_text_placeholder),
                    supportingText = if (editing) null else stringResource(R.string.balloon_text_hint),
                    singleLine = false,
                    fieldModifier = Modifier.testTag("balloon_text"),
                )
                Text(stringResource(R.string.text_font_label), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("balloon_fonts"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (if (shownFont in BALLOON_FONTS) BALLOON_FONTS else listOf(shownFont) + BALLOON_FONTS).forEach { f ->
                        FontChip(f, f == shownFont) { font = f; if (editing) onText?.invoke(null, f, null, null, null) }
                    }
                }
                FontPickerButton(shownFont, onPick = { f -> font = f; if (editing) onText?.invoke(null, f, null, null, null) })
                Tagged("balloon_text_size") {
                    com.karapp.drawguidance.ui.components.LabeledValueSlider(
                        stringResource(R.string.text_size_label), "${shownSize.roundToInt()} px", shownSize.coerceIn(12f, 200f), 12f..200f,
                    ) {
                        val v = it.roundToInt().toFloat()
                        if (v != shownSize) { textSize = v; if (editing) onText?.invoke(null, null, v, null, null) }
                    }
                }
                Text(stringResource(R.string.text_color), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("balloon_text_color"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!editing) {
                        val chosen = textColor == null
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(30.dp).clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Black, Color.Black, Color.White, Color.White)))
                                .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                                .clickable(role = Role.RadioButton) { textColor = null }
                                .semantics { contentDescription = "balloon_text_auto"; this.selected = chosen },
                        )
                    }
                    TEXT_COLORS.forEach { hex ->
                        val chosen = hex.equals(textColor ?: if (editing) autoInk else null, ignoreCase = true)
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(30.dp).clip(CircleShape).background(hexToColor(hex))
                                .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                                .clickable(role = Role.RadioButton) { textColor = hex; if (editing) onText?.invoke(null, null, null, hex, null) }
                                .semantics { contentDescription = hex; this.selected = chosen },
                        )
                    }
                }
                Text(stringResource(R.string.balloon_align), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(TextAlignKind.LEFT, R.string.text_align_left, Icons.AutoMirrored.Filled.FormatAlignLeft),
                        Triple(TextAlignKind.CENTER, R.string.text_align_center, Icons.Filled.FormatAlignCenter),
                        Triple(TextAlignKind.RIGHT, R.string.text_align_right, Icons.AutoMirrored.Filled.FormatAlignRight),
                    ).forEach { (kind, label, icon) ->
                        NeuChip(stringResource(label), align == kind, onClick = {
                            align = kind; if (editing) onText?.invoke(null, null, null, null, kind)
                        }, modifier = Modifier.testTag("balloon_align_${kind.name}"), icon = icon)
                    }
                }
                if (!editing) InsertRow(picked, onSfx != null, { picked = it }) { insert(picked) }
            }
            BalloonTab.TAIL -> {
                BalloonLivePreview(shownSpec, shownType == SFX_TYPE, content, font ?: defaultFont, textColor ?: if (shownType == BalloonStyle.PHONE.name) "#FFFFFF" else "#111111", align)
                if (shownType == BalloonStyle.FREE.name || shownType == SFX_TYPE) {
                    Text(stringResource(R.string.balloon_tail_free), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
                } else {
                    val apply: () -> Unit = { if (editing) onTails?.invoke(tailDir, tailLength, tailCurve) }
                    Text(stringResource(R.string.balloon_tail_direction), style = MaterialTheme.typography.labelLarge)
                    Grid(TailDirection.entries, 3) { dir, m ->
                        NeuChip(
                            stringResource(tailLabel(dir)), dir == tailDir,
                            onClick = { tailDir = dir; if (speakers == 0) speakers = 1; apply() },
                            modifier = m.testTag("balloon_tail_${dir.name}"), icon = tailIcon(dir),
                        )
                    }
                    Tagged("balloon_tail_length") {
                        com.karapp.drawguidance.ui.components.LabeledValueSlider(
                            stringResource(R.string.balloon_tail_length), "%${(tailLength * 100).roundToInt()}", tailLength,
                            BalloonRenderer.MIN_TAIL..BalloonRenderer.MAX_TAIL,
                        ) {
                            val v = (it * 20f).roundToInt() / 20f
                            if (v != tailLength) { tailLength = v; apply() }
                        }
                    }
                    Tagged("balloon_tail_curve") {
                        com.karapp.drawguidance.ui.components.LabeledValueSlider(
                            stringResource(R.string.balloon_tail_curve), "${(tailCurve * 100).roundToInt()}", tailCurve, -1f..1f,
                        ) {
                            val v = (it * 20f).roundToInt() / 20f
                            if (v != tailCurve) { tailCurve = v; apply() }
                        }
                    }
                    Text(stringResource(R.string.balloon_tail_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                }
                if (!editing) InsertRow(picked, onSfx != null, { picked = it }) { insert(picked) }
            }
        }
    }
}

private fun tailLabel(dir: TailDirection): Int = when (dir) {
    TailDirection.BOTTOM_LEFT -> R.string.balloon_tail_bottom_left
    TailDirection.BOTTOM_RIGHT -> R.string.balloon_tail_bottom_right
    TailDirection.LEFT -> R.string.balloon_tail_left
    TailDirection.RIGHT -> R.string.balloon_tail_right
    TailDirection.TOP_LEFT -> R.string.balloon_tail_top_left
    TailDirection.TOP_RIGHT -> R.string.balloon_tail_top_right
}

private fun tailIcon(dir: TailDirection) = when (dir) {
    TailDirection.BOTTOM_LEFT -> Icons.Filled.SouthWest
    TailDirection.BOTTOM_RIGHT -> Icons.Filled.SouthEast
    TailDirection.LEFT -> Icons.Filled.West
    TailDirection.RIGHT -> Icons.Filled.East
    TailDirection.TOP_LEFT -> Icons.Filled.NorthWest
    TailDirection.TOP_RIGHT -> Icons.Filled.NorthEast
}

/** v36 yeni balonda yazı ve kuyruk sekmelerinin altı: tür seçimi ve ekleme düğmesi. */
@Composable
private fun InsertRow(picked: String, sfx: Boolean, onType: (String) -> Unit, onInsert: () -> Unit) {
    val colors = NeuTheme.colors
    Text(stringResource(R.string.balloon_new_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (MAIN_STYLES.map { it.name } + if (sfx) listOf(SFX_TYPE) else emptyList()).forEach { type ->
            NeuChip(typeLabel(type), type == picked, onClick = { onType(type) }, modifier = Modifier.testTag("balloon_pick_$type"))
        }
    }
    com.karapp.drawguidance.ui.components.NeuButton(
        stringResource(R.string.balloon_insert, typeLabel(picked)), onClick = onInsert,
        modifier = Modifier.fillMaxWidth().testTag("balloon_insert"),
    )
}

/** v36 yazı tipi düğmesi: adı kendi harfleriyle. */
@Composable
private fun FontChip(font: TextFont, selected: Boolean, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    Row(
        Modifier.height(40.dp).clip(com.karapp.drawguidance.ui.theme.NeuPillShape)
            .background(if (selected) colors.accent else colors.background)
            .border(1.dp, if (selected) colors.accent else colors.darkShadow.copy(alpha = 0.45f), com.karapp.drawguidance.ui.theme.NeuPillShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = font.title; this.selected = selected }
            .testTag("balloon_font_${font.name}")
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            font.title, fontFamily = composeFamily(font), translate = false, maxLines = 1,
            style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.onAccent else colors.content,
        )
    }
}

/** v36 türün küçük önizlemesi: balon, serbest yazıda yalnız harfler, SFX'te efekt yazısı. */
@Composable
private fun TypePreview(type: String, speakers: Int, font: TextFont) {
    when (type) {
        SFX_TYPE -> Text(
            "BAM!", translate = false, fontFamily = composeFamily(TextFont.BANGERS), color = Color(0xFFFF5722), fontSize = 26.sp,
            modifier = Modifier.height(58.dp).padding(top = 12.dp),
        )
        BalloonStyle.FREE.name -> Text(
            "Aa Çğ", translate = false, fontFamily = composeFamily(font), color = NeuTheme.colors.content, fontSize = 22.sp,
            modifier = Modifier.height(58.dp).padding(top = 14.dp),
        )
        else -> BalloonPreview(previewSpec(BalloonStyle.valueOf(type), speakers))
    }
}

/**
 * v36 yazının balon içinde canlı önizlemesi: seçilen yazı tipi, renk ve hizalamayla (yazı boşsa Türkçe harfli örnek);
 * kuyruk ayarları balonda görünür. SFX yazısı büyük harfle (Türkçe kurallarıyla: i → İ).
 */
@Composable
private fun BalloonLivePreview(spec: BalloonSpec?, sfx: Boolean, content: String, font: TextFont, colorHex: String, align: TextAlignKind) {
    val tr = java.util.Locale.forLanguageTag("tr-TR")
    val text = when {
        sfx -> content.trim().ifEmpty { "BAM!" }.uppercase(tr)
        else -> content.ifBlank { "Merhaba! Çağrı, ışık, İğne, öğüş" }
    }
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF1F3F5)).testTag("balloon_preview"),
        contentAlignment = Alignment.Center,
    ) {
        if (spec != null && !sfx) {
            Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                val w = size.width * 0.56f; val h = size.height * 0.36f
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    n.save()
                    n.translate((size.width - w) / 2f, (size.height - h) / 2f)
                    BalloonRenderer.draw(n, spec, w, h, h * 0.3f, 1f, false)
                    n.restore()
                }
            }
        }
        Text(
            text, translate = false, fontFamily = composeFamily(font), color = hexToColor(colorHex),
            fontSize = if (sfx) 34.sp else 15.sp, maxLines = 2, lineHeight = if (sfx) 36.sp else 18.sp,
            textAlign = when (align) { TextAlignKind.LEFT -> TextAlign.Left; TextAlignKind.RIGHT -> TextAlign.Right; TextAlignKind.CENTER -> TextAlign.Center },
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(if (sfx) 0.9f else 0.46f).testTag("balloon_preview_text"),
        )
    }
}

@Composable
private fun SwatchRow(selected: String, onPick: (String) -> Unit) {
    val colors = NeuTheme.colors
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("#FFFFFF", "#111111", "#FFF59D", "#FFCDD2", "#BBDEFB", "#C8E6C9", "#E1BEE7", "#FFE0B2", "#E53935", "#1E88E5").forEach { hex ->
            val chosen = hex.equals(selected, ignoreCase = true)
            androidx.compose.foundation.layout.Box(
                Modifier.size(30.dp).clip(CircleShape).background(hexToColor(hex))
                    .border(if (chosen) 3.dp else 1.dp, if (chosen) colors.accent else colors.contentMuted.copy(alpha = 0.4f), CircleShape)
                    .clickable { onPick(hex) }.semantics { contentDescription = hex; if (chosen) this.selected = true },
            )
        }
    }
}

/** Balonun küçük önizlemesi (gerçek çizimle). */
@Composable
private fun BalloonPreview(b: BalloonSpec) {
    Canvas(Modifier.fillMaxWidth().height(58.dp)) {
        val w = size.width * 0.78f; val h = size.height * 0.52f
        drawIntoCanvas { c ->
            val n = c.nativeCanvas
            n.save()
            n.translate((size.width - w) / 2f, size.height * 0.06f)
            BalloonRenderer.draw(n, b, w, h, h * 0.28f, 1f, false)
            n.restore()
        }
    }
}

/** v29 çizgi türlerinin adları. */
@Composable
fun linesLabel(kind: ComicLinesKind): String = stringResource(
    when (kind) {
        ComicLinesKind.SPEED_H -> R.string.lines_speed_h
        ComicLinesKind.SPEED_V -> R.string.lines_speed_v
        ComicLinesKind.FOCUS -> R.string.lines_focus
        ComicLinesKind.BURST -> R.string.lines_burst
        ComicLinesKind.SPEED_DIAG -> R.string.lines_speed_diag
        ComicLinesKind.MOTION_CURVE -> R.string.lines_motion_curve
        ComicLinesKind.FOCUS_ROUGH -> R.string.lines_focus_rough
        ComicLinesKind.FLASH -> R.string.lines_flash
        ComicLinesKind.RADIATE -> R.string.lines_radiate
        ComicLinesKind.DRIP -> R.string.lines_drip
        ComicLinesKind.WAVER -> R.string.lines_waver
        ComicLinesKind.BLUSH -> R.string.lines_blush
        ComicLinesKind.SWIRL -> R.string.lines_swirl
        ComicLinesKind.RINGS -> R.string.lines_rings
        ComicLinesKind.ZOOM -> R.string.lines_zoom
        ComicLinesKind.PERSPECTIVE -> R.string.lines_perspective
        ComicLinesKind.BEAMS -> R.string.lines_beams
        ComicLinesKind.MANGA_DENSE -> R.string.lines_manga_dense
        ComicLinesKind.ANIME_SOFT -> R.string.lines_anime_soft
    },
)

/** v27 resim filtreleri penceresi (çizgi roman görünümü). */
@Composable
fun ImageFilterSheet(onPick: (com.karapp.drawguidance.content.comic.ComicFilter) -> Unit, onDismiss: () -> Unit) {
    val ink = NeuTheme.colors.content
    ComicSheetFrame(stringResource(R.string.image_filter), stringResource(R.string.image_filter_hint), "image_filter_sheet", onDismiss) { columns ->
        val kinds = listOf(
            com.karapp.drawguidance.content.comic.ComicFilter.COMIC to R.string.filter_comic,
            com.karapp.drawguidance.content.comic.ComicFilter.HALFTONE to R.string.filter_halftone,
            com.karapp.drawguidance.content.comic.ComicFilter.MANGA to R.string.filter_manga,
            com.karapp.drawguidance.content.comic.ComicFilter.POP to R.string.filter_pop,
        )
        Grid(kinds, columns.coerceAtMost(4)) { (k, label), m ->
            ComicTile(stringResource(label), false, { onPick(k) }, m, "filter_${k.name}", premium = com.karapp.drawguidance.data.billing.PremiumFeature.IMAGE_EFFECTS_ADVANCED.takeIf { com.karapp.drawguidance.data.billing.PremiumRules.isAdvancedFilter(k) }) {
                Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                    when (k) {
                        com.karapp.drawguidance.content.comic.ComicFilter.HALFTONE, com.karapp.drawguidance.content.comic.ComicFilter.MANGA -> {
                            val step = 9.dp.toPx()
                            var y = step / 2f
                            while (y < size.height) {
                                var x = step / 2f
                                while (x < size.width) {
                                    val r = step * 0.45f * (x / size.width)
                                    drawCircle(ink, r, androidx.compose.ui.geometry.Offset(x, y))
                                    x += step
                                }
                                y += step
                            }
                        }
                        else -> {
                            val cols = if (k == com.karapp.drawguidance.content.comic.ComicFilter.POP) listOf(Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF2979FF)) else listOf(Color(0xFFFFCC80), Color(0xFF81C784), Color(0xFF64B5F6))
                            cols.forEachIndexed { i, c ->
                                drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(size.width * i / 3f, 0f), size = androidx.compose.ui.geometry.Size(size.width / 3f, size.height))
                            }
                            drawRect(ink, style = Stroke(width = 3.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
}
