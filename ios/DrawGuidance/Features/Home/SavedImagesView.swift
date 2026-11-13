import QuickLook
import SwiftUI

/// v39 Kaydedilenler (Android `ImagesScreen`): kaydedilen resimler ve belgeler (PNG, JPEG, PDF, CBZ); sıralama, aç
/// (Hızlı Bakış), paylaş ve sil.
struct SavedImagesView: View {
    let repo: ImagesRepository
    @ObservedObject var store: SavedImagesStore
    enum Sort: String, CaseIterable { case newest, oldest, name, size }
    @State private var sort: Sort = .newest
    @State private var deleting: SavedImage?
    @State private var preview: URL?

    init(repo: ImagesRepository) {
        self.repo = repo
        store = repo.store
    }

    private var shown: [SavedImage] {
        switch sort {
        case .newest: return store.images.sorted { $0.createdAt > $1.createdAt }
        case .oldest: return store.images.sorted { $0.createdAt < $1.createdAt }
        case .name: return store.images.sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
        case .size: return store.images.sorted { $0.sizeBytes > $1.sizeBytes }
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(L("images_count", store.images.count)).font(.subheadline).foregroundStyle(.secondary)
                    Spacer()
                    Picker(L("images_sort"), selection: $sort) {
                        Text(L("images_sort_newest")).tag(Sort.newest)
                        Text(L("images_sort_oldest")).tag(Sort.oldest)
                        Text(L("images_sort_name")).tag(Sort.name)
                        Text(L("images_sort_size")).tag(Sort.size)
                    }
                    .pickerStyle(.menu)
                    .accessibilityIdentifier("images_sort")
                }
                if store.images.isEmpty { Text(L("images_empty")).foregroundStyle(.secondary).padding(.vertical, 24) }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 14)], spacing: 14) {
                    ForEach(shown) { img in
                        let url = repo.url(img)
                        VStack(alignment: .leading, spacing: 6) {
                            SavedThumb(url: url, mime: img.mime)
                                .aspectRatio(1, contentMode: .fit)
                                .frame(maxWidth: .infinity)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            Text(img.title).font(.callout.weight(.medium)).lineLimit(1)
                            Text(L("image_size_line", img.width, img.height,
                                   ByteCountFormatter.string(fromByteCount: img.sizeBytes, countStyle: .file)))
                                .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                        }
                        .padding(8)
                        .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 18))
                        .shadow(color: .black.opacity(0.06), radius: 5, y: 2)
                        .onTapGesture { preview = url }
                        .contextMenu {
                            Button { preview = url } label: { Label(L("v42_saved_open"), systemImage: "eye") }
                            ShareLink(item: url) { Label(L("share_title"), systemImage: "square.and.arrow.up") }
                            Button(role: .destructive) { deleting = img } label: { Label(L("delete_image"), systemImage: "trash") }
                        }
                        .accessibilityIdentifier("saved_\(img.id)")
                    }
                }
            }
            .padding(16)
        }
        .navigationTitle(L("images_title"))
        .quickLookPreview($preview)
        .alert(L("delete_image"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button(L("delete_button"), role: .destructive) { if let d = deleting { repo.delete(d.id) }; deleting = nil }
            Button(L("cancel_button"), role: .cancel) { deleting = nil }
        } message: { Text(L("delete_image_confirm")) }
        .accessibilityIdentifier("images_screen")
    }
}

/// Kaydedilen dosyanın küçük resmi (resim değilse belge simgesi).
private struct SavedThumb: View {
    let url: URL
    let mime: String
    @State private var image: CGImage?

    var body: some View {
        ZStack {
            Color(uiColor: .secondarySystemBackground)
            if let image { Image(decorative: image, scale: 1).resizable().scaledToFill() } else if !mime.hasPrefix("image/") {
                Image(systemName: mime.contains("pdf") ? "doc.richtext" : "doc.zipper").font(.largeTitle).foregroundStyle(.secondary)
            }
        }
        .task(id: url) {
            guard mime.hasPrefix("image/") else { return }
            let u = url
            image = await Task.detached(priority: .utility) { (try? Data(contentsOf: u)).flatMap { CanvasImageCache.decode($0, maxSide: 320) } }.value
        }
    }
}
