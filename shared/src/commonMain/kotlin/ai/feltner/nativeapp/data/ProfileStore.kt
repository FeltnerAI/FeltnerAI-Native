package ai.feltner.nativeapp.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A saved FeltnerAI server the native app can connect to. */
@Serializable
data class ServerProfile(
    val id: String,
    val serverUuid: String,
    val name: String,
    val url: String,
    val allowInsecureHttp: Boolean = false,
    val lastUsedAt: String = "",
)

interface ProfileRepository {
    fun listProfiles(): List<ServerProfile>
    fun saveProfile(profile: ServerProfile): List<ServerProfile>
    fun deleteProfile(id: String): List<ServerProfile>
    fun loadTheme(): String?
    fun storeTheme(theme: String)
    fun loadToken(serverUuid: String): String?
    fun storeToken(serverUuid: String, token: String)
    fun deleteToken(serverUuid: String)
}

/**
 * Persists server profiles and their bearer tokens via multiplatform-settings
 * (NSUserDefaults on Apple platforms, SharedPreferences on Android, Preferences on the JVM).
 *
 * NOTE: For production, bearer tokens should live in the platform secure store
 * (Keychain / Keystore), mirroring FeltnerAI's secure desktop storage. This
 * scaffold keeps them in settings for portability; see README.
 */
class ProfileStore(private val settings: Settings = Settings()) : ProfileRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override fun listProfiles(): List<ServerProfile> {
        val raw = settings.getStringOrNull(KEY_PROFILES) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
    }

    override fun saveProfile(profile: ServerProfile): List<ServerProfile> {
        val updated = listProfiles().filterNot { it.id == profile.id } + profile
        settings.putString(KEY_PROFILES, json.encodeToString(updated))
        return updated
    }

    override fun deleteProfile(id: String): List<ServerProfile> {
        val target = listProfiles().firstOrNull { it.id == id }
        val updated = listProfiles().filterNot { it.id == id }
        settings.putString(KEY_PROFILES, json.encodeToString(updated))
        target?.let { deleteToken(it.serverUuid) }
        return updated
    }

    /** Last-used theme preference, persisted globally so it applies at launch. */
    override fun loadTheme(): String? = settings.getStringOrNull(KEY_THEME)

    override fun storeTheme(theme: String) = settings.putString(KEY_THEME, theme)

    override fun loadToken(serverUuid: String): String? =
        settings.getStringOrNull(tokenKey(serverUuid))

    override fun storeToken(serverUuid: String, token: String) =
        settings.putString(tokenKey(serverUuid), token)

    override fun deleteToken(serverUuid: String) =
        settings.remove(tokenKey(serverUuid))

    private fun tokenKey(serverUuid: String) = "$KEY_TOKEN_PREFIX$serverUuid"

    private companion object {
        const val KEY_PROFILES = "feltnerai.nativeapp.profiles"
        const val KEY_TOKEN_PREFIX = "feltnerai.nativeapp.token."
        const val KEY_THEME = "feltnerai.nativeapp.theme"
    }
}
