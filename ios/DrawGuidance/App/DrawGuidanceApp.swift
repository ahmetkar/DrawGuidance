import SwiftUI

@main
struct DrawGuidanceApp: App {
    @StateObject private var library = CanvasLibrary()

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(library)
        }
    }
}
