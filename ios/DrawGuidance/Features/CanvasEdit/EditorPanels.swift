import PhotosUI
import SwiftUI

/// Elemanlar paneli (Android `EditorSidePanel` elemanlar sekmesi): en son çizilen en üstte; dokununca seçime eklenir/çıkarılır.
struct ElementsPanel: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            let rows = ElementOps.rows(store.elements)
            List {
                if rows.isEmpty {
                    Text(L("elements_empty")).foregroundStyle(.secondary)
                } else {
                    Section(footer: Text(L("elements_select_hint"))) {
                        ForEach(Array(rows.enumerated()), id: \.offset) { _, row in rowView(row) }
                    }
                }
            }
            .navigationTitle("Elements")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } }
            }
        }
        .dockedEditorSheet()
    }

    @ViewBuilder private func rowView(_ row: ElementRow) -> some View {
        switch row {
        case let .single(e):
            elementButton(e, indent: false)
        case let .group(groupId, number, members):
            let selected = members.allSatisfy { m in m.strokeIds.allSatisfy(store.selectedIds.contains) }
            Button { vm.selection.toggleGroup(groupId) } label: {
                HStack {
                    Image(systemName: "square.stack.3d.up")
                    Text("Grup \(number)")
                    Spacer()
                    if selected { Image(systemName: "checkmark") }
                }
            }
            ForEach(members) { m in elementButton(m, indent: true) }
        }
    }

    private func elementButton(_ e: Element, indent: Bool) -> some View {
        let selected = e.strokeIds.allSatisfy(store.selectedIds.contains)
        return Button { vm.selection.toggleElement(e.id) } label: {
            HStack {
                Circle().fill(Color(hex: e.colorHex) ?? .gray).frame(width: 14, height: 14)
                Text("\(L(e.brushType.titleKey)) \(e.number)")
                    .foregroundStyle(e.hidden ? .secondary : .primary)
                if e.hidden { Image(systemName: "eye.slash").foregroundStyle(.secondary) }
                Spacer()
                if selected { Image(systemName: "checkmark").foregroundStyle(Color.accentColor) }
            }
            .padding(.leading, indent ? 20 : 0)
        }
        .accessibilityIdentifier("element_\(e.number)")
    }
}

/// Katmanlar paneli: etkin katman, görünürlük, kilit, sıra, ad ve seçimi katmana taşıma.
struct LayersPanel: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @Environment(\.dismiss) private var dismiss
    @State private var renaming: Layer?
    @State private var newName = ""

    var body: some View {
        NavigationStack {
            List {
                Section(footer: Text(L("layers_hint"))) {
                    ForEach((store.canvas?.layers ?? [Layer.Default]).reversed()) { layer in
                        HStack(spacing: 14) {
                            Button { vm.layers.setActive(layer.id) } label: {
                                Image(systemName: store.activeLayer == layer.id ? "largecircle.fill.circle" : "circle")
                            }
                            Text(layer.name).onTapGesture(count: 2) { renaming = layer; newName = layer.name }
                            Spacer()
                            Button { vm.layers.toggleVisible(layer.id) } label: { Image(systemName: layer.visible ? "eye" : "eye.slash") }
                            Button { vm.layers.toggleLocked(layer.id) } label: { Image(systemName: layer.locked ? "lock" : "lock.open") }
                        }
                        .buttonStyle(.borderless)
                        .swipeActions {
                            if layer.id != Layer.BASE { Button(role: .destructive) { vm.layers.remove(layer.id) } label: { Image(systemName: "trash") } }
                            Button { vm.layers.move(layer.id, 1) } label: { Image(systemName: "arrow.up") }
                            Button { vm.layers.move(layer.id, -1) } label: { Image(systemName: "arrow.down") }
                        }
                        .contextMenu {
                            if !store.selectedIds.isEmpty {
                                Button { vm.layers.moveSelectionTo(layer.id) } label: { Label("→ \(layer.name)", systemImage: "square.and.arrow.down.on.square") }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Layers")
            .toolbar {
                ToolbarItem(placement: .primaryAction) { Button { _ = vm.layers.add() } label: { Image(systemName: "plus") } }
                ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } }
            }
            .alert("", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                TextField("", text: $newName)
                Button(L("apply_button")) { if let r = renaming { vm.layers.rename(r.id, newName) }; renaming = nil }
            }
        }
        .dockedEditorSheet()
    }
}

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

    private static func title(_ k: DiagramKind) -> String {
        let key = "diagram_" + k.rawValue.lowercased()
        let t = L(key)
        return t == key ? k.rawValue.replacingOccurrences(of: "_", with: " ").capitalized : t
    }

    var body: some View {
        NavigationStack {
            List(DiagramKind.allCases, id: \.self) { k in
                Button(Self.title(k)) {
                    vm.selectDiagram(k)
                    vm.closeDiagramPicker()
                    if vm.mode != .DIAGRAM { vm.startAction(.DIAGRAM, target: []) }
                    dismiss()
                }
            }
            .navigationTitle(L("add_diagram"))
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { vm.closeDiagramPicker(); dismiss() } } }
        }
    }
}

/// Hazır formül seçici (kategoriler).
struct FormulaPickerSheet: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(FormulaCatalog.categories, id: \.id) { cat in
                    Section(cat.title) {
                        ForEach(cat.templates, id: \.id) { t in
                            Button(t.title) {
                                vm.selectFormula(t)
                                vm.closeFormulaPicker()
                                if vm.mode != .FORMULA { vm.startAction(.FORMULA, target: []) }
                                dismiss()
                            }
                        }
                    }
                }
            }
            .navigationTitle(L("add_formula"))
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { vm.closeFormulaPicker(); dismiss() } } }
        }
    }
}

/// v27/v36 çizgi roman ekleme penceresi: konuşma balonları, sayfa şablonları, efektler ve efekt yazıları.
struct ComicInsertSheet: View {
    enum Tab: String, CaseIterable { case balloons, templates, images, lines, effects, sfx }
    @ObservedObject var vm: EditorViewModel
    @State var tab: Tab = .balloons
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var sub = SubscriptionStore.shared
    @State private var premiumOpen = false

    private var tabs: [(Tab, String)] {
        var t: [(Tab, String)] = [(.balloons, L("add_balloon")), (.templates, L("add_templates"))]
        if !ComicAssets.catalog.effects.isEmpty { t.append((.images, L("effects_tab_effects"))) }
        t += [(.lines, L("effects_tab_lines")), (.effects, L("add_effects")), (.sfx, "SFX")]
        return t
    }

    private static func linesTitle(_ k: ComicLinesKind) -> String { L("lines_" + k.rawValue.lowercased()) }

    private static func balloonTitle(_ s: BalloonStyle) -> String {
        let key = "balloon_" + s.rawValue.lowercased()
        let t = L(key)
        return t == key ? s.rawValue.capitalized : t
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(tabs, id: \.0) { item in
                            let t = item.0
                            Button { tab = t } label: {
                                Text(item.1).font(.subheadline.weight(tab == t ? .semibold : .regular)).lineLimit(1)
                                    .padding(.horizontal, 12).padding(.vertical, 7)
                                    .foregroundStyle(tab == t ? Color.accentColor : Color.primary)
                                    .background(Capsule().fill(tab == t ? Color.accentColor.opacity(0.18) : Color.primary.opacity(0.06)))
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("comic_tab_\(t.rawValue)")
                        }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 10)
                }
                if tab == .images {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(L("v42_fx_images_hint")).font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 16)
                            ComicPropsBrowser(kind: .EFFECT, tag: "fximg") { p in vm.insertComicProp(.EFFECT, p); dismiss() }
                        }
                    }
                } else {
                    List { content }
                }
            }
            .navigationTitle(L("add_balloon"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { dismiss() } } }
        }
        .dockedEditorSheet()
        .sheet(isPresented: $premiumOpen) { PremiumRequiredSheet() }
    }

    @ViewBuilder private var content: some View {
        switch tab {
        case .balloons:
            Section(footer: Text(L("balloon_hint"))) {
                ForEach(BalloonStyle.allCases, id: \.self) { s in
                    Button(Self.balloonTitle(s)) { vm.insertBalloon(s, speakers: 1); dismiss() }
                }
            }
        case .templates:
            ForEach(ComicTemplates.all) { t in
                Button(t.title) { let ids = vm.comic.insertTemplate(t.id); vm.inserted(ids); dismiss() }
            }
        case .images:
            EmptyView()
        case .lines:
            // v50 çizgi roman / manga bölümleri; her tür kendi ayarlarıyla (ortak ayar yok). BURST'ten sonrakiler Premium
            // (Android `PremiumRules.isAdvancedLines`).
            LinesInsertSection(onInsert: { k, o in vm.comic.insertLines(k, o); dismiss() },
                               locked: { k in k.ordinal > ComicLinesKind.BURST.ordinal && !sub.allows(.COMIC_EFFECTS, .COMIC) },
                               onLocked: { premiumOpen = true })
        case .effects:
            ForEach(ComicEffects.all) { e in
                Button(e.title) { vm.insertEffect(e, options: EffectOptions()); dismiss() }
            }
        case .sfx:
            ForEach(Array(ComicSfx.presets.enumerated()), id: \.offset) { _, p in
                Button { vm.insertSfx(p); dismiss() } label: {
                    Text(p.text).font(.title3.weight(.heavy)).foregroundStyle(Color(hex: p.colors.first ?? "#000000") ?? .primary)
                }
            }
        }
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
