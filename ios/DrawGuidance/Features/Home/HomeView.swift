import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: CanvasStore
    @EnvironmentObject private var models3d: Model3DRepository
    @State private var openDoc: CanvasUi?

    private let advanced = CanvasKind.Advanced
    private let simple = CanvasKind.Simple

    var body: some View {
        NavigationStack {
            List {
                Section(LocalizedStringKey("home.new_advanced")) { kindRow(advanced) }
                Section(LocalizedStringKey("home.new_simple")) { kindRow(simple) }
                Section {
                    NavigationLink {
                        Model3DListView(repository: models3d)
                    } label: {
                        Label(L("model3d_my_models"), systemImage: "cube")
                    }
                    .accessibilityIdentifier("home_models3d")
                }
                Section(LocalizedStringKey("home.my_canvases")) {
                    if store.canvases.isEmpty {
                        Text(LocalizedStringKey("home.empty")).foregroundStyle(.secondary)
                    }
                    ForEach(store.canvases) { doc in
                        Button { openDoc = doc } label: {
                            Label(doc.name, systemImage: doc.kind.systemImage)
                        }
                        .accessibilityIdentifier("canvas_\(doc.name)")
                    }
                    .onDelete { idx in idx.map { store.canvases[$0].id }.forEach(store.delete) }
                }
            }
            .navigationTitle(LocalizedStringKey("app.name"))
            .navigationDestination(item: $openDoc) { doc in
                CanvasEditScreen(canvasLocalId: doc.localId)
            }
        }
    }

    @ViewBuilder
    private func kindRow(_ kinds: [CanvasKind]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(kinds) { kind in
                    Button {
                        let n = store.canvases.filter { $0.kind == kind }.count + 1
                        let title = NSLocalizedString(kind.titleKey, comment: "")
                        openDoc = store.create(kind: kind, name: "\(title) \(n)")
                    } label: {
                        VStack(spacing: 6) {
                            Image(systemName: kind.systemImage).font(.title2)
                            Text(LocalizedStringKey(kind.titleKey)).font(.caption).multilineTextAlignment(.center)
                        }
                        .frame(width: 96, height: 80)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.accentColor.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("new_\(kind.rawValue)")
                }
            }
            .padding(.vertical, 4)
        }
    }
}

extension CanvasUi: Hashable {
    func hash(into hasher: inout Hasher) { hasher.combine(localId) }
}
