package com.karapp.drawguidance.data.model

/**
 * v56 özellik bayrakları ve mod kuralı (saf; planın 1. bölümü ve 2D eklemeleri). Bir özellik [off] kümesine eklenerek
 * yayından önce bütün modlarda gizlenebilir. Not almada yalnız yayın düzeltmeleri, sayfalar arası kes-yapıştır ve
 * dışa aktarma (kaydetme seçeneği) vardır; çocuk modunda yalnız simetri.
 */
object V56Flags {
    enum class Feature {
        SELECTION_MASK, LASSO_FILL, LAYER_BLEND, ALPHA_LOCK_CLIP, SELECTION_OPS, GRAIN, COLOR_ADJUST, TRANSFORM_PANEL,
        REFLECTION_LIQUIFY, PRESETS, SYMMETRY, QUICK_BRUSH, SKETCH_3D, ADD_3D_SHAPE, LIVE_FIX, LIVE_APPROVAL, CROSS_PAGE_PASTE,
        // 2D (2026-11-20)
        EXPORT_SVG_PSD_DXF, LAYER_HSL_BALANCE, LAYER_DUPLICATE_MERGE, QUICK_BRUSH_COLOR, EYEDROPPER_LOUPE,
        // 2E gelişmiş çizim araçları (yalnız Resim modu)
        ADV_SELECTION, ADV_TRANSFORM, ADV_ALIGN, ADV_PATHFINDER, ADV_APPEARANCE, ADV_NODES, ADV_REPEAT, ADV_EFFECTS,
    }

    private val advanced = setOf(
        Feature.ADV_SELECTION, Feature.ADV_TRANSFORM, Feature.ADV_ALIGN, Feature.ADV_PATHFINDER, Feature.ADV_APPEARANCE,
        Feature.ADV_NODES, Feature.ADV_REPEAT, Feature.ADV_EFFECTS,
    )

    /** Yayından önce kapatılan özellikler (sorunlu bir özellik buradan gizlenir). */
    val off: Set<Feature> = emptySet()

    private val drawing = setOf(
        Feature.SELECTION_MASK, Feature.LASSO_FILL, Feature.LAYER_BLEND, Feature.ALPHA_LOCK_CLIP, Feature.SELECTION_OPS,
        Feature.GRAIN, Feature.COLOR_ADJUST, Feature.TRANSFORM_PANEL, Feature.REFLECTION_LIQUIFY, Feature.PRESETS,
        Feature.SYMMETRY, Feature.ADD_3D_SHAPE, Feature.LIVE_FIX, Feature.LIVE_APPROVAL, Feature.CROSS_PAGE_PASTE,
        Feature.EXPORT_SVG_PSD_DXF, Feature.LAYER_HSL_BALANCE, Feature.LAYER_DUPLICATE_MERGE, Feature.EYEDROPPER_LOUPE,
    )

    /** [kind] modunda açık olan özellikler (bayrakla kapatılanlar hariç). */
    fun features(kind: CanvasKind): Set<Feature> {
        val base = when (kind) {
            CanvasKind.ART -> drawing + advanced + Feature.QUICK_BRUSH + Feature.QUICK_BRUSH_COLOR
            CanvasKind.SIMPLE_ART -> drawing + Feature.QUICK_BRUSH + Feature.QUICK_BRUSH_COLOR
            CanvasKind.COMIC -> drawing
            CanvasKind.NOTES, CanvasKind.SIMPLE_NOTES -> setOf(
                Feature.LIVE_FIX, Feature.LIVE_APPROVAL, Feature.CROSS_PAGE_PASTE, Feature.EXPORT_SVG_PSD_DXF, Feature.EYEDROPPER_LOUPE,
            )
            CanvasKind.KIDS -> setOf(Feature.SYMMETRY)
        }
        return base - off
    }

    fun enabled(feature: Feature, kind: CanvasKind): Boolean = feature in features(kind)
}
