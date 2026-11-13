import SwiftUI

/// v45 seçim menüsünün düğme kimlikleri (Android `SelectionMenuIds`).
enum SelectionMenuIds {
    static let MOVE = "move", SCALE = "scale", COPY = "copy", DELETE = "delete", EDIT = "edit"
    static let ORDER = [MOVE, SCALE, COPY, DELETE, EDIT]
}

/// v45 menülerin yerleşimi (Android `CanvasMenuPlacement`): seçimin üst köşesinde (sağdan sola dillerde sol üst), üstte yer
/// yoksa altında, ikisinde de yoksa alanın içinde kalacak biçimde.
enum CanvasMenuPlacement {
    static func aboveOrBelow(_ anchor: CGRect, menu: CGSize, area: CGSize, gap: CGFloat, margin: CGFloat, rtl: Bool) -> CGPoint {
        let w = menu.width, h = menu.height
        let x = clampSpan(rtl ? anchor.minX : anchor.maxX - w, w, area.width, margin)
        let above = anchor.minY - gap - h, below = anchor.maxY + gap
        let y: CGFloat = above >= margin ? above : below + h <= area.height - margin ? below : clampSpan(above, h, area.height, margin)
        return CGPoint(x: x, y: y)
    }

    static func clampSpan(_ start: CGFloat, _ size: CGFloat, _ total: CGFloat, _ margin: CGFloat) -> CGFloat {
        let mx = total - size - margin
        return mx < margin ? max((total - size) / 2, 0) : min(max(start, margin), mx)
    }
}

/// v45 seçili elemanın köşesindeki saydam yatay menü (Android `SelectionMenu`): Taşı, Ölçekle, Kopyala, Sil, Düzenle.
/// Düzenle seçime özel işlemleri açar; altında sıra, gizle ve seçimi kaldır listesi.
struct SelectionMenu: View {
    @ObservedObject var vm: EditorViewModel
    @ObservedObject var store: EditorStore
    @ObservedObject var viewport: ViewportState
    @Environment(\.layoutDirection) private var direction
    @State private var size: CGSize = .zero
    @State private var listOpen = false

    private struct Action: Identifiable {
        let id: String; let label: String; let icon: String; var danger = false; var selected = false; let run: () -> Void
    }

    private var actions: [Action] {
        [
            Action(id: SelectionMenuIds.MOVE, label: L("mode_move"), icon: "arrow.up.and.down.and.arrow.left.and.right",
                   selected: vm.mode == .MOVE) { vm.chooseMode(.MOVE) },
            Action(id: SelectionMenuIds.SCALE, label: L("mode_scale"), icon: "arrow.up.left.and.arrow.down.right",
                   selected: vm.mode == .SCALE) { vm.chooseMode(.SCALE) },
            Action(id: SelectionMenuIds.COPY, label: L("duplicate_selection"), icon: "plus.square.on.square") { vm.selection.duplicate() },
            Action(id: SelectionMenuIds.DELETE, label: L("delete_selection"), icon: "trash", danger: true) { vm.selection.delete() },
            Action(id: SelectionMenuIds.EDIT, label: L("mode_edit_pick"), icon: "pencil", selected: listOpen) { listOpen.toggle() },
        ]
    }

    private var editItems: [Action] {
        var items: [Action] = [
            Action(id: "sel_edit_open", label: L("mode_edit_pick"), icon: "slider.horizontal.3") { _ = vm.editSelection() },
            Action(id: "sel_rotate", label: L("mode_rotate"), icon: "rotate.right") { vm.chooseMode(.ROTATE) },
        ]
        if store.selectedIds.count > 1 {
            items.append(Action(id: "sel_group", label: L("group_selection"), icon: "square.stack.3d.up") { vm.selection.group() })
        }
        items += [
            Action(id: "sel_forward", label: L("order_forward"), icon: "square.2.layers.3d.top.filled") { vm.selection.reorder(1) },
            Action(id: "sel_backward", label: L("order_backward"), icon: "square.2.layers.3d.bottom.filled") { vm.selection.reorder(-1) },
            Action(id: "sel_hide", label: L("hide_elements"), icon: "eye.slash") { vm.hideElements() },
            Action(id: "sel_clear", label: L("clear_selection"), icon: "xmark") { vm.selection.clear() },
        ]
        return items
    }

    var body: some View {
        if let box = anchorRect {
            GeometryReader { geo in
                let rtl = direction == .rightToLeft
                let at = CanvasMenuPlacement.aboveOrBelow(box, menu: size, area: geo.size, gap: 8, margin: 8, rtl: rtl)
                VStack(alignment: rtl ? .leading : .trailing, spacing: 6) {
                    row
                    if listOpen { list }
                }
                .fixedSize()
                .background(GeometryReader { Color.clear.preference(key: MenuSizeKey.self, value: $0.size) })
                .offset(x: at.x, y: at.y)
                .opacity(size == .zero ? 0 : 1)
            }
            .onPreferenceChange(MenuSizeKey.self) { size = $0 }
            .onChange(of: store.selectedIds) { _, _ in listOpen = false }
        }
    }

    private var anchorRect: CGRect? {
        guard let strokes = store.canvas?.strokes, let b = ElementOps.selectionBounds(strokes, store.selectedIds) else { return nil }
        let r = viewport.toScreen(b)
        return CGRect(x: CGFloat(r.left), y: CGFloat(r.top), width: CGFloat(r.width), height: CGFloat(r.height))
    }

    private var row: some View {
        HStack(spacing: 0) {
            ForEach(actions) { a in
                Button(action: a.run) {
                    HStack(spacing: 3) {
                        Image(systemName: a.icon).font(.system(size: 13))
                        Text(a.label).font(.caption2).lineLimit(1).fixedSize()
                    }
                    .foregroundStyle(a.danger ? Color.red.opacity(0.85) : a.selected ? Color.accentColor : Color.primary.opacity(0.8))
                    .padding(.horizontal, 6).padding(.vertical, 5)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("selection_menu_\(a.id)")
                .tutorialAnchor(TutorialAnchor.selection(a.id))
            }
        }
        .padding(.horizontal, 3).padding(.vertical, 2)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.secondary.opacity(0.22), lineWidth: 1))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(L("v45_selection_menu"))
        .accessibilityIdentifier("selection_menu")
    }

    private var list: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(L("v45_selection_edit_menu")).font(.caption2).foregroundStyle(.secondary).padding(.horizontal, 14).padding(.vertical, 4)
            ForEach(editItems) { item in
                Button { listOpen = false; item.run() } label: {
                    HStack(spacing: 10) {
                        Image(systemName: item.icon).foregroundStyle(Color.accentColor).frame(width: 18)
                        Text(item.label).font(.subheadline).foregroundStyle(.primary).lineLimit(1)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .frame(minWidth: 160, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier(item.id)
            }
        }
        .padding(.vertical, 4)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.secondary.opacity(0.22), lineWidth: 1))
        .accessibilityIdentifier("selection_menu_edit_list")
    }
}

private struct MenuSizeKey: PreferenceKey {
    static let defaultValue: CGSize = .zero
    static func reduce(value: inout CGSize, nextValue: () -> CGSize) { let n = nextValue(); if n != .zero { value = n } }
}
