import CoreGraphics
import XCTest
@testable import DrawGuidance

/// Formül motoru: Android `FormulaTest` ve `V17FormulaAtomTest` ile aynı beklentiler (CoreText ölçüleriyle).
final class FormulaTests: XCTestCase {
    private let m = FormulaRenderer.Metrics()
    private let size: Float = 40

    private struct G { let text: String; let x: Float; let y: Float; let size: Float; let style: GlyphStyle; let slot: String? }
    private struct R { let x1: Float; let y1: Float; let x2: Float; let thickness: Float }

    private func lay(_ s: String) -> FormulaLayout { Formula.layout(s, size, m) }
    private func glyphs(_ l: FormulaLayout) -> [G] {
        l.ops.compactMap { if case let .glyph(t, x, y, s, st, sl) = $0 { return G(text: t, x: x, y: y, size: s, style: st, slot: sl) }; return nil }
    }
    private func rules(_ l: FormulaLayout) -> [R] {
        l.ops.compactMap { if case let .rule(x1, y1, x2, _, t) = $0 { return R(x1: x1, y1: y1, x2: x2, thickness: t) }; return nil }
    }
    private func strokes(_ l: FormulaLayout) -> [[Float]] {
        l.ops.compactMap { if case let .stroke(p, _, _, _) = $0 { return p }; return nil }
    }
    private func glyph(_ l: FormulaLayout, _ t: String, file: StaticString = #filePath, line: UInt = #line) -> G {
        guard let g = glyphs(l).first(where: { $0.text == t }) else {
            XCTFail("'\(t)' glifi yok: \(glyphs(l).map(\.text))", file: file, line: line)
            return G(text: t, x: .nan, y: .nan, size: .nan, style: .ROMAN, slot: nil)
        }
        return g
    }
    private func adv(_ g: G) -> Float { m.advance(g.text, g.size, g.style) }

    func testFractionIsStackedAndCentered() {
        let l = lay("\\frac{a}{bb}")
        let a = glyph(l, "a")
        let bs = glyphs(l).filter { $0.text == "b" }
        XCTAssertEqual(rules(l).count, 1)
        let bar = rules(l)[0]
        XCTAssertLessThan(a.y, bar.y1)
        XCTAssertTrue(bs.allSatisfy { $0.y > bar.y1 })
        XCTAssertTrue(bar.y1 < 0 && bar.y1 > -0.5 * size)
        let denCenter = (bs.map(\.x).min()! + bs.map { $0.x + adv($0) }.max()!) / 2
        XCTAssertEqual(a.x + adv(a) / 2, denCenter, accuracy: 1.5)
        XCTAssertTrue((0.045 * size...0.065 * size).contains(bar.thickness), "\(bar.thickness)")
    }

    func testScriptsAreRaisedLoweredAndScaled() {
        let sup = glyph(lay("x^2"), "2")
        XCTAssertLessThan(sup.y, -0.25 * size)
        XCTAssertEqual(sup.size, 0.7 * size, accuracy: 0.01)
        XCTAssertGreaterThan(glyph(lay("x_2"), "2").y, 0.05 * size)
        let both = lay("x_1^2")
        XCTAssertLessThan(glyph(both, "2").y, glyph(both, "1").y - 0.4 * size)
        XCTAssertEqual(glyph(both, "x").style, .ITALIC)
        XCTAssertEqual(glyph(both, "1").style, .ROMAN)
        XCTAssertEqual(glyph(lay("e^{x^2}"), "2").size, 0.5 * size, accuracy: 0.01)
    }

    func testFunctionsRomanAndSpacing() {
        let l = lay("\\sin x + 1 = y")
        XCTAssertEqual(glyph(l, "sin").style, .ROMAN)
        let plus = glyph(l, "+"), x = glyph(l, "x")
        XCTAssertGreaterThan(plus.x - (x.x + adv(x)), 0.15 * size)
        XCTAssertEqual(glyph(lay("-b"), "−").x, 0, accuracy: 0.01)
    }

    func testBigOperatorsAndIntegrals() {
        let sum = lay("\\sum_{i=1}^{n} i")
        XCTAssertGreaterThan(glyph(sum, "Σ").size, 1.4 * size)
        XCTAssertGreaterThan(glyph(sum, "1").y, 0.5 * size)
        XCTAssertLessThan(glyph(sum, "n").y, -0.8 * size)
        let int = lay("\\int_a^b f(x)\\,dx")
        let ys = strokes(int).flatMap { p in p.enumerated().filter { $0.offset % 2 == 1 }.map(\.element) }
        XCTAssertFalse(ys.isEmpty)
        XCTAssertGreaterThan(ys.max()! - ys.min()!, 1.8 * size)
        XCTAssertLessThan(glyph(int, "b").y, -0.6 * size)
        XCTAssertGreaterThan(glyph(lay("\\lim_{x \\to 0} f(x)"), "0").y, 0.4 * size)
    }

    func testRadicalsDelimitersMatrices() {
        let r = lay("\\sqrt{x^2+1}")
        XCTAssertFalse(rules(r).isEmpty)
        XCTAssertFalse(strokes(r).isEmpty)
        XCTAssertEqual(glyph(lay("\\sqrt[3]{8}"), "3").size, 0.5 * size, accuracy: 0.01)
        XCTAssertFalse(glyphs(lay("\\left(\\frac{a}{b}\\right)")).contains { $0.text == "(" })
        XCTAssertTrue(glyphs(lay("(a+b)")).contains { $0.text == "(" })
        XCTAssertFalse(glyphs(lay("(\\frac{a}{b})")).contains { $0.text == "(" })
        let mat = lay("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}")
        let a = glyph(mat, "a"), b = glyph(mat, "b"), c = glyph(mat, "c")
        XCTAssertEqual(a.y, b.y, accuracy: 0.01)
        XCTAssertEqual(a.x, c.x, accuracy: 0.5 * size)
        XCTAssertGreaterThan(c.y, a.y + 0.8 * size)
        XCTAssertGreaterThanOrEqual(strokes(lay("\\begin{vmatrix} a & b \\\\ c & d \\end{vmatrix}")).count, 2)
        XCTAssertGreaterThan(lay("f(x) = \\begin{cases} 1, & x > 0 \\\\ 0, & x \\le 0 \\end{cases}").height, 1.8 * size)
    }

    func testSlotsProduceRegionsAndTags() {
        let src = "\\slot{a}{a}x^2 + \\slot{b}{b}x + \\slot{c}{c} = 0"
        XCTAssertEqual(Formula.slots(src), [Slot(name: "a", value: "a"), Slot(name: "b", value: "b"), Slot(name: "c", value: "c")])
        let l = lay(src)
        XCTAssertEqual(Set(l.slots.map(\.name)), ["a", "b", "c"])
        let ga = glyphs(l).first { $0.text == "a" }!
        XCTAssertEqual(ga.slot, "a")
        XCTAssertNil(glyph(l, "x").slot)
        let ra = l.slots.first { $0.name == "a" }!
        XCTAssertTrue(ra.left <= ga.x && ra.right >= ga.x + adv(ga) - 0.01)
        XCTAssertTrue(ra.top < 0 && ra.bottom > 0)
        let twice = lay("(\\slot{a}{a}+\\slot{b}{b})^2 = \\slot{a}{a}^2 + 2\\slot{a}{a}\\slot{b}{b} + \\slot{b}{b}^2")
        XCTAssertEqual(twice.slots.filter { $0.name == "a" }.count, 3)
        XCTAssertEqual(glyphs(twice).filter { $0.slot == "a" }.count, 3)
        let empty = lay("x = \\slot{v}{}")
        XCTAssertEqual(empty.slots.count, 1)
        XCTAssertGreaterThan(empty.slots[0].right - empty.slots[0].left, 0.3 * size)
    }

    func testSetSlotRoundTrip() {
        let src = FormulaCatalog.find("algebra.quadratic")!.source
        var s = Formula.setSlot(src, "a", "2")
        s = Formula.setSlot(s, "b", "-3")
        s = Formula.setSlot(s, "c", "\\frac{1}{2}")
        XCTAssertEqual(Formula.slots(s), [Slot(name: "a", value: "2"), Slot(name: "b", value: "-3"), Slot(name: "c", value: "\\frac{1}{2}")])
        XCTAssertEqual(Formula.plainText(s), "2x² − 3x + 1/2 = 0")
        let l = lay(s)
        XCTAssertTrue(glyphs(l).contains { $0.text == "3" && $0.slot == "b" })
        XCTAssertTrue(glyphs(l).contains { $0.text == "−" && $0.slot == "b" })
        XCTAssertEqual(l.slots.filter { $0.name == "b" }.count, 1)
        let sq = Formula.setSlot(FormulaCatalog.find("algebra.square_of_sum")!.source, "a", "x")
        XCTAssertEqual(Formula.plainText(sq), "(x + b)² = x² + 2xb + b²")
        XCTAssertEqual(Formula.slots(Formula.setSlot("\\slot{n}{n}+1", "n", "{x}}{")), [Slot(name: "n", value: "{x}{}")])
        XCTAssertEqual(Formula.setSlot("\\slot{n}{n}", "n", "\\}\\"), "\\slot{n}{\\}}")
        XCTAssertEqual(Formula.setSlot("x", "q", "1"), "x")
        XCTAssertEqual(Formula.setSlot("\\slot{k}q y", "k", "5"), "\\slot{k}{5} y")
    }

    func testSmartSlotValues() {
        func p(_ src: String, _ kv: [(String, String)]) -> String {
            var s = src
            for (k, v) in kv { s = Formula.setSlot(s, k, v) }
            return Formula.plainText(s)
        }
        let q = FormulaCatalog.find("algebra.quadratic_formula")!.source
        XCTAssertEqual(p(q, [("a", "2"), ("b", "-3"), ("c", "1")]), "x₁,₂ = (3 ± √((−3)² − 4·2·1)) / 2·2")
        XCTAssertEqual(p("F = \\slot{m}{m}\\slot{a}{a}", [("m", "2"), ("a", "3")]), "F = 2·3")
        XCTAssertEqual(p("2\\slot{a}{a}", [("a", "-1")]), "2(−1)")
        XCTAssertEqual(p("F = -\\slot{k}{k}x", [("k", "-5")]), "F = 5x")
        XCTAssertEqual(p("a - \\slot{b}{b}^2", [("b", "-3")]), "a − (−3)²")
        XCTAssertEqual(p("\\sin \\slot{x}{x}", [("x", "x+1")]), "sin(x + 1)")
    }

    func testPlainText() {
        XCTAssertEqual(Formula.plainText("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}"), "x = (−b ± √(b² − 4ac)) / 2a")
        XCTAssertEqual(Formula.plainText("E = mc^2"), "E = mc²")
        XCTAssertEqual(Formula.plainText("\\sin^2 x + \\cos^2 x = 1"), "sin² x + cos² x = 1")
        XCTAssertEqual(Formula.plainText("\\vec{v} = (a, b)"), "v\u{20D7} = (a, b)")
        XCTAssertEqual(Formula.plainText("30^\\circ + x_1"), "30° + x₁")
        XCTAssertEqual(Formula.plainText("\\int_a^b f(x)\\,dx"), "∫ₐᵇ f(x) dx")
        XCTAssertEqual(Formula.plainText("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}"), "(a, b; c, d)")
        XCTAssertEqual(Formula.plainText("\\binom{n}{k}"), "C(n, k)")
    }

    func testBrokenInputNeverCrashes() {
        let inputs = [
            "", " ", "{", "}", "}}}{{{", "\\", "\\frac", "\\frac{a}", "x^", "_", "^^^", "\\sqrt[", "\\sqrt[3",
            "\\left(", "\\right)", "\\left( x", "\\begin{pmatrix} a & b", "\\end{cases}", "&&\\\\&", "a \\\\ b",
            "\\unknowncommand{x}", "\\slot", "\\slot{", "\\slot{a}", "\\slot{}{}", "x''''^2_3", "\\text{", "\\text{a\\}",
            String(repeating: "{", count: 500) + "x", String(repeating: "\\frac{", count: 200), String(repeating: "\\sqrt{", count: 100) + "2",
            "(((((((", "))))]]]", "[a, b)", "\\left\\langle x \\right\\rangle", "🙂 + ∑∫√π≤²₃", "\\mathbb{R} \\mathbf{v} \\boldsymbol{\\alpha}",
            "\\begin{array}{cc} 1 & 2 \\end{array}", "\\overline{\\overline{x}}", "\\not= \\not\\in \\not",
            "\\int\\limits_0^1 \\sum\\nolimits_i \\lim\\limits_{x}", "a &= b \\\\ &= c", "\\color{red}{x}",
        ]
        for src in inputs {
            let l = Formula.layout(src, size, m)
            XCTAssertTrue(l.width.isFinite && l.width >= 0, src)
            XCTAssertTrue(l.ascent.isFinite && l.descent.isFinite, src)
            _ = Formula.plainText(src); _ = Formula.slots(src); _ = Formula.setSlot(src, "a", "{")
        }
        struct Evil: GlyphMetrics {
            func advance(_ text: String, _ size: Float, _ style: GlyphStyle) -> Float { text == "x" ? .nan : 1 }
            func ascent(_ size: Float) -> Float { size }
            func descent(_ size: Float) -> Float { size / 4 }
        }
        XCTAssertTrue(Formula.layout("\\frac{x}{2}", 20, Evil()).width.isFinite)
        XCTAssertGreaterThan(Formula.layout("x", .nan, m).width, 0)
    }

    func testEstimatesOnlyMetricsWork() {
        struct Plain: GlyphMetrics {
            let m = FormulaRenderer.Metrics()
            func advance(_ text: String, _ size: Float, _ style: GlyphStyle) -> Float { m.advance(text, size, style) }
            func ascent(_ size: Float) -> Float { m.ascent(size) }
            func descent(_ size: Float) -> Float { m.descent(size) }
        }
        for t in FormulaCatalog.categories.flatMap(\.templates) {
            let l = Formula.layout(t.source, size, Plain())
            XCTAssertTrue(l.width > 0 && l.height > 0, t.id)
        }
        let bar = rules(Formula.layout("\\frac{1}{2}", size, Plain()))
        XCTAssertEqual(bar.count, 1)
        XCTAssertEqual(bar[0].y1, -0.3 * size, accuracy: 0.02 * size)
    }

    func testCatalogIsCompleteAndEveryTemplateLaysOut() {
        let cats = FormulaCatalog.categories
        XCTAssertEqual(cats.map(\.id), ["algebra", "functions", "trig", "matrix", "calculus", "probability", "physics", "geometry"])
        var ids = Set<String>(), total = 0
        let idRe = try! NSRegularExpression(pattern: "^[a-z0-9_]+\\.[a-z0-9_]+$")
        let nameRe = try! NSRegularExpression(pattern: "^[A-Za-z][A-Za-z0-9_]*$")
        func matches(_ re: NSRegularExpression, _ s: String) -> Bool { re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil }
        for c in cats {
            XCTAssertFalse(c.templates.isEmpty)
            for t in c.templates {
                total += 1
                XCTAssertTrue(ids.insert(t.id).inserted, t.id)
                XCTAssertTrue(matches(idRe, t.id) && t.id.hasPrefix(c.id + "."), t.id)
                XCTAssertFalse(t.source.contains("@"), t.id)
                let l = Formula.layout(t.source, size, m)
                // Android (Java2D) ölçüsünde > 1 em; Times New Roman daha dar olduğundan "aⁿ" ~0.85 em
                XCTAssertTrue(l.width > 0.6 * size && l.ascent > 0 && l.descent > 0, "\(t.id) \(l.width)")
                XCTAssertGreaterThan(l.ops.count, 1, t.id)
                let slots = Formula.slots(t.source)
                XCTAssertFalse(slots.isEmpty, t.id)
                for s in slots {
                    XCTAssertTrue(matches(nameRe, s.name), "\(t.id) \(s.name)")
                    let regions = l.slots.filter { $0.name == s.name }
                    XCTAssertFalse(regions.isEmpty, "\(t.id) \(s.name)")
                    XCTAssertTrue(glyphs(l).contains { $0.slot == s.name }, "\(t.id) \(s.name)")
                    for r in regions {
                        XCTAssertTrue(r.left >= -0.2 * size && r.right <= l.width + 0.2 * size && r.top >= -l.ascent - 0.2 * size &&
                                      r.bottom <= l.descent + 0.2 * size, "\(t.id) \(s.name) \(r)")
                    }
                }
                var src = t.source
                for (k, sl) in slots.enumerated() { src = Formula.setSlot(src, sl.name, k % 2 == 0 ? "-\(k + 2)" : "\(k + 1),5") }
                XCTAssertGreaterThan(Formula.layout(src, size, m).width, 0, t.id)
                XCTAssertFalse(Formula.plainText(t.source).trimmingCharacters(in: .whitespaces).isEmpty, t.id)
                XCTAssertTrue(l.ops.allSatisfy(\.isFinite), t.id)
            }
        }
        XCTAssertEqual(total, 148)
    }

    func testLayoutCoordinatesFollowContract() {
        let l = lay("\\frac{\\sum_{k=1}^{n} k^2}{\\sqrt{2}}")
        for g in glyphs(l) {
            XCTAssertTrue(g.y > -l.ascent && g.y < l.descent + 1)
            XCTAssertTrue(g.x >= -0.1 * size && g.x < l.width)
        }
        XCTAssertEqual(l.height, l.ascent + l.descent, accuracy: 1e-3)
    }

    // MARK: v17 elemanlara ayırma

    func testEveryLetterAndNumberBecomesItsOwnSlot() {
        let a = Formula.atomize("x_{1,2} = \\frac{-\\slot{b}{b} \\pm \\sqrt{\\slot{b}{b}^2 - 4\\slot{a}{a}\\slot{c}{c}}}{2\\slot{a}{a}}")
        let slots = Formula.slots(a)
        XCTAssertEqual(slots.map(\.value), ["x", "1", "2", "b", "b", "2", "4", "a", "c", "2", "a"])
        XCTAssertEqual(slots.map(\.name), (1...slots.count).map(String.init))
        XCTAssertTrue(Formula.isAtomized(a))
        XCTAssertFalse(Formula.isAtomized("a+b"))
        XCTAssertEqual(Formula.plain(a), "x_{1,2} = \\frac{-b \\pm \\sqrt{b^{2} - 4ac}}{2a}")
    }

    func testSameLetterIsEditedIndependently() {
        let a = Formula.atomize("(a + b)^2 = a^2 + 2ab + b^2")
        let slots = Formula.slots(a)
        let firstA = slots.first { $0.value == "a" }!.name
        let edited = Formula.setSlot(a, firstA, "3")
        let after = Formula.slots(edited)
        XCTAssertEqual(after.first { $0.name == firstA }!.value, "3")
        XCTAssertEqual(after.filter { $0.value == "a" }.count, 2)
        XCTAssertEqual(Formula.layout(edited, 40, m).slots.count, slots.count)
    }

    func testTextEnvironmentsDelimitersAndGreek() {
        let src = "|x| = \\begin{cases} x, & x \\ge 0 \\\\ -x, & x < 0 \\end{cases} \\quad \\text{mutlak} \\left(\\alpha x\\right)^{n}"
        let a = Formula.atomize(src)
        XCTAssertTrue(a.contains("\\begin{cases}") && a.contains("\\end{cases}"))
        XCTAssertTrue(a.contains("\\text{mutlak}"))
        XCTAssertTrue(a.contains("\\left(") && a.contains("\\right)"))
        XCTAssertTrue(Formula.slots(a).contains { $0.value == "\\alpha" })
        let l = Formula.layout(a, 40, m)
        XCTAssertTrue(l.width > 0 && !l.slots.isEmpty)
        XCTAssertEqual(Formula.atomize(Formula.plain(a)), a)
    }

    func testDecimalsScriptsAndRoots() {
        let a = Formula.atomize("\\sqrt[3]{x^2} + 3.14r^2 + x_1")
        XCTAssertEqual(Formula.slots(a).map(\.value), ["3", "x", "2", "3.14", "r", "2", "x", "1"])
        XCTAssertEqual(Formula.plain(a), "\\sqrt[3]{x^{2}} + 3.14r^{2} + x_{1}")
        for t in FormulaCatalog.categories.flatMap(\.templates) {
            let s = Formula.atomize(t.source)
            XCTAssertGreaterThan(Formula.layout(s, 40, m).width, 0, t.id)
            XCTAssertEqual(Formula.plainText(Formula.plain(s)).replacingOccurrences(of: " ", with: ""),
                           Formula.plainText(Formula.plain(t.source)).replacingOccurrences(of: " ", with: ""), t.id)
        }
    }

    // MARK: Çizim

    func testRendererDrawsInsideQuad() {
        let src = FormulaCatalog.find("algebra.quadratic_formula")!.source
        let quad = FormulaRenderer.quadAt(src, 40, 10, 10)
        let s = Stroke(id: "f", points: quad, colorHex: "#000000", brushType: .FORMULA, sizePx: 40, opacity: 1, order: 0,
                       formula: FormulaSpec(source: src))
        let w = Int(quad[1].x) + 20, h = Int(quad[3].y) + 20
        var data = [UInt8](repeating: 0, count: w * h * 4)
        data.withUnsafeMutableBytes { buf in
            let c = CGContext(data: buf.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: Bitmaps.space,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
            c.translateBy(x: 0, y: CGFloat(h)); c.scaleBy(x: 1, y: -1)
            StrokeRenderer.drawStroke(c, s)
        }
        var inked = 0, outside = 0
        for y in 0..<h { for x in 0..<w where data[(y * w + x) * 4 + 3] > 40 {
            inked += 1
            if Float(x) < quad[0].x - 1 || Float(x) > quad[1].x + 1 || Float(y) < quad[0].y - 1 || Float(y) > quad[3].y + 1 { outside += 1 }
        } }
        XCTAssertGreaterThan(inked, 500)
        XCTAssertEqual(outside, 0)
        XCTAssertEqual(FormulaRenderer.slotAt(s, quad[0].x - 50, quad[0].y - 50), nil)
        let q = FormulaRenderer.slotQuad(s, "a")!
        let cx = (q[0].x + q[2].x) / 2, cy = (q[0].y + q[2].y) / 2
        XCTAssertEqual(FormulaRenderer.slotAt(s, cx, cy), "a")
        let refit = FormulaRenderer.refit(s, Formula.setSlot(src, "b", "\\frac{1}{2}"))
        XCTAssertEqual(refit[0], quad[0])
        XCTAssertGreaterThan(refit[3].y, quad[3].y - 0.01)
    }
}
