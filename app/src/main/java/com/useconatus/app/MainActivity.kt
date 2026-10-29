package com.useconatus.app

import com.useconatus.app.data.prefs.StartScreen
import kotlinx.coroutines.flow.first
import androidx.core.content.pm.ShortcutManagerCompat
import com.useconatus.app.ui.AppLink
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.useconatus.app.ui.ConatusApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container get() = (application as ConatusApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreated activity already handled the intent it was started with.
        if (savedInstanceState == null) {
            handle(intent)
            openStartScreen()
        }
        setContent { ConatusApp(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A fresh start opens the start screen from Settings, unless a link already says where to go. */
    private fun openStartScreen() = lifecycleScope.launch {
        val link = when (container.store.appearance.first().start) {
            StartScreen.Today -> return@launch
            StartScreen.Upcoming -> AppLink.Upcoming
            StartScreen.Inbox -> container.db.projects().inbox()?.let { AppLink.Project(it.id) } ?: return@launch
        }
        container.links.compareAndSet(null, link)
    }

    /** A link into the app (see [AppLink]), or the browser sign-in coming back as com.useconatus.app:/auth?code=… */
    private fun handle(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != AppLink.SCHEME) return
        intent.data = null
        val link = AppLink.parse(uri.path.orEmpty())
        if (link == null) {
            lifecycleScope.launch { container.session.completeBrowserSignIn(uri) }
            return
        }
        container.links.value = link
        // So the launcher can put the shortcuts used most first.
        if (link !is AppLink.Task && link !is AppLink.Project) ShortcutManagerCompat.reportShortcutUsed(this, uri.path.orEmpty().trim('/'))
    }
}
