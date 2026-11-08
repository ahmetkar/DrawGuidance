import Foundation

enum PageAlign: String, KotlinEnum {
    case LEFT, CENTER, RIGHT
    static var fallback: PageAlign { .CENTER }
}

enum PageNumberFormat: String, KotlinEnum {
    case NUMBER, NUMBER_OF_TOTAL
    static var fallback: PageNumberFormat { .NUMBER }
}

enum PageTextSize: String, KotlinEnum {
    case SMALL, MEDIUM, LARGE
    static var fallback: PageTextSize { .MEDIUM }
    var ratio: Float {
        switch self {
        case .SMALL: return 0.016
        case .MEDIUM: return 0.021
        case .LARGE: return 0.028
        }
    }
}

/// v40 sayfa numarası, üst ve alt bilgi.
struct PageSettings: Codable, Equatable {
    var pageNumbers = false
    var numberAlign: PageAlign = .CENTER
    var numberAtTop = false
    var numberFormat: PageNumberFormat = .NUMBER
    var header = ""
    var headerAlign: PageAlign = .CENTER
    var headerSize: PageTextSize = .MEDIUM
    var footer = ""
    var footerAlign: PageAlign = .CENTER
    var footerSize: PageTextSize = .MEDIUM

    init() {}

    var isEmpty: Bool { !pageNumbers && header.isBlank && footer.isBlank }

    func numberText(page: Int, count: Int, cover: Bool = false) -> String {
        guard let n = PageNumbering.number(page: page, cover: cover) else { return "" }
        switch numberFormat {
        case .NUMBER: return "\(n)"
        case .NUMBER_OF_TOTAL: return "\(n) / \(max(1, PageNumbering.count(pageCount: count, cover: cover)))"
        }
    }

    struct Line: Equatable {
        var top: Bool
        var align: PageAlign
        var size: PageTextSize
        var text: String
    }

    func lines(page: Int, count: Int, cover: Bool = false) -> [Line] {
        if isEmpty { return [] }
        if cover && page == 0 { return [] }
        var out: [Line] = []
        let number: String? = pageNumbers ? numberText(page: page, count: count, cover: cover) : nil
        var numberUsed = number == nil
        if !header.isBlank {
            var joined = header.trimmed
            if !numberUsed && numberAtTop && numberAlign == headerAlign { numberUsed = true; joined = "\(header.trimmed)   \(number!)" }
            out.append(Line(top: true, align: headerAlign, size: headerSize, text: joined))
        }
        if !footer.isBlank {
            var joined = footer.trimmed
            if !numberUsed && !numberAtTop && numberAlign == footerAlign { numberUsed = true; joined = "\(footer.trimmed)   \(number!)" }
            out.append(Line(top: false, align: footerAlign, size: footerSize, text: joined))
        }
        if !numberUsed, let number {
            out.append(Line(top: numberAtTop, align: numberAlign, size: numberAtTop ? headerSize : footerSize, text: number))
        }
        return out
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: AnyKey.self)
        pageNumbers = c.v("pageNumbers", false); numberAlign = c.v("numberAlign", .CENTER); numberAtTop = c.v("numberAtTop", false)
        numberFormat = c.v("numberFormat", .NUMBER); header = c.v("header", ""); headerAlign = c.v("headerAlign", .CENTER)
        headerSize = c.v("headerSize", .MEDIUM); footer = c.v("footer", ""); footerAlign = c.v("footerAlign", .CENTER)
        footerSize = c.v("footerSize", .MEDIUM)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: AnyKey.self)
        try c.e(pageNumbers, "pageNumbers", false); try c.e(numberAlign, "numberAlign", .CENTER)
        try c.e(numberAtTop, "numberAtTop", false); try c.e(numberFormat, "numberFormat", .NUMBER); try c.e(header, "header", "")
        try c.e(headerAlign, "headerAlign", .CENTER); try c.e(headerSize, "headerSize", .MEDIUM); try c.e(footer, "footer", "")
        try c.e(footerAlign, "footerAlign", .CENTER); try c.e(footerSize, "footerSize", .MEDIUM)
    }
}

/// v45 kapaklı çizgi romanda sayfa numaraları.
enum PageNumbering {
    static func number(page: Int, cover: Bool) -> Int? {
        if !cover { return page + 1 }
        if page == 0 { return nil }
        return page
    }

    static func count(pageCount: Int, cover: Bool) -> Int { cover ? max(0, pageCount - 1) : pageCount }
}

extension String {
    /// Kotlin `isBlank`.
    var isBlank: Bool { allSatisfy { $0.isWhitespace } }
    /// Kotlin `trim()`.
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
