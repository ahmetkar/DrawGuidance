import Foundation

/// Uygulamayla gelen ikili kaynaklar (yazı/şekil ağları, şablonlar): Android `getResourceAsStream` karşılığı.
/// Testler ve komut satırı araçları [loader]'ı değiştirebilir.
enum BundledResources {
    static var loader: (String) -> Data? = { name in
        let base = (name as NSString).deletingPathExtension, ext = (name as NSString).pathExtension
        final class Marker {}
        for b in [Bundle.main, Bundle(for: Marker.self)] {
            if let url = b.url(forResource: base, withExtension: ext), let d = try? Data(contentsOf: url) { return d }
        }
        return nil
    }

    static func data(_ name: String) -> Data? { loader(name) }
}

/// Java `DataInputStream` okuyucusu (büyük uçlu; writeUTF biçimi değiştirilmiş UTF-8).
struct JavaDataReader {
    private let d: [UInt8]
    private(set) var pos = 0
    init(_ data: Data) { d = [UInt8](data) }

    struct EOF: Error {}

    mutating func u8() throws -> UInt8 { guard pos < d.count else { throw EOF() }; defer { pos += 1 }; return d[pos] }
    mutating func int() throws -> Int32 {
        var v: UInt32 = 0
        for _ in 0..<4 { v = v << 8 | UInt32(try u8()) }
        return Int32(bitPattern: v)
    }
    mutating func short() throws -> UInt16 { UInt16(try u8()) << 8 | UInt16(try u8()) }
    mutating func float() throws -> Float { Float(bitPattern: UInt32(bitPattern: try int())) }
    mutating func bytes(_ n: Int) throws -> [Int8] {
        guard n >= 0, pos + n <= d.count else { throw EOF() }
        defer { pos += n }
        return d[pos..<(pos + n)].map { Int8(bitPattern: $0) }
    }
    mutating func utf() throws -> String {
        let n = Int(try short())
        guard pos + n <= d.count else { throw EOF() }
        var units: [UInt16] = []
        var i = pos
        let end = pos + n
        while i < end {
            let a = UInt16(d[i])
            if a & 0x80 == 0 { units.append(a); i += 1 }
            else if a & 0xE0 == 0xC0 { units.append((a & 0x1F) << 6 | UInt16(d[i + 1] & 0x3F)); i += 2 }
            else { units.append((a & 0x0F) << 12 | UInt16(d[i + 1] & 0x3F) << 6 | UInt16(d[i + 2] & 0x3F)); i += 3 }
        }
        pos = end
        return String(utf16CodeUnits: units, count: units.count)
    }
}
