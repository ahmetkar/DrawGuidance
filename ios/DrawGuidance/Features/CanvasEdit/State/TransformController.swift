import Foundation

/// v56 yaslama ayarları (mod başına kayıtlı; Android `SnapPrefs`): mıknatıslar, yaslama, mesafe (ekran pt), hız eşiği.
struct SnapPrefs: Equatable {
    var magnets = true
    var snap = true
    var distance: Float = 10
    var speed: Float = 1500

    func encode() -> String { "m=\(magnets ? 1 : 0);s=\(snap ? 1 : 0);d=\(Int(distance.rounded()));v=\(Int(speed.rounded()))" }

    static func decode(_ text: String?) -> SnapPrefs {
        guard let text, !text.trimmingCharacters(in: .whitespaces).isEmpty else { return SnapPrefs() }
        var m: [String: String] = [:]
        for part in text.split(separator: ";") {
            let kv = part.split(separator: "=", omittingEmptySubsequences: false)
            if kv.count == 2 { m[String(kv[0])] = String(kv[1]) }
        }
        return SnapPrefs(magnets: m["m"] != "0", snap: m["s"] != "0",
                         distance: m["d"].flatMap(Float.init)?.coerceIn(2, 40) ?? 10, speed: m["v"].flatMap(Float.init)?.coerceIn(0, 6000) ?? 1500)
    }
}

/// v56 aşama 6 Dönüştür paneli durumu (Android `TransformController`): seçimin başlangıç kopyası, dönüşüm durumu, canlı
/// önizleme (geçmişe girmez), yaslama kılavuzları. "Bitti" tek geri alma adımı; "Sıfırla" ve "İptal" belgeyi değiştirmez.
@MainActor
final class TransformController: ObservableObject {
    private let store: EditorStore
    private let loadSnap: () -> String?
    private let saveSnap: (String) -> Void
    private let fitTarget: (Float, Float) -> [Float]?

    @Published private(set) var state: TransformState?
    @Published private(set) var snap = SnapPrefs()
    @Published private(set) var guides: (Float?, Float?) = (nil, nil)
    @Published private(set) var skipped = 0
    @Published private(set) var advancedMesh = false

    private var ids = Set<String>()
    private var original: [Stroke] = []
    private var targetsX: [Float] = []
    private var targetsY: [Float] = []

    var isOpen: Bool { state != nil }

    init(_ store: EditorStore, loadSnap: @escaping () -> String? = { nil }, saveSnap: @escaping (String) -> Void = { _ in },
         fitTarget: @escaping (Float, Float) -> [Float]? = { _, _ in nil }) {
        self.store = store; self.loadSnap = loadSnap; self.saveSnap = saveSnap; self.fitTarget = fitTarget
    }

    @discardableResult
    func open(_ kind: TransformKind = .UNIFORM) -> Bool {
        if isOpen { return true }
        guard let c = store.canvas else { return false }
        let sel = store.selection
        if sel.isEmpty { return false }
        let strokes = c.strokes
        guard let b = ElementOps.selectionBounds(strokes, sel) else { return false }
        snap = SnapPrefs.decode(loadSnap())
        ids = sel
        original = strokes
        var xs: [Float] = [0, Float(c.width) / 2, Float(c.width)]
        var ys: [Float] = [0, Float(c.height) / 2, Float(c.height)]
        for s in strokes.filter({ !sel.contains($0.id) && !$0.hidden }).prefix(400) {
            if let r = ElementOps.bounds(s) {
                xs += [r.left, (r.left + r.right) / 2, r.right]; ys += [r.top, (r.top + r.bottom) / 2, r.bottom]
            }
        }
        targetsX = xs; targetsY = ys
        skipped = 0
        state = TransformOps.setKind(TransformState.start(b.left, b.top, b.right, b.bottom), kind, meshSize: meshSize)
        return true
    }

    private var meshSize: Int { advancedMesh ? WarpMesh.ADVANCED : WarpMesh.BASIC }

    private func preview(_ s: TransformState) {
        state = s
        let applied = TransformOps.apply(original, ids, s)
        skipped = applied.skipped.count
        store.previewStrokes(applied.strokes)
    }

    private func change(_ f: (TransformState) -> TransformState) {
        guard let s = state else { return }
        preview(f(s))
    }

    func setKind(_ kind: TransformKind) { change { TransformOps.setKind($0, kind, meshSize: meshSize) } }

    func setAdvancedMesh(_ on: Bool) {
        advancedMesh = on
        change { s in
            guard s.kind == .WARP else { return s }
            var r = s
            if let m = s.mesh { r.mesh = WarpMesh.resample(m, WarpMesh.sizeOf(m), meshSize) }
            return TransformOps.setKind(r, .WARP, meshSize: meshSize)
        }
    }

    func setFilter(_ f: TransformFilter) { change { var s = $0; s.filter = f; return s } }

    func setSnap(_ p: SnapPrefs) { snap = p; saveSnap(p.encode()) }

    /// Taşıma: tuval birimi; [scale] görünüm ölçeği (yaslama mesafesi ekran pt), [speed] ekran pt/sn.
    func translate(_ dx: Float, _ dy: Float, scale: Float = 1, speed: Float = 0) {
        guard let s = state else { return }
        var moved = TransformOps.translate(s, dx, dy)
        if snap.snap && Snapping.active(speed, snap.speed) {
            let q = moved.mesh.map { WarpMesh.corners($0) } ?? moved.quad
            let l = q.map(\.x).min()!, r = q.map(\.x).max()!, t = q.map(\.y).min()!, b = q.map(\.y).max()!
            let res = Snapping.snapBox(l, t, r, b, targetsX, targetsY, snap.distance / max(0.01, scale))
            if res.dx != 0 || res.dy != 0 { moved = TransformOps.translate(moved, res.dx, res.dy) }
            guides = (res.guideX, res.guideY)
        } else {
            guides = (nil, nil)
        }
        preview(moved)
    }

    func endDrag() { guides = (nil, nil) }

    func dragCorner(_ i: Int, _ x: Float, _ y: Float) {
        change { s in
            var next = TransformOps.dragCorner(s, i, x, y)
            // Mıknatıs: Tekdüze'de ölçek tam oranlara (%25 adımları)
            if snap.magnets && s.kind == .UNIFORM {
                let k = TransformOps.widthOf(next.quad) / (s.r - s.l)
                let ks = Snapping.angle(k * 100, step: 25, tolerance: 3) / 100
                if ks != k && ks > 0 {
                    let o = next.quad[(i + 2) % 4], c = next.quad[i]
                    next = TransformOps.dragCorner(s, i, o.x + (c.x - o.x) * ks / k, o.y + (c.y - o.y) * ks / k)
                }
            }
            return next
        }
    }

    func dragEdge(_ e: Int, _ dx: Float, _ dy: Float) { change { TransformOps.dragEdge($0, e, dx, dy) } }
    func rotateTo(_ degrees: Float) { let m = snap.magnets; change { TransformOps.rotateTo($0, degrees, magnet: m) } }
    func dragMesh(_ index: Int, _ x: Float, _ y: Float) { change { TransformOps.dragMesh($0, index, x, y) } }
    func dragMeshInside(_ px: Float, _ py: Float, _ dx: Float, _ dy: Float) { change { TransformOps.dragMeshInside($0, px, py, dx, dy) } }
    func flip(horizontal: Bool) { change { TransformOps.flip($0, horizontal: horizontal) } }
    func rotateStep(_ degrees: Float = 45) { change { TransformOps.rotate($0, degrees) } }

    func fitToCanvas() {
        guard let c = store.canvas else { return }
        change { s in
            let cx = s.quad.reduce(0) { $0 + $1.x } / 4, cy = s.quad.reduce(0) { $0 + $1.y } / 4
            let box = fitTarget(cx, cy) ?? [0, 0, Float(c.width), Float(c.height)]
            return TransformOps.fit(s, box[0], box[1], box[2], box[3])
        }
    }

    func numeric(_ x: Float, _ y: Float, _ w: Float, _ h: Float, _ degrees: Float) { change { TransformOps.numeric($0, x, y, w, h, degrees) } }

    func reset() { change { TransformOps.reset($0) } }

    /// Uygula: tek geri alma adımı.
    func done() {
        guard let s = state else { return }
        store.endPreview()
        if !s.isIdentity {
            store.commit(TransformOps.apply(original, ids, s).strokes, useArea: false)
            store.select(ids)
        }
        close()
    }

    func cancel() {
        guard state != nil else { return }
        store.endPreview()
        close()
    }

    private func close() {
        state = nil
        guides = (nil, nil)
        original = []
        ids = []
    }

    /// Kutu bilgileri: (x, y, en, boy, açı).
    func metrics() -> [Float]? {
        guard let q = state?.quad else { return nil }
        return [q[0].x, q[0].y, TransformOps.widthOf(q), TransformOps.heightOf(q), TransformOps.angleOf(q)]
    }
}

/// v56 aşama 6 Sıvılaştır (Android `LiquifyController`): resim elemanında 17×17 ağ deformasyonu; it, döndür, büzüştür,
/// şişir. Önizlenir; "Bitti" tek geri alma adımı, "İptal" belgeyi değiştirmez. Asıl resim değişmez.
@MainActor
final class LiquifyController: ObservableObject {
    private let store: EditorStore
    @Published private(set) var target: String?
    @Published var mode: WarpMesh.Liquify = .PUSH
    @Published private(set) var radius: Float = 80
    @Published private(set) var strength: Float = 0.6

    private var original: [Stroke] = []
    private var mesh: [StrokePoint] = []

    var isOpen: Bool { target != nil }

    init(_ store: EditorStore) { self.store = store }

    func setRadius(_ r: Float) { radius = r.coerceIn(10, 400) }
    func setStrength(_ s: Float) { strength = s.coerceIn(0.05, 1) }

    @discardableResult
    func begin(_ imageId: String) -> Bool {
        guard let c = store.canvas,
              let img = c.strokes.first(where: { $0.id == imageId && $0.brushType == .IMAGE && $0.points.count >= 4 }) else { return false }
        original = c.strokes
        let old = img.warp?.mesh
        let n = old.map { WarpMesh.sizeOf($0) } ?? 0
        if n == WarpMesh.LIQUIFY { mesh = old! } else if n > 0 { mesh = WarpMesh.resample(old!, n, WarpMesh.LIQUIFY) } else { mesh = WarpMesh.onQuad(Array(img.points.prefix(4)), WarpMesh.LIQUIFY) }
        target = imageId
        return true
    }

    private func applied() -> [Stroke] {
        guard let id = target else { return original }
        let corners = WarpMesh.corners(mesh)
        return original.map { s in
            guard s.id == id else { return s }
            var o = s
            o.points = corners + s.points.dropFirst(4)
            var w = s.warp ?? ImageWarp()
            w.mesh = mesh
            o.warp = w
            return o
        }
    }

    func brush(_ cx: Float, _ cy: Float, _ dx: Float, _ dy: Float) {
        guard target != nil else { return }
        mesh = WarpMesh.liquify(mesh, mode, cx, cy, dx, dy, radius, strength)
        store.previewStrokes(applied())
    }

    func done() {
        guard target != nil else { return }
        let result = applied()
        store.endPreview()
        if result != original { store.commit(result, useArea: false) }
        close()
    }

    func cancel() {
        guard target != nil else { return }
        store.endPreview()
        close()
    }

    private func close() { target = nil; original = []; mesh = [] }
}
