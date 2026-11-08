import Foundation

/// Sunucudaki / uygulamayla gelen vektör şekil kaydı.
struct CatalogVector {
    var id: String
    var title: String
    var categoryId: String
    var categoryTitle: String
    var section: String
    var audience: Audience
    var fit: Fit
    var aspect: Float
    var tags: [String]
    var paths: [VPath]
}

struct CatalogModeNet: Equatable {
    var mode: String
    var version: Int32
    var format: String
    var weights: Data
}

enum CatalogInfo {
    static let VERSION: Int64 = 43000
    static let MODE_NET_FORMAT = "modenet-int8-v1"
}

/// Katalog bölümlerinin ikili biçimi (Android/sunucu `CatalogBinary` ile aynı).
enum CatalogBinary {
    static let MEDIA_TYPE = "application/vnd.drawguidance.catalog+bin"
    static let FORMAT_VERSION = 1
    static let SECTION_VECTORS = 1
    static let SECTION_MODE_NETS = 2
    static let V_TITLE: Int32 = 1, V_CATEGORY_ID: Int32 = 2, V_CATEGORY_TITLE: Int32 = 4, V_SECTION: Int32 = 8, V_AUDIENCE: Int32 = 16
    static let V_FIT: Int32 = 32, V_ASPECT: Int32 = 64, V_TAGS: Int32 = 128, V_KINDS: Int32 = 256
    static let N_VERSION: Int32 = 1, N_FORMAT: Int32 = 2
    private static let MAGIC: [UInt8] = [0x44, 0x47, 0x43, 0x42] // DGCB

    struct Section<T> {
        var section: Int
        var version: Int64
        var items: [T]
    }

    struct FormatError: Error, CustomStringConvertible { let description: String }

    static func isBinary(_ bytes: Data) -> Bool { bytes.count >= 6 && Array(bytes.prefix(4)) == MAGIC }

    static func decodeVectors(_ bytes: Data) throws -> Section<CatalogVector> {
        try decode(bytes, SECTION_VECTORS) { r in
            let mask = try r.varint()
            let id = try r.string()
            let title = mask & V_TITLE != 0 ? try r.string() : nil
            let categoryId = mask & V_CATEGORY_ID != 0 ? try r.string() : nil
            let categoryTitle = mask & V_CATEGORY_TITLE != 0 ? try r.string() : nil
            let section = mask & V_SECTION != 0 ? try r.string() : nil
            let audience = mask & V_AUDIENCE != 0 ? try r.string() : nil
            let fit = mask & V_FIT != 0 ? try r.string() : nil
            let aspect = mask & V_ASPECT != 0 ? try r.float32() : nil
            let tags = mask & V_TAGS != 0 ? try r.strings() : []
            _ = mask & V_KINDS != 0 ? try r.strings() : []
            let packed = try r.bytes()
            return CatalogVector(id: id, title: title ?? "", categoryId: categoryId ?? "remote",
                                 categoryTitle: categoryTitle ?? "Yeni şekiller", section: section ?? "",
                                 audience: audience.flatMap(Audience.init(rawValue:)) ?? .ART,
                                 fit: fit.flatMap(Fit.init(rawValue:)) ?? .BOX, aspect: aspect ?? 1, tags: tags,
                                 paths: CatalogCodec.unpackPaths(packed))
        }
    }

    static func decodeModeNets(_ bytes: Data) throws -> Section<CatalogModeNet> {
        try decode(bytes, SECTION_MODE_NETS) { r in
            let mask = try r.varint()
            let mode = try r.string()
            let version = mask & N_VERSION != 0 ? try r.zigzag() : 0
            let format = mask & N_FORMAT != 0 ? try r.string() : CatalogInfo.MODE_NET_FORMAT
            return CatalogModeNet(mode: mode, version: version, format: format, weights: try r.bytes())
        }
    }

    private static func decode<T>(_ data: Data, _ section: Int, _ item: (inout ByteReader) throws -> T) throws -> Section<T> {
        guard isBinary(data) else { throw FormatError(description: "ikili katalog değil") }
        let bytes = [UInt8](data)
        var r = ByteReader(bytes, 4, bytes.count)
        let format = try r.byte()
        guard format == FORMAT_VERSION else { throw FormatError(description: "desteklenmeyen ikili katalog sürümü: \(format)") }
        let got = try r.byte()
        guard got == section else { throw FormatError(description: "beklenen bölüm \(section), gelen \(got)") }
        let version = try r.varlong()
        let count = Int(try r.varint())
        guard count >= 0 else { throw FormatError(description: "bozuk öğe sayısı") }
        var out: [T] = []
        out.reserveCapacity(min(count, 65_536))
        for _ in 0..<count {
            let len = Int(try r.varint())
            guard len >= 0, len <= r.remaining else { throw FormatError(description: "bozuk öğe uzunluğu") }
            var sub = ByteReader(bytes, r.pos, r.pos + len)
            out.append(try item(&sub))
            try r.skip(len)
        }
        return Section(section: section, version: version, items: out)
    }
}

/// Sınırlı bayt okuyucu (katalog biçimleri için; varint 32 bit, Java `int` gibi taşar).
struct ByteReader {
    let buf: [UInt8]
    var pos: Int
    let end: Int

    init(_ buf: [UInt8], _ pos: Int = 0, _ end: Int? = nil) { self.buf = buf; self.pos = pos; self.end = end ?? buf.count }

    struct Short: Error {}

    var remaining: Int { end - pos }

    mutating func byte() throws -> Int {
        guard pos < end else { throw Short() }
        defer { pos += 1 }
        return Int(buf[pos])
    }

    mutating func skip(_ n: Int) throws {
        guard n >= 0, n <= remaining else { throw Short() }
        pos += n
    }

    mutating func varint() throws -> Int32 {
        var shift: UInt32 = 0
        var out: UInt32 = 0
        while true {
            let b = UInt32(try byte())
            out |= (b & 0x7F) << shift
            if b & 0x80 == 0 { return Int32(bitPattern: out) }
            shift += 7
            guard shift < 35 else { throw Short() }
        }
    }

    mutating func varlong() throws -> Int64 {
        var shift: UInt64 = 0
        var out: UInt64 = 0
        while true {
            let b = UInt64(try byte())
            if shift < 64 { out |= (b & 0x7F) << shift }
            if b & 0x80 == 0 { return Int64(bitPattern: out) }
            shift += 7
            guard shift < 70 else { throw Short() }
        }
    }

    mutating func zigzag() throws -> Int32 {
        let v = UInt32(bitPattern: try varint())
        return Int32(bitPattern: v >> 1) ^ -Int32(bitPattern: v & 1)
    }

    mutating func float32() throws -> Float {
        let b = (UInt32(try byte()) << 24) | (UInt32(try byte()) << 16) | (UInt32(try byte()) << 8) | UInt32(try byte())
        return Float(bitPattern: b)
    }

    mutating func bytes() throws -> Data {
        let n = Int(try varint())
        guard n >= 0, n <= remaining else { throw Short() }
        defer { pos += n }
        return Data(buf[pos..<(pos + n)])
    }

    mutating func string() throws -> String {
        let n = Int(try varint())
        guard n >= 0, n <= remaining else { throw Short() }
        defer { pos += n }
        return String(decoding: buf[pos..<(pos + n)], as: UTF8.self)
    }

    mutating func strings() throws -> [String] {
        let n = Int(try varint())
        guard n >= 0, n <= remaining else { throw Short() }
        var out: [String] = []
        for _ in 0..<n { out.append(try string()) }
        return out
    }
}

/// Yolların paketli ikili biçimi (Android `CatalogCodec.packPaths/unpackPaths`).
enum CatalogCodec {
    private static let PACK_VERSION = 1
    private static let F_CLOSED: Int32 = 1, F_FILL: Int32 = 2, F_STROKE: Int32 = 4, F_WIDTH: Int32 = 8, F_LINE: Int32 = 16
    private static let F_ALPHA: Int32 = 32, F_TAG: Int32 = 64, F_LABEL: Int32 = 128, F_LABEL_SIZE: Int32 = 256

    static func unpackPaths(_ data: Data) -> [VPath] {
        var r = ByteReader([UInt8](data))
        do {
            guard try r.byte() == 0x56, try r.byte() == PACK_VERSION else { return [] }
            let n = Int(try r.varint())
            var out: [VPath] = []
            out.reserveCapacity(n)
            var px: Int32 = 0, py: Int32 = 0
            for _ in 0..<n {
                let flags = try r.varint()
                let count = Int(try r.varint())
                var pts: [P] = []
                pts.reserveCapacity(count)
                for _ in 0..<count {
                    px &+= try r.zigzag(); py &+= try r.zigzag()
                    pts.append(P(Float(px) / 10000, Float(py) / 10000))
                }
                var p = VPath(points: pts, closed: flags & F_CLOSED != 0)
                if flags & F_FILL != 0 { p.fill = try r.string() }
                if flags & F_STROKE != 0 { p.stroke = try r.string() }
                if flags & F_WIDTH != 0 { p.width = Float(try r.zigzag()) / 10000 }
                if flags & F_LINE != 0 { p.line = LineStyle(rawValue: try r.string()) ?? .SOLID }
                if flags & F_ALPHA != 0 { p.alpha = Float(try r.zigzag()) / 10000 }
                if flags & F_TAG != 0 { p.tag = try r.string() }
                if flags & F_LABEL != 0 { p.label = try r.string() }
                if flags & F_LABEL_SIZE != 0 { p.labelSize = Float(try r.zigzag()) / 10000 }
                out.append(p)
            }
            return out
        } catch {
            return []
        }
    }
}
