import SwiftUI
import FeltnerAINativeShared

// MARK: - Servers

/// Connect to a new server or open a saved profile.
struct ServersView: View {
    @EnvironmentObject var model: NativeModel
    @State private var url = ""

    var body: some View {
        let state = model.state
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Connect to a self-hosted FeltnerAI server")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    GlassCard {
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Add a server").font(.headline)
                            TextField("chat.example.com", text: $url)
                                .nativeTextInput()
                                .nativeUrlKeyboard()
                                .textFieldStyle(.roundedBorder)
                                .disabled(state.busy)
                                .onSubmit { model.controller.connectToServer(rawUrl: url) }
                            Button {
                                model.controller.connectToServer(rawUrl: url)
                            } label: {
                                if state.busy {
                                    ProgressView().frame(maxWidth: .infinity)
                                } else {
                                    Text("Connect").frame(maxWidth: .infinity)
                                }
                            }
                            .buttonStyle(.glassProminent)
                            .disabled(state.busy || url.isEmpty)
                        }
                    }

                    if !state.profiles.isEmpty {
                        Text("Saved servers").font(.headline)
                        ForEach(state.profiles, id: \.id) { profile in
                            ProfileRow(profile: profile)
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("FeltnerAI-Native")
        }
    }
}

private struct ProfileRow: View {
    @EnvironmentObject var model: NativeModel
    let profile: ServerProfile

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(profile.name).font(.headline)
                Text(profile.url).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Button(role: .destructive) {
                model.controller.deleteProfile(profile: profile)
            } label: {
                Image(systemName: "trash")
            }
            .buttonStyle(.borderless)
            .disabled(model.state.busy)
        }
        .padding()
        .glassEffect(in: .rect(cornerRadius: 18))
        .contentShape(Rectangle())
        .onTapGesture {
            if !model.state.busy { model.controller.openSavedProfile(profile: profile) }
        }
    }
}

// MARK: - Login

struct LoginView: View {
    @EnvironmentObject var model: NativeModel
    @State private var username = ""
    @State private var password = ""

    var body: some View {
        let state = model.state
        let canSubmit = !state.busy && !username.isEmpty && !password.isEmpty

        NavigationStack {
            VStack {
                Spacer()
                GlassCard {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(model.native.loginTitle(state: state))
                            .font(.title2).bold()
                        Text("Sign in to your account").foregroundStyle(.secondary)

                        TextField("Username or email", text: $username)
                            .nativeTextInput()
                            .textFieldStyle(.roundedBorder)
                            .disabled(state.busy)
                        SecureField("Password", text: $password)
                            .textFieldStyle(.roundedBorder)
                            .disabled(state.busy)
                            .onSubmit { if canSubmit { model.controller.login(username: username, password: password) } }

                        Button {
                            model.controller.login(username: username, password: password)
                        } label: {
                            if state.busy {
                                ProgressView().frame(maxWidth: .infinity)
                            } else {
                                Text("Sign in").frame(maxWidth: .infinity)
                            }
                        }
                        .buttonStyle(.glassProminent)
                        .disabled(!canSubmit)
                    }
                }
                .padding()
                Spacer()
            }
            .navigationTitle("Sign in")
            .nativeInlineNavigationTitle()
            .toolbar {
                ToolbarItem(placement: .nativeTopLeading) {
                    Button { model.controller.backToServers() } label: {
                        Image(systemName: "chevron.left")
                    }
                }
            }
        }
    }
}

// MARK: - Main shell

/// Signed-in shell. macOS gets the desktop three-column layout; compact Apple
/// targets keep the navigation-stack layout.
struct MainView: View {
    var body: some View {
        #if os(macOS)
        DesktopMainView()
        #else
        MobileMainView()
        #endif
    }
}

private struct MobileMainView: View {
    @EnvironmentObject var model: NativeModel
    @State private var showSidebar = false

    var body: some View {
        let state = model.state
        NavigationStack {
            SectionContent(section: state.section)
                .navigationTitle(state.section.title)
                .nativeInlineNavigationTitle()
                .toolbar {
                    ToolbarItem(placement: .nativeTopLeading) {
                        Button { showSidebar = true } label: {
                            Image(systemName: "line.3.horizontal")
                        }
                    }
                    if state.section.name == "CHATS" {
                        ToolbarItem(placement: .nativeTopTrailing) { ModelMenu() }
                    }
                }
        }
        .sheet(isPresented: $showSidebar) {
            SidebarView { showSidebar = false }
                .nativeLargeSheetDetent()
        }
    }
}

#if os(macOS)
private struct DesktopMainView: View {
    @EnvironmentObject var model: NativeModel

    var body: some View {
        let state = model.state
        HStack(spacing: 0) {
            DesktopWorkspaceSidebar()
                .frame(width: 260)
            Divider()
            if state.section.name == "CHATS" {
                DesktopChatList()
                    .frame(width: 292)
                Divider()
                DesktopConversationPane()
            } else {
                DesktopAdminPane(section: state.section)
            }
        }
        .frame(minWidth: 1080, minHeight: 680)
        .background(Color.nativeAppBackground)
    }
}

private struct DesktopWorkspaceSidebar: View {
    @EnvironmentObject var model: NativeModel

    var body: some View {
        let state = model.state
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(Color.nativeAccent.opacity(0.22))
                    Text("F").font(.headline.weight(.bold)).foregroundStyle(Color.nativeAccent)
                }
                .frame(width: 34, height: 34)

                Text(state.serverName)
                    .font(.headline.weight(.semibold))
                    .lineLimit(1)
                Spacer()
            }
            .padding(.horizontal, 22)
            .padding(.top, 26)
            .padding(.bottom, 28)

            DesktopNavGroup(title: "WORKSPACE", sections: model.native.userSections())

            if state.isAdmin {
                DesktopNavGroup(title: "ADMINISTRATION", sections: model.native.adminSections())
                    .padding(.top, 18)
            }

            Spacer(minLength: 18)
            DesktopAccountBar()
                .padding(14)
        }
        .background(Color.nativeSidebar)
    }
}

private struct DesktopNavGroup: View {
    @EnvironmentObject var model: NativeModel
    let title: String
    let sections: [NavSection]

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundStyle(.secondary)
                .tracking(3)
                .padding(.horizontal, 22)
                .padding(.bottom, 10)

            ForEach(sections, id: \.name) { section in
                DesktopNavRow(section: section, selected: model.state.section == section)
            }
        }
    }
}

private struct DesktopNavRow: View {
    @EnvironmentObject var model: NativeModel
    let section: NavSection
    let selected: Bool

    var body: some View {
        Button {
            model.controller.selectSection(section: section)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: sectionIcon(section))
                    .font(.system(size: 16, weight: .semibold))
                    .frame(width: 22)
                Text(section.title)
                    .font(.system(size: 15, weight: .semibold))
                Spacer()
            }
            .foregroundStyle(selected ? Color.primary : Color.secondary)
            .padding(.horizontal, 18)
            .padding(.vertical, 9)
            .background {
                if selected {
                    RoundedRectangle(cornerRadius: 10).fill(Color.nativePanel)
                }
            }
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 10)
    }
}

private struct DesktopAccountBar: View {
    @EnvironmentObject var model: NativeModel

    var body: some View {
        let user = model.state.user
        HStack(spacing: 10) {
            ZStack {
                Circle().fill(Color.nativeAccent)
                Text(String((user?.username ?? "?").prefix(1)).uppercased())
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.white)
            }
            .frame(width: 36, height: 36)

            VStack(alignment: .leading, spacing: 2) {
                Text(user?.username ?? "")
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text((user?.role.name ?? "").capitalized)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            ThemeButton()
            Button { model.controller.logout() } label: {
                Image(systemName: "rectangle.portrait.and.arrow.right")
            }
            .buttonStyle(.plain)
            .foregroundStyle(.secondary)
        }
        .padding(12)
        .background(Color.nativePanel, in: RoundedRectangle(cornerRadius: 14))
        .overlay {
            RoundedRectangle(cornerRadius: 14).stroke(Color.nativeBorder, lineWidth: 1)
        }
    }
}

private struct DesktopChatList: View {
    @EnvironmentObject var model: NativeModel
    @State private var renaming: Chat?
    @State private var renameText = ""

    var body: some View {
        VStack(spacing: 0) {
            Button {
                model.controller.startNewChat()
            } label: {
                HStack {
                    Image(systemName: "plus.bubble")
                    Text("New chat")
                    Spacer()
                }
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 18)
                .padding(.vertical, 14)
                .background(
                    LinearGradient(
                        colors: [Color(red: 0.22, green: 0.16, blue: 0.86), Color(red: 0.14, green: 0.53, blue: 0.92)],
                        startPoint: .leading,
                        endPoint: .trailing
                    ),
                    in: RoundedRectangle(cornerRadius: 15)
                )
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 14)
            .padding(.top, 14)
            .padding(.bottom, 12)

            ScrollView {
                LazyVStack(spacing: 7) {
                    ForEach(model.state.chats, id: \.id) { chat in
                        DesktopChatRow(
                            chat: chat,
                            selected: chat.id == model.state.activeChatId,
                            onOpen: { model.controller.openChat(chatId: chat.id) },
                            onRename: {
                                renameText = chat.title
                                renaming = chat
                            },
                            onDelete: { model.controller.deleteChat(chatId: chat.id) }
                        )
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 18)
            }
        }
        .background(Color.nativeSidebar)
        .alert("Rename chat", isPresented: renamingBinding) {
            TextField("Title", text: $renameText)
            Button("Cancel", role: .cancel) { renaming = nil }
            Button("Save") {
                if let chat = renaming {
                    model.controller.renameChat(chatId: chat.id, title: renameText)
                }
                renaming = nil
            }
        }
    }

    private var renamingBinding: Binding<Bool> {
        Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })
    }
}

private struct DesktopChatRow: View {
    let chat: Chat
    let selected: Bool
    let onOpen: () -> Void
    let onRename: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Text(chat.title.isEmpty ? "Untitled" : chat.title)
                .font(.system(size: 15, weight: selected ? .semibold : .regular))
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)

            if selected {
                Button(action: onRename) {
                    Image(systemName: "pencil")
                }
                .buttonStyle(.plain)
                Button(action: onDelete) {
                    Image(systemName: "trash")
                }
                .buttonStyle(.plain)
            }
        }
        .foregroundStyle(selected ? Color.white : Color.primary.opacity(0.86))
        .padding(.horizontal, 13)
        .padding(.vertical, 11)
        .background {
            if selected {
                LinearGradient(
                    colors: [Color(red: 0.22, green: 0.16, blue: 0.86), Color(red: 0.14, green: 0.53, blue: 0.92)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            } else {
                Color.clear
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 13))
        .contentShape(RoundedRectangle(cornerRadius: 13))
        .onTapGesture(perform: onOpen)
        .contextMenu {
            Button("Rename", action: onRename)
            Button("Delete", role: .destructive, action: onDelete)
        }
    }
}

private struct DesktopConversationPane: View {
    @EnvironmentObject var model: NativeModel
    @State private var draft = ""

    var body: some View {
        let state = model.state
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Text(activeTitle(state))
                    .font(.headline.weight(.semibold))
                    .lineLimit(1)
                Spacer()
                ModelMenu()
                    .frame(maxWidth: 430, alignment: .trailing)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.nativePanel, in: RoundedRectangle(cornerRadius: 16))
                    .overlay {
                        RoundedRectangle(cornerRadius: 16).stroke(Color.nativeBorder, lineWidth: 1)
                    }
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 12)
            .background(Color.nativeAppBackground)

            Divider()

            DesktopMessageList()
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            Divider()
            DesktopComposer(draft: $draft)
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .background(Color.nativeAppBackground)
        }
        .background(Color.nativeAppBackground)
    }

    private func activeTitle(_ state: NativeAppState) -> String {
        let title = state.chats.first { $0.id == state.activeChatId }?.title ?? state.section.title
        return title.isEmpty ? "Untitled" : title
    }
}

private struct DesktopMessageList: View {
    @EnvironmentObject var model: NativeModel

    var body: some View {
        let state = model.state
        if state.messages.isEmpty {
            VStack {
                Spacer()
                Text("Ask anything to get started.")
                    .foregroundStyle(.secondary)
                Spacer()
            }
        } else {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 18) {
                        ForEach(state.messages, id: \.id) { message in
                            MessageBubble(
                                message: message,
                                canRegenerate: message.id == state.messages.last?.id
                                    && message.role.name == "ASSISTANT"
                                    && !state.streaming
                            )
                            .id(message.id)
                        }
                    }
                    .padding(.horizontal, 22)
                    .padding(.vertical, 28)
                }
                .onChange(of: state.messages.count) { scrollToBottom(proxy) }
                .onChange(of: state.messages.last?.content) { scrollToBottom(proxy) }
            }
        }
    }

    private func scrollToBottom(_ proxy: ScrollViewProxy) {
        guard let last = model.state.messages.last else { return }
        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
    }
}

private struct DesktopComposer: View {
    @EnvironmentObject var model: NativeModel
    @Binding var draft: String

    var body: some View {
        let state = model.state
        HStack(alignment: .bottom, spacing: 10) {
            TextField("Message...", text: $draft, axis: .vertical)
                .textFieldStyle(.plain)
                .lineLimit(1...6)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Color.nativePanel, in: RoundedRectangle(cornerRadius: 16))
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.nativeAccent.opacity(0.95), lineWidth: 2)
                }
                .disabled(state.streaming)

            if state.streaming {
                Button { model.controller.stopStreaming() } label: {
                    Image(systemName: "stop.fill")
                        .frame(width: 58, height: 44)
                }
                .buttonStyle(.borderedProminent)
            } else {
                Button {
                    let text = draft
                    draft = ""
                    model.controller.send(text: text)
                } label: {
                    Label("Send", systemImage: "paperplane")
                        .frame(minWidth: 96, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
    }
}

private struct DesktopAdminPane: View {
    let section: NavSection

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(section.title)
                    .font(.headline.weight(.semibold))
                Spacer()
            }
            .padding(.horizontal, 22)
            .padding(.vertical, 14)
            Divider()
            SectionContent(section: section)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.nativeAppBackground)
    }
}
#endif

private struct SidebarView: View {
    @EnvironmentObject var model: NativeModel
    var dismiss: () -> Void

    var body: some View {
        let state = model.state
        NavigationStack {
            List {
                Section {
                    ForEach(model.native.userSections(), id: \.name) { section in
                        sectionRow(section)
                    }
                }
                if state.isAdmin {
                    Section("Administration") {
                        ForEach(model.native.adminSections(), id: \.name) { section in
                            sectionRow(section)
                        }
                    }
                }
            }
            .navigationTitle(state.serverName)
            .nativeInlineNavigationTitle()
            .safeAreaInset(edge: .bottom) { accountBar }
        }
    }

    @ViewBuilder private func sectionRow(_ section: NavSection) -> some View {
        Button {
            model.controller.selectSection(section: section)
            dismiss()
        } label: {
            HStack {
                Image(systemName: sectionIcon(section))
                    .frame(width: 24)
                Text(section.title)
                Spacer()
                if model.state.section == section {
                    Image(systemName: "checkmark").foregroundStyle(.tint)
                }
            }
        }
        .foregroundStyle(.primary)
    }

    private var accountBar: some View {
        let user = model.state.user
        return HStack {
            VStack(alignment: .leading, spacing: 1) {
                Text(user?.username ?? "").font(.subheadline.weight(.semibold))
                Text((user?.role.name ?? "").capitalized)
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            ThemeButton()
            Button { model.controller.logout() } label: {
                Image(systemName: "rectangle.portrait.and.arrow.right")
            }
        }
        .padding()
        .background(.bar)
    }
}

private struct ThemeButton: View {
    @EnvironmentObject var model: NativeModel

    var body: some View {
        let current = model.state.themePref
        Button {
            model.controller.setTheme(theme: nextTheme(current))
        } label: {
            Text(current.name.capitalized).font(.caption)
        }
        .buttonStyle(.glass)
    }

    private func nextTheme(_ theme: Theme) -> Theme {
        let nextName: String
        switch theme.name {
        case "SYSTEM": nextName = "LIGHT"
        case "LIGHT": nextName = "DARK"
        default: nextName = "SYSTEM"
        }
        return model.native.allThemes.first { $0.name == nextName } ?? theme
    }
}

/// Dispatches to the view for the active section, keyed by the stable enum name.
private struct SectionContent: View {
    let section: NavSection

    var body: some View {
        switch section.name {
        case "CHATS": ChatView()
        case "SETTINGS": SettingsView()
        case "USERS": UsersView()
        case "PROVIDERS": ProvidersView()
        case "MODELS": ModelsView()
        case "LM_STUDIO": LmStudioView()
        case "SERVER": ServerSettingsView()
        case "BRANDING": BrandingView()
        default: EmptyView()
        }
    }
}

private struct ModelMenu: View {
    @EnvironmentObject var model: NativeModel

    var body: some View {
        let state = model.state
        let current = state.models.first { $0.id == state.selectedModelId }
        Menu {
            ForEach(state.models, id: \.id) { item in
                Button {
                    model.controller.selectModel(modelId: item.id)
                } label: {
                    Text("\(item.display_name) · \(item.provider_name)")
                }
            }
        } label: {
            Text(current?.display_name ?? (state.models.isEmpty ? "No models" : "Select model"))
                .lineLimit(1)
        }
        .disabled(state.models.isEmpty)
    }
}

// MARK: - Chat

struct ChatView: View {
    @EnvironmentObject var model: NativeModel
    @State private var draft = ""
    @State private var renaming: Chat?
    @State private var renameText = ""

    var body: some View {
        VStack(spacing: 0) {
            chatStrip
            Divider()
            ConversationView(draft: $draft)
        }
        .alert("Rename chat", isPresented: renamingBinding) {
            TextField("Title", text: $renameText)
            Button("Cancel", role: .cancel) { renaming = nil }
            Button("Save") {
                if let chat = renaming { model.controller.renameChat(chatId: chat.id, title: renameText) }
                renaming = nil
            }
        }
    }

    private var renamingBinding: Binding<Bool> {
        Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })
    }

    private var chatStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Button { model.controller.startNewChat() } label: {
                    Label("New", systemImage: "plus")
                }
                .buttonStyle(.glass)

                ForEach(model.state.chats, id: \.id) { chat in
                    let selected = chat.id == model.state.activeChatId
                    Button {
                        model.controller.openChat(chatId: chat.id)
                    } label: {
                        Text(chat.title.isEmpty ? "Untitled" : chat.title)
                            .lineLimit(1)
                            .fontWeight(selected ? .semibold : .regular)
                    }
                    .buttonStyle(.glass)
                    .tint(selected ? Color.nativeAccent : nil)
                    .contextMenu {
                        Button {
                            renameText = chat.title
                            renaming = chat
                        } label: { Label("Rename", systemImage: "pencil") }
                        Button(role: .destructive) {
                            model.controller.deleteChat(chatId: chat.id)
                        } label: { Label("Delete", systemImage: "trash") }
                    }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }
}

private struct ConversationView: View {
    @EnvironmentObject var model: NativeModel
    @Binding var draft: String

    var body: some View {
        let state = model.state
        VStack(spacing: 0) {
            if state.messages.isEmpty {
                Spacer()
                Text("Ask anything to get started.")
                    .foregroundStyle(.secondary)
                Spacer()
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(state.messages, id: \.id) { message in
                                MessageBubble(
                                    message: message,
                                    canRegenerate: message.id == state.messages.last?.id
                                        && message.role.name == "ASSISTANT"
                                        && !state.streaming
                                )
                                .id(message.id)
                            }
                        }
                        .padding()
                    }
                    .onChange(of: state.messages.count) { scrollToBottom(proxy) }
                    .onChange(of: state.messages.last?.content) { scrollToBottom(proxy) }
                }
            }

            Divider()
            inputBar
        }
    }

    private func scrollToBottom(_ proxy: ScrollViewProxy) {
        guard let last = model.state.messages.last else { return }
        withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
    }

    private var inputBar: some View {
        let state = model.state
        return HStack(alignment: .bottom, spacing: 8) {
            TextField("Message…", text: $draft, axis: .vertical)
                .lineLimit(1...6)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .glassEffect(in: .rect(cornerRadius: 22))
                .disabled(state.streaming)

            if state.streaming {
                Button { model.controller.stopStreaming() } label: {
                    Image(systemName: "stop.fill")
                }
                .buttonStyle(.glassProminent)
            } else {
                Button {
                    let text = draft
                    draft = ""
                    model.controller.send(text: text)
                } label: {
                    Image(systemName: "arrow.up")
                }
                .buttonStyle(.glassProminent)
                .disabled(draft.isEmpty)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
}

private struct MessageBubble: View {
    @EnvironmentObject var model: NativeModel
    let message: Message
    let canRegenerate: Bool

    var body: some View {
        let isUser = message.role.name == "USER"
        VStack(alignment: isUser ? .trailing : .leading, spacing: 4) {
            bubble(isUser: isUser)
                .frame(maxWidth: .infinity, alignment: isUser ? .trailing : .leading)

            if message.status.name == "ERROR" {
                Text("Generation failed.")
                    .font(.caption).foregroundStyle(.red)
            }
            if !isUser && !message.content.isEmpty {
                actions
            }
        }
    }

    @ViewBuilder private func bubble(isUser: Bool) -> some View {
        let streamingPlaceholder = message.content.isEmpty && message.status.name == "STREAMING"
        Group {
            if isUser {
                Text(message.content).foregroundStyle(.white)
            } else if streamingPlaceholder {
                Text("…")
            } else {
                MarkdownText(message.content)
            }
        }
        .padding(12)
        .background {
            if isUser {
                RoundedRectangle(cornerRadius: 16).fill(Color.nativeAccent)
            } else {
                RoundedRectangle(cornerRadius: 16).fill(.regularMaterial)
            }
        }
        .frame(maxWidth: 640, alignment: isUser ? .trailing : .leading)
    }

    private var actions: some View {
        HStack(spacing: 8) {
            Button {
                copyTextToPasteboard(message.content)
            } label: {
                Image(systemName: "doc.on.doc")
            }
            if canRegenerate {
                Button { model.controller.regenerate() } label: {
                    Image(systemName: "arrow.clockwise")
                }
            }
            let meta = [message.provider_name, message.model_name]
                .compactMap { $0 }
                .joined(separator: " · ")
            if !meta.isEmpty {
                Text(meta).font(.caption2)
            }
        }
        .buttonStyle(.borderless)
        .font(.caption)
        .foregroundStyle(.secondary)
    }
}

// MARK: - Settings

struct SettingsView: View {
    @EnvironmentObject var model: NativeModel
    @State private var current = ""
    @State private var replacement = ""
    @State private var message: String?

    var body: some View {
        let state = model.state
        Form {
            Section("Appearance") {
                Picker("Theme", selection: themeBinding) {
                    ForEach(model.native.allThemes, id: \.name) { theme in
                        Text(theme.name.capitalized).tag(theme.name)
                    }
                }
                .pickerStyle(.segmented)
            }

            Section("Password") {
                Text("Changing your password signs out your other sessions.")
                    .font(.caption).foregroundStyle(.secondary)
                SecureField("Current password", text: $current)
                SecureField("New password (min 12 chars)", text: $replacement)
                if let message {
                    Text(message).font(.caption).foregroundStyle(.tint)
                }
                Button("Change password") {
                    model.controller.changePassword(current: current, new: replacement) {
                        current = ""
                        replacement = ""
                        message = "Password changed. Other sessions were signed out."
                    }
                }
                .disabled(state.busy || current.isEmpty || replacement.count < 12)
            }

            Section("Account") {
                Text("Signed in as \(state.user?.username ?? "")")
                if let email = state.user?.email {
                    Text(email).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    private var themeBinding: Binding<String> {
        Binding(
            get: { model.state.themePref.name },
            set: { name in
                if let theme = model.native.allThemes.first(where: { $0.name == name }) {
                    model.controller.setTheme(theme: theme)
                }
            }
        )
    }
}
