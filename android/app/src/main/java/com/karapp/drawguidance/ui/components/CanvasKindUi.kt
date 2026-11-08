package com.karapp.drawguidance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.CanvasKind
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v33 tuval türünün adı (formda, listede, ana sayfada ve aktarmada). */
@Composable
fun canvasKindLabel(kind: CanvasKind): String = stringResource(
    when (kind) {
        CanvasKind.ART -> R.string.canvas_kind_art
        CanvasKind.NOTES -> R.string.canvas_kind_notes
        CanvasKind.COMIC -> R.string.canvas_kind_comic
        CanvasKind.SIMPLE_ART -> R.string.canvas_kind_simple_art
        CanvasKind.SIMPLE_NOTES -> R.string.canvas_kind_simple_notes
        CanvasKind.KIDS -> R.string.canvas_kind_kids
    },
)

/** v33 tuval türünün kısa açıklaması. */
@Composable
fun canvasKindHint(kind: CanvasKind): String = stringResource(
    when (kind) {
        CanvasKind.ART -> R.string.canvas_kind_art_hint
        CanvasKind.NOTES -> R.string.canvas_kind_notes_hint
        CanvasKind.COMIC -> R.string.canvas_kind_comic_hint
        CanvasKind.SIMPLE_ART -> R.string.canvas_kind_simple_art_hint
        CanvasKind.SIMPLE_NOTES -> R.string.canvas_kind_simple_notes_hint
        CanvasKind.KIDS -> R.string.canvas_kind_kids_hint
    },
)

/** v40 Ana sayfadaki mod kutularının kısa, samimi tek satırı (ayrıntı formda: [canvasKindHint]). */
@Composable
fun homeKindHint(kind: CanvasKind): String = stringResource(
    when (kind) {
        CanvasKind.ART -> R.string.v40_home_kind_art
        CanvasKind.NOTES -> R.string.v40_home_kind_notes
        CanvasKind.COMIC -> R.string.v40_home_kind_comic
        CanvasKind.SIMPLE_ART -> R.string.v40_home_kind_simple_art
        CanvasKind.SIMPLE_NOTES -> R.string.v40_home_kind_simple_notes
        CanvasKind.KIDS -> R.string.v40_home_kind_kids
    },
)

/** v33 listede tuval adının önündeki tür etiketi (resimde yok). */
@Composable
fun canvasKindBadge(kind: CanvasKind): String? = when (kind) {
    CanvasKind.ART -> null
    CanvasKind.NOTES -> stringResource(R.string.canvas_kind_badge_notes)
    CanvasKind.COMIC -> stringResource(R.string.canvas_kind_badge_comic)
    CanvasKind.SIMPLE_ART -> stringResource(R.string.canvas_kind_badge_simple_art)
    CanvasKind.SIMPLE_NOTES -> stringResource(R.string.canvas_kind_badge_simple_notes)
    CanvasKind.KIDS -> stringResource(R.string.canvas_kind_badge_kids)
}

fun canvasKindIcon(kind: CanvasKind): ImageVector = when (kind) {
    CanvasKind.ART -> Icons.Filled.Brush
    CanvasKind.NOTES -> Icons.Filled.EditNote
    CanvasKind.COMIC -> Icons.Filled.AutoStories
    CanvasKind.SIMPLE_ART -> Icons.Filled.Gesture
    CanvasKind.SIMPLE_NOTES -> Icons.Filled.StickyNote2
    CanvasKind.KIDS -> Icons.Filled.ChildCare
}

fun canvasKindTint(kind: CanvasKind): Color = when (kind) {
    CanvasKind.ART -> Color(0xFF6366F1)
    CanvasKind.NOTES -> Color(0xFF0EA5E9)
    CanvasKind.COMIC -> Color(0xFFF43F5E)
    CanvasKind.SIMPLE_ART -> Color(0xFF8B5CF6)
    CanvasKind.SIMPLE_NOTES -> Color(0xFF14B8A6)
    CanvasKind.KIDS -> Color(0xFFF59E0B)
}

/** v33 çizim grupları: basit (tek menü, sınırlı katalog) ve gelişmiş (bütün özellikler, 3B şekil oluşturma dahil). */
enum class DrawingGroup { SIMPLE, ADVANCED }

/** 3B tuvalin seçicideki sırası (tuvallerle aynı listede, 3B düzenleyicide açılır, 3B Şekillerim'e kaydedilir). */
private val Model3dTint = Color(0xFF10B981)

/**
 * v34 yeni çizimin alt modları ana sayfada basit / gelişmiş düğmelerinin altında açılan listede (ayrı pencere yok):
 * basit çizimde basit resim, basit not alma ve çocuklar için çizim; gelişmiş çizimde resim, not alma, çizgi roman ve
 * 3B şekil oluşturma. [group] null ise liste kapanır (kayarak ve solarak).
 */
@Composable
fun DrawingGroupList(
    group: DrawingGroup?,
    onPick: (CanvasKind) -> Unit,
    onNew3d: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NeuTheme.colors
    // Kapanırken son grubun listesi görünmeye devam eder (animasyon bitene kadar)
    val last = remember { arrayOfNulls<DrawingGroup>(1) }
    if (group != null) last[0] = group
    val shown = group ?: last[0]
    androidx.compose.animation.AnimatedVisibility(
        visible = group != null,
        modifier = modifier,
        enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(),
    ) {
        val g = shown ?: return@AnimatedVisibility
        NeuSurface(modifier = Modifier.fillMaxWidth().testTag("new_drawing_list"), elevation = 4.dp, inset = true) {
            androidx.compose.animation.AnimatedContent(
                targetState = g,
                transitionSpec = { androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut() },
                label = "drawing_group",
            ) { current ->
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(if (current == DrawingGroup.SIMPLE) R.string.drawing_group_simple_hint else R.string.drawing_group_advanced_hint),
                        style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    val kinds = if (current == DrawingGroup.SIMPLE) CanvasKind.Simple else CanvasKind.Advanced
                    kinds.forEach { k ->
                        KindRow(canvasKindLabel(k), homeKindHint(k), canvasKindIcon(k), canvasKindTint(k), "new_kind_${k.name}") { onPick(k) }
                    }
                    if (current == DrawingGroup.ADVANCED) KindRow(
                        stringResource(R.string.canvas_kind_3d), stringResource(R.string.v40_home_kind_3d),
                        Icons.Filled.ViewInAr, Model3dTint, "new_kind_3D", onNew3d,
                    )
                }
            }
        }
    }
}

@Composable
private fun KindRow(title: String, hint: String, icon: ImageVector, tint: Color, tag: String, onClick: () -> Unit) {
    val colors = NeuTheme.colors
    NeuSurface(modifier = Modifier.fillMaxWidth().testTag(tag), elevation = 5.dp, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.contentMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = colors.contentMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * v33 3B tuval: tuvaller gibi adı sorulur, sonra 3B düzenleyici açılır. Kaydedilince 3B Şekillerim'de görünür
 * (Tuvallerim'de görünmez).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun New3dSheet(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = NeuTheme.colors
    val defaultName = stringResource(R.string.model3d_default_name)
    var name by rememberSaveable { mutableStateOf("") }
    NeuSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 24.dp).testTag("new_3d_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.new_3d_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.new_3d_hint), style = MaterialTheme.typography.bodySmall, color = colors.contentMuted)
            NeuTextField(
                value = name, onValueChange = { name = it.take(80) },
                label = stringResource(R.string.canvas_name_label), placeholder = defaultName,
                modifier = Modifier.fillMaxWidth(),
            )
            NeuButton(
                stringResource(R.string.create_button), { onCreate(name.trim().ifEmpty { defaultName }) },
                Modifier.fillMaxWidth().testTag("new_3d_create"), primary = true, icon = Icons.Filled.ViewInAr,
            )
        }
    }
}
