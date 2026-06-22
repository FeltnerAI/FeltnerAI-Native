import SwiftUI
import FeltnerAINativeShared
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

/// The shared Kotlin navigation `NativeSection` enum. Aliased because SwiftUI also
/// exports a `Section` view, which would otherwise make the bare name ambiguous.
typealias NavSection = FeltnerAINativeShared.NativeSection

/// Observable wrapper around the shared Kotlin [FeltnerNativeController].
///
/// SwiftUI owns one of these for the app's lifetime. It mirrors the Kotlin
/// `NativeAppState` StateFlow into a `@Published` property via the closure-based
/// bridge, so the rest of the UI is a pure function of `state`, exactly like
/// the Compose version.
final class NativeModel: ObservableObject {
    let native = AppleNativeBridge()

    @Published var state: NativeAppState

    private var subscription: StateSubscription?

    init() {
        state = native.currentState()
        subscription = native.observeState { [weak self] newState in
            // Delivered on the main thread by the bridge's Main dispatcher.
            self?.state = newState
        }
    }

    /// Direct access to the shared controller for fire-and-forget actions.
    var controller: FeltnerNativeController { native.controller }

    deinit {
        subscription?.cancel()
        native.dispose()
    }
}

// MARK: - Shared helpers

extension Color {
    /// The app's indigo accent (mirrors the Android theme's primary).
    static let nativeAccent = Color(red: 0x4F / 255, green: 0x46 / 255, blue: 0xE5 / 255)
    static let nativeAppBackground = Color(red: 0x03 / 255, green: 0x03 / 255, blue: 0x08 / 255)
    static let nativeSidebar = Color(red: 0x0E / 255, green: 0x0E / 255, blue: 0x18 / 255)
    static let nativePanel = Color(red: 0x13 / 255, green: 0x13 / 255, blue: 0x20 / 255)
    static let nativeBorder = Color(red: 0x2A / 255, green: 0x2A / 255, blue: 0x3A / 255)
}

/// Maps a Kotlin `Theme` to a SwiftUI color scheme (`nil` = follow the system).
func preferredScheme(_ theme: Theme) -> ColorScheme? {
    switch theme.name {
    case "LIGHT": return .light
    case "DARK": return .dark
    default: return nil
    }
}

/// SF Symbol for a navigation `NativeSection`, keyed by the stable Kotlin enum name.
func sectionIcon(_ section: NavSection) -> String {
    switch section.name {
    case "CHATS": return "bubble.left.and.bubble.right"
    case "SETTINGS": return "gearshape"
    case "USERS": return "person.2"
    case "PROVIDERS": return "server.rack"
    case "MODELS": return "cpu"
    case "LM_STUDIO": return "memorychip"
    case "SERVER": return "externaldrive"
    case "BRANDING": return "paintbrush"
    default: return "circle"
    }
}

/// Extracts a human-readable message from an error thrown across the Kotlin
/// boundary, falling back to the Foundation description.
func errorMessage(_ error: Error) -> String {
    let ns = error as NSError
    if let kotlin = ns.userInfo["KotlinException"] as? KotlinThrowable, let message = kotlin.message {
        return message
    }
    return ns.localizedDescription
}

/// Renders GitHub-flavored Markdown inline (bold/italic/code/links), preserving
/// line breaks. A lightweight, dependency-free stand-in for the Compose
/// Markdown renderer — block elements like tables are shown as plain text.
struct MarkdownText: View {
    let content: String

    init(_ content: String) { self.content = content }

    var body: some View {
        if let attributed = try? AttributedString(
            markdown: content,
            options: .init(
                interpretedSyntax: .inlineOnlyPreservingWhitespace,
                failurePolicy: .returnPartiallyParsedIfPossible
            )
        ) {
            Text(attributed)
        } else {
            Text(content)
        }
    }
}

/// A Liquid Glass card used for the connect/login surfaces.
struct GlassCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        content
            .padding(20)
            .glassEffect(in: .rect(cornerRadius: 24))
    }
}

extension View {
    @ViewBuilder func nativeTextInput() -> some View {
        #if os(iOS)
        self
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
        #else
        self
        #endif
    }

    @ViewBuilder func nativeUrlKeyboard() -> some View {
        #if os(iOS)
        self.keyboardType(.URL)
        #else
        self
        #endif
    }

    @ViewBuilder func nativeLargeSheetDetent() -> some View {
        #if os(iOS)
        self.presentationDetents([.large])
        #else
        self
        #endif
    }

    @ViewBuilder func nativeInlineNavigationTitle() -> some View {
        #if os(iOS)
        self.navigationBarTitleDisplayMode(.inline)
        #else
        self
        #endif
    }
}

extension ToolbarItemPlacement {
    static var nativeTopLeading: ToolbarItemPlacement {
        #if os(iOS)
        return .topBarLeading
        #else
        return .automatic
        #endif
    }

    static var nativeTopTrailing: ToolbarItemPlacement {
        #if os(iOS)
        return .topBarTrailing
        #else
        return .automatic
        #endif
    }
}

func copyTextToPasteboard(_ text: String) {
    #if os(iOS)
    UIPasteboard.general.string = text
    #elseif os(macOS)
    NSPasteboard.general.clearContents()
    NSPasteboard.general.setString(text, forType: .string)
    #endif
}
