package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.theme.NeuFieldShape
import com.karapp.drawguidance.ui.theme.NeuTheme
import com.karapp.drawguidance.ui.theme.neumorphic

/** Uygulamanın ana bölümleri; alt menüden geçilir. v30: Ana sayfa en solda. */
enum class MainTab { HOME, CANVASES, IMAGES, MODELS, SETTINGS }

/**
 * Neumorphic alt menü: kabarık bir kart içinde Tuvallerim / Resimlerim / Ayarlar.
 * Seçili bölüm zemine gömülü ve vurgu renginde görünür.
 */
@Composable
fun MainBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NeuSurface(
        modifier = modifier
            .testTag("main_bottom_bar")
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp, top = 6.dp),
        elevation = 10.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(6.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BottomItem(stringResource(R.string.tab_home), Icons.Filled.Home, selected == MainTab.HOME, Modifier.weight(1f).testTag("tab_home")) {
                onSelect(MainTab.HOME)
            }
            BottomItem(stringResource(R.string.drawer_my_canvases), Icons.Filled.Palette, selected == MainTab.CANVASES, Modifier.weight(1f)) {
                onSelect(MainTab.CANVASES)
            }
            // v44 çocuk kilidinde yok (yalnız Ana sayfa, Tuvallerim ve Ayarlar)
            if (!com.karapp.drawguidance.ui.kids.LocalKidsLocked.current) BottomItem(stringResource(R.string.drawer_my_images), Icons.Filled.Bookmark, selected == MainTab.IMAGES, Modifier.weight(1f)) {
                onSelect(MainTab.IMAGES)
            }
            // v44 çocuk kilidinde yok (yalnız Ana sayfa, Tuvallerim ve Ayarlar)
            if (!com.karapp.drawguidance.ui.kids.LocalKidsLocked.current) BottomItem(stringResource(R.string.drawer_models3d), Icons.Filled.ViewInAr, selected == MainTab.MODELS, Modifier.weight(1f)) {
                onSelect(MainTab.MODELS)
            }
            BottomItem(stringResource(R.string.drawer_settings), Icons.Filled.Settings, selected == MainTab.SETTINGS, Modifier.weight(1f)) {
                onSelect(MainTab.SETTINGS)
            }
        }
    }
}

/** Yatay ekran veya tablet: alt menü yerine ekranın solunda dikey menü kullanılır. */
@Composable
fun isWideLayout(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp >= 600 || config.screenWidthDp > config.screenHeightDp
}

/** Geniş ekranlarda alt menünün yerini alan neumorphic dikey menü (navigation rail). */
@Composable
fun MainNavRail(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NeuSurface(
        modifier = modifier
            .testTag("main_nav_rail")
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp)
            .width(92.dp),
        elevation = 10.dp,
    ) {
        Column(
            Modifier.fillMaxHeight().padding(8.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BottomItem(stringResource(R.string.tab_home), Icons.Filled.Home, selected == MainTab.HOME, Modifier.fillMaxWidth().testTag("rail_home")) {
                onSelect(MainTab.HOME)
            }
            BottomItem(stringResource(R.string.drawer_my_canvases), Icons.Filled.Palette, selected == MainTab.CANVASES, Modifier.fillMaxWidth()) {
                onSelect(MainTab.CANVASES)
            }
            // v44 çocuk kilidinde yok (yalnız Ana sayfa, Tuvallerim ve Ayarlar)
            if (!com.karapp.drawguidance.ui.kids.LocalKidsLocked.current) BottomItem(stringResource(R.string.drawer_my_images), Icons.Filled.Bookmark, selected == MainTab.IMAGES, Modifier.fillMaxWidth()) {
                onSelect(MainTab.IMAGES)
            }
            // v44 çocuk kilidinde yok (yalnız Ana sayfa, Tuvallerim ve Ayarlar)
            if (!com.karapp.drawguidance.ui.kids.LocalKidsLocked.current) BottomItem(stringResource(R.string.drawer_models3d), Icons.Filled.ViewInAr, selected == MainTab.MODELS, Modifier.fillMaxWidth()) {
                onSelect(MainTab.MODELS)
            }
            BottomItem(stringResource(R.string.drawer_settings), Icons.Filled.Settings, selected == MainTab.SETTINGS, Modifier.fillMaxWidth()) {
                onSelect(MainTab.SETTINGS)
            }
        }
    }
}

/**
 * Ana bölümlerin çerçevesi: dar ekranda içerik alt menüyle, geniş ekranda sol dikey menüyle gösterilir.
 * [content] ekranın Scaffold'una verilecek alt menüyü parametre olarak alır.
 */
@Composable
fun MainSectionFrame(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    content: @Composable (bottomBar: @Composable () -> Unit) -> Unit,
) {
    if (isWideLayout()) {
        Row(Modifier.fillMaxSize().background(NeuTheme.colors.background)) {
            MainNavRail(selected, onSelect)
            Box(Modifier.weight(1f).fillMaxHeight()) { content {} }
        }
    } else {
        content { MainBottomBar(selected = selected, onSelect = onSelect) }
    }
}

@Composable
private fun BottomItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    Column(
        modifier = modifier
            .height(58.dp)
            .then(if (selected) Modifier.neumorphic(colors, NeuFieldShape, elevation = 3.dp, inset = true) else Modifier)
            .clip(NeuFieldShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (selected) colors.accent else colors.contentMuted, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.contentMuted,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
