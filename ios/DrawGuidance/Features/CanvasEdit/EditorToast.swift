import Combine
import SwiftUI

/// Düzenleyici olaylarının kısa bildirimleri (Android `CanvasEditScreen` içindeki `viewModel.events.collect` + Snackbar).
enum EditEventText {
    static func message(_ event: EditEvent) -> String? {
        switch event {
        case let .imageAdded(stored, original): return L("image_added", bytes(stored), bytes(original))
        case let .erasedRemoved(n): return L("erased_removed", n)
        case let .gapsFilled(n): return L("command_gaps_filled", n)
        case .commandNeedsSelection: return L("command_needs_selection")
        case .noGapsFound: return L("command_no_gaps")
        case .layerLocked: return L("layer_locked_event")
        case .saved: return L("saved_event")
        case .regionOpen: return L("region_open")
        case .noCorrection: return L("no_correction")
        case let .swiped(redo, done):
            return L(redo ? (done ? "swipe_redone" : "swipe_nothing_to_redo") : (done ? "swipe_undone" : "swipe_nothing_to_undo"))
        case .suggestionsOff: return L("suggestions_off_event")
        case .textSuggestionsOff: return L("text_suggest_off_event")
        case .shapeSuggestionsOff: return L("shape_suggest_off_event")
        case .selectionSuggestionsOff: return L("selection_suggest_off_event")
        case .draw3dOn: return L("draw3d_on_event")
        case .draw3dOff: return L("draw3d_off_event")
        case let .message(text): return I18n.tr(text)
        case let .transferred(name, empty):
            if empty { return L("transfer_empty") }
            guard let name else { return L("transfer_failed") }
            return L("transfer_done", name)
        case .aiOn: return L("ai_canvas_on_done")
        case .aiOff: return L("ai_canvas_off_done")
        case let .hiddenChanged(hidden, shown): return hidden > 0 ? L("hidden_done", hidden) : L("shown_done", shown)
        case let .backgroundRemoved(percent): return L("bg_removed_done", percent)
        case let .tutorialFinished(title): return L("tut_finished", L(title))
        case let .pageLimit(max): return L("page_limit_reached", max)
        case .paintMiss: return L("paint_miss")
        }
    }

    private static func bytes(_ n: Int) -> String { ByteCountFormatter.string(fromByteCount: Int64(n), countStyle: .file) }
}

/// Ekranın altında birkaç saniye görünen bildirim.
struct EditorToastHost: View {
    let events: PassthroughSubject<EditEvent, Never>
    @State private var text: String?
    @State private var hide: Task<Void, Never>?

    var body: some View {
        VStack {
            Spacer()
            if let text {
                Text(text)
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Capsule().fill(Color.black.opacity(0.78)))
                    .padding(.horizontal, 24).padding(.bottom, 28)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onTapGesture { withAnimation { self.text = nil } }
                    .accessibilityIdentifier("editor_toast")
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .allowsHitTesting(text != nil)
        .onReceive(events.receive(on: RunLoop.main)) { e in
            guard let m = EditEventText.message(e), !m.isEmpty else { return }
            withAnimation(.easeOut(duration: 0.2)) { text = m }
            hide?.cancel()
            hide = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                if !Task.isCancelled { withAnimation(.easeIn(duration: 0.2)) { text = nil } }
            }
        }
    }
}
