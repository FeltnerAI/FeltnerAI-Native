package ai.feltner.portal.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A saved FeltnerAI server the Portal can connect to. */
@Serializable
data class ServerProfile(
    val id: String,
    val serverUuid: String,
    val name: String,
    val url: String,
    val allowInsecureHttp: Boolean = false,
    val lastUsedAt: String = "",
)

/**
 * Persists server profiles and their bearer tokens via multiplatform-settings
 * (NSUserDefaults on iOS, SharedPreferences on Android, Preferences on the JVM).
 *
 * NOTE: For production, bearer tokens should live in the platform secure store
 * (Keychain / Keystore), mirroring the Rust Portal's `keyring` usage. This
 * scaffold keeps them in settings for portability; see README.
 */
class ProfileStore(private val settings: Settings = Settings()) {
    private val json = Json { ignoreUnknownKeys = true }

    fun listProfiles(): List<ServerProfile> {
        val raw = settings.getStringOrNull(KEY_PROFILES) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())
    }

    fun saveProfile(profile: ServerProfile): List<ServerProfile> {
        val updated = listProfiles().filterNot { it.id == profile.id } + profile
        settings.putString(KEY_PROFILES, json.encodeToString(updated))
        return updated
    }

    fun deleteProfile(id: String): List<ServerProfile> {
        val target = listProfiles().firstOrNull { it.id == id }
        val updated = listProfiles().filterNot { it.id == id }
        settings.putString(KEY_PROFILES, json.encodeToString(updated))
        target?.let { deleteToken(it.serverUuid) }
        return updated
    }

    /** Last-used theme preference, persisted globally so it applies at launch. */
    fun loadTheme(): String? = settings.getStringOrNull(KEY_THEME)

    fun storeTheme(theme: String) = settings.putString(KEY_THEME, theme)

    fun loadToken(serverUuid: String): String? =
        settings.getStringOrNull(tokenKey(serverUuid))

    fun storeToken(serverUuid: String, token: String) =
        settings.putString(tokenKey(serverUuid), token)

    fun deleteToken(serverUuid: String) =
        settings.remove(tokenKey(serverUuid))

    private fun tokenKey(serverUuid: String) = "$KEY_TOKEN_PREFIX$serverUuid"

    private companion object {
        const val KEY_PROFILES = "feltnerai.profiles"
        const val KEY_TOKEN_PREFIX = "feltnerai.token."
        const val KEY_THEME = "feltnerai.theme"
    }
}
