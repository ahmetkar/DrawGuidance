import Foundation

/// v32 eğitim çizimlerinin geometrisi (Android `TutorialArt`): yumuşak eğriler (Catmull-Rom), elle çizilmiş gibi titrek
/// çizgiler, şekil veritabanındaki şablonların el çizimi halleri, yapay el yazısı ve hazır elemanlar (yazı, formül, diyagram, şekil).
enum TutorialArt {

    /// Noktalardan geçen yumuşak eğri (Catmull-Rom; uçlar sabit).
    static func smooth(_ points: [P], samples: Int = 10) -> [P] {
        if points.count < 3 { return points }
        var out: [P] = []
        out.reserveCapacity(points.count * samples + 1)
        for i in 0..<(points.count - 1) {
            let p0 = points[max(i - 1, 0)], p1 = points[i], p2 = points[i + 1], p3 = points[min(i + 2, points.count - 1)]
            for k in 0..<samples {
                let t = Float(k) / Float(samples), t2 = t * t, t3 = t2 * t
                func c(_ a: Float, _ b: Float, _ cc: Float, _ d: Float) -> Float {
                    let l1 = 2 * b + (-a + cc) * t
                    let l2 = (2 * a - 5 * b + 4 * cc - d) * t2
                    let l3 = (-a + 3 * b - 3 * cc + d) * t3
                    return 0.5 * (l1 + l2 + l3)
                }
                out.append(P(c(p0.x, p1.x, p2.x, p3.x), c(p0.y, p1.y, p2.y, p3.y)))
            }
        }
        out.append(points[points.count - 1])
        return out
    }

    /// El titremesi: düşük frekanslı bükülme ve küçük gürültü ([amp] tuval birimi).
    static func wobble(_ points: [P], amp: Float, seed: Int64) -> [P] {
        var rnd = JavaRandom(seed)
        let ph = rnd.nextFloat() * 6.28, f = 1.5 + rnd.nextFloat() * 2
        var len: Float = 0
        var out: [P] = []
        out.reserveCapacity(points.count)
        for (i, p) in points.enumerated() {
            if i > 0 { len += hypotf(p.x - points[i - 1].x, p.y - points[i - 1].y) }
            let wv = amp * sinf(f * len / max(amp * 60, 1) + ph)
            let nx = (rnd.nextFloat() - 0.5) * amp * 0.35
            let wy = amp * cosf(f * len / max(amp * 55, 1) + ph) * 0.8
            let ny = (rnd.nextFloat() - 0.5) * amp * 0.35
            out.append(P(p.x + wv + nx, p.y + wy + ny))
        }
        return out
    }

    /// Elle çizilmiş çember (biraz taşan uçla).
    static func roughCircle(_ c: P, _ r: Float, seed: Int64, overshoot: Float = 1.05) -> [P] {
        var rnd = JavaRandom(seed)
        let a0 = Double(rnd.nextFloat() * 6.28)
        return (0...80).map { i in
            let a = a0 + Double(i) / 80.0 * 2 * Double.pi * Double(overshoot)
            let rr = r * (1 + (rnd.nextFloat() - 0.5) * 0.05 + 0.03 * sinf(Float(i) / 9))
            return P(c.x + rr * Float(cos(a)), c.y + rr * Float(sin(a)))
        }
    }

    /// Köşelerden geçen elle çizilmiş kırık çizgi.
    static func roughPoly(_ corners: [P], seed: Int64, closed: Bool = true, amp: Float = 2.5) -> [P] {
        var rnd = JavaRandom(seed)
        let pts = closed && !corners.isEmpty ? corners + [corners[0]] : corners
        guard pts.count >= 2 else { return pts }
        var out: [P] = []
        for i in 0..<(pts.count - 1) {
            let a = pts[i], b = pts[i + 1]
            let n = 16
            for k in 0..<n {
                let t = Float(k) / Float(n)
                let x = a.x + (b.x - a.x) * t + (rnd.nextFloat() - 0.5) * amp
                let y = a.y + (b.y - a.y) * t + (rnd.nextFloat() - 0.5) * amp
                out.append(P(x, y))
            }
        }
        out.append(pts[pts.count - 1])
        return out
    }

    /// Şekil veritabanındaki şablonun ([key]) [center] etrafında [size] boyunda elle çizilmiş hali (çizgi sırası korunur).
    static func handDrawnTemplate(_ key: String, _ center: P, _ size: Float, seed: Int64) -> [[P]] {
        guard let t = ShapeTemplates.byKey(key) else { return [] }
        var rnd = JavaRandom(seed)
        // v38 şekil önerisi birbirine yakın 4+ elemanda: az çizgili şablon parçalara bölünerek çizilir
        let norm = ShapeSignatures.normalize(t.strokes)
        let base = norm.count >= SuggestRules.SHAPE_MIN ? norm : ShapeAugment.atLeast(norm, SuggestRules.SHAPE_MIN, JRandom(seed))
        return base.map { s in
            let pts = TemplateFit.densify(s.map { P(center.x + ($0.x - 0.5) * size, center.y + ($0.y - 0.5) * size) }, max(size / 90, 2))
            let ph = rnd.nextFloat() * 6.28, f = 1 + rnd.nextFloat() * 2
            var len: Float = 0
            var out: [P] = []
            out.reserveCapacity(pts.count)
            for (i, p) in pts.enumerated() {
                if i > 0 { len += hypotf(p.x - pts[i - 1].x, p.y - pts[i - 1].y) }
                let wv = size * 0.008
                let arg = f * len / size * 6.28 + ph
                let x = p.x + wv * sinf(arg) + (rnd.nextFloat() - 0.5)
                let y = p.y + wv * cosf(arg) + (rnd.nextFloat() - 0.5)
                out.append(P(x, y))
            }
            return out
        }
    }

    /// Şablonun düzgün (kusursuz) hali — öneri gelmezse yedek.
    static func cleanTemplate(_ key: String, _ center: P, _ size: Float) -> [[P]] {
        guard let t = ShapeTemplates.byKey(key) else { return [] }
        return ShapeSignatures.normalize(t.strokes).map { s in
            TemplateFit.densify(s.map { P(center.x + ($0.x - 0.5) * size, center.y + ($0.y - 0.5) * size) }, max(size / 90, 2))
        }
    }

    /// [text] için düzgün yapay el yazısı; satır [box] içine (yüksekliğine göre) oturtulur.
    static func handwriting(_ text: String, seed: Int, box: FRect) -> [[P]] {
        let opt = InkSynth.Options(slantDeg: 6, rotDeg: 2, minH: 60, maxH: 61, delayMarks: 0, warp: 2.5)
        let ink = InkSynth.sample(text, InkSynth.Rng(seed), opt)
        let all = ink.flatMap { $0.points }
        guard let first = all.first else { return [] }
        var minX = first.x, maxX = first.x, minY = first.y, maxY = first.y
        for p in all { minX = min(minX, p.x); maxX = max(maxX, p.x); minY = min(minY, p.y); maxY = max(maxY, p.y) }
        let k = min(box.height / max(maxY - minY, 1), box.width / max(maxX - minX, 1))
        return ink.map { s in s.points.map { P(box.left + ($0.x - minX) * k, box.top + ($0.y - minY) * k) } }
    }

    // MARK: - Hazır elemanlar

    private static func q(_ v: Float) -> Float { ContentCodec.quantize(v) }

    static func text(_ content: String, _ left: Float, _ top: Float, _ size: Float, _ color: String, font: TextFont = .SANS,
                     bold: Bool = false, italic: Bool = false, align: TextAlignKind = .LEFT) -> Stroke {
        var spec = TextSpec(content: content)
        spec.font = font; spec.bold = bold; spec.italic = italic; spec.align = align
        let points = TextRenderer.quadAt(spec, size, left, top).map { StrokePoint(q($0.x), q($0.y)) }
        var s = Stroke(id: UUID().uuidString, points: points, colorHex: color, brushType: .TEXT, sizePx: size, opacity: 1, order: 0)
        s.text = spec
        return s
    }

    /// Yazının ölçüsü (genişlik, yükseklik).
    static func measure(_ content: String, _ size: Float, font: TextFont = .SANS, bold: Bool = false) -> (Float, Float) {
        var spec = TextSpec(content: content)
        spec.font = font; spec.bold = bold
        return TextRenderer.measure(spec, size)
    }

    static func formula(_ templateId: String, _ left: Float, _ top: Float, _ size: Float, _ color: String) -> Stroke? {
        guard let t = FormulaCatalog.find(templateId) else { return nil }
        let quad = FormulaRenderer.quadAt(t.source, size, left, top).map { StrokePoint(q($0.x), q($0.y)) }
        var s = Stroke(id: UUID().uuidString, points: quad, colorHex: color, brushType: .FORMULA, sizePx: size, opacity: 1, order: 0)
        s.formula = FormulaSpec(source: Formula.atomize(t.source), templateId: t.id)
        return s
    }

    /// Diyagram ([texts] verilirse adımların yazıları sırayla onlar).
    static func diagram(_ kind: DiagramKind, _ rect: FRect, _ style: ShapeStyle, _ theme: String, texts: [String] = []) -> Stroke {
        let defaults = DiagramRenderer.defaultNodes(kind).enumerated().map { i, n -> DiagramNode in
            var m = n
            if i < texts.count { m.text = texts[i] }
            return m
        }
        let nodes = DiagramRenderer.layout(kind, defaults)
        var spec = DiagramSpec(kind: kind, nodes: nodes)
        spec.edges = DiagramRenderer.defaultEdges(kind, nodes); spec.style = style; spec.theme = theme
        let pts = [StrokePoint(rect.left, rect.top), StrokePoint(rect.right, rect.top), StrokePoint(rect.right, rect.bottom),
                   StrokePoint(rect.left, rect.bottom)]
        var s = Stroke(id: UUID().uuidString, points: pts, colorHex: style.strokeColorHex, brushType: .DIAGRAM, sizePx: style.strokeWidth,
                       opacity: 1, order: 0)
        s.diagram = spec
        return s
    }

    static func shape(_ kind: String, _ start: P, _ end: P, _ style: ShapeStyle, opacity: Float = 1) -> Stroke? {
        ShapeFactory.build(kind, start, end, style: style, id: UUID().uuidString, order: 0, opacity: opacity)
    }
}
