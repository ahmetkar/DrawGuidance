import Compression
import Foundation

/// Tuval içeriğinin kompakt, kayıpsız ikili biçimi (Android `ContentCodec` ile bayt düzeyinde aynı gövde;
/// DEFLATE çıktısı sıkıştırıcıya göre farklı olabilir ama iki taraf da birbirininkini okur).
///
/// Başlık: "DG" + sürüm. Başlıksız veri eski JSON biçimi olarak okunur.
enum ContentCodec {
    private static let MAGIC_0: UInt8 = 0x44 // D
    private static let MAGIC_1: UInt8 = 0x47 // G
    private static let VERSION: UInt8 = 1
    private static let VERSION_PAGES: UInt8 = 2
    private static let VERSION_V3: UInt8 = 3
    private static let VERSION_V4: UInt8 = 4
    private static let VERSION_V5: UInt8 = 5
    private static let VERSION_V6: UInt8 = 6
    private static let VERSION_V7: UInt8 = 7
    private static let VERSION_V8: UInt8 = 8
    private static let VERSION_V9: UInt8 = 9
    private static let EXT2_TEXTBOX = 1
    private static let EXT2_MODEL = 2
    private static let EXT2_HIDDEN = 4
    private static let EXT2_TEXTPATH = 8
    private static let EXT_CLIP = 1
    private static let EXT_PARAGRAPH = 2
    private static let EXT_LAYER = 4
    private static let EXT_FORMULA = 8
    private static let EXT_DYNAMICS = 16
    private static let EXT_ORIENT = 32
    private static let EXT_PARAMS = 64
    private static let EXT_SPACE = 128
    private static let QUANT: Float = 100
    private static let SPACE_Q: Float = 16

    private static let F_ID_UUID = 1
    private static let F_GROUP = 2
    private static let F_GROUP_UUID = 4
    private static let F_ASSET = 8
    private static let F_ASSET_UUID = 16
    private static let F_QUANTIZED = 32
    private static let F_COLOR_RGB = 64
    private static let F_DYNAMICS = 128
    private static let TEXT_EXTRA = 32

    // Enum sırası değişse bile kayıtlı veri bozulmasın diye sabit kodlar (Android ile aynı).
    private static let typeCodes: [BrushType: Int] = [
        .PENCIL: 0, .MARKER: 1, .HIGHLIGHTER: 2, .PASTEL: 3, .ERASER: 4, .IMAGE: 5,
        .INK_PEN: 6, .FINELINER: 7, .CALLIGRAPHY: 8, .CHARCOAL: 9,
        .FLAT_BRUSH: 10, .HARD_ROUND: 11, .SOFT_ROUND: 12, .AIRBRUSH: 13,
        .STAMP_STAR: 14, .STAMP_HEART: 15, .STAMP_LEAF: 16,
        .PATTERN_DOTS: 17, .PATTERN_ZIGZAG: 18, .PATTERN_HATCH: 19,
        .TEXTURE_SPONGE: 20, .TEXTURE_CANVAS: 21, .TEXTURE_SPLATTER: 22,
        .HALFTONE_DOTS: 23, .HALFTONE_LINES: 24,
        .NOTE_PENCIL: 25, .NOTE_BALLPOINT: 26, .NOTE_FOUNTAIN: 27, .NOTE_GEL: 28,
        .NOTE_FINELINER: 29, .NOTE_FELT: 30, .NOTE_HIGHLIGHTER: 31,
        .TEXT: 32, .SHAPE: 33, .DIAGRAM: 34, .FORMULA: 35, .DOT_PEN: 36, .BRUSH_3D: 37,
        .INK_TRAIL: 38, .DRIP_TRAIL: 39, .PAINT_TRAIL: 40,
        .PATTERN_CARPET: 41, .PATTERN_WALLPAPER: 42, .PATTERN_WOODPAINT: 43,
        .TEXTURE_SKIN: 44, .TEXTURE_FUR: 45, .TEXTURE_PLANT: 46,
        .TEXTURE_WOOD: 47, .TEXTURE_IRON: 48, .TEXTURE_METAL: 49,
    ]
    private static let fontCodes: [TextFont: Int] = [
        .SANS: 0, .SERIF: 1, .MONO: 2, .CONDENSED: 3, .HANDWRITING: 4, .CASUAL: 5,
        .LIGHT: 6, .MEDIUM: 7, .BLACK: 8, .SMALLCAPS: 9, .SERIF_MONO: 10,
        .MONTSERRAT: 11, .POPPINS: 12, .NUNITO: 13, .RALEWAY: 14, .QUICKSAND: 15,
        .COMFORTAA: 16, .OSWALD: 17, .MERRIWEATHER: 18, .LORA: 19, .PLAYFAIR: 20,
        .ROBOTO_SLAB: 21, .CAVEAT: 22, .KALAM: 23, .PATRICK_HAND: 24,
        .DANCING_SCRIPT: 25, .PACIFICO: 26, .GREAT_VIBES: 27,
        .AMATIC: 29, .BEBAS: 30, .LOBSTER: 31, .ABRIL: 32, .RIGHTEOUS: 33,
        .PIXEL: 34, .JETBRAINS_MONO: 35, .SOURCE_CODE: 36, .TYPEWRITER: 37,
        .COMIC_RELIEF: 38, .COMIC_RELIEF_BOLD: 39, .BANGERS: 40, .LUCKIEST_GUY: 41,
        .SHANTELL: 42, .BOOGALOO: 43, .GRANDSTANDER: 44, .BUNGEE: 45,
        .BUNGEE_SHADE: 46, .RUBIK_BUBBLES: 47, .MARGARINE: 48, .MANSALVA: 49,
    ]
    private static let paragraphCodes: [TextParagraph: Int] = [
        .NORMAL: 0, .H1: 1, .H2: 2, .H3: 3, .BULLET: 4, .NUMBERED: 5, .CHECKLIST: 6,
    ]
    private static let codeTypes = Dictionary(uniqueKeysWithValues: typeCodes.map { ($1, $0) })
    private static let codeFonts = Dictionary(uniqueKeysWithValues: fontCodes.map { ($1, $0) })
    private static let codeParagraphs = Dictionary(uniqueKeysWithValues: paragraphCodes.map { ($1, $0) })

    static let EMPTY: Data = encode([])

    struct Document: Equatable {
        var pages: [[Stroke]]
        var layers: [Layer]
    }

    struct CodecError: Error, CustomStringConvertible {
        let description: String
    }

    // MARK: JSON (Android'deki specWriteJson / paramsJson: varsayılanlar yazılmaz)

    private static func json<T: Encodable>(_ v: T) -> String {
        let e = JSONEncoder()
        e.outputFormatting = [.withoutEscapingSlashes]
        guard let d = try? e.encode(v) else { return "" }
        return String(decoding: d, as: UTF8.self)
    }

    private static func fromJson<T: Decodable>(_ type: T.Type, _ s: String) -> T? {
        try? JSONDecoder().decode(T.self, from: Data(s.utf8))
    }

    // MARK: Kodlama

    static func encode(_ strokes: [Stroke]) -> Data { encodePages([strokes]) }

    static func encodePages(_ pages: [[Stroke]], layers: [Layer] = [Layer.Default]) -> Data {
        let docLayers = layers.isEmpty ? [Layer.Default] : layers
        var layerIndex: [String: Int] = [:]
        for (i, l) in docLayers.enumerated() { layerIndex[l.id] = i }
        var raw = Writer()
        raw.varint(Int64(docLayers.count))
        for l in docLayers {
            raw.string(l.id); raw.string(l.name)
            raw.byte((l.visible ? 1 : 0) | (l.locked ? 2 : 0))
        }
        let pageList = pages.isEmpty ? [[]] : pages
        raw.varint(Int64(max(pages.count, 1)))
        let v8 = pages.contains { $0.contains(where: needsV8) }
        for page in pageList {
            raw.varint(Int64(page.count))
            for s in page { writeStroke(&raw, s, layerIndex, v8: v8) }
        }
        let dynamics = pages.contains { $0.contains(where: hasDynamics) }
        let v6 = pages.contains { $0.contains { $0.params != nil || hasOrient($0) } }
        let v7 = pages.contains { $0.contains { $0.space != nil } }
        let v9 = pages.contains { $0.contains(where: needsV9) }
        let version: UInt8 = v9 ? VERSION_V9 : v8 ? VERSION_V8 : v7 ? VERSION_V7 : v6 ? VERSION_V6 : dynamics ? VERSION_V5 : VERSION_V4
        var out = Data([MAGIC_0, MAGIC_1, version])
        out.append(deflate(raw.data))
        return out
    }

    // MARK: Çözme

    static func decode(_ data: Data) throws -> [Stroke] { try decodePages(data).first ?? [] }

    static func decodePages(_ data: Data) throws -> [[Stroke]] { try decodeDocument(data).pages }

    static func decodeDocument(_ data: Data) throws -> Document {
        let bytes = [UInt8](data)
        if bytes.count < 3 || bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1 {
            // Eski (v2 veritabanı) JSON içeriği
            return Document(pages: [LegacyJson.decodeStrokes(String(decoding: bytes, as: UTF8.self))], layers: [Layer.Default])
        }
        let version = bytes[2]
        guard (VERSION...VERSION_V9).contains(version) else {
            throw CodecError(description: "Desteklenmeyen içerik sürümü: \(version)")
        }
        let body = try inflate(Data(bytes[3...]))
        var r = Reader(body)
        var layers = [Layer.Default]
        if version >= VERSION_V4 {
            let n = Int(try r.varint())
            var ls: [Layer] = []
            for _ in 0..<n {
                let id = try r.string(), name = try r.string(), flags = try r.byte()
                ls.append(Layer(id: id, name: name, visible: flags & 1 != 0, locked: flags & 2 != 0))
            }
            if !ls.isEmpty { layers = ls }
        }
        let pageCount = version == VERSION ? 1 : Int(try r.varint())
        var pages: [[Stroke]] = []
        for _ in 0..<pageCount {
            let count = Int(try r.varint())
            var page: [Stroke] = []
            page.reserveCapacity(count)
            for _ in 0..<count { page.append(try readStroke(&r, Int(version), layers)) }
            pages.append(page)
        }
        return Document(pages: pages, layers: layers)
    }

    // MARK: Çizim

    private static func needsV8(_ s: Stroke) -> Bool {
        s.hidden || s.model != nil || (s.brushType == .TEXT && (s.text?.box != nil || s.text?.path != nil))
    }

    private static func needsV9(_ s: Stroke) -> Bool { s.brushType == .TEXT && s.text?.path != nil }

    private static func writeStroke(_ w: inout Writer, _ s: Stroke, _ layerIndex: [String: Int], v8: Bool) {
        writeStrokeBody(&w, s)
        let clip = (s.clip?.count == 4) ? s.clip : nil
        let paragraph: TextParagraph? = (s.brushType == .TEXT && s.text != nil && s.text!.paragraph != .NORMAL) ? s.text!.paragraph : nil
        let layer: Int? = s.layerId.flatMap { layerIndex[$0] }.flatMap { $0 > 0 ? $0 : nil }
        let formula = s.brushType == .FORMULA ? s.formula : nil
        let dynamics = hasDynamics(s)
        let orient = hasOrient(s)
        var ext = 0
        if clip != nil { ext |= EXT_CLIP }
        if paragraph != nil { ext |= EXT_PARAGRAPH }
        if layer != nil { ext |= EXT_LAYER }
        if formula != nil { ext |= EXT_FORMULA }
        if dynamics { ext |= EXT_DYNAMICS }
        if orient { ext |= EXT_ORIENT }
        if s.params != nil { ext |= EXT_PARAMS }
        if s.space != nil { ext |= EXT_SPACE }
        w.byte(ext)
        clip?.forEach { w.int32(Int32(bitPattern: $0.x.bitPattern)); w.int32(Int32(bitPattern: $0.y.bitPattern)) }
        if let paragraph { w.byte(paragraphCodes[paragraph]!) }
        if let layer { w.varint(Int64(layer)) }
        if let formula { w.string(formula.source); w.string(formula.templateId) }
        if dynamics {
            for p in s.points { w.byte(unitByte(p.pressure)) }
            for p in s.points { w.byte(unitByte(p.tilt)) }
        }
        if orient { for p in s.points { w.byte(unitByte(p.orient)) } }
        if let params = s.params { w.string(json(params)) }
        if let space = s.space { writeSpace(&w, space) }
        if v8 {
            let box = s.brushType == .TEXT ? s.text?.box : nil
            let textPath = s.brushType == .TEXT ? s.text?.path : nil
            var ext2 = 0
            if box != nil { ext2 |= EXT2_TEXTBOX }
            if s.model != nil { ext2 |= EXT2_MODEL }
            if s.hidden { ext2 |= EXT2_HIDDEN }
            if textPath != nil { ext2 |= EXT2_TEXTPATH }
            w.byte(ext2)
            if let box { w.string(json(box)) }
            if let m = s.model { w.string(m.modelId); w.string(m.dataAssetId) }
            if let textPath { w.string(json(textPath)) }
        }
    }

    private static func writeSpace(_ w: inout Writer, _ sp: Space3D) {
        sp.frame.forEach { w.int32(Int32(bitPattern: $0.bitPattern)) }
        w.int32(Int32(truncatingIfNeeded: Int64(bitPattern: UInt64(bitPattern: sp.stamp) >> 32)))
        w.int32(Int32(truncatingIfNeeded: sp.stamp))
        w.int32(sp.view)
        let n = sp.pointCount
        w.varint(Int64(n))
        var pa: Int64 = 0, pb: Int64 = 0
        for i in 0..<n {
            let a = Int64(javaRound(sp.local[2 * i] * SPACE_Q)), b = Int64(javaRound(sp.local[2 * i + 1] * SPACE_Q))
            w.varint(zigzag(a - pa)); w.varint(zigzag(b - pb))
            pa = a; pb = b
        }
    }

    private static func readSpace(_ r: inout Reader) throws -> Space3D {
        var frame = [Float](repeating: 0, count: 12)
        for i in 0..<12 { frame[i] = Float(bitPattern: UInt32(bitPattern: try r.int32())) }
        let hi = Int64(try r.int32())
        let lo = Int64(try r.int32()) & 0xFFFF_FFFF
        let view = try r.int32()
        let n = Int(try r.varint())
        var local = [Float](repeating: 0, count: n * 2)
        var pa: Int64 = 0, pb: Int64 = 0
        for i in 0..<n {
            pa += unzigzag(try r.varint()); pb += unzigzag(try r.varint())
            local[2 * i] = Float(pa) / SPACE_Q; local[2 * i + 1] = Float(pb) / SPACE_Q
        }
        return Space3D(frame: frame, local: local, stamp: (hi << 32) | lo, view: view)
    }

    private static func writeStrokeBody(_ w: inout Writer, _ s: Stroke) {
        let idUuid = isCanonicalUuid(s.id)
        let groupUuid = s.groupId.map(isCanonicalUuid) ?? false
        let assetUuid = s.assetId.map(isCanonicalUuid) ?? false
        let quantized = s.points.allSatisfy { isQuantized($0.x) && isQuantized($0.y) }
        let rgb = isRgb(s.colorHex)
        var flags = 0
        if idUuid { flags |= F_ID_UUID }
        if s.groupId != nil { flags |= F_GROUP }
        if groupUuid { flags |= F_GROUP_UUID }
        if s.assetId != nil { flags |= F_ASSET }
        if assetUuid { flags |= F_ASSET_UUID }
        if quantized { flags |= F_QUANTIZED }
        if rgb { flags |= F_COLOR_RGB }
        let dynamics = s.flow.bitPattern != Float(1).bitPattern || s.strength.bitPattern != Float(1).bitPattern
        if dynamics { flags |= F_DYNAMICS }
        w.byte(flags)
        w.id(s.id, idUuid)
        w.byte(typeCodes[s.brushType]!)
        if rgb {
            let v = Int(s.colorHex.dropFirst(), radix: 16)!
            w.byte(v >> 16 & 0xFF); w.byte(v >> 8 & 0xFF); w.byte(v & 0xFF)
        } else {
            w.string(s.colorHex)
        }
        w.float(s.sizePx)
        w.float(s.opacity)
        if dynamics { w.float(s.flow); w.float(s.strength) }
        w.varint(zigzag(Int64(s.order)))
        if let g = s.groupId { w.id(g, groupUuid) }
        if let a = s.assetId { w.id(a, assetUuid) }
        w.varint(Int64(s.points.count))
        if quantized {
            var px: Int64 = 0, py: Int64 = 0
            for p in s.points {
                let qx = roundToLong(p.x * QUANT), qy = roundToLong(p.y * QUANT)
                w.varint(zigzag(qx - px)); w.varint(zigzag(qy - py))
                px = qx; py = qy
            }
        } else {
            var bx: UInt32 = 0, by: UInt32 = 0
            for p in s.points {
                let x = p.x.bitPattern, y = p.y.bitPattern
                w.int32(Int32(bitPattern: x ^ bx)); w.int32(Int32(bitPattern: y ^ by))
                bx = x; by = y
            }
        }
        if s.brushType == .SHAPE { w.string(s.shape.map { json($0) } ?? "") }
        if s.brushType == .DIAGRAM { w.string(s.diagram.map { json($0) } ?? "") }
        if s.brushType == .TEXT {
            guard let spec = s.text else { w.byte(0); return }
            w.byte(1)
            w.string(spec.content)
            w.byte(fontCodes[spec.font]!)
            let outline = spec.outlineColorHex
            let hasOutline = outline != nil && spec.outlineWidth > 0
            w.byte((spec.bold ? 1 : 0) | (spec.italic ? 2 : 0) | (spec.underline ? 4 : 0) | (spec.align.ordinal << 3) | (hasOutline ? TEXT_EXTRA : 0))
            if hasOutline {
                w.string(outline!)
                w.float(spec.outlineWidth)
            }
        }
    }

    private static func readStroke(_ r: inout Reader, _ version: Int, _ layers: [Layer]) throws -> Stroke {
        let stroke = try readStrokeBody(&r, version)
        if version < Int(VERSION_V3) { return stroke }
        let ext = try r.byte()
        var clip: [StrokePoint]? = nil
        if ext & EXT_CLIP != 0 {
            var c: [StrokePoint] = []
            for _ in 0..<4 { c.append(StrokePoint(try r.float(), try r.float())) }
            clip = c
        }
        let paragraph: TextParagraph? = ext & EXT_PARAGRAPH != 0 ? (codeParagraphs[try r.byte()] ?? .NORMAL) : nil
        var layer: String? = nil
        if ext & EXT_LAYER != 0 {
            let i = Int(try r.varint())
            layer = layers.indices.contains(i) ? layers[i].id : nil
        }
        let formula: FormulaSpec? = ext & EXT_FORMULA != 0 ? FormulaSpec(source: try r.string(), templateId: try r.string()) : nil
        var out = stroke
        if let clip { out.clip = clip }
        if ext & EXT_DYNAMICS != 0 {
            let n = stroke.points.count
            var pressure = [Float](), tilt = [Float](), orient: [Float]? = nil
            for _ in 0..<n { pressure.append(Float(try r.byte()) / 255) }
            for _ in 0..<n { tilt.append(Float(try r.byte()) / 255) }
            if ext & EXT_ORIENT != 0 {
                var o = [Float]()
                for _ in 0..<n { o.append(Float(try r.byte()) / 255) }
                orient = o
            }
            out.points = stroke.points.enumerated().map { i, p in
                StrokePoint(p.x, p.y, pressure: pressure[i], tilt: tilt[i], orient: orient?[i] ?? 0)
            }
        }
        if ext & EXT_PARAMS != 0 {
            if let params = fromJson(BrushParams.self, try r.string()) { out.params = params }
        }
        if version >= Int(VERSION_V7) && ext & EXT_SPACE != 0 { out.space = try readSpace(&r) }
        if version >= Int(VERSION_V8) {
            let ext2 = try r.byte()
            if ext2 & EXT2_TEXTBOX != 0 {
                let box = fromJson(TextBox.self, try r.string())
                if let box, out.text != nil { out.text!.box = box }
            }
            if ext2 & EXT2_MODEL != 0 { out.model = Model3DLink(modelId: try r.string(), dataAssetId: try r.string()) }
            if ext2 & EXT2_HIDDEN != 0 { out.hidden = true }
            if ext2 & EXT2_TEXTPATH != 0 {
                let path = fromJson(TextPathSpec.self, try r.string())
                if let path, out.text != nil { out.text!.path = path }
            }
        }
        if let paragraph, out.text != nil { out.text!.paragraph = paragraph }
        if let layer { out.layerId = layer }
        if let formula { out.formula = formula }
        return out
    }

    private static func readStrokeBody(_ r: inout Reader, _ version: Int) throws -> Stroke {
        let flags = try r.byte()
        let id = try r.id(flags & F_ID_UUID != 0)
        let type = codeTypes[try r.byte()] ?? .PENCIL
        let color: String
        if flags & F_COLOR_RGB != 0 {
            let v = (try r.byte() << 16) | (try r.byte() << 8) | (try r.byte())
            let hex = String(v, radix: 16).uppercased()
            color = "#" + String(repeating: "0", count: max(0, 6 - hex.count)) + hex
        } else {
            color = try r.string()
        }
        let size = try r.float()
        let opacity = try r.float()
        let flow: Float = flags & F_DYNAMICS != 0 ? try r.float() : 1
        let strength: Float = flags & F_DYNAMICS != 0 ? try r.float() : 1
        let order = Int(unzigzag(try r.varint()))
        let group: String? = flags & F_GROUP != 0 ? try r.id(flags & F_GROUP_UUID != 0) : nil
        let asset: String? = flags & F_ASSET != 0 ? try r.id(flags & F_ASSET_UUID != 0) : nil
        let n = Int(try r.varint())
        var points: [StrokePoint] = []
        points.reserveCapacity(n)
        if flags & F_QUANTIZED != 0 {
            var qx: Int64 = 0, qy: Int64 = 0
            for _ in 0..<n {
                qx += unzigzag(try r.varint()); qy += unzigzag(try r.varint())
                points.append(StrokePoint(Float(qx) / QUANT, Float(qy) / QUANT))
            }
        } else {
            var bx: UInt32 = 0, by: UInt32 = 0
            for _ in 0..<n {
                bx ^= UInt32(bitPattern: try r.int32()); by ^= UInt32(bitPattern: try r.int32())
                points.append(StrokePoint(Float(bitPattern: bx), Float(bitPattern: by)))
            }
        }
        var shape: ShapeSpec? = nil
        var diagram: DiagramSpec? = nil
        if type == .SHAPE {
            let s = try r.string()
            if !s.isEmpty { shape = fromJson(ShapeSpec.self, s) }
        }
        if type == .DIAGRAM {
            let s = try r.string()
            if !s.isEmpty { diagram = fromJson(DiagramSpec.self, s) }
        }
        var text: TextSpec? = nil
        if type == .TEXT, try r.byte() == 1 {
            let content = try r.string()
            let font = codeFonts[try r.byte()] ?? .SANS
            let style = try r.byte()
            var outlineColor: String? = nil
            var outlineWidth: Float = 0
            if style & TEXT_EXTRA != 0 {
                if version >= Int(VERSION_V3) {
                    outlineColor = try r.string()
                    outlineWidth = try r.float()
                } else if let box = fromJson(ShapeStyle.self, try r.string()), box.strokeWidth > 0.05 {
                    outlineColor = box.strokeColorHex
                    outlineWidth = box.strokeWidth
                }
            }
            let alignIdx = style >> 3 & 3
            text = TextSpec(content: content, font: font, bold: style & 1 != 0, italic: style & 2 != 0, underline: style & 4 != 0,
                            align: TextAlignKind.allCases.indices.contains(alignIdx) ? TextAlignKind.allCases[alignIdx] : .LEFT,
                            outlineColorHex: outlineColor, outlineWidth: outlineWidth)
        }
        return Stroke(id: id, points: points, colorHex: color, brushType: type, sizePx: size, opacity: opacity, order: order,
                      groupId: group, assetId: asset, flow: flow, strength: strength, text: text, shape: shape, diagram: diagram)
    }

    // MARK: Yardımcılar

    /// Java `Math.round` (yarımlar +∞ yönüne).
    static func javaRound(_ v: Float) -> Int32 {
        if v.isNaN { return 0 }
        let r = (Double(v) + 0.5).rounded(.down)
        return Int32(clamping: Int64(max(min(r, Double(Int32.max)), Double(Int32.min))))
    }

    /// Kotlin `Float.roundToLong()`.
    static func roundToLong(_ v: Float) -> Int64 {
        let r = (Double(v) + 0.5).rounded(.down)
        if r >= 9.2e18 { return .max }
        if r <= -9.2e18 { return .min }
        return Int64(r)
    }

    private static func isQuantized(_ v: Float) -> Bool {
        if !v.isFinite || abs(v) > 1e7 { return false }
        let q = roundToLong(v * QUANT)
        return (Float(q) / QUANT).bitPattern == v.bitPattern
    }

    static func quantize(_ v: Float) -> Float { Float(roundToLong(v * QUANT)) / QUANT }

    static func quantizeUnit(_ v: Float) -> Float { Float(unitByte(v)) / 255 }

    private static func unitByte(_ v: Float) -> Int {
        if v.isNaN { return 255 }
        return Int(javaRound(min(max(v, 0), 1) * 255))
    }

    private static func hasDynamics(_ s: Stroke) -> Bool { s.points.contains { $0.hasDynamics } }
    private static func hasOrient(_ s: Stroke) -> Bool { s.points.contains { $0.orient != 0 } }

    static func isCanonicalUuid(_ value: String) -> Bool {
        let u = Array(value.utf8)
        if u.count != 36 { return false }
        for (i, c) in u.enumerated() {
            if i == 8 || i == 13 || i == 18 || i == 23 {
                if c != 0x2D { return false }
            } else if !((0x30...0x39).contains(c) || (0x61...0x66).contains(c)) {
                return false
            }
        }
        return true
    }

    private static func isRgb(_ hex: String) -> Bool {
        let u = Array(hex.utf8)
        if u.count != 7 || u[0] != 0x23 { return false }
        for c in u[1...] where !((0x30...0x39).contains(c) || (0x41...0x46).contains(c)) { return false }
        return true
    }

    private static func zigzag(_ v: Int64) -> Int64 { (v << 1) ^ (v >> 63) }
    private static func unzigzag(_ v: Int64) -> Int64 { Int64(bitPattern: UInt64(bitPattern: v) >> 1) ^ -(v & 1) }

    // MARK: DEFLATE (ham, başlıksız — Android `Deflater(nowrap = true)` ile uyumlu)

    static func deflate(_ input: Data) -> Data { stream(input, operation: COMPRESSION_STREAM_ENCODE) ?? Data() }

    static func inflate(_ input: Data) throws -> Data {
        guard let out = stream(input, operation: COMPRESSION_STREAM_DECODE) else {
            throw CodecError(description: "Bozuk içerik")
        }
        return out
    }

    private static func stream(_ input: Data, operation: compression_stream_operation) -> Data? {
        let dst = UnsafeMutablePointer<UInt8>.allocate(capacity: 64 * 1024)
        defer { dst.deallocate() }
        var s = compression_stream(dst_ptr: dst, dst_size: 0, src_ptr: dst, src_size: 0, state: nil)
        guard compression_stream_init(&s, operation, COMPRESSION_ZLIB) == COMPRESSION_STATUS_OK else { return nil }
        defer { compression_stream_destroy(&s) }
        var out = Data()
        let ok: Bool = input.withUnsafeBytes { (buf: UnsafeRawBufferPointer) -> Bool in
            let base = buf.bindMemory(to: UInt8.self).baseAddress
            s.src_ptr = base ?? UnsafePointer(dst)
            s.src_size = buf.count
            while true {
                s.dst_ptr = dst
                s.dst_size = 64 * 1024
                let status = compression_stream_process(&s, Int32(COMPRESSION_STREAM_FINALIZE.rawValue))
                let produced = 64 * 1024 - s.dst_size
                if produced > 0 { out.append(dst, count: produced) }
                switch status {
                case COMPRESSION_STATUS_OK: continue
                case COMPRESSION_STATUS_END: return true
                default: return false
                }
            }
        }
        return ok ? out : nil
    }

    // MARK: Bayt yazıcı / okuyucu

    struct Writer {
        private(set) var data = Data()

        mutating func byte(_ v: Int) { data.append(UInt8(truncatingIfNeeded: v)) }

        mutating func int32(_ v: Int32) {
            let u = UInt32(bitPattern: v)
            data.append(contentsOf: [UInt8(u >> 24 & 0xFF), UInt8(u >> 16 & 0xFF), UInt8(u >> 8 & 0xFF), UInt8(u & 0xFF)])
        }

        mutating func float(_ f: Float) { int32(Int32(bitPattern: f.bitPattern)) }

        mutating func varint(_ value: Int64) {
            var v = UInt64(bitPattern: value)
            while v & ~0x7F != 0 {
                data.append(UInt8((v & 0x7F) | 0x80))
                v >>= 7
            }
            data.append(UInt8(v))
        }

        mutating func string(_ s: String) {
            let b = Array(s.utf8)
            varint(Int64(b.count))
            data.append(contentsOf: b)
        }

        mutating func id(_ s: String, _ uuid: Bool) {
            if !uuid { string(s); return }
            var hi = -1
            for c in s.utf8 where c != 0x2D {
                let d = c <= 0x39 ? Int(c) - 0x30 : Int(c) - 0x61 + 10
                if hi < 0 { hi = d } else { data.append(UInt8((hi << 4) | d)); hi = -1 }
            }
        }
    }

    struct Reader {
        private let data: [UInt8]
        private var pos = 0

        init(_ d: Data) { data = [UInt8](d) }

        private struct EOF: Error {}

        mutating func byte() throws -> Int {
            guard pos < data.count else { throw EOF() }
            defer { pos += 1 }
            return Int(data[pos])
        }

        mutating func int32() throws -> Int32 {
            let v = (UInt32(try byte()) << 24) | (UInt32(try byte()) << 16) | (UInt32(try byte()) << 8) | UInt32(try byte())
            return Int32(bitPattern: v)
        }

        mutating func float() throws -> Float { Float(bitPattern: UInt32(bitPattern: try int32())) }

        mutating func varint() throws -> Int64 {
            var shift: UInt64 = 0
            var result: UInt64 = 0
            while true {
                let b = UInt64(try byte())
                if shift < 64 { result |= (b & 0x7F) << shift }
                if b & 0x80 == 0 { return Int64(bitPattern: result) }
                shift += 7
            }
        }

        mutating func string() throws -> String {
            let n = Int(try varint())
            guard n >= 0, pos + n <= data.count else { throw EOF() }
            let s = String(decoding: data[pos..<(pos + n)], as: UTF8.self)
            pos += n
            return s
        }

        mutating func id(_ uuid: Bool) throws -> String {
            if !uuid { return try string() }
            guard pos + 16 <= data.count else { throw EOF() }
            let b = data[pos..<(pos + 16)]
            pos += 16
            let hex = b.map { String(format: "%02x", $0) }.joined()
            let h = Array(hex)
            return String(h[0..<8]) + "-" + String(h[8..<12]) + "-" + String(h[12..<16]) + "-" + String(h[16..<20]) + "-" + String(h[20..<32])
        }
    }
}

/// Eski (v2) JSON içerik biçimi.
enum LegacyJson {
    static func decodeStrokes(_ json: String) -> [Stroke] {
        (try? JSONDecoder().decode([Stroke].self, from: Data(json.utf8))) ?? []
    }

    static func encodeStrokes(_ strokes: [Stroke]) -> String {
        guard let d = try? JSONEncoder().encode(strokes) else { return "[]" }
        return String(decoding: d, as: UTF8.self)
    }
}
