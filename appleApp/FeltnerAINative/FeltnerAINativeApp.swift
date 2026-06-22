import SwiftUI

@main
struct FeltnerAINativeApp: App {
    @StateObject private var model = NativeModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
        }
    }
}
