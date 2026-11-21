import Foundation

// v56 aşama 9 (2D-3) SVG, PSD ve DXF yazıcıları (Android `data/export/VectorExport.kt` ile aynı çıktı).

enum ExportNum {
    static func f(_ v: Float) -> String { f(Double(v)) }
    static func f(_ v: Double) -> String {
        let n = Int64((v * 100.0 + 0.5).rounded(.down))
        let neg = n < 0
        let a = abs(n)
        let ip = a / 100, fp = Int(a % 100)
        let body: String
        if fp == 0 { body = "\(ip)" } else if fp % 10 == 0 { body = "\(ip).\(fp / 10)" } else if fp < 10 { body = "\(ip).0\(fp)" } else { body = "\(ip).\(fp)" }
        return neg && (ip != 0 || fp != 0) ? "-" + body : body
    }
}

struct ExportDoc {
    var width: Int
    var height: Int
    var paperHex: String
    var layers: [Layer]
    var strokes: [Stroke]
    var title: String = ""
}

enum DocFormat: String, CaseIterable {
    case SVG, PSD, DXF
    var ext: String { rawValue.lowercased() }
    var mime: String {
        switch self { case .SVG: return "image/svg+xml"; case .PSD: return "image/vnd.adobe.photoshop"; case .DXF: return "application/dxf" }
    }
}

enum VectorKinds {
    private static let lineBrushes: Set<BrushType> = [.PENCIL, .INK_PEN, .FINELINER, .MARKER, .HIGHLIGHTER, .HARD_ROUND,
        .NOTE_PENCIL, .NOTE_BALLPOINT, .NOTE_FOUNTAIN, .NOTE_GEL, .NOTE_FINELINER, .NOTE_FELT, .NOTE_HIGHLIGHTER, .CALLIGRAPHY]

    static func isLine(_ s: Stroke) -> Bool { lineBrushes.contains(s.brushType) && s.params?.grain == nil && s.params?.stampAssetId == nil && !s.points.isEmpty }

    static func isVector(_ s: Stroke) -> Bool {
        switch s.brushType {
        case .SHAPE:
            guard let sh = s.shape else { return false }
            let v = s.style2
            return !sh.subpaths.contains { $0.label != nil } && sh.style.fillGradient == nil && sh.style.tone == nil &&
                (v == nil || (v!.freeGradient == nil && v!.fills.isEmpty && v!.strokes.isEmpty && v!.opacityMask == nil && v!.patternId == nil))
        case .LASSO_FILL: return s.points.count >= 3
        case .TEXT: return s.text != nil && s.text!.box == nil && s.text!.path == nil && s.points.count >= 4
        default: return isLine(s)
        }
    }

    static func subpaths(_ s: Stroke) -> [([StrokePoint], Bool)] {
        guard let spec = s.shape else { return [(s.points, s.brushType == .LASSO_FILL)] }
        var out: [([StrokePoint], Bool)] = []
        var start = 0
        for sub in spec.subpaths {
            let end = min(s.points.count, start + sub.count)
            if end > start { out.append((Array(s.points[start..<end]), sub.closed)) }
            start = end
        }
        if out.isEmpty && !s.points.isEmpty { out.append((s.points, false)) }
        return out
    }
}

enum SvgWriter {
    private static func esc(_ t: String) -> String {
        t.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;").replacingOccurrences(of: "\"", with: "&quot;")
    }
    private static func strip(_ hex: String) -> String { hex.hasPrefix("#") ? String(hex.dropFirst()) : hex }
    private static func color(_ hex: String) -> String {
        let h = strip(hex)
        return "#" + (h.count == 8 ? String(h.dropFirst(2)) : h).uppercased()
    }
    private static func alphaOf(_ hex: String) -> Float {
        let h = strip(hex)
        return h.count == 8 ? Float(Int(h.prefix(2), radix: 16) ?? 255) / 255 : 1
    }

    static func blendCss(_ b: LayerBlend) -> String {
        switch b {
        case .NORMAL: return "normal"; case .MULTIPLY: return "multiply"; case .SCREEN: return "screen"; case .OVERLAY: return "overlay"
        case .DARKEN: return "darken"; case .LIGHTEN: return "lighten"; case .COLOR_DODGE: return "color-dodge"; case .COLOR_BURN: return "color-burn"
        case .HARD_LIGHT: return "hard-light"; case .SOFT_LIGHT: return "soft-light"; case .DIFFERENCE: return "difference"
        case .EXCLUSION: return "exclusion"; case .HUE: return "hue"; case .SATURATION: return "saturation"; case .COLOR: return "color"
        case .LUMINOSITY: return "luminosity"; case .ADD: return "plus-lighter"
        }
    }

    private static func pathData(_ pts: [StrokePoint], _ closed: Bool) -> String {
        var sb = ""
        for (i, p) in pts.enumerated() { sb += (i == 0 ? "M" : " L") + ExportNum.f(p.x) + " " + ExportNum.f(p.y) }
        if pts.count == 1 { sb += " L" + ExportNum.f(pts[0].x) + " " + ExportNum.f(pts[0].y) }
        if closed { sb += " Z" }
        return sb
    }

    /// SVG metni; [raster] vektöre çevrilemeyen ardışık çizimleri tuval boyutunda PNG'ye çevirir (nil: atlanır).
    static func write(_ doc: ExportDoc, raster: ([Stroke]) -> Data?) -> String {
        let w = doc.width, h = doc.height
        var sb = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        sb += "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
        sb += "xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\" width=\"\(w)\" height=\"\(h)\" viewBox=\"0 0 \(w) \(h)\">\n"
        if !doc.title.isEmpty { sb += "<title>" + esc(doc.title) + "</title>\n" }
        var defs = ""
        var body = "<rect id=\"paper\" width=\"\(w)\" height=\"\(h)\" fill=\"\(color(doc.paperHex))\"/>\n"
        var defId = 0
        let layers = doc.layers.isEmpty ? [Layer(id: Layer.BASE, name: "Katman 1")] : doc.layers
        for (li, layer) in layers.enumerated() {
            guard layer.visible else { continue }
            let own = doc.strokes.filter { Layers.of($0, layers) == layer.id && !$0.hidden }
            if own.isEmpty { continue }
            body += "<g id=\"layer\(li + 1)\" inkscape:groupmode=\"layer\" inkscape:label=\"\(esc(layer.name))\""
            if layer.opacity < 1 { body += " opacity=\"\(ExportNum.f(layer.opacity))\"" }
            if layer.blend != .NORMAL { body += " style=\"mix-blend-mode:\(blendCss(layer.blend))\"" }
            body += ">\n"
            let erased = own.contains { $0.brushType == .ERASER }
            var run: [Stroke] = []
            func flush() {
                if run.isEmpty { return }
                if let png = raster(run) {
                    body += "<image x=\"0\" y=\"0\" width=\"\(w)\" height=\"\(h)\" xlink:href=\"data:image/png;base64," + png.base64EncodedString() + "\"/>\n"
                }
                run.removeAll()
            }
            for s in own {
                let vector = VectorKinds.isVector(s) && !(erased && s.brushType != .SHAPE && s.brushType != .TEXT)
                if !vector { run.append(s); continue }
                flush()
                var open = "", close = ""
                let clipPts: [StrokePoint]? = (s.clip.map { $0.count >= 3 ? $0 : nil } ?? nil) ??
                    (s.mask.flatMap { $0.points.count >= 3 && !$0.inverted ? $0.points : nil })
                if let clipPts {
                    defId += 1
                    defs += "<clipPath id=\"c\(defId)\"><path d=\"\(pathData(clipPts, true))\"/></clipPath>\n"
                    open += "<g clip-path=\"url(#c\(defId))\">"; close = "</g>"
                }
                let blend = s.style2?.blend ?? .NORMAL
                if blend != .NORMAL { open += "<g style=\"mix-blend-mode:\(blendCss(blend))\">"; close += "</g>" }
                body += open
                defs += element(&body, s) { defId += 1; return defId }
                body += close + "\n"
            }
            flush()
            body += "</g>\n"
        }
        if !defs.isEmpty { sb += "<defs>\n" + defs + "</defs>\n" }
        sb += body + "</svg>\n"
        return sb
    }

    private static func element(_ out: inout String, _ s: Stroke, _ nextId: () -> Int) -> String {
        let op = min(max(s.opacity, 0), 1)
        switch s.brushType {
        case .SHAPE:
            let st = s.shape!.style
            let v = s.style2
            var defs = "", attrs = ""
            attrs += " fill=\"\(st.fillColorHex.map(color) ?? "none")\""
            if st.fillColorHex != nil && st.fillOpacity < 1 { attrs += " fill-opacity=\"\(ExportNum.f(st.fillOpacity))\"" }
            if st.strokeWidth > 0 {
                attrs += " stroke=\"\(color(st.strokeColorHex))\" stroke-width=\"\(ExportNum.f(st.strokeWidth))\""
                let cap: String = { switch v?.cap { case .BUTT?: return "butt"; case .SQUARE?: return "square"; default: return "round" } }()
                let join: String = { switch v?.join { case .MITER?: return "miter"; case .BEVEL?: return "bevel"; default: return "round" } }()
                attrs += " stroke-linecap=\"\(cap)\" stroke-linejoin=\"\(join)\""
                if let v, v.join == .MITER { attrs += " stroke-miterlimit=\"\(ExportNum.f(v.miterLimit))\"" }
                if let d = v?.dash, d.count >= 2 { attrs += " stroke-dasharray=\"\(d.map { ExportNum.f($0) }.joined(separator: " "))\"" }
                if let v, v.arrowStart != .NONE || v.arrowEnd != .NONE {
                    let id = nextId()
                    defs += "<marker id=\"m\(id)\" viewBox=\"0 0 10 10\" refX=\"10\" refY=\"5\" markerWidth=\"4\" markerHeight=\"4\" orient=\"auto-start-reverse\">"
                    defs += "<path d=\"M0 0 L10 5 L0 10 Z\" fill=\"\(color(st.strokeColorHex))\"/></marker>\n"
                    if v.arrowStart != .NONE { attrs += " marker-start=\"url(#m\(id))\"" }
                    if v.arrowEnd != .NONE { attrs += " marker-end=\"url(#m\(id))\"" }
                }
            } else { attrs += " stroke=\"none\"" }
            if op < 1 { attrs += " opacity=\"\(ExportNum.f(op))\"" }
            let d = VectorKinds.subpaths(s).map { pathData($0.0, $0.1) }.joined(separator: " ")
            out += "<path d=\"\(d)\"\(attrs)/>"
            return defs
        case .LASSO_FILL:
            out += "<path d=\"\(pathData(s.points, true))\" fill=\"\(color(s.colorHex))\""
            if op < 1 { out += " opacity=\"\(ExportNum.f(op))\"" }
            out += "/>"
        case .TEXT:
            let t = s.text!, q = s.points
            let angle = atan2(Double(q[1].y - q[0].y), Double(q[1].x - q[0].x)) * 180 / .pi
            let size = s.sizePx
            out += "<text x=\"\(ExportNum.f(q[0].x))\" y=\"\(ExportNum.f(q[0].y))\" font-size=\"\(ExportNum.f(size))\" fill=\"\(color(s.colorHex))\""
            out += " font-family=\"\(esc(t.font.family))\""
            if t.bold { out += " font-weight=\"bold\"" }
            if t.italic { out += " font-style=\"italic\"" }
            if abs(angle) > 0.01 { out += " transform=\"rotate(\(ExportNum.f(angle)) \(ExportNum.f(q[0].x)) \(ExportNum.f(q[0].y)))\"" }
            if op < 1 { out += " opacity=\"\(ExportNum.f(op))\"" }
            out += ">"
            for (i, line) in t.content.components(separatedBy: "\n").enumerated() {
                out += "<tspan x=\"\(ExportNum.f(q[0].x))\" dy=\"\(ExportNum.f(i == 0 ? size * 0.9 : size * 1.2))\">" + esc(line) + "</tspan>"
            }
            out += "</text>"
        default:
            let hl = s.brushType == .HIGHLIGHTER || s.brushType == .NOTE_HIGHLIGHTER
            let width = hl ? s.sizePx * 1.6 : s.sizePx
            let alpha = op * alphaOf(s.colorHex) * (hl ? 0.45 : 1)
            out += "<path d=\"\(pathData(s.points, false))\" fill=\"none\" stroke=\"\(color(s.colorHex))\" stroke-width=\"\(ExportNum.f(width))\" stroke-linecap=\"round\" stroke-linejoin=\"round\""
            if alpha < 1 { out += " stroke-opacity=\"\(ExportNum.f(alpha))\"" }
            out += "/>"
        }
        return ""
    }
}

/// ASCII DXF (R12): katman tablosu, POLYLINE ve TEXT; birim mm, Y ters. Resimler ve boya izleri atlanır.
enum DxfWriter {
    static let mmPerPx = 25.4 / 96.0

    struct Result { var text: String; var skipped: Int }

    private static func aci(_ hex: String) -> Int {
        var h = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        if h.count == 8 { h = String(h.dropFirst(2)) }
        guard let v = Int(h, radix: 16) else { return 7 }
        let r = (v >> 16) & 0xFF, g = (v >> 8) & 0xFF, b = v & 0xFF
        let palette: [(Int, [Int])] = [(1, [255, 0, 0]), (2, [255, 255, 0]), (3, [0, 255, 0]), (4, [0, 255, 255]), (5, [0, 0, 255]),
                                       (6, [255, 0, 255]), (7, [0, 0, 0]), (8, [128, 128, 128]), (9, [192, 192, 192])]
        var best = palette[0], bd = Int.max
        for p in palette {
            let c = p.1
            let d = (c[0] - r) * (c[0] - r) + (c[1] - g) * (c[1] - g) + (c[2] - b) * (c[2] - b)
            if d < bd { bd = d; best = p }
        }
        return best.0
    }

    static func layerName(_ name: String, _ index: Int) -> String {
        let map: [Character: Character] = ["ç": "c", "Ç": "C", "ğ": "g", "Ğ": "G", "ı": "i", "İ": "I", "ö": "o", "Ö": "O", "ş": "s", "Ş": "S", "ü": "u", "Ü": "U"]
        let ascii = String(name.map { ch -> Character in
            let c = map[ch] ?? ch
            return (c.isASCII && (c.isLetter || c.isNumber)) || c == "-" || c == "_" ? c : "_"
        }).uppercased()
        return (ascii.trimmingCharacters(in: .whitespaces).isEmpty ? "LAYER" : ascii) + "_\(index + 1)"
    }

    static func write(_ doc: ExportDoc) -> Result {
        let k = mmPerPx
        let hmm = Double(doc.height) * k
        func x(_ v: Float) -> String { ExportNum.f(Double(v) * k) }
        func y(_ v: Float) -> String { ExportNum.f(hmm - Double(v) * k) }
        var sb = ""
        func g(_ code: Int, _ value: String) { sb += "\(code)\n\(value)\n" }
        let layers = doc.layers.isEmpty ? [Layer(id: Layer.BASE, name: "Katman 1")] : doc.layers
        var names: [String: String] = [:]
        for (i, l) in layers.enumerated() { names[l.id] = layerName(l.name, i) }
        g(0, "SECTION"); g(2, "HEADER")
        g(9, "$ACADVER"); g(1, "AC1009")
        g(9, "$INSUNITS"); g(70, "4")
        g(9, "$EXTMIN"); g(10, "0"); g(20, "0")
        g(9, "$EXTMAX"); g(10, x(Float(doc.width))); g(20, ExportNum.f(hmm))
        g(0, "ENDSEC")
        g(0, "SECTION"); g(2, "TABLES")
        g(0, "TABLE"); g(2, "LAYER"); g(70, "\(layers.count)")
        for l in layers {
            let first = doc.strokes.first { Layers.of($0, layers) == l.id }
            let c = aci(first?.colorHex ?? "#000000")
            g(0, "LAYER"); g(2, names[l.id]!); g(70, "0"); g(62, "\(l.visible ? c : -c)"); g(6, "CONTINUOUS")
        }
        g(0, "ENDTAB"); g(0, "ENDSEC")
        g(0, "SECTION"); g(2, "ENTITIES")
        var skipped = 0
        for s in doc.strokes {
            if s.hidden || s.brushType == .ERASER { continue }
            let layer = names[Layers.of(s, layers)] ?? "0"
            if !VectorKinds.isVector(s) { skipped += 1; continue }
            let color = "\(aci(s.brushType == .SHAPE ? s.shape!.style.strokeColorHex : s.colorHex))"
            if s.brushType == .TEXT {
                let q = s.points
                let angle = atan2(-Double(q[1].y - q[0].y), Double(q[1].x - q[0].x)) * 180 / .pi
                for (i, line) in s.text!.content.components(separatedBy: "\n").enumerated() {
                    let dy = s.sizePx * (0.9 + 1.2 * Float(i))
                    g(0, "TEXT"); g(8, layer); g(62, color)
                    g(10, x(q[0].x)); g(20, y(q[0].y + dy)); g(30, "0")
                    g(40, ExportNum.f(Double(s.sizePx) * k * 0.75)); g(1, line)
                    if abs(angle) > 0.01 { g(50, ExportNum.f(angle)) }
                }
                continue
            }
            for (pts, closed) in VectorKinds.subpaths(s) where pts.count >= 2 {
                g(0, "POLYLINE"); g(8, layer); g(62, color); g(66, "1"); g(10, "0"); g(20, "0"); g(30, "0"); g(70, closed ? "1" : "0")
                for p in pts { g(0, "VERTEX"); g(8, layer); g(10, x(p.x)); g(20, y(p.y)); g(30, "0") }
                g(0, "SEQEND"); g(8, layer)
            }
        }
        g(0, "ENDSEC"); g(0, "EOF")
        return Result(text: sb, skipped: skipped)
    }
}

/// Katmanlı PSD (sürüm 1, RGB, 8 bit, RLE); pikseller ARGB.
enum PsdWriter {
    struct PsdLayer {
        var name: String
        var pixels: [UInt32]
        var visible = true
        var opacity: Float = 1
        var blend: LayerBlend = .NORMAL
        var clipping = false
        var alphaLock = false
    }

    static func blendKey(_ b: LayerBlend) -> String {
        switch b {
        case .NORMAL: return "norm"; case .MULTIPLY: return "mul "; case .SCREEN: return "scrn"; case .OVERLAY: return "over"
        case .DARKEN: return "dark"; case .LIGHTEN: return "lite"; case .COLOR_DODGE: return "div "; case .COLOR_BURN: return "idiv"
        case .HARD_LIGHT: return "hLit"; case .SOFT_LIGHT: return "sLit"; case .DIFFERENCE: return "diff"; case .EXCLUSION: return "smud"
        case .HUE: return "hue "; case .SATURATION: return "sat "; case .COLOR: return "colr"; case .LUMINOSITY: return "lum "
        case .ADD: return "lddg"
        }
    }

    private struct Out {
        var bytes: [UInt8] = []
        mutating func u8(_ v: Int) { bytes.append(UInt8(truncatingIfNeeded: v)) }
        mutating func u16(_ v: Int) { u8(v >> 8); u8(v) }
        mutating func u32(_ v: Int) { u16((v >> 16) & 0xFFFF); u16(v & 0xFFFF) }
        mutating func ascii(_ s: String) { for c in s.unicodeScalars { u8(Int(c.value)) } }
        mutating func raw(_ b: [UInt8]) { bytes.append(contentsOf: b) }
        var size: Int { bytes.count }
    }

    static func packBits(_ row: [UInt8]) -> [UInt8] {
        var out: [UInt8] = []
        var i = 0
        let n = row.count
        while i < n {
            var run = 1
            while i + run < n && run < 128 && row[i + run] == row[i] { run += 1 }
            if run >= 2 { out.append(UInt8(257 - run)); out.append(row[i]); i += run; continue }
            var lit = 1
            while i + lit < n && lit < 128 && !(i + lit + 1 < n && row[i + lit] == row[i + lit + 1]) { lit += 1 }
            out.append(UInt8(lit - 1)); out.append(contentsOf: row[i..<(i + lit)]); i += lit
        }
        return out
    }

    private static func channelRle(_ px: [UInt32], _ w: Int, _ h: Int, _ shift: UInt32) -> ([UInt8], [UInt8]) {
        var counts: [UInt8] = [], data: [UInt8] = []
        var row = [UInt8](repeating: 0, count: w)
        for yy in 0..<h {
            for xx in 0..<w { row[xx] = UInt8(truncatingIfNeeded: px[yy * w + xx] >> shift) }
            let packed = packBits(row)
            counts.append(UInt8(truncatingIfNeeded: packed.count >> 8)); counts.append(UInt8(truncatingIfNeeded: packed.count))
            data.append(contentsOf: packed)
        }
        return (counts, data)
    }

    private static func asciiName(_ s: String) -> String {
        String(String(s.map { c -> Character in
            if let v = c.unicodeScalars.first?.value, c.unicodeScalars.count == 1, v >= 32 && v <= 126 { return c }
            return "_"
        }).prefix(255))
    }

    static func write(_ width: Int, _ height: Int, _ layers: [PsdLayer], _ composite: [UInt32]) -> Data {
        var o = Out()
        o.ascii("8BPS"); o.u16(1); for _ in 0..<6 { o.u8(0) }; o.u16(3); o.u32(height); o.u32(width); o.u16(8); o.u16(3)
        o.u32(0); o.u32(0)
        var info = Out()
        info.u16(layers.count)
        var channelData = Out()
        for l in layers {
            info.u32(0); info.u32(0); info.u32(height); info.u32(width)
            info.u16(4)
            let chans: [(Int, ([UInt8], [UInt8]))] = [(-1, 24), (0, 16), (1, 8), (2, 0)].map { ($0.0, channelRle(l.pixels, width, height, UInt32($0.1))) }
            for (id, c) in chans { info.u16(id & 0xFFFF); info.u32(2 + c.0.count + c.1.count) }
            info.ascii("8BIM"); info.ascii(blendKey(l.blend))
            info.u8(Int(min(max(l.opacity, 0), 1) * 255 + 0.5))
            info.u8(l.clipping ? 1 : 0)
            info.u8((l.alphaLock ? 1 : 0) | (l.visible ? 0 : 2))
            info.u8(0)
            var extra = Out()
            extra.u32(0); extra.u32(0)
            let name = asciiName(l.name)
            let nameLen = 1 + name.count
            extra.u8(name.count); extra.ascii(name); for _ in 0..<((4 - nameLen % 4) % 4) { extra.u8(0) }
            var uni = Out()
            let utf16 = Array(l.name.utf16)
            uni.u32(utf16.count); for c in utf16 { uni.u16(Int(c)) }
            if uni.size % 2 != 0 { uni.u8(0) }
            extra.ascii("8BIM"); extra.ascii("luni"); extra.u32(uni.size); extra.raw(uni.bytes)
            info.u32(extra.size); info.raw(extra.bytes)
            for (_, c) in chans { channelData.u16(1); channelData.raw(c.0); channelData.raw(c.1) }
        }
        info.raw(channelData.bytes)
        if info.size % 2 != 0 { info.u8(0) }
        o.u32(4 + info.size + 4)
        o.u32(info.size); o.raw(info.bytes)
        o.u32(0)
        o.u16(1)
        let merged = [16, 8, 0].map { channelRle(composite, width, height, UInt32($0)) }
        for m in merged { o.raw(m.0) }
        for m in merged { o.raw(m.1) }
        return Data(o.bytes)
    }

    struct Info { var width: Int; var height: Int; var channels: Int; var layers: [(String, String, Int)]; var flags: [Int]; var clipping: [Int] }

    static func readInfo(_ data: Data) -> Info? {
        let b = [UInt8](data)
        var p = 0
        func u8() -> Int { let v = Int(b[p]); p += 1; return v }
        func u16() -> Int { (u8() << 8) | u8() }
        func u32() -> Int { (u16() << 16) | u16() }
        func ascii(_ n: Int) -> String { String((0..<n).map { _ in Character(UnicodeScalar(UInt8(u8()))) }) }
        func skip() { let n = u32(); p += n }
        guard b.count > 26, ascii(4) == "8BPS" else { return nil }
        _ = u16(); p += 6
        let ch = u16(), h = u32(), w = u32(); _ = u16(); _ = u16()
        skip(); skip()
        _ = u32(); _ = u32()
        let count = abs(Int(Int16(truncatingIfNeeded: u16())))
        var layers: [(String, String, Int)] = [], flags: [Int] = [], clip: [Int] = []
        for _ in 0..<count {
            p += 16
            let nc = u16(); p += nc * 6
            _ = ascii(4); let key = ascii(4); let op = u8(); clip.append(u8()); flags.append(u8()); _ = u8()
            let extraLen = u32(); let end = p + extraLen
            skip(); skip()
            let nl = u8(); let name = ascii(nl)
            p = end
            layers.append((name, key, op))
        }
        return Info(width: w, height: h, channels: ch, layers: layers, flags: flags, clipping: clip)
    }
}
