import XCTest
@testable import DrawGuidance

/// Grafik üreticisi: Android `GraphsTest`, `V18GraphPatternTest` ve `V45GraphFormulaTest` ile aynı beklentiler.
final class GraphTests: XCTestCase {
    private let aspects: [Float] = [0.6, 1, 1.7]

    private func G(_ k: GraphKind, _ f: (inout GraphParams) -> Void = { _ in }) -> GraphParams { GraphParams(kind: k).with(f) }

    private func tags(_ paths: [VPath]) -> [String] { paths.compactMap(\.tag) }

    private func isHex(_ c: String) -> Bool {
        let a = Array(c)
        return a.count == 7 && a[0] == "#" && a[1...].allSatisfy(\.isHexDigit)
    }

    /// Geçerlilik: sonlu noktalar, geçerli renkler, geometri ve yazılar kutuda (≤ 0.05 taşma).
    private func validate(_ name: String, _ paths: [VPath], _ aspect: Float, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(paths.contains { $0.label == nil && $0.points.count >= 2 }, "\(name) boş", file: file, line: line)
        let bw = max(aspect, 1), bh = max(1, 1 / aspect)
        let lim: Float = 0.05
        for p in paths {
            for pt in p.points where !pt.x.isFinite || !pt.y.isFinite { XCTFail("\(name): sonlu olmayan nokta \(p.tag ?? "")", file: file, line: line) }
            for c in [p.fill, p.stroke].compactMap({ $0 }) { XCTAssertTrue(c == "@ink" || isHex(c), "\(name): renk \(c)", file: file, line: line) }
            if let label = p.label {
                XCTAssertTrue(p.labelSize > 0 && p.labelSize.isFinite, "\(name): yazı boyu", file: file, line: line)
                XCTAssertEqual(p.points.count, 1)
                let pt = p.points[0]
                let w = GMetrics.width(label, p.labelSize) / bw
                let h = p.labelSize / bh
                if pt.x < -lim || pt.y < -lim || pt.x + w > 1 + lim || pt.y + h > 1 + lim {
                    XCTFail("\(name) (aspect \(aspect)): yazı kutu dışında '\(label)' \(pt) w=\(w) h=\(h)", file: file, line: line)
                }
            } else {
                for pt in p.points where pt.x < -lim || pt.x > 1 + lim || pt.y < -lim || pt.y > 1 + lim {
                    XCTFail("\(name) (aspect \(aspect)): nokta kutu dışında \(pt) \(p.tag ?? "")", file: file, line: line)
                    break
                }
            }
        }
    }

    private func assertTags(_ name: String, _ paths: [VPath], _ prefixes: [String], file: StaticString = #filePath, line: UInt = #line) {
        let t = tags(paths)
        for pre in prefixes { XCTAssertTrue(t.contains { $0.hasPrefix(pre) }, "\(name): '\(pre)' etiketi yok (\(Array(Set(t)).sorted().prefix(30)))", file: file, line: line) }
    }

    private var kindSamples: [(GraphParams, [String])] {
        [
            (G(.AXES), ["tick:x:", "tick:y:", "axis:x", "axis:y"]),
            (G(.COORD_PLANE), ["tick:x:", "tick:y:", "axis:x", "axis:y", "tick:x:0"]),
            (G(.COORD_PLANE) { $0.formula = "y = x^2 - 2"; $0.values = [2, 3, -1, 2] }, ["curve", "value:0", "vlabel:1", "formula"]),
            (G(.NUMBER_LINE), ["tick:x:1", "tick:x:-2"]),
            (G(.UNIT_CIRCLE), ["curve", "tick:x:1", "tick:y:-1", "angle:30", "axis:x"]),
            (G(.AXES_3D), ["tick:x:1", "tick:y:1", "tick:z:1", "axis:z"]),
            (G(.AXES_3D) { $0.formula = "z = sin(x)*cos(y)" }, ["curve", "formula", "axis:x"]),
            (G(.FUNCTION) { $0.preset = "sinus" }, ["curve", "tick:x:1", "tick:y:", "axis:x", "axis:y", "formula"]),
            (G(.FUNCTION) { $0.formula = "x^3 - 3x" }, ["curve", "formula", "key:"]),
            (G(.BAR), ["value:0", "vlabel:0", "tick:y:1", "axis:x", "axis:y"]),
            (G(.LINE), ["curve", "value:0", "vlabel:0", "tick:y:1"]),
            (G(.PIE), ["value:0", "vlabel:0"]),
            (G(.SCATTER), ["value:0", "tick:x:", "tick:y:"]),
            (G(.HISTOGRAM), ["value:0", "tick:x:1", "tick:y:1"]),
        ]
    }

    func testEveryKindBuilds() {
        let samples = kindSamples
        for k in GraphKind.allCases { XCTAssertTrue(samples.contains { $0.0.kind == k }, "\(k) örneği yok") }
        for (p, expected) in samples {
            for a in aspects {
                let paths = Graphs.build(p, aspect: a, resolution: 512)
                validate("\(p.kind) \(p.formula)", paths, a)
                assertTags("\(p.kind) \(p.formula) @\(a)", paths, expected)
            }
        }
    }

    func testEveryPresetBuilds() {
        XCTAssertGreaterThanOrEqual(CurvePresets.all.count, 14)
        XCTAssertEqual(CurvePresets.all.count, Set(CurvePresets.all.map(\.id)).count)
        for preset in CurvePresets.all {
            let parsed = GExpression.isImplicit(preset.formula) ? GExpression.parseImplicit(preset.formula) : GExpression.parse(preset.formula)
            XCTAssertNotNil(parsed, "\(preset.id) formülü")
            for a in aspects {
                let p = G(.FUNCTION) { $0.preset = preset.id; $0.repeat = preset.defaultRepeat }
                let paths = Graphs.build(p, aspect: a, resolution: 512)
                validate(preset.id, paths, a)
                assertTags("\(preset.id) @\(a)", paths, ["curve", "formula", "tick:x:", "axis:x"])
            }
        }
    }

    func testCircleIsRound() {
        for a in aspects {
            let paths = Graphs.build(G(.FUNCTION) { $0.preset = "cember"; $0.repeat = 4 }, aspect: a, resolution: 512)
            let pts = paths.filter { $0.tag == "curve" }.flatMap(\.points)
            let xs = pts.map { $0.x * a }, ys = pts.map(\.y)
            let w = xs.max()! - xs.min()!, h = ys.max()! - ys.min()!
            XCTAssertEqual(w / h, 1, accuracy: 0.03, "çember en/boy @\(a)")
        }
    }

    func testTickLabelsRespectOverrides() {
        let base = G(.COORD_PLANE)
        let p = Graphs.withTickLabel(base, "x:2", "A")
        XCTAssertEqual("A", Graphs.build(p, aspect: 1).first { $0.tag == "tick:x:2" && $0.label != nil }?.label)
        XCTAssertEqual("A", Graphs.tickValue(p, "x:2"))
        XCTAssertEqual("2", Graphs.tickValue(base, "x:2"))
        let hidden = Graphs.withTickLabel(base, "y:1", "")
        XCTAssertNil(Graphs.build(hidden, aspect: 1).first { $0.tag == "tick:y:1" && $0.label != nil })
        XCTAssertNotNil(Graphs.build(base, aspect: 1).first { $0.tag == "tick:y:1" && $0.label != nil })

        let sine = G(.FUNCTION) { $0.preset = "sinus" }
        XCTAssertEqual("π/2", Graphs.tickValue(sine, "x:1"))
        XCTAssertEqual("π", Graphs.tickValue(sine, "x:2"))
        XCTAssertEqual("−3π/2", Graphs.tickValue(sine, "x:-3"))
        XCTAssertEqual("y = sin(x)", Graphs.tickValue(sine, "formula"))
        let renamed = Graphs.withTickLabel(sine, "formula", "f(x) = sin x")
        XCTAssertEqual("f(x) = sin x", Graphs.build(renamed, aspect: 1.5).first { $0.tag == "formula" && $0.label != nil }?.label)

        let nl = Graphs.withTickLabel(G(.NUMBER_LINE) { $0.ticks = 8 }, "x:1/4", "¼")
        XCTAssertEqual("¼", Graphs.build(nl, aspect: 1.6).first { $0.tag == "tick:x:1/4" && $0.label != nil }?.label)
        XCTAssertEqual("−2", Graphs.tickValue(G(.NUMBER_LINE), "x:-2"))
    }

    private func tickTags(_ p: GraphParams, _ a: Float = 1) -> Int { Set(tags(Graphs.build(p, aspect: a)).filter { $0.hasPrefix("tick:") }).count }

    /// Eğrinin x eksenini kaç kez kestiği (eksen: "tick:x:1" çentiğinin orta noktası).
    private func zeroCrossings(_ paths: [VPath]) -> Int {
        guard let tick = paths.first(where: { $0.tag == "tick:x:1" && $0.label == nil }) else { return -1 }
        let axisY = (tick.points[0].y + tick.points[1].y) / 2
        var n = 0
        for c in paths where c.tag == "curve" {
            var prev = 0
            for pt in c.points {
                let s = pt.y < axisY - 1e-4 ? 1 : pt.y > axisY + 1e-4 ? -1 : 0
                if s != 0 { if prev != 0 && s != prev { n += 1 }; prev = s }
            }
        }
        return n
    }

    func testPatternAndPointsChangeOutput() {
        let plane = G(.COORD_PLANE)
        XCTAssertGreaterThan(tickTags(Graphs.morePoints(plane)), tickTags(plane))
        XCTAssertLessThan(tickTags(Graphs.lessPoints(plane)), tickTags(plane))
        XCTAssertEqual(12, Graphs.morePoints(plane.with { $0.ticks = 12 }).ticks)
        XCTAssertEqual(1, Graphs.lessPoints(plane.with { $0.ticks = 1 }).ticks)

        let sine = G(.FUNCTION) { $0.preset = "sinus"; $0.repeat = 2; $0.freq = 2 }
        let z2 = zeroCrossings(Graphs.build(sine, aspect: 1.7))
        let z3 = zeroCrossings(Graphs.build(Graphs.morePattern(sine), aspect: 1.7))
        let z1 = zeroCrossings(Graphs.build(Graphs.lessPattern(sine), aspect: 1.7))
        XCTAssertTrue(z1 < z2 && z2 < z3, "sinüs kesişimleri \(z1) < \(z2) < \(z3)")

        let nl = G(.NUMBER_LINE)
        XCTAssertGreaterThan(tickTags(Graphs.morePattern(nl), 1.7), tickTags(nl, 1.7))
        XCTAssertGreaterThanOrEqual(tickTags(Graphs.morePoints(nl), 1.7), tickTags(nl, 1.7))

        let bar = G(.BAR)
        func bars(_ p: GraphParams) -> Int { Set(tags(Graphs.build(p, aspect: 1.4)).filter { $0.hasPrefix("value:") }).count }
        XCTAssertGreaterThan(bars(Graphs.morePattern(bar)), bars(bar))
        XCTAssertLessThan(bars(Graphs.lessPattern(bar)), bars(bar))
        let edited = Graphs.withValue(bar, 0, 3)
        XCTAssertGreaterThan(bars(Graphs.morePattern(edited)), bars(edited))
        XCTAssertLessThan(bars(Graphs.lessPattern(edited)), bars(edited))

        let uc = G(.UNIT_CIRCLE)
        func angles(_ p: GraphParams) -> Int { tags(Graphs.build(p, aspect: 1)).filter { $0.hasPrefix("angle:") }.count }
        XCTAssertGreaterThan(angles(Graphs.morePattern(uc)), angles(uc))
        XCTAssertLessThan(angles(Graphs.lessPoints(uc)), angles(uc))

        let para = G(.FUNCTION) { $0.preset = "parabol"; $0.repeat = 3 }
        XCTAssertNotEqual(Graphs.build(Graphs.morePattern(para), aspect: 1.4), Graphs.build(para, aspect: 1.4))
        XCTAssertGreaterThan(tickTags(Graphs.morePoints(para), 1.4), tickTags(para, 1.4))
        XCTAssertLessThan(tickTags(Graphs.lessPoints(Graphs.lessPoints(para)), 1.4), tickTags(para, 1.4))
    }

    func testTangentIsSplitAtAsymptotes() {
        for a in aspects {
            let paths = Graphs.build(G(.FUNCTION) { $0.preset = "tanjant"; $0.repeat = 3 }, aspect: a)
            let curves = paths.filter { $0.tag == "curve" }
            let asym = paths.filter { $0.tag == "asymptote" }
            XCTAssertGreaterThanOrEqual(curves.count, 3, "tanjant parçaları")
            XCTAssertGreaterThanOrEqual(asym.count, 2, "asimptotlar")
            for s in asym { XCTAssertEqual(.DASHED, s.line) }
            let xs = asym.map { $0.points[0].x }
            for c in curves {
                for x in xs {
                    let left = c.points.contains { $0.x < x - 1e-3 }
                    let right = c.points.contains { $0.x > x + 1e-3 }
                    XCTAssertFalse(left && right, "eğri asimptotu aşıyor (x=\(x))")
                }
            }
        }
        let hyp = Graphs.build(G(.FUNCTION) { $0.formula = "1/(x-1)" }, aspect: 1.3)
        XCTAssertGreaterThanOrEqual(hyp.filter { $0.tag == "curve" }.count, 2)
        XCTAssertTrue(hyp.contains { $0.tag == "asymptote" })
    }

    func testFloorHasOpenAndClosedDots() {
        let paths = Graphs.build(G(.FUNCTION) { $0.preset = "basamak"; $0.repeat = 3 }, aspect: 1.3)
        let white = paths.filter { $0.tag == "key" && $0.fill == "#FFFFFF" }.count
        let full = paths.filter { $0.tag == "key" && $0.fill != "#FFFFFF" }.count
        XCTAssertTrue(white >= 4 && full >= 4, "boş \(white) dolu \(full)")
        XCTAssertGreaterThanOrEqual(paths.filter { $0.tag == "curve" }.count, 5)
    }

    private func barHeight(_ paths: [VPath], _ i: Int) -> Float {
        let b = paths.first { $0.tag == "value:\(i)" && $0.label == nil && $0.closed }!
        return b.points.map(\.y).max()! - b.points.map(\.y).min()!
    }

    func testWithValueEditsBarHeight() {
        let bar = G(.BAR)
        let before = Graphs.build(bar, aspect: 1.3)
        let edited = Graphs.withValue(bar, 1, 30)
        XCTAssertEqual(Graphs.values(bar).count, edited.values.count)
        XCTAssertEqual(30, edited.values[1])
        let after = Graphs.build(edited, aspect: 1.3)
        let r0 = barHeight(before, 1) / barHeight(before, 0)
        let r1 = barHeight(after, 1) / barHeight(after, 0)
        XCTAssertGreaterThan(r1, r0 * 2)
        XCTAssertEqual("30", after.first { $0.tag == "value:1" && $0.label != nil }?.label)
        XCTAssertEqual(8, Graphs.withValue(bar, 7, 2).values.count)
        XCTAssertEqual(9, Graphs.withValue(G(.SCATTER), 2, 9).values[5])
        XCTAssertGreaterThanOrEqual(Graphs.itemCount(G(.PIE)), 3)
    }

    func testExpressionParsing() {
        func v(_ s: String, _ x: Double, _ y: Double = 0) -> Double { GExpression.parse(s)!(x, y) }
        XCTAssertEqual(7, v("2x + 1", 3), accuracy: 1e-9)
        XCTAssertEqual(3 * sin(1.0), v("3sin(x)", 1), accuracy: 1e-9)
        XCTAssertEqual(12, v("x(x+1)", 3), accuracy: 1e-9)
        XCTAssertEqual(-9, v("-x^2", 3), accuracy: 1e-9)
        XCTAssertEqual(0.25, v("2^-x", 2), accuracy: 1e-9)
        XCTAssertEqual(9, v("x²", 3), accuracy: 1e-9)
        XCTAssertEqual(2, v("√x", 4), accuracy: 1e-9)
        XCTAssertEqual(3, v("|x|", -3), accuracy: 1e-9)
        XCTAssertEqual(16, v("f(x) = x^2", 4), accuracy: 1e-9)
        XCTAssertEqual(2 * .pi * 2, v("2πx", 2), accuracy: 1e-9)
        XCTAssertEqual(6, v("xy", 2, 3), accuracy: 1e-9)
        XCTAssertEqual(sin(2.0), v("sin 2x", 1), accuracy: 1e-9)
        XCTAssertEqual(sin(1.0) * cos(1.0), v("sin x cos x", 1), accuracy: 1e-9)
        XCTAssertEqual(-2, v("x^(1/3)", -8), accuracy: 1e-9)
        XCTAssertEqual(1.5, v("1,5", 0), accuracy: 1e-9)
        XCTAssertEqual(2, v("⌊x⌋", 2.7), accuracy: 1e-9)
        XCTAssertEqual(-3, v("⟦x⟧", -2.5), accuracy: 1e-9)
        XCTAssertEqual(2.0.squareRoot() * 3, v("3·√2", 0), accuracy: 1e-9)
        XCTAssertNil(GExpression.parse("2 +"))
        XCTAssertNil(GExpression.parse("foo(x)"))
        XCTAssertTrue(GExpression.isImplicit("x^2 + y^2 = 9"))
        XCTAssertEqual(0, GExpression.parseImplicit("x^2 + y^2 = 25")!(3, 4), accuracy: 1e-9)
    }

    func testFormulaLabels() {
        func fn(_ f: String) -> String { Graphs.formulaLabel(G(.FUNCTION) { $0.formula = f }) }
        XCTAssertEqual("y = sin(x)", Graphs.formulaLabel(G(.FUNCTION) { $0.preset = "sinus" }))
        XCTAssertEqual("y = x² − 3x", fn("x^2-3x"))
        XCTAssertEqual("y = 2x + 1", fn("2*x+1"))
        XCTAssertEqual("y = √x", fn("sqrt(x)"))
        XCTAssertEqual("y = |x − 1|", fn("abs(x-1)"))
        XCTAssertEqual("y = e^(−x/4)·sin(2x)", fn("e^(-x/4)*sin(2x)"))
        XCTAssertEqual("f(x) = 2ˣ", fn("f(x) = 2^x"))
        XCTAssertEqual("x² + y² = 9", fn("x^2+y^2=9"))
        XCTAssertEqual("y = 1,5x", fn("1,5x"))
        XCTAssertEqual("y = ⟦x⟧", fn("floor(x)"))
        XCTAssertEqual("z = x² + y²", Graphs.formulaLabel(G(.AXES_3D) { $0.formula = "z = x^2+y^2" }))
    }

    func testUserPeriodicFormulaUsesPiTicks() {
        XCTAssertEqual("π/2", Graphs.tickValue(G(.FUNCTION) { $0.formula = "y = 2cos(x)" }, "x:1", aspect: 1.5))
    }

    // MARK: v18 desen

    private func label(_ preset: String, _ k: Int) -> String { Graphs.formulaLabel(G(.FUNCTION) { $0.preset = preset; $0.freq = k }) }

    func testPatternChangesFormulaText() {
        XCTAssertEqual("y = sin(x)", label("sinus", 1))
        XCTAssertEqual("y = sin(2x)", label("sinus", 2))
        XCTAssertEqual("y = cos(3x)", label("kosinus", 3))
        XCTAssertEqual("y = (2x)²", label("parabol", 2))
        XCTAssertEqual("y = 4x + 1", label("dogrusal", 2))
        XCTAssertEqual("y = (2x)³ − 6x", label("kubik", 2))
        XCTAssertEqual("y = ln(2x)", label("logaritma", 2))
        XCTAssertEqual("y = √(2x)", label("karekok", 2))
        XCTAssertEqual("y = 1/(2x)", label("hiperbol", 2))
        XCTAssertEqual("y = 2²ˣ", label("ustel", 2))
        XCTAssertEqual("y = |3x|", label("mutlak", 3))
        XCTAssertEqual("x² + y² = 9", label("cember", 1))
        XCTAssertEqual("y = (2x)² − 6x", Graphs.formulaLabel(G(.FUNCTION) { $0.formula = "x^2 - 3x"; $0.freq = 2 }))
        XCTAssertEqual("y = exp(2x)", FormulaText.scaleX("y = exp(x)", 2))
    }

    func testPatternStepsFrequencyNotRange() {
        let sine = G(.FUNCTION) { $0.preset = "sinus"; $0.repeat = 2 }
        let more = Graphs.morePattern(sine)
        XCTAssertEqual(2, more.freq)
        XCTAssertEqual(sine.repeat, more.repeat, "x aralığı değişmez")
        XCTAssertEqual(2, Graphs.patternValue(more))
        XCTAssertEqual(1, Graphs.lessPattern(sine).freq)
        var p = sine
        for _ in 0..<20 { p = Graphs.morePattern(p) }
        XCTAssertEqual(Graphs.MAX_FREQ, p.freq)
        let a = Graphs.build(sine, aspect: 1.6)
        let b = Graphs.build(more, aspect: 1.6)
        func ticks(_ paths: [VPath]) -> [String?] { paths.filter { $0.tag?.hasPrefix("tick:x") == true }.map(\.label) }
        XCTAssertEqual(ticks(a), ticks(b))
        XCTAssertNotEqual(a.filter { $0.tag == "curve" }, b.filter { $0.tag == "curve" })
        XCTAssertTrue(b.contains { $0.tag == "formula" && $0.label == "y = sin(2x)" })
        let plane = G(.COORD_PLANE) { $0.repeat = 2 }
        XCTAssertEqual(3, Graphs.morePattern(plane).repeat)
        XCTAssertEqual(1, Graphs.morePattern(plane).freq)
    }

    func testEditableFormulaCarriesFrequency() {
        XCTAssertEqual("sin(3*x)", Graphs.editableFormula(G(.FUNCTION) { $0.preset = "sinus"; $0.freq = 3 }))
        XCTAssertEqual("(2*x)^2", Graphs.editableFormula(G(.FUNCTION) { $0.preset = "parabol"; $0.freq = 2 }))
        XCTAssertEqual("1/(2*x)", Graphs.editableFormula(G(.FUNCTION) { $0.preset = "hiperbol"; $0.freq = 2 }))
        XCTAssertEqual("exp(2*x) + 1", Graphs.editableFormula(G(.FUNCTION) { $0.formula = "exp(x) + 1"; $0.freq = 2 }))
        XCTAssertEqual("sin(x)", Graphs.editableFormula(G(.FUNCTION) { $0.preset = "sinus"; $0.freq = 1 }))
        let k2 = G(.FUNCTION) { $0.preset = "sinus"; $0.freq = 2 }
        let e = GExpression.parse(Graphs.editableFormula(k2))!
        XCTAssertEqual(sin(2 * 0.7), e(0.7, 0), accuracy: 1e-9)
    }

    func testGraphFollowsPaperColor() {
        let white = Graphs.build(G(.FUNCTION) { $0.preset = "sinus" }, aspect: 1.5)
        XCTAssertEqual("#FFFFFF", white.first { $0.tag == "formula" && $0.closed }?.fill)
        XCTAssertTrue(white.contains { $0.stroke == "#DDE3EA" })

        let dark = Graphs.build(G(.FUNCTION) { $0.preset = "sinus"; $0.paper = "#1B1F24" }, aspect: 1.5)
        XCTAssertEqual("#1B1F24", dark.first { $0.tag == "formula" && $0.closed }?.fill, "formül kutusu kağıt renginde")
        XCTAssertFalse(dark.contains { $0.stroke == "#DDE3EA" || $0.fill == "#FFFFFF" }, "beyaz ızgara kalmaz")
        let grid = dark.first { ($0.stroke?.hasPrefix("#") ?? false) && $0.tag == nil && $0.width < 0.5 }!.stroke!
        XCTAssertGreaterThan(GColors.luminance(grid), GColors.luminance("#1B1F24"), "ızgara \(grid)")

        let pie = Graphs.build(G(.PIE) { $0.paper = "#FFF3E0" }, aspect: 1)
        let slices = pie.filter { ($0.tag?.hasPrefix("value:") ?? false) && $0.closed && $0.fill != nil }
        XCTAssertTrue(slices.contains { $0.stroke == "#FFF3E0" })
        XCTAssertFalse(slices.contains { $0.stroke == "#FFFFFF" }, "beyaz ayraç kalmaz")
    }

    // MARK: v45 formüller

    private func curves(_ paths: [VPath], _ tag: String = "curve") -> [VPath] { paths.filter { $0.tag == tag } }

    func testMeaningPerKind() {
        XCTAssertEqual(.CURVE, GraphFormula.meaning(.FUNCTION))
        XCTAssertEqual(.CURVE, GraphFormula.meaning(.AXES))
        XCTAssertEqual(.CURVE, GraphFormula.meaning(.COORD_PLANE))
        XCTAssertEqual(.SURFACE, GraphFormula.meaning(.AXES_3D))
        XCTAssertEqual(.SET, GraphFormula.meaning(.NUMBER_LINE))
        for k in [GraphKind.BAR, .LINE, .PIE, .SCATTER, .HISTOGRAM] { XCTAssertEqual(.SAMPLED, GraphFormula.meaning(k)) }
        XCTAssertFalse(GraphFormula.accepts(.UNIT_CIRCLE))
        for k in GraphKind.allCases where k != .UNIT_CIRCLE {
            XCTAssertTrue(GraphFormula.accepts(k), k.rawValue)
            XCTAssertNil(GraphFormula.error(k, GraphFormula.example(k)), "örnek formül geçerli: \(k)")
        }
    }

    func testValidationPerKind() {
        func code(_ k: GraphKind, _ f: String) -> MathError.Code? { GraphFormula.error(k, f)?.code }
        XCTAssertNil(code(.FUNCTION, "y = x^2 - 2"))
        XCTAssertNil(code(.FUNCTION, "x^2 + y^2 = 9"))
        XCTAssertNil(code(.AXES, "sin(x); cos(x)"))
        XCTAssertEqual(.UNKNOWN_NAME, code(.FUNCTION, "y = 2a x"))
        XCTAssertEqual(.PARENTHESIS, code(.COORD_PLANE, "sin(x"))
        XCTAssertEqual(.MISSING_OPERAND, code(.FUNCTION, "x^"))
        XCTAssertNil(code(.AXES_3D, "z = x^2 - y^2"))
        XCTAssertEqual(.RELATION, code(.AXES_3D, "x + y = 1"))
        XCTAssertEqual(.UNKNOWN_NAME, code(.AXES_3D, "z = x t"))
        XCTAssertNil(code(.NUMBER_LINE, "-1 ≤ x < 3"))
        XCTAssertNil(code(.NUMBER_LINE, "x = 2; x = -1"))
        XCTAssertNil(code(.NUMBER_LINE, "2.5"))
        XCTAssertEqual(.UNKNOWN_NAME, code(.NUMBER_LINE, "y > 2"))
        XCTAssertNil(code(.BAR, "y = 2x + 1"))
        XCTAssertEqual(.UNKNOWN_NAME, code(.LINE, "y = x + y"))
        XCTAssertEqual(.RELATION, code(.LINE, "x^2 + y^2 = 4"))
        XCTAssertEqual(.RELATION, code(.SCATTER, "x; 2x"))
        XCTAssertNotNil(code(.UNIT_CIRCLE, "x"))
        XCTAssertNil(code(.FUNCTION, ""))
    }

    func testPartsSplitOnlyAtTopLevel() {
        XCTAssertEqual(["x^2", "2x+1"], GraphFormula.parts("x^2; 2x+1"))
        XCTAssertEqual(["min(x; 2)", "x"], GraphFormula.parts("min(x; 2) ; x"))
        XCTAssertEqual(["a", "b"], GraphFormula.parts("a\nb\n"))
        XCTAssertEqual([], GraphFormula.parts(" ; "))
    }

    func testWithFormulaAppliesOrKeepsLastValid() {
        let p = G(.FUNCTION) { $0.preset = "sinus"; $0.freq = 3 }
        let q = Graphs.withFormula(p, "y = x^3")
        XCTAssertEqual("y = x^3", q.formula)
        XCTAssertEqual("", q.preset)
        XCTAssertEqual(1, q.freq)
        XCTAssertEqual(q, Graphs.withFormula(q, "y = x^"))
        XCTAssertEqual("", Graphs.withFormula(q, "  ").formula)
        let bar2 = Graphs.withFormula(G(.BAR) { $0.values = [1, 2, 3] }, "y = x^2")
        XCTAssertTrue(bar2.values.isEmpty)
        let uc = G(.UNIT_CIRCLE)
        XCTAssertEqual(uc, Graphs.withFormula(uc, "x^2"))
        XCTAssertEqual("sin(x)", Graphs.formulaText(G(.FUNCTION) { $0.preset = "sinus" }))
    }

    func testAxesAndPlaneDrawFormulaCurves() {
        for k in [GraphKind.AXES, .COORD_PLANE, .FUNCTION] {
            let none = Graphs.build(G(k) { $0.formula = k == .FUNCTION ? "x" : "" }, aspect: 1.3)
            let with = Graphs.build(G(k) { $0.formula = "y = x^2 - 2" }, aspect: 1.3)
            if k != .FUNCTION { XCTAssertTrue(curves(none).isEmpty, k.rawValue) }
            XCTAssertFalse(curves(with).isEmpty, k.rawValue)
            XCTAssertTrue(with.contains { $0.tag == "formula" && ($0.label?.contains("x²") ?? false) }, k.rawValue)
            for c in curves(with) { for pt in c.points { XCTAssertTrue(pt.x >= -0.001 && pt.x <= 1.001 && pt.y >= -0.001 && pt.y <= 1.001) } }
        }
    }

    func testMultipleCurvesHaveOwnTagsColorsAndLabel() {
        let p = G(.COORD_PLANE) { $0.formula = "y = x^2 - 2; y = 2x + 1; x^2 + y^2 = 4" }
        let paths = Graphs.build(p, aspect: 1.3)
        XCTAssertFalse(curves(paths).isEmpty)
        let c1 = curves(paths, "curve:1"), c2 = curves(paths, "curve:2")
        XCTAssertFalse(c1.isEmpty); XCTAssertFalse(c2.isEmpty)
        XCTAssertTrue(c1.allSatisfy { $0.stroke == Cartesian.EXTRA_COLORS[0] })
        XCTAssertTrue(c2.allSatisfy { $0.stroke == Cartesian.EXTRA_COLORS[1] })
        let label = Graphs.formulaLabel(p)
        XCTAssertTrue(label.contains("x² − 2") && label.contains("2x + 1") && label.contains("x² + y² = 4"), label)
        let many = G(.AXES) { $0.formula = (1...7).map { "x + \($0)" }.joined(separator: ";") }
        let t = Set(tags(Graphs.build(many, aspect: 1.3)).filter { $0.hasPrefix("curve:") })
        XCTAssertEqual(["curve:1", "curve:2", "curve:3"], t)
    }

    private func maxJump(_ cs: [VPath]) -> Float {
        cs.map { c in zip(c.points, c.points.dropFirst()).map { abs($1.y - $0.y) }.max() ?? 0 }.max() ?? 0
    }

    func testAsymptotesAndNaNBreakTheCurve() {
        let hyper = Graphs.build(G(.FUNCTION) { $0.formula = "1/x" }, aspect: 1.3)
        XCTAssertGreaterThanOrEqual(curves(hyper).count, 2)
        XCTAssertLessThan(maxJump(curves(hyper)), 0.5, "dikey bağlantı")
        let tan = Graphs.build(G(.FUNCTION) { $0.formula = "tan(x)" }, aspect: 1.3)
        XCTAssertGreaterThanOrEqual(curves(tan).count, 3)
        XCTAssertLessThan(maxJump(curves(tan)), 0.5)
        for f in ["sqrt(x)", "ln(x)"] {
            let paths = Graphs.build(G(.AXES) { $0.formula = f }, aspect: 1.3)
            let xs = curves(paths).flatMap { $0.points.map(\.x) }
            XCTAssertFalse(xs.isEmpty, f)
            let axis = paths.first { $0.tag == nil && $0.points.count == 2 && abs($0.points[0].x - $0.points[1].x) < 1e-4 && abs($0.points[0].y - $0.points[1].y) > 0.5 }!.points[0].x
            XCTAssertGreaterThanOrEqual(xs.min()!, axis - 0.01, "\(f) eksenin solunda çizilmemeli")
        }
        let two = Graphs.build(G(.COORD_PLANE) { $0.formula = "x; 1/(x-1)" }, aspect: 1.3)
        let extra = curves(two, "curve:1")
        XCTAssertGreaterThanOrEqual(extra.count, 2)
        XCTAssertLessThan(maxJump(extra), 0.5)
    }

    func testDegreeModeChangesPeriod() {
        let rad = Graphs.build(G(.FUNCTION) { $0.formula = "sin(x)"; $0.xMin = 0; $0.xMax = 360 }, aspect: 1.3)
        let deg = Graphs.build(G(.FUNCTION) { $0.formula = "sin(x)"; $0.xMin = 0; $0.xMax = 360; $0.angleDeg = true }, aspect: 1.3)
        func crossings(_ paths: [VPath]) -> Int {
            let pts = curves(paths).flatMap(\.points).enumerated().sorted { $0.element.x != $1.element.x ? $0.element.x < $1.element.x : $0.offset < $1.offset }.map(\.element)
            let mid = (pts.map(\.y).min()! + pts.map(\.y).max()!) / 2
            return zip(pts, pts.dropFirst()).filter { ($0.y - mid) * ($1.y - mid) < 0 }.count
        }
        XCTAssertGreaterThan(crossings(rad), 50)
        XCTAssertTrue((1...4).contains(crossings(deg)), "derece: \(crossings(deg))")
    }

    func testAxes3dDrawsSurfaceFromFormula() {
        XCTAssertTrue(curves(Graphs.build(G(.AXES_3D), aspect: 1)).isEmpty)
        XCTAssertGreaterThan(curves(Graphs.build(G(.AXES_3D) { $0.formula = "z = x^2 - y^2" }, aspect: 1)).count, 10)
        XCTAssertTrue(Graphs.formulaLabel(G(.AXES_3D) { $0.formula = "x^2 - y^2" }).hasPrefix("z ="))
    }

    func testNumberLineSolutionSets() {
        func solve(_ f: String) -> GraphFormula.SolutionSet { GraphFormula.solve(f, -5, 5)! }
        XCTAssertEqual([3.0], solve("x = 3").points)
        XCTAssertEqual([2.5], solve("2.5").points)
        XCTAssertEqual([-2.0, 2.0], solve("x^2 = 4").points)
        XCTAssertEqual([0.0], solve("x^2 = 0").points)
        let a = solve("-1 ≤ x < 3")
        XCTAssertEqual(1, a.intervals.count)
        let iv = a.intervals[0]
        XCTAssertEqual(-1, iv.a, accuracy: 1e-6); XCTAssertEqual(3, iv.b, accuracy: 1e-6)
        XCTAssertTrue(iv.aClosed); XCTAssertFalse(iv.bClosed); XCTAssertFalse(iv.aInf); XCTAssertFalse(iv.bInf)
        let r = solve("x > 2").intervals
        XCTAssertEqual(1, r.count)
        XCTAssertEqual(2, r[0].a, accuracy: 1e-6); XCTAssertFalse(r[0].aClosed); XCTAssertTrue(r[0].bInf)
        let l = solve("x ≤ -1").intervals
        XCTAssertEqual(1, l.count)
        XCTAssertTrue(l[0].aInf); XCTAssertEqual(-1, l[0].b, accuracy: 1e-6); XCTAssertTrue(l[0].bClosed)
        let two = solve("x^2 > 4").intervals
        XCTAssertEqual(2, two.count)
        XCTAssertEqual(-2, two[0].b, accuracy: 1e-6); XCTAssertEqual(2, two[1].a, accuracy: 1e-6)
        let abs1 = solve("|x| <= 1").intervals
        XCTAssertEqual(1, abs1.count)
        XCTAssertEqual(-1, abs1[0].a, accuracy: 1e-6); XCTAssertEqual(1, abs1[0].b, accuracy: 1e-6); XCTAssertTrue(abs1[0].aClosed && abs1[0].bClosed)
        let ne = solve("x != 0").intervals
        XCTAssertEqual(2, ne.count)
        XCTAssertFalse(ne[0].bClosed); XCTAssertFalse(ne[1].aClosed)
        XCTAssertTrue(solve("x^2 < -1").isEmpty)
        let inv = solve("1/x > 0").intervals
        XCTAssertEqual(1, inv.count)
        XCTAssertEqual(0, inv[0].a, accuracy: 1e-6); XCTAssertFalse(inv[0].aClosed)
        XCTAssertEqual([0.0], solve("0 <= x <= 0").points)
        XCTAssertNil(GraphFormula.solve("y > 1", -5, 5))
    }

    func testNumberLineDrawsSetWithDotsAndLabel() {
        let paths = Graphs.build(G(.NUMBER_LINE) { $0.formula = "-1 ≤ x < 3"; $0.repeat = 5 }, aspect: 5)
        let seg = curves(paths)
        XCTAssertEqual(1, seg.count)
        XCTAssertTrue(seg.first?.stroke == "#1E88E5" && (seg.first?.width ?? 0) > 2)
        let keys = paths.filter { $0.tag == "key" }
        XCTAssertTrue(keys.contains { $0.fill == "#1E88E5" })
        XCTAssertTrue(keys.contains { $0.fill != "#1E88E5" })
        XCTAssertEqual("−1 ≤ x < 3", paths.first { $0.tag == "formula" }?.label)
        let pts = Graphs.build(G(.NUMBER_LINE) { $0.formula = "x = -2; x = 4"; $0.repeat = 5 }, aspect: 5)
        let dots = pts.filter { $0.tag == "key" }
        XCTAssertGreaterThanOrEqual(dots.count, 2)
        XCTAssertGreaterThanOrEqual(Set(dots.map(\.fill)).count, 2)
        XCTAssertEqual("x = −2;  x = 4", Graphs.formulaLabel(G(.NUMBER_LINE) { $0.formula = "x = -2; x = 4" }))
    }

    func testSampledChartsUseFormula() {
        let bar = G(.BAR) { $0.formula = "y = x^2"; $0.repeat = 2 }
        XCTAssertEqual([1, 4, 9, 16, 25], Graphs.values(bar))
        XCTAssertEqual([3, 5, 7, 9], Graphs.values(G(.LINE) { $0.formula = "2x + 1"; $0.repeat = 1 }))
        let scatter = G(.SCATTER) { $0.formula = "10/x"; $0.repeat = 1 }
        XCTAssertEqual([1, 10, 2, 5, 3, 3.333, 4, 2.5], Graphs.values(scatter))
        XCTAssertEqual(4, Graphs.itemCount(scatter))
        XCTAssertEqual([0, 0, 1, 2], Graphs.values(G(.PIE) { $0.formula = "x - 2"; $0.repeat = 1 }))
        XCTAssertEqual(0, Graphs.values(G(.LINE) { $0.formula = "1/(x-2)"; $0.repeat = 1 })[1])
        XCTAssertEqual(3, Graphs.itemCount(G(.SCATTER) { $0.formula = "1/(x-2)"; $0.repeat = 1 }))
        XCTAssertEqual(6, Graphs.values(Graphs.morePattern(bar)).count)
        for k in [GraphKind.BAR, .LINE, .PIE, .SCATTER, .HISTOGRAM] {
            let paths = Graphs.build(G(k) { $0.formula = "y = x + 1" }, aspect: 1.3)
            XCTAssertTrue(paths.contains { $0.tag == "formula" && $0.label == "y = x + 1" }, k.rawValue)
        }
        XCTAssertEqual(7, Graphs.values(Graphs.withValue(bar, 0, 7))[0])
    }

    func testUnitCircleIgnoresFormula() {
        XCTAssertEqual(Graphs.build(G(.UNIT_CIRCLE), aspect: 1), Graphs.build(G(.UNIT_CIRCLE) { $0.formula = "x^2" }, aspect: 1))
    }

    func testJavaHashOrderMatchesHashMap() {
        // Java: HashMap<Int,…> 13 öğede 32 kovaya büyür; 40 % 32 = 8 → 40, 8'den sonra (ekleme sırası) gelir
        XCTAssertEqual([1, 2, 3], javaHashOrder([3, 1, 2]))
        XCTAssertEqual([8, 40], javaHashOrder([8, 40]).filter { $0 == 8 || $0 == 40 })
        XCTAssertEqual([16, 0], javaHashOrder([16, 0]))
    }

    // MARK: şekil olarak grafik

    func testGraphShapesBuildStrokes() {
        let style = ShapeStyle(strokeColorHex: "#222222", strokeWidth: 3)
        for id in GraphShapes.graphItems + GraphShapes.curveItems {
            XCTAssertTrue(ShapeFactory.canBuild(id), id)
            XCTAssertNotEqual(id, ShapeFactory.title(id))
            let s = ShapeFactory.build(id, P(100, 100), P(500, 400), style: style, id: "g", order: 0)
            XCTAssertNotNil(s, id)
            XCTAssertNotNil(s?.shape?.graph, id)
            XCTAssertTrue(s?.shape?.subpaths.contains { $0.label != nil } ?? false, "\(id) yazı yok")
        }
        let g = GraphShapes.defaultGraph("curve.sinus")
        XCTAssertEqual("sinus", g.preset)
        XCTAssertEqual("FUNCTION", g.kind)
        let p = GraphShapes.params("curve.sinus", g)
        XCTAssertEqual(p, GraphShapes.params("curve.sinus", GraphShapes.spec(p, g)))
        XCTAssertEqual("x^2 - 2", GraphShapes.defaultGraph("graph.FUNCTION").formula)
        XCTAssertEqual(.LINE, ShapeFactory.fit("graph.NUMBER_LINE"))
    }
}
