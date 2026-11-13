import Combine
import Foundation

/// v32 eğitimde tanıtılan araçların ekrandaki yerleri (Android `TutorialAnchor`).
enum TutorialAnchor {
    static let UNDO = "undo", REDO = "redo", BRUSH = "brush", ERASER = "eraser", PEN = "pen", FILL = "fill"
    static let BACKGROUND = "background", ELEMENTS = "elements", BRUSH_SETTINGS = "brush_settings", LAYERS = "layers"
    static let MOVE_EXPAND = "move_expand", MORE = "more_menu", CANVAS = "canvas"
    static let SUGGEST = "suggest_balloon", SUGGEST_TEXT = "suggest_text_balloon", SUGGEST_LINE = "suggest_line_balloon"
    static let SUGGESTS: Set<String> = [SUGGEST, SUGGEST_TEXT, SUGGEST_LINE]
    static let PAGES = "pages", MODE_BAR = "mode_bar", SAVE = "save_image"
    static let RULER_BAR = "ruler_bar", RULER_TYPE = "ruler_type", RULER_UNIT = "ruler_unit", RULER_SCALE = "ruler_scale"
    static let RULER_FINISH = "ruler_finish", TEMPLATE_LIBRARY = "template_library", RULER = "tool_ruler", TEMPLATE = "tool_template"
    static func mode(_ m: EditMode) -> String { "mode_\(m)" }
    static func add(_ id: String) -> String { "add_\(id)" }
    static func selection(_ id: String) -> String { "selection_\(id)" }
    static func simple(_ id: String) -> String { "simple_\(id)" }
}

typealias TutorialDemoBlock = @MainActor (TutorialDemo) async -> Void

/// Eğitimin bir adımı: aracın altında başlık ve açıklama; [demo] çizim alanında o araçla bir çizimin bölümünü çizer.
struct TutorialStep {
    let anchor: String
    let title: String
    let body: String
    var ai = false
    var mode: EditMode? = nil
    /// v40 önceki adımda açılan cetvel/kalıp bu adımda da açık kalır.
    var guide = false
    var demo: TutorialDemoBlock? = nil
    /// v37 çocuk eğitiminde maskotun bu adımdaki yüz ifadesi ve el hareketi.
    var look: MascotLook? = nil

    init(_ anchor: String, _ title: String, _ body: String, ai: Bool = false, mode: EditMode? = nil, guide: Bool = false,
         demo: TutorialDemoBlock? = nil) {
        self.anchor = anchor; self.title = title; self.body = body; self.ai = ai; self.mode = mode; self.guide = guide; self.demo = demo
    }

    func looking(_ face: MascotFace, _ gesture: MascotGesture) -> TutorialStep {
        var s = self; s.look = MascotLook(face: face, gesture: gesture); return s
    }
}

/// Bir araç grubunun eğitimi.
final class TutorialGroup: Identifiable {
    let id: String
    let kind: CanvasKind
    let title: String
    let summary: String
    let icon: String
    let ai: Bool
    let steps: [TutorialStep]
    init(id: String, kind: CanvasKind, title: String, summary: String, icon: String, ai: Bool, steps: [TutorialStep]) {
        self.id = id; self.kind = kind; self.title = title; self.summary = summary; self.icon = icon; self.ai = ai; self.steps = steps
    }
}

/// Açık eğitim adımı.
struct TutorialUi {
    let group: TutorialGroup
    let index: Int
    var focus: String
    var running: Bool
    var hint: String? = nil
    var step: TutorialStep { group.steps[index] }
    var last: Bool { index == group.steps.count - 1 }
    var count: Int { group.steps.count }
}

/// Çizim alanında gösterilen, henüz eklenmemiş çizim ve "parmak".
struct TutorialLive {
    var strokes: [Stroke] = []
    var pointer: P? = nil
    var pointerAnchor: String? = nil
    var pressed = false
    var hold: Float? = nil
}

/// Eğitimin düzenleyiciden istedikleri (eğitim kayıt dışı "kum havuzunda" çalışır).
@MainActor
protocol TutorialHost: AnyObject {
    var shapeSuggestion: ShapeSuggestion? { get }
    var textSuggestion: TextSuggestion? { get }
    var lineSuggestion: LineSuggestion? { get }
    func tutorialCanvas() -> CanvasUi?
    func commitStroke(_ stroke: Stroke, suggest: Bool)
    func commitElements(_ strokes: [Stroke])
    func mutate(_ block: ([Stroke]) -> [Stroke])
    func tutorialSetMode(_ mode: EditMode)
    func select(_ ids: Set<String>)
    func moveSelected(_ dx: Float, _ dy: Float)
    func scaleSelected(_ factor: Float)
    func undo()
    func redo()
    func setPaperGradient(_ gradient: GradientSpec?)
    func balloon(_ style: BalloonStyle, speakers: Int, center: P, text: String) -> Stroke?
    func sfx(_ preset: SfxPreset, center: P, size: Float) -> Stroke?
    func comicLines(_ kind: ComicLinesKind, density: Float, area: FRect, ink: String) -> [Stroke]
    func acceptTemplate(_ position: Int)
    func acceptLines()
    func acceptText(_ text: String)
    func dismissSuggestions()
    func fit(_ stroke: Stroke) -> ShapeFix?
    func tutorialClosePanel()
    func finished(_ group: TutorialGroup)
    func paintCursor(_ point: P?, hold: Int, color: String?)
    func paint(_ point: P, color: String, whole: Bool) -> Bool
    var tutorialGuides: RulerController? { get }
    func setPageSettings(_ settings: PageSettings)
    func assetImage(_ path: String, target: FRect, fill: Bool) async -> Stroke?
    /// v50 resim [id] seçilip Resmi düzenle'ye geçilir.
    func editImage(_ id: String)
    /// v50 resim [id] vektörleştirilir (keepImage ise resmin üstüne); bitince vektör çizgiler döner.
    func vectorizeImage(_ id: String, mode: EdgeMode, lineWidth: Float, keepImage: Bool) async -> Stroke?
}

/// Eğitim tercihleri (Android `TutorialPrefs`): tamamlanan gruplar, ilk girişte öneri, sürüm.
@MainActor
final class TutorialPrefs: ObservableObject {
    static let shared = TutorialPrefs()
    private let d = UserDefaults.standard
    @Published private(set) var completed: Set<String>
    /// UI testlerinde eğitim kendiliğinden açılmaz.
    let autoOffer = !ProcessInfo.processInfo.arguments.contains("-uiTesting")

    private init() { completed = Set(UserDefaults.standard.stringArray(forKey: "tutorials.completed") ?? []) }
    func offered(_ kind: String) -> Bool { d.bool(forKey: "tutorials.offered_" + kind) }
    func markOffered(_ kind: String) { d.set(true, forKey: "tutorials.offered_" + kind) }
    func version(_ kind: String) -> Int { d.object(forKey: "tutorials.version_" + kind) as? Int ?? (offered(kind) ? 1 : 0) }
    func markVersion(_ kind: String, _ v: Int) { d.set(v, forKey: "tutorials.version_" + kind) }
    func markCompleted(_ id: String) { completed.insert(id); d.set(Array(completed), forKey: "tutorials.completed") }
    /// Ayarlar → "Eğitimleri yeniden öner" (Android `resetOffers`): ilk girişteki eğitim teklifi yeniden çıkar.
    func resetOffers() {
        for k in d.dictionaryRepresentation().keys where k.hasPrefix("tutorials.offered_") || k.hasPrefix("tutorials.version_") {
            d.removeObject(forKey: k)
        }
    }
}

/// v32 eğitim modu (Android `TutorialController`): adımları oynatır; eğitim kum havuzunda çalışır, iptal ya da bitince
/// tuval eğitimden önceki haline döner. "Sonraki adım"a çizim bitmeden basılırsa adımın çizimi hemen tamamlanır.
@MainActor
final class TutorialController: ObservableObject {
    unowned let host: TutorialHost
    private let store: EditorStore
    private let prefs: TutorialPrefs

    @Published private(set) var ui: TutorialUi?
    @Published private(set) var live = TutorialLive()
    @Published var offer = false

    var completed: Set<String> { prefs.completed }
    var active: Bool { ui != nil }
    var aiStep: Bool { ui?.step.ai == true }

    private var job: Task<Void, Never>?
    var fast = false
    private var stepStart: CanvasUi?
    private var advancing = false
    private var guideSettings: RulerSettings?

    init(host: TutorialHost, store: EditorStore, prefs: TutorialPrefs? = nil) {
        self.host = host; self.store = store; self.prefs = prefs ?? .shared
    }

    func groups(_ kind: CanvasKind) -> [TutorialGroup] { TutorialScripts.groups(kind) }

    /// Tuval açıldı: bu türde ilk girişse eğitim önerilir.
    func maybeOffer(_ kind: CanvasKind) {
        let name = "\(kind)"
        if !prefs.autoOffer || groups(kind).isEmpty { return }
        if prefs.offered(name) {
            if prefs.version(name) >= TutorialScripts.VERSION { return }
            prefs.markVersion(name, TutorialScripts.VERSION)
            guard let fresh = TutorialScripts.whatsNew(kind) else { return }
            let done = prefs.completed
            if groups(kind).contains(where: { $0.id != fresh.id && done.contains($0.id) }) && !done.contains(fresh.id) { start(fresh) }
            return
        }
        prefs.markOffered(name)
        prefs.markVersion(name, TutorialScripts.VERSION)
        if kind == .KIDS { start(groups(kind)[0]) } else { offer = true }
    }

    func openOffer() { if !active { offer = true } }
    func closeOffer() { offer = false }

    func start(_ group: TutorialGroup) {
        offer = false
        if active { stop() }
        if let g = host.tutorialGuides { g.finish(); guideSettings = g.settings } else { guideSettings = nil }
        host.tutorialClosePanel()
        host.dismissSuggestions()
        store.beginSandbox()
        host.tutorialSetMode(.DRAW)
        run(group, 0)
    }

    private func run(_ group: TutorialGroup, _ index: Int) {
        let step = group.steps[index]
        fast = false
        host.dismissSuggestions()
        if !step.guide { closeGuides() }
        host.tutorialSetMode(step.mode ?? .DRAW)
        stepStart = store.sandboxState()
        live = TutorialLive()
        ui = TutorialUi(group: group, index: index, focus: step.anchor, running: step.demo != nil)
        guard let demo = step.demo else { return }
        job = Task { [weak self] in
            guard let self else { return }
            await self.pause(700)
            if !Task.isCancelled { await demo(TutorialDemo(self, self.host)) }
            self.live = TutorialLive()
            if var u = self.ui, u.group === group, u.index == index {
                u.running = false; u.focus = step.anchor; u.hint = nil
                self.ui = u
            }
        }
    }

    /// Sonraki adım (son adımda eğitim biter). Çizim sürüyorsa önce hemen tamamlanır.
    func next() {
        guard let u = ui, !advancing else { return }
        advancing = true
        Task {
            defer { advancing = false }
            await completeCurrent()
            guard let now = ui, now.group === u.group, now.index == u.index else { return }
            if u.last { finish() } else { run(u.group, u.index + 1) }
        }
    }

    /// Adımın çizimini baştan gösterir.
    func replay() {
        guard let u = ui else { return }
        Task {
            if let j = job { fast = true; j.cancel(); await j.value }
            job = nil
            host.dismissSuggestions()
            if let s = stepStart { store.restoreSandboxState(s) }
            run(u.group, u.index)
        }
    }

    func cancel() { job?.cancel(); job = nil; stop() }

    func finish() {
        guard let u = ui else { return }
        job?.cancel(); job = nil
        prefs.markCompleted(u.group.id)
        stop()
        host.finished(u.group)
    }

    private func completeCurrent() async {
        guard let j = job else { return }
        fast = true
        await j.value
        job = nil
    }

    private func closeGuides() {
        guard let g = host.tutorialGuides else { return }
        g.libraryOpen = false
        if g.active { g.finish() }
    }

    private func stop() {
        ui = nil
        live = TutorialLive()
        closeGuides()
        if let saved = guideSettings, let g = host.tutorialGuides, g.settings != saved {
            g.setType(saved.type); g.setUnit(saved.unit); g.setScale(saved.scale)
        }
        guideSettings = nil
        host.dismissSuggestions()
        store.endSandbox()
        host.tutorialSetMode(.DRAW)
        fast = false
    }

    // --- oynatıcının kullandıkları

    func pause(_ ms: Int) async {
        if !fast && ms > 0 { await sleepMs(ms) }
    }

    /// Bekler; "Sonraki adım"a basılınca (fast) hemen döner.
    func sleepMs(_ ms: Int) async {
        var left = ms
        while left > 0 && !fast && !Task.isCancelled {
            let step = min(left, 40)
            try? await Task.sleep(nanoseconds: UInt64(step) * 1_000_000)
            left -= step
        }
    }

    func setLive(_ l: TutorialLive) { live = l }

    func focus(_ anchor: String, _ hint: String?) {
        guard var u = ui else { return }
        u.focus = anchor; u.hint = hint
        ui = u
    }
}

/// Bir adımın çizimini oynatan yardımcılar (Android `TutorialDemo`): çizgiler parmakla çiziliyormuş gibi belirir,
/// şekiller sürüklenerek büyür, öneri balonuna dokunulur. "Sonraki adım"a basılınca her şey beklemeden tamamlanır.
@MainActor
final class TutorialDemo {
    private unowned let c: TutorialController
    unowned let host: TutorialHost

    static let FRAME_MS = 16
    static let AI_WAIT_MS = 6_000
    static let USER_WAIT_MS = 3_500

    init(_ c: TutorialController, _ host: TutorialHost) { self.c = c; self.host = host }

    private var canvas: CanvasUi? { host.tutorialCanvas() }
    var w: Float { Float(canvas?.width ?? 1000) }
    var h: Float { Float(canvas?.height ?? 1000) }
    var u: Float { min(w, h) }
    var kind: CanvasKind { canvas?.kind ?? .ART }
    var fast: Bool { c.fast || Task.isCancelled }

    func p(_ fx: Float, _ fy: Float) -> P { P(w * fx, h * fy) }
    func pause(_ ms: Int) async { if !fast { await c.pause(ms) } }
    func live(_ l: TutorialLive) { c.setLive(l) }
    func focus(_ anchor: String, _ hint: String? = nil) { c.focus(anchor, hint) }
    func mode(_ m: EditMode) { host.tutorialSetMode(m) }
    private func frame() async { await c.sleepMs(Self.FRAME_MS) }

    /// Parmak noktada basılı kalır, halka dolar.
    func hold(_ at: P, ms: Int = 1_600) async {
        if fast { return }
        let frames = max(ms / Self.FRAME_MS, 2)
        for f in 0...frames {
            if fast { break }
            c.setLive(TutorialLive(pointer: at, pressed: true, hold: Float(f) / Float(frames)))
            await frame()
        }
    }

    /// Fırça çizgisi: yol boyunca parmak gezinir, çizgi belirir; bitince tuvale eklenir. Basınç uçlarda azalır.
    @discardableResult
    func stroke(_ path: [P], _ brush: BrushType, _ color: String, _ size: Float, opacity: Float = 1, suggest: Bool = false,
                speed: Float = 1, taper: Bool = true) async -> Stroke {
        let pts = Self.resample(path, max(u * 0.006, 1.5))
        let total = pts.count
        let points: [StrokePoint] = pts.enumerated().map { i, o in
            let t: Float = total <= 1 ? 0.5 : Float(i) / Float(total - 1)
            let pressure: Float = taper && brush != .ERASER ? min(max(0.45 + 0.55 * max(sin(Float.pi * t), 0).squareRoot(), 0.35), 1) : 1
            return StrokePoint(o.x, o.y, pressure: pressure)
        }
        let stroke = Stroke(id: UUID().uuidString, points: points, colorHex: color, brushType: brush, sizePx: size, opacity: opacity, order: 0)
        if !fast && points.count > 1 {
            let duration = min(max(Int(Self.length(path) / (u * 1.15 * speed) * 1000), 220), 1600)
            let frames = max(duration / Self.FRAME_MS, 2)
            for f in 1...frames {
                if fast { break }
                let n = min(max(points.count * f / frames, 1), points.count)
                var partial = stroke
                partial.points = Array(points[0..<n])
                c.setLive(TutorialLive(strokes: [partial], pointer: P(points[n - 1].x, points[n - 1].y), pressed: true))
                await frame()
            }
        }
        c.setLive(TutorialLive())
        host.commitStroke(stroke, suggest: suggest)
        return stroke
    }

    /// Hazır elemanlar sürüklenerek büyür; bitince eklenir.
    @discardableResult
    func drag(_ from: P, _ to: P, durationMs: Int = 650, _ build: (P, P) -> [Stroke]) async -> [Stroke] {
        if !fast {
            c.setLive(TutorialLive(pointer: from, pressed: true))
            await c.sleepMs(120)
            let frames = max(durationMs / Self.FRAME_MS, 2)
            for f in 1...frames {
                if fast { break }
                let t = Self.easeOut(Float(f) / Float(frames))
                let cur = P(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
                c.setLive(TutorialLive(strokes: build(from, cur), pointer: cur, pressed: true))
                await frame()
            }
        }
        c.setLive(TutorialLive())
        let result = build(from, to)
        host.commitElements(result)
        return result
    }

    /// Parmak bir noktaya dokunur (tuvalde).
    func tap(_ at: P) async {
        if fast { return }
        c.setLive(TutorialLive(pointer: at, pressed: false)); await c.sleepMs(220)
        c.setLive(TutorialLive(pointer: at, pressed: true)); await c.sleepMs(180)
        c.setLive(TutorialLive(pointer: at, pressed: false)); await c.sleepMs(140)
        c.setLive(TutorialLive())
    }

    /// Parmak bir aracın üstüne dokunur.
    func tapAnchor(_ anchor: String) async {
        if fast { return }
        c.setLive(TutorialLive(pointerAnchor: anchor, pressed: false)); await c.sleepMs(320)
        c.setLive(TutorialLive(pointerAnchor: anchor, pressed: true)); await c.sleepMs(200)
        c.setLive(TutorialLive(pointerAnchor: anchor, pressed: false)); await c.sleepMs(120)
        c.setLive(TutorialLive())
    }

    /// Hazır elemanları beklemeden ekler (parmak dokunuşuyla).
    func place(_ at: P?, _ strokes: [Stroke]) async {
        if let at { await tap(at) }
        host.commitElements(strokes)
    }

    /// Koşul sağlanana dek (en çok [ms]) bekler; sağlanırsa değeri döner.
    private func waitFor<T>(_ ms: Int, _ probe: () -> T?) async -> T? {
        var left = ms
        while left > 0 {
            if fast { return nil }
            if let v = probe() { return v }
            try? await Task.sleep(nanoseconds: 50_000_000)
            left -= 50
        }
        return probe()
    }

    /// Yapay zekâ şekil önerisini bekler; gelince balon vurgulanır, kullanıcı dokunmazsa parmak dokunur ve uygulanır.
    /// Öneri gelmezse [fallback].
    func aiShape(_ hint: String, accept: (ShapeSuggestion) -> Bool = { _ in true }, apply: (ShapeSuggestion) -> Void, fallback: () -> Void) async {
        guard let s = await waitFor(Self.AI_WAIT_MS, { host.shapeSuggestion.flatMap { accept($0) ? $0 : nil } }) else {
            host.dismissSuggestions(); fallback(); return
        }
        focus(TutorialAnchor.SUGGEST, hint)
        let changed = await waitFor(Self.USER_WAIT_MS, { host.shapeSuggestion != s ? true : nil })
        if changed == nil && host.shapeSuggestion == s {
            await tapAnchor(TutorialAnchor.SUGGEST)
            if host.shapeSuggestion == s { apply(s) }
        }
        await pause(500)
    }

    /// El yazısının yazı önerisini bekler ve uygular (gelmezse [fallback]).
    func aiText(_ hint: String, fallback: () -> Void) async {
        guard let s = await waitFor(Self.AI_WAIT_MS, { host.textSuggestion }) else { host.dismissSuggestions(); fallback(); return }
        focus(TutorialAnchor.SUGGEST_TEXT, hint)
        let changed = await waitFor(Self.USER_WAIT_MS, { host.textSuggestion != s ? true : nil })
        if changed == nil && host.textSuggestion == s {
            await tapAnchor(TutorialAnchor.SUGGEST_TEXT)
            if host.textSuggestion == s { host.acceptText(s.text) }
        }
        await pause(500)
    }

    /// v38 çizgi önerisini bekler ve tek düğmeyle uygular (gelmezse [fallback]).
    func aiLines(_ hint: String, accept: (LineSuggestion) -> Bool = { _ in true }, fallback: () -> Void) async {
        guard let s = await waitFor(Self.AI_WAIT_MS, { host.lineSuggestion.flatMap { accept($0) ? $0 : nil } }) else {
            host.dismissSuggestions(); fallback(); return
        }
        focus(TutorialAnchor.SUGGEST_LINE, hint)
        let changed = await waitFor(Self.USER_WAIT_MS, { host.lineSuggestion != s ? true : nil })
        if changed == nil && host.lineSuggestion == s {
            await tapAnchor(TutorialAnchor.SUGGEST_LINE)
            if host.lineSuggestion == s { host.acceptLines() }
        }
        await pause(500)
    }

    nonisolated static func length(_ path: [P]) -> Float {
        guard path.count > 1 else { return 0 }
        var s: Float = 0
        for i in 1..<path.count { s += hypot(path[i].x - path[i - 1].x, path[i].y - path[i - 1].y) }
        return s
    }

    nonisolated static func easeOut(_ t: Float) -> Float { 1 - (1 - t) * (1 - t) }

    /// Yolu yaklaşık [step] aralıklı noktalara böler.
    nonisolated static func resample(_ path: [P], _ step: Float) -> [P] {
        if path.count < 2 { return path }
        var out = [path[0]]
        for i in 1..<path.count {
            let a = path[i - 1], b = path[i]
            let n = Int(hypot(b.x - a.x, b.y - a.y) / step)
            if n >= 1 { for k in 1...n { let t = Float(k) / Float(n + 1); out.append(P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)) } }
            out.append(b)
        }
        return out
    }
}

/// Şekil biçimi kısaltması.
func tutShapeStyle(_ stroke: String, _ width: Float, fill: String? = nil, gradient: GradientSpec? = nil, fillOpacity: Float = 1) -> ShapeStyle {
    var s = ShapeStyle(strokeColorHex: stroke, strokeWidth: width)
    s.fillColorHex = fill; s.fillGradient = gradient; s.fillOpacity = fillOpacity
    return s
}
