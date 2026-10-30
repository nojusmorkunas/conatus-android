package com.useconatus.app.push

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.useconatus.app.ConatusApplication
import com.useconatus.app.data.net.ConatusApi
import com.useconatus.app.data.net.PushAddressDto
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Instant updates in the Play build. The server gets https://<relay>/<fid> as this phone's push
 * address, fid being its Firebase installation ID, and posts there when something changes
 * elsewhere. The relay (push-relay/ in this repository) turns that into a Firebase data message,
 * and [PushService] syncs.
 */
object Push {
    /** The deployed push-relay Worker, with a trailing slash. Empty turns instant updates off. */
    private const val RELAY = ""

    private val state = MutableStateFlow<String?>("Setting up")

    /** What Settings shows for instant updates. */
    val status: StateFlow<String?> = state.asStateFlow()

    /** Registers with FCM, then gives the server this installation's address. */
    suspend fun register(context: Context, api: ConatusApi) {
        // No google-services.json in the build, so Firebase never started.
        if (RELAY.isEmpty() || FirebaseApp.getApps(context).isEmpty()) {
            state.value = "Not in this build"
            return
        }
        if (GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            state.value = "Off, because this phone has no Google Play services"
            return
        }
        val fid = try {
            FirebaseMessaging.getInstance().register().await()
            FirebaseInstallations.getInstance().id.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.value = "Off until Firebase can be reached"
            return
        }
        registered(api, fid)
    }

    /** Gives the server the address for [fid], which FCM has registered. */
    suspend fun registered(api: ConatusApi, fid: String) {
        if (RELAY.isEmpty()) return
        state.value = try {
            val response = api.setPushAddress(PushAddressDto(RELAY + fid))
            when {
                response.isSuccessful -> "On"
                response.code() == 404 || response.code() == 405 -> "Off, because the server doesn't offer them yet"
                else -> "Off, because the server answered ${response.code()}"
            }
        } catch (_: IOException) {
            "Off until the server can be reached"
        }
    }

    suspend fun unregister(api: ConatusApi) {
        state.value = "Setting up"
        try {
            api.deletePushAddress()
        } catch (_: IOException) {
        }
    }
}

/** Every message is the same wake-up, so it only starts a sync, which finds out what changed. */
// The lint check predates installation IDs: onRegistered replaces onNewToken for them.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class PushService : FirebaseMessagingService() {
    private val container get() = (application as ConatusApplication).container

    override fun onMessageReceived(message: RemoteMessage) = container.onPush()

    // FCM registered this installation, at start or after its ID changed.
    override fun onRegistered(fid: String) {
        container.scope.launch { container.signedInApi()?.let { Push.registered(it, fid) } }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resume(task.result) else continuation.resumeWithException(task.exception ?: IOException("Firebase didn't say why"))
    }
}
