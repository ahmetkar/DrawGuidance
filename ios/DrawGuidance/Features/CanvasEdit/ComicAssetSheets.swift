import CoreGraphics
import SwiftUI

/// v40 hazır çizgi roman resimlerinin küçük resim önbelleği (Android `ComicThumbs`): arka planda hedef boyda çözülür.
enum ComicThumbCache {
    private static let cache: NSCache<NSString, CGImage> = {
        let c = NSCache<NSString, CGImage>()
        c.totalCostLimit = 24 * 1024 * 1024
        return c
    }()

    static func cached(_ path: String, _ maxPx: Int) -> CGImage? { cache.object(forKey: "\(maxPx):\(path)" as NSString) }

    static func load(_ path: String, _ maxPx: Int) async -> CGImage? {
        if let hit = cached(path, maxPx) { return hit }
        let image = await Task.detached(priority: .utility) { () -> CGImage? in
            guard let bytes = try? ComicAssets.bytes(path) else { return nil }
            return CanvasImageCache.decode(bytes, maxSide: maxPx)
        }.value
        if let image { cache.setObject(image, forKey: "\(maxPx):\(path)" as NSString, cost: image.width * image.height * 4) }
        return image
    }
}

/// Hazır resmin küçük resmi (Android `ComicAssetThumb`).
struct ComicAssetThumb: View {
    let path: String
    var maxPx = 192
    var crop = false
    @State private var image: CGImage?

    var body: some View {
        ZStack {
            if let image {
                Image(decorative: image, scale: 1)
                    .resizable()
                    .aspectRatio(contentMode: crop ? .fill : .fit)
            } else {
                ProgressView().controlSize(.small)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .task(id: path) { image = ComicThumbCache.cached(path, maxPx); if image == nil { image = await ComicThumbCache.load(path, maxPx) } }
    }
}

/// Yatay kayan seçim çipleri ("Tümü" isteğe bağlı).
private struct ComicChipRow<ID: Hashable>: View {
    let tag: String
    let items: [(id: ID, label: String)]
    let selected: ID?
    var allLabel: String?
    let onSelect: (ID?) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if let allLabel { chip(allLabel, selected == nil, "\(tag)_all") { onSelect(nil) } }
                ForEach(items, id: \.id) { item in
                    chip(item.label, item.id == selected, "\(tag)_\(item.id)") { onSelect(item.id) }
                }
            }
            .padding(.horizontal, 16)
        }
        .accessibilityIdentifier(tag)
    }

    private func chip(_ label: String, _ on: Bool, _ id: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            Text(label).font(.subheadline.weight(on ? .semibold : .regular)).lineLimit(1)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .foregroundStyle(on ? Color.accentColor : Color.primary)
                .background(Capsule().fill(on ? Color.accentColor.opacity(0.18) : Color.primary.opacity(0.06)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}

/// Izgara hücresi: küçük resim, altında ad, kilitliyse taç.
private struct ComicTile: View {
    let title: String
    let id: String
    let locked: Bool
    let height: CGFloat
    let thumb: AnyView
    let run: () -> Void

    var body: some View {
        Button(action: run) {
            VStack(spacing: 4) {
                thumb.frame(height: height)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.primary.opacity(0.05)))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(alignment: .topTrailing) {
                        if locked { Image(systemName: "crown.fill").font(.caption).foregroundStyle(.yellow).padding(5) }
                    }
                if !title.isEmpty { Text(title).font(.caption).lineLimit(1).foregroundStyle(.primary) }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}

private func styleLabel(_ s: ComicStyle) -> String { L(s == .MANGA ? "v40_comic_style_manga" : "v40_comic_style_comic") }

private func groupLabel(_ g: VariantGroup) -> String {
    switch g {
    case .EXPRESSION: return L("v40_comic_group_expressions")
    case .POSE: return L("v40_comic_group_poses")
    case .ACTION: return L("v40_comic_group_action")
    case .DAILY: return L("v40_comic_group_daily")
    }
}

/// v40 Karakterler penceresi (Android `CharactersSheet`): stil ve tema süzgeçleri, karakter ızgarası; karaktere dokununca
/// çeşitleri İfadeler, Pozlar, Aksiyon ve Günlük gruplarında. Önizleme Free planda da açık; kilitli karakterin çeşidini
/// eklemek "Premium gerekli" penceresini açar.
struct CharactersSheet: View {
    let onInsert: (ComicVariant) -> Void
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var sub = SubscriptionStore.shared
    @State private var style: ComicStyle?
    @State private var theme: String?
    @State private var selected: String?
    @State private var premiumOpen = false
    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 10)]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text(L("v40_comic_chars_hint")).font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 16)
                    if let c = selected.flatMap({ ComicAssets.character($0) }) { variants(c) } else { browser }
                }
                .padding(.vertical, 8)
            }
            .navigationTitle(L("characters_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { dismiss() } } }
        }
        .dockedEditorSheet()
        .accessibilityIdentifier("characters_sheet")
        .sheet(isPresented: $premiumOpen) { PremiumRequiredSheet() }
    }

    @ViewBuilder private var browser: some View {
        let lang = I18n.language
        let themes = ComicAssets.themes(style)
        let results = ComicAssets.characters(style, theme)
        Text(L("v40_comic_style")).font(.subheadline.weight(.semibold)).padding(.horizontal, 16)
        ComicChipRow(tag: "char_style", items: ComicStyle.allCases.map { (id: $0.id, label: styleLabel($0)) }, selected: style?.id,
                     allLabel: L("shape_filter_all")) { id in style = ComicStyle.allCases.first { $0.id == id }; theme = nil }
        if !themes.isEmpty {
            Text(L("v40_comic_theme")).font(.subheadline.weight(.semibold)).padding(.horizontal, 16)
            ComicChipRow(tag: "char_theme", items: themes.map { (id: $0.id, label: ComicAssets.name($0.names, lang)) }, selected: theme,
                         allLabel: L("shape_filter_all")) { theme = $0 }
        }
        Text(L("characters_count", results.count)).font(.subheadline.weight(.semibold)).padding(.horizontal, 16)
            .accessibilityIdentifier("char_count")
        if results.isEmpty { Text(L("characters_empty")).foregroundStyle(.secondary).padding(.horizontal, 16) }
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(results) { c in
                ComicTile(title: ComicAssets.name(c.names, lang), id: "char_\(c.id)",
                          locked: ComicPremiumRules.characterFeature(c).map { !sub.allows($0, .COMIC) } ?? false,
                          height: 96, thumb: AnyView(ComicAssetThumb(path: c.cover.thumb))) { selected = c.id }
            }
        }
        .padding(.horizontal, 16)
        .accessibilityIdentifier("char_results")
    }

    @ViewBuilder private func variants(_ c: ComicCharacter) -> some View {
        let locked = ComicPremiumRules.characterFeature(c).map { !sub.allows($0, .COMIC) } ?? false
        HStack(spacing: 10) {
            Button { selected = nil } label: { Label(L("characters_title"), systemImage: "chevron.backward") }
                .accessibilityIdentifier("char_back")
            Text(ComicAssets.name(c.names, I18n.language)).font(.headline).accessibilityIdentifier("char_selected_name")
        }
        .padding(.horizontal, 16)
        ForEach(VariantGroup.allCases, id: \.self) { g in
            let list = c.variants(g)
            if !list.isEmpty {
                Text("\(groupLabel(g)) (\(list.count))").font(.subheadline.weight(.semibold)).padding(.horizontal, 16)
                    .accessibilityIdentifier("char_group_\(g.id)")
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(list) { v in
                        ComicTile(title: "", id: "charvar_\(c.id)_\(v.id)", locked: locked, height: 104,
                                  thumb: AnyView(ComicAssetThumb(path: v.thumb))) {
                            if locked { premiumOpen = true } else { onInsert(v); dismiss() }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

/// v41 "Mekân arka planı ekle" penceresi (Android `LocationBackgroundsSheet`): Manga / Çizgi Roman, tema paketi ve alt
/// kategori süzgeçleri. Dokununca yerleştirilir: sayfada panel varsa bir panele bırakılır ya da bütün sayfayı kaplar.
struct LocationBackgroundsSheet: View {
    let onPick: (ComicBackground) -> Void
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var sub = SubscriptionStore.shared
    @State private var style: ComicStyle = .COMIC
    @State private var category: String?
    @State private var theme: String?
    @State private var premiumOpen = false
    private let columns = [GridItem(.adaptive(minimum: 140), spacing: 10)]

    var body: some View {
        let lang = I18n.language
        let themes = ComicAssets.themesFor(.BACKGROUND, style)
        let categories = ComicAssets.bgCategories(style, theme)
        let list = ComicAssets.backgrounds(style, category, theme)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text(L("v41_loc_hint")).font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 16)
                    ComicChipRow(tag: "loc_style", items: ComicStyle.allCases.map { (id: $0.id, label: styleLabel($0)) }, selected: style.id) { id in
                        style = ComicStyle.allCases.first { $0.id == id } ?? .COMIC; category = nil; theme = nil
                    }
                    if !themes.isEmpty {
                        Text(L("v40_comic_theme")).font(.subheadline.weight(.semibold)).padding(.horizontal, 16)
                        ComicChipRow(tag: "loc_theme", items: themes.map { (id: $0.id, label: ComicAssets.name($0.names, lang)) }, selected: theme,
                                     allLabel: L("shape_filter_all")) { theme = $0; category = nil }
                    }
                    ComicChipRow(tag: "loc_cat", items: categories.map { (id: $0.id, label: ComicAssets.name($0.names, lang)) }, selected: category,
                                 allLabel: L("shape_filter_all")) { category = $0 }
                    if list.isEmpty { Text(L("characters_empty")).foregroundStyle(.secondary).padding(.horizontal, 16) }
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(list) { b in
                            let locked = ComicPremiumRules.backgroundFeature(b).map { !sub.allows($0, .COMIC) } ?? false
                            ComicTile(title: ComicAssets.name(b.names, lang), id: "loc_\(b.id)", locked: locked, height: 84,
                                      thumb: AnyView(ComicAssetThumb(path: b.thumb, maxPx: 256, crop: true))) {
                                if locked { premiumOpen = true } else { onPick(b); dismiss() }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .accessibilityIdentifier("loc_list")
                }
                .padding(.vertical, 8)
            }
            .navigationTitle(L("v41_loc_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { dismiss() } } }
        }
        .dockedEditorSheet()
        .accessibilityIdentifier("locations_sheet")
        .sheet(isPresented: $premiumOpen) { PremiumRequiredSheet() }
    }
}

/// v42 hazır efekt ya da nesne resimleri (Android `ComicPropsBrowser`): stil (yalnız efektlerde), tema paketi ve kategori.
struct ComicPropsBrowser: View {
    let kind: ComicAssetKind
    let tag: String
    let onPick: (ComicProp) -> Void
    @ObservedObject private var sub = SubscriptionStore.shared
    @State private var style: ComicStyle = .COMIC
    /// v50 efekt ve nesnelerde tema paketleri gösterilmez (yalnız içerik kategorileri; Android ile aynı).
    private let theme: String? = nil
    @State private var category: String?
    @State private var premiumOpen = false
    private let columns = [GridItem(.adaptive(minimum: 84), spacing: 10)]

    var body: some View {
        let lang = I18n.language
        let categories = ComicAssets.propCategories(kind, style, theme)
        let list = ComicAssets.props(kind, style, category, theme)
        let cats = (kind == .EFFECT ? ComicAssets.catalog.effectCategories : ComicAssets.catalog.objectCategories)
        let catNames = Dictionary(cats.map { (id: $0.id, label: ComicAssets.name($0.names, lang)) }, uniquingKeysWith: { a, _ in a })
        VStack(alignment: .leading, spacing: 10) {
            // v43 nesneler stile ayrılmaz
            if kind != .OBJECT {
                ComicChipRow(tag: "\(tag)_style", items: ComicStyle.allCases.map { (id: $0.id, label: styleLabel($0)) }, selected: style.id) { id in
                    style = ComicStyle.allCases.first { $0.id == id } ?? .COMIC; category = nil
                }
            }
            if categories.count > 1 {
                ComicChipRow(tag: "\(tag)_cat", items: categories.map { (id: $0.id, label: ComicAssets.name($0.names, lang)) }, selected: category,
                             allLabel: L("shape_filter_all")) { category = $0 }
            }
            Text(L("characters_count", list.count)).font(.subheadline.weight(.semibold)).padding(.horizontal, 16)
                .accessibilityIdentifier("\(tag)_count")
            if list.isEmpty { Text(L("characters_empty")).foregroundStyle(.secondary).padding(.horizontal, 16) }
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(list) { p in
                    let locked = ComicPremiumRules.propFeature(kind, p).map { !sub.allows($0, .COMIC) } ?? false
                    ComicTile(title: catNames[p.category] ?? "", id: "\(tag)_item_\(p.id)", locked: locked, height: 72,
                              thumb: AnyView(ComicAssetThumb(path: p.thumb, maxPx: 160))) {
                        if locked { premiumOpen = true } else { onPick(p) }
                    }
                }
            }
            .padding(.horizontal, 16)
        }
        .accessibilityIdentifier(tag)
        .sheet(isPresented: $premiumOpen) { PremiumRequiredSheet() }
    }
}

/// v42 "Nesne ekle" penceresi (Android `ComicObjectsSheet`).
struct ComicObjectsSheet: View {
    let onPick: (ComicProp) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text(L("v42_obj_hint")).font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 16)
                    ComicPropsBrowser(kind: .OBJECT, tag: "obj") { p in onPick(p); dismiss() }
                }
                .padding(.vertical, 8)
            }
            .navigationTitle(L("v42_obj_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { dismiss() } } }
        }
        .dockedEditorSheet()
        .accessibilityIdentifier("objects_sheet")
    }
}

/// v41 seçilen mekân arka planını yerleştirme katmanı (Android `LocationPlaceOverlay`): sayfadaki paneller kesik çizgiyle
/// işaretlenir; parmak basılıyken altındaki panel vurgulanır ve arka plan orada önizlenir. Parmak bir panelde kalkınca
/// arka plan o panele sığar; panellerin dışında kalkarsa ya da "Bütün sayfa"ya basılırsa sayfayı kaplar.
struct LocationPlaceOverlay: View {
    let background: ComicBackground
    let panels: [Int: FRect]
    @ObservedObject var viewport: ViewportState
    let onPlace: (Int?) -> Void
    let onCancel: () -> Void
    @State private var hover: P?
    @State private var thumb: CGImage?

    private func leafAt(_ p: P) -> Int? {
        panels.first { p.x >= $0.value.left && p.x <= $0.value.right && p.y >= $0.value.top && p.y <= $0.value.bottom }?.key
    }

    var body: some View {
        ZStack(alignment: .top) {
            Canvas { g, _ in
                let hovered = hover.flatMap { leafAt($0) }
                for (leaf, r) in panels {
                    let s = viewport.toScreen(r)
                    let rect = CGRect(x: CGFloat(s.left), y: CGFloat(s.top), width: CGFloat(s.width), height: CGFloat(s.height))
                    if leaf == hovered {
                        if let thumb {
                            let crop = Backdrop.cropRect(thumb.width, thumb.height, Float(rect.width / max(rect.height, 1)))
                            if let part = thumb.cropping(to: CGRect(x: crop.left, y: crop.top, width: crop.width, height: crop.height)) {
                                var layer = g
                                layer.opacity = 0.85
                                layer.draw(Image(decorative: part, scale: 1), in: rect)
                            }
                        }
                        g.fill(Path(rect), with: .color(.accentColor.opacity(0.18)))
                    }
                    g.stroke(Path(rect), with: .color(.accentColor),
                             style: StrokeStyle(lineWidth: leaf == hovered ? 4 : 2, dash: [14, 9]))
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in hover = viewport.toCanvas(P(Float(v.location.x), Float(v.location.y))) }
                    .onEnded { v in
                        let last = viewport.toCanvas(P(Float(v.location.x), Float(v.location.y)))
                        hover = nil
                        onPlace(leafAt(last))
                    }
            )
            .accessibilityIdentifier("loc_place_overlay")

            VStack(alignment: .leading, spacing: 8) {
                Text(ComicAssets.name(background.names, I18n.language)).font(.subheadline.weight(.semibold))
                Text(L("v41_loc_place_hint")).font(.caption).foregroundStyle(.secondary)
                HStack(spacing: 8) {
                    Button { onPlace(nil) } label: { Label(L("v41_loc_whole_page"), systemImage: "arrow.up.left.and.arrow.down.right") }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("loc_whole_page")
                    Button(role: .cancel, action: onCancel) { Label(L("cancel_button"), systemImage: "xmark") }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("loc_cancel")
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .frame(maxWidth: 520, alignment: .leading)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
            .padding(10)
            .accessibilityIdentifier("loc_place_bar")
        }
        .task(id: background.id) { thumb = await ComicThumbCache.load(background.thumb, 256) }
    }
}
