package com.useconatus.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.useconatus.app.AppContainer
import com.useconatus.app.data.prefs.AppLayout
import com.useconatus.app.data.prefs.Appearance
import com.useconatus.app.data.prefs.Session
import com.useconatus.app.data.prefs.ThemeChoice
import com.useconatus.app.ui.components.LocalOpenSettings
import com.useconatus.app.ui.components.LocalTaskSelection
import com.useconatus.app.ui.components.SelectionBar
import com.useconatus.app.ui.components.SlideOverPage
import com.useconatus.app.ui.components.TaskSelection
import com.useconatus.app.ui.layout.IndexLayout
import com.useconatus.app.ui.layout.ParityLayout
import com.useconatus.app.ui.layout.ThumbLayout
import com.useconatus.app.ui.settings.SettingsScreen
import com.useconatus.app.ui.signin.ServerScreen
import com.useconatus.app.ui.signin.SignInScreen
import com.useconatus.app.ui.signin.WelcomeScreen
import com.useconatus.app.ui.theme.ConatusTheme
import com.useconatus.app.ui.today.TodayViewModel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun ConatusApp(container: AppContainer) {
    val session by container.store.session.collectAsStateWithLifecycle(initialValue = null)
    val stored by container.store.appearance.collectAsStateWithLifecycle(initialValue = null)
    val appearance = stored ?: Appearance(AppLayout.Thumb, ThemeChoice.System)
    val dark = when (appearance.theme) {
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
        ThemeChoice.System -> isSystemInDarkTheme()
    }
    // The status bar icons follow the chosen theme, not just the system's.
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(dark) {
        val transparent = android.graphics.Color.TRANSPARENT
        val bars = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(bars, bars)
    }
    val scope = rememberCoroutineScope()
    // Choosing a server, from the welcome screen or later from Settings.
    var connecting by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(session) { if (session is Session.SignedIn || session is Session.SignedOut) connecting = false }
    ConatusTheme(dark) {
        // Nothing until the layout has loaded too, so the first frame isn't another layout,
        // which would also take a link meant for the real one.
        when (val current = session.takeIf { stored != null }) {
            null -> Surface(Modifier.fillMaxSize()) {}
            Session.NoServer -> if (connecting) {
                ServerScreen(container.session, previous = null) { connecting = false }
            } else {
                WelcomeScreen(onLocal = { scope.launch { container.session.useLocally() } }, onConnect = { connecting = true })
            }
            Session.Local -> if (connecting) {
                ServerScreen(container.session, previous = null) { connecting = false }
            } else {
                Main(container, null, appearance) { connecting = true }
            }
            is Session.SignedOut -> {
                val busy by container.session.browserBusy.collectAsStateWithLifecycle()
                val problem by container.session.browserProblem.collectAsStateWithLifecycle()
                SignInScreen(container.session, current.serverUrl, current.notice, busy, problem)
            }
            is Session.SignedIn -> Main(container, current, appearance) {}
        }
    }
}

/** The task screens, with or without a server ([account] is null without one). */
@Composable
private fun Main(container: AppContainer, account: Session.SignedIn?, appearance: Appearance, onConnect: () -> Unit) {
    // Keyed by account, so signing in again starts a fresh first sync.
    val today: TodayViewModel = viewModel(key = account?.let { it.serverUrl + it.username } ?: "local") { TodayViewModel(container) }
    val state by today.state.collectAsStateWithLifecycle()
    val lastSync by container.store.lastSyncAt.collectAsStateWithLifecycle(initialValue = null)
    val unsynced by container.db.changes().observeCount().collectAsStateWithLifecycle(initialValue = 0)
    val emailReminders by container.store.emailReminders.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var settings by rememberSaveable { mutableStateOf(false) }
    // A link opens in the layout, so Settings steps aside.
    val link by container.links.collectAsStateWithLifecycle()
    LaunchedEffect(link) { if (link != null) settings = false }
    // Index keeps completed rows in place; another layout shouldn't inherit them.
    LaunchedEffect(appearance.layout) { today.releaseKept() }
    val selection = remember { TaskSelection() }
    val sections by container.db.sections().observeOpen().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize()) {
        // Settings sits on top of the layout instead of replacing it, so the layout keeps its place.
        // While the selection bar is below, it has the navigation bar's space, not the layout.
        Box(Modifier.weight(1f).then(if (selection.active) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)) {
            CompositionLocalProvider(LocalOpenSettings provides { selection.clear(); settings = true }, LocalTaskSelection provides selection) {
                when (appearance.layout) {
                    AppLayout.Thumb -> ThumbLayout(today, state, container)
                    AppLayout.Index -> IndexLayout(today, state, container)
                    AppLayout.Parity -> ParityLayout(today, state, container)
                }
            }
            SlideOverPage(settings, onBack = { settings = false }) {
                SettingsScreen(
                    appearance,
                    account,
                    lastSync,
                    unsynced,
                    onLayout = { scope.launch { container.store.setLayout(it) } },
                    onTheme = { scope.launch { container.store.setTheme(it) } },
                    onStart = { scope.launch { container.store.setStart(it) } },
                    onConnect = {
                        settings = false
                        onConnect()
                    },
                    onSyncNow = { today.refresh() },
                    onSignOut = {
                        settings = false
                        scope.launch { container.session.signOut() }
                    },
                    emailReminders = emailReminders,
                    onEmailReminders = container::setEmailReminders,
                    onBack = { settings = false },
                )
            }
        }
        if (selection.active) SelectionBar(selection, today, state, sections)
    }

    // Tasks made before the server was connected: the user decides whether they join the account.
    if (account != null) {
        val waiting by container.db.tasks().observeUnsentCount().collectAsStateWithLifecycle(initialValue = 0)
        if (waiting > 0) {
            val host = account.serverUrl.toHttpUrlOrNull()?.host ?: account.serverUrl
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Add this phone's tasks?") },
                text = { Text("The tasks, projects and labels on this phone aren't in your account on $host. Add them to the account, or remove them from this phone.") },
                confirmButton = {
                    TextButton({
                        scope.launch {
                            // Projects, sections and labels first, so the tasks in them have somewhere to go.
                            container.lists.upload()
                            container.writes.upload()
                            container.notes.upload()
                        }
                    }) { Text("Add them") }
                },
                dismissButton = {
                    TextButton({
                        scope.launch {
                            container.lists.dropUnsent()
                            container.writes.dropUnsent()
                            container.notes.dropUnsent()
                        }
                    }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Remove them")
                    }
                },
                shape = MaterialTheme.shapes.large,
            )
        }
    }
}
