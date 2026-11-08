import Foundation

/// Yapay el yazısı üreteci (Android `InkSynth` ile birebir, aynı tohumla aynı mürekkep): testler ve eğitim modunda
/// el yazısının yazıya çevrilmesinin gösterimi.
enum InkSynth {
    struct Spec {
        let sketches: [String], top: Float, bottom: Float, marks: Marks
        init(_ sketches: [String], _ top: Float, _ bottom: Float, _ marks: Marks = .NONE) { self.sketches = sketches; self.top = top; self.bottom = bottom; self.marks = marks }
    }

    private static let XH: Float = -1

    private static let O_LO = ["@48,50,46,50,-100,-460", "50,0 12,14 0,52 16,90 50,100 84,86 96,48 82,12 50,0"]
    private static let O_UP = ["@44,50,44,50,-90,-450", "@42,50,40,50,-60,300"]
    private static let U = ["6,0 6,70 22,96 50,100 76,84 *84,0 *84,100", "4,0 8,72 26,98 50,100 74,96 90,70 94,0", "4,0 6,68 22,96 48,100 72,86 *82,0 *82,86 94,100"]
    private static let C = ["84,18 56,0 18,10 2,52 22,92 58,100 84,82", "@46,50,44,50,-50,-310", "70,10 46,0 16,14 6,52 22,90 50,100 76,88"]
    private static let S = ["82,14 56,0 22,6 12,26 34,46 72,56 86,78 64,98 28,100 4,86", "76,10 48,0 20,10 16,30 44,48 70,60 76,82 54,100 22,96 2,82", "*78,6 46,0 16,10 18,32 *60,50 80,72 64,96 30,100 *4,86"]
    private static let G_LO = [
        "78,10 48,0 12,10 4,28 22,46 50,48 76,32 *80,2 80,82 62,100 28,98 8,84",
        "@40,24,36,24,-10,-370 | 80,0 80,78 60,98 26,100 6,84",
        "78,10 48,0 12,10 4,28 22,46 50,48 76,32 *80,2 80,70 66,96 40,100 26,86 44,70 84,60"
    ]
    private static let G_UP = ["84,20 54,0 18,12 0,52 16,90 50,100 80,88 *84,56 *52,56", "84,20 54,0 16,12 0,52 16,90 50,100 82,86 | 50,56 86,56", "84,20 54,0 18,12 0,52 16,90 50,100 80,88 84,60 *60,58"]
    private static let STEM = ["50,0 50,100", "49,0 51,100"]
    private static let I_LO = ["50,0 50,100", "50,0 *50,84 60,100 76,96"]
    private static let J_LO = ["70,0 *70,78 54,98 26,100 6,84", "70,0 70,76 58,96 30,100 8,86"]
    private static let I_UP = ["50,0 50,100", "50,0 50,100 | 22,0 78,0 | 22,100 78,100"]


    static let specs: [Character: Spec] = {
        var m: [Character: Spec] = [:]
        // rakamlar
        m["0"] = Spec(["30,0 8,20 2,55 12,88 32,100 52,85 58,50 50,15 30,0", "@30,50,27,50,200,-160", "@30,50,28,50,90,450", "34,4 12,18 4,50 14,86 32,100 52,88 58,52 50,16 30,2 22,8"], 0, 1)
        m["1"] = Spec(["45,0 44,100", "20,30 38,15 *52,0 50,100", "*28,20 *50,0 *51,100 | 28,100 72,100", "*8,38 *52,0 50,100"], 0, 1)
        m["2"] = Spec(["4,20 25,0 52,2 64,22 56,45 30,72 *4,98 *68,96", "6,25 30,0 58,8 62,32 40,62 12,90 6,100 16,96 30,92 50,98 *70,96", "8,14 30,0 58,6 *64,24 *6,100 *70,100"], 0, 1)
        m["3"] = Spec(["10,8 35,0 56,12 52,32 *30,46 55,55 64,78 48,98 20,100 2,86", "*8,2 *58,0 *28,42 52,46 62,68 52,92 26,100 4,90", "10,6 30,0 50,8 46,26 *28,36 58,48 64,76 44,98 14,96 2,82"], 0, 1)
        m["4"] = Spec(["*42,0 *4,62 *70,62 | 55,20 55,100", "*55,100 *55,2 *3,68 *72,68", "*10,0 *8,55 *70,55 | 55,5 55,100", "*32,6 *6,60 *76,60 | 58,0 58,100"], 0, 1)
        m["5"] = Spec(["*14,0 *10,42 32,36 54,44 62,68 52,92 28,100 6,92 | 14,0 58,2", "*60,2 *16,0 *12,44 40,36 60,52 58,82 36,100 6,92", "*16,4 *12,46 36,40 56,52 58,78 42,98 18,100 2,90 | 14,4 40,0 62,4"], 0, 1)
        m["6"] = Spec(["58,6 40,0 18,14 6,48 8,82 28,100 52,96 62,74 50,56 26,54 8,68", "52,2 22,38 8,72 18,96 40,100 58,84 56,62 36,52 14,62", "56,4 30,6 10,34 6,70 22,98 48,100 62,78 52,56 28,52 8,66"], 0, 1)
        m["7"] = Spec(["*2,0 *62,2 *22,100", "*2,0 *62,2 *22,100 | 18,50 56,50", "*0,8 *4,0 *60,0 38,50 30,100", "*4,14 *8,2 *62,4 44,40 32,100 | 16,54 58,50"], 0, 1)
        m["8"] = Spec(["54,14 32,0 10,14 16,36 52,62 58,86 34,100 8,88 10,66 48,36 50,12 36,2", "@30,23,20,23,-80,-440 | @30,73,26,27,-100,-460", "30,50 10,30 12,8 32,0 50,10 48,32 30,50 8,70 12,94 34,100 54,88 52,66 30,50"], 0, 1)
        m["9"] = Spec(["56,16 34,0 8,12 6,36 28,48 50,40 *58,6 *56,100", "56,18 34,0 8,14 8,38 30,48 54,36 *58,8 56,60 46,92 22,100 6,90", "58,20 36,0 8,10 6,34 26,46 52,40 *60,10 58,60 40,94 12,100"], 0, 1)
        // küçük harfler
        m["a"] = Spec(["78,22 52,0 16,12 2,52 18,94 48,100 76,70 *82,2 82,86 96,100", "@40,52,38,48,-30,-370 | 80,0 82,100", "14,20 44,0 72,10 78,34 *78,100 | 78,50 36,46 8,70 24,98 58,92 78,74", "86,18 72,55 60,85 44,100 18,94 2,60 14,16 40,0 62,18 76,62 92,100"], XH, 1)
        m["b"] = Spec(["*12,0 *12,100 12,68 38,48 72,50 86,74 72,98 36,100 12,86", "12,0 12,100 | 12,64 44,46 80,54 86,80 64,100 30,98 12,88", "18,30 30,10 26,0 16,6 12,30 *12,100 12,66 40,46 74,50 88,76 70,98 34,100 12,88"], 0, 1)
        m["c"] = Spec(C, XH, 1)
        m["ç"] = Spec(C, XH, 1, .CEDILLA_BELOW)
        m["d"] = Spec(["78,60 46,46 10,58 2,80 20,98 50,100 78,82 *84,0 *84,100", "@40,74,36,26,-10,-370 | 82,0 84,100", "80,62 50,46 14,56 2,78 18,98 48,100 76,86 *82,20 72,4 80,0 88,10 *84,100"], 0, 1)
        m["e"] = Spec(["6,56 62,54 88,42 72,8 42,0 12,18 2,58 18,92 52,100 88,84", "2,72 44,58 66,36 62,12 40,0 14,18 6,58 24,92 58,100 86,82", "10,55 70,50 84,30 64,4 36,0 10,20 2,58 16,90 48,100 80,90"], XH, 1)
        m["f"] = Spec(["80,10 60,0 44,6 38,28 *38,100 | 8,40 70,42", "70,5 50,0 40,18 38,100 | 12,38 64,38"], 0, 1)
        m["g"] = Spec(G_LO, XH, 1.42)
        m["ğ"] = Spec(G_LO, XH, 1.42, .BREVE_ABOVE)
        m["h"] = Spec(["*12,0 *12,100 12,68 38,46 68,46 84,64 *84,100", "12,0 12,100 | 12,60 38,44 66,46 82,64 84,100", "4,40 24,14 26,0 14,4 10,30 *10,100 10,66 36,46 64,46 80,64 *82,100"], 0, 1)
        m["ı"] = Spec(I_LO, XH, 1)
        m["i"] = Spec(I_LO, XH, 1, .DOT_ABOVE)
        m["j"] = Spec(J_LO, XH, 1.42, .DOT_ABOVE)
        m["k"] = Spec(["12,0 *12,100 | 68,44 *16,70 *72,100", "*12,0 *12,100 *12,72 *66,44 *28,66 *74,100", "12,0 12,100 | 70,40 14,66 | 34,56 74,100"], 0, 1)
        m["l"] = Spec(["48,0 48,100", "6,78 32,48 50,16 42,0 28,10 24,48 26,86 40,100 62,90", "42,0 *42,82 52,100 70,94"], 0, 1)
        m["m"] = Spec(["*6,0 *6,100 *6,32 26,4 44,2 58,18 *60,100 *60,32 80,4 100,2 114,18 *116,100", "6,0 6,100 | 6,28 26,4 46,4 58,22 60,100 | 60,28 80,4 100,4 114,22 116,100", "*0,18 *8,0 *8,100 8,34 26,4 44,4 56,22 *58,100 58,34 78,4 98,4 110,22 *112,100"], XH, 1)
        m["n"] = Spec(["*6,0 *6,100 *6,30 30,4 54,2 72,18 *74,100", "6,0 6,100 | 6,28 32,4 58,4 72,24 74,100", "*0,20 *8,0 *8,100 8,36 30,4 56,2 72,20 *76,100"], XH, 1)
        m["o"] = Spec(O_LO, XH, 1)
        m["ö"] = Spec(O_LO, XH, 1, .TWO_DOTS_ABOVE)
        m["p"] = Spec(["*10,0 *10,100 | 10,10 38,0 72,4 88,26 70,48 36,50 10,40", "*10,100 *10,0 40,0 74,6 88,28 70,48 36,50 *10,46", "*10,0 *10,100 | 10,14 40,0 76,6 90,26 74,46 40,50"], XH, 1.42)
        m["q"] = Spec(["80,12 48,0 12,10 4,28 22,48 52,50 78,34 *82,0 *82,100", "@40,26,36,24,-10,-370 | 80,0 80,100", "80,12 48,0 12,10 4,28 22,48 52,50 78,34 *82,0 *82,86 96,74"], XH, 1.42)
        m["r"] = Spec(["*10,0 *10,100 *10,38 32,8 56,0 78,10", "10,0 10,100 | 10,32 34,4 60,0 76,8", "*10,0 *10,100 10,40 24,12 46,0 70,6"], XH, 1)
        m["s"] = Spec(S, XH, 1)
        m["ş"] = Spec(S, XH, 1, .CEDILLA_BELOW)
        m["t"] = Spec(["42,0 *42,84 54,100 74,94 | 6,28 78,30", "40,0 40,100 | 8,30 76,28", "30,0 *30,86 42,100 62,92 | 4,24 70,20"], 0.14, 1)
        m["u"] = Spec(U, XH, 1)
        m["ü"] = Spec(U, XH, 1, .TWO_DOTS_ABOVE)
        m["v"] = Spec(["*0,0 *44,100 *86,0", "0,0 18,58 38,96 46,100 56,88 72,42 86,0", "*0,4 *8,0 *44,100 *84,0"], XH, 1)
        m["w"] = Spec(["*0,0 *26,100 *54,28 *80,100 *106,0", "0,0 4,72 16,100 32,98 46,72 *52,30 *58,72 72,100 88,98 98,70 102,0"], XH, 1)
        m["x"] = Spec(["0,0 88,100 | 88,0 0,100", "6,6 26,0 42,22 44,50 40,78 22,100 4,94 | 84,6 64,0 48,22 44,50 48,78 64,100 84,94"], XH, 1)
        m["y"] = Spec(["*0,0 *44,50 | *88,0 *12,100", "6,0 6,32 22,48 46,50 70,40 *80,0 *80,82 62,100 32,98 12,86", "*0,0 *44,50 | 88,0 50,60 30,90 12,100"], XH, 1.42)
        m["z"] = Spec(["*6,0 *84,0 *6,100 *88,100", "*6,0 *84,0 *6,100 *88,100 | 24,50 68,50", "*8,6 *82,0 *4,100 *86,96 | 20,52 66,48"], XH, 1)
        // büyük harfler
        m["A"] = Spec(["*0,100 *38,0 *78,100 | 16,62 60,62", "38,0 2,100 | 38,0 76,100 | 18,60 58,60", "*0,100 30,10 40,0 50,10 *80,100 | 16,60 64,60"], 0, 1)
        m["B"] = Spec(["*10,0 *10,100 | *10,0 52,2 70,16 64,38 *12,48 62,54 80,74 68,96 *10,100", "*10,100 *10,0 54,2 70,16 62,40 *12,48 64,56 80,76 66,96 *10,100", "10,0 10,100 | *10,0 50,0 66,14 60,36 *10,48 | *10,48 64,54 80,74 66,96 *10,100"], 0, 1)
        m["C"] = Spec(C, 0, 1)
        m["Ç"] = Spec(C, 0, 1, .CEDILLA_BELOW)
        m["D"] = Spec(["10,0 10,100 | *10,0 48,4 76,28 78,70 54,96 *10,100", "*10,100 *10,0 52,4 78,30 80,68 58,94 *10,100", "*8,4 *10,100 | *4,4 44,0 74,22 80,60 60,92 *10,100"], 0, 1)
        m["E"] = Spec(["*74,0 *6,0 *6,100 *74,100 | 6,50 58,50", "6,0 6,100 | 6,0 72,0 | 6,50 56,50 | 6,100 74,100", "72,14 44,0 14,12 16,36 *40,48 12,62 6,86 36,100 76,90", "*6,0 *6,100 *74,100 | 6,0 72,0 | 6,50 56,50"], 0, 1)
        m["F"] = Spec(["*74,0 *6,0 *6,100 | 6,48 54,48", "6,0 6,100 | 6,0 72,0 | 6,50 52,50", "*6,0 *6,100 | 6,0 72,0 | 6,46 50,46"], 0, 1)
        m["G"] = Spec(G_UP, 0, 1)
        m["Ğ"] = Spec(G_UP, 0, 1, .BREVE_ABOVE)
        m["H"] = Spec(["6,0 6,100 | 74,0 74,100 | 6,50 74,50", "*6,0 *6,100 *6,48 *74,48 | 74,0 74,100", "*6,0 *8,100 | *72,0 *74,100 | 0,52 80,48"], 0, 1)
        m["I"] = Spec(I_UP, 0, 1)
        m["İ"] = Spec(I_UP, 0, 1, .DOT_ABOVE)
        m["J"] = Spec(["74,0 *74,76 58,98 30,100 6,82", "*74,0 *74,76 58,98 30,100 6,82 | 44,0 100,0", "54,0 *74,0 *74,76 58,98 30,100 6,82"], 0, 1)
        m["K"] = Spec(["6,0 6,100 | *74,0 *8,56 *78,100", "6,0 6,100 | 74,0 6,56 | 30,40 78,100", "6,0 6,100 | 72,0 *10,58 44,52 *78,100"], 0, 1)
        m["L"] = Spec(["*6,0 *6,100 *70,100", "*8,0 6,60 *6,100 40,94 *74,100"], 0, 1)
        m["M"] = Spec(["*0,100 *6,0 *44,70 *84,0 *90,100", "0,0 0,100 | *0,0 *44,66 *88,0 *88,100", "*0,100 *6,0 *44,100 *84,0 *90,100"], 0, 1)
        m["N"] = Spec(["*6,100 *6,0 *74,100 *74,0", "6,0 6,100 | *6,0 *74,100 *74,0", "*6,100 *6,0 *74,100 *74,0 70,-4"], 0, 1)
        m["O"] = Spec(O_UP, 0, 1)
        m["Ö"] = Spec(O_UP, 0, 1, .TWO_DOTS_ABOVE)
        m["P"] = Spec(["*6,0 *6,100 | *6,0 48,2 70,20 64,44 *6,52", "*6,100 *6,0 50,2 72,22 64,44 *6,52", "*6,100 *6,0 50,2 72,20 60,44 *16,50"], 0, 1)
        m["Q"] = Spec(["@44,50,44,50,-90,-450 | 56,72 88,100", "@44,50,44,50,-90,-450 | 40,80 62,92 94,100"], 0, 1)
        m["R"] = Spec(["*6,100 *6,0 48,2 70,20 64,44 *6,52 *74,100", "6,0 6,100 | *6,0 48,2 70,20 64,44 *6,52 *74,100", "*6,100 *6,0 48,2 70,20 64,44 *6,52 40,64 *76,100"], 0, 1)
        m["S"] = Spec(S, 0, 1)
        m["Ş"] = Spec(S, 0, 1, .CEDILLA_BELOW)
        m["T"] = Spec(["0,0 80,0 | 40,0 40,100", "2,2 78,0 | 40,0 40,100", "0,6 40,0 82,4 | 42,0 40,100"], 0, 1)
        m["U"] = Spec(U, 0, 1)
        m["Ü"] = Spec(U, 0, 1, .TWO_DOTS_ABOVE)
        m["V"] = Spec(["*0,0 *42,100 *84,0", "0,0 16,56 36,96 44,100 52,90 70,44 84,0"], 0, 1)
        m["W"] = Spec(["*0,0 *24,100 *52,26 *80,100 *104,0", "0,0 4,72 16,100 32,98 46,72 *52,30 *58,72 72,100 88,98 98,70 102,0"], 0, 1)
        m["X"] = Spec(["0,0 86,100 | 86,0 0,100"], 0, 1)
        m["Y"] = Spec(["*0,0 *40,52 *80,0 | 40,52 40,100", "0,0 40,52 | *80,0 *40,52 *40,100", "0,0 40,52 | *80,0 *40,52 *40,100"], 0, 1)
        m["Z"] = Spec(["*6,0 *84,0 *6,100 *88,100", "*6,0 *84,0 *6,100 *88,100 | 22,50 66,50"], 0, 1)
        // simgeler
        m["+"] = Spec(["50,0 50,100 | 0,50 100,50", "48,2 52,98 | 2,52 98,48", "50,8 50,92 | 10,50 90,50"], 0.32, 0.88)
        m["−"] = Spec(["0,50 100,50", "0,52 100,48"], 0.32, 0.88)
        m["="] = Spec(["0,32 100,32 | 0,70 100,70", "4,30 96,32 | 0,70 100,68"], 0.32, 0.88)
        m["×"] = Spec(["4,4 86,96 | 86,4 4,96"], 0.4, 0.9)
        m["÷"] = Spec(["0,50 100,50"], 0.3, 0.9, .DOTS_ABOVE_BELOW)
        m["/"] = Spec(["68,0 2,100"], 0, 1)
        m["("] = Spec(["42,0 14,26 4,52 16,78 44,100", "50,0 20,20 6,50 18,82 50,100"], -0.05, 1.1)
        m[")"] = Spec(["4,0 32,24 42,50 30,78 4,100", "0,0 30,18 44,50 32,82 0,100"], -0.05, 1.1)
        m["<"] = Spec(["*78,0 *2,50 *78,100", "*84,6 *0,50 *84,94"], 0.32, 0.88)
        m[">"] = Spec(["*2,0 *78,50 *2,100", "*0,6 *84,50 *0,94"], 0.32, 0.88)
        m["%"] = Spec(["*78,0 *6,100 | @18,18,14,16,-90,-450 | @66,82,14,16,-90,-450", "*6,100 *78,0 | @16,20,12,18,-90,-450 | @66,80,12,18,-90,-450"], 0, 1)
        m["?"] = Spec(["8,26 20,4 46,0 68,12 70,34 50,56 *44,70 *44,100", "4,22 24,2 52,0 70,16 66,40 46,56 42,100", "2,30 12,6 40,0 66,8 72,30 56,50 42,62 *40,100"], 0, 0.72, .DOT_BELOW)
        m["!"] = Spec(STEM, 0, 0.72, .DOT_BELOW)
        m[","] = Spec(["52,0 50,50 22,100"], 0.86, 1.16)
        m["."] = Spec([], 0.9, 1, .ONLY_DOT)
        m[":"] = Spec([], 0.5, 1, .ONLY_COLON)
        return m
    }()

    /// Kotlin `Random` paylaşılan durum (aynı ifadede birden çok çağrı için sınıf).
    final class Rng {
        var r: KotlinRandom
        init(_ seed: Int) { r = KotlinRandom(seed) }
        func nextFloat() -> Float { r.nextFloat() }
        func nextDouble() -> Double { r.nextDouble() }
        func nextInt(_ n: Int) -> Int { r.nextInt(n) }
    }

    private struct P { let x: Float, y: Float; init(_ x: Float, _ y: Float) { self.x = x; self.y = y } }

    private struct CharInk { let strokes: [[P]]; let delayable: Set<Int>; let width: Float }

    private static let PIf = Float.pi
    @inline(__always) private static func fsin(_ v: Float) -> Float { Float(sin(Double(v))) }
    @inline(__always) private static func fcos(_ v: Float) -> Float { Float(cos(Double(v))) }

    private static func gauss(_ r: Rng) -> Float {
        let a = r.nextDouble(), b = r.nextDouble(), c = r.nextDouble()
        return Float(a + b + c - 1.5) * 1.41
    }

    private static func dot(_ r: Rng, _ cx: Float, _ cy: Float) -> [P] {
        if r.nextFloat() < 0.3 { return [P(cx, cy)] }
        let rad = 0.012 + r.nextFloat() * 0.022
        let n = 2 + r.nextInt(4)
        let a0 = r.nextFloat() * 6.28
        return (0..<n).map { k in P(cx + rad * fcos(a0 + Float(k) * 2.2), cy + rad * fsin(a0 + Float(k) * 2.2)) }
    }

    private static func charInk(_ ch: Character, _ r: Rng, _ xh: Float, _ warp: Float = 3.5) -> CharInk {
        guard let sp = specs[ch] else { fatalError("tanımsız: \(ch)") }
        let top = sp.top == XH ? xh : sp.top
        let bottom = sp.bottom
        var out: [[P]] = []
        var delay = Set<Int>()
        if sp.sketches.isEmpty {
            switch sp.marks {
            case .ONLY_DOT: out.append(dot(r, 0.04, 0.96 + gauss(r) * 0.01))
            case .ONLY_COLON:
                out.append(dot(r, 0.04, 0.52 + gauss(r) * 0.02))
                let x = 0.04 + gauss(r) * 0.01
                out.append(dot(r, x, 0.95))
            default: break
            }
            return CharInk(strokes: out, delayable: [], width: 0.08)
        }
        let src = sp.sketches[r.nextInt(sp.sketches.count)]
        let k = (bottom - top) / 100
        let kx = k * (0.85 + r.nextFloat() * 0.3)
        let ax = gauss(r) * warp, ay = gauss(r) * warp
        let fx = 1 + r.nextFloat() * 2, fy = 1 + r.nextFloat() * 2
        let px = r.nextFloat() * 6.28, py = r.nextFloat() * 6.28
        let strokes: [[P]] = Sketch.parse(src).map { ctrls in
            let warped = ctrls.map { c -> Ctrl in
                let dx = ax * fsin(c.y / 100 * fx * PIf + px) + gauss(r) * 1.4
                let dy = ay * fsin(c.x / 100 * fy * PIf + py) + gauss(r) * 1.4
                return Ctrl(c.x + dx, c.y + dy, c.corner)
            }
            let poly = Sketch.render(warped, 8)
            return (0..<poly.n).map { P(poly.x[$0] * kx, top + poly.y[$0] * k) }
        }
        out += strokes
        let flat = strokes.flatMap { $0 }
        let minX = flat.map(\.x).min()!, maxX = flat.map(\.x).max()!
        let minY = flat.map(\.y).min()!, maxY = flat.map(\.y).max()!
        let cx = (minX + maxX) / 2
        let w = maxX - minX
        func firstMin() -> P { var b = flat[0]; for p in flat where p.y < b.y { b = p }; return b }
        func firstMax() -> P { var b = flat[0]; for p in flat where p.y > b.y { b = p }; return b }
        switch sp.marks {
        case .DOT_ABOVE:
            delay.insert(out.count)
            let x = firstMin().x + gauss(r) * 0.03
            let y = minY - 0.17 - r.nextFloat() * 0.1
            out.append(dot(r, x, y))
        case .TWO_DOTS_ABOVE:
            let y = minY - 0.14 - r.nextFloat() * 0.08
            let half = w * (0.2 + r.nextFloat() * 0.1)
            if r.nextFloat() < 0.15 {
                delay.insert(out.count)
                let y2 = y + gauss(r) * 0.01
                out.append([P(cx - half, y), P(cx + half, y2)])
            } else {
                delay.insert(out.count); let y1 = y + gauss(r) * 0.015; out.append(dot(r, cx - half, y1))
                delay.insert(out.count); let y2 = y + gauss(r) * 0.015; out.append(dot(r, cx + half, y2))
            }
        case .BREVE_ABOVE:
            let hw = 0.1 + r.nextFloat() * 0.07
            let d = 0.05 + r.nextFloat() * 0.05
            let yb = minY - 0.1 - r.nextFloat() * 0.08
            delay.insert(out.count)
            out.append((0...8).map { i in let t = Float(i) / 8; return P(cx - hw + 2 * hw * t, yb - d + d * fsin(t * PIf)) })
        case .CEDILLA_BELOW:
            let bx = cx + gauss(r) * 0.02
            delay.insert(out.count)
            switch r.nextInt(3) {
            case 0: out.append([P(bx, maxY - 0.01), P(bx + 0.035, maxY + 0.06), P(bx + 0.01, maxY + 0.13), P(bx - 0.05, maxY + 0.15)])
            case 1: out.append([P(bx + 0.01, maxY + 0.01), P(bx - 0.04, maxY + 0.14)])
            default: out.append([P(bx, maxY + 0.02), P(bx + 0.02, maxY + 0.07), P(bx - 0.03, maxY + 0.14)])
            }
        case .DOT_BELOW:
            delay.insert(out.count)
            let x = firstMax().x + gauss(r) * 0.02
            let y = 0.95 + gauss(r) * 0.015
            out.append(dot(r, x, y))
        case .DOTS_ABOVE_BELOW:
            let ly = (minY + maxY) / 2
            let x1 = cx + gauss(r) * 0.02; let y1 = ly - 0.2 - r.nextFloat() * 0.05
            out.append(dot(r, x1, y1))
            let x2 = cx + gauss(r) * 0.02; let y2 = ly + 0.2 + r.nextFloat() * 0.05
            out.append(dot(r, x2, y2))
        default: break
        }
        let shifted = out.map { st in st.map { P($0.x - minX, $0.y) } }
        return CharInk(strokes: shifted, delayable: delay, width: w)
    }

    struct Options {
        var slantDeg: Float = 12
        var rotDeg: Float = 8
        var minH: Float = 20
        var maxH: Float = 160
        var delayMarks: Float = 0.35
        var warp: Float = 3.5
    }

    /// [text] için yapay mürekkep (boşluk = sözcük arası).
    static func sample(_ text: String, _ r: Rng, _ o: Options = Options()) -> [InkStroke] {
        let xh = 0.4 + r.nextFloat() * 0.12
        var all: [[P]] = []
        var delayed: [[P]] = []
        var x: Float = 0
        for ch in text {
            if ch == " " { x += 0.75 + r.nextFloat() * 0.35; continue }
            let ci = charInk(ch, r, xh, o.warp)
            let sc = 0.92 + r.nextFloat() * 0.16
            let dy = gauss(r) * 0.025
            let rot = gauss(r) * 2.5 * PIf / 180
            let cw = ci.width
            let cr = fcos(rot), sr = fsin(rot)
            let placed = ci.strokes.map { st in
                st.map { p -> P in
                    let lx = p.x - cw / 2, ly = p.y - 1
                    let rx = lx * cr - ly * sr, ry = lx * sr + ly * cr
                    return P(x + cw / 2 + rx * sc, 1 + ry * sc + dy)
                }
            }
            var idx = Array(placed.indices)
            if idx.count > 1 && r.nextFloat() < 0.3 {
                for i in stride(from: idx.count - 1, through: 1, by: -1) { let j = r.nextInt(i + 1); idx.swapAt(i, j) }
            }
            for i in idx {
                if ci.delayable.contains(i) && r.nextFloat() < o.delayMarks { delayed.append(placed[i]) } else { all.append(placed[i]) }
            }
            x += cw * sc + 0.13 + r.nextFloat() * 0.25
        }
        all += delayed
        let slant = Float(tan(Double((r.nextFloat() * 2 - 1) * o.slantDeg * PIf / 180)))
        let rot = (r.nextFloat() * 2 - 1) * o.rotDeg * PIf / 180
        let h = o.minH + r.nextFloat() * (o.maxH - o.minH)
        let sx = 0.9 + r.nextFloat() * 0.2
        let ox = r.nextFloat() * 500, oy = 100 + r.nextFloat() * 500
        let cxAll = x / 2
        let cr = fcos(rot), sr = fsin(rot)
        let lines = all.map { st in
            st.map { p -> P in
                let ax = (p.x - cxAll) * sx + slant * (1 - p.y)
                let ay = p.y - 0.7
                return P(ox + h * (ax * cr - ay * sr), oy + h * (ax * sr + ay * cr))
            }
        }
        let speed = h * (3 + r.nextFloat() * 5) / 1000
        let dt = 8 + r.nextFloat() * 8
        let noise = h * (0.002 + r.nextFloat() * 0.006)
        var t: Int64 = 0
        return lines.map { st0 in
            let st = r.nextFloat() < 0.3 ? Array(st0.reversed()) : st0
            let pts = timeSample(st, speed, dt, r, noise, t)
            t = pts[pts.count - 1].t + 150 + Int64(r.nextInt(200))
            return InkStroke(pts)
        }
    }

    private static func timeSample(_ st: [P], _ vmax: Float, _ dt: Float, _ r: Rng, _ noise: Float, _ t0: Int64) -> [InkPoint] {
        if st.count == 1 { let gx = gauss(r) * noise; let gy = gauss(r) * noise; return [InkPoint(st[0].x + gx, st[0].y + gy, t0)] }
        var dense: [P] = []
        for i in 0..<(st.count - 1) {
            let a = st[i], b = st[i + 1]
            let d = kHypot(b.x - a.x, b.y - a.y)
            let m = max(1, (d / (vmax * dt * 0.25)).toInt())
            for k in 0..<m { dense.append(P(a.x + (b.x - a.x) * Float(k) / Float(m), a.y + (b.y - a.y) * Float(k) / Float(m))) }
        }
        dense.append(st[st.count - 1])
        var tot = 0.0
        for i in 1..<dense.count { tot += Double(kHypot(dense[i].x - dense[i - 1].x, dense[i].y - dense[i - 1].y)) }
        let total = Float(tot)
        var out: [InkPoint] = []
        do { let gx = gauss(r) * noise; let gy = gauss(r) * noise; out.append(InkPoint(dense[0].x + gx, dense[0].y + gy, t0)) }
        var time: Float = 0
        var next = dt
        var s: Float = 0
        for i in 1..<dense.count {
            let a = dense[i - 1], b = dense[i]
            let d = kHypot(b.x - a.x, b.y - a.y)
            s += d
            var turn: Float = 0
            if i + 1 < dense.count {
                let c = dense[i + 1]
                let a1 = kAtan2(b.y - a.y, b.x - a.x), a2 = kAtan2(c.y - b.y, c.x - b.x)
                var da = abs(a2 - a1)
                if Double(da) > Double.pi { da = Float(2 * Double.pi - Double(da)) }
                turn = da
            }
            let endF = min(1, 0.25 + min(s, total - s) / (0.15 * max(total, 1e-3)))
            let v = max(vmax * 0.15, vmax * endF / (1 + 6 * turn))
            time += d / v
            if time >= next {
                let gx = gauss(r) * noise; let gy = gauss(r) * noise
                out.append(InkPoint(b.x + gx, b.y + gy, t0 + Int64(time.toInt())))
                next += dt
            }
        }
        let l = dense[dense.count - 1]
        let last = out[out.count - 1]
        if last.x != l.x || last.y != l.y { let gx = gauss(r) * noise; let gy = gauss(r) * noise; out.append(InkPoint(l.x + gx, l.y + gy, t0 + Int64(time.toInt()) + 1)) }
        return out
    }
}
