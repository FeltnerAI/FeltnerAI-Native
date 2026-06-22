import SwiftUI
import FeltnerAINativeShared

// MARK: - Shared admin helpers

/// Standard error alert bound to an optional message.
private extension View {
    func adminErrorAlert(_ message: Binding<String?>) -> some View {
        alert(
            "Error",
            isPresented: Binding(get: { message.wrappedValue != nil }, set: { if !$0 { message.wrappedValue = nil } }),
            actions: { Button("OK") { message.wrappedValue = nil } },
            message: { Text(message.wrappedValue ?? "") }
        )
    }
}

// MARK: - Users

struct UsersView: View {
    @EnvironmentObject var model: NativeModel
    @State private var users: [User] = []
    @State private var loaded = false
    @State private var error: String?
    @State private var reloadToken = 0
    @State private var creating = false
    @State private var editing: User?

    var body: some View {
        Group {
            if !loaded {
                ProgressView()
            } else {
                List {
                    ForEach(users, id: \.id) { user in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(user.username).font(.headline)
                            Text(subtitle(user)).font(.caption).foregroundStyle(.secondary)
                        }
                        .swipeActions {
                            Button(role: .destructive) { delete(user) } label: {
                                Label("Delete", systemImage: "trash")
                            }
                            Button { editing = user } label: {
                                Label("Edit", systemImage: "pencil")
                            }
                            .tint(.indigo)
                        }
                    }
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .nativeTopTrailing) {
                Button { creating = true } label: { Image(systemName: "plus") }
            }
        }
        .task(id: reloadToken) { await load() }
        .sheet(isPresented: $creating) {
            UserEditor(user: nil) { reloadToken += 1 }
        }
        .sheet(isPresented: editingBinding) {
            if let editing { UserEditor(user: editing) { reloadToken += 1 } }
        }
        .adminErrorAlert($error)
    }

    private var editingBinding: Binding<Bool> {
        Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })
    }

    private func subtitle(_ user: User) -> String {
        var parts = [user.role.name.lowercased()]
        if let email = user.email { parts.append(email) }
        if user.disabled { parts.append("disabled") }
        return parts.joined(separator: " · ")
    }

    @MainActor private func load() async {
        guard let admin = model.native.admin() else { return }
        do {
            users = try await admin.listUsers()
        } catch {
            self.error = errorMessage(error)
        }
        loaded = true
    }

    private func delete(_ user: User) {
        Task { @MainActor in
            guard let admin = model.native.admin() else { return }
            do {
                try await admin.deleteUser(id: user.id)
                reloadToken += 1
            } catch {
                self.error = errorMessage(error)
            }
        }
    }
}

private struct UserEditor: View {
    @EnvironmentObject var model: NativeModel
    @Environment(\.dismiss) private var dismiss

    let user: User?
    let onSaved: () -> Void

    @State private var username: String
    @State private var email: String
    @State private var password = ""
    @State private var roleName: String
    @State private var disabled: Bool
    @State private var error: String?

    init(user: User?, onSaved: @escaping () -> Void) {
        self.user = user
        self.onSaved = onSaved
        _username = State(initialValue: user?.username ?? "")
        _email = State(initialValue: user?.email ?? "")
        _roleName = State(initialValue: user?.role.name ?? "USER")
        _disabled = State(initialValue: user?.disabled ?? false)
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Username", text: $username)
                    .nativeTextInput()
                TextField("Email (optional)", text: $email)
                    .nativeTextInput()
                SecureField(user == nil ? "Password" : "Replacement password (optional)", text: $password)
                Picker("Role", selection: $roleName) {
                    ForEach(model.native.allRoles, id: \.name) { role in
                        Text(role.name.capitalized).tag(role.name)
                    }
                }
                if user != nil {
                    Toggle("Disabled", isOn: $disabled)
                }
            }
            .navigationTitle(user == nil ? "New user" : "Edit \(user!.username)")
            .nativeInlineNavigationTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                }
            }
            .adminErrorAlert($error)
        }
    }

    @MainActor private func save() async {
        guard let admin = model.native.admin(),
              let role = model.native.allRoles.first(where: { $0.name == roleName }) else { return }
        do {
            if let user {
                _ = try await admin.updateUser(
                    id: user.id,
                    username: username,
                    email: email.isEmpty ? nil : email,
                    role: role,
                    disabled: disabled,
                    replacementPassword: password.isEmpty ? nil : password
                )
            } else {
                _ = try await admin.createUser(
                    username: username,
                    email: email.isEmpty ? nil : email,
                    password: password,
                    role: role
                )
            }
            onSaved()
            dismiss()
        } catch {
            self.error = errorMessage(error)
        }
    }
}

// MARK: - Providers

struct ProvidersView: View {
    @EnvironmentObject var model: NativeModel
    @State private var providers: [Provider] = []
    @State private var loaded = false
    @State private var error: String?
    @State private var testResult: String?
    @State private var reloadToken = 0
    @State private var creating = false
    @State private var editing: Provider?

    var body: some View {
        Group {
            if !loaded {
                ProgressView()
            } else {
                List {
                    ForEach(providers, id: \.id) { provider in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(provider.name).font(.headline)
                                    Text(provider.base_url).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if !provider.enabled {
                                    Text("disabled").font(.caption2)
                                        .padding(.horizontal, 8).padding(.vertical, 3)
                                        .background(.quaternary, in: .capsule)
                                }
                            }
                            Button("Test connection") { test(provider) }
                                .buttonStyle(.bordered)
                                .controlSize(.small)
                        }
                        .swipeActions {
                            Button(role: .destructive) { delete(provider) } label: {
                                Label("Delete", systemImage: "trash")
                            }
                            Button { editing = provider } label: {
                                Label("Edit", systemImage: "pencil")
                            }
                            .tint(.indigo)
                        }
                    }
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .nativeTopTrailing) {
                Button { creating = true } label: { Image(systemName: "plus") }
            }
        }
        .task(id: reloadToken) { await load() }
        .sheet(isPresented: $creating) {
            ProviderEditor(provider: nil) { reloadToken += 1 }
        }
        .sheet(isPresented: editingBinding) {
            if let editing { ProviderEditor(provider: editing) { reloadToken += 1 } }
        }
        .alert(
            "Connection test",
            isPresented: Binding(get: { testResult != nil }, set: { if !$0 { testResult = nil } }),
            actions: { Button("OK") { testResult = nil } },
            message: { Text(testResult ?? "") }
        )
        .adminErrorAlert($error)
    }

    private var editingBinding: Binding<Bool> {
        Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })
    }

    @MainActor private func load() async {
        guard let admin = model.native.admin() else { return }
        do {
            providers = try await admin.listProviders()
        } catch {
            self.error = errorMessage(error)
        }
        loaded = true
    }

    private func delete(_ provider: Provider) {
        Task { @MainActor in
            guard let admin = model.native.admin() else { return }
            do {
                try await admin.deleteProvider(id: provider.id)
                reloadToken += 1
            } catch {
                self.error = errorMessage(error)
            }
        }
    }

    private func test(_ provider: Provider) {
        Task { @MainActor in
            guard let admin = model.native.admin() else { return }
            do {
                let result = try await admin.testProvider(id: provider.id)
                testResult = result.ok
                    ? "✓ \(result.message)\n\(result.models.count) models"
                    : "✗ \(result.message)"
            } catch {
                self.error = errorMessage(error)
            }
        }
    }
}

private struct ProviderEditor: View {
    @EnvironmentObject var model: NativeModel
    @Environment(\.dismiss) private var dismiss

    let provider: Provider?
    let onSaved: () -> Void

    @State private var name: String
    @State private var url: String
    @State private var key = ""
    @State private var enabled: Bool
    @State private var error: String?

    init(provider: Provider?, onSaved: @escaping () -> Void) {
        self.provider = provider
        self.onSaved = onSaved
        _name = State(initialValue: provider?.name ?? "")
        _url = State(initialValue: provider?.base_url ?? "")
        _enabled = State(initialValue: provider?.enabled ?? true)
    }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                TextField("Base URL", text: $url)
                    .nativeTextInput()
                SecureField(
                    provider?.has_api_key == true ? "API key (leave blank to keep)" : "API key (optional)",
                    text: $key
                )
                Toggle("Enabled", isOn: $enabled)
            }
            .navigationTitle(provider == nil ? "New provider" : "Edit \(provider!.name)")
            .nativeInlineNavigationTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                }
            }
            .adminErrorAlert($error)
        }
    }

    @MainActor private func save() async {
        guard let admin = model.native.admin() else { return }
        do {
            if let provider {
                _ = try await admin.updateProvider(
                    id: provider.id, name: name, baseUrl: url,
                    apiKey: key.isEmpty ? nil : key, enabled: enabled
                )
            } else {
                _ = try await admin.createProvider(
                    name: name, baseUrl: url,
                    apiKey: key.isEmpty ? nil : key, enabled: enabled
                )
            }
            onSaved()
            dismiss()
        } catch {
            self.error = errorMessage(error)
        }
    }
}

// MARK: - Models

struct ModelsView: View {
    @EnvironmentObject var model: NativeModel
    @State private var models: [Model] = []
    @State private var providers: [Provider] = []
    @State private var loaded = false
    @State private var error: String?
    @State private var reloadToken = 0
    @State private var configuring = false

    var body: some View {
        Group {
            if !loaded {
                ProgressView()
            } else {
                List {
                    ForEach(models, id: \.id) { item in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.display_name).font(.headline)
                                    Text("\(item.provider_name) · \(item.upstream_id)")
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if item.is_default {
                                    Text("default").font(.caption2)
                                        .padding(.horizontal, 8).padding(.vertical, 3)
                                        .background(.tint.opacity(0.2), in: .capsule)
                                }
                            }
                            HStack {
                                Toggle("Enabled", isOn: Binding(
                                    get: { item.enabled },
                                    set: { setEnabled(item, $0) }
                                ))
                                .labelsHidden()
                                if !item.is_default {
                                    Button("Set default") { setDefault(item) }
                                        .buttonStyle(.bordered).controlSize(.small)
                                }
                            }
                        }
                        .swipeActions {
                            Button(role: .destructive) { delete(item) } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .nativeTopTrailing) {
                Button { configuring = true } label: { Image(systemName: "plus") }
                    .disabled(providers.isEmpty)
            }
        }
        .task(id: reloadToken) { await load() }
        .sheet(isPresented: $configuring) {
            ConfigureModelEditor(providers: providers) { reloadToken += 1 }
        }
        .adminErrorAlert($error)
    }

    @MainActor private func load() async {
        guard let admin = model.native.admin() else { return }
        do {
            models = try await admin.listAdminModels()
            providers = try await admin.listProviders()
        } catch {
            self.error = errorMessage(error)
        }
        loaded = true
    }

    private func setEnabled(_ item: Model, _ on: Bool) {
        run { _ = try await $0.setModelEnabled(id: item.id, enabled: on) }
    }

    private func setDefault(_ item: Model) {
        run { _ = try await $0.setModelDefault(id: item.id) }
    }

    private func delete(_ item: Model) {
        run { try await $0.deleteModel(id: item.id) }
    }

    private func run(_ action: @escaping (AppleAdminClient) async throws -> Void) {
        Task { @MainActor in
            guard let admin = model.native.admin() else { return }
            do {
                try await action(admin)
                reloadToken += 1
            } catch {
                self.error = errorMessage(error)
            }
        }
    }
}

private struct ConfigureModelEditor: View {
    @EnvironmentObject var model: NativeModel
    @Environment(\.dismiss) private var dismiss

    let providers: [Provider]
    let onSaved: () -> Void

    @State private var providerId: String
    @State private var upstreamId = ""
    @State private var displayName = ""
    @State private var enabled = true
    @State private var isDefault = false
    @State private var error: String?

    init(providers: [Provider], onSaved: @escaping () -> Void) {
        self.providers = providers
        self.onSaved = onSaved
        _providerId = State(initialValue: providers.first?.id ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker("Provider", selection: $providerId) {
                    ForEach(providers, id: \.id) { provider in
                        Text(provider.name).tag(provider.id)
                    }
                }
                TextField("Upstream model id", text: $upstreamId)
                    .nativeTextInput()
                TextField("Display name", text: $displayName)
                Toggle("Enabled", isOn: $enabled)
                Toggle("Default model", isOn: $isDefault)
            }
            .navigationTitle("Configure model")
            .nativeInlineNavigationTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                }
            }
            .adminErrorAlert($error)
        }
    }

    @MainActor private func save() async {
        guard let admin = model.native.admin() else { return }
        do {
            _ = try await admin.configureModel(
                providerId: providerId,
                upstreamId: upstreamId,
                displayName: displayName,
                enabled: enabled,
                isDefault: isDefault
            )
            onSaved()
            dismiss()
        } catch {
            self.error = errorMessage(error)
        }
    }
}

// MARK: - LM Studio

struct LmStudioView: View {
    @EnvironmentObject var model: NativeModel
    @State private var status: LmStudioStatus?
    @State private var error: String?
    @State private var reloadToken = 0

    var body: some View {
        Group {
            if let status {
                List {
                    Section("LM Studio CLI") {
                        Text(status.cli_available
                             ? "Available\(status.version.map { " · \($0)" } ?? "")"
                             : "Not found")
                        if let path = status.cli_path {
                            Text(path).font(.caption).foregroundStyle(.secondary)
                        }
                        if let message = status.message {
                            Text(message).font(.caption)
                        }
                        HStack {
                            Text("Local server: \(status.server_running ? "running" : "stopped")")
                            Spacer()
                            Button(status.server_running ? "Stop" : "Start") {
                                server(status.server_running ? "stop" : "start")
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(!status.cli_available)
                        }
                    }
                    Section("Loaded (\(status.loaded.count))") {
                        ForEach(status.loaded, id: \.id) { item in
                            HStack {
                                Text(item.display_name ?? item.id)
                                Spacer()
                                Button("Unload") { unload(item.id) }
                                    .buttonStyle(.bordered).controlSize(.small)
                            }
                        }
                    }
                    Section("Downloaded (\(status.downloaded.count))") {
                        ForEach(status.downloaded, id: \.id) { item in
                            HStack {
                                Text(item.display_name ?? item.id)
                                Spacer()
                                Button("Load") { loadModel(item.id) }
                                    .buttonStyle(.bordered).controlSize(.small)
                            }
                        }
                    }
                }
            } else {
                ProgressView()
            }
        }
        .task(id: reloadToken) { await refresh() }
        .adminErrorAlert($error)
    }

    @MainActor private func refresh() async {
        guard let admin = model.native.admin() else { return }
        do {
            status = try await admin.lmStudioStatus()
        } catch {
            self.error = errorMessage(error)
        }
    }

    private func server(_ action: String) {
        run { try await $0.lmStudioServer(action: action) }
    }

    private func loadModel(_ model: String) {
        run { try await $0.lmStudioLoad(model: model) }
    }

    private func unload(_ model: String) {
        run { try await $0.lmStudioUnload(model: model) }
    }

    private func run(_ action: @escaping (AppleAdminClient) async throws -> LmStudioStatus) {
        Task { @MainActor in
            guard let admin = model.native.admin() else { return }
            do {
                status = try await action(admin)
            } catch {
                self.error = errorMessage(error)
            }
        }
    }
}

// MARK: - Server settings

struct ServerSettingsView: View {
    @EnvironmentObject var model: NativeModel
    @State private var settings: ServerSettings?
    @State private var publicUrl = ""
    @State private var trustedProxies = ""
    @State private var startAtLogin = false
    @State private var lmsPath = ""
    @State private var saved = false
    @State private var error: String?

    var body: some View {
        Group {
            if let settings {
                Form {
                    Section {
                        TextField("Public URL", text: $publicUrl)
                            .nativeTextInput()
                        TextField("Trusted proxies (comma-separated)", text: $trustedProxies)
                            .nativeTextInput()
                        TextField("LM Studio CLI path (blank = auto-detect)", text: $lmsPath)
                            .nativeTextInput()
                        if settings.startup_supported {
                            Toggle("Start at login", isOn: $startAtLogin)
                        }
                        Text("Data directory: \(settings.data_dir)")
                            .font(.caption).foregroundStyle(.secondary)
                        if saved {
                            Text("Saved.").font(.caption).foregroundStyle(.tint)
                        }
                        Button("Save settings") { Task { await save(settings) } }
                    }
                    Section("Backup & restore") {
                        Text("ZIP export/import requires native file dialogs and is available in the web admin. All other server settings are managed here.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
            } else {
                ProgressView()
            }
        }
        .task { await load() }
        .adminErrorAlert($error)
    }

    @MainActor private func load() async {
        guard let admin = model.native.admin() else { return }
        do {
            let loaded = try await admin.serverSettings()
            settings = loaded
            publicUrl = loaded.public_url ?? ""
            trustedProxies = loaded.trusted_proxies.joined(separator: ", ")
            startAtLogin = loaded.start_at_login
            lmsPath = loaded.lmstudio_cli_path ?? ""
        } catch {
            self.error = errorMessage(error)
        }
    }

    @MainActor private func save(_ settings: ServerSettings) async {
        guard let admin = model.native.admin() else { return }
        saved = false
        let proxies = trustedProxies
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        do {
            let updated = try await admin.updateServerSettings(
                publicUrl: publicUrl.isEmpty ? nil : publicUrl,
                trustedProxies: proxies,
                applyStartAtLogin: settings.startup_supported,
                startAtLogin: startAtLogin,
                lmStudioCliPath: lmsPath
            )
            self.settings = updated
            saved = true
        } catch {
            self.error = errorMessage(error)
        }
    }
}

// MARK: - Branding

struct BrandingView: View {
    @EnvironmentObject var model: NativeModel
    @State private var serverName = ""
    @State private var accent = "#4f46e5"
    @State private var customCss = ""
    @State private var saved = false
    @State private var error: String?
    @State private var primed = false

    var body: some View {
        Form {
            Section {
                TextField("Server name", text: $serverName)
                TextField("Accent color (hex)", text: $accent)
                    .nativeTextInput()
                VStack(alignment: .leading) {
                    Text("Custom CSS").font(.caption).foregroundStyle(.secondary)
                    TextField("Custom CSS", text: $customCss, axis: .vertical)
                        .lineLimit(4...10)
                        .nativeTextInput()
                }
                if saved {
                    Text("Saved.").font(.caption).foregroundStyle(.tint)
                }
                Button("Save branding") { Task { await save() } }
            }
            Section {
                Text("Logo and favicon uploads require native file selection and are available in the web admin.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
        .onAppear {
            if !primed { serverName = model.state.serverName; primed = true }
        }
        .adminErrorAlert($error)
    }

    @MainActor private func save() async {
        guard let admin = model.native.admin() else { return }
        saved = false
        do {
            _ = try await admin.updateBranding(
                serverName: serverName.isEmpty ? nil : serverName,
                accentColor: accent.isEmpty ? nil : accent,
                customCss: customCss
            )
            saved = true
        } catch {
            self.error = errorMessage(error)
        }
    }
}
