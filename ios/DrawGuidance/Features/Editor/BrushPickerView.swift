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
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    LabeledContent(L("brush_size_label")) {
                        Slider(value: $size, in: 1...200).frame(width: 180).accessibilityIdentifier("brush_size")
                    }
                    LabeledContent(L("brush_flow_label")) {
                        Slider(value: $flow, in: 0.02...1).frame(width: 180).accessibilityIdentifier("brush_flow")
                    }
                    LabeledContent(L("brush_opacity_label")) {
                        Slider(value: $opacity, in: 0.05...1).frame(width: 180).accessibilityIdentifier("brush_opacity")
                    }
                }
                ForEach(BrushCategory.forKind(kind), id: \.self) { cat in
                    Section(L(cat.titleKey)) {
                        ForEach(cat.brushes, id: \.self) { type in
                            Button {
                                brush = type
                                size = Double(type.defaultSize)
                                dismiss()
                            } label: {
                                HStack {
                                    Image(uiImage: BrushPreview.image(type, color: color))
                                        .resizable().frame(width: 120, height: 36)
                                    Text(L(type.titleKey)).foregroundStyle(.primary)
                                    Spacer()
                                    if type == brush { Image(systemName: "checkmark") }
                                }
                            }
                            .accessibilityIdentifier("brush_\(type.rawValue)")
                        }
                    }
                }
            }
            .navigationTitle(L("brush_picker_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("common.close")) { dismiss() } } }
        }
    }
}

/// Seçicideki fırça önizlemesi: dalgalı örnek çizgi, gerçek fırça motoruyla.
enum BrushPreview {
    private static let cache = NSCache<NSString, UIImage>()

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
            BrushPainter.paint(c, xs: xs, ys: ys, color: color, type: type, sizePx: size, opacity: 1, seed: type.rawValue.javaHashCode,
                               flow: 1, strength: 1)
        }
        let img = UIImage(cgImage: cg)
        cache.setObject(img, forKey: key)
        return img
    }
}
