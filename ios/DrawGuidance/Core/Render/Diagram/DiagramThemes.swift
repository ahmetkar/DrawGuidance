import Foundation

/// Diyagram renk temaları (Android `DiagramThemes`): kutular sırayla temanın renklerini alır; boş kimlik klasik
/// (kalem rengiyle çizgiler, dolgusuz). v28 editoryal temalar: kağıt zemin, mürekkep çizgiler, tek vurgu rengi.
enum DiagramThemes {
    /// Editoryal renk dizgesi (ARGB onaltılık).
    struct Editorial {
        let paper: String, ink: String, muted: String, soft: String, rule: String, accent: String, link: String
        /// v29 saydam zemin: kağıt kartı ve ok yazısı maskeleri çizilmez, kutular dolgusuz.
        var clear = false
    }

    struct Theme {
        let id: String
        let title: String
        let fills: [String]
        let text: String?
        let edge: String?
        var stroke: String? = nil
        var shadow = true
        var editorial: Editorial? = nil

        func fill(_ index: Int) -> String? { (fills.isEmpty || editorial != nil) ? nil : fills[index % fills.count] }
    }

    static let EDITORIAL = "editorial"
    static let EDITORIAL_DARK = "editorial_dark"
    static let EDITORIAL_CLEAR = "editorial_clear"

    static let editorialLight = Editorial(paper: "#FAF8F4", ink: "#1C1B19", muted: "#6E6A63", soft: "#EFEBE4", rule: "#D8D2C8",
                                          accent: "#D9572B", link: "#2F6BD8")
    static let editorialDark = Editorial(paper: "#171614", ink: "#EDEAE4", muted: "#A29D94", soft: "#24221F", rule: "#3D3934",
                                         accent: "#F0784A", link: "#6C9BF0")
    static let editorialClear = Editorial(paper: editorialLight.paper, ink: editorialLight.ink, muted: editorialLight.muted,
                                          soft: editorialLight.soft, rule: editorialLight.rule, accent: editorialLight.accent,
                                          link: editorialLight.link, clear: true)

    static let all: [Theme] = [
        Theme(id: EDITORIAL, title: "Editoryal", fills: [editorialLight.paper, editorialLight.soft, editorialLight.accent, editorialLight.link],
              text: editorialLight.ink, edge: editorialLight.ink, stroke: editorialLight.ink, shadow: false, editorial: editorialLight),
        Theme(id: EDITORIAL_DARK, title: "Editoryal koyu", fills: [editorialDark.paper, editorialDark.soft, editorialDark.accent, editorialDark.link],
              text: editorialDark.ink, edge: editorialDark.ink, stroke: editorialDark.rule, shadow: false, editorial: editorialDark),
        Theme(id: EDITORIAL_CLEAR, title: "Editoryal saydam", fills: ["#00FFFFFF", editorialClear.ink, editorialClear.accent, editorialClear.link],
              text: editorialClear.ink, edge: editorialClear.ink, stroke: editorialClear.ink, shadow: false, editorial: editorialClear),
        Theme(id: "", title: "Klasik", fills: [], text: nil, edge: nil, shadow: false),
        Theme(id: "pastel", title: "Pastel", fills: ["#FFE0B2", "#C8E6C9", "#BBDEFB", "#F8BBD0", "#D1C4E9", "#FFF59D"], text: "#263238", edge: "#546E7A"),
        Theme(id: "vivid", title: "Canlı", fills: ["#EF5350", "#42A5F5", "#66BB6A", "#FFA726", "#AB47BC", "#26A69A"], text: "#FFFFFF", edge: "#37474F"),
        Theme(id: "ocean", title: "Okyanus", fills: ["#E1F5FE", "#B3E5FC", "#81D4FA", "#4FC3F7", "#29B6F6"], text: "#01579B", edge: "#0277BD"),
        Theme(id: "forest", title: "Orman", fills: ["#E8F5E9", "#C8E6C9", "#A5D6A7", "#81C784", "#66BB6A"], text: "#1B5E20", edge: "#2E7D32"),
        Theme(id: "sunset", title: "Gün batımı", fills: ["#FFF3E0", "#FFE0B2", "#FFCC80", "#FFAB91", "#F8BBD0"], text: "#BF360C", edge: "#D84315"),
        Theme(id: "chalk", title: "Tebeşir", fills: ["#2F3E46", "#354F52", "#3D5A5E", "#52796F"], text: "#F4F1DE", edge: "#2F3E46", stroke: "#F4F1DE"),
        Theme(id: "mono", title: "Sade", fills: ["#ECEFF1", "#CFD8DC", "#F5F5F5"], text: "#263238", edge: "#455A64"),
    ]

    static func find(_ id: String) -> Theme? { all.first { $0.id == id && !$0.id.isEmpty } ?? clearVariant(id) }

    /// Tema editoryal mi.
    static func isEditorial(_ id: String) -> Bool { id == EDITORIAL || id == EDITORIAL_DARK || id == EDITORIAL_CLEAR || find(id)?.editorial != nil }

    // v40 zeminli / zeminsiz (saydam) diyagram
    static let CLEAR_SUFFIX = "_clear"

    static func hasBackground(_ id: String) -> Bool { !id.hasSuffix(CLEAR_SUFFIX) }
    static func baseOf(_ id: String) -> String { id.hasSuffix(CLEAR_SUFFIX) ? String(id.dropLast(CLEAR_SUFFIX.count)) : id }
    static func withBackground(_ id: String, _ background: Bool) -> String { background ? baseOf(id) : baseOf(id) + CLEAR_SUFFIX }

    private static let lock = NSLock()
    private static var clearCache: [String: Theme] = [:]

    /// Zeminsiz tema: editoryalde kağıt kartı ve maskeler yok; renkli temalarda kutular dolgusuz, kenar ve yazı kenar rengiyle.
    private static func clearVariant(_ id: String) -> Theme? {
        if !id.hasSuffix(CLEAR_SUFFIX) || id == CLEAR_SUFFIX { return nil }
        lock.lock(); defer { lock.unlock() }
        if let t = clearCache[id] { return t }
        guard let base = all.first(where: { $0.id == baseOf(id) && !$0.id.isEmpty }) else { return nil }
        let theme: Theme
        if let e = base.editorial {
            var clear = e
            clear.clear = true
            theme = Theme(id: id, title: base.title, fills: base.fills, text: base.text, edge: base.edge, stroke: base.stroke, shadow: false, editorial: clear)
        } else {
            let line = base.stroke ?? base.edge ?? base.text
            theme = Theme(id: id, title: base.title, fills: [], text: line, edge: base.edge ?? line, stroke: line, shadow: false)
        }
        clearCache[id] = theme
        return theme
    }
}

/// v40 diyagram yazılarının okunurluğu (WCAG 2.x; Android `DiagramContrast`).
enum DiagramContrast {
    static let MIN_RATIO = 4.5

    private static func channel(_ c: UInt32) -> Double {
        let s = Double(c) / 255
        return s <= 0.03928 ? s / 12.92 : Foundation.pow((s + 0.055) / 1.055, 2.4)
    }

    /// Göreli açıklık (0 siyah … 1 beyaz).
    static func luminance(_ argb: UInt32) -> Double {
        0.2126 * channel(argb >> 16 & 0xFF) + 0.7152 * channel(argb >> 8 & 0xFF) + 0.0722 * channel(argb & 0xFF)
    }

    /// İki rengin karşıtlık oranı (1..21).
    static func ratio(_ a: UInt32, _ b: UInt32) -> Double {
        let la = luminance(a), lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /// [fill] üstünde okunur yazı rengi (yazının saydamlığı korunur).
    static func readable(_ text: UInt32, _ fill: UInt32?, fillAlpha: Float = 1) -> UInt32 {
        guard let fill else { return text }
        let alpha = Float(fill >> 24) / 255 * fillAlpha
        if alpha < 0.5 { return text }
        let opaqueFill = fill | 0xFF00_0000
        let opaqueText = text | 0xFF00_0000
        if ratio(opaqueText, opaqueFill) >= MIN_RATIO { return text }
        let best: UInt32 = ratio(0xFF00_0000, opaqueFill) >= ratio(0xFFFF_FFFF, opaqueFill) ? 0x000000 : 0xFFFFFF
        return (text & 0xFF00_0000) | best
    }
}
