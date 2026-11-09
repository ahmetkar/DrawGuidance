import SwiftUI

@main
struct DrawGuidanceApp: App {
    @StateObject private var store = CanvasStore()
    @StateObject private var models3d = Model3DRepository()

    init() {
        // Tuval resimleri: önbellekten düşen resimler dosyadan yeniden çözülür
        CanvasAssetStore.shared.install()
        // Koddaki Türkçe metinlerin (3B şekil adları, iletiler) çevirisi
        I18n.setLanguage(I18n.systemLanguage())
    }

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(store)
                .environmentObject(models3d)
        }
    }
}
