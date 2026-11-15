import SwiftUI

/// v50 dikey telefon ekranında düzenleyicinin alt pencereleri (Android `DockedSheetHost`): pencere ekranın en çok
/// [FRACTION] kadarını kaplar ve tuval ile seçim menüsü pencere açıkken de kullanılabilir. İkisi çakışırsa son
/// kullanılan öne gelir: seçim menüsüne dokunulunca pencere [LOW] yüksekliğe iner (menü üstte kalır), pencereye dokunulup
/// büyütülünce yeniden öne çıkar.
@MainActor
final class DockedSheetState: ObservableObject {
    static let shared = DockedSheetState()

    static let FRACTION: CGFloat = 0.46
    static let LOW: CGFloat = 0.16
    static let normal = PresentationDetent.fraction(FRACTION)
    static let low = PresentationDetent.fraction(LOW)

    /// Açık yerleşik pencere sayısı.
    @Published var openCount = 0
    /// Pencerenin yüksekliği (kullanıcı da sürükleyerek değiştirir).
    @Published var detent: PresentationDetent = normal

    var open: Bool { openCount > 0 }

    /// Pencerenin ekranın altından kapladığı oran (açık değilse 0; tam ekranda menü zaten görünmez).
    var coveredFraction: CGFloat {
        guard open else { return 0 }
        if detent == Self.low { return Self.LOW }
        if detent == Self.normal { return Self.FRACTION }
        return 0
    }

    /// Seçim menüsüne dokunuldu: çakışırsa menü pencerenin üstünde kalsın diye pencere alçalır.
    func menuTouched() { if open && detent != Self.low { detent = Self.low } }
}

private struct DockedEditorSheet: ViewModifier {
    @ObservedObject private var state = DockedSheetState.shared
    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.verticalSizeClass) private var vSize

    func body(content: Content) -> some View {
        if hSize == .compact && vSize == .regular {
            // v52 dikey telefonda bütün pencereler ekranı kaplar (Ahmet: alta yerleşik küçük pencere kalmasın)
            content
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        } else {
            content.presentationDetents([.medium, .large])
        }
    }
}

/// v51 ekleme pencereleri (karakterler, efektler, nesneler, şekiller, alıştırmalar vb.): dikey ekranda da ekranın
/// tamamını kaplar (Android `NeuSheet(full = true)`).
private struct FullEditorSheet: ViewModifier {
    func body(content: Content) -> some View {
        content
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
    }
}

/// v52 genel pencereler: dikey telefonda yalnız tam ekran, öteki boyutlarda yarım/tam.
private struct PortraitFullDetents: ViewModifier {
    @Environment(\.horizontalSizeClass) private var hSize
    @Environment(\.verticalSizeClass) private var vSize

    func body(content: Content) -> some View {
        if hSize == .compact && vSize == .regular {
            content.presentationDetents([.large]).presentationDragIndicator(.visible)
        } else {
            content.presentationDetents([.medium, .large])
        }
    }
}

extension View {
    /// v52 dikey telefonda tam ekran pencere (bkz. [PortraitFullDetents]).
    func portraitFullDetents() -> some View { modifier(PortraitFullDetents()) }

    /// v51 tam ekran düzenleyici penceresi (ekleme ve seçim pencereleri; bkz. [FullEditorSheet]).
    func fullEditorSheet() -> some View { modifier(FullEditorSheet()) }

    /// v50 düzenleyici alt penceresi: dikey telefonda küçük ve tuvali kapatmayan (bkz. [DockedSheetState]).
    func dockedEditorSheet() -> some View { modifier(DockedEditorSheet()) }
}
