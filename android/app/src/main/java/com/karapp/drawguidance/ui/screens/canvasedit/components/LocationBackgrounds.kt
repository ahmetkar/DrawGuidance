package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.billing.PremiumRules
import com.karapp.drawguidance.data.comicassets.ComicAssets
import com.karapp.drawguidance.data.comicassets.ComicBackground
import com.karapp.drawguidance.data.comicassets.ComicStyle
import com.karapp.drawguidance.i18n.I18n
import com.karapp.drawguidance.ui.components.NeuButton
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.screens.canvasedit.editor.ViewportState
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v41 "Mekân arka planı ekle" penceresi (çizgi roman): hazır arka planlar Manga ve Çizgi Roman sekmelerinde, alt
 * kategori süzgeciyle. Arka plana dokununca yerleştirilir: sayfada şablon varsa bir panele bırakılır (o panele sığar)
 * ya da bütün sayfayı kaplar (bkz. [LocationPlaceOverlay]). Şehir, Sokak ve Doğa/Park dışındakiler Premium; önizleme açık.
 */
@Composable
fun LocationBackgroundsSheet(onPick: (ComicBackground) -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    var styleId by rememberSaveable { mutableStateOf(ComicStyle.COMIC.id) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    // v42 tema paketi (paket ↔ mekân bağlantıları veritabanında)
    var theme by rememberSaveable { mutableStateOf<String?>(null) }
    val index by com.karapp.drawguidance.data.comicassets.ComicThemeIndex.flow.collectAsState()
    val style = ComicStyle.entries.firstOrNull { it.id == styleId } ?: ComicStyle.COMIC
    val lang = I18n.language
    val premium = com.karapp.drawguidance.ui.premium.LocalPremium.current
    ComicSheetFrame(stringResource(R.string.v41_loc_title), stringResource(R.string.v41_loc_hint), "locations_sheet", onDismiss) { columns ->
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("loc_styles"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComicStyle.entries.forEach { s ->
                NeuChip(styleLabel(s), s == style, onClick = { styleId = s.id; category = null; theme = null }, modifier = Modifier.testTag("loc_style_${s.id}"))
            }
        }
        ThemePackChips(com.karapp.drawguidance.data.comicassets.ComicAssetKind.BACKGROUND, style, theme, "loc_theme") { theme = it; category = null }
        val categories = remember(style, theme, index) { ComicAssets.bgCategories(style, theme) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("loc_categories"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuChip(stringResource(R.string.shape_filter_all), category == null, onClick = { category = null }, modifier = Modifier.testTag("loc_cat_all"))
            categories.forEach { c ->
                NeuChip(ComicAssets.name(c.names, lang), c.id == category, onClick = { category = c.id }, modifier = Modifier.testTag("loc_cat_${c.id}"))
            }
        }
        val list = remember(style, category, theme, index) { ComicAssets.backgrounds(style, category, theme) }
        if (list.isEmpty()) Text(stringResource(R.string.characters_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
        Column(Modifier.testTag("loc_list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Grid(list, (columns - 1).coerceAtLeast(2)) { b, m ->
                val feature = PremiumRules.backgroundFeature(b)
                ComicTile(ComicAssets.name(b.names, lang), false, { if (premium.request(feature)) onPick(b) }, m, "loc_${b.id}", badge = feature) {
                    ComicAssetThumb(b.thumb, Modifier.fillMaxWidth().height(84.dp), maxPx = 256, crop = true)
                }
            }
        }
    }
}

/**
 * v41 seçilen mekân arka planını yerleştirme katmanı: sayfadaki şablonun panelleri işaretlenir; parmak basılıyken
 * altındaki panel vurgulanır ve arka planın küçük resmi orada önizlenir. Parmak bir panelde kalkınca arka plan o
 * panele sığar; panellerin dışında kalkarsa ya da "Bütün sayfa"ya basılırsa sayfayı kaplar. Üstte ipucu ve Vazgeç.
 */
@Composable
fun LocationPlaceOverlay(
    background: ComicBackground,
    panels: Map<Int, Rect>,
    viewport: ViewportState,
    onPlace: (leaf: Int?) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = NeuTheme.colors
    var hover by remember { mutableStateOf<Offset?>(null) }
    val place by rememberUpdatedState(onPlace)
    val currentPanels by rememberUpdatedState(panels)
    val thumb = com.karapp.drawguidance.ui.components.rememberKeyedThumb(
        background.thumb to 256, com.karapp.drawguidance.data.comicassets.ComicThumbs::cached, dispatcher = kotlinx.coroutines.Dispatchers.IO,
    ) { com.karapp.drawguidance.data.comicassets.ComicThumbs.load(it) }
    fun leafAt(p: Offset): Int? = currentPanels.entries.firstOrNull { it.value.contains(p) }?.key
    Box(Modifier.fillMaxSize().testTag("loc_place_overlay")) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var last = viewport.toCanvas(down.position)
                    hover = last
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = event.changes.firstOrNull() ?: break
                        c.consume()
                        if (!c.pressed) break
                        last = viewport.toCanvas(c.position)
                        hover = last
                    }
                    hover = null
                    place(leafAt(last))
                }
            },
        ) {
            val hovered = hover?.let { leafAt(it) }
            currentPanels.forEach { (leaf, r) ->
                val tl = viewport.toScreen(r.topLeft)
                val br = viewport.toScreen(r.bottomRight)
                val size = Size(br.x - tl.x, br.y - tl.y)
                if (leaf == hovered) {
                    thumb?.let {
                        // Panelin oranında ortadan kırpılmış önizleme (eklenince de böyle kırpılır)
                        val crop = com.karapp.drawguidance.ui.screens.canvasedit.state.Backdrop.cropRect(it.width, it.height, size.width / size.height.coerceAtLeast(1f))
                        drawImage(
                            it, srcOffset = IntOffset(crop.left, crop.top), srcSize = IntSize(crop.width, crop.height),
                            dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()), dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                            alpha = 0.85f,
                        )
                    }
                    drawRect(colors.accent.copy(alpha = 0.18f), tl, size)
                }
                drawRect(
                    colors.accent, tl, size,
                    style = Stroke(width = (if (leaf == hovered) 4 else 2).dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))),
                )
            }
        }
        NeuSurface(Modifier.align(Alignment.TopCenter).padding(10.dp).widthIn(max = 520.dp).testTag("loc_place_bar"), elevation = 6.dp) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ComicAssets.name(background.names, I18n.language), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.v41_loc_place_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuButton(
                        stringResource(R.string.v41_loc_whole_page), { onPlace(null) }, modifier = Modifier.testTag("loc_whole_page"),
                        primary = true, icon = Icons.Filled.Fullscreen,
                    )
                    NeuButton(stringResource(R.string.cancel_button), onCancel, modifier = Modifier.testTag("loc_cancel"), icon = Icons.Filled.Close)
                }
            }
        }
    }
}
