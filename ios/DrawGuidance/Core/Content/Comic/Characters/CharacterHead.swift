import Foundation



/*
 * v37 baş çizimi (anime / çizgi roman). Baş kendi çerçevesinde çizilir: merkez kafatası, birim baş yarıçapı (R=1),
 * y aşağı; çene ucu y≈1.1. Yüz parçaları baş dönünce ([PoseSpec.turn]) o yana kayar, uzaktaki göz daralır.
 * Kaşlar saçın üstünde çizilir (anime geleneği: ifade perçemin altında kalmaz).
 */

/** Saç tutamı: kök (rx, ry) → sivri uç (tx, ty), kök genişliği [w], [bend] eğrilik (boyun oranı). */
struct Lk {
    let rx: Float
    let ry: Float
    let tx: Float
    let ty: Float
    let w: Float
    let bend: Float
    init(_ rx: Float, _ ry: Float, _ tx: Float, _ ty: Float, _ w: Float, _ bend: Float = 0.0) {
        self.rx = rx
        self.ry = ry
        self.tx = tx
        self.ty = ty
        self.w = w
        self.bend = bend
    }
}

/**
 * Saç biçiminin tarifi: [back] başın arkasındaki tutamlar (sivri siluet), [capV] tepe kütlesinin hacmi (null: tepe yok),
 * [line] alın saç çizgisi, [bangs] perçem, [side] yüzü çerçeveleyen yan tutamlar, [shine] parlak halka.
 */
struct HairDef {
    let back: [Lk]
    let capV: Float?
    let capLow: Float
    let line: Float
    let bangs: [Lk]
    let side: [Lk]
    let shine: Bool
    init(back: [Lk] = [], capV: Float? = 0.12, capLow: Float = 0.1, line: Float = -0.45, bangs: [Lk] = [], side: [Lk] = [], shine: Bool = true) {
        self.back = back
        self.capV = capV
        self.capLow = capLow
        self.line = line
        self.bangs = bangs
        self.side = side
        self.shine = shine
    }
}

/** Kökleri başın ortasına doğru, uçları dışa bakan sivri tutamlar (diken saç). */
func spikes(_ tips: Float..., w: Float, k: Float = 0.33, dy: Float = -0.2, bend: Float = 0.08) -> [Lk] {
    (0..<(tips.count / 2)).map { i in let tx = tips[i * 2]; let ty = tips[i * 2 + 1]; return Lk(tx * k, ty * k + dy, tx, ty, w, i % 2 == 0 ? bend : -bend) }
}

/** Perçem: uçlar verilir, kökler saç çizgisinin üstünde. */
func bangs(_ tips: Float..., rootY: Float = -0.66, w: Float = 0.42, bend: Float = 0.1, k: Float = 0.75) -> [Lk] {
    (0..<(tips.count / 2)).map { i in let tx = tips[i * 2]; let ty = tips[i * 2 + 1]; return Lk(tx * k, rootY, tx, ty, w, tx < 0.0 ? -bend : bend) }
}

let hairDefs: [Hair: HairDef] = [Hair.SPIKY_KID: HairDef(
        back: spikes(-1.55, -0.2, -1.5, -0.78, -1.12, -1.32, -0.45, -1.72, 0.3, -1.75, 0.98, -1.45, 1.42, -0.95, 1.6, -0.3, w: 0.82),
        capV: 0.14,
        bangs: bangs(-0.92, 0.04, -0.62, -0.14, -0.3, 0.0, 0.05, -0.17, 0.38, -0.02, 0.7, -0.14, 0.96, 0.1)
    ), Hair.PONYTAIL: HairDef(
        capV: 0.1,
        bangs: bangs(-0.8, -0.02, -0.5, 0.06, -0.2, 0.0, 0.12, 0.07, 0.42, 0.0, 0.72, -0.06, w: 0.38, bend: 0.06, k: 0.8),
        side: [Lk(-0.9, -0.45, -0.95, 0.95, 0.3, -0.06), Lk(0.9, -0.45, 0.94, 0.92, 0.3, 0.06)]
    ), Hair.PARTED: HairDef(
        back: spikes(-1.14, -0.5, -0.92, -1.08, -0.38, -1.32, 0.28, -1.34, 0.86, -1.14, 1.16, -0.55, w: 0.62, k: 0.45, dy: -0.12, bend: 0.05),
        capV: 0.08,
        capLow: -0.12,
        bangs: [
    Lk(0.3, -0.8, -0.98, -0.1, 0.55, 0.12),
    Lk(0.35, -0.75, -0.6, -0.3, 0.5, 0.1),
    Lk(0.4, -0.7, -0.25, -0.33, 0.45, 0.08),
    Lk(0.45, -0.75, 0.98, -0.05, 0.45, -0.1),
    Lk(0.5, -0.7, 0.72, -0.3, 0.35, -0.05)
],
        side: [Lk(-1.0, -0.35, -0.96, 0.08, 0.16), Lk(1.0, -0.35, 0.96, 0.08, 0.16)]
    ), Hair.TUFTS: HairDef(
        capV: nil,
        shine: false
    ), Hair.BUN: HairDef(
        capV: 0.1,
        bangs: [
    Lk(-0.05, -0.85, -0.95, -0.12, 0.5, -0.12),
    Lk(-0.1, -0.8, -0.55, -0.3, 0.4, -0.08),
    Lk(0.05, -0.85, 0.95, -0.12, 0.5, 0.12),
    Lk(0.1, -0.8, 0.55, -0.3, 0.4, 0.08)
],
        side: [Lk(-0.92, -0.4, -0.86, 0.98, 0.22, -0.08), Lk(0.92, -0.4, 0.86, 0.98, 0.22, 0.08)]
    ), Hair.WAVY: HairDef(
        capV: 0.18,
        bangs: [
    Lk(-0.45, -0.85, 0.95, 0.05, 0.62, -0.15),
    Lk(-0.4, -0.8, 0.5, -0.16, 0.5, -0.1),
    Lk(-0.5, -0.8, 0.1, -0.28, 0.4, -0.05),
    Lk(-0.5, -0.8, -0.95, -0.05, 0.45, 0.1)
],
        side: [Lk(-0.95, -0.4, -1.12, 1.25, 0.38, -0.12), Lk(0.95, -0.4, 1.12, 1.25, 0.38, 0.12)]
    ), Hair.UNDER_CAP: HairDef(
        capV: 0.05,
        capLow: -0.1,
        bangs: [Lk(-0.45, -0.55, -0.42, -0.15, 0.3), Lk(-0.05, -0.6, 0.02, -0.12, 0.32), Lk(0.38, -0.55, 0.45, -0.16, 0.3)],
        side: [Lk(-1.0, -0.45, -1.0, 0.3, 0.25), Lk(1.0, -0.45, 1.0, 0.3, 0.25)],
        shine: false
    ), Hair.FORELOCK: HairDef(
        capV: 0.06,
        capLow: -0.1,
        bangs: [Lk(0.0, -0.65, 0.55, 0.08, 0.42, 0.12), Lk(0.25, -0.65, 0.85, -0.02, 0.36, 0.1), Lk(-0.3, -0.6, -0.6, -0.2, 0.3, -0.05)],
        side: [Lk(-1.0, -0.45, -0.97, 0.1, 0.2), Lk(1.0, -0.45, 0.98, 0.1, 0.2)],
        shine: false
    ), Hair.CROPPED: HairDef(
        capV: 0.04,
        capLow: -0.15,
        bangs: [Lk(-0.3, -0.6, -0.3, -0.32, 0.3), Lk(0.05, -0.62, 0.1, -0.34, 0.3), Lk(0.4, -0.6, 0.42, -0.33, 0.28)],
        side: [Lk(-1.0, -0.5, -0.99, 0.15, 0.22), Lk(1.0, -0.5, 0.99, 0.15, 0.22)],
        shine: false
    ), Hair.SPIKY_UP: HairDef(
        back: spikes(-1.35, -0.6, -1.05, -1.3, -0.5, -1.72, 0.12, -1.85, 0.72, -1.62, 1.18, -1.22, 1.38, -0.6, w: 0.72, k: 0.3, dy: -0.25),
        capV: 0.1,
        bangs: [
    Lk(-0.72, -0.6, -0.82, -0.2, 0.35, -0.06),
    Lk(-0.35, -0.6, -0.42, -0.1, 0.38),
    Lk(0.02, -0.62, 0.05, -0.18, 0.38),
    Lk(0.4, -0.6, 0.48, -0.12, 0.36),
    Lk(0.75, -0.6, 0.86, -0.24, 0.32, 0.06)
]
    ), Hair.SWEPT_BACK: HairDef(
        back: spikes(-1.42, -0.45, -1.32, -1.05, -0.9, -1.48, -0.25, -1.68, 0.45, -1.62, 1.05, -1.3, 1.42, -0.75, 1.48, -0.2, w: 0.8, k: 0.35, bend: -0.1),
        capV: 0.14,
        line: -0.62,
        bangs: [Lk(-0.05, -0.85, -0.32, -0.16, 0.3, -0.15), Lk(0.2, -0.9, 0.78, -0.42, 0.36, 0.05), Lk(-0.3, -0.9, -0.86, -0.38, 0.36, -0.05)]
    ), Hair.BOB: HairDef(
        capV: 0.16,
        bangs: (0..<7).map { i in let x: Float = -0.8 + Float(i) * 0.267; return Lk(x * 0.9, -0.7, x, -0.1 + (i % 2 == 0 ? 0.03 : -0.02), 0.36) },
        side: [Lk(-0.98, -0.45, -1.0, 0.95, 0.42, -0.04), Lk(0.98, -0.45, 1.0, 0.95, 0.42, 0.04)]
    ), Hair.KNIGHT: HairDef(
        back: spikes(-1.38, 0.4, -1.45, -0.3, -1.2, -0.98, -0.62, -1.45, 0.1, -1.58, 0.78, -1.4, 1.25, -0.95, 1.45, -0.25, 1.35, 0.45, w: 0.75, k: 0.3, dy: -0.15, bend: 0.06),
        capV: 0.13,
        bangs: bangs(-0.92, 0.02, -0.62, -0.13, -0.28, 0.04, 0.08, -0.12, 0.42, 0.02, 0.74, -0.12, 0.96, 0.12, k: 0.7),
        side: [Lk(-0.96, -0.35, -0.98, 0.7, 0.3, -0.08), Lk(0.96, -0.35, 0.98, 0.7, 0.3, 0.08)]
    ), Hair.LONG: HairDef(
        capV: 0.08,
        capLow: 0.2,
        side: [Lk(-0.98, -0.45, -1.15, 1.3, 0.36, -0.1), Lk(0.98, -0.45, 1.15, 1.3, 0.36, 0.1)],
        shine: false
    ), Hair.NONE: HairDef(
        capV: nil,
        shine: false
    )]

/** Tutamın kapalı biçimi (yerel çerçevede). */
extension Fig {
func lockShape(_ l: Lk, _ dx: Float = 0.0, _ dy: Float = 0.0) -> () -> VectorBuilder.Part { { [self] in
    let rx = l.rx + dx; let ry = l.ry + dy; let tx = l.tx + dx; let ty = l.ty + dy
    let ddx = tx - rx; let ddy = ty - ry
    let len = max(1e-3, kHypot(ddx, ddy))
    let nx = -ddy / len; let ny = ddx / len
    let mx = rx + ddx * 0.5 + nx * l.bend * len; let my = ry + ddy * 0.5 + ny * l.bend * len
    return path(closed: true) {
        moveTo(rx + nx * l.w / 2.0, ry + ny * l.w / 2.0)
        quadTo(mx + nx * l.w * 0.32, my + ny * l.w * 0.32, tx, ty)
        quadTo(mx - nx * l.w * 0.32, my - ny * l.w * 0.32, rx - nx * l.w / 2.0, ry - ny * l.w / 2.0)
    }
} }
}

/** Tepe kütlesi: kafatasını [v] kadar aşar; alt kenarı ([line]) perçemin altında kalır. */
extension Fig {
func capShape(_ v: Float, _ low: Float, _ line: Float) -> () -> VectorBuilder.Part { { [self] in
    smooth(
        -1.04 - v * 0.5, low, -1.02 - v, -0.45, -0.74 - v * 0.6, -0.97 - v * 0.55, 0.0, -1.04 - v, 0.74 + v * 0.6, -0.97 - v * 0.55,
        1.02 + v, -0.45, 1.04 + v * 0.5, low, 0.7, line + 0.15, 0.0, line, -0.7, line + 0.15, closed: true
    )
} }
}

/** Baş çerçevesi (birim baş yarıçapı). */
extension Fig {
func headFrame(_ block: () -> Void) { frame(sk.head, sk.headAngle, R, block) }
}

extension Fig { var def: HairDef { hairDefs[look.hair]! } }

/** Yüz parçalarının dönüşle kayması. */
extension Fig { var fs: Float { pose.turn * 0.16 } }

// ------------------------------------------------------------------ saç: vücudun arkası

/** Vücudun arkasına düşen saç (at kuyruğu, omuz boyu dalgalı saç, uzun ak saç). */
extension Fig {
func hairBehindBody() {
    let c = look.hairColor
    switch look.hair {
case Hair.PONYTAIL: 
headFrame {
            // Tepeden arkaya, sağ omuza doğru savrulan at kuyruğu
            let tail: [Float] = [0.1, -1.05, 0.95, -1.15, 1.5, -0.45, 1.58, 0.7, 1.32, 1.85, 1.12, 2.45, 0.98, 1.65, 1.06, 0.55, 0.82, -0.38, 0.3, -0.72]
            union(c, OUT, [{ [self] in smooth(tail, closed: true, tension: 0.5) }])
            taper([P(0.9, -0.85), P(1.32, -0.1), P(1.32, 1.1), P(1.15, 2.1)], 0.02, 0.06, 0.01, CK.shade(c))
            taper([P(0.7, -0.7), P(1.1, 0.0), P(1.18, 1.0)], 0.02, 0.05, 0.01, CK.shade(c))
            taper([P(1.25, -0.55), P(1.45, 0.2), P(1.4, 0.9)], 0.02, 0.07, 0.01, CK.light(c), 0.8)
        }
case Hair.WAVY: 
headFrame {
            let mass: [Float] = [-1.15, -0.4, -1.32, 0.55, -1.38, 1.3, -1.22, 1.9, -0.92, 1.66, -0.62, 1.95, 0.0, 1.7, 0.62, 1.95, 0.92, 1.66, 1.22, 1.9, 1.38, 1.3, 1.32, 0.55, 1.15, -0.4, 0.0, -1.1]
            union(c, OUT, [{ [self] in smooth(mass, closed: true, tension: 0.5) }])
            spot(CK.shade(c)) { smooth(-0.9, 0.0, -1.05, 1.0, -0.7, 1.6, 0.0, 1.5, 0.7, 1.6, 1.05, 1.0, 0.9, 0.0, closed: true) }
        }
case Hair.LONG: 
headFrame {
            let mass: [Float] = [-1.15, -0.4, -1.45, 0.8, -1.55, 2.0, -1.3, 2.5, -0.9, 2.3, -0.4, 2.5, 0.4, 2.5, 0.9, 2.3, 1.3, 2.5, 1.55, 2.0, 1.45, 0.8, 1.15, -0.4, 0.0, -1.1]
            union(c, OUT, [{ [self] in smooth(mass, closed: true, tension: 0.5) }])
            for x in ([-1.25, -0.8, 0.8, 1.25] as [Float]) { taper([P(x * 0.9, 0.4), P(x, 1.4), P(x * 1.02, 2.2)], 0.02, 0.05, 0.01, CK.shade(c)) }
        }
default: break
}
}
}

// ------------------------------------------------------------------ baş

extension Fig {
func headPart() {
    headFrame {
        hairBehindHead()
        if robot { robotHead(); return }
        ears()
        neckShadow()
        let skin = look.skin
        fill(skin, OUT, faceOutline())
        faceShade()
        if look.extras.contains(Extra.MASK) { mask() }
        foreheadShadow()
        eyes()
        nose()
        if look.extras.contains(Extra.BEARD) { beard() }
        mouth()
        cheeks()
        if look.extras.contains(Extra.MUSTACHE) { mustache() }
        if look.extras.contains(Extra.CHEF_MUSTACHE) { chefMustache() }
        hairFront()
        brows()
        if look.extras.contains(Extra.GLASSES) || look.extras.contains(Extra.ROUND_GLASSES) { glasses() }
        hats()
        overlays()
        if look.extras.contains(Extra.SPACE_HELMET) { spaceHelmet() }
    }
}
}

/** Yüz dış hattı: kafatası, yanaklar, çene (çene tipine göre). */
extension Fig {
func faceOutline() -> () -> VectorBuilder.Part {
    let cx = pose.turn * 0.1
    let j: [Float] = switch body.jaw {
case Jaw.ROUND: [0.94, 0.48, 0.26, 1.02]
case Jaw.SOFT: [0.87, 0.55, 0.1, 1.1]
case Jaw.SHARP: [0.86, 0.6, 0.08, 1.13]
case Jaw.SQUARE: [0.91, 0.7, 0.3, 1.1]
case Jaw.OLD: [0.9, 0.62, 0.2, 1.08]
}
    let jx: Float = j[0]; let jy: Float = j[1]; let cw: Float = j[2]; let cy: Float = j[3]
    return { [self] in
        path(closed: true) {
            moveTo(-1.0, -0.05)
            cubicTo(-1.0, -0.6, -0.56, -1.02, 0.0, -1.02)
            cubicTo(0.56, -1.02, 1.0, -0.6, 1.0, -0.05)
            cubicTo(1.0, 0.22, jx + 0.06, jy - 0.18, jx, jy)
            cubicTo(jx - 0.1, jy + 0.2, cx + cw + 0.25, cy - 0.04, cx + cw, cy)
            lineTo(cx - cw, cy)
            cubicTo(cx - cw - 0.25, cy - 0.04, -jx + 0.1, jy + 0.2, -jx, jy)
            cubicTo(-jx - 0.06, jy - 0.18, -1.0, 0.22, -1.0, -0.05)
        }
    }
}
}

/** Yüzün sağ yanındaki cel gölge (elmacık altından çeneye). */
extension Fig {
func faceShade() {
    let cx = pose.turn * 0.1
    spot(CK.skinShade(look.skin)) {
        path(closed: true) {
            moveTo(0.99, 0.05)
            cubicTo(0.99, 0.3, 0.92, 0.48, 0.84, 0.62)
            cubicTo(0.72, 0.85, cx + 0.45, 1.02, cx + 0.15, 1.08)
            quadTo(cx + 0.5, 0.86, 0.76, 0.42)
            quadTo(0.86, 0.2, 0.99, 0.05)
        }
    }.alpha(0.7)
}
}

extension Fig {
func ears() {
    if look.extras.contains(Extra.SPACE_HELMET) && look.hair == Hair.BOB { return }
    let skin = look.skin
    for sx in ([-1.0, 1.0] as [Float]) {
        let x = sx * 0.97 - pose.turn * 0.12
        fill(skin, MID) { ellipse(x, 0.14, 0.15, 0.24) }
        line(CK.skinShade(skin), FINE) { arc(x, 0.15, 0.07, 0.13, (sx < 0 ? 100.0 : -80.0), 160.0) }
    }
}
}

/** Çenenin boyna düşen gölgesi (boyun çerçevesinde, boyun genişliğinde). */
extension Fig {
func neckShadow() {
    if look.extras.contains(Extra.SPACE_HELMET) { return }
    let nr = body.neckR / R * 0.88
    spot(CK.skinShade(look.skin)) { path(closed: true) { moveTo(-nr, 0.4); lineTo(nr, 0.4); lineTo(nr, 1.18); quadTo(0.0, 1.42, -nr, 1.18) } }
}
}

/** Perçemin alna düşen gölgesi. */
extension Fig {
func foreheadShadow() {
    let d = def
    if d.capV == nil || look.hair == Hair.UNDER_CAP { return }
    for l in d.bangs { if abs(l.tx) < 0.8 { spot(CK.skinShade(look.skin), lockShape(l, 0.02, 0.1)).alpha(0.8) }}
}
}

// ------------------------------------------------------------------ saç: başın arkası ve önü

extension Fig {
func hairBehindHead() {
    let c = look.hairColor
    let d = def
    if !d.back.isEmpty {
        union(c, OUT, d.back.map { lockShape($0) })
        // İç gölge: arka tutamların köke yakın kısmı koyu
        for l in d.back { taper([P(l.rx, l.ry), P(l.rx + (l.tx - l.rx) * 0.75, l.ry + (l.ty - l.ry) * 0.75)], 0.02, 0.1, 0.01, CK.shade(c), 0.9) }
    }
    switch look.hair {
case Hair.BUN: 
union(c, OUT, [{ [self] in circle(0.05, -1.18, 0.44) }])
            taper([P(-0.2, -1.4), P(0.05, -1.5), P(0.3, -1.38)], 0.02, 0.06, 0.01, CK.shade(c))
            fill(CK.GOLD, FINE) { poly(-0.62, -1.5, 0.62, -0.88, 0.6, -0.84, -0.64, -1.46, closed: true) }
            fill(CK.GOLD, FINE) { circle(-0.62, -1.49, 0.07) }
case Hair.BOB: union(c, OUT, [{ [self] in smooth(-1.2, -0.3, -1.25, 0.45, -1.12, 0.96, -0.8, 1.02, 0.8, 1.02, 1.12, 0.96, 1.25, 0.45, 1.2, -0.3, 0.0, -1.12, closed: true) }])
case Hair.TUFTS: 
// Kulak üstünde kabarık ak tutamlar
            for sx in ([-1.0, 1.0] as [Float]) { union(c, MID, [
    { [self] in circle(sx * 0.98, -0.28, 0.2) },
    { [self] in circle(sx * 1.08, -0.02, 0.22) },
    { [self] in circle(sx * 0.98, 0.22, 0.17) }
]) }
default: break
}
    if look.extras.contains(Extra.HEADBAND) {
        // Alın bandının arkada uçuşan uçları
        let a = look.accent
        fill(a, MID) { path(closed: true) { moveTo(-0.9, -0.5); cubicTo(-1.3, -0.6, -1.6, -0.4, -1.95, -0.55); cubicTo(-1.7, -0.25, -1.35, -0.3, -0.95, -0.3) } }
        fill(a, MID) { path(closed: true) { moveTo(-0.92, -0.42); cubicTo(-1.3, -0.3, -1.5, 0.0, -1.85, 0.05); cubicTo(-1.5, 0.2, -1.2, 0.0, -0.95, -0.25) } }
    }
}
}

extension Fig {
func hairFront() {
    let c = look.hairColor
    let d = def
    var shapes = [() -> VectorBuilder.Part]()
    if let v = d.capV { shapes.append(capShape(v, d.capLow, d.line)) }
    d.side.forEach { shapes.append(lockShape($0)) }
    d.bangs.forEach { shapes.append(lockShape($0)) }
    if shapes.isEmpty {
        if look.hair == Hair.TUFTS {
            // Kel tepe: parıltı ve tek tük tel
            spot(CK.WHITE) { ellipse(-0.35, -0.72, 0.28, 0.12, -25.0) }.alpha(0.55)
            taper([P(-0.05, -1.0), P(0.05, -1.18), P(0.2, -1.14)], 0.02, 0.04, 0.01, CK.INK)
            taper([P(0.1, -1.01), P(0.25, -1.15), P(0.38, -1.08)], 0.02, 0.035, 0.01, CK.INK)
        }
        return
    }
    union(c, OUT, shapes)
    // Tutam gölgeleri: her perçem tutamının ortasında koyu ince çizgi
    for l in d.bangs + d.side {
        let m = P(l.rx + (l.tx - l.rx) * 0.15, l.ry + (l.ty - l.ry) * 0.15)
        let e = P(l.rx + (l.tx - l.rx) * 0.82, l.ry + (l.ty - l.ry) * 0.82)
        let mid = P((m.x + e.x) / 2.0 - (e.y - m.y) * l.bend * 0.6, (m.y + e.y) / 2.0 + (e.x - m.x) * l.bend * 0.6)
        taper([m, mid, e], 0.02, l.w * 0.22, 0.01, CK.shade(c))
    }
    // Tutam ayrımları: perçem tutamlarının iki kenarı uçtan köke doğru incelen mürekkep çizgisi (sivri anime perçemi)
    for l in d.bangs {
        let ddx = l.tx - l.rx; let ddy = l.ty - l.ry
        let len = max(1e-3, kHypot(ddx, ddy))
        let nx = -ddy / len; let ny = ddx / len
        let mx = l.rx + ddx * 0.5 + nx * l.bend * len; let my = l.ry + ddy * 0.5 + ny * l.bend * len
        for sd in ([1.0, -1.0] as [Float]) {
            let ax = l.rx + Float(sd) * nx * l.w / 2.0; let ay = l.ry + Float(sd) * ny * l.w / 2.0
            let cx = mx + Float(sd) * nx * l.w * 0.32; let cy = my + Float(sd) * ny * l.w * 0.32
            let pts = (0...6).map { i in
                let t: Float = 1 - Float(i) * 0.09
                let u: Float = 1 - t
                return P(u * u * ax + 2.0 * u * t * cx + t * t * l.tx, u * u * ay + 2.0 * u * t * cy + t * t * l.ty)
            }
            taper(pts, 0.012, 0.032, 0.004, CK.INK, 0.9)
        }
    }
    // Saç çizgisinde koyu gölge bandı (tepe ile perçem arası)
    if d.capV != nil && !d.bangs.isEmpty { taper([P(-0.75, d.line + 0.02), P(0.0, d.line - 0.1), P(0.75, d.line + 0.02)], 0.01, 0.07, 0.01, CK.shade(c), 0.8) }
    // Parlak halka (anime): tepede zikzak açık bant
    if d.shine && d.capV != nil {
        let v = d.capV!
        let outer = (0...10).map { i in let a = toRadians(222.0 + Double(i) * 9.6); return P((0.93 + v * 0.5) * kCos(a).toFloat(), -0.05 + (0.93 + v * 0.5) * kSin(a).toFloat()) }
        let inner = (0...10).reversed().map { i in
            let a = toRadians(222.0 + Double(i) * 9.6)
            let r: Float = 0.83 + v * 0.5 + (i % 2 == 0 ? 0.04 : -0.05)
            return P(r * kCos(a).toFloat(), -0.05 + r * kSin(a).toFloat())
        }
        spot(CK.mix(c, "#FFFFFF", 0.38)) { points(outer + inner, closed: true) }.alpha(0.75)
        taper([P(-0.42, -0.84), P(-0.3, -0.9)], 0.01, 0.04, 0.01, CK.WHITE, 0.9)
        taper([P(0.18, -0.93), P(0.32, -0.9)], 0.01, 0.035, 0.01, CK.WHITE, 0.9)
    }
    switch look.hair {
case Hair.PARTED: taper([P(0.38, -1.08), P(0.34, -0.9), P(0.3, -0.76)], 0.01, 0.03, 0.01, CK.deep(c))
case Hair.PONYTAIL: if look.extras.contains(Extra.RIBBON) { bow(0.62, -1.0, look.accent, 0.075) }
case Hair.WAVY: 
if look.extras.contains(Extra.HAIRCLIP) {
            fill(look.accent, FINE) { roundRect(-0.78, -0.6, 0.36, 0.1, 0.05) }
            fill(look.accent, FINE) { roundRect(-0.72, -0.47, 0.36, 0.1, 0.05) }
        }
default: break
}
}
}

// ------------------------------------------------------------------ gözler

/** Göz ölçüleri (yarı en, üst ve alt yükseklik, iris yarıçapları, kirpik kalınlığı). */
struct EyeGeo {
    let w: Float
    let up: Float
    let low: Float
    let irx: Float
    let iry: Float
    let lash: Float
    init(_ w: Float, _ up: Float, _ low: Float, _ irx: Float, _ iry: Float, _ lash: Float) {
        self.w = w
        self.up = up
        self.low = low
        self.irx = irx
        self.iry = iry
        self.lash = lash
    }
}

extension Fig {
func eyeGeo() -> EyeGeo { switch look.eyes {
case EyeStyle.FEMALE: EyeGeo(0.3, 0.4, 0.22, 0.22, 0.3, 0.12)
case EyeStyle.MALE: EyeGeo(0.29, 0.3, 0.17, 0.19, 0.25, 0.105)
case EyeStyle.KID: EyeGeo(0.31, 0.4, 0.26, 0.23, 0.31, 0.115)
case EyeStyle.COMIC: EyeGeo(0.27, 0.23, 0.14, 0.145, 0.19, 0.1)
case EyeStyle.ELDER: EyeGeo(0.22, 0.17, 0.11, 0.12, 0.14, 0.08)
case EyeStyle.SCREEN: EyeGeo(0.25, 0.25, 0.15, 0.15, 0.2, 0.1)
} }
}

let EY: Float = 0.22

/** Eğri (u → v) üzerinde doğrusal ara değer; [pts] u'ya göre sıralı. */
func interp(_ pts: [P], _ u: Float) -> Float {
    if u <= pts.first!.x { return pts.first!.y }
    for i in 1..<pts.count { if u <= pts[i].x {
        let a = pts[i - 1]; let c = pts[i]
        let t = (c.x - a.x < 1e-6 ? 0.0 : (u - a.x) / (c.x - a.x))
        return a.y + (c.y - a.y) * t
    } }
    return pts.last!.y
}

extension Fig {
func eyes() {
    eye(false, face.eye)
    eye(true, face.eyeR ?? face.eye)
}
}

extension Fig {
func eye(_ right: Bool, _ shape: EyeShape) {
    let g = eyeGeo()
    let f = face
    let o: Float = (right ? 1.0 : -1.0)
    let turn = pose.turn
    let far = (turn > 0.1 && right) || (turn < -0.1 && !right)
    let w = g.w * ((far ? 1.0 - 0.3 * abs(turn) : 1.0))
    let ex = o * 0.42 + fs
    let ey = EY
    var up = g.up; var low = g.low; var irisK = f.iris
    if shape == EyeShape.WIDE || shape == EyeShape.TINY { up *= 1.15; low *= 1.35; irisK *= 0.78 }
    let lashC = (look.eyes == EyeStyle.ELDER ? CK.mix(CK.LASH, look.skin, 0.25) : CK.LASH)
    func X(_ u: Float) -> Float { ex + o * u * w }

    switch shape {
case EyeShape.HAPPY: 
taper([P(X(-1.0), ey + 0.06), P(X(-0.4), ey - 0.13), P(X(0.35), ey - 0.15), P(X(1.05), ey + 0.02)], 0.03, g.lash * 0.85, 0.03, lashC)
            taper([P(X(0.9), ey - 0.02), P(X(1.2), ey - 0.1)], 0.03, 0.04, 0.01, lashC)
            return
case EyeShape.CLOSED: 
taper([P(X(-1.0), ey - 0.02), P(X(0.0), ey + 0.1), P(X(1.05), ey - 0.04)], 0.03, g.lash * 0.8, 0.03, lashC)
            for i in 0...1 { taper([P(X(0.45 + Float(i) * 0.3), ey + 0.07 - Float(i) * 0.03), P(X(0.55 + Float(i) * 0.35), ey + 0.16 - Float(i) * 0.02)], 0.02, 0.025, 0.005, lashC) }
            return
case EyeShape.SQUINT: 
taper([P(X(1.0), ey - 0.15), P(X(-0.75), ey + 0.02)], 0.03, g.lash * 0.8, 0.05, lashC)
            taper([P(X(-0.75), ey + 0.02), P(X(1.0), ey + 0.17)], 0.05, g.lash * 0.7, 0.02, lashC)
            return
default: break
}

    // Göz kapakları: üst ve alt eğri (u: -1 iç köşe .. 1 dış köşe; v: göz merkezine göre)
    let upperBase = VectorBuilder.catmullRom([P(-1.0, 0.05 * up), P(-0.6, -0.8 * up), P(0.05, -up), P(0.6, -0.88 * up), P(1.0, -0.32 * up)], false, 0.5, steps: 6)
    let lowerBase = VectorBuilder.catmullRom([P(-1.0, 0.05 * up), P(-0.6, 0.8 * low), P(0.0, low), P(0.6, 0.75 * low), P(1.0, -0.32 * up)], false, 0.5, steps: 6)
    var lidIn = f.lidIn; var lidOut = f.lidOut; var lowerLid = f.lowerLid
    if shape == EyeShape.HALF { lidIn = max(lidIn, 0.45); lidOut = max(lidOut, 0.45); lowerLid = max(lowerLid, 0.2) }
    let us = (0...16).map { Float(-1.0) + Float($0) / 8.0 }
    let upper = us.map { u in
        let base = interp(upperBase, u)
        let t = (u + 1.0) / 2.0
        let lidLine = -up * (1.0 - (lidIn + (lidOut - lidIn) * t))
        return P(u, max(base, lidLine))
    }
    let lower = us.map { u in
        let base = interp(lowerBase, u)
        let raised = base - low * lowerLid * (1.0 - Float(u) * u)
        return P(u, max(raised, interp(upper, u) + 0.03))
    }
    func upY(_ u: Float) -> Float { interp(upper, u) }
    func lowY(_ u: Float) -> Float { interp(lower, u) }
    func clamp(_ x: Float, _ y: Float, maxY: Float = Float.greatestFiniteMagnitude, minY: Float = -Float.greatestFiniteMagnitude) -> P {
        let u = ((x - ex) / (o * w)).coerceIn(-1.0, 1.0)
        let yy = y.coerceIn(ey + upY(u), ey + lowY(u)).coerceIn(minY, maxY)
        return P(X(u), yy)
    }
    func clipped(_ cx: Float, _ cy: Float, _ rx: Float, _ ry: Float, maxY: Float = Float.greatestFiniteMagnitude, minY: Float = -Float.greatestFiniteMagnitude) -> [P] {
        (0..<36).map { i in let a = Double.pi * 2 * Double(i) / 36; return clamp(cx + rx * kCos(a).toFloat(), cy + ry * kSin(a).toFloat(), maxY: maxY, minY: minY) }
    }

    // Akı ve üst kapak gölgesi
    let white = upper.map { P(X($0.x), ey + $0.y) } + lower.reversed().map { P(X($0.x), ey + $0.y) }
    spot(CK.EYE_WHITE) { points(white, closed: true) }
    spot("#D8D4EC") { points(upper.map { P(X($0.x), ey + $0.y) } + upper.reversed().map { P(X($0.x), ey + min($0.y + 0.07, interp(lower, $0.x))) }, closed: true) }

    let irx = g.irx * irisK; let iry = g.iry * irisK
    let ix = ex + f.gazeX * w * 0.42 + turn * 0.05
    let iy = ey + 0.01 + f.gazeY * 0.08
    if shape != EyeShape.TINY {
        let ec = look.eyeColor
        spot(ec) { points(clipped(ix, iy, irx, iry), closed: true) }
        spot(CK.mix(ec, "#120A22", 0.5)) { points(clipped(ix, iy, irx, iry, maxY: iy - iry * 0.12), closed: true) }
        spot(CK.mix(ec, "#FFFFFF", 0.42)) { points(clipped(ix, iy + iry * 0.4, irx * 0.68, iry * 0.48), closed: true) }.alpha(0.9)
        spot(CK.PUPIL) { points(clipped(ix, iy + iry * 0.04, irx * 0.42, iry * 0.5), closed: true) }
        b.points(clipped(ix, iy, irx, iry), closed: true).fill("@none").stroke(CK.mix(ec, "#120A22", 0.65)).width(0.5)
        if shape == EyeShape.STAR {
            spot(CK.WHITE) { star(ix, iy, irx * 0.85, irx * 0.22, 4) }
            spot(CK.WHITE) { star(ix + irx * 0.55, iy + iry * 0.55, irx * 0.32, irx * 0.09, 4) }
        } else {
            spot(CK.WHITE) { points(clipped(ix - irx * 0.36, iy - iry * 0.38, irx * 0.36, iry * 0.3), closed: true) }
            spot(CK.WHITE) { points(clipped(ix + irx * 0.42, iy + iry * 0.45, irx * 0.14, irx * 0.14), closed: true) }
            if f.tear || f.streams { spot(CK.WHITE) { points(clipped(ix + irx * 0.3, iy - iry * 0.1, irx * 0.16, irx * 0.16), closed: true) }.alpha(0.9) }
        }
    } else {
        spot(CK.PUPIL) { circle(ix, iy + 0.02, 0.045) }
        b.points(white, closed: true).fill("@none").stroke(CK.LASH).width(0.5)
    }

    // Kalın üst kirpik çizgisi ve dış uçtaki kıvrım
    let lashPts = upper.filter { $0.x >= -0.95 }.map { P(X($0.x), ey + $0.y - 0.015) } + [P(X(1.16), ey + upY(1.0) - 0.08)]
    let lw = (shape == EyeShape.TINY ? g.lash * 0.6 : g.lash)
    taper(lashPts, 0.02, lw, 0.03, lashC)
    if look.eyes == EyeStyle.FEMALE {
        taper([P(X(0.82), ey + upY(0.82) - 0.04), P(X(1.05), ey + upY(0.82) - 0.16)], 0.04, 0.03, 0.005, lashC)
        taper([P(X(0.6), ey + upY(0.6) - 0.05), P(X(0.74), ey + upY(0.6) - 0.17)], 0.035, 0.025, 0.005, lashC)
    }
    // Alt kirpik ve kapak çizgisi
    let lowFrom: Float = (look.eyes == EyeStyle.FEMALE || look.eyes == EyeStyle.KID ? -0.3 : 0.25)
    taper(lower.filter { $0.x >= lowFrom }.reversed().map { P(X($0.x), ey + $0.y + 0.01) }, 0.035, 0.022, 0.004, lashC, 0.85)
    if look.eyes != EyeStyle.COMIC { taper(upper.filter { $0.x >= -0.3 }.map { P(X($0.x), ey + $0.y - 0.13) }, 0.004, 0.022, 0.004, CK.mix(look.skin, CK.LASH, 0.45)) }
    if look.eyes == EyeStyle.ELDER {
        // Göz kenarında kırışık
        for i in 0...1 { taper([P(X(1.15), ey - 0.02 + Float(i) * 0.09), P(X(1.38), ey - 0.06 + Float(i) * 0.14)], 0.01, 0.025, 0.005, CK.skinShade(look.skin)) }
    }
    if f.sparkle && shape != EyeShape.STAR { spot(CK.WHITE) { star(X(1.25), ey - up - 0.05, 0.1, 0.025, 4) } }
}
}

// ------------------------------------------------------------------ kaş, burun, ağız, yanak

extension Fig {
func brows() {
    let g = eyeGeo()
    let f = face
    let c = (look.hair == Hair.TUFTS || look.hair == Hair.LONG ? look.hairColor : CK.mix(look.hairColor, CK.INK, 0.55))
    let bushy = look.hair == Hair.TUFTS || look.hair == Hair.LONG
    let thick: Float = switch look.eyes {
case EyeStyle.FEMALE: 0.045
case EyeStyle.COMIC: 0.09
case EyeStyle.KID: 0.07
default: 0.075
}
    for right in [false, true] {
        let o: Float = (right ? 1.0 : -1.0)
        let ex = o * 0.42 + fs
        let w = g.w
        let by = EY - g.up - 0.17
        let raise = (right ? f.browRaiseR : 0.0)
        let inner = P(ex - o * w * 0.85, by + f.browIn - raise * 0.6)
        let outer = P(ex + o * w * 1.12, by + f.browOut + 0.05 - raise)
        let mid = P(ex + o * w * 0.15, by - 0.05 + (f.browIn + f.browOut) * 0.35 - raise * 1.1)
        if bushy {
            union(c, MID, [{ [self] in smooth(inner.x, inner.y + 0.05, mid.x, mid.y - 0.06, outer.x + o * 0.06, outer.y + 0.02, mid.x, mid.y + 0.09, closed: true) }])
            taper([P(mid.x - o * 0.05, mid.y), P(outer.x, outer.y + 0.06)], 0.01, 0.025, 0.005, CK.shade(c))
        } else {
            taper([inner, mid, outer], thick * 0.95, thick, thick * 0.15, c)
        }
    }
}
}

extension Fig {
func nose() {
    let nx = pose.turn * 0.22
    let c = CK.mix(CK.skinShade(look.skin), CK.INK, 0.35)
    switch look.eyes {
case EyeStyle.COMIC: 
taper([P(nx + 0.07, 0.32), P(nx + 0.08, 0.52), P(nx + 0.02, 0.62)], 0.01, 0.03, 0.02, c)
            taper([P(nx - 0.07, 0.62), P(nx + 0.01, 0.65), P(nx + 0.08, 0.62)], 0.01, 0.025, 0.01, c)
case EyeStyle.ELDER: 
taper([P(nx + 0.06, 0.36), P(nx + 0.12, 0.6), P(nx - 0.02, 0.66)], 0.01, 0.04, 0.01, c)
            spot(CK.skinShade(look.skin)) { ellipse(nx + 0.04, 0.6, 0.1, 0.06) }.alpha(0.6)
case EyeStyle.KID: spot(c) { ellipse(nx + 0.01, 0.55, 0.035, 0.022) }
default: 
taper([P(nx + 0.035, 0.47), P(nx + 0.01, 0.58), P(nx - 0.05, 0.61)], 0.01, 0.03, 0.01, c)
            spot(CK.skinShade(look.skin)) { poly(nx + 0.02, 0.45, nx + 0.06, 0.58, nx - 0.01, 0.59, closed: true) }.alpha(0.6)
}
}
}

extension Fig {
func mouth() {
    let mx = pose.turn * 0.18
    let my: Float = switch true {
case _ where look.eyes == EyeStyle.ELDER: 0.9
case _ where body.jaw == Jaw.ROUND: 0.79
default: 0.82
}
    let mw: Float = switch look.eyes {
case EyeStyle.FEMALE: 0.16
case EyeStyle.COMIC: 0.22
default: 0.19
}
    let lc = CK.mix(CK.MOUTH, CK.INK, 0.35)
    func dark(_ shape: () -> VectorBuilder.Part) -> VectorBuilder.Part { fill(CK.MOUTH, FINE, shape) }
    switch face.mouth {
case Mouth.SMILE: 
taper([P(mx - mw, my - 0.03), P(mx, my + 0.045), P(mx + mw, my - 0.04)], 0.015, 0.04, 0.015, lc)
            if look.eyes == EyeStyle.FEMALE { spot(CK.TONGUE) { ellipse(mx, my + 0.07, mw * 0.4, 0.015) }.alpha(0.5) }
case Mouth.LINE: taper([P(mx - mw * 0.8, my), P(mx, my + 0.01), P(mx + mw * 0.8, my - 0.005)], 0.015, 0.035, 0.015, lc)
case Mouth.FROWN: taper([P(mx - mw * 0.9, my + 0.05), P(mx, my - 0.02), P(mx + mw * 0.9, my + 0.05)], 0.015, 0.04, 0.015, lc)
case Mouth.POUT: taper([P(mx - mw * 0.55, my + 0.03), P(mx, my - 0.01), P(mx + mw * 0.55, my + 0.03)], 0.012, 0.03, 0.012, lc)
case Mouth.WAVY: taper([P(mx - mw, my + 0.02), P(mx - mw * 0.5, my - 0.03), P(mx, my + 0.02), P(mx + mw * 0.5, my - 0.03), P(mx + mw, my + 0.02)], 0.012, 0.03, 0.012, lc)
case Mouth.SMIRK: 
taper([P(mx - mw * 0.85, my + 0.02), P(mx + mw * 0.2, my + 0.035), P(mx + mw * 1.05, my - 0.08)], 0.012, 0.04, 0.015, lc)
            taper([P(mx + mw * 1.05, my - 0.11), P(mx + mw * 1.15, my - 0.03)], 0.005, 0.02, 0.005, lc)
case Mouth.O: 
dark { ellipse(mx, my + 0.03, 0.075, 0.095) }
            spot(CK.TONGUE) { ellipse(mx, my + 0.08, 0.045, 0.03) }
case Mouth.OPEN_SMILE, Mouth.LAUGH: 
let k: Float = (face.mouth == Mouth.LAUGH ? 1.45 : 1.0)
            let hw = mw * 1.15 * k; let depth: Float = 0.2 * k
            dark { path(closed: true) { moveTo(mx - hw, my - 0.06); quadTo(mx, my - 0.03, mx + hw, my - 0.06); cubicTo(mx + hw * 0.85, my + depth, mx - hw * 0.85, my + depth, mx - hw, my - 0.06) } }
            spot(CK.TONGUE) { ellipse(mx, my + depth * 0.62, hw * 0.5, depth * 0.22) }
            spot(CK.TEETH) { path(closed: true) { moveTo(mx - hw * 0.8, my - 0.05); quadTo(mx, my - 0.025, mx + hw * 0.8, my - 0.05); lineTo(mx + hw * 0.7, my - 0.005); quadTo(mx, my + 0.015, mx - hw * 0.7, my - 0.005) } }
case Mouth.GRIT: 
fill(CK.TEETH, FINE) { roundRect(mx - mw * 1.1, my - 0.06, mw * 2.2, 0.13, 0.04) }
            line(lc, 0.6) { line(mx - mw * 1.05, my + 0.005, mx + mw * 1.05, my + 0.005) }
            for i in -1...1 { line(lc, 0.5) { line(mx + Float(i) * mw * 0.55, my - 0.055, mx + Float(i) * mw * 0.55, my + 0.065) } }
case Mouth.SHOUT: 
let top = my - 0.12; let bot = my + 0.3
            dark { smooth(mx - 0.33, top, mx + 0.33, top, mx + 0.22, bot - 0.06, mx, bot, mx - 0.22, bot - 0.06, closed: true, tension: 0.35) }
            spot(CK.TEETH) { poly(mx - 0.28, top + 0.015, mx + 0.28, top + 0.015, mx + 0.25, top + 0.06, mx + 0.16, top + 0.065, mx + 0.13, top + 0.11, mx + 0.1, top + 0.065, mx - 0.1, top + 0.065, mx - 0.13, top + 0.11, mx - 0.16, top + 0.065, mx - 0.25, top + 0.06, closed: true) }
            spot(CK.TONGUE) { ellipse(mx, bot - 0.07, 0.13, 0.06) }
case Mouth.WAIL: 
dark { smooth(mx - 0.27, my + 0.08, mx - 0.12, my - 0.06, mx + 0.12, my - 0.06, mx + 0.27, my + 0.08, mx + 0.16, my + 0.24, mx - 0.16, my + 0.24, closed: true, tension: 0.4) }
            spot(CK.TONGUE) { ellipse(mx, my + 0.17, 0.11, 0.045) }
            spot(CK.TEETH) { poly(mx - 0.13, my - 0.045, mx + 0.13, my - 0.045, mx + 0.1, my - 0.01, mx - 0.1, my - 0.01, closed: true) }
case Mouth.JAWDROP: 
dark { roundRect(mx - 0.1, my - 0.06, 0.2, 0.3, 0.09) }
            spot(CK.TONGUE) { ellipse(mx, my + 0.19, 0.07, 0.04) }
case Mouth.OUCH: 
dark { smooth(mx - 0.15, my + 0.08, mx + 0.02, my - 0.07, mx + 0.17, my + 0.06, mx, my + 0.14, closed: true, tension: 0.3) }
            spot(CK.TONGUE) { ellipse(mx, my + 0.09, 0.06, 0.03) }
default: break
}
}
}

/** Yanak: kızarma, çil, yara bandı, gözyaşı. */
extension Fig {
func cheeks() {
    let f = face
    let cy: Float = 0.56
    if f.blush { for sx in ([-1.0, 1.0] as [Float]) {
        let x = sx * 0.6 + fs
        spot(CK.BLUSH) { ellipse(x, cy, 0.2, 0.085) }.alpha(0.45)
        for i in -1...1 { taper([P(x + Float(i) * 0.08 + 0.03, cy - 0.05), P(x + Float(i) * 0.08 - 0.03, cy + 0.05)], 0.012, 0.022, 0.008, CK.mix(CK.BLUSH, CK.MOUTH, 0.4)) }
    } }
    if look.extras.contains(Extra.FRECKLES) { for sx in ([-1.0, 1.0] as [Float]) { for (dx, dy) in [(Float(-0.08), Float(0.0)), (Float(0.0), Float(-0.04)), (Float(0.08), Float(0.01))] {
        spot(CK.mix(CK.skinShade(look.skin), "#6B3A22", 0.4)) { circle(sx * 0.58 + dx + fs, cy - 0.02 + dy, 0.022) }
    } } }
    if look.extras.contains(Extra.BANDAID) || f.bandage {
        let x: Float = 0.6 + fs; let y = cy - 0.02
        b.group(rotate: -18.0, px: x, py: y) {
            fill("#F2D3A2", FINE) { roundRect(x - 0.2, y - 0.07, 0.4, 0.14, 0.06) }
            fill("#E8BE84", 0.4) { roundRect(x - 0.07, y - 0.055, 0.14, 0.11, 0.03) }
        }
    }
    if f.tear {
        let x: Float = 0.42 + fs + eyeGeo().w * 0.9
        drop(x, EY + 0.3, 0.055)
    }
    if f.streams { for sx in ([-1.0, 1.0] as [Float]) {
        // Şelale gözyaşı: göz altından çene hizasına inen dalgalı şerit
        let x0 = sx * 0.42 + fs
        let pts = [P(x0 - 0.12, EY + 0.12), P(x0 + sx * 0.05, EY + 0.12), P(x0 + sx * 0.18, 0.7), P(x0 + sx * 0.3, 1.12), P(x0 + sx * 0.06, 1.12), P(x0 - sx * 0.02, 0.7)]
        fill(CK.TEAR, FINE) { smooth(pts.flatMap { [$0.x, $0.y] }.toFloatArray(), closed: true, tension: 0.4) }.alpha(0.9)
        taper([P(x0 + sx * 0.02, EY + 0.2), P(x0 + sx * 0.12, 0.75), P(x0 + sx * 0.2, 1.05)], 0.01, 0.035, 0.01, CK.WHITE, 0.9)
    } }
}
}

extension Fig {
func mustache() {
    let c = look.hairColor
    union(c, MID, [{ [self] in smooth(-0.58, 0.82, -0.32, 0.64, 0.0, 0.7, 0.32, 0.64, 0.58, 0.82, 0.36, 0.86, 0.0, 0.8, -0.36, 0.86, closed: true) }])
    taper([P(-0.3, 0.72), P(-0.15, 0.76)], 0.005, 0.02, 0.005, CK.shade(c))
    taper([P(0.15, 0.76), P(0.3, 0.72)], 0.005, 0.02, 0.005, CK.shade(c))
}
}

extension Fig {
func chefMustache() {
    let c = look.hairColor
    for sx in ([-1.0, 1.0] as [Float]) { taper([P(sx * 0.02, 0.69), P(sx * 0.3, 0.74), P(sx * 0.55, 0.66), P(sx * 0.6, 0.55), P(sx * 0.5, 0.52)], 0.13, 0.11, 0.03, c) }
}
}

extension Fig {
func beard() {
    let c = look.hairColor
    union(c, OUT, [
    { [self] in
        path(closed: true) {
            moveTo(-0.98, 0.12)
            cubicTo(-1.08, 0.9, -0.72, 1.9, 0.05, 2.7)
            cubicTo(0.76, 1.9, 1.08, 0.9, 0.98, 0.12)
            cubicTo(0.86, 0.6, 0.55, 1.02, 0.0, 1.04)
            cubicTo(-0.55, 1.02, -0.86, 0.6, -0.98, 0.12)
        }
    }
])
    for (x0, x1) in [(Float(-0.6), Float(-0.25)), (Float(-0.2), Float(-0.02)), (Float(0.25), Float(0.12)), (Float(0.62), Float(0.32))] {
        taper([P(x0, 1.15), P((x0 + x1) / 2.0, 1.75), P(x1, 2.3)], 0.01, 0.04, 0.005, CK.shade(c))
    }
    // Bıyık (sakalla birlikte, sarkık)
    union(c, MID, [{ [self] in smooth(0.0, 0.66, 0.3, 0.66, 0.56, 0.86, 0.6, 1.08, 0.38, 0.86, 0.0, 0.78, -0.38, 0.86, -0.6, 1.08, -0.56, 0.86, -0.3, 0.66, closed: true) }])
}
}

extension Fig {
func mask() {
    let c = look.second
    let fsx = fs
    fill(c, MID) {
        path(closed: true) {
            moveTo(-1.08, -0.14)
            cubicTo(-0.7, -0.12, -0.3, -0.08, fsx, 0.06)
            cubicTo(0.3, -0.08, 0.7, -0.12, 1.08, -0.14)
            cubicTo(1.02, 0.2, 0.85, 0.44, 0.45 + fsx, 0.47)
            cubicTo(0.24 + fsx, 0.48, 0.12 + fsx, 0.4, fsx, 0.32)
            cubicTo(-0.12 + fsx, 0.4, -0.24 + fsx, 0.48, -0.45 + fsx, 0.47)
            cubicTo(-0.85, 0.44, -1.02, 0.2, -1.08, -0.14)
        }
    }
    spot(CK.light(c)) { ellipse(-0.62 + fsx, 0.0, 0.18, 0.04, -8.0) }.alpha(0.5)
}
}

extension Fig {
func glasses() {
    let g = eyeGeo()
    let round = look.extras.contains(Extra.ROUND_GLASSES)
    let fc = (round ? "#6B5A3A" : look.accent)
    for sx in ([-1.0, 1.0] as [Float]) {
        let x = sx * 0.42 + fs
        let shape: () -> VectorBuilder.Part = (round ? ({ [self] in circle(x, EY, g.w + 0.08) }) : ({ [self] in roundRect(x - g.w - 0.08, EY - 0.2, (g.w + 0.08) * 2.0, 0.38, 0.08) }))
        shape().fill("#D9F0FF").noStroke().alpha(0.18)
        shape().fill("@none").stroke(fc).width(1.2)
        taper([P(x - g.w * 0.5, EY + 0.08), P(x + g.w * 0.1, EY - 0.12)], 0.01, 0.05, 0.01, CK.WHITE, 0.75)
    }
    line(fc, 1.1) { line(-0.42 + g.w + 0.08 + fs, EY - 0.04, 0.42 - g.w - 0.08 + fs, EY - 0.04) }
}
}

// ------------------------------------------------------------------ şapkalar

extension Fig {
func hats() {
    let ex = look.extras
    if ex.contains(Extra.HEADBAND) {
        fill(look.accent, MID) { path(closed: true) { moveTo(-1.1, -0.44); quadTo(0.0, -0.78, 1.1, -0.44); lineTo(1.08, -0.24); quadTo(0.0, -0.58, -1.08, -0.24) } }
        taper([P(-0.8, -0.43), P(0.0, -0.66), P(0.8, -0.43)], 0.01, 0.04, 0.01, CK.light(look.accent), 0.8)
    }
    if ex.contains(Extra.POLICE_CAP) {
        let c = look.main
        fill(c, OUT) { smooth(-1.04, -0.5, -1.32, -0.98, -1.08, -1.36, 0.0, -1.46, 1.08, -1.36, 1.32, -0.98, 1.04, -0.5, 0.0, -0.44, closed: true, tension: 0.45) }
        spot(CK.light(c)) { ellipse(-0.45, -1.25, 0.4, 0.08, -6.0) }.alpha(0.5)
        fill(CK.deep(c), MID) { path(closed: true) { moveTo(-1.06, -0.7); quadTo(0.0, -0.82, 1.06, -0.7); lineTo(1.04, -0.46); quadTo(0.0, -0.56, -1.04, -0.46) } }
        fill("#15151C", MID) { path(closed: true) { moveTo(-0.98, -0.48); quadTo(0.0, -0.62, 0.98, -0.48); quadTo(0.5, -0.24, 0.0, -0.2); quadTo(-0.5, -0.24, -0.98, -0.48) } }
        taper([P(-0.6, -0.42), P(-0.1, -0.36)], 0.01, 0.04, 0.01, CK.WHITE, 0.6)
        fill(CK.GOLD, FINE) { path(closed: true) { moveTo(-0.17, -1.15); lineTo(0.17, -1.15); lineTo(0.16, -0.94); quadTo(0.0, -0.8, -0.16, -0.94) } }
        spot(CK.GOLD_D) { star(0.0, -1.0, 0.08, 0.035, 5) }
    }
    if ex.contains(Extra.FEDORA) {
        let c = "#6B4F3A"
        fill(c, OUT) { ellipse(0.0, -0.5, 1.62, 0.3) }
        spot(CK.shade(c)) { ellipse(0.1, -0.42, 1.4, 0.16) }
        fill(c, OUT) { path(closed: true) { moveTo(-1.0, -0.56); cubicTo(-1.12, -1.4, -0.42, -1.48, 0.0, -1.26); cubicTo(0.42, -1.48, 1.12, -1.4, 1.0, -0.56); quadTo(0.0, -0.48, -1.0, -0.56) } }
        spot(CK.shade(c)) { path(closed: true) { moveTo(0.45, -1.32); cubicTo(0.9, -1.3, 1.05, -1.0, 1.0, -0.56); lineTo(0.6, -0.53); cubicTo(0.65, -0.9, 0.6, -1.15, 0.45, -1.32) } }
        fill("#2E2420", MID) { path(closed: true) { moveTo(-1.02, -0.8); quadTo(0.0, -0.72, 1.02, -0.8); lineTo(1.0, -0.58); quadTo(0.0, -0.5, -1.0, -0.58) } }
        taper([P(-0.05, -1.25), P(0.0, -0.95)], 0.01, 0.04, 0.01, CK.deep(c))
    }
    if ex.contains(Extra.CHEF_HAT) {
        let c = "#FBFBF8"
        union(c, OUT, [{ [self] in circle(-0.6, -1.4, 0.46) }, { [self] in circle(0.0, -1.58, 0.56) }, { [self] in circle(0.6, -1.4, 0.46) }, { [self] in circle(-0.3, -1.68, 0.44) }, { [self] in circle(0.32, -1.7, 0.44) }, { [self] in roundRect(-0.95, -1.12, 1.9, 0.62, 0.12) }])
        fill(c, MID) { roundRect(-0.96, -1.08, 1.92, 0.58, 0.12) }
        spot("#E3E0D6") { roundRect(0.35, -1.06, 0.58, 0.54, 0.1) }
        for x in ([-0.45, 0.0, 0.45] as [Float]) { line("#C9C3B6", FINE) { line(x, -1.02, x, -0.6) } }
        line("#C9C3B6", FINE) { arc(0.0, -1.55, 0.3, 0.2, 200.0, 120.0) }
    }
    if ex.contains(Extra.WIZARD_HAT) {
        let c = look.main
        fill(c, OUT) { ellipse(0.0, -0.55, 1.78, 0.32) }
        spot(CK.shade(c)) { ellipse(0.12, -0.47, 1.55, 0.17) }
        fill(c, OUT) {
            path(closed: true) {
                moveTo(-1.05, -0.62)
                cubicTo(-0.8, -1.5, -0.45, -2.3, 0.2, -2.75)
                cubicTo(0.6, -2.98, 1.1, -2.88, 1.42, -2.56)
                cubicTo(1.1, -2.62, 0.72, -2.52, 0.56, -2.25)
                cubicTo(0.62, -1.6, 0.9, -1.0, 1.05, -0.62)
                quadTo(0.0, -0.5, -1.05, -0.62)
            }
        }
        spot(CK.shade(c)) { path(closed: true) { moveTo(0.4, -2.2); cubicTo(0.5, -1.6, 0.8, -1.0, 1.05, -0.62); lineTo(0.6, -0.58); cubicTo(0.45, -1.1, 0.38, -1.6, 0.4, -2.2) } }
        fill(CK.GOLD, MID) { path(closed: true) { moveTo(-1.0, -0.86); quadTo(0.0, -0.76, 1.0, -0.86); lineTo(1.05, -0.62); quadTo(0.0, -0.52, -1.05, -0.62) } }
        fill(CK.GOLD, FINE) { star(-0.12, -1.5, 0.2, 0.08, 5) }
        fill(CK.GOLD, FINE) { star(0.4, -2.1, 0.13, 0.05, 5) }
        fill(CK.GOLD, FINE) { path(closed: true) { moveTo(-0.5, -1.05); arcTo(-0.42, -1.12, 0.13, 0.13, 140.0, 220.0); arcTo(-0.37, -1.12, 0.1, 0.1, 0.0, -220.0) } }
    }
}
}

/** Duygu işaretleri: öfke damarı, ter damlası, kara çizgiler. */
extension Fig {
func overlays() {
    let f = face
    if f.gloom {
        spot(CK.GLOOM) { path(closed: true) { moveTo(-0.98, 0.1); cubicTo(-0.98, -0.45, -0.6, -0.85, 0.0, -0.85); cubicTo(0.6, -0.85, 0.98, -0.45, 0.98, 0.1); quadTo(0.0, -0.15, -0.98, 0.1) } }.alpha(0.22)
        for i in 0...5 {
            let x: Float = -0.55 + Float(i) * 0.22
            taper([P(x, -0.62), P(x, -0.12 + Float(i % 2) * 0.08)], 0.035, 0.025, 0.004, CK.GLOOM, 0.85)
        }
    }
    if f.vein {
        let c = P(0.6, -0.72)
        for r in ([0.0, 90.0, 180.0, 270.0] as [Float]) {
            let d = dirOf(r + 45.0)
            let a = c + d * 0.08
            let m = c + d * 0.17 + dirOf(r + 135.0) * 0.05
            let e = c + d * 0.2 + dirOf(r + 135.0) * 0.13
            taper([a, m, e], 0.04, 0.06, 0.02, CK.ANGER)
        }
    }
    if f.sweat { drop(1.12, -0.38, 0.12) }
}
}

extension Fig {
func spaceHelmet() {
    b.circle(0.0, -0.05, 1.62).fill(CK.WHITE).noStroke().alpha(0.16)
    b.circle(0.0, -0.05, 1.62).fill("@none").stroke(ink).width(OUT)
    taper((0...8).map { i in let a = toRadians(198.0 + Double(i) * 7.0); return P(1.42 * kCos(a).toFloat(), -0.05 + 1.42 * kSin(a).toFloat()) }, 0.02, 0.12, 0.02, CK.WHITE, 0.9)
    taper((0...2).map { i in let a = toRadians(262.0 + Double(i) * 5.0); return P(1.42 * kCos(a).toFloat(), -0.05 + 1.42 * kSin(a).toFloat()) }, 0.02, 0.08, 0.02, CK.WHITE, 0.9)
    spot(CK.WHITE) { ellipse(0.85, 0.7, 0.22, 0.1, -40.0) }.alpha(0.35)
}
}

// ------------------------------------------------------------------ robot başı

/** Robot başı: köşeli kafa, kulak modülleri, anten kanadı ve ifadeyi parlayan çizgilerle gösteren ekran. */
extension Fig {
func robotHead() {
    let plate = "#D5DCE4"
    let dark = "#5B6573"
    for sx in ([-1.0, 1.0] as [Float]) {
        fill(dark, MID) { circle(sx * 1.05, 0.05, 0.32) }
        fill("#F28A2E", FINE) { circle(sx * 1.05, 0.05, 0.14) }
    }
    fill(plate, OUT) { poly(0.5, -0.86, 0.72, -0.86, 1.02, -1.62, closed: true) }
    spot(CK.GLOW) { circle(1.02, -1.62, 0.09) }
    fill(plate, OUT) { roundRect(-1.0, -0.98, 2.0, 1.95, 0.42) }
    spot(CK.shade(plate)) { path(closed: true) { moveTo(0.98, -0.6); lineTo(0.98, 0.6); quadTo(0.95, 0.95, 0.6, 0.96); lineTo(0.55, 0.85); quadTo(0.82, 0.82, 0.84, 0.5); lineTo(0.84, -0.6) } }
    fill("#F28A2E", FINE) { roundRect(-0.55, -0.9, 1.1, 0.15, 0.07) }
    fill(dark, MID) { roundRect(-0.6, 0.68, 1.2, 0.24, 0.1) }
    for i in -2...2 { line("#3A414C", 0.6) { line(Float(i) * 0.18, 0.72, Float(i) * 0.18, 0.88) } }
    fill(CK.SCREEN, MID) { roundRect(-0.8, -0.62, 1.6, 1.24, 0.3) }
    spot(CK.WHITE) { roundRect(-0.7, -0.55, 0.5, 0.07, 0.035) }.alpha(0.25)
    robotFace()
}
}

/** Robot ekranındaki ifade (göz biçimi ve ağız tarifinden). */
extension Fig {
func robotFace() {
    let g = CK.GLOW
    let f = face
    let ey: Float = -0.1
    let my: Float = 0.32
    let sh = pose.turn * 0.12
    func glowEye(_ x: Float, _ shape: EyeShape, _ o: Float) {
        switch shape {
case EyeShape.HAPPY: taper([P(x - 0.17, ey + 0.06), P(x, ey - 0.12), P(x + 0.17, ey + 0.06)], 0.05, 0.08, 0.05, g)
case EyeShape.CLOSED: taper([P(x - 0.17, ey - 0.03), P(x, ey + 0.08), P(x + 0.17, ey - 0.03)], 0.05, 0.07, 0.05, g)
case EyeShape.SQUINT: 
taper([P(x - o * 0.16, ey - 0.12), P(x + o * 0.12, ey)], 0.05, 0.07, 0.05, g)
                taper([P(x + o * 0.12, ey), P(x - o * 0.16, ey + 0.12)], 0.05, 0.07, 0.05, g)
case EyeShape.STAR: spot(g) { star(x, ey, 0.2, 0.08, 5) }
case EyeShape.TINY: spot(g) { circle(x, ey, 0.05) }
case EyeShape.WIDE: line(g, 1.3) { circle(x, ey, 0.17) }; spot(g) { circle(x, ey, 0.06) }
default: 
let h: Float = 0.3 * (1.0 - max(f.lidIn, f.lidOut) * 0.5) * ((shape == EyeShape.HALF ? 0.5 : 1.0))
                spot(g) { roundRect(x - 0.1, ey - h / 2.0, 0.2, h, 0.1) }
                spot(CK.WHITE) { circle(x - 0.03, ey - h * 0.25, 0.035) }.alpha(0.8)
                if f.lidIn > 0.2 { spot(CK.SCREEN) { poly(x - o * 0.14, ey - h / 2.0 - 0.02, x + o * 0.14, ey - h / 2.0 - 0.02, x - o * 0.14, ey + 0.02, closed: true) } }
}
    }
    glowEye(-0.36 + sh, f.eye, -1.0)
    glowEye(0.36 + sh, f.eyeR ?? f.eye, 1.0)
    // Kaş çizgileri (öfke, üzüntü, şaşkınlık)
    if abs(f.browIn) > 0.05 || abs(f.browOut) > 0.05 || f.browRaiseR > 0.0 { for o in ([-1.0, 1.0] as [Float]) {
        let x = Float(o) * 0.36 + sh
        let raise = (o > 0 ? f.browRaiseR : 0.0)
        taper([P(x - Float(o) * 0.15, ey - 0.24 + f.browIn * 0.6 - raise), P(x + Float(o) * 0.15, ey - 0.26 + f.browOut * 0.6 - raise)], 0.04, 0.05, 0.03, g)
    } }
    let mx = sh
    switch f.mouth {
case Mouth.SMILE: taper([P(mx - 0.2, my - 0.03), P(mx, my + 0.07), P(mx + 0.2, my - 0.03)], 0.04, 0.06, 0.04, g)
case Mouth.LINE, Mouth.POUT: taper([P(mx - 0.16, my), P(mx + 0.16, my)], 0.04, 0.05, 0.04, g)
case Mouth.FROWN: taper([P(mx - 0.18, my + 0.05), P(mx, my - 0.03), P(mx + 0.18, my + 0.05)], 0.04, 0.06, 0.04, g)
case Mouth.WAVY: taper([P(mx - 0.2, my), P(mx - 0.1, my - 0.04), P(mx, my), P(mx + 0.1, my - 0.04), P(mx + 0.2, my)], 0.03, 0.05, 0.03, g)
case Mouth.SMIRK: taper([P(mx - 0.16, my + 0.02), P(mx + 0.06, my + 0.03), P(mx + 0.2, my - 0.06)], 0.04, 0.05, 0.04, g)
case Mouth.O, Mouth.JAWDROP: line(g, 1.3) { ellipse(mx, my + 0.02, 0.08, (f.mouth == Mouth.O ? 0.08 : 0.13)) }
case Mouth.GRIT: line(g, 1.1) { roundRect(mx - 0.2, my - 0.06, 0.4, 0.13, 0.03) }; for i in -1...1 { line(g, 0.8) { line(mx + Float(i) * 0.1, my - 0.06, mx + Float(i) * 0.1, my + 0.07) } }
case Mouth.OPEN_SMILE, Mouth.LAUGH, Mouth.SHOUT, Mouth.WAIL, Mouth.OUCH: 
let k: Float = (f.mouth == Mouth.OPEN_SMILE ? 0.8 : 1.0)
            spot(g) { path(closed: true) { moveTo(mx - 0.22 * k, my - 0.06); lineTo(mx + 0.22 * k, my - 0.06); quadTo(mx + 0.2 * k, my + 0.2 * k, mx, my + 0.2 * k); quadTo(mx - 0.2 * k, my + 0.2 * k, mx - 0.22 * k, my - 0.06) } }.alpha(0.85)
default: break
}
    if f.blush { for o in ([-1.0, 1.0] as [Float]) { spot("#FF7AB6") { roundRect(Float(o) * 0.56 + sh - 0.09, 0.12, 0.18, 0.06, 0.03) } } }
    if f.streams { for o in ([-1.0, 1.0] as [Float]) { for i in 0...2 { spot(g) { circle(Float(o) * 0.36 + sh, 0.12 + Float(i) * 0.13, 0.035) } } } }
    if f.vein {
        let c = P(0.55, -0.8)
        for r in ([0.0, 90.0, 180.0, 270.0] as [Float]) { let d = dirOf(r + 45.0); taper([c + d * 0.07, c + d * 0.16 + dirOf(r + 135.0) * 0.1], 0.04, 0.05, 0.02, CK.ANGER) }
    }
    if f.sweat { drop(1.18, -0.5, 0.12) }
    if f.gloom { for i in 0...4 { taper([P(-0.5 + Float(i) * 0.25, -0.55), P(-0.5 + Float(i) * 0.25, -0.3)], 0.03, 0.02, 0.004, CK.GLOOM, 0.9) } }
}
}

