import SwiftUI
import UIKit

/// Seçicideki bir kategori (Android `ShapeRegistry.PickerCategory`).
struct PickerCategory: Identifiable {
    let id: String
    let title: String
    let section: String
    let items: [String]
}

enum ShapePicker {
    static let FLOWCHART = "gen_flowchart"

    /// Tuval türünün seçici kategorileri (Android `ShapeRegistry.categories` kütüphane bölümü).
    static func categories(_ kind: CanvasKind) -> [PickerCategory] {
        if kind == .SIMPLE_NOTES { return categories(.NOTES) }
        // Yerleşik temel şekil kategorileri önce; not tuvalinde grafikler ve eğriler
        let builtins = ShapeCategory.forKind(kind).filter { $0 != .NOTE_GRAPHS }.map {
            PickerCategory(id: $0.rawValue, title: L($0.titleKey), section: "Temel", items: $0.shapes.map(\.rawValue))
        }
        var graphs: [PickerCategory] = []
        if kind == .NOTES {
            graphs.append(PickerCategory(id: "graphs", title: "Grafikler", section: "Grafik", items: GraphShapes.graphItems))
            graphs.append(PickerCategory(id: "curves", title: "Grafik eğrileri", section: "Grafik", items: GraphShapes.curveItems))
        }
        return builtins + graphs + libraryCategories(kind)
    }

    private static func libraryCategories(_ kind: CanvasKind) -> [PickerCategory] {
        let lib = ShapeLibrary.shared
        func cat(_ c: LibCategory, _ fallback: String) -> PickerCategory {
            PickerCategory(id: c.id, title: c.title, section: c.section.isEmpty ? fallback : c.section,
                           items: c.shapes.map { ShapeLibrary.PREFIX + $0.id })
        }
        switch kind {
        case .ART, .SIMPLE_ART:
            // v42 resim tuvallerinde yalnız genel şekiller; basit resimde akış şeması yok
            return lib.categories
                .filter { $0.id.hasPrefix("gen_") && (kind == .ART || $0.id != FLOWCHART) }
                .map { cat($0, "Temel") }
        case .KIDS:
            return lib.forAudience(.KIDS).filter(\.listed).map { cat($0, "Çocuklar") }
        default:
            let audience: Audience = kind.isNotes ? .NOTES : .ART
            return lib.categories
                .filter { $0.listed && ($0.audience == audience || $0.audience == .BOTH) }
                .map { cat($0, "Diğer") }
        }
    }

    private static let thumbs = NSCache<NSString, UIImage>()

    /// Seçicideki küçük resim (koyu kalemle, dolgusuz).
    static func thumbnail(_ id: String, side: CGFloat = 64) -> UIImage? {
        let key = "\(id)@\(side)" as NSString
        if let img = thumbs.object(forKey: key) { return img }
        let pad: Float = 6
        let s = Float(side)
        let start: P, end: P
        if ShapeFactory.fit(id) == .LINE {
            start = P(pad, s / 2); end = P(s - pad, s / 2)
        } else {
            let a = ShapeFactory.fit(id) == .SQUARE ? 1 : ShapeFactory.aspect(id)
            let w = a >= 1 ? s - 2 * pad : (s - 2 * pad) * a
            let h = a >= 1 ? (s - 2 * pad) / a : s - 2 * pad
            start = P((s - w) / 2, (s - h) / 2); end = P((s + w) / 2, (s + h) / 2)
        }
        guard let stroke = ShapeFactory.fromPaths(id, ShapeFactory.paths(id, start, end),
                                                  style: ShapeStyle(strokeColorHex: "#2A2A33", strokeWidth: 1.2),
                                                  graph: nil, id: "thumb-\(id)", order: 0) else { return nil }
        let img = UIGraphicsImageRenderer(size: CGSize(width: side, height: side)).image { r in
            ShapeRenderer.draw(r.cgContext, stroke, cache: false)
        }
        thumbs.setObject(img, forKey: key)
        return img
    }
}

/// Şekil seçici: kategoriler sekmeli, şekiller ızgarada (Android `ShapePickerModal` sadeleştirilmiş).
struct ShapePickerView: View {
    let kind: CanvasKind
    let onPick: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var selected: String?
    @State private var query = ""

    private var cats: [PickerCategory] { ShapePicker.categories(kind) }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(cats) { c in
                            Button(c.title) { selected = c.id }
                                .buttonStyle(.bordered)
                                .tint(c.id == current?.id ? .accentColor : .secondary)
                                .accessibilityIdentifier("shape_cat_\(c.id)")
                        }
                    }.padding(.horizontal, 12).padding(.vertical, 8)
                }
                .accessibilityIdentifier("shape_categories")
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 10)], spacing: 10) {
                        ForEach(items, id: \.self) { id in
                            Button {
                                onPick(id)
                                dismiss()
                            } label: {
                                VStack(spacing: 4) {
                                    if let img = ShapePicker.thumbnail(id) {
                                        Image(uiImage: img).resizable().scaledToFit().frame(width: 56, height: 56)
                                    }
                                    Text(ShapeFactory.title(id))
                                        .font(.caption2).lineLimit(2).multilineTextAlignment(.center)
                                }
                                .frame(maxWidth: .infinity, minHeight: 88)
                                .background(RoundedRectangle(cornerRadius: 10).fill(Color(white: 0.96)))
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("shape_\(id)")
                        }
                    }.padding(12)
                }
            }
            .searchable(text: $query)
            .navigationTitle(LocalizedStringKey("shapes.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(LocalizedStringKey("common.close")) { dismiss() } } }
        }
    }

    private var current: PickerCategory? { cats.first { $0.id == selected } ?? cats.first }

    /// Arama tüm kategorilerde ad ve etiketlerde yapılır (Android `ShapePickerFilter`).
    private var items: [String] {
        let q = query.trimmed.lowercased(with: Locale(identifier: "tr"))
        if q.isEmpty { return current?.items ?? [] }
        var seen = Set<String>()
        return cats.flatMap(\.items).filter { id in
            guard seen.insert(id).inserted else { return false }
            let tags = ShapeLibrary.shared.find(id)?.tags ?? []
            let text = ([ShapeFactory.title(id)] + tags).joined(separator: " ").lowercased(with: Locale(identifier: "tr"))
            return text.contains(q)
        }
    }
}
