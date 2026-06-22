package ai.feltner.nativeapp

import ai.feltner.nativeapp.api.ApiRequestException
import ai.feltner.nativeapp.api.Branding
import ai.feltner.nativeapp.api.ChangePasswordRequest
import ai.feltner.nativeapp.api.Chat
import ai.feltner.nativeapp.api.ConfigureModelRequest
import ai.feltner.nativeapp.api.ConnectionTestResponse
import ai.feltner.nativeapp.api.CreateProviderRequest
import ai.feltner.nativeapp.api.CreateUserRequest
import ai.feltner.nativeapp.api.FeltnerApiClient
import ai.feltner.nativeapp.api.FeltnerApiClientFactory
import ai.feltner.nativeapp.api.GenerateRequest
import ai.feltner.nativeapp.api.LmStudioStatus
import ai.feltner.nativeapp.api.Message
import ai.feltner.nativeapp.api.MessageRole
import ai.feltner.nativeapp.api.MessageStatus
import ai.feltner.nativeapp.api.Model
import ai.feltner.nativeapp.api.Provider
import ai.feltner.nativeapp.api.Role
import ai.feltner.nativeapp.api.ServerHandshake
import ai.feltner.nativeapp.api.ServerSettings
import ai.feltner.nativeapp.api.SessionResponse
import ai.feltner.nativeapp.api.StreamEvent
import ai.feltner.nativeapp.api.Theme
import ai.feltner.nativeapp.api.UpdateBrandingRequest
import ai.feltner.nativeapp.api.UpdateChatRequest
import ai.feltner.nativeapp.api.UpdateModelRequest
import ai.feltner.nativeapp.api.UpdateProviderRequest
import ai.feltner.nativeapp.api.UpdateServerSettingsRequest
import ai.feltner.nativeapp.api.UpdateUserRequest
import ai.feltner.nativeapp.api.User
import ai.feltner.nativeapp.api.parseStreamEventPayload
import ai.feltner.nativeapp.data.ProfileRepository
import ai.feltner.nativeapp.data.ServerProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeltnerNativeControllerTest {
    @Test
    fun normalizesServerUrls() {
        assertEquals("https://chat.example.com", normalizeServerUrl("chat.example.com"))
        assertEquals("https://example.com", normalizeServerUrl(" example.com/ "))
        assertEquals("http://localhost:8080", normalizeServerUrl("http://localhost:8080/"))
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("https://"))
    }

    @Test
    fun parsesSsePayloads() {
        assertEquals(StreamEvent.Started("m1"), parseStreamEventPayload("""{"event":"started","message_id":"m1"}"""))
        assertEquals(StreamEvent.Delta("hi"), parseStreamEventPayload("""{"event":"delta","content":"hi"}"""))
        assertEquals(StreamEvent.Completed("m1"), parseStreamEventPayload("""{"event":"completed","message_id":"m1"}"""))
        assertEquals(StreamEvent.Error("bad"), parseStreamEventPayload("""{"event":"error","message":"bad"}"""))
        assertNull(parseStreamEventPayload("""{"event":"unknown"}"""))
        assertNull(parseStreamEventPayload("not json"))
    }

    @Test
    fun choosesDefaultModelWhenNoSelectionExists() {
        val models = listOf(
            model("m1", isDefault = false),
            model("m2", isDefault = true),
        )

        assertEquals("m2", chooseSelectedModel(null, models))
        assertEquals("m1", chooseSelectedModel("m1", models))
        assertNull(chooseSelectedModel(null, emptyList()))
    }

    @Test
    fun sendStreamsOptimisticMessagesThenReconciles() = runTest {
        val api = FakeClient()
        val controller = controller(api)

        signIn(controller)
        controller.send(" hello ")
        runCurrent()
        api.afterSendDelta.await()

        val streaming = controller.state.value
        assertTrue(streaming.streaming)
        assertEquals("hello", streaming.messages.first { it.role == MessageRole.USER }.content)
        assertEquals("Hi", streaming.messages.last { it.role == MessageRole.ASSISTANT }.content)

        api.finishSend.complete(Unit)
        advanceUntilIdle()

        val reconciled = controller.state.value
        assertFalse(reconciled.streaming)
        assertEquals(listOf("hello", "Hi there"), reconciled.messages.map { it.content })
        assertEquals(listOf("c1"), reconciled.chats.map { it.id })
    }

    @Test
    fun regenerateReplacesLastAssistantWhileStreaming() = runTest {
        val api = FakeClient()
        val controller = controller(api)

        signIn(controller)
        controller.send("hello")
        runCurrent()
        api.finishSend.complete(Unit)
        advanceUntilIdle()

        controller.regenerate()
        runCurrent()
        api.afterRegenerateDelta.await()

        val streaming = controller.state.value
        assertTrue(streaming.streaming)
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), streaming.messages.map { it.role })
        assertEquals("Better", streaming.messages.last().content)

        api.finishRegenerate.complete(Unit)
        advanceUntilIdle()

        val reconciled = controller.state.value
        assertFalse(reconciled.streaming)
        assertEquals(listOf("hello", "Better answer"), reconciled.messages.map { it.content })
    }

    @Test
    fun surfacesApiRequestErrors() = runTest {
        val api = FakeClient(handshakeError = ApiRequestException("Server rejected", status = 400))
        val controller = controller(api)

        controller.connectToServer("example.com")
        advanceUntilIdle()

        assertEquals("Server rejected", controller.state.value.error)
        assertFalse(controller.state.value.busy)
    }

    private suspend fun TestScope.signIn(controller: FeltnerNativeController) {
        controller.connectToServer("example.com")
        advanceUntilIdle()
        assertIs<NativeRoute.Login>(controller.state.value.screen)

        controller.login("aspen", "password")
        advanceUntilIdle()
        assertIs<NativeRoute.Main>(controller.state.value.screen)
        assertEquals("m2", controller.state.value.selectedModelId)
    }

    private fun TestScope.controller(api: FakeClient): FeltnerNativeController =
        FeltnerNativeController(
            scope = this,
            store = InMemoryProfiles(),
            clientFactory = FeltnerApiClientFactory { _, token ->
                api.bearerToken = token
                api
            },
        )
}

private class InMemoryProfiles : ProfileRepository {
    private var profiles = emptyList<ServerProfile>()
    private var theme: String? = null
    private val tokens = mutableMapOf<String, String>()

    override fun listProfiles(): List<ServerProfile> = profiles
    override fun saveProfile(profile: ServerProfile): List<ServerProfile> {
        profiles = profiles.filterNot { it.id == profile.id } + profile
        return profiles
    }
    override fun deleteProfile(id: String): List<ServerProfile> {
        val target = profiles.firstOrNull { it.id == id }
        profiles = profiles.filterNot { it.id == id }
        target?.let { tokens.remove(it.serverUuid) }
        return profiles
    }
    override fun loadTheme(): String? = theme
    override fun storeTheme(theme: String) {
        this.theme = theme
    }
    override fun loadToken(serverUuid: String): String? = tokens[serverUuid]
    override fun storeToken(serverUuid: String, token: String) {
        tokens[serverUuid] = token
    }
    override fun deleteToken(serverUuid: String) {
        tokens.remove(serverUuid)
    }
}

private class FakeClient(
    private val handshakeError: Throwable? = null,
) : FeltnerApiClient {
    override var bearerToken: String? = null

    val afterSendDelta = CompletableDeferred<Unit>()
    val finishSend = CompletableDeferred<Unit>()
    val afterRegenerateDelta = CompletableDeferred<Unit>()
    val finishRegenerate = CompletableDeferred<Unit>()

    private var chats = emptyList<Chat>()
    private var canonicalMessages = emptyList<Message>()

    override suspend fun handshake(): ServerHandshake {
        handshakeError?.let { throw it }
        return ServerHandshake(
            server_uuid = "server-1",
            api_major = 1,
            version = "1.0",
            setup_complete = true,
            branding = Branding(server_name = "FeltnerAI"),
        )
    }

    override suspend fun login(login: String, password: String): SessionResponse {
        bearerToken = "token-1"
        return session()
    }

    override suspend fun logout() {
        bearerToken = null
    }

    override suspend fun session(): SessionResponse =
        SessionResponse(user = user(), bearer_token = bearerToken ?: "token-1", expires_at = "2099-01-01T00:00:00Z")

    override suspend fun listModels(): List<Model> =
        listOf(model("m1", isDefault = false), model("m2", isDefault = true))

    override suspend fun listChats(): List<Chat> = chats

    override suspend fun createChat(modelId: String?): Chat {
        val chat = Chat(id = "c1", title = "New chat", model_id = modelId, created_at = now, updated_at = now)
        chats = listOf(chat)
        return chat
    }

    override suspend fun deleteChat(id: String) {
        chats = chats.filterNot { it.id == id }
    }

    override suspend fun messages(chatId: String): List<Message> = canonicalMessages

    override suspend fun stop(chatId: String) = Unit

    override suspend fun updateChat(id: String, request: UpdateChatRequest): Chat =
        chats.first { it.id == id }.copy(
            title = request.title ?: chats.first { it.id == id }.title,
            model_id = request.model_id ?: chats.first { it.id == id }.model_id,
        )

    override suspend fun changePassword(request: ChangePasswordRequest) = Unit
    override suspend fun updatePreferences(theme: Theme): User = user(theme)

    override suspend fun streamGenerate(
        chatId: String,
        request: GenerateRequest,
        regenerate: Boolean,
        onEvent: (StreamEvent) -> Unit,
    ) {
        if (regenerate) {
            onEvent(StreamEvent.Started("a2"))
            onEvent(StreamEvent.Delta("Better"))
            afterRegenerateDelta.complete(Unit)
            finishRegenerate.await()
            onEvent(StreamEvent.Delta(" answer"))
            onEvent(StreamEvent.Completed("a2"))
            canonicalMessages = listOf(
                message(chatId, "u1", MessageRole.USER, "hello"),
                message(chatId, "a2", MessageRole.ASSISTANT, "Better answer"),
            )
        } else {
            onEvent(StreamEvent.Started("a1"))
            onEvent(StreamEvent.Delta("Hi"))
            afterSendDelta.complete(Unit)
            finishSend.await()
            onEvent(StreamEvent.Delta(" there"))
            onEvent(StreamEvent.Completed("a1"))
            canonicalMessages = listOf(
                message(chatId, "u1", MessageRole.USER, request.content),
                message(chatId, "a1", MessageRole.ASSISTANT, "Hi there"),
            )
        }
    }

    override fun close() = Unit

    override suspend fun listUsers(): List<User> = emptyList()
    override suspend fun createUser(request: CreateUserRequest): User = user()
    override suspend fun updateUser(id: String, request: UpdateUserRequest): User = user()
    override suspend fun deleteUser(id: String) = Unit
    override suspend fun listProviders(): List<Provider> = emptyList()
    override suspend fun createProvider(request: CreateProviderRequest): Provider = provider()
    override suspend fun updateProvider(id: String, request: UpdateProviderRequest): Provider = provider()
    override suspend fun deleteProvider(id: String) = Unit
    override suspend fun testProvider(id: String): ConnectionTestResponse =
        ConnectionTestResponse(ok = true, message = "ok")
    override suspend fun listAdminModels(): List<Model> = listModels()
    override suspend fun configureModel(providerId: String, request: ConfigureModelRequest): Model = model("m3")
    override suspend fun updateModel(id: String, request: UpdateModelRequest): Model = model(id)
    override suspend fun deleteModel(id: String) = Unit
    override suspend fun serverSettings(): ServerSettings = ServerSettings()
    override suspend fun updateServerSettings(request: UpdateServerSettingsRequest): ServerSettings = ServerSettings()
    override suspend fun updateBranding(request: UpdateBrandingRequest): Branding = Branding(server_name = "FeltnerAI")
    override suspend fun lmStudioStatus(): LmStudioStatus = LmStudioStatus()
    override suspend fun lmStudioServer(action: String): LmStudioStatus = LmStudioStatus()
    override suspend fun lmStudioLoad(model: String, contextLength: Int?): LmStudioStatus = LmStudioStatus()
    override suspend fun lmStudioUnload(model: String?): LmStudioStatus = LmStudioStatus()
}

private const val now = "2026-01-01T00:00:00Z"

private fun user(theme: Theme = Theme.SYSTEM): User =
    User(id = "user-1", username = "aspen", role = Role.ADMIN, theme = theme, created_at = now)

private fun model(id: String, isDefault: Boolean = false): Model =
    Model(
        id = id,
        provider_id = "provider-1",
        provider_name = "Provider",
        upstream_id = id,
        display_name = id,
        enabled = true,
        is_default = isDefault,
    )

private fun provider(): Provider =
    Provider(
        id = "provider-1",
        name = "Provider",
        base_url = "https://example.com",
        has_api_key = true,
        enabled = true,
        created_at = now,
    )

private fun message(chatId: String, id: String, role: MessageRole, content: String): Message =
    Message(
        id = id,
        chat_id = chatId,
        role = role,
        content = content,
        status = MessageStatus.COMPLETE,
        created_at = now,
    )
