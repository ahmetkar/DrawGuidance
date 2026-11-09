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
        .presentationDetents([.medium, .large])
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
        .presentationDetents([.medium, .large])
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
        .presentationDetents([.medium, .large])
    }
}

/// Arka plan: kağıt rengi, resim ve kaldırma (Android `BackgroundSheet` temel kısmı).
struct BackgroundSheet: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @Environment(\.dismiss) private var dismiss
    @State private var photo: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            Form {
                ColorPicker(L("add_background"), selection: Binding(
                    get: { Color(hex: store.canvas?.pagePaperHex ?? "#FFFFFF") ?? .white },
                    set: { vm.setBackgroundColor($0.hexRGB, coalesce: "bg-color") }), supportsOpacity: false)
                PhotosPicker(selection: $photo, matching: .images) { Label(L("add_image"), systemImage: "photo") }
                if !(store.canvas?.pageBackground.isEmpty ?? true) {
                    Button(L("guide_remove"), role: .destructive) { vm.removeBackground() }
                }
            }
            .navigationTitle(L("add_background"))
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .presentationDetents([.medium])
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task { if let d = try? await item.loadTransferable(type: Data.self) { vm.importBackground(d) }; photo = nil }
        }
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
