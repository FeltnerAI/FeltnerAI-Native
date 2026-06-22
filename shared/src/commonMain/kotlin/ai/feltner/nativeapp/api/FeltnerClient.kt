package ai.feltner.nativeapp.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Thrown for non-2xx responses, carrying the server's structured error code. */
class ApiRequestException(
    message: String,
    val status: Int,
    val code: String = "request_failed",
) : Exception(message)

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/**
 * UI-neutral API surface used by the shared app controller. Keeping this small
 * interface in front of the concrete Ktor client lets tests exercise state
 * transitions without a real FeltnerAI server.
 */
interface FeltnerApiClient {
    var bearerToken: String?

    suspend fun handshake(): ServerHandshake
    suspend fun login(login: String, password: String): SessionResponse
    suspend fun logout()
    suspend fun session(): SessionResponse
    suspend fun listModels(): List<Model>
    suspend fun listChats(): List<Chat>
    suspend fun createChat(modelId: String?): Chat
    suspend fun deleteChat(id: String)
    suspend fun messages(chatId: String): List<Message>
    suspend fun stop(chatId: String)
    suspend fun updateChat(id: String, request: UpdateChatRequest): Chat
    suspend fun changePassword(request: ChangePasswordRequest)
    suspend fun updatePreferences(theme: Theme): User
    suspend fun listUsers(): List<User>
    suspend fun createUser(request: CreateUserRequest): User
    suspend fun updateUser(id: String, request: UpdateUserRequest): User
    suspend fun deleteUser(id: String)
    suspend fun listProviders(): List<Provider>
    suspend fun createProvider(request: CreateProviderRequest): Provider
    suspend fun updateProvider(id: String, request: UpdateProviderRequest): Provider
    suspend fun deleteProvider(id: String)
    suspend fun testProvider(id: String): ConnectionTestResponse
    suspend fun listAdminModels(): List<Model>
    suspend fun configureModel(providerId: String, request: ConfigureModelRequest): Model
    suspend fun updateModel(id: String, request: UpdateModelRequest): Model
    suspend fun deleteModel(id: String)
    suspend fun serverSettings(): ServerSettings
    suspend fun updateServerSettings(request: UpdateServerSettingsRequest): ServerSettings
    suspend fun updateBranding(request: UpdateBrandingRequest): Branding
    suspend fun lmStudioStatus(): LmStudioStatus
    suspend fun lmStudioServer(action: String): LmStudioStatus
    suspend fun lmStudioLoad(model: String, contextLength: Int?): LmStudioStatus
    suspend fun lmStudioUnload(model: String?): LmStudioStatus
    suspend fun streamGenerate(
        chatId: String,
        request: GenerateRequest,
        regenerate: Boolean,
        onEvent: (StreamEvent) -> Unit,
    )

    fun close()
}

fun interface FeltnerApiClientFactory {
    fun create(baseUrl: String, bearerToken: String?): FeltnerApiClient
}

/**
 * Stateful client for a single FeltnerAI server. Holds the base URL and the
 * opaque bearer token returned by portal login, and sends it as
 * `Authorization: Bearer ...` on every authenticated call. Portal bearer
 * requests do not require CSRF, unlike browser cookie sessions.
 */
class FeltnerClient(
    var baseUrl: String,
    override var bearerToken: String? = null,
) : FeltnerApiClient {
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
    }

    private fun api(path: String) = "${baseUrl.trimEnd('/')}/api/v1$path"

    private suspend fun ensureOk(response: HttpResponse): HttpResponse {
        if (response.status.isSuccess()) return response
        val raw = runCatching { response.bodyAsText() }.getOrNull()
        val parsed = raw?.let { runCatching { json.decodeFromString<ApiError>(it) }.getOrNull() }
        throw ApiRequestException(
            message = parsed?.message ?: "Request failed (${response.status.value})",
            status = response.status.value,
            code = parsed?.code ?: "request_failed",
        )
    }

    // ---- Public handshake -------------------------------------------------

    /** Validate a server and read its public metadata. No auth required. */
    override suspend fun handshake(): ServerHandshake =
        ensureOk(httpClient.get(api("/server"))).body()

    // ---- Auth -------------------------------------------------------------

    /** Portal login. Stores and returns the opaque bearer session token. */
    override suspend fun login(login: String, password: String): SessionResponse {
        val response = ensureOk(
            httpClient.post(api("/auth/login")) {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(login = login, password = password, portal = true))
            },
        )
        val session: SessionResponse = response.body()
        bearerToken = session.bearer_token
        return session
    }

    override suspend fun logout() {
        runCatching {
            httpClient.post(api("/auth/logout")) { auth() }
        }
        bearerToken = null
    }

    override suspend fun session(): SessionResponse =
        ensureOk(httpClient.get(api("/auth/session")) { auth() }).body()

    // ---- Models & chats ---------------------------------------------------

    override suspend fun listModels(): List<Model> =
        ensureOk(httpClient.get(api("/models")) { auth() }).body()

    override suspend fun listChats(): List<Chat> =
        ensureOk(httpClient.get(api("/chats")) { auth() }).body()

    override suspend fun createChat(modelId: String?): Chat =
        ensureOk(
            httpClient.post(api("/chats")) {
                auth(); contentType(ContentType.Application.Json)
                setBody(CreateChatRequest(model_id = modelId))
            },
        ).body()

    override suspend fun deleteChat(id: String) {
        ensureOk(httpClient.delete(api("/chats/$id")) { auth() })
    }

    override suspend fun messages(chatId: String): List<Message> =
        ensureOk(httpClient.get(api("/chats/$chatId/messages")) { auth() }).body()

    override suspend fun stop(chatId: String) {
        ensureOk(httpClient.post(api("/chats/$chatId/stop")) { auth() })
    }

    override suspend fun updateChat(id: String, request: UpdateChatRequest): Chat =
        ensureOk(httpClient.patch(api("/chats/$id")) { auth(); jsonBody(request) }).body()

    // ---- Account ----------------------------------------------------------

    override suspend fun changePassword(request: ChangePasswordRequest) {
        ensureOk(httpClient.put(api("/auth/password")) { auth(); jsonBody(request) })
    }

    override suspend fun updatePreferences(theme: Theme): User =
        ensureOk(
            httpClient.put(api("/auth/preferences")) { auth(); jsonBody(UpdatePreferencesRequest(theme)) },
        ).body<SessionResponse>().user

    // ---- Admin: users -----------------------------------------------------

    override suspend fun listUsers(): List<User> =
        ensureOk(httpClient.get(api("/admin/users")) { auth() }).body()

    override suspend fun createUser(request: CreateUserRequest): User =
        ensureOk(httpClient.post(api("/admin/users")) { auth(); jsonBody(request) }).body()

    override suspend fun updateUser(id: String, request: UpdateUserRequest): User =
        ensureOk(httpClient.patch(api("/admin/users/$id")) { auth(); jsonBody(request) }).body()

    override suspend fun deleteUser(id: String) {
        ensureOk(httpClient.delete(api("/admin/users/$id")) { auth() })
    }

    // ---- Admin: providers -------------------------------------------------

    override suspend fun listProviders(): List<Provider> =
        ensureOk(httpClient.get(api("/admin/providers")) { auth() }).body()

    override suspend fun createProvider(request: CreateProviderRequest): Provider =
        ensureOk(httpClient.post(api("/admin/providers")) { auth(); jsonBody(request) }).body()

    override suspend fun updateProvider(id: String, request: UpdateProviderRequest): Provider =
        ensureOk(httpClient.patch(api("/admin/providers/$id")) { auth(); jsonBody(request) }).body()

    override suspend fun deleteProvider(id: String) {
        ensureOk(httpClient.delete(api("/admin/providers/$id")) { auth() })
    }

    override suspend fun testProvider(id: String): ConnectionTestResponse =
        ensureOk(httpClient.post(api("/admin/providers/$id/test")) { auth() }).body()

    // ---- Admin: models ----------------------------------------------------

    override suspend fun listAdminModels(): List<Model> =
        ensureOk(httpClient.get(api("/admin/models")) { auth() }).body()

    override suspend fun configureModel(providerId: String, request: ConfigureModelRequest): Model =
        ensureOk(httpClient.post(api("/admin/providers/$providerId/models")) { auth(); jsonBody(request) }).body()

    override suspend fun updateModel(id: String, request: UpdateModelRequest): Model =
        ensureOk(httpClient.patch(api("/admin/models/$id")) { auth(); jsonBody(request) }).body()

    override suspend fun deleteModel(id: String) {
        ensureOk(httpClient.delete(api("/admin/models/$id")) { auth() })
    }

    // ---- Admin: server settings & branding --------------------------------

    override suspend fun serverSettings(): ServerSettings =
        ensureOk(httpClient.get(api("/admin/server")) { auth() }).body()

    override suspend fun updateServerSettings(request: UpdateServerSettingsRequest): ServerSettings =
        ensureOk(httpClient.put(api("/admin/server")) { auth(); jsonBody(request) }).body()

    override suspend fun updateBranding(request: UpdateBrandingRequest): Branding =
        ensureOk(httpClient.put(api("/admin/branding")) { auth(); jsonBody(request) }).body()

    // ---- Admin: LM Studio -------------------------------------------------

    override suspend fun lmStudioStatus(): LmStudioStatus =
        ensureOk(httpClient.get(api("/admin/lmstudio/status")) { auth() }).body()

    override suspend fun lmStudioServer(action: String): LmStudioStatus =
        ensureOk(httpClient.post(api("/admin/lmstudio/server")) { auth(); jsonBody(LmStudioServerRequest(action)) }).body()

    override suspend fun lmStudioLoad(model: String, contextLength: Int?): LmStudioStatus =
        ensureOk(httpClient.post(api("/admin/lmstudio/models/load")) { auth(); jsonBody(LmStudioLoadRequest(model, contextLength)) }).body()

    override suspend fun lmStudioUnload(model: String?): LmStudioStatus =
        ensureOk(httpClient.post(api("/admin/lmstudio/models/unload")) { auth(); jsonBody(LmStudioUnloadRequest(model)) }).body()

    // ---- Streaming generation --------------------------------------------

    /**
     * Stream a generation (or regeneration) turn. The server emits SSE blocks
     * whose `data:` payloads are JSON objects with an `event` discriminator
     * (`started` | `delta` | `completed` | `error`). Each parsed event is
     * delivered to [onEvent] on the calling coroutine's context.
     */
    override suspend fun streamGenerate(
        chatId: String,
        request: GenerateRequest,
        regenerate: Boolean,
        onEvent: (StreamEvent) -> Unit,
    ) {
        val endpoint = if (regenerate) "regenerate" else "generate"
        httpClient.preparePost(api("/chats/$chatId/$endpoint")) {
            auth()
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "text/event-stream")
            setBody(request)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                ensureOk(response)
                return@execute
            }
            val channel = response.body<io.ktor.utils.io.ByteReadChannel>()
            val dataLines = StringBuilder()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                when {
                    line.startsWith("data:") -> {
                        if (dataLines.isNotEmpty()) dataLines.append('\n')
                        dataLines.append(line.removePrefix("data:").trimStart())
                    }
                    line.isBlank() -> {
                        val payload = dataLines.toString()
                        dataLines.clear()
                        if (payload.isNotEmpty()) parseStreamEventPayload(payload)?.let(onEvent)
                    }
                    // `event:`, `id:`, `:` comments, etc. are ignored; the JSON
                    // payload carries its own discriminator.
                }
            }
            val tail = dataLines.toString()
            if (tail.isNotEmpty()) parseStreamEventPayload(tail)?.let(onEvent)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        bearerToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    private inline fun <reified T> io.ktor.client.request.HttpRequestBuilder.jsonBody(value: T) {
        contentType(ContentType.Application.Json)
        setBody(value)
    }

    override fun close() = httpClient.close()
}

internal fun parseStreamEventPayload(payload: String): StreamEvent? {
    val obj = runCatching { json.decodeFromString<JsonObject>(payload) }.getOrNull() ?: return null
    return when (obj["event"]?.jsonPrimitive?.content) {
        "started" -> StreamEvent.Started(obj["message_id"]?.jsonPrimitive?.content.orEmpty())
        "delta" -> StreamEvent.Delta(obj["content"]?.jsonPrimitive?.content.orEmpty())
        "completed" -> StreamEvent.Completed(obj["message_id"]?.jsonPrimitive?.content.orEmpty())
        "error" -> StreamEvent.Error(obj["message"]?.jsonPrimitive?.content ?: "Generation failed.")
        else -> null
    }
}
