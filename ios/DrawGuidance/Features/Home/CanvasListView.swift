import SwiftUI

/// Yeni tuval oluşturma (Android `CanvasListViewModel.createCanvas`): v46 şablon, v45 çizgi roman kapağı, v23 galeriden
/// arka plan resmi.
@MainActor
enum CanvasCreator {
    static func create(_ r: CanvasFormResult, store: CanvasStore, assets: CanvasAssetStore = .shared, folderId: String? = nil) -> CanvasUi {
        if let tid = r.templateId, let t = CanvasTemplates.prepare(tid, canvasName: r.name) {
            return store.createFromTemplate(t, kind: r.kind, name: r.name, aiEnabled: r.aiEnabled, folderId: folderId) { canvasId, id, bytes, a in
                assets.saveData(canvasId, bytes, a.mime, id: id, width: a.w, height: a.h)
            }
        }
        var bg = r.background
        if bg.imageId == BackgroundPickerView.GALLERY_PENDING { bg.imageId = nil }
        let cover = r.kind == .COMIC ? ComicCover.build(r.width, r.height, r.name, ComicCover.subtitlePlaceholder()) : nil
        let made = store.create(kind: r.kind, name: r.name, width: r.width, height: r.height, paperHex: r.paperHex, aiEnabled: r.aiEnabled,
                                pageCount: r.pageCount, background: bg, cover: cover, folderId: folderId)
        if let data = r.galleryData { applyGallery(made.localId, bg, data, store: store, assets: assets) }
        return made
    }

    /// Galeriden seçilen resmi tuvale ekleyip arka plan yapar (okunamazsa resimsiz kalır).
    private static func applyGallery(_ id: String, _ spec: BackgroundSpec, _ data: Data, store: CanvasStore, assets: CanvasAssetStore) {
        let result = ImageImportValidator.check(data)
        guard result.accepted, let image = result.image else { return }
        let assetId = assets.save(id, image)
        var s = spec
        s.imageId = BackgroundSpec.ASSET + assetId
        s.imageFit = .COVER
        store.saveBackgroundSpec(id, s)
    }
}

/// Tuvalin küçük resmi (Android `CanvasThumbnail`): arka planda küçük boyda çizilir.
struct CanvasThumbnail: View {
    let canvas: CanvasUi
    var side: CGFloat = 240
    @State private var image: CGImage?

    var body: some View {
        ZStack {
            Color(hex: canvas.pagePaperHex) ?? .white
            if let image { Image(decorative: image, scale: 1).resizable().scaledToFit() }
        }
        .task(id: "\(canvas.localId)@\(canvas.updatedAt)") {
            let c = canvas
            let scale = Float(side) * 2 / Float(max(c.width, c.height, 1))
            image = await Task.detached(priority: .utility) { ImageExporter().render(c, scale: min(scale, 1)) }.value
        }
    }
}

/// v40 Tuvallerim (Android `CanvasListScreen`): arama, sıralama, kartlar (küçük resim, tür, sayfa, düzenleme zamanı),
/// Yeni Tuval, tuval ayarları, kopyala, resim olarak kaydet ve sil.
struct CanvasListView: View {
    @EnvironmentObject private var store: CanvasStore
    let onOpen: (CanvasUi) -> Void
    @State private var query = ""
    @State private var sortRecent = true
    @State private var creating = false
    @State private var editing: CanvasUi?
    @State private var deleting: CanvasUi?
    @State private var message: String?

    private var shown: [CanvasUi] {
        let q = query.trimmingCharacters(in: .whitespaces)
        let list = q.isEmpty ? store.canvases : store.canvases.filter { $0.name.localizedCaseInsensitiveContains(q) }
        return sortRecent ? list.sorted { $0.updatedAt > $1.updatedAt } : list.sorted { $0.createdAt > $1.createdAt }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(L("canvas_count", store.canvases.count)).font(.subheadline).foregroundStyle(.secondary)
                    Spacer()
                    Picker(L("images_sort"), selection: $sortRecent) {
                        Text(L("canvas_sort_recent")).tag(true)
                        Text(L("canvas_sort_newest")).tag(false)
                    }
                    .pickerStyle(.menu)
                    .accessibilityIdentifier("canvas_sort")
                }
                if store.canvases.isEmpty {
                    Text(L("empty_canvas_list")).foregroundStyle(.secondary).padding(.vertical, 24)
                } else if shown.isEmpty {
                    Text(L("search_no_result", query)).foregroundStyle(.secondary).padding(.vertical, 24)
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 14)], spacing: 14) {
                    ForEach(shown) { c in
                        CanvasCard(canvas: c)
                            .onTapGesture { onOpen(c) }
                            .contextMenu { menu(c) }
                            .accessibilityIdentifier("canvas_\(c.name)")
                    }
                }
            }
            .padding(16)
        }
        .searchable(text: $query, prompt: L("search_canvases_hint"))
        .navigationTitle(L("canvas_list_title"))
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { creating = true } label: { Label(L("new_canvas"), systemImage: "plus") }
                    .accessibilityIdentifier("canvas_list_new")
            }
        }
        .overlay(alignment: .bottom) {
            if let message {
                Text(message).font(.subheadline).foregroundStyle(.white).padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Capsule().fill(Color.black.opacity(0.78))).padding(.bottom, 24)
                    .onTapGesture { self.message = nil }
            }
        }
        .sheet(isPresented: $creating) {
            CanvasFormSheet(kind: .ART, kindChoices: CanvasKind.Advanced + CanvasKind.Simple) { r in
                onOpen(CanvasCreator.create(r, store: store))
            }
        }
        .sheet(item: $editing) { c in
            CanvasFormSheet(isEditMode: true, kind: c.kind, kindChoices: [c.kind], name: c.name, width: c.width, height: c.height,
                            paperHex: c.backgroundColorHex, aiEnabled: c.aiEnabled, background: c.background) { r in
                store.updateSettings(c.localId, name: r.name, width: r.width, height: r.height, background: r.paperHex)
                store.setAiEnabled(c.localId, r.aiEnabled)
                var bg = r.background
                if bg.imageId == BackgroundPickerView.GALLERY_PENDING { bg.imageId = nil }
                store.saveBackgroundSpec(c.localId, bg, c.pageBackgrounds)
                if let data = r.galleryData {
                    let result = ImageImportValidator.check(data)
                    if result.accepted, let image = result.image {
                        bg.imageId = BackgroundSpec.ASSET + CanvasAssetStore.shared.save(c.localId, image)
                        bg.imageFit = .COVER
                        store.saveBackgroundSpec(c.localId, bg, c.pageBackgrounds)
                    }
                }
                store.refresh(c.localId)
            }
        }
        .alert(L("delete_confirm_title"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button(L("delete_button"), role: .destructive) {
                if let d = deleting { store.delete(d.localId); EditorDependencies.shared.recent.remove(d.localId) }
                deleting = nil
            }
            Button(L("cancel_button"), role: .cancel) { deleting = nil }
        } message: { Text(L("delete_confirm_message")) }
    }

    @ViewBuilder private func menu(_ c: CanvasUi) -> some View {
        Button { onOpen(c) } label: { Label(L("v42_saved_open"), systemImage: "pencil") }
        Button { editing = c } label: { Label(L("action_canvas_settings"), systemImage: "slider.horizontal.3") }
        Button {
            if let d = store.duplicate(c.localId, name: c.name + " 2") { show(d.name) }
        } label: { Label(L("duplicate_selection"), systemImage: "plus.square.on.square") }
        Button { saveAsImage(c) } label: { Label(L("save_as_image"), systemImage: "square.and.arrow.down") }
        Button(role: .destructive) { deleting = c } label: { Label(L("action_delete_canvas"), systemImage: "trash") }
    }

    private func saveAsImage(_ c: CanvasUi) {
        guard let repo = EditorDependencies.shared.images, let full = store.getCanvas(c.localId) else { return }
        do {
            let saved = try repo.saveCanvasAsImage(full)
            show(L("image_saved", saved.title))
        } catch { show(L("image_save_failed")) }
    }

    private func show(_ s: String) {
        message = s
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { if message == s { message = nil } }
    }
}

/// Tuval kartı (Android `CanvasCard`).
struct CanvasCard: View {
    let canvas: CanvasUi

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            CanvasThumbnail(canvas: canvas)
                .aspectRatio(CGFloat(max(canvas.width, 1)) / CGFloat(max(canvas.height, 1)) > 1.3 ? 4 / 3 : 3 / 4, contentMode: .fit)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.secondary.opacity(0.2), lineWidth: 1))
            Text(canvas.name).font(.callout.weight(.medium)).lineLimit(1)
            HStack(spacing: 6) {
                Image(systemName: canvas.kind.systemImage)
                Text(canvas.kind.label).lineLimit(1)
                if canvas.pageCount > 1 { Text("· \(canvas.pageCount)") }
            }
            .font(.caption).foregroundStyle(.secondary)
            Text(L("home_edited_ago", RelativeTime.text(canvas.updatedAt))).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
        }
        .padding(8)
        .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 18))
        .shadow(color: .black.opacity(0.06), radius: 5, y: 2)
        .accessibilityElement(children: .combine)
    }
}

/// "az önce", "5 dk önce", "dün", "4 gün önce" (Android `relative`).
enum RelativeTime {
    static func text(_ ms: Int64, now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> String {
        let m = max((now - ms) / 60_000, 0)
        if m < 1 { return L("time_just_now") }
        if m < 60 { return L("time_minutes", Int(m)) }
        if m < 60 * 24 { return L("time_hours", Int(m / 60)) }
        if m < 60 * 48 { return L("time_yesterday") }
        return L("time_days", Int(m / (60 * 24)))
    }
}
