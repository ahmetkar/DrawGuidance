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
