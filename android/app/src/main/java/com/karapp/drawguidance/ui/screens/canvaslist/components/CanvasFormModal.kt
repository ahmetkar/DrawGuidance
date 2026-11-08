package com.karapp.drawguidance.ui.screens.canvaslist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasSizeTemplate
import com.karapp.drawguidance.data.model.CanvasKind
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import com.karapp.drawguidance.ui.common.CanvasBackgroundSwatches
import com.karapp.drawguidance.ui.common.hexToColor
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSectionTitle
import com.karapp.drawguidance.ui.components.NeuSwitch
import com.karapp.drawguidance.ui.components.NeuTextField
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic

data class CanvasFormResult(
    val name: String,
    val width: Int,
    val height: Int,
    val backgroundColorHex: String,
    val aiEnabled: Boolean,
    val kind: CanvasKind = CanvasKind.ART,
    /** Çalışma/not tuvalinde başlangıç sayfa sayısı (resim tuvalinde her zaman 1). */
    val pageCount: Int = 1,
    /** v23 arka plan resmi ve deseni. */
    val background: com.karapp.drawguidance.data.model.BackgroundSpec = com.karapp.drawguidance.data.model.BackgroundSpec(),
    /** Galeriden seçilen arka plan resmi (tuval oluşturulunca eklenir). */
    val galleryUri: android.net.Uri? = null,
    /** v46 seçilen hazır şablon (null: boş tuval). Şablonun boyutu, kâğıdı ve sayfaları kullanılır. */
    val templateId: String? = null,
)

data class CanvasFormInitial(
    val name: String = "",
    val width: Int = CanvasSizeTemplate.SQUARE.width,
    val height: Int = CanvasSizeTemplate.SQUARE.height,
    val backgroundColorHex: String = CanvasBackgroundSwatches.first(),
    /** v30 yeni tuvallerde yapay zekâ öntanımlı açık. */
    val aiEnabled: Boolean = true,
    val kind: CanvasKind = CanvasKind.ART,
    val background: com.karapp.drawguidance.data.model.BackgroundSpec = com.karapp.drawguidance.data.model.BackgroundSpec(),
)

/**
 * Yeni Tuval Oluştur / Tuval Ayarları modalı — aynı form, `isEditMode`
 * yalnızca başlığı ve buton metnini değiştirir. Yapay zeka anahtarı
 * varsayılan olarak KAPALI gelir. Tuvaller yalnızca cihazda saklanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasFormModal(
    isEditMode: Boolean,
    initial: CanvasFormInitial,
    onDismiss: () -> Unit,
    onSubmit: (CanvasFormResult) -> Unit,
    /** v33 seçilebilen türler (ana sayfadan basit/gelişmiş grubu gelir; Tuvallerim'de hepsi). */
    kindChoices: List<CanvasKind> = CanvasKind.Advanced + CanvasKind.Simple,
) {
    val colors = NeuTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by rememberSaveable(initial) { mutableStateOf(initial.name) }
    // v40 boyut: türe uygun hazır boyutlar ve px / in / cm özel boyut (CanvasSizeSection); uygun değilse Oluştur kapalı
    var width by rememberSaveable(initial) { mutableIntStateOf(initial.width) }
    var height by rememberSaveable(initial) { mutableIntStateOf(initial.height) }
    var sizeIssue by remember(initial) { mutableStateOf<SizeIssue?>(null) }
    var backgroundColorHex by rememberSaveable(initial) { mutableStateOf(initial.backgroundColorHex) }
    var aiEnabled by rememberSaveable(initial) { mutableStateOf(initial.aiEnabled) }
    var kind by rememberSaveable(initial) { mutableStateOf(initial.kind) }
    var pageCount by rememberSaveable(initial) { mutableIntStateOf(1) }
    var background by remember(initial) { mutableStateOf(initial.background) }
    var galleryUri by remember(initial) { mutableStateOf<android.net.Uri?>(null) }
    // v46 hazır şablon (çizgi roman ve not alma); tür değişince şablon seçimi kalkar
    var templateId by rememberSaveable(initial) { mutableStateOf<String?>(null) }
    val templates = remember(kind, isEditMode) {
        if (isEditMode || kind !in com.karapp.drawguidance.data.templates.CanvasTemplates.KINDS) emptyList()
        else com.karapp.drawguidance.data.templates.CanvasTemplates.forKind(kind)
    }
    if (templateId != null && templates.none { it.id == templateId }) templateId = null
    val fromTemplate = templateId != null
    val pickBackground = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            galleryUri = uri
            background = background.copy(imageId = com.karapp.drawguidance.ui.components.GALLERY_PENDING, imageFit = com.karapp.drawguidance.data.model.BoxFit.COVER)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.content,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(if (isEditMode) R.string.canvas_settings_title else R.string.new_canvas_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            // Tuval türü: fırçaları ve şekil kataloğunu belirler (oluşturulduktan sonra değişmez)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuSectionTitle(stringResource(R.string.canvas_kind_label))
                if (isEditMode) {
                    Text(com.karapp.drawguidance.ui.components.canvasKindLabel(kind), style = MaterialTheme.typography.bodyLarge)
                } else {
                    // v33 türler iki grupta: gelişmiş çizim (resim, not alma, çizgi roman) ve basit çizim (basit resim,
                    // basit not alma, çocuklar için); kartlar alt alta (dar ekranda açıklamalar okunur kalsın)
                    val groups = listOf(
                        R.string.drawing_group_advanced to kindChoices.filter { !it.simple },
                        R.string.drawing_group_simple to kindChoices.filter { it.simple },
                    ).filter { it.second.isNotEmpty() }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        groups.forEach { (title, kinds) ->
                            if (groups.size > 1) Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = colors.contentMuted)
                            kinds.forEach { k ->
                                KindCard(
                                    title = com.karapp.drawguidance.ui.components.canvasKindLabel(k),
                                    hint = com.karapp.drawguidance.ui.components.canvasKindHint(k),
                                    icon = com.karapp.drawguidance.ui.components.canvasKindIcon(k),
                                    selected = kind == k,
                                    onClick = { kind = k },
                                    modifier = Modifier.fillMaxWidth().testTag("form_kind_${k.name}"),
                                )
                            }
                        }
                    }
                }
            }

            NeuTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.canvas_name_label),
                placeholder = stringResource(R.string.canvas_name_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )

            // v46 hazır şablonlar: kapak resmi ve adıyla; seçilince boyut, kâğıt ve sayfa ayarları şablondan gelir
            if (templates.isNotEmpty()) {
                TemplatePicker(templates, templateId, onSelect = { templateId = it })
            }

            if (!fromTemplate) CanvasSizeSection(
                kind = kind, initialWidth = initial.width, initialHeight = initial.height,
                onSize = { w, h, issue -> width = w; height = h; sizeIssue = issue },
            )

            if (!fromTemplate) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuSectionTitle(stringResource(R.string.canvas_background_color_label))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CanvasBackgroundSwatches.forEach { hex ->
                        val isSelected = hex.equals(backgroundColorHex, ignoreCase = true)
                        val description = stringResource(R.string.background_color_cd, hex)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .neumorphic(colors, CircleShape, elevation = if (isSelected) 2.dp else 5.dp, selected = isSelected)
                                .clip(CircleShape)
                                .clickable { backgroundColorHex = hex }
                                .semantics { contentDescription = description }
                                .padding(if (isSelected) 8.dp else 5.dp)
                                .background(hexToColor(hex), CircleShape),
                        )
                    }
                }
            }

            // v23 arka plan deseni ve resmi (renk üstünde); v33 çocuk tuvalinde sade form (yalnız renk)
            if (kind != CanvasKind.KIDS && !fromTemplate) com.karapp.drawguidance.ui.components.BackgroundPicker(
                usage = if (kind.isNotes) com.karapp.drawguidance.content.paper.PaperUsage.NOTE else com.karapp.drawguidance.content.paper.PaperUsage.ART,
                paperHex = backgroundColorHex,
                spec = background,
                onChange = { spec ->
                    if (spec.imageId != com.karapp.drawguidance.ui.components.GALLERY_PENDING) galleryUri = null
                    background = spec
                },
                onGallery = {
                    pickBackground.launch(
                        androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                // v42 çizgi romanda resim yok (mekân arka planları düzenleyicide)
                showImages = kind != CanvasKind.COMIC,
                galleryPreview = galleryUri?.let { uri ->
                    { m -> coil.compose.AsyncImage(model = uri, contentDescription = null, modifier = m, contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                },
            )

            // Çalışma/not tuvalinde başlangıç sayfa sayısı
            if (kind.paged && !isEditMode && !fromTemplate) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            NeuSectionTitle(stringResource(R.string.page_count_label))
                            // v33 tuval türünün sayfa sınırı (çizgi roman 1000, not alma 200)
                            Text(stringResource(R.string.page_limit_hint, kind.maxPages), style = MaterialTheme.typography.labelSmall, color = colors.contentMuted, modifier = Modifier.testTag("page_limit_hint"))
                        }
                        Text("$pageCount", style = MaterialTheme.typography.labelLarge, color = colors.accent)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 4, 8, 16).filter { it <= kind.maxPages }.forEach { count ->
                            val label = stringResource(R.string.page_count_cd, count)
                            NeuChip(
                                text = "$count",
                                selected = pageCount == count,
                                onClick = { pageCount = count },
                                modifier = Modifier.weight(1f).semantics { contentDescription = label },
                            )
                        }
                    }
                }
            }

            SwitchRow(
                label = stringResource(R.string.ai_switch_label),
                supportingText = stringResource(R.string.ai_switch_hint),
                checked = aiEnabled,
                onCheckedChange = { aiEnabled = it },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                NeuButton(stringResource(R.string.cancel_button), onDismiss, Modifier.weight(1f))
                NeuButton(
                    text = stringResource(if (isEditMode) R.string.update_button else R.string.create_button),
                    primary = true,
                    enabled = sizeIssue == null || fromTemplate,
                    modifier = Modifier.weight(1f).testTag("canvas_form_submit"),
                    onClick = {
                        onSubmit(
                            CanvasFormResult(
                                name = name.ifBlank { "Adsız Tuval" },
                                width = width.coerceIn(CanvasSizing.MIN_SIDE, CanvasSizing.MAX_SIDE),
                                height = height.coerceIn(CanvasSizing.MIN_SIDE, CanvasSizing.MAX_SIDE),
                                backgroundColorHex = backgroundColorHex,
                                aiEnabled = aiEnabled,
                                kind = kind,
                                pageCount = if (kind.paged) pageCount.coerceIn(1, kind.maxPages) else 1,
                                background = background,
                                galleryUri = if (fromTemplate) null else galleryUri,
                                templateId = templateId,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun KindCard(
    title: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    Row(
        modifier = modifier
            .neumorphic(colors, androidx.compose.foundation.shape.RoundedCornerShape(18.dp), elevation = if (selected) 3.dp else 6.dp, selected = selected)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) colors.accent else colors.contentMuted, modifier = Modifier.size(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (selected) colors.accent else colors.content)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = NeuTheme.colors.contentMuted)
            }
        }
        NeuSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
