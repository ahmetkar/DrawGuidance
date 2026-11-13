import SwiftUI

/// v33 çocuklar için 12 canlı renk (Android `KidsColors`).
enum KidsColors {
    static let all: [(String, String)] = [
        ("#E53935", "Kırmızı"), ("#FB8C00", "Turuncu"), ("#FDD835", "Sarı"), ("#7CB342", "Açık yeşil"),
        ("#2E7D32", "Yeşil"), ("#00ACC1", "Turkuaz"), ("#1E88E5", "Mavi"), ("#5E35B1", "Mor"),
        ("#EC407A", "Pembe"), ("#8D6E63", "Kahverengi"), ("#212121", "Siyah"), ("#FFFFFF", "Beyaz"),
    ]
}

/// v45 fırça boyu seçenekleri (tuval birimi): İnce, Orta, Kalın.
enum KidsBrushSizes {
    static let sizes: [Float] = [10, 24, 48]
    static func nearest(_ s: Float) -> Float { sizes.min { abs($0 - s) < abs($1 - s) } ?? 24 }
}

/// Çocuk tuvalinin araç çubuğu (Android `KidsToolRow` + `KidsPaintBar`): büyük renkli karolar, palet ve fırça boyu.
struct KidsToolbar: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var brush: BrushController
    @ObservedObject var shapes: ShapeController
    var challengeUi: KidsChallengeUi? = nil

    private struct Tile: Identifiable {
        let id: String; let icon: String; let label: String; let color: UInt32; let selected: Bool; let enabled: Bool; let action: () -> Void
    }

    private var tiles: [Tile] {
        let painting = vm.mode == .PAINT
        return [
            Tile(id: "brush", icon: "pencil.tip", label: L("mode_draw"), color: 0xFF9A_D0FF,
                 selected: vm.mode == .DRAW && brush.settings.brushType != .ERASER, enabled: true) {
                vm.chooseMode(.DRAW); if brush.settings.brushType == .ERASER { brush.toggleEraser() }
            },
            Tile(id: "eraser", icon: "eraser", label: "Silgi", color: 0xFFFF_B3C7,
                 selected: vm.mode == .DRAW && brush.settings.brushType == .ERASER, enabled: true) {
                vm.chooseMode(.DRAW); if brush.settings.brushType != .ERASER { brush.toggleEraser() }
            },
            Tile(id: "paint", icon: "drop.fill", label: L("mode_paint"), color: 0xFFFF_E066, selected: painting && !vm.paintBrush, enabled: true) {
                vm.chooseMode(.PAINT)
            },
            Tile(id: "paint_brush", icon: "paintbrush.fill", label: "Boya fırçası", color: 0xFFFF_B870, selected: painting && vm.paintBrush, enabled: true) {
                vm.choosePaintBrush()
            },
            Tile(id: "select", icon: "hand.tap", label: L("mode_select"), color: 0xFFE2_D1FF, selected: vm.mode == .SELECT, enabled: true) {
                vm.chooseMode(.SELECT)
            },
            Tile(id: "shape", icon: "square.on.circle", label: L("add_shape"), color: 0xFFA8_E6FF, selected: vm.mode == .SHAPE, enabled: true) {
                vm.shapes.openPicker()
            },
            Tile(id: "challenge", icon: "trophy.fill", label: L("kids_challenge"), color: 0xFFFF_D59A,
                 selected: challengeUi?.menu == true || challengeUi?.progress == true, enabled: challengeUi != nil) {
                challengeUi?.toggleMenu()
            },
            Tile(id: "undo", icon: "arrow.uturn.backward", label: L("undo"), color: 0xFFFF_E08A, selected: false, enabled: store.canUndo) { vm.undo() },
            Tile(id: "redo", icon: "arrow.uturn.forward", label: L("redo"), color: 0xFFFF_E08A, selected: false, enabled: store.canRedo) { vm.redo() },
        ]
    }

    private var selectedHex: String { vm.mode == .PAINT ? vm.paintColor() : brush.settings.colorHex }

    var body: some View {
        VStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(tiles) { t in
                        Button(action: t.action) {
                            VStack(spacing: 2) {
                                Image(systemName: t.icon).font(.title2.weight(.bold))
                                Text(t.label).font(.caption2.weight(t.selected ? .heavy : .bold)).lineLimit(1)
                            }
                            .foregroundStyle(Color(red: 0.12, green: 0.10, blue: 0.20))
                            .frame(width: 72, height: 64)
                            .background(RoundedRectangle(cornerRadius: 18).fill(Color(uiColor: UIColor(argb: t.color)).opacity(t.enabled ? 1 : 0.4)))
                            .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.primary, lineWidth: t.selected ? 3 : 0))
                        }
                        .disabled(!t.enabled)
                        .accessibilityLabel(t.label)
                        .accessibilityIdentifier("kids_\(t.id)")
                        .tutorialAnchor(t.id == "undo" ? TutorialAnchor.UNDO : t.id == "redo" ? TutorialAnchor.REDO : TutorialAnchor.simple(t.id))
                    }
                }
                .padding(.horizontal, 12)
            }
            if vm.mode == .PAINT && vm.paintBrush {
                HStack(spacing: 14) {
                    Text("Fırça boyu").font(.subheadline.bold())
                    Spacer()
                    ForEach(Array(KidsBrushSizes.sizes.enumerated()), id: \.offset) { i, s in
                        let on = KidsBrushSizes.nearest(brush.settings.sizePx) == s
                        Button { brush.updateSize(s) } label: {
                            Circle().fill(Color.primary).frame(width: CGFloat(8 + 8 * i), height: CGFloat(8 + 8 * i))
                                .frame(width: 44, height: 44)
                                .background(Circle().fill(Color.orange.opacity(on ? 0.6 : 0.2)))
                                .overlay(Circle().stroke(Color.primary, lineWidth: on ? 3 : 1))
                        }
                        .accessibilityIdentifier("kids_brush_size_\(i)")
                    }
                }
                .padding(.horizontal, 16)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 10) {
                ForEach(KidsColors.all, id: \.0) { hex, name in
                    let on = hex.caseInsensitiveCompare(selectedHex) == .orderedSame
                    Button {
                        brush.updateColor(hex)
                        shapes.setRegionColor(hex)
                    } label: {
                        Circle().fill(Color(hex: hex) ?? .gray).frame(width: 40, height: 40)
                            .overlay(Circle().stroke(on ? Color.accentColor : Color.secondary.opacity(0.45), lineWidth: on ? 4 : 1))
                    }
                    .accessibilityLabel(name)
                    .accessibilityIdentifier("kids_color_\(hex)")
                }
            }
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 8)
        .accessibilityIdentifier("kids_palette")
    }
}
