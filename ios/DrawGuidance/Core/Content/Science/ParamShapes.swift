import Foundation

/// v45/v46 parametreli fen ve geometri şekillerinin kaydı (Android'de kategorilerdeki `XModel.shape`).
/// Kimlikler kütüphane kimlikleridir ("sci.phys.projectile"…); eski kayıtlar varsayılan değerlerle açılır.
enum ParamShapes {
    static let models: [ParamModel] = [
        TriangleModel.shared, CircleModel.shared, PolygonModel.shared, ParabolaModel.shared, PopulationModel.shared,
        ProjectileModel.shared, WaveModel.shared, SpringModel.shared, PendulumModel.shared, LensModel.convex, LensModel.concave,
        InclineModel.shared, OhmModel.shared, FreeBodyModel.shared, FixedPulleyModel.shared, MovablePulleyModel.shared, LeverModel.shared,
        PeriodicCellModel.shared, BohrAtomModel.shared,
    ] + Molecules.all

    private static let byId: [String: ParamModel] = Dictionary(models.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
    private static let shapes = ShapeCache()

    static func model(_ id: String) -> ParamModel? { byId[id] }

    /// Varsayılan değerlerle çizilmiş kütüphane şekli (ilk istekte bir kez kurulur).
    static func shape(_ id: String) -> LibShape? {
        guard let m = byId[id] else { return nil }
        return shapes.get(id) { m.makeShape() }
    }

    private final class ShapeCache {
        private var map: [String: LibShape] = [:]
        private let lock = NSLock()
        func get(_ id: String, _ make: () -> LibShape) -> LibShape {
            if let s = lock.withLock({ map[id] }) { return s }
            let s = make()
            lock.withLock { map[id] = s }
            return s
        }
    }
}
