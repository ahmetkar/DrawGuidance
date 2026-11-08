import Foundation



/**
 * v37 bir karakteri çizen bağlam: iskeleti kurar, katmanları sırayla çizer (arka efektler, pelerin ve uzun saç, arkadaki
 * kol, bacaklar, gövde, öndeki kol, baş, başın önündeki kol, ön efektler). Baş ve yüz CharacterHead.kt'de.
 */
/// Dış hat kalınlığı (görünen).
let OUT: Float = 1.6
/// Giysi ve iç parça kenarı.
let MID: Float = 1.2
/// Ayrıntı çizgisi.
let FINE: Float = 0.8

final class Fig {
    let b: VectorBuilder
    let look: Look
    let pose: PoseSpec
    let face: FaceSpec
    let body: Body
    let sk: Skel
    let ink = CK.INK
    let outfit: Outfit
    let robot: Bool
    let T: Float
    let R: Float
    /** Pozun yuvasında karakterin eşyası (yoksa null). */
    let prop: Prop?
    /// Açık yol (path { … } bloğunun içindeyken).
    private var pb: PathBuilder?

    init(_ b: VectorBuilder, _ look: Look, _ pose: PoseSpec, _ face: FaceSpec) {
        self.b = b
        self.look = look
        self.pose = pose
        self.face = face
        body = look.body
        sk = Skel(look.body, pose)
        outfit = look.outfit
        robot = look.outfit == .MECHA
        T = look.body.torsoLen
        R = look.body.headR
        prop = pose.prop.flatMap { look.props[$0] }
    }

    func draw() {
        groundShadow()
        b.group(rotate: pose.tilt, px: sk.pelvis.x, py: sk.pelvis.y) {
            backFx()
            backLayer()
            if pose.seat { stool() }
            arms(.BACK)
            legs()
            lowerGarment()
            neck()
            torso()
            if pose.crossed { crossedArms() } else { arms(.MID) }
            headPart()
            arms(.TOP)
            frontFx()
        }
        groundFx()
    }

    // ------------------------------------------------------------------ VectorBuilder kısayolları (Kotlin alıcılı lambdalar)

    @discardableResult func poly(_ xy: Float..., closed: Bool = false) -> VectorBuilder.Part { b.poly(xy, closed: closed) }
    @discardableResult func poly(_ xy: [Float], closed: Bool = false) -> VectorBuilder.Part { b.poly(xy, closed: closed) }
    @discardableResult func points(_ list: [P], closed: Bool = false) -> VectorBuilder.Part { b.points(list, closed: closed) }
    @discardableResult func line(_ x1: Float, _ y1: Float, _ x2: Float, _ y2: Float) -> VectorBuilder.Part { b.line(x1, y1, x2, y2) }
    @discardableResult func rect(_ x: Float, _ y: Float, _ w: Float, _ h: Float) -> VectorBuilder.Part { b.rect(x, y, w, h) }
    @discardableResult func roundRect(_ x: Float, _ y: Float, _ w: Float, _ h: Float, _ r: Float) -> VectorBuilder.Part { b.roundRect(x, y, w, h, r) }
    @discardableResult func ellipse(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ rotate: Float = 0) -> VectorBuilder.Part { b.ellipse(cx, cy, rx, ry, rotate: rotate) }
    @discardableResult func circle(_ cx: Float, _ cy: Float, _ r: Float) -> VectorBuilder.Part { b.circle(cx, cy, r) }
    @discardableResult func arc(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ start: Float, _ sweep: Float, pie: Bool = false, closed: Bool = false) -> VectorBuilder.Part {
        b.arc(cx, cy, rx, ry, start, sweep, pie: pie, closed: closed)
    }
    @discardableResult func star(_ cx: Float, _ cy: Float, _ outer: Float, _ inner: Float, _ points: Int = 5, rotate: Float = 0) -> VectorBuilder.Part {
        b.star(cx, cy, outer, inner, points, rotate: rotate)
    }
    @discardableResult func smooth(_ xy: Float..., closed: Bool = false, tension: Float = 0.5) -> VectorBuilder.Part { b.smooth(xy, closed: closed, tension: tension) }
    @discardableResult func smooth(_ xy: [Float], closed: Bool = false, tension: Float = 0.5) -> VectorBuilder.Part { b.smooth(xy, closed: closed, tension: tension) }
    @discardableResult func path(closed: Bool = false, _ block: () -> Void) -> VectorBuilder.Part {
        b.path(closed: closed) { p in
            let saved = pb
            pb = p
            block()
            pb = saved
        }
    }
    func moveTo(_ x: Float, _ y: Float) { pb!.moveTo(x, y) }
    func lineTo(_ x: Float, _ y: Float) { pb!.lineTo(x, y) }
    func quadTo(_ cx: Float, _ cy: Float, _ x: Float, _ y: Float) { pb!.quadTo(cx, cy, x, y) }
    func cubicTo(_ c1x: Float, _ c1y: Float, _ c2x: Float, _ c2y: Float, _ x: Float, _ y: Float) { pb!.cubicTo(c1x, c1y, c2x, c2y, x, y) }
    func arcTo(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, _ start: Float, _ sweep: Float) { pb!.arcTo(cx, cy, rx, ry, start, sweep) }
    func close() { pb!.close() }

    // ------------------------------------------------------------------ temel biçimler

    @discardableResult func fill(_ color: String, _ w: Float = MID, _ shape: () -> VectorBuilder.Part) -> VectorBuilder.Part { shape().fill(color).stroke(ink).width(w) }
    @discardableResult func spot(_ color: String, _ shape: () -> VectorBuilder.Part) -> VectorBuilder.Part { shape().fill(color).noStroke() }
    @discardableResult func line(_ color: String = CK.INK, _ w: Float = FINE, _ shape: () -> VectorBuilder.Part) -> VectorBuilder.Part { shape().fill("@none").stroke(color).width(w) }

    /** Birleşik siluet: önce kalın kenarlı, sonra kenarsız dolgu (parçalar arasında iç çizgi kalmaz). */
    func union(_ color: String, _ w: Float = OUT, _ shapes: [() -> VectorBuilder.Part]) {
        for s in shapes { s().fill(color).stroke(ink).width(w * 2.0) }
        for s in shapes { s().fill(color).noStroke() }
    }

    /** Uçları yuvarlak, [r1]'den [r2]'ye incelen kapsül. */
    @discardableResult func capsule(_ a: P, _ c: P, _ r1: Float) -> VectorBuilder.Part { capsule(a, c, r1, r1) }
    @discardableResult func capsule(_ a: P, _ c: P, _ r1: Float, _ r2: Float) -> VectorBuilder.Part {
        let dx = c.x - a.x; let dy = c.y - a.y
        let len = kHypot(dx, dy)
        if len < 0.01 { return b.circle(a.x, a.y, max(r1, r2)) }
        let ux = dx / len; let uy = dy / len
        let ang = toDegrees(kAtan2(dy, dx).toDouble()).toFloat()
        return path(closed: true) {
            moveTo(a.x - uy * r1, a.y + ux * r1)
            lineTo(c.x - uy * r2, c.y + ux * r2)
            arcTo(c.x, c.y, r2, r2, ang + 90.0, -180.0)
            lineTo(a.x + uy * r1, a.y - ux * r1)
            arcTo(a.x, a.y, r1, r1, ang - 90.0, -180.0)
        }
    }

    /**
     * İnceltilmiş mürekkep çizgisi (dolgulu şerit): [pts] üzerinden geçen yumuşak eğri; kalınlık başta [w0], ortada
     * [w1], sonda [w2] (bulunulan çerçevenin biriminde).
     */
    @discardableResult func taper(_ pts: [P], _ w0: Float, _ w1: Float, _ w2: Float, _ color: String = CK.INK, _ alpha: Float = 1.0) -> VectorBuilder.Part {
        let c = pts.count >= 3 ? VectorBuilder.catmullRom(pts, false, 0.5, steps: 8) : pts
        let n = c.count
        var lens = [Float](repeating: 0, count: n)
        for i in 1..<n { lens[i] = lens[i - 1] + dist(c[i - 1], c[i]) }
        let total = max(1e-4, lens[n - 1])
        var left = [P](); var right = [P]()
        left.reserveCapacity(n); right.reserveCapacity(n)
        for i in 0..<n {
            let t = lens[i] / total
            let w = t < 0.5 ? w0 + (w1 - w0) * (t * 2.0) : w1 + (w2 - w1) * ((t - 0.5) * 2.0)
            let a = c[max(0, i - 1)]; let z = c[min(n - 1, i + 1)]
            let dl = max(1e-5, dist(a, z))
            let nx = -(z.y - a.y) / dl; let ny = (z.x - a.x) / dl
            left.append(P(c[i].x + nx * w / 2.0, c[i].y + ny * w / 2.0))
            right.append(P(c[i].x - nx * w / 2.0, c[i].y - ny * w / 2.0))
        }
        return b.points(left + right.reversed(), closed: true).fill(color).noStroke().alpha(alpha)
    }

    @discardableResult func taper(_ pts: P..., w0: Float, w1: Float, w2: Float, color: String = CK.INK, alpha: Float = 1.0) -> VectorBuilder.Part {
        taper(pts, w0, w1, w2, color, alpha)
    }

    /** Yerel çerçeve: ([o]) noktasında, [deg] dönük, [s] ölçekli. */
    func frame(_ o: P, _ deg: Float, _ s: Float = 1.0, _ block: () -> Void) {
        b.group(dx: o.x, dy: o.y, sx: s, sy: s, rotate: deg, px: 0.0, py: 0.0, block)
    }

    /** Gövde çerçevesi: başlangıç leğen, y yukarı eksi, gövde eğimiyle dönük. */
    func torsoFrame(_ block: () -> Void) { frame(sk.pelvis, pose.lean, 1.0, block) }

    /** Kalça çerçevesi (gövdeden daha az eğik). */
    func hipFrame(_ block: () -> Void) { frame(sk.pelvis, pose.lean * 0.3, 1.0, block) }

    /** Kapsülün cel gölgesi (sağ/alt yanda, kapsülün içinde kalır). */
    func celSeg(_ a: P, _ c: P, _ ra: Float, _ rc: Float, _ color: String, alpha: Float = 1.0) {
        let dx = c.x - a.x; let dy = c.y - a.y
        let len = kHypot(dx, dy)
        if len < 0.5 { return }
        var nx = -dy / len; var ny = dx / len
        if nx < -0.15 || (abs(nx) <= 0.15 && ny < 0.0) { nx = -nx; ny = -ny }
        capsule(P(a.x + nx * ra * 0.45, a.y + ny * ra * 0.45), P(c.x + nx * rc * 0.45, c.y + ny * rc * 0.45), ra * 0.5, rc * 0.5)
            .fill(color).noStroke().alpha(alpha)
    }

    /** İki parçalı uzuv (omuz-dirsek-el ya da kalça-diz-ayak): birleşim yerinde iç çizgi yok; cel gölgeli. */
    func limb(_ a: P, _ m: P, _ c: P, _ r0: Float, _ r1: Float, _ r2: Float, _ color: String, outline: Float = OUT, shadow: Bool = true) {
        capsule(a, m, r0, r1).fill(color).stroke(ink).width(outline * 2.0)
        capsule(m, c, r1, r2).fill(color).stroke(ink).width(outline * 2.0)
        capsule(a, m, r0, r1).fill(color).noStroke()
        capsule(m, c, r1, r2).fill(color).noStroke()
        if shadow {
            let sh = (color == look.skin ? CK.skinShade(color) : CK.shade(color))
            celSeg(a, m, r0, r1, sh)
            celSeg(m, c, r1, r2, sh)
        }
    }

    func along(_ a: P, _ c: P, _ t: Float) -> P { lerpP(a, c, t) }

    // ------------------------------------------------------------------ zemin ve tabure

    func groundShadow() {
        let cx = (sk.ankleL.x + sk.ankleR.x) / 2.0
        let wide = abs(sk.ankleL.x - sk.ankleR.x) / 2.0 + 14.0
        let air = pose.lift > 2.0
        b.ellipse(cx, 197.5, (air ? wide * 0.7 : wide), (air ? 2.6 : 3.8)).fill(ink).noStroke().alpha((air ? 0.12 : 0.2))
    }

    func stool() {
        // Tabure: leğenin altında, ayakları zemine iner
        let wood = "#B07A4A"
        let top = sk.pelvis.y + body.legR0 - 1.0
        let cx = sk.pelvis.x + 2.0
        let w = body.hipW * 2.4
        fill(CK.shade(wood)) { roundRect(cx - w * 0.38, 178.0, w * 0.76, 5.0, 2.0) }
        fill(wood) { poly(cx - w / 2.0 + 3.0, top + 6.0, cx - w / 2.0 + 9.0, top + 6.0, cx - w / 2.0 + 8.0, 196.0, cx - w / 2.0 + 1.0, 196.0, closed: true) }
        fill(wood) { poly(cx + w / 2.0 - 9.0, top + 6.0, cx + w / 2.0 - 3.0, top + 6.0, cx + w / 2.0 - 1.0, 196.0, cx + w / 2.0 - 8.0, 196.0, closed: true) }
        fill(wood, OUT) { roundRect(cx - w / 2.0, top, w, 9.0, 3.5) }
        spot(CK.light(wood)) { roundRect(cx - w / 2.0 + 4.0, top + 2.0, w * 0.55, 2.2, 1.1) }.alpha(0.7)
    }

    // ------------------------------------------------------------------ arka katman (pelerin, sırt çantası, uzun saç)

    func backLayer() {
        if look.extras.contains(Extra.CAPE) { cape() }
        if look.extras.contains(Extra.BACKPACK) { torsoFrame {
            let c = (outfit == Outfit.SPACESUIT ? "#C9D1DA" : "#E07B39")
            let w = body.shoulderOut * sk.widthK + 3.0
            fill(c, OUT) { roundRect(-w, -T - 2.0, w * 2.0, T * 0.85, 7.0) }
            fill(CK.shade(c)) { roundRect(-w + 2.0, -T * 0.4, 5.0, T * 0.32, 2.0) }
            fill(CK.shade(c)) { roundRect(w - 7.0, -T * 0.4, 5.0, T * 0.32, 2.0) }
        } }
        hairBehindBody()
    }

    func cape() {
        // Pelerin: omuzlardan aşağı; harekette rüzgârla yana ve yukarı savrulur
        let moving = pose.fx.contains(PoseFx.SPEED) || pose.fx.contains(PoseFx.WIND) || pose.fx.contains(PoseFx.SWISH_ARM) || pose.lift > 10.0
        let sL = sk.shoulderL; let sR = sk.shoulderR
        let low = max(sk.ankleL.y, sk.ankleR.y) - 22.0
        let drift = (moving ? -26.0 - abs(pose.lean) * 0.6 : 0.0)
        let rise: Float = (pose.fx.contains(PoseFx.WIND) ? -40.0 : (moving ? -14.0 : 0.0))
        let c = look.accent
        let bl = P(sL.x - 16.0 + drift, low + rise); let br = P(sR.x + 16.0 + drift * 0.6, low + rise * 0.5)
        fill(c, OUT) {
            path(closed: true) {
                moveTo(sL.x + 2.0, sL.y - 4.0)
                cubicTo(sL.x - 10.0, sL.y + 30.0, bl.x + 8.0, bl.y - 40.0, bl.x, bl.y)
                quadTo(bl.x + 8.0, bl.y + 6.0, (bl.x + br.x) / 2.0 - 4.0, (bl.y + br.y) / 2.0 + 2.0)
                quadTo((bl.x + br.x) / 2.0 + 10.0, (bl.y + br.y) / 2.0 + 8.0, br.x, br.y)
                cubicTo(br.x - 6.0, br.y - 40.0, sR.x + 10.0, sR.y + 30.0, sR.x - 2.0, sR.y - 4.0)
            }
        }
        // İç yüz gölgesi ve kıvrım çizgileri
        spot(CK.shade(c)) {
            path(closed: true) {
                moveTo(sR.x - 2.0, sR.y + 2.0)
                cubicTo(sR.x + 8.0, sR.y + 30.0, br.x - 8.0, br.y - 30.0, br.x - 2.0, br.y - 2.0)
                quadTo(br.x - 10.0, br.y - 4.0, (bl.x + br.x) / 2.0 + 6.0, (bl.y + br.y) / 2.0)
                cubicTo((bl.x + br.x) / 2.0 + 2.0, sR.y + 40.0, sR.x - 4.0, sR.y + 20.0, sR.x - 2.0, sR.y + 2.0)
            }
        }
        taper(P(sL.x + 4.0, sL.y + 20.0), P(bl.x + 14.0, (sL.y + bl.y) / 2.0 + 10.0), P(bl.x + 12.0, bl.y - 6.0), w0: 0.2, w1: 1.2, w2: 0.2, color: CK.deep(c))
    }

    // ------------------------------------------------------------------ bacaklar

    func legColor() -> String { switch look.legwear {
case Legwear.PANTS, Legwear.ROBE: look.second
case Legwear.TIGHTS: look.main
case Legwear.SHORTS, Legwear.SKIRT, Legwear.PENCIL: look.skin
case Legwear.MECHA: "#5B6573"
} }

    func legs() {
        // Uzak bacak önce: dönükken yüzün baktığı yandaki bacak arkada kalır
        let order = (pose.turn > 0.2 ? [false, true] : [true, false])
        for left in order { leg(left) }
    }

    func leg(_ left: Bool) {
        let h = (left ? sk.hipL : sk.hipR)
        let k = (left ? sk.kneeL : sk.kneeR)
        let a = (left ? sk.ankleL : sk.ankleR)
        let color = legColor()
        let bulk: Float = switch look.legwear {
case Legwear.PANTS, Legwear.ROBE: 0.9
case Legwear.TIGHTS: 0.2
default: 0.0
}
        if robot {
            // Robot bacağı: koyu eklemli iç, açık zırh plakaları
            limb(h, k, a, body.legR0 * 0.8, body.legR1 * 0.7, body.legR2 * 0.7, "#5B6573")
            capsule(along(h, k, 0.12), along(h, k, 0.82), body.legR0 * 1.05, body.legR1 * 0.95).fill("#D5DCE4").stroke(ink).width(OUT)
            celSeg(along(h, k, 0.12), along(h, k, 0.82), body.legR0 * 1.05, body.legR1 * 0.95, CK.shade("#D5DCE4"))
            capsule(along(k, a, 0.18), along(k, a, 0.9), body.legR1 * 1.1, body.legR2 * 1.05).fill("#D5DCE4").stroke(ink).width(OUT)
            celSeg(along(k, a, 0.18), along(k, a, 0.9), body.legR1 * 1.1, body.legR2 * 1.05, CK.shade("#D5DCE4"))
            fill("#F28A2E") { circle(k.x, k.y, body.legR1 * 0.75) }
            line(CK.STEEL_D, FINE) { line(along(k, a, 0.45).x - 3.0, along(k, a, 0.45).y, along(k, a, 0.45).x + 3.0, along(k, a, 0.45).y) }
        } else {
            limb(h, k, a, body.legR0 + bulk, body.legR1 + bulk, body.legR2 + bulk * 0.8, color)
        }
        switch look.legwear {
case Legwear.SHORTS: 
let e = along(h, k, 0.55)
                capsule(h, e, body.legR0 + 2.2, body.legR0 + 1.6).fill(look.second).stroke(ink).width(MID)
                celSeg(h, e, body.legR0 + 2.2, body.legR0 + 1.6, CK.shade(look.second))
                if outfit == Outfit.JERSEY { capsule(along(h, e, 0.3), along(h, e, 0.98), 1.2).fill(look.accent).noStroke() }
                // Çorap
                capsule(along(k, a, 0.55), a, body.legR2 + 0.9, body.legR2 + 0.8).fill(CK.WHITE).stroke(ink).width(MID)
                if outfit == Outfit.JERSEY { capsule(along(k, a, 0.6), along(k, a, 0.68), body.legR2 + 1.0).fill(look.accent).noStroke() }
case Legwear.SKIRT: 
// Diz çorabı
                capsule(along(k, a, 0.08), a, body.legR1 + 0.5, body.legR2 + 0.7).fill("#2A2A3A").stroke(ink).width(MID)
                celSeg(along(k, a, 0.08), a, body.legR1 + 0.5, body.legR2 + 0.7, CK.deep("#2A2A3A"))
default: break
}
        if outfit == Outfit.ARMOR {
            // Baldır zırhı ve dizlik
            capsule(along(k, a, 0.12), along(k, a, 0.86), body.legR1 + 1.6, body.legR2 + 1.8).fill(CK.STEEL).stroke(ink).width(MID)
            celSeg(along(k, a, 0.12), along(k, a, 0.86), body.legR1 + 1.6, body.legR2 + 1.8, CK.shade(CK.STEEL))
            fill(CK.light(CK.STEEL)) { circle(k.x, k.y, body.legR1 + 1.6) }
            spot(CK.WHITE) { circle(k.x - 1.2, k.y - 1.4, 1.3) }.alpha(0.8)
        }
        boot(k, a, left)
        shoe(a, k, left)
    }

    /** Çizme konçu (kahraman, şövalye, astronot, polis). */
    func boot(_ k: P, _ a: P, _ left: Bool) {
        let top: Float
        switch look.shoe {
        case .BOOT: top = 0.62
        case .HERO_BOOT: top = 0.12
        case .SPACE: top = 0.5
        default: return
        }
        let color = look.shoeColor
        let s = along(k, a, top)
        capsule(s, a, body.legR1 + 1.2, body.legR2 + 1.5).fill(color).stroke(ink).width(MID)
        celSeg(s, a, body.legR1 + 1.2, body.legR2 + 1.5, CK.shade(color))
        if look.shoe == Shoe.HERO_BOOT {
            // Konç ağzı: V kesik
            let d = a - k; let len: Float = max(0.01, kHypot(d.x, d.y)); let n = P(-d.y / len, d.x / len)
            let r = body.legR1 + 2.4
            fill(color) { poly(s.x + n.x * r, s.y + n.y * r, s.x - n.x * r, s.y - n.y * r, along(k, a, 0.32).x, along(k, a, 0.32).y, closed: true) }
        }
        if look.shoe == Shoe.SPACE { capsule(along(k, a, top + 0.02), along(k, a, top + 0.12), body.legR1 + 1.6).fill("#F28A2E").stroke(ink).width(FINE) }
    }

    func shoe(_ a: P, _ k: P, _ left: Bool) {
        let planted = a.y + body.legR2 + 2.4 >= sk.ground - 2.5
        let shinA = angleOf(a - k)
        let footA = ((left ? pose.footL : pose.footR)) ?? (planted ? 0.0 : shinA * 0.8)
        // Ayak ucu: dönükken baktığı yana, değilse dışa
        let toe: Float = switch true {
case _ where pose.turn > 0.25: 1.0
case _ where pose.turn < -0.25: -1.0
default: if left { -1.0 } else { 1.0 }
}
        let side = abs(pose.turn) > 0.25 || !planted
        let fl = body.footLen * ((side ? 1.0 : 0.78))
        let r2 = body.legR2 + (look.shoe == Shoe.SNEAKER || look.shoe == Shoe.SHOE || look.shoe == Shoe.LOAFER ? 0.6 : 1.4)
        let sole = body.legR2 + 2.4
        let color = look.shoeColor
        frame(a, -footA) {
            func x(_ v: Float) -> Float { v * toe }
            let pts = [
    P(x(-r2 * 1.02), -r2 * 0.4),
    P(x(r2 * 0.95), -r2 * 0.55),
    P(x(fl * 0.42), sole * 0.15),
    P(x(fl * 0.72), sole * 0.5),
    P(x(fl * 0.8), sole * 0.92),
    P(x(fl * 0.5), sole),
    P(x(-fl * 0.22), sole),
    P(x(-r2 * 1.15), sole * 0.55)
]
            fill(color, OUT) { points(VectorBuilder.catmullRom(pts, true, 0.45, steps: 6), closed: true) }
            switch look.shoe {
case Shoe.SNEAKER: 
spot(CK.WHITE) { poly(x(-fl * 0.24), sole - 2.0, x(fl * 0.68), sole - 2.0, x(fl * 0.55), sole - 0.4, x(-fl * 0.18), sole - 0.4, closed: true) }
                    line(ink, FINE) { line(x(-fl * 0.24), sole - 2.0, x(fl * 0.68), sole - 2.0) }
                    line(CK.WHITE, 0.9) { line(x(r2 * 0.6), -r2 * 0.1, x(fl * 0.2), sole * 0.12) }
case Shoe.MECHA: fill("#F28A2E") { roundRect(x(fl * 0.1) - 2.0, sole * 0.25, 4.0, 3.0, 1.0) }
case Shoe.SABATON: for i in 1...2 { line(CK.STEEL_D, FINE) { line(x(fl * (0.1 + Float(i) * 0.18)), sole * 0.1, x(fl * (0.08 + Float(i) * 0.18)), sole * 0.95) } }
default: spot(CK.light(color)) { ellipse(x(fl * 0.35), sole * 0.35, fl * 0.16, 1.2) }.alpha(0.7)
}
        }
    }

    // ------------------------------------------------------------------ alt giysi (leğen, etek, uzun palto, cüppe)

    func lowerGarment() {
        let w = body.waistW * sk.widthK; let h = body.hipW * sk.widthK
        let r0 = body.legR0
        switch look.legwear {
case Legwear.PANTS, Legwear.SHORTS, Legwear.TIGHTS, Legwear.MECHA: 
hipFrame {
                let c = (robot ? "#5B6573" : (look.legwear == Legwear.TIGHTS ? legColor() : look.second))
                fill(c, OUT) {
                    smooth(-w - 0.5, -T * 0.34, -h, -3.0, -h * 0.98, 2.0 + r0 * 0.55, 0.0, 2.0 + r0 * 0.9, h * 0.98, 2.0 + r0 * 0.55, h, -3.0, w + 0.5, -T * 0.34, closed: true, tension: 0.4)
                }
                if !robot { line(CK.shade(c), FINE) { line(0.0, -T * 0.2, 0.0, 2.0 + r0 * 0.7) } }
                if robot { fill("#D5DCE4") { poly(-w * 0.7, -T * 0.3, w * 0.7, -T * 0.3, w * 0.35, 2.0 + r0 * 0.4, -w * 0.35, 2.0 + r0 * 0.4, closed: true) } }
            }
case Legwear.SKIRT: skirt(pleats: true)
case Legwear.PENCIL: skirt(pleats: false)
case Legwear.ROBE: break
default: break
}
        switch outfit {
case Outfit.LABCOAT, Outfit.TRENCH: coatTails()
case Outfit.ROBE: robeSkirt()
case Outfit.ARMOR: tabard()
default: break
}
        if look.extras.contains(Extra.APRON) { apron() }
    }

    /** Etek: belden dizlere, bacakların açıklığına göre genişler (pileli ya da dar). */
    func skirt(pleats: Bool) {
        let c = look.second
        let w = body.waistW * sk.widthK
        let kl = sk.kneeL; let kr = sk.kneeR
        let p = sk.pelvis
        // Kalça çerçevesinde dizlerin yeri
        func loc(_ q: P) -> P { rotV(q - p, -pose.lean * 0.3) }
        let lk = loc(kl); let rk = loc(kr)
        let len: Float = (pleats ? 0.42 : 0.62)
        let yL: Float = 2 + (lk.y - 2.0) * len; let yR: Float = 2 + (rk.y - 2.0) * len
        let xL = min(-body.hipW, lk.x * len - body.legR0) - ((pleats ? 5.0 : 1.0))
        let xR = max(body.hipW, rk.x * len + body.legR0) + ((pleats ? 5.0 : 1.0))
        hipFrame {
            fill(c, OUT) {
                smooth(-w, -T * 0.36, -body.hipW - 1.0, -4.0, xL, yL, (xL + xR) / 2.0, (yL + yR) / 2.0 + 2.0, xR, yR, body.hipW + 1.0, -4.0, w, -T * 0.36, closed: true, tension: 0.35)
            }
            spot(CK.shade(c)) { poly(xR - 6.0, yR - 1.0, body.hipW * 0.5, -2.0, body.hipW * 0.8, -4.0, xR - 1.0, yR - 2.0, closed: true) }.alpha(0.9)
            if pleats { for i in -2...2 {
                let bx = (xL + xR) / 2.0 + Float(i) * (xR - xL) / 5.4
                line(CK.deep(c), FINE) { line(Float(i) * w * 0.32, -T * 0.2, bx, (yL + yR) / 2.0 + 1.0) }
            } } else { line(CK.deep(c), FINE) { line(xR * 0.4, (yL + yR) / 2.0 - 7.0, xR * 0.42, (yL + yR) / 2.0 + 1.0) } }
            fill(CK.shade(c)) { roundRect(-w - 0.5, -T * 0.38, w * 2.0 + 1.0, 4.0, 1.5) }
        }
    }

    /** Uzun palto/önlük etekleri: belden dizlere, iki parça. */
    func coatTails() {
        let c = look.main
        let w = body.waistW * sk.widthK
        let lowL = P(sk.kneeL.x - body.legR1 - 6.0, sk.kneeL.y + 6.0)
        let lowR = P(sk.kneeR.x + body.legR1 + 6.0, sk.kneeR.y + 6.0)
        let p = sk.pelvis
        let wl = p + rotV(P(-w - 1.0, -T * 0.3), pose.lean * 0.6)
        let wr = p + rotV(P(w + 1.0, -T * 0.3), pose.lean * 0.6)
        let mid = p + rotV(P(0.0, -T * 0.3), pose.lean * 0.6)
        let crotchL = p + P(-1.5, body.legR0 + 6.0); let crotchR = p + P(1.5, body.legR0 + 6.0)
        fill(c, OUT) { smooth(wl.x, wl.y, wl.x - 3.0, p.y + 2.0, lowL.x, lowL.y, (lowL.x + crotchL.x) / 2.0 + 2.0, max(lowL.y, crotchL.y) - 1.0, crotchL.x, crotchL.y, mid.x - 1.0, mid.y, closed: true, tension: 0.3) }
        fill(c, OUT) { smooth(wr.x, wr.y, wr.x + 3.0, p.y + 2.0, lowR.x, lowR.y, (lowR.x + crotchR.x) / 2.0 - 2.0, max(lowR.y, crotchR.y) - 1.0, crotchR.x, crotchR.y, mid.x + 1.0, mid.y, closed: true, tension: 0.3) }
        spot(CK.shade(c)) { smooth(wr.x - 1.0, wr.y + 4.0, lowR.x - 1.0, lowR.y - 3.0, (lowR.x + crotchR.x) / 2.0, lowR.y - 4.0, wr.x - 6.0, wr.y + 10.0, closed: true) }.alpha(0.85)
        line(CK.deep(c), FINE) { line(along(wl, lowL, 0.3).x + 4.0, along(wl, lowL, 0.3).y, along(wl, lowL, 0.9).x + 5.0, along(wl, lowL, 0.9).y - 2.0) }
    }

    /** Cüppenin alt kısmı: belden baldırlara, bacakları örter. */
    func robeSkirt() {
        let c = look.main
        let p = sk.pelvis
        let lowY = max(sk.ankleL.y, sk.ankleR.y) - 12.0
        let l = min(sk.kneeL.x, sk.ankleL.x) - 10.0
        let r = max(sk.kneeR.x, sk.ankleR.x) + 10.0
        let w = body.waistW * sk.widthK
        let wl = p + rotV(P(-w - 1.0, -T * 0.3), pose.lean * 0.5)
        let wr = p + rotV(P(w + 1.0, -T * 0.3), pose.lean * 0.5)
        let yl = min(lowY, sk.ankleL.y - 8.0); let yr = min(lowY, sk.ankleR.y - 8.0)
        fill(c, OUT) {
            path(closed: true) {
                moveTo(wl.x, wl.y)
                cubicTo(wl.x - 6.0, p.y + 10.0, l + 2.0, yl - 30.0, l, yl)
                quadTo((l + r) / 2.0, max(yl, yr) + 7.0, r, yr)
                cubicTo(r - 2.0, yr - 30.0, wr.x + 6.0, p.y + 10.0, wr.x, wr.y)
            }
        }
        spot(CK.shade(c)) { poly((l + r) / 2.0 + 6.0, max(yl, yr) + 3.0, r - 2.0, yr, wr.x + 2.0, p.y + 6.0, wr.x - 4.0, wr.y + 6.0, closed: true) }.alpha(0.9)
        // Ön açıklık ve etek süsü (altın şerit)
        taper(P(p.x + 1.0, wl.y + 4.0), P(p.x + 3.0, p.y + 20.0), P((l + r) / 2.0 + 4.0, max(yl, yr) + 4.0), w0: 2.2, w1: 2.6, w2: 2.6, color: look.trim)
        line(look.trim, 1.6) { path { moveTo(l + 2.0, yl - 3.0); quadTo((l + r) / 2.0, max(yl, yr) + 3.0, r - 2.0, yr - 3.0) } }
        for (fx, fy) in ([(Float(0.3), Float(0.45)), (Float(0.7), Float(0.3)), (Float(0.2), Float(0.75)), (Float(0.78), Float(0.7))] as [(Float, Float)]) {
            let x = l + (r - l) * fx; let y = p.y + (max(yl, yr) - p.y) * fy
            spot(CK.GOLD) { star(x, y, 2.6, 1.1, 5) }
        }
    }

    /** Şövalye: kırmızı armalı önlük (göğüsten uyluk ortasına). */
    func tabard() {
        let c = look.accent
        let p = sk.pelvis
        let k = (sk.kneeL.y + sk.kneeR.y) / 2.0
        let bottom = p.y + (k - p.y) * 0.55
        let w = body.waistW * 0.62
        frame(p, pose.lean * 0.5) {
            fill(c, OUT) { poly(-w, -T * 0.4, w, -T * 0.4, w + 1.0, bottom - p.y - 4.0, 0.0, bottom - p.y + 3.0, -w - 1.0, bottom - p.y - 4.0, closed: true) }
            spot(CK.shade(c)) { poly(w * 0.2, -T * 0.4, w, -T * 0.4, w + 1.0, bottom - p.y - 4.0, w * 0.3, bottom - p.y - 1.0, closed: true) }
            line(look.trim, 1.2) { poly(-w + 1.5, -T * 0.38, -w + 1.5, bottom - p.y - 5.0, 0.0, bottom - p.y + 1.0, w - 1.5, bottom - p.y - 5.0, w - 1.5, -T * 0.38) }
        }
    }

    func apron() {
        let p = sk.pelvis
        let k = (sk.kneeL.y + sk.kneeR.y) / 2.0
        let bottom = p.y + (k - p.y) * 0.85
        let w = body.waistW + 3.0
        frame(p, pose.lean * 0.4) {
            fill("#FBFBF8", OUT) { path(closed: true) { moveTo(-w, -T * 0.32); lineTo(w, -T * 0.32); lineTo(w + 3.0, bottom - p.y - 3.0); quadTo(0.0, bottom - p.y + 3.0, -w - 3.0, bottom - p.y - 3.0) } }
            spot("#E6E2D6") { poly(w * 0.3, -T * 0.3, w, -T * 0.3, w + 3.0, bottom - p.y - 3.0, w * 0.4, bottom - p.y, closed: true) }
            fill(look.accent) { roundRect(-w - 0.5, -T * 0.36, w * 2.0 + 1.0, 3.6, 1.5) }
        }
    }

    // ------------------------------------------------------------------ gövde

    /** Gövde dış hattı (gövde çerçevesinde; [hem] alt kenar, [bulk] giysi kalınlığı). */
    func torsoOutline(_ hem: Float, _ bulk: Float = 0.0) -> [P] {
        let k = sk.widthK
        let s = (body.shoulderOut + bulk) * k; let c = (body.chestW + bulk) * k
        let w = (body.waistW + bulk) * k; let h = (body.hipW + bulk) * k
        let top = -T - 4.5 - bulk * 0.5
        let n = body.neckR + 1.4
        let shift = sk.bodyTurn * 2.0
        let half = [
    P(-n, top),
    P(-s * 0.55, top + 1.8),
    P(-s * 0.88, top + 3.4),
    P(-s, top + 8.0),
    P(-c, -T + 16.0),
    P(-w, -T * 0.36),
    P(-h, hem - 4.0),
    P(-h * 0.97, hem)
]
        let pts = half + half.reversed().map { P(-$0.x, $0.y) }
        return VectorBuilder.catmullRom(pts.map { P($0.x + shift * (-$0.y / T).coerceIn(0.0, 1.0), $0.y) }, true, 0.45, steps: 8)
    }

    /** Gövde hattının [y] yüksekliğindeki sol ve sağ kenarı. */
    func edgesAt(_ outline: [P], _ y: Float) -> (Float, Float) {
        var l = Float.greatestFiniteMagnitude; var r = -Float.greatestFiniteMagnitude
        for i in outline.indices {
            let a = outline[i]; let c = outline[(i + 1) % outline.count]
            if (a.y - y) * (c.y - y) <= 0.0 && a.y != c.y {
                let x = a.x + (c.x - a.x) * (y - a.y) / (c.y - a.y)
                l = min(l, x); r = max(r, x)
            }
        }
        if l > r { return (0.0, 0.0) }
        return (l, r)
    }

    /** Gövde hattına göre yatay şerit (kemer, göğüs bandı): [y0]..[y1], kenarlardan [inset] içeride. */
    func band(_ outline: [P], _ y0: Float, _ y1: Float, _ inset: Float = 0.6) -> [P] {
        let (l0, r0) = edgesAt(outline, y0); let (l1, r1) = edgesAt(outline, y1)
        return [P(l0 + inset, y0), P(r0 - inset, y0), P(r1 - inset, y1), P(l1 + inset, y1)]
    }

    /** Gövdenin sağ yanındaki cel gölge. */
    func torsoShadow(_ outline: [P], _ color: String, _ from: Float, _ to: Float, _ depth: Float) {
        let right = outline.filter { $0.x > 0.0 && (from...to).contains($0.y) }.sorted { $0.y < $1.y }
        if right.count < 2 { return }
        let inner = right.reversed().enumerated().map { (i, p) -> P in
            let t = Float(i) / Float(right.count - 1)
            return P(p.x - depth * (0.7 + 0.3 * kSin(t * Double.pi.toFloat())), p.y)
        }
        spot(color) { points(right + inner, closed: true) }
    }

    func neck() {
        if robot {
            capsule(sk.chest, sk.neckTop, body.neckR * 1.1).fill("#5B6573").stroke(ink).width(OUT)
            for i in 1...2 { line(CK.STEEL_D, FINE) { let q = along(sk.chest, sk.neckTop, Float(i) * 0.3); return line(q.x - 4.0, q.y, q.x + 4.0, q.y) } }
            return
        }
        if look.extras.contains(Extra.SPACE_HELMET) { return }
        let skin = look.skin
        capsule(along(sk.chest, sk.neckTop, -0.1), along(sk.chest, sk.neckTop, 0.9), body.neckR, body.neckR * 0.92).fill(skin).stroke(ink).width(OUT)
    }

    func torso() {
        switch outfit {
case Outfit.ROBE: robeTop()
case Outfit.MECHA: mechaTop()
default: clothTop()
}
    }

    /** Giysili gövde: taban biçim, cel gölge, kıyafete özgü ayrıntılar. */
    func clothTop() {
        let hem: Float = switch outfit {
case Outfit.TEE, Outfit.JERSEY, Outfit.CARDIGAN, Outfit.SUIT, Outfit.LABCOAT, Outfit.TRENCH, Outfit.CHEF: 5.0
case Outfit.SAILOR: -T * 0.22
case Outfit.SPACESUIT: 6.0
case Outfit.ARMOR: -T * 0.1
default: -T * 0.26
}
        let bulk: Float = switch outfit {
case Outfit.SPACESUIT: 3.0
case Outfit.ARMOR: 1.5
case Outfit.TRENCH, Outfit.LABCOAT: 1.0
default: 0.4
}
        let base = switch outfit {
case Outfit.ARMOR: CK.STEEL
default: look.main
}
        let o = torsoOutline(hem, bulk)
        let cx = sk.bodyTurn * body.waistW * 0.3
        torsoFrame {
            fill(base, OUT) { points(o, closed: true) }
            torsoShadow(o, CK.shade(base), -T + 12.0, hem - 1.0, body.waistW * 0.42)
            topDetails(o, hem, cx)
            if body.muscle > 0.3 && outfit == Outfit.HEROSUIT { muscles(cx) }
            if body.female && body.bust > 0.0 && outfit != Outfit.SPACESUIT && outfit != Outfit.LABCOAT {
                let y = -T * 0.62
                line(CK.deep(base), FINE) { arc(cx - body.chestW * 0.42, y, body.chestW * 0.34, 4.0 * body.bust + 1.5, 20.0, 120.0) }
                line(CK.deep(base), FINE) { arc(cx + body.chestW * 0.42, y, body.chestW * 0.34, 4.0 * body.bust + 1.5, 40.0, 120.0) }
            }
        }
    }

    /** Kahraman kas çizgileri: göğüs, karın. */
    func muscles(_ cx: Float) {
        let c = CK.deep(look.main)
        let y = -T * 0.62
        taper(P(cx - body.chestW * 0.75, y - 6.0), P(cx - body.chestW * 0.4, y + 2.0), P(cx - 1.0, y - 1.0), w0: 0.3, w1: 1.3, w2: 0.3, color: c)
        taper(P(cx + 1.0, y - 1.0), P(cx + body.chestW * 0.4, y + 2.0), P(cx + body.chestW * 0.75, y - 6.0), w0: 0.3, w1: 1.3, w2: 0.3, color: c)
        for i in 0...2 {
            let yy = -T * 0.42 + Float(i) * T * 0.1
            taper(P(cx - 6.0, yy), P(cx - 3.0, yy + 1.2), P(cx - 0.8, yy), w0: 0.2, w1: 0.9, w2: 0.2, color: c, alpha: 0.8)
            taper(P(cx + 0.8, yy), P(cx + 3.0, yy + 1.2), P(cx + 6.0, yy), w0: 0.2, w1: 0.9, w2: 0.2, color: c, alpha: 0.8)
        }
        taper(P(cx, -T * 0.48), P(cx, -T * 0.2), P(cx, -T * 0.08), w0: 0.2, w1: 0.8, w2: 0.1, color: c, alpha: 0.8)
    }

    func topDetails(_ o: [P], _ hem: Float, _ cx: Float) {
        let top = -T - 4.5
        let n = body.neckR
        let main = look.main
        switch outfit {
case Outfit.TEE: 
// Göğüste geniş şerit ve yaka
                spot(look.accent) { points(band(o, -T * 0.6, -T * 0.44), closed: true) }
                spot(CK.shade(look.accent)) { points({ (q: [P]) in [P(q[1].x - 4.0, q[1].y), q[1], q[2], P(q[2].x - 4.0, q[2].y)] }(band(o, -T * 0.6, -T * 0.44)), closed: true) }
                fill(CK.WHITE, FINE) { star(cx - 1.0, -T * 0.52, 4.2, 1.9, 5) }
                fill(CK.shade(main), FINE) { path(closed: true) { moveTo(cx - n - 1.5, top + 0.5); quadTo(cx, top + 6.0, cx + n + 1.5, top + 0.5); quadTo(cx, top + 3.5, cx - n - 1.5, top + 0.5) } }
case Outfit.SAILOR: 
// Denizci yakası, kurdele, manşet şeritleri
                let col = look.second
                let v = -T * 0.5
                fill(col, MID) { poly(cx - n - 2.0, top, cx, v, cx + n + 2.0, top, cx + body.shoulderOut * 0.86, top + 4.0, cx + body.chestW * 0.55, top + 13.0, cx, v + 6.0, cx - body.chestW * 0.55, top + 13.0, cx - body.shoulderOut * 0.86, top + 4.0, closed: true) }
                line(CK.WHITE, 0.9) { poly(cx - body.shoulderOut * 0.78, top + 5.0, cx - body.chestW * 0.5, top + 12.0, cx, v + 4.0, cx + body.chestW * 0.5, top + 12.0, cx + body.shoulderOut * 0.78, top + 5.0) }
                spot(CK.skinShade(look.skin)) { poly(cx - n - 1.0, top, cx, v - 1.0, cx + n + 1.0, top, closed: true) }.alpha(0.0)
                fill(look.skin, FINE) { poly(cx - n - 0.5, top + 0.5, cx, v - 4.0, cx + n + 0.5, top + 0.5, closed: true) }
                bow(cx, v + 2.0, look.accent, 1.0)
case Outfit.SUIT: 
let v = -T * 0.42
                fill(look.trim, FINE) { poly(cx - n - 1.5, top, cx, v, cx + n + 1.5, top, closed: true) }
                // Yakalar
                fill(CK.shade(main), MID) { poly(cx - n - 1.5, top, cx - 1.0, v + 1.0, cx - 7.0, v - 6.0, cx - 10.0, top + 12.0, cx - 7.0, top + 9.0, cx - n - 4.0, top + 1.0, closed: true) }
                fill(CK.shade(main), MID) { poly(cx + n + 1.5, top, cx + 1.0, v + 1.0, cx + 7.0, v - 6.0, cx + 10.0, top + 12.0, cx + 7.0, top + 9.0, cx + n + 4.0, top + 1.0, closed: true) }
                line(CK.deep(main), FINE) { line(cx, v + 1.0, cx, hem - 1.0) }
                fill(CK.GOLD_D, FINE) { circle(cx + 1.5, v + 6.0, 1.1) }
                fill(CK.GOLD_D, FINE) { circle(cx + 1.5, v + 13.0, 1.1) }
                fill(CK.WHITE, FINE) { poly(cx - body.chestW * 0.72, -T * 0.62, cx - body.chestW * 0.42, -T * 0.62, cx - body.chestW * 0.5, -T * 0.66, cx - body.chestW * 0.62, -T * 0.67, closed: true) }
                line(CK.deep(main), FINE) { line(cx - body.chestW * 0.75, -T * 0.62, cx - body.chestW * 0.4, -T * 0.62) }
case Outfit.CARDIGAN: 
let v = -T * 0.3
                fill(look.trim, FINE) { poly(cx - n - 1.0, top, cx - 2.0, v, cx + 2.0, v, cx + n + 1.0, top, closed: true) }
                spot(CK.shade(look.trim)) { poly(cx - n - 1.0, top, cx + n + 1.0, top, cx, top + 5.0, closed: true) }
                line(CK.deep(main), MID) { line(cx - n - 1.0, top + 1.0, cx - 2.5, hem - 1.0) }
                line(CK.deep(main), MID) { line(cx + n + 1.0, top + 1.0, cx + 2.5, hem - 1.0) }
                for i in 0...2 { fill(CK.deep(main), FINE) { circle(cx - 4.5, v + 4.0 + Float(i) * 6.0, 1.1) } }
                fill(CK.shade(main), FINE) { roundRect(cx - body.waistW * 0.85, -T * 0.2, 8.0, 7.0, 1.5) }
                fill(CK.shade(main), FINE) { roundRect(cx + body.waistW * 0.85 - 8.0, -T * 0.2, 8.0, 7.0, 1.5) }
case Outfit.BLOUSE: 
fill(look.trim, FINE) { poly(cx - n - 2.0, top - 0.5, cx - 1.0, top + 7.0, cx - 6.0, top + 9.0, cx - 9.0, top + 3.0, closed: true) }
                fill(look.trim, FINE) { poly(cx + n + 2.0, top - 0.5, cx + 1.0, top + 7.0, cx + 6.0, top + 9.0, cx + 9.0, top + 3.0, closed: true) }
                line(CK.deep(main), FINE) { line(cx, top + 7.0, cx, hem) }
                for i in 0...2 { fill(look.trim, FINE) { circle(cx + 1.2, top + 12.0 + Float(i) * 7.0, 0.9) } }
                // Kolye
                line(CK.GOLD, 0.8) { arc(cx, top + 2.0, n + 3.0, 8.0, 20.0, 140.0) }
                fill(CK.GOLD, FINE) { circle(cx, top + 10.0, 1.4) }
case Outfit.LABCOAT: 
let v = -T * 0.35
                fill(look.second, FINE) { poly(cx - n - 1.5, top, cx, v, cx + n + 1.5, top, closed: true) }
                fill(look.skin, FINE) { poly(cx - n, top + 0.5, cx, top + 7.0, cx + n, top + 0.5, closed: true) }
                fill(CK.shade(main), MID) { poly(cx - n - 1.5, top, cx - 1.0, v, cx - 2.0, hem - 1.0, cx - 6.0, hem - 1.0, cx - 8.0, v - 4.0, cx - 11.0, top + 10.0, closed: true) }
                fill(main, MID) { poly(cx + n + 1.5, top, cx + 1.0, v, cx + 2.0, hem - 1.0, cx + 6.0, hem - 1.0, cx + 8.0, v - 4.0, cx + 11.0, top + 10.0, closed: true) }
                fill(main, FINE) { roundRect(cx - body.chestW * 0.78, -T * 0.62, 8.0, 7.0, 1.0) }
                line("#3E7CC9", 1.0) { line(cx - body.chestW * 0.7, -T * 0.62 - 3.0, cx - body.chestW * 0.7, -T * 0.62 + 2.0) }
                fill(CK.WHITE, FINE) { roundRect(cx + body.chestW * 0.3, -T * 0.58, 7.0, 9.0, 1.0) }
                spot("#3E7CC9") { rect(cx + body.chestW * 0.3 + 1.0, -T * 0.58 + 1.5, 5.0, 2.0) }
case Outfit.UNIFORM: 
let v = -T * 0.55
                fill(CK.light(main), FINE) { poly(cx - n - 2.0, top, cx, top + 6.0, cx + n + 2.0, top, cx + n + 4.0, top + 4.0, cx, top + 9.0, cx - n - 4.0, top + 4.0, closed: true) }
                fill(look.accent, FINE) { poly(cx - 1.5, top + 6.0, cx + 1.5, top + 6.0, cx + 2.2, v + 10.0, cx, v + 13.0, cx - 2.2, v + 10.0, closed: true) }
                for sx in ([-1.0, 1.0] as [Float]) {
                    fill(CK.shade(main), FINE) { roundRect(cx + sx * body.chestW * 0.55 - 4.5, -T * 0.66, 9.0, 3.4, 1.0) }
                    line(CK.deep(main), FINE) { roundRect(cx + sx * body.chestW * 0.55 - 4.5, -T * 0.66 + 3.4, 9.0, 7.0, 1.0) }
                }
                if look.extras.contains(Extra.BADGE) {
                    fill(CK.GOLD, FINE) { path(closed: true) { let bx = cx - body.chestW * 0.55; let by = -T * 0.72; moveTo(bx - 3.2, by - 3.0); lineTo(bx + 3.2, by - 3.0); lineTo(bx + 3.0, by + 1.5); quadTo(bx, by + 4.5, bx - 3.0, by + 1.5) } }
                    spot(CK.GOLD_D) { star(cx - body.chestW * 0.55, -T * 0.72, 1.6, 0.7, 5) }
                }
                fill(CK.WHITE, FINE) { roundRect(cx + body.chestW * 0.35, -T * 0.76, 7.0, 2.4, 0.8) }
case Outfit.TRENCH: 
let v = -T * 0.36
                fill(look.second, FINE) { poly(cx - n - 2.0, top, cx, v, cx + n + 2.0, top, closed: true) }
                // Kalkık yaka ve geniş yakalar
                fill(CK.shade(main), MID) { poly(cx - n - 1.0, top - 5.0, cx - n - 6.0, top - 2.0, cx - 13.0, top + 12.0, cx - 2.0, v + 2.0, cx - n - 1.0, top + 2.0, closed: true) }
                fill(main, MID) { poly(cx + n + 1.0, top - 5.0, cx + n + 6.0, top - 2.0, cx + 13.0, top + 12.0, cx + 2.0, v + 2.0, cx + n + 1.0, top + 2.0, closed: true) }
                for i in 0...1 { for sx in ([-1.0, 1.0] as [Float]) { fill(CK.deep(main), FINE) { circle(cx + sx * 5.5, v + 5.0 + Float(i) * 7.0, 1.2) } } }
                fill(CK.shade(main), MID) { points(band(o, -T * 0.3, -T * 0.22, 0.2), closed: true) }
                fill(CK.GOLD_D, FINE) { roundRect(cx - 3.0, -T * 0.31, 6.0, 5.0, 1.0) }
case Outfit.CHEF: 
for i in 0...2 { for sx in ([-1.0, 1.0] as [Float]) { fill("#5B6270", FINE) { circle(cx + sx * 5.5, -T * 0.68 + Float(i) * 8.0, 1.4) } } }
                line("#D9D4C8", FINE) { path { moveTo(cx - n - 2.0, top + 2.0); quadTo(cx + 3.0, -T * 0.5, cx + 2.0, hem - 1.0) } }
                spot("#E6E2D6") { roundRect(cx - n - 2.0, top - 1.0, (n + 2.0) * 2.0, 4.0, 2.0) }
case Outfit.JERSEY: 
// V yaka, yan şeritler, numara
                fill(look.skin, FINE) { poly(cx - n - 1.0, top, cx, top + 8.0, cx + n + 1.0, top, closed: true) }
                line(CK.WHITE, 1.4) { poly(cx - n - 1.5, top + 0.5, cx, top + 9.0, cx + n + 1.5, top + 0.5) }
                for sx in ([-1.0, 1.0] as [Float]) { spot(CK.WHITE) { let (l, r) = edgesAt(o, -T * 0.4); let x = (sx < 0.0 ? l + 1.2 : r - 3.6); return roundRect(x, -T + 16.0, 2.4, T * 0.7, 1.2) } }
                spot(CK.WHITE) { poly(cx - 5.0, -T * 0.68, cx + 5.0, -T * 0.68, cx + 5.0, -T * 0.62, cx + 0.5, -T * 0.3, cx - 3.0, -T * 0.3, cx + 1.2, -T * 0.615, cx - 5.0, -T * 0.615, closed: true) }
case Outfit.HEROSUIT: 
// Yan paneller, göğüs arması (özgün: eşkenar dörtgen içinde çift köşeli ok), altın kemer
                let panel = look.second
                for sx in ([-1.0, 1.0] as [Float]) { spot(panel) {
                    let (l, r) = edgesAt(o, -T * 0.5)
                    let e = (sx < 0.0 ? l : r)
                    return poly(e + sx * -0.4, -T + 10.0, e - sx * 6.0, -T * 0.55, e - sx * 3.0, hem - 2.0, e - sx * 0.6, hem - 2.0, closed: true)
                } }
                let ey = -T * 0.66
                fill(CK.GOLD, MID) { poly(cx, ey - 9.0, cx + 9.0, ey, cx, ey + 9.0, cx - 9.0, ey, closed: true) }
                fill(look.accent, FINE) { poly(cx - 5.0, ey + 2.0, cx, ey - 4.0, cx + 5.0, ey + 2.0, cx + 3.0, ey + 3.6, cx, ey, cx - 3.0, ey + 3.6, closed: true) }
                fill(look.accent, FINE) { poly(cx - 5.0, ey + 6.0, cx, ey + 0.5, cx + 5.0, ey + 6.0, cx + 3.0, ey + 7.0, cx, ey + 4.2, cx - 3.0, ey + 7.0, closed: true) }
case Outfit.SPACESUIT: 
fill("#E4E8EE", MID) { roundRect(cx - 9.0, -T * 0.66, 18.0, 13.0, 2.5) }
                spot(CK.RED) { circle(cx - 5.0, -T * 0.66 + 4.0, 1.6) }
                spot("#4FAE5A") { circle(cx, -T * 0.66 + 4.0, 1.6) }
                spot("#3E7CC9") { circle(cx + 5.0, -T * 0.66 + 4.0, 1.6) }
                spot("#F28A2E") { roundRect(cx - 6.0, -T * 0.66 + 8.0, 12.0, 2.2, 1.0) }
                // Görev arması (özgün: halkalı gezegen)
                fill("#2F4E8C", FINE) { circle(cx - body.chestW * 0.62, -T * 0.8, 4.0) }
                fill("#F2C14E", FINE) { circle(cx - body.chestW * 0.62, -T * 0.8, 1.8) }
                line(CK.WHITE, 0.6) { ellipse(cx - body.chestW * 0.62, -T * 0.8, 3.4, 1.1, -20.0) }
                // Boyun halkası
                fill("#9AA6B4", OUT) { roundRect(cx - n - 6.0, top - 4.0, (n + 6.0) * 2.0, 6.0, 3.0) }
                line(CK.shade("#E9EDF2"), FINE) { line(cx - body.waistW, -T * 0.2, cx + body.waistW, -T * 0.2) }
case Outfit.ARMOR: 
// Göğüs zırhı: parlak kenar, orta sırt, boyunluk
                spot(CK.light(CK.STEEL)) { roundRect(cx - body.chestW * 0.75, -T + 8.0, 5.0, T * 0.45, 2.5) }.alpha(0.9)
                line(CK.STEEL_D, FINE) { line(cx, -T + 6.0, cx, -T * 0.42) }
                line(CK.STEEL_D, FINE) { arc(cx, -T * 0.42, body.chestW * 0.7, 5.0, 20.0, 140.0) }
                fill(CK.STEEL, MID) { roundRect(cx - n - 3.0, top - 2.0, (n + 3.0) * 2.0, 6.0, 2.5) }
                fill(look.accent, MID) { poly(cx - 6.0, -T * 0.62, cx + 6.0, -T * 0.62, cx + 6.0, -T * 0.5, cx, -T * 0.43, cx - 6.0, -T * 0.5, closed: true) }
                fill(CK.GOLD, FINE) { star(cx, -T * 0.54, 3.6, 1.6, 8) }
default: break
}
        // Ortak eklentiler
        if look.extras.contains(Extra.TIE) {
            let c = look.accent
            fill(c, FINE) { poly(cx - 2.0, top + 1.0, cx + 2.0, top + 1.0, cx + 1.4, top + 4.5, cx - 1.4, top + 4.5, closed: true) }
            fill(c, FINE) { poly(cx - 1.4, top + 4.5, cx + 1.4, top + 4.5, cx + 3.2, -T * 0.42 + 2.0, cx, -T * 0.42 + 5.5, cx - 3.2, -T * 0.42 + 2.0, closed: true) }
            spot(CK.shade(c)) { poly(cx + 0.2, top + 5.0, cx + 1.4, top + 4.5, cx + 3.2, -T * 0.42 + 2.0, cx + 0.6, -T * 0.42 + 4.5, closed: true) }
        }
        if look.extras.contains(Extra.NECKERCHIEF) {
            fill(look.accent, MID) { poly(cx - n - 3.0, top - 0.5, cx + n + 3.0, top - 0.5, cx + 2.0, top + 9.0, cx - 2.0, top + 9.0, closed: true) }
            fill(look.accent, FINE) { circle(cx, top + 3.0, 2.6) }
        }
        if look.extras.contains(Extra.SCARF) {
            fill(look.accent, MID) { roundRect(cx - n - 5.0, top - 4.0, (n + 5.0) * 2.0, 6.5, 3.0) }
            fill(look.accent, MID) { poly(cx + 3.0, top + 1.0, cx + 8.0, top + 1.0, cx + 9.0, top + 18.0, cx + 4.0, top + 19.0, closed: true) }
            for i in 0...2 { line(CK.light(look.accent), 0.7) { line(cx + 4.5 + Float(i) * 1.6, top + 19.0, cx + 4.7 + Float(i) * 1.6, top + 21.0) } }
        }
        if look.extras.contains(Extra.STETHOSCOPE) {
            line("#2B2F3A", 1.5) { smooth(cx - n - 3.0, top + 1.0, cx - n - 4.0, -T * 0.55, cx - 2.0, -T * 0.45) }
            line("#2B2F3A", 1.5) { smooth(cx + n + 3.0, top + 1.0, cx + n + 4.0, -T * 0.6, cx + 1.0, -T * 0.52, cx - 2.0, -T * 0.45) }
            fill(CK.STEEL, FINE) { circle(cx - 2.0, -T * 0.42, 2.8) }
        }
        if look.extras.contains(Extra.BADGE) && outfit != Outfit.UNIFORM {
            fill(CK.GOLD, FINE) { star(cx - body.chestW * 0.5, -T * 0.7, 4.0, 1.8, 5) }
        }
        // Kemer
        if outfit == Outfit.UNIFORM || outfit == Outfit.HEROSUIT || outfit == Outfit.BLOUSE || outfit == Outfit.ARMOR {
            let c = switch outfit {
case Outfit.HEROSUIT: CK.GOLD
case Outfit.UNIFORM: "#1A1A22"
case Outfit.ARMOR: "#5A3A22"
default: CK.shade(look.second)
}
            let y0 = hem - 4.5
            fill(c, MID) { points(band(o, y0, hem + 0.5, -0.3), closed: true) }
            let bc = (outfit == Outfit.HEROSUIT ? look.accent : CK.GOLD)
            fill(bc, FINE) { roundRect(cx - 3.2, y0 - 0.6, 6.4, 6.0, 1.4) }
            if outfit == Outfit.UNIFORM { for sx in ([-1.0, 1.0] as [Float]) { fill("#2A2A33", FINE) { roundRect(cx + sx * body.waistW * 0.62 - 3.0, y0, 6.0, 6.5, 1.2) } } }
        }
    }

    /** Kurdele/fiyonk: iki ilmek, düğüm ve iki uç. */
    func bow(_ x: Float, _ y: Float, _ c: String, _ s: Float) {
        fill(c, FINE) { poly(x - 1.0 * s, y + 1.0 * s, x - 4.0 * s, y + 9.0 * s, x - 1.5 * s, y + 8.0 * s, x, y + 10.0 * s, closed: true) }
        fill(c, FINE) { poly(x + 1.0 * s, y + 1.0 * s, x + 4.0 * s, y + 9.0 * s, x + 1.5 * s, y + 8.0 * s, x, y + 10.0 * s, closed: true) }
        fill(c, FINE) { ellipse(x - 4.2 * s, y, 4.2 * s, 2.6 * s, -12.0) }
        fill(c, FINE) { ellipse(x + 4.2 * s, y, 4.2 * s, 2.6 * s, 12.0) }
        spot(CK.shade(c)) { ellipse(x + 4.8 * s, y + 0.8 * s, 2.4 * s, 1.2 * s, 12.0) }
        fill(CK.shade(c), FINE) { roundRect(x - 1.6 * s, y - 1.8 * s, 3.2 * s, 3.6 * s, 1.2 * s) }
    }

    /** Cüppe üstü: omuzdan bele; sakal göğsü örter. */
    func robeTop() {
        let o = torsoOutline(4.0, 1.5)
        let cx = sk.bodyTurn * body.waistW * 0.3
        torsoFrame {
            fill(look.main, OUT) { points(o, closed: true) }
            torsoShadow(o, CK.shade(look.main), -T + 12.0, 3.0, body.waistW * 0.45)
            // Yaka şeridi (V), ip kemer ve püskül
            taper(P(cx - body.neckR - 3.0, -T - 4.0), P(cx - 2.0, -T * 0.6), P(cx, -T * 0.3), w0: 2.4, w1: 2.6, w2: 2.6, color: look.trim)
            taper(P(cx + body.neckR + 3.0, -T - 4.0), P(cx + 2.0, -T * 0.6), P(cx, -T * 0.3), w0: 2.4, w1: 2.6, w2: 2.6, color: look.trim)
            fill("#C9A15A", MID) { points(band(o, -T * 0.33, -T * 0.26, -0.3), closed: true) }
            fill("#C9A15A", FINE) { circle(cx + 4.0, -T * 0.29, 2.4) }
            taper(P(cx + 4.0, -T * 0.27), P(cx + 5.5, -T * 0.1), P(cx + 5.0, 4.0), w0: 1.6, w1: 1.4, w2: 2.6, color: "#C9A15A")
        }
    }

    /** Robot gövdesi: köşeli göğüs plakası, parlayan çekirdek, karın halkaları. */
    func mechaTop() {
        let o = torsoOutline(-T * 0.3, 0.5)
        let cx = sk.bodyTurn * body.waistW * 0.3
        let plate = "#D5DCE4"
        torsoFrame {
            // Karın (koyu, halkalı)
            fill("#5B6573", OUT) { roundRect(cx - body.waistW * 0.8, -T * 0.42, body.waistW * 1.6, T * 0.45, 4.0) }
            for i in 0...2 { line("#3A414C", FINE) { line(cx - body.waistW * 0.7, -T * 0.32 + Float(i) * T * 0.1, cx + body.waistW * 0.7, -T * 0.32 + Float(i) * T * 0.1) } }
            fill(plate, OUT) { points(o, closed: true) }
            torsoShadow(o, CK.shade(plate), -T + 10.0, -T * 0.32, body.waistW * 0.45)
            fill("#F28A2E", MID) { poly(cx - body.chestW * 0.62, -T + 2.0, cx - 4.0, -T + 2.0, cx - 6.0, -T + 9.0, cx - body.chestW * 0.55, -T + 9.0, closed: true) }
            fill("#F28A2E", MID) { poly(cx + body.chestW * 0.62, -T + 2.0, cx + 4.0, -T + 2.0, cx + 6.0, -T + 9.0, cx + body.chestW * 0.55, -T + 9.0, closed: true) }
            line(CK.STEEL_D, FINE) { poly(cx - body.chestW * 0.7, -T * 0.55, cx, -T * 0.42, cx + body.chestW * 0.7, -T * 0.55) }
            fill(CK.SCREEN, MID) { circle(cx, -T * 0.66, 6.0) }
            spot(CK.GLOW) { circle(cx, -T * 0.66, 4.0) }.alpha(0.9)
            spot(CK.WHITE) { circle(cx - 1.2, -T * 0.66 - 1.4, 1.5) }
            spot(CK.light(plate)) { roundRect(cx - body.chestW * 0.78, -T + 11.0, 4.0, 12.0, 2.0) }.alpha(0.9)
        }
    }

    // ------------------------------------------------------------------ kollar

    func arms(_ layer: ArmLayer) {
        if pose.layerL == layer { arm(left: true) }
        if pose.layerR == layer { arm(left: false) }
    }

    func crossedArms() {
        // Kollar göğüste kavuşturulmuş: önce sağ kol, sonra sol kol; sol el sağ pazının altında kalır
        arm(left: false)
        arm(left: true, handVisible: false)
        armSegment(sk.shoulderR, sk.elbowR, upperOnly: true)
    }

    /** Kolun rengi ve kalınlığı (kıyafete göre). */
    func sleeveColor() -> String { switch outfit {
case Outfit.TEE, Outfit.JERSEY: look.skin
case Outfit.MECHA: "#5B6573"
case Outfit.ARMOR: "#4A4E5A"
case Outfit.SPACESUIT: look.main
case Outfit.SAILOR: "#F4F5FA"
default: look.main
} }

    func armBulk() -> Float { switch outfit {
case Outfit.TEE, Outfit.JERSEY, Outfit.HEROSUIT: 0.0
case Outfit.SPACESUIT: 2.0
case Outfit.MECHA: -0.5
default: 0.7
} }

    func armSegment(_ s: P, _ e: P, upperOnly: Bool) {
        let bulk = armBulk()
        let c = sleeveColor()
        capsule(s, e, body.armR0 + bulk, body.armR1 + bulk).fill(c).stroke(ink).width(OUT * 2.0)
        capsule(s, e, body.armR0 + bulk, body.armR1 + bulk).fill(c).noStroke()
        celSeg(s, e, body.armR0 + bulk, body.armR1 + bulk, (c == look.skin ? CK.skinShade(c) : CK.shade(c)))
        if upperOnly { sleeveTop(s, e) }
    }

    /** Kısa kol ve omuz parçaları (pazı üstünde). */
    func sleeveTop(_ s: P, _ e: P) {
        switch outfit {
case Outfit.TEE, Outfit.JERSEY: 
let end = along(s, e, 0.5)
                capsule(s, end, body.armR0 + 2.0, body.armR0 + 1.6).fill(look.main).stroke(ink).width(MID)
                celSeg(s, end, body.armR0 + 2.0, body.armR0 + 1.6, CK.shade(look.main))
                capsule(along(s, e, 0.4), end, body.armR0 + 1.7).fill((outfit == Outfit.JERSEY ? CK.WHITE : look.accent)).stroke(ink).width(FINE)
case Outfit.ARMOR: 
// Omuzluk: üst üste iki plaka
                let d = e - s; let len = max(0.01, kHypot(d.x, d.y)); let u = P(d.x / len, d.y / len)
                let c1 = s + u * 2.0
                fill(CK.STEEL, MID) { ellipse(c1.x, c1.y, body.armR0 + 4.0, body.armR0 + 3.0, angleOf(u) * -1.0 + 90.0) }
                fill(CK.STEEL, MID) { ellipse(c1.x + u.x * 5.0, c1.y + u.y * 5.0, body.armR0 + 3.0, body.armR0 + 1.6, angleOf(u) * -1.0 + 90.0) }
                spot(CK.WHITE) { circle(c1.x - 2.0, c1.y - 2.0, 1.4) }.alpha(0.8)
case Outfit.MECHA: 
let d = e - s; let len = max(0.01, kHypot(d.x, d.y)); let u = P(d.x / len, d.y / len)
                let c1 = s + u * 1.5
                fill("#D5DCE4", OUT) { roundRect(c1.x - body.armR0 - 3.5, c1.y - body.armR0 - 3.0, (body.armR0 + 3.5) * 2.0, (body.armR0 + 3.0) * 2.0, 4.5) }
                spot(CK.shade("#D5DCE4")) { roundRect(c1.x + 1.0, c1.y - body.armR0 - 2.0, body.armR0 + 1.5, (body.armR0 + 2.0) * 2.0, 3.0) }
                fill("#F28A2E", FINE) { roundRect(c1.x - body.armR0 - 2.0, c1.y - 1.5, (body.armR0 + 2.0) * 2.0, 3.0, 1.0) }
case Outfit.HEROSUIT: 
if body.muscle > 0.5 {
                    let m = along(s, e, 0.45)
                    taper(along(s, e, 0.15), m + rotV((e - s) * (1.0 / max(0.01, dist(s, e))), 90.0) * (body.armR0 * 0.4), along(s, e, 0.75), w0: 0.2, w1: 0.8, w2: 0.2, color: CK.deep(look.main), alpha: 0.6)
                }
case Outfit.SPACESUIT: capsule(along(s, e, 0.55), along(s, e, 0.75), body.armR0 + 2.3).fill("#F28A2E").stroke(ink).width(FINE)
default: break
}
    }

    func arm(left: Bool, handVisible: Bool = true) {
        let s = (left ? sk.shoulderL : sk.shoulderR)
        let e = (left ? sk.elbowL : sk.elbowR)
        let h = (left ? sk.handL : sk.handR)
        let holds = prop != nil && pose.propRight != left
        // Eşya tutan el yumruk olur (sapı kavrar)
        let shape = (holds ? Hand.FIST : (left ? pose.handL : pose.handR))
        if holds { propBack(prop!, h, e) }
        let bulk = armBulk()
        let c = sleeveColor()
        limb(s, e, h, body.armR0 + bulk, body.armR1 + bulk, body.armR2 + bulk * 0.8, c)
        sleeveTop(s, e)
        // Kol ucu: manşet, eldiven, yen
        switch outfit {
case Outfit.SUIT, Outfit.CARDIGAN, Outfit.LABCOAT, Outfit.UNIFORM, Outfit.TRENCH, Outfit.CHEF, Outfit.SAILOR, Outfit.BLOUSE: 
let cuff = switch outfit {
case Outfit.SUIT: CK.WHITE
case Outfit.SAILOR: look.second
case Outfit.BLOUSE, Outfit.CHEF: CK.shade(c)
default: CK.shade(c)
}
                let r = body.armR2 + bulk * 0.8 + 0.5
                capsule(along(e, h, 0.82), along(e, h, 0.97), r).fill(cuff).stroke(ink).width(MID)
                if outfit == Outfit.SAILOR { capsule(along(e, h, 0.86), along(e, h, 0.88), r + 0.1).fill(CK.WHITE).noStroke() }
case Outfit.HEROSUIT: 
// Eldiven: ön kolun yarısından, ağzı genişleyen
                let g = look.trim
                let st = along(e, h, 0.45)
                capsule(st, h, body.armR1 * 1.02, body.armR2 + 0.4).fill(g).stroke(ink).width(MID)
                celSeg(st, h, body.armR1 * 1.02, body.armR2 + 0.4, CK.shade(g))
                let d = h - e; let len = max(0.01, kHypot(d.x, d.y)); let n = P(-d.y / len, d.x / len)
                let r = body.armR1 + 2.2
                fill(g, MID) { poly(st.x + n.x * r, st.y + n.y * r, st.x - n.x * r, st.y - n.y * r, along(e, h, 0.62).x, along(e, h, 0.62).y, closed: true) }
case Outfit.ARMOR: 
let st = along(e, h, 0.25)
                capsule(st, along(e, h, 0.95), body.armR1 + 1.6, body.armR2 + 1.6).fill(CK.STEEL).stroke(ink).width(MID)
                celSeg(st, along(e, h, 0.95), body.armR1 + 1.6, body.armR2 + 1.6, CK.shade(CK.STEEL))
                fill(CK.light(CK.STEEL), MID) { circle(e.x, e.y, body.armR1 + 1.2) }
case Outfit.MECHA: 
capsule(along(e, h, 0.15), along(e, h, 0.9), body.armR1 + 1.6, body.armR2 + 1.2).fill("#D5DCE4").stroke(ink).width(OUT)
                celSeg(along(e, h, 0.15), along(e, h, 0.9), body.armR1 + 1.6, body.armR2 + 1.2, CK.shade("#D5DCE4"))
                fill("#F28A2E") { circle(e.x, e.y, body.armR1 * 0.75) }
case Outfit.SPACESUIT: capsule(along(e, h, 0.85), along(e, h, 1.0), body.armR2 + 2.6).fill("#9AA6B4").stroke(ink).width(MID)
case Outfit.ROBE: 
// Geniş çan yen
                let st = along(e, h, 0.2); let tip = along(e, h, 0.95)
                let d = h - e; let len = max(0.01, kHypot(d.x, d.y)); let n = P(-d.y / len, d.x / len)
                let w0 = body.armR1 + 1.5; let w1 = body.armR2 + 7.0
                fill(look.main, MID) { poly(st.x + n.x * w0, st.y + n.y * w0, tip.x + n.x * w1, tip.y + n.y * w1, tip.x - n.x * w1, tip.y - n.y * w1, st.x - n.x * w0, st.y - n.y * w0, closed: true) }
                line(look.trim, 1.6) { line(tip.x + n.x * (w1 - 0.8), tip.y + n.y * (w1 - 0.8), tip.x - n.x * (w1 - 0.8), tip.y - n.y * (w1 - 0.8)) }
case Outfit.JERSEY, Outfit.TEE: if look.extras.contains(Extra.WRISTBANDS) { capsule(along(e, h, 0.78), along(e, h, 0.95), body.armR2 + 1.1).fill(look.accent).stroke(ink).width(MID) }
default: break
}
        if handVisible { hand(h, e, left, shape) }
        if holds { propFront(prop!, h, e, left) }
    }

    // ------------------------------------------------------------------ eller

    func handColor() -> String { switch outfit {
case Outfit.MECHA: "#D5DCE4"
case Outfit.ARMOR: CK.STEEL
case Outfit.SPACESUIT: "#9AA6B4"
case Outfit.HEROSUIT: look.trim
default: look.skin
} }

    /** El: bilekte yerel çerçeve (y ön kol yönünde), ölçek el yarıçapı. */
    func hand(_ h: P, _ e: P, _ left: Bool, _ shape: Hand) {
        if shape == Hand.HIDDEN { return }
        let a = angleOf(h - e)
        let c = handColor()
        let sh = (c == look.skin ? CK.skinShade(c) : CK.shade(c))
        // Başparmak gövdeye bakan yanda
        let th: Float = (left ? 1.0 : -1.0)
        let s = body.handR * ((shape == Hand.FIST ? 1.08 : 1.0))
        frame(h, -a, s) {
            var parts = [() -> VectorBuilder.Part]()
            switch shape {
case Hand.FIST: 
parts.append { [self] in roundRect(-0.95, 0.05, 1.9, 1.75, 0.62) }
                    parts.append { [self] in capsule2(th * 0.78, 0.45, th * 0.25, 1.15, 0.36) }
case Hand.POINT: 
parts.append { [self] in roundRect(-0.9, 0.05, 1.8, 1.55, 0.58) }
                    parts.append { [self] in capsule2(-th * 0.45, 1.2, -th * 0.45, 3.0, 0.27) }
                    parts.append { [self] in capsule2(th * 0.8, 0.45, th * 0.3, 1.05, 0.33) }
case Hand.VSIGN: 
parts.append { [self] in roundRect(-0.9, 0.05, 1.8, 1.55, 0.58) }
                    parts.append { [self] in capsule2(-th * 0.5, 1.2, -th * 0.95, 2.9, 0.27) }
                    parts.append { [self] in capsule2(-th * 0.05, 1.2, th * 0.25, 2.95, 0.27) }
                    parts.append { [self] in capsule2(th * 0.8, 0.45, th * 0.3, 1.05, 0.33) }
case Hand.OPEN, Hand.CLAW: 
let claw = shape == Hand.CLAW
                    parts.append { [self] in roundRect(-0.85, 0.05, 1.7, 1.35, 0.5) }
                    for i in 0...3 {
                        let x0: Float = -0.6 + Float(i) * 0.4
                        let x1 = x0 * 1.45
                        let len: Float = (i == 0 || i == 3 ? 2.05 : 2.35)
                        if claw { parts.append { [self] in capsule2(x0, 1.1, x1, len - 0.35, 0.22) } } else { parts.append { [self] in capsule2(x0, 1.1, x1, len, 0.22) } }
                    }
                    parts.append { [self] in capsule2(th * 0.7, 0.45, th * 1.45, 1.15, 0.27) }
case Hand.FLAT: 
parts.append { [self] in roundRect(-0.82, 0.05, 1.64, 2.35, 0.62) }
                    parts.append { [self] in capsule2(th * 0.75, 0.4, th * 1.1, 1.2, 0.27) }
case Hand.RELAXED: 
parts.append { [self] in path(closed: true) { moveTo(-0.85, 0.1); lineTo(0.85, 0.1); cubicTo(0.95, 0.9, 0.75, 1.85, 0.1, 1.95); cubicTo(-0.55, 2.0, -0.95, 1.2, -0.85, 0.1) } }
                    parts.append { [self] in capsule2(th * 0.82, 0.4, th * 0.62, 1.2, 0.3) }
case Hand.HIDDEN: break
default: break
}
            union(c, OUT, parts)
            // Parmak ayrımları ve gölge
            switch shape {
case Hand.FIST: 
for i in -1...1 { line(sh, FINE) { line(Float(i) * 0.48, 1.25, Float(i) * 0.48, 1.75) } }
                    spot(sh) { roundRect(-0.85, 1.45, 1.7, 0.3, 0.15) }.alpha(0.8)
                    if robot { line(CK.STEEL_D, FINE) { line(-0.9, 0.9, 0.9, 0.9) } }
case Hand.POINT, Hand.VSIGN: line(sh, FINE) { line(-0.7, 1.3, 0.6, 1.3) }
case Hand.FLAT: for i in -1...1 { line(sh, FINE) { line(Float(i) * 0.4, 1.5, Float(i) * 0.4, 2.3) } }
case Hand.RELAXED: line(sh, FINE) { line(-th * 0.15, 1.2, -th * 0.2, 1.85) }
default: break
}
        }
    }

    /** Yerel çerçevede iki noktalı kapsül (el parmakları). */
    @discardableResult func capsule2(_ x0: Float, _ y0: Float, _ x1: Float, _ y1: Float, _ r: Float) -> VectorBuilder.Part { capsuleIn(P(x0, y0), P(x1, y1), r) }

    func capsuleIn(_ a: P, _ c: P, _ r: Float) -> VectorBuilder.Part {
        let dx = c.x - a.x; let dy = c.y - a.y
        let len = max(0.001, kHypot(dx, dy))
        let ux = dx / len; let uy = dy / len
        let ang = toDegrees(kAtan2(dy, dx).toDouble()).toFloat()
        return path(closed: true) {
            moveTo(a.x - uy * r, a.y + ux * r)
            lineTo(c.x - uy * r, c.y + ux * r)
            arcTo(c.x, c.y, r, r, ang + 90.0, -180.0)
            lineTo(a.x + uy * r, a.y - ux * r)
            arcTo(a.x, a.y, r, r, ang - 90.0, -180.0)
        }
    }

    // ------------------------------------------------------------------ eşyalar

    /** Havaya kaldırılan uzun eşya dışa yatar (çerçeveden taşmaz, zafer pozunda daha dinamik). */
    func outward(_ a: Float) -> Float { (abs(a) > 120.0 ? a - kSign(a) * 28.0 : a) }

    /** Eşyanın eldeki yönü: asılı eşyalar dik, ötekiler ön kol yönünde. */
    func gripAngle(_ p: Prop, _ h: P, _ e: P) -> Float { switch p {
case Prop.BRIEFCASE, Prop.BAG, Prop.BOOK, Prop.CLIPBOARD: 0.0
case Prop.CANE: if dist(h, e) > 0.0 && (h.y - e.y) > -8.0 { 0.0 } else { angleOf(h - e) }
// Asa: aşağıdaysa dik, kalkınca tepesindeki taş ön kol yönünde (elin ötesinde) kalır
case Prop.STAFF: if dist(h, e) > 0.0 && (h.y - e.y) > -8.0 { 0.0 } else { outward(angleOf(h - e)) + 180.0 }
case Prop.SWORD: outward(angleOf(h - e))
default: angleOf(h - e)
} }

    /** Elin arkasında kalan kısım (sap, asılı çanta). */
    func propBack(_ p: Prop, _ h: P, _ e: P) {
        let a = gripAngle(p, h, e)
        frame(h, -a) {
            switch p {
case Prop.SWORD: 
// Kılıç: kabza elin içinde, namlu ileri
                    fill("#5A3A22", MID) { roundRect(-1.4, -6.0, 2.8, 9.0, 1.0) }
                    fill(CK.GOLD, MID) { roundRect(-7.0, 3.2, 14.0, 3.0, 1.2) }
                    fill(CK.GOLD, FINE) { circle(0.0, -6.5, 2.0) }
                    fill("#E4EAF0", OUT) { poly(-2.6, 6.0, 2.6, 6.0, 2.6, 44.0, 0.0, 50.0, -2.6, 44.0, closed: true) }
                    spot("#AEB8C4") { poly(0.3, 7.0, 2.4, 7.0, 2.4, 44.0, 0.3, 48.0, closed: true) }
                    line("#8994A2", FINE) { line(0.0, 8.0, 0.0, 44.0) }
                    if pose.prop == PropSlot.ACTION { spot(CK.WHITE) { star(-1.5, 30.0, 4.0, 0.9, 4) } }
case Prop.STAFF: 
let up: Float = -36; let down: Float = 44
                    fill("#7A4E2A", OUT) { roundRect(-1.8, up, 3.6, down - up, 1.8) }
                    spot("#5A3A22") { rect(0.4, up + 2.0, 1.2, down - up - 4.0) }
                    fill("#7A4E2A", MID) { path(closed: true) { moveTo(-1.8, up + 2.0); cubicTo(-7.0, up - 2.0, -6.0, up - 10.0, -1.0, up - 12.0); cubicTo(-3.0, up - 8.0, -2.0, up - 4.0, 0.0, up - 1.0) } }
                    fill("#7A4E2A", MID) { path(closed: true) { moveTo(1.8, up + 2.0); cubicTo(7.0, up - 2.0, 6.0, up - 10.0, 1.0, up - 12.0); cubicTo(3.0, up - 8.0, 2.0, up - 4.0, 0.0, up - 1.0) } }
                    spot(look.aura) { circle(0.0, up - 6.0, 7.5) }.alpha(0.35)
                    fill(look.aura, MID) { circle(0.0, up - 6.0, 4.6) }
                    spot(CK.WHITE) { circle(-1.4, up - 7.6, 1.5) }
case Prop.CANE: 
// Asılıyken yere değer; havaya kalkınca bacak boyunda kalır
                    let len = (a == 0.0 ? (sk.ground - h.y).coerceIn(30.0, body.thigh + body.shin) : (body.thigh + body.shin) * 0.55)
                    fill("#8A5A32", OUT) { roundRect(-1.4, -2.0, 2.8, len + 2.0, 1.4) }
                    fill("#8A5A32", OUT) { path(closed: true) { moveTo(-1.4, 0.0); cubicTo(-1.4, -9.0, 9.0, -9.0, 9.0, -2.0); lineTo(6.4, -2.0); cubicTo(6.4, -6.0, 1.4, -6.0, 1.4, 0.0) } }
case Prop.BRIEFCASE: 
fill("#2E2A2A", MID) { path { moveTo(-4.0, 2.0); quadTo(-4.0, -1.5, 0.0, -1.5); quadTo(4.0, -1.5, 4.0, 2.0) } }
                    fill("#6B4428", OUT) { roundRect(-12.0, 2.0, 24.0, 17.0, 2.5) }
                    spot(CK.shade("#6B4428")) { rect(1.0, 2.6, 10.5, 16.0) }
                    line("#3E2716", FINE) { line(-12.0, 7.0, 12.0, 7.0) }
                    fill(CK.GOLD, FINE) { rect(-2.0, 5.5, 4.0, 3.0) }
case Prop.BAG: 
fill("#3A2C25", MID) { path { moveTo(-5.0, 3.0); quadTo(-5.0, -2.0, 0.0, -2.0); quadTo(5.0, -2.0, 5.0, 3.0) } }
                    fill("#5A3D2B", OUT) { roundRect(-11.0, 3.0, 22.0, 15.0, 3.5) }
                    fill(CK.shade("#5A3D2B"), MID) { path(closed: true) { moveTo(-11.0, 4.0); lineTo(11.0, 4.0); lineTo(11.0, 9.0); quadTo(0.0, 12.0, -11.0, 9.0) } }
                    fill(CK.GOLD, FINE) { roundRect(-1.5, 8.5, 3.0, 3.0, 0.8) }
case Prop.BOOK: 
fill("#2F6DB5", OUT) { roundRect(-8.0, -2.0, 16.0, 20.0, 1.5) }
                    fill("#F4EFDF", FINE) { rect(-6.5, 16.0, 14.0, 2.0) }
                    fill(CK.GOLD, FINE) { roundRect(-4.5, 3.0, 9.0, 3.0, 0.8) }
case Prop.CLIPBOARD: 
fill("#A87A4E", OUT) { roundRect(-8.0, -1.0, 16.0, 21.0, 1.5) }
                    fill(CK.WHITE, FINE) { rect(-6.0, 2.0, 12.0, 16.0) }
                    for i in 0...3 { line("#9AA6B4", 0.7) { line(-4.5, 5.0 + Float(i) * 3.4, 4.5, 5.0 + Float(i) * 3.4) } }
                    fill(CK.STEEL_D, FINE) { roundRect(-3.5, -2.5, 7.0, 3.5, 1.0) }
case Prop.POINTER: 
fill("#8A5A32", MID) { poly(-0.9, -3.0, 0.9, -3.0, 0.5, 34.0, -0.5, 34.0, closed: true) }
                    fill(CK.RED, FINE) { circle(0.0, 34.5, 1.2) }
case Prop.BATON: 
fill("#26262E", OUT) { roundRect(-1.8, -5.0, 3.6, 30.0, 1.8) }
                    fill("#3A3A44", FINE) { roundRect(-2.3, -1.0, 4.6, 3.0, 1.0) }
case Prop.MAGNIFIER: 
fill("#5A3A22", OUT) { roundRect(-1.6, -3.0, 3.2, 13.0, 1.6) }
                    fill(CK.GOLD_D, OUT) { circle(0.0, 17.0, 8.0) }
                    fill("#CFEFFF", MID) { circle(0.0, 17.0, 6.0) }.alpha(0.9)
                    line(CK.WHITE, 1.2) { arc(0.0, 17.0, 4.0, 4.0, 200.0, 70.0) }
case Prop.LADLE: 
fill("#B8C2CE", MID) { roundRect(-1.1, -4.0, 2.2, 24.0, 1.1) }
                    fill("#B8C2CE", OUT) { path(closed: true) { moveTo(-6.0, 19.0); lineTo(6.0, 19.0); arcTo(0.0, 19.0, 6.0, 6.0, 0.0, 180.0) } }
                    spot(CK.shade("#B8C2CE")) { path(closed: true) { moveTo(1.0, 20.0); lineTo(5.6, 20.0); arcTo(0.0, 19.0, 5.6, 5.6, 10.0, 70.0) } }
case Prop.PAN: 
fill("#26262E", MID) { roundRect(-1.6, -4.0, 3.2, 16.0, 1.4) }
                    fill("#3A3A44", OUT) { circle(0.0, 22.0, 11.0) }
                    spot("#55555F") { circle(-1.0, 21.0, 8.0) }
                    spot(CK.WHITE) { ellipse(-4.0, 17.5, 3.0, 1.4, -30.0) }.alpha(0.7)
case Prop.BALL: 
fill("#F28A2E", OUT) { circle(0.0, 9.0, 9.0) }
                    line(CK.deep("#F28A2E"), FINE) { line(-9.0, 9.0, 9.0, 9.0) }
                    line(CK.deep("#F28A2E"), FINE) { arc(-9.0, 9.0, 5.0, 8.0, -70.0, 140.0) }
                    line(CK.deep("#F28A2E"), FINE) { arc(9.0, 9.0, 5.0, 8.0, 110.0, 140.0) }
                    spot(CK.WHITE) { ellipse(-3.5, 4.0, 2.4, 1.3, -30.0) }.alpha(0.7)
default: break
}
        }
    }

    /** Elin önünde kalan kısım (kılıcın parıltısı, büyü ışığı). */
    func propFront(_ p: Prop, _ h: P, _ e: P, _ left: Bool) {
        if p == Prop.STAFF && pose.prop == PropSlot.ACTION {
            let a = gripAngle(p, h, e)
            let tip = h + rotV(P(0.0, -42.0), -a)
            for (dx, dy, r) in [(Float(-9.0), Float(-6.0), Float(3.2)), (Float(8.0), Float(-10.0), Float(2.6)), (Float(4.0), Float(7.0), Float(2.2))] { spot(CK.WHITE) { star(tip.x + dx, tip.y + dy, r, r * 0.25, 4) } }
        }
    }

    // ------------------------------------------------------------------ efektler

    /** Vücudun arkasında kalan efektler (aura, hız çizgileri, savrulma izi). */
    func backFx() {
        let fx = pose.fx
        if fx.contains(PoseFx.AURA) { aura() }
        if fx.contains(PoseFx.SPEED) {
            let left = min(min(sk.shoulderL.x, sk.hipL.x), min(sk.handL.x, sk.ankleL.x)) - 6.0
            let rows = [(Float(0.15), Float(1.0)), (Float(0.3), Float(0.65)), (Float(0.45), Float(0.9)), (Float(0.6), Float(0.55)), (Float(0.75), Float(0.8))]
            let top = sk.head.y; let bottom = sk.ground - 10.0
            for (t, f) in rows {
                let y = top + (bottom - top) * t
                taper(P(left - 44.0 * f, y), P(left - 4.0, y), w0: 0.2, w1: 1.4, w2: 2.0, color: ink, alpha: 0.85)
            }
        }
        if fx.contains(PoseFx.SWISH_KICK) {
            let c = sk.hipR; let r = dist(c, sk.ankleR)
            let a0 = angleOf(sk.ankleR - c)
            for i in 0...2 {
                let rr = r + 6.0 - Float(i) * 6.0
                let pts = (0...8).map { j in c + dirOf(a0 - 70.0 + Float(j) * 8.0) * rr }
                taper(pts, 0.1, 1.6 - Float(i) * 0.3, 0.4, ink, 0.75)
            }
        }
        if fx.contains(PoseFx.SWISH_ARM) {
            let holdR = pose.propRight
            let s = (holdR ? sk.shoulderR : sk.shoulderL)
            let h = (holdR ? sk.handR : sk.handL)
            let r = dist(s, h) + (prop != nil ? 34.0 : 6.0)
            let a0 = angleOf(h - s)
            let sgn: Float = (holdR ? 1.0 : -1.0)
            for i in 0...2 {
                let rr = r - Float(i) * 6.0
                let pts = (0...9).map { j in s + dirOf(a0 + sgn * (25.0 - Float(j) * 9.0)) * rr }
                taper(pts, 0.1, 1.8 - Float(i) * 0.4, 0.3, ink, 0.7)
            }
        }
        if fx.contains(PoseFx.WIND) {
            let y0 = max(sk.ankleL.y, sk.ankleR.y) + 10.0
            for (dx, len) in [(Float(-14.0), Float(26.0)), (Float(-4.0), Float(40.0)), (Float(6.0), Float(32.0)), (Float(16.0), Float(22.0))] {
                let x = (sk.ankleL.x + sk.ankleR.x) / 2.0 + dx
                taper(P(x, y0), P(x, y0 + len), w0: 2.0, w1: 1.2, w2: 0.2, color: ink, alpha: 0.8)
            }
            for sx in ([-1.0, 1.0] as [Float]) {
                let x = sk.pelvis.x + sx * (body.shoulderOut + 16.0)
                taper(P(x, sk.chest.y), P(x + sx * 2.0, sk.chest.y + 40.0), w0: 1.6, w1: 1.0, w2: 0.2, color: ink, alpha: 0.6)
            }
        }
        if fx.contains(PoseFx.FALL_LINES) {
            for i in 0...3 {
                let x = sk.head.x + 18.0 + Float(i) * 9.0; let y = sk.head.y - 26.0 + Float(i) * 6.0
                taper(P(x, y), P(x + 6.0, y - 22.0), w0: 1.8, w1: 1.0, w2: 0.2, color: ink, alpha: 0.75)
            }
        }
    }

    /** Güç toplarken alev gibi aura: vücudun çevresinde yukarı uzanan diller. */
    func aura() {
        let c = look.aura
        let cx = sk.pelvis.x
        let top = sk.head.y - R * 2.6
        let bottom = sk.ground + 1.0
        let half = max(abs(sk.ankleR.x - sk.ankleL.x) / 2.0 + 18.0, body.shoulderOut + 26.0)
        func flame(_ scale: Float) -> [P] {
            var pts = [P]()
            let n = 13
            for i in 0...n {
                let t = Float(i) / n.toFloat()
                // Sol taban → tepe → sağ taban: diller yukarı sivrilir
                let side: Float = -1 + 2.0 * t
                let yBase = bottom - (bottom - top) * (1.0 - side * side * 0.75) * scale
                let xBase = cx + side * half * scale
                let tongue: Float = (i % 2 == 1 ? 1.0 : 0.0)
                pts.append(P(xBase + side * 4.0 * tongue, yBase - tongue * 16.0 * scale))
            }
            return pts
        }
        let outer = flame(1.0)
        fill(CK.light(c), OUT) { smooth((outer.flatMap { [$0.x, $0.y] } + [cx + half * 0.7, bottom, cx - half * 0.7, bottom]).toFloatArray(), closed: true, tension: 0.35) }.alpha(0.55)
        let inner = flame(0.78)
        spot(c) { smooth((inner.flatMap { [$0.x, $0.y] } + [cx + half * 0.5, bottom, cx - half * 0.5, bottom]).toFloatArray(), closed: true, tension: 0.35) }.alpha(0.45)
    }

    /** Vücudun önündeki efektler. */
    func frontFx() {
        let fx = pose.fx
        if fx.contains(PoseFx.IMPACT_HAND) { impact(sk.handR + dirOf(angleOf(sk.handR - sk.elbowR)) * (body.handR * 1.8), 15.0) }
        if fx.contains(PoseFx.IMPACT_FOOT) { impact(sk.ankleR + dirOf(angleOf(sk.ankleR - sk.kneeR)) * 7.0, 16.0) }
        if fx.contains(PoseFx.SHOUT) {
            let c = sk.head + rotV(P(0.0, R * 0.5), sk.headAngle)
            for i in 0..<9 {
                let a: Float = 100 + Float(i) * 20.0 + (i % 2 == 0 ? 0.0 : 6.0)
                let d = dirOf(a)
                let r0 = R * 1.75; let r1 = R * ((i % 2 == 0 ? 2.55 : 2.3))
                taper(c + d * r0, c + d * r1, w0: 0.4, w1: 2.2, w2: 0.4, color: ink)
            }
            for sx in ([-1.0, 1.0] as [Float]) {
                let p0 = c + P(sx * R * 1.5, R * 0.4)
                taper(p0, p0 + P(sx * 9.0, 3.0), w0: 0.4, w1: 1.8, w2: 0.3, color: ink)
                taper(p0 + P(0.0, 6.0), p0 + P(sx * 8.0, 11.0), w0: 0.4, w1: 1.6, w2: 0.3, color: ink)
            }
        }
        if fx.contains(PoseFx.SPARKLE) {
            let h = (sk.handR.y < sk.handL.y ? sk.handR : sk.handL)
            for (dx, dy, r) in [(Float(-13.0), Float(0.0), Float(5.0)), (Float(12.0), Float(-7.0), Float(4.0)), (Float(14.0), Float(10.0), Float(3.0))] {
                fill(CK.GOLD, FINE) { star(h.x + dx, h.y + dy, r, r * 0.3, 4) }
            }
            fill(CK.GOLD, FINE) { star(sk.head.x - R * 1.6, sk.head.y - R * 0.6, 4.0, 1.2, 4) }
        }
        if fx.contains(PoseFx.MAGIC) {
            let h = (pose.propRight ? sk.handR : sk.handL)
            for (dx, dy, r) in [(Float(-10.0), Float(-20.0), Float(4.0)), (Float(12.0), Float(-26.0), Float(3.4)), (Float(2.0), Float(-36.0), Float(2.6))] { fill(look.aura, FINE) { star(h.x + dx, h.y + dy, r, r * 0.3, 4) } }
        }
        if fx.contains(PoseFx.AURA) {
            // Yükselen parçacıklar ve küçük şimşekler
            for (dx, dy) in [(Float(-30.0), Float(-10.0)), (Float(28.0), Float(-30.0)), (Float(-22.0), Float(-60.0)), (Float(34.0), Float(20.0)), (Float(-36.0), Float(40.0))] {
                let p = sk.chest + P(dx * (body.shoulderOut / 22.0), dy)
                fill(CK.light(look.aura), FINE) { poly(p.x, p.y - 3.4, p.x + 1.6, p.y, p.x, p.y + 3.4, p.x - 1.6, p.y, closed: true) }
            }
            for sx in ([-1.0, 1.0] as [Float]) {
                let p = sk.pelvis + P(sx * (body.hipW + 22.0), -10.0)
                fill(CK.WHITE, FINE) { poly(p.x, p.y - 12.0, p.x + sx * 4.0, p.y - 4.0, p.x + sx * 1.0, p.y - 3.0, p.x + sx * 5.0, p.y + 8.0, p.x - sx * 1.0, p.y - 1.0, p.x + sx * 1.5, p.y - 2.0, closed: true) }
            }
        }
        if fx.contains(PoseFx.DIZZY) {
            let c = sk.head + rotV(P(0.0, -R * 1.25), sk.headAngle)
            line(ink, FINE) { ellipse(c.x, c.y, R * 1.1, R * 0.32) }.alpha(0.6)
            for (i, a) in ([0.0, 120.0, 240.0] as [Float]).enumerated() {
                let r = toRadians((a + 30.0).toDouble())
                fill(CK.GOLD, FINE) { star(c.x + kCos(r).toFloat() * R * 1.1, c.y + kSin(r).toFloat() * R * 0.32, 4.0 - Float(i) * 0.5, 1.7 - Float(i) * 0.2, 5) }
            }
        }
        if fx.contains(PoseFx.PAIN) {
            let p = sk.shoulderL + P(-8.0, -6.0)
            for a in ([-40.0, 0.0, 40.0] as [Float]) { let d = dirOf(180.0 + a); taper(p + d * 3.0, p + d * 9.0, w0: 0.4, w1: 1.6, w2: 0.3, color: CK.ANGER) }
        }
        if fx.contains(PoseFx.SWEAT) {
            let c = sk.head + rotV(P(-R * 1.25, -R * 0.2), sk.headAngle)
            drop(c.x, c.y, 2.4)
            drop(c.x - 6.0, c.y + 8.0, 1.7)
        }
        if fx.contains(PoseFx.LAUGH_MARKS) {
            let c = sk.head
            for sx in ([-1.0, 1.0] as [Float]) { for i in 0...2 {
                let a: Float = ((sx > 0 ? 60.0 : -60.0)) + Float(i - 1) * 28.0 + 180.0
                let d = dirOf(a); let p0 = c + d * (R * 1.55)
                taper(p0, p0 + d * 7.0, w0: 0.3, w1: 1.6, w2: 0.3, color: ink)
            } }
        }
        if fx.contains(PoseFx.THOUGHT) {
            let c = sk.head + P(R * 1.1, -R * 1.2)
            fill(CK.WHITE, FINE) { circle(c.x, c.y, 2.0) }
            fill(CK.WHITE, FINE) { circle(c.x + 5.0, c.y - 7.0, 3.2) }
            fill(CK.WHITE, MID) { ellipse(c.x + 13.0, c.y - 18.0, 7.0, 5.0) }
            for i in -1...1 { spot(ink) { circle(c.x + 13.0 + Float(i) * 3.4, c.y - 18.0, 0.9) } }
        }
        if fx.contains(PoseFx.WAVE_ARCS) {
            let h = sk.handR
            for (r, a0) in ([(Float(13.0), Float(-70.0)), (Float(18.0), Float(-64.0))] as [(Float, Float)]) { line(ink, 1.1) { arc(h.x, h.y - 2.0, r, r, a0, 40.0) } }
            line(ink, 1.1) { arc(h.x, h.y - 2.0, 13.0, 13.0, 205.0, 40.0) }
        }
        if fx.contains(PoseFx.BLOCK_ARCS) {
            let c = (sk.handL + sk.handR) * 0.5
            for i in 0...2 { line(ink, 1.3 - Float(i) * 0.25) { arc(c.x + 4.0 + Float(i) * 5.0, c.y, 12.0 + Float(i) * 5.0, 16.0 + Float(i) * 6.0, -60.0, 120.0) } }
            for (dx, dy) in ([(Float(22.0), Float(-14.0)), (Float(26.0), Float(10.0))] as [(Float, Float)]) { fill(CK.GOLD, FINE) { star(c.x + dx, c.y + dy, 3.2, 1.0, 4) } }
        }
        if fx.contains(PoseFx.POINT_LINES) {
            let h = sk.handR + dirOf(angleOf(sk.handR - sk.elbowR)) * (body.handR * 3.2)
            for a in ([-35.0, 0.0, 35.0] as [Float]) { let d = dirOf(90.0 + a); taper(h + d * 3.0, h + d * 8.0, w0: 0.3, w1: 1.3, w2: 0.3, color: ink) }
        }
        if fx.contains(PoseFx.SOB) || fx.contains(PoseFx.TREMBLE) {
            for sx in ([-1.0, 1.0] as [Float]) {
                let p = ((sx < 0 ? sk.shoulderL : sk.shoulderR)) + P(sx * 8.0, -2.0)
                for i in 0...1 { line(ink, 1.0) { path { moveTo(p.x + sx * Float(i) * 3.5, p.y - 4.0); quadTo(p.x + sx * (Float(i) * 3.5 + 2.0), p.y, p.x + sx * Float(i) * 3.5, p.y + 4.0) } } }
            }
        }
        if fx.contains(PoseFx.BRAWL) {
            let c = (sk.handL + sk.handR) * 0.5 + P(10.0, -4.0)
            for (dx, dy, r) in ([(Float(8.0), Float(-18.0), Float(4.0)), (Float(18.0), Float(4.0), Float(3.4)), (Float(4.0), Float(16.0), Float(3.0))] as [(Float, Float, Float)]) { fill(CK.GOLD, FINE) { star(c.x + dx, c.y + dy, r, r * 0.42, 5) } }
            for a in ([-50.0, -10.0, 30.0, 70.0] as [Float]) { let d = dirOf(90.0 + a); taper(c + d * 16.0, c + d * 24.0, w0: 0.3, w1: 1.5, w2: 0.3, color: ink) }
        }
    }

    /** Zemindeki efektler (toz, zıplama çizgileri) dönmeden çizilir. */
    func groundFx() {
        let fx = pose.fx
        if fx.contains(PoseFx.DUST) {
            let back = (pose.turn >= 0.0 ? min(sk.ankleL.x, sk.ankleR.x) - 8.0 : max(sk.ankleL.x, sk.ankleR.x) + 8.0)
            let dir: Float = (pose.turn >= 0.0 ? -1.0 : 1.0)
            let y: Float = 192
            let puffs: [[Float]] = [[back, y, 5.0], [back + dir * 7.0, y - 3.0, 6.5], [back + dir * 15.0, y, 4.5]]
            for p in puffs { b.circle(p[0], p[1], p[2]).fill("#EDE7DA").stroke(ink).width(FINE * 2.0) }
            for p in puffs { b.circle(p[0], p[1], p[2]).fill("#EDE7DA").noStroke() }
        }
        if fx.contains(PoseFx.JUMP_LINES) {
            let cx = (sk.ankleL.x + sk.ankleR.x) / 2.0
            for (dx, ang) in [(Float(-16.0), Float(-20.0)), (Float(0.0), Float(0.0)), (Float(16.0), Float(20.0))] {
                let p = P(cx + dx, 186.0)
                taper(p, p + dirOf(ang) * 9.0, w0: 1.6, w1: 1.0, w2: 0.3, color: ink)
            }
        }
        if fx.contains(PoseFx.FALL_LINES) {
            let cx = sk.pelvis.x
            for (dx, r) in [(Float(-18.0), Float(4.0)), (Float(-8.0), Float(5.5)), (Float(4.0), Float(4.5))] {
                b.circle(cx + dx, 193.0, r).fill("#EDE7DA").stroke(ink).width(FINE * 2.0)
            }
            for (dx, r) in [(Float(-18.0), Float(4.0)), (Float(-8.0), Float(5.5)), (Float(4.0), Float(4.5))] { b.circle(cx + dx, 193.0, r).fill("#EDE7DA").noStroke() }
        }
    }

    /** Darbe yıldızı. */
    func impact(_ c: P, _ r: Float) {
        func jag(_ ro: Float, _ ri: Float, _ n: Int, _ rot: Float) -> [P] { (0..<n * 2).map { i in
            let a = toRadians(Double(rot) + 180.0 * Double(i) / Double(n))
            let rr: Float = (i % 2 == 0 ? ro * ((i % 4 == 0 ? 1.0 : 0.82)) : ri)
            return P(c.x + rr * kCos(a).toFloat(), c.y + rr * kSin(a).toFloat())
        } }
        fill(CK.GOLD, MID) { points(jag(r, r * 0.5, 10, 8.0), closed: true) }
        spot(CK.WHITE) { points(jag(r * 0.55, r * 0.28, 8, 20.0), closed: true) }
    }

    /** Ter damlası (ucu yukarıda). */
    func drop(_ cx: Float, _ cy: Float, _ s: Float) {
        fill(CK.TEAR, FINE) {
            path(closed: true) {
                moveTo(cx, cy - s * 2.1)
                cubicTo(cx + s * 0.35, cy - s * 1.2, cx + s, cy - s * 0.6, cx + s, cy)
                arcTo(cx, cy, s, s, 0.0, 180.0)
                cubicTo(cx - s, cy - s * 0.6, cx - s * 0.35, cy - s * 1.2, cx, cy - s * 2.1)
            }
        }
        spot(CK.WHITE) { ellipse(cx - s * 0.35, cy - s * 0.2, s * 0.25, s * 0.4) }
    }
}

