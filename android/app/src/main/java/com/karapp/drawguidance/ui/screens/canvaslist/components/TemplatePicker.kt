package com.karapp.drawguidance.ui.screens.canvaslist.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.templates.CanvasTemplates
import com.karapp.drawguidance.data.templates.TemplateEntry
import com.karapp.drawguidance.ui.components.NeuSectionTitle
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v46 kart genişliği ve kapak oranı (dikey sayfa). */
private val CardWidth = 108.dp
private const val COVER_RATIO = 0.72f

/**
 * v46 yeni tuval formunda hazır şablonlar: ilk kart "Boş" (şablonsuz), ardından şablonlar kapak resmi ve adıyla.
 * Seçilen kart vurgulanır; altında seçilen şablonun kısa açıklaması. [selected] null: boş tuval.
 */
@Composable
fun TemplatePicker(templates: List<TemplateEntry>, selected: String?, onSelect: (String?) -> Unit) {
    val colors = NeuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().testTag("template_picker")) {
        NeuSectionTitle(stringResource(R.string.v46_template_section))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TemplateCard(
                title = stringResource(R.string.v46_template_blank),
                selected = selected == null,
                onClick = { onSelect(null) },
                tag = "template_card_blank",
            ) {
                Box(Modifier.fillMaxSize().background(colors.contentMuted.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = colors.contentMuted)
                }
            }
            templates.forEach { t ->
                TemplateCard(
                    title = CanvasTemplates.name(t),
                    selected = selected == t.id,
                    onClick = { onSelect(t.id) },
                    tag = "template_card_${t.id}",
                ) { TemplateCover(t.id) }
            }
        }
        val hint = selected?.let { id -> templates.firstOrNull { it.id == id } }?.let { CanvasTemplates.hint(it) }
        Text(
            hint?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.v46_template_selected_hint, it) }
                ?: stringResource(R.string.v46_template_blank_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.contentMuted,
            modifier = Modifier.testTag("template_hint"),
        )
    }
}

@Composable
private fun TemplateCard(title: String, selected: Boolean, onClick: () -> Unit, tag: String, cover: @Composable () -> Unit) {
    val colors = NeuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(CardWidth)
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = title }
            .testTag(tag)
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO)
                .clip(shape)
                .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.contentMuted.copy(alpha = 0.35f), shape),
        ) { cover() }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.accent else colors.content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(36.dp),
        )
    }
}

/** Şablonun kapak resmi (uygulamadaki WebP; arka planda çözülür). */
@Composable
private fun TemplateCover(id: String) {
    val bitmap by produceState<ImageBitmap?>(null, id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CanvasTemplates.coverBytes(id)?.let { bytes ->
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }
    val b = bitmap
    if (b != null) Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White))
    else Box(Modifier.fillMaxSize().background(NeuTheme.colors.contentMuted.copy(alpha = 0.08f)))
}
