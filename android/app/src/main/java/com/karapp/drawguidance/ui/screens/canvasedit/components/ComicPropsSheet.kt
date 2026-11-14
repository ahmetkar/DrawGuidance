package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.billing.PremiumRules
import com.karapp.drawguidance.data.comicassets.ComicAssetKind
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.ComicProp
import com.karapp.drawguidance.data.comicassets.ComicStyle
import com.karapp.drawguidance.data.comicassets.ComicThemeIndex
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v42 tema paketi süzgeci: stilde bu türden varlığı olan paketler ("Tümü" + paketler). Paketler veritabanındaki
 * paket ↔ varlık bağlantılarından gelir ([ComicThemeIndex]).
 */
@Composable
internal fun ThemePackChips(kind: ComicAssetKind, style: ComicStyle?, selected: String?, tag: String, onSelect: (String?) -> Unit) {
    val index by ComicThemeIndex.flow.collectAsState()
    val packs = remember(kind, style, index) { ComicAssets.themesFor(kind, style) }
    if (packs.isEmpty()) return
    val lang = I18n.language
    Text(stringResource(R.string.v40_comic_theme), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(tag), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeuChip(stringResource(R.string.shape_filter_all), selected == null, onClick = { onSelect(null) }, modifier = Modifier.testTag("${tag}_all"))
        packs.forEach { t ->
            NeuChip(ComicAssets.name(t.names, lang), t.id == selected, onClick = { onSelect(t.id) }, modifier = Modifier.testTag("${tag}_${t.id}"))
        }
    }
}

/**
 * v42 hazır efekt ya da nesne resimleri: stil (Manga / Çizgi roman; v43 yalnız efektlerde), tema paketi ve kategori
 * süzgeçleri; dokununca [onPick]. Free planda açık olmayan kategorilerde taç gösterilir ([PremiumRules.propFeature]).
 */
@Composable
internal fun ComicPropsBrowser(kind: ComicAssetKind, columns: Int, tag: String, onPick: (ComicProp) -> Unit) {
    // v43 nesneler stile ayrılmaz: aynı nesne Manga ve Çizgi roman sekmelerinde iki kez görünmesin (tek liste)
    val showStyles = kind != ComicAssetKind.OBJECT
    val colors = NeuTheme.colors
    var styleId by rememberSaveable(kind) { mutableStateOf(ComicStyle.COMIC.id) }
    val theme: String? = null
    var category by rememberSaveable(kind) { mutableStateOf<String?>(null) }
    val style = ComicStyle.entries.firstOrNull { it.id == styleId } ?: ComicStyle.COMIC
    val index by ComicThemeIndex.flow.collectAsState()
    val lang = I18n.language
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showStyles) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("${tag}_styles"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComicStyle.entries.forEach { s ->
                NeuChip(styleLabel(s), s == style, onClick = { styleId = s.id; category = null }, modifier = Modifier.testTag("${tag}_style_${s.id}"))
            }
        }
        // v50 efekt ve nesnelerde tema paketleri ("Şehir ve ev", "Uzman karakterler"…) gösterilmez: eklenecek resme
        // uymayan bağlamsız süzgeçler yerine yalnız içerik kategorileri (patlama, enerji, silah, hazine…)
        val categories = remember(kind, style, theme, index) { ComicAssets.propCategories(kind, style, theme) }
        if (categories.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("${tag}_categories"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuChip(stringResource(R.string.shape_filter_all), category == null, onClick = { category = null }, modifier = Modifier.testTag("${tag}_cat_all"))
                categories.forEach { c ->
                    NeuChip(ComicAssets.name(c.names, lang), c.id == category, onClick = { category = c.id }, modifier = Modifier.testTag("${tag}_cat_${c.id}"))
                }
            }
        }
        val list = remember(kind, style, theme, category, index) { ComicAssets.props(kind, style, category, theme) }
        Text(stringResource(R.string.characters_count, list.size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("${tag}_count"))
        if (list.isEmpty()) Text(stringResource(R.string.characters_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        val catNames = remember(kind, lang) {
            val cats = if (kind == ComicAssetKind.EFFECT) ComicAssets.catalog.effectCategories else ComicAssets.catalog.objectCategories
            cats.associate { it.id to ComicAssets.name(it.names, lang) }
        }
        Grid(list, columns) { p, m ->
            val feature = PremiumRules.propFeature(kind, p)
            ComicTile(catNames[p.category].orEmpty(), false, { onPick(p) }, m, "${tag}_item_${p.id}", premium = feature) {
                // Saydam resimler açık ve koyu temada görünsün: hafif gri zemin
                ComicAssetThumb(
                    p.thumb,
                    Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(10.dp)).background(colors.content.copy(alpha = 0.06f)),
                    maxPx = 160,
                )
            }
        }
    }
}

/** v42 "Nesne ekle" penceresi (çizgi roman): hazır nesneler tema paketine ve kategoriye göre. */
@Composable
fun ComicObjectsSheet(onPick: (ComicProp) -> Unit, onDismiss: () -> Unit) {
    ComicSheetFrame(stringResource(R.string.v42_obj_title), stringResource(R.string.v42_obj_hint), "objects_sheet", onDismiss) { columns ->
        ComicPropsBrowser(ComicAssetKind.OBJECT, columns, "obj", onPick)
    }
}
