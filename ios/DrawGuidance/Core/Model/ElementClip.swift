import Foundation

/// v56 2C-3 sayfalar arası kes, kopyala, yapıştır (Android `ElementClip` ile aynı kural): seçili elemanların
/// toplanması, yapıştırılacak konum, yeni kimlikler (gruplar ve kırpma maskesi kaynağı kendi aralarında korunur) ve
/// resim varlıklarının kimlik eşlemesi.
enum ElementClip {
    static let REPEAT_SHIFT: Float = 16

    /// Sayfadan seçili elemanlar (çizim sırasıyla); seçili elemanların kırpma maskesi kaynağı da alınır.
    static func collect(_ page: [Stroke], _ ids: Set<String>) -> [Stroke] {
        if ids.isEmpty { return [] }
        let sources = Set(page.filter { ids.contains($0.id) }.compactMap { $0.mask?.source })
        return page.filter { ids.contains($0.id) || sources.contains($0.id) }
    }

    /// Kapsayan kutu [sol, üst, sağ, alt] (boşsa nil).
    static func bounds(_ strokes: [Stroke]) -> [Float]? {
        var l = Float.infinity, t = Float.infinity, r = -Float.infinity, b = -Float.infinity
        for s in strokes { for p in s.points { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) } }
        return l > r || t > b ? nil : [l, t, r, b]
    }

    /// Yapıştırma kayması (Android `ElementClip.offset`).
    static func offset(_ bounds: [Float], pageW: Float, pageH: Float, visible: [Float]? = nil, anchor: P? = nil, repeat: Int = 0) -> (Float, Float) {
        let cx = (bounds[0] + bounds[2]) / 2, cy = (bounds[1] + bounds[3]) / 2
        let shift = REPEAT_SHIFT * Float(repeat)
        if let a = anchor { return (a.x - cx + shift, a.y - cy + shift) }
        let outside = bounds[0] < -0.5 || bounds[1] < -0.5 || bounds[2] > pageW + 0.5 || bounds[3] > pageH + 0.5
        var hidden = false
        if let v = visible { hidden = bounds[2] < v[0] || bounds[0] > v[2] || bounds[3] < v[1] || bounds[1] > v[3] }
        if outside || hidden {
            let tx = visible.map { ($0[0] + $0[2]) / 2 } ?? pageW / 2
            let ty = visible.map { ($0[1] + $0[3]) / 2 } ?? pageH / 2
            return (tx - cx + shift, ty - cy + shift)
        }
        return (shift, shift)
    }

    /// Elemanların kullandığı tuval varlıkları (sıralı, tekrarsız).
    static func assetIds(_ strokes: [Stroke]) -> [String] {
        var out: [String] = []
        func add(_ s: String?) { if let s, !out.contains(s) { out.append(s) } }
        for s in strokes {
            add(s.assetId)
            add(s.model?.dataAssetId)
            if let b = s.text?.box?.imageId, b.hasPrefix(BackgroundSpec.ASSET) { add(String(b.dropFirst(BackgroundSpec.ASSET.count))) }
            add(s.params?.stampAssetId)
        }
        return out
    }

    /// Yapıştırılacak kopyalar (Android `ElementClip.paste`).
    static func paste(_ clip: [Stroke], newId: () -> String, startOrder: Int, dx: Float = 0, dy: Float = 0,
                      assets: [String: String] = [:], q: (Float) -> Float = { $0 }) -> [Stroke] {
        var ids: [String: String] = [:]
        for s in clip { ids[s.id] = newId() }
        var groups: [String: String] = [:]
        func moved(_ p: StrokePoint) -> StrokePoint { p.at(q(p.x + dx), q(p.y + dy)) }
        return clip.enumerated().map { i, s in
            var c = s
            c.id = ids[s.id]!
            if let g = s.groupId {
                if groups[g] == nil { groups[g] = newId() }
                c.groupId = groups[g]
            }
            c.order = startOrder + i
            c.layerId = nil
            c.points = s.points.map(moved)
            c.clip = s.clip?.map(moved)
            c.style2 = V56Vector.mapStyle(s.style2, moved)
            if var m = s.mask {
                m.points = m.points.map(moved)
                if let src = m.source { m.source = ids[src] ?? src }
                c.mask = m
            }
            if !assets.isEmpty {
                if let a = s.assetId, let n = assets[a] { c.assetId = n }
                if let d = s.model?.dataAssetId, let n = assets[d] { c.model?.dataAssetId = n }
                if let a = s.params?.stampAssetId, let n = assets[a] { c.params?.stampAssetId = n }
                if let b = s.text?.box?.imageId, b.hasPrefix(BackgroundSpec.ASSET), let n = assets[String(b.dropFirst(BackgroundSpec.ASSET.count))] {
                    c.text?.box?.imageId = BackgroundSpec.ASSET + n
                }
            }
            return c
        }
    }
}
