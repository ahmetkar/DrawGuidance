package com.karapp.drawguidance.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BlurLinear
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ViewComfy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Texture
import com.karapp.drawguidance.ui.drawing.BrushCategory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.data.model.BrushType

/** Fırçalara özel çizilmiş ikonlar (24×24). */
object BrushIcons {
    private fun icon(name: String, vararg paths: Pair<String, Float>): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for ((data, alpha) in paths) {
                    addPath(pathData = addPathNodes(data), fill = SolidColor(Color.Black), fillAlpha = alpha)
                }
            }
            .build()

    /** Fare imleci (İnceleme modu). */
    val Cursor: ImageVector by lazy {
        icon(
            "Cursor",
            "M5.5,2.2L5.5,19.6L9.7,15.6L12.6,22L15.6,20.7L12.7,14.4L18.6,14.4Z" to 1f,
        )
    }

    val Pencil: ImageVector by lazy {
        icon(
            "Pencil",
            "M14.1,4.6l5.3,5.3L9.2,20.1L3.5,21.5L4.9,15.8Z" to 1f,
            "M15.5,3.2l1.4,-1.4a1.6,1.6 0,0 1,2.3,0l3,3a1.6,1.6 0,0 1,0,2.3l-1.4,1.4Z" to 0.55f,
        )
    }
    val Marker: ImageVector by lazy {
        icon(
            "Marker",
            "M8.2,13.6L15.6,6.2L19.8,10.4L12.4,17.8Z" to 1f,
            "M16.6,5.2L18.4,3.4a1.5,1.5 0,0 1,2.1,0L22.6,5.5a1.5,1.5 0,0 1,0,2.1L20.8,9.4Z" to 0.55f,
            "M7.2,14.6L11.4,18.8L6.6,20.6L5.4,19.4Z" to 1f,
            "M2,21.2h11v2H2Z" to 1f,
        )
    }
    val Highlighter: ImageVector by lazy {
        icon(
            "Highlighter",
            "M9,2h6v6.5l-1.2,2.2V14h-3.6V10.7L9,8.5Z" to 1f,
            "M10.2,15h3.6l-0.6,3.4h-2.4Z" to 1f,
            "M2,19.6h20V23H2Z" to 0.4f,
        )
    }
    val Pastel: ImageVector by lazy {
        icon(
            "Pastel",
            "M5.3,15.9L14.6,6.6L17.4,9.4L8.1,18.7Z" to 1f,
            "M15.6,5.6L17.6,3.6a1.2,1.2 0,0 1,1.7,0L20.4,4.7a1.2,1.2 0,0 1,0,1.7L18.4,8.4Z" to 0.55f,
            "M4.3,16.9L7.1,19.7L3,21Z" to 1f,
            "M13,21.3a1,1 0,1 1,0.01,0Z M16.2,20.2a0.8,0.8 0,1 1,0.01,0Z M19.4,21.6a1.1,1.1 0,1 1,0.01,0Z M21.2,18.6a0.7,0.7 0,1 1,0.01,0Z" to 0.7f,
        )
    }
    val Eraser: ImageVector by lazy {
        icon(
            "Eraser",
            "M15.3,2.9L21.1,8.7a1.8,1.8 0,0 1,0,2.5L12.6,19.7H7.9L2.9,14.7a1.8,1.8 0,0 1,0,-2.5L12.8,2.9a1.8,1.8 0,0 1,2.5,0Z" to 0.55f,
            "M8.6,8.3L14.7,14.4L10.9,18.2H8.5L4.3,14a0.9,0.9 0,0 1,0,-1.3Z" to 1f,
            "M13,19.7H21V21.6H13Z" to 1f,
        )
    }

    fun of(type: BrushType): ImageVector = when (type) {
        BrushType.PENCIL -> Pencil
        BrushType.INK_PEN -> Icons.Filled.Create
        BrushType.FINELINER -> Icons.Filled.Edit
        BrushType.CALLIGRAPHY -> Icons.Filled.HistoryEdu
        BrushType.CHARCOAL -> Icons.Filled.Gesture
        BrushType.MARKER -> Marker
        BrushType.FLAT_BRUSH -> Icons.Filled.FormatPaint
        BrushType.HIGHLIGHTER -> Highlighter
        BrushType.HARD_ROUND -> Icons.Filled.Brush
        BrushType.PASTEL -> Pastel
        BrushType.SOFT_ROUND -> Icons.Filled.BlurCircular
        BrushType.AIRBRUSH -> Icons.Filled.Air
        BrushType.STAMP_STAR -> Icons.Filled.Star
        BrushType.STAMP_HEART -> Icons.Filled.Favorite
        BrushType.STAMP_LEAF -> Icons.Filled.Eco
        BrushType.PATTERN_DOTS -> Icons.Filled.MoreHoriz
        BrushType.PATTERN_ZIGZAG -> Icons.AutoMirrored.Filled.ShowChart
        BrushType.PATTERN_HATCH -> Icons.Filled.Tag
        BrushType.TEXTURE_SPONGE -> Icons.Filled.BubbleChart
        BrushType.TEXTURE_CANVAS -> Icons.Filled.Texture
        BrushType.TEXTURE_SPLATTER -> Icons.Filled.Grain
        BrushType.HALFTONE_DOTS -> Icons.Filled.BlurOn
        BrushType.HALFTONE_LINES -> Icons.Filled.BlurLinear
        BrushType.NOTE_PENCIL -> Pencil
        BrushType.NOTE_BALLPOINT -> Icons.Filled.Edit
        BrushType.NOTE_FOUNTAIN -> Icons.Filled.HistoryEdu
        BrushType.NOTE_GEL -> Icons.Filled.Create
        BrushType.NOTE_FINELINER -> Icons.Filled.DriveFileRenameOutline
        BrushType.NOTE_FELT -> Marker
        BrushType.NOTE_HIGHLIGHTER -> Highlighter
        BrushType.ERASER -> Eraser
        BrushType.IMAGE -> Icons.Filled.Image
        BrushType.TEXT -> Icons.Filled.TextFields
        BrushType.SHAPE -> Icons.Filled.Category
        BrushType.DIAGRAM -> Icons.Filled.AccountTree
        BrushType.DOT_PEN -> Icons.Filled.MoreVert
        BrushType.BRUSH_3D -> Icons.Filled.Gradient
        BrushType.FORMULA -> Icons.Filled.Functions
        BrushType.INK_TRAIL -> Icons.Filled.WaterDrop
        BrushType.DRIP_TRAIL -> Icons.Filled.Opacity
        BrushType.PAINT_TRAIL -> Icons.Filled.Draw
        BrushType.PATTERN_CARPET -> Icons.Filled.ViewComfy
        BrushType.PATTERN_WALLPAPER -> Icons.Filled.Wallpaper
        BrushType.PATTERN_WOODPAINT -> Icons.Filled.Deck
        BrushType.TEXTURE_SKIN -> Icons.Filled.Face
        BrushType.TEXTURE_FUR -> Icons.Filled.Pets
        BrushType.TEXTURE_PLANT -> Icons.Filled.Spa
        BrushType.TEXTURE_WOOD -> Icons.Filled.Forest
        BrushType.TEXTURE_IRON -> Icons.Filled.Anchor
        BrushType.TEXTURE_METAL -> Icons.Filled.Hardware
    }
}

@Composable
fun brushLabel(type: BrushType): String = stringResource(
    when (type) {
        BrushType.PENCIL -> R.string.brush_pencil
        BrushType.INK_PEN -> R.string.brush_ink_pen
        BrushType.FINELINER -> R.string.brush_fineliner
        BrushType.CALLIGRAPHY -> R.string.brush_calligraphy
        BrushType.CHARCOAL -> R.string.brush_charcoal
        BrushType.MARKER -> R.string.brush_marker
        BrushType.FLAT_BRUSH -> R.string.brush_flat
        BrushType.HIGHLIGHTER -> R.string.brush_highlighter
        BrushType.HARD_ROUND -> R.string.brush_hard_round
        BrushType.PASTEL -> R.string.brush_pastel
        BrushType.SOFT_ROUND -> R.string.brush_soft_round
        BrushType.AIRBRUSH -> R.string.brush_airbrush
        BrushType.STAMP_STAR -> R.string.brush_stamp_star
        BrushType.STAMP_HEART -> R.string.brush_stamp_heart
        BrushType.STAMP_LEAF -> R.string.brush_stamp_leaf
        BrushType.PATTERN_DOTS -> R.string.brush_pattern_dots
        BrushType.PATTERN_ZIGZAG -> R.string.brush_pattern_zigzag
        BrushType.PATTERN_HATCH -> R.string.brush_pattern_hatch
        BrushType.TEXTURE_SPONGE -> R.string.brush_texture_sponge
        BrushType.TEXTURE_CANVAS -> R.string.brush_texture_canvas
        BrushType.TEXTURE_SPLATTER -> R.string.brush_texture_splatter
        BrushType.HALFTONE_DOTS -> R.string.brush_halftone_dots
        BrushType.HALFTONE_LINES -> R.string.brush_halftone_lines
        BrushType.NOTE_PENCIL -> R.string.brush_note_pencil
        BrushType.NOTE_BALLPOINT -> R.string.brush_note_ballpoint
        BrushType.NOTE_FOUNTAIN -> R.string.brush_note_fountain
        BrushType.NOTE_GEL -> R.string.brush_note_gel
        BrushType.NOTE_FINELINER -> R.string.brush_note_fineliner
        BrushType.NOTE_FELT -> R.string.brush_note_felt
        BrushType.NOTE_HIGHLIGHTER -> R.string.brush_note_highlighter
        BrushType.ERASER -> R.string.brush_eraser
        BrushType.IMAGE -> R.string.element_image
        BrushType.TEXT -> R.string.element_text
        BrushType.SHAPE -> R.string.element_shape
        BrushType.DIAGRAM -> R.string.element_diagram
        BrushType.DOT_PEN -> R.string.brush_dot_pen
        BrushType.BRUSH_3D -> R.string.brush_3d
        BrushType.FORMULA -> R.string.element_formula
        BrushType.INK_TRAIL -> R.string.brush_ink_trail
        BrushType.DRIP_TRAIL -> R.string.brush_drip_trail
        BrushType.PAINT_TRAIL -> R.string.brush_paint_trail
        BrushType.PATTERN_CARPET -> R.string.brush_pattern_carpet
        BrushType.PATTERN_WALLPAPER -> R.string.brush_pattern_wallpaper
        BrushType.PATTERN_WOODPAINT -> R.string.brush_pattern_woodpaint
        BrushType.TEXTURE_SKIN -> R.string.brush_texture_skin
        BrushType.TEXTURE_FUR -> R.string.brush_texture_fur
        BrushType.TEXTURE_PLANT -> R.string.brush_texture_plant
        BrushType.TEXTURE_WOOD -> R.string.brush_texture_wood
        BrushType.TEXTURE_IRON -> R.string.brush_texture_iron
        BrushType.TEXTURE_METAL -> R.string.brush_texture_metal
    },
)

@Composable
fun brushCategoryLabel(category: BrushCategory): String = stringResource(
    when (category) {
        BrushCategory.FINE_ART -> R.string.brush_cat_fine_art
        BrushCategory.BROAD -> R.string.brush_cat_broad
        BrushCategory.HARD -> R.string.brush_cat_hard
        BrushCategory.SOFT -> R.string.brush_cat_soft
        BrushCategory.SHAPE -> R.string.brush_cat_shape
        BrushCategory.PATTERN -> R.string.brush_cat_pattern
        BrushCategory.TEXTURE -> R.string.brush_cat_texture
        BrushCategory.TRAIL -> R.string.brush_cat_trail
        BrushCategory.LIVING -> R.string.brush_cat_living
        BrushCategory.MATERIAL -> R.string.brush_cat_material
        BrushCategory.HALFTONE -> R.string.brush_cat_halftone
        BrushCategory.NOTE_WRITING -> R.string.brush_cat_note_writing
        BrushCategory.NOTE_MARKING -> R.string.brush_cat_note_marking
        BrushCategory.EFFECTS, BrushCategory.NOTE_EFFECTS -> R.string.brush_cat_effects
    },
)
