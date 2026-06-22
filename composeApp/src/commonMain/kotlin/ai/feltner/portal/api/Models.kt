package ai.feltner.portal.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data-transfer types mirroring FeltnerAI's `/api/v1` JSON contract.
 *
 * Only the subset the Portal client needs is modeled. Field names match the
 * server's snake_case wire format, so no custom [SerialName] mapping is needed
 * beyond enum variants.
 */

@Serializable
enum class Role {
    @SerialName("admin") ADMIN,
    @SerialName("user") USER,
}

@Serializable
enum class Theme {
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
    @SerialName("system") SYSTEM,
}

@Serializable
enum class MessageRole {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
}

@Serializable
enum class MessageStatus {
    @SerialName("complete") COMPLETE,
    @SerialName("streaming") STREAMING,
    @SerialName("canceled") CANCELED,
    @SerialName("error") ERROR,
}

@Serializable
data class Capabilities(
    val chat_streaming: Boolean = false,
    val portal_sessions: Boolean = false,
    val custom_branding: Boolean = false,
)

@Serializable
data class Branding(
    val server_name: String,
    val accent_color: String = "#4f46e5",
    val logo_url: String? = null,
    val favicon_url: String? = null,
    val custom_css_url: String? = null,
)

@Serializable
data class ServerHandshake(
    val server_uuid: String,
    val api_major: Int,
    val version: String,
    val setup_complete: Boolean,
    val public_url: String? = null,
    val capabilities: Capabilities = Capabilities(),
    val branding: Branding,
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String,
    val portal: Boolean,
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val email: String? = null,
    val role: Role,
    val disabled: Boolean = false,
    val must_change_password: Boolean = false,
    val theme: Theme = Theme.SYSTEM,
    val created_at: String,
)

@Serializable
data class SessionResponse(
    val user: User,
    val csrf_token: String? = null,
    val bearer_token: String? = null,
    val expires_at: String,
)

@Serializable
data class Model(
    val id: String,
    val provider_id: String,
    val provider_name: String,
    val upstream_id: String,
    val display_name: String,
    val enabled: Boolean,
    val is_default: Boolean,
)

@Serializable
data class Chat(
    val id: String,
    val title: String,
    val model_id: String? = null,
    val created_at: String,
    val updated_at: String,
)

@Serializable
data class CreateChatRequest(
    val title: String? = null,
    val model_id: String? = null,
)

@Serializable
data class UpdateChatRequest(
    val title: String? = null,
    val model_id: String? = null,
)

@Serializable
data class Message(
    val id: String,
    val chat_id: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val model_id: String? = null,
    val provider_name: String? = null,
    val model_name: String? = null,
    val created_at: String,
)

@Serializable
data class GenerateRequest(
    val request_id: String,
    val content: String,
    val model_id: String? = null,
)

@Serializable
data class ChangePasswordRequest(
    val current_password: String,
    val new_password: String,
)

@Serializable
data class UpdatePreferencesRequest(val theme: Theme)

// ---- Admin: users ---------------------------------------------------------

@Serializable
data class CreateUserRequest(
    val username: String,
    val email: String? = null,
    val password: String,
    val role: Role,
)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val role: Role? = null,
    val disabled: Boolean? = null,
    val replacement_password: String? = null,
)

// ---- Admin: providers -----------------------------------------------------

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val base_url: String,
    val has_api_key: Boolean,
    val additional_header_names: List<String> = emptyList(),
    val enabled: Boolean,
    val created_at: String,
)

@Serializable
data class CreateProviderRequest(
    val name: String,
    val base_url: String,
    val api_key: String? = null,
    val additional_headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true,
)

@Serializable
data class UpdateProviderRequest(
    val name: String? = null,
    val base_url: String? = null,
    val api_key: String? = null,
    val clear_api_key: Boolean? = null,
    val additional_headers: Map<String, String>? = null,
    val enabled: Boolean? = null,
)

@Serializable
data class ConnectionTestResponse(
    val ok: Boolean,
    val message: String,
    val models: List<String> = emptyList(),
)

// ---- Admin: models --------------------------------------------------------

@Serializable
data class ConfigureModelRequest(
    val upstream_id: String,
    val display_name: String,
    val enabled: Boolean = true,
    val is_default: Boolean = false,
)

@Serializable
data class UpdateModelRequest(
    val upstream_id: String? = null,
    val display_name: String? = null,
    val enabled: Boolean? = null,
    val is_default: Boolean? = null,
)

// ---- Admin: server settings & data ---------------------------------------

@Serializable
data class ServerSettings(
    val public_url: String? = null,
    val trusted_proxies: List<String> = emptyList(),
    val data_dir: String = "",
    val startup_supported: Boolean = false,
    val start_at_login: Boolean = false,
    val lmstudio_cli_path: String? = null,
)

@Serializable
data class UpdateServerSettingsRequest(
    val public_url: String? = null,
    val trusted_proxies: List<String>? = null,
    val start_at_login: Boolean? = null,
    val lmstudio_cli_path: String? = null,
)

@Serializable
data class ImportDataResponse(
    val restart_required: Boolean,
    val message: String,
)

// ---- Admin: branding ------------------------------------------------------

@Serializable
data class UpdateBrandingRequest(
    val server_name: String? = null,
    val accent_color: String? = null,
    val custom_css: String? = null,
)

// ---- Admin: LM Studio -----------------------------------------------------

@Serializable
data class LmStudioModel(
    val id: String,
    val display_name: String? = null,
    val size_bytes: Long? = null,
)

@Serializable
data class LmStudioStatus(
    val cli_available: Boolean = false,
    val cli_path: String? = null,
    val version: String? = null,
    val server_running: Boolean = false,
    val server_url: String? = null,
    val downloaded: List<LmStudioModel> = emptyList(),
    val loaded: List<LmStudioModel> = emptyList(),
    val message: String? = null,
)

@Serializable
data class LmStudioServerRequest(val action: String) // "start" | "stop"

@Serializable
data class LmStudioLoadRequest(
    val model: String,
    val context_length: Int? = null,
)

@Serializable
data class LmStudioUnloadRequest(val model: String? = null)

@Serializable
data class ApiError(
    val code: String = "request_failed",
    val message: String = "Request failed",
)

/** Normalized SSE generation events emitted under `/chats/{id}/generate`. */
sealed interface StreamEvent {
    data class Started(val messageId: String) : StreamEvent
    data class Delta(val content: String) : StreamEvent
    data class Completed(val messageId: String) : StreamEvent
    data class Error(val message: String) : StreamEvent
}
