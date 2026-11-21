import Foundation

/// v56 3B Çizim düzlemi (Android `PlaneSnap`): hazır yönler (Ön, Yan, Üst, Kameraya dönük), ekrandan düzleme izdüşüm,
/// yapıştırma (yüze, çizgi ucuna, iki çizginin kesişimine), 15° mıknatıslı döndürme ve yatıklık uyarısı.
enum PlaneSnap {

    /// Yaslama adımı (derece).
    static let STEP_DEG: Float = 15

    /// Bu açıdan (düzlem normali ile bakış yönü arasında) fazlası "yatık" sayılır.
    static let TILT_WARN_DEG: Float = 75

    enum Preset: Int, CaseIterable { case FRONT, SIDE, TOP, CAMERA }

    /// Hazır yönde, [through] noktasından geçen düzlem. Kameraya dönük düzlem [view]'ın sağ ve yukarı eksenleridir.
    static func preset(_ kind: Preset, through: V3 = .ZERO, view: View3? = nil) -> Plane3 {
        switch kind {
        case .FRONT: return Plane3(through, V3(1, 0, 0), V3(0, 1, 0), id: "front")
        case .SIDE: return Plane3(through, V3(0, 0, -1), V3(0, 1, 0), id: "side")
        case .TOP: return Plane3(through, V3(1, 0, 0), V3(0, 0, -1), id: "top")
        case .CAMERA:
            guard let v = view else { return Plane3(through, V3(1, 0, 0), V3(0, 1, 0), id: "camera") }
            return Plane3(through, v.right.normalized(), (-v.down).normalized(), id: "camera")
        }
    }

    /// Ekran noktasının düzlemdeki karşılığı (ışın düzleme paralelse nil). Düzlemin kenarı yoktur.
    static func project(_ plane: Plane3, _ view: View3, _ sx: Float, _ sy: Float) -> V3? {
        let (o, d) = view.ray(sx, sy)
        return intersect(plane, o, d)
    }

    static func intersect(_ plane: Plane3, _ o: V3, _ d: V3) -> V3? {
        let n = plane.normal
        let den = d.dot(n)
        if abs(den) < 1e-5 { return nil }
        let t = (plane.origin - o).dot(n) / den
        if !t.isFinite { return nil }
        return o + d * t
    }

    /// Düzlem normali ile bakış yönü arasındaki açı (0: tam karşıdan, 90: yandan).
    static func tilt(_ plane: Plane3, _ view: View3, at: V3? = nil) -> Float {
        let p = at ?? plane.origin
        let toEye = view.perspective ? (view.eye - p).normalized() : view.toViewer
        let c = abs(plane.normal.dot(toEye)).coerceIn(0, 1)
        return Float(toDegrees(acos(Double(c))))
    }

    static func tooTilted(_ plane: Plane3, _ view: View3) -> Bool { tilt(plane, view) > TILT_WARN_DEG }

    /// Yüze yapıştırma: [hit] yüzünün dünya normaline dik, değilen noktadan geçen düzlem.
    static func onFace(_ model: Model3D, _ hit: Hit3) -> Plane3 {
        let part = model.parts[hit.part]
        let fn = part.mesh.faceNormals
        var out = [Float](repeating: 0, count: 3)
        Transform3.of(part).normal(fn[3 * hit.face], fn[3 * hit.face + 1], fn[3 * hit.face + 2], &out, 0)
        let n = V3(out[0], out[1], out[2]).normalized()
        return fromNormal(hit.point, n, id: "face:\(part.id):\(hit.face)")
    }

    /// [normal]'e dik, [origin]'den geçen düzlem (u yatay, v yukarıya en yakın).
    static func fromNormal(_ origin: V3, _ normal: V3, id: String = "custom") -> Plane3 {
        let n = normal.normalized()
        let ref = abs(n.y) > 0.95 ? V3(0, 0, -1) : V3.UP
        let v0 = (ref - n * ref.dot(n)).normalized()
        let u = v0.cross(n).normalized()
        return Plane3(origin, u, n.cross(u).normalized(), id: id)
    }

    /// Çizgi ucu ya da iki çizginin (ekranda) kesişimi [radius] piksel içindeyse o dünya noktası; yoksa nil.
    /// Uçlar kesişimlerden önce gelir.
    static func snapPoint(_ model: Model3D, _ view: View3, _ sx: Float, _ sy: Float, _ radius: Float) -> V3? {
        var s = [Float](repeating: 0, count: 4)
        var best: V3? = nil
        var bestD = radius
        for st in model.strokes {
            if st.count == 0 || model.layerHidden(st.layer) { continue }
            for i in [0, st.count - 1] {
                view.project(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2], &s)
                if s[3] <= 0 { continue }
                let d = ((s[0] - sx) * (s[0] - sx) + (s[1] - sy) * (s[1] - sy)).squareRoot()
                if d < bestD { bestD = d; best = V3(st.points[3 * i], st.points[3 * i + 1], st.points[3 * i + 2]) }
            }
        }
        if let best { return best }
        return intersection(model, view, sx, sy, radius)
    }

    /// Ekranda kesişen iki çizgi parçasının, dokunuşa en yakın kesişim noktası (iki parçanın 3B orta noktası).
    static func intersection(_ model: Model3D, _ view: View3, _ sx: Float, _ sy: Float, _ radius: Float) -> V3? {
        struct Seg { let ax: Float, ay: Float, bx: Float, by: Float; let a: V3, b: V3; let stroke: Int }
        var segs: [Seg] = []
        var pa = [Float](repeating: 0, count: 4), pb = [Float](repeating: 0, count: 4)
        let r2 = radius * 3
        for (si, st) in model.strokes.enumerated() {
            if model.layerHidden(st.layer) || st.count < 2 { continue }
            let p = st.points
            for i in 0..<(st.count - 1) {
                view.project(p[3 * i], p[3 * i + 1], p[3 * i + 2], &pa)
                view.project(p[3 * i + 3], p[3 * i + 4], p[3 * i + 5], &pb)
                if pa[3] <= 0 || pb[3] <= 0 { continue }
                if min(pa[0], pb[0]) > sx + r2 || max(pa[0], pb[0]) < sx - r2 || min(pa[1], pb[1]) > sy + r2 || max(pa[1], pb[1]) < sy - r2 { continue }
                segs.append(Seg(ax: pa[0], ay: pa[1], bx: pb[0], by: pb[1],
                                a: V3(p[3 * i], p[3 * i + 1], p[3 * i + 2]), b: V3(p[3 * i + 3], p[3 * i + 4], p[3 * i + 5]), stroke: si))
            }
        }
        var best: V3? = nil
        var bestD = radius
        if segs.count < 2 { return nil }
        for i in 0..<segs.count {
            for j in (i + 1)..<segs.count {
                let p = segs[i], q = segs[j]
                if p.stroke == q.stroke { continue }
                let rx = p.bx - p.ax, ry = p.by - p.ay, qx = q.bx - q.ax, qy = q.by - q.ay
                let den = rx * qy - ry * qx
                if abs(den) < 1e-6 { continue }
                let t = ((q.ax - p.ax) * qy - (q.ay - p.ay) * qx) / den
                let u = ((q.ax - p.ax) * ry - (q.ay - p.ay) * rx) / den
                if t < 0 || t > 1 || u < 0 || u > 1 { continue }
                let ix = p.ax + rx * t, iy = p.ay + ry * t
                let d = ((ix - sx) * (ix - sx) + (iy - sy) * (iy - sy)).squareRoot()
                if d < bestD {
                    bestD = d
                    let a = p.a + (p.b - p.a) * t, b = q.a + (q.b - q.a) * u
                    best = (a + b) * 0.5
                }
            }
        }
        return best
    }

    /// Düzlemi kendi normali boyunca [d] dünya birimi kaydırır.
    static func offset(_ plane: Plane3, _ d: Float) -> Plane3 {
        var c = plane
        c.origin = plane.origin + plane.normal * d
        return c
    }

    /// Düzlemi [axis] çevresinde (0: u ekseni, 1: v ekseni, 2: normal) [deg] derece döndürür; [snap] açıkken
    /// döndürme miktarı 15°'nin katına yuvarlanır.
    static func rotate(_ plane: Plane3, _ axis: Int, _ deg: Float, snap: Bool = true) -> Plane3 {
        let a = snap ? snapAngle(deg) : deg
        if a == 0 { return plane }
        let k = (axis == 0 ? plane.u : axis == 1 ? plane.v : plane.normal).normalized()
        var c = plane
        c.u = rotateAround(plane.u, k, a).normalized()
        c.v = rotateAround(plane.v, k, a).normalized()
        return c
    }

    /// Açıyı 15°'nin en yakın katına yuvarlar (Kotlin `roundToInt`: yarımlar yukarı).
    static func snapAngle(_ deg: Float) -> Float { (deg / STEP_DEG + 0.5).rounded(.down) * STEP_DEG }

    /// Rodrigues döndürmesi.
    static func rotateAround(_ v: V3, _ k: V3, _ deg: Float) -> V3 {
        let r = toRadians(Double(deg))
        let c = Float(cos(r)), s = Float(sin(r))
        return v * c + k.cross(v) * s + k * (k.dot(v) * (1 - c))
    }

    /// Kameraya çevir: düzlemin merkezi korunur, eksenler kameranın sağı ve yukarısı olur.
    static func faceCamera(_ plane: Plane3, _ view: View3) -> Plane3 { preset(.CAMERA, through: plane.origin, view: view) }

    /// Düzlemin ızgara çizgileri ([half] yarı kenar, [step] aralık): (başlangıç, bitiş) dünya noktaları.
    static func grid(_ plane: Plane3, _ half: Float, _ step: Float) -> [(V3, V3)] {
        let n = Int(half / step).coerceIn(1, 60)
        var out: [(V3, V3)] = []
        out.reserveCapacity(4 * n + 2)
        for i in -n...n {
            let t = Float(i) * step
            out.append((plane.world(t, -half), plane.world(t, half)))
            out.append((plane.world(-half, t), plane.world(half, t)))
        }
        return out
    }
}
