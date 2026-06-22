package ai.feltner.nativeapp.ui

import ai.feltner.nativeapp.NativeAppState
import ai.feltner.nativeapp.FeltnerNativeController
import ai.feltner.nativeapp.NativeRoute
import ai.feltner.nativeapp.NativeSection
import ai.feltner.nativeapp.api.Chat
import ai.feltner.nativeapp.api.Message
import ai.feltner.nativeapp.api.MessageRole
import ai.feltner.nativeapp.api.MessageStatus
import ai.feltner.nativeapp.api.Theme
import ai.feltner.nativeapp.data.ServerProfile
import ai.feltner.nativeapp.platformName
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Servers
// ---------------------------------------------------------------------------

@Composable
fun ServersScreen(state: NativeAppState, vm: FeltnerNativeController, padding: PaddingValues) {
    var url by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp).widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("FeltnerAI-Native", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Connect to a self-hosted FeltnerAI server · ${platformName()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add a server", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("chat.example.com") },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                )
                Button(
                    onClick = { vm.connectToServer(url) },
                    enabled = !state.busy && url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Connect")
                }
            }
        }

        if (state.profiles.isNotEmpty()) {
            Text("Saved servers", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.profiles, key = { it.id }) { profile ->
                    ProfileRow(profile, !state.busy, { vm.openSavedProfile(profile) }, { vm.deleteProfile(profile) })
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: ServerProfile, enabled: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onOpen,
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text(profile.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = "Remove server")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Login
// ---------------------------------------------------------------------------

@Composable
fun LoginScreen(screen: NativeRoute.Login, state: NativeAppState, vm: FeltnerNativeController, padding: PaddingValues) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canSubmit = !state.busy && username.isNotBlank() && password.isNotBlank()

    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp).widthIn(max = 420.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.backToServers() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(screen.handshake.branding.server_name, style = MaterialTheme.typography.titleLarge)
                }
                Text("Sign in to your account", color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username or email") },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.login(username, password) },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Sign in")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Main shell (adaptive navigation drawer)
// ---------------------------------------------------------------------------

private fun NativeSection.icon(): ImageVector = when (this) {
    NativeSection.CHATS -> Icons.AutoMirrored.Filled.Chat
    NativeSection.SETTINGS -> Icons.Default.Settings
    NativeSection.USERS -> Icons.Default.People
    NativeSection.PROVIDERS -> Icons.Default.Dns
    NativeSection.MODELS -> Icons.Default.Storage
    NativeSection.LM_STUDIO -> Icons.Default.Memory
    NativeSection.SERVER -> Icons.Default.Storage
    NativeSection.BRANDING -> Icons.Default.Brush
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: NativeAppState, vm: FeltnerNativeController, padding: PaddingValues) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        val wide = maxWidth >= 900.dp
        if (wide) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(Modifier.width(260.dp)) { NavContent(state, vm) }
                },
            ) {
                SectionContent(state, vm, showMenu = false, onMenu = {})
            }
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet { NavContent(state, vm) { scope.launch { drawerState.close() } } }
                },
            ) {
                SectionContent(state, vm, showMenu = true, onMenu = { scope.launch { drawerState.open() } })
            }
        }
    }
}

@Composable
private fun NavContent(state: NativeAppState, vm: FeltnerNativeController, onNavigate: () -> Unit = {}) {
    Column(Modifier.fillMaxHeight().padding(12.dp)) {
        Text(
            state.serverName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(12.dp),
        )
        val userSections = NativeSection.entries.filter { !it.admin }
        userSections.forEach { section ->
            NavItem(section, state.section == section) { vm.selectSection(section); onNavigate() }
        }
        if (state.isAdmin) {
            Text(
                "ADMINISTRATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            NativeSection.entries.filter { it.admin }.forEach { section ->
                NavItem(section, state.section == section) { vm.selectSection(section); onNavigate() }
            }
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(state.user?.username ?: "", style = MaterialTheme.typography.titleSmall)
                Text(
                    state.user?.role?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThemeToggle(state, vm)
            IconButton(onClick = { vm.logout() }) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
            }
        }
    }
}

@Composable
private fun NavItem(section: NativeSection, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(section.icon(), contentDescription = null) },
        label = { Text(section.title) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun ThemeToggle(state: NativeAppState, vm: FeltnerNativeController) {
    val next = when (state.themePref) {
        Theme.SYSTEM -> Theme.LIGHT
        Theme.LIGHT -> Theme.DARK
        Theme.DARK -> Theme.SYSTEM
    }
    TextButton(onClick = { vm.setTheme(next) }) {
        Text(state.themePref.name.lowercase().replaceFirstChar { it.uppercase() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionContent(state: NativeAppState, vm: FeltnerNativeController, showMenu: Boolean, onMenu: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showMenu) {
                IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
            }
            Text(state.section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (state.section == NativeSection.CHATS) ModelSelector(state, vm)
        }
        HorizontalDivider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.section) {
                NativeSection.CHATS -> ChatSection(state, vm)
                NativeSection.SETTINGS -> SettingsSection(state, vm)
                NativeSection.USERS -> UsersSection(vm)
                NativeSection.PROVIDERS -> ProvidersSection(vm)
                NativeSection.MODELS -> ModelsSection(vm)
                NativeSection.LM_STUDIO -> LmStudioSection(vm)
                NativeSection.SERVER -> ServerSection(vm)
                NativeSection.BRANDING -> BrandingSection(state, vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelector(state: NativeAppState, vm: FeltnerNativeController) {
    var open by remember { mutableStateOf(false) }
    val current = state.models.firstOrNull { it.id == state.selectedModelId }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = state.models.isNotEmpty()) {
            Text(current?.display_name ?: if (state.models.isEmpty()) "No models" else "Select model")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.models.forEach { model ->
                DropdownMenuItem(
                    text = { Text("${model.display_name}  ·  ${model.provider_name}") },
                    onClick = { vm.selectModel(model.id); open = false },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chat section
// ---------------------------------------------------------------------------

@Composable
private fun ChatSection(state: NativeAppState, vm: FeltnerNativeController) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 700.dp) {
            Row(Modifier.fillMaxSize()) {
                ChatListPanel(state, vm, Modifier.width(260.dp).fillMaxHeight())
                VerticalDivider()
                ConversationPane(state, vm, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ChatListStrip(state, vm)
                HorizontalDivider()
                ConversationPane(state, vm, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ChatListPanel(state: NativeAppState, vm: FeltnerNativeController, modifier: Modifier) {
    var renaming by remember { mutableStateOf<Chat?>(null) }
    Column(modifier.padding(8.dp)) {
        Button(onClick = { vm.newChat() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New chat")
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.chats, key = { it.id }) { chat ->
                ChatListItem(
                    chat = chat,
                    selected = chat.id == state.activeChatId,
                    onOpen = { vm.openChat(chat.id) },
                    onRename = { renaming = chat },
                    onDelete = { vm.deleteChat(chat.id) },
                )
            }
        }
    }
    renaming?.let { chat ->
        RenameDialog(chat.title, onDismiss = { renaming = null }) { newTitle ->
            vm.renameChat(chat.id, newTitle); renaming = null
        }
    }
}

@Composable
private fun ChatListStrip(state: NativeAppState, vm: FeltnerNativeController) {
    LazyRow(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            OutlinedButton(onClick = { vm.newChat() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New")
            }
        }
        items(state.chats, key = { it.id }) { chat ->
            val selected = chat.id == state.activeChatId
            val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Text(
                chat.title.ifBlank { "Untitled" },
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(bg)
                    .clickable { vm.openChat(chat.id) }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ChatListItem(chat: Chat, selected: Boolean, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(chat.title.ifBlank { "Untitled chat" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
        IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ConversationPane(state: NativeAppState, vm: FeltnerNativeController, modifier: Modifier) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier) {
        if (state.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Ask anything to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    val isLast = msg.id == state.messages.lastOrNull()?.id
                    MessageBubble(
                        message = msg,
                        canRegenerate = isLast && msg.role == MessageRole.ASSISTANT && !state.streaming,
                        onCopy = { clipboard.setText(AnnotatedString(msg.content)) },
                        onRegenerate = { vm.regenerate() },
                    )
                }
            }
        }

        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message…") },
                modifier = Modifier.weight(1f).heightIn(max = 160.dp),
                enabled = !state.streaming,
                maxLines = 6,
            )
            if (state.streaming) {
                IconButton(onClick = { vm.stopStreaming() }) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
            } else {
                IconButton(
                    onClick = { val t = draft; draft = ""; vm.send(t) },
                    enabled = draft.isNotBlank(),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, canRegenerate: Boolean, onCopy: () -> Unit, onRegenerate: () -> Unit) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 640.dp),
        ) {
            Box(Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(message.content, color = textColor)
                } else if (message.content.isEmpty() && message.status == MessageStatus.STREAMING) {
                    Text("…", color = textColor)
                } else {
                    // Assistant text is GitHub-flavored Markdown. Override the
                    // library's default fillMaxSize modifier so the bubble wraps.
                    Markdown(content = message.content, modifier = Modifier)
                }
            }
        }
        if (message.status == MessageStatus.ERROR) {
            Text("Generation failed.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
        }
        if (!isUser && message.content.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                }
                if (canRegenerate) {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(16.dp))
                    }
                }
                val meta = listOfNotNull(message.provider_name, message.model_name).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
