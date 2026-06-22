package ai.feltner.nativeapp

import ai.feltner.nativeapp.api.ConfigureModelRequest
import ai.feltner.nativeapp.api.CreateProviderRequest
import ai.feltner.nativeapp.api.CreateUserRequest
import ai.feltner.nativeapp.api.FeltnerApiClient
import ai.feltner.nativeapp.api.Role
import ai.feltner.nativeapp.api.Theme
import ai.feltner.nativeapp.api.UpdateBrandingRequest
import ai.feltner.nativeapp.api.UpdateModelRequest
import ai.feltner.nativeapp.api.UpdateProviderRequest
import ai.feltner.nativeapp.api.UpdateServerSettingsRequest
import ai.feltner.nativeapp.api.UpdateUserRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Apple-facing facade over the shared [FeltnerNativeController].
 *
 * The SwiftUI apps replace Compose entirely on Apple platforms, but keep the
 * Compose-free shared core: controller, HTTP client, and persistence.
 * This bridge hands SwiftUI everything it needs without exposing Kotlin Flows
 * or default-argument DTO constructors across the language border:
 *
 *  - [observeState] turns the [NativeAppState] StateFlow into a plain closure callback.
 *  - [admin] returns an ergonomic, suspend-only client for the admin screens.
 *  - the small accessors below expose enum lists and routing in Swift-friendly
 *    shapes so the UI never has to name generated enum cases.
 */
class AppleNativeBridge {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)

    val controller: FeltnerNativeController = FeltnerNativeController(scope)

    /** The current snapshot, for SwiftUI's initial (synchronous) render. */
    fun currentState(): NativeAppState = controller.state.value

    /**
     * Observe [NativeAppState]. The closure fires with the current value immediately
     * and on every subsequent change, always on the main thread. Hold the
     * returned [StateSubscription] for the lifetime of the observer.
     */
    fun observeState(onChange: (NativeAppState) -> Unit): StateSubscription {
        val job = controller.state.onEach { onChange(it) }.launchIn(scope)
        return StateSubscription(job)
    }

    /** Swift-ergonomic admin client bound to the active session, if signed in. */
    fun admin(): AppleAdminClient? = controller.activeClient?.let { AppleAdminClient(it) }

    /** Non-admin / admin sections as plain arrays for SwiftUI iteration. */
    fun userSections(): List<NativeSection> = NativeSection.entries.filter { !it.admin }
    fun adminSections(): List<NativeSection> = NativeSection.entries.filter { it.admin }

    val allThemes: List<Theme> get() = Theme.entries
    val allRoles: List<Role> get() = Role.entries

    /** Routing discriminator for [NativeAppState.screen] ("servers" | "login" | "main"). */
    fun screenKind(state: NativeAppState): String = when (state.screen) {
        is NativeRoute.Servers -> "servers"
        is NativeRoute.Login -> "login"
        is NativeRoute.Main -> "main"
    }

    /** Title shown on the login screen, taken from the server handshake. */
    fun loginTitle(state: NativeAppState): String =
        (state.screen as? NativeRoute.Login)?.handshake?.branding?.server_name ?: state.serverName

    /** Tear down the bridge's coroutine scope. */
    fun dispose() = scope.cancel()
}

/** Opaque, cancelable handle for a Swift-side [AppleNativeBridge.observeState] subscription. */
class StateSubscription internal constructor(private val job: Job) {
    fun cancel() = job.cancel()
}

/**
 * Suspend-only wrapper around [FeltnerApiClient] for the admin screens. Every
 * method takes plain Swift-friendly parameters and builds the request DTOs on
 * the Kotlin side, so SwiftUI never deals with boxed optionals or default args.
 * Kotlin suspend functions surface to Swift as `async throws`.
 */
class AppleAdminClient internal constructor(private val client: FeltnerApiClient) {

    // ---- Users ----
    suspend fun listUsers() = client.listUsers()

    suspend fun createUser(username: String, email: String?, password: String, role: Role) =
        client.createUser(CreateUserRequest(username = username, email = email, password = password, role = role))

    suspend fun updateUser(
        id: String,
        username: String,
        email: String?,
        role: Role,
        disabled: Boolean,
        replacementPassword: String?,
    ) = client.updateUser(
        id,
        UpdateUserRequest(
            username = username,
            email = email,
            role = role,
            disabled = disabled,
            replacement_password = replacementPassword,
        ),
    )

    suspend fun deleteUser(id: String) = client.deleteUser(id)

    // ---- Providers ----
    suspend fun listProviders() = client.listProviders()

    suspend fun createProvider(name: String, baseUrl: String, apiKey: String?, enabled: Boolean) =
        client.createProvider(CreateProviderRequest(name = name, base_url = baseUrl, api_key = apiKey, enabled = enabled))

    suspend fun updateProvider(id: String, name: String, baseUrl: String, apiKey: String?, enabled: Boolean) =
        client.updateProvider(id, UpdateProviderRequest(name = name, base_url = baseUrl, api_key = apiKey, enabled = enabled))

    suspend fun deleteProvider(id: String) = client.deleteProvider(id)

    suspend fun testProvider(id: String) = client.testProvider(id)

    // ---- Models ----
    suspend fun listAdminModels() = client.listAdminModels()

    suspend fun configureModel(
        providerId: String,
        upstreamId: String,
        displayName: String,
        enabled: Boolean,
        isDefault: Boolean,
    ) = client.configureModel(
        providerId,
        ConfigureModelRequest(upstream_id = upstreamId, display_name = displayName, enabled = enabled, is_default = isDefault),
    )

    suspend fun setModelEnabled(id: String, enabled: Boolean) =
        client.updateModel(id, UpdateModelRequest(enabled = enabled))

    suspend fun setModelDefault(id: String) =
        client.updateModel(id, UpdateModelRequest(is_default = true))

    suspend fun deleteModel(id: String) = client.deleteModel(id)

    // ---- LM Studio ----
    suspend fun lmStudioStatus() = client.lmStudioStatus()
    suspend fun lmStudioServer(action: String) = client.lmStudioServer(action)
    suspend fun lmStudioLoad(model: String) = client.lmStudioLoad(model, null)
    suspend fun lmStudioUnload(model: String?) = client.lmStudioUnload(model)

    // ---- Server settings & branding ----
    suspend fun serverSettings() = client.serverSettings()

    suspend fun updateServerSettings(
        publicUrl: String?,
        trustedProxies: List<String>,
        applyStartAtLogin: Boolean,
        startAtLogin: Boolean,
        lmStudioCliPath: String,
    ) = client.updateServerSettings(
        UpdateServerSettingsRequest(
            public_url = publicUrl,
            trusted_proxies = trustedProxies,
            start_at_login = if (applyStartAtLogin) startAtLogin else null,
            lmstudio_cli_path = lmStudioCliPath,
        ),
    )

    suspend fun updateBranding(serverName: String?, accentColor: String?, customCss: String) =
        client.updateBranding(UpdateBrandingRequest(server_name = serverName, accent_color = accentColor, custom_css = customCss))
}
