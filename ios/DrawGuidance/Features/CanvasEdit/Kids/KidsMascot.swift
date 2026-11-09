import SwiftUI

/*
 * v37 çocuk modunun maskotu Boyacık (Android `KidsMascot`): bereli, yuvarlak, turuncu küçük bir ressam (özgün karakter).
 * SwiftUI Canvas ile ışık (sol üst), gölge, kenar ışığı ve parlamalarla oyuncak gibi çizilir. Her konuşmada başka bir
 * yüz ifadesine ve el hareketine bürünür; yazı yazılırken ağzı açılıp kapanır, boştayken göz kırpar ve hafifçe zıplar.
 * Çizim 120×120 birimlik bir kutuda yapılır.
 */

enum MascotEyes { case OPEN, WIDE, HAPPY, WINK, SPARKLE }

/// Kaşların duruşu: lift yukarı kalkma, tilt dış ucun kalkması (sol ve sağ kaş).
enum MascotBrows {
    case NORMAL, RAISED, ONE_UP, PROUD
    var v: (ll: CGFloat, lt: CGFloat, rl: CGFloat, rt: CGFloat) {
        switch self {
        case .NORMAL: return (0, 0, 0, 0)
        case .RAISED: return (-3.2, 0.4, -3.2, 0.4)
        case .ONE_UP: return (0.6, -0.6, -4.2, 1.6)
        case .PROUD: return (-1.2, 1.4, -1.2, 1.4)
        }
    }
}

enum MascotMouth { case SMILE, GRIN, O, SMALL, SMIRK }

/// Yüz ifadeleri: göz, kaş, ağız, bakış yönü (-1..1) ve yanak kızarıklığı.
enum MascotFace: String {
    case HAPPY, SURPRISED, THINKING, PROUD, WINKING, EXPLAINING, EXCITED
    var spec: (eyes: MascotEyes, brows: MascotBrows, mouth: MascotMouth, gx: CGFloat, gy: CGFloat, blush: Double) {
        switch self {
        case .HAPPY: return (.OPEN, .NORMAL, .SMILE, 0, 0.1, 0.55)
        case .SURPRISED: return (.WIDE, .RAISED, .O, 0, -0.3, 0.35)
        case .THINKING: return (.OPEN, .ONE_UP, .SMALL, 0.7, -0.8, 0.3)
        case .PROUD: return (.HAPPY, .PROUD, .GRIN, 0, 0, 0.75)
        case .WINKING: return (.WINK, .NORMAL, .SMIRK, 0.25, 0, 0.6)
        case .EXPLAINING: return (.OPEN, .NORMAL, .SMILE, -0.45, -0.7, 0.45)
        case .EXCITED: return (.SPARKLE, .RAISED, .GRIN, 0, 0, 0.8)
        }
    }
}

enum MascotGesture: String { case REST, WAVE, POINT, THUMBS_UP, BRUSH, CHEER, THINK }

struct MascotLook: Equatable {
    let face: MascotFace
    let gesture: MascotGesture
    static let idle = MascotLook(face: .HAPPY, gesture: .REST)
}

/// Maskotun o anki duruşu: görünüş, ağız açık mı, göz kırpma (0..1), zıplama (0..1), el sallama (-1..1).
struct MascotPose {
    var look: MascotLook
    var mouthOpen = false
    var blink: CGFloat = 0
    var bounce: CGFloat = 0
    var swing: CGFloat = 0
    /// Testler ve erişilebilirlik için: "YÜZ/EL/open|closed".
    var describe: String { "\(look.face.rawValue)/\(look.gesture.rawValue)/\(mouthOpen ? "open" : "closed")" }
}

/// Maskotun kendisi. [faceOnly] küçük düğmede yalnız yüz ve bere; [animate] kapalıyken hareketsiz.
struct MascotFigure: View {
    let look: MascotLook
    var mouthOpen = false
    var faceOnly = false
    var animate = true

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: !animate)) { tl in
            let pose = animate ? Self.pose(look, mouthOpen, tl.date) : MascotPose(look: look, mouthOpen: mouthOpen)
            Canvas { ctx, size in
                let s = min(size.width, size.height) / 120
                var c = ctx
                c.translateBy(x: (size.width - 120 * s) / 2, y: (size.height - 120 * s) / 2)
                c.scaleBy(x: s, y: s)
                if faceOnly { MascotDraw.scale(&c, 1.7, 1.7, CGPoint(x: 60, y: 66)) }
                MascotDraw.mascot(&c, pose)
            }
            .accessibilityValue(pose.describe)
        }
    }

    /// Android sonsuz geçişleriyle aynı zamanlama: zıplama 1300 ms gidiş-dönüş, göz kırpma 3800 ms, el sallama 420 ms.
    static func pose(_ look: MascotLook, _ mouthOpen: Bool, _ date: Date) -> MascotPose {
        let ms = date.timeIntervalSinceReferenceDate * 1000
        func pingPong(_ period: Double) -> Double {
            let p = ms.truncatingRemainder(dividingBy: 2 * period) / period
            return p <= 1 ? p : 2 - p
        }
        let b = pingPong(1300)
        let bounce = fastOutSlowIn(b)
        let k = ms.truncatingRemainder(dividingBy: 3800)
        let blink: Double
        switch k {
        case ..<3420: blink = 0
        case ..<3520: blink = (k - 3420) / 100
        case ..<3580: blink = 1
        case ..<3700: blink = 1 - (k - 3580) / 120
        default: blink = 0
        }
        let swing = pingPong(420) * 2 - 1
        return MascotPose(look: look, mouthOpen: mouthOpen, blink: CGFloat(blink), bounce: CGFloat(bounce), swing: CGFloat(swing))
    }

    /// Compose FastOutSlowInEasing (kübik Bézier 0.4, 0, 0.2, 1).
    static func fastOutSlowIn(_ x: Double) -> Double {
        func bez(_ t: Double, _ a: Double, _ b: Double) -> Double { 3 * a * t * (1 - t) * (1 - t) + 3 * b * t * t * (1 - t) + t * t * t }
        var lo = 0.0, hi = 1.0
        for _ in 0..<20 { let m = (lo + hi) / 2; if bez(m, 0.4, 0.2) < x { lo = m } else { hi = m } }
        return bez((lo + hi) / 2, 0, 1)
    }
}

// ------------------------------------------------------------------------------------------------ çizim

enum MascotDraw {
    private static func c(_ argb: UInt32) -> Color {
        Color(.sRGB, red: Double((argb >> 16) & 0xFF) / 255, green: Double((argb >> 8) & 0xFF) / 255,
              blue: Double(argb & 0xFF) / 255, opacity: Double((argb >> 24) & 0xFF) / 255)
    }
    static let bodyLight = c(0xFFFFEDB0), bodyC = c(0xFFFFBF3C), bodyShade = c(0xFFF38D1F), bodyDeep = c(0xFFCF5F0C)
    static let belly = c(0xFFFFF6DE), beretLight = c(0xFFD4C5FF), beret = c(0xFF8B5CF6), beretDark = c(0xFF4C1D95)
    static let irisLight = c(0xFF8A7CF5), iris = c(0xFF4637B4), irisDark = c(0xFF1C1552), pupil = c(0xFF0F0A29)
    static let scleraShade = c(0xFFD9E1EF), lineC = c(0xFF5B2A0B), browC = c(0xFF8C4513), mouthC = c(0xFF7A2232)
    static let tongue = c(0xFFFF8DA2), cheek = c(0xFFFF6F91), wood = c(0xFFB7652A), woodLight = c(0xFFE3A35F)
    static let metal = c(0xFFB8C0CC), paint = c(0xFF3B82F6), sparkle = c(0xFFFFC928)
    static let white = Color.white

    // ---- yardımcılar (Compose DrawScope karşılıkları)

    static func scale(_ g: inout GraphicsContext, _ sx: CGFloat, _ sy: CGFloat, _ p: CGPoint) {
        g.translateBy(x: p.x, y: p.y); g.scaleBy(x: sx, y: sy); g.translateBy(x: -p.x, y: -p.y)
    }
    static func rotate(_ g: inout GraphicsContext, _ deg: CGFloat, _ p: CGPoint) {
        g.translateBy(x: p.x, y: p.y); g.rotate(by: .degrees(deg)); g.translateBy(x: -p.x, y: -p.y)
    }
    static func oval(_ r: CGRect) -> Path { Path(ellipseIn: r) }
    static func oval(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> Path { Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h)) }
    static func circle(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)) }
    static func radial(_ stops: [(Double, Color)], _ center: CGPoint, _ r: CGFloat) -> GraphicsContext.Shading {
        .radialGradient(Gradient(stops: stops.map { .init(color: $0.1, location: $0.0) }), center: center, startRadius: 0, endRadius: r)
    }
    static func radial(_ colors: [Color], _ center: CGPoint, _ r: CGFloat) -> GraphicsContext.Shading {
        .radialGradient(Gradient(colors: colors), center: center, startRadius: 0, endRadius: r)
    }
    static func vertical(_ stops: [(Double, Color)], _ y0: CGFloat, _ y1: CGFloat) -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: stops.map { .init(color: $0.1, location: $0.0) }), startPoint: CGPoint(x: 0, y: y0), endPoint: CGPoint(x: 0, y: y1))
    }
    static func round(_ w: CGFloat) -> StrokeStyle { StrokeStyle(lineWidth: w, lineCap: .round) }
    /// Compose `drawArc` (dikdörtgene oturan elips yayı; açılar derece, saat yönünde, 3 hizasından).
    static func arc(_ r: CGRect, _ start: CGFloat, _ sweep: CGFloat) -> Path {
        var p = Path()
        let steps = max(Int(abs(sweep) / 4), 2)
        for i in 0...steps {
            let a = (start + sweep * CGFloat(i) / CGFloat(steps)) * .pi / 180
            let pt = CGPoint(x: r.midX + r.width / 2 * cos(a), y: r.midY + r.height / 2 * sin(a))
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }
    static func quad(_ a: CGPoint, _ c: CGPoint, _ b: CGPoint) -> Path {
        var p = Path(); p.move(to: a); p.addQuadCurve(to: b, control: c); return p
    }
    static func line(_ g: GraphicsContext, _ color: Color, _ a: CGPoint, _ b: CGPoint, _ w: CGFloat, cap: CGLineCap = .round) {
        var p = Path(); p.move(to: a); p.addLine(to: b)
        g.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: w, lineCap: cap))
    }

    // ---- maskot

    static func mascot(_ g: inout GraphicsContext, _ pose: MascotPose) {
        let face = pose.look.face, spec = face.spec
        let lift = -1.8 * pose.bounce
        let squash = 0.014 * (1 - pose.bounce)
        groundShadow(g, 1 - 0.12 * pose.bounce)
        var t = g
        t.translateBy(x: 0, y: lift)
        scale(&t, 1 + squash, 1 - squash, CGPoint(x: 60, y: 108))
        feet(t); body(t); beret(t); cheeks(t, spec.blush)
        eyes(t, face, spec.eyes == .HAPPY ? 0 : pose.blink)
        brows(t, spec.brows)
        mouth(t, spec.mouth, pose.mouthOpen)
        arms(t, pose.look.gesture, pose.swing)
        if face == .EXCITED || pose.look.gesture == .CHEER { sparkles(t) }
    }

    private static func groundShadow(_ g: GraphicsContext, _ strength: CGFloat) {
        var s = g
        scale(&s, 1, 0.2, CGPoint(x: 60, y: 113))
        let c = CGPoint(x: 60, y: 113)
        s.fill(circle(c, 38), with: radial([Color.black.opacity(0.28 * strength), Color.black.opacity(0)], c, 38))
    }

    private static func feet(_ g: GraphicsContext) {
        for x: CGFloat in [44, 76] {
            let tl = CGPoint(x: x - 11, y: 100)
            g.fill(oval(tl.x - 0.8, tl.y - 0.8, 23.6, 13.6), with: .color(bodyDeep.opacity(0.6)))
            g.fill(oval(tl.x, tl.y, 22, 12), with: vertical([(0, bodyC), (1, bodyShade)], 100, 112))
            g.fill(oval(tl.x + 4, tl.y + 1.6, 9, 3.2), with: .color(white.opacity(0.35)))
        }
    }

    private static let BODY = CGRect(x: 24, y: 34, width: 72, height: 74)

    private static func body(_ g: GraphicsContext) {
        g.fill(oval(BODY.insetBy(dx: -1, dy: -1)), with: .color(bodyDeep.opacity(0.55)))
        g.fill(oval(BODY), with: radial([(0, bodyLight), (0.4, bodyC), (0.8, bodyShade), (1, bodyDeep)], CGPoint(x: 46, y: 52), 66))
        g.fill(oval(BODY), with: vertical([(0.62, bodyDeep.opacity(0)), (1, bodyDeep.opacity(0.32))], BODY.minY, BODY.maxY))
        g.fill(oval(40, 83, 40, 24), with: radial([belly.opacity(0.9), belly.opacity(0)], CGPoint(x: 60, y: 95), 21))
        g.stroke(arc(BODY.insetBy(dx: 3, dy: 3), 8, 72), with: .color(white.opacity(0.32)), style: round(2.4))
        var r = g
        rotate(&r, -32, CGPoint(x: 40, y: 47))
        r.fill(oval(30, 42, 20, 10), with: radial([white.opacity(0.9), white.opacity(0)], CGPoint(x: 40, y: 47), 10))
        g.fill(circle(CGPoint(x: 51, y: 41.5), 1.7), with: .color(white.opacity(0.85)))
    }

    private static func beret(_ g: GraphicsContext) {
        g.fill(oval(35, 37.5, 52, 9), with: radial([Color.black.opacity(0.22), Color.black.opacity(0)], CGPoint(x: 61, y: 42), 26))
        var r = g
        rotate(&r, -9, CGPoint(x: 60, y: 34))
        r.fill(oval(30, 24.2, 60, 20.6), with: .color(beretDark.opacity(0.75)))
        r.fill(oval(31, 25, 58, 19), with: radial([(0, beretLight), (0.45, beret), (1, beretDark)], CGPoint(x: 49, y: 28), 38))
        r.stroke(arc(CGRect(x: 33, y: 27, width: 54, height: 16), 18, 144), with: .color(beretDark.opacity(0.55)), lineWidth: 1.5)
        r.fill(circle(CGPoint(x: 61, y: 23.2), 4.3), with: .color(beretDark))
        r.fill(circle(CGPoint(x: 61, y: 23.2), 3.6), with: radial([beretLight, beret, beretDark], CGPoint(x: 59.8, y: 21.8), 5))
        r.fill(circle(CGPoint(x: 45, y: 32.5), 2.5), with: .color(c(0xFFEF4444)))
        r.fill(circle(CGPoint(x: 72, y: 35), 2.1), with: .color(c(0xFF22C55E)))
        r.fill(circle(CGPoint(x: 57.5, y: 30), 1.8), with: .color(c(0xFF38BDF8)))
        for d in [CGPoint(x: 44.2, y: 31.6), CGPoint(x: 71.4, y: 34.3), CGPoint(x: 57, y: 29.4)] {
            r.fill(circle(d, 0.7), with: .color(white.opacity(0.7)))
        }
        r.fill(oval(39, 27.2, 13, 4), with: .color(white.opacity(0.5)))
    }

    private static func cheeks(_ g: GraphicsContext, _ blush: Double) {
        for p in [CGPoint(x: 35, y: 75), CGPoint(x: 85, y: 75)] {
            g.fill(circle(p, 8.5), with: radial([cheek.opacity(blush), cheek.opacity(0)], p, 8.5))
        }
    }

    private static let EYE_L = CGPoint(x: 46, y: 62)
    private static let EYE_R = CGPoint(x: 74, y: 62)

    private static func eyes(_ g: GraphicsContext, _ face: MascotFace, _ blink: CGFloat) {
        let s = face.spec
        let gaze = CGPoint(x: s.gx, y: s.gy)
        switch s.eyes {
        case .HAPPY: closedHappy(g, EYE_L); closedHappy(g, EYE_R)
        case .WINK: openEye(g, EYE_L, gaze, blink, wide: false, sparkle: false); closedHappy(g, EYE_R)
        case .WIDE: openEye(g, EYE_L, gaze, blink, wide: true, sparkle: false); openEye(g, EYE_R, gaze, blink, wide: true, sparkle: false)
        case .SPARKLE: openEye(g, EYE_L, gaze, blink, wide: false, sparkle: true); openEye(g, EYE_R, gaze, blink, wide: false, sparkle: true)
        case .OPEN: openEye(g, EYE_L, gaze, blink, wide: false, sparkle: false); openEye(g, EYE_R, gaze, blink, wide: false, sparkle: false)
        }
    }

    private static func closedHappy(_ g: GraphicsContext, _ c: CGPoint) {
        g.stroke(quad(CGPoint(x: c.x - 7.5, y: c.y + 2.5), CGPoint(x: c.x, y: c.y - 7.5), CGPoint(x: c.x + 7.5, y: c.y + 2.5)),
                 with: .color(lineC), style: round(3))
    }

    private static func openEye(_ g: GraphicsContext, _ c: CGPoint, _ gaze: CGPoint, _ blink: CGFloat, wide: Bool, sparkle: Bool) {
        if blink > 0.82 {
            g.stroke(quad(CGPoint(x: c.x - 7.5, y: c.y + 1), CGPoint(x: c.x, y: c.y + 5.5), CGPoint(x: c.x + 7.5, y: c.y + 1)),
                     with: .color(lineC), style: round(2.6))
            return
        }
        let rx: CGFloat = wide ? 9.8 : 8.6, ry: CGFloat = wide ? 11.6 : 10.2, ir: CGFloat = wide ? 6.1 : 6.2
        var e = g
        scale(&e, 1, max(1 - blink, 0.1), c)
        e.fill(oval(c.x - rx - 1, c.y - ry - 1, 2 * rx + 2, 2 * ry + 2), with: .color(lineC.opacity(0.7)))
        let sclera = CGRect(x: c.x - rx, y: c.y - ry, width: 2 * rx, height: 2 * ry)
        e.fill(oval(sclera), with: vertical([(0, white), (1, scleraShade)], sclera.minY, sclera.maxY))
        e.clip(to: oval(sclera))
        let ic = CGPoint(x: c.x + gaze.x * (rx - ir - 0.4), y: c.y + gaze.y * (ry - ir - 0.6) + 0.8)
        e.fill(circle(ic, ir), with: radial([(0, irisLight), (0.5, iris), (1, irisDark)], CGPoint(x: ic.x, y: ic.y + 2.4), ir + 1))
        e.fill(circle(ic, ir * 0.5), with: .color(pupil))
        e.fill(Path(sclera), with: vertical([(0, Color.black.opacity(0.2)), (0.3, Color.black.opacity(0))], sclera.minY, sclera.maxY))
        if sparkle {
            star(e, CGPoint(x: ic.x - 2.1, y: ic.y - 2.4), 3.6, white)
            e.fill(circle(CGPoint(x: ic.x + 2.4, y: ic.y + 2.2), 1.1), with: .color(white.opacity(0.9)))
        } else {
            e.fill(circle(CGPoint(x: ic.x - 2.3, y: ic.y - 2.6), 2.5), with: .color(white))
            e.fill(circle(CGPoint(x: ic.x + 2.3, y: ic.y + 2.1), 1.1), with: .color(white.opacity(0.85)))
        }
    }

    private static func brows(_ g: GraphicsContext, _ b: MascotBrows) {
        let v = b.v
        brow(g, EYE_L, side: -1, lift: v.ll, tilt: v.lt)
        brow(g, EYE_R, side: 1, lift: v.rl, tilt: v.rt)
    }

    private static func brow(_ g: GraphicsContext, _ eye: CGPoint, side: CGFloat, lift: CGFloat, tilt: CGFloat) {
        let y = eye.y - 15.5 + lift
        let outer = CGPoint(x: eye.x + side * 6.2, y: y + 1.2 - tilt)
        let inner = CGPoint(x: eye.x - side * 6.2, y: y + 1.2 + tilt * 0.35)
        g.stroke(quad(outer, CGPoint(x: eye.x, y: y - 2.6), inner), with: .color(browC), style: round(2.6))
    }

    private static func mouth(_ g: GraphicsContext, _ m: MascotMouth, _ open: Bool) {
        if open {
            let r = m == .O ? CGRect(x: 53.6, y: 74.6, width: 12.8, height: 13.8) : CGRect(x: 53.2, y: 76.4, width: 13.6, height: 10.2)
            filledMouth(g, oval(r), tongue: CGRect(x: r.minX + 2, y: r.midY + 1.4, width: r.width - 4, height: r.maxY + 2 - (r.midY + 1.4)))
            return
        }
        switch m {
        case .SMILE:
            var p = Path(); p.move(to: CGPoint(x: 51.5, y: 78))
            p.addQuadCurve(to: CGPoint(x: 68.5, y: 78), control: CGPoint(x: 60, y: 88.5))
            p.addQuadCurve(to: CGPoint(x: 51.5, y: 78), control: CGPoint(x: 60, y: 81.4)); p.closeSubpath()
            filledMouth(g, p, tongue: CGRect(x: 55, y: 81.6, width: 10, height: 6.4))
        case .GRIN:
            var p = Path(); p.move(to: CGPoint(x: 48, y: 76.5))
            p.addQuadCurve(to: CGPoint(x: 72, y: 76.5), control: CGPoint(x: 60, y: 94))
            p.addQuadCurve(to: CGPoint(x: 48, y: 76.5), control: CGPoint(x: 60, y: 79.8)); p.closeSubpath()
            filledMouth(g, p, tongue: CGRect(x: 53, y: 83, width: 14, height: 8), teeth: true)
        case .O:
            filledMouth(g, oval(CGRect(x: 55.2, y: 76.2, width: 9.6, height: 11.2)), tongue: CGRect(x: 55.5, y: 82, width: 9, height: 6.5))
        case .SMALL:
            g.stroke(quad(CGPoint(x: 54.5, y: 81.5), CGPoint(x: 60, y: 83), CGPoint(x: 66, y: 79.6)), with: .color(mouthC), style: round(2.5))
        case .SMIRK:
            g.stroke(quad(CGPoint(x: 52.5, y: 79.5), CGPoint(x: 61, y: 86.5), CGPoint(x: 68.5, y: 77.2)), with: .color(mouthC), style: round(2.6))
            g.stroke(quad(CGPoint(x: 67.4, y: 75.6), CGPoint(x: 69.6, y: 76.6), CGPoint(x: 70.2, y: 78.8)), with: .color(mouthC.opacity(0.8)), style: round(1.6))
        }
    }

    private static func filledMouth(_ g: GraphicsContext, _ path: Path, tongue: CGRect, teeth: Bool = false) {
        g.fill(path, with: .color(mouthC))
        var m = g
        m.clip(to: path)
        m.fill(oval(tongue), with: .color(Self.tongue))
        if teeth { m.fill(Path(CGRect(x: 46, y: 75, width: 28, height: 4.4)), with: .color(white)) }
        m.fill(Path(CGRect(x: 44, y: 74, width: 32, height: 20)), with: vertical([(0, Color.black.opacity(0.25)), (0.5, Color.black.opacity(0))], 74, 92))
    }

    // ---- kollar ve eller

    private enum HandExtra { case NONE, POINT, THUMB, BRUSH, CHIN }
    private struct Arm { let hand: CGPoint; let bend: CGFloat; var extra: HandExtra = .NONE; var dir = CGPoint(x: 0, y: -1) }

    private static let SHOULDER_L = CGPoint(x: 30, y: 77)
    private static let SHOULDER_R = CGPoint(x: 90, y: 77)
    private static let REST_L = Arm(hand: CGPoint(x: 20, y: 95), bend: -3)
    private static let REST_R = Arm(hand: CGPoint(x: 100, y: 95), bend: 3)

    private static func armsOf(_ g: MascotGesture) -> (Arm, Arm) {
        switch g {
        case .REST: return (REST_L, REST_R)
        case .WAVE: return (REST_L, Arm(hand: CGPoint(x: 105, y: 47), bend: -7))
        case .POINT: return (REST_L, Arm(hand: CGPoint(x: 103, y: 46), bend: -6, extra: .POINT, dir: CGPoint(x: -0.25, y: -1)))
        case .THUMBS_UP: return (REST_L, Arm(hand: CGPoint(x: 103, y: 68), bend: 4, extra: .THUMB))
        case .BRUSH: return (REST_L, Arm(hand: CGPoint(x: 101, y: 74), bend: 3, extra: .BRUSH))
        case .CHEER: return (Arm(hand: CGPoint(x: 14, y: 44), bend: 7), Arm(hand: CGPoint(x: 106, y: 44), bend: -7))
        case .THINK: return (REST_L, Arm(hand: CGPoint(x: 67, y: 94), bend: 9, extra: .CHIN, dir: CGPoint(x: -0.55, y: -1)))
        }
    }

    private static func arms(_ g: GraphicsContext, _ gesture: MascotGesture, _ swing: CGFloat) {
        let (left, right) = armsOf(gesture)
        arm(g, SHOULDER_L, left)
        if gesture == .WAVE {
            var r = g
            rotate(&r, swing * 13, SHOULDER_R)
            arm(r, SHOULDER_R, right)
            for (i, rr) in [CGFloat(10.5), 14.5].enumerated() {
                let rect = CGRect(x: right.hand.x - rr, y: right.hand.y - rr, width: 2 * rr, height: 2 * rr)
                r.stroke(arc(rect, -60, 70), with: .color(sparkle.opacity(0.8 - Double(i) * 0.25)), style: round(1.8))
            }
        } else {
            arm(g, SHOULDER_R, right)
        }
    }

    private static func arm(_ g: GraphicsContext, _ shoulder: CGPoint, _ a: Arm) {
        let mid = CGPoint(x: (shoulder.x + a.hand.x) / 2, y: (shoulder.y + a.hand.y) / 2)
        let dx = a.hand.x - shoulder.x, dy = a.hand.y - shoulder.y
        let len = max(hypot(dx, dy), 0.01)
        let ctrl = CGPoint(x: mid.x - dy / len * a.bend, y: mid.y + dx / len * a.bend)
        let path = quad(shoulder, ctrl, a.hand)
        g.stroke(path, with: .color(bodyDeep.opacity(0.6)), style: round(10))
        g.stroke(path, with: .color(bodyC), style: round(8))
        var l = g
        l.translateBy(x: -0.9, y: -1.3)
        l.stroke(path, with: .color(bodyLight.opacity(0.7)), style: round(2.6))
        if a.extra == .BRUSH { brush(g, a.hand) }
        hand(g, a.hand)
        switch a.extra {
        case .POINT, .CHIN: finger(g, a.hand, a.dir, a.extra == .CHIN ? 8.5 : 11.5)
        case .THUMB:
            finger(g, CGPoint(x: a.hand.x - 1, y: a.hand.y - 3.5), CGPoint(x: 0, y: -1), 9, width: 5)
            for k in 0...2 {
                let y = a.hand.y - 1.5 + CGFloat(k) * 2.6
                line(g, bodyDeep.opacity(0.55), CGPoint(x: a.hand.x + 1.6, y: y), CGPoint(x: a.hand.x + 5.2, y: y + 0.4), 1.1)
            }
        default: break
        }
    }

    private static func hand(_ g: GraphicsContext, _ c: CGPoint) {
        g.fill(circle(c, 7.6), with: .color(bodyDeep.opacity(0.6)))
        g.fill(circle(c, 6.6), with: radial([(0, bodyLight), (0.55, bodyC), (1, bodyShade)], CGPoint(x: c.x - 2.4, y: c.y - 2.6), 9))
        g.fill(circle(CGPoint(x: c.x - 2.6, y: c.y - 2.8), 1.4), with: .color(white.opacity(0.6)))
    }

    private static func finger(_ g: GraphicsContext, _ hand: CGPoint, _ dir: CGPoint, _ length: CGFloat, width: CGFloat = 4.3) {
        let n = max(hypot(dir.x, dir.y), 0.01)
        let u = CGPoint(x: dir.x / n, y: dir.y / n)
        let a = CGPoint(x: hand.x + u.x * 3.5, y: hand.y + u.y * 3.5)
        let b = CGPoint(x: hand.x + u.x * length, y: hand.y + u.y * length)
        line(g, bodyDeep.opacity(0.6), a, b, width + 1.8)
        line(g, bodyC, a, b, width)
        line(g, bodyLight.opacity(0.75), CGPoint(x: a.x - 0.7, y: a.y - 0.4), CGPoint(x: b.x - 0.7, y: b.y - 0.4), width * 0.35)
    }

    private static func brush(_ g: GraphicsContext, _ hand: CGPoint) {
        func o(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: hand.x + x, y: hand.y + y) }
        let bottom = o(-7, 15), ferrule = o(7.6, -19), tipStart = o(9.6, -24), tip = o(12.6, -33)
        line(g, lineC.opacity(0.5), bottom, ferrule, 5.6)
        line(g, wood, bottom, ferrule, 4)
        line(g, woodLight, CGPoint(x: bottom.x - 0.8, y: bottom.y - 0.3), CGPoint(x: ferrule.x - 0.8, y: ferrule.y - 0.3), 1.2)
        line(g, metal, ferrule, tipStart, 4.8, cap: .butt)
        line(g, white.opacity(0.7), CGPoint(x: ferrule.x - 1, y: ferrule.y), CGPoint(x: tipStart.x - 1, y: tipStart.y), 1.1, cap: .butt)
        let dx = tip.x - tipStart.x, dy = tip.y - tipStart.y, n = hypot(dx, dy)
        let side = CGPoint(x: -dy / n * 2.6, y: dx / n * 2.6)
        var p = Path()
        p.move(to: CGPoint(x: tipStart.x + side.x, y: tipStart.y + side.y))
        p.addQuadCurve(to: tip, control: CGPoint(x: tip.x + side.x * 0.9, y: tip.y + side.y * 0.4 + 3))
        p.addQuadCurve(to: CGPoint(x: tipStart.x - side.x, y: tipStart.y - side.y), control: CGPoint(x: tip.x - side.x * 0.9, y: tip.y - side.y * 0.4 + 3))
        p.closeSubpath()
        g.fill(p, with: .linearGradient(Gradient(colors: [c(0xFF93C5FD), paint, c(0xFF1D4ED8)]), startPoint: tipStart, endPoint: tip))
        g.fill(circle(CGPoint(x: tip.x + 2.4, y: tip.y + 6.5), 1.9), with: .color(paint))
        g.fill(circle(CGPoint(x: tip.x + 1.9, y: tip.y + 5.9), 0.6), with: .color(white.opacity(0.7)))
    }

    private static func sparkles(_ g: GraphicsContext) {
        star(g, CGPoint(x: 14, y: 28), 5, sparkle)
        star(g, CGPoint(x: 104, y: 24), 4, sparkle)
        star(g, CGPoint(x: 20, y: 60), 2.8, sparkle.opacity(0.8))
    }

    /// Dört köşeli parıltı yıldızı.
    static func star(_ g: GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ color: Color) {
        var p = Path()
        for i in 0..<8 {
            let a = Double.pi / 4 * Double(i) - Double.pi / 2
            let rr = i % 2 == 0 ? r : r * 0.32
            let pt = CGPoint(x: c.x + CGFloat(cos(a)) * rr, y: c.y + CGFloat(sin(a)) * rr)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath()
        g.fill(p, with: .color(color))
    }
}
