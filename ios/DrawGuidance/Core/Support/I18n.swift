import Foundation

/// Koddaki Türkçe metinlerin (hazır şekil ve 3B model adları, görünüm modeli iletileri) çalışma anındaki çevirisi
/// (Android `I18n`). Tablolar `i18n-<dil>.tsv` dosyalarındadır: her satır `Türkçe<TAB>çeviri`. Türkçede tablo yoktur.
enum I18n {
    static let DEFAULT = "tr"
    private static let lock = NSLock()
    private static var _language = DEFAULT
    private static var table: [String: String] = [:]

    static var language: String { lock.lock(); defer { lock.unlock() }; return _language }

    /// Uygulamanın dili: tercih edilen ilk desteklenen dil (yoksa Türkçe).
    static func systemLanguage() -> String {
        let supported = ["tr", "en", "zh", "ja", "ru", "ko", "ar"]
        for id in Locale.preferredLanguages {
            let code = String(id.lowercased().prefix { $0 != "-" && $0 != "_" })
            if supported.contains(code) { return code }
        }
        return DEFAULT
    }

    static func setLanguage(_ code: String) {
        let c = code.lowercased()
        let t = c == DEFAULT ? [:] : load(c)
        lock.lock(); _language = c; table = t; lock.unlock()
    }

    static func load(_ code: String, bundle: Bundle = .main) -> [String: String] {
        guard let url = bundle.url(forResource: "i18n-\(code)", withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [:] }
        var map = [String: String](minimumCapacity: 8192)
        text.enumerateLines { line, _ in
            guard let tab = line.firstIndex(of: "\t"), tab != line.startIndex else { return }
            map[unescape(String(line[..<tab]))] = unescape(String(line[line.index(after: tab)...]))
        }
        return map
    }

    static func tr(_ s: String) -> String {
        lock.lock(); let t = table; lock.unlock()
        if t.isEmpty || s.isEmpty { return s }
        if let v = t[s] { return v }
        return lookup(t, s) ?? s
    }

    /// Kalıbı çevirip `{0}`, `{1}` … yerlerine [args] yazar.
    static func trf(_ pattern: String, _ args: Any...) -> String {
        var out = tr(pattern)
        for (i, a) in args.enumerated() { out = out.replacingOccurrences(of: "{\(i)}", with: "\(a)") }
        return out
    }

    private static let TR = Locale(identifier: "tr")
    private static let SEP = " · "
    private static let SUFFIXES = [":", "…", "..."]

    private static func lookup(_ t: [String: String], _ s: String) -> String? {
        let trimmed = s.trimmingCharacters(in: .whitespaces)
        if trimmed.count != s.count && !trimmed.isEmpty {
            guard let inner = t[trimmed] ?? lookup(t, trimmed) else { return nil }
            let lead = s.prefix { $0 == " " }.count
            let trail = String(s.reversed()).prefix { $0 == " " }.count
            return String(repeating: " ", count: lead) + inner + String(repeating: " ", count: trail)
        }
        for suffix in SUFFIXES where s.count > suffix.count && s.hasSuffix(suffix) {
            if let v = t[String(s.dropLast(suffix.count))] { return v + suffix }
        }
        guard let first = s.first else { return nil }
        if first.isUppercase {
            let lower = String(first).lowercased(with: TR) + s.dropFirst()
            if let v = t[lower] { return capitalize(v) }
            if s.count > 1 && !s.contains(where: { $0.isLowercase }) && s.contains(where: { $0.isLetter }) {
                let low = s.lowercased(with: TR)
                let cap = String(low.prefix(1)).uppercased(with: TR) + low.dropFirst()
                if let v = t[cap] ?? t[low] { return v.uppercased() }
            }
        } else if first.isLowercase {
            let cap = String(first).uppercased(with: TR) + s.dropFirst()
            if let v = t[cap], let f = v.first { return String(f).lowercased() + v.dropFirst() }
        }
        // Sonda sayı ("Parça 3")
        if let m = s.range(of: "^(.*[^\\d\\s])( \\d+)$", options: .regularExpression), m == s.startIndex..<s.endIndex,
           let sp = s.lastIndex(of: " ") {
            let head = String(s[..<sp]), num = String(s[sp...])
            if let v = t["\(head) {0}"] { return v.replacingOccurrences(of: "{0}", with: num.trimmingCharacters(in: .whitespaces)) }
            let r = tr(head)
            if r != head { return r + num }
        }
        // Sonda ayraç ("Parça (2)")
        if s.hasSuffix(")"), let open = s.range(of: " (", options: .backwards) {
            let head = String(s[..<open.lowerBound])
            let inner = String(s[open.upperBound..<s.index(before: s.endIndex)])
            if !head.isEmpty && !inner.isEmpty && !inner.contains("(") && !inner.contains(")") {
                let r = tr(head)
                if r != head { return r + " (" + tr(inner) + ")" }
            }
        }
        if let colon = s.range(of: ": "), colon.lowerBound > s.startIndex, s.distance(from: colon.upperBound, to: s.endIndex) > 0 {
            let a = String(s[..<colon.lowerBound]), b = String(s[colon.upperBound...])
            let ra = tr(a), rb = tr(b)
            if ra != a || rb != b { return "\(ra): \(rb)" }
        }
        if s.contains(SEP) {
            let parts = s.components(separatedBy: SEP)
            var any = false
            let out = parts.map { p -> String in let r = tr(p); if r != p { any = true }; return r }
            if any { return out.joined(separator: SEP) }
        }
        return nil
    }

    private static func capitalize(_ v: String) -> String {
        guard let f = v.first, f.isLowercase else { return v }
        return String(f).uppercased() + v.dropFirst()
    }

    private static func unescape(_ s: String) -> String {
        guard s.contains("\\") else { return s }
        var out = ""
        var it = Array(s)
        var i = 0
        while i < it.count {
            let c = it[i]
            if c == "\\" && i + 1 < it.count {
                let n = it[i + 1]
                switch n {
                case "n": out.append("\n"); i += 2; continue
                case "t": out.append("\t"); i += 2; continue
                case "\\": out.append("\\"); i += 2; continue
                case "u":
                    if i + 5 < it.count, let v = UInt32(String(it[(i + 2)...(i + 5)]), radix: 16), let u = Unicode.Scalar(v) {
                        out.unicodeScalars.append(u); i += 6; continue
                    }
                default: out.append("\\"); out.append(n); i += 2; continue
                }
            }
            out.append(c)
            i += 1
        }
        it.removeAll()
        return out
    }
}

extension String {
    /// Kısa yol: `"Küp".tr`.
    var tr: String { I18n.tr(self) }
}
