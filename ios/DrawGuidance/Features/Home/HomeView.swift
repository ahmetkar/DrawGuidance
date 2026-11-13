import SwiftUI

/// Alt sekmeler (Android `MainTab` + `MainBottomBar`): Ana sayfa, Tuvallerim, Kaydedilenler, 3B Şekiller, Ayarlar.
struct HomeView: View {
    @EnvironmentObject private var store: CanvasStore
    @EnvironmentObject private var models3d: Model3DRepository
    enum Tab: Hashable { case home, canvases, images, models, settings }
    @State private var tab: Tab = .home
    @MainActor private static var entered = false

    var body: some View {
        TabView(selection: $tab) {
            OpenCanvasStack { open in HomeTab(onOpen: open, onSeeAll: { tab = .canvases }) }
                .tabItem { Label(L("tab_home"), systemImage: "house") }.tag(Tab.home)
            OpenCanvasStack { open in CanvasListView(onOpen: open) }
                .tabItem { Label(L("drawer_my_canvases"), systemImage: "square.grid.2x2") }.tag(Tab.canvases)
            NavigationStack {
                if let repo = EditorDependencies.shared.images { SavedImagesView(repo: repo) }
            }
            .tabItem { Label(L("drawer_my_images"), systemImage: "photo.on.rectangle") }.tag(Tab.images)
            NavigationStack { Model3DListView(repository: models3d) }
                .tabItem { Label(L("drawer_models3d"), systemImage: "cube") }.tag(Tab.models)
                .accessibilityIdentifier("home_models3d")
            NavigationStack { SettingsView(prefs: EditorDependencies.shared.prefs) }
                .tabItem { Label(L("drawer_settings"), systemImage: "gearshape") }.tag(Tab.settings)
                .accessibilityIdentifier("home_settings")
        }
        .onAppear {
            // v39 açılış reklamı (kurallara göre; ilk açılışta yok)
            if !Self.entered { Self.entered = true; AdsCoordinator.shared.onAppEntry() }
        }
    }
}

private struct OpenCanvasId: Hashable, Identifiable { let id: String }

/// Tuval açabilen gezinme yığını (her sekmenin kendi yığını).
private struct OpenCanvasStack<Content: View>: View {
    @ViewBuilder let content: (@escaping (CanvasUi) -> Void) -> Content
    @State private var open: OpenCanvasId?

    var body: some View {
        NavigationStack {
            content { open = OpenCanvasId(id: $0.localId) }
                .navigationDestination(item: $open) { doc in CanvasEditScreen(canvasLocalId: doc.id).toolbar(.hidden, for: .tabBar) }
        }
    }
}

/// v30 Ana sayfa (Android `HomeScreen`): günün saatine göre karşılama, kaldığın yerden devam (ya da çizmeye başla)
/// kartı, basit / gelişmiş çizim grupları (türler altta açılır), bırakıp kapattıkların, son çizimler ve Premium kartı.
private struct HomeTab: View {
    @EnvironmentObject private var store: CanvasStore
    @EnvironmentObject private var models3d: Model3DRepository
    @ObservedObject private var recent = EditorDependencies.shared.recent
    @ObservedObject private var sub = SubscriptionStore.shared
    let onOpen: (CanvasUi) -> Void
    let onSeeAll: () -> Void
    enum Group { case simple, advanced }
    @State private var expanded: Group?
    @State private var createKind: CanvasKind?
    @State private var new3d = false

    private var leftOff: [(CanvasUi, Int64)] {
        recent.entries.filter { $0.closedAt > 0 }.sorted { $0.closedAt > $1.closedAt }.compactMap { e in
            store.canvases.first { $0.localId == e.canvasId }.map { ($0, e.closedAt) }
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                hero
                create
                if leftOff.count > 1 {
                    row(L("home_left_title"), Array(leftOff.dropFirst().prefix(10)).map { ($0.0, L("home_left_ago", RelativeTime.text($0.1))) },
                        tag: "home_left_row")
                }
                if !store.canvases.isEmpty {
                    HStack {
                        Text(L("home_recent_title")).font(.headline)
                        Spacer()
                        Button(L("home_see_all"), action: onSeeAll).accessibilityIdentifier("home_see_all")
                    }
                    cards(Array(store.canvases.prefix(10)).map { ($0, L("home_edited_ago", RelativeTime.text($0.updatedAt))) }, tag: "home_recent_row")
                }
                if !sub.isPremium { premiumCard }
            }
            .padding(20)
            .frame(maxWidth: 920)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle(L("tab_home"))
        .toolbar(.hidden, for: .navigationBar)
        .accessibilityIdentifier("home_screen")
        .sheet(item: $createKind) { k in
            CanvasFormSheet(kind: k, kindChoices: k.simple ? CanvasKind.Simple : CanvasKind.Advanced) { r in
                onOpen(CanvasCreator.create(r, store: store))
            }
        }
        .fullScreenCover(isPresented: $new3d) { NavigationStack { Model3DEditorView(repository: models3d, modelId: nil) } }
    }

    private var greeting: String {
        switch Calendar.current.component(.hour, from: Date()) {
        case 5...10: return L("home_greeting_morning")
        case 11...16: return L("home_greeting_day")
        case 17...21: return L("home_greeting_evening")
        default: return L("home_greeting_night")
        }
    }

    private var header: some View {
        HStack(spacing: 14) {
            Image(systemName: "paintbrush.pointed.fill").font(.system(size: 28)).foregroundStyle(.white)
                .frame(width: 52, height: 52)
                .background(RoundedRectangle(cornerRadius: 14).fill(LinearGradient(colors: [.indigo, .purple], startPoint: .topLeading, endPoint: .bottomTrailing)))
            VStack(alignment: .leading, spacing: 2) {
                Text(greeting).font(.title2.bold()).accessibilityIdentifier("home_greeting")
                Text(L(store.canvases.isEmpty ? "home_welcome_first" : "home_welcome_back")).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }

    private var gradient: LinearGradient {
        LinearGradient(colors: [Color(red: 0.39, green: 0.40, blue: 0.95), Color(red: 0.55, green: 0.36, blue: 0.96)],
                       startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    @ViewBuilder private var hero: some View {
        if let first = leftOff.first {
            let c = first.0, closed = first.1
            Button { onOpen(c) } label: {
                HStack(spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(L("home_resume_title")).font(.caption.weight(.semibold)).foregroundStyle(.white.opacity(0.8))
                        Text(c.name).font(.title3.bold()).foregroundStyle(.white).lineLimit(2)
                        Text(c.kind.label + " · " + L("home_left_ago", RelativeTime.text(closed))).font(.caption).foregroundStyle(.white.opacity(0.8))
                        Label(L("home_resume_button"), systemImage: "play.fill").font(.subheadline.weight(.semibold))
                            .padding(.horizontal, 16).padding(.vertical, 9).background(Capsule().fill(.white)).foregroundStyle(.indigo)
                            .padding(.top, 6)
                    }
                    Spacer()
                    CanvasThumbnail(canvas: c, side: 118).frame(width: 102, height: 102).clipShape(RoundedRectangle(cornerRadius: 14))
                        .padding(8).background(RoundedRectangle(cornerRadius: 20).fill(.white.opacity(0.18)))
                }
                .padding(20)
                .background(RoundedRectangle(cornerRadius: 28).fill(gradient))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("home_resume")
        } else {
            Button { withAnimation { expanded = .advanced } } label: {
                VStack(alignment: .leading, spacing: 6) {
                    Text(L(store.canvases.isEmpty ? "home_start_title_first" : "home_start_title")).font(.title3.bold()).foregroundStyle(.white)
                    Text(L("home_start_text")).font(.subheadline).foregroundStyle(.white.opacity(0.85))
                    Text(L("home_start_button")).font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 16).padding(.vertical, 9).background(Capsule().fill(.white)).foregroundStyle(.indigo)
                        .padding(.top, 6)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(22)
                .background(RoundedRectangle(cornerRadius: 28).fill(gradient))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("home_start")
        }
    }

    private var create: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(L("home_create_title")).font(.headline)
            HStack(spacing: 12) {
                groupTile(.simple, L("home_new_simple"), L("v40_home_group_simple"), "scribble", .purple, "home_new_simple")
                groupTile(.advanced, L("home_new_advanced"), L("v40_home_group_advanced"), "paintbrush.fill", .indigo, "home_new_advanced")
            }
            if let g = expanded {
                VStack(spacing: 8) {
                    ForEach(g == .simple ? CanvasKind.Simple : CanvasKind.Advanced, id: \.self) { k in
                        kindRow(k.label, L(Self.kindHintKey(k)), k.systemImage, "new_\(k.rawValue)") { createKind = k; expanded = nil }
                    }
                    if g == .advanced {
                        kindRow(L("home_new_3d"), L("v40_home_kind_3d"), "cube", "new_3d") { new3d = true; expanded = nil }
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    private static func kindHintKey(_ k: CanvasKind) -> String {
        switch k {
        case .ART: return "v40_home_kind_art"
        case .NOTES: return "v40_home_kind_notes"
        case .COMIC: return "v40_home_kind_comic"
        case .SIMPLE_ART: return "v40_home_kind_simple_art"
        case .SIMPLE_NOTES: return "v40_home_kind_simple_notes"
        case .KIDS: return "v40_home_kind_kids"
        }
    }

    private func groupTile(_ g: Group, _ title: String, _ hint: String, _ icon: String, _ tint: Color, _ id: String) -> some View {
        let on = expanded == g
        return Button { withAnimation { expanded = on ? nil : g } } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: icon).font(.title3).foregroundStyle(tint)
                        .frame(width: 44, height: 44).background(Circle().fill(tint.opacity(on ? 0.24 : 0.15)))
                    Spacer()
                    Image(systemName: "chevron.down").rotationEffect(.degrees(on ? 180 : 0)).foregroundStyle(on ? tint : .secondary)
                }
                Text(title).font(.subheadline.weight(.semibold)).lineLimit(1)
                Text(hint).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 20).fill(Color(uiColor: on ? .secondarySystemBackground : .systemBackground)))
            .shadow(color: .black.opacity(on ? 0.02 : 0.07), radius: 6, y: 2)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func kindRow(_ title: String, _ hint: String, _ icon: String, _ id: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.title3).foregroundStyle(Color.accentColor).frame(width: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.subheadline.weight(.semibold))
                    Text(hint).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(uiColor: .secondarySystemBackground)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }

    private func row(_ title: String, _ items: [(CanvasUi, String)], tag: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline)
            cards(items, tag: tag)
        }
    }

    private func cards(_ items: [(CanvasUi, String)], tag: String) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 14) {
                ForEach(items, id: \.0.localId) { item in
                    let c = item.0, subtitle = item.1
                    Button { onOpen(c) } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            CanvasThumbnail(canvas: c, side: 136).frame(width: 136, height: 136)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.secondary.opacity(0.2), lineWidth: 1))
                            Text(c.name).font(.subheadline.weight(.medium)).lineLimit(1)
                            Text(subtitle).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                        }
                        .frame(width: 136)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("home_card_\(c.localId)")
                }
            }
        }
        .accessibilityIdentifier(tag)
    }

    private var premiumCard: some View {
        NavigationLink { SubscriptionView() } label: {
            HStack(spacing: 14) {
                Image(systemName: "crown.fill").font(.title).foregroundStyle(.yellow)
                VStack(alignment: .leading, spacing: 4) {
                    Text(L("v39_home_premium_title")).font(.headline)
                    Text(L("v39_home_premium_body")).font(.caption).foregroundStyle(.secondary)
                    Text(L("v39_home_premium_cta")).font(.caption.weight(.semibold)).foregroundStyle(Color.accentColor)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 20).fill(Color(uiColor: .secondarySystemBackground)))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("home_premium")
    }
}

