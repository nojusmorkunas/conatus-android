package com.useconatus.app

import android.app.Application
import android.content.Context
import com.useconatus.app.data.SessionRepository
import com.useconatus.app.data.SyncWorker
import com.useconatus.app.data.db.Conflict
import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.domain.todayIn
import com.useconatus.app.data.sync.ListWrites
import com.useconatus.app.data.sync.Outbox
import com.useconatus.app.data.sync.TaskWrites
import com.useconatus.app.data.sync.NoteWrites
import com.useconatus.app.data.sync.localTimeToMillis
import com.useconatus.app.notify.Reminders
import com.useconatus.app.data.prefs.Session
import com.useconatus.app.push.Push
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import com.useconatus.app.ui.today.days
import com.useconatus.app.widget.TodayWidget
import kotlinx.coroutines.flow.MutableStateFlow
import com.useconatus.app.ui.AppLink
import java.time.ZoneId
import com.useconatus.app.data.auth.TokenCipher
import com.useconatus.app.data.auth.keystoreKey
import com.useconatus.app.data.db.ConatusDatabase
import com.useconatus.app.data.net.ConatusApi
import com.useconatus.app.data.prefs.SettingsStore
import com.useconatus.app.data.sync.SyncRepository
import com.useconatus.app.data.net.toApiProblem
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import com.useconatus.app.data.net.allowsPlainHttp
import java.net.UnknownServiceException
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ConatusApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.reminders.createChannel()
        container.reminders.watch(container.scope)
        publishShortcuts(this)
        // A sign-in from before the hourly sync was scheduled reliably may not have one.
        container.scope.launch { if (container.store.token() != null) SyncWorker.scheduleHourly(this@ConatusApplication) }
        // The server gets this phone's push address at every sign-in and every start, so a server
        // that learned push since, or a lost registration, catches up.
        container.scope.launch {
            container.store.session.map { it is Session.SignedIn }.distinctUntilChanged().filter { it }.collect { container.registerPush() }
        }
        // The widget shows today's tasks, so it's redrawn when they change or the day does.
        container.scope.launch {
            container.days().flatMapLatest { container.db.tasks().observeDueBy(it.toString()) }.debounce(500).collect { TodayWidget().updateAll(this@ConatusApplication) }
        }
    }
}

/** Builds the app's dependencies once. No DI framework: this is the only place objects get wired. */
class AppContainer(private val context: Context) {
    val store = SettingsStore(context, TokenCipher { keystoreKey() })
    val db = ConatusDatabase.create(context)

    // Every request carries the device token, except the few that set their own Authorization.
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val token = store.cachedToken()
            chain.proceed(
                if (token == null || request.header("Authorization") != null) request
                else request.newBuilder().header("Authorization", "Bearer $token").build(),
            )
        }
        // Checked on the address actually connected to, so a name that resolves elsewhere later is caught too.
        .addNetworkInterceptor { chain ->
            val address = chain.connection()?.route()?.socketAddress?.address
            if (!chain.request().isHttps && (address == null || !allowsPlainHttp(address))) {
                throw UnknownServiceException("Plain http is only for servers on the local network")
            }
            chain.proceed(chain.request())
        }
        .build()

    private val apis = ConcurrentHashMap<HttpUrl, ConatusApi>()

    fun apiFor(url: HttpUrl): ConatusApi = apis.getOrPut(url) { ConatusApi.create(url, client) }

    /** The signed-in server's API, with the token decrypted and ready for the interceptor. */
    private suspend fun api(): ConatusApi {
        store.token()
        return apiFor(store.serverUrl()!!.toHttpUrl())
    }

    val session = SessionRepository(context, store, db, ::apiFor)

    val outbox = Outbox(db)

    val sync = SyncRepository(db, outbox, store, ::api, dismissReminders = { reminders.dismiss(it) }) {
        val lost = when (val unsent = db.changes().all().size) {
            0 -> ""
            1 -> " 1 change that hadn't been sent was lost."
            else -> " $unsent changes that hadn't been sent were lost."
        }
        session.signOut("This phone's sign-in was revoked on the server.$lost Sign in again.")
    }

    /** Lives as long as the app's process, for work no screen owns, like keeping the reminder alarm set. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val requestSync: () -> Unit = { scope.launch { SyncWorker.runSoon(context) } }

    /** A push: something changed elsewhere, so sync now. */
    fun onPush() = requestSync()

    /** The server's API while signed in, otherwise null. */
    suspend fun signedInApi(): ConatusApi? = if (store.token() != null) api() else null

    /** Gives the signed-in server this phone's push address, in a build that has push. */
    suspend fun registerPush() {
        signedInApi()?.let { Push.register(context, it) }
    }

    /** Comments and reminders. */
    val notes = NoteWrites(db, connected = { store.token() != null }, requestSync)

    val reminders = Reminders(context, db, notes)

    /** Where a notification, shortcut, the widget or a link asked to go, until the layout showing opens it. */
    val links = MutableStateFlow<AppLink?>(null)

    /** The account's time zone, or the phone's without a server. */
    suspend fun zone(): ZoneId = store.timezone()?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    /** Every write: Room first, then (with a server) the queue and a sync as soon as there's a network. */
    val writes = TaskWrites(db, connected = { store.token() != null }, today = { todayIn(store.timezone()) }, requestSync, dismissReminders = { reminders.dismiss(it) }) { task, at ->
        localTimeToMillis(at, zone())?.let { notes.addReminder(task.id, it) }
    }

    /** Projects, sections and labels, written the same way. */
    val lists = ListWrites(db, connected = { store.token() != null }, requestSync)

    suspend fun discard(change: PendingChange) = outbox.discard(change, api())

    /** An account setting, so it goes to the server straight away rather than through the queue. Null when it worked. */
    suspend fun setEmailReminders(on: Boolean): String? = try {
        api().updateSettings(buildJsonObject { put("emailReminders", on) }.toString().toRequestBody("application/json".toMediaType()))
        store.setEmailReminders(on)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        error.toApiProblem().message
    }

    suspend fun keepMine(conflict: Conflict) {
        outbox.keepMine(conflict)
        requestSync()
    }

    suspend fun keepTheirs(conflict: Conflict) {
        outbox.keepTheirs(conflict)
        requestSync()
    }
}
