import SwiftUI
import FeltnerAINativeShared

/// Root of the native iOS Native. Routes between the server picker, login, and
/// the signed-in shell based on the shared `NativeAppState`, and applies the global
/// theme, accent, and error alert — the SwiftUI analogue of the Compose `App()`.
struct ContentView: View {
    @EnvironmentObject var model: NativeModel

    var body: some View {
        let state = model.state

        Group {
            switch model.native.screenKind(state: state) {
            case "servers": ServersView()
            case "login": LoginView()
            default: MainView()
            }
        }
        .tint(.nativeAccent)
        .preferredColorScheme(preferredScheme(state.themePref))
        .alert(
            "Something went wrong",
            isPresented: Binding(
                get: { state.error != nil },
                set: { presented in if !presented { model.controller.dismissError() } }
            ),
            actions: { Button("OK") { model.controller.dismissError() } },
            message: { Text(state.error ?? "") }
        )
    }
}
