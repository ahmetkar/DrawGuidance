import Foundation

/// v56 aşama 8: simetri kılavuzu (dikey, yatay, dörtlü, radyal) ve "Desen yap" (Android `V56Symmetry.kt` ile aynı hesap).
enum V56Symmetry {
    /// Merkez etrafında 2×2 doğrusal dönüşüm (x' = cx + a·dx + b·dy, y' = cy + c·dx + d·dy).
    struct Affine: Equatable {
        let a: Float, b: Float, c: Float, d: Float
        func map(_ cx: Float, _ cy: Float, _ x: Float, _ y: Float) -> (Float, Float) {
            let dx = x - cx, dy = y - cy
            return (cx + a * dx + b * dy, cy + c * dx + d * dy)
        }
    }

    private static func rad(_ deg: Float) -> Float { deg * .pi / 180 }

    /// Yönü [deg] olan, merkezden geçen eksene göre yansıma.
    static func reflect(_ deg: Float) -> Affine {
        let t = 2 * rad(deg)
        return Affine(a: cos(t), b: sin(t), c: sin(t), d: -cos(t))
    }

    static func rotate(_ deg: Float) -> Affine {
        let t = rad(deg)
        return Affine(a: cos(t), b: -sin(t), c: sin(t), d: cos(t))
    }

    /// Özdeşlik dışındaki kopya dönüşümleri.
    static func transforms(_ spec: SymmetrySpec) -> [Affine] {
        switch spec.kind {
        case .VERTICAL: return [reflect(90 + spec.angle)]
        case .HORIZONTAL: return [reflect(spec.angle)]
        case .QUAD: return [reflect(90 + spec.angle), reflect(spec.angle), rotate(180)]
        case .RADIAL:
            let n = min(max(spec.arms, 2), 16)
            return (1..<n).map { rotate(360 * Float($0) / Float(n)) }
        }
    }

    /// Eksen çizgileri: her biri [x1, y1, x2, y2] ([len] merkezden uzunluk).
    static func axes(_ spec: SymmetrySpec, _ len: Float) -> [[Float]] {
        let dirs: [Float]
        switch spec.kind {
        case .VERTICAL: dirs = [90 + spec.angle]
        case .HORIZONTAL: dirs = [spec.angle]
        case .QUAD: dirs = [90 + spec.angle, spec.angle]
        case .RADIAL:
            let n = min(max(spec.arms, 2), 16)
            dirs = (0..<n).map { -90 + spec.angle + 360 * Float($0) / Float(n) }
        }
        return dirs.map { deg in
            let ux = cos(rad(deg)), uy = sin(rad(deg))
            if spec.kind == .RADIAL { return [spec.cx, spec.cy, spec.cx + ux * len, spec.cy + uy * len] }
            return [spec.cx - ux * len, spec.cy - uy * len, spec.cx + ux * len, spec.cy + uy * len]
        }
    }

    static func mapPoints(_ points: [StrokePoint], _ cx: Float, _ cy: Float, _ t: Affine) -> [StrokePoint] {
        points.map { p in
            let (x, y) = t.map(cx, cy, p.x, p.y)
            var q = p; q.x = x; q.y = y
            return q
        }
    }

    /// [s] çiziminin simetri kopyaları (yeni kimliklerle, aynı katmanda).
    static func copies(_ s: Stroke, _ spec: SymmetrySpec, newId: () -> String) -> [Stroke] {
        transforms(spec).enumerated().map { i, t in
            var c = s
            c.id = newId(); c.points = mapPoints(s.points, spec.cx, spec.cy, t); c.order = s.order + i + 1
            return c
        }
    }

    /// Tuvalin ortasında yeni simetri ayarı.
    static func start(_ kind: SymmetryKind, _ width: Int, _ height: Int, arms: Int = 6) -> SymmetrySpec {
        SymmetrySpec(kind: kind, arms: kind == .RADIAL ? arms : 2, cx: Float(width) / 2, cy: Float(height) / 2)
    }

    // MARK: Desen yap

    /// Seçili elemanları ([bounds]: l, t, r, b) [gap] aralıkla tuval boyunca döşer; kopyalar kaynağın grubuna (yoksa yeni gruba) girer.
    static func pattern(_ selected: [Stroke], _ bounds: [Float], _ width: Int, _ height: Int, _ gap: Float, maxCopies: Int = 200,
                        newId: () -> String) -> [Stroke] {
        if selected.isEmpty { return [] }
        let w = bounds[2] - bounds[0] + gap, h = bounds[3] - bounds[1] + gap
        if w <= 1 || h <= 1 { return [] }
        let i0 = -Int(ceil(bounds[0] / w)), i1 = Int(ceil((Float(width) - bounds[2]) / w))
        let j0 = -Int(ceil(bounds[1] / h)), j1 = Int(ceil((Float(height) - bounds[3]) / h))
        var out: [Stroke] = []
        let group = selected.compactMap(\.groupId).first ?? newId()
        var copies = 0
        outer: for j in stride(from: j0, through: j1, by: 1) {
            for i in stride(from: i0, through: i1, by: 1) {
                if i == 0 && j == 0 { continue }
                if copies >= maxCopies { break outer }
                let dx = Float(i) * w, dy = Float(j) * h
                for s in selected { var c = V56Arrange.shift(s, dx, dy); c.id = newId(); c.groupId = group; out.append(c) }
                copies += 1
            }
        }
        return out
    }

    /// Desende kaç kopya olur.
    static func patternCount(_ bounds: [Float], _ width: Int, _ height: Int, _ gap: Float) -> Int {
        let w = bounds[2] - bounds[0] + gap, h = bounds[3] - bounds[1] + gap
        if w <= 1 || h <= 1 { return 0 }
        let cols = Int(ceil(bounds[0] / w)) + Int(ceil((Float(width) - bounds[2]) / w)) + 1
        let rows = Int(ceil(bounds[1] / h)) + Int(ceil((Float(height) - bounds[3]) / h)) + 1
        return max(cols * rows - 1, 0)
    }
}
