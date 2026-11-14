import SwiftUI

/// Maskotun bir cümle kalıbı (Android `KidsLine`): metin anahtarı ve o cümlede bürüneceği görünüş.
struct KidsLine: Equatable {
    let text: String
    let look: MascotLook
    init(_ text: String, _ face: MascotFace, _ gesture: MascotGesture) { self.text = text; look = MascotLook(face: face, gesture: gesture) }
}

/// v37 maskotun cümleleri (Android `KidsLines`): öneri türüne göre kalıplar (öneriye göre hep aynısı), selam, ipucu, övgü.
enum KidsLines {
    static let suggestions: [KidsTopic: [KidsLine]] = [
        .TEMPLATE: [KidsLine("kids_say_template_1", .EXCITED, .THUMBS_UP), KidsLine("kids_say_template_2", .SURPRISED, .CHEER),
                    KidsLine("kids_say_template_3", .THINKING, .THINK)],
        .TEXT: [KidsLine("kids_say_text_1", .WINKING, .POINT), KidsLine("kids_say_text_2", .HAPPY, .BRUSH)],
        .LINE: [KidsLine("kids_say_line_1", .EXCITED, .CHEER), KidsLine("kids_say_line_2", .EXPLAINING, .BRUSH)],
    ]
    static let greeting = KidsLine("kids_mascot_hello", .HAPPY, .WAVE)
    static let tips = [
        KidsLine("kids_tip_1", .WINKING, .THUMBS_UP), KidsLine("kids_tip_2", .EXCITED, .POINT), KidsLine("kids_tip_3", .THINKING, .THINK),
        KidsLine("kids_tip_4", .EXPLAINING, .BRUSH), KidsLine("kids_tip_5", .SURPRISED, .CHEER),
    ]
    static let praise = [KidsLine("kids_praise_1", .PROUD, .CHEER), KidsLine("kids_praise_2", .EXCITED, .THUMBS_UP),
                         KidsLine("kids_praise_3", .WINKING, .THUMBS_UP)]

    /// Kotlin `Random(seed).nextInt(size)` ile aynı seçim.
    static func pick(_ list: [KidsLine], _ seed: Int64) -> KidsLine {
        var r = KotlinRandom(Int(truncatingIfNeeded: seed))
        return list[r.nextInt(list.count)]
    }
    static func pick(_ topic: KidsTopic, _ seed: Int64) -> KidsLine { pick(suggestions[topic]!, seed) }
}


/// Çocuk modu tercihleri (Android `KidsPrefs`): maskot açık mı, ana sayfa kartı, görev en iyi puanları.
@MainActor
final class KidsPrefs: ObservableObject {
    static let shared = KidsPrefs()
    private let d = UserDefaults.standard
    @Published private(set) var mascotOn: Bool
    @Published private(set) var homeCardDismissed: Bool
    @Published private(set) var challengeBest: [String: Int]

    private init() {
        mascotOn = d.bool(forKey: "kids.mascot_on")
        homeCardDismissed = d.bool(forKey: "kids.home_card_dismissed")
        challengeBest = Self.parseBest(d.string(forKey: "kids.challenge_best"))
    }

    func setMascotOn(_ on: Bool) { mascotOn = on; d.set(on, forKey: "kids.mascot_on") }
    func dismissHomeCard() { homeCardDismissed = true; d.set(true, forKey: "kids.home_card_dismissed") }

    /// Seviyenin puanı yazılır (en iyisi kalır); en iyi puan döner.
    @discardableResult
    func recordChallenge(_ levelId: String, _ score: Int) -> Int {
        let best = max(challengeBest[levelId] ?? 0, min(max(score, 0), 100))
        challengeBest[levelId] = best
        d.set(challengeBest.map { "\($0.key):\($0.value)" }.joined(separator: ","), forKey: "kids.challenge_best")
        return best
    }

    static func parseBest(_ raw: String?) -> [String: Int] {
        var out: [String: Int] = [:]
        for part in (raw ?? "").split(separator: ",").map(String.init) {
            guard let i = part.lastIndex(of: ":"), i > part.startIndex, let v = Int(part[part.index(after: i)...]) else { continue }
            out[String(part[..<i])] = v
        }
        return out
    }
}

/// Balondaki yazı harf harf yazılır (Android `Typing`); yazılırken ağız iki harfte bir açılıp kapanır.
@MainActor
final class Typing: ObservableObject {
    static let TYPE_MS: UInt64 = 32
    let text: String
    @Published var typed = 0
    private var task: Task<Void, Never>?

    init(_ text: String) {
        self.text = text
        task = Task { [weak self] in
            while let s = self, !s.done {
                try? await Task.sleep(nanoseconds: Self.TYPE_MS * 1_000_000)
                if Task.isCancelled { return }
                self?.typed += 1
            }
        }
    }
    deinit { task?.cancel() }

    var done: Bool { typed >= text.count }
    var mouthOpen: Bool { !done && (typed / 2) % 2 == 0 }
    func skip() { typed = text.count }
}

/// Balonun düğmesi: büyük, yuvarlak (çocuk parmağına uygun).
enum MascotButtonKind { case PRIMARY, NEUTRAL, CLOSE }

struct MascotRoundButton: View {
    let icon: String
    let description: String
    let id: String
    let kind: MascotButtonKind
    var size: CGFloat = 46
    let action: () -> Void

    var body: some View {
        let colors: [Color] = switch kind {
        case .PRIMARY: [Color(red: 0.29, green: 0.87, blue: 0.5), Color(red: 0.09, green: 0.64, blue: 0.29)]
        case .NEUTRAL: [Color(red: 1, green: 0.82, blue: 0.4), Color(red: 0.96, green: 0.62, blue: 0.04)]
        case .CLOSE: [Color(red: 1, green: 0.54, blue: 0.54), Color(red: 0.9, green: 0.28, blue: 0.3)]
        }
        Button(action: action) {
            Image(systemName: icon).font(.system(size: size * 0.42, weight: .bold)).foregroundStyle(.white)
                .frame(width: size, height: size)
                .background(Circle().fill(LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom)))
                .overlay(Circle().stroke(Color.white.opacity(0.85), lineWidth: 2))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(description)
        .accessibilityIdentifier(id)
    }
}

/// Konuşma balonu biçimi: yuvarlak köşeli kutu ve altta (maskotun başına doğru) kuyruk.
struct MascotBalloonShape: Shape {
    var corner: CGFloat = 22
    var tail: CGFloat = 14
    var tailAt: CGFloat = 0.82

    func path(in rect: CGRect) -> Path {
        let r = corner, t = tail, h = rect.height - t
        let x = min(max(rect.width * tailAt, r + t), max(rect.width - r - t, r + t))
        var p = Path(roundedRect: CGRect(x: 0, y: 0, width: rect.width, height: h), cornerRadius: r)
        p.move(to: CGPoint(x: x - t * 0.9, y: h - 1))
        p.addQuadCurve(to: CGPoint(x: x + t * 0.35, y: h + t), control: CGPoint(x: x + t * 0.1, y: h + t * 0.55))
        p.addQuadCurve(to: CGPoint(x: x + t * 0.9, y: h - 1), control: CGPoint(x: x + t * 0.45, y: h + t * 0.35))
        p.closeSubpath()
        return p
    }
}

/// Maskotun konuşma balonu: başlık, harf harf yazılan metin (yer baştan ayrılır) ve büyük yuvarlak düğmeler.
struct MascotBalloon<Buttons: View>: View {
    @ObservedObject var typing: Typing
    var title: String?
    @ViewBuilder var buttons: () -> Buttons
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let shape = MascotBalloonShape()
        let dark = scheme == .dark
        VStack(alignment: .leading, spacing: 8) {
            if let title {
                Text(title).font(.subheadline.bold()).foregroundStyle(Color(red: 0.55, green: 0.36, blue: 0.96))
            }
            let shown = min(typing.typed, typing.text.count)
            (Text(String(typing.text.prefix(shown))) + Text(String(typing.text.dropFirst(shown))).foregroundColor(.clear))
                .font(.title3)
                .foregroundStyle(dark ? Color(red: 0.96, green: 0.95, blue: 1) : Color(red: 0.17, green: 0.13, blue: 0.25))
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityLabel(typing.text)
                .accessibilityIdentifier("kids_mascot_text")
            HStack(spacing: 10) { Spacer(); buttons() }
        }
        .padding(.leading, 16).padding(.trailing, 12).padding(.top, 12).padding(.bottom, 12 + 14)
        .frame(maxWidth: 320, alignment: .leading)
        .background(shape.fill(dark ? Color(red: 0.17, green: 0.18, blue: 0.27) : .white))
        .overlay(shape.stroke(LinearGradient(colors: [Color(red: 1, green: 0.73, blue: 0.22), Color(red: 0.65, green: 0.55, blue: 0.98)],
                                             startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 2.5))
        .shadow(color: .black.opacity(0.15), radius: 6, y: 2)
        .contentShape(Rectangle())
        .onTapGesture { typing.skip() }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("kids_mascot_balloon")
    }
}

/// Konuşan maskot: üstte balon (metin yoksa balon yok), altında maskot; yazı yazılırken ağzı oynar.
struct MascotSpeaker<Buttons: View>: View {
    let look: MascotLook
    let text: String?
    var title: String?
    var figure: CGFloat = 96
    var onMascot: (() -> Void)?
    var mascotDescription: String?
    @ViewBuilder var buttons: () -> Buttons

    var body: some View {
        VStack(alignment: .trailing, spacing: 2) {
            if let text {
                SpeakerContent(typing: Typing(text), look: look, title: title, figure: figure, onMascot: onMascot,
                               mascotDescription: mascotDescription, buttons: buttons)
                    .id(text)
            } else {
                figureView(mouthOpen: false)
            }
        }
    }

    private func figureView(mouthOpen: Bool) -> some View {
        MascotTap(look: look, mouthOpen: mouthOpen, figure: figure, onMascot: onMascot, mascotDescription: mascotDescription)
    }
}

/// Metin değişince yazma baştan başlar (`.id(text)` ile yeni [Typing]).
private struct SpeakerContent<Buttons: View>: View {
    @StateObject var typing: Typing
    let look: MascotLook
    let title: String?
    let figure: CGFloat
    let onMascot: (() -> Void)?
    let mascotDescription: String?
    let buttons: () -> Buttons

    init(typing: @autoclosure @escaping () -> Typing, look: MascotLook, title: String?, figure: CGFloat, onMascot: (() -> Void)?,
         mascotDescription: String?, buttons: @escaping () -> Buttons) {
        _typing = StateObject(wrappedValue: typing())
        self.look = look; self.title = title; self.figure = figure; self.onMascot = onMascot
        self.mascotDescription = mascotDescription; self.buttons = buttons
    }

    var body: some View {
        VStack(alignment: .trailing, spacing: 2) {
            MascotBalloon(typing: typing, title: title, buttons: buttons).padding(.trailing, 4)
            MascotTap(look: look, mouthOpen: typing.mouthOpen, figure: figure, onMascot: onMascot, mascotDescription: mascotDescription)
        }
    }
}

private struct MascotTap: View {
    let look: MascotLook
    let mouthOpen: Bool
    let figure: CGFloat
    let onMascot: (() -> Void)?
    let mascotDescription: String?

    var body: some View {
        let fig = MascotFigure(look: look, mouthOpen: mouthOpen)
            .frame(width: figure, height: figure)
            .contentShape(Rectangle())
            .accessibilityLabel(mascotDescription ?? "")
            .accessibilityIdentifier("kids_mascot")
        if let onMascot {
            fig.onTapGesture(perform: onMascot).accessibilityAddTraits(.isButton)
        } else {
            fig
        }
    }
}
