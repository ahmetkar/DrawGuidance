package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.billing.PremiumRules
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.ComicCharacter
import com.karapp.drawguidance.data.comicassets.ComicStyle
import com.karapp.drawguidance.data.comicassets.ComicThumbs
import com.karapp.drawguidance.data.comicassets.ComicVariant
import com.karapp.drawguidance.data.comicassets.VariantGroup
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v40 stilin pencerede gösterilen adı. */
@Composable
internal fun styleLabel(style: ComicStyle): String =
    stringResource(if (style == ComicStyle.MANGA) R.string.v40_comic_style_manga else R.string.v40_comic_style_comic)

/** v40 çeşit grubunun adı. */
@Composable
internal fun groupLabel(group: VariantGroup): String = stringResource(
    when (group) {
        VariantGroup.EXPRESSION -> R.string.v40_comic_group_expressions
        VariantGroup.POSE -> R.string.v40_comic_group_poses
        VariantGroup.ACTION -> R.string.v40_comic_group_action
        VariantGroup.DAILY -> R.string.v40_comic_group_daily
    },
)

/**
 * v40 Karakterler penceresi (hazır resimler): stil (Manga / Çizgi Roman) ve tema süzgeçleri, karakter ızgarası.
 * Karaktere dokununca çeşitleri İfadeler, Pozlar, Aksiyon ve Günlük gruplarında görünür; çeşide dokununca sayfaya
 * resim elemanı olarak eklenir. Önizleme (karaktere ve çeşitlerine bakmak) Free planda da açıktır; kilitli
 * karakterin çeşidini eklemek "Premium gerekli" penceresini açar.
 */
@Composable
fun CharactersSheet(onInsert: (ComicVariant) -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    var styleId by rememberSaveable { mutableStateOf<String?>(null) }
    var theme by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val style = ComicStyle.entries.firstOrNull { it.id == styleId }
    val lang = I18n.language
    val premium = com.karapp.drawguidance.ui.premium.LocalPremium.current
    val selected = selectedId?.let { ComicAssets.character(it) }
    ComicSheetFrame(stringResource(R.string.characters_title), stringResource(R.string.v40_comic_chars_hint), "characters_sheet", onDismiss) { columns ->
        if (selected == null) {
            // v42 temalar tema paketlerinden (veritabanındaki paket ↔ karakter bağlantıları; değişince yenilenir)
            val index by com.karapp.drawguidance.data.comicassets.ComicThemeIndex.flow.collectAsState()
            val themes = remember(style, index) { ComicAssets.themes(style) }
            val results = remember(style, theme, index) { ComicAssets.characters(style, theme) }
            Text(stringResource(R.string.v40_comic_style), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("char_style"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuChip(stringResource(R.string.shape_filter_all), style == null, onClick = { styleId = null; theme = null }, modifier = Modifier.testTag("char_style_all"))
                ComicStyle.entries.forEach { s ->
                    NeuChip(styleLabel(s), s == style, onClick = { styleId = s.id; theme = null }, modifier = Modifier.testTag("char_style_${s.id}"))
                }
            }
            Text(stringResource(R.string.v40_comic_theme), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("char_theme"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuChip(stringResource(R.string.shape_filter_all), theme == null, onClick = { theme = null }, modifier = Modifier.testTag("char_theme_all"))
                themes.forEach { t ->
                    NeuChip(ComicAssets.name(t.names, lang), t.id == theme, onClick = { theme = t.id }, modifier = Modifier.testTag("char_theme_${t.id}"))
                }
            }
            Text(stringResource(R.string.characters_count, results.size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("char_count"))
            if (results.isEmpty()) Text(stringResource(R.string.characters_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
            Box(Modifier.testTag("char_results")) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Grid(results, columns) { c, m ->
                        ComicTile(ComicAssets.name(c.names, lang), false, { selectedId = c.id }, m, "char_${c.id}", badge = PremiumRules.characterFeature(c)) {
                            ComicAssetThumb(c.cover.thumb, Modifier.fillMaxWidth().height(96.dp))
                        }
                    }
                }
            }
        } else {
            CharacterVariants(selected, columns, lang, onBack = { selectedId = null }) { v ->
                if (premium.request(PremiumRules.characterFeature(selected))) onInsert(v)
            }
        }
    }
}

@Composable
private fun CharacterVariants(c: ComicCharacter, columns: Int, lang: String, onBack: () -> Unit, onPick: (ComicVariant) -> Unit) {
    val feature = PremiumRules.characterFeature(c)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeuButton(stringResource(R.string.characters_title), onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, modifier = Modifier.testTag("char_back"))
        Text(ComicAssets.name(c.names, lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("char_selected_name"))
    }
    VariantGroup.entries.forEach { g ->
        val list = c.variants(g)
        if (list.isEmpty()) return@forEach
        Text("${groupLabel(g)} (${list.size})", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("char_group_${g.id}"))
        Grid(list, columns) { v, m ->
            ComicTile("", false, { onPick(v) }, m, "charvar_${c.id}_${v.id}", badge = feature) {
                ComicAssetThumb(v.thumb, Modifier.fillMaxWidth().height(104.dp))
            }
        }
    }
}

/** v40 hazır resmin küçük resmi: G/Ç iş parçacığında hedef boyda çözülür, LRU önbellekte tutulur. */
@Composable
fun ComicAssetThumb(path: String, modifier: Modifier, maxPx: Int = 192, crop: Boolean = false) {
    val bmp = com.karapp.drawguidance.ui.components.rememberKeyedThumb(
        path to maxPx, ComicThumbs::cached, dispatcher = kotlinx.coroutines.Dispatchers.IO,
    ) { ComicThumbs.load(it) }
    Box(modifier) {
        bmp?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit) }
    }
}
