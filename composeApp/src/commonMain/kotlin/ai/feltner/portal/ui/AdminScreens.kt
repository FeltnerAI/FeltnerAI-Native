package ai.feltner.portal.ui

import ai.feltner.portal.AppState
import ai.feltner.portal.AppViewModel
import ai.feltner.portal.api.ConfigureModelRequest
import ai.feltner.portal.api.CreateProviderRequest
import ai.feltner.portal.api.CreateUserRequest
import ai.feltner.portal.api.LmStudioStatus
import ai.feltner.portal.api.Model
import ai.feltner.portal.api.Provider
import ai.feltner.portal.api.Role
import ai.feltner.portal.api.ServerSettings
import ai.feltner.portal.api.Theme
import ai.feltner.portal.api.UpdateBrandingRequest
import ai.feltner.portal.api.UpdateModelRequest
import ai.feltner.portal.api.UpdateProviderRequest
import ai.feltner.portal.api.UpdateServerSettingsRequest
import ai.feltner.portal.api.UpdateUserRequest
import ai.feltner.portal.api.User
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

@Composable
private fun FormColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun BoolRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit, password: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun SectionHeader(title: String, onAdd: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (onAdd != null) {
            OutlinedButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("New")
            }
        }
    }
}

@Composable
private fun <T> EnumPicker(label: String, options: List<T>, selected: T, render: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: ${render(selected)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(render(opt)) }, onClick = { onSelect(opt); open = false })
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings (theme + password)
// ---------------------------------------------------------------------------

@Composable
fun SettingsSection(state: AppState, vm: AppViewModel) {
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    FormColumn {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Theme.entries.forEach { theme ->
                        FilterChip(
                            selected = state.themePref == theme,
                            onClick = { vm.setTheme(theme) },
                            label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Password", style = MaterialTheme.typography.titleMedium)
                Text("Changing your password signs out your other sessions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field("Current password", current, { current = it }, password = true)
                Field("New password (min 12 chars)", replacement, { replacement = it }, password = true)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        vm.changePassword(current, replacement) {
                            current = ""; replacement = ""; message = "Password changed. Other sessions were signed out."
                        }
                    },
                    enabled = !state.busy && current.isNotBlank() && replacement.length >= 12,
                ) { Text("Change password") }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Account", style = MaterialTheme.typography.titleMedium)
                Text("Signed in as ${state.user?.username}", style = MaterialTheme.typography.bodyMedium)
                state.user?.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------

@Composable
fun UsersSection(vm: AppViewModel) {
    var reload by remember { mutableStateOf(0) }
    var users by remember { mutableStateOf<List<User>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(reload) {
        vm.activeClient?.let { c -> runCatching { users = c.listUsers() }.onFailure(vm::reportError) }
    }
    val list = users ?: return Loading()

    FormColumn {
        SectionHeader("Users (${list.size})") { creating = true }
        list.forEach { user ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(user.username, style = MaterialTheme.typography.titleSmall)
                        Text(
                            buildString {
                                append(user.role.name.lowercase())
                                user.email?.let { append(" · $it") }
                                if (user.disabled) append(" · disabled")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = user }) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = { vm.launch(onDone = { reload++ }) { it.deleteUser(user.id) } }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        }
    }

    if (creating) {
        UserDialog(title = "New user", initial = null, onDismiss = { creating = false }) { username, email, password, role, disabled ->
            vm.launch(onDone = { reload++; creating = false }) {
                it.createUser(CreateUserRequest(username = username, email = email.ifBlank { null }, password = password, role = role))
            }
        }
    }
    editing?.let { user ->
        UserDialog(title = "Edit ${user.username}", initial = user, onDismiss = { editing = null }) { username, email, password, role, disabled ->
            vm.launch(onDone = { reload++; editing = null }) {
                it.updateUser(
                    user.id,
                    UpdateUserRequest(
                        username = username,
                        email = email.ifBlank { null },
                        role = role,
                        disabled = disabled,
                        replacement_password = password.ifBlank { null },
                    ),
                )
            }
        }
    }
}

@Composable
private fun UserDialog(
    title: String,
    initial: User?,
    onDismiss: () -> Unit,
    onConfirm: (username: String, email: String, password: String, role: Role, disabled: Boolean) -> Unit,
) {
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(initial?.role ?: Role.USER) }
    var disabled by remember { mutableStateOf(initial?.disabled ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Username", username, { username = it })
                Field("Email (optional)", email, { email = it })
                Field(if (initial == null) "Password" else "Replacement password (optional)", password, { password = it }, password = true)
                EnumPicker("Role", Role.entries, role, { it.name.lowercase() }) { role = it }
                if (initial != null) BoolRow("Disabled", disabled) { disabled = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(username, email, password, role, disabled) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// Providers
// ---------------------------------------------------------------------------

@Composable
fun ProvidersSection(vm: AppViewModel) {
    var reload by remember { mutableStateOf(0) }
    var providers by remember { mutableStateOf<List<Provider>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Provider?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        vm.activeClient?.let { c -> runCatching { providers = c.listProviders() }.onFailure(vm::reportError) }
    }
    val list = providers ?: return Loading()

    FormColumn {
        SectionHeader("Providers (${list.size})") { creating = true }
        list.forEach { provider ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.name, style = MaterialTheme.typography.titleSmall)
                            Text(provider.base_url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!provider.enabled) AssistChip(onClick = {}, label = { Text("disabled") })
                        IconButton(onClick = { editing = provider }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { vm.launch(onDone = { reload++ }) { it.deleteProvider(provider.id) } }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            vm.launch { c ->
                                val r = c.testProvider(provider.id)
                                testResult = if (r.ok) "✓ ${r.message}\n${r.models.size} models" else "✗ ${r.message}"
                            }
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    ) { Text("Test connection") }
                }
            }
        }
    }

    if (creating) {
        ProviderDialog("New provider", null, onDismiss = { creating = false }) { name, url, key, enabled ->
            vm.launch(onDone = { reload++; creating = false }) {
                it.createProvider(CreateProviderRequest(name = name, base_url = url, api_key = key.ifBlank { null }, enabled = enabled))
            }
        }
    }
    editing?.let { provider ->
        ProviderDialog("Edit ${provider.name}", provider, onDismiss = { editing = null }) { name, url, key, enabled ->
            vm.launch(onDone = { reload++; editing = null }) {
                it.updateProvider(
                    provider.id,
                    UpdateProviderRequest(name = name, base_url = url, api_key = key.ifBlank { null }, enabled = enabled),
                )
            }
        }
    }
    testResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { testResult = null },
            confirmButton = { TextButton(onClick = { testResult = null }) { Text("OK") } },
            title = { Text("Connection test") },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun ProviderDialog(
    title: String,
    initial: Provider?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, apiKey: String, enabled: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.base_url ?: "") }
    var key by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Name", name, { name = it })
                Field("Base URL", url, { url = it })
                Field(if (initial?.has_api_key == true) "API key (leave blank to keep)" else "API key (optional)", key, { key = it }, password = true)
                BoolRow("Enabled", enabled) { enabled = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, url, key, enabled) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------

@Composable
fun ModelsSection(vm: AppViewModel) {
    var reload by remember { mutableStateOf(0) }
    var models by remember { mutableStateOf<List<Model>?>(null) }
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var configuring by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        vm.activeClient?.let { c ->
            runCatching {
                models = c.listAdminModels()
                providers = c.listProviders()
            }.onFailure(vm::reportError)
        }
    }
    val list = models ?: return Loading()

    FormColumn {
        SectionHeader("Models (${list.size})") { if (providers.isNotEmpty()) configuring = true }
        list.forEach { model ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(model.display_name, style = MaterialTheme.typography.titleSmall)
                        Text("${model.provider_name} · ${model.upstream_id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (model.is_default) AssistChip(onClick = {}, label = { Text("default") })
                    Switch(
                        checked = model.enabled,
                        onCheckedChange = { on -> vm.launch(onDone = { reload++ }) { it.updateModel(model.id, UpdateModelRequest(enabled = on)) } },
                    )
                    if (!model.is_default) {
                        TextButton(onClick = { vm.launch(onDone = { reload++ }) { it.updateModel(model.id, UpdateModelRequest(is_default = true)) } }) {
                            Text("Set default")
                        }
                    }
                    IconButton(onClick = { vm.launch(onDone = { reload++ }) { it.deleteModel(model.id) } }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }
            }
        }
    }

    if (configuring) {
        ConfigureModelDialog(providers, onDismiss = { configuring = false }) { providerId, upstreamId, displayName, enabled, isDefault ->
            vm.launch(onDone = { reload++; configuring = false }) {
                it.configureModel(providerId, ConfigureModelRequest(upstream_id = upstreamId, display_name = displayName, enabled = enabled, is_default = isDefault))
            }
        }
    }
}

@Composable
private fun ConfigureModelDialog(
    providers: List<Provider>,
    onDismiss: () -> Unit,
    onConfirm: (providerId: String, upstreamId: String, displayName: String, enabled: Boolean, isDefault: Boolean) -> Unit,
) {
    var providerId by remember { mutableStateOf(providers.first().id) }
    var upstreamId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var isDefault by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EnumPicker("Provider", providers, providers.first { it.id == providerId }, { it.name }) { providerId = it.id }
                Field("Upstream model id", upstreamId, { upstreamId = it })
                Field("Display name", displayName, { displayName = it })
                BoolRow("Enabled", enabled) { enabled = it }
                BoolRow("Default model", isDefault) { isDefault = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(providerId, upstreamId, displayName, enabled, isDefault) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------
// LM Studio
// ---------------------------------------------------------------------------

@Composable
fun LmStudioSection(vm: AppViewModel) {
    var reload by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<LmStudioStatus?>(null) }

    LaunchedEffect(reload) {
        vm.activeClient?.let { c -> runCatching { status = c.lmStudioStatus() }.onFailure(vm::reportError) }
    }
    val s = status ?: return Loading()

    FormColumn {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LM Studio CLI", style = MaterialTheme.typography.titleMedium)
                Text(if (s.cli_available) "Available${s.version?.let { " · $it" } ?: ""}" else "Not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.cli_path?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                s.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Local server: ${if (s.server_running) "running" else "stopped"}", Modifier.weight(1f))
                    Button(
                        onClick = {
                            val action = if (s.server_running) "stop" else "start"
                            vm.launch(onDone = { reload++ }) { status = it.lmStudioServer(action) }
                        },
                        enabled = s.cli_available,
                    ) { Text(if (s.server_running) "Stop" else "Start") }
                }
            }
        }
        Text("Loaded (${s.loaded.size})", style = MaterialTheme.typography.titleSmall)
        s.loaded.forEach { model ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.display_name ?: model.id, Modifier.weight(1f))
                    TextButton(onClick = { vm.launch(onDone = { reload++ }) { status = it.lmStudioUnload(model.id) } }) { Text("Unload") }
                }
            }
        }
        Text("Downloaded (${s.downloaded.size})", style = MaterialTheme.typography.titleSmall)
        s.downloaded.forEach { model ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.display_name ?: model.id, Modifier.weight(1f))
                    TextButton(onClick = { vm.launch(onDone = { reload++ }) { status = it.lmStudioLoad(model.id, null) } }) { Text("Load") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Server settings
// ---------------------------------------------------------------------------

@Composable
fun ServerSection(vm: AppViewModel) {
    var settings by remember { mutableStateOf<ServerSettings?>(null) }
    var publicUrl by remember { mutableStateOf("") }
    var trustedProxies by remember { mutableStateOf("") }
    var startAtLogin by remember { mutableStateOf(false) }
    var lmsPath by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.activeClient?.let { c ->
            runCatching { c.serverSettings() }.onSuccess { s ->
                settings = s
                publicUrl = s.public_url ?: ""
                trustedProxies = s.trusted_proxies.joinToString(", ")
                startAtLogin = s.start_at_login
                lmsPath = s.lmstudio_cli_path ?: ""
            }.onFailure(vm::reportError)
        }
    }
    val s = settings ?: return Loading()

    FormColumn {
        Text("Server settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Field("Public URL", publicUrl, { publicUrl = it })
        Field("Trusted proxies (comma-separated)", trustedProxies, { trustedProxies = it })
        Field("LM Studio CLI path (blank = auto-detect)", lmsPath, { lmsPath = it })
        if (s.startup_supported) BoolRow("Start at login", startAtLogin) { startAtLogin = it }
        Text("Data directory: ${s.data_dir}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (saved) Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            saved = false
            vm.launch(onDone = { saved = true }) {
                val updated = it.updateServerSettings(
                    UpdateServerSettingsRequest(
                        public_url = publicUrl.ifBlank { null },
                        trusted_proxies = trustedProxies.split(",").map { p -> p.trim() }.filter { p -> p.isNotEmpty() },
                        start_at_login = if (s.startup_supported) startAtLogin else null,
                        lmstudio_cli_path = lmsPath, // "" clears the override server-side
                    ),
                )
                settings = updated
            }
        }) { Text("Save settings") }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Backup & restore", style = MaterialTheme.typography.titleSmall)
                Text(
                    "ZIP export/import requires native file dialogs and is available in the web admin. " +
                        "All other server settings are managed here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Branding
// ---------------------------------------------------------------------------

@Composable
fun BrandingSection(state: AppState, vm: AppViewModel) {
    var serverName by remember { mutableStateOf(state.serverName) }
    var accent by remember { mutableStateOf("#4f46e5") }
    var customCss by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    FormColumn {
        Text("Branding", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Field("Server name", serverName, { serverName = it })
        Field("Accent color (hex)", accent, { accent = it })
        OutlinedTextField(
            value = customCss,
            onValueChange = { customCss = it },
            label = { Text("Custom CSS") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )
        if (saved) Text("Saved.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            saved = false
            vm.launch(onDone = { saved = true }) {
                it.updateBranding(
                    UpdateBrandingRequest(
                        server_name = serverName.ifBlank { null },
                        accent_color = accent.ifBlank { null },
                        custom_css = customCss,
                    ),
                )
            }
        }) { Text("Save branding") }
        Text(
            "Logo and favicon uploads require native file selection and are available in the web admin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
