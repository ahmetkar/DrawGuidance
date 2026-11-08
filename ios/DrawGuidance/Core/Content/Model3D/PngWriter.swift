import Foundation

/// v24 PNG yazıcı (glTF dışa aktarmada dokuları gömmek için; Android `PngWriter`): RGBA 8 bit, her satırda "Sub"
/// süzgeci, zlib sarmalı DEFLATE.
enum PngWriter {

    static func encode(_ t: Texture) -> Data { encode(t.px, t.w, t.h) }

    /// Önçarpımsız ARGB pikseller → PNG baytları.
    static func encode(_ argb: [UInt32], _ w: Int, _ h: Int) -> Data {
        precondition(argb.count == w * h)
        var raw = [UInt8](repeating: 0, count: h * (1 + w * 4))
        var o = 0
        for y in 0..<h {
            raw[o] = 1; o += 1 // Sub süzgeci
            var pr = 0, pg = 0, pb = 0, pa = 0
            for x in 0..<w {
                let c = argb[y * w + x]
                let a = Int(c >> 24 & 0xFF), r = Int(c >> 16 & 0xFF), g = Int(c >> 8 & 0xFF), b = Int(c & 0xFF)
                raw[o] = UInt8(truncatingIfNeeded: r - pr); raw[o + 1] = UInt8(truncatingIfNeeded: g - pg)
                raw[o + 2] = UInt8(truncatingIfNeeded: b - pb); raw[o + 3] = UInt8(truncatingIfNeeded: a - pa)
                o += 4
                pr = r; pg = g; pb = b; pa = a
            }
        }
        var idat = Data([0x78, 0xDA])
        idat.append(ContentCodec.deflate(Data(raw)))
        idat.append(be32(adler32(raw)))
        var out = Data([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
        var ihdr = be32(UInt32(w)); ihdr.append(be32(UInt32(h))); ihdr.append(contentsOf: [8, 6, 0, 0, 0])
        chunk(&out, "IHDR", ihdr)
        chunk(&out, "IDAT", idat)
        chunk(&out, "IEND", Data())
        return out
    }

    private static func chunk(_ out: inout Data, _ type: String, _ data: Data) {
        out.append(be32(UInt32(data.count)))
        let t = Data(type.utf8)
        out.append(t); out.append(data)
        var crc = Crc32()
        crc.update(t); crc.update(data)
        out.append(be32(crc.value))
    }

    private static func be32(_ v: UInt32) -> Data { Data([UInt8(v >> 24 & 0xFF), UInt8(v >> 16 & 0xFF), UInt8(v >> 8 & 0xFF), UInt8(v & 0xFF)]) }

    static func adler32(_ d: [UInt8]) -> UInt32 {
        var a: UInt32 = 1, b: UInt32 = 0
        var i = 0
        while i < d.count {
            let end = Swift.min(i + 5552, d.count)
            while i < end { a += UInt32(d[i]); b += a; i += 1 }
            a %= 65521; b %= 65521
        }
        return b << 16 | a
    }
}

/// CRC-32 (IEEE; `java.util.zip.CRC32` ile aynı).
struct Crc32 {
    private static let table: [UInt32] = (0..<256).map { n -> UInt32 in
        var c = UInt32(n)
        for _ in 0..<8 { c = (c & 1) != 0 ? 0xEDB8_8320 ^ (c >> 1) : c >> 1 }
        return c
    }
    private var crc: UInt32 = 0xFFFF_FFFF
    mutating func update<S: Sequence>(_ bytes: S) where S.Element == UInt8 {
        for b in bytes { crc = Crc32.table[Int((crc ^ UInt32(b)) & 0xFF)] ^ (crc >> 8) }
    }
    var value: UInt32 { crc ^ 0xFFFF_FFFF }
}
