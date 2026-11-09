import Foundation

// v46 periyodik tablonun 118 elementi (Android `PeriodicTable.kt`).

/// Element grubu (hücre rengi ve açıklaması).
enum ElementKind: CaseIterable {
    case ALKALI, ALKALINE, TRANSITION, POST_TRANSITION, METALLOID, NONMETAL, HALOGEN, NOBLE, LANTHANIDE, ACTINIDE

    var title: String {
        switch self {
        case .ALKALI: return "Alkali metal"
        case .ALKALINE: return "Toprak alkali metal"
        case .TRANSITION: return "Geçiş metali"
        case .POST_TRANSITION: return "Zayıf metal"
        case .METALLOID: return "Yarı metal"
        case .NONMETAL: return "Ametal"
        case .HALOGEN: return "Halojen"
        case .NOBLE: return "Soy gaz"
        case .LANTHANIDE: return "Lantanit"
        case .ACTINIDE: return "Aktinit"
        }
    }

    var color: String {
        switch self {
        case .ALKALI: return "#FFCDD2"
        case .ALKALINE: return "#FFE0B2"
        case .TRANSITION: return "#FFF9C4"
        case .POST_TRANSITION: return "#DCEDC8"
        case .METALLOID: return "#C8E6C9"
        case .NONMETAL: return "#E3F2E1"
        case .HALOGEN: return "#B2EBF2"
        case .NOBLE: return "#D1C4E9"
        case .LANTHANIDE: return "#F8BBD0"
        case .ACTINIDE: return "#E1BEE7"
        }
    }
}

/// Periyodik tablo elementi.
struct PtElement {
    let z: Int
    let symbol: String
    let name: String
    let mass: Double
    let kind: ElementKind
    let stable: Bool

    var shells: [Int] { PeriodicTable.shells(z) }
    /// Kütle numarası (Java Math.round: yarıda yukarı).
    var massNumber: Int { Int((mass + 0.5).rounded(.down)) }
    var neutrons: Int { massNumber - z }
    var period: Int { shells.count }
}

enum PeriodicTable {
    private static let raw = [
        "H|Hidrojen|1.008", "He|Helyum|4.0026", "Li|Lityum|6.94", "Be|Berilyum|9.0122", "B|Bor|10.81",
        "C|Karbon|12.011", "N|Azot|14.007", "O|Oksijen|15.999", "F|Flor|18.998", "Ne|Neon|20.180",
        "Na|Sodyum|22.990", "Mg|Magnezyum|24.305", "Al|Alüminyum|26.982", "Si|Silisyum|28.085", "P|Fosfor|30.974",
        "S|Kükürt|32.06", "Cl|Klor|35.45", "Ar|Argon|39.948", "K|Potasyum|39.098", "Ca|Kalsiyum|40.078",
        "Sc|Skandiyum|44.956", "Ti|Titanyum|47.867", "V|Vanadyum|50.942", "Cr|Krom|51.996", "Mn|Mangan|54.938",
        "Fe|Demir|55.845", "Co|Kobalt|58.933", "Ni|Nikel|58.693", "Cu|Bakır|63.546", "Zn|Çinko|65.38",
        "Ga|Galyum|69.723", "Ge|Germanyum|72.630", "As|Arsenik|74.922", "Se|Selenyum|78.971", "Br|Brom|79.904",
        "Kr|Kripton|83.798", "Rb|Rubidyum|85.468", "Sr|Stronsiyum|87.62", "Y|İtriyum|88.906", "Zr|Zirkonyum|91.224",
        "Nb|Niyobyum|92.906", "Mo|Molibden|95.95", "Tc|Teknesyum|98", "Ru|Rutenyum|101.07", "Rh|Rodyum|102.91",
        "Pd|Paladyum|106.42", "Ag|Gümüş|107.87", "Cd|Kadmiyum|112.41", "In|İndiyum|114.82", "Sn|Kalay|118.71",
        "Sb|Antimon|121.76", "Te|Tellür|127.60", "I|İyot|126.90", "Xe|Ksenon|131.29", "Cs|Sezyum|132.91",
        "Ba|Baryum|137.33", "La|Lantan|138.91", "Ce|Seryum|140.12", "Pr|Praseodim|140.91", "Nd|Neodim|144.24",
        "Pm|Prometyum|145", "Sm|Samaryum|150.36", "Eu|Evropiyum|151.96", "Gd|Gadolinyum|157.25", "Tb|Terbiyum|158.93",
        "Dy|Disprosyum|162.50", "Ho|Holmiyum|164.93", "Er|Erbiyum|167.26", "Tm|Tulyum|168.93", "Yb|İterbiyum|173.05",
        "Lu|Lutesyum|174.97", "Hf|Hafniyum|178.49", "Ta|Tantal|180.95", "W|Volfram|183.84", "Re|Renyum|186.21",
        "Os|Osmiyum|190.23", "Ir|İridyum|192.22", "Pt|Platin|195.08", "Au|Altın|196.97", "Hg|Cıva|200.59",
        "Tl|Talyum|204.38", "Pb|Kurşun|207.2", "Bi|Bizmut|208.98", "Po|Polonyum|209", "At|Astatin|210",
        "Rn|Radon|222", "Fr|Fransiyum|223", "Ra|Radyum|226", "Ac|Aktinyum|227", "Th|Toryum|232.04",
        "Pa|Protaktinyum|231.04", "U|Uranyum|238.03", "Np|Neptünyum|237", "Pu|Plütonyum|244", "Am|Amerikyum|243",
        "Cm|Küriyum|247", "Bk|Berkelyum|247", "Cf|Kaliforniyum|251", "Es|Aynştaynyum|252", "Fm|Fermiyum|257",
        "Md|Mendelevyum|258", "No|Nobelyum|259", "Lr|Lavrensiyum|262", "Rf|Rutherfordiyum|267", "Db|Dubniyum|268",
        "Sg|Seaborgiyum|269", "Bh|Bohriyum|270", "Hs|Hassiyum|277", "Mt|Meitneriyum|278", "Ds|Darmstadtiyum|281",
        "Rg|Röntgenyum|282", "Cn|Kopernikyum|285", "Nh|Nihonyum|286", "Fl|Flerovyum|289", "Mc|Moskovyum|290",
        "Lv|Livermoryum|293", "Ts|Tenesin|294", "Og|Oganesson|294",
    ]

    private static func stable(_ z: Int) -> Bool { z != 43 && z != 61 && z <= 83 }

    private static func kind(_ z: Int, _ s: String) -> ElementKind {
        if (57...71).contains(z) { return .LANTHANIDE }
        if (89...103).contains(z) { return .ACTINIDE }
        if ["He", "Ne", "Ar", "Kr", "Xe", "Rn", "Og"].contains(s) { return .NOBLE }
        if ["F", "Cl", "Br", "I", "At", "Ts"].contains(s) { return .HALOGEN }
        if ["Li", "Na", "K", "Rb", "Cs", "Fr"].contains(s) { return .ALKALI }
        if ["Be", "Mg", "Ca", "Sr", "Ba", "Ra"].contains(s) { return .ALKALINE }
        if ["B", "Si", "Ge", "As", "Sb", "Te"].contains(s) { return .METALLOID }
        if ["H", "C", "N", "O", "P", "S", "Se"].contains(s) { return .NONMETAL }
        if ["Al", "Ga", "In", "Sn", "Tl", "Pb", "Bi", "Po", "Nh", "Fl", "Mc", "Lv"].contains(s) { return .POST_TRANSITION }
        return .TRANSITION
    }

    static let all: [PtElement] = raw.enumerated().map { i, r in
        let p = r.split(separator: "|").map(String.init)
        return PtElement(z: i + 1, symbol: p[0], name: p[1], mass: Double(p[2])!, kind: kind(i + 1, p[0]), stable: stable(i + 1))
    }
    private static let bySymbol: [String: PtElement] = Dictionary(all.map { ($0.symbol.lowercased(), $0) }, uniquingKeysWith: { a, _ in a })
    private static let byName: [String: PtElement] = Dictionary(all.map { (trLower($0.name), $0) }, uniquingKeysWith: { a, _ in a })

    /// Türkçe küçük harf (İ → i, I → ı).
    static func trLower(_ s: String) -> String { s.lowercased(with: Locale(identifier: "tr")) }

    static subscript(_ z: Int) -> PtElement? { z >= 1 && z <= all.count ? all[z - 1] : nil }

    /// Sembol, Türkçe ad ya da atom numarası → element.
    static func find(_ text: String) -> PtElement? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return nil }
        if let z = Int(t) { return self[z] }
        return bySymbol[t.lowercased()] ?? byName[trLower(t)]
    }

    private static let order: [(Int, Int)] = [
        (1, 0), (2, 0), (2, 1), (3, 0), (3, 1), (4, 0), (3, 2), (4, 1), (5, 0), (4, 2), (5, 1), (6, 0), (4, 3), (5, 2),
        (6, 1), (7, 0), (5, 3), (6, 2), (7, 1),
    ]

    private static let exceptions: [Int: (from: Int, to: Int, count: Int)] = [
        24: (4, 3, 1), 29: (4, 3, 1),
        41: (5, 4, 1), 42: (5, 4, 1), 44: (5, 4, 1), 45: (5, 4, 1), 46: (5, 4, 2), 47: (5, 4, 1),
        78: (6, 5, 1), 79: (6, 5, 1),
        57: (4, 5, 1), 58: (4, 5, 1), 64: (4, 5, 1),
        89: (5, 6, 1), 90: (5, 6, 2), 91: (5, 6, 1), 92: (5, 6, 1), 93: (5, 6, 1), 96: (5, 6, 1),
    ]

    /// Katmanlardaki elektron sayıları (K'dan dışa).
    static func shells(_ z: Int) -> [Int] {
        var count = [Int](repeating: 0, count: 8)
        var left = z
        for (n, l) in order {
            if left <= 0 { break }
            let take = min(left, 2 * (2 * l + 1))
            count[n] += take
            left -= take
        }
        if let e = exceptions[z] { count[e.from] -= e.count; count[e.to] += e.count }
        let last = (1...7).reversed().first { count[$0] > 0 } ?? 1
        return (1...last).map { count[$0] }
    }
}
