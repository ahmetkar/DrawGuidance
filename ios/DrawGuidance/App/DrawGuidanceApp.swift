import SwiftUI

@main
struct DrawGuidanceApp: App {
    @StateObject private var store = CanvasStore()

    init() {
        // Tuval resimleri: önbellekten düşen resimler dosyadan yeniden çözülür
        CanvasAssetStore.shared.install()
    }

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(store)
        }
    }
}
