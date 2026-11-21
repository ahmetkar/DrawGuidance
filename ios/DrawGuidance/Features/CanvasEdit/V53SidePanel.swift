import SwiftUI

/// v53 sağdan açılan panel (Android `EditorSidePanel`): Elemanlar, Katmanlar ve Özellikler aynı pencerede sekmeler.
/// Özellikler, seçili eleman varsa onun özelliklerini; yoksa seçili aracın (fırça, silgi, yazı) ayarlarını gösterir.
struct EditorSidePanelHost: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @Binding var brushPickerOpen: Bool

    var body: some View {
        GeometryReader { geo in
            if let tab = vm.panel {
                HStack(spacing: 0) {
                    Spacer(minLength: 0)
                    EditorSidePanel(vm: vm, store: store, brush: vm.brush, texts: vm.texts, tab: tab, brushPickerOpen: $brushPickerOpen)
                        .frame(width: min(geo.size.width * 0.86, 360))
                        .frame(maxHeight: .infinity)
                        .background(
                            UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: 22, style: .continuous)
                                .fill(Color(uiColor: .systemBackground))
                                .shadow(color: .black.opacity(0.18), radius: 16, x: -4)
                        )
                        .clipShape(UnevenRoundedRectangle(topLeadingRadius: 22, bottomLeadingRadius: 22, style: .continuous))
                        .transition(.move(edge: .trailing).combined(with: .opacity))
                }
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("side_panel")
            }
        }
        .animation(.easeOut(duration: 0.25), value: vm.panel)
    }
}

struct EditorSidePanel: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var brush: BrushController
    @ObservedObject var texts: TextController
    let tab: PanelTab
    @Binding var brushPickerOpen: Bool

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                Picker("", selection: Binding(get: { tab }, set: { t in if t != tab { vm.togglePanel(t) } })) {
                    ForEach(AdvancedMenus.panelTabs, id: \.0) { Text(L($0.2)).tag($0.0) }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("side_panel_tabs")
                Button { vm.closePanel() } label: {
                    Image(systemName: "xmark").font(.system(size: 13, weight: .bold)).foregroundStyle(.secondary)
                        .frame(width: 30, height: 30).background(Circle().fill(Color.primary.opacity(0.07)))
                }
                .accessibilityLabel(L("close"))
                .accessibilityIdentifier("side_panel_close")
            }
            .padding(.horizontal, 12).padding(.top, 12).padding(.bottom, 8)
            Divider()
            switch tab {
            case .ELEMENTS: ElementsList(vm: vm, store: store)
            case .LAYERS: LayersList(vm: vm, store: store)
            case .BRUSH: PropertiesView(vm: vm, store: store, brush: brush, texts: texts, brushPickerOpen: $brushPickerOpen)
            }
        }
    }
}

// MARK: - Elemanlar

/// Elemanların listesi: en son eklenen en üstte; türüne göre simge, ad ve renk; dokununca seçime eklenir/çıkarılır.
struct ElementsList: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore

    var body: some View {
        let rows = ElementOps.rows(store.elements)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 6) {
                if rows.isEmpty {
                    EmptyPanelNote(icon: "square.grid.2x2", text: L("elements_empty"))
                } else {
                    Text(L("elements_select_hint")).font(.caption).foregroundStyle(.secondary).padding(.horizontal, 4)
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, row in rowView(row) }
                }
            }
            .padding(12)
        }
        .safeAreaInset(edge: .bottom) {
            if !store.selectedIds.isEmpty {
                HStack(spacing: 4) {
                    ChromeButton(icon: "plus.square.on.square", label: L("duplicate_selection"), labelStyle: .below, id: "panel_duplicate") { vm.selection.duplicate() }
                    ChromeButton(icon: "eye.slash", label: L("hide_elements"), labelStyle: .below, id: "panel_hide") { vm.hideElements() }
                    ChromeButton(icon: "square.2.layers.3d.top.filled", label: L("order_forward"), labelStyle: .below, id: "panel_front") { vm.selection.reorder(1) }
                    ChromeButton(icon: "trash", label: L("delete_selection"), danger: true, labelStyle: .below, id: "panel_delete") { vm.selection.delete() }
                }
                .frame(maxWidth: .infinity)
                .padding(8)
                .background(.regularMaterial)
            }
        }
    }

    @ViewBuilder private func rowView(_ row: ElementRow) -> some View {
        switch row {
        case let .single(e):
            elementRow(e, indent: false)
        case let .group(groupId, number, members):
            let selected = members.allSatisfy { m in m.strokeIds.allSatisfy(store.selectedIds.contains) }
            Button { vm.selection.toggleGroup(groupId) } label: {
                HStack(spacing: 10) {
                    Image(systemName: "square.stack.3d.up").frame(width: 30, height: 30)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Color.accentColor.opacity(0.12)))
                    Text(L("v53_group_n", number)).font(.subheadline.weight(.semibold))
                    Spacer()
                    if selected { Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.accentColor) }
                }
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 12).fill(selected ? Color.accentColor.opacity(0.1) : Color.primary.opacity(0.04)))
            }
            .buttonStyle(.plain)
            ForEach(members) { m in elementRow(m, indent: true) }
        }
    }

    private func elementRow(_ e: Element, indent: Bool) -> some View {
        let selected = e.strokeIds.allSatisfy(store.selectedIds.contains)
        return Button { vm.selection.toggleElement(e.id) } label: {
            HStack(spacing: 10) {
                Image(systemName: Self.icon(e.brushType)).font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color(hex: e.colorHex) ?? .primary)
                    .frame(width: 30, height: 30)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.primary.opacity(0.06)))
                Text("\(L(e.brushType.titleKey)) \(e.number)").font(.subheadline)
                    .foregroundStyle(e.hidden ? .secondary : .primary).lineLimit(1)
                Spacer(minLength: 4)
                if e.hidden { Image(systemName: "eye.slash").foregroundStyle(.secondary) }
                if selected { Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.accentColor) }
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 12).fill(selected ? Color.accentColor.opacity(0.1) : Color.primary.opacity(0.04)))
            .padding(.leading, indent ? 18 : 0)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("element_\(e.number)")
    }

    static func icon(_ t: BrushType) -> String {
        switch t {
        case .TEXT: return "textformat"
        case .IMAGE: return "photo"
        case .SHAPE: return "square.on.circle"
        case .DIAGRAM: return "point.3.connected.trianglepath.dotted"
        case .FORMULA: return "function"
        case .LASSO_FILL: return "drop.fill"
        default: return BrushSymbols.of(t)
        }
    }
}

struct EmptyPanelNote: View {
    let icon: String
    let text: String
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 34)).foregroundStyle(.secondary.opacity(0.6))
            Text(text).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 28).padding(.horizontal, 8)
        .accessibilityIdentifier("properties_empty")
    }
}

// MARK: - Katmanlar

struct LayersList: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @State private var renaming: Layer?
    @State private var newName = ""
    // v56 aşama 7 çoklu seçimle birleştirme
    @State private var picking = false
    @State private var picked: Set<String> = []
    @State private var confirmRaster = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                Button { _ = vm.layers.add() } label: {
                    Label(L("v53_layer_add"), systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity).padding(10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.accentColor.opacity(0.12)))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("layer_add")
                if vm.v56(.LAYER_DUPLICATE_MERGE) && (store.canvas?.layers.count ?? 1) > 1 {
                    HStack(spacing: 8) {
                        Toggle(L("v56_layer_pick"), isOn: Binding(get: { picking }, set: { picking = $0; picked = [] }))
                            .toggleStyle(.button).font(.caption).accessibilityIdentifier("v56_layer_pick")
                        if picking {
                            Button(L("v56_layer_merge", picked.count)) { merge(picked) }
                                .buttonStyle(.borderedProminent).font(.caption).disabled(picked.count < 2)
                                .accessibilityIdentifier("v56_layer_merge")
                        }
                    }
                }
                ForEach((store.canvas?.layers ?? [Layer.Default]).reversed()) { layer in row(layer) }
                Text(L("layers_hint")).font(.caption).foregroundStyle(.secondary).padding(.top, 6)
            }
            .padding(12)
        }
        .alert(L("layer_rename"), isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("", text: $newName)
            Button(L("apply_button")) { if let r = renaming { vm.layers.rename(r.id, newName) }; renaming = nil }
            Button(L("v53_cancel"), role: .cancel) { renaming = nil }
        }
        .alert(L("v56_layer_merge_raster_title"), isPresented: $confirmRaster) {
            Button(L("v56_layer_merge_confirm")) { vm.mergeLayers(picked); picking = false; picked = [] }
            Button(L("v53_cancel"), role: .cancel) {}
        } message: { Text(L("v56_layer_merge_raster_text")) }
    }

    private func merge(_ ids: Set<String>) {
        picked = ids
        if vm.layersNeedRaster(ids) { confirmRaster = true } else { vm.mergeLayers(ids); picking = false; picked = [] }
    }

    private func row(_ layer: Layer) -> some View {
        let active = store.activeLayer == layer.id
        let kind = store.canvas?.kind ?? .ART
        let index = store.canvas?.layers.firstIndex(where: { $0.id == layer.id }) ?? 0
        return VStack(alignment: .leading, spacing: 8) {
            rowHeader(layer, active)
            if active { LayerLookRow(vm: vm, layer: layer, index: index, kind: kind) }
        }
        .buttonStyle(.borderless)
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 12).fill(active ? Color.accentColor.opacity(0.1) : Color.primary.opacity(0.04)))
        .accessibilityIdentifier("layer_\(layer.id)")
    }

    private func rowHeader(_ layer: Layer, _ active: Bool) -> some View {
        HStack(spacing: 8) {
            if picking {
                Button { if picked.contains(layer.id) { picked.remove(layer.id) } else { picked.insert(layer.id) } } label: {
                    Image(systemName: picked.contains(layer.id) ? "checkmark.square.fill" : "square")
                }
                .accessibilityIdentifier("v56_layer_check_\(layer.id)")
            }
            Button { vm.layers.setActive(layer.id) } label: {
                Image(systemName: active ? "largecircle.fill.circle" : "circle").foregroundStyle(active ? Color.accentColor : .secondary)
            }
            Text(layer.name).font(.subheadline.weight(active ? .semibold : .regular)).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .onTapGesture { vm.layers.setActive(layer.id) }
            Button { vm.layers.toggleVisible(layer.id) } label: { Image(systemName: layer.visible ? "eye" : "eye.slash") }
            Button { vm.layers.toggleLocked(layer.id) } label: { Image(systemName: layer.locked ? "lock.fill" : "lock.open") }
            Menu {
                Button { renaming = layer; newName = layer.name } label: { Label(L("layer_rename"), systemImage: "pencil") }
                Button { vm.layers.move(layer.id, 1) } label: { Label(L("order_forward"), systemImage: "arrow.up") }
                Button { vm.layers.move(layer.id, -1) } label: { Label(L("order_backward"), systemImage: "arrow.down") }
                if !store.selectedIds.isEmpty {
                    Button { vm.layers.moveSelectionTo(layer.id) } label: { Label(L("v53_layer_move_here"), systemImage: "square.and.arrow.down.on.square") }
                }
                if vm.v56(.LAYER_DUPLICATE_MERGE) {
                    Button { vm.layers.duplicate(layer.id) } label: { Label(L("v56_layer_duplicate"), systemImage: "plus.square.on.square") }
                        .accessibilityIdentifier("v56_layer_duplicate")
                    if let below = LayerOps.below(store.canvas?.layers ?? [], layer.id) {
                        Button { merge([layer.id, below.id]) } label: { Label(L("v56_layer_merge_down"), systemImage: "arrow.down.to.line") }
                            .accessibilityIdentifier("v56_layer_merge_down")
                    }
                }
                if vm.v56(.GRAIN) && !layer.locked && layer.visible {
                    Button { vm.textureFillTarget = "layer:\(layer.id)" } label: { Label(L("v56_texture_fill_layer"), systemImage: "square.grid.3x3.topleft.filled") }
                        .accessibilityIdentifier("v56_layer_texture_fill")
                }
                if layer.id != Layer.BASE {
                    Button(role: .destructive) { vm.layers.remove(layer.id) } label: { Label(L("delete_selection"), systemImage: "trash") }
                }
            } label: { Image(systemName: "ellipsis.circle") }
        }
    }
}

/// v56 karışım modunun adı (Android `layerBlendLabel`).
func layerBlendLabel(_ b: LayerBlend) -> String { L("v56_blend_" + b.rawValue.lowercased()) }

/// v56 etkin katmanın altında: opaklık, karışım modu, alfa kilidi, alttakine kırp (mod kuralı: `V56Flags`).
struct LayerLookRow: View {
    @ObservedObject var vm: EditorViewModel
    let layer: Layer
    let index: Int
    let kind: CanvasKind
    @State private var opacity: Float = 1

    var body: some View {
        let blend = V56Flags.enabled(.LAYER_BLEND, kind), lockClip = V56Flags.enabled(.ALPHA_LOCK_CLIP, kind)
        VStack(alignment: .leading, spacing: 6) {
            if blend {
                Text(L("v56_layer_opacity", Int((opacity * 100).rounded()))).font(.caption).foregroundStyle(.secondary)
                Slider(value: Binding(get: { Double(opacity) }, set: { opacity = Float($0); vm.layers.setOpacity(layer.id, Float($0)) }), in: 0...1)
                    .accessibilityIdentifier("layer_opacity")
                Menu {
                    ForEach(LayerBlend.allCases, id: \.self) { b in
                        Button { vm.layers.setBlend(layer.id, b) } label: {
                            if b == layer.blend { Label(layerBlendLabel(b), systemImage: "checkmark") } else { Text(layerBlendLabel(b)) }
                        }
                    }
                } label: {
                    Label(L("v56_layer_blend") + ": " + layerBlendLabel(layer.blend), systemImage: "square.2.layers.3d")
                        .font(.caption.weight(.semibold))
                }
                .accessibilityIdentifier("layer_blend")
            }
            if lockClip {
                HStack(spacing: 8) {
                    Toggle(L("v56_layer_alpha_lock"), isOn: Binding(get: { layer.alphaLock }, set: { _ in vm.layers.toggleAlphaLock(layer.id) }))
                        .toggleStyle(.button).font(.caption).accessibilityIdentifier("layer_alpha_lock")
                    if index > 0 {
                        Toggle(L("v56_layer_clip"), isOn: Binding(get: { layer.clipToBelow }, set: { _ in vm.layers.toggleClip(layer.id) }))
                            .toggleStyle(.button).font(.caption).accessibilityIdentifier("layer_clip")
                    }
                }
                if layer.clipToBelow && index > 0 {
                    Text(L("v56_layer_clip_hint")).font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
        .onChangeCompat(of: layer.opacity, initial: true) { _, new in opacity = new }
        .accessibilityIdentifier("layer_look")
    }
}

// MARK: - Özellikler

/// Özellikler: seçili eleman(lar)ın özellikleri; seçim yoksa fırça/silgi/yazı aracının ayarları; yoksa boş not.
struct PropertiesView: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var brush: BrushController
    @ObservedObject var texts: TextController
    @Binding var brushPickerOpen: Bool

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) { content }.padding(14)
        }
    }

    @ViewBuilder private var content: some View {
        let kind = vm.selectionKind()
        if kind != .NONE {
            SelectionProperties(vm: vm, store: store, texts: texts, kind: kind)
        } else if vm.mode == .TEXT || texts.draft != nil {
            TextSettingsEditor(vm: vm, texts: texts, selected: nil)
        } else if vm.mode == .DRAW || vm.mode == .PEN {
            BrushSettingsEditor(vm: vm, brush: brush, brushPickerOpen: $brushPickerOpen)
        } else if vm.v56(.LAYER_HSL_BALANCE) {
            // v56 aşama 7 (2D-4): eleman seçili değilken etkin katmanın renkleri
            V56LayerColorSection(vm: vm, store: store)
        } else {
            EmptyPanelNote(icon: "slider.horizontal.3", text: L("v53_properties_empty"))
        }
    }
}

/// Bölüm başlığı ve kartı.
struct PropSection<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.uppercased(with: Locale.current)).font(.caption2.weight(.bold)).foregroundStyle(.secondary)
            VStack(alignment: .leading, spacing: 10) { content() }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color.primary.opacity(0.045)))
        }
    }
}

struct PropSlider: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    var format: (Double) -> String = { "\(Int($0))" }
    var id: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(label).font(.footnote)
                Spacer()
                Text(format(value)).font(.footnote.monospacedDigit()).foregroundStyle(.secondary)
            }
            Slider(value: $value, in: range).accessibilityIdentifier(id)
        }
    }
}

/// Fırça ayarları (fırçanın kendisi fırça düğmesine dokununca açılan pencerede seçilir).
struct BrushSettingsEditor: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var brush: BrushController
    @Binding var brushPickerOpen: Bool

    var body: some View {
        let eraser = brush.settings.brushType == .ERASER
        PropSection(title: L(eraser ? "brush_eraser" : "v53_brush_section")) {
            Button { brushPickerOpen = true } label: {
                HStack(spacing: 10) {
                    if !eraser {
                        Image(uiImage: BrushPreview.image(brush.settings.brushType, color: ArgbColor.hex(brush.settings.colorHex)))
                            .resizable().frame(width: 84, height: 26)
                    }
                    Text(L(brush.settings.brushType.titleKey)).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                    Spacer()
                    Text(L("v53_change")).font(.footnote).foregroundStyle(Color.accentColor)
                }
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("properties_brush_open")
            PropSlider(label: L("brush_size_label"), value: Binding(get: { Double(brush.settings.sizePx) }, set: { brush.updateSize(Float($0)) }),
                       range: 1...200, id: "brush_size")
            if !eraser {
                PropSlider(label: L("brush_flow_label"), value: Binding(get: { Double(brush.settings.flow) }, set: { brush.updateFlow(Float($0)) }),
                           range: 0.02...1, format: { "\(Int($0 * 100))%" }, id: "brush_flow")
                PropSlider(label: L("brush_opacity_label"), value: Binding(get: { Double(brush.settings.opacity) }, set: { brush.updateOpacity(Float($0)) }),
                           range: 0.05...1, format: { "\(Int($0 * 100))%" }, id: "brush_opacity")
            }
        }
        if !eraser {
            PropSection(title: L("brush_color_label")) {
                ColorRow(hex: brush.settings.colorHex, id: "properties_brush_color") { vm.updateBrushColor($0) }
            }
            if vm.store.canvas?.kind.simple != true {
                // v51 gelişmiş fırça ayarları
                List { BrushAdvancedSections(brush: brush) }
                    .listStyle(.insetGrouped)
                    .scrollDisabled(true)
                    .frame(minHeight: 420)
            }
            // v56 gren ve resimden fırça
            GrainSectionView(vm: vm, brush: brush)
        }
    }
}

/// Renk satırı: sistem renk seçici ve hızlı renkler.
struct ColorRow: View {
    let hex: String
    var id: String
    let onPick: (String) -> Void
    static let quick = ["#1E1E1E", "#FFFFFF", "#E53935", "#FB8C00", "#FDD835", "#43A047", "#1E88E5", "#8E24AA", "#6D4C41", "#9E9E9E"]

    var body: some View {
        HStack(spacing: 8) {
            ColorPicker("", selection: Binding(get: { Color(hex: hex) ?? .black }, set: { onPick($0.hexRGB) }), supportsOpacity: false)
                .labelsHidden().accessibilityIdentifier(id)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Self.quick, id: \.self) { c in
                        Button { onPick(c) } label: {
                            Circle().fill(Color(hex: c) ?? .black).frame(width: 26, height: 26)
                                .overlay(Circle().stroke(Color.primary.opacity(c.uppercased() == hex.uppercased() ? 0.9 : 0.15),
                                                         lineWidth: c.uppercased() == hex.uppercased() ? 2.5 : 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

/// Seçili elemanların özellikleri (türe göre).
struct SelectionProperties: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var texts: TextController
    let kind: SelectionKind

    private var first: Stroke? { store.strokes.first { store.selection.contains($0.id) } }

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: ElementsList.icon(first?.brushType ?? .INK_PEN)).frame(width: 32, height: 32)
                .background(RoundedRectangle(cornerRadius: 9).fill(Color.accentColor.opacity(0.14)))
            VStack(alignment: .leading, spacing: 1) {
                Text(L("v53_selected_props")).font(.subheadline.weight(.semibold))
                Text(L("v53_selected_count", store.selection.count)).font(.caption).foregroundStyle(.secondary)
            }
        }
        switch kind {
        case .SHAPE, .DIAGRAM:
            if let style = first?.shape?.style ?? first?.diagram?.style { shapeEditor(style) }
        case .INK:
            PropSection(title: L("brush_color_label")) {
                ColorRow(hex: vm.selectedInkColor(), id: "prop_ink_color") { vm.recolorSelectedInk($0) }
            }
            PropSection(title: L("v53_line_width")) {
                HStack {
                    Button { vm.scaleSelectedInkWidth(0.8) } label: { Label(L("v53_thinner"), systemImage: "minus") }
                    Spacer()
                    Button { vm.scaleSelectedInkWidth(1.25) } label: { Label(L("v53_thicker"), systemImage: "plus") }
                }
                .buttonStyle(.bordered)
                Button { vm.smoothSelectedInk() } label: { Label(L("v53_smooth"), systemImage: "scribble") }.buttonStyle(.bordered)
            }
        case .TEXT:
            if let t = first, t.text != nil { TextSettingsEditor(vm: vm, texts: texts, selected: t) }
        case .IMAGE:
            PropSection(title: L("v53_image_section")) {
                Button { vm.openPhotoEditor() } label: { Label(L("v52_photo_open"), systemImage: "camera.filters") }.buttonStyle(.borderedProminent)
                Button { vm.startAction(.CROP, target: store.selection) } label: { Label(L("image_crop"), systemImage: "crop") }.buttonStyle(.bordered)
            }
        case .FORMULA, .MIXED, .NONE:
            PropSection(title: L("brush_color_label")) {
                ColorRow(hex: first?.colorHex ?? "#1E1E1E", id: "prop_color") { vm.style.recolorSelected($0) }
            }
        }
        // v56 aşama 8A gelişmiş görünüm (yalnız Resim)
        if vm.v56(.ADV_APPEARANCE) { V56AppearanceView(vm: vm, store: store, shape: kind == .SHAPE) }
        if vm.canEditSelection() {
            Button { _ = vm.editSelection() } label: {
                Label(L("v53_edit_selected"), systemImage: "slider.horizontal.3").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("prop_edit")
        }
    }

    @ViewBuilder private func shapeEditor(_ style: ShapeStyle) -> some View {
        PropSection(title: L("v53_line_section")) {
            ColorRow(hex: style.strokeColorHex, id: "prop_stroke_color") { h in var s = style; s.strokeColorHex = h; vm.style.applyStyleToSelection(s) }
            PropSlider(label: L("v53_line_width"), value: Binding(get: { Double(style.strokeWidth) }, set: { v in
                var s = style; s.strokeWidth = Float(v); vm.style.applyStyleToSelection(s)
            }), range: 0...40, id: "prop_stroke_width")
        }
        PropSection(title: L("v53_fill_section")) {
            Toggle(L("v53_fill_on"), isOn: Binding(get: { style.fillColorHex != nil || style.fillGradient != nil }, set: { on in
                var s = style; s.fillColorHex = on ? (style.fillColorHex ?? "#90CAF9") : nil; if !on { s.fillGradient = nil }
                vm.style.applyStyleToSelection(s)
            }))
            .accessibilityIdentifier("prop_fill_toggle")
            if let fill = style.fillColorHex {
                ColorRow(hex: fill, id: "prop_fill_color") { h in var s = style; s.fillColorHex = h; vm.style.applyStyleToSelection(s) }
                PropSlider(label: L("brush_opacity_label"), value: Binding(get: { Double(style.fillOpacity) }, set: { v in
                    var s = style; s.fillOpacity = Float(v); vm.style.applyStyleToSelection(s)
                }), range: 0...1, format: { "\(Int($0 * 100))%" }, id: "prop_fill_opacity")
            }
            PropSlider(label: L("v53_corner"), value: Binding(get: { Double(style.cornerRadius) }, set: { v in
                var s = style; s.cornerRadius = Float(v); vm.style.applyStyleToSelection(s)
            }), range: 0...80, id: "prop_corner")
        }
    }
}

/// Yazı ayarları: yazı tipi, boyut, kalın/italik/altı çizili, hizalama ve renk. [selected] varsa o yazıya uygulanır.
struct TextSettingsEditor: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var texts: TextController
    let selected: Stroke?

    private var current: TextSettings {
        if let s = selected, let spec = s.text { return TextSettings(spec: spec, sizePx: s.sizePx) }
        return texts.settings
    }

    private func apply(_ edit: (inout TextSettings) -> Void) {
        let before = current
        var next = before
        edit(&next)
        texts.updateSettings(next, before: before)
    }

    var body: some View {
        let s = current
        PropSection(title: L("v53_text_section")) {
            Menu {
                ForEach(FontGroup.allCases, id: \.self) { g in
                    Section(L("v53_font_group_" + g.rawValue.lowercased())) {
                        ForEach(TextFont.allCases.filter { $0.group == g }, id: \.self) { f in
                            Button { apply { $0.spec.font = f } } label: {
                                if f == s.spec.font { Label(f.title, systemImage: "checkmark") } else { Text(f.title) }
                            }
                        }
                    }
                }
            } label: {
                HStack {
                    Text(L("v53_font")).foregroundStyle(.primary)
                    Spacer()
                    Text(s.spec.font.title).foregroundStyle(Color.accentColor)
                    Image(systemName: "chevron.up.chevron.down").font(.caption).foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("prop_font")
            PropSlider(label: L("v53_text_size"), value: Binding(get: { Double(s.sizePx) }, set: { v in apply { $0.sizePx = Float(v) } }),
                       range: Double(TextController.MIN_TEXT_SIZE)...Double(min(TextController.MAX_TEXT_SIZE, 300)), id: "prop_text_size")
            HStack(spacing: 6) {
                toggle("bold", s.spec.bold) { apply { $0.spec.bold.toggle() } }
                toggle("italic", s.spec.italic) { apply { $0.spec.italic.toggle() } }
                toggle("underline", s.spec.underline) { apply { $0.spec.underline.toggle() } }
                Spacer()
                ForEach([(TextAlignKind.LEFT, "text.alignleft"), (.CENTER, "text.aligncenter"), (.RIGHT, "text.alignright")], id: \.1) { a in
                    toggle(a.1, s.spec.align == a.0) { apply { $0.spec.align = a.0 } }
                }
            }
        }
        PropSection(title: L("brush_color_label")) {
            ColorRow(hex: selected?.colorHex ?? vm.brush.settings.colorHex, id: "prop_text_color") { vm.setTextColor($0) }
        }
    }

    private func toggle(_ icon: String, _ on: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).frame(width: 32, height: 32)
                .foregroundStyle(on ? Color.accentColor : .primary)
                .background(RoundedRectangle(cornerRadius: 8).fill(on ? Color.accentColor.opacity(0.16) : Color.primary.opacity(0.06)))
        }
        .buttonStyle(.plain)
    }
}
