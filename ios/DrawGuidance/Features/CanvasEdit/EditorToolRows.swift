import SwiftUI

/// Bir araç düğmesi (Android `ToolItem`).
struct ToolButton: View {
    let icon: String
    let label: String
    var selected = false
    var enabled = true
    var id: String? = nil
    /// Eğitimde tanıtılan aracın yeri.
    var anchor: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon).frame(width: 36, height: 36)
                .background(RoundedRectangle(cornerRadius: 10).fill(selected ? Color.accentColor.opacity(0.2) : .clear))
        }
        .disabled(!enabled)
        .accessibilityLabel(label)
        .accessibilityIdentifier(id ?? label)
        .tutorialAnchor(anchor)
    }
}

/// v40 cetvel ve kalıp çubuğu (Android cetvel çubuğu): tür, birim, ölçek, kilit ve Bitir.
struct RulerBar: View {
    @ObservedObject var ruler: RulerController

    private static let types: [(RulerType, String)] = [
        (.STRAIGHT, "v40_ruler_straight"), (.PROTRACTOR, "v40_ruler_protractor"), (.SET_SQUARE_45, "v40_ruler_set45"),
        (.SET_SQUARE_30, "v40_ruler_set30"), (.COMPASS, "v40_ruler_compass"), (.ELLIPSE, "v40_ruler_ellipse"), (.FRENCH_CURVE, "v40_ruler_french"),
    ]

    var body: some View {
        HStack(spacing: 6) {
            if ruler.session == .RULER {
                Menu {
                    ForEach(Self.types, id: \.1) { t in
                        Button { ruler.setType(t.0) } label: {
                            if ruler.settings.type == t.0 { Label(L(t.1), systemImage: "checkmark") } else { Text(L(t.1)) }
                        }
                    }
                } label: { Image(systemName: "triangle").frame(width: 36, height: 36) }
                .accessibilityLabel(L("v40_ruler_type")).accessibilityIdentifier("ruler_type").tutorialAnchor(TutorialAnchor.RULER_TYPE)
                Menu {
                    ForEach(RulerUnit.allCases, id: \.self) { u in Button(u.label) { ruler.setUnit(u) } }
                } label: { Text(ruler.settings.unit.label).font(.caption.bold()).frame(width: 36, height: 36) }
                .accessibilityLabel(L("v40_ruler_unit")).accessibilityIdentifier("ruler_unit").tutorialAnchor(TutorialAnchor.RULER_UNIT)
                Menu {
                    ForEach([1, 2, 5, 10, 20, 50, 100], id: \.self) { k in Button("1:\(k)") { ruler.setScale(k) } }
                } label: { Text("1:\(ruler.settings.scale)").font(.caption.bold()).frame(minWidth: 36, minHeight: 36) }
                .accessibilityLabel(L("v40_ruler_scale")).accessibilityIdentifier("ruler_scale").tutorialAnchor(TutorialAnchor.RULER_SCALE)
            } else {
                ToolButton(icon: "books.vertical", label: L("v40_template_library"), id: "template_library", anchor: TutorialAnchor.TEMPLATE_LIBRARY) {
                    ruler.libraryOpen = true
                }
                if ruler.session == .TEMPLATE {
                    ToolButton(icon: ruler.locked ? "lock.fill" : "lock.open", label: L(ruler.locked ? "v40_template_unlock" : "v40_template_lock"),
                               selected: ruler.locked, id: "template_lock") { ruler.toggleLock() }
                }
            }
            Button(L("v40_ruler_finish")) { ruler.finish() }
                .font(.caption.bold()).buttonStyle(.borderedProminent)
                .accessibilityIdentifier("ruler_finish").tutorialAnchor(TutorialAnchor.RULER_FINISH)
        }
        .tutorialAnchor(TutorialAnchor.RULER_BAR)
    }
}

/// Yazı modunda açık taslak: metin alanı, onay ve vazgeç (Android `InlineTextEditor`).
struct TextDraftBar: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var texts: TextController
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        // v53 balon yazısı balonun üstünde yazılır (alttaki çubuk gösterilmez)
        if let draft = texts.draft, BalloonInlineEditor.balloonStroke(texts, vm.store) == nil {
            HStack(spacing: 8) {
                TextField(L("add_text"), text: $text, axis: .vertical)
                    .lineLimit(1...4)
                    .textFieldStyle(.roundedBorder)
                    .focused($focused)
                    .accessibilityIdentifier("text_draft_field")
                    .onChangeCompat(of: text) { _, t in texts.updateDraftText(t) }
                Button { vm.confirmText(text); text = "" } label: { Image(systemName: "checkmark.circle.fill").font(.title2) }
                    .accessibilityIdentifier("text_confirm")
                Button { texts.cancel(); text = "" } label: { Image(systemName: "xmark.circle").font(.title2) }
                    .accessibilityIdentifier("text_cancel")
            }
            .padding(10)
            .background(.regularMaterial)
            .onAppear { text = draft.content; focused = true }
            .onChangeCompat(of: draft.anchor) { _, _ in text = draft.content }
        }
    }
}
