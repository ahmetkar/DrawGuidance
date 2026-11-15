import SwiftUI

/// v53 not almada hızlı yazı kutusu (Android `QuickTextBox`): tuvalde uygulama temasında saydam bir metin kutusu açılır;
/// yazılar çizilmez, klavyeyle hızlıca yazılır. Her yazı bitince (Enter) kutu arka plansız sıradan yazıya dönüşür ve
/// hemen altında yeni kutu açılır. Kutu tutamağından sürüklenerek başka yere taşınır.
@MainActor
final class QuickTextState: ObservableObject {
    @Published var active = false
    /// Kutunun sol-üst köşesi (tuval birimi; yazının taban çizgisi bunun biraz altında).
    @Published var anchor: P?
    @Published var text = ""
    /// Kutunun genişliği (tuval birimi).
    @Published var width: Float = 600

    func close() { active = false; text = ""; anchor = nil }
}

extension EditorViewModel {
    /// Hızlı yazı kutusunu aç/kapat (yalnız not alma).
    func toggleQuickText() {
        if quickText.active { commitQuickText(advance: false); quickText.close(); return }
        guard let c = store.canvas, c.kind.isNotes else { return }
        texts.commitOpenDraft()
        let size = texts.settings.sizePx
        let v = viewport
        let topLeft = v.toCanvas(P(v.viewSize.w * 0.06, v.viewSize.h * 0.08))
        let x = topLeft.x.coerceIn(Float(c.width) * 0.04, Float(c.width) * 0.5)
        // Son yazının altından başlar (yoksa görünen alanın üstünden)
        let lastBottom = store.strokes.filter { $0.brushType == .TEXT }.compactMap { $0.points.map(\.y).max() }.max()
        let y = (lastBottom.map { $0 + size * 0.9 } ?? topLeft.y).coerceIn(size, Float(c.height) - size * 2)
        quickText.width = (Float(c.width) * 0.86 - x).coerceIn(size * 4, Float(c.width))
        quickText.anchor = P(x, y)
        quickText.text = ""
        if mode != .SELECT { setMode(.SELECT) }
        store.select([])
        quickText.active = true
    }

    /// Kutudaki yazıyı sıradan yazı olarak ekler; [advance] ise yeni kutu hemen altında açılır.
    func commitQuickText(advance: Bool = true) {
        guard let a = quickText.anchor else { return }
        let t = quickText.text
        quickText.text = ""
        guard let bottom = texts.insertQuick(t, at: P(a.x, a.y + texts.settings.sizePx * 0.7), width: quickText.width) else { return }
        if advance, let c = store.canvas {
            let next = bottom + texts.settings.sizePx * 0.45
            if next < Float(c.height) - texts.settings.sizePx { quickText.anchor = P(a.x, next) }
        }
    }
}

/// Tuvalin üstündeki hızlı yazı kutusu.
struct QuickTextOverlay: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var qt: QuickTextState
    @ObservedObject var viewport: ViewportState
    @ObservedObject var texts: TextController
    @FocusState private var focused: Bool
    @State private var drag: CGSize = .zero

    var body: some View {
        if qt.active, let a = qt.anchor {
            let p = viewport.toScreen(a)
            let scale: CGFloat = CGFloat(viewport.scale)
            let w: CGFloat = max(CGFloat(qt.width) * scale, 160)
            let boxW: CGFloat = w + 90
            let fontSize: CGFloat = max(CGFloat(texts.settings.sizePx) * scale, 12)
            let px: CGFloat = CGFloat(p.x) - 28 + boxW / 2 + drag.width
            let py: CGFloat = CGFloat(p.y) + 18 + drag.height
            let ink: Color = Color(hex: vm.brush.settings.colorHex == "#FFFFFF" ? "#1E1E1E" : vm.brush.settings.colorHex) ?? .primary
            HStack(alignment: .top, spacing: 4) {
                Image(systemName: "line.3.horizontal").font(.system(size: 12, weight: .bold)).foregroundStyle(Color.accentColor)
                    .frame(width: 22, height: 30)
                    .contentShape(Rectangle())
                    .gesture(DragGesture().onChanged { drag = $0.translation }.onEnded { g in
                        qt.anchor = P(a.x + Float(g.translation.width / scale), a.y + Float(g.translation.height / scale))
                        drag = .zero
                    })
                    .accessibilityLabel(L("v53_quick_text_move"))
                TextField(L("v53_quick_text_hint"), text: $qt.text)
                    .font(.system(size: fontSize))
                    .foregroundStyle(ink)
                    .focused($focused)
                    .submitLabel(.next)
                    .onSubmit { vm.commitQuickText(); focused = true }
                    .accessibilityIdentifier("quick_text_field")
                Button { vm.commitQuickText(); focused = true } label: {
                    Image(systemName: "return").font(.system(size: 13, weight: .bold))
                        .frame(width: 28, height: 28).background(Circle().fill(Color.accentColor.opacity(0.18)))
                }
                .accessibilityLabel(L("v53_quick_text_next"))
                .accessibilityIdentifier("quick_text_commit")
                Button { vm.toggleQuickText() } label: {
                    Image(systemName: "xmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.secondary)
                        .frame(width: 28, height: 28).background(Circle().fill(Color.primary.opacity(0.07)))
                }
                .accessibilityLabel(L("close"))
                .accessibilityIdentifier("quick_text_close")
            }
            .padding(.horizontal, 6).padding(.vertical, 4)
            .frame(width: boxW, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Color.accentColor.opacity(0.07))
                    .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .strokeBorder(Color.accentColor.opacity(0.55), style: StrokeStyle(lineWidth: 1.2, dash: [6, 4])))
            )
            .position(x: px, y: py)
            .onAppear { focused = true }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("quick_text_box")
        }
    }
}
