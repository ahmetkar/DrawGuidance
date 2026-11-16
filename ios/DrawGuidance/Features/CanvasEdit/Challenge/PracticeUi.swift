import SwiftUI

/*
 * v50 yetişkin çizim alıştırmalarının ekranı (Android `PracticeUi`): alıştırma listesi (gruplar, küçük resimler, en iyi
 * puan ve yıldızlar), kâğıttaki kılavuz (kesikli; kaybolan kılavuzda birkaç saniye sonra solar, simetride sol yarı ve
 * eksen), üstteki kart (ad, tür, anlık puan, İpucu, Liste, Bitti) ve sonuç kartı. Menüdeki "Alıştırmalar" düğmesi
 * `PracticeController.listOpen` değerini açar.
 */

private let guideBlue = Color(red: 0.145, green: 0.388, blue: 0.922)
private let axisRed = Color(red: 0.863, green: 0.149, blue: 0.149)

private func scoreColor(_ s: Int) -> Color {
    if s >= PracticeLevels.STARS[2] { return Color(red: 0.086, green: 0.639, blue: 0.29) }
    if s >= PracticeLevels.PASS { return Color(red: 0.396, green: 0.639, blue: 0.051) }
    if s >= 40 { return Color(red: 0.851, green: 0.467, blue: 0.024) }
    return .gray
}

struct PracticeLayer: View {
    @ObservedObject var ctl: PracticeController
    @ObservedObject var viewport: ViewportState
    @ObservedObject private var prefs = PracticePrefs.shared
    var tutorial = false
    @State private var guideAlpha: Double = 1
    @State private var introOpen = true
    @State private var rewardBusy = false
    @State private var rewardFailed = false
    @Environment(\.verticalSizeClass) private var vSize
    @Environment(\.horizontalSizeClass) private var hSize

    var body: some View {
        GeometryReader { geo in
            let portrait = geo.size.height >= geo.size.width
            ZStack(alignment: portrait ? .top : .topLeading) {
                if !tutorial, let run = ctl.run {
                    PracticeGuide(target: run.target, alpha: guideAlpha, viewport: viewport).allowsHitTesting(false)
                    // v51 küçük bildirim kartı: dikeyde üstte alçak satır, yatayda solda dar sütun (çizimi kapatmaz)
                    PracticeCard(run: run, count: ctl.levels().count, live: ctl.live, hints: ctl.hints, remaining: ctl.remaining,
                                 intro: introOpen, portrait: portrait,
                                 onToggleIntro: { introOpen.toggle() },
                                 onHint: { ctl.hint() }, onFinish: { _ = ctl.finish() }, onList: { ctl.listOpen = true }, onClose: { ctl.stop() })
                        .frame(maxWidth: portrait ? 460 : 260)
                        .padding(.top, 8).padding(.horizontal, portrait ? 10 : 8)
                        .frame(maxWidth: .infinity, alignment: portrait ? .center : .leading)
                    if let r = ctl.result {
                        Color.black.opacity(0.35).ignoresSafeArea()
                        PracticeResultCard(result: r, level: ctl.levels().indices.contains(r.index) ? ctl.levels()[r.index] : nil,
                                           onNext: { _ = ctl.next() }, onRetry: { _ = ctl.retry() },
                                           onList: { ctl.dismissResult(); ctl.listOpen = true }, onClose: { ctl.stop() })
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
            }
            .frame(width: geo.size.width, height: geo.size.height, alignment: portrait ? .top : .topLeading)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("practice_layer")
        .onChangeCompat(of: ctl.run?.seq) { _, seq in
            if seq != nil {
                viewport.fit(); fade()
                introOpen = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 4.5) { if ctl.run?.seq == seq { withAnimation { introOpen = false } } }
            }
        }
        .onChangeCompat(of: ctl.hints) { _, _ in fade() }
        .sheet(isPresented: Binding(get: { ctl.listOpen }, set: { ctl.listOpen = $0 })) {
            PracticeSheet(levels: ctl.levels(), best: prefs.best, current: ctl.run?.index, active: ctl.active,
                          locked: { l in !prefs.unlocked.contains(l.id) && ctl.isLocked(l) },
                          onOpen: { i in ctl.listOpen = false; ctl.open(i) }, onStop: { ctl.listOpen = false; ctl.stop() })
        }
        .alert(L("v51_practice_reward_title"), isPresented: Binding(get: { ctl.locked != nil }, set: { if !$0 && !rewardBusy { ctl.locked = nil } })) {
            Button(L("v51_practice_reward_watch")) { watchReward() }
            Button(L("v51_practice_reward_later"), role: .cancel) { rewardFailed = false; ctl.locked = nil }
        } message: {
            let name = ctl.locked.flatMap { i in ctl.levels().indices.contains(i) ? ctl.levels()[i] : nil }
                .map { I18n.tr($0.title) + " · " + L($0.kind.label) + "\n\n" } ?? ""
            Text(name + L(rewardFailed ? "v51_practice_reward_failed" : "v51_practice_reward_body"))
        }
        .overlay { if rewardBusy { ProgressView().padding(20).background(RoundedRectangle(cornerRadius: 14).fill(.regularMaterial)) } }
    }

    /// v51 ödüllü reklam: kazanılınca (ya da Premium'da) alıştırma kalıcı olarak açılır.
    private func watchReward() {
        guard let index = ctl.locked else { return }
        rewardBusy = true
        rewardFailed = false
        AdsCoordinator.shared.requestReward { outcome in
            rewardBusy = false
            switch outcome {
            case .EARNED, .FREE: ctl.unlockAndOpen(index)
            case .CANCELLED: ctl.locked = nil
            case .UNAVAILABLE: rewardFailed = true; ctl.locked = index
            }
        }
    }

    /// Kaybolan kılavuz: ilk kurulumda 5 sn, ipucunda 2,5 sn görünür, sonra solar.
    private func fade() {
        guideAlpha = 1
        guard let run = ctl.run, run.level.kind == .FADE else { return }
        let seq = run.seq, hints = ctl.hints
        let wait: Double = hints == 0 ? 5 : 2.5
        DispatchQueue.main.asyncAfter(deadline: .now() + wait) {
            guard ctl.run?.seq == seq, ctl.hints == hints else { return }
            withAnimation(.easeOut(duration: 1.2)) { guideAlpha = 0 }
        }
    }
}

private struct PracticeGuide: View {
    let target: PracticeTarget
    let alpha: Double
    @ObservedObject var viewport: ViewportState

    var body: some View {
        let sc = CGFloat(viewport.scale), ox = CGFloat(viewport.offset.x), oy = CGFloat(viewport.offset.y)
        Canvas { ctx, _ in
            var c = ctx
            c.translateBy(x: ox, y: oy)
            c.scaleBy(x: sc, y: sc)
            if let x = target.axis {
                var axis = Path()
                let pad = 12 / sc
                axis.move(to: CGPoint(x: CGFloat(x), y: CGFloat(target.box.top) - pad))
                axis.addLine(to: CGPoint(x: CGFloat(x), y: CGFloat(target.box.bottom) + pad))
                c.stroke(axis, with: .color(axisRed.opacity(0.8)), style: StrokeStyle(lineWidth: 1.5 / sc, dash: [4 / sc, 4 / sc]))
            }
            if let r = target.reference {
                // v51 Bakarak çiz: örnek çerçevesi ve kesikli çizim çerçevesi; örnek düz çizgiyle
                let pad = 8 / sc
                let refRect = CGRect(x: CGFloat(r.left) - pad, y: CGFloat(r.top) - pad, width: CGFloat(r.width) + 2 * pad, height: CGFloat(r.height) + 2 * pad)
                c.fill(Path(roundedRect: refRect, cornerRadius: 10 / sc), with: .color(.white.opacity(0.85)))
                c.stroke(Path(roundedRect: refRect, cornerRadius: 10 / sc), with: .color(guideBlue.opacity(0.5)), lineWidth: 1.5 / sc)
                let b = target.box
                let boxRect = CGRect(x: CGFloat(b.left) - pad, y: CGFloat(b.top) - pad, width: CGFloat(b.width) + 2 * pad, height: CGFloat(b.height) + 2 * pad)
                c.stroke(Path(roundedRect: boxRect, cornerRadius: 10 / sc), with: .color(guideBlue.opacity(0.35)),
                         style: StrokeStyle(lineWidth: 1.5 / sc, dash: [8 / sc, 6 / sc]))
                var ref = Path()
                for line in target.guide {
                    for (i, p) in line.enumerated() {
                        let q = CGPoint(x: CGFloat(p.x), y: CGFloat(p.y))
                        if i == 0 { ref.move(to: q) } else { ref.addLine(to: q) }
                    }
                }
                c.stroke(ref, with: .color(Color(red: 0.12, green: 0.16, blue: 0.22)), style: StrokeStyle(lineWidth: 1.6 / sc, lineCap: .round, lineJoin: .round))
                return
            }
            guard alpha > 0.01 else { return }
            var path = Path()
            for line in target.guide {
                for (i, p) in line.enumerated() {
                    let q = CGPoint(x: CGFloat(p.x), y: CGFloat(p.y))
                    if i == 0 { path.move(to: q) } else { path.addLine(to: q) }
                }
            }
            c.stroke(path, with: .color(.white.opacity(0.55 * alpha)), style: StrokeStyle(lineWidth: 5 / sc, lineCap: .round, lineJoin: .round))
            c.stroke(path, with: .color(guideBlue.opacity(0.75 * alpha)),
                     style: StrokeStyle(lineWidth: 2 / sc, lineCap: .round, lineJoin: .round, dash: [7 / sc, 5 / sc]))
        }
        .accessibilityIdentifier("practice_guide")
    }
}

private let finishGreen = Color(red: 0.086, green: 0.639, blue: 0.29)

private func kindLook(_ kind: PracticeKind) -> (String, Color) {
    switch kind {
    case .TRACE: return ("figure.strengthtraining.traditional", Color(red: 0.145, green: 0.388, blue: 0.922))
    case .FADE: return ("lightbulb", Color(red: 0.576, green: 0.2, blue: 0.918))
    case .MIRROR: return ("arrow.left.and.right.righttriangle.left.righttriangle.right", axisRed)
    case .COPY: return ("eye", Color(red: 0.051, green: 0.58, blue: 0.533))
    case .TIMED: return ("timer", Color(red: 0.918, green: 0.345, blue: 0.047))
    }
}

/// v51 küçük bildirim kartı (Android `PracticeCard`): dikeyde tek alçak satır, yatayda dar sütun; açıklama başta bir süre
/// görünür, ada dokununca açılıp kapanır.
private struct PracticeCard: View {
    let run: PracticeRun
    let count: Int
    let live: Int?
    let hints: Int
    let remaining: Int?
    let intro: Bool
    let portrait: Bool
    let onToggleIntro: () -> Void
    let onHint: () -> Void
    let onFinish: () -> Void
    let onList: () -> Void
    let onClose: () -> Void

    var body: some View {
        let level = run.level
        VStack(alignment: .leading, spacing: 4) {
            if portrait {
                HStack(spacing: 6) {
                    badge(level.kind)
                    title(level)
                    Spacer(minLength: 2)
                    score
                    actions(level)
                    close
                }
            } else {
                HStack(spacing: 6) { badge(level.kind); title(level); Spacer(minLength: 2); close }
                HStack(spacing: 6) { score; Spacer(); actions(level) }
            }
            if intro {
                Text(L(level.kind.intro)).font(.caption2).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("practice_intro")
            }
        }
        .padding(.horizontal, 8).padding(.vertical, 5)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color(uiColor: .systemBackground).opacity(0.96)).shadow(radius: 3))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.primary.opacity(0.12), lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("practice_card")
    }

    private func badge(_ kind: PracticeKind) -> some View {
        let (icon, tint) = kindLook(kind)
        return Image(systemName: icon).font(.system(size: 14, weight: .semibold)).foregroundStyle(tint)
            .frame(width: 28, height: 28).background(RoundedRectangle(cornerRadius: 9).fill(tint.opacity(0.15)))
    }

    private func title(_ level: PracticeLevel) -> some View {
        Button(action: onToggleIntro) {
            VStack(alignment: .leading, spacing: 0) {
                Text(I18n.tr(level.title)).font(.footnote.weight(.semibold)).lineLimit(1)
                Text(L(level.kind.label) + " · " + L("v50_practice_level", run.index + 1, count)).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
            }
        }
        .buttonStyle(.plain)
    }

    private var score: some View {
        HStack(spacing: 4) {
            if let remaining {
                let urgent = remaining <= 5 && run.startedAt != nil
                Label("\(remaining)", systemImage: "timer").font(.caption.bold()).labelStyle(.titleAndIcon)
                    .foregroundStyle(urgent ? Color.red : Color.primary)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Capsule().fill((urgent ? Color.red : Color.accentColor).opacity(0.14)))
                    .accessibilityIdentifier("practice_timer")
            }
            Text("\(live ?? 0)").font(.footnote.bold()).foregroundStyle(.white)
                .padding(.horizontal, 7).padding(.vertical, 2).background(Capsule().fill(scoreColor(live ?? 0)))
                .accessibilityIdentifier("practice_live")
        }
    }

    @ViewBuilder private func actions(_ level: PracticeLevel) -> some View {
        if level.kind == .FADE {
            round("lightbulb", L("v50_practice_hint") + (hints > 0 ? " (\(hints))" : ""), "practice_hint", onHint)
        }
        round("list.bullet", L("v50_practice_list"), "practice_list", onList)
        Button(action: onFinish) {
            Image(systemName: "checkmark").font(.system(size: 15, weight: .bold)).foregroundStyle(.white)
                .frame(width: 32, height: 32).background(Circle().fill(finishGreen))
        }
        .accessibilityLabel(L("v50_practice_done"))
        .accessibilityIdentifier("practice_finish")
    }

    private func round(_ icon: String, _ label: String, _ id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.accentColor)
                .frame(width: 32, height: 32).background(Circle().fill(Color.accentColor.opacity(0.12)))
        }
        .accessibilityLabel(label)
        .accessibilityIdentifier(id)
    }

    private var close: some View {
        Button(action: onClose) { Image(systemName: "xmark").font(.caption.bold()).foregroundStyle(.secondary).frame(width: 28, height: 28) }
            .accessibilityLabel(L("v50_practice_close"))
    }
}

/// v51 sonuç kartı: renkli başlık, dolan puan halkası, yıldızlar, rekor, puan parçaları ve düğmeler.
private struct PracticeResultCard: View {
    let result: PracticeResult
    let level: PracticeLevel?
    let onNext: () -> Void
    let onRetry: () -> Void
    let onList: () -> Void
    let onClose: () -> Void
    @State private var ring: Double = 0

    var body: some View {
        let canNext = result.passed && !result.last
        let colors: [Color] = result.passed ? [Color(red: 0.13, green: 0.77, blue: 0.37), Color(red: 0.05, green: 0.65, blue: 0.91)]
            : [Color(red: 0.96, green: 0.62, blue: 0.04), Color(red: 0.94, green: 0.27, blue: 0.27)]
        ViewThatFits(in: .vertical) {
            content(canNext: canNext, colors: colors)
            ScrollView { content(canNext: canNext, colors: colors) }
        }
        .frame(maxWidth: 380)
        .background(RoundedRectangle(cornerRadius: 24).fill(Color(uiColor: .systemBackground)))
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .shadow(radius: 12)
        .padding(20)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("practice_result")
        .onAppear { withAnimation(.easeOut(duration: 0.9)) { ring = Double(result.score) / 100 } }
    }

    private func content(canNext: Bool, colors: [Color]) -> some View {
            VStack(spacing: 10) {
                VStack(spacing: 2) {
                    Text(L(result.timeUp ? "v51_practice_time_up" : (result.passed ? "v50_practice_passed" : "v50_practice_failed")))
                        .font(.title3.bold()).foregroundStyle(.white)
                    if let level {
                        Text(I18n.tr(level.title) + " · " + L(level.kind.label)).font(.footnote).foregroundStyle(.white.opacity(0.9)).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity).padding(.vertical, 16)
                .background(LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing))
                ZStack {
                    Circle().stroke(Color.primary.opacity(0.12), lineWidth: 10)
                    Circle().trim(from: 0, to: ring).stroke(scoreColor(result.score), style: StrokeStyle(lineWidth: 10, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    VStack(spacing: 0) {
                        Text("\(result.score)").font(.system(size: 34, weight: .heavy)).accessibilityIdentifier("practice_result_score")
                        Text(L("v51_practice_points")).font(.caption2).foregroundStyle(.secondary)
                    }
                }
                .frame(width: 118, height: 118).padding(.top, 6)
                StarRow(stars: result.stars, size: 28)
                if result.record {
                    Label(L("v51_practice_record"), systemImage: "trophy.fill").font(.caption.weight(.semibold)).foregroundStyle(.orange)
                        .padding(.horizontal, 10).padding(.vertical, 3).background(Capsule().fill(Color.orange.opacity(0.16)))
                }
                VStack(spacing: 7) {
                    bar(L("v51_practice_accuracy"), result.detail.accuracy)
                    bar(L("v51_practice_coverage"), result.detail.coverage)
                    if let sp = result.detail.speed { bar(L("v51_practice_speed"), sp) }
                }
                .padding(.horizontal, 20)
                Text(L("v50_practice_best", result.best)).font(.footnote).foregroundStyle(.secondary)
                if result.hints > 0 {
                    Text(L("v50_practice_hints_used", result.hints, result.hints * PracticeLevels.HINT_PENALTY)).font(.caption).foregroundStyle(.secondary)
                }
                VStack(spacing: 8) {
                    if canNext {
                        Button(action: onNext) { Label(L("v50_practice_next"), systemImage: "arrow.right").frame(maxWidth: .infinity) }
                            .buttonStyle(.borderedProminent).accessibilityIdentifier("practice_next")
                        Button(action: onRetry) { Text(L("v50_practice_retry")).frame(maxWidth: .infinity) }.buttonStyle(.bordered)
                    } else {
                        Button(action: onRetry) { Label(L("v50_practice_retry"), systemImage: "arrow.counterclockwise").frame(maxWidth: .infinity) }
                            .buttonStyle(.borderedProminent).accessibilityIdentifier("practice_retry")
                    }
                    HStack {
                        Button(L("v50_practice_list"), action: onList)
                        Spacer()
                        Button(L("v50_practice_close"), action: onClose)
                    }
                    .font(.subheadline)
                }
                .controlSize(.large)
                .padding(16)
            }
    }

    private func bar(_ label: String, _ value: Int) -> some View {
        HStack(spacing: 8) {
            Text(label).font(.footnote).frame(width: 96, alignment: .leading).lineLimit(1)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.primary.opacity(0.1))
                    Capsule().fill(scoreColor(value)).frame(width: g.size.width * CGFloat(value) / 100)
                }
            }
            .frame(height: 8)
            Text("\(value)").font(.footnote.weight(.semibold)).frame(width: 34, alignment: .trailing)
        }
    }
}

private struct PracticeSheet: View {
    let levels: [PracticeLevel]
    let best: [String: Int]
    let current: Int?
    let active: Bool
    let locked: (PracticeLevel) -> Bool
    let onOpen: (Int) -> Void
    let onStop: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(L("v50_practice_title")).font(.title3.bold())
                Text(L("v50_practice_subtitle")).font(.footnote).foregroundStyle(.secondary)
                if active { Button(L("v50_practice_stop"), action: onStop).buttonStyle(.bordered).accessibilityIdentifier("practice_stop") }
                let passed = levels.filter { (best[$0.id] ?? 0) >= PracticeLevels.PASS }.count
                Text(L("v50_practice_progress", passed, levels.count)).font(.caption).foregroundStyle(.secondary)
                ForEach(PracticeGroup.allCases, id: \.self) { group in
                    let items = levels.enumerated().filter { $0.element.group == group }
                    if !items.isEmpty {
                        Text(L(group.title)).font(.headline).padding(.top, 10)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 8)], spacing: 8) {
                            ForEach(items, id: \.offset) { i, level in
                                PracticeTile(level: level, score: best[level.id], current: i == current, locked: locked(level)) { onOpen(i) }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .accessibilityIdentifier("practice_sheet")
        .presentationDetents([.large])
    }
}

private struct PracticeTile: View {
    let level: PracticeLevel
    let score: Int?
    let current: Bool
    var locked = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 3) {
                PracticeThumb(level: level).frame(width: 84, height: 72)
                Text(I18n.tr(level.title)).font(.caption.weight(.medium)).lineLimit(1)
                Text(L(level.kind.label)).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                if let score { StarRow(stars: PracticeLevels.stars(score), size: 14) } else { Text("—").font(.caption).foregroundStyle(.secondary) }
            }
            .padding(6)
            .frame(maxWidth: .infinity)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(current ? Color.accentColor : Color.primary.opacity(0.15), lineWidth: current ? 2 : 1))
            .overlay(alignment: .topTrailing) {
                // v51 ödüllü reklamla açılır
                if locked {
                    Image(systemName: "play.circle.fill").font(.caption).foregroundStyle(Color(red: 0.96, green: 0.7, blue: 0.0))
                        .padding(4).background(RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.12, green: 0.11, blue: 0.29).opacity(0.9)))
                        .padding(4)
                        .accessibilityLabel(L("v51_practice_reward_badge"))
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("practice_tile_\(level.id)")
    }
}

/// Alıştırmanın küçük resmi: görünen kılavuz (simetride sol yarı ve eksen).
private struct PracticeThumb: View {
    let level: PracticeLevel
    // v52 küçük resim arka planda hesaplanır (90 alıştırmanın şekilleri ana iş parçacığında üretilince liste açılırken
    // uygulama donuyordu)
    @State private var loaded: PracticeTarget?

    var body: some View {
        let target = loaded ?? PracticeThumbs.cache[level.id]
        Canvas { ctx, size in
            guard let target, let box = ChallengeScore.bounds(target.lines), box.width > 0, box.height > 0 else { return }
            let pad: CGFloat = 4
            let s = min((size.width - 2 * pad) / CGFloat(box.width), (size.height - 2 * pad) / CGFloat(box.height))
            let ox = (size.width - CGFloat(box.width) * s) / 2 - CGFloat(box.left) * s
            let oy = (size.height - CGFloat(box.height) * s) / 2 - CGFloat(box.top) * s
            var path = Path()
            for line in target.guide {
                for (i, p) in line.enumerated() {
                    let q = CGPoint(x: ox + CGFloat(p.x) * s, y: oy + CGFloat(p.y) * s)
                    if i == 0 { path.move(to: q) } else { path.addLine(to: q) }
                }
            }
            ctx.stroke(path, with: .color(.primary.opacity(0.85)), style: StrokeStyle(lineWidth: 1.3, lineCap: .round, lineJoin: .round))
            if let x = target.axis {
                var axis = Path()
                let ax = ox + CGFloat(x) * s
                axis.move(to: CGPoint(x: ax, y: oy + CGFloat(box.top) * s))
                axis.addLine(to: CGPoint(x: ax, y: oy + CGFloat(box.bottom) * s))
                ctx.stroke(axis, with: .color(axisRed), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
            }
        }
        .task(id: level.id) {
            if PracticeThumbs.cache[level.id] != nil { return }
            let lv = level
            let t = await Task.detached(priority: .utility) { PracticeLevels.target(lv, 1000, 1000) }.value
            PracticeThumbs.cache[lv.id] = t
            loaded = t
        }
    }
}

@MainActor
private enum PracticeThumbs {
    static var cache: [String: PracticeTarget] = [:]
}
