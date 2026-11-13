import Photos
import SwiftUI

// MARK: - hazır şekiller ızgarası

/// v24 hazır şekillerin küçük resimleri (bir kez çizilir; diskte de saklanır).
enum CatalogThumbs {
    private static let lock = NSLock()
    private static var cache: [String: CGImage] = [:]
    private static let SIZE = 144
    private static let DISK_VERSION = 1
    private static let gate = DispatchSemaphore(value: Budget3D.tier == .LOW ? 1 : 2)

    private static var dir: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("catalog3d", isDirectory: true)
    }

    static func get(_ item: CatalogItem) -> CGImage? {
        lock.lock(); if let c = cache[item.id] { lock.unlock(); return c }; lock.unlock()
        let file = dir.appendingPathComponent("\(item.id.replacingOccurrences(of: "[^A-Za-z0-9_.-]", with: "_", options: .regularExpression))-\(SIZE)-v\(DISK_VERSION).png")
        if let d = try? Data(contentsOf: file), let t = PixelCodec.decode([UInt8](d)) {
            let img = Bitmaps.image(argb: t.px, t.w, t.h)
            lock.lock(); cache[item.id] = img; lock.unlock()
            return img
        }
        gate.wait(); defer { gate.signal() }
        let m = item.build()
        let f = Renderer3D.render(m, View3.fit(m, m.camera, SIZE, SIZE, 0.08),
                                  RenderOptions(textures: { id in TextureCatalog.byId(id)?.generate(64) }, supersample: 2, ground: false, background: 0))
        let img = Bitmaps.image(argb: f.color, SIZE, SIZE)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let png = PixelCodec.encode(img) { try? png.write(to: file, options: .atomic) }
        lock.lock(); cache[item.id] = img; lock.unlock()
        return img
    }
}

/// v24 hazır 3B şekiller ızgarası: kategori seçimi ve o kategorinin şekilleri.
struct CatalogGrid3: View {
    var columns = 4
    var tile: CGFloat = 64
    var selected: String?
    var query = ""
    /// v33 basit çizimde yalnız bu hazır şekiller (kategori çipleri gizlenir).
    var only: [String]?
    let onPick: (CatalogItem) -> Void
    @State private var category = Catalog3D.categories[0]

    init(columns: Int = 4, tile: CGFloat = 64, selected: String? = nil, query: String = "", only: [String]? = nil,
         onPick: @escaping (CatalogItem) -> Void) {
        self.columns = columns; self.tile = tile; self.selected = selected; self.query = query; self.only = only; self.onPick = onPick
    }

    var body: some View {
        let q = query.trimmingCharacters(in: .whitespaces)
        let items: [CatalogItem] = only.map { ids in ids.compactMap { id in Catalog3D.all.first { $0.id == id } } }
            ?? (q.isEmpty ? Catalog3D.all.filter { $0.category == category } : Catalog3D.all.filter { $0.name.tr.localizedCaseInsensitiveContains(q) })
        VStack(alignment: .leading, spacing: 10) {
            if only != nil {
                EmptyView()
            } else if q.isEmpty {
                ChipRow3 { ForEach(Catalog3D.categories, id: \.self) { c in Chip3(c.tr, c == category) { category = c } } }
            } else if items.isEmpty {
                Text(L("search_no_result", q)).foregroundStyle(.secondary).padding(8).accessibilityIdentifier("model3d_catalog_empty")
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: columns), spacing: 8) {
                ForEach(items, id: \.id) { item in
                    let chosen = item.id == selected
                    Button { onPick(item) } label: {
                        VStack(spacing: 2) {
                            AsyncThumb(key: "cat-\(item.id)") { CatalogThumbs.get(item) }
                                .frame(width: tile, height: tile)
                                .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 10))
                            Text(item.name.tr).font(.caption2).lineLimit(2).multilineTextAlignment(.center)
                        }
                        .padding(4)
                        .background(chosen ? Color.accentColor.opacity(0.16) : .clear, in: RoundedRectangle(cornerRadius: 12))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(item.name.tr)
                }
            }
        }
        .accessibilityIdentifier("model3d_catalog")
    }
}

// MARK: - resim olarak dışa aktarma

/// v25 3B şekli resim olarak dışa aktarma: boyut, oran, arka plan, zemin, gölge, kenar, açı, biçim, yumuşatma; ayarlar
/// değiştikçe önizleme yenilenir. "Kaydet" Fotoğraflar'a yazar, "Paylaş" paylaşım penceresini açar.
struct ImageExportSheet3: View {
    @ObservedObject var vm: Model3DEditorModel
    @Environment(\.dismiss) private var dismiss
    @State private var e = ImageExport(size: min(1080, Budget3D.exportMaxSide))
    @State private var preview: CGImage?
    @State private var working = false
    @State private var share: ShareFile?

    private static let SIZES = [720, 1080, 1600, 2048, 3072]
    private static let BACKGROUNDS: [Int32] = [0xFFFFFFFF, 0xFFF5F0E6, 0xFFECEFF1, 0xFFDDEBFF, 0xFF263238, 0xFF000000].map { Int32(bitPattern: UInt32($0)) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    ZStack {
                        if e.background == 0 && !e.jpeg { Checker() } else { Color(argb: e.jpeg && e.background == 0 ? -1 : e.background) }
                        if let p = preview { Image(decorative: p, scale: 1).resizable().scaledToFit() } else { ProgressView() }
                    }
                    .aspectRatio(CGFloat(e.aspect), contentMode: .fit)
                    .frame(maxWidth: 260)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Önizleme".tr)

                    label(L("model3d_image_size"))
                    ChipRow3 { ForEach(Self.SIZES.filter { $0 <= Budget3D.exportMaxSide }, id: \.self) { s in Chip3("\(s) px", e.size == s) { e.size = s } } }
                    Text(L("model3d_image_limit", Budget3D.exportMaxSide)).font(.caption).foregroundStyle(.secondary)
                    label(L("model3d_image_aspect"))
                    ChipRow3 {
                        ForEach([(Float(1), "model3d_image_square"), (0.75, "model3d_image_portrait"), (4.0 / 3, "model3d_image_landscape"), (16.0 / 9, "model3d_image_wide")], id: \.1) { item in
                            Chip3(L(item.1), abs(e.aspect - item.0) < 0.01) { e.aspect = item.0 }
                        }
                    }
                    label(L("model3d_image_background"))
                    HStack {
                        Chip3(L("model3d_transparent"), e.background == 0) { e.background = 0 }
                        ColorRow3(selected: e.background == 0 ? nil : e.background, extra: []) { e.background = $0 }
                    }
                    if e.jpeg && e.background == 0 { Text(L("model3d_image_jpeg_bg")).font(.caption).foregroundStyle(.secondary) }
                    Toggle(L("model3d_ground_grid"), isOn: $e.ground)
                    Toggle(L("model3d_shadow"), isOn: $e.shadow)
                    Toggle(L("model3d_edges"), isOn: $e.edges)
                    label(L("model3d_image_angle"))
                    ChipRow3 { Chip3(L("model3d_image_fit"), e.fit) { e.fit = true }; Chip3(L("model3d_image_current"), !e.fit) { e.fit = false } }
                    label(L("model3d_image_format"))
                    ChipRow3 { Chip3("PNG", !e.jpeg) { e.jpeg = false }; Chip3("JPEG", e.jpeg) { e.jpeg = true } }
                    label(L("model3d_image_smooth"))
                    ChipRow3 {
                        ForEach([(1, "model3d_image_smooth_low"), (2, "model3d_image_smooth_mid"), (3, "model3d_image_smooth_high")], id: \.0) { item in
                            Chip3(L(item.1), e.smoothness == item.0) { e.smoothness = item.0 }
                        }
                    }
                    HStack(spacing: 8) {
                        Button { export(share: true) } label: { Label(L("model3d_image_share"), systemImage: "square.and.arrow.up").frame(maxWidth: .infinity) }
                            .buttonStyle(.bordered)
                        Button { export(share: false) } label: { Label(L("model3d_image_save"), systemImage: "square.and.arrow.down").frame(maxWidth: .infinity) }
                            .buttonStyle(.borderedProminent)
                    }
                    .disabled(working)
                    if working { ProgressView().frame(maxWidth: .infinity) }
                }
                .padding(20)
            }
            .navigationTitle(L("model3d_export_image"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L("cancel_button")) { dismiss() } } }
            .accessibilityIdentifier("model3d_image_export")
        }
        .task(id: e) {
            try? await Task.sleep(nanoseconds: 150_000_000)
            if Task.isCancelled { return }
            preview = await vm.renderImage(e, previewSide: 300)
        }
        .sheet(item: $share) { ShareSheet(items: [$0.url]) }
    }

    private func label(_ s: String) -> some View { Text(s).font(.subheadline.weight(.semibold)) }

    private func export(share toShare: Bool) {
        guard !working else { return }
        working = true
        let settings = e
        Task {
            defer { working = false }
            guard let img = await vm.renderImage(settings), let data = PixelCodec.encode(img, jpeg: settings.jpeg) else { vm.message(L("model3d_image_failed")); return }
            let name = (vm.model?.name ?? "3D").replacingOccurrences(of: "[\\\\/:*?\"<>|]", with: "_", options: .regularExpression)
            if toShare {
                let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(name).\(settings.jpeg ? "jpg" : "png")")
                do { try data.write(to: url); share = ShareFile(url: url) } catch { vm.message(L("model3d_image_failed")) }
                return
            }
            let ok = await PhotoSaver.save(data)
            vm.message(L(ok ? "model3d_image_saved" : "model3d_image_failed"))
            if ok { dismiss() }
        }
    }
}

/// Fotoğraflar'a yalnız ekleme izniyle kaydeder.
enum PhotoSaver {
    static func save(_ data: Data) async -> Bool {
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else { return false }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: nil)
            }
            return true
        } catch { return false }
    }
}

/// Saydam önizleme için dama deseni.
struct Checker: View {
    var body: some View {
        Canvas { ctx, size in
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(white: 0.95)))
            let cell: CGFloat = 10
            for y in 0...Int(size.height / cell) { for x in 0...Int(size.width / cell) where (x + y) % 2 == 0 {
                ctx.fill(Path(CGRect(x: CGFloat(x) * cell, y: CGFloat(y) * cell, width: cell, height: cell)), with: .color(Color(white: 0.855)))
            } }
        }
    }
}

// MARK: - 3B Şekillerim

/// v23 "3B Şekiller" bölümü: kaydedilen şekiller (dokununca düzenleyici), yeni şekil, silme, hazır şekiller ve GLB içe aktarma.
struct Model3DListView: View {
    @ObservedObject var repository: Model3DRepository
    @State private var ready = false
    @State private var query = ""
    @State private var open: OpenModel?
    @State private var deleting: Model3DInfo?
    @State private var picking = false
    @State private var pending: (name: String, bytes: [UInt8])?
    @State private var importing = false
    @State private var message: String?

    struct OpenModel: Identifiable, Hashable { let id: String; let modelId: String? }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Chip3(L("model3d_my_shapes_tab"), !ready) { ready = false }
                Chip3(L("model3d_ready_shapes"), ready) { ready = true }
                Spacer()
            }
            .padding(.horizontal, 16).padding(.vertical, 4)
            TextField(L("search_models_hint"), text: $query).textFieldStyle(.roundedBorder).padding(.horizontal, 16).padding(.vertical, 6)
                .accessibilityIdentifier("models3d_search")
            if importing { Text(L("model3d_importing")).font(.caption).foregroundStyle(Color.accentColor) }
            if let message { Text(message).font(.caption).foregroundStyle(Color.accentColor).padding(.horizontal, 20).accessibilityIdentifier("models3d_message") }
            ScrollView {
                if ready {
                    CatalogGrid3(columns: 4, tile: 72, query: query) { open = OpenModel(id: UUID().uuidString, modelId: Catalog3D.PREFIX + $0.id) }.padding(16)
                } else {
                    let q = query.trimmingCharacters(in: .whitespaces)
                    let shown = q.isEmpty ? repository.models : repository.models.filter { $0.name.localizedCaseInsensitiveContains(q) }
                    if repository.models.isEmpty { Text(L("model3d_list_empty")).foregroundStyle(.secondary).padding(24) } else if shown.isEmpty {
                        Text(L("search_no_result", q)).foregroundStyle(.secondary).padding(24)
                    }
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 14)], spacing: 14) {
                        ForEach(shown) { info in
                            Model3DCard(info: info)
                                .onTapGesture { open = OpenModel(id: info.id, modelId: info.id) }
                                .contextMenu { Button(L("delete_button"), role: .destructive) { deleting = info } }
                                .onLongPressGesture { deleting = info }
                        }
                    }
                    .padding(16)
                }
            }
        }
        .navigationTitle(L("model3d_my_models"))
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { picking = true } label: { Label(L("glb_import_button"), systemImage: "square.and.arrow.down") }
                    .disabled(importing).accessibilityIdentifier("models3d_import_glb")
                Button { open = OpenModel(id: UUID().uuidString, modelId: nil) } label: { Label(L("model3d_new"), systemImage: "plus") }
                    .accessibilityIdentifier("models3d_new")
            }
        }
        .navigationDestination(item: $open) { o in Model3DEditorView(repository: repository, modelId: o.modelId) }
        .fileImporter(isPresented: $picking, allowedContentTypes: [.glb, .gltf, .data]) { r in
            guard case .success(let url) = r else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            if let d = try? Data(contentsOf: url) { pending = (url.deletingPathExtension().lastPathComponent, [UInt8](d)) } else { show(L("model3d_import_failed", "")) }
        }
        .sheet(isPresented: Binding(get: { pending != nil }, set: { if !$0 { pending = nil } })) {
            if let p = pending {
                GlbImportSheet(name: p.name, bytes: p.bytes.count, askSave: false) { tris, _ in
                    pending = nil
                    importGlb(p.bytes, p.name, tris)
                } onDismiss: { pending = nil }
            }
        }
        .alert(deleting.map { L("model3d_delete_title", $0.name) } ?? "", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button(L("delete_button"), role: .destructive) { if let d = deleting { repository.delete([d.id]) }; deleting = nil }
            Button(L("cancel_button"), role: .cancel) { deleting = nil }
        } message: { Text(L("model3d_delete_text")) }
        .task { await repository.warmUp() }
        .accessibilityIdentifier("models3d_screen")
    }

    private func show(_ s: String) {
        message = s
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { if message == s { message = nil } }
    }

    private func importGlb(_ bytes: [UInt8], _ name: String, _ tris: Int) {
        importing = true
        Task {
            defer { importing = false }
            do {
                let r = try await repository.importGlb(bytes, name, tris)
                await repository.save(r.model)
                show(L("model3d_imported", r.model.name, r.sourceTriangles, r.triangles))
                open = OpenModel(id: r.model.id, modelId: r.model.id)
            } catch { show(L("model3d_import_failed", "\(error)")) }
        }
    }
}

/// 3B şeklin kartı: küçük resim ve ad.
struct Model3DCard: View {
    let info: Model3DInfo
    var selected = false
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            AsyncThumb(key: "\(info.id)@\(info.updatedAt)") { [url = info.thumbURL] in
                (try? Data(contentsOf: url)).flatMap { PixelCodec.decode([UInt8]($0)) }.map { Bitmaps.image(argb: $0.px, $0.w, $0.h) }
            }
            .aspectRatio(1, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
            Text(info.name).font(.callout.weight(.medium)).lineLimit(1)
        }
        .padding(8)
        .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(selected ? Color.accentColor : .clear, lineWidth: 2))
        .shadow(color: .black.opacity(0.06), radius: 5, y: 2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(info.name)
    }
}
