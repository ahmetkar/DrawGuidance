import CoreGraphics
import Foundation

/// v56 katman birleştirme (Android `LayerCompositor` ile aynı kural). Opaklığı 1 ve modu Normal olan kırpmasız katmanlar
/// doğrudan çizilir; ötekiler saydamlık katmanında (`beginTransparencyLayer`, katmanın opaklığı ve `CGBlendMode`)
/// toplanır. Alttakine kırpma: katman, alttaki ilk kırpmasız katmanın ("taban") alfa'sıyla `destinationIn` maskelenir.
/// Alfa kilidi: kilitliyken çizilen çizgi (`BrushParams.alphaLocked`) `sourceAtop` ile çizilir (`StrokeRenderer`).
enum LayerCompositor {
    static func blendMode(_ b: LayerBlend) -> CGBlendMode {
        switch b {
        case .NORMAL: return .normal
        case .MULTIPLY: return .multiply
        case .SCREEN: return .screen
        case .OVERLAY: return .overlay
        case .DARKEN: return .darken
        case .LIGHTEN: return .lighten
        case .COLOR_DODGE: return .colorDodge
        case .COLOR_BURN: return .colorBurn
        case .HARD_LIGHT: return .hardLight
        case .SOFT_LIGHT: return .softLight
        case .DIFFERENCE: return .difference
        case .EXCLUSION: return .exclusion
        case .HUE: return .hue
        case .SATURATION: return .saturation
        case .COLOR: return .color
        case .LUMINOSITY: return .luminosity
        case .ADD: return .plusLighter
        }
    }

    static func needed(_ layers: [Layer]) -> Bool { layers.contains { $0.composited } }

    /// Önbelleğe yalnız eklenen çizimleri çizmek doğru mu (eklenenlerin katmanı birleştirilmiyor ve kırpılan bir katmanın
    /// tabanı değil); değilse görüntü baştan üretilir.
    static func appendSafe(_ added: [Stroke], _ layers: [Layer]) -> Bool {
        if added.contains(where: { $0.params?.alphaLocked == true }) { return false }
        if !needed(layers) { return true }
        var unsafe = Set<String>()
        var base: Layer? = nil
        for (i, l) in layers.enumerated() {
            if l.clipToBelow && i > 0 {
                unsafe.insert(l.id)
                if let base { unsafe.insert(base.id) }
            } else {
                base = l
            }
            if l.composited { unsafe.insert(l.id) }
        }
        return !added.contains { unsafe.contains(Layers.of($0, layers)) }
    }

    /// Canlı (henüz önbellekte olmayan) çizginin katman görünümü: opaklık ve mod (kırpma önizlemede yok).
    static func withLayerLook(_ c: CGContext, _ layer: Layer?, _ body: () -> Void) {
        guard let layer, layer.opacity < 1 || layer.blend != .NORMAL else { body(); return }
        c.saveGState()
        c.setAlpha(CGFloat(min(max(layer.opacity, 0), 1)))
        c.setBlendMode(blendMode(layer.blend))
        c.beginTransparencyLayer(auxiliaryInfo: nil)
        body()
        c.endTransparencyLayer()
        c.restoreGState()
    }

    /// Çizimleri katman katman birleştirir; `plain` bir katmanın çizimlerini (silgi ve alfa kilidiyle) çizer.
    static func drawLayered(_ c: CGContext, _ strokes: [Stroke], _ layers: [Layer], plain: ([Stroke]) -> Void) {
        var index: [String: Int] = [:]
        for (i, l) in layers.enumerated() { index[l.id] = i }
        var groups = Array(repeating: [Stroke](), count: layers.count)
        for s in strokes { groups[index[Layers.of(s, layers)] ?? 0].append(s) }
        var base = -1
        for (i, l) in layers.enumerated() {
            let clipped = l.clipToBelow && base >= 0
            if !clipped { base = i }
            let list = groups[i]
            if list.isEmpty { continue }
            if !clipped && l.opacity >= 1 && l.blend == .NORMAL { plain(list); continue }
            c.saveGState()
            c.setAlpha(CGFloat(min(max(l.opacity, 0), 1)))
            c.setBlendMode(blendMode(l.blend))
            c.beginTransparencyLayer(auxiliaryInfo: nil)
            plain(list)
            if clipped {
                // Tabanın alfa'sı maske: taban boşsa (ya da gizliyse) kırpılan katman görünmez
                c.saveGState()
                c.setBlendMode(.destinationIn)
                c.beginTransparencyLayer(auxiliaryInfo: nil)
                plain(groups[base])
                c.endTransparencyLayer()
                c.restoreGState()
            }
            c.endTransparencyLayer()
            c.restoreGState()
        }
    }
}
