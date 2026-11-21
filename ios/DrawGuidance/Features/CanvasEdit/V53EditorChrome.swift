import PhotosUI
import SwiftUI

// v53 iPhone düzenleyicisinin çerçevesi (Android `NeuTopBar` + `EditorToolRow` + `ModeSelector` + `ToolInfoChip`):
//  - Başlıkta yalnız geri, tuval adı, Eğitim, Kaydet ve sağ köşede Diğer (⋯) menüsü.
//  - Gelişmiş modlarda üstte seçili modun araç satırı, altında Seç / Çiz / Ekle / Düzenle ve Elemanlar / Katmanlar /
//    Özellikler; seçili aracın ayarları menülerin hemen altındaki şeritte.
//  - Elemanlar, Katmanlar ve Özellikler sağdan açılan panelde (bkz. V53SidePanel.swift).

/// Android `NeuIconButton` görünümünde araç düğmesi: yumuşak zemin, seçiliyken vurgu rengi; ad isteğe bağlı simgenin
/// yanında ya da altında.
struct ChromeButton: View {
    enum LabelStyle { case none, inline, below }
    let icon: String
    let label: String
    var selected = false
    var enabled = true
    var danger = false
    var tint: Color? = nil
    var labelStyle: LabelStyle = .inline
    var id: String? = nil
    var anchor: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) { content }
            .buttonStyle(ChromePressStyle())
            .disabled(!enabled)
            .opacity(enabled ? 1 : 0.38)
            .accessibilityLabel(label)
            .accessibilityAddTraits(selected ? .isSelected : [])
            .accessibilityIdentifier(id ?? label)
            .tutorialAnchor(anchor)
    }

    private var fg: Color {
        if danger && enabled { return .red }
        if selected { return .accentColor }
        return tint ?? .primary.opacity(0.82)
    }

    @ViewBuilder private var content: some View {
        switch labelStyle {
        case .none:
            Image(systemName: icon).font(.system(size: 17, weight: .medium)).foregroundStyle(fg)
                .frame(width: 40, height: 40)
                .background(ChromeChip(selected: selected))
        case .inline:
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 15, weight: .medium))
                Text(label).font(.footnote.weight(.semibold)).lineLimit(1).fixedSize()
            }
            .foregroundStyle(fg)
            .padding(.horizontal, 11).frame(height: 38)
            .background(ChromeChip(selected: selected))
        case .below:
            VStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 17, weight: .medium))
                Text(label).font(.system(size: 10, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.6)
            }
            .foregroundStyle(fg)
            .frame(minWidth: 0, maxWidth: 70, minHeight: 50)
            .padding(.horizontal, 1)
            .background(ChromeChip(selected: selected, radius: 14))
        }
    }
}

/// Düğmenin zemini: seçiliyken vurgu renginde dolu, değilken saydam (satırın kendi zemini var).
struct ChromeChip: View {
    var selected: Bool
    var radius: CGFloat = 12
    var body: some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(selected ? Color.accentColor.opacity(0.16) : Color.clear)
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous)
                .stroke(selected ? Color.accentColor.opacity(0.35) : .clear, lineWidth: 1))
    }
}

struct ChromePressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}

/// Menülerin ortak kabı (Android `NeuSurface`, hafif kabarık yüzey).
struct ChromeBar<Content: View>: View {
    var scroll = true
    var tag: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        Group {
            if scroll {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 4) { content() }.padding(.horizontal, 6)
                }
            } else {
                HStack(spacing: 2) { content() }.padding(.horizontal, 4)
            }
        }
        .padding(.vertical, 4)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(uiColor: .secondarySystemGroupedBackground))
                .shadow(color: .black.opacity(0.07), radius: 6, y: 2)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(tag)
    }
}

// MARK: - Başlık

/// v53 başlık: geri, tuval adı, Eğitim, Kaydet ve Diğer (⋯). Öteki düğmeler alttaki menülerde.
struct EditorHeaderBar: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @Binding var sheet: EditorSheet?
    @Environment(\.dismiss) private var dismiss

    private var kind: CanvasKind { store.canvas?.kind ?? .ART }

    var body: some View {
        HStack(spacing: 8) {
            Button { if vm.tutorial.active { vm.tutorial.cancel() }; vm.close(); dismiss() } label: {
                Image(systemName: "chevron.backward").font(.system(size: 17, weight: .semibold))
                    .frame(width: 38, height: 38)
                    .background(Circle().fill(Color(uiColor: .secondarySystemGroupedBackground)))
            }
            .buttonStyle(ChromePressStyle())
            .accessibilityLabel(L("back"))
            .accessibilityIdentifier("editor_back")
            VStack(alignment: .leading, spacing: 1) {
                Text(store.canvas?.name ?? "").font(.headline).lineLimit(1).truncationMode(.tail)
                    .accessibilityIdentifier("editor_title")
                if let c = store.canvas {
                    Text("\(c.width) × \(c.height)").font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1)
            if vm.isTempImageEdit {
                // v51 geçici resim tuvali: Kaydet resmi nota aktarır ve tuvali siler
                Button { vm.commitTempImage() } label: {
                    Label(L(vm.isTempShapeEdit ? "v52_temp_shape_save" : "v51_temp_image_save"), systemImage: "checkmark.circle.fill")
                        .font(.subheadline.weight(.semibold)).lineLimit(1)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("temp_image_save")
            } else {
                if !vm.tutorial.groups(kind).isEmpty {
                    headerIcon("graduationcap", L("tut_menu"), id: "tutorial_menu") { vm.tutorial.openOffer() }
                        .tutorialAnchor(TutorialAnchor.MORE)
                }
                headerIcon("square.and.arrow.down", L("v53_save"), id: "save_button") { sheet = .save }
                    .tutorialAnchor(TutorialAnchor.SAVE)
                moreMenu
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 6)
        .padding(.bottom, 4)
    }

    private func headerIcon(_ icon: String, _ label: String, id: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 17, weight: .medium))
                .frame(width: 38, height: 38)
                .background(Circle().fill(Color(uiColor: .secondarySystemGroupedBackground)))
        }
        .buttonStyle(ChromePressStyle())
        .accessibilityLabel(label)
        .accessibilityIdentifier(id)
    }

    /// Diğer menüsü (Android `EditorMoreMenu`): paylaşım, canlı yayın, kılavuz ve yardımcılar.
    private var moreMenu: some View {
        Menu {
            if kind == .ART || kind == .SIMPLE_ART {
                Button { sheet = .helpers } label: { Label(L("v52_helpers_title"), systemImage: "squareshape.split.3x3") }
                    .accessibilityIdentifier("helpers_menu_item")
            }
            if !kind.simple {
                Button { sheet = .guide } label: { Label(L("add_guide"), systemImage: "grid") }
                    .accessibilityIdentifier("guide_menu_item")
            }
            Button { sheet = .background } label: { Label(L("add_background"), systemImage: "photo.on.rectangle") }
                .accessibilityIdentifier("background_menu_item")
            Divider()
            Button { sheet = .share } label: { Label(L("v52_share_send_title"), systemImage: "square.and.arrow.up") }
                .accessibilityIdentifier("share_menu_item")
            if ShareManager.liveAllowed(kind) {
                Button { sheet = .live } label: { Label(L("v52_share_live_title"), systemImage: "dot.radiowaves.left.and.right") }
                    .accessibilityIdentifier("live_menu_item")
            }
        } label: {
            Image(systemName: "ellipsis").font(.system(size: 17, weight: .bold))
                .frame(width: 38, height: 38)
                .background(Circle().fill(Color(uiColor: .secondarySystemGroupedBackground)))
        }
        .accessibilityLabel(L("more_options"))
        .accessibilityIdentifier("editor_more")
    }
}

// MARK: - Gelişmiş modların menüleri

/// v53 gelişmiş modlar (resim, not alma, çizgi roman): araç satırı, mod menüsü ve ayar şeridi.
struct AdvancedMenus: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var brush: BrushController
    @ObservedObject var ruler: RulerController
    @ObservedObject var shapes: ShapeController
    @Binding var sheet: EditorSheet?
    @Binding var brushPickerOpen: Bool
    @ObservedObject var addUi: AddWindows
    /// v56 2C-3 uygulama içi pano (Yapıştır düğmesi).
    @ObservedObject var clipboard = ElementClipboard.shared

    private var kind: CanvasKind { store.canvas?.kind ?? .ART }
    private var notes: Bool { kind.isNotes }
    private var comic: Bool { kind == .COMIC }
    private var hasSelection: Bool { !store.selectedIds.isEmpty }
    private var erasing: Bool { brush.settings.brushType == .ERASER }

    var body: some View {
        VStack(spacing: 6) {
            ChromeBar(tag: "tool_row_context") { toolRow }
                .tutorialAnchor(TutorialAnchor.MODE_BAR)
            modeSelector
            ToolOptionsStrip(vm: vm, store: store, brush: brush, ruler: ruler, shapes: shapes)
        }
        .padding(.horizontal, 10)
        .padding(.bottom, 6)
    }

    // Seç / Çiz / Ekle / Düzenle ve panel düğmeleri
    private var modeSelector: some View {
        ChromeBar(scroll: false, tag: "mode_selector") {
            ForEach(EditMode.toolbar, id: \.self) { m in
                ChromeButton(icon: Self.modeIcon(m), label: L(Self.modeLabel(m)), selected: vm.mode.menuFamily == m,
                             labelStyle: .below, id: "mode_\(m)", anchor: TutorialAnchor.mode(m)) { vm.chooseMode(m) }
                    .frame(maxWidth: .infinity)
            }
            Rectangle().fill(Color.primary.opacity(0.12)).frame(width: 1, height: 34)
            ForEach(Self.panelTabs, id: \.0) { tab in
                ChromeButton(icon: tab.1, label: L(tab.2), selected: vm.panel == tab.0, labelStyle: .below,
                             id: "panel_btn_\(tab.0)", anchor: tab.3) { withAnimation(.easeOut(duration: 0.25)) { vm.togglePanel(tab.0) } }
                    .frame(maxWidth: .infinity)
            }
        }
    }

    static let panelTabs: [(PanelTab, String, String, String)] = [
        (.ELEMENTS, "square.grid.2x2", "tab_elements", TutorialAnchor.ELEMENTS),
        (.LAYERS, "square.3.layers.3d", "tab_layers", TutorialAnchor.LAYERS),
        (.BRUSH, "slider.horizontal.3", "tab_brush", TutorialAnchor.BRUSH_SETTINGS),
    ]

    static func modeIcon(_ m: EditMode) -> String {
        switch m {
        case .SELECT: return "hand.tap"
        case .DRAW: return "pencil.tip"
        case .ADD: return "plus.square"
        default: return "slider.horizontal.below.square.filled.and.square"
        }
    }

    static func modeLabel(_ m: EditMode) -> String {
        switch m {
        case .SELECT: return "mode_select"
        case .DRAW: return "mode_draw"
        case .ADD: return "mode_add"
        default: return "mode_edit_pick"
        }
    }

    private func undoRedo() -> some View {
        Group {
            ChromeButton(icon: "arrow.uturn.backward", label: L("undo"), enabled: store.canUndo, labelStyle: .none, id: "undo",
                         anchor: TutorialAnchor.UNDO) { vm.undo() }
            ChromeButton(icon: "arrow.uturn.forward", label: L("redo"), enabled: store.canRedo, labelStyle: .none, id: "redo",
                         anchor: TutorialAnchor.REDO) { vm.redo() }
            Rectangle().fill(Color.primary.opacity(0.12)).frame(width: 1, height: 26).padding(.horizontal, 2)
        }
    }

    @ViewBuilder private var toolRow: some View {
        undoRedo()
        switch vm.mode.rowKind {
        case .DRAW: drawRow
        case .SELECT: selectRow
        case .ADD: AddRowItems(vm: vm, store: store, addUi: addUi, sheet: $sheet, inline: true)
        case .EDIT, .MOVE, .FRAME: editRow
        }
    }

    // Çiz: fırça, silgi, renk, boya, damlalık, kalem aracı, cetvel, kalıp, alıştırma, 3B, arka plan
    @ViewBuilder private var drawRow: some View {
        let drawing = vm.mode == .DRAW
        ChromeButton(icon: BrushSymbols.of(brush.settings.brushType == .ERASER ? vm.lastDrawingBrush : brush.settings.brushType),
                     label: L("brush_select"), selected: drawing && !erasing,
                     tint: Color(hex: brush.settings.colorHex), id: "brush_button", anchor: TutorialAnchor.BRUSH) {
            // Çizerken fırçaya dokununca fırça penceresi açılır; değilse fırçaya dönülür
            if drawing && !erasing { brushPickerOpen = true } else { vm.chooseMode(.DRAW); if erasing { brush.toggleEraser() } }
        }
        ChromeButton(icon: "eraser", label: L("brush_eraser"), selected: drawing && erasing, id: "eraser_button", anchor: TutorialAnchor.ERASER) {
            if !drawing { vm.chooseMode(.DRAW) }
            brush.toggleEraser()
        }
        ColorPicker(L("brush_color_label"), selection: Binding(
            get: { Color(hex: brush.settings.colorHex) ?? .black },
            set: { vm.updateBrushColor($0.hexRGB) }), supportsOpacity: false)
            .labelsHidden()
            .frame(width: 40, height: 38)
            .accessibilityIdentifier("color_button")
        ChromeButton(icon: "drop.fill", label: L("fill_color"), selected: vm.mode == .PAINT, id: "paint_button", anchor: TutorialAnchor.FILL) {
            vm.chooseMode(vm.mode == .PAINT ? .DRAW : .PAINT)
        }
        if !notes {
            ChromeButton(icon: "eyedropper", label: L("v43_eyedropper"), selected: vm.canvasPick == .EYEDROPPER, id: "eyedropper",
                         anchor: TutorialAnchor.simple("eyedropper")) { vm.toggleCanvasPick(.EYEDROPPER) }
            // v51 not almada kalem aracı (yol) yok
            ChromeButton(icon: "scribble.variable", label: L("add_pen"), selected: vm.mode == .PEN, id: "pen_tool", anchor: TutorialAnchor.PEN) {
                vm.chooseMode(vm.mode == .PEN ? .DRAW : .PEN)
            }
        }
        ChromeButton(icon: "ruler", label: L("v40_ruler"), selected: ruler.session == .RULER, id: "ruler_button", anchor: TutorialAnchor.RULER) {
            if ruler.session == .RULER { ruler.finish() } else { RulerOpen.ruler(vm, ruler) }
        }
        ChromeButton(icon: "star.square.on.square", label: L("v40_template"), selected: ruler.session == .TEMPLATE || ruler.session == .TEMPLATE_PICK,
                     id: "template_button", anchor: TutorialAnchor.TEMPLATE) {
            RulerOpen.template(vm, ruler)
        }
        if kind == .ART {
            ChromeButton(icon: "figure.strengthtraining.traditional", label: L("v50_practice"), selected: vm.practice.active, id: "practice_button") {
                vm.practice.listOpen = true
            }
        }
        if kind == .ART || kind == .COMIC {
            // v53 canlı, nesne ve eşya yapı kılavuzları (ayrı düğme ve pencere)
            ChromeButton(icon: "figure.stand", label: L("v53_struct_short"), selected: vm.structure.active, id: "structure_button") {
                vm.structure.sheet = true
            }
        }
        if kind == .ART {
            ChromeButton(icon: "squareshape.split.3x3", label: L("v52_helpers_short"), selected: vm.helpers.active, id: "helpers_button") { sheet = .helpers }
        }
        ChromeButton(icon: "cube", label: L("v53_draw3d"), selected: store.canvas?.guide.draw3d == true, id: "draw3d") { vm.toggleDraw3d() }
        ChromeButton(icon: "photo.on.rectangle", label: L("add_background"), id: "background_button", anchor: TutorialAnchor.BACKGROUND) { sheet = .background }
    }

    // Seç: dikdörtgen ve serbest seçim, damlalık, kopyala, gizle, sil, grup ve sıra
    @ViewBuilder private var selectRow: some View {
        ChromeButton(icon: "rectangle.dashed", label: L("v43_select_rect"), selected: vm.canvasPick == .RECT_SELECT, id: "rect_select",
                     anchor: TutorialAnchor.simple("rect_select")) { vm.toggleCanvasPick(.RECT_SELECT) }
        ChromeButton(icon: "lasso", label: L("v43_select_lasso"), selected: vm.canvasPick == .LASSO_SELECT, id: "lasso_select") {
            vm.toggleCanvasPick(.LASSO_SELECT)
        }
        if !notes {
            ChromeButton(icon: "eyedropper", label: L("v43_eyedropper"), selected: vm.canvasPick == .EYEDROPPER, id: "select_eyedropper") {
                vm.toggleCanvasPick(.EYEDROPPER)
            }
        }
        // v56 sihirli değnek (2E, yalnız gelişmiş çizim) ve kement dolgu
        if vm.v56(.ADV_SELECTION) {
            ChromeButton(icon: "wand.and.stars", label: L("v56_magic_wand"), selected: vm.canvasPick == .MAGIC_WAND, id: "select_magic_wand") {
                vm.toggleCanvasPick(.MAGIC_WAND)
            }
        }
        if vm.v56(.LASSO_FILL) {
            ChromeButton(icon: "drop.fill", label: L("v56_element_lasso_fill"), selected: vm.canvasPick == .LASSO_FILL, id: "select_lasso_fill") {
                vm.toggleCanvasPick(.LASSO_FILL)
            }
        }
        ChromeButton(icon: "plus.square.on.square", label: L("duplicate_selection"), enabled: hasSelection, id: "duplicate") { vm.selection.duplicate() }
        // v56 aşama 6: Dönüştür paneli, yansıma, sıvılaştır
        if vm.v56(.TRANSFORM_PANEL) {
            ChromeButton(icon: "skew", label: L("v56_transform"), enabled: hasSelection, id: "v56_open_transform") { vm.openTransform() }
        }
        if vm.v56(.REFLECTION_LIQUIFY) {
            ChromeButton(icon: "arrow.up.and.down.righttriangle.up.righttriangle.down", label: L("v56_reflection"), enabled: hasSelection, id: "v56_reflection") { vm.reflectSelection() }
            ChromeButton(icon: "water.waves", label: L("v56_liquify"), id: "v56_liquify") { vm.startLiquify() }
        }
        // v56 aşama 7 seçime renk ayarları
        if vm.v56(.COLOR_ADJUST) {
            ChromeButton(icon: "paintpalette", label: L("v56_color_adjust"), enabled: hasSelection, id: "v56_color_adjust_open") { vm.colorAdjustOpen = true }
        }
        // v56 aşama 8 Desen yap
        if vm.v56(.SYMMETRY) {
            ChromeButton(icon: "square.grid.3x3.fill", label: L("v56_pattern"), enabled: hasSelection, id: "v56_pattern_open") { vm.patternOpen = true }
        }
        if vm.v56(.ADV_ALIGN) || vm.v56(.ADV_EFFECTS) {
            ChromeButton(icon: "square.on.circle", label: L("v56_object_menu"), enabled: hasSelection, id: "v56_object_menu_open") { vm.objectMenuOpen = true }
        }
        if vm.canUseClipboard() {
            // v56 2C-3 kes, kopyala, yapıştır (yapıştır pano doluyken her sayfada)
            ChromeButton(icon: "scissors", label: L("v56_clip_cut"), enabled: hasSelection, id: "clip_cut") { vm.clipboard.cut() }
            ChromeButton(icon: "doc.on.doc", label: L("v56_clip_copy"), enabled: hasSelection, id: "clip_copy") { vm.clipboard.copy() }
            ChromeButton(icon: "doc.on.clipboard", label: L("v56_clip_paste"), enabled: clipboard.content != nil, id: "clip_paste") { vm.paste() }
        }
        ChromeButton(icon: "eye.slash", label: L("hide_elements"), enabled: hasSelection, id: "hide") { vm.hideElements() }
        ChromeButton(icon: "trash", label: L("delete_selection"), enabled: hasSelection, danger: true, id: "delete") { vm.selection.delete() }
        ChromeButton(icon: "square.stack.3d.up", label: L("group_selection"), enabled: hasSelection, id: "group") { vm.selection.group() }
        ChromeButton(icon: "square.stack.3d.down.dottedline", label: L("v53_ungroup"), enabled: hasSelection, id: "ungroup") { vm.selection.ungroup() }
        ChromeButton(icon: "square.2.layers.3d.top.filled", label: L("order_forward"), enabled: hasSelection, id: "bring_front") { vm.selection.reorder(1) }
        ChromeButton(icon: "square.2.layers.3d.bottom.filled", label: L("order_backward"), enabled: hasSelection, id: "send_back") { vm.selection.reorder(-1) }
    }

    // Düzenle: seçili elemanın düzenleyicisine göre (şekil, resim) ya da genel araçlar
    @ViewBuilder private var editRow: some View {
        switch vm.mode {
        case .SHAPE_EDIT:
            ShapeEditToolRow(vm: vm, shapes: shapes)
        case .IMAGE_EDIT:
            ChromeButton(icon: "camera.filters", label: L("v52_photo_open"), id: "image_photo_edit") { vm.openPhotoEditor() }
            ChromeButton(icon: "crop", label: L("image_crop"), id: "image_crop") { vm.startAction(.CROP, target: store.selection) }
            ChromeButton(icon: "scribble.variable", label: L("vectorize_direct_auto"), selected: vm.imageEdit.tracing, id: "vectorize_auto", anchor: "vectorize_auto") {
                vm.imageEdit.vectorize(EdgeOptions(mode: .AUTO), lineWidth: 2.5, keepImage: true)
            }
            ChromeButton(icon: "viewfinder", label: L("vectorize_direct_edges"), id: "vectorize_edges", anchor: "vectorize_edges") {
                vm.imageEdit.vectorize(EdgeOptions(mode: .EDGES), lineWidth: 2.5, keepImage: true)
            }
            ChromeButton(icon: "point.topleft.down.to.point.bottomright.curvepath", label: L("vectorize_direct_lines"), id: "vectorize_centerline", anchor: "vectorize_centerline") {
                vm.imageEdit.vectorize(EdgeOptions(mode: .CENTERLINE), lineWidth: 2.5, keepImage: true)
            }
        default:
            ChromeButton(icon: "slider.horizontal.3", label: L("v53_edit_selected"), enabled: hasSelection, id: "edit_selection") { _ = vm.editSelection() }
            ForEach([ShapeTool.NODES, .LINES, .FILL], id: \.self) { t in
                ChromeButton(icon: ShapeEditToolRow.icon(t), label: ShapeEditToolRow.label(t), selected: vm.pendingShapeTool == t, id: "edit_tool_\(t)") {
                    vm.editShapeWith(t)
                }
            }
            ChromeButton(icon: "rotate.right", label: L("mode_rotate"), selected: vm.mode == .ROTATE, enabled: hasSelection, id: "mode_ROTATE") {
                vm.chooseMode(.ROTATE)
            }
            ChromeButton(icon: "arrow.up.left.and.arrow.down.right", label: L("mode_scale"), selected: vm.mode == .SCALE, enabled: hasSelection, id: "mode_SCALE") {
                vm.chooseMode(.SCALE)
            }
            ChromeButton(icon: "crop", label: L("mode_crop"), selected: vm.mode == .CROP, id: "mode_CROP") {
                vm.startAction(.CROP, target: store.selection)
            }
            ChromeButton(icon: "arrow.up.and.down.and.arrow.left.and.right", label: L("mode_move"), selected: vm.mode == .MOVE,
                         enabled: hasSelection || vm.mode == .MOVE, id: "mode_MOVE") { vm.chooseMode(.MOVE) }
            ChromeButton(icon: "aspectratio", label: L("mode_expand"), selected: vm.mode == .EXPAND,
                         enabled: !hasSelection || vm.mode == .EXPAND, id: "mode_EXPAND") { vm.chooseMode(.EXPAND) }
        }
    }
}

/// Cetvel açma (tuvalin görünen ortasında).
enum RulerOpen {
    /// v54 şekil kalıbı: seçili şekil ya da resim varsa kalıp doğrudan ondan; yoksa çizim modlarında ve çizgi romanda
    /// "Çizim yardımcıları" ya da "Şekiller"e yönlendiren uyarı, diğer modlarda şekil kütüphanesi açılır.
    @MainActor static func template(_ vm: EditorViewModel, _ ruler: RulerController) {
        if ruler.session == .TEMPLATE || ruler.session == .TEMPLATE_PICK { ruler.finish(); return }
        let selected = selectedStrokes(vm)
        if vm.mode != .DRAW { vm.chooseMode(.DRAW) }
        ruler.openTemplate()
        if !selected.isEmpty && ruler.templateFrom(selected) { return }
        // eğitim kalıbı kendisi seçer; uyarı açılmaz
        if vm.tutorial.active { return }
        if vm.store.canvas?.kind.base == .ART { ruler.chooserOpen = true } else { ruler.libraryOpen = true }
    }

    /// Seçili elemanların (grupları dahil) görünür çizimleri; silgiler alınmaz.
    @MainActor static func selectedStrokes(_ vm: EditorViewModel) -> [Stroke] {
        let ids = vm.store.selectedIds
        if ids.isEmpty { return [] }
        let index = vm.store.currentIndex()
        let all = index.expand(ids)
        return index.strokes.filter { all.contains($0.id) && !$0.hidden && $0.brushType != .ERASER }
    }

    /// v54 çizim yardımcısının çizgileri görünen alanın ortasında kalıp olur.
    @MainActor static func templateFromHelper(_ vm: EditorViewModel, _ ruler: RulerController, _ g: StructureGuide) {
        let v = vm.viewport
        let sc = max(v.scale, 0.01)
        let center = v.toCanvas(P(v.viewSize.w / 2, v.viewSize.h / 2))
        let h = v.viewSize.w > 0 ? min(v.viewSize.h / sc * 0.6, v.viewSize.w / sc * 0.7 / max(g.aspect, 0.1)) : 600
        _ = ruler.templateFromContours(StructureContours.of(g, center: center, height: h))
    }

    @MainActor static func ruler(_ vm: EditorViewModel, _ ruler: RulerController) {
        let v = vm.viewport
        let center = v.toCanvas(P(v.viewSize.w / 2, v.viewSize.h / 2))
        ruler.openRuler(center: center, visibleWidth: v.viewSize.w / max(v.scale, 0.01), thickness: GuideMetrics.THICKNESS / max(v.scale, 0.01))
    }
}

/// Fırça türlerinin simgeleri (Android `BrushIcons`).
enum BrushSymbols {
    static func of(_ t: BrushType) -> String {
        switch t {
        case .PENCIL: return "pencil"
        case .MARKER: return "highlighter"
        case .ERASER: return "eraser"
        default:
            let n = t.rawValue
            if n.contains("PEN") || n.contains("INK") { return "pencil.tip" }
            if n.contains("SPRAY") || n.contains("AIR") { return "aqi.medium" }
            if n.contains("WATER") || n.contains("BLUR") || n.contains("CLOUD") { return "drop" }
            if n.contains("CHALK") || n.contains("CRAYON") || n.contains("CHARCOAL") { return "scribble" }
            return "paintbrush.pointed"
        }
    }
}

// MARK: - Ekle menüsü

/// v53 Ekle menüsünün pencereleri: her iş kendi penceresinde (karma pencereler bölündü).
@MainActor
final class AddWindows: ObservableObject {
    enum Window: String, Identifiable {
        case balloon, templates, effects, sfx, lines, patterns, tones, characters, locations, objects, panels, model3d, imageSource
        var id: String { rawValue }
    }
    @Published var open: Window?
}

/// Ekle menüsünün düğmeleri (gelişmiş Ekle satırı).
struct AddRowItems: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var addUi: AddWindows
    @Binding var sheet: EditorSheet?
    var inline: Bool
    @State private var photo: PhotosPickerItem?

    private var kind: CanvasKind { store.canvas?.kind ?? .ART }

    var body: some View {
        let notes = kind.isNotes, comic = kind == .COMIC
        // v53 hızlı yazı kutusu; v54 not almada Ekle menüsünün en başında
        if notes { item("textbox", "character.textbox", L("v53_quick_text"), selected: vm.quickText.active) { vm.toggleQuickText() } }
        item("shape", "square.on.circle", L("add_shape")) { vm.shapes.openPicker() }
        if notes {
            item("formula", "function", L("add_formula")) { vm.formulas.openPicker() }
            item("graph", "chart.xyaxis.line", L("v52_add_graph")) { vm.shapes.openGraphPicker() }
            item("table", "tablecells", L("v52_add_table")) { vm.tables.openCreate() }
        }
        item("text", "textformat", L("add_text"), selected: vm.mode == .TEXT) { vm.startAction(.TEXT, target: []) }
        if notes {
            item("diagram", "point.3.connected.trianglepath.dotted", L("add_diagram")) { vm.diagrams.openPicker() }
            item("panels", "rectangle.split.2x2", L("v45_panels_add")) { addUi.open = .panels }
        }
        if comic {
            item("balloon", "bubble.left", L("add_balloon")) { addUi.open = .balloon }
            item("templates", "rectangle.3.group", L("add_templates")) { addUi.open = .templates }
            item("characters", "figure.wave", L("add_characters")) { addUi.open = .characters }
            item("locations", "mountain.2", L("v41_add_location")) { addUi.open = .locations }
            item("objects", "shippingbox", L("v42_add_objects")) { addUi.open = .objects }
            item("effects", "sparkles", L("add_effects")) { addUi.open = .effects }
            item("sfx", "textformat.size.larger", L("v53_add_sfx")) { addUi.open = .sfx }
            item("lines", "rays", L("effects_tab_lines")) { addUi.open = .lines }
            // v53 desenler ve ekran tonları yalnız çizgi romanda, ayrı düğme ve pencerelerde
            item("patterns", "circle.hexagongrid", L("v53_patterns")) { addUi.open = .patterns }
            item("tones", "circle.grid.3x3.fill", L("v53_tones")) { addUi.open = .tones }
        }
        if !kind.simple { item("guide", "grid", L("add_guide")) { sheet = .guide } }
        // v56 Ekle › Doku: etkin katmanın sayfası hazır dokuyla doldurulur
        if vm.v56(.GRAIN) { item("texture", "square.grid.3x3.topleft.filled", L("v56_add_texture")) { vm.textureFillTarget = "page" } }
        // v56 aşama 7 hazır ayarlar: ışık katmanı, gölge katmanı, parıltı fırçası
        if vm.v56(.PRESETS) {
            item("light_layer", "sun.max", L("v56_light_layer")) { vm.addPresetLayer(.LIGHT) }
            item("shadow_layer", "moon", L("v56_shadow_layer")) { vm.addPresetLayer(.SHADOW) }
            item("glitter_brush", "sparkle", L("v56_glitter_brush")) { vm.useGlitterBrush() }
        }
        item("model3d", "cube.transparent", L("add_model3d")) { addUi.open = .model3d }
        PhotosPicker(selection: $photo, matching: .images) {
            HStack(spacing: 6) {
                Image(systemName: "photo.badge.plus").font(.system(size: 15, weight: .medium))
                Text(L("add_image")).font(.footnote.weight(.semibold)).lineLimit(1).fixedSize()
            }
            .foregroundStyle(Color.primary.opacity(0.82))
            .padding(.horizontal, 11).frame(height: 38)
        }
        .accessibilityLabel(L("add_image"))
        .accessibilityIdentifier("add_image")
        .tutorialAnchor(TutorialAnchor.add("image"))
        .onChangeCompat(of: photo) { _, it in
            guard let it else { return }
            Task {
                if let data = try? await it.loadTransferable(type: Data.self) {
                    let c = vm.takeAddAnchor() ?? vm.viewport.toCanvas(P(vm.viewport.viewSize.w / 2, vm.viewport.viewSize.h / 2))
                    vm.images.importImage(data, center: c)
                }
                photo = nil
            }
        }
        item("background", "photo.on.rectangle", L("add_background")) { sheet = .background }
    }

    private func item(_ id: String, _ icon: String, _ label: String, selected: Bool = false, _ action: @escaping () -> Void) -> some View {
        ChromeButton(icon: icon, label: label, selected: selected, labelStyle: inline ? .inline : .below, id: "add_\(id)",
                     anchor: TutorialAnchor.add(id), action: action)
    }
}

// MARK: - Ayar şeridi (madde 7)

/// v53 seçili aracın ayarları menülerin hemen altında (Android `ToolInfoChip` + cetvel çubuğu): fırça boyu ve
/// örtücülüğü, silgi boyu, kalem aracı, cetvel/kalıp, kırpma, ekleme modlarında Uygula.
struct ToolOptionsStrip: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var brush: BrushController
    @ObservedObject var ruler: RulerController
    @ObservedObject var shapes: ShapeController

    var body: some View {
        if let content = content {
            content
                .padding(.horizontal, 12).padding(.vertical, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color.accentColor.opacity(0.07)))
                .transition(.opacity.combined(with: .move(edge: .top)))
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("tool_options")
        }
    }

    private var content: AnyView? {
        if ruler.session != .NONE {
            return AnyView(ScrollView(.horizontal, showsIndicators: false) { RulerBar(ruler: ruler) })
        }
        switch vm.mode {
        case .DRAW:
            if vm.canvasPick == .EYEDROPPER { return hint(L("v53_hint_eyedropper")) }
            let eraser = brush.settings.brushType == .ERASER
            return AnyView(HStack(spacing: 10) {
                Image(systemName: eraser ? "eraser" : BrushSymbols.of(brush.settings.brushType))
                    .foregroundStyle(eraser ? Color.secondary : (Color(hex: brush.settings.colorHex) ?? .primary))
                Text(L(brush.settings.brushType.titleKey)).font(.caption.weight(.semibold)).lineLimit(1).frame(maxWidth: 86, alignment: .leading)
                miniSlider("brush_size_quick", Binding(get: { Double(brush.settings.sizePx) }, set: { brush.updateSize(Float($0)) }), 1...200,
                           icon: "circle.fill", text: "\(Int(brush.settings.sizePx))")
                if !eraser {
                    miniSlider("brush_opacity_quick", Binding(get: { Double(brush.settings.opacity) }, set: { brush.updateOpacity(Float($0)) }), 0.05...1,
                               icon: "circle.lefthalf.filled", text: "\(Int(brush.settings.opacity * 100))%")
                }
            })
        case .PEN:
            return AnyView(HStack(spacing: 8) {
                Text(L("v53_hint_pen")).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                Spacer(minLength: 4)
                ChromeButton(icon: "arrow.uturn.backward.circle", label: L("undo"), labelStyle: .none, id: "pen_undo") { vm.pen.undoAnchor() }
                ChromeButton(icon: "checkmark.circle.fill", label: L("apply_button"), id: "pen_finish") { vm.pen.finish(closed: false) }
            })
        case .PAINT: return hint(L("v53_hint_paint"))
        case .SELECT:
            if vm.canvasPick == .RECT_SELECT || vm.canvasPick == .LASSO_SELECT || vm.canvasPick == .LASSO_FILL { return hint(L("v53_hint_area_select")) }
            if vm.canvasPick == .EYEDROPPER { return hint(L("v53_hint_eyedropper")) }
            return nil
        case .CROP:
            return AnyView(HStack(spacing: 8) {
                Text(L("v53_hint_crop")).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                Spacer(minLength: 4)
                ChromeButton(icon: "arrow.counterclockwise", label: L("v53_reset"), id: "crop_reset") { vm.frame.resetCrop() }
                ChromeButton(icon: "checkmark", label: L("apply_button"), selected: true, id: "crop_apply") { _ = vm.frame.applyCrop(); vm.finishAction() }
            })
        case .SHAPE, .DIAGRAM, .FORMULA, .GUIDE, .PERSPECTIVE, .EXPAND, .DIAGRAM_EDIT, .MOVE, .ROTATE, .SCALE, .IMAGE_EDIT:
            return AnyView(HStack(spacing: 8) {
                Text(L(Self.hintKey(vm.mode))).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                Spacer(minLength: 4)
                ChromeButton(icon: "checkmark", label: L("v53_done"), selected: true, id: "finish_action") { vm.finishAction() }
            })
        case .SHAPE_EDIT:
            return AnyView(HStack(spacing: 8) {
                Toggle(L("v52_vector_fine_nodes"), isOn: Binding(get: { shapes.fineNodes }, set: { shapes.setFineNodes($0) }))
                    .font(.caption.weight(.semibold)).fixedSize()
                Spacer(minLength: 4)
                ChromeButton(icon: "checkmark", label: L("v53_done"), selected: true, id: "finish_action") { vm.finishAction() }
            })
        case .TEXT:
            return hint(L("v53_hint_text"))
        case .EDIT_PICK:
            return hint(L(vm.pendingShapeTool != nil ? "v53_hint_edit_tool" : "v53_hint_edit_pick"))
        case .ADD:
            return hint(L("v53_hint_add"))
        }
    }

    static func hintKey(_ m: EditMode) -> String {
        switch m {
        case .SHAPE: return "v53_hint_shape"
        case .DIAGRAM: return "v53_hint_diagram"
        case .FORMULA: return "v53_hint_formula"
        case .GUIDE, .PERSPECTIVE: return "v53_hint_guide"
        case .EXPAND: return "v53_hint_expand"
        case .MOVE: return "v53_hint_move"
        case .ROTATE: return "v53_hint_rotate"
        case .SCALE: return "v53_hint_scale"
        case .IMAGE_EDIT: return "v53_hint_image_edit"
        default: return "v53_hint_diagram_edit"
        }
    }

    private func hint(_ text: String) -> AnyView {
        AnyView(Label(text, systemImage: "info.circle").font(.caption).foregroundStyle(.secondary).lineLimit(2))
    }

    private func miniSlider(_ id: String, _ value: Binding<Double>, _ range: ClosedRange<Double>, icon: String, text: String) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon).font(.system(size: 9)).foregroundStyle(.secondary)
            Slider(value: value, in: range).accessibilityIdentifier(id)
            Text(text).font(.caption2.monospacedDigit()).foregroundStyle(.secondary).frame(width: 34, alignment: .trailing)
        }
    }
}
