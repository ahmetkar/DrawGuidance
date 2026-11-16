import Compression
import Foundation

/// v54 resim dosyasının yapı ve gömülü kod denetimi (Android `ImageSecurity` ile aynı kurallar; çözücüye verilmeden önce).
///
/// - PNG: imzadan sonra parçalar dosya içinde kalmalı; ilk parça IHDR, IEND bulunmalı.
/// - JPEG: SOI ile başlar; segment uzunlukları dosya içinde kalmalı (SOS sonrası sıkıştırılmış veri atlanır).
/// - WEBP: RIFF boyutu dosyayı aşmamalı; parçalar dosya içinde kalmalı.
/// - Metin/üst veri parçaları ve bitiş işaretinden sonraki veriler betik, HTML/PHP, çalıştırılabilir dosya ve arşiv
///   imzalarına karşı taranır. Bitişten sonraki zararsız veri reddedilmez (resim her zaman yeniden kodlanır).
enum ImageSecurity {
    enum Verdict { case CLEAN, MALFORMED, SUSPICIOUS }

    /// En az 5 karakter: büyük ikili veride rastlantıyla eşleşmesin.
    private static let textSigns: [[UInt8]] = [
        "<script", "</script", "<?php", "<html", "<body", "<iframe", "<object", "<embed", "<svg ", "javascript:",
        "vbscript:", "#!/bin/", "#!/usr/", "eval(", "document.cookie", "powershell", "cmd.exe", "/bin/sh",
    ].map { Array($0.utf8) }

    private static let binSigns: [[UInt8]] = [
        [0x7F, 0x45, 0x4C, 0x46], [0x50, 0x4B, 3, 4], Array("Rar!".utf8), Array("%PDF-".utf8), [0xCA, 0xFE, 0xBA, 0xBE],
        [0x64, 0x65, 0x78, 0x0A],
    ]
    private static let zipEocd: [UInt8] = [0x50, 0x4B, 5, 6]
    private static let binScanMax = 256 * 1024
    private static let maxInflate = 1 << 20

    static func inspect(_ data: Data, mime: String) -> Verdict {
        let b = [UInt8](data)
        switch mime {
        case "image/png": return png(b)
        case "image/jpeg": return jpeg(b)
        case "image/webp": return webp(b)
        default: return .MALFORMED
        }
    }

    // MARK: PNG

    private static func png(_ b: [UInt8]) -> Verdict {
        var at = 8
        var first = true
        while true {
            if at + 12 > b.count { return .MALFORMED }
            let len = be32(b, at)
            if len < 0 || len > b.count - at - 12 { return .MALFORMED }
            let typeBytes = b[(at + 4)..<(at + 8)]
            guard typeBytes.allSatisfy({ (0x41...0x5A).contains($0) || (0x61...0x7A).contains($0) }) else { return .MALFORMED }
            let type = String(decoding: typeBytes, as: UTF8.self)
            if first && type != "IHDR" { return .MALFORMED }
            first = false
            let data = at + 8
            switch type {
            case "IEND": return trailing(b, data + len + 4)
            case "IDAT", "IHDR", "PLTE", "tRNS", "fdAT", "fcTL", "acTL": break
            case "zTXt":
                if let z = indexOf0(b, data, data + len), z + 2 <= data + len {
                    if suspicious(b, data, z - data) { return .SUSPICIOUS }
                    guard let text = inflate(b, z + 2, data + len - z - 2) else { return .MALFORMED }
                    if suspicious(text, 0, text.count) { return .SUSPICIOUS }
                }
            case "iTXt":
                if let z = indexOf0(b, data, data + len), z + 2 < data + len, b[z + 1] == 1,
                   let l1 = indexOf0(b, z + 3, data + len), let l2 = indexOf0(b, l1 + 1, data + len) {
                    if suspicious(b, data, l2 + 1 - data) { return .SUSPICIOUS }
                    guard let text = inflate(b, l2 + 1, data + len - l2 - 1) else { return .MALFORMED }
                    if suspicious(text, 0, text.count) { return .SUSPICIOUS }
                } else if suspicious(b, data, len) { return .SUSPICIOUS }
            default:
                if suspicious(b, data, len) { return .SUSPICIOUS }
            }
            at = data + len + 4
        }
    }

    // MARK: JPEG

    private static func jpeg(_ b: [UInt8]) -> Verdict {
        guard b.count >= 4, b[0] == 0xFF, b[1] == 0xD8 else { return .MALFORMED }
        var at = 2
        while at < b.count {
            if b[at] != 0xFF { return .MALFORMED }
            while at < b.count && b[at] == 0xFF { at += 1 }
            if at >= b.count { return .MALFORMED }
            let marker = Int(b[at])
            at += 1
            if marker == 0xD9 { return trailing(b, at) }
            if marker == 0x01 || (0xD0...0xD7).contains(marker) { continue }
            if at + 2 > b.count { return .MALFORMED }
            let len = be16(b, at)
            if len < 2 || at + len > b.count { return .MALFORMED }
            if (0xE0...0xEF).contains(marker) || marker == 0xFE {
                if suspicious(b, at + 2, len - 2) { return .SUSPICIOUS }
            }
            at += len
            if marker == 0xDA {
                while at + 1 < b.count {
                    if b[at] == 0xFF {
                        let n = Int(b[at + 1])
                        if n != 0x00 && !(0xD0...0xD7).contains(n) && n != 0xFF { break }
                    }
                    at += 1
                }
                if at + 1 >= b.count { return .CLEAN }
            }
        }
        return .CLEAN
    }

    // MARK: WEBP

    private static func webp(_ b: [UInt8]) -> Verdict {
        guard b.count >= 20 else { return .MALFORMED }
        let riff = le32(b, 4)
        guard riff >= 4, riff + 8 <= b.count else { return .MALFORMED }
        let end = 8 + riff
        let known: Set<String> = ["VP8 ", "VP8L", "VP8X", "ALPH", "ANIM", "ANMF", "ICCP"]
        var at = 12
        while at + 8 <= end {
            let tag = String(decoding: b[at..<(at + 4)], as: UTF8.self)
            let len = le32(b, at + 4)
            if len < 0 || at + 8 + len > end { return .MALFORMED }
            if !known.contains(tag), suspicious(b, at + 8, len) { return .SUSPICIOUS }
            at += 8 + len + (len & 1)
        }
        return trailing(b, end)
    }

    // MARK: Ortak

    private static func trailing(_ b: [UInt8], _ from: Int) -> Verdict {
        if from >= b.count { return .CLEAN }
        if from + 1 < b.count && b[from] == 0x4D && b[from + 1] == 0x5A { return .SUSPICIOUS }
        let head = min(b.count, from + 64)
        for sig in binSigns where find(b, from, head, sig) { return .SUSPICIOUS }
        if find(b, max(from, b.count - 65_557), b.count, zipEocd) { return .SUSPICIOUS }
        return suspicious(b, from, b.count - from) ? .SUSPICIOUS : .CLEAN
    }

    /// `b` içinde `off`'tan `len` baytta metin ya da ikili kod imzası var mı.
    static func suspicious(_ b: [UInt8], _ off: Int, _ len: Int) -> Bool {
        if len <= 0 { return false }
        let end = min(b.count, off + len)
        if off >= end { return false }
        if end - off <= binScanMax { for sig in binSigns where find(b, off, end, sig) { return true } }
        var lower = Array(b[off..<end])
        for i in lower.indices where (0x41...0x5A).contains(lower[i]) { lower[i] += 32 }
        for sig in textSigns where find(lower, 0, lower.count, sig) { return true }
        return false
    }

    private static func find(_ b: [UInt8], _ from: Int, _ end: Int, _ sig: [UInt8]) -> Bool {
        let last = end - sig.count
        if from > last { return false }
        let first = sig[0]
        var i = from
        while i <= last {
            if b[i] == first {
                var k = 1
                while k < sig.count && b[i + k] == sig[k] { k += 1 }
                if k == sig.count { return true }
            }
            i += 1
        }
        return false
    }

    /// zlib akışını açar (en çok 1 MB; açılamazsa nil).
    private static func inflate(_ b: [UInt8], _ off: Int, _ len: Int) -> [UInt8]? {
        // zlib başlığı (2 bayt) atlanır; Compression çerçevesi ham DEFLATE bekler
        guard len > 2 else { return [] }
        let src = Array(b[(off + 2)..<(off + len)])
        var out = [UInt8](repeating: 0, count: maxInflate)
        let n = src.withUnsafeBufferPointer { s in
            out.withUnsafeMutableBufferPointer { d in
                compression_decode_buffer(d.baseAddress!, maxInflate, s.baseAddress!, src.count, nil, COMPRESSION_ZLIB)
            }
        }
        if n == 0 { return nil }
        return Array(out[0..<n])
    }

    private static func indexOf0(_ b: [UInt8], _ from: Int, _ end: Int) -> Int? {
        var i = from
        while i < min(end, b.count) { if b[i] == 0 { return i }; i += 1 }
        return nil
    }

    private static func be32(_ b: [UInt8], _ at: Int) -> Int { Int(Int32(bitPattern: UInt32(b[at]) << 24 | UInt32(b[at + 1]) << 16 | UInt32(b[at + 2]) << 8 | UInt32(b[at + 3]))) }
    private static func be16(_ b: [UInt8], _ at: Int) -> Int { Int(b[at]) << 8 | Int(b[at + 1]) }
    private static func le32(_ b: [UInt8], _ at: Int) -> Int { Int(Int32(bitPattern: UInt32(b[at]) | UInt32(b[at + 1]) << 8 | UInt32(b[at + 2]) << 16 | UInt32(b[at + 3]) << 24)) }
}
