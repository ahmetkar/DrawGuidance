import Foundation

/// Uygulama genelindeki düzenleyici bağımlılıkları (Android Hilt modülünün karşılığı).
@MainActor
final class EditorDependencies {
    /// Uygulama açılışında kurulur (`DrawGuidanceApp`).
    static let shared = EditorDependencies(canvases: CanvasStore(), prefs: EditorPrefsStore(), palette: ColorPaletteStore(),
                                           images: ImagesRepository(store: SavedImagesStore()), models3d: Model3DRepository(),
                                           model3dResults: .shared)
    let canvases: CanvasStore
    let assets: CanvasAssetStore
    let prefs: EditorPrefsStore
    let palette: ColorPaletteStore
    let shapeDb: ShapeTemplateRepository
    let shapeModels: ShapeModeModels
    let recent: RecentCanvasStore
    let images: ImagesRepository?
    let models3d: Model3DRepository?
    let model3dResults: Model3DResults?
    private(set) lazy var transfer = CanvasTransfer(canvases, assets: assets)

    init(canvases: CanvasStore, assets: CanvasAssetStore = .shared, prefs: EditorPrefsStore, palette: ColorPaletteStore,
         shapeDb: ShapeTemplateRepository = ShapeTemplateRepository(),
         shapeModels: ShapeModeModels = ShapeModeModels(FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first),
         recent: RecentCanvasStore = .shared, images: ImagesRepository?, models3d: Model3DRepository?, model3dResults: Model3DResults?) {
        self.canvases = canvases; self.assets = assets; self.prefs = prefs; self.palette = palette; self.shapeDb = shapeDb
        self.shapeModels = shapeModels; self.recent = recent; self.images = images; self.models3d = models3d; self.model3dResults = model3dResults
    }
}

