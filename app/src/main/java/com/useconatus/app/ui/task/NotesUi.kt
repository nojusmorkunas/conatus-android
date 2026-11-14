@file:OptIn(ExperimentalMaterial3Api::class)

package com.useconatus.app.ui.task

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.useconatus.app.data.db.CommentEntity
import com.useconatus.app.data.db.ReminderEntity
import com.useconatus.app.notify.Reminders
import com.useconatus.app.ui.components.SectionHeading
import com.useconatus.app.ui.components.locale
import com.useconatus.app.ui.format.LocalDatePrefs
import com.useconatus.app.ui.format.formatDate
import com.useconatus.app.ui.format.timeLabel
import com.useconatus.app.ui.theme.LocalConatusColors
import com.useconatus.app.ui.theme.SheetShape
import com.useconatus.app.ui.theme.sheetColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import com.useconatus.app.ui.components.MAX_COMMENT
import com.useconatus.app.ui.components.tooLong
import com.useconatus.app.ui.project.ConfirmDialog
import com.useconatus.app.ui.components.linkified

// Comments and reminders in task details, shared by the three layouts.

/** One quick reminder choice: what it says and when it rings, in epoch milliseconds. */
data class ReminderChoice(val label: String, val at: Long)

/**
 * The quick choices for a task's reminder: at its time or a little before when it has one, the
 * morning of its day when it has only a date. Times already past or already set are left out.
 */
fun reminderChoices(dueDate: String?, dueTime: String?, zone: ZoneId, now: Long, existing: Set<Long>, timeText: (LocalTime) -> String): List<ReminderChoice> {
    val date = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return emptyList()
    fun at(time: LocalTime) = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    val time = dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val choices = if (time == null) {
        listOf(ReminderChoice("On the day, at ${timeText(LocalTime.of(9, 0))}", at(LocalTime.of(9, 0))))
    } else {
        val due = at(time)
        listOf(
            ReminderChoice("At the time of the task", due),
            ReminderChoice("10 minutes before", due - 10 * 60_000),
            ReminderChoice("30 minutes before", due - 30 * 60_000),
            ReminderChoice("1 hour before", due - 60 * 60_000),
        )
    }
    return choices.filter { it.at > now && it.at !in existing }
}

/** "30.11.2026 14:30" or "30.11.2026 2:30 PM" for an instant, in [zone], as the web shows reminders and comments. */
@Composable
fun momentLabel(millis: Long, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val use24 = DateFormat.is24HourFormat(LocalContext.current)
    return formatDate(at.toLocalDate(), LocalDatePrefs.current.format) + " " + timeLabel(at.toLocalTime().withSecond(0).withNano(0).toString(), use24, locale())
}

/** What the Reminders property says: the next one, and how many more, by the phone's clock. */
@Composable
fun remindersText(reminders: List<ReminderEntity>): String {
    val first = reminders.minByOrNull { it.remindAt } ?: return "No reminders"
    val zone = ZoneId.systemDefault()
    val label = momentLabel(first.remindAt, zone)
    return if (reminders.size == 1) label else "$label and ${reminders.size - 1} more"
}

/** Whether reminders can show, and show on time, with the ways to fix each. */
class ReminderAccess(val allowed: Boolean, val exact: Boolean, val turnOn: () -> Unit, val turnOff: () -> Unit, val allowExact: () -> Unit)

private fun openNotificationSettings(context: Context) =
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))

/** Checked again whenever the app comes back, from system settings for instance. */
@Composable
fun rememberReminderAccess(): ReminderAccess {
    val context = LocalContext.current
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose {}
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checks++ }
    return remember(checks) {
        ReminderAccess(
            allowed = Reminders.notificationsAllowed(context),
            exact = Reminders.exact(context),
            // Android's own prompt the first time; after that only system settings can change it.
            turnOn = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked) {
                    asked = true
                    ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings(context)
                }
            },
            // An app can't take back its own notification permission, so Android's settings do it.
            turnOff = { openNotificationSettings(context) },
            // Only offered where exact alarms need asking for, Android 12 and later.
            allowExact = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                }
            },
        )
    }
}

/** Lists a task's reminders with Remove, offers quick ones and a date and time, and says when notifications can't come. */
@Composable
fun RemindersDialog(detail: TaskDetail, onAdd: (Long) -> Unit, onDelete: (ReminderEntity) -> Unit, onDismiss: () -> Unit) {
    val task = detail.task ?: return
    // Reminders go by the phone's clock, the one the person setting them is looking at, whatever the account's time zone.
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val context = LocalContext.current
    val locale = locale()
    val use24 = DateFormat.is24HourFormat(context)
    val access = rememberReminderAccess()
    fun add(at: Long) {
        onAdd(at)
        if (!access.allowed) access.turnOn()
    }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val choices = reminderChoices(task.dueDate, task.dueTime, zone, System.currentTimeMillis(), detail.reminders.mapTo(HashSet()) { it.remindAt }) {
        timeLabel(it.toString(), use24, locale)
    }
    val colors = LocalConatusColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminders") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!access.allowed) {
                    Notice("Notifications are off for Conatus, so reminders won't show.", "Turn on", access.turnOn)
                } else if (!access.exact) {
                    Notice("Reminders can come a few minutes late.", "Allow exact times", access.allowExact)
                }
                detail.reminders.forEach { reminder ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Alarm, null, Modifier.size(18.dp), tint = colors.muted)
                        Text(momentLabel(reminder.remindAt, zone), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        IconButton({ onDelete(reminder) }) { Icon(Icons.Outlined.Close, "Remove the reminder for ${momentLabel(reminder.remindAt, zone)}") }
                    }
                }
                if (detail.reminders.isNotEmpty()) Spacer(Modifier.size(4.dp))
                choices.forEach { choice ->
                    Choice(choice.label, momentLabel(choice.at, zone)) { add(choice.at) }
                }
                Choice("Pick a date and time", null) { picking = "" }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
        shape = MaterialTheme.shapes.large,
    )
    // A date first, then its time.
    picking?.let { date ->
        if (date.isEmpty()) {
            DateDialog(task.dueDate ?: today.toString(), clearLabel = null, onDismiss = { picking = null }, onClear = { picking = null }) { picking = it.toString() }
        } else {
            TimeDialog(task.dueTime, onDismiss = { picking = null }, onClear = { picking = null }) { time ->
                LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli().let(::add)
                picking = null
            }
        }
    }
}

@Composable
private fun Notice(text: String, action: String, onClick: () -> Unit) = Column(Modifier.padding(bottom = 8.dp)) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    TextButton(onClick, Modifier.padding(start = 0.dp)) { Text(action) }
}

@Composable
private fun Choice(label: String, detail: String?, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/**
 * Comments oldest first, each with when it was written, then a field to add one. The user's own
 * comments have Edit and Delete; a collaborator's can only be read.
 */
@Composable
fun CommentList(
    comments: List<CommentEntity>,
    me: String?,
    zone: ZoneId,
    onAdd: (String) -> Unit,
    onEdit: (CommentEntity, String) -> Unit,
    onDelete: (CommentEntity) -> Unit,
    modifier: Modifier = Modifier,
    heading: Boolean = true,
) {
    var editing by remember { mutableStateOf<CommentEntity?>(null) }
    var deleting by remember { mutableStateOf<CommentEntity?>(null) }
    Column(modifier) {
        if (heading) SectionHeading("Comments", comments.size.takeIf { it > 0 }, Modifier.padding(top = 8.dp))
        comments.forEach { comment ->
            CommentItem(comment, mine = comment.userId == null || comment.userId == me, zone, onEdit = { editing = comment }, onDelete = { deleting = comment })
        }
        CommentField(onAdd)
    }
    deleting?.let { comment ->
        ConfirmDialog("Delete this comment?", "This can't be undone.", "Delete", onConfirm = { onDelete(comment) }, onDismiss = { deleting = null })
    }
    editing?.let { comment ->
        TextDialog("Edit comment", comment.content, required = true, problem = { tooLong(it, MAX_COMMENT) }, onDismiss = { editing = null }) {
            onEdit(comment, it)
            editing = null
        }
    }
}

@Composable
private fun CommentItem(comment: CommentEntity, mine: Boolean, zone: ZoneId, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalConatusColors.current
    var menu by remember { mutableStateOf(false) }
    val written = runCatching { Instant.parse(comment.createdAt).toEpochMilli() }.getOrNull()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, bottom = 6.dp)) {
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(linkified(comment.content), style = MaterialTheme.typography.bodyMedium)
            val edited = comment.updatedAt != comment.createdAt
            written?.let { Text(momentLabel(it, zone) + if (edited) ", edited" else "", style = MaterialTheme.typography.labelSmall, color = colors.muted) }
        }
        if (mine) {
            Box {
                IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "Comment options", tint = colors.muted) }
                DropdownMenu(menu, { menu = false }, shape = MaterialTheme.shapes.medium) {
                    DropdownMenuItem({ Text("Edit") }, { menu = false; onEdit() })
                    DropdownMenuItem({ Text("Delete", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun CommentField(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val problem = tooLong(text, MAX_COMMENT)
    OutlinedTextField(
        text,
        { text = it },
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Write a comment") },
        trailingIcon = {
            IconButton({ onAdd(text); text = "" }, enabled = text.isNotBlank() && problem == null) { Icon(Icons.AutoMirrored.Outlined.Send, "Add comment") }
        },
        isError = problem != null,
        supportingText = problem?.let { { Text(it) } },
        maxLines = 6,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.outline, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Comments in a sheet: Index's Comment button and a project's comments. */
@Composable
fun CommentsSheet(
    title: String,
    comments: List<CommentEntity>,
    me: String?,
    zone: ZoneId,
    onAdd: (String) -> Unit,
    onEdit: (CommentEntity, String) -> Unit,
    onDelete: (CommentEntity) -> Unit,
    onDismiss: () -> Unit,
) = ModalBottomSheet(onDismiss, containerColor = sheetColor(), shape = SheetShape) {
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(bottom = 8.dp)) {
        Text(title, Modifier.padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        if (comments.isEmpty()) {
            Text("No comments yet.", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CommentList(comments, me, zone, onAdd, onEdit, onDelete, heading = false)
    }
}
