import SwiftUI

@main
struct DrawGuidanceApp: App {
    @StateObject private var store = EditorDependencies.shared.canvases
    @StateObject private var models3d = EditorDependencies.shared.models3d!
    @AppStorage(ThemeMode.KEY) private var theme = ThemeMode.SYSTEM.rawValue

    init() {
        // freeRASP çalışma zamanı koruması (Android TalsecGuard karşılığı)
        RaspGuard.start()
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
                .preferredColorScheme(ThemeMode(rawValue: theme)?.scheme)
        }
    }
}
