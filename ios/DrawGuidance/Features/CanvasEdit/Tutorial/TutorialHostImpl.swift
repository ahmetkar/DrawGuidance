import Foundation

/// Eğitimin düzenleyiciye erişimi (Android `CanvasEditViewModel.tutorialHost`). Eğitim kum havuzunda çalışır: kâğıt, sayfa
/// yazıları ve çizimler eğitim bitince eski haline döner.
extension EditorViewModel: TutorialHost {
    var shapeSuggestion: ShapeSuggestion? { handwriting.shape }
    var textSuggestion: TextSuggestion? { handwriting.text }
    var lineSuggestion: LineSuggestion? { handwriting.line }

    func tutorialCanvas() -> CanvasUi? { store.canvas }

    func commitStroke(_ stroke: Stroke, suggest: Bool) { drawing.commitDemo(stroke, suggest: suggest) }

    func commitElements(_ strokes: [Stroke]) {
        guard let current = store.canvas, !strokes.isEmpty else { return }
        var order = current.strokes.count
        let added: [Stroke] = strokes.map { var s = $0; s.order = order; order += 1; return s }
        store.commit(current.strokes + added)
    }

    func mutate(_ block: ([Stroke]) -> [Stroke]) {
        guard let current = store.canvas else { return }
        store.commit(block(current.strokes).enumerated().map { i, s in
            if s.order == i { return s }
            var c = s; c.order = i; return c
        })
    }

    func tutorialSetMode(_ m: EditMode) {
        if m == mode { return }
        if m.kind == .BASE { setMode(m) } else { startAction(m) }
    }

    func select(_ ids: Set<String>) { store.select(ids) }
    func moveSelected(_ dx: Float, _ dy: Float) { selection.move(dx, dy) }
    func scaleSelected(_ factor: Float) { selection.scale(factor, factor) }

    func setPaperGradient(_ gradient: GradientSpec?) {
        // Kaydedilmez: eğitim bitince kâğıt eski haline döner
        store.updateCanvas { var c = $0; c.background.gradient = gradient; return c }
    }

    func balloon(_ style: BalloonStyle, speakers: Int, center: P, text: String) -> Stroke? {
        comic.balloonStroke(style, speakers: speakers, center: center, content: text)
    }
    func sfx(_ preset: SfxPreset, center: P, size: Float) -> Stroke? { comic.sfxStroke(preset, center: center, size: size) }
    func comicLines(_ kind: ComicLinesKind, density: Float, area: FRect, ink: String) -> [Stroke] {
        comic.linesStrokes(kind, density: density, area: area, ink: ink)
    }

    func acceptTemplate(_ position: Int) { handwriting.acceptTemplate(position) }
    func acceptLines() { handwriting.acceptLines() }
    func acceptText(_ text: String) { handwriting.accept(text) }
    func dismissSuggestions() { handwriting.reset() }
    func fit(_ stroke: Stroke) -> ShapeFix? { LineSuggester().fit(stroke) }

    func tutorialClosePanel() {
        // Açık yazı taslağı eğitimden önce belgeye yazılır (eğitim boş sayfada başlar)
        texts.commitOpenDraft()
        closePanel()
    }

    func finished(_ group: TutorialGroup) { emit(.tutorialFinished(group.title)) }

    // v37 çocuk eğitimi: kova imleci ve boyama (maskotun seçtiği renkle; kullanıcının boya rengi değişmez)
    func paintCursor(_ point: P?, hold: Int, color: String?) { kidsPaint.hover(point, hold: hold, color: color) }

    func paint(_ point: P, color: String, whole: Bool) -> Bool {
        let before = shapes.regionColor
        shapes.setRegionColor(color)
        let ok = whole ? shapes.paintWhole(point, locked: []) : shapes.paintAt(point, locked: [])
        shapes.setRegionColor(before)
        kidsPaint.refresh()
        return ok
    }

    // v40 cetvel ve kalıp, sayfa yazıları (kaydedilmez)
    var tutorialGuides: RulerController? { ruler }
    func setPageSettings(_ settings: PageSettings) { store.updateCanvas { var c = $0; c.pageSettings = settings; return c } }
    func assetImage(_ path: String, target: FRect, fill: Bool) async -> Stroke? { await images.assetImageStroke(path, target: target, fill: fill) }
}
