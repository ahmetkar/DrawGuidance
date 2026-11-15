import SwiftUI

/// v52 çizim yardımcıları (Android `V52DrawHelpers`; basit ve gelişmiş resim; yalnız görünüm, tuvale yazılmaz):
///  - Kompozisyon kılavuzu: üçler kuralı, altın oran, köşegenler ya da merkez çizgileri kâğıdın üstünde.
///  - Kontrol önizlemesi: köşede küçük önizleme; aynalı bakış oran/simetri, gri ton açık-koyu dengesini gösterir.
enum CompositionGuide: String, CaseIterable {
    case NONE, THIRDS, GOLDEN, DIAGONALS, CENTER

    var labelKey: String {
        switch self {
        case .NONE: return "v52_helper_none"
        case .THIRDS: return "v52_helper_thirds"
        case .GOLDEN: return "v52_helper_golden"
        case .DIAGONALS: return "v52_helper_diagonals"
        case .CENTER: return "v52_helper_center"
        }
    }

    private static let PHI: CGFloat = 0.618034

    /// Kâğıdın 0..1 oranlarında çizgiler.
    var lines: [(CGPoint, CGPoint)] {
        func cross(_ vs: [CGFloat]) -> [(CGPoint, CGPoint)] {
            vs.flatMap { v in [(CGPoint(x: v, y: 0), CGPoint(x: v, y: 1)), (CGPoint(x: 0, y: v), CGPoint(x: 1, y: v))] }
        }
        switch self {
        case .NONE: return []
        case .THIRDS: return cross([1.0 / 3, 2.0 / 3])
        case .GOLDEN: return cross([1 - Self.PHI, Self.PHI])
        case .DIAGONALS: return [(CGPoint(x: 0, y: 0), CGPoint(x: 1, y: 1)), (CGPoint(x: 1, y: 0), CGPoint(x: 0, y: 1))]
        case .CENTER: return cross([0.5])
        }
    }
}

@MainActor
final class DrawHelpersState: ObservableObject {
    @Published var composition = CompositionGuide.NONE
    @Published var preview = false
    @Published var mirror = true
    @Published var gray = false
    var active: Bool { composition != .NONE || preview }
}

/// Kompozisyon çizgileri tuvalin görünümüne göre (dokunuşu tutmaz).
struct CompositionOverlay: View {
    @ObservedObject var helpers: DrawHelpersState
    let canvas: CanvasUi
    @ObservedObject var viewport: ViewportState

    var body: some View {
        let g = helpers.composition
        if g != .NONE {
            Canvas { ctx, _ in
                let w = CGFloat(canvas.width) * CGFloat(viewport.scale)
                let h = CGFloat(canvas.height) * CGFloat(viewport.scale)
                let ox = CGFloat(viewport.offset.x), oy = CGFloat(viewport.offset.y)
                func pt(_ p: CGPoint) -> CGPoint { CGPoint(x: ox + p.x * w, y: oy + p.y * h) }
                for (a, b) in g.lines {
                    var path = Path(); path.move(to: pt(a)); path.addLine(to: pt(b))
                    ctx.stroke(path, with: .color(.white.opacity(0.7)), lineWidth: 2)
                    ctx.stroke(path, with: .color(.accentColor.opacity(0.85)), style: StrokeStyle(lineWidth: 1, dash: [7, 5]))
                }
                if g == .THIRDS || g == .GOLDEN {
                    // Güçlü noktalar: çizgilerin kesişimleri
                    let xs = g.lines.filter { $0.0.y == 0 }.map { $0.0.x }
                    let ys = g.lines.filter { $0.0.x == 0 }.map { $0.0.y }
                    for x in xs { for y in ys {
                        let c = pt(CGPoint(x: x, y: y))
                        ctx.fill(Path(ellipseIn: CGRect(x: c.x - 3.5, y: c.y - 3.5, width: 7, height: 7)), with: .color(.accentColor.opacity(0.8)))
                    } }
                }
            }
            .allowsHitTesting(false)
            .accessibilityIdentifier("composition_overlay")
        }
    }
}

/// Köşedeki kontrol önizlemesi: aynalı ve/veya gri ton.
struct CheckPreview: View {
    @ObservedObject var helpers: DrawHelpersState
    let canvas: CanvasUi

    var body: some View {
        if helpers.preview {
            VStack(spacing: 4) {
                CanvasThumbnail(canvas: canvas, side: 200)
                    .aspectRatio(CGFloat(canvas.width) / CGFloat(max(canvas.height, 1)), contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.secondary.opacity(0.3)))
                    .scaleEffect(x: helpers.mirror ? -1 : 1, y: 1)
                    .grayscale(helpers.gray ? 1 : 0)
                Text(L(helpers.mirror && helpers.gray ? "v52_helper_preview_both" : helpers.mirror ? "v52_helper_mirror"
                       : helpers.gray ? "v52_helper_gray" : "v52_helper_preview"))
                    .font(.system(size: 10)).foregroundStyle(.secondary)
            }
            .padding(6)
            .frame(width: 132)
            .background(RoundedRectangle(cornerRadius: 12).fill(.regularMaterial).shadow(radius: 4))
            .padding(10)
            .allowsHitTesting(false)
            .accessibilityIdentifier("check_preview")
        }
    }
}

/// Yardımcıların ayar penceresi.
struct DrawHelpersSheet: View {
    @ObservedObject var helpers: DrawHelpersState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker(L("v52_helper_composition"), selection: $helpers.composition) {
                        ForEach(CompositionGuide.allCases, id: \.self) { Text(L($0.labelKey)).tag($0) }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                    .accessibilityIdentifier("helper_composition")
                } header: { Text(L("v52_helper_composition")) } footer: { Text(L("v52_helper_composition_hint")) }
                Section {
                    Toggle(L("v52_helper_preview"), isOn: $helpers.preview).accessibilityIdentifier("helper_preview")
                    if helpers.preview {
                        Toggle(isOn: $helpers.mirror) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L("v52_helper_mirror"))
                                Text(L("v52_helper_mirror_hint")).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .accessibilityIdentifier("helper_mirror")
                        Toggle(isOn: $helpers.gray) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L("v52_helper_gray"))
                                Text(L("v52_helper_gray_hint")).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .accessibilityIdentifier("helper_gray")
                    }
                } footer: { Text(L("v52_helper_preview_hint")) }
            }
            .navigationTitle(L("v52_helpers_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("close")) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
