import SwiftUI
import UIKit

/// v53 Ekle menüsünün pencereleri: her iş kendi penceresinde (eski karma çizgi roman penceresi bölündü).
struct AddWindowsHost: ViewModifier {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var addUi: AddWindows

    func body(content: Content) -> some View {
        content.sheet(item: $addUi.open) { w in
            switch w {
            case .balloon: BalloonWindow(vm: vm)
            case .templates: ComicTemplatesWindow(vm: vm)
            case .effects: EffectsWindow(vm: vm)
            case .sfx: SfxWindow(vm: vm)
            case .lines: LinesWindow(vm: vm)
            case .patterns: ToneWindow(vm: vm, group: .PATTERN)
            case .tones: ToneWindow(vm: vm, group: .TONE)
            case .characters: CharactersSheet { v in vm.insertCharacter(v) }
            case .locations: LocationBackgroundsSheet { b in vm.pickLocation(b) }
            case .objects: ComicObjectsSheet { p in vm.insertComicProp(.OBJECT, p) }
            case .panels: NotesPanelsSheet { id in vm.v45.insertPanels(id); addUi.open = nil }
            case .model3d:
                if let repo = EditorDependencies.shared.models3d {
                    Model3DPickerSheet(vm: vm, repository: repo, kind: vm.store.canvas?.kind ?? .ART)
                }
            case .imageSource: EmptyView()
            }
        }
    }
}

/// Pencerelerin ortak iskeleti: başlık, kapat ve kaydırılan içerik.
struct WindowFrame<Content: View>: View {
    let title: String
    var hint: String? = nil
    @ViewBuilder let content: () -> Content
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let hint { Text(hint).font(.footnote).foregroundStyle(.secondary) }
                    content()
                }
                .padding(16)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Küçük resimli kart.
struct ThumbCard<Thumb: View>: View {
    let title: String
    var subtitle: String? = nil
    var selected = false
    var locked = false
    @ViewBuilder let thumb: () -> Thumb

    var body: some View {
        VStack(spacing: 6) {
            thumb().frame(height: 74).frame(maxWidth: .infinity)
            Text(title).font(.caption.weight(.semibold)).lineLimit(2).multilineTextAlignment(.center).foregroundStyle(.primary)
            if let subtitle { Text(subtitle).font(.caption2).foregroundStyle(.secondary).lineLimit(3).multilineTextAlignment(.center) }
        }
        .padding(8)
        .frame(maxWidth: .infinity, minHeight: 120, alignment: .top)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(selected ? Color.accentColor.opacity(0.14) : Color(uiColor: .secondarySystemGroupedBackground)))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(selected ? Color.accentColor : .clear, lineWidth: 1.5))
        .overlay(alignment: .topTrailing) {
            if locked { Image(systemName: "crown.fill").font(.caption).foregroundStyle(.yellow).padding(6) }
        }
    }
}

// MARK: - Konuşma balonu

enum BalloonThumbs {
    private static let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.countLimit = 40
        MemoryHygiene.register(editor: false) { c.removeAllObjects() }
        return c
    }()

    /// Balonun küçük resmi (gerçek balon çizicisiyle).
    static func image(_ style: BalloonStyle) -> UIImage? {
        let key = style.rawValue as NSString
        if let hit = cache.object(forKey: key) { return hit }
        let size: Float = 16
        var balloon = BalloonSpec()
        balloon.style = style
        balloon.tails = style == .FREE || style == .NARRATION ? [] : BalloonTails.defaultTails(1)
        balloon.fillHex = style == .NARRATION ? "#FFF59D" : style == .PHONE ? ComicController.PHONE_FILL : "#FFFFFF"
        balloon.strokeHex = style == .PHONE ? ComicController.PHONE_BORDER : "#111111"
        let spec = TextSpec(content: "…", font: .COMIC_RELIEF, align: .CENTER, box: TextBox(width: 92, balloon: balloon))
        let (w, h) = TextRenderer.measure(spec, size)
        let W: CGFloat = 150, H: CGFloat = 110
        let pts = TextRenderer.quadAt(spec, size, Float(W) / 2 - w / 2, Float(H) / 2 - h / 2 - 6)
        let stroke = Stroke(id: "balloon-thumb-\(style.rawValue)", points: pts, colorHex: style == .PHONE ? "#FFFFFF" : "#111111",
                            brushType: .TEXT, sizePx: size, opacity: 1, order: 0, text: spec)
        let img = UIGraphicsImageRenderer(size: CGSize(width: W, height: H)).image { r in
            StrokeRenderer.drawStrokeList(r.cgContext, [stroke])
        }
        cache.setObject(img, forKey: key)
        return img
    }

    static func title(_ s: BalloonStyle) -> String {
        let key = "balloon_" + s.rawValue.lowercased()
        let t = L(key)
        return t == key ? s.rawValue.capitalized : t
    }
}

/// v53 konuşma balonu penceresi: balon türleri küçük resimleriyle; konuşan sayısı. Eklenince yazı doğrudan balonun
/// üstünde yazılır (bkz. BalloonInlineEditor).
struct BalloonWindow: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var speakers = 1

    var body: some View {
        WindowFrame(title: L("add_balloon"), hint: L("v53_balloon_hint")) {
            Picker(L("v53_balloon_speakers"), selection: $speakers) {
                Text(L("v53_balloon_speakers_1")).tag(1)
                Text(L("v53_balloon_speakers_2")).tag(2)
                Text(L("v53_balloon_speakers_3")).tag(3)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("balloon_speakers")
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 10)], spacing: 10) {
                ForEach(BalloonStyle.allCases, id: \.self) { s in
                    Button {
                        vm.insertBalloon(s, speakers: s == .NARRATION || s == .FREE ? 0 : speakers)
                        dismiss()
                    } label: {
                        ThumbCard(title: BalloonThumbs.title(s)) {
                            if let img = BalloonThumbs.image(s) { Image(uiImage: img).resizable().scaledToFit() }
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("balloon_\(s.rawValue)")
                }
            }
        }
    }
}

/// Balon yazısı balonun tam üstünde yazılır: yazı alanı balonun içine oturur, yazı tipi ve boyutu balonunki.
struct BalloonInlineEditor: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var texts: TextController
    @ObservedObject var store: EditorStore
    @ObservedObject var viewport: ViewportState
    @State private var text = ""
    @FocusState private var focused: Bool

    static func balloonStroke(_ texts: TextController, _ store: EditorStore) -> Stroke? {
        guard let id = texts.draft?.editingId, let s = store.strokes.first(where: { $0.id == id }), s.text?.box?.balloon != nil else { return nil }
        return s
    }

    /// Balonun ekrandaki kutusu ve yazı boyutu.
    private struct Geo {
        let rect: CGRect
        let inset: CGFloat
        let fontSize: CGFloat
    }

    private func geo(_ s: Stroke) -> Geo {
        let xs = s.points.map(\.x), ys = s.points.map(\.y)
        let tl = viewport.toScreen(P(xs.min() ?? 0, ys.min() ?? 0))
        let br = viewport.toScreen(P(xs.max() ?? 0, ys.max() ?? 0))
        let rect = CGRect(x: CGFloat(tl.x), y: CGFloat(tl.y), width: CGFloat(br.x - tl.x), height: CGFloat(br.y - tl.y))
        let fs: CGFloat = max(CGFloat(s.sizePx * viewport.scale), 9)
        return Geo(rect: rect, inset: min(rect.width, rect.height) * 0.16, fontSize: fs)
    }

    var body: some View {
        if let s = Self.balloonStroke(texts, store), let spec = s.text {
            let g = geo(s)
            ZStack {
                field(s, spec, g)
                buttons(g)
            }
            .onAppear {
                // Yeni balonda örnek yazı silinir, doğrudan yazılır
                text = spec.content == I18n.tr(ComicController.DEFAULT_TEXT) ? "" : spec.content
                texts.updateDraftText(text)
                focused = true
            }
            .onChange(of: text) { _, t in texts.updateDraftText(t) }
        }
    }

    private func alignment(_ a: TextAlignKind) -> TextAlignment {
        switch a {
        case .LEFT: return .leading
        case .RIGHT: return .trailing
        default: return .center
        }
    }

    private func field(_ s: Stroke, _ spec: TextSpec, _ g: Geo) -> some View {
        let fill: Color = Color(hex: spec.box?.balloon?.fillHex ?? "#FFFFFF") ?? .white
        let ink: Color = Color(hex: s.colorHex) ?? .black
        let font = Font(FontRegistry.uiFont(spec.font, size: g.fontSize))
        let w: CGFloat = max(g.rect.width - g.inset * 2, 60)
        let h: CGFloat = max(g.rect.height - g.inset * 2, g.fontSize * 1.6)
        return TextField("", text: $text, axis: .vertical)
            .font(font)
            .bold(spec.bold)
            .italic(spec.italic)
            .multilineTextAlignment(alignment(spec.align))
            .foregroundStyle(ink)
            .focused($focused)
            .padding(4)
            .frame(width: w, height: h)
            .background(RoundedRectangle(cornerRadius: 8).fill(fill.opacity(0.96)))
            .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Color.accentColor.opacity(0.5), style: StrokeStyle(lineWidth: 1, dash: [4, 3])))
            .position(x: g.rect.midX, y: g.rect.midY)
            .accessibilityIdentifier("balloon_inline_field")
    }

    private func buttons(_ g: Geo) -> some View {
        HStack(spacing: 8) {
            Button { texts.cancel(); text = "" } label: {
                Image(systemName: "xmark").font(.system(size: 12, weight: .bold)).frame(width: 30, height: 30)
                    .background(Circle().fill(Color(uiColor: .secondarySystemBackground)))
            }
            .accessibilityIdentifier("balloon_inline_cancel")
            Button { vm.confirmText(text); text = "" } label: {
                Label(L("v53_done"), systemImage: "checkmark").font(.footnote.weight(.semibold))
                    .padding(.horizontal, 12).frame(height: 30)
                    .background(Capsule().fill(Color.accentColor)).foregroundStyle(.white)
            }
            .accessibilityIdentifier("balloon_inline_done")
        }
        .position(x: g.rect.midX, y: g.rect.maxY + 24)
    }
}

// MARK: - Şablonlar, efektler, efekt yazıları, çizgiler

struct ComicTemplatesWindow: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        WindowFrame(title: L("add_templates"), hint: L("v53_templates_hint")) {
            ForEach(TemplateGroup.allCases, id: \.self) { g in
                let items = ComicTemplates.all.filter { $0.group == g }
                if !items.isEmpty {
                    Text(g.title).font(.subheadline.weight(.semibold))
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 10)], spacing: 10) {
                        ForEach(items) { t in
                            Button { let ids = vm.comic.insertTemplate(t.id); vm.inserted(ids); dismiss() } label: {
                                ThumbCard(title: t.title, subtitle: L("v53_panels_n", t.panelCount)) { PanelPageThumb(page: t.page) }
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("comic_template_\(t.id)")
                        }
                    }
                }
            }
        }
    }
}

/// Panel düzeninin küçük resmi.
struct PanelPageThumb: View {
    let page: PanelPage
    var body: some View {
        Canvas { ctx, size in
            let h = size.height, w = h * 0.68
            let ox = (size.width - w) / 2
            ctx.fill(Path(roundedRect: CGRect(x: ox, y: 0, width: w, height: h), cornerRadius: 3), with: .color(.white))
            ctx.stroke(Path(roundedRect: CGRect(x: ox, y: 0, width: w, height: h), cornerRadius: 3), with: .color(.secondary.opacity(0.4)), lineWidth: 1)
            for shape in page.shapes(Float(w), Float(h)) {
                var p = Path()
                let n = shape.points.count / 2
                for i in 0..<n {
                    let q = CGPoint(x: ox + CGFloat(shape.x(i)), y: CGFloat(shape.y(i)))
                    if i == 0 { p.move(to: q) } else { p.addLine(to: q) }
                }
                p.closeSubpath()
                ctx.fill(p, with: .color(.accentColor.opacity(0.1)))
                ctx.stroke(p, with: .color(.primary.opacity(0.75)), lineWidth: 1.2)
            }
        }
    }
}

struct EffectsWindow: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        WindowFrame(title: L("add_effects"), hint: L("v53_effects_hint")) {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 10)], spacing: 10) {
                ForEach(ComicEffects.all) { e in
                    Button { vm.insertEffect(e, options: EffectOptions()); dismiss() } label: {
                        ThumbCard(title: e.title) { Image(systemName: "sparkles").font(.system(size: 30)).foregroundStyle(Color.accentColor) }
                    }
                    .buttonStyle(.plain)
                }
            }
            if !ComicAssets.catalog.effects.isEmpty {
                Text(L("effects_tab_effects")).font(.subheadline.weight(.semibold))
                ComicPropsBrowser(kind: .EFFECT, tag: "fximg") { p in vm.insertComicProp(.EFFECT, p); dismiss() }
            }
        }
    }
}

struct SfxWindow: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        WindowFrame(title: L("v53_add_sfx"), hint: L("v53_sfx_hint")) {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 10)], spacing: 10) {
                ForEach(Array(ComicSfx.presets.enumerated()), id: \.offset) { _, p in
                    Button { vm.insertSfx(p); dismiss() } label: {
                        // v53 tuvaldeki biçimiyle (gradyan, koyu kenar, beyaz dış kenar, gölge)
                        Group {
                            if let img = PickerThumbs.sfx(p) {
                                Image(uiImage: img).resizable().scaledToFit().padding(8)
                            } else {
                                Text(p.text).font(.title2.weight(.black))
                            }
                        }
                        .rotationEffect(.degrees(Double(p.tilt)))
                        .frame(maxWidth: .infinity, minHeight: 72, maxHeight: 72)
                            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color(uiColor: .secondarySystemGroupedBackground)))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

struct LinesWindow: View {
    @ObservedObject var vm: EditorViewModel
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var sub = SubscriptionStore.shared
    @State private var premiumOpen = false

    var body: some View {
        NavigationStack {
            List {
                LinesInsertSection(onInsert: { k, o in vm.comic.insertLines(k, o); dismiss() },
                                   locked: { k in k.ordinal > ComicLinesKind.BURST.ordinal && !sub.allows(.COMIC_EFFECTS, .COMIC) },
                                   onLocked: { premiumOpen = true })
            }
            .navigationTitle(L("effects_tab_lines"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
        .sheet(isPresented: $premiumOpen) { PremiumRequiredSheet() }
    }
}

// MARK: - Desenler ve ekran tonları

/// Tonun örnek görüntüsü (yuvarlak köşeli kutuda).
struct TonePreview: View {
    let tone: ToneFill
    var body: some View {
        Canvas { ctx, size in
            let rect = CGRect(origin: .zero, size: size)
            ctx.fill(Path(roundedRect: rect, cornerRadius: 10), with: .color(.white))
            ctx.withCGContext { cg in
                ToneRenderer.draw(cg, clip: CGPath(roundedRect: rect, cornerWidth: 10, cornerHeight: 10, transform: nil), tone,
                                  l: 0, t: 0, r: Float(size.width), b: Float(size.height), alpha: 1)
            }
            ctx.stroke(Path(roundedRect: rect, cornerRadius: 10), with: .color(.secondary.opacity(0.35)), lineWidth: 1)
        }
    }
}

/// v53 Desenler ve Ekran tonları (yalnız çizgi roman; ayrı düğme ve pencereler): her tür örneği ve çizgi romandaki
/// kullanımıyla; koyuluk, sıklık, açı ve renk ayarlanır. Seçili şekle/panele uygulanır ya da yeni bir alan eklenir.
struct ToneWindow: View {
    @ObservedObject var vm: EditorViewModel
    let group: ComicTones.Group
    @Environment(\.dismiss) private var dismiss
    @State private var preset: ComicTones.Preset?
    @State private var density: Double = 0.3
    @State private var scale: Double = 1
    @State private var angle: Double = 45
    @State private var color = "#111111"

    private var presets: [ComicTones.Preset] { group == .TONE ? ComicTones.TONES : ComicTones.PATTERNS }
    private var tone: ToneFill? {
        preset.map { ToneFill(id: $0.id, density: Float(density), scale: Float(scale), angle: Float(angle), colorHex: color) }
    }

    var body: some View {
        WindowFrame(title: L(group == .TONE ? "v53_tones" : "v53_patterns"), hint: L(group == .TONE ? "v53_tones_hint" : "v53_patterns_hint")) {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 10)], spacing: 10) {
                ForEach(presets) { p in
                    Button { select(p) } label: {
                        ThumbCard(title: L(p.nameKey), subtitle: L(p.usageKey), selected: preset?.key == p.key) {
                            TonePreview(tone: ToneFill(id: p.id, density: p.density, scale: p.scale * 0.6, angle: p.angle))
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("tone_\(p.key)")
                }
            }
            if let tone {
                PropSection(title: L("v53_tone_settings")) {
                    TonePreview(tone: tone).frame(height: 90)
                    PropSlider(label: L("v53_tone_density"), value: $density, range: 0.03...0.9, format: { "\(Int($0 * 100))%" }, id: "tone_density")
                    PropSlider(label: L("v53_tone_scale"), value: $scale, range: 0.4...3, format: { String(format: "%.1f×", $0) }, id: "tone_scale")
                    PropSlider(label: L("v53_tone_angle"), value: $angle, range: 0...180, format: { "\(Int($0))°" }, id: "tone_angle")
                    ColorRow(hex: color, id: "tone_color") { color = $0 }
                }
                HStack(spacing: 10) {
                    Button { vm.insertToneArea(tone); dismiss() } label: {
                        Label(L("v53_tone_insert"), systemImage: "plus.rectangle").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("tone_insert")
                    Button { vm.applyToneToSelection(tone); dismiss() } label: {
                        Label(L("v53_tone_apply"), systemImage: "paintbrush").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .disabled(!vm.selectionHasClosedShape())
                    .accessibilityIdentifier("tone_apply")
                }
                Text(L("v53_tone_apply_hint")).font(.caption).foregroundStyle(.secondary)
            }
            if vm.selectionHasTone() {
                Button(role: .destructive) { vm.applyToneToSelection(nil); dismiss() } label: {
                    Label(L("v53_tone_remove"), systemImage: "xmark.circle")
                }
                .accessibilityIdentifier("tone_remove")
            }
        }
    }

    private func select(_ p: ComicTones.Preset) {
        preset = p
        density = Double(p.density); scale = Double(p.scale); angle = Double(p.angle)
    }
}

extension EditorViewModel {
    /// Seçimde tonlanabilir (kapalı alanlı) şekil var mı.
    func selectionHasClosedShape() -> Bool {
        store.strokes.contains { store.selection.contains($0.id) && $0.shape != nil }
    }

    func selectionHasTone() -> Bool {
        store.strokes.contains { store.selection.contains($0.id) && $0.shape?.style.tone != nil }
    }

    /// Seçili şekillerin (paneller dahil) tonunu değiştirir; nil tonu kaldırır.
    func applyToneToSelection(_ tone: ToneFill?) {
        guard let c = store.canvas else { return }
        let ids = store.selection
        var changed = false
        let updated = c.strokes.map { s -> Stroke in
            guard ids.contains(s.id), var shape = s.shape, shape.style.tone != tone else { return s }
            shape.style.tone = tone
            var n = s
            n.shape = shape
            changed = true
            return n
        }
        if changed { store.commit(updated) }
    }

    /// Görünen alanın ortasına tonla dolu bir alan ekler (kenarsız dikdörtgen; taşınır ve boyutlanır).
    func insertToneArea(_ tone: ToneFill) {
        guard let c = store.canvas else { return }
        let v = viewport
        let center = takeAddAnchor() ?? v.toCanvas(P(v.viewSize.w / 2, v.viewSize.h / 2))
        let side = Float(min(c.width, c.height)) * 0.42
        let start = P(center.x - side / 2, center.y - side * 0.35), end = P(center.x + side / 2, center.y + side * 0.35)
        let id = UUID().uuidString.lowercased()
        guard let stroke = ShapeFactory.fromPaths("RECTANGLE", ShapeFactory.paths("RECTANGLE", start, end),
                                                  style: ShapeStyle(strokeColorHex: tone.colorHex, strokeWidth: 0, tone: tone),
                                                  graph: nil, id: id, order: c.strokes.count) else { return }
        store.commit(c.strokes + [stroke])
        inserted([id])
    }
}
