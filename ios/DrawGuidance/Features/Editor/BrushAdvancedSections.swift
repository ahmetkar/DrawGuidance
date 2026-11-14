import SwiftUI

/// v51 gelişmiş fırça ayarları (Android `BrushAdvancedSections`): yardımcı fırça, boyut ve şekil, boyama ve yoğunluk,
/// kalem ve hareket dinamikleri; ayrıca damga aralığı. Fırça seçicinin listesine bölümler olarak eklenir.
struct BrushAdvancedSections: View {
    @ObservedObject var brush: BrushController

    private var type: BrushType { brush.settings.brushType }
    private var tuning: BrushTuning { brush.settings.tuning }

    var body: some View {
        if type != .ERASER {
            let raster = type.isRaster
            let eff = RasterBrush.effectiveTip(type, BrushParams(tip: tuning.tip))
            Group {
                if type.isHelper { helperSection }
                shapeSection(raster, eff)
                if raster { paintSection(eff) }
                penSection(raster)
                otherSection
            }
        }
    }

    // MARK: Yardımcılar

    private func r2(_ v: Double) -> Float { Float((v * 100).rounded()) / 100 }
    private func pct(_ v: Float) -> String { "%\(Int((v * 100).rounded()))" }
    private func signedPct(_ v: Float) -> String {
        v > 0 ? "+%\(Int((v * 100).rounded()))" : v < 0 ? "−%\(Int((-v * 100).rounded()))" : "%0"
    }

    private func setTip(_ f: @escaping (inout TipParams) -> Void) { brush.updateTuning { t in var n = t; f(&n.tip); return n } }
    private func setPen(_ f: @escaping (inout PenDynamics) -> Void) { brush.updateTuning { t in var n = t; f(&n.pen); return n } }
    private func setFx(_ f: @escaping (inout FxParams) -> Void) { brush.updateTuning { t in var n = t; f(&n.fx); return n } }

    private func slider(_ titleKey: String, _ value: Float, _ range: ClosedRange<Double>, _ text: String, id: String, hint: String? = nil,
                        _ set: @escaping (Double) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(L(titleKey))
                Spacer()
                Text(text).foregroundStyle(.secondary).monospacedDigit()
            }
            Slider(value: Binding(get: { Double(value) }, set: set), in: range)
                .accessibilityIdentifier(id)
            if let hint { Text(hint).font(.caption).foregroundStyle(.secondary) }
        }
    }

    private func chip(_ titleKey: String, _ selected: Bool, id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(L(titleKey))
                .font(.subheadline)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(Capsule().fill(selected ? Color.accentColor.opacity(0.2) : Color.secondary.opacity(0.12)))
                .overlay(Capsule().stroke(selected ? Color.accentColor : Color.clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
        .accessibilityAddTraits(selected ? AccessibilityTraits.isSelected : [])
    }

    private func chips<T: Hashable>(_ items: [(T, String)], selected: T, idPrefix: String, _ pick: @escaping (T) -> Void) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items, id: \.0) { item in
                    chip(item.1, item.0 == selected, id: "\(idPrefix)_\(item.0)") { pick(item.0) }
                }
            }
        }
    }

    // MARK: Bölümler

    private var helperHintKey: String {
        switch type {
        case .FX_BLUR: return "v51_fx_blur_hint"
        case .FX_SHARPEN: return "v51_fx_sharpen_hint"
        case .FX_DODGE: return "v51_fx_dodge_hint"
        case .FX_BURN: return "v51_fx_burn_hint"
        case .FX_SPONGE: return "v51_fx_sponge_hint"
        case .FX_CLONE: return "v51_fx_clone_hint"
        case .FX_HEAL: return "v51_fx_heal_hint"
        default: return "v51_fx_mixer_hint"
        }
    }

    private var helperSection: some View {
        Section(L("v51_sec_helper")) {
            Text(L(helperHintKey)).font(.footnote)
            if type != .FX_MIXER { Text(L("v51_fx_hint")).font(.caption).foregroundStyle(.secondary) }
            if type == .FX_DODGE || type == .FX_BURN {
                VStack(alignment: .leading, spacing: 6) {
                    Text(L("v51_fx_range")).font(.subheadline.weight(.semibold))
                    let mode = (0...2).contains(tuning.fx.mode) ? tuning.fx.mode : 1
                    chips([(0, "v51_fx_shadows"), (1, "v51_fx_midtones"), (2, "v51_fx_highlights")], selected: mode,
                          idPrefix: "brush_v51_range") { m in setFx { $0.mode = m } }
                }
            }
            if type == .FX_SPONGE {
                VStack(alignment: .leading, spacing: 6) {
                    Text(L("v51_fx_mode")).font(.subheadline.weight(.semibold))
                    chips([(0, "v51_fx_desaturate"), (1, "v51_fx_saturate")], selected: tuning.fx.mode,
                          idPrefix: "brush_v51_sponge") { m in setFx { $0.mode = m } }
                }
            }
            if type == .FX_CLONE || type == .FX_HEAL {
                VStack(alignment: .leading, spacing: 6) {
                    Text(L("v51_fx_source")).font(.subheadline.weight(.semibold))
                    Text(sourceState).font(.caption).foregroundStyle(.secondary)
                        .accessibilityIdentifier("brush_v51_source_state")
                    HStack(spacing: 8) {
                        chip("v51_fx_pick_source", brush.cloneSourcePicking, id: "brush_v51_pick_source") { brush.startPickCloneSource() }
                        if tuning.fx.srcDx != nil {
                            chip("v51_fx_source_reset", false, id: "brush_v51_source_reset") { setFx { $0.srcDx = nil; $0.srcDy = nil } }
                        }
                    }
                }
            }
        }
    }

    private var sourceState: String {
        if brush.cloneSourcePicking { return L("v51_fx_picking") }
        if brush.cloneSource != nil { return L("v51_fx_source_wait") }
        if let dx = tuning.fx.srcDx, let dy = tuning.fx.srcDy { return L("v51_fx_source_set", Int(dx.rounded()), Int(dy.rounded())) }
        return L("v51_fx_source_auto")
    }

    private func shapeSection(_ raster: Bool, _ eff: TipParams) -> some View {
        let tip = tuning.tip
        let minS = tip.minSize ?? (raster ? eff.minSize ?? 0 : 0)
        let maxS = tip.maxSize ?? 1
        return Section(L("v51_sec_shape")) {
            slider("v51_min_size", minS, 0...1, pct(minS), id: "brush_v51_min_size") { v in setTip { $0.minSize = r2(v) } }
            slider("v51_max_size", maxS, 0.2...3, pct(maxS), id: "brush_v51_max_size") { v in setTip { $0.maxSize = r2(v) } }
            if raster {
                let h = eff.hardness ?? 0.5, so = eff.softness ?? 0, asp = eff.aspect ?? 1, ang = eff.angle ?? 0
                let rot = eff.rotation ?? 0, rnd = eff.roundness ?? 1, edge = eff.edge ?? 0.5
                slider("v51_hardness", h, 0...1, pct(h), id: "brush_v51_hardness") { v in setTip { $0.hardness = r2(v) } }
                slider("v51_softness", so, 0...1, pct(so), id: "brush_v51_softness") { v in setTip { $0.softness = r2(v) } }
                slider("v51_aspect", asp, 0.05...1, pct(asp), id: "brush_v51_aspect") { v in setTip { $0.aspect = r2(v) } }
                slider("v51_angle", ang, 0...359, L("degree_value", Int(ang.rounded())), id: "brush_v51_angle") { v in
                    setTip { $0.angle = Float(v.rounded()) }
                }
                slider("v51_rotation", rot, 0...1, pct(rot), id: "brush_v51_rotation") { v in setTip { $0.rotation = r2(v) } }
                slider("v51_roundness", rnd, 0...1, pct(rnd), id: "brush_v51_roundness") { v in setTip { $0.roundness = r2(v) } }
                slider("v51_edge", edge, 0...1, pct(edge), id: "brush_v51_edge") { v in setTip { $0.edge = r2(v) } }
                VStack(alignment: .leading, spacing: 6) {
                    Text(L("v51_tip_shape")).font(.subheadline.weight(.semibold))
                    let shapes: [(TipShape, String)] = [
                        (.ROUND, "v51_tip_round"), (.SQUARE, "v51_tip_square"), (.FLAT, "v51_tip_flat"), (.CHISEL, "v51_tip_chisel"),
                        (.BRISTLE, "v51_tip_bristle"), (.SPONGE, "v51_tip_sponge"), (.SPLATTER, "v51_tip_splatter"), (.CLOUD, "v51_tip_cloud"),
                    ]
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(shapes, id: \.0) { item in
                                chip(item.1, (eff.shape ?? .ROUND) == item.0, id: "brush_v51_tip_\(item.0.rawValue.lowercased())") {
                                    setTip { $0.shape = item.0 }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func paintSection(_ eff: TipParams) -> some View {
        Section(L("v51_sec_paint")) {
            Text(L("v51_paint_hint")).font(.caption).foregroundStyle(.secondary)
            let den = eff.density ?? 1, bu = eff.buildUp ?? 0
            slider("v51_density", den, 0...1, pct(den), id: "brush_v51_density") { v in setTip { $0.density = r2(v) } }
            slider("v51_buildup", bu, 0...1, pct(bu), id: "brush_v51_buildup") { v in setTip { $0.buildUp = r2(v) } }
            if RasterBrush.paints(type) {
                let wet = eff.wetness ?? 0, pa = eff.paintAmount ?? 1, th = eff.thickness ?? 0
                let ca = eff.colorAmount ?? 1, sm = eff.smudge ?? 0, bl = eff.blend ?? 0
                slider("v51_wetness", wet, 0...1, pct(wet), id: "brush_v51_wetness") { v in setTip { $0.wetness = r2(v) } }
                slider("v51_paint_amount", pa, 0...1, pct(pa), id: "brush_v51_paint_amount") { v in setTip { $0.paintAmount = r2(v) } }
                slider("v51_thickness", th, 0...1, pct(th), id: "brush_v51_thickness") { v in setTip { $0.thickness = r2(v) } }
                slider("v51_color_amount", ca, 0...1, pct(ca), id: "brush_v51_color_amount") { v in setTip { $0.colorAmount = r2(v) } }
                slider("v51_smudge", sm, 0...1, pct(sm), id: "brush_v51_smudge") { v in setTip { $0.smudge = r2(v) } }
                slider("v51_blend", bl, 0...1, pct(bl), id: "brush_v51_blend") { v in setTip { $0.blend = r2(v) } }
            }
        }
    }

    private func penSection(_ raster: Bool) -> some View {
        let pen = tuning.pen
        let p = pen.pressure ?? 0, po = pen.pressureOpacity ?? 0, tx = pen.tiltX ?? 0, ty = pen.tiltY ?? 0
        let td = pen.tiltDirection ?? 0, ro = pen.rotation ?? 0, ve = pen.velocity ?? 0, di = pen.direction ?? 0
        let ds = pen.distance ?? 0, ti = pen.time ?? 0, ba = pen.barrel ?? 0
        func pos(_ v: Double) -> Float? { let x = r2(v); return x > 0 ? x : nil }
        func nonZero(_ v: Double) -> Float? { let x = r2(v); return x != 0 ? x : nil }
        return Section(L("v51_sec_pen")) {
            Text(L("v51_pen_hint")).font(.caption).foregroundStyle(.secondary)
            slider("v51_pen_pressure", p, 0...1, pen.pressure == nil ? L("v51_default") : pct(p), id: "brush_v51_pen_pressure") { v in
                setPen { $0.pressure = r2(v) }
            }
            slider("v51_pen_pressure_opacity", po, 0...1, pct(po), id: "brush_v51_pen_pressure_opacity") { v in setPen { $0.pressureOpacity = pos(v) } }
            slider("v51_pen_tilt_x", tx, -1...1, signedPct(tx), id: "brush_v51_pen_tilt_x") { v in setPen { $0.tiltX = nonZero(v) } }
            slider("v51_pen_tilt_y", ty, -1...1, signedPct(ty), id: "brush_v51_pen_tilt_y") { v in setPen { $0.tiltY = nonZero(v) } }
            if raster {
                slider("v51_pen_tilt_dir", td, 0...1, pct(td), id: "brush_v51_pen_tilt_dir") { v in setPen { $0.tiltDirection = pos(v) } }
                slider("v51_pen_rotation", ro, 0...1, pct(ro), id: "brush_v51_pen_rotation", hint: L("v51_pen_rotation_hint")) { v in
                    setPen { $0.rotation = pos(v) }
                }
            }
            slider("v51_pen_velocity", ve, -1...1, signedPct(ve), id: "brush_v51_pen_velocity") { v in setPen { $0.velocity = nonZero(v) } }
            if raster {
                slider("v51_pen_direction", di, 0...1, pct(di), id: "brush_v51_pen_direction") { v in setPen { $0.direction = pos(v) } }
            }
            slider("v51_pen_distance", ds, 0...1, pct(ds), id: "brush_v51_pen_distance") { v in setPen { $0.distance = pos(v) } }
            slider("v51_pen_time", ti, 0...1, pct(ti), id: "brush_v51_pen_time") { v in setPen { $0.time = pos(v) } }
            slider("v51_pen_barrel", ba, -1...1, signedPct(ba), id: "brush_v51_pen_barrel", hint: L("v51_pen_barrel_hint")) { v in
                setPen { $0.barrel = nonZero(v) }
            }
        }
    }

    /// Damga aralığı (damgalarla çizen fırçalarda) ve sıfırlama.
    @ViewBuilder private var otherSection: some View {
        let def = type.defaultSpacing
        if def != nil || tuning != BrushTuning() {
            Section {
                if let def {
                    let sp = tuning.spacing ?? def
                    slider("brush_spacing_label", sp, 0.02...3, pct(sp), id: "brush_spacing", hint: L("brush_spacing_hint")) { v in
                        brush.updateTuning { t in var n = t; n.spacing = r2(v); return n }
                    }
                    if let cur = tuning.spacing, abs(cur - def) > 1e-3 {
                        Button(L("brush_default") + " · " + pct(def)) {
                            brush.updateTuning { t in var n = t; n.spacing = nil; return n }
                        }
                        .accessibilityIdentifier("brush_spacing_default")
                    }
                }
                if tuning != BrushTuning() {
                    Button(L("brush_tuning_reset")) { brush.resetTuning() }
                        .accessibilityIdentifier("brush_tuning_reset")
                }
            }
        }
    }
}
