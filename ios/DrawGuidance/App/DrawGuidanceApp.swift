import SwiftUI

@main
struct DrawGuidanceApp: App {
    @StateObject private var store = CanvasStore()

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(store)
        }
    }
}
