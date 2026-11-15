import Foundation

/// v52 not alma eğitimi (Android `TutorialV52`): formülle oluşturulan kimya ve fizik şekilleri (molekül formülden, Bohr
/// atomu atom numarasından, eğik atış hız ve açıdan) ve formül hesaplama (yuvalara değer yazılır, sonuç eklenir).
@MainActor
enum TutorialV52 {
    static let INK = "#263238"
    static let ACCENT = "#1565C0"
    static let MOLECULE = "lib.sci.chem.molecule"
    static let BOHR = "lib.sci.chem.bohr_atom"
    static let PROJECTILE = "lib.sci.phys.projectile"
    static let MOLECULE_ID = "tut52-molecule"
    static let BOHR_ID = "tut52-bohr"
    static let PROJECTILE_ID = "tut52-projectile"
    static let CALC_ID = "tut52-calc"
    static let CALC2_ID = "tut52-calc2"

    nonisolated static func param(_ kind: String, _ r: FRect, _ params: [String: String], id: String, width: Float = 2.5) -> Stroke? {
        ShapeFactory.build(kind, r.topLeft, r.bottomRight, style: ShapeStyle(strokeColorHex: INK, strokeWidth: width), graph: nil, id: id,
                           order: 0, params: params)
    }

    /// Şablon formülü [values] yuvalarına yazılmış ve hesaplanmış olarak (sonuç eklenir); [values] boşsa şablon.
    nonisolated static func calcSource(_ templateId: String, _ values: [String: String]) -> String? {
        guard let t = FormulaCatalog.find(templateId) else { return nil }
        var src = Formula.atomize(t.source)
        if values.isEmpty { return src }
        for s in Formula.slots(src) { if let v = values[s.value] { src = Formula.setSlot(src, s.name, v) } }
        return FormulaEval.annotate(src).source
    }

    nonisolated static func formula(_ templateId: String, _ values: [String: String], _ left: Float, _ top: Float, _ size: Float, id: String) -> Stroke? {
        guard let src = calcSource(templateId, values) else { return nil }
        let quad = FormulaRenderer.quadAt(src, size, left, top).map { StrokePoint(ContentCodec.quantize($0.x), ContentCodec.quantize($0.y)) }
        var s = Stroke(id: id, points: quad, colorHex: ACCENT, brushType: .FORMULA, sizePx: size, opacity: 1, order: 0)
        s.formula = FormulaSpec(source: src, templateId: templateId)
        return s
    }

    static let notesScience = TutorialGroup(
        id: "notes_science_v52", kind: .NOTES, title: "v52_tut_science", summary: "v52_tut_science_text", icon: "atom", ai: false,
        steps: [
            TutorialStep(TutorialAnchor.simple("shape"), "v52_tut_molecule", "v52_tut_molecule_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("shape"))
                let r = d.t52MoleculeRect()
                guard let s = param(MOLECULE, r, ["formula": "H2O", "view": "structural"], id: MOLECULE_ID) else { return }
                await d.drag(r.topLeft, r.bottomRight, durationMs: 600) { _, _ in [s] }
                await d.pause(500)
                for f in ["CO2", "CH4", "C2H5OH"] { await d.t52Redraw(MOLECULE_ID, MOLECULE, r, ["formula": f, "view": "structural"]) }
            },
            TutorialStep(TutorialAnchor.simple("shape"), "v52_tut_atom", "v52_tut_atom_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("shape"))
                let r = d.t52BohrRect()
                guard let s = param(BOHR, r, ["z": "6"], id: BOHR_ID, width: 2) else { return }
                await d.drag(r.topLeft, r.bottomRight, durationMs: 500) { _, _ in [s] }
                await d.pause(400)
                await d.t52Redraw(BOHR_ID, BOHR, r, ["z": "8"])
                await d.t52Redraw(BOHR_ID, BOHR, r, ["z": "11"])
            },
            TutorialStep(TutorialAnchor.simple("shape"), "v52_tut_projectile", "v52_tut_projectile_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("shape"))
                let r = d.t52ProjectileRect()
                guard let s = param(PROJECTILE, r, ["v": "20", "theta": "45"], id: PROJECTILE_ID) else { return }
                await d.drag(r.topLeft, r.bottomRight, durationMs: 600) { _, _ in [s] }
                await d.pause(400)
                await d.t52Redraw(PROJECTILE_ID, PROJECTILE, r, ["v": "20", "theta": "60"])
                await d.t52Redraw(PROJECTILE_ID, PROJECTILE, r, ["v": "30", "theta": "60"])
            },
            TutorialStep(TutorialAnchor.simple("formula"), "v52_tut_calc", "v52_tut_calc_text") { d in
                await d.tapAnchor(TutorialAnchor.simple("formula"))
                let left = d.w * 0.06, top = d.t52CalcTop(), size = d.u * 0.05
                guard let f = formula("geometry.circle_area", [:], left, top, size, id: CALC_ID) else { return }
                await d.place(P(left + d.u * 0.1, top + size), [f])
                await d.pause(700)
                await d.t52Reformula(CALC_ID, "geometry.circle_area", ["r": "3"], left, top, size)
                let top2 = top + size * 2.2
                guard let f2 = formula("physics.newton2", ["m": "4", "a": "3"], left, top2, size, id: CALC2_ID) else { return }
                await d.place(P(left + d.u * 0.1, top2 + size), [f2])
            },
            TutorialStep(TutorialAnchor.CANVAS, "v52_tut_calc_edit", "v52_tut_calc_edit_text") { d in
                let left = d.w * 0.06, top = d.t52CalcTop(), size = d.u * 0.05
                await d.t52Reformula(CALC_ID, "geometry.circle_area", ["r": "5"], left, top, size)
                await d.t52Reformula(CALC2_ID, "physics.newton2", ["m": "6", "a": "3"], left, top + size * 2.2, size)
            },
        ])
}

extension TutorialDemo {
    func t52MoleculeRect() -> FRect { FRect(w * 0.06, h * 0.07, w * 0.06 + u * 0.46, h * 0.07 + u * 0.33) }
    func t52BohrRect() -> FRect { FRect(w * 0.94 - u * 0.32, h * 0.07, w * 0.94, h * 0.07 + u * 0.32) }
    func t52ProjectileRect() -> FRect {
        let bw = min(w * 0.88, u * 0.8), top = h * 0.07 + u * 0.4
        return FRect(w * 0.06, top, w * 0.06 + bw, top + bw / 1.8)
    }
    func t52CalcTop() -> Float { t52ProjectileRect().bottom + u * 0.05 }

    func t52Redraw(_ id: String, _ kind: String, _ r: FRect, _ params: [String: String]) async {
        guard let next = TutorialV52.param(kind, r, params, id: id) else { return }
        replace(id, next)
        await pause(700)
    }

    func t52Reformula(_ id: String, _ templateId: String, _ values: [String: String], _ left: Float, _ top: Float, _ size: Float) async {
        guard let next = TutorialV52.formula(templateId, values, left, top, size, id: id) else { return }
        replace(id, next)
        await pause(800)
    }

    private func replace(_ id: String, _ next: Stroke) {
        host.mutate { list in list.map { s in
            guard s.id == id else { return s }
            var n = next; n.order = s.order; n.layerId = s.layerId
            return n
        } }
    }
}
