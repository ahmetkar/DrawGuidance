import SwiftUI

/// v56 hızlı fırça seçici (planın 2. bölümü ve 2D-5): görünür tuval alanının köşesinde 44 pt'lik yuvarlak düğme (etkin
/// fırça simgesi, kenarında seçili rengin halkası, altında boyut noktası). Dokununca yanından yatay şerit açılır:
/// favoriler ve son kullanılanlar (uzun basınca sabitle / kaldır), renk düğmesi, silgi, boyut ve opaklık, "Tüm fırçalar".
/// Çizerken şerit kapanır ve düğme %40 saydamlaşır; kalem yaklaşınca düğme kenar boyunca kayar. Tablette düğme
/// sürüklenip dört köşeden birine bırakılır. Yalnız Resim ve Basit resimde, Çiz modunda.
struct QuickBrushView: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var brush: BrushController
    @ObservedObject var quick: QuickBrushStore
    @Binding var brushPickerOpen: Bool
    @Environment(\.layoutDirection) private var layoutDirection
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var colorOpen = false
    @State private var drag: CGSize = .zero
    @State private var favFull = false

    private var rtl: Bool { layoutDirection == .rightToLeft }
    private var shown: Bool { vm.v56(.QUICK_BRUSH) && vm.mode == .DRAW && vm.texts.draft == nil }

    var body: some View {
        GeometryReader { geo in
            if shown { content(geo.size) }
        }
        .sheet(isPresented: $colorOpen) { QuickColorSheet(vm: vm, brush: brush, open: $colorOpen) }
        .onChangeCompat(of: vm.reopenColorSheet) { _, _ in reopenIfNeeded() }
        .onChangeCompat(of: vm.canvasPick) { _, _ in reopenIfNeeded() }
        .alert(L("v56_quick_fav_full"), isPresented: $favFull) { Button(L("ok_button")) {} }
    }

    private func reopenIfNeeded() {
        if vm.reopenColorSheet && vm.canvasPick != .EYEDROPPER {
            vm.reopenColorSheet = false
            colorOpen = true
        }
    }

    @ViewBuilder private func content(_ size: CGSize) -> some View {
        let phys = QuickBrushLayout.physical(quick.corner, rtl: rtl)
        let half = CGFloat(QuickBrushLayout.BUTTON) / 2
        let bx = phys.right ? size.width - 8 - half : 8 + half
        let by = phys.bottom ? size.height - 8 - half : 8 + half
        let pen = vm.penPoint.map { vm.viewport.toScreen($0) }
        let dodge = pen.map { CGFloat(QuickBrushLayout.dodge($0.x, $0.y, Float(bx), Float(by), Float(half))) } ?? 0
        // Fiziksel köşe: düzen yönünden bağımsız (RTL'de mantıksal başlangıç sağdadır)
        let alignment: Alignment = phys.bottom ? (phys.right ? .bottomTrailing : .bottomLeading) : (phys.right ? .topTrailing : .topLeading)
        HStack(alignment: .top, spacing: 8) {
            // Şerit düğmenin iç tarafına açılır
            if phys.right { strip(width: size.width - 72) }
            button.gesture(DragGesture(minimumDistance: 12)
                .onChanged { drag = $0.translation }
                .onEnded { v in
                    quick.setCorner(QuickBrushLayout.nearest(Float(bx + v.translation.width), Float(by + v.translation.height),
                                                            Float(size.width), Float(size.height), rtl: rtl))
                    drag = .zero
                }, including: sizeClass == .regular ? .all : .subviews)
            if !phys.right { strip(width: size.width - 72) }
        }
        .environment(\.layoutDirection, .leftToRight)
        .padding(8)
        .offset(x: drag.width, y: drag.height + (phys.bottom ? -dodge : dodge))
        .animation(.easeOut(duration: 0.15), value: dodge)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment)
    }

    private var button: some View {
        let type = brush.settings.brushType
        let ring = type == .ERASER ? Color.secondary : (Color(hex: brush.settings.colorHex) ?? .black)
        let dot = 3 + CGFloat(min(max(brush.settings.sizePx / BrushTuning.MAX_SIZE, 0), 1)) * 9
        return VStack(spacing: 3) {
            Button { quick.toggle() } label: {
                Image(systemName: BrushSymbols.of(type))
                    .font(.system(size: 18, weight: .semibold))
                    .frame(width: CGFloat(QuickBrushLayout.BUTTON), height: CGFloat(QuickBrushLayout.BUTTON))
                    .background(Circle().fill(Color(uiColor: .secondarySystemGroupedBackground)))
                    .overlay(Circle().stroke(ring, lineWidth: 3))
                    .shadow(color: .black.opacity(0.15), radius: 3, y: 1)
            }
            .buttonStyle(.plain)
            .simultaneousGesture(LongPressGesture().onEnded { _ in if vm.v56(.QUICK_BRUSH_COLOR) { colorOpen = true } })
            .accessibilityLabel(L("v56_quick_brush_a11y", L(type.titleKey)))
            .accessibilityIdentifier("quick_brush_button")
            Circle().fill(ring).frame(width: dot, height: dot)
        }
        .opacity(vm.drawingNow ? 0.4 : 1)
    }

    @ViewBuilder private func strip(width: CGFloat) -> some View {
        if quick.open && !vm.drawingNow {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    if store.area != nil {
                        Text(L("v56_quick_inside_selection")).font(.caption2.weight(.semibold)).foregroundStyle(.white)
                            .padding(.horizontal, 8).padding(.vertical, 3).background(Capsule().fill(Color.accentColor))
                            .accessibilityIdentifier("quick_brush_inside")
                    }
                    ForEach(quick.lists.strip(), id: \.self) { type in item(type) }
                    if vm.v56(.QUICK_BRUSH_COLOR) {
                        Button { colorOpen = true } label: {
                            Circle().fill(Color(hex: brush.settings.colorHex) ?? .black).frame(width: 24, height: 24)
                                .overlay(Circle().stroke(Color.black.opacity(0.25), lineWidth: 1))
                                .frame(width: 38, height: 38)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(L("v56_quick_color"))
                        .accessibilityIdentifier("quick_brush_color")
                    }
                    Button { brush.toggleEraser() } label: {
                        Image(systemName: "eraser").frame(width: 38, height: 38)
                            .background(Circle().fill(brush.settings.brushType == .ERASER ? Color.accentColor.opacity(0.2) : Color.clear))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L("brush_eraser"))
                    .accessibilityIdentifier("quick_brush_eraser")
                    mini(L("v56_quick_size"), Double(brush.settings.sizePx), Double(BrushTuning.MIN_SIZE)...Double(BrushTuning.MAX_SIZE), "quick_brush_size") {
                        brush.updateSize(Float($0))
                    }
                    mini(L("v56_quick_opacity"), Double(brush.settings.opacity), 0.05...1, "quick_brush_opacity") { brush.updateOpacity(Float($0)) }
                    Button {
                        quick.open = false
                        brushPickerOpen = true
                    } label: {
                        Label(L("v56_quick_all_brushes"), systemImage: "square.grid.3x3").font(.caption.weight(.medium))
                            .padding(.horizontal, 10).padding(.vertical, 9)
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("quick_brush_all")
                }
                .padding(.horizontal, 8).padding(.vertical, 6)
            }
            .frame(maxWidth: max(width, 120))
            .fixedSize(horizontal: false, vertical: true)
            .background(RoundedRectangle(cornerRadius: 22).fill(Color(uiColor: .secondarySystemGroupedBackground)).shadow(color: .black.opacity(0.15), radius: 4, y: 2))
            .accessibilityIdentifier("quick_brush_strip")
        }
    }

    private func item(_ type: BrushType) -> some View {
        let selected = type == brush.settings.brushType
        let fav = quick.lists.favorites.contains(type)
        return Button { vm.selectBrush(type) } label: {
            Image(systemName: BrushSymbols.of(type))
                .foregroundStyle(selected ? Color.accentColor : Color.primary)
                .frame(width: 38, height: 38)
                .background(Circle().fill(selected ? Color.accentColor.opacity(0.18) : Color.clear))
                .overlay(alignment: .topTrailing) {
                    if fav { Image(systemName: "pin.fill").font(.system(size: 9)).foregroundStyle(Color.accentColor) }
                }
        }
        .buttonStyle(.plain)
        .simultaneousGesture(LongPressGesture().onEnded { _ in if !quick.toggleFavorite(type) { favFull = true } })
        .accessibilityLabel(L(type.titleKey))
        .accessibilityHint(L(fav ? "v56_quick_unpin" : "v56_quick_pin"))
        .accessibilityIdentifier("quick_brush_item_\(type.rawValue)")
    }

    private func mini(_ label: String, _ value: Double, _ range: ClosedRange<Double>, _ id: String, _ set: @escaping (Double) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(label).font(.caption2).foregroundStyle(.secondary)
            Slider(value: Binding(get: { value }, set: set), in: range)
        }
        .frame(width: 96)
        .accessibilityIdentifier(id)
    }
}

/// v56 (2D-5) renk penceresi: sistem renk seçicisi ve üst satırda damlalık (pencere kapanır, tuvalden renk alınınca açılır).
struct QuickColorSheet: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var brush: BrushController
    @Binding var open: Bool

    var body: some View {
        NavigationStack {
            Form {
                ColorPicker(L("brush_color_label"), selection: Binding(
                    get: { Color(hex: brush.settings.colorHex) ?? .black },
                    set: { vm.updateBrushColor($0.hexRGB) }), supportsOpacity: false)
                    .accessibilityIdentifier("color_pick_sheet")
            }
            .navigationTitle(L("pick_color_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        open = false
                        vm.eyedropFromColorSheet()
                    } label: { Image(systemName: "eyedropper") }
                    .accessibilityLabel(L("v43_eyedropper"))
                    .accessibilityIdentifier("color_pick_eyedropper")
                }
                ToolbarItem(placement: .confirmationAction) { Button(L("close")) { open = false } }
            }
        }
        .presentationDetents([.medium])
    }
}
