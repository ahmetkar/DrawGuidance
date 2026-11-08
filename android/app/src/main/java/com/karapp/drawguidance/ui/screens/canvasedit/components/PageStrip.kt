package com.karapp.drawguidance.ui.screens.canvasedit.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.NeuIconButton
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.theme.NeuPillShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic

/**
 * Çizim alanının hemen altındaki sayfa şeridi (çalışma/not tuvali): sayfalar arası geçiş,
 * sayfa ekleme; bir sayfaya uzun basınca çoğalt/sil seçenekleri açılır.
 * v40: şeride uzun basınca üstünde büyütülmüş sayfa kaydırıcısı açılır; sağa/sola sürükledikçe sayfa anında değişir,
 * bırakınca kapanır (sürüklemeden bırakılırsa basılan sayfanın çoğalt/sil seçenekleri açılır).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageStrip(
    pageCount: Int,
    currentPage: Int,
    onGoToPage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** v33 tuval türünün sayfa sınırı: ulaşılınca ekle ve çoğalt pasif. */
    maxPages: Int = Int.MAX_VALUE,
    /** v45 ilk sayfa çizgi roman kapağı: "Kapak" rozetiyle gösterilir, numaralar sonraki sayfada 1'den başlar. */
    cover: Boolean = false,
    /** v45 kapaksız çizgi romanda sayfa menüsündeki "Kapak ekle" (null: seçenek yok). */
    onAddCover: (() -> Unit)? = null,
) {
    val colors = NeuTheme.colors
    val canAdd = pageCount < maxPages
    var menuFor by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    LaunchedEffect(currentPage, pageCount) {
        if (currentPage in 0 until pageCount) listState.animateScrollToItem(currentPage)
    }
    // v40 büyütülmüş sayfa kaydırıcısı
    val scrub = remember { PageScrubState() }
    val goTo by rememberUpdatedState(onGoToPage)
    val current by rememberUpdatedState(currentPage)
    Box(modifier.fillMaxWidth()) {
    if (scrub.active) PageScrubberPopup(scrub.page, pageCount, cover)
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag("page_strip"), shape = NeuPillShape, elevation = 6.dp) {
        Row(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { scrub.root = it }
                .pageScrubGestures(scrub, pageCount, { current }, { goTo(it) }) { at -> scrub.chipAt(at)?.let { menuFor = it } }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                pagePosition(currentPage, pageCount, cover),
                style = MaterialTheme.typography.labelMedium,
                color = colors.contentMuted,
                modifier = Modifier.testTag("page_position"),
            )
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items((0 until pageCount).toList(), key = { it }) { index ->
                    PageChip(
                        index = index,
                        cover = cover && index == 0,
                        number = com.karapp.drawguidance.data.model.PageNumbering.number(index, cover) ?: 0,
                        selected = index == currentPage,
                        onClick = { menuFor = -1; onGoToPage(index) },
                        onLongClick = { menuFor = if (menuFor == index) -1 else index },
                        modifier = Modifier.onGloballyPositioned { scrub.chips[index] = it },
                    )
                }
            }
            if (menuFor >= 0) {
                // v45 kapaksız çizgi romana kapak ekleme (eski sayfaların numaraları değişmez)
                if (onAddCover != null) NeuIconButton(
                    Icons.Filled.AutoStories, stringResource(R.string.v45_cover_add),
                    { onAddCover(); menuFor = -1 }, size = 34.dp,
                    enabled = canAdd, modifier = Modifier.testTag("page_add_cover"),
                )
                NeuIconButton(
                    Icons.Filled.ContentCopy, stringResource(R.string.page_duplicate),
                    { onDuplicatePage(menuFor); menuFor = -1 }, size = 34.dp,
                    enabled = canAdd,
                )
                NeuIconButton(
                    Icons.Filled.Delete, stringResource(R.string.page_delete),
                    { onDeletePage(menuFor); menuFor = -1 }, size = 34.dp,
                    enabled = pageCount > 1, tint = colors.danger,
                )
            }
            NeuIconButton(Icons.Filled.Add, stringResource(R.string.page_add), onAddPage, size = 34.dp, tint = if (canAdd) colors.accent else null, enabled = canAdd)
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageChip(
    index: Int, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier,
    /** v45 kapak sayfası: numara yerine "Kapak" rozeti. */
    cover: Boolean = false,
    /** v45 gösterilen numara (kapaklı çizgi romanda sıradan bir eksik). */
    number: Int = index + 1,
) {
    val colors = NeuTheme.colors
    val label = if (cover) stringResource(R.string.v45_cover) else stringResource(R.string.page_number, number)
    Box(
        modifier = modifier
            .height(32.dp)
            .neumorphic(colors, NeuPillShape, elevation = if (selected) 2.dp else 4.dp, selected = selected)
            .clip(NeuPillShape)
            // v40 uzun basma şeridin kaydırıcısında (sürüklemeden bırakınca bu seçenekler açılır); erişilebilirlikte eylem olarak kalır
            .combinedClickable(role = Role.Tab, onClick = onClick)
            .semantics { onLongClick(label) { onLongClick(); true } }
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = label }
            .testTag(if (cover) "page_chip_cover" else "page_chip_$index"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (cover) label else "$number",
            translate = false,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.accent else colors.contentMuted,
        )
    }
}

/** v45 şeritteki konum yazısı: kapakta "Kapak", kapaklı çizgi romanda numara ve toplam kapaksız ("1 / 3"). */
@Composable
fun pagePosition(page: Int, pageCount: Int, cover: Boolean): String {
    val n = com.karapp.drawguidance.data.model.PageNumbering.number(page, cover)
        ?: return stringResource(R.string.v45_cover)
    return stringResource(R.string.page_of, n, com.karapp.drawguidance.data.model.PageNumbering.count(pageCount, cover).coerceAtLeast(1))
}
