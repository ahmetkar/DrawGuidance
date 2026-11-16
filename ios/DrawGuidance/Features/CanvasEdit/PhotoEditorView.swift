import SwiftUI

/// v52 foto düzenleme sekmeleri (Android `PhotoTab`).
enum PhotoTab: String, CaseIterable, Identifiable {
    case crop, light, color, filters, effects, background, retouch
    var id: String { rawValue }
    var icon: String {
        switch self {
        case .crop: return "crop.rotate"
        case .light: return "sun.max"
        case .color: return "paintpalette"
        case .filters: return "camera.filters"
        case .effects: return "sparkles"
        case .background: return "wand.and.stars"
        case .retouch: return "paintbrush.pointed"
        }
    }
    var labelKey: String { self == .background ? "v53_photo_tab_mask" : "v52_photo_tab_\(rawValue)" }
}

private let retouchColors: [UInt32] = [0xFFFFFFFF, 0xFF000000, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFFF8BBD0, 0xFF8D6E63]
private let photoAspects: [(key: String?, ratio: Float?)] = [(nil, nil), ("orig", 0), ("1:1", 1), ("4:3", 4.0 / 3), ("3:4", 3.0 / 4), ("16:9", 16.0 / 9), ("9:16", 9.0 / 16)]

private func uiColor(_ argb: UInt32) -> Color {
    Color(red: Double(argb >> 16 & 0xFF) / 255, green: Double(argb >> 8 & 0xFF) / 255, blue: Double(argb & 0xFF) / 255)
}

/// v52 Foto düzenleme modu (tam ekran): üstte Kapat, Geri al, Yinele, Önce/sonra (basılı tut), Sıfırla, Kaydet;
/// ortada önizleme (iki parmakla yakınlaştır); altta menülere ayrılmış bütün raster düzenlemeleri.
struct PhotoEditorView: View {
    @ObservedObject var photo: PhotoEditController
    let kind: CanvasKind?
    @ObservedObject private var sub = SubscriptionStore.shared
    @State private var tab: PhotoTab = .crop
    @State private var comparing = false
    @State private var confirmDiscard = false
    @State private var premiumOpen = false
    @State private var aspectKey: String?
    @State private var brush: PhotoBrush = .LIGHTEN
    @State private var brushSize: Float = 0.08
    @State private var brushStrength: Float = 0.6
    @State private var brushColor: UInt32 = 0xFFE53935
    @State private var bgRemoveTap = true
    // v53 menülerin alt grupları
    @State private var lightSub = 0
    @State private var colorSub = 0
    @State private var effectsSub = 0
    @State private var maskSub = 0
    @State private var hslRange = 0
    @State private var balanceRange = 1

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ZStack(alignment: .topTrailing) {
                Color(red: 0.06, green: 0.07, blue: 0.08)
                if let img = comparing ? photo.original : photo.preview {
                    PhotoCanvasView(photo: photo, image: img, tab: tab, cropOverlay: photo.cropMode && !comparing,
                                    aspect: photoAspects.first { $0.key == aspectKey }?.ratio ?? nil,
                                    brush: brush, brushSize: brushSize, brushStrength: brushStrength, brushColor: brushColor, bgRemoveTap: bgRemoveTap)
                }
                if photo.loading || photo.rendering || photo.saving { ProgressView().tint(.white).padding(12) }
                if photo.saving {
                    Text(L("v52_photo_saving")).foregroundStyle(.white).padding(16)
                        .background(RoundedRectangle(cornerRadius: 12).fill(.black.opacity(0.6)))
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .frame(maxHeight: .infinity)
            ScrollView {
                VStack(alignment: .leading, spacing: 10) { panel }
                    .padding(.horizontal, 16).padding(.vertical, 10)
            }
            .frame(height: 230)
            .background(Color(.secondarySystemBackground))
            .accessibilityIdentifier("photo_panel_\(tab.rawValue)")
            tabBar
        }
        .background(Color(red: 0.06, green: 0.07, blue: 0.08).ignoresSafeArea())
        .accessibilityIdentifier("photo_editor")
        .onChangeCompat(of: tab) { _, t in photo.setCropMode(t == .crop) }
        .onAppear { photo.setCropMode(tab == .crop) }
        .confirmationDialog(L("v52_photo_discard"), isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button(L("v52_photo_discard_yes"), role: .destructive) { photo.close() }
            Button(L("cancel_button"), role: .cancel) {}
        }
        .sheet(isPresented: $premiumOpen) { PremiumRequiredSheet() }
    }

    private var topBar: some View {
        HStack(spacing: 6) {
            iconButton("xmark", L("close"), id: "photo_close") { if photo.canUndo { confirmDiscard = true } else { photo.close() } }
            Text(L("v52_photo_title")).font(.headline).foregroundStyle(.white).lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
            iconButton("arrow.uturn.backward", L("undo"), enabled: photo.canUndo, id: "photo_undo") { photo.undo() }
            iconButton("arrow.uturn.forward", L("redo"), enabled: photo.canRedo, id: "photo_redo") { photo.redo() }
            Image(systemName: "square.split.2x1")
                .foregroundStyle(.white).frame(width: 38, height: 38)
                .background(Circle().fill(comparing ? Color.accentColor : Color.white.opacity(0.12)))
                .onLongPressGesture(minimumDuration: 0, maximumDistance: 60, pressing: { comparing = $0 }, perform: {})
                .accessibilityLabel(L("v52_photo_compare")).accessibilityIdentifier("photo_compare")
            iconButton("arrow.counterclockwise", L("v52_photo_reset"), enabled: !photo.edit.isIdentity, id: "photo_reset") { aspectKey = nil; photo.reset() }
            iconButton("checkmark", L("save_button"), enabled: !photo.saving && !photo.loading, id: "photo_save", primary: true) { save() }
        }
        .padding(.horizontal, 10).padding(.vertical, 6)
    }

    private func iconButton(_ icon: String, _ label: String, enabled: Bool = true, id: String, primary: Bool = false, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).foregroundStyle(.white).frame(width: 38, height: 38)
                .background(Circle().fill(primary ? Color.accentColor : Color.white.opacity(0.12)))
        }
        .disabled(!enabled).opacity(enabled ? 1 : 0.4)
        .accessibilityLabel(label).accessibilityIdentifier(id)
    }

    private func save() {
        let e = photo.edit
        if e.background != nil && !sub.allows(.IMAGE_BG_REMOVAL, kind) { premiumOpen = true; return }
        let advanced = PhotoPremium.advancedLook(e.look) || (e.comic.map(PhotoPremium.advancedFilter) ?? false) || e.adjust.vignette > 0 || e.adjust.grain > 0
        if advanced && !sub.allows(.IMAGE_EFFECTS_ADVANCED, kind) { premiumOpen = true; return }
        photo.save()
    }

    private var tabBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(PhotoTab.allCases) { t in
                    Button { tab = t } label: {
                        VStack(spacing: 3) {
                            Image(systemName: t.icon).font(.system(size: 18))
                            Text(L(t.labelKey)).font(.caption2).lineLimit(1)
                        }
                        .frame(width: 84).padding(.vertical, 6)
                        .foregroundStyle(t == tab ? Color.accentColor : Color.primary)
                        .background(RoundedRectangle(cornerRadius: 12).fill(t == tab ? Color.accentColor.opacity(0.16) : .clear))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("photo_tab_\(t.rawValue)")
                }
            }
            .padding(.horizontal, 8).padding(.vertical, 6)
        }
        .background(Color(.secondarySystemBackground))
    }

    // MARK: paneller

    @ViewBuilder private var panel: some View {
        let e = photo.edit
        switch tab {
        case .crop:
            Text(L("v52_photo_crop_hint")).font(.caption).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    chipButton(L("v52_photo_rotate_left"), icon: "rotate.left", id: "photo_rotate_left") { aspectKey = nil; photo.update { $0.rotation = ($0.quarter + 3) % 4; $0.crop = PhotoCrop() } }
                    chipButton(L("v52_photo_rotate_right"), icon: "rotate.right", id: "photo_rotate_right") { aspectKey = nil; photo.update { $0.rotation = ($0.quarter + 1) % 4; $0.crop = PhotoCrop() } }
                    chipButton(L("v52_photo_flip_h"), icon: "arrow.left.and.right.righttriangle.left.righttriangle.right", id: "photo_flip_h") {
                        photo.update { $0.flipH.toggle(); let c = $0.crop; $0.crop = PhotoCrop(left: 1 - c.right, top: c.top, right: 1 - c.left, bottom: c.bottom) }
                    }
                    chipButton(L("v52_photo_flip_v"), icon: "arrow.up.and.down.righttriangle.up.righttriangle.down", id: "photo_flip_v") {
                        photo.update { $0.flipV.toggle(); let c = $0.crop; $0.crop = PhotoCrop(left: c.left, top: 1 - c.bottom, right: c.right, bottom: 1 - c.top) }
                    }
                }
            }
            slider(L("v52_photo_straighten"), e.straighten / 45, -1...1, "straighten") { v, x in x.straighten = v * 45 }
            Text(L("v52_photo_aspect")).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(photoAspects.indices, id: \.self) { i in
                        let a = photoAspects[i]
                        let label = a.key == nil ? L("v52_photo_aspect_free") : a.key == "orig" ? L("v52_photo_aspect_original") : a.key!
                        chip(label, selected: aspectKey == a.key, id: "photo_aspect_\(a.key ?? "free")") { setAspect(a.key, a.ratio) }
                    }
                }
            }
        case .light:
            // v53 alt gruplar: Temel, Tonlar, Ayrıntı
            subTabs(["v53_photo_sub_basic", "v53_photo_sub_tones", "v53_photo_sub_detail"], $lightSub, "light")
            if lightSub == 0 {
                slider(L("v52_photo_exposure"), e.light.exposure, -1...1, "exposure") { v, x in x.light.exposure = v }
                slider(L("adj_brightness"), e.adjust.brightness, -1...1, "brightness") { v, x in x.adjust.brightness = v }
                slider(L("adj_contrast"), e.adjust.contrast, -1...1, "contrast") { v, x in x.adjust.contrast = v }
            } else if lightSub == 1 {
                slider(L("v52_photo_highlights"), e.light.highlights, -1...1, "highlights") { v, x in x.light.highlights = v }
                slider(L("v52_photo_shadows"), e.light.shadows, -1...1, "shadows") { v, x in x.light.shadows = v }
                slider(L("v52_photo_whites"), e.light.whites, -1...1, "whites") { v, x in x.light.whites = v }
                slider(L("v52_photo_blacks"), e.light.blacks, -1...1, "blacks") { v, x in x.light.blacks = v }
            } else {
                slider(L("v52_photo_clarity"), e.light.clarity, -1...1, "clarity") { v, x in x.light.clarity = v }
                slider(L("adj_sharpen"), e.adjust.sharpen, 0...1, "sharpen") { v, x in x.adjust.sharpen = v }
            }
        case .color:
            // v53 alt gruplar: Temel, HSL, Renk dengesi
            subTabs(["v53_photo_sub_basic", "v53_photo_sub_hsl", "v53_photo_sub_balance"], $colorSub, "color")
            if colorSub == 0 {
                slider(L("adj_saturation"), e.adjust.saturation, -1...1, "saturation") { v, x in x.adjust.saturation = v }
                slider(L("v52_photo_vibrance"), e.light.vibrance, -1...1, "vibrance") { v, x in x.light.vibrance = v }
                slider(L("adj_warmth"), e.adjust.warmth, -1...1, "warmth") { v, x in x.adjust.warmth = v }
                slider(L("adj_tint"), e.adjust.tint, -1...1, "tint") { v, x in x.adjust.tint = v }
                slider(L("adj_fade"), e.adjust.fade, 0...1, "fade") { v, x in x.adjust.fade = v }
            } else if colorSub == 1 {
                Text(L("v53_photo_hsl_hint")).font(.caption).foregroundStyle(.secondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(0..<8, id: \.self) { i in
                            Circle().fill(uiColor(PhotoHsl.SWATCHES[i])).frame(width: 34, height: 34)
                                .overlay(Circle().stroke(i == hslRange ? Color.primary : Color.secondary.opacity(0.3), lineWidth: i == hslRange ? 3 : 1))
                                .onTapGesture { hslRange = i }
                                .accessibilityIdentifier("photo_hsl_range_\(i)")
                        }
                    }
                }
                let r = hslRange
                Text(L("v53_photo_range_\(r)")).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
                slider(L("v53_photo_hsl_hue"), e.hsl.hue[r], -1...1, "hsl_hue") { v, x in x.hsl.hue[r] = v }
                slider(L("v53_photo_hsl_sat"), e.hsl.saturation[r], -1...1, "hsl_sat") { v, x in x.hsl.saturation[r] = v }
                slider(L("v53_photo_hsl_light"), e.hsl.lightness[r], -1...1, "hsl_light") { v, x in x.hsl.lightness[r] = v }
                if !e.hsl.isIdentity {
                    Button(L("v53_photo_hsl_reset")) { photo.update { $0.hsl = PhotoHsl() } }.accessibilityIdentifier("photo_hsl_reset")
                }
            } else {
                subTabs(["v53_photo_bal_shadows", "v53_photo_bal_midtones", "v53_photo_bal_highlights"], $balanceRange, "balance")
                let b = balanceRange
                let vals = e.balance.range(b)
                slider(L("v53_photo_bal_cr"), vals[0], -1...1, "bal_cr") { v, x in x.balance.set(b, 0, v) }
                slider(L("v53_photo_bal_mg"), vals[1], -1...1, "bal_mg") { v, x in x.balance.set(b, 1, v) }
                slider(L("v53_photo_bal_yb"), vals[2], -1...1, "bal_yb") { v, x in x.balance.set(b, 2, v) }
                Toggle(L("v53_photo_bal_preserve"), isOn: Binding(get: { e.balance.preserve }, set: { v in photo.update { $0.balance.preserve = v } }))
                    .accessibilityIdentifier("photo_bal_preserve")
            }
        case .filters:
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ImageLook.allCases, id: \.self) { l in
                        let locked = PhotoPremium.advancedLook(l) && !sub.allows(.IMAGE_EFFECTS_ADVANCED, kind)
                        chip(L("look_\(l.rawValue.lowercased())") + (locked ? " 👑" : ""), selected: e.look == l, id: "photo_look_\(l.rawValue)") {
                            if locked { premiumOpen = true } else { photo.update { $0.look = l } }
                        }
                    }
                }
            }
        case .effects:
            // v53 alt gruplar: Çizgi roman, Bulanıklık, Vinyet ve gren
            subTabs(["v53_photo_sub_comic", "v53_photo_sub_blur", "v53_photo_sub_vignette"], $effectsSub, "effects")
            if effectsSub == 0 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        chip(L("v52_photo_none"), selected: e.comic == nil, id: "photo_comic_none") { photo.update { $0.comic = nil } }
                        ForEach(ComicFilter.allCases, id: \.self) { k in
                            let locked = PhotoPremium.advancedFilter(k) && !sub.allows(.IMAGE_EFFECTS_ADVANCED, kind)
                            chip(L("filter_\(k.rawValue.lowercased())") + (locked ? " 👑" : ""), selected: e.comic == k, id: "photo_comic_\(k.rawValue)") {
                                if locked { premiumOpen = true } else { photo.update { $0.comic = k } }
                            }
                        }
                    }
                }
            } else if effectsSub == 1 {
                slider(L("adj_blur"), e.adjust.blur, 0...1, "blur") { v, x in x.adjust.blur = v }
            } else {
                slider(L("adj_vignette"), e.adjust.vignette, 0...1, "vignette") { v, x in x.adjust.vignette = v }
                slider(L("adj_grain"), e.adjust.grain, 0...1, "grain") { v, x in x.adjust.grain = v }
            }
        case .background:
            // v53 Maske: arka plan kaldırma ve kırpma maskesi
            subTabs(["v53_photo_sub_bg", "v53_photo_sub_clip"], $maskSub, "mask")
            if maskSub == 1 { clipPanel(e) } else {
            Toggle(L("v52_photo_bg_on"), isOn: Binding(get: { e.background != nil }, set: { on in photo.update { $0.background = on ? BackgroundOptions() : nil } }))
                .accessibilityIdentifier("photo_bg_on")
            if let bg = e.background {
                Text(L("v52_photo_bg_tap_hint")).font(.caption).foregroundStyle(.secondary)
                Picker("", selection: $bgRemoveTap) {
                    Text(L("bg_tap_remove")).tag(true)
                    Text(L("bg_tap_keep")).tag(false)
                }
                .pickerStyle(.segmented)
                Toggle(L("bg_auto"), isOn: Binding(get: { bg.auto }, set: { v in photo.update { $0.background?.auto = v } }))
                Toggle(L("bg_holes"), isOn: Binding(get: { bg.removeHoles }, set: { v in photo.update { $0.background?.removeHoles = v } }))
                slider(L("bg_tolerance"), bg.tolerance, 0...1, "bg_tolerance") { v, x in x.background?.tolerance = v }
                slider(L("bg_feather"), bg.feather, 0...1, "bg_feather") { v, x in x.background?.feather = v }
                Button {
                    photo.update { x in
                        guard var b = x.background else { return }
                        if !b.removeAt.isEmpty && (bgRemoveTap || b.keepAt.isEmpty) { b.removeAt.removeLast() } else if !b.keepAt.isEmpty { b.keepAt.removeLast() }
                        x.background = b
                    }
                } label: { Label(L("bg_undo_tap"), systemImage: "arrow.uturn.backward") }
                .disabled(bg.removeAt.isEmpty && bg.keepAt.isEmpty)
            } else {
                Text(L("bg_remove_hint")).font(.caption).foregroundStyle(.secondary)
            }
            }
        case .retouch:
            Text(L("v52_photo_brush_hint")).font(.caption).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach([PhotoBrush.LIGHTEN, .DARKEN, .SMOOTH, .BLUR, .PAINT, .RESTORE], id: \.self) { b in
                        chip(L("v52_photo_brush_\(b.rawValue.lowercased())"), selected: brush == b, id: "photo_brush_\(b.rawValue.lowercased())") { brush = b }
                    }
                }
            }
            VStack(alignment: .leading) {
                Text(L("v52_photo_brush_size")).font(.subheadline)
                Slider(value: $brushSize, in: 0.01...0.3)
            }
            VStack(alignment: .leading) {
                Text(L("v52_photo_brush_strength")).font(.subheadline)
                Slider(value: $brushStrength, in: 0.1...1)
            }
            if brush == .PAINT {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(retouchColors, id: \.self) { c in
                            Circle().fill(uiColor(c)).frame(width: 30, height: 30)
                                .overlay(Circle().stroke(c == brushColor ? Color.accentColor : Color.secondary.opacity(0.4), lineWidth: c == brushColor ? 3 : 1))
                                .onTapGesture { brushColor = c }
                        }
                    }
                }
            }
        }
    }

    private func subTabs(_ keys: [String], _ sel: Binding<Int>, _ tag: String) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(keys.indices, id: \.self) { i in chip(L(keys[i]), selected: sel.wrappedValue == i, id: "photo_sub_\(tag)_\(i)") { sel.wrappedValue = i } }
            }
        }
    }

    /// v53 kırpma maskesi: renk ya da gradyan dolgusu yalnız resmin görünen kısmına karışır.
    @ViewBuilder private func clipPanel(_ e: PhotoEdit) -> some View {
        Toggle(L("v53_photo_clip_on"), isOn: Binding(get: { e.clip != nil }, set: { on in photo.update { $0.clip = on ? PhotoClip() : nil } }))
            .accessibilityIdentifier("photo_clip_on")
        Text(L("v53_photo_clip_hint")).font(.caption).foregroundStyle(.secondary)
        if let c = e.clip {
            HStack(spacing: 8) {
                chip(L("v53_photo_clip_color"), selected: c.fill == .COLOR, id: "photo_clip_fill_color") { photo.update { $0.clip?.fill = .COLOR } }
                chip(L("v53_photo_clip_gradient"), selected: c.fill == .GRADIENT, id: "photo_clip_fill_gradient") { photo.update { $0.clip?.fill = .GRADIENT } }
            }
            Text(L("v53_photo_clip_color1")).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
            swatches(c.color1) { v in photo.update { $0.clip?.color1 = v } }
            if c.fill == .GRADIENT {
                Text(L("v53_photo_clip_color2")).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
                swatches(c.color2) { v in photo.update { $0.clip?.color2 = v } }
                slider(L("v53_photo_clip_angle"), c.angle / 360, 0...1, "clip_angle") { v, x in x.clip?.angle = v * 360 }
            }
            Text(L("v53_photo_clip_blend")).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ClipBlend.allCases, id: \.self) { m in
                        chip(L("v53_photo_blend_\(m.rawValue.lowercased())"), selected: c.blend == m, id: "photo_clip_blend_\(m.rawValue)") { photo.update { $0.clip?.blend = m } }
                    }
                }
            }
            slider(L("v53_photo_clip_opacity"), c.opacity, 0...1, "clip_opacity") { v, x in x.clip?.opacity = v }
        }
    }

    private func swatches(_ selected: UInt32, _ onPick: @escaping (UInt32) -> Void) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(retouchColors, id: \.self) { c in
                    Circle().fill(uiColor(c)).frame(width: 30, height: 30)
                        .overlay(Circle().stroke(c == selected ? Color.accentColor : Color.secondary.opacity(0.4), lineWidth: c == selected ? 3 : 1))
                        .onTapGesture { onPick(c) }
                }
            }
        }
    }

    private func setAspect(_ key: String?, _ ratio: Float?) {
        aspectKey = key
        let (w, h) = photo.sourceSize
        photo.update { x in
            if let ratio {
                let r: Float = ratio == 0 ? { let s = PhotoOps.orientedSize(w, h, x.quarter); return Float(s.0) / Float(s.1) }() : ratio
                x.crop = PhotoOps.cropForAspect(w, h, x, r)
            } else {
                x.crop = PhotoCrop()
            }
        }
    }

    private func slider(_ label: String, _ value: Float, _ range: ClosedRange<Float>, _ key: String, _ apply: @escaping (Float, inout PhotoEdit) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label).font(.subheadline)
                Spacer()
                Text(range.lowerBound < 0 ? String(format: "%+d", Int(value * 100)) : "\(Int(value * 100))")
                    .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                    .onTapGesture { photo.update { apply(range.lowerBound < 0 ? 0 : range.lowerBound, &$0) } }
            }
            Slider(value: Binding(get: { value }, set: { v in photo.update(key) { apply(v, &$0) } }), in: range,
                   onEditingChanged: { editing in if !editing { photo.endGesture() } })
        }
        .accessibilityIdentifier("photo_\(key)")
    }

    private func chip(_ text: String, selected: Bool, id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.subheadline).lineLimit(1).padding(.horizontal, 12).padding(.vertical, 7)
                .foregroundStyle(selected ? Color.white : Color.primary)
                .background(Capsule().fill(selected ? Color.accentColor : Color.accentColor.opacity(0.1)))
        }
        .buttonStyle(.plain).accessibilityIdentifier(id)
    }

    private func chipButton(_ text: String, icon: String, id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(text, systemImage: icon).font(.subheadline).lineLimit(1).padding(.horizontal, 12).padding(.vertical, 7)
                .background(Capsule().fill(Color.accentColor.opacity(0.1)))
        }
        .buttonStyle(.plain).accessibilityIdentifier(id)
    }
}

/// v44 Premium kuralları (Android `PremiumRules`): Noir, Canlı, Soğuk, Sıcak, Eski fotoğraf görünümleri ve ton noktaları /
/// pop art filtreleri gelişmiş.
enum PhotoPremium {
    static func advancedLook(_ l: ImageLook) -> Bool { [.NOIR, .VIVID, .COOL, .WARM, .VINTAGE].contains(l) }
    static func advancedFilter(_ f: ComicFilter) -> Bool { f != .COMIC && f != .MANGA }
}

/// Önizleme: resim, kırpma çerçevesi, fırça izi; tek parmak sekmeye göre (kırp, fırça, dokunma), iki parmak yakınlaştırır.
private struct PhotoCanvasView: View {
    @ObservedObject var photo: PhotoEditController
    let image: CGImage
    let tab: PhotoTab
    let cropOverlay: Bool
    let aspect: Float?
    let brush: PhotoBrush
    let brushSize: Float
    let brushStrength: Float
    let brushColor: UInt32
    let bgRemoveTap: Bool

    @State private var scale: CGFloat = 1
    @State private var baseScale: CGFloat = 1
    @State private var pan: CGSize = .zero
    @State private var basePan: CGSize = .zero
    @State private var live: [CGPoint] = []
    @State private var points: [Float] = []
    @State private var dragMode = 0
    @State private var startCrop = PhotoCrop()
    @State private var startUv = CGPoint.zero

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            let rect = fitRect(size)
            ZStack {
                ZStack {
                    PhotoChecker().frame(width: rect.width, height: rect.height).position(x: rect.midX, y: rect.midY)
                    Image(decorative: image, scale: 1).resizable().interpolation(.high)
                        .frame(width: rect.width, height: rect.height).position(x: rect.midX, y: rect.midY)
                    if cropOverlay { CropFrame(crop: photo.edit.crop, rect: rect, scale: scale).accessibilityIdentifier("photo_crop_frame") }
                }
                .scaleEffect(scale)
                .offset(pan)
                if !live.isEmpty {
                    Path { p in p.addLines(live) }
                        .stroke(brush == .PAINT ? uiColor(brushColor).opacity(0.45) : Color.white.opacity(0.35),
                                style: StrokeStyle(lineWidth: max(4, CGFloat(brushSize) * min(rect.width, rect.height) * scale), lineCap: .round, lineJoin: .round))
                        .allowsHitTesting(false)
                }
            }
            .contentShape(Rectangle())
            .gesture(drag(size, rect))
            .simultaneousGesture(
                // v55 iOS 16: MagnifyGesture (iOS 17) yerine MagnificationGesture
                MagnificationGesture()
                    .onChanged { v in scale = min(6, max(1, baseScale * v)); if scale <= 1.001 { pan = .zero } }
                    .onEnded { _ in baseScale = scale; basePan = pan }
            )
            .accessibilityIdentifier("photo_canvas")
        }
        .clipped()
    }

    private func fitRect(_ size: CGSize) -> CGRect {
        let pad: CGFloat = 18
        let ar = CGFloat(image.width) / CGFloat(max(1, image.height))
        let aw = max(1, size.width - 2 * pad), ah = max(1, size.height - 2 * pad)
        let (w, h) = aw / ah > ar ? (ah * ar, ah) : (aw, aw / ar)
        return CGRect(x: (size.width - w) / 2, y: (size.height - h) / 2, width: w, height: h)
    }

    private func toUv(_ d: CGPoint, _ size: CGSize, _ rect: CGRect) -> CGPoint {
        let c = CGPoint(x: size.width / 2, y: size.height / 2)
        let p = CGPoint(x: (d.x - pan.width - c.x) / scale + c.x, y: (d.y - pan.height - c.y) / scale + c.y)
        return CGPoint(x: (p.x - rect.minX) / rect.width, y: (p.y - rect.minY) / rect.height)
    }

    private func drag(_ size: CGSize, _ rect: CGRect) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                switch tab {
                case .crop:
                    if dragMode == 0 { beginCrop(v.startLocation, size, rect) }
                    moveCrop(v.location, size, rect)
                case .retouch:
                    let uv = toUv(v.location, size, rect)
                    guard uv.x > -0.05, uv.y > -0.05, uv.x < 1.05, uv.y < 1.05 else { return }
                    let (sx, sy) = photo.toSource(Float(min(1, max(0, uv.x))), Float(min(1, max(0, uv.y))))
                    points += [sx, sy]
                    live.append(v.location)
                case .background:
                    break
                default:
                    if scale > 1 { pan = CGSize(width: basePan.width + v.translation.width, height: basePan.height + v.translation.height) }
                }
            }
            .onEnded { v in
                switch tab {
                case .crop:
                    dragMode = 0; photo.endGesture()
                case .retouch:
                    if points.count >= 2 {
                        let s = PhotoStroke(brush: brush, points: points, size: brushSize, strength: brushStrength, color: brushColor)
                        photo.update { $0.strokes.append(s) }
                    }
                    points = []; live = []
                case .background:
                    let moved = abs(v.translation.width) + abs(v.translation.height) > 8
                    guard !moved, var bg = photo.edit.background else { return }
                    let uv = toUv(v.location, size, rect)
                    guard (0...1).contains(uv.x), (0...1).contains(uv.y) else { return }
                    let pt = photo.toSource(Float(uv.x), Float(uv.y))
                    if bgRemoveTap { bg.removeAt.append(pt) } else { bg.keepAt.append(pt) }
                    photo.update { $0.background = bg }
                default:
                    basePan = pan
                }
            }
    }

    private func beginCrop(_ p: CGPoint, _ size: CGSize, _ rect: CGRect) {
        let c = photo.edit.crop
        let uv = toUv(p, size, rect)
        startUv = uv; startCrop = c
        let hx = 28 / (rect.width * scale), hy = 28 / (rect.height * scale)
        func near(_ x: Float, _ y: Float) -> Bool { abs(uv.x - CGFloat(x)) < hx && abs(uv.y - CGFloat(y)) < hy }
        let inX = (CGFloat(c.left)...CGFloat(c.right)).contains(uv.x), inY = (CGFloat(c.top)...CGFloat(c.bottom)).contains(uv.y)
        if near(c.left, c.top) { dragMode = 1 } else if near(c.right, c.top) { dragMode = 2 } else if near(c.right, c.bottom) { dragMode = 3 } else if near(c.left, c.bottom) { dragMode = 4 }
        else if aspect == nil && abs(uv.x - CGFloat(c.left)) < hx && inY { dragMode = 5 }
        else if aspect == nil && abs(uv.y - CGFloat(c.top)) < hy && inX { dragMode = 6 }
        else if aspect == nil && abs(uv.x - CGFloat(c.right)) < hx && inY { dragMode = 7 }
        else if aspect == nil && abs(uv.y - CGFloat(c.bottom)) < hy && inX { dragMode = 8 }
        else if inX && inY { dragMode = 9 }
        else { dragMode = -1 }
    }

    private func moveCrop(_ p: CGPoint, _ size: CGSize, _ rect: CGRect) {
        guard dragMode > 0 else { return }
        let uv = toUv(p, size, rect)
        let dx = Float(uv.x - startUv.x), dy = Float(uv.y - startUv.y)
        let c0 = startCrop
        let minS: Float = 0.05
        func cl(_ v: Float, _ lo: Float, _ hi: Float) -> Float { min(hi, max(lo, v)) }
        var next = c0
        switch dragMode {
        case 9:
            let w = c0.right - c0.left, h = c0.bottom - c0.top
            let l = cl(c0.left + dx, 0, 1 - w), t = cl(c0.top + dy, 0, 1 - h)
            next = PhotoCrop(left: l, top: t, right: l + w, bottom: t + h)
        case 1...4:
            var l = c0.left, t = c0.top, r = c0.right, b = c0.bottom
            switch dragMode { case 1: l += dx; t += dy; case 2: r += dx; t += dy; case 3: r += dx; b += dy; default: l += dx; b += dy }
            l = cl(l, 0, r - minS); r = cl(r, l + minS, 1); t = cl(t, 0, b - minS); b = cl(b, t + minS, 1)
            if let aspect {
                let k = aspect * Float(rect.height / rect.width)
                let w = r - l
                var h = w / k
                let anchorTop = dragMode == 3 || dragMode == 4
                if anchorTop { if t + h > 1 { h = 1 - t } } else { if b - h < 0 { h = b } }
                let ww = h * k
                if dragMode == 1 || dragMode == 4 { l = r - ww } else { r = l + ww }
                if anchorTop { b = t + h } else { t = b - h }
                if l < 0 || r > 1 { return }
            }
            next = PhotoCrop(left: l, top: t, right: r, bottom: b)
        case 5: next.left = cl(c0.left + dx, 0, c0.right - minS)
        case 6: next.top = cl(c0.top + dy, 0, c0.bottom - minS)
        case 7: next.right = cl(c0.right + dx, c0.left + minS, 1)
        default: next.bottom = cl(c0.bottom + dy, c0.top + minS, 1)
        }
        photo.update("crop") { $0.crop = next }
    }
}

private struct PhotoChecker: View {
    var body: some View {
        Canvas { ctx, size in
            let cell: CGFloat = 10
            var y: CGFloat = 0, row = 0
            while y < size.height {
                var x: CGFloat = 0, col = 0
                while x < size.width {
                    ctx.fill(Path(CGRect(x: x, y: y, width: cell, height: cell)), with: .color((row + col) % 2 == 0 ? Color(white: 0.23) : Color(white: 0.18)))
                    x += cell; col += 1
                }
                y += cell; row += 1
            }
        }
    }
}

private struct CropFrame: View {
    let crop: PhotoCrop
    let rect: CGRect
    let scale: CGFloat
    var body: some View {
        Canvas { ctx, _ in
            let r = CGRect(x: rect.minX + CGFloat(crop.left) * rect.width, y: rect.minY + CGFloat(crop.top) * rect.height,
                           width: CGFloat(crop.right - crop.left) * rect.width, height: CGFloat(crop.bottom - crop.top) * rect.height)
            var shade = Path(rect)
            shade.addRect(r)
            ctx.fill(shade, with: .color(.black.opacity(0.55)), style: FillStyle(eoFill: true))
            let sw = 1.5 / scale
            ctx.stroke(Path(r), with: .color(.white), lineWidth: sw)
            for i in 1...2 {
                var g = Path()
                g.move(to: CGPoint(x: r.minX + r.width * CGFloat(i) / 3, y: r.minY)); g.addLine(to: CGPoint(x: r.minX + r.width * CGFloat(i) / 3, y: r.maxY))
                g.move(to: CGPoint(x: r.minX, y: r.minY + r.height * CGFloat(i) / 3)); g.addLine(to: CGPoint(x: r.maxX, y: r.minY + r.height * CGFloat(i) / 3))
                ctx.stroke(g, with: .color(.white.opacity(0.5)), lineWidth: sw / 1.5)
            }
            let hl = 18 / scale
            let corners: [(CGPoint, CGFloat, CGFloat)] = [(CGPoint(x: r.minX, y: r.minY), 1, 1), (CGPoint(x: r.maxX, y: r.minY), -1, 1), (CGPoint(x: r.maxX, y: r.maxY), -1, -1), (CGPoint(x: r.minX, y: r.maxY), 1, -1)]
            for (p, dx, dy) in corners {
                var h = Path()
                h.move(to: CGPoint(x: p.x + hl * dx, y: p.y)); h.addLine(to: p); h.addLine(to: CGPoint(x: p.x, y: p.y + hl * dy))
                ctx.stroke(h, with: .color(.white), style: StrokeStyle(lineWidth: 4 / scale, lineCap: .round))
            }
        }
        .allowsHitTesting(false)
    }
}

/// v52 not almada ve çizgi romanda resim düzenlemenin yeri (Android `ImageEditChoiceSheet`).
struct ImageEditChoiceView: View {
    let comic: Bool
    let onPick: (ImageEditChoice) -> Void
    var body: some View {
        NavigationStack {
            List {
                row(.PHOTO, "camera.filters", "v52_image_edit_choice_photo")
                row(.ADVANCED, "paintbrush", "v52_image_edit_choice_advanced")
                if comic { row(.HERE, "crop", "v52_image_edit_choice_here") }
            }
            .navigationTitle(L("v52_image_edit_choice_title"))
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium])
        .accessibilityIdentifier("image_edit_choice")
    }

    private func row(_ c: ImageEditChoice, _ icon: String, _ key: String) -> some View {
        Button { onPick(c) } label: {
            HStack(spacing: 14) {
                Image(systemName: icon).font(.title2).foregroundStyle(Color.accentColor).frame(width: 32)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L(key)).font(.headline).foregroundStyle(.primary)
                    Text(L(key + "_hint")).font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 4)
        }
        .accessibilityIdentifier("image_edit_choice_\(c.rawValue.lowercased())")
    }
}

/// v52 resim düzenlemenin yapılacağı yer.
enum ImageEditChoice: String { case ADVANCED, PHOTO, HERE }
