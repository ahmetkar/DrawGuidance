package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.inbox.BasePacks
import com.karapp.drawguidance.data.inbox.PackStore
import com.karapp.drawguidance.ui.components.NeuChip
import com.karapp.drawguidance.ui.components.NeuSheet
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/**
 * v43 "Paket içerikleri": sunucudan inmiş paketlerin, kendi hazır penceresi olmayan resimleri (çizgi romanda diyalog
 * kutuları ve şekiller; not almada şekiller ve diyagramlar; resimde şekiller). Pencere ve paket seçilir, dokunulan
 * resim tuvale eklenir. Çizgi romanın nesne, efekt, mekân ve karakterleri kendi pencerelerindedir.
 */
object PackWindows {
    /** Bu modda "Paket içerikleri"nde gösterilen pencereler. */
    fun forMode(mode: String): List<String> = when (mode.uppercase()) {
        "COMIC" -> listOf("balloons", "shapes")
        "NOTES" -> listOf("shapes", "diagrams")
        "ART" -> listOf("shapes")
        else -> emptyList()
    }

    /** Pencerede gösterilecek (paket, öğe) çiftleri; taban paketlerin uygulamada zaten olan öğeleri atlanır. */
    fun items(packs: List<PackStore.Pack>, mode: String, window: String): List<Pair<PackStore.Pack, PackStore.Item>> =
        packs.filter { it.mode.equals(mode, ignoreCase = true) }.flatMap { p ->
            val base = BasePacks.isBase(p.id)
            p.items(window).filter { !base || !builtin(window, it.id) }.map { p to it }
        }

    private val shapeIds: Set<String> by lazy {
        com.karapp.drawguidance.data.model.CanvasKind.entries.flatMap { k ->
            runCatching { com.karapp.drawguidance.ui.drawing.ShapeRegistry.categories(k).flatMap { it.items } }.getOrDefault(emptyList())
        }.map(BasePacks::itemId).toSet()
    }

    /** Uygulamanın kendi penceresinde zaten olan öğe mi (taban paket kimlikleri). */
    fun builtin(window: String, itemId: String): Boolean = when (window) {
        "balloons" -> com.karapp.drawguidance.data.model.BalloonStyle.entries.any { "balloon-" + it.name.lowercase() == itemId }
        "diagrams" -> com.karapp.drawguidance.data.model.DiagramKind.entries.any { "diagram-" + it.name.lowercase() == itemId }
        "shapes" -> itemId in shapeIds
        else -> false
    }

    fun hasAny(packs: List<PackStore.Pack>, mode: String): Boolean = forMode(mode).any { items(packs, mode, it).isNotEmpty() }

    fun titleRes(window: String): Int = when (window) {
        "balloons" -> R.string.v43_pack_window_balloons
        "diagrams" -> R.string.v43_pack_window_diagrams
        else -> R.string.v43_pack_window_shapes
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PackItemsSheet(
    mode: String,
    store: PackStore,
    onPick: (PackStore.Pack, PackStore.Item) -> Unit,
    onDismiss: () -> Unit,
) {
    val packs by store.packs.collectAsState()
    val windows = PackWindows.forMode(mode).filter { PackWindows.items(packs, mode, it).isNotEmpty() }
    var window by rememberSaveable { mutableStateOf(windows.firstOrNull() ?: "shapes") }
    val colors = NeuTheme.colors
    NeuSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).testTag("pack_items_sheet"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.v43_add_packs), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                windows.forEach { w ->
                    NeuChip(stringResource(PackWindows.titleRes(w)), w == window, onClick = { window = w }, modifier = Modifier.testTag("pack_window_$w"))
                }
            }
            val list = PackWindows.items(packs, mode, window)
            if (list.isEmpty()) {
                Text(stringResource(R.string.v43_pack_empty), style = MaterialTheme.typography.bodyMedium, color = colors.contentMuted)
            } else {
                LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxWidth().heightIn(max = 460.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { (p, i) -> p.id + "/" + i.id }) { (p, i) ->
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPick(p, i) }.padding(4.dp).testTag("pack_item_${p.id}_${i.id}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                coil.compose.AsyncImage(model = p.file(i), contentDescription = i.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                            }
                            Text(i.title, translate = false, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
