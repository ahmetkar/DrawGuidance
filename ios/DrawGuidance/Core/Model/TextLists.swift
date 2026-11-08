import Foundation

/// Not yazılarında liste işaretleri. İmleç konumları UTF-16 birimidir (Android ve UITextView ile aynı).
enum TextLists {
    static let INDENT = "    "
    private static let MAX_LEVEL = 5
    private static let bullets = ["•", "–", "·"]
    private static let checks = ["☐", "☑"]

    struct Line: Equatable {
        var level: Int
        var marker: String
        var body: String
        var hasMarker: Bool { !marker.isEmpty }
    }

    private static let markerRegex = try! NSRegularExpression(pattern: "^(•|–|·|☐|☑|\\d{1,3}\\.|[a-z]{1,2}\\.|[ivxlc]{1,5}\\.) ")

    static func parse(_ line: String) -> Line {
        let ns = line as NSString
        var i = 0
        var spaces = 0
        while i < ns.length {
            let c = ns.character(at: i)
            if c == 0x20 { spaces += 1 } else if c == 0x09 { spaces += 4 } else { break }
            i += 1
        }
        let rest = ns.substring(from: i)
        let restNs = rest as NSString
        if let m = markerRegex.firstMatch(in: rest, range: NSRange(location: 0, length: restNs.length)) {
            let marker = restNs.substring(with: m.range)
            return Line(level: min(MAX_LEVEL, spaces / 4), marker: marker, body: restNs.substring(from: m.range.length))
        }
        return Line(level: min(MAX_LEVEL, spaces / 4), marker: "", body: rest)
    }

    static func marker(_ paragraph: TextParagraph, level: Int, index: Int, checked: Bool = false) -> String {
        switch paragraph {
        case .BULLET: return bullets[level % bullets.count] + " "
        case .CHECKLIST: return (checked ? checks[1] : checks[0]) + " "
        case .NUMBERED:
            switch level % 3 {
            case 0: return "\(index). "
            case 1: return "\(letters(index)). "
            default: return "\(roman(index)). "
            }
        default: return ""
        }
    }

    private static func letters(_ n: Int) -> String {
        var v = n
        var s = ""
        while v > 0 {
            v -= 1
            s.insert(Character(UnicodeScalar(UInt8(97 + v % 26))), at: s.startIndex)
            v /= 26
        }
        return s
    }

    private static func roman(_ n: Int) -> String {
        let values: [(Int, String)] = [(100, "c"), (90, "xc"), (50, "l"), (40, "xl"), (10, "x"), (9, "ix"), (5, "v"), (4, "iv"), (1, "i")]
        var v = min(max(n, 1), 399)
        var s = ""
        for (value, r) in values { while v >= value { s += r; v -= value } }
        return s
    }

    private static func compose(_ level: Int, _ marker: String, _ body: String) -> String {
        String(repeating: INDENT, count: level) + marker + body
    }

    private static func split(_ s: String) -> [String] { s.components(separatedBy: "\n") }

    static func apply(_ content: String, _ paragraph: TextParagraph) -> String {
        let lines = split(content).map(parse)
        if !paragraph.isList { return lines.map(\.body).joined(separator: "\n") }
        if lines.allSatisfy({ $0.body.isEmpty }) { return marker(paragraph, level: 0, index: 1) }
        let out = lines.map { line -> Line in
            if line.body.isEmpty && !line.hasMarker { return line }
            var l = line
            l.marker = marker(paragraph, level: line.level, index: 1, checked: line.marker.hasPrefix(checks[1]))
            return l
        }
        return renumber(out, paragraph).map { compose($0.level, $0.marker, $0.body) }.joined(separator: "\n")
    }

    static func normalize(_ content: String, _ paragraph: TextParagraph) -> String {
        if !paragraph.isList { return content }
        let lines = split(content).map(parse)
        return renumber(lines, paragraph).map { compose($0.level, $0.marker, $0.body) }.joined(separator: "\n")
    }

    private static func renumber(_ lines: [Line], _ paragraph: TextParagraph) -> [Line] {
        var counters = [Int](repeating: 0, count: MAX_LEVEL + 1)
        return lines.map { line in
            if !line.hasMarker {
                if !line.body.isEmpty { counters = [Int](repeating: 0, count: MAX_LEVEL + 1) }
                return line
            }
            counters[line.level] += 1
            if line.level + 1 <= MAX_LEVEL { for d in (line.level + 1)...MAX_LEVEL { counters[d] = 0 } }
            var l = line
            l.marker = marker(paragraph, level: line.level, index: counters[line.level], checked: line.marker.hasPrefix(checks[1]))
            return l
        }
    }

    struct Edit: Equatable {
        var text: String
        var cursor: Int
    }

    static func onEdit(old: String, new: String, cursor: Int, paragraph: TextParagraph) -> Edit {
        if !paragraph.isList { return Edit(text: new, cursor: cursor) }
        let n = new as NSString
        let o = old as NSString
        let inserted = n.length == o.length + 1 && cursor >= 1 && cursor <= n.length && n.character(at: cursor - 1) == 0x0A &&
            (n.substring(to: cursor - 1) + n.substring(from: cursor)) == old
        if !inserted { return normalized(new, cursor, paragraph) }
        let lineStart = lastIndexOfNewline(n, before: cursor - 2).map { $0 + 1 } ?? 0
        let previous = parse(n.substring(with: NSRange(location: lineStart, length: cursor - 1 - lineStart)))
        if !previous.hasMarker { return normalized(new, cursor, paragraph) }
        if previous.body.isBlank {
            let text = n.substring(to: lineStart) + n.substring(from: cursor)
            return normalized(text, lineStart, paragraph)
        }
        let prefix = String(repeating: INDENT, count: previous.level) + marker(paragraph, level: previous.level, index: 1)
        let text = n.substring(to: cursor) + prefix + n.substring(from: cursor)
        return normalized(text, cursor + (prefix as NSString).length, paragraph)
    }

    static func indent(_ content: String, cursor: Int, paragraph: TextParagraph, delta: Int) -> Edit {
        let ns = content as NSString
        let c = min(max(cursor, 0), ns.length)
        let lineStart = lastIndexOfNewline(ns, before: c - 1).map { $0 + 1 } ?? 0
        let lineEnd = indexOfNewline(ns, from: c) ?? ns.length
        let line = parse(ns.substring(with: NSRange(location: lineStart, length: lineEnd - lineStart)))
        let level = min(max(line.level + delta, 0), MAX_LEVEL)
        if level == line.level { return Edit(text: content, cursor: c) }
        let mk = paragraph.isList ? marker(paragraph, level: level, index: 1, checked: line.marker.hasPrefix(checks[1])) : line.marker
        let replaced = compose(level, mk, line.body)
        let text = ns.substring(to: lineStart) + replaced + ns.substring(from: lineEnd)
        let offsetInBody = (c - lineStart) - (compose(line.level, line.marker, "") as NSString).length
        let bodyLen = (line.body as NSString).length
        let newCursor = lineStart + (compose(level, mk, "") as NSString).length + min(max(offsetInBody, 0), bodyLen)
        return normalized(text, newCursor, paragraph)
    }

    private static func normalized(_ text: String, _ cursor: Int, _ paragraph: TextParagraph) -> Edit {
        let result = normalize(text, paragraph)
        let t = text as NSString
        if result == text { return Edit(text: text, cursor: min(max(cursor, 0), t.length)) }
        let c = min(max(cursor, 0), t.length)
        let lineIndex = t.substring(to: c).filter { $0 == "\n" }.count
        let fromEnd = (indexOfNewline(t, from: c) ?? t.length) - c
        let lines = split(result)
        var start = 0
        for i in 0..<lineIndex { start += (lines[i] as NSString).length + 1 }
        let end = start + (lines[lineIndex] as NSString).length
        return Edit(text: result, cursor: min(max(end - fromEnd, start), end))
    }

    /// Kotlin `lastIndexOf('\n', startIndex)`: `before` dahil geriye doğru arar.
    private static func lastIndexOfNewline(_ s: NSString, before: Int) -> Int? {
        var i = min(before, s.length - 1)
        while i >= 0 { if s.character(at: i) == 0x0A { return i }; i -= 1 }
        return nil
    }

    private static func indexOfNewline(_ s: NSString, from: Int) -> Int? {
        var i = max(from, 0)
        while i < s.length { if s.character(at: i) == 0x0A { return i }; i += 1 }
        return nil
    }
}
