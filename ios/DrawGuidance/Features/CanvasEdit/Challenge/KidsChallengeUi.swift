import SwiftUI
import UIKit

/*
 * v38 çocuk modunda çizim görevinin ekranı (Android `KidsChallengeUi`, `KidsChallengeScreens`): çocuk araç
 * çubuğundaki "Çizim Görevi" düğmesi ve küçük menüsü, kâğıttaki hedef (kesikli çizgiler, boyanacak yerler), üstte
 * seviye kartı, köşede yönlendiren maskot, sonuç ve İlerleme pencereleri.
 */

/// Düğmenin ve menülerin açık/kapalı durumu.
@MainActor
final class KidsChallengeUi: ObservableObject {
    @Published var menu = false
    @Published var progress = false
    func toggleMenu() { menu.toggle(); progress = false }
}

/// v39 Ana sayfadaki görev kartından açılacak seviye: tuval kimliği → seviye sırası (bir kez kullanılır).
enum ChallengeLaunch {
    private static var pending: (String, Int)?
    static func request(_ canvasLocalId: String, _ level: Int) { pending = (canvasLocalId, level) }
    static func take(_ canvasLocalId: String) -> Int? {
        guard let p = pending, p.0 == canvasLocalId else { return nil }
        pending = nil
        return p.1
    }
}

// ------------------------------------------------------------------------------------------------ görünüş

enum KidStyle {
    static let purple = Color(red: 0.545, green: 0.361, blue: 0.965)
    static let border = LinearGradient(colors: [Color(red: 1, green: 0.725, blue: 0.22), Color(red: 0.655, green: 0.545, blue: 0.98)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
    static func card(_ dark: Bool) -> LinearGradient {
        dark ? LinearGradient(colors: [Color(red: 0.23, green: 0.18, blue: 0.35), Color(red: 0.17, green: 0.18, blue: 0.27)], startPoint: .topLeading, endPoint: .bottomTrailing)
            : LinearGradient(colors: [Color(red: 1, green: 0.97, blue: 0.87), Color(red: 1, green: 0.9, blue: 0.945), Color(red: 0.93, green: 0.906, blue: 1)],
                             startPoint: .topLeading, endPoint: .bottomTrailing)
    }
    static func ink(_ dark: Bool) -> Color { dark ? Color(red: 0.96, green: 0.95, blue: 1) : Color(red: 0.17, green: 0.13, blue: 0.25) }
    static let green = Color(red: 0.086, green: 0.639, blue: 0.29)
    static let gold = Color(red: 0.72, green: 0.47, blue: 0.12)
}

enum KidPillKind { case PRIMARY, NEUTRAL, DANGER, PLAIN }

/// Çocuk parmağına uygun hap düğme (simge ve yazı).
struct KidPill: View {
    let text: String
    let icon: String?
    let kind: KidPillKind
    let id: String
    var fill = false
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors: [Color]? = switch kind {
        case .PRIMARY: [Color(red: 0.29, green: 0.87, blue: 0.5), KidStyle.green]
        case .NEUTRAL: [Color(red: 1, green: 0.82, blue: 0.4), Color(red: 0.96, green: 0.62, blue: 0.04)]
        case .DANGER: [Color(red: 1, green: 0.54, blue: 0.54), Color(red: 0.9, green: 0.28, blue: 0.3)]
        case .PLAIN: nil
        }
        let ink = colors != nil ? Color.white : KidStyle.ink(scheme == .dark)
        Button(action: action) {
            HStack(spacing: 7) {
                if let icon { Image(systemName: icon).font(.system(size: 17, weight: .bold)) }
                Text(text).font(.subheadline.bold()).lineLimit(1)
            }
            .foregroundStyle(ink)
            .padding(.horizontal, 16).padding(.vertical, 11)
            .frame(maxWidth: fill ? .infinity : nil)
            .background {
                if let colors { Capsule().fill(LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom)) }
                else { Capsule().fill(Color.white.opacity(scheme == .dark ? 0.08 : 0.75)) }
            }
            .overlay {
                if colors != nil { Capsule().stroke(Color.white.opacity(0.85), lineWidth: 2) }
                else { Capsule().stroke(KidStyle.border, lineWidth: 2) }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}

/// Üç yıldız (dolu olanlar sarı).
struct StarRow: View {
    let stars: Int
    let size: CGFloat
    var body: some View {
        HStack(spacing: size * 0.08) {
            ForEach(0..<3, id: \.self) { i in
                Image(systemName: i < stars ? "star.fill" : "star")
                    .font(.system(size: size * 0.8, weight: .bold))
                    .foregroundStyle(i < stars ? Color(red: 1, green: 0.7, blue: 0) : Color(red: 0.72, green: 0.69, blue: 0.81))
                    .frame(width: size, height: size)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("challenge_stars_cd", stars))
        .accessibilityValue("\(stars)")
    }
}

/// Seviyenin küçük resmi: çizme görevinde izlenecek çizgiler, boyama görevlerinde örnekteki renklerle boyanmış şekil.
struct ChallengeThumb: View {
    let level: ChallengeLevel

    var body: some View {
        if level.kind == .TRACE {
            let lines = ChallengeThumbs.lines(level)
            Canvas { ctx, size in
                guard let box = ChallengeScore.bounds(lines), box.width > 0, box.height > 0 else { return }
                let pad: CGFloat = 4
                let s = min((size.width - 2 * pad) / CGFloat(box.width), (size.height - 2 * pad) / CGFloat(box.height))
                let ox = (size.width - CGFloat(box.width) * s) / 2 - CGFloat(box.left) * s
                let oy = (size.height - CGFloat(box.height) * s) / 2 - CGFloat(box.top) * s
                var path = Path()
                for line in lines {
                    for (i, p) in line.enumerated() {
                        let q = CGPoint(x: ox + CGFloat(p.x) * s, y: oy + CGFloat(p.y) * s)
                        if i == 0 { path.move(to: q) } else { path.addLine(to: q) }
                    }
                }
                ctx.stroke(path, with: .color(KidStyle.purple), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
            }
        } else {
            GeometryReader { geo in
                if let img = ChallengeThumbs.render(level, Int(geo.size.width), Int(geo.size.height)) {
                    Image(uiImage: img).resizable().interpolation(.high)
                }
            }
        }
    }
}

/// Örnek resimlerin önbelleği.
@MainActor
enum ChallengeThumbs {
    private static var traces: [String: [[P]]] = [:]
    private static var images: [String: UIImage] = [:]

    static func lines(_ level: ChallengeLevel) -> [[P]] {
        if let l = traces[level.id] { return l }
        let l = ChallengeLevels.target(level, 1000, 1000).lines
        traces[level.id] = l
        return l
    }

    static func render(_ level: ChallengeLevel, _ w: Int, _ h: Int) -> UIImage? {
        guard w >= 8, h >= 8 else { return nil }
        let key = "\(level.id)|\(w)x\(h)"
        if let i = images[key] { return i }
        let pad: Float = 3
        let aspect = ShapeFactory.aspect(level.shape)
        let aw = Float(w) - 2 * pad, ah = Float(h) - 2 * pad
        let (bw, bh): (Float, Float) = aw / ah > aspect ? (ah * aspect, ah) : (aw, aw / aspect)
        let box = FRect((Float(w) - bw) / 2, (Float(h) - bh) / 2, (Float(w) + bw) / 2, (Float(h) + bh) / 2)
        var style = ChallengeLevels.inkStyle
        style.strokeWidth = 1.4
        guard let stroke = ChallengeLevels.build(level, box, id: "challenge-thumb", style: style, fills: ChallengeLevels.fills(level)) else { return nil }
        let fmt = UIGraphicsImageRendererFormat()
        fmt.scale = UIScreen.main.scale
        let img = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: fmt).image { c in
            ShapeRenderer.draw(c.cgContext, stroke, cache: false)
        }
        images[key] = img
        return img
    }
}

/// Maskotun puana göre yüzü (o an bir şey söylemiyorken): çizdikçe sevinci artar.
func challengeLiveLook(_ eval: ChallengeEval?, _ kind: ChallengeKind) -> MascotLook {
    let score = eval?.score ?? 0
    if eval == nil || score <= 0 || (kind == .TRACE && eval?.drawn != true) { return MascotLook(face: .HAPPY, gesture: .WAVE) }
    if score < 35 { return MascotLook(face: .HAPPY, gesture: .BRUSH) }
    if score < ChallengeScore.PASS { return MascotLook(face: .WINKING, gesture: .THUMBS_UP) }
    return MascotLook(face: .EXCITED, gesture: .CHEER)
}

// ------------------------------------------------------------------------------------------------ katman

/// v38 çizim görevinin katmanı (çocuk tuvalinde, çizim alanının üstünde). Eğitim sürerken gizlenir; yapay zekâ önerisi
/// söylenirken görevin maskotu çekilir.
struct KidsChallengeLayer: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var ctl: KidsChallengeController
    @ObservedObject var ui: KidsChallengeUi
    @ObservedObject var viewport: ViewportState
    @ObservedObject var hw: HandwritingController
    @ObservedObject private var prefs = KidsPrefs.shared
    @ObservedObject private var sub = SubscriptionStore.shared
    var tutorial = false
    @State private var premiumOpen = false

    private var suggesting: Bool { hw.text != nil || hw.shape != nil || hw.line != nil }

    var body: some View {
        let current = ctl.active ? ctl.run : nil
        ZStack {
            if !tutorial {
                if let current {
                    ChallengeGuide(run: current, live: ctl.live, viewport: viewport).allowsHitTesting(false)
                    VStack {
                        ChallengeCard(run: current) { _ = ctl.finish() }.padding(.top, 8).padding(.horizontal, 12)
                        Spacer()
                    }
                    if !suggesting {
                        ChallengeMascot(vm: vm, run: current, live: ctl.live)
                            .padding(.trailing, 10).padding(.bottom, 20)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    }
                }
                if current == nil && ctl.suggest && !suggesting {
                    let next = ctl.nextIndex()
                    ChallengeSuggestBubble(next: next, premium: ctl.premiumLocked(next), resumed: ctl.passed(0),
                                           onStart: { if ctl.acceptSuggestion() == .NEEDS_PREMIUM { premiumOpen = true } },
                                           onCancel: { ctl.cancelSuggestion() })
                        .padding(.trailing, 10).padding(.bottom, 20)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                }
                if ui.menu {
                    ChallengeMenu(run: current, onDismiss: { ui.menu = false }, onStart: {
                        ui.menu = false
                        if !ctl.start() && ctl.premiumLocked(ctl.nextIndex()) { premiumOpen = true }
                    }, onStop: { ui.menu = false; ctl.stop() }, onProgress: { ui.menu = false; ui.progress = true })
                }
                if let r = ctl.result {
                    Color.black.opacity(0.35).ignoresSafeArea()
                    ChallengeResultCard(result: r, onNext: {
                        if ctl.nextNeedsPremium() { ctl.dismissResult(); premiumOpen = true } else { ctl.next() }
                    }, onRetry: { _ = ctl.retry() }, onExit: { ctl.stop() }, onProgress: { ctl.dismissResult(); ui.progress = true })
                    .id("\(r.index)-\(r.score)")
                }
            }
        }
        .accessibilityIdentifier("challenge_layer")
        .onAppear {
            if let id = vm.store.canvas?.localId, let level = ChallengeLaunch.take(id) { ctl.open(level) }
            ctl.onKidsEntry()
        }
        .onChange(of: current?.seq) { _, seq in if seq != nil { viewport.fit() } }
        .sheet(isPresented: $ui.progress) {
            ChallengeProgressSheet(best: prefs.challengeBest, current: current?.index, reachable: ctl.reachable, premiumLocked: ctl.premiumLocked,
                                   onOpen: { i in ui.progress = false; ctl.open(i) },
                                   onPremium: { ui.progress = false; premiumOpen = true })
        }
        .sheet(isPresented: $premiumOpen) { PremiumRequiredSheet() }
    }
}

/// Kâğıttaki hedef (tuval koordinatında, görünümle birlikte): kesikli mor çizgiler ve boyanacak yerlerin renkli noktaları.
private struct ChallengeGuide: View {
    let run: ChallengeRun
    let live: ChallengeEval?
    @ObservedObject var viewport: ViewportState

    var body: some View {
        let target = run.target
        let showMarks = target.level.kind != .TRACE
        let marks: [PaintMark] = (live?.marks.isEmpty == false ? live?.marks : nil) ?? target.spots().map { PaintMark(at: $0.0, color: $0.1, done: false) }
        let sc = CGFloat(viewport.scale), ox = CGFloat(viewport.offset.x), oy = CGFloat(viewport.offset.y)
        Canvas { ctx, _ in
            var c = ctx
            c.translateBy(x: ox, y: oy)
            c.scaleBy(x: sc, y: sc)
            var path = Path()
            for line in target.lines {
                for (i, p) in line.enumerated() {
                    let q = CGPoint(x: CGFloat(p.x), y: CGFloat(p.y))
                    if i == 0 { path.move(to: q) } else { path.addLine(to: q) }
                }
            }
            c.stroke(path, with: .color(.white.opacity(0.7)), style: StrokeStyle(lineWidth: 7 / sc, lineCap: .round, lineJoin: .round))
            c.stroke(path, with: .color(KidStyle.purple.opacity(0.8)),
                     style: StrokeStyle(lineWidth: 3 / sc, lineCap: .round, lineJoin: .round, dash: [9 / sc, 7 / sc]))
            guard showMarks else { return }
            for m in marks {
                let r = 7 / sc
                let at = CGPoint(x: CGFloat(m.at.x), y: CGFloat(m.at.y))
                func dot(_ p: CGPoint, _ rr: CGFloat, _ color: Color) {
                    c.fill(Path(ellipseIn: CGRect(x: p.x - rr, y: p.y - rr, width: 2 * rr, height: 2 * rr)), with: .color(color))
                }
                dot(CGPoint(x: at.x, y: at.y + r * 0.12), r * 1.35, .black.opacity(0.18))
                dot(at, r * 1.3, .white)
                dot(at, r, Color(hex: m.color) ?? .gray)
                if m.done {
                    let cc = CGPoint(x: at.x + r * 0.95, y: at.y - r * 0.95)
                    dot(cc, r * 0.62, .white)
                    dot(cc, r * 0.5, KidStyle.green)
                    var tick = Path()
                    tick.move(to: CGPoint(x: cc.x - r * 0.24, y: cc.y + r * 0.02))
                    tick.addLine(to: CGPoint(x: cc.x - r * 0.06, y: cc.y + r * 0.2))
                    tick.addLine(to: CGPoint(x: cc.x + r * 0.26, y: cc.y - r * 0.18))
                    c.stroke(tick, with: .color(.white), style: StrokeStyle(lineWidth: r * 0.14, lineCap: .round, lineJoin: .round))
                }
            }
        }
        .accessibilityIdentifier("challenge_guide")
        .accessibilityValue("\(target.lines.count)/\(showMarks ? marks.count : 0)")
    }
}

/// Üstteki seviye kartı: örnek resim, "Seviye n / 50", şeklin adı, görevin türü ve Bitti.
private struct ChallengeCard: View {
    let run: ChallengeRun
    let onFinish: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let level = run.level, dark = scheme == .dark
        HStack(spacing: 8) {
            ChallengeThumb(level: level).padding(3).frame(width: 50, height: 50)
                .background(RoundedRectangle(cornerRadius: 14).fill(.white))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(KidStyle.purple.opacity(0.35), lineWidth: 1.5))
                .accessibilityIdentifier("challenge_card_thumb")
            VStack(alignment: .leading, spacing: 1) {
                Text(L("challenge_level_of", level.number, ChallengeLevels.count)).font(.caption.bold()).foregroundStyle(KidStyle.purple)
                    .accessibilityIdentifier("challenge_card_level")
                Text(I18n.tr(level.title) + " · " + L(level.kind.label)).font(.subheadline.weight(.heavy)).foregroundStyle(KidStyle.ink(dark)).lineLimit(1)
            }
            Spacer(minLength: 4)
            KidPill(text: L("challenge_finish"), icon: "checkmark", kind: .PRIMARY, id: "challenge_finish", action: onFinish)
        }
        .padding(.horizontal, 8).padding(.vertical, 6)
        .frame(maxWidth: 460)
        .background(RoundedRectangle(cornerRadius: 22).fill(KidStyle.card(dark)))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(KidStyle.border, lineWidth: 2))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("challenge_card")
        .accessibilityValue(level.id)
    }
}

/// Köşedeki maskot: seviye başında görevi anlatır, ipucu düğmesiyle sırayla araçların ipuçlarını verir; hedefe
/// yaklaşınca "Bitti'ye dokun" der; konuşmuyorken yüzü puana göre değişir. Anlatım balonu ilk çizgide kapanır.
private struct ChallengeMascot: View {
    @ObservedObject var vm: EditorViewModel
    let run: ChallengeRun
    let live: ChallengeEval?
    @State private var said: (key: String, text: String, look: MascotLook)?
    @State private var tips = 0
    @State private var cheered = false
    @State private var seq = -1

    private var title: String { I18n.tr(run.level.title) }

    var body: some View {
        let close = (live?.score ?? 0) >= ChallengeScore.PASS && live?.drawn == true
        MascotSpeaker(look: said?.look ?? challengeLiveLook(live, run.level.kind), text: said?.text, title: L("kids_mascot_name"), figure: 84,
                      onMascot: nextTip, mascotDescription: L("challenge_mascot_cd")) {
            MascotRoundButton(icon: "xmark", description: L("kids_mascot_close"), id: "challenge_mascot_close", kind: .CLOSE) { said = nil }
            MascotRoundButton(icon: "lightbulb.fill", description: L("challenge_tip_next"), id: "challenge_mascot_tip", kind: .NEUTRAL, action: nextTip)
        }
        .accessibilityIdentifier("challenge_mascot")
        .accessibilityValue(said?.key ?? "idle")
        .onAppear { reset() }
        .onChange(of: run.seq) { _, _ in reset() }
        .onChange(of: vm.store.canvas?.strokes.count) { _, _ in if said?.key == "intro" { said = nil } }
        .onChange(of: close) { _, c in
            if c && !cheered { cheered = true; said = ("close", L("challenge_live_close"), MascotLook(face: .EXCITED, gesture: .CHEER)) }
        }
    }

    private func reset() {
        guard seq != run.seq else { return }
        seq = run.seq; tips = 0; cheered = false
        said = ("intro", L(run.level.kind.intro, run.level.number, title), MascotLook(face: .EXPLAINING, gesture: .WAVE))
    }

    private func nextTip() {
        let tools = ChallengeTool.tips(run.level)
        let i = tips % (tools.count + 1)
        tips += 1
        if i < tools.count {
            let t = tools[i]
            let text = t == .SHAPE ? L(t.tip, title.lowercased(with: Locale.current)) : L(t.tip)
            said = ("tip_\(t)".lowercased(), text, t.look)
        } else {
            said = ("tip_finish", L("challenge_tip_finish"), MascotLook(face: .PROUD, gesture: .POINT))
        }
    }
}

/// Düğmenin küçük menüsü: mod kapalıyken Başlat ve İlerleme, açıkken Modu kapat ve İlerleme.
private struct ChallengeMenu: View {
    let run: ChallengeRun?
    let onDismiss: () -> Void
    let onStart: () -> Void
    let onStop: () -> Void
    let onProgress: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let dark = scheme == .dark, ink = KidStyle.ink(dark)
        ZStack(alignment: .top) {
            Color.black.opacity(0.18).ignoresSafeArea().onTapGesture(perform: onDismiss).accessibilityIdentifier("challenge_menu_scrim")
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 18) {
                    MascotFigure(look: run != nil ? MascotLook(face: .WINKING, gesture: .THUMBS_UP) : MascotLook(face: .EXCITED, gesture: .WAVE), faceOnly: true)
                        .frame(width: 58, height: 58)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("kids_challenge")).font(.title2.weight(.heavy)).foregroundStyle(ink)
                        Text(run.map { L("challenge_menu_running", $0.level.number, I18n.tr($0.level.title)) } ?? L("challenge_menu_idle"))
                            .font(.subheadline).foregroundStyle(ink.opacity(0.8))
                    }
                }
                HStack(spacing: 10) {
                    if run == nil {
                        KidPill(text: L("challenge_start"), icon: "play.fill", kind: .PRIMARY, id: "challenge_start", fill: true, action: onStart)
                    } else {
                        KidPill(text: L("challenge_stop"), icon: "stop.fill", kind: .DANGER, id: "challenge_stop", fill: true, action: onStop)
                    }
                    KidPill(text: L("challenge_progress"), icon: "trophy.fill", kind: .NEUTRAL, id: "challenge_progress", fill: true, action: onProgress)
                }
            }
            .padding(16)
            .frame(maxWidth: 380)
            .background(RoundedRectangle(cornerRadius: 26).fill(KidStyle.card(dark)))
            .overlay(RoundedRectangle(cornerRadius: 26).stroke(KidStyle.border, lineWidth: 2.5))
            .padding(.top, 10).padding(.horizontal, 16)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("challenge_menu")
            .accessibilityValue(run != nil ? "running" : "idle")
        }
    }
}

/// v40 maskotun çizim görevi önerisi (ilk 5 girişte, görev açık değilken).
private struct ChallengeSuggestBubble: View {
    let next: Int
    let premium: Bool
    let resumed: Bool
    let onStart: () -> Void
    let onCancel: () -> Void

    var body: some View {
        let text = premium ? L("v40_challenge_suggest_premium") : resumed ? L("v40_challenge_suggest_resume", next + 1) : L("v40_challenge_suggest_new")
        MascotSpeaker(look: MascotLook(face: .EXCITED, gesture: .WAVE), text: text, title: L("kids_mascot_name"), figure: 84,
                      mascotDescription: L("v40_challenge_suggest_cd")) {
            KidPill(text: L("v40_challenge_suggest_cancel"), icon: "xmark", kind: .PLAIN, id: "challenge_suggest_cancel", action: onCancel)
            KidPill(text: L("v40_challenge_suggest_start"), icon: "play.fill", kind: .PRIMARY, id: "challenge_suggest_start", action: onStart)
        }
        .accessibilityIdentifier("challenge_suggest")
        .accessibilityValue(premium ? "premium" : "level_\(next + 1)")
    }
}

/// Seviye sonucu: maskot, puan, yıldızlar, maskotun sözü ve düğmeler (Sonraki / İlerleme / Tekrar dene, Moddan çık).
private struct ChallengeResultCard: View {
    let result: ChallengeResult
    let onNext: () -> Void
    let onRetry: () -> Void
    let onExit: () -> Void
    let onProgress: () -> Void
    @StateObject private var typing: Typing
    @Environment(\.colorScheme) private var scheme

    init(result: ChallengeResult, onNext: @escaping () -> Void, onRetry: @escaping () -> Void, onExit: @escaping () -> Void, onProgress: @escaping () -> Void) {
        self.result = result; self.onNext = onNext; self.onRetry = onRetry; self.onExit = onExit; self.onProgress = onProgress
        _typing = StateObject(wrappedValue: Typing(Self.say(result)))
    }

    static func say(_ r: ChallengeResult) -> String {
        if !r.passed { return L("challenge_failed_say", ChallengeScore.PASS) }
        if r.last { return L("challenge_all_done") }
        return r.stars >= 3 ? L("challenge_passed_say_3") : L("challenge_passed_say")
    }

    var body: some View {
        let level = ChallengeLevels.all[result.index], dark = scheme == .dark, ink = KidStyle.ink(dark)
        let look = !result.passed ? MascotLook(face: .HAPPY, gesture: .THUMBS_UP)
            : result.stars >= 3 ? MascotLook(face: .EXCITED, gesture: .CHEER) : MascotLook(face: .PROUD, gesture: .CHEER)
        ScrollView {
            VStack(spacing: 10) {
                Text(L("challenge_level_of", level.number, ChallengeLevels.count) + " · " + I18n.tr(level.title))
                    .font(.subheadline.bold()).foregroundStyle(KidStyle.purple)
                Text(L(result.passed ? "challenge_passed_title" : "challenge_failed_title")).font(.title2.weight(.heavy)).foregroundStyle(ink)
                    .multilineTextAlignment(.center).accessibilityIdentifier("challenge_result_title")
                HStack(spacing: 6) {
                    MascotFigure(look: look, mouthOpen: typing.mouthOpen).frame(width: 112, height: 112).accessibilityIdentifier("challenge_result_mascot")
                    VStack(spacing: 2) {
                        Text(L("challenge_score")).font(.subheadline.bold()).foregroundStyle(ink.opacity(0.7))
                        Text("\(result.score)").font(.system(size: 52, weight: .black))
                            .foregroundStyle(result.passed ? KidStyle.green : Color(red: 0.96, green: 0.62, blue: 0.04))
                            .accessibilityIdentifier("challenge_result_score")
                        StarRow(stars: result.stars, size: 30).accessibilityIdentifier("challenge_result_stars")
                        Text(L("challenge_best", result.best)).font(.caption.bold()).foregroundStyle(ink.opacity(0.65))
                    }
                }
                let shown = min(typing.typed, typing.text.count)
                (Text(String(typing.text.prefix(shown))) + Text(String(typing.text.dropFirst(shown))).foregroundColor(.clear))
                    .font(.headline).foregroundStyle(ink).multilineTextAlignment(.center)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 18).fill(dark ? Color(red: 0.17, green: 0.18, blue: 0.27) : .white))
                    .onTapGesture { typing.skip() }
                    .accessibilityLabel(typing.text)
                    .accessibilityIdentifier("challenge_result_say")
                VStack(spacing: 10) {
                    if result.passed && !result.last {
                        KidPill(text: L("challenge_next"), icon: "play.fill", kind: .PRIMARY, id: "challenge_next", fill: true, action: onNext)
                    } else if result.passed {
                        KidPill(text: L("challenge_progress"), icon: "trophy.fill", kind: .PRIMARY, id: "challenge_result_progress", fill: true, action: onProgress)
                    } else {
                        KidPill(text: L("challenge_retry"), icon: "arrow.clockwise", kind: .PRIMARY, id: "challenge_retry", fill: true, action: onRetry)
                    }
                    KidPill(text: L("challenge_exit"), icon: "rectangle.portrait.and.arrow.right", kind: .PLAIN, id: "challenge_exit", fill: true, action: onExit)
                }
                .padding(.top, 2)
            }
            .padding(.horizontal, 20).padding(.vertical, 18)
        }
        .scrollBounceBehavior(.basedOnSize)
        .frame(maxWidth: 380)
        .fixedSize(horizontal: false, vertical: true)
        .background(RoundedRectangle(cornerRadius: 30).fill(KidStyle.card(dark)))
        .overlay(RoundedRectangle(cornerRadius: 30).stroke(KidStyle.border, lineWidth: 2.5))
        .padding(20)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("challenge_result")
        .accessibilityValue(result.passed ? "passed" : "failed")
    }
}

/// Seviyenin İlerleme'deki durumu.
private enum LevelState { case PASSED, OPEN, LOCKED, PREMIUM }

/// İlerleme: bütün seviyeler (üçerli ızgara); geçilenler yıldızlarla, açıklar Oyna ile, kilitliler kilitle, Premium taçla.
private struct ChallengeProgressSheet: View {
    let best: [String: Int]
    let current: Int?
    let reachable: @MainActor (Int) -> Bool
    let premiumLocked: @MainActor (Int) -> Bool
    let onOpen: (Int) -> Void
    let onPremium: () -> Void
    @State private var lockedNote: Int?
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let levels = ChallengeLevels.all, dark = scheme == .dark, ink = KidStyle.ink(dark)
        let passed = levels.filter { (best[$0.id] ?? -1) >= ChallengeScore.PASS }.count
        let stars = levels.reduce(0) { $0 + ChallengeScore.stars(best[$1.id] ?? 0) }
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 18) {
                    MascotFigure(look: lockedNote != nil ? MascotLook(face: .THINKING, gesture: .THINK) : MascotLook(face: .PROUD, gesture: .THUMBS_UP), faceOnly: true)
                        .frame(width: 64, height: 64)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("challenge_progress")).font(.title2.weight(.heavy)).foregroundStyle(ink)
                        Text(lockedNote.map { L("challenge_locked_say", $0) } ?? L("challenge_progress_summary", passed, levels.count, stars))
                            .font(.subheadline).foregroundStyle(lockedNote != nil ? Color(red: 0.9, green: 0.28, blue: 0.3) : ink.opacity(0.75))
                            .accessibilityIdentifier("challenge_progress_note")
                        if let first = levels.indices.first(where: premiumLocked) {
                            Label(L("v40_challenge_premium_note", first + 1, levels.count), systemImage: "crown.fill")
                                .font(.caption.bold()).foregroundStyle(ink.opacity(0.75))
                                .accessibilityIdentifier("challenge_premium_note")
                        }
                    }
                }
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                    ForEach(levels.indices, id: \.self) { i in
                        let level = levels[i], score = best[level.id]
                        let state: LevelState = premiumLocked(i) ? .PREMIUM : (score ?? -1) >= ChallengeScore.PASS ? .PASSED : reachable(i) ? .OPEN : .LOCKED
                        LevelTile(level: level, state: state, score: score, current: current == i) {
                            switch state {
                            case .LOCKED: lockedNote = (levels.firstIndex { (best[$0.id] ?? -1) < ChallengeScore.PASS } ?? i) + 1
                            case .PREMIUM: onPremium()
                            default: onOpen(i)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(KidStyle.card(dark).opacity(0.5).ignoresSafeArea())
        .presentationDetents([.large])
        .accessibilityIdentifier("challenge_progress_sheet")
    }
}

private struct LevelTile: View {
    let level: ChallengeLevel
    let state: LevelState
    let score: Int?
    let current: Bool
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let dark = scheme == .dark, ink = KidStyle.ink(dark)
        let dim = state == .LOCKED || state == .PREMIUM
        let stateText: String = switch state {
        case .PASSED: L("challenge_state_passed")
        case .OPEN: L("challenge_state_open")
        case .LOCKED: L("challenge_state_locked")
        case .PREMIUM: L("v40_challenge_state_premium")
        }
        let borderColors: [Color] = current ? [KidStyle.purple, KidStyle.purple]
            : state == .PASSED ? [Color(red: 0.29, green: 0.87, blue: 0.5), KidStyle.green]
            : state == .OPEN ? [Color(red: 1, green: 0.725, blue: 0.22), Color(red: 0.655, green: 0.545, blue: 0.98)]
            : state == .PREMIUM ? [Color(red: 0.85, green: 0.65, blue: 0.13), Color(red: 1, green: 0.85, blue: 0.54)]
            : [Color(red: 0.79, green: 0.76, blue: 0.85), Color(red: 0.79, green: 0.76, blue: 0.85)]
        Button(action: action) {
            VStack(spacing: 4) {
                ZStack {
                    ChallengeThumb(level: level).opacity(dim ? 0.35 : 1)
                    Text("\(level.number)").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 24, height: 24).background(Circle().fill(dim ? Color(red: 0.72, green: 0.69, blue: 0.81) : KidStyle.purple))
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    switch state {
                    case .PASSED:
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(KidStyle.green).font(.system(size: 20))
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    case .LOCKED: Image(systemName: "lock.fill").font(.system(size: 26)).foregroundStyle(Color(red: 0.56, green: 0.53, blue: 0.65))
                    case .PREMIUM: Image(systemName: "crown.fill").font(.system(size: 22)).foregroundStyle(.yellow)
                    case .OPEN: EmptyView()
                    }
                }
                .frame(height: 62)
                Text(I18n.tr(level.title)).font(.subheadline.bold()).foregroundStyle(ink.opacity(dim ? 0.5 : 1)).lineLimit(1)
                switch state {
                case .PASSED: StarRow(stars: ChallengeScore.stars(score ?? 0), size: 16)
                case .OPEN: Label(L("challenge_play"), systemImage: "play.fill").font(.caption.bold()).foregroundStyle(KidStyle.green)
                case .LOCKED: Text(stateText).font(.caption).foregroundStyle(ink.opacity(0.45))
                case .PREMIUM: Text(stateText).font(.caption.bold()).foregroundStyle(KidStyle.gold)
                }
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 20).fill(dim ? Color(uiColor: .secondarySystemBackground) : Color.white.opacity(dark ? 0.06 : 1)))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(LinearGradient(colors: borderColors, startPoint: .topLeading, endPoint: .bottomTrailing),
                                                               lineWidth: current ? 3 : 2))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(level.number). \(I18n.tr(level.title))")
        .accessibilityValue(stateText)
        .accessibilityIdentifier("challenge_level_\(level.number)")
    }
}
