import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var library: CanvasLibrary
    @State private var openDoc: CanvasDocument?

    private let advanced: [CanvasKind] = [.art, .notes, .comic]
    private let simple: [CanvasKind] = [.simpleArt, .simpleNotes, .kids]

    var body: some View {
        NavigationStack {
            List {
                Section(LocalizedStringKey("home.new_advanced")) { kindRow(advanced) }
                Section(LocalizedStringKey("home.new_simple")) { kindRow(simple) }
                Section(LocalizedStringKey("home.my_canvases")) {
                    if library.canvases.isEmpty {
                        Text(LocalizedStringKey("home.empty")).foregroundStyle(.secondary)
                    }
                    ForEach(library.canvases) { doc in
                        Button { openDoc = doc } label: {
                            Label(doc.name, systemImage: doc.kind.systemImage)
                        }
                        .accessibilityIdentifier("canvas_\(doc.name)")
                    }
                    .onDelete { idx in idx.map { library.canvases[$0].id }.forEach(library.delete) }
                }
            }
            .navigationTitle(LocalizedStringKey("app.name"))
            .navigationDestination(item: $openDoc) { doc in
                EditorView(document: doc)
            }
        }
    }

    @ViewBuilder
    private func kindRow(_ kinds: [CanvasKind]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(kinds) { kind in
                    Button {
                        let n = library.canvases.filter { $0.kind == kind }.count + 1
                        let title = NSLocalizedString(kind.titleKey, comment: "")
                        openDoc = library.create(kind: kind, name: "\(title) \(n)")
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

extension CanvasDocument: Hashable {
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}
