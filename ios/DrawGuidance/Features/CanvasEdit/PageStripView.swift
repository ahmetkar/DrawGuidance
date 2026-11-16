import SwiftUI

/// v55 çizim alanının altındaki sayfa şeridi (Android `PageStrip`): çizgi roman ve not alma tuvallerinde sayfalar arası
/// geçiş, sayfa ekleme; bir sayfaya uzun basınca (ya da bağlam menüsünden) çoğalt / sil / kapak ekle.
/// Önceden iOS'ta yalnız ilk sayfa görünüyordu: sayfalar oluşturuluyor ama aralarında geçilemiyordu.
struct PageStripView: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @State private var menuFor: Int?

    var body: some View {
        if let c = store.canvas, c.kind.paged {
            let count = c.pageCount
            let current = c.currentPage
            let cover = c.hasCover
            let canAdd = count < c.kind.maxPages
            HStack(spacing: 8) {
                Text(PageStripView.position(page: current, count: count, cover: cover))
                    .font(.caption.weight(.semibold).monospacedDigit())
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier("page_position")
                ScrollViewReader { proxy in
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(0..<count, id: \.self) { index in
                                chip(index, current: current, cover: cover)
                                    .id(index)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                    .onAppear { proxy.scrollTo(current, anchor: .center) }
                    .onChangeCompat(of: current) { _, page in
                        withAnimation(.easeInOut(duration: 0.2)) { proxy.scrollTo(page, anchor: .center) }
                    }
                }
                if let index = menuFor, index < count {
                    if c.kind == .COMIC && !cover {
                        iconButton("book", L("v45_cover_add"), enabled: canAdd) { vm.v45.addCover(); menuFor = nil }
                            .accessibilityIdentifier("page_add_cover")
                    }
                    iconButton("plus.square.on.square", L("page_duplicate"), enabled: canAdd) { vm.pages.duplicatePage(index); menuFor = nil }
                        .accessibilityIdentifier("page_duplicate")
                    iconButton("trash", L("page_delete"), enabled: count > 1, role: .destructive) { vm.pages.deletePage(index); menuFor = nil }
                        .accessibilityIdentifier("page_delete")
                }
                iconButton("plus", L("page_add"), enabled: canAdd, prominent: true) { menuFor = nil; vm.pages.addPage() }
                    .accessibilityIdentifier("page_add")
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(.regularMaterial, in: Capsule())
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("page_strip")
            .tutorialAnchor(TutorialAnchor.PAGES)
        }
    }

    private func chip(_ index: Int, current: Int, cover: Bool) -> some View {
        let isCover = cover && index == 0
        let number = PageNumbering.number(page: index, cover: cover) ?? 0
        let label = isCover ? L("v45_cover") : L("page_number", number)
        let selected = index == current
        return Button {
            menuFor = nil
            vm.pages.goToPage(index)
        } label: {
            Text(isCover ? label : "\(number)")
                .font(.subheadline.weight(selected ? .bold : .regular).monospacedDigit())
                .foregroundStyle(selected ? Color.accentColor : Color.secondary)
                .padding(.horizontal, 14)
                .frame(height: 32)
                .background(
                    Capsule().fill(selected ? Color.accentColor.opacity(0.16) : Color(uiColor: .secondarySystemBackground))
                )
                .overlay(Capsule().stroke(selected ? Color.accentColor : Color.clear, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .simultaneousGesture(LongPressGesture(minimumDuration: 0.45).onEnded { _ in
            menuFor = menuFor == index ? nil : index
        })
        .contextMenu {
            Button { vm.pages.duplicatePage(index) } label: { Label(L("page_duplicate"), systemImage: "plus.square.on.square") }
            if (store.canvas?.pageCount ?? 1) > 1 {
                Button(role: .destructive) { vm.pages.deletePage(index) } label: { Label(L("page_delete"), systemImage: "trash") }
            }
        }
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier(isCover ? "page_chip_cover" : "page_chip_\(index)")
    }

    private func iconButton(_ symbol: String, _ label: String, enabled: Bool, prominent: Bool = false, role: ButtonRole? = nil,
                            action: @escaping () -> Void) -> some View {
        Button(role: role, action: action) {
            Image(systemName: symbol)
                .font(.body.weight(.semibold))
                .frame(width: 34, height: 34)
                .foregroundStyle(!enabled ? Color.secondary.opacity(0.4) : role == .destructive ? Color.red : prominent ? Color.accentColor : Color.primary)
                .background(Circle().fill(Color(uiColor: .secondarySystemBackground)))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    /// Şeritteki konum yazısı: kapakta "Kapak", kapaklı çizgi romanda numara ve toplam kapaksız ("1 / 3").
    static func position(page: Int, count: Int, cover: Bool) -> String {
        guard let n = PageNumbering.number(page: page, cover: cover) else { return L("v45_cover") }
        return L("page_of", n, max(1, PageNumbering.count(pageCount: count, cover: cover)))
    }
}
