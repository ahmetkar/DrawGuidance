import Foundation

/// v40 tutamakların ekrandaki boyutları (pt; tuval birimine ölçeğe bölünerek çevrilir) (Android `GuideMetrics`).
enum GuideMetrics {
    static let HANDLE_RADIUS: Float = 24
    static let HANDLE_GAP: Float = 30
    static let CAPTURE: Float = 28
    static let THICKNESS: Float = 72
    static let PICK_TOLERANCE: Float = 14
}

/// v40 cetvel/kalıp sürüklemesi (Android `guideGestures` + `rulerDrag`): dokunuş katmanı tuval koordinatlarını verir.
/// `begin` cetvel/kalıp dokunuşu tutarsa oturum döner; tutmazsa dokunuş çizime kalır.
@MainActor
final class GuideDrag {
    private let c: RulerController
    private let kind: Kind
    private let p0: P
    private let gap: Float
    private var snapOn = true
    private var lastMove = Date()
    private var twoStartAngle: Float?
    private var twoStartPose: RulerPose?
    private var twoStartCentroid = P(0, 0)
    private var last: P

    private enum Kind {
        case pick
        case ruler(RulerShape, RulerHandle, handleAngle: Float)
        case template(TemplateGhost, moving: Bool, scaleHandle: P)
    }

    private init(_ c: RulerController, _ kind: Kind, _ p0: P, _ gap: Float) { self.c = c; self.kind = kind; self.p0 = p0; self.gap = gap; last = p0 }

    /// `scale` görünüm ölçeği (ekran pt / tuval birimi).
    static func begin(_ c: RulerController, at p0: P, scale: Float) -> GuideDrag? {
        let s = max(scale, 0.01)
        let gap = GuideMetrics.HANDLE_GAP / s, r = GuideMetrics.HANDLE_RADIUS / s
        switch c.session {
        case .NONE: return nil
        case .TEMPLATE_PICK: return GuideDrag(c, .pick, p0, gap)
        case .RULER:
            guard let shape = c.shape() else { return nil }
            let hit = shape.hit(p0, handleRadius: r, gap: gap)
            if hit == .NONE { return nil }
            return GuideDrag(c, .ruler(shape, hit, handleAngle: shape.rotateHandleLocalAngle(gap: gap) * 180 / .pi), p0, gap)
        case .TEMPLATE:
            guard !c.locked, let ghost = c.template else { return nil }
            let mh = ghost.moveHandle(gap: gap), sh = ghost.scaleHandle(gap: gap)
            let moving = hypot(p0.x - mh.x, p0.y - mh.y) <= r
            let scaling = !moving && hypot(p0.x - sh.x, p0.y - sh.y) <= r
            if !moving && !scaling { return nil }
            return GuideDrag(c, .template(ghost, moving: moving, scaleHandle: sh), p0, gap)
        }
    }

    /// Kalıp seçiminde sürükleme görünümü kaydırır (çağıran yapar); bırakınca dokunulan eleman kalıp olur.
    var isPick: Bool { if case .pick = kind { return true }; return false }

    /// Tek parmak hareketi (tuval koordinatı).
    func move(_ p: P) {
        defer { last = p }
        twoStartAngle = nil
        switch kind {
        case .pick: break
        case let .ruler(start, hit, handleAngle):
            guard var current = c.pose else { return }
            switch hit {
            case .BODY: current.cx += p.x - last.x; current.cy += p.y - last.y; c.setPose(current)
            case .ROTATE:
                // Tutamakta RELEASE_MS hareketsiz kalınırsa yakalama bırakılır (ince ayar)
                if snapOn && Date().timeIntervalSince(lastMove) * 1000 >= Double(AngleSnap.RELEASE_MS) { snapOn = false }
                let raw = atan2(p.y - start.pose.cy, p.x - start.pose.cx) * 180 / .pi - handleAngle
                current.angle = AngleSnap.snap(raw, enabled: snapOn)
                c.setPose(current)
            case .SIZE, .MINOR: c.setPose(start.resized(hit, p, gap: gap))
            case .NONE: break
            }
        case let .template(ghost, moving, sh):
            c.setTemplate(moving ? ghost.moved(p.x - p0.x, p.y - p0.y) : ghost.scaled(ghost.scaleFor(p, startScale: ghost.scale, startHandle: sh)))
        }
        lastMove = Date()
    }

    /// İki parmak (yalnız cetvel gövdesinde): döndürür ve taşır.
    func twoFinger(_ a: P, _ b: P) {
        guard case let .ruler(_, hit, _) = kind, hit == .BODY, let current = c.pose else { return }
        let angle = atan2(b.y - a.y, b.x - a.x) * 180 / .pi
        let centroid = P((a.x + b.x) / 2, (a.y + b.y) / 2)
        guard let startAngle = twoStartAngle, let sp = twoStartPose else {
            twoStartAngle = angle; twoStartPose = current; twoStartCentroid = centroid
            return
        }
        let target = AngleSnap.snap(sp.angle + (angle - startAngle))
        let delta = (target - sp.angle) * .pi / 180
        let rx = sp.cx - twoStartCentroid.x, ry = sp.cy - twoStartCentroid.y
        let co = cos(delta), si = sin(delta)
        var n = sp
        n.cx = centroid.x + rx * co - ry * si; n.cy = centroid.y + rx * si + ry * co; n.angle = target
        c.setPose(n)
        last = a
    }

    /// Bırakış: kalıp seçiminde hareket yoksa dokunulan eleman kalıp olur.
    func end(moved: Bool, scale: Float) {
        if isPick && !moved { _ = c.pickAt(p0, tolerance: GuideMetrics.PICK_TOLERANCE / max(scale, 0.01)) }
    }
}
