package com.useconatus.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.useconatus.app.ConatusApplication
import com.useconatus.app.data.auth.Pkce
import com.useconatus.app.data.db.ConatusDatabase
import com.useconatus.app.data.net.ApiProblem
import com.useconatus.app.data.net.CodeTokenRequest
import com.useconatus.app.data.net.ConatusApi
import com.useconatus.app.data.net.PasswordTokenRequest
import com.useconatus.app.data.net.isOnLocalNetwork
import com.useconatus.app.data.net.normalizeServerUrl
import com.useconatus.app.data.net.toApiProblem
import com.useconatus.app.data.prefs.SettingsStore
import com.useconatus.app.data.sync.inbox
import com.useconatus.app.push.Push
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/** Choosing a server, signing in and out. Methods return an error message, or null on success. */
class SessionRepository(
    private val context: Context,
    private val store: SettingsStore,
    private val db: ConatusDatabase,
    private val apiFor: (HttpUrl) -> ConatusApi,
) {
    /** What the server shows on its "Sign in on …?" page and in its list of API tokens. */
    private val deviceName: String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

    /** A browser sign-in finishing in the background, for the sign-in screen to show. */
    val browserBusy = MutableStateFlow(false)
    val browserProblem = MutableStateFlow<String?>(null)

    suspend fun forgetServer() = store.forgetServer()

    /** Starts using the app without a server, with an Inbox of its own. */
    suspend fun useLocally() {
        db.inbox()
        store.useLocally()
    }

    /** Called by MainActivity when the browser hands back com.useconatus.app:/auth. */
    suspend fun completeBrowserSignIn(redirect: Uri) {
        browserBusy.value = true
        browserProblem.value = finishBrowserSignIn(redirect)
        browserBusy.value = false
    }

    /**
     * True when [input] points at a server on the local network and Android 17 or later still
     * needs the user's permission before the app may connect to it.
     */
    /**
     * True when Android hasn't let the app use the network yet. Android 17 on Pixels makes INTERNET
     * a runtime permission that has to be asked for; elsewhere it's granted at install.
     */
    fun needsInternetPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED

    suspend fun needsLocalNetworkPermission(input: String): Boolean {
        if (Build.VERSION.SDK_INT < 37) return false
        if (context.checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED) return false
        val url = normalizeServerUrl(input) ?: return false
        return withContext(Dispatchers.IO) { isOnLocalNetwork(url) }
    }

    suspend fun chooseServer(input: String): String? {
        val url = normalizeServerUrl(input) ?: return "That isn't a web address. Try something like conatus.example.com."
        return try {
            val health = apiFor(url).health()
            if (health.status == "ok") {
                store.setServer(url.toString())
                null
            } else {
                "The server answered, but says it isn't ready. Try again in a moment."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: HttpException) {
            when (error.code()) {
                404 -> "There's no Conatus server at that address."
                503 -> "The server is running, but can't reach its database. Try again in a moment."
                else -> error.toApiProblem().message
            }
        } catch (error: Throwable) {
            error.toApiProblem().message
        }
    }

    suspend fun signInWithPassword(username: String, password: String): String? = attempt {
        val token = api().deviceToken(PasswordTokenRequest(username.trim(), password, deviceName)).token
        finishSignIn(token)
    }

    /** The page to open in a Custom Tab. The verifier waits in DataStore in case Android kills the app meanwhile. */
    suspend fun browserSignInUri(): Uri {
        val verifier = Pkce.verifier()
        store.setPkceVerifier(verifier)
        return (store.serverUrl() + "device-login").toUri().buildUpon()
            .appendQueryParameter("device", deviceName)
            .appendQueryParameter("challenge", Pkce.challenge(verifier))
            .build()
    }

    /** Handles com.useconatus.app:/auth?code=… (or ?error=…) coming back from the browser. */
    suspend fun finishBrowserSignIn(redirect: Uri): String? {
        if (redirect.getQueryParameter("error") != null) {
            store.setPkceVerifier(null)
            return "Sign-in was cancelled in the browser."
        }
        val code = redirect.getQueryParameter("code") ?: return "The browser didn't send a sign-in code. Try again."
        val verifier = store.pkceVerifier() ?: return "That sign-in link has already been used. Start again from the app."
        return attempt {
            val token = api().deviceToken(CodeTokenRequest(code, verifier)).token
            finishSignIn(token)
        }
    }

    /** [notice] says why, when the server ended the sign-in. Signing out yourself also revokes the token there. */
    suspend fun signOut(notice: String? = null) {
        // Before the revoke, so the server stops waking this phone even if the revoke doesn't get through.
        if (notice == null && store.token() != null) withTimeoutOrNull(5_000) { Push.unregister(api()) }
        val revoked = notice != null || revokeToken()
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork("sync-now")
        withContext(Dispatchers.IO) { db.clearAllTables() }
        store.signOut(notice ?: "The server didn't confirm the sign-out, so this phone stays listed under API tokens on the web until you revoke it there.".takeIf { !revoked })
    }

    // Offline, or on a server before 1.3.3, this fails and the token stays valid.
    private suspend fun revokeToken(): Boolean = withTimeoutOrNull(5_000) {
        val token = store.token() ?: return@withTimeoutOrNull true
        try {
            api().revokeDeviceToken("Bearer $token").isSuccessful
        } catch (_: IOException) {
            false
        }
    } ?: false

    private suspend fun finishSignIn(token: String) {
        val me = api().context("Bearer $token").user
        // Before the sign-in is stored: storing it closes the sign-in screen, and with it the
        // coroutine running this.
        SyncWorker.scheduleHourly(context)
        store.signIn(token, me.username, me.name, me.timezone)
    }

    private suspend fun api(): ConatusApi = apiFor(store.serverUrl()!!.toHttpUrl())

    private suspend fun attempt(block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        when (val problem = error.toApiProblem()) {
            is ApiProblem.Unauthorized -> when (problem.message) {
                "Invalid credentials" -> "That username and password don't match."
                "Invalid or expired code" -> "That sign-in link expired. Start again from the app."
                else -> problem.message
            }
            else -> problem.message
        }
    }

    companion object {
        const val SYNC_WORK = "sync"

        // Manifest.permission.ACCESS_LOCAL_NETWORK, spelled out because the constant only exists from API 37.
        const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}

/** The hourly background sync, and the one right after each local write. It does nothing while signed out. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        /** The hourly sync. Keeps one that's already scheduled; the worker does nothing while signed out. */
        fun scheduleHourly(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SessionRepository.SYNC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }

        /**
         * Runs a sync as soon as there's a network. A sync that's running gets this one appended, so
         * a write made during it still gets a sync after it. One that's waiting, for a network or to
         * retry after failing, is replaced, so a new write doesn't wait out its retry delay.
         * A waiting sync that starts between the check and the enqueue is cancelled; the
         * queue keeps its changes for this one.
         */
        suspend fun runSoon(context: Context) {
            val work = WorkManager.getInstance(context)
            val running = work.getWorkInfosForUniqueWorkFlow("sync-now").first().any { it.state == WorkInfo.State.RUNNING }
            work.enqueueUniqueWork(
                "sync-now",
                if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }

    override suspend fun doWork(): Result {
        val container = (applicationContext as ConatusApplication).container
        if (container.store.token() == null) return Result.success()
        return when (container.sync.sync()) {
            null, is ApiProblem.Unauthorized -> Result.success()
            is ApiProblem.Offline -> Result.retry()
            is ApiProblem.Server -> Result.retry()
        }
    }
}
