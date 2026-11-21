import SwiftUI

/// v56 damlalığın canlı renk dairesi (2D-5): dokunma noktasının 60 pt yukarısında halka; üst yarısı o an altındaki renk,
/// alt yarısı önceki renk; ortada artı ve 1 px nokta; dış kenar açık ve koyu çift çizgi. Ekranda kalacak şekilde kayar;
/// bırakınca 150 ms'de solar. Çocuk modunda daha büyük.
struct EyedropperLoupeView: View {
    @ObservedObject var vm: EditorViewModel
    @State private var last: EditorViewModel.Loupe?

    private var kids: Bool { vm.store.canvas?.kind == .KIDS }

    var body: some View {
        GeometryReader { geo in
            if let l = vm.loupe ?? last {
                let r = CGFloat(EyedropLoupe.radius(kids: kids))
                let ringW: CGFloat = kids ? 18 : 14
                let s = vm.viewport.toScreen(l.point)
                let c = EyedropLoupe.center(s.x, s.y, Float(geo.size.width), Float(geo.size.height), Float(r))
                let center = CGPoint(x: CGFloat(c.0), y: CGFloat(c.1))
                let current = l.hex.flatMap { Color(hex: $0) }
                ZStack {
                    // Üst yarı: altındaki renk; alt yarı: önceki renk
                    Circle().trim(from: 0.5, to: 1).stroke(current ?? Color.gray.opacity(0.4), lineWidth: ringW)
                        .frame(width: (r - ringW / 2) * 2, height: (r - ringW / 2) * 2)
                    Circle().trim(from: 0, to: 0.5).stroke(Color(hex: l.previous) ?? .black, lineWidth: ringW)
                        .frame(width: (r - ringW / 2) * 2, height: (r - ringW / 2) * 2)
                    Circle().stroke(Color.white, lineWidth: 2).frame(width: r * 2 + 3, height: r * 2 + 3)
                    Circle().stroke(Color.black.opacity(0.6), lineWidth: 2).frame(width: r * 2 + 7, height: r * 2 + 7)
                    Circle().stroke(Color.black.opacity(0.6), lineWidth: 1.5).frame(width: (r - ringW - 1) * 2, height: (r - ringW - 1) * 2)
                    Path { p in
                        let arm = r * 0.22
                        p.move(to: CGPoint(x: r - arm, y: r)); p.addLine(to: CGPoint(x: r + arm, y: r))
                        p.move(to: CGPoint(x: r, y: r - arm)); p.addLine(to: CGPoint(x: r, y: r + arm))
                    }
                    .stroke(Color.black.opacity(0.7), lineWidth: 1.5)
                    .frame(width: r * 2, height: r * 2)
                    Circle().fill(current ?? .white).frame(width: 3, height: 3)
                }
                .position(center)
                .opacity(vm.loupe == nil ? 0 : 1)
                .animation(.easeOut(duration: vm.loupe == nil ? 0.15 : 0), value: vm.loupe == nil)
                .allowsHitTesting(false)
                .accessibilityIdentifier("eyedropper_loupe")
                .accessibilityValue(l.hex ?? "")
            }
        }
        .allowsHitTesting(false)
        .onChangeCompat(of: vm.loupe) { _, new in if let new { last = new } }
    }
}
