@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package ai.feltner.nativeapp

import ai.feltner.nativeapp.api.Message
import ai.feltner.nativeapp.api.Model
import ai.feltner.nativeapp.api.Theme
import ai.feltner.nativeapp.api.User
import ai.feltner.nativeapp.data.ServerProfile
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.free
import kotlinx.cinterop.invoke
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.native.CName

@Serializable
private data class WindowsBridgeState(
    val screen: String,
    val loginProfileId: String? = null,
    val loginServerName: String? = null,
    val profiles: List<ServerProfile>,
    val busy: Boolean,
    val error: String? = null,
    val themePref: Theme,
    val serverName: String,
    val section: String,
    val user: User? = null,
    val models: List<Model>,
    val selectedModelId: String? = null,
    val chats: List<ai.feltner.nativeapp.api.Chat>,
    val activeChatId: String? = null,
    val messages: List<Message>,
    val streaming: Boolean,
    val isAdmin: Boolean,
)

@OptIn(ExperimentalForeignApi::class)
private typealias WindowsStateCallback = CPointer<CFunction<(COpaquePointer?) -> Unit>>

private val bridgeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@OptIn(ExperimentalForeignApi::class)
private class WindowsBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controller = FeltnerNativeController(scope)
    private var callback: WindowsStateCallback? = null
    private var handle: COpaquePointer? = null

    init {
        scope.launch {
            controller.state.collectLatest {
                callback?.let { it(handle) }
            }
        }
    }

    fun attachHandle(handle: COpaquePointer) {
        this.handle = handle
    }

    fun setCallback(callback: WindowsStateCallback?) {
        this.callback = callback
        callback?.let { it(handle) }
    }

    fun stateJson(): String = bridgeJson.encodeToString(controller.state.value.toWindowsBridgeState())

    fun connectToServer(rawUrl: String) = controller.connectToServer(rawUrl)

    fun openSavedProfile(profileId: String) {
        val profile = controller.state.value.profiles.firstOrNull { it.id == profileId }
        if (profile == null) {
            controller.showError("Saved server was not found.")
            return
        }
        controller.openSavedProfile(profile)
    }

    fun deleteProfile(profileId: String) {
        val profile = controller.state.value.profiles.firstOrNull { it.id == profileId }
        if (profile != null) controller.deleteProfile(profile)
    }

    fun login(username: String, password: String) = controller.login(username, password)

    fun logout() = controller.logout()

    fun backToServers() = controller.backToServers()

    fun dismissError() = controller.dismissError()

    fun selectSection(sectionName: String) {
        val section = NativeSection.entries.firstOrNull { it.name == sectionName }
        if (section != null) controller.selectSection(section)
    }

    fun setTheme(themeName: String) {
        val theme = Theme.entries.firstOrNull { it.name == themeName }
        if (theme != null) controller.setTheme(theme)
    }

    fun changePassword(current: String, replacement: String) {
        controller.changePassword(current, replacement) {}
    }

    fun selectModel(modelId: String) = controller.selectModel(modelId)

    fun openChat(chatId: String) = controller.openChat(chatId)

    fun newChat() = controller.newChat()

    fun renameChat(chatId: String, title: String) = controller.renameChat(chatId, title)

    fun deleteChat(chatId: String) = controller.deleteChat(chatId)

    fun send(text: String) = controller.send(text)

    fun regenerate() = controller.regenerate()

    fun stopStreaming() = controller.stopStreaming()

    fun dispose() {
        callback = null
        controller.backToServers()
        scope.cancel()
    }
}

private fun NativeAppState.toWindowsBridgeState(): WindowsBridgeState {
    val login = screen as? NativeRoute.Login
    return WindowsBridgeState(
        screen = when (screen) {
            NativeRoute.Servers -> "SERVERS"
            is NativeRoute.Login -> "LOGIN"
            NativeRoute.Main -> "MAIN"
        },
        loginProfileId = login?.profile?.id,
        loginServerName = login?.handshake?.branding?.server_name,
        profiles = profiles,
        busy = busy,
        error = error,
        themePref = themePref,
        serverName = serverName,
        section = section.name,
        user = user,
        models = models,
        selectedModelId = selectedModelId,
        chats = chats,
        activeChatId = activeChatId,
        messages = messages,
        streaming = streaming,
        isAdmin = isAdmin,
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun bridge(handle: COpaquePointer?): WindowsBridge =
    requireNotNull(handle) { "Windows bridge handle is null." }.asStableRef<WindowsBridge>().get()

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<ByteVar>?.string(): String = this?.toKString().orEmpty()

@OptIn(ExperimentalForeignApi::class)
private fun String.toNativeString(): CPointer<ByteVar> =
    nativeHeap.allocArrayOf(encodeToByteArray() + 0.toByte())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_create")
fun feltnerBridgeCreate(): COpaquePointer {
    val ref = StableRef.create(WindowsBridge())
    val handle = ref.asCPointer()
    ref.get().attachHandle(handle)
    return handle
}

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_dispose")
fun feltnerBridgeDispose(handle: COpaquePointer?) {
    val ref = requireNotNull(handle).asStableRef<WindowsBridge>()
    ref.get().dispose()
    ref.dispose()
}

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_set_state_callback")
fun feltnerBridgeSetStateCallback(handle: COpaquePointer?, callback: WindowsStateCallback?) =
    bridge(handle).setCallback(callback)

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_state_json")
fun feltnerBridgeStateJson(handle: COpaquePointer?): CPointer<ByteVar> =
    bridge(handle).stateJson().toNativeString()

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_free_string")
fun feltnerBridgeFreeString(value: CPointer<ByteVar>?) {
    if (value != null) nativeHeap.free(value)
}

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_connect_to_server")
fun feltnerBridgeConnectToServer(handle: COpaquePointer?, rawUrl: CPointer<ByteVar>?) =
    bridge(handle).connectToServer(rawUrl.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_open_saved_profile")
fun feltnerBridgeOpenSavedProfile(handle: COpaquePointer?, profileId: CPointer<ByteVar>?) =
    bridge(handle).openSavedProfile(profileId.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_delete_profile")
fun feltnerBridgeDeleteProfile(handle: COpaquePointer?, profileId: CPointer<ByteVar>?) =
    bridge(handle).deleteProfile(profileId.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_login")
fun feltnerBridgeLogin(handle: COpaquePointer?, username: CPointer<ByteVar>?, password: CPointer<ByteVar>?) =
    bridge(handle).login(username.string(), password.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_logout")
fun feltnerBridgeLogout(handle: COpaquePointer?) = bridge(handle).logout()

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_back_to_servers")
fun feltnerBridgeBackToServers(handle: COpaquePointer?) = bridge(handle).backToServers()

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_dismiss_error")
fun feltnerBridgeDismissError(handle: COpaquePointer?) = bridge(handle).dismissError()

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_select_section")
fun feltnerBridgeSelectSection(handle: COpaquePointer?, sectionName: CPointer<ByteVar>?) =
    bridge(handle).selectSection(sectionName.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_set_theme")
fun feltnerBridgeSetTheme(handle: COpaquePointer?, themeName: CPointer<ByteVar>?) =
    bridge(handle).setTheme(themeName.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_change_password")
fun feltnerBridgeChangePassword(
    handle: COpaquePointer?,
    current: CPointer<ByteVar>?,
    replacement: CPointer<ByteVar>?,
) = bridge(handle).changePassword(current.string(), replacement.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_select_model")
fun feltnerBridgeSelectModel(handle: COpaquePointer?, modelId: CPointer<ByteVar>?) =
    bridge(handle).selectModel(modelId.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_open_chat")
fun feltnerBridgeOpenChat(handle: COpaquePointer?, chatId: CPointer<ByteVar>?) =
    bridge(handle).openChat(chatId.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_new_chat")
fun feltnerBridgeNewChat(handle: COpaquePointer?) = bridge(handle).newChat()

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_rename_chat")
fun feltnerBridgeRenameChat(handle: COpaquePointer?, chatId: CPointer<ByteVar>?, title: CPointer<ByteVar>?) =
    bridge(handle).renameChat(chatId.string(), title.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_delete_chat")
fun feltnerBridgeDeleteChat(handle: COpaquePointer?, chatId: CPointer<ByteVar>?) =
    bridge(handle).deleteChat(chatId.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_send")
fun feltnerBridgeSend(handle: COpaquePointer?, text: CPointer<ByteVar>?) =
    bridge(handle).send(text.string())

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_regenerate")
fun feltnerBridgeRegenerate(handle: COpaquePointer?) = bridge(handle).regenerate()

@OptIn(ExperimentalForeignApi::class)
@CName("feltner_bridge_stop_streaming")
fun feltnerBridgeStopStreaming(handle: COpaquePointer?) = bridge(handle).stopStreaming()
