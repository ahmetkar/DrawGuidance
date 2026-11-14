import SwiftUI

// MARK: - Araçların yeri

/// Eğitimde tanıtılan araçların ekrandaki yerleri (Android `TutorialAnchors`): her araç `.tutorialAnchor(id)` ile bildirir.
struct TutorialAnchorKey: PreferenceKey {
    static let defaultValue: [String: Anchor<CGRect>] = [:]
    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) {
        value.merge(nextValue()) { _, new in new }
    }
}

extension View {
    /// Aracın yerini eğitim için bildirir (açıklama kutusu bu aracın altında açılır).
    @ViewBuilder func tutorialAnchor(_ id: String?) -> some View {
        if let id { anchorPreference(key: TutorialAnchorKey.self, value: .bounds) { [id: $0] } } else { self }
    }
}

private extension CGRect {
    var center: CGPoint { CGPoint(x: midX, y: midY) }
}

private extension P { var cg: CGPoint { CGPoint(x: CGFloat(x), y: CGFloat(y)) } }

// MARK: - Çizilmekte olan çizgiler

/// Eğitimde çizilmekte olan (henüz eklenmemiş) çizgiler; tuvalin üstünde, görünümle birlikte (Android `TutorialLiveLayer`).
struct TutorialLiveLayer: View {
    @ObservedObject var ctl: TutorialController
    @ObservedObject var viewport: ViewportState

    var body: some View {
        let strokes = ctl.live.strokes
        if !strokes.isEmpty {
            let sc = CGFloat(viewport.scale), ox = CGFloat(viewport.offset.x), oy = CGFloat(viewport.offset.y)
            Canvas { g, _ in
                g.withCGContext { c in
                    c.translateBy(x: ox, y: oy)
                    c.scaleBy(x: sc, y: sc)
                    for s in strokes { StrokeRenderer.drawStroke(c, s) }
                }
            }
            .allowsHitTesting(false)
            .accessibilityIdentifier("tutorial_live")
        }
    }
}

// MARK: - Eğitim katmanı

/// v32 eğitim katmanı (Android `TutorialOverlay`): tanıtılan aracın çevresinde nabız gibi atan vurgu halkası, aracın hemen
/// altında (yer yoksa üstünde; çizim alanı adımlarında alanın altında) açıklama kutusu ve kutudan araca uzanan ok, çizim
/// yapan "parmak". Eğitim sürerken ekrana dokunmalar engellenir; yalnız yapay zekâ önerisi vurgulanınca öneri balonuna
/// dokunulabilir. Çocuk tuvalinde açıklamalar maskotun balonunda.
struct TutorialOverlay: View {
    @ObservedObject var ctl: TutorialController
    @ObservedObject var viewport: ViewportState
    /// Araçların yerleri (bu katmanın koordinatında).
    let rects: [String: CGRect]
    var mascot = false
    @State private var callout: CGRect?
    @State private var calloutSize: CGSize = .zero
    @State private var pulse = false

    var body: some View {
        if let ui = ctl.ui {
            GeometryReader { geo in
                content(ui, geo.size)
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("tutorial_overlay")
            .onDisappear { viewport.fit() }
        }
    }

    @ViewBuilder private func content(_ ui: TutorialUi, _ size: CGSize) -> some View {
        let canvasRect = rects[TutorialAnchor.CANVAS]
        let stepTarget = rects[ui.step.anchor] ?? canvasRect
        let focusTarget = rects[ui.focus] ?? rects[ui.step.anchor] ?? canvasRect
        let onCanvas = ui.step.anchor == TutorialAnchor.CANVAS
        let hole = TutorialAnchor.SUGGESTS.contains(ui.focus) ? focusTarget?.insetBy(dx: -6, dy: -6) : nil
        ZStack(alignment: .topLeading) {
            TouchBlocker(hole: hole)
            if let r = rects[ui.step.anchor] {
                Color.clear.frame(width: max(r.width, 1), height: max(r.height, 1)).offset(x: r.minX, y: r.minY)
                    .allowsHitTesting(false)
                    .accessibilityElement().accessibilityIdentifier("tutorial_target").accessibilityValue(ui.step.anchor)
            }
            ring(ui, focusTarget, stepTarget, canvasRect, onCanvas)
                .allowsHitTesting(false)
            if mascot {
                KidsTutorialMascot(ui: ui, onNext: ctl.next, onCancel: ctl.cancel)
                    .frame(maxWidth: max(size.width - 24, 0), alignment: .trailing)
                    .background(sizeReader)
                    .offset(mascotOffset(size, canvasRect))
            } else {
                TutorialCallout(ui: ui, onNext: ctl.next, onCancel: ctl.cancel, onReplay: ctl.replay)
                    .frame(maxWidth: min(size.width - 24, 400))
                    .fixedSize(horizontal: false, vertical: true)
                    .background(sizeReader)
                    .offset(calloutOffset(size, stepTarget, canvasRect, onCanvas))
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .onPreferenceChange(CalloutSizeKey.self) { calloutSize = $0 }
        .onChange(of: "\(ui.group.id):\(ui.index):\(Int(calloutSize.height))") { _, key in fitPaper(key, size, canvasRect) }
        .onAppear { withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { pulse = true } }
    }

    private var sizeReader: some View { GeometryReader { Color.clear.preference(key: CalloutSizeKey.self, value: $0.size) } }

    private func calloutOffset(_ size: CGSize, _ t: CGRect?, _ cr: CGRect?, _ onCanvas: Bool) -> CGSize {
        let margin: CGFloat = 12, gap: CGFloat = 16
        let w = calloutSize.width, h = calloutSize.height
        guard let t else { return CGSize(width: (size.width - w) / 2, height: size.height - h - margin) }
        if onCanvas, let cr { return CGSize(width: (size.width - w) / 2, height: max(cr.maxY - h - margin, margin)) }
        let below = t.maxY + gap, above = t.minY - gap - h
        let y: CGFloat = below + h <= size.height - margin ? below : above >= margin ? above : max(size.height - h - margin, margin)
        let x = min(max(t.midX - w / 2, margin), max(size.width - w - margin, margin))
        return CGSize(width: x, height: y)
    }

    private func mascotOffset(_ size: CGSize, _ cr: CGRect?) -> CGSize {
        let margin: CGFloat = 12
        let right = cr?.maxX ?? size.width
        let bar = rects[TutorialAnchor.MODE_BAR].map { $0.minY + margin }
        let bottom = [cr?.maxY, bar].compactMap { $0 }.min() ?? size.height
        return CGSize(width: max(right - margin - calloutSize.width, margin), height: max(bottom - margin - calloutSize.height, margin))
    }

    /// Her adımda kâğıt açıklama kutusunun kapatmadığı bölgeye sığar.
    private func fitPaper(_ key: String, _ size: CGSize, _ area0: CGRect?) {
        guard calloutSize.height > 0, let area0, let ui = ctl.ui else { return }
        let off = mascot ? mascotOffset(size, area0) : calloutOffset(size, rects[ui.step.anchor] ?? area0, area0, ui.step.anchor == TutorialAnchor.CANVAS)
        let gap: Float = 10
        let top = Float(off.height - area0.minY), bottom = top + Float(calloutSize.height)
        let aw = Float(area0.width), ah = Float(area0.height)
        let area = top > ah * 0.4 || mascot ? FRect(0, 0, aw, top - gap) : FRect(0, max(bottom + gap, 0), aw, ah)
        if area.height < ah * (mascot ? 0.2 : 0.3) || area.width <= 0 { return }
        let (s1, o1) = viewport.fitTarget(area)
        withAnimation(.easeInOut(duration: 0.32)) { viewport.setView(s1, o1, settle: true) }
    }

    /// Vurgu halkası, kutudan araca ok ve parmak.
    private func ring(_ ui: TutorialUi, _ focus: CGRect?, _ step: CGRect?, _ canvas: CGRect?, _ onCanvas: Bool) -> some View {
        let live = ctl.live
        let pulseV: CGFloat = pulse ? 1 : 0
        let pointer: CGPoint? = {
            if let a = live.pointerAnchor { return rects[a]?.center }
            if let p = live.pointer, let canvas {
                let s = viewport.toScreen(p)
                return CGPoint(x: canvas.minX + CGFloat(s.x), y: canvas.minY + CGFloat(s.y))
            }
            return nil
        }()
        return ZStack(alignment: .topLeading) {
            if let f = focus, !(onCanvas && ui.focus == TutorialAnchor.CANVAS) {
                let r = f.insetBy(dx: -(6 + 3 * pulseV), dy: -(6 + 3 * pulseV))
                let radius = f.height > 70 ? 20 : min(r.width, r.height) / 2
                RoundedRectangle(cornerRadius: radius)
                    .fill(Color.accentColor.opacity(0.10 + 0.10 * pulseV))
                    .overlay(RoundedRectangle(cornerRadius: radius).stroke(Color.accentColor.opacity(0.55 + 0.35 * pulseV), lineWidth: 2.5))
                    .frame(width: r.width, height: r.height)
                    .offset(x: r.minX, y: r.minY)
                    .accessibilityIdentifier("tutorial_ring")
            }
            if let p = pointer {
                let outer: CGFloat = live.pressed ? 15 : 21
                ZStack {
                    Circle().fill(Color.black.opacity(0.25)).frame(width: outer * 2, height: outer * 2).offset(x: 2, y: 3)
                    Circle().fill(Color(.systemBackground).opacity(0.92)).frame(width: outer * 2, height: outer * 2)
                    Circle().stroke(Color.accentColor.opacity(live.pressed ? 0.55 : 0.35), lineWidth: 2).frame(width: outer * 2, height: outer * 2)
                    Circle().fill(Color.accentColor).frame(width: live.pressed ? 12 : 9, height: live.pressed ? 12 : 9)
                    if let f = live.hold {
                        let r = outer + 7
                        Circle().stroke(Color.secondary.opacity(0.25), lineWidth: 3).frame(width: r * 2, height: r * 2)
                        Circle().trim(from: 0, to: CGFloat(min(max(f, 0), 1)))
                            .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                            .frame(width: r * 2, height: r * 2)
                    }
                }
                .position(p)
                .accessibilityIdentifier("tutorial_finger")
            }
        }
    }
}

private struct CalloutSizeKey: PreferenceKey {
    static let defaultValue: CGSize = .zero
    static func reduce(value: inout CGSize, nextValue: () -> CGSize) { let n = nextValue(); if n != .zero { value = n } }
}

/// Dokunmaları yutar; [hole] içine dokunulabilir (altındaki öneri balonuna geçer).
private struct TouchBlocker: View {
    let hole: CGRect?
    var body: some View {
        GeometryReader { geo in
            let shape = HoleShape(hole: hole)
            Color.black.opacity(0.001)
                .clipShape(shape, style: FillStyle(eoFill: true))
                .contentShape(shape, eoFill: true)
                .onTapGesture {}
                .gesture(DragGesture(minimumDistance: 0))
                .frame(width: geo.size.width, height: geo.size.height)
        }
        .accessibilityHidden(true)
    }
}

private struct HoleShape: Shape {
    let hole: CGRect?
    func path(in rect: CGRect) -> Path {
        var p = Path(rect)
        if let hole { p.addRoundedRect(in: hole, cornerSize: CGSize(width: 12, height: 12)) }
        return p
    }
}

/// Adımın açıklama kutusu: adım, başlık, açıklama, ilerleme ve düğmeler (Android `TutorialCallout`).
struct TutorialCallout: View {
    let ui: TutorialUi
    let onNext: () -> Void
    let onCancel: () -> Void
    let onReplay: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(L("tut_step_of", ui.index + 1, ui.count)).font(.caption.weight(.semibold)).foregroundStyle(Color.accentColor)
                    .accessibilityIdentifier("tutorial_step")
                Text(L(ui.group.title)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                Spacer()
                if ui.step.demo != nil {
                    Button(action: onReplay) { Image(systemName: "arrow.counterclockwise").font(.footnote) }
                        .disabled(ui.running)
                        .accessibilityLabel(L("tut_replay")).accessibilityIdentifier("tutorial_replay")
                }
            }
            HStack(spacing: 6) {
                if ui.step.ai { Image(systemName: "sparkles").foregroundStyle(Color.accentColor).accessibilityLabel(L("tut_ai_badge")) }
                Text(L(ui.step.title)).font(.headline).accessibilityIdentifier("tutorial_title")
            }
            Text(L(ui.step.body)).font(.footnote).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
            if let hint = ui.hint {
                Label(L(hint), systemImage: "hand.tap")
                    .font(.footnote)
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Color.accentColor.opacity(0.12)))
                    .accessibilityIdentifier("tutorial_hint")
            }
            ProgressView(value: Double(ui.index + 1), total: Double(ui.count)).tint(.accentColor)
            HStack(spacing: 10) {
                Button(L("tut_cancel"), action: onCancel)
                    .buttonStyle(.bordered).frame(maxWidth: .infinity)
                    .accessibilityIdentifier("tutorial_cancel")
                Button(L(ui.last ? "tut_finish" : "tut_next"), action: onNext)
                    .buttonStyle(.borderedProminent).frame(maxWidth: .infinity)
                    .accessibilityIdentifier("tutorial_next")
            }
            .font(.subheadline)
        }
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))
        .shadow(color: .black.opacity(0.18), radius: 10, y: 4)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("tutorial_callout")
    }
}

/// v37 çocuk eğitiminde açıklama kutusunun yerine maskot (Android `KidsTutorialMascot`): adımın açıklaması başının üstündeki
/// balonda harf harf yazılır; ok simgeli Tamam (sonraki adım; son adımda eğitimi bitirir) ve çarpı simgeli Sonlandır.
struct KidsTutorialMascot: View {
    let ui: TutorialUi
    let onNext: () -> Void
    let onCancel: () -> Void

    var body: some View {
        let title = L("tut_step_of", ui.index + 1, ui.count) + " · " + L(ui.step.title)
        MascotSpeaker(look: TutorialKids.lookOf(ui.step), text: L(ui.step.body), title: title, mascotDescription: L("kids_mascot_cd")) {
            MascotRoundButton(icon: "xmark", description: L("kids_mascot_end"), id: "mascot_close", kind: .CLOSE, action: onCancel)
            MascotRoundButton(icon: "arrow.forward", description: L(ui.last ? "tut_finish" : "kids_mascot_next"), id: "mascot_next",
                              kind: .PRIMARY, action: onNext)
        }
        .accessibilityIdentifier("kids_tutorial")
    }
}

// MARK: - Grup seçimi

/// Eğitim grubu seçimi (Android `TutorialOfferSheet`): her grubun başlığı, özeti, adım sayısı ve rozetleri.
struct TutorialOfferSheet: View {
    let kind: CanvasKind
    let groups: [TutorialGroup]
    let completed: Set<String>
    let onStart: (TutorialGroup) -> Void
    let onDismiss: () -> Void

    private var titleKey: String {
        switch kind {
        case .NOTES: return "tut_offer_title_notes"
        case .COMIC: return "tut_offer_title_comic"
        case .SIMPLE_ART: return "tut40_offer_title_simple_art"
        case .SIMPLE_NOTES: return "tut40_offer_title_simple_notes"
        default: return "tut_offer_title_art"
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    Image(systemName: "graduationcap.fill").font(.title2).foregroundStyle(Color.accentColor)
                        .frame(width: 46, height: 46).background(Circle().fill(Color.accentColor.opacity(0.12)))
                    Text(L(titleKey)).font(.title2.bold())
                }
                Text(L("tut_offer_text")).font(.body).foregroundStyle(.secondary)
                ForEach(groups) { g in
                    let title = L(g.title)
                    Button { onStart(g) } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: g.icon).font(.title3).foregroundStyle(Color.accentColor).frame(width: 32)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(title).font(.headline).foregroundStyle(.primary)
                                Text(L(g.summary)).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.leading)
                                HStack(spacing: 10) {
                                    Text(L("tut_steps", g.steps.count)).font(.caption2).foregroundStyle(.secondary)
                                    if g.ai { Text(L("tut_ai_badge")).font(.caption2).foregroundStyle(Color.accentColor) }
                                    if completed.contains(g.id) {
                                        Label(L("tut_done_badge"), systemImage: "checkmark.circle.fill").font(.caption2).foregroundStyle(.secondary)
                                    }
                                }
                            }
                            Spacer(minLength: 0)
                            Image(systemName: "play.circle.fill").font(.title2).foregroundStyle(Color.accentColor)
                        }
                        .padding(14)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L("tut_offer_start_cd", title))
                    .accessibilityIdentifier("tutorial_group_\(g.id)")
                }
                Text(L("tut_offer_note")).font(.footnote).foregroundStyle(.secondary)
                Button(L("tut_offer_later"), action: onDismiss)
                    .buttonStyle(.bordered).frame(maxWidth: .infinity)
                    .accessibilityIdentifier("tutorial_later")
            }
            .padding(20)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("tutorial_offer")
        .presentationDetents([.medium, .large])
    }
}
