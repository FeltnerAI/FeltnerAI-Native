package ai.feltner.portal

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point consumed by the SwiftUI app in `iosApp/`. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
