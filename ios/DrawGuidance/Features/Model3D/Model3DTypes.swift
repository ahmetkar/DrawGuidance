import Foundation

/// v25 resim olarak dışa aktarma ayarları.
struct ImageExport: Equatable {
    var size = 1080
    var aspect: Float = 1
    /// ARGB (0: saydam).
    var background: Int32 = 0
    var ground = false
    var shadow = true
    var edges = true
    var fit = true
    var jpeg = false
    var smoothness = 2
}

// Geri al/yinele ve "değişti mi" denetimleri için içerik eşitliği (Kotlin veri sınıfları gibi).
extension Part: Equatable {
    static func == (a: Part, b: Part) -> Bool {
        a.id == b.id && a.name == b.name && a.mesh == b.mesh && a.primitive == b.primitive && a.position == b.position
            && a.rotation == b.rotation && a.scale == b.scale && a.material == b.material && a.faceMaterials == b.faceMaterials
            && a.smooth == b.smooth && a.uv == b.uv && a.visible == b.visible && a.group == b.group && a.locked == b.locked
            && a.layer == b.layer
    }
}

extension Model3D: Equatable {
    static func == (a: Model3D, b: Model3D) -> Bool {
        a.id == b.id && a.name == b.name && a.scene == b.scene && a.camera == b.camera && a.groups == b.groups
            && a.strokes == b.strokes && a.parts == b.parts && a.layers == b.layers && a.plane == b.plane
    }

    func with(camera c: Camera3) -> Model3D { var m = self; m.camera = c; return m }
}
