package com.useconatus.app.ui.settings

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.useconatus.app.data.prefs.AppLayout
import com.useconatus.app.data.prefs.Appearance
import com.useconatus.app.data.prefs.Session
import com.useconatus.app.data.prefs.StartScreen
import com.useconatus.app.data.prefs.ThemeChoice
import com.useconatus.app.ui.task.rememberReminderAccess
import kotlinx.coroutines.launch

// What the layouts are called on screen. In code they keep the names they were designed under.
val AppLayout.title get() = when (this) {
    AppLayout.Thumb -> "Comfortable"
    AppLayout.Index -> "Compact"
    AppLayout.Parity -> "Web"
}

private val AppLayout.description get() = when (this) {
    AppLayout.Thumb -> "Big rows and tabs at the bottom, for one hand. Swipe a task to complete or reschedule it, and its details open in a sheet."
    AppLayout.Index -> "A line per task, with every list as a tab at the bottom. Details open in place, under the task."
    AppLayout.Parity -> "Like the web app, with a menu of every list and each task's details on a page of their own."
}

private val ThemeChoice.title get() = when (this) {
    ThemeChoice.System -> "Match phone"
    ThemeChoice.Light -> "Light"
    ThemeChoice.Dark -> "Dark"
}

private enum class SettingsPage { Main, Account }

// The light and dark themes' backgrounds, for the theme swatches.
private val LightSwatch = Color(0xFFFFFFFF)
private val DarkSwatch = Color(0xFF0A0A0A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appearance: Appearance,
    /** Null when the app runs without a server. */
    account: Session.SignedIn?,
    lastSyncAt: Long?,
    /** Changes on this phone the server hasn't confirmed yet. */
    unsynced: Int,
    /** Whether changes made elsewhere arrive at once; null in a build without push. */
    instantUpdates: String?,
    onLayout: (AppLayout) -> Unit,
    onTheme: (ThemeChoice) -> Unit,
    onStart: (StartScreen) -> Unit,
    onConnect: () -> Unit,
    /** Null when it worked, otherwise why not. */
    onSyncNow: suspend () -> String?,
    onSignOut: () -> Unit,
    /** Whether the server also emails reminders; null without a server. */
    emailReminders: Boolean?,
    /** Null when the change went through, otherwise why not. */
    onEmailReminders: suspend (Boolean) -> String?,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    BackHandler(enabled = page != SettingsPage.Main) { page = SettingsPage.Main }
    AnimatedContent(
        page,
        transitionSpec = {
            val forward = targetState != SettingsPage.Main
            slideInHorizontally { if (forward) it else -it / 3 } togetherWith slideOutHorizontally { if (forward) -it / 3 else it }
        },
        label = "settings",
    ) { shown ->
        when (shown) {
            SettingsPage.Main -> MainPage(
                appearance, account, lastSyncAt, emailReminders, onEmailReminders, onLayout, onTheme, onStart, onConnect, onBack,
                onAccount = { page = SettingsPage.Account },
            )
            SettingsPage.Account -> account?.let { AccountPage(it, lastSyncAt, unsynced, instantUpdates, onSyncNow, onSignOut) { page = SettingsPage.Main } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) = Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    },
    content = content,
)

@Composable
private fun MainPage(
    appearance: Appearance,
    account: Session.SignedIn?,
    lastSyncAt: Long?,
    emailReminders: Boolean?,
    onEmailReminders: suspend (Boolean) -> String?,
    onLayout: (AppLayout) -> Unit,
    onTheme: (ThemeChoice) -> Unit,
    onStart: (StartScreen) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    onAccount: () -> Unit,
) = SettingsScaffold("Settings", onBack) { padding ->
    val access = rememberReminderAccess()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var emailProblem by remember { mutableStateOf<String?>(null) }
    var choosingStart by remember { mutableStateOf(false) }
    Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (account != null) AccountCard(account, lastSyncAt, onAccount) else LocalCard(onConnect)

        Heading("Appearance")
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppLayout.entries.forEach { layout ->
                val selected = layout == appearance.layout
                // The sketches show what each layout is like, so a tap switches straight away.
                Column(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.medium).selectable(selected, role = Role.RadioButton) { onLayout(layout) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LayoutPreview(layout, Modifier.fillMaxWidth(0.82f), selected = selected)
                    Text(layout.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(
            appearance.layout.description,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChoice.entries.forEach { theme ->
                ThemeTile(theme, appearance.theme == theme, Modifier.weight(1f)) { onTheme(theme) }
            }
        }
        SettingRow("Opens at", appearance.start.name, onClick = { choosingStart = true })

        Heading("Reminders")
        // Turning them off happens in Android's notification settings, which the switch opens.
        SwitchRow(
            "Notifications",
            if (access.allowed) "Reminders show on this phone" else "Off, so reminders won't show on this phone",
            access.allowed,
        ) { on -> if (on) access.turnOn() else access.turnOff() }
        if (access.allowed && !access.exact) {
            SettingRow("Exact times", "Off, so reminders can come a few minutes late", action = "Allow" to access.allowExact)
        }
        emailReminders?.let { on ->
            SwitchRow("Also by email", emailProblem ?: "The server sends each reminder to your email too.", on, problem = emailProblem != null) { next ->
                scope.launch { emailProblem = onEmailReminders(next) }
            }
        }

        Heading("About")
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
        SettingRow("Conatus ${version.orEmpty()}".trim(), "Free software under the GPL-3.0 licence")
    }
    if (choosingStart) {
        AlertDialog(
            onDismissRequest = { choosingStart = false },
            title = { Text("Opens at") },
            text = {
                Column {
                    StartScreen.entries.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().selectable(option == appearance.start, role = Role.RadioButton) { onStart(option); choosingStart = false }.heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(option == appearance.start, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(option.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ choosingStart = false }) { Text("Cancel") } },
            shape = MaterialTheme.shapes.large,
        )
    }
}

private fun syncedText(lastSyncAt: Long?) =
    lastSyncAt?.let { "Synced " + DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) } ?: "Not synced yet"

private fun String.host() = toUri().host ?: removeSuffix("/")

@Composable
private fun AccountCard(account: Session.SignedIn, lastSyncAt: Long?, onClick: () -> Unit) = Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
) {
    val name = account.name?.takeIf { it.isNotBlank() } ?: account.username
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(account.serverUrl.host(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(syncedText(lastSyncAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LocalCard(onConnect: () -> Unit) = Surface(
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
) {
    Column(Modifier.padding(16.dp)) {
        Text("On this phone only", style = MaterialTheme.typography.titleMedium)
        Text(
            "Connect your Conatus server to sync your tasks with the web app and your other devices.",
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onConnect,
            Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) { Text("Connect a server", color = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun AccountPage(
    account: Session.SignedIn,
    lastSyncAt: Long?,
    unsynced: Int,
    instantUpdates: String?,
    onSyncNow: suspend () -> String?,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) =
    SettingsScaffold("Account", onBack) { padding ->
        var confirmSignOut by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        var syncing by remember { mutableStateOf(false) }
        var syncProblem by remember { mutableStateOf<String?>(null) }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SettingRow("Name", account.name?.takeIf { it.isNotBlank() } ?: account.username)
            SettingRow("Username", account.username)
            SettingRow("Server", account.serverUrl.removeSuffix("/"))
            // The one quiet place sync shows: when it last worked, and what went wrong after tapping Sync now.
            SettingRow(
                "Sync",
                if (syncing) "Syncing" else syncProblem ?: syncedText(lastSyncAt),
                problem = syncProblem != null && !syncing,
                action = "Sync now" to {
                    if (!syncing) scope.launch {
                        syncing = true
                        syncProblem = onSyncNow()
                        syncing = false
                    }
                },
            )
            instantUpdates?.let { SettingRow("Instant updates", it) }
            OutlinedButton(
                { confirmSignOut = true },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).height(48.dp),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Sign out") }
        }
        if (confirmSignOut) {
            AlertDialog(
                onDismissRequest = { confirmSignOut = false },
                title = { Text("Sign out?") },
                text = {
                    val lost = when (unsynced) {
                        0 -> ""
                        1 -> "1 change on this phone hasn't reached the server yet and will be lost. "
                        else -> "$unsynced changes on this phone haven't reached the server yet and will be lost. "
                    }
                    Text(lost + "This removes your tasks from this phone and ends its sign-in on the server.")
                },
                confirmButton = {
                    TextButton({ confirmSignOut = false; onSignOut() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Sign out")
                    }
                },
                dismissButton = { TextButton({ confirmSignOut = false }) { Text("Cancel") } },
                shape = MaterialTheme.shapes.large,
            )
        }
    }

/**
 * A sketch of a layout's Today, in the theme's own colours: lines for tasks, and each layout's
 * navigation where it really is. Drawn in units of a twelfth of its width, so it works at any size.
 */
@Composable
private fun LayoutPreview(layout: AppLayout, modifier: Modifier, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val line = scheme.outlineVariant
    val ink = scheme.onSurfaceVariant
    BoxWithConstraints(
        modifier.aspectRatio(0.5f).clip(RoundedCornerShape(14))
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.onSurface else line, RoundedCornerShape(14))
            .background(scheme.surface)
            .clearAndSetSemantics { contentDescription = "${layout.title} layout" },
    ) {
        val u = maxWidth / 12
        @Composable
        fun bar(width: Float, height: Float = 0.45f, color: Color = line) =
            Box(Modifier.fillMaxWidth(width).height(u * height).clip(RoundedCornerShape(50)).background(color))
        @Composable
        fun box(size: Dp, color: Color, shape: RoundedCornerShape = RoundedCornerShape(30)) = Box(Modifier.size(size).clip(shape).background(color))
        Column(Modifier.fillMaxSize().padding(top = u * 1.2f)) {
            when (layout) {
                AppLayout.Thumb -> {
                    Column(Modifier.padding(horizontal = u), verticalArrangement = Arrangement.spacedBy(u * 0.5f)) {
                        bar(0.5f, 1.1f, ink)
                        bar(0.35f, 0.4f)
                    }
                    Column(Modifier.padding(horizontal = u, vertical = u * 1.2f), verticalArrangement = Arrangement.spacedBy(u * 1.1f)) {
                        repeat(4) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                box(u * 1.1f, line)
                                Column(Modifier.padding(start = u * 0.6f), verticalArrangement = Arrangement.spacedBy(u * 0.3f)) {
                                    bar(if (it % 2 == 0) 0.8f else 0.6f, 0.5f, ink)
                                    bar(0.4f, 0.35f)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().padding(end = u, bottom = u * 0.6f), contentAlignment = Alignment.CenterEnd) {
                        box(u * 1.8f, ink, CircleShape)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = u * 0.9f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(4) { box(u * 0.9f, if (it == 0) ink else line, CircleShape) }
                    }
                }
                AppLayout.Index -> {
                    Row(Modifier.padding(horizontal = u), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { bar(0.45f, 0.8f, ink) }
                        box(u * 0.8f, line, CircleShape)
                        Spacer(Modifier.width(u * 0.5f))
                        box(u * 0.8f, line, CircleShape)
                    }
                    Column(Modifier.padding(horizontal = u, vertical = u), verticalArrangement = Arrangement.spacedBy(u * 0.75f)) {
                        repeat(9) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                box(u * 0.6f, line)
                                Spacer(Modifier.width(u * 0.5f))
                                Box(Modifier.weight(1f)) { bar(if (it % 3 == 0) 0.7f else 0.55f, 0.4f, ink) }
                                Box(Modifier.width(u * 1.6f)) { bar(1f, 0.35f) }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().padding(end = u, bottom = u * 0.6f), contentAlignment = Alignment.CenterEnd) {
                        box(u * 1.8f, ink, RoundedCornerShape(30))
                    }
                    HorizontalDivider(color = line)
                    Row(Modifier.padding(horizontal = u, vertical = u * 0.9f), horizontalArrangement = Arrangement.spacedBy(u * 0.6f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(u * 2f)) { bar(1f, 0.5f, ink) }
                        repeat(3) { Box(Modifier.width(u * 1.6f)) { bar(1f, 0.4f) } }
                    }
                }
                AppLayout.Parity -> Row(Modifier.fillMaxSize()) {
                    // The menu, as it looks when open.
                    Column(Modifier.fillMaxHeight().width(u * 4.2f).background(scheme.surfaceContainerHigh).padding(u * 0.6f), verticalArrangement = Arrangement.spacedBy(u * 0.7f)) {
                        bar(1f, 0.8f, line)
                        repeat(7) { bar(if (it % 2 == 0) 0.85f else 0.65f, 0.4f, if (it == 1) ink else line) }
                    }
                    Column(Modifier.weight(1f).padding(horizontal = u * 0.7f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(u * 1f)) { bar(1f, 0.4f, ink) }
                            Spacer(Modifier.width(u * 0.5f))
                            Box(Modifier.weight(1f)) { bar(0.6f, 0.7f, ink) }
                        }
                        Column(Modifier.padding(vertical = u), verticalArrangement = Arrangement.spacedBy(u * 0.9f)) {
                            repeat(5) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    box(u * 0.8f, line, CircleShape)
                                    Spacer(Modifier.width(u * 0.4f))
                                    Box(Modifier.weight(1f)) { bar(if (it % 2 == 0) 0.85f else 0.65f, 0.45f, ink) }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.fillMaxWidth().padding(bottom = u), contentAlignment = Alignment.CenterEnd) {
                            box(u * 1.6f, ink, CircleShape)
                        }
                    }
                }
            }
        }
    }
}

/** A theme as a small swatch of itself, with its name under it. */
@Composable
private fun ThemeTile(theme: ThemeChoice, selected: Boolean, modifier: Modifier, onClick: () -> Unit) = Surface(
    modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
    shape = MaterialTheme.shapes.medium,
    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
) {
    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val swatch = Modifier.fillMaxWidth().height(28.dp).clip(MaterialTheme.shapes.small).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
        when (theme) {
            ThemeChoice.Light -> Box(swatch.background(LightSwatch))
            ThemeChoice.Dark -> Box(swatch.background(DarkSwatch))
            ThemeChoice.System -> Row(swatch) {
                Box(Modifier.weight(1f).fillMaxHeight().background(LightSwatch))
                Box(Modifier.weight(1f).fillMaxHeight().background(DarkSwatch))
            }
        }
        Text(theme.title, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A setting's name and state, with a button when there's something to do, or the whole row as one when [onClick] is set. */
@Composable
private fun SettingRow(title: String, state: String, problem: Boolean = false, action: Pair<String, () -> Unit>? = null, onClick: (() -> Unit)? = null) = Row(
    Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(state, style = MaterialTheme.typography.bodyMedium, color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    action?.let { (label, act) -> TextButton(act) { Text(label) } }
    if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, state: String, checked: Boolean, problem: Boolean = false, onChange: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).heightIn(min = 56.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            state,
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Switch(checked, onCheckedChange = null)
}

@Composable
private fun Heading(text: String) = Text(
    text,
    Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
