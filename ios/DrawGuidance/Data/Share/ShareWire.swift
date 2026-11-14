import Foundation

/// v51 cihazlar arası paylaşım protokolü (Android `ShareWire.kt` ile bayt düzeyinde aynı).
///
/// mesaj = [tür: 1 bayt][JSON uzunluğu: u32 BE][JSON (UTF-8)][ikili kuyruk]. Kuyruk, JSON başlığındaki uzunluklara göre
/// bölünür (tuval içeriği, resimler). Taşıma: aynı Wi-Fi ağı. Cihazlar Bonjour ile kendiliğinden bulunur, bağlantı
/// TCP'dir; her mesaj [u32 BE uzunluk][mesaj] çerçevesiyle gider.
enum ShareWire {
    static let PROTOCOL = 1
    /// Bonjour hizmet türü (Android NSD ile ortak).
    static let LAN_SERVICE_TYPE = "_dgshare._tcp"

    static let HELLO: UInt8 = 1
    static let OFFER: UInt8 = 2
    static let ACCEPT: UInt8 = 3
    static let REJECT: UInt8 = 4
    static let PACKAGE: UInt8 = 5
    static let RECEIVED: UInt8 = 6
    static let LIVE_START: UInt8 = 10
    static let LIVE_UPDATE: UInt8 = 11
    static let LIVE_STOP: UInt8 = 12
    static let BYE: UInt8 = 20

    static let TYPE_CANVAS = "canvas"
    static let TYPE_IMAGE = "image"
    static let TYPE_LIVE = "live"

    static let MAX_MESSAGE = 96 * 1024 * 1024

    struct Message {
        let type: UInt8
        let json: Data
        let tail: Data

        /// Kuyruktaki ardışık parçalar.
        func parts(_ lengths: [Int]) throws -> [Data] {
            var at = 0
            return try lengths.map { n in
                guard n >= 0, at + n <= tail.count else { throw ShareError.malformed }
                let start = tail.startIndex + at
                defer { at += n }
                return tail.subdata(in: start..<(start + n))
            }
        }

        func header<T: Decodable>(_ type: T.Type) throws -> T { try JSONDecoder().decode(T.self, from: json) }
    }

    static func encode(_ type: UInt8, _ header: Data, _ tail: [Data] = []) -> Data {
        var out = Data(capacity: 5 + header.count + tail.reduce(0) { $0 + $1.count })
        out.append(type)
        out.append(u32(header.count))
        out.append(header)
        tail.forEach { out.append($0) }
        return out
    }

    static func encode<T: Encodable>(_ type: UInt8, _ value: T, _ tail: [Data] = []) -> Data {
        encode(type, (try? JSONEncoder().encode(value)) ?? Data("{}".utf8), tail)
    }

    static func decode(_ bytes: Data) throws -> Message {
        guard bytes.count >= 5 else { throw ShareError.malformed }
        let b = [UInt8](bytes.prefix(5))
        let n = Int(readU32(b, 1))
        guard n <= bytes.count - 5 else { throw ShareError.malformed }
        let s = bytes.startIndex
        return Message(type: b[0], json: bytes.subdata(in: (s + 5)..<(s + 5 + n)), tail: bytes.subdata(in: (s + 5 + n)..<bytes.endIndex))
    }

    static func u32(_ v: Int) -> Data {
        let x = UInt32(truncatingIfNeeded: v)
        return Data([UInt8(x >> 24 & 0xFF), UInt8(x >> 16 & 0xFF), UInt8(x >> 8 & 0xFF), UInt8(x & 0xFF)])
    }

    static func readU32(_ b: [UInt8], _ at: Int) -> UInt32 {
        UInt32(b[at]) << 24 | UInt32(b[at + 1]) << 16 | UInt32(b[at + 2]) << 8 | UInt32(b[at + 3])
    }
}

enum ShareError: Error { case malformed, notFound, failed(String) }

/// Bağlantı kurulunca karşılıklı tanışma.
struct ShareHello: Codable {
    var v = ShareWire.PROTOCOL
    var name: String
    var platform: String
    var app = ""

    init(name: String, platform: String, app: String) { self.name = name; self.platform = platform; self.app = app }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        v = try c.decodeIfPresent(Int.self, forKey: .v) ?? 1
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? "?"
        platform = try c.decodeIfPresent(String.self, forKey: .platform) ?? ""
        app = try c.decodeIfPresent(String.self, forKey: .app) ?? ""
    }
}

struct ShareOffer: Codable, Equatable {
    var id: String
    var type: String
    var title: String
    var bytes: Int64 = 0
    var canvasKind: String?

    init(id: String, type: String, title: String, bytes: Int64, canvasKind: String?) {
        self.id = id; self.type = type; self.title = title; self.bytes = bytes; self.canvasKind = canvasKind
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        type = try c.decode(String.self, forKey: .type)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        bytes = try c.decodeIfPresent(Int64.self, forKey: .bytes) ?? 0
        canvasKind = try c.decodeIfPresent(String.self, forKey: .canvasKind)
    }
}

struct ShareReply: Codable {
    var id: String
    var canvasId: String?
}

struct ShareAsset: Codable, Equatable {
    var id: String
    var mime: String
    var w = 0
    var h = 0
    var len: Int

    init(id: String, mime: String, w: Int, h: Int, len: Int) { self.id = id; self.mime = mime; self.w = w; self.h = h; self.len = len }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        mime = try c.decodeIfPresent(String.self, forKey: .mime) ?? "image/png"
        w = try c.decodeIfPresent(Int.self, forKey: .w) ?? 0
        h = try c.decodeIfPresent(Int.self, forKey: .h) ?? 0
        len = try c.decode(Int.self, forKey: .len)
    }
}

/// Tuval / resim paketi ve yayın güncellemesinin başlığı (Android `SharePackageHeader`).
struct SharePackageHeader: Codable {
    var id: String
    var type: String
    var name: String
    var canvasKind = "ART"
    var width = 0
    var height = 0
    var backgroundColorHex = "#FFFFFF"
    /// Android CanvasEntity.background biçimi (ortak arka plan + "pages" + "pageSettings").
    var background = ""
    var guide = ""
    var currentPage = 0
    var aiEnabled = true
    var contentLen = 0
    var assets: [ShareAsset] = []
    var seq: Int64 = 0
    var mime: String?
    var imageW = 0
    var imageH = 0
    var from = ""

    init(id: String, type: String, name: String) { self.id = id; self.type = type; self.name = name }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        type = try c.decode(String.self, forKey: .type)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        canvasKind = try c.decodeIfPresent(String.self, forKey: .canvasKind) ?? "ART"
        width = try c.decodeIfPresent(Int.self, forKey: .width) ?? 0
        height = try c.decodeIfPresent(Int.self, forKey: .height) ?? 0
        backgroundColorHex = try c.decodeIfPresent(String.self, forKey: .backgroundColorHex) ?? "#FFFFFF"
        background = try c.decodeIfPresent(String.self, forKey: .background) ?? ""
        guide = try c.decodeIfPresent(String.self, forKey: .guide) ?? ""
        currentPage = try c.decodeIfPresent(Int.self, forKey: .currentPage) ?? 0
        aiEnabled = try c.decodeIfPresent(Bool.self, forKey: .aiEnabled) ?? true
        contentLen = try c.decodeIfPresent(Int.self, forKey: .contentLen) ?? 0
        assets = try c.decodeIfPresent([ShareAsset].self, forKey: .assets) ?? []
        seq = try c.decodeIfPresent(Int64.self, forKey: .seq) ?? 0
        mime = try c.decodeIfPresent(String.self, forKey: .mime)
        imageW = try c.decodeIfPresent(Int.self, forKey: .imageW) ?? 0
        imageH = try c.decodeIfPresent(Int.self, forKey: .imageH) ?? 0
        from = try c.decodeIfPresent(String.self, forKey: .from) ?? ""
    }
}

/// Kurulmaya hazır paket: başlık, içerik ve resimler.
struct SharePackage {
    let header: SharePackageHeader
    let content: Data
    let assets: [Data]

    func encode(_ type: UInt8) -> Data { ShareWire.encode(type, header, [content] + assets) }

    static func decode(_ m: ShareWire.Message) throws -> SharePackage {
        let h = try m.header(SharePackageHeader.self)
        let parts = try m.parts([h.contentLen] + h.assets.map(\.len))
        return SharePackage(header: h, content: parts[0], assets: Array(parts.dropFirst()))
    }
}

/// Mesaj tabanlı yollar için bölme/birleştirme (Android `ShareChunker` ile aynı düzen).
final class ShareChunker {
    private let chunkSize: Int
    private var nextId: UInt32 = 1
    private var partial: [UInt32: [Data?]] = [:]
    private var received: [UInt32: Int] = [:]
    private let lock = NSLock()

    init(chunkSize: Int) { self.chunkSize = chunkSize }

    func split(_ message: Data) -> [Data] {
        lock.lock(); let id = nextId; nextId += 1; lock.unlock()
        let count = max(1, (message.count + chunkSize - 1) / chunkSize)
        return (0..<count).map { i in
            let from = message.startIndex + i * chunkSize
            let to = min(message.endIndex, from + chunkSize)
            var d = ShareWire.u32(Int(id)); d.append(ShareWire.u32(i)); d.append(ShareWire.u32(count))
            d.append(message.subdata(in: from..<to))
            return d
        }
    }

    func accept(_ chunk: Data, progress: ((Int, Int) -> Void)? = nil) -> Data? {
        guard chunk.count >= 12 else { return nil }
        let b = [UInt8](chunk.prefix(12))
        let id = ShareWire.readU32(b, 0), index = Int(ShareWire.readU32(b, 4)), count = Int(ShareWire.readU32(b, 8))
        guard count > 0, index < count, count * chunkSize <= ShareWire.MAX_MESSAGE + chunkSize else { return nil }
        let body = chunk.subdata(in: (chunk.startIndex + 12)..<chunk.endIndex)
        if count == 1 { return body }
        lock.lock(); defer { lock.unlock() }
        var parts = partial[id] ?? Array(repeating: nil, count: count)
        guard parts.count == count else { return nil }
        if parts[index] == nil { parts[index] = body; received[id, default: 0] += 1 }
        partial[id] = parts
        let got = received[id] ?? 0
        progress?(got, count)
        guard got == count else { return nil }
        partial[id] = nil; received[id] = nil
        return parts.reduce(into: Data()) { $0.append($1!) }
    }
}
