import XCTest
@testable import DrawGuidance

/// Matematik çekirdeği ve formül hesabı: Android `V45MathExprTest` ve `V45FormulaEvalTest` ile aynı beklentiler.
final class MathTests: XCTestCase {
    private func v(_ t: String, _ vars: [String: Double] = [:], _ mode: AngleMode = .RAD, file: StaticString = #filePath, line: UInt = #line) -> Double {
        do { return try MathExpr.eval(try MathExpr.parse(t), vars, mode) } catch { XCTFail("\(t): \(error)", file: file, line: line); return .nan }
    }

    private func near(_ e: Double, _ a: Double, _ msg: String = "", eps: Double = 1e-9, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(abs(e - a) <= eps * max(1, abs(e)), "\(msg): beklenen \(e), bulunan \(a)", file: file, line: line)
    }

    // MARK: MathExpr

    func testPrecedenceTable() {
        let cases: [(String, Double)] = [
            ("1+2*3", 7), ("(1+2)*3", 9), ("2^3^2", 512), ("-2^2", -4), ("(-2)^2", 4), ("2^-1", 0.5),
            ("2^-x", 0.25), ("10-4-3", 3), ("100/10/5", 2), ("2*3/4", 1.5), ("-3*-2", 6), ("--3", 3),
            ("+5", 5), ("2·3", 6), ("6÷4", 1.5), ("6:4", 1.5), ("3×4", 12), ("5 − 7", -2), ("2²", 4),
            ("2³+1", 9), ("x²", 4), ("x⁻¹", 0.5), ("-x^2+1", -3), ("1-x^2", -3), ("2^(1+1)", 4),
            ("[1+1]*{2+1}", 6), ("3!", 6), ("0!", 1), ("2*3!", 12), ("(1+2)!", 6),
            ("1e3", 1000), ("1.5e-2", 0.015), ("2,5*2", 5), (".5+.5", 1), ("1.25", 1.25),
        ]
        for (t, e) in cases { near(e, v(t, ["x": 2]), t) }
    }

    func testImplicitMultiplication() {
        let vars: [String: Double] = ["x": 3, "y": 2, "a": 5]
        let cases: [(String, Double)] = [
            ("2x", 6), ("2x^2", 18), ("xy", 6), ("2(x+1)", 8), ("(x+1)(x-1)", 8), ("x(x+1)", 12),
            ("2πx", 6 * .pi), ("3sin(0)", 0), ("2 x y", 12), ("(2)(3)", 6), ("2a x", 30), ("-2x", -6),
            ("x/2y", 3), ("2|x-5|", 4), ("|x-5||y-5|", 6), ("|-x|", 3),
        ]
        for (t, e) in cases { near(e, v(t, vars), t) }
    }

    func testFunctionsAndConstants() {
        let x = 0.7
        let cases: [(String, Double)] = [
            ("sin(x)", sin(x)), ("sin x", sin(x)), ("sin 2x", sin(2 * x)), ("sin^2 x + cos^2 x", 1), ("sin(x)^2", sin(x) * sin(x)),
            ("sqrt(16)", 4), ("√16", 4), ("√(9+16)", 5), ("∛27", 3), ("cbrt(-8)", -2), ("abs(-3)", 3),
            ("ln(e)", 1), ("ln e^2", 2), ("log(1000)", 3), ("lg 100", 2), ("log2(8)", 3), ("log_2(8)", 3), ("log_3 81", 4),
            ("exp(1)", M_E), ("e^x", exp(x)), ("pi", .pi), ("π/2", .pi / 2), ("PI", .pi), ("tan(pi/4)", 1), ("cot(pi/4)", 1),
            ("floor(2.7)", 2), ("ceil(2.1)", 3), ("round(2.5)", 3), ("sign(-4)", -1), ("min(3; 5)", 3), ("max(3, 5)", 5),
            ("arcsin(1)", .pi / 2), ("atan(1)", .pi / 4), ("tg(0)", 0), ("sinh(0)", 0), ("cosh(0)", 1), ("ln(x)", log(x)),
            ("log(x)", log10(x)), ("sqrt x + 1", x.squareRoot() + 1), ("2sqrt(x)", 2 * x.squareRoot()), ("x^(1/3)", cbrt(x)),
            ("(-8)^(1/3)", -2), ("(-8)^(2/3)", 4),
        ]
        for (t, e) in cases { near(e, v(t, ["x": x]), t) }
    }

    func testDegreeAndRadian() {
        near(0.5, v("sin(30°)")); near(0.5, v("sin 30°"))
        near(0.5, v("cos(60)", [:], .DEG)); near(0.5, v("sin(30)", [:], .DEG)); near(1, v("tan(45)", [:], .DEG))
        near(90, v("asin(1)", [:], .DEG)); near(45, v("atan(1)", [:], .DEG)); near(0.5, v("sin(30°)", [:], .DEG))
        near(.pi / 6, v("30°"))
        XCTAssertEqual(v("sin(pi)"), 0)
        XCTAssertTrue(v("tan(pi/2)").isNaN)
        XCTAssertTrue(v("tan(90)", [:], .DEG).isNaN)
    }

    func testRelationsAndChains() throws {
        let r = try MathExpr.parseRelation("-1 ≤ x < 4")
        XCTAssertEqual(r.ops, [.LE, .LT]); XCTAssertEqual(r.sides.count, 3)
        XCTAssertEqual(try MathExpr.parseRelation("x >= 2").ops, [.GE])
        XCTAssertEqual(try MathExpr.parseRelation("x <= 2").ops, [.LE])
        XCTAssertEqual(try MathExpr.parseRelation("x != 2").ops, [.NE])
        XCTAssertEqual(try MathExpr.parseRelation("x ≠ 2").ops, [.NE])
        XCTAssertEqual(try MathExpr.parseRelation("x^2 + y^2 = 9").ops, [.EQ])
        XCTAssertFalse(try MathExpr.parseRelation("x^2 + 1").isRelation)
        XCTAssertTrue(Rel.LE.holds(2, 2)); XCTAssertFalse(Rel.LT.holds(2, 2)); XCTAssertTrue(Rel.NE.holds(1, 2))
        XCTAssertTrue(Rel.EQ.holds(0.1 + 0.2, 0.3))
        XCTAssertEqual(Rel.LT.flipped(), .GT)
    }

    func testVariablesAndNames() throws {
        XCTAssertEqual(Set(try MathExpr.parse("x^2 + 2xy").variables()), ["x", "y"])
        XCTAssertEqual(Set(try MathExpr.parse("v_0 t - g t^2/2").variables()), ["v_0", "t", "g"])
        let n = try MathExpr.parse("1/do + 1/di", names: ["do", "di"])
        XCTAssertEqual(Set(n.variables()), ["do", "di"])
        near(0.75, try MathExpr.eval(n, ["do": 2, "di": 4]))
        near(1, v("tan θ", ["θ": .pi / 4]))
        XCTAssertEqual(Set(try MathExpr.parseRelation("F = m a").sides.flatMap { $0.variables() }), ["F", "m", "a"])
    }

    private func code(_ t: String) -> MathError.Code? { MathExpr.tryParseRelation(t).1?.code }

    func testParseErrors() {
        XCTAssertEqual(code(""), .EMPTY); XCTAssertEqual(code("   "), .EMPTY)
        XCTAssertEqual(code("2+"), .MISSING_OPERAND); XCTAssertEqual(code("*3"), .MISSING_OPERAND)
        XCTAssertEqual(code("2*/3"), .MISSING_OPERAND); XCTAssertEqual(code("x ="), .MISSING_OPERAND)
        XCTAssertEqual(code("(1+2"), .PARENTHESIS); XCTAssertEqual(code("1+2)"), .PARENTHESIS)
        XCTAssertEqual(code("()"), .MISSING_OPERAND); XCTAssertEqual(code("|x"), .PARENTHESIS)
        XCTAssertEqual(code("2 # 3"), .UNEXPECTED); XCTAssertEqual(code("2 $ 3"), .UNEXPECTED)
        XCTAssertEqual(code("sin()"), .ARGUMENTS); XCTAssertEqual(code("min(3)"), .ARGUMENTS); XCTAssertEqual(code("sin"), .MISSING_OPERAND)
        XCTAssertEqual(MathExpr.tryParseRelation("1 + 2 ) * 3").1?.pos, 6)
        let u = MathExpr.tryParseRelation("2 + @").1
        XCTAssertEqual(u?.pos, 4); XCTAssertEqual(u?.detail, "@")
        XCTAssertEqual(MathExpr.tryParse("x = 2").1?.code, .RELATION)
        XCTAssertEqual(MathExpr.tryParseRelation("π + ) ").1?.pos, 4)
    }

    func testStrictEvaluation() {
        func strictCode(_ t: String, _ vars: [String: Double] = [:]) -> MathError.Code? {
            do { _ = try MathExpr.evalStrict(try MathExpr.parse(t), vars); return nil } catch let e as MathException { return e.error.code } catch { return nil }
        }
        XCTAssertEqual(strictCode("1/0"), .DIVISION_BY_ZERO); XCTAssertEqual(strictCode("1/(x-2)", ["x": 2]), .DIVISION_BY_ZERO)
        XCTAssertEqual(strictCode("0^-1"), .DIVISION_BY_ZERO); XCTAssertEqual(strictCode("sqrt(-4)"), .DOMAIN)
        XCTAssertEqual(strictCode("ln(0)"), .DOMAIN); XCTAssertEqual(strictCode("log(-1)"), .DOMAIN)
        XCTAssertEqual(strictCode("asin(2)"), .DOMAIN); XCTAssertEqual(strictCode("tan(pi/2)"), .DOMAIN)
        XCTAssertEqual(strictCode("(-8)^(1/2)"), .DOMAIN); XCTAssertEqual(strictCode("(-1)!"), .DOMAIN)
        XCTAssertEqual(strictCode("q + 1"), .UNKNOWN_NAME); XCTAssertEqual(strictCode("10^400"), .OVERFLOW)
        XCTAssertNil(strictCode("sqrt(4)"))
        XCTAssertTrue(v("1/0").isInfinite); XCTAssertTrue(v("sqrt(-1)").isNaN); XCTAssertTrue(v("ln(0)").isNaN)
    }

    func testCompiledMatchesInterpreted() throws {
        for t in ["x^2 - 3x + 2", "sin(x)/x", "e^(-x^2/2)", "|x| + floor(y)", "max(x, y) - min(x; y)", "x^(1/3)", "log_2(x+3)", "3!x"] {
            let n = try MathExpr.parse(t)
            let f = try MathExpr.compile(n, ["x", "y"])
            for x in [-2.5, -1.0, 0.3, 1.7, 4.0] {
                let a = try MathExpr.eval(n, ["x": x, "y": 0.5]), b = f([x, 0.5])
                if a.isNaN { XCTAssertTrue(b.isNaN, t) } else { near(a, b, t) }
            }
        }
        XCTAssertThrowsError(try MathExpr.compile(try MathExpr.parse("x + q"), ["x"])) { e in
            XCTAssertEqual((e as? MathException)?.error.code, .UNKNOWN_NAME)
            XCTAssertEqual((e as? MathException)?.error.detail, "q")
        }
    }

    func testPrettyPrinting() throws {
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("2x^2-3")), "2x² − 3")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("A*sin(k x)")), "A·sin(kx)")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("x + -3")), "x − 3")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("9.8*t^2/2")), "9,8·t²/2")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("v_0*t")), "v₀·t")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parseRelation("-1 <= x < 4")), "−1 ≤ x < 4")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("(x+1)^2")), "(x + 1)²")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("sqrt(x)")), "√x")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("2*3")), "2·3")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("x^-1")), "x⁻¹")
        XCTAssertEqual(MathExpr.pretty(try MathExpr.parse("1.50"), decimalComma: false), "1.5")
    }

    // MARK: Kesin hesap

    private func exact(_ t: String) throws -> Value { try ExactEval.eval(try MathExpr.parse(t)) }
    private func q(_ t: String) -> String {
        guard let v = try? exact(t), case let .exact(r) = v else { return "yaklaşık/hata" }
        return r.description
    }

    func testBigInt() {
        let a = BigInt("123456789012345678901234567890")!, b = BigInt("-987654321098765432")!
        XCTAssertEqual((a * b).description, "-121932631137021795212620027521140070120989178480")
        XCTAssertEqual((a + b).description, "123456789011358024580135802458")
        XCTAssertEqual((a / b).description, "-124999998860")
        XCTAssertEqual(a.quotientAndRemainder(b).1.description, "925925953827160370")
        XCTAssertEqual(BigInt(-7).mod(BigInt(3)), BigInt(2))
        XCTAssertEqual(BigInt(2).pow(100).description, "1267650600228229401496703205376")
        XCTAssertEqual(BigInt(462).gcd(BigInt(-1071)), BigInt(21))
        XCTAssertEqual(BigInt(1024).bitLength, 11); XCTAssertEqual(BigInt(-1024).bitLength, 10)
        XCTAssertTrue(b < a); XCTAssertTrue(BigInt(-3) < BigInt(-2))
    }

    func testRationalArithmeticTable() {
        let cases: [(String, String)] = [
            ("1/2 + 1/3", "5/6"), ("2/4", "1/2"), ("1 - 1/3", "2/3"), ("(2/3)*(9/4)", "3/2"), ("(1/2)/(1/4)", "2"),
            ("2^10", "1024"), ("2^-2", "1/4"), ("(-3)^2", "9"), ("-3^2", "-9"), ("(2/3)^3", "8/27"), ("0.1 + 0.2", "3/10"),
            ("1.5 * 4", "6"), ("sqrt(9/4)", "3/2"), ("sqrt(16)", "4"), ("∛(-27)", "-3"), ("8^(1/3)", "2"), ("4^(3/2)", "8"),
            ("|-7/2|", "7/2"), ("5!", "120"), ("floor(7/2)", "3"), ("ceil(-7/2)", "-3"), ("2*3+4*5", "26"), ("1/3+1/3+1/3", "1"),
            ("sin(30°)", "1/2"), ("cos(60°)", "1/2"), ("tan(45°)", "1"), ("sin(90°)", "1"), ("cos(180°)", "-1"), ("sin(210°)", "-1/2"),
            ("12345678901234567890 + 1", "12345678901234567891"),
        ]
        for (t, e) in cases { XCTAssertEqual(q(t), e, t) }
    }

    func testApproximateWhenIrrational() throws {
        let v = try exact("sqrt(2)")
        if case .approx = v {} else { XCTFail("yaklaşık bekleniyordu") }
        XCTAssertEqual(v.toDouble(), 1.41421356, accuracy: 1e-8)
        XCTAssertEqual(try exact("pi*2^2").toDouble(), 12.566370614, accuracy: 1e-8)
        XCTAssertEqual(ExactEval.tidy(.approx(0.49999999999999994)), .exact(Rational.of(1, 2)))
        XCTAssertEqual(ExactEval.tidy(.approx(2.9999999999999996)), .exact(Rational.of(3)))
        if case .exact = ExactEval.tidy(.approx(.pi)) { XCTFail("π kesin olmamalı") }
    }

    func testExactErrors() throws {
        func err(_ t: String) -> MathError.Code? {
            do { _ = try exact(t); return nil } catch let e as MathException { return e.error.code } catch { return nil }
        }
        XCTAssertEqual(err("1/0"), .DIVISION_BY_ZERO); XCTAssertEqual(err("5/(3-3)"), .DIVISION_BY_ZERO); XCTAssertEqual(err("0^(-2)"), .DIVISION_BY_ZERO)
        XCTAssertEqual(err("sqrt(-4)"), .DOMAIN); XCTAssertEqual(err("ln(0)"), .DOMAIN); XCTAssertEqual(err("(-4)^(1/2)"), .DOMAIN)
        XCTAssertEqual(err("tan(90°)"), .DOMAIN); XCTAssertEqual(err("csc(180°)"), .DOMAIN); XCTAssertEqual(err("e^1000"), .OVERFLOW)
        XCTAssertEqual(FormulaEval.resultTex(try exact("10^999"), false, true), "\\approx 1\\cdot 10^{999}")
        XCTAssertEqual(FormulaEval.resultTex(try exact("sqrt(2)/10^8"), false, true), "\\approx 1{,}41421\\cdot 10^{-8}")
        XCTAssertEqual(FormulaEval.resultTex(try exact("pi*10^20"), false, true), "\\approx 3{,}14159\\cdot 10^{20}")
    }

    func testRationalBasics() throws {
        XCTAssertEqual(try Rational.parse("2.5"), Rational.of(5, 2))
        XCTAssertEqual(try Rational.parse("0.25").description, "1/4")
        XCTAssertEqual(Rational.of(1, -2).description, "-1/2")
        XCTAssertEqual(Rational.of(5, 2).terminatingDecimal(), "2.5")
        XCTAssertNil(Rational.of(1, 3).terminatingDecimal())
        XCTAssertEqual(Rational.of(9, 4).root(2), Rational.of(3, 2))
        XCTAssertNil(Rational.of(2).root(2))
        XCTAssertTrue(Rational.of(1, 3) < Rational.of(1, 2))
        XCTAssertEqual(FormulaEval.decimal(12.566370614359172), "12.5664")
        XCTAssertEqual(FormulaEval.decimal(0.000123456789), "0.000123457")
        XCTAssertEqual(FormulaEval.decimal(-2.5), "-2.5")
    }

    // MARK: Formüle sonuç ekleme

    private func run(_ template: String, _ values: [String: String], decimalComma: Bool = true) -> FormulaEval.Outcome {
        var src = Formula.atomize(template)
        for s in Formula.slots(src) { if let v = values[s.value] { src = Formula.setSlot(src, s.name, v) } }
        return FormulaEval.annotate(src, decimalComma: decimalComma)
    }

    private func shown(_ o: FormulaEval.Outcome) -> String { Formula.plainText(o.source).replacingOccurrences(of: " ", with: "") }

    func testLinearExpression() {
        let o = run("2x+3", ["x": "4"])
        XCTAssertEqual(o.result, "=11"); XCTAssertNil(o.error)
        XCTAssertTrue(o.source.hasSuffix(FormulaEval.RESULT_MARK + "=11"))
        XCTAssertTrue(shown(o).hasPrefix("2·4+3"), shown(o)); XCTAssertTrue(shown(o).hasSuffix("=11"), shown(o))
    }

    func testFalseInequalityShowsTrueRelation() {
        let o = run("x^2 \\ge 10", ["x": "3"])
        XCTAssertEqual(o.result, "<")
        let text = shown(o)
        XCTAssertTrue(text.contains("3²<10") || text.contains("3^2<10"), text)
        XCTAssertFalse(text.contains("≥"))
        let back = FormulaEval.annotate(Formula.setSlot(o.source, Formula.slots(o.source).first!.name, "4"))
        XCTAssertEqual(back.result, "≥")
        XCTAssertTrue(Formula.plainText(back.source).contains("≥"))
        var s = o.source
        for _ in 0..<5 { s = FormulaEval.annotate(s).source }
        XCTAssertEqual(s, o.source)
    }

    func testRelationSelectionTable() {
        let cases: [(String, String, String)] = [
            ("x+1 = 5", "4", "="), ("x+1 = 5", "3", "<"), ("x+1 = 5", "6", ">"),
            ("2x < 7", "3", "<"), ("2x < 7", "4", ">"), ("2x < 8", "4", "="),
            ("x \\le 2", "2", "≤"), ("x \\le 2", "3", ">"), ("x \\ge 2", "1", "<"), ("x \\ge 2", "2", "≥"),
            ("x \\neq 3", "3", "="), ("x \\neq 3", "5", "≠"), ("x > 0", "0", "="), ("x > 0", "-1", "<"),
            ("\\frac{x}{2} = 1.5", "3", "="), ("x^2 = 2", "1.4142", "<"),
        ]
        for (tpl, x, rel) in cases { XCTAssertEqual(run(tpl, ["x": x]).result, rel, "\(tpl) x=\(x)") }
    }

    func testLoneUnknownSideGetsValue() {
        let o = run("A = \\pi r^2", ["r": "2"])
        XCTAssertTrue(o.result?.hasPrefix("\\approx 12{,}5664") ?? false, o.result ?? "nil")
        let f = run("F = m a", ["m": "2", "a": "3"])
        XCTAssertEqual(f.result, "=6"); XCTAssertTrue(shown(f).hasSuffix("=6"), shown(f))
        XCTAssertNil(run("F = m a", ["m": "2"]).result)
    }

    func testFractionsAndDecimalsDisplay() {
        XCTAssertEqual(run("\\frac{1}{2} + \\frac{x}{3}", ["x": "1"]).result, "=\\frac{5}{6}")
        XCTAssertEqual(run("\\frac{1}{3} - \\frac{x}{2}", ["x": "1"]).result, "=-\\frac{1}{6}")
        XCTAssertEqual(run("2.5 x", ["x": "3"]).result, "=7{,}5")
        XCTAssertEqual(run("2.5 x", ["x": "3"], decimalComma: false).result, "=7.5")
        XCTAssertEqual(run("\\sqrt{x}", ["x": "9"]).result, "=3")
        XCTAssertEqual(run("\\sqrt{x}", ["x": "2"]).result, "\\approx 1{,}41421")
        XCTAssertEqual(run("x^3", ["x": "2"]).result, "=8")
        XCTAssertEqual(run("2^{x}", ["x": "10"]).result, "=1024")
    }

    func testNegativeValuesAreParenthesizedForPowers() {
        let o = run("x^2", ["x": "-3"])
        XCTAssertEqual(o.result, "=9")
        let t = shown(o)
        XCTAssertTrue(t.hasPrefix("(−3)²") || t.hasPrefix("(-3)²") || t.hasPrefix("(−3)^2"), t)
        XCTAssertEqual(run("2x", ["x": "-5"]).result, "=-10")
    }

    func testDivisionByZeroAndDomainErrors() {
        let d = run("\\frac{1}{x}", ["x": "0"])
        XCTAssertEqual(d.error?.code, .DIVISION_BY_ZERO)
        XCTAssertTrue(d.source.hasSuffix("=\\text{tanımsız}"))
        XCTAssertEqual(run("\\sqrt{x}", ["x": "-4"]).error?.code, .DOMAIN)
        let l = FormulaEval.annotate(Formula.atomize("\\ln 0"), undefined: "undefined")
        XCTAssertEqual(l.error?.code, .DOMAIN)
        XCTAssertTrue(l.source.hasSuffix("\\text{undefined}"))
        let ok = FormulaEval.annotate(Formula.setSlot(d.source, Formula.slots(d.source)[1].name, "4"))
        XCTAssertNil(ok.error); XCTAssertEqual(ok.result, "=\\frac{1}{4}"); XCTAssertFalse(ok.source.contains("tanımsız"))
    }

    func testNothingAddedWhileUnknownsRemain() {
        let o = FormulaEval.annotate(Formula.atomize("2x+3"))
        XCTAssertNil(o.result); XCTAssertEqual(o.source, Formula.atomize("2x+3"))
        XCTAssertNil(FormulaEval.annotate(Formula.atomize("5")).result)
        XCTAssertNil(FormulaEval.annotate(Formula.atomize("x = 5")).result)
        for t in ["\\int_0^1 x\\,dx", "\\sum_{k=1}^{n} k", "1 \\pm 2", "\\vec{v}"] { XCTAssertNil(FormulaEval.annotate(Formula.atomize(t)).result, t) }
    }

    func testResultUpdatesAndStrips() {
        let a = run("2x+3", ["x": "4"])
        let xSlot = Formula.slots(a.source).first { $0.value == "4" }!.name
        let b = FormulaEval.annotate(Formula.setSlot(a.source, xSlot, "5"))
        XCTAssertEqual(b.result, "=13")
        XCTAssertEqual(b.source.components(separatedBy: FormulaEval.RESULT_MARK).count - 1, 1)
        let c = FormulaEval.annotate(Formula.setSlot(b.source, xSlot, "x"))
        XCTAssertNil(c.result); XCTAssertFalse(c.source.contains("dgres")); XCTAssertEqual(c.source, Formula.atomize("2x+3"))
    }

    func testMarkersSurviveAtomizeAndPlain() {
        let a = run("2x+3", ["x": "4"])
        XCTAssertEqual(Formula.atomize(a.source), a.source)
        XCTAssertTrue(Formula.isAtomized(a.source))
        let plain = Formula.plain(a.source)
        XCTAssertFalse(plain.contains("dgres"), plain); XCTAssertFalse(plain.contains("11"), plain)
        let r = run("x^2 \\ge 10", ["x": "3"])
        XCTAssertTrue(Formula.plain(r.source).contains("\\ge"))
        XCTAssertFalse(Formula.plainText(r.source).contains("dgrel"))
    }

    func testCatalogTemplatesDoNotBreak() {
        for cat in FormulaCatalog.categories {
            for t in cat.templates {
                let src = Formula.atomize(t.source)
                let o = FormulaEval.annotate(src)
                if o.result == nil { XCTAssertEqual(o.source, src, t.id) }
                XCTAssertEqual(FormulaEval.strip(o.source), src, t.id)
            }
        }
    }
}
