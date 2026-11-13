import SwiftUI
import UniformTypeIdentifiers

/// v25 tuvalde "3B şekil ekle" penceresi (Android `Model3DPickerSheet`): 3B Şekillerim ve hazır şekiller; dokunmak şekli
/// seçer, alttaki "3B şekil ekle" düğmesi seçili şekli tuvale ekler. Kalem 3B düzenleyicide açar, "Yeni 3B şekil" boş
/// düzenleyiciyi açar. v27 GLB içe aktarma (basit çizimde yok); v33 basit resimde yalnız 10 hazır şekil.
struct Model3DPickerSheet: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var repository: Model3DRepository
    let kind: CanvasKind
    @Environment(\.dismiss) private var dismiss
    @State private var chosen: String?
    @State private var editor: Model3DListView.OpenModel?
    @State private var picking = false
    @State private var pending: (name: String, bytes: [UInt8])?

    private static let MY = "my:", CAT = "cat:"

    /// Basit resim ve çocuk çiziminde 10 hazır 3B şekil (Android `SimpleCatalog.models3d`); öteki türlerde bütün katalog.
    static func catalogOnly(_ kind: CanvasKind) -> [String]? {
        guard kind.simple, !kind.isNotes else { return nil }
        return ["geo.cuboid", "geo.sphere", "geo.cylinder", "geo.cone", "geo.pyramid",
                "geo.torus", "draw.house", "draw.tree", "draw.car", "draw.apple"]
    }

    private var chosenName: String? {
        guard let key = chosen else { return nil }
        if key.hasPrefix(Self.MY) { return repository.models.first { $0.id == key.dropFirst(Self.MY.count) }?.name }
        if key.hasPrefix(Self.CAT) { return Catalog3D.all.first { $0.id == key.dropFirst(Self.CAT.count) }?.name.tr }
        return nil
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(L("model3d_add_pick_hint")).font(.footnote).foregroundStyle(.secondary)
                        HStack(spacing: 10) {
                            Button { editor = .init(id: UUID().uuidString, modelId: nil) } label: {
                                Label(L("model3d_new"), systemImage: "cube").frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier("model3d_new")
                            if !kind.simple {
                                Button { picking = true } label: {
                                    Label(L("glb_import_button"), systemImage: "square.and.arrow.down").frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.bordered)
                                .accessibilityIdentifier("model3d_import_glb")
                            }
                        }
                        Text(L("model3d_my_models")).font(.headline)
                        if repository.models.isEmpty { Text(L("model3d_list_empty")).foregroundStyle(.secondary) }
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 12)], spacing: 12) {
                            ForEach(repository.models) { info in
                                let key = Self.MY + info.id
                                Model3DCard(info: info, selected: chosen == key)
                                    .overlay(alignment: .topTrailing) {
                                        Button { editor = .init(id: info.id, modelId: info.id) } label: {
                                            Image(systemName: "pencil").padding(6).background(Circle().fill(.regularMaterial))
                                        }
                                        .padding(6)
                                        .accessibilityLabel(L("model3d_edit"))
                                    }
                                    .onTapGesture { chosen = chosen == key ? nil : key }
                            }
                        }
                        Text(L("model3d_ready_shapes")).font(.headline)
                        Text(L("model3d_add_ready_hint")).font(.footnote).foregroundStyle(.secondary)
                        CatalogGrid3(columns: 5, tile: 60, selected: chosen.flatMap { $0.hasPrefix(Self.CAT) ? String($0.dropFirst(Self.CAT.count)) : nil },
                                     only: Self.catalogOnly(kind)) { item in
                            let key = Self.CAT + item.id
                            chosen = chosen == key ? nil : key
                        }
                    }
                    .padding(16)
                }
                Divider()
                HStack(spacing: 12) {
                    Text(chosenName.map { L("model3d_add_selected", $0) } ?? L("model3d_add_none"))
                        .font(.subheadline).lineLimit(2)
                        .foregroundStyle(chosenName != nil ? Color.accentColor : .secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Button { insert() } label: { Label(L("model3d_add_button"), systemImage: "plus") }
                        .buttonStyle(.borderedProminent)
                        .disabled(chosenName == nil)
                        .accessibilityIdentifier("model3d_add_button")
                }
                .padding(.horizontal, 20).padding(.vertical, 12)
            }
            .navigationTitle(L("model3d_add_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("close")) { dismiss() } } }
        }
        .accessibilityIdentifier("model3d_picker")
        .fullScreenCover(item: $editor) { o in
            NavigationStack { Model3DEditorView(repository: repository, modelId: o.modelId) }
        }
        .fileImporter(isPresented: $picking, allowedContentTypes: [.glb, .gltf, .data]) { r in
            guard case .success(let url) = r else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            if let d = try? Data(contentsOf: url) { pending = (url.deletingPathExtension().lastPathComponent, [UInt8](d)) }
        }
        .sheet(isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } })) {
            if let p = pending {
                GlbImportSheet(name: p.name, bytes: p.bytes.count, askSave: true) { tris, save in
                    pending = nil
                    vm.importGlbToCanvas(p.bytes, name: p.name, maxTriangles: tris, save: save)
                    dismiss()
                } onDismiss: { pending = nil }
            }
        }
        .task { await repository.warmUp() }
    }

    private func insert() {
        guard let key = chosen, chosenName != nil else { return }
        if key.hasPrefix(Self.MY) { vm.insertModel3d(String(key.dropFirst(Self.MY.count))) }
        else { vm.insertCatalogModel3d(String(key.dropFirst(Self.CAT.count))) }
        dismiss()
    }
}
