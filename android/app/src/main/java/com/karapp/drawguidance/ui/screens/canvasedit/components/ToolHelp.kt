package com.karapp.drawguidance.ui.screens.canvasedit.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import com.karapp.drawguidance.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.LocalToolHelp
import com.karapp.drawguidance.ui.components.NeuSurface
import com.karapp.drawguidance.ui.components.ToolHelpState
import com.karapp.drawguidance.ui.components.ToolHint
import com.karapp.drawguidance.ui.theme.NeuTheme
import kotlinx.coroutines.delay

/**
 * v33 çizim alanının üstündeki menülerin düğme açıklamaları: düğmenin adı (string kaynağı) → açıklaması. Aynı adı
 * taşıyan düğmeler (ör. "Kırp", "Sil") tek açıklamayı paylaşır; açıklamalar o adın bütün kullanımlarına uyar.
 */
object ToolDescriptions {
    /** Ad kaynağı → açıklama kaynağı. */
    val pairs: List<Pair<Int, Int>> = listOf(
        // Çiz satırı
        R.string.undo to R.string.help_undo,
        R.string.redo to R.string.help_redo,
        R.string.brush_select to R.string.help_brush_select,
        R.string.brush_eraser to R.string.help_brush_eraser,
        R.string.add_pen to R.string.help_add_pen,
        R.string.mode_pen to R.string.help_add_pen,
        R.string.fill_color to R.string.help_fill_color,
        R.string.add_background to R.string.help_add_background,
        R.string.tab_elements to R.string.help_tab_elements,
        // v33 basit çizim menüsü
        R.string.mode_paint to R.string.help_mode_paint,
        R.string.simple_paint_background to R.string.help_simple_paint_background,
        // v38 çocuk modunda çizim görevi
        R.string.kids_challenge to R.string.help_kids_challenge,
        // v40 cetvel ve şekil kalıbı (basit menüde ayrı düğmeler)
        R.string.v40_ruler to R.string.v40_help_ruler,
        R.string.v40_template to R.string.v40_help_template,
        // v43 damlalık, alanla seçim, nesne araçları
        R.string.v43_eyedropper to R.string.v43_help_eyedropper,
        R.string.v43_select_rect to R.string.v43_help_select_rect,
        R.string.v44_select_rect_simple to R.string.v43_help_select_rect,
        R.string.v43_select_lasso to R.string.v43_help_select_lasso,
        R.string.v43_harmonize to R.string.v43_help_harmonize,
        R.string.v43_place to R.string.v43_help_place,
        R.string.v43_add_packs to R.string.v43_help_add_packs,
        // v45 not alma panelleri ve çizgi roman kapağı (sayfa şeridi)
        R.string.v45_panels_add to R.string.v45_help_panels_add,
        R.string.v45_cover to R.string.v45_help_cover,
        R.string.v45_cover_add to R.string.v45_help_cover_add,
        R.string.brush_settings_toggle to R.string.help_brush_settings_toggle,
        R.string.tab_layers to R.string.help_tab_layers,
        // Seç ve Taşı
        R.string.duplicate_selection to R.string.help_duplicate_selection,
        R.string.hide_elements to R.string.help_hide_elements,
        R.string.delete_selection to R.string.help_delete_selection,
        R.string.move_nudge_left to R.string.help_move_nudge_left,
        R.string.move_nudge_up to R.string.help_move_nudge_up,
        R.string.move_nudge_down to R.string.help_move_nudge_down,
        R.string.move_nudge_right to R.string.help_move_nudge_right,
        R.string.move_center to R.string.help_move_center,
        R.string.move_position_title to R.string.help_move_position_title,
        R.string.move_step_name to R.string.help_move_step_name,
        R.string.canvas_size_title to R.string.help_canvas_size_title,
        R.string.pen_mode_toggle to R.string.help_pen_mode_toggle,
        // Ekle
        R.string.add_shape to R.string.help_add_shape,
        R.string.add_formula to R.string.help_add_formula,
        R.string.add_text to R.string.help_add_text,
        R.string.add_diagram to R.string.help_add_diagram,
        R.string.add_balloon to R.string.help_add_balloon,
        R.string.add_sfx to R.string.help_add_sfx,
        R.string.add_comic_lines to R.string.help_add_comic_lines,
        R.string.add_effects to R.string.help_add_effects,
        R.string.add_characters to R.string.help_add_characters,
        // v41 çizgi roman: hazır mekân arka planı (panele ya da bütün sayfaya)
        R.string.v41_add_location to R.string.v41_help_add_location,
        // v42 hazır nesneler
        R.string.v42_add_objects to R.string.v42_help_add_objects,
        R.string.add_templates to R.string.help_add_templates,
        R.string.add_guide to R.string.help_add_guide,
        R.string.add_model3d to R.string.help_add_model3d,
        R.string.add_perspective to R.string.help_add_perspective,
        R.string.add_image to R.string.help_add_image,
        // Düzenle
        R.string.vector_edit_open to R.string.help_vector_edit_open,
        R.string.mode_rotate to R.string.help_mode_rotate,
        R.string.mode_scale to R.string.help_mode_scale,
        R.string.mode_crop to R.string.help_crop,
        R.string.image_crop to R.string.help_crop,
        R.string.crop_apply to R.string.help_crop,
        // Şekil düzenleme
        R.string.tool_nodes to R.string.help_tool_nodes,
        R.string.tool_node_move to R.string.help_tool_node_move,
        R.string.tool_add_node to R.string.help_tool_add_node,
        R.string.tool_connect to R.string.help_tool_connect,
        R.string.tool_lines to R.string.help_tool_lines,
        R.string.tool_region_fill to R.string.help_tool_region_fill,
        R.string.edit_nodes to R.string.help_edit_nodes,
        R.string.simple_shape_edit to R.string.help_simple_shape_edit,
        R.string.vector_simplify to R.string.help_vector_simplify,
        R.string.edit_lines to R.string.help_edit_lines,
        R.string.vectorize_direct_auto to R.string.help_vectorize_direct_auto,
        R.string.vectorize_direct_edges to R.string.help_vectorize_direct_edges,
        R.string.vectorize_direct_lines to R.string.help_vectorize_direct_lines,
        R.string.tool_graph to R.string.help_tool_graph,
        R.string.part_style to R.string.help_part_style,
        R.string.part_style_title to R.string.help_part_style,
        R.string.vector_cut to R.string.help_vector_cut,
        R.string.vector_delete to R.string.help_delete_selection,
        R.string.region_fill_clear to R.string.help_region_fill_clear,
        R.string.region_fill_custom to R.string.help_region_fill_custom,
        R.string.edge_turn_less to R.string.help_edge_turn_less,
        R.string.edge_turn_more to R.string.help_edge_turn_more,
        R.string.edge_horizontal to R.string.help_edge_horizontal,
        R.string.edge_vertical to R.string.help_edge_vertical,
        R.string.edge_straighten to R.string.help_edge_straighten,
        R.string.graph_pattern_less to R.string.help_graph_pattern_less,
        R.string.graph_pattern_more to R.string.help_graph_pattern_more,
        R.string.graph_points_less to R.string.help_graph_points_less,
        R.string.graph_points_more to R.string.help_graph_points_more,
        R.string.graph_numbers to R.string.help_graph_numbers,
        R.string.graph_grid to R.string.help_graph_grid,
        R.string.graph_fill_under to R.string.help_graph_fill_under,
        R.string.graph_key_points to R.string.help_graph_key_points,
        // Resim düzenleme
        R.string.image_vectorize to R.string.help_image_vectorize,
        R.string.image_filter to R.string.help_image_filter,
        R.string.bg_remove to R.string.help_bg_remove,
        R.string.image_effects to R.string.help_image_effects,
        // Diyagram düzenleme
        R.string.diagram_theme to R.string.help_diagram_theme,
        R.string.diagram_add_step to R.string.help_diagram_add_step,
        R.string.diagram_add_arrow to R.string.help_diagram_add_arrow,
        R.string.diagram_link_cancel to R.string.help_diagram_link_cancel,
        R.string.diagram_edit to R.string.help_diagram_edit,
        // Genişlet
        R.string.expand_left to R.string.help_expand_left,
        R.string.expand_top to R.string.help_expand_top,
        R.string.expand_bottom to R.string.help_expand_bottom,
        R.string.expand_right to R.string.help_expand_right,
        R.string.zoom_fit to R.string.help_zoom_fit,
        // Mod seçici
        R.string.mode_draw to R.string.help_mode_draw,
        R.string.mode_select to R.string.help_mode_select,
        R.string.mode_add to R.string.help_mode_add,
        R.string.mode_edit_pick to R.string.help_mode_edit_pick,
        R.string.mode_move to R.string.help_mode_move,
        R.string.mode_expand to R.string.help_mode_expand,
        // Mod çubuğu
        R.string.move_done to R.string.help_move_done,
        R.string.clear_selection to R.string.help_clear_selection,
        R.string.crop_reset to R.string.help_crop_reset,
        R.string.change_shape to R.string.help_change_shape,
        R.string.text_settings to R.string.help_text_settings,
        R.string.edit_text to R.string.help_edit_text,
        R.string.shape_edit_open to R.string.help_shape_edit_open,
        R.string.balloon_edit_title to R.string.help_balloon_edit_title,
        R.string.lines_density_action to R.string.help_lines_density_action,
        R.string.image_edit_open to R.string.help_image_edit_open,
        R.string.formula_edit to R.string.help_formula_edit,
        R.string.ink_color to R.string.help_ink_color,
        R.string.ink_thinner to R.string.help_ink_thinner,
        R.string.ink_thicker to R.string.help_ink_thicker,
        R.string.ink_smooth to R.string.help_ink_smooth,
        R.string.ink_correct to R.string.help_ink_correct,
        R.string.ink_correct_multi to R.string.help_ink_correct_multi,
        R.string.formula_change to R.string.help_formula_change,
        R.string.pen_undo to R.string.help_pen_undo,
        R.string.pen_finish to R.string.help_pen_finish,
        R.string.guide_remove to R.string.help_guide_remove,
        // Sayfa şeridi ve başlık
        R.string.page_add to R.string.help_page_add,
        R.string.page_duplicate to R.string.help_page_duplicate,
        R.string.page_delete to R.string.help_page_delete,
        R.string.save_now to R.string.help_save_now,
        R.string.save_now_dirty to R.string.help_save_now,
        R.string.editor_more to R.string.help_editor_more,
        R.string.save_as_image to R.string.help_save_as_image,
        R.string.back to R.string.help_back,
        // v45 çocuk menüsünün kısa adları (aynı adı taşıyan düğmeler varsa onların açıklaması geçerli kalır)
        R.string.v45_kids_undo to R.string.help_undo,
        R.string.v45_kids_redo to R.string.help_redo,
        R.string.v45_kids_pen to R.string.v45_help_kids_pen,
        R.string.v45_kids_eraser to R.string.help_brush_eraser,
        R.string.v45_kids_paint_brush to R.string.v45_help_kids_paint_brush,
        R.string.v45_kids_bucket to R.string.v45_help_kids_bucket,
        R.string.v45_kids_parts to R.string.v45_help_kids_parts,
        R.string.v45_kids_colors to R.string.v45_help_kids_colors,
        R.string.v45_kids_shapes to R.string.v45_help_kids_shapes,
        R.string.v45_kids_challenge to R.string.help_kids_challenge,
        R.string.v45_kids_background to R.string.v45_help_kids_background,
        R.string.v45_kids_image to R.string.help_add_image,
        R.string.v45_kids_text to R.string.help_add_text,
        R.string.v45_kids_model3d to R.string.help_add_model3d,
        R.string.v45_kids_select to R.string.help_mode_select,
        R.string.v45_kids_eyedropper to R.string.v43_help_eyedropper,
        R.string.v45_kids_ruler to R.string.v40_help_ruler,
        R.string.v45_kids_template to R.string.v40_help_template,
        R.string.v45_kids_expand to R.string.help_mode_expand,
        R.string.v45_kids_size_thin to R.string.v45_help_kids_brush_size,
        R.string.v45_kids_size_medium to R.string.v45_help_kids_brush_size,
        R.string.v45_kids_size_thick to R.string.v45_help_kids_brush_size,
    )

    /** Kaynak olmayan sabit adlar (Döndür ve Ölçekle mod çubuğundaki adım düğmeleri). */
    private val literals: List<Pair<String, Int>> = listOf(
        "−15°" to R.string.help_rotate_minus,
        "+15°" to R.string.help_rotate_plus,
        "+90°" to R.string.help_rotate_90,
        "−10%" to R.string.help_scale_minus,
        "+10%" to R.string.help_scale_plus,
    )

    /** Görünen ad → açıklama tablosu (dile göre). */
    fun table(context: Context): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        literals.forEach { (label, help) -> map[label] = context.getString(help) }
        pairs.forEach { (label, help) -> map.putIfAbsent(context.getString(label), context.getString(help)) }
        return map
    }
}

/** Ekranın araç yardımı durumu (açıklama tablosu bağlı). */
@Composable
fun rememberToolHelp(): ToolHelpState {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val table = remember(configuration) { ToolDescriptions.table(context) }
    val state = remember { ToolHelpState() }
    state.describer = { table[it] }
    state.fallback = { context.getString(R.string.tool_help_fallback, it) }
    return state
}

/** [content] içindeki düğmelerde araç yardımı açık (yalnız çizim alanının üstündeki menülerin çevresinde). */
@Composable
fun ToolHelpScope(state: ToolHelpState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalToolHelp provides state, content = content)
}

/**
 * Çizim alanının köşesindeki açıklama kutusu: basılı tutulan düğmenin adı ve açıklaması (~6 sn). Kutuya dokununca
 * kapanır. v45: [showNames] false ise (gelişmiş modlar) dokunulan düğmenin adı burada gösterilmez; basit modlarda
 * dokununca ad (~1,5 sn) görünmeye devam eder.
 */
@Composable
fun ToolHelpBox(state: ToolHelpState, modifier: Modifier = Modifier, showNames: Boolean = true) {
    // v45 gelişmiş modlarda köşede yalnız açıklama (basılı tutunca) görünür; ad düğmenin içinde/altında yazar
    val hint = state.hint?.takeIf { showNames || it.description != null }
    LaunchedEffect(hint?.id) {
        val h = hint ?: return@LaunchedEffect
        delay(if (h.description == null) NameMillis else DescriptionMillis)
        if (state.hint?.id == h.id) state.dismiss()
    }
    // Kapanırken son içerik görünmeye devam eder
    val last = remember { arrayOfNulls<ToolHint>(1) }
    if (hint != null) last[0] = hint
    val shown = hint ?: last[0]
    val colors = NeuTheme.colors
    val boxDescription = stringResource(R.string.tool_help_box_cd)
    val holdHint = stringResource(R.string.tool_help_hint)
    AnimatedVisibility(visible = hint != null && shown != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (shown == null) return@AnimatedVisibility
        // Ekran okuyucu için tek açıklama (ad ve açıklama); metinler ayrı düğüm olmaz (düğme adlarıyla karışmasın)
        val spoken = listOfNotNull(boxDescription, shown.name, shown.description).joinToString(": ")
        NeuSurface(
            modifier = Modifier
                .widthIn(max = if (shown.description == null) 220.dp else 280.dp)
                .clearAndSetSemantics {
                    testTag = "tool_help_box"
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                    if (shown.description != null) onClick { state.dismiss(); true }
                },
            elevation = 8.dp,
        ) {
            // Yalnız ad gösterilirken kutu dokunuşları tutmaz (altındaki tuvale geçer); açıklama dokununca kapanır
            Column(
                (if (shown.description != null) Modifier.clickable { state.dismiss() } else Modifier).padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(shown.name, style = MaterialTheme.typography.titleSmall, color = colors.accent)
                if (shown.description != null) {
                    Text(shown.description, style = MaterialTheme.typography.bodySmall, color = colors.content, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text(holdHint, style = MaterialTheme.typography.labelSmall, color = colors.contentMuted)
                }
            }
        }
    }
}

private const val NameMillis = 1500L
private const val DescriptionMillis = 6000L
