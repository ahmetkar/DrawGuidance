import Foundation

/// v36 şablon grupları.
enum TemplateGroup: String, CaseIterable {
    case BASIC, WEBTOON, MANGA, GENRE
    var id: String { rawValue.lowercased() }
    var title: String {
        switch self {
        case .BASIC: return "Temel"
        case .WEBTOON: return "Webtoon"
        case .MANGA: return "Manga"
        case .GENRE: return "Türler"
        }
    }
}

/// v36 çizgi roman sayfa şablonu: panel düzeni, adı ve grubu. [line] panel çizgisinin kalınlık çarpanı (korkuda kalın).
/// Şablonlar özgün düzenlerdir (yaygın sayfa kurgularından esinlenen geometri; görsel içermez).
struct ComicTemplate: Identifiable {
    let id: String
    let title: String
    let group: TemplateGroup
    let tags: String
    let page: PanelPage
    var line: Float = 1
    var panelCount: Int { page.leafIds().count }
}

enum ComicTemplates {
    /// Kimlikleri sırayla veren küçük kurucu (ağaçtaki her panel ayrı kimlik).
    private final class B {
        var next = 0
        func p(round: Float = 0, bevel: Float = 0) -> PanelNode { defer { next += 1 }; return .leaf(id: next, round: round, bevel: bevel) }
        let e = PanelNode.space
        func R(_ w: Float..., gap: Float = 1, skews: [Float]? = nil, _ c: () -> [PanelNode]) -> PanelNode { split(true, w, gap, skews, c()) }
        func C(_ w: Float..., gap: Float = 1, skews: [Float]? = nil, _ c: () -> [PanelNode]) -> PanelNode { split(false, w, gap, skews, c()) }
        private func split(_ rows: Bool, _ w: [Float], _ gap: Float, _ skews: [Float]?, _ children: [PanelNode]) -> PanelNode {
            precondition(w.count == children.count)
            return .split(PanelNode.Split(rows, children, w, skews ?? [Float](repeating: 0, count: children.count - 1), gap))
        }
    }

    private static func t(_ id: String, _ title: String, _ group: TemplateGroup, _ tags: String,
                          margin: Float = PanelPage.DEFAULT_MARGIN, gutter: Float = PanelPage.DEFAULT_GUTTER, rtl: Bool = false,
                          line: Float = 1, insets: [PanelInset] = [], _ root: (B) -> PanelNode) -> ComicTemplate {
        ComicTemplate(id: id, title: title, group: group, tags: tags, page: PanelPage(root(B()), margin, gutter, rtl, insets), line: line)
    }

    static let all: [ComicTemplate] = [
        // Temel düzenler
        t("single", "Tek büyük panel", .BASIC, "tek sayfa kapak splash") { b in b.p() },
        t("two_rows", "İki panel (üst üste)", .BASIC, "iki yatay") { b in b.R(1, 1) { [b.p(), b.p()] } },
        t("two_cols", "İki panel (yan yana)", .BASIC, "iki dikey sütun") { b in b.C(1, 1) { [b.p(), b.p()] } },
        t("three_rows", "Üç panel (şerit)", .BASIC, "üç yatay şerit") { b in b.R(1, 1, 1) { [b.p(), b.p(), b.p()] } },
        t("three_big", "Üç panel (büyük üstte)", .BASIC, "üç büyük") { b in b.R(1.4, 1) { [b.p(), b.C(1, 1) { [b.p(), b.p()] }] } },
        // v37 eski "Panel düzeni"ndeki "İki + büyük" şablonlara katıldı
        t("three_big_bottom", "Üç panel (büyük altta)", .BASIC, "üç büyük alt") { b in b.R(1, 1.4) { [b.C(1, 1) { [b.p(), b.p()] }, b.p()] } },
        t("three_cols", "Üç panel (gazete şeridi)", .BASIC, "üç sütun gazete şerit") { b in b.C(1, 1, 1) { [b.p(), b.p(), b.p()] } },
        t("four_grid", "Dört panel (ızgara)", .BASIC, "dört ızgara kare") { b in
            b.R(1, 1) { [b.C(1, 1) { [b.p(), b.p()] }, b.C(1, 1) { [b.p(), b.p()] }] }
        },
        t("four_mix", "Dört panel (geniş alt)", .BASIC, "dört geniş") { b in
            b.R(1, 1, 1.25) { [b.p(), b.C(1, 1) { [b.p(), b.p()] }, b.p()] }
        },
        t("six_grid", "Altı panel (ızgara)", .BASIC, "altı ızgara") { b in
            b.R(1, 1, 1) { (0..<3).map { _ in b.C(1, 1) { [b.p(), b.p()] } } }
        },
        t("six_mix", "Altı panel (karışık)", .BASIC, "altı karışık") { b in
            b.R(1, 1.25, 1) { [b.C(1, 1, 1) { [b.p(), b.p(), b.p()] }, b.p(), b.C(1.4, 1) { [b.p(), b.p()] }] }
        },
        // Dikey webtoon: tam genişlik, geniş dikey aralıklar, kaydırılmış paneller
        t("webtoon", "Dikey webtoon", .WEBTOON, "webtoon dikey kaydırma telefon", margin: 0.025, gutter: 0.02) { b in
            b.R(1, 1.6, 0.8, 1.3, gap: 3.5) { [b.p(), b.p(), b.C(0.3, 1) { [b.e, b.p()] }, b.p()] }
        },
        t("webtoon_scene", "Webtoon geniş sahne", .WEBTOON, "webtoon sahne dikey", margin: 0.025, gutter: 0.02) { b in
            b.R(0.9, 1.8, 0.9, gap: 3) { [b.C(1, 0.35) { [b.p(), b.e] }, b.p(), b.C(0.35, 1) { [b.e, b.p()] }] }
        },
        t("webtoon_flow", "Webtoon akış", .WEBTOON, "webtoon akış dikey", margin: 0.025, gutter: 0.02) { b in
            b.R(1, 0.8, 1.2, 0.9, gap: 2.5) { [b.p(), b.C(0.2, 1, 0.2) { [b.e, b.p(), b.e] }, b.p(), b.C(1, 1) { [b.p(), b.p()] }] }
        },
        // Manga: sağdan sola okunur
        t("manga", "Manga klasik", .MANGA, "manga sağdan sola", rtl: true) { b in
            b.R(0.9, 1.1, 1) { [b.p(), b.C(1.6, 1) { [b.p(), b.p()] }, b.C(1, 1.6) { [b.p(), b.p()] }] }
        },
        t("manga_action", "Manga aksiyon", .MANGA, "manga aksiyon eğik", rtl: true) { b in
            b.R(1, 1.3, 1, skews: [0.04, -0.05]) {
                [b.C(1, 1, skews: [0.12]) { [b.p(), b.p()] }, b.p(), b.C(1, 1, 1, skews: [0.1, -0.1]) { [b.p(), b.p(), b.p()] }]
            }
        },
        t("yonkoma", "4-koma (yonkoma)", .MANGA, "manga yonkoma 4 koma dört şerit", gutter: 0.03, rtl: true) { b in
            b.C(1, 1, gap: 1.6) { (0..<2).map { _ in b.R(1, 1, 1, 1) { (0..<4).map { _ in b.p() } } } }
        },
        // Türlere göre sayfa düzenleri
        t("action", "Aksiyon", .GENRE, "aksiyon dövüş eğik darbe") { b in
            b.R(1, 1.6, 0.9, skews: [0.06, -0.08]) {
                [b.C(1, 1, skews: [0.2]) { [b.p(), b.p()] }, b.p(), b.C(1, 1, 1, skews: [-0.15, 0.15]) { [b.p(), b.p(), b.p()] }]
            }
        },
        t("comedy", "Komedi", .GENRE, "komedi mizah espri ritim") { b in
            b.R(1, 1, 1.1) { [b.C(1, 1) { [b.p(), b.p()] }, b.C(1, 1) { [b.p(), b.p()] }, b.p()] }
        },
        t("romance", "Romantizm", .GENRE, "romantizm aşk yumuşak", gutter: 0.03,
          insets: [PanelInset(id: 3, l: 0.6, t: 0.3, r: 0.95, b: 0.56, oval: true, after: 0)]) { b in
            b.R(1.6, 1) { [b.p(round: 0.03), b.C(1, 1) { [b.p(round: 0.03), b.p(round: 0.03)] }] }
        },
        t("horror", "Korku", .GENRE, "korku gerilim karanlık", margin: 0.06, line: 1.6) { b in
            b.R(1.1, 1.4, skews: [0.03]) { [b.C(1, 0.5, 1, skews: [0.04, -0.04]) { [b.p(), b.p(), b.p()] }, b.p()] }
        },
        t("fantasy", "Fantastik", .GENRE, "fantastik büyü macera sahne") { b in
            b.R(1.8, 1) { [b.p(bevel: 0.015), b.C(1, 1.3, 1) { [b.p(), b.p(), b.p()] }] }
        },
        t("scifi", "Bilim kurgu", .GENRE, "bilim kurgu uzay gelecek sinematik") { b in
            b.R(0.7, 0.7, 1.2, 0.7) { [b.p(bevel: 0.03), b.p(bevel: 0.03), b.C(1, 1) { [b.p(bevel: 0.03), b.p(bevel: 0.03)] }, b.p(bevel: 0.03)] }
        },
    ]

    private static let index: [String: ComicTemplate] = Dictionary(all.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })

    static func byId(_ id: String) -> ComicTemplate? { index[id] }

    /// Gruptaki şablonlar (nil: hepsi).
    static func inGroup(_ group: TemplateGroup?) -> [ComicTemplate] { group == nil ? all : all.filter { $0.group == group } }
}
