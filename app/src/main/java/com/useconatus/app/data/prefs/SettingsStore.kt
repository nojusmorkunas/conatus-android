package com.useconatus.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.useconatus.app.data.auth.TokenCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class AppLayout { Thumb, Index, Parity }
enum class ThemeChoice { System, Light, Dark }

/** Where the app opens when it starts fresh. */
enum class StartScreen { Today, Upcoming, Inbox }

data class Appearance(val layout: AppLayout, val theme: ThemeChoice, val start: StartScreen = StartScreen.Today)

/** Who is signed in where. The token itself never leaves [SettingsStore]. */
sealed interface Session {
    /** First launch: no choice made yet. */
    data object NoServer : Session

    /** Used on this phone only. A server can be connected later. */
    data object Local : Session

    data class SignedOut(val serverUrl: String, val notice: String? = null) : Session
    data class SignedIn(val serverUrl: String, val username: String, val name: String?, val timezone: String?) : Session
}

/**
 * Settings, the signed-in account and sync bookkeeping, in DataStore. The device token is stored
 * encrypted by [TokenCipher] and cached in memory once decrypted.
 */
class SettingsStore(private val context: Context, private val cipher: TokenCipher) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val token = stringPreferencesKey("token")
        val username = stringPreferencesKey("username")
        val name = stringPreferencesKey("name")
        val timezone = stringPreferencesKey("timezone")
        val notice = stringPreferencesKey("signed_out_notice")
        // Named after the database version: a version that stores more of each task pulls them all
        // once, so tasks from before it get the new fields too (completion times, Trash).
        val tasksUpdatedSince = stringPreferencesKey("tasks_updated_since_v5")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val pkceVerifier = stringPreferencesKey("pkce_verifier")
        val layout = stringPreferencesKey("layout")
        val theme = stringPreferencesKey("theme")
        val start = stringPreferencesKey("start_screen")
        val local = booleanPreferencesKey("local_only")
        val board = stringSetPreferencesKey("board_projects")
        val weekStart = intPreferencesKey("week_start")
        val userId = stringPreferencesKey("user_id")
        val emailReminders = booleanPreferencesKey("email_reminders")
        val emailRemindersAvailable = booleanPreferencesKey("email_reminders_available")
    }

    @Volatile private var cachedToken: String? = null

    val session: Flow<Session> = context.dataStore.data.map { prefs ->
        val server = prefs[Keys.serverUrl]
        val username = prefs[Keys.username]
        when {
            server == null -> if (prefs[Keys.local] == true) Session.Local else Session.NoServer
            prefs[Keys.token] == null || username == null -> Session.SignedOut(server, prefs[Keys.notice])
            else -> Session.SignedIn(server, username, prefs[Keys.name], prefs[Keys.timezone])
        }
    }

    val appearance: Flow<Appearance> = context.dataStore.data.map { prefs ->
        Appearance(
            AppLayout.entries.firstOrNull { it.name == prefs[Keys.layout] } ?: AppLayout.Thumb,
            ThemeChoice.entries.firstOrNull { it.name == prefs[Keys.theme] } ?: ThemeChoice.System,
            StartScreen.entries.firstOrNull { it.name == prefs[Keys.start] } ?: StartScreen.Today,
        )
    }

    val lastSyncAt: Flow<Long?> = context.dataStore.data.map { it[Keys.lastSyncAt] }

    /** Projects shown as a board. The server has no field for it, so it stays on this phone, like the web's. */
    val boardProjects: Flow<Set<String>> = context.dataStore.data.map { it[Keys.board].orEmpty() }

    suspend fun setBoard(projectId: String, board: Boolean) = context.dataStore.edit {
        val current = it[Keys.board].orEmpty()
        it[Keys.board] = if (board) current + projectId else current - projectId
    }

    suspend fun serverUrl(): String? = context.dataStore.data.first()[Keys.serverUrl]

    /** The account's time zone, or null without a server (the phone's zone applies). */
    suspend fun timezone(): String? = context.dataStore.data.first().let { if (it[Keys.token] == null) null else it[Keys.timezone] }

    suspend fun useLocally() = context.dataStore.edit { it[Keys.local] = true }

    /** The decrypted token, or null when signed out or when it can no longer be decrypted. */
    suspend fun token(): String? {
        cachedToken?.let { return it }
        val stored = context.dataStore.data.first()[Keys.token] ?: return null
        return cipher.decrypt(stored).also { cachedToken = it }
    }

    /** For the OkHttp interceptor, which runs on a background thread and must not suspend. */
    fun cachedToken(): String? = cachedToken

    suspend fun setServer(url: String) = context.dataStore.edit {
        it[Keys.serverUrl] = url
        it.remove(Keys.notice)
    }

    suspend fun forgetServer() = context.dataStore.edit {
        it.remove(Keys.serverUrl)
        it.remove(Keys.notice)
    }

    suspend fun signIn(token: String, username: String, name: String?, timezone: String) {
        cachedToken = token
        context.dataStore.edit {
            it[Keys.token] = cipher.encrypt(token)
            it[Keys.username] = username
            name?.let { n -> it[Keys.name] = n } ?: it.remove(Keys.name)
            it[Keys.timezone] = timezone
            it.remove(Keys.notice)
            it.remove(Keys.pkceVerifier)
            it.remove(Keys.local)
        }
    }

    suspend fun updateUser(id: String, username: String, name: String?, timezone: String, weekStart: Int?, emailReminders: Boolean?, emailRemindersAvailable: Boolean) = context.dataStore.edit {
        it[Keys.userId] = id
        it[Keys.username] = username
        name?.let { n -> it[Keys.name] = n } ?: it.remove(Keys.name)
        it[Keys.timezone] = timezone
        weekStart?.let { w -> it[Keys.weekStart] = w }
        emailReminders?.let { e -> it[Keys.emailReminders] = e }
        it[Keys.emailRemindersAvailable] = emailRemindersAvailable
    }

    /** The signed-in user's id, to tell their own comments from collaborators'. */
    val userId: Flow<String?> = context.dataStore.data.map { if (it[Keys.token] == null) null else it[Keys.userId] }

    /** Whether the server also emails reminders; null without a server, or when it can't send email. */
    val emailReminders: Flow<Boolean?> = context.dataStore.data.map {
        if (it[Keys.token] == null || it[Keys.emailRemindersAvailable] == false) null else it[Keys.emailReminders] ?: true
    }

    suspend fun setEmailReminders(on: Boolean) = context.dataStore.edit { it[Keys.emailReminders] = on }

    /** The account's first day of the week, 0 for Sunday; null without a server (the phone's locale decides). */
    val weekStart: Flow<Int?> = context.dataStore.data.map { if (it[Keys.token] == null) null else it[Keys.weekStart] }

    /** Forgets the account and its sync position. Layout, theme and the server address stay. */
    suspend fun signOut(notice: String? = null) {
        cachedToken = null
        context.dataStore.edit { prefs ->
            listOf<Preferences.Key<*>>(Keys.token, Keys.username, Keys.name, Keys.timezone, Keys.tasksUpdatedSince, Keys.lastSyncAt, Keys.pkceVerifier, Keys.userId, Keys.emailReminders, Keys.emailRemindersAvailable)
                .forEach { prefs.remove(it) }
            notice?.let { prefs[Keys.notice] = it }
        }
    }

    suspend fun tasksUpdatedSince(): String? = context.dataStore.data.first()[Keys.tasksUpdatedSince]

    suspend fun recordSync(tasksUpdatedSince: String, at: Long) = context.dataStore.edit {
        it[Keys.tasksUpdatedSince] = tasksUpdatedSince
        it[Keys.lastSyncAt] = at
    }

    suspend fun setPkceVerifier(verifier: String?) = context.dataStore.edit {
        if (verifier == null) it.remove(Keys.pkceVerifier) else it[Keys.pkceVerifier] = verifier
    }

    suspend fun pkceVerifier(): String? = context.dataStore.data.first()[Keys.pkceVerifier]

    suspend fun setLayout(layout: AppLayout) = context.dataStore.edit { it[Keys.layout] = layout.name }

    suspend fun setTheme(theme: ThemeChoice) = context.dataStore.edit { it[Keys.theme] = theme.name }

    suspend fun setStart(start: StartScreen) = context.dataStore.edit { it[Keys.start] = start.name }
}
