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

    var body: some View {
        ZStack {
            if !tutorial, let run = ctl.run {
                PracticeGuide(target: run.target, alpha: guideAlpha, viewport: viewport).allowsHitTesting(false)
                VStack {
                    PracticeCard(run: run, count: ctl.levels().count, live: ctl.live, hints: ctl.hints,
                                 onHint: { ctl.hint() }, onFinish: { _ = ctl.finish() }, onList: { ctl.listOpen = true }, onClose: { ctl.stop() })
                        .padding(.top, 8).padding(.horizontal, 12)
                    Spacer()
                }
                if let r = ctl.result {
                    Color.black.opacity(0.35).ignoresSafeArea()
                    PracticeResultCard(result: r, onNext: { _ = ctl.next() }, onRetry: { _ = ctl.retry() },
                                       onList: { ctl.dismissResult(); ctl.listOpen = true }, onClose: { ctl.stop() })
                }
            }
        }
        .accessibilityIdentifier("practice_layer")
        .onChange(of: ctl.run?.seq) { _, seq in if seq != nil { viewport.fit(); fade() } }
        .onChange(of: ctl.hints) { _, _ in fade() }
        .sheet(isPresented: Binding(get: { ctl.listOpen }, set: { ctl.listOpen = $0 })) {
            PracticeSheet(levels: ctl.levels(), best: prefs.best, current: ctl.run?.index, active: ctl.active,
                          onOpen: { i in ctl.listOpen = false; ctl.open(i) }, onStop: { ctl.listOpen = false; ctl.stop() })
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

private struct PracticeCard: View {
    let run: PracticeRun
    let count: Int
    let live: Int?
    let hints: Int
    let onHint: () -> Void
    let onFinish: () -> Void
    let onList: () -> Void
    let onClose: () -> Void

    var body: some View {
        let level = run.level
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(I18n.tr(level.title)).font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text(L(level.kind.label) + " · " + L("v50_practice_level", run.index + 1, count)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer()
                Text(L("v50_practice_score", live ?? 0)).font(.headline.bold()).foregroundStyle(scoreColor(live ?? 0))
                    .accessibilityIdentifier("practice_live")
                Button(action: onClose) { Image(systemName: "xmark").font(.body.bold()).foregroundStyle(.secondary).frame(width: 34, height: 34) }
                    .accessibilityLabel(L("v50_practice_close"))
            }
            Text(L(level.kind.intro)).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 8) {
                Button(action: onList) { Label(L("v50_practice_list"), systemImage: "list.bullet") }.buttonStyle(.bordered)
                if level.kind == .FADE {
                    Button(action: onHint) { Label(L("v50_practice_hint") + (hints > 0 ? " (\(hints))" : ""), systemImage: "lightbulb") }
                        .buttonStyle(.bordered).accessibilityIdentifier("practice_hint")
                }
                Spacer()
                Button(L("v50_practice_done"), action: onFinish).buttonStyle(.borderedProminent).tint(Color(red: 0.086, green: 0.639, blue: 0.29))
                    .accessibilityIdentifier("practice_finish")
            }
            .font(.subheadline)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .frame(maxWidth: 520)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(uiColor: .systemBackground)).shadow(radius: 4))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.primary.opacity(0.12), lineWidth: 1))
        .accessibilityIdentifier("practice_card")
    }
}

private struct PracticeResultCard: View {
    let result: PracticeResult
    let onNext: () -> Void
    let onRetry: () -> Void
    let onList: () -> Void
    let onClose: () -> Void

    var body: some View {
        let canNext = result.passed && !result.last
        VStack(spacing: 10) {
            Text(L(result.passed ? "v50_practice_passed" : "v50_practice_failed")).font(.title3.bold())
            Text(L("v50_practice_score", result.score)).font(.system(size: 32, weight: .bold)).foregroundStyle(scoreColor(result.score))
                .accessibilityIdentifier("practice_result_score")
            StarRow(stars: result.stars, size: 28)
            Text(L("v50_practice_best", result.best)).foregroundStyle(.secondary)
            if result.hints > 0 {
                Text(L("v50_practice_hints_used", result.hints, result.hints * PracticeLevels.HINT_PENALTY)).font(.caption).foregroundStyle(.secondary)
            }
            HStack {
                if canNext {
                    Button(L("v50_practice_retry"), action: onRetry).buttonStyle(.bordered)
                    Button(L("v50_practice_next"), action: onNext).buttonStyle(.borderedProminent).accessibilityIdentifier("practice_next")
                } else {
                    Button(L("v50_practice_retry"), action: onRetry).buttonStyle(.borderedProminent).accessibilityIdentifier("practice_retry")
                }
            }
            HStack {
                Button(L("v50_practice_list"), action: onList)
                Button(L("v50_practice_close"), action: onClose)
            }
            .font(.subheadline)
        }
        .padding(20)
        .frame(maxWidth: 360)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color(uiColor: .systemBackground)))
        .padding(24)
    }
}

private struct PracticeSheet: View {
    let levels: [PracticeLevel]
    let best: [String: Int]
    let current: Int?
    let active: Bool
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
                                PracticeTile(level: level, score: best[level.id], current: i == current) { onOpen(i) }
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
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("practice_tile_\(level.id)")
    }
}

/// Alıştırmanın küçük resmi: görünen kılavuz (simetride sol yarı ve eksen).
private struct PracticeThumb: View {
    let level: PracticeLevel

    var body: some View {
        let target = PracticeThumbs.target(level)
        Canvas { ctx, size in
            guard let box = ChallengeScore.bounds(target.lines), box.width > 0, box.height > 0 else { return }
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
    }
}

@MainActor
private enum PracticeThumbs {
    static var cache: [String: PracticeTarget] = [:]
    static func target(_ level: PracticeLevel) -> PracticeTarget {
        if let t = cache[level.id] { return t }
        let t = PracticeLevels.target(level, 1000, 1000)
        cache[level.id] = t
        return t
    }
}
