import Foundation

/// v56 özellik bayrakları ve mod kuralı (Android `V56Flags` ile aynı). Bir özellik [off] kümesine eklenerek yayından
/// önce bütün modlarda gizlenebilir. Not almada yalnız yayın düzeltmeleri, sayfalar arası kes-yapıştır ve dışa aktarma;
/// çocuk modunda yalnız simetri.
enum V56Flags {
    enum Feature: CaseIterable {
        case SELECTION_MASK, LASSO_FILL, LAYER_BLEND, ALPHA_LOCK_CLIP, SELECTION_OPS, GRAIN, COLOR_ADJUST, TRANSFORM_PANEL,
             REFLECTION_LIQUIFY, PRESETS, SYMMETRY, QUICK_BRUSH, SKETCH_3D, ADD_3D_SHAPE, LIVE_FIX, LIVE_APPROVAL, CROSS_PAGE_PASTE,
             // 2D
             EXPORT_SVG_PSD_DXF, LAYER_HSL_BALANCE, LAYER_DUPLICATE_MERGE, QUICK_BRUSH_COLOR, EYEDROPPER_LOUPE,
             // 2E gelişmiş çizim araçları (yalnız Resim modu)
             ADV_SELECTION, ADV_TRANSFORM, ADV_ALIGN, ADV_PATHFINDER, ADV_APPEARANCE, ADV_NODES, ADV_REPEAT, ADV_EFFECTS
    }

    private static let advanced: Set<Feature> = [
        .ADV_SELECTION, .ADV_TRANSFORM, .ADV_ALIGN, .ADV_PATHFINDER, .ADV_APPEARANCE, .ADV_NODES, .ADV_REPEAT, .ADV_EFFECTS,
    ]

    /// Yayından önce kapatılan özellikler.
    static let off: Set<Feature> = []

    private static let drawing: Set<Feature> = [
        .SELECTION_MASK, .LASSO_FILL, .LAYER_BLEND, .ALPHA_LOCK_CLIP, .SELECTION_OPS, .GRAIN, .COLOR_ADJUST, .TRANSFORM_PANEL,
        .REFLECTION_LIQUIFY, .PRESETS, .SYMMETRY, .ADD_3D_SHAPE, .LIVE_FIX, .LIVE_APPROVAL, .CROSS_PAGE_PASTE,
        .EXPORT_SVG_PSD_DXF, .LAYER_HSL_BALANCE, .LAYER_DUPLICATE_MERGE, .EYEDROPPER_LOUPE,
    ]

    static func features(_ kind: CanvasKind) -> Set<Feature> {
        let base: Set<Feature>
        switch kind {
        case .ART: base = drawing.union(advanced).union([.QUICK_BRUSH, .QUICK_BRUSH_COLOR])
        case .SIMPLE_ART: base = drawing.union([.QUICK_BRUSH, .QUICK_BRUSH_COLOR])
        case .COMIC: base = drawing
        case .NOTES, .SIMPLE_NOTES: base = [.LIVE_FIX, .LIVE_APPROVAL, .CROSS_PAGE_PASTE, .EXPORT_SVG_PSD_DXF, .EYEDROPPER_LOUPE]
        case .KIDS: base = [.SYMMETRY, .EYEDROPPER_LOUPE] // 2D-5: çocukta da canlı renk dairesi
        }
        return base.subtracting(off)
    }

    static func enabled(_ feature: Feature, _ kind: CanvasKind) -> Bool { features(kind).contains(feature) }
}
