import SwiftUI

/// Öneri balonundaki bir seçenek (Android `SuggestOption`).
struct SuggestOption: Identifiable {
    let id: String
    let text: String
    let icon: String
    let cd: String
    var badge: String? = nil
    /// v39 Premium kilidi: öneri gösterilir, uygulamak kilitliyse "Premium gerekli" açılır.
    var premium: PremiumFeature? = nil
    let action: () -> Void
}

/// v23/v38 yapay zekâ öneri balonu (Android `SuggestBalloon`): başlık, seçenekler, kapat ve önerileri kapat.
struct SuggestBalloon: View {
    let title: String
    let options: [SuggestOption]
    let kind: CanvasKind?
    let onDismiss: () -> Void
    let onTurnOff: (() -> Void)?
    let turnOffLabel: String
    let id: String
    @ObservedObject private var sub = SubscriptionStore.shared
    @State private var premiumOpen = false

    init(title: String, options: [SuggestOption], kind: CanvasKind?, onDismiss: @escaping () -> Void, onTurnOff: (() -> Void)?,
         turnOffLabel: String, id: String) {
        self.title = title; self.options = options; self.kind = kind; self.onDismiss = onDismiss
        self.onTurnOff = onTurnOff; self.turnOffLabel = turnOffLabel; self.id = id
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(title).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                Spacer()
                if let onTurnOff {
                    Button(action: onTurnOff) { Image(systemName: "eye.slash") }
                        .accessibilityLabel(turnOffLabel).accessibilityIdentifier("\(id)_turn_off")
                }
                Button(action: onDismiss) { Image(systemName: "xmark") }
                    .accessibilityLabel(L("handwriting_dismiss")).accessibilityIdentifier("\(id)_dismiss")
            }
            .font(.footnote)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(options) { o in
                        let locked = o.premium.map { !sub.allows($0, kind) } ?? false
                        Button {
                            if locked { premiumOpen = true } else { o.action() }
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: o.icon)
                                Text(o.text).lineLimit(1)
                                if let b = o.badge { Text(b).font(.caption2).foregroundStyle(.secondary) }
                                if locked { Image(systemName: "crown.fill").font(.caption2).foregroundStyle(.yellow) }
                            }
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(Capsule().fill(Color.accentColor.opacity(0.12)))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(o.cd)
                        .accessibilityIdentifier(o.id)
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: 520)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.12), radius: 6, y: 2)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(id)
        .sheet(isPresented: $premiumOpen) { PremiumRequiredSheet() }
    }
}

/// v38 Premium kuralı: gelişmiş (çok parçalı, kütüphane, 3B) şekil önerisi (Android `PremiumRules.isAdvancedSuggestion`).
enum SuggestPremium {
    static let ADVANCED_SUGGEST_PARTS = 4
    static func isAdvanced(vector: Bool, solid: String?, parts: Int) -> Bool {
        if vector { return solid == nil || solid!.hasPrefix(ShapeLibrary.PREFIX) }
        if solid != nil { return true }
        return parts >= ADVANCED_SUGGEST_PARTS
    }
}

/// Büyüklerin tuvalinde yazı, şekil ve çizgi önerisi balonları (Android `TextSuggestionChip`, `ShapeSuggestionChip`,
/// `LineSuggestionChip`); çocuk tuvalinde öneriler maskotla söylenir.
struct SuggestionsHost: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var hw: HandwritingController

    var body: some View {
        let kind = vm.store.canvas?.kind
        if kind != .KIDS {
            VStack(spacing: 8) {
                if let s = hw.text {
                    SuggestBalloon(
                        title: L("text_suggest_title"),
                        options: ([s.text] + s.alternatives).enumerated().map { i, t in
                            SuggestOption(id: "text_option_\(i)", text: t, icon: "textformat", cd: "Öneri \(t)") { vm.handwriting.accept(t) }
                        },
                        kind: kind, onDismiss: { vm.handwriting.dismissText() }, onTurnOff: { vm.turnOffTextSuggestions() },
                        turnOffLabel: L("text_suggest_turn_off"), id: "text_suggest_chip")
                }
                if let s = hw.shape {
                    SuggestBalloon(title: L("shape_suggest_title"), options: Self.shapeOptions(vm, s), kind: kind,
                                   onDismiss: { vm.handwriting.dismissShape() }, onTurnOff: { vm.turnOffShapeSuggestions() },
                                   turnOffLabel: L("shape_suggest_turn_off"), id: "shape_suggest_chip")
                }
                if let s = hw.line {
                    let n = s.strokeIds.count
                    SuggestBalloon(
                        title: L("line_suggest_title"),
                        options: [SuggestOption(id: "line_fix_option", text: L("line_fix_all", n), icon: "wand.and.stars", cd: L("line_fix_cd", n)) {
                            vm.handwriting.acceptLines()
                        }],
                        kind: kind, onDismiss: { vm.handwriting.dismissLine() }, onTurnOff: { vm.turnOffLineSuggestions() },
                        turnOffLabel: L("line_suggest_turn_off"), id: "line_suggest_chip")
                }
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 12)
            .accessibilityIdentifier("handwriting_chip")
        }
    }

    static func shapeOptions(_ vm: EditorViewModel, _ s: ShapeSuggestion) -> [SuggestOption] {
        s.templates.enumerated().map { i, fix in
            let label: String
            if fix.vector { label = L("template_vector", ShapeFactory.title(fix.solid ?? "")) }
            else if let _ = fix.solid { label = L("template_solid", fix.name) }
            else { label = fix.name }
            let icon = fix.vector ? "seal" : fix.solid != nil ? "cube" : "square.on.circle"
            let pct = min(max(Int((fix.confidence * 100).rounded()), 0), 100)
            return SuggestOption(
                id: "template_option_\(i)", text: label, icon: icon, cd: L("template_apply_cd", label), badge: "%\(pct)",
                premium: SuggestPremium.isAdvanced(vector: fix.vector, solid: fix.solid, parts: fix.strokes.count) ? .SHAPE_SUGGEST_ADVANCED : nil
            ) { vm.handwriting.acceptTemplate(i) }
        }
    }
}
