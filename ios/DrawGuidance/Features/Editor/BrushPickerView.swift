import SwiftUI
import UIKit

/// Fırça seçici (Android fırça menüsü): tuval türünün kategorileri, her fırçanın canlı önizlemesi, boyut,
/// akışkanlık ve opaklık.
struct BrushPickerView: View {
    let kind: CanvasKind
    @Binding var brush: BrushType
    @Binding var size: Double
    @Binding var flow: Double
    @Binding var opacity: Double
    let color: UInt32
    /// v51 gelişmiş ayarlar için seçili fırçanın denetleyicisi (yoksa gelişmiş bölümler gösterilmez).
    var controller: BrushController? = nil
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        // v53 fırça penceresi yalnız fırça seçimi içindir: fırçalar kategorilerde, örnek iziyle kart olarak; boy, akış,
        // örtücülük ve gelişmiş ayarlar Özellikler panelinde
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Label(L("v53_brush_settings_in_properties"), systemImage: "slider.horizontal.3")
                        .font(.footnote).foregroundStyle(.secondary)
                    ForEach(BrushCategory.forKind(kind), id: \.self) { cat in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(L(cat.titleKey)).font(.subheadline.weight(.semibold))
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
                                ForEach(cat.brushes, id: \.self) { type in card(type) }
                            }
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle(L("brush_picker_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }

    private func card(_ type: BrushType) -> some View {
        let selected = type == brush
        return Button {
            brush = type
            size = Double(type.defaultSize)
            dismiss()
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                Image(uiImage: BrushPreview.image(type, color: color))
                    .resizable().aspectRatio(240 / 72, contentMode: .fit)
                    .frame(maxWidth: .infinity)
                HStack {
                    Text(L(type.titleKey)).font(.footnote.weight(.medium)).foregroundStyle(.primary).lineLimit(1)
                    Spacer(minLength: 2)
                    if selected { Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.accentColor) }
                }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(selected ? Color.accentColor.opacity(0.14) : Color(uiColor: .secondarySystemGroupedBackground)))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(selected ? Color.accentColor : .clear, lineWidth: 1.5))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("brush_\(type.rawValue)")
    }
}

/// Seçicideki fırça önizlemesi: dalgalı örnek çizgi, gerçek fırça motoruyla.
enum BrushPreview {
    private static let cache: NSCache<NSString, UIImage> = {
        let c = NSCache<NSString, UIImage>()
        c.countLimit = 80
        MemoryHygiene.register(editor: false) { c.removeAllObjects() }
        return c
    }()

    static func image(_ type: BrushType, color: UInt32) -> UIImage {
        let key = "\(type.rawValue)-\(color)" as NSString
        if let img = cache.object(forKey: key) { return img }
        let w: CGFloat = 240, h: CGFloat = 72
        var xs: [Float] = [], ys: [Float] = []
        for i in 0...40 {
            let t = Float(i) / 40
            xs.append(16 + t * Float(w - 32)); ys.append(Float(h) / 2 + sin(t * 2 * .pi) * 14)
        }
        let size = min(type.defaultSize, 26)
        let cg = Bitmaps.draw(Int(w), Int(h)) { c in
            if type.isHelper {
                // v51 yardımcı fırça: renkli bir zemin çizilir, fırça onun üstünde etkisini gösterir
                StrokeRenderer.drawStrokeList(c, helperSample(type, color: color, xs: xs, ys: ys, w: Float(w), h: Float(h)))
            } else {
                BrushPainter.paint(c, xs: xs, ys: ys, color: color, type: type, sizePx: size, opacity: 1, seed: type.rawValue.javaHashCode,
                                   flow: 1, strength: 1)
            }
        }
        let img = UIImage(cgImage: cg)
        cache.setObject(img, forKey: key)
        return img
    }

    /// v51 yardımcı fırça örneği: üç renkli şerit ve üstünde dalga biçiminde yardımcı fırça izi.
    private static func helperSample(_ type: BrushType, color: UInt32, xs: [Float], ys: [Float], w: Float, h: Float) -> [Stroke] {
        func line(_ id: String, _ y: Float, _ hex: String) -> Stroke {
            Stroke(id: id, points: (0...20).map { StrokePoint(12 + Float($0) * (w - 24) / 20, y) }, colorHex: hex, brushType: .MARKER,
                   sizePx: 13, opacity: 1, order: 0)
        }
        let ink = String(format: "#%06X", color & 0xFFFFFF)
        let fx = Stroke(id: "pv-fx-\(type.rawValue)", points: zip(xs, ys).map { StrokePoint($0, $1) }, colorHex: ink, brushType: type,
                        sizePx: 22, opacity: 1, order: 1)
        return [line("pv-a", h * 0.28, "#E4572E"), line("pv-b", h * 0.5, ink), line("pv-c", h * 0.72, "#2E86AB"), fx]
    }
}
