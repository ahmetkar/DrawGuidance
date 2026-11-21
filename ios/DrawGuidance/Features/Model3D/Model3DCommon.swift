import SwiftUI
import UIKit

// v56 3B Çizim ve 3B Çizimlerim ortak parçaları (eski 3B düzenleyiciden kalan yardımcılar).

/// 3B renk paleti.
let Palette3D: [Int32] = [
    0xFFFFFFFF, 0xFFECEFF1, 0xFF9E9E9E, 0xFF37474F, 0xFF212121, 0xFFE53935, 0xFFE07A5F, 0xFFF2CC8F, 0xFFFFD54F, 0xFF81B29A,
    0xFF43A047, 0xFF6EC1E4, 0xFF4F8BD6, 0xFF3949AB, 0xFF9C6ADE, 0xFFE5989B, 0xFF8D6E63, 0xFFB98B55,
].map { Int32(bitPattern: UInt32($0)) }

struct ColorRow3: View {
    let selected: Int32?
    var extra: [Int32] = []
    let onPick: (Int32) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array((extra + Palette3D).enumerated()), id: \.offset) { _, c in
                    let chosen = selected.map { ($0 | Int32(bitPattern: 0xFF00_0000)) == (c | Int32(bitPattern: 0xFF00_0000)) } ?? false
                    Button { onPick(c) } label: {
                        Circle().fill(Color(argb: c)).frame(width: 22, height: 22).padding(4)
                            .overlay(Circle().stroke(chosen ? Color.accentColor : Color.secondary.opacity(0.4), lineWidth: chosen ? 3 : 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(String(format: "#%06X", UInt32(bitPattern: c) & 0xFFFFFF))
                    .accessibilityAddTraits(chosen ? .isSelected : [])
                }
            }
        }
    }
}

struct Chip3: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    init(_ label: String, _ selected: Bool, action: @escaping () -> Void) { self.label = label; self.selected = selected; self.action = action }
    var body: some View {
        Button(action: action) {
            Text(label).font(.footnote).lineLimit(1).fixedSize()
                .padding(.horizontal, 12).frame(height: 32)
                .foregroundStyle(selected ? Color.accentColor : .primary)
                .background(selected ? Color.accentColor.opacity(0.15) : Color(uiColor: .secondarySystemBackground), in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct ChipRow3<Content: View>: View {
    @ViewBuilder let content: () -> Content
    var body: some View { ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 8) { content() } } }
}

struct ValueSlider3: View {
    let label: String, value: String
    let current: Float
    let range: ClosedRange<Float>
    let onChange: (Float) -> Void

    init(_ label: String, _ value: String, _ current: Float, _ range: ClosedRange<Float>, _ onChange: @escaping (Float) -> Void) {
        self.label = label; self.value = value; self.current = current; self.range = range; self.onChange = onChange
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack { Text(label).font(.footnote); Spacer(); Text(value).font(.footnote.monospacedDigit()).foregroundStyle(.secondary) }
            Slider(value: Binding(get: { Double(current) }, set: { onChange(Float($0)) }), in: Double(range.lowerBound)...Double(range.upperBound))
                .accessibilityLabel(label)
        }
    }
}

struct Section3<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Color.accentColor)
            content()
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.accentColor.opacity(0.05), in: RoundedRectangle(cornerRadius: 14))
    }
}

/// Arka planda üretilip gösterilen küçük resim.
struct AsyncThumb: View {
    let key: String
    let make: @Sendable () -> CGImage?
    @State private var image: CGImage?
    var body: some View {
        ZStack {
            if let image { Image(decorative: image, scale: 1).resizable().scaledToFit() } else { Image(systemName: "cube").foregroundStyle(.secondary.opacity(0.4)) }
        }
        .task(id: key) { image = await Task.detached(priority: .utility) { make() }.value }
    }
}

/// Çok parmaklı dokunuş yüzeyi: dokunuşları (konum, basınç, kalem mi) olduğu gibi iletir.
struct TouchSurface: UIViewRepresentable {
    struct Touch { let id: ObjectIdentifier; let point: CGPoint; let pressure: CGFloat; let stylus: Bool }
    enum Phase { case began, moved, ended }
    var identifier = "sketch3d_viewport"
    let onTouches: (Phase, [Touch]) -> Void

    func makeUIView(context: Context) -> TouchView {
        let v = TouchView()
        v.isMultipleTouchEnabled = true
        v.backgroundColor = .clear
        v.onTouches = onTouches
        // SwiftUI'nin kimliği boş UIView'a ulaşmıyor; UI testleri görünümü bu kimlikle bulur
        v.accessibilityIdentifier = identifier
        return v
    }

    func updateUIView(_ v: TouchView, context: Context) { v.onTouches = onTouches }

    final class TouchView: UIView {
        var onTouches: ((Phase, [Touch]) -> Void)?
        private var active: [ObjectIdentifier: UITouch] = [:]

        private func snapshot() -> [Touch] {
            active.values.map { t in
                let force = t.maximumPossibleForce > 0 ? t.force / t.maximumPossibleForce : 1
                return Touch(id: ObjectIdentifier(t), point: t.location(in: self), pressure: t.type == .pencil ? force : 1, stylus: t.type == .pencil)
            }
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
            for t in touches { active[ObjectIdentifier(t)] = t }
            onTouches?(.began, snapshot())
        }
        override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) { onTouches?(.moved, snapshot()) }
        override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
            for t in touches { active.removeValue(forKey: ObjectIdentifier(t)) }
            onTouches?(active.isEmpty ? .ended : .moved, snapshot())
        }
        override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { touchesEnded(touches, with: event) }
    }
}

struct ShareFile: Identifiable { let url: URL; var id: String { url.path } }

/// UIKit paylaşım penceresi.
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}


extension Color {
    /// ARGB tamsayıdan renk.
    init(argb c: Int32) {
        let u = UInt32(bitPattern: c)
        self.init(.sRGB, red: Double(u >> 16 & 0xFF) / 255, green: Double(u >> 8 & 0xFF) / 255, blue: Double(u & 0xFF) / 255,
                  opacity: Double(u >> 24 & 0xFF) / 255)
    }
}
