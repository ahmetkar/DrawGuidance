import Foundation

/// Java `UUID.nameUUIDFromBytes` karşılığı (MD5 tabanlı sürüm 3 UUID): aynı addan hep aynı kimlik çıkar, böylece
/// aynı hareket iki kez işlenirse eleman iki kez eklenmez. Android ile aynı kimlikleri üretir.
enum StableId {
    static func from(_ name: String) -> String {
        var d = md5(Array(name.utf8))
        d[6] = (d[6] & 0x0f) | 0x30
        d[8] = (d[8] & 0x3f) | 0x80
        let hex = d.map { String(format: "%02x", $0) }.joined()
        let c = Array(hex)
        return "\(String(c[0..<8]))-\(String(c[8..<12]))-\(String(c[12..<16]))-\(String(c[16..<20]))-\(String(c[20..<32]))"
    }

    private static let s: [UInt32] = [7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
                                      5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
                                      4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
                                      6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21]
    private static let k: [UInt32] = (0..<64).map { UInt32(truncatingIfNeeded: Int64(abs(sin(Double($0 + 1))) * 4294967296.0)) }

    static func md5(_ message: [UInt8]) -> [UInt8] {
        var msg = message
        let bitLen = UInt64(message.count) &* 8
        msg.append(0x80)
        while msg.count % 64 != 56 { msg.append(0) }
        for i in 0..<8 { msg.append(UInt8(truncatingIfNeeded: bitLen >> (8 * UInt64(i)))) }
        var a0: UInt32 = 0x67452301, b0: UInt32 = 0xefcdab89, c0: UInt32 = 0x98badcfe, d0: UInt32 = 0x10325476
        for chunk in stride(from: 0, to: msg.count, by: 64) {
            var m = [UInt32](repeating: 0, count: 16)
            for i in 0..<16 {
                let j = chunk + i * 4
                m[i] = UInt32(msg[j]) | UInt32(msg[j + 1]) << 8 | UInt32(msg[j + 2]) << 16 | UInt32(msg[j + 3]) << 24
            }
            var a = a0, b = b0, c = c0, d = d0
            for i in 0..<64 {
                var f: UInt32
                let g: Int
                switch i {
                case 0..<16: f = (b & c) | (~b & d); g = i
                case 16..<32: f = (d & b) | (~d & c); g = (5 * i + 1) % 16
                case 32..<48: f = b ^ c ^ d; g = (3 * i + 5) % 16
                default: f = c ^ (b | ~d); g = (7 * i) % 16
                }
                f = f &+ a &+ k[i] &+ m[g]
                a = d; d = c; c = b
                b = b &+ ((f << s[i]) | (f >> (32 - s[i])))
            }
            a0 = a0 &+ a; b0 = b0 &+ b; c0 = c0 &+ c; d0 = d0 &+ d
        }
        return [a0, b0, c0, d0].flatMap { v in (0..<4).map { UInt8(truncatingIfNeeded: v >> (8 * UInt32($0))) } }
    }
}
