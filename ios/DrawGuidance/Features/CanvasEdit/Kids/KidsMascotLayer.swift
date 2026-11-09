import SwiftUI

/// v37 çocuk tuvalinin maskotu (Android `KidsMascotLayer`), sağ alt köşede:
/// - yapay zekâ önerisi varsa (şekil > yazı > çizgi) maskot söyler: öneri türüne göre bir kalıp (öneriye göre hep aynı),
///   ✓ Uygula ve ✕ Kapat (öneriyi reddeder, maskotu kapatır),
/// - öneri yokken maskot açıksa köşede durur (dokununca ipucu söyler),
/// - kapalıysa küçük yüz düğmesi: dokununca maskot açılır ve selam verir. Eğitim sürerken gizlenir.
struct KidsMascotLayer: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var hw: HandwritingController
    var tutorial = false
    var challengeOn = false
    @ObservedObject private var prefs = KidsPrefs.shared
    @State private var talk: KidsLine?
    @State private var tips = 0
    @State private var praises = 0

    private struct Said { let line: KidsLine; let text: String; let apply: () -> Void; let dismiss: () -> Void }

    private func praise() {
        if prefs.mascotOn { talk = KidsLines.praise[praises % KidsLines.praise.count]; praises += 1 }
    }

    private var said: Said? {
        if let s = hw.shape, let fix = s.templates.first {
            let line = KidsLines.pick(.TEMPLATE, KidsSuggest.seed(s))
            let name = (fix.vector ? ShapeFactory.title(fix.solid ?? "") : I18n.tr(fix.name)).lowercased(with: Locale.current)
            return Said(line: line, text: L(line.text, name), apply: { vm.handwriting.acceptTemplate(0); praise() },
                        dismiss: { vm.handwriting.dismissShape() })
        }
        if let s = hw.text {
            let line = KidsLines.pick(.TEXT, KidsSuggest.seed(s))
            return Said(line: line, text: L(line.text, s.text), apply: { vm.handwriting.accept(s.text); praise() },
                        dismiss: { vm.handwriting.dismissText() })
        }
        if let s = hw.line {
            let line = KidsLines.pick(.LINE, KidsSuggest.seed(s))
            return Said(line: line, text: L(line.text, s.strokeIds.count), apply: { vm.handwriting.acceptLines(); praise() },
                        dismiss: { vm.handwriting.dismissLine() })
        }
        return nil
    }

    var body: some View {
        if !tutorial {
            content
                .padding(.trailing, 10)
                .padding(.bottom, 20)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                .accessibilityIdentifier("kids_mascot_layer")
        }
    }

    @ViewBuilder private var content: some View {
        let title = L("kids_mascot_name"), cd = L("kids_mascot_cd")
        if let said {
            MascotSpeaker(look: said.line.look, text: said.text, title: title, mascotDescription: cd) {
                MascotRoundButton(icon: "xmark", description: L("kids_mascot_close"), id: "mascot_close", kind: .CLOSE) {
                    said.dismiss(); talk = nil; prefs.setMascotOn(false)
                }
                MascotRoundButton(icon: "checkmark", description: L("kids_mascot_apply"), id: "mascot_apply", kind: .PRIMARY) { said.apply() }
            }
            .accessibilityIdentifier("kids_mascot_suggest")
        } else if challengeOn {
            EmptyView()
        } else if prefs.mascotOn {
            MascotSpeaker(look: talk?.look ?? .idle, text: talk.map { L($0.text) }, title: title,
                          onMascot: { talk = KidsLines.tips[tips % KidsLines.tips.count]; tips += 1 }, mascotDescription: cd) {
                MascotRoundButton(icon: "xmark", description: L("kids_mascot_close"), id: "mascot_close", kind: .CLOSE) {
                    talk = nil; prefs.setMascotOn(false)
                }
                MascotRoundButton(icon: "arrow.forward", description: L("kids_mascot_next"), id: "mascot_next", kind: .PRIMARY) { talk = nil }
            }
        } else {
            Button { prefs.setMascotOn(true); talk = KidsLines.greeting } label: {
                MascotFigure(look: .idle, faceOnly: true)
                    .frame(width: 52, height: 52)
                    .frame(width: 56, height: 56)
                    .background(Circle().fill(LinearGradient(colors: [Color(red: 1, green: 0.96, blue: 0.84), Color(red: 1, green: 0.85, blue: 0.54)],
                                                             startPoint: .top, endPoint: .bottom)))
                    .clipShape(Circle())
                    .overlay(Circle().stroke(LinearGradient(colors: [Color(red: 1, green: 0.73, blue: 0.22), Color(red: 0.65, green: 0.55, blue: 0.98)],
                                                            startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 2.5))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(L("kids_mascot_open"))
            .accessibilityIdentifier("kids_mascot_toggle")
        }
    }
}
