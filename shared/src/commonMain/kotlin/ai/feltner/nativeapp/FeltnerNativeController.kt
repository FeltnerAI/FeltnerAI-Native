package ai.feltner.nativeapp

import ai.feltner.nativeapp.api.ApiRequestException
import ai.feltner.nativeapp.api.Chat
import ai.feltner.nativeapp.api.FeltnerApiClient
import ai.feltner.nativeapp.api.FeltnerApiClientFactory
import ai.feltner.nativeapp.api.FeltnerClient
import ai.feltner.nativeapp.api.GenerateRequest
import ai.feltner.nativeapp.api.Message
import ai.feltner.nativeapp.api.MessageRole
import ai.feltner.nativeapp.api.MessageStatus
import ai.feltner.nativeapp.api.Model
import ai.feltner.nativeapp.api.ChangePasswordRequest
import ai.feltner.nativeapp.api.Role
import ai.feltner.nativeapp.api.ServerHandshake
import ai.feltner.nativeapp.api.StreamEvent
import ai.feltner.nativeapp.api.Theme
import ai.feltner.nativeapp.api.UpdateChatRequest
import ai.feltner.nativeapp.api.User
import ai.feltner.nativeapp.data.ProfileRepository
import ai.feltner.nativeapp.data.ProfileStore
import ai.feltner.nativeapp.data.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which top-level screen is shown. */
sealed interface NativeRoute {
    data object Servers : NativeRoute
    data class Login(val profile: ServerProfile, val handshake: ServerHandshake) : NativeRoute
    data object Main : NativeRoute
}

/** Sections of the signed-in app, mirroring the web portal's sidebar. */
enum class NativeSection(val title: String, val admin: Boolean) {
    CHATS("Chats", false),
    SETTINGS("Settings", false),
    USERS("Users", true),
    PROVIDERS("Providers", true),
    MODELS("Models", true),
    LM_STUDIO("LM Studio", true),
    SERVER("Server", true),
    BRANDING("Branding", true),
}

/** Immutable snapshot of everything the UI renders. */
data class NativeAppState(
    val screen: NativeRoute = NativeRoute.Servers,
    val profiles: List<ServerProfile> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val themePref: Theme = Theme.SYSTEM,
    // Active session
    val serverName: String = "FeltnerAI",
    val section: NativeSection = NativeSection.CHATS,
    val user: User? = null,
    val models: List<Model> = emptyList(),
    val selectedModelId: String? = null,
    val chats: List<Chat> = emptyList(),
    val activeChatId: String? = null,
    val messages: List<Message> = emptyList(),
    val streaming: Boolean = false,
) {
    val isAdmin: Boolean get() = user?.role == Role.ADMIN
}

/**
 * Drives the whole native app: server-profile management, login, chat list,
 * message history, and streaming generation. Holds the single active
 * [FeltnerApiClient]. UI layers render [state] and call these actions.
 * UI is a pure function of [state].
 */
class FeltnerNativeController(
    private val scope: CoroutineScope,
    private val store: ProfileRepository = ProfileStore(),
    private val clientFactory: FeltnerApiClientFactory = FeltnerApiClientFactory { baseUrl, token ->
        FeltnerClient(baseUrl, bearerToken = token)
    },
) {
    private val _state = MutableStateFlow(
        NativeAppState(
            profiles = store.listProfiles(),
            themePref = store.loadTheme()?.let { runCatching { Theme.valueOf(it) }.getOrNull() } ?: Theme.SYSTEM,
        ),
    )
    val state: StateFlow<NativeAppState> = _state.asStateFlow()

    private var client: FeltnerApiClient? = null
    private var activeProfile: ServerProfile? = null
    private var streamJob: Job? = null

    /** The active server client, available to admin/account screens after login. */
    val activeClient: FeltnerApiClient? get() = client

    /** Run suspend [block] on the app scope, surfacing failures through [state]. */
    fun launch(onDone: () -> Unit = {}, block: suspend (FeltnerApiClient) -> Unit) {
        val c = client ?: return
        scope.launch {
            try {
                block(c)
            } catch (t: Throwable) {
                fail(t)
            } finally {
                onDone()
            }
        }
    }

    // ---- Errors -----------------------------------------------------------

    fun dismissError() = _state.update { it.copy(error = null) }

    fun reportError(t: Throwable) = fail(t)

    /** Surface a pre-formatted message on the global error channel (used by the
     *  native iOS screens, which catch Swift errors and forward the text). */
    fun showError(message: String) = _state.update { it.copy(busy = false, error = message) }

    private fun fail(t: Throwable) {
        val msg = (t as? ApiRequestException)?.message ?: t.message ?: "Something went wrong."
        _state.update { it.copy(busy = false, error = msg) }
    }

    // ---- Server profiles --------------------------------------------------

    /** Validate a URL, persist a profile, and advance to the login screen. */
    fun connectToServer(rawUrl: String) {
        val url = normalizeServerUrl(rawUrl) ?: run {
            _state.update { it.copy(error = "Enter a valid server URL or hostname.") }
            return
        }
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val probe = clientFactory.create(url, null)
                val handshake = probe.handshake()
                probe.close()
                if (handshake.api_major != 1) {
                    throw ApiRequestException(
                        "This Native supports API v1; the server requires v${handshake.api_major}.",
                        status = 0,
                    )
                }
                if (!handshake.setup_complete) {
                    throw ApiRequestException(
                        "Finish first-run setup in a browser before adding this server.",
                        status = 0,
                    )
                }
                val existing = store.listProfiles().firstOrNull { it.serverUuid == handshake.server_uuid }
                val profile = ServerProfile(
                    id = existing?.id ?: randomUuid(),
                    serverUuid = handshake.server_uuid,
                    name = handshake.branding.server_name,
                    url = url,
                    allowInsecureHttp = url.startsWith("http://"),
                    lastUsedAt = nowIso(),
                )
                val profiles = store.saveProfile(profile)
                _state.update { it.copy(busy = false, profiles = profiles) }
                openProfile(profile, handshake)
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    /** Open a saved profile: re-handshake, then resume session or show login. */
    fun openSavedProfile(profile: ServerProfile) {
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val probe = clientFactory.create(profile.url, null)
                val handshake = probe.handshake()
                probe.close()
                openProfile(profile, handshake)
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    fun deleteProfile(profile: ServerProfile) {
        val profiles = store.deleteProfile(profile.id)
        _state.update { it.copy(profiles = profiles) }
    }

    private suspend fun openProfile(profile: ServerProfile, handshake: ServerHandshake) {
        activeProfile = profile
        val saved = store.loadToken(profile.serverUuid)
        val c = clientFactory.create(profile.url, saved)
        client = c
        // A stored token may have expired; verify before trusting it.
        if (saved != null) {
            val session = runCatching { c.session() }.getOrNull()
            if (session != null) {
                _state.update {
                    it.copy(
                        busy = false,
                        serverName = handshake.branding.server_name,
                        user = session.user,
                        themePref = session.user.theme,
                    )
                }
                enterMain()
                return
            }
            store.deleteToken(profile.serverUuid)
            c.bearerToken = null
        }
        _state.update {
            it.copy(busy = false, serverName = handshake.branding.server_name, screen = NativeRoute.Login(profile, handshake))
        }
    }

    // ---- Auth -------------------------------------------------------------

    fun login(username: String, password: String) {
        val c = client ?: return
        val profile = activeProfile ?: return
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val session = c.login(username, password)
                session.bearer_token?.let { store.storeToken(profile.serverUuid, it) }
                _state.update { it.copy(busy = false, user = session.user, themePref = session.user.theme) }
                enterMain()
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    fun logout() {
        val c = client
        val profile = activeProfile
        scope.launch {
            streamJob?.cancel()
            runCatching { c?.logout() }
            profile?.let { store.deleteToken(it.serverUuid) }
            c?.close()
            client = null
            activeProfile = null
            _state.update {
                NativeAppState(profiles = store.listProfiles())
            }
        }
    }

    fun backToServers() {
        client?.close()
        client = null
        activeProfile = null
        _state.update { NativeAppState(profiles = store.listProfiles()) }
    }

    // ---- Navigation, theme & account -------------------------------------

    private fun enterMain() {
        _state.update { it.copy(screen = NativeRoute.Main, section = NativeSection.CHATS) }
        refreshModels()
        refreshChats()
    }

    fun selectSection(section: NativeSection) = _state.update { it.copy(section = section) }

    /** Update the theme locally + on the server (best-effort) and persist it. */
    fun setTheme(theme: Theme) {
        store.storeTheme(theme.name)
        _state.update { it.copy(themePref = theme) }
        val c = client ?: return
        scope.launch {
            runCatching { c.updatePreferences(theme) }
                .onSuccess { user -> _state.update { it.copy(user = user) } }
        }
    }

    fun changePassword(current: String, new: String, onSuccess: () -> Unit) {
        val c = client ?: return
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                c.changePassword(ChangePasswordRequest(current_password = current, new_password = new))
                _state.update { it.copy(busy = false) }
                onSuccess()
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    // ---- Chat -------------------------------------------------------------

    private fun refreshModels() {
        val c = client ?: return
        scope.launch {
            runCatching { c.listModels() }.onSuccess { models ->
                _state.update {
                    it.copy(
                        models = models,
                        selectedModelId = chooseSelectedModel(it.selectedModelId, models),
                    )
                }
            }
        }
    }

    private fun refreshChats() {
        val c = client ?: return
        scope.launch {
            runCatching { c.listChats() }
                .onSuccess { chats -> _state.update { it.copy(chats = chats) } }
                .onFailure { fail(it) }
        }
    }

    fun selectModel(modelId: String) {
        _state.update { it.copy(selectedModelId = modelId) }
        // Persist the choice onto the active chat, matching the web portal.
        val c = client ?: return
        val chatId = _state.value.activeChatId ?: return
        scope.launch { runCatching { c.updateChat(chatId, UpdateChatRequest(model_id = modelId)) } }
    }

    fun renameChat(chatId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val c = client ?: return
        scope.launch {
            runCatching { c.updateChat(chatId, UpdateChatRequest(title = trimmed)) }
                .onSuccess { updated ->
                    _state.update { s -> s.copy(chats = s.chats.map { if (it.id == chatId) updated else it }) }
                }
                .onFailure { fail(it) }
        }
    }

    fun openChat(chatId: String) {
        val c = client ?: return
        _state.update { it.copy(activeChatId = chatId, messages = emptyList()) }
        scope.launch {
            runCatching { c.messages(chatId) }
                .onSuccess { msgs -> _state.update { it.copy(messages = msgs) } }
                .onFailure { fail(it) }
        }
    }

    fun newChat() {
        _state.update { it.copy(activeChatId = null, messages = emptyList()) }
    }

    /** Swift-friendly alias; Kotlin/Native renames `newChat` because `new` is reserved. */
    fun startNewChat() = newChat()

    fun deleteChat(chatId: String) {
        val c = client ?: return
        scope.launch {
            runCatching { c.deleteChat(chatId) }.onSuccess {
                _state.update {
                    val activeCleared = if (it.activeChatId == chatId) null else it.activeChatId
                    it.copy(
                        chats = it.chats.filterNot { ch -> ch.id == chatId },
                        activeChatId = activeCleared,
                        messages = if (activeCleared == null) emptyList() else it.messages,
                    )
                }
            }.onFailure { fail(it) }
        }
    }

    /** Send a user message, creating the chat first if needed, then stream. */
    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        val c = client ?: return
        val modelId = _state.value.selectedModelId
        streamJob?.cancel()
        streamJob = scope.launch {
            try {
                val chatId = _state.value.activeChatId ?: run {
                    val created = c.createChat(modelId)
                    _state.update { it.copy(activeChatId = created.id, chats = listOf(created) + it.chats) }
                    created.id
                }

                // Optimistically append the user's message and an empty assistant
                // bubble that the stream fills in.
                val userMsg = localMessage(chatId, MessageRole.USER, content, MessageStatus.COMPLETE)
                val assistantMsg = localMessage(chatId, MessageRole.ASSISTANT, "", MessageStatus.STREAMING)
                _state.update {
                    it.copy(messages = it.messages + userMsg + assistantMsg, streaming = true)
                }

                val request = GenerateRequest(request_id = randomUuid(), content = content, model_id = modelId)
                c.streamGenerate(chatId, request, regenerate = false) { event ->
                    when (event) {
                        is StreamEvent.Started -> {}
                        is StreamEvent.Delta -> appendDelta(assistantMsg.id, event.content)
                        is StreamEvent.Completed -> setStatus(assistantMsg.id, MessageStatus.COMPLETE)
                        is StreamEvent.Error -> {
                            setStatus(assistantMsg.id, MessageStatus.ERROR)
                            _state.update { it.copy(error = event.message) }
                        }
                    }
                }
            } catch (t: Throwable) {
                fail(t)
            } finally {
                _state.update { it.copy(streaming = false) }
                // Reconcile optimistic state with the server's canonical history.
                _state.value.activeChatId?.let { id ->
                    runCatching { c.messages(id) }.onSuccess { msgs ->
                        _state.update { it.copy(messages = msgs) }
                    }
                }
                refreshChats()
            }
        }
    }

    /** Re-run the last assistant turn for the active chat. */
    fun regenerate() {
        val s = _state.value
        if (s.streaming) return
        val chatId = s.activeChatId ?: return
        val c = client ?: return
        val modelId = s.selectedModelId
        streamJob?.cancel()
        streamJob = scope.launch {
            try {
                // Drop the last assistant message and show a fresh streaming bubble.
                val withoutLast = s.messages.dropLastWhile { it.role == MessageRole.ASSISTANT }
                val assistantMsg = localMessage(chatId, MessageRole.ASSISTANT, "", MessageStatus.STREAMING)
                _state.update { it.copy(messages = withoutLast + assistantMsg, streaming = true) }

                val request = GenerateRequest(request_id = randomUuid(), content = "", model_id = modelId)
                c.streamGenerate(chatId, request, regenerate = true) { event ->
                    when (event) {
                        is StreamEvent.Started -> {}
                        is StreamEvent.Delta -> appendDelta(assistantMsg.id, event.content)
                        is StreamEvent.Completed -> setStatus(assistantMsg.id, MessageStatus.COMPLETE)
                        is StreamEvent.Error -> {
                            setStatus(assistantMsg.id, MessageStatus.ERROR)
                            _state.update { it.copy(error = event.message) }
                        }
                    }
                }
            } catch (t: Throwable) {
                fail(t)
            } finally {
                _state.update { it.copy(streaming = false) }
                runCatching { c.messages(chatId) }.onSuccess { msgs ->
                    _state.update { it.copy(messages = msgs) }
                }
            }
        }
    }

    fun stopStreaming() {
        val c = client ?: return
        val chatId = _state.value.activeChatId ?: return
        scope.launch { runCatching { c.stop(chatId) } }
        streamJob?.cancel()
        _state.update { it.copy(streaming = false) }
    }

    private fun appendDelta(messageId: String, delta: String) {
        _state.update { s ->
            s.copy(messages = s.messages.map {
                if (it.id == messageId) it.copy(content = it.content + delta) else it
            })
        }
    }

    private fun setStatus(messageId: String, status: MessageStatus) {
        _state.update { s ->
            s.copy(messages = s.messages.map {
                if (it.id == messageId) it.copy(status = status) else it
            })
        }
    }

    private fun localMessage(chatId: String, role: MessageRole, content: String, status: MessageStatus) =
        Message(
            id = "local-${randomUuid()}",
            chat_id = chatId,
            role = role,
            content = content,
            status = status,
            created_at = nowIso(),
        )

}

internal fun normalizeServerUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    val schemeEnd = withScheme.indexOf("://")
    if (schemeEnd <= 0) return null

    val hostAndPath = withScheme.substring(schemeEnd + 3).trimEnd('/')
    if (hostAndPath.isBlank() || hostAndPath.contains("://")) return null

    return withScheme.substring(0, schemeEnd + 3) + hostAndPath
}

internal fun chooseSelectedModel(existing: String?, models: List<Model>): String? =
    existing ?: models.firstOrNull { it.is_default }?.id ?: models.firstOrNull()?.id
