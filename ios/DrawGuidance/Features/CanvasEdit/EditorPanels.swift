import PhotosUI
import SwiftUI

/// Birleşik kaydetme penceresi (Android `UnifiedSaveSheet`): resim (PNG/JPEG, ölçek, tüm sayfalar), PDF ve CBZ; bitince paylaşma.
struct SaveSheet: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var images: ImageController
    @ObservedObject var v40: V40ExportController
    @Environment(\.dismiss) private var dismiss
    @State private var tab: SaveTab = .IMAGE
    @State private var format: ImageFormat = .PNG
    @State private var scale: Double = 1
    @State private var allPages = false
    @State private var includeGuides = false

    var body: some View {
        NavigationStack {
            Form {
                let tabs = vm.saveTabs()
                if tabs.count > 1 {
                    Picker("", selection: $tab) {
                        ForEach(tabs, id: \.self) { t in Text(t == .IMAGE ? "PNG / JPEG" : t == .PDF ? "PDF" : "CBZ").tag(t) }
                    }
                    .pickerStyle(.segmented)
                }
                switch tab {
                case .IMAGE: imageSection
                case .PDF: pdfSection
                case .CBZ: cbzSection
                }
            }
            .navigationTitle(L("save_button"))
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { dismiss() } } }
        }
        .onAppear {
            tab = vm.saveTabs().first ?? .IMAGE
            images.openSaveDialog()
            if let o = images.saveOptions { format = o.format; scale = Double(o.scale); allPages = o.allPages; includeGuides = o.includeGuides }
        }
        .onDisappear { vm.closeUnifiedSave() }
    }

    @ViewBuilder private var imageSection: some View {
        Section {
            Picker(L("save_format"), selection: $format) { ForEach(ImageFormat.allCases, id: \.self) { Text($0.label).tag($0) } }
            LabeledContent("×\(String(format: "%.1f", scale))") { Slider(value: $scale, in: 0.5...4, step: 0.5) }
            if vm.store.canvas?.kind.paged == true { Toggle(L("save_all_pages", vm.store.canvas?.pageCount ?? 1), isOn: $allPages) }
            Toggle(L("save_include_guides"), isOn: $includeGuides)
            Button {
                guard var o = images.saveOptions else { return }
                o.format = format; o.scale = Float(scale); o.allPages = allPages; o.includeGuides = includeGuides
                images.saveAsImage(o)
            } label: {
                if images.save == .saving { ProgressView() } else { Label(L("save_button"), systemImage: "square.and.arrow.down") }
            }
            .accessibilityIdentifier("save_image")
        }
        if case let .saved(list) = images.save, let repo = EditorDependencies.shared.images {
            Section {
                ForEach(list) { img in
                    ShareLink(item: repo.url(img)) { Label(img.title, systemImage: "square.and.arrow.up") }
                }
            }
        }
        if images.save == .failed { Text("⚠︎").foregroundStyle(.red) }
    }

    @ViewBuilder private var pdfSection: some View {
        Section {
            Button {
                images.openPdfDialog()
                images.exportPdf(images.pdfOptions)
            } label: {
                if images.pdf == .exporting { ProgressView() } else { Label("PDF", systemImage: "doc.richtext") }
            }
            .accessibilityIdentifier("save_pdf")
            switch images.pdf {
            case let .done(f), let .printDone(f): ShareLink(item: f.url) { Label(f.location, systemImage: "square.and.arrow.up") }
            case .failed: Text("⚠︎").foregroundStyle(.red)
            default: EmptyView()
            }
        }
    }

    @ViewBuilder private var cbzSection: some View {
        Section {
            Button {
                v40.openCbz()
                if let o = v40.cbzOptions { v40.exportCbz(o) }
            } label: {
                if v40.cbz == .exporting { ProgressView() } else { Label("CBZ", systemImage: "books.vertical") }
            }
            .accessibilityIdentifier("save_cbz")
            switch v40.cbz {
            case let .done(f), let .printDone(f): ShareLink(item: f.url) { Label(f.location, systemImage: "square.and.arrow.up") }
            case .failed: Text("⚠︎").foregroundStyle(.red)
            default: EmptyView()
            }
        }
    }
}

/// Kılavuz çizgileri ve perspektif penceresi (Android `GuideSheet`).
struct GuideSheet: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @Environment(\.dismiss) private var dismiss

    private var spec: GuideSpec { store.canvas?.guide ?? GuideSpec() }

    var body: some View {
        NavigationStack {
            Form {
                Section(L("guide_title")) {
                    Picker(L("guide_title"), selection: Binding(get: { GuideSpec.GridKinds.contains(spec.kind) ? spec.kind : .NONE }, set: { k in
                        if k == .NONE { vm.guides.removeGuide() } else { vm.guides.addGuide(k) }
                    })) {
                        Text(L("guide_none")).tag(GuideKind.NONE)
                        Text(L("guide_grid")).tag(GuideKind.GRID)
                        Text(L("guide_ruled")).tag(GuideKind.RULED)
                        Text(L("guide_dots")).tag(GuideKind.DOTS)
                        Text(L("guide_isometric")).tag(GuideKind.ISOMETRIC)
                    }
                    .pickerStyle(.segmented)
                    LabeledContent(L("guide_spacing")) {
                        Slider(value: Binding(get: { Double(spec.spacing) }, set: { v in var s = spec; s.spacing = Float(v); vm.guides.setGuide(s, persist: false) }),
                               in: 20...300, onEditingChanged: { e in if !e { vm.guides.setGuide(spec) } })
                    }
                    LabeledContent(L("guide_opacity")) {
                        Slider(value: Binding(get: { Double(spec.opacity) }, set: { v in var s = spec; s.opacity = Float(v); vm.guides.setGuide(s, persist: false) }),
                               in: 0.05...1, onEditingChanged: { e in if !e { vm.guides.setGuide(spec) } })
                    }
                    Toggle(L("save_include_guides"), isOn: Binding(get: { spec.includeInExport }, set: { v in var s = spec; s.includeInExport = v; vm.guides.setGuide(s) }))
                }
                if store.canvas?.kind.isNotes != true {
                    Section(L("add_perspective")) {
                        Picker(L("add_perspective"), selection: Binding(get: { spec.perspective }, set: { n in
                            if n == 0 { vm.guides.removePerspective() } else { vm.guides.addPerspective(n) }
                        })) {
                            Text(L("guide_none")).tag(0)
                            Text(L("guide_p1")).tag(1); Text(L("guide_p2")).tag(2); Text(L("guide_p3")).tag(3)
                        }
                        .pickerStyle(.segmented)
                        if spec.perspective > 0 {
                            LabeledContent(L("guide_horizon")) {
                                Slider(value: Binding(get: { Double(spec.horizon) }, set: { v in var s = spec; s.horizon = Float(v); vm.guides.setGuide(s, persist: false) }),
                                       in: 0.05...0.95, onEditingChanged: { e in if !e { vm.guides.setGuide(spec) } })
                            }
                            Button(L("perspective_bar_hint")) { dismiss(); vm.startAction(.PERSPECTIVE, target: []) }
                        }
                    }
                }
                Section { Button(L("guide_clear_all"), role: .destructive) { vm.guides.clearGuides() } }
            }
            .navigationTitle(L("add_guide"))
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .dockedEditorSheet()
    }
}

/// Arka plan: kağıt rengi, resim ve kaldırma (Android `BackgroundSheet` temel kısmı).
struct BackgroundSheet: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let kind = store.canvas?.kind ?? .ART
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ColorPicker(L("canvas_background_color_label"), selection: Binding(
                        get: { Color(hex: store.canvas?.pagePaperHex ?? "#FFFFFF") ?? .white },
                        set: { vm.setBackgroundColor($0.hexRGB, coalesce: "bg-color") }), supportsOpacity: false)
                    // v23 desen, resim ve gradyan (Android çizim ekranındaki arka plan penceresi)
                    BackgroundPickerView(
                        usage: kind.isNotes ? .NOTE : .ART,
                        paperHex: store.canvas?.pagePaperHex ?? "#FFFFFF",
                        spec: Binding(get: { store.canvas?.pageBackground ?? BackgroundSpec() }, set: { vm.setBackgroundSpec($0) }),
                        showImages: kind != .COMIC,
                        onGallery: { vm.importBackground($0) })
                    if vm.backgroundPerPage() {
                        Button(L("bg_apply_all")) { vm.applyBackgroundToAllPages() }
                            .accessibilityIdentifier("bg_apply_all")
                    }
                    if !(store.canvas?.pageBackground.isEmpty ?? true) {
                        Button(L("guide_remove"), role: .destructive) { vm.removeBackground() }
                    }
                }
                .padding(16)
            }
            .navigationTitle(L("add_background"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .dockedEditorSheet()
    }
}

/// Diyagram türü seçici: seçilen tür sürüklenerek ya da dokunularak yerleştirilir.
struct DiagramPickerSheet: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss

    static func title(_ k: DiagramKind) -> String {
        let key = "diagram_" + k.rawValue.lowercased()
        let t = L(key)
        return t == key ? k.rawValue.replacingOccurrences(of: "_", with: " ").capitalized : t
    }

    var body: some View {
        // v53 diyagramlar küçük resimleri ve adlarıyla ızgarada (Android `DiagramPicker`)
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 10)], spacing: 10) {
                    ForEach(DiagramKind.allCases, id: \.self) { k in
                        Button {
                            vm.selectDiagram(k)
                            vm.closeDiagramPicker()
                            if vm.mode != .DIAGRAM { vm.startAction(.DIAGRAM, target: []) }
                            dismiss()
                        } label: {
                            ThumbCard(title: Self.title(k)) {
                                if let img = PickerThumbs.diagram(k) { Image(uiImage: img).resizable().scaledToFit() }
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("diagram_\(k.rawValue)")
                    }
                }
                .padding(16)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle(L("add_diagram"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { vm.closeDiagramPicker(); dismiss() } } }
        }
    }
}

/// Hazır formül seçici: kategoriler, formüller dizilmiş haliyle (küçük resim) ve adıyla.
struct FormulaPickerSheet: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(FormulaCatalog.categories, id: \.id) { cat in
                        Text(cat.title).font(.subheadline.weight(.semibold))
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 10)], spacing: 10) {
                            ForEach(cat.templates, id: \.id) { t in
                                Button {
                                    vm.selectFormula(t)
                                    vm.closeFormulaPicker()
                                    if vm.mode != .FORMULA { vm.startAction(.FORMULA, target: []) }
                                    dismiss()
                                } label: {
                                    ThumbCard(title: t.title) {
                                        if let img = PickerThumbs.formula(t) { Image(uiImage: img).resizable().scaledToFit().padding(4) }
                                    }
                                }
                                .buttonStyle(.plain)
                                .accessibilityIdentifier("formula_\(t.id)")
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle(L("add_formula"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { vm.closeFormulaPicker(); dismiss() } } }
        }
    }
}

/// v53 seçicilerin küçük resimleri (gerçek çiziciyle; sınırlı önbellek, bellek uyarısında boşalır).
enum PickerThumbs {
    static let cache: NSCache<NSString, UIImage> = { let c = NSCache<NSString, UIImage>(); c.countLimit = 120; return c }()

    static func diagram(_ k: DiagramKind) -> UIImage? {
        let key = "d-\(k.rawValue)" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        let W: Float = 220
        let aspect = DiagramCatalog.aspect(k) ?? 1.3
        let H = W / max(min(aspect, 2.4), 0.6)
        let nodes = DiagramRenderer.layout(k, DiagramRenderer.defaultNodes(k))
        let edges = DiagramRenderer.defaultEdges(k, nodes)
        let spec = DiagramSpec(kind: k, nodes: nodes, edges: edges, style: ShapeStyle(strokeColorHex: "#37474F", strokeWidth: 2))
        let stroke = Stroke(id: "thumb-d-\(k.rawValue)", points: [StrokePoint(4, 4), StrokePoint(W - 4, 4), StrokePoint(W - 4, H - 4), StrokePoint(4, H - 4)],
                            colorHex: "#37474F", brushType: .DIAGRAM, sizePx: 2, opacity: 1, order: 0, diagram: spec)
        let img = UIGraphicsImageRenderer(size: CGSize(width: CGFloat(W), height: CGFloat(H))).image { r in
            DiagramRenderer.draw(r.cgContext, stroke, labels: false)
        }
        cache.setObject(img, forKey: key)
        return img
    }

    static func formula(_ t: FormulaTemplate) -> UIImage? {
        let key = "f-\(t.id)" as NSString
        if let hit = cache.object(forKey: key) { return hit }
        let size: Float = 26
        let quad = FormulaRenderer.quadAt(t.source, size, 4, 4)
        let w = (quad.map(\.x).max() ?? 100) + 4, h = (quad.map(\.y).max() ?? 40) + 4
        let stroke = Stroke(id: "thumb-f-\(t.id)", points: quad, colorHex: "#1E1E1E", brushType: .FORMULA, sizePx: size, opacity: 1, order: 0,
                            formula: FormulaSpec(source: Formula.atomize(t.source), templateId: t.id))
        let img = UIGraphicsImageRenderer(size: CGSize(width: CGFloat(w), height: CGFloat(h))).image { r in
            FormulaRenderer.draw(r.cgContext, stroke, color: ArgbColor.hex("#1E1E1E"))
        }
        cache.setObject(img, forKey: key)
        return img
    }
}

/// v50 her odak çizgisi türünün kendi ayarları (pencere kapansa da uygulama açıkken hatırlanır; Android `LinesPresetMemory`).
enum LinesPresetMemory {
    nonisolated(unsafe) static var options: [ComicLinesKind: LinesOptions] = [:]
    nonisolated(unsafe) static var activeKind: ComicLinesKind = .FOCUS
    nonisolated(unsafe) static var style: ComicLinesStyle = .MANGA

    static func defaultFor(_ k: ComicLinesKind) -> LinesOptions {
        let d = ComicLines.defaults(k)
        return LinesOptions(density: d.density, length: d.length, thickness: d.thickness)
    }

    static func get(_ k: ComicLinesKind) -> LinesOptions { options[k] ?? defaultFor(k) }
}

/// v50 çizgi ekleme bölümü: tarz (Çizgi roman / Manga), tür ve seçili türün kendi ayarları.
struct LinesInsertSection: View {
    let onInsert: (ComicLinesKind, LinesOptions) -> Void
    let locked: (ComicLinesKind) -> Bool
    let onLocked: () -> Void
    @State private var style: ComicLinesStyle = LinesPresetMemory.style
    @State private var kind: ComicLinesKind = LinesPresetMemory.activeKind
    @State private var opts: LinesOptions = LinesPresetMemory.get(LinesPresetMemory.activeKind)

    private static func title(_ k: ComicLinesKind) -> String { L("lines_" + k.rawValue.lowercased()) }

    private func select(_ k: ComicLinesKind) {
        LinesPresetMemory.options[kind] = opts
        kind = k
        LinesPresetMemory.activeKind = k
        opts = LinesPresetMemory.get(k)
    }

    var body: some View {
        Section(footer: Text(L("lines_hint"))) {
            Picker("", selection: $style) {
                Text(L("v50_lines_style_comic")).tag(ComicLinesStyle.COMIC)
                Text(L("v50_lines_style_manga")).tag(ComicLinesStyle.MANGA)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("lines_style")
            .onChange(of: style) { _, s in
                LinesPresetMemory.style = s
                if ComicLines.style(kind) != s, let first = ComicLines.kinds(s).first { select(first) }
            }
            ForEach(ComicLines.kinds(style), id: \.self) { k in
                Button { select(k) } label: {
                    HStack {
                        Text(Self.title(k)).foregroundStyle(.primary)
                        Spacer()
                        if locked(k) { Image(systemName: "crown.fill").foregroundStyle(.yellow) }
                        if k == kind { Image(systemName: "checkmark").foregroundStyle(Color.accentColor) }
                    }
                }
                .accessibilityIdentifier("lines_\(k.rawValue)")
            }
        }
        Section(header: Text(Self.title(kind))) {
            slider(L("lines_density_action"), $opts.density, ComicLines.MIN_DENSITY...ComicLines.MAX_DENSITY)
            slider(L("lines_length"), $opts.length, ComicLines.MIN_LENGTH...ComicLines.MAX_LENGTH)
            slider(L("lines_thickness"), $opts.thickness, ComicController.MIN_THICKNESS...ComicController.MAX_THICKNESS)
            if ComicLines.usesFocus(kind) {
                slider(L("lines_focus_x"), $opts.focusX, 0...1)
                slider(L("lines_focus_y"), $opts.focusY, 0...1)
            }
            if ComicLines.usesAngle(kind) { slider(L("lines_angle"), $opts.angle, -180...180) }
            Button {
                LinesPresetMemory.options[kind] = opts
                if locked(kind) { onLocked() } else { onInsert(kind, opts) }
            } label: {
                HStack {
                    Text(L("v50_lines_add", Self.title(kind))).fontWeight(.semibold)
                    if locked(kind) { Spacer(); Image(systemName: "crown.fill").foregroundStyle(.yellow) }
                }
            }
            .accessibilityIdentifier("lines_add")
            Button(L("v50_lines_reset")) {
                opts = LinesPresetMemory.defaultFor(kind)
                LinesPresetMemory.options[kind] = nil
            }
            .accessibilityIdentifier("lines_reset")
        }
        .onDisappear { LinesPresetMemory.options[kind] = opts }
    }

    private func slider(_ label: String, _ value: Binding<Float>, _ range: ClosedRange<Float>) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.footnote).foregroundStyle(.secondary)
            Slider(value: value, in: range)
        }
    }
}
