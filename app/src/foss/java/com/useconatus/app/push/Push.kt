package com.useconatus.app.push

import android.content.Context
import com.useconatus.app.data.net.ConatusApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The F-Droid build has no Google code and no instant updates yet: it syncs on open, after each
 * change and every hour. This is where UnifiedPush will register its address.
 */
object Push {
    /** Null, so Settings doesn't mention instant updates. */
    val status: StateFlow<String?> = MutableStateFlow(null)

    @Suppress("UNUSED_PARAMETER")
    suspend fun register(context: Context, api: ConatusApi) {}

    @Suppress("UNUSED_PARAMETER")
    suspend fun unregister(api: ConatusApi) {}
}
