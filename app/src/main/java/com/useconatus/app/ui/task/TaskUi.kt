@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.useconatus.app.ui.task

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.OutlinedFlag
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.useconatus.app.data.db.Conflict
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.data.db.ReminderEntity
import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.data.sync.jsonObjectOf
import com.useconatus.app.data.sync.withFields
import com.useconatus.app.domain.firstOccurrence
import com.useconatus.app.domain.parseRecurrence
import com.useconatus.app.data.sync.TaskEdit
import com.useconatus.app.ui.components.ProjectIcon
import com.useconatus.app.ui.components.locale
import com.useconatus.app.ui.format.dueColor
import com.useconatus.app.ui.format.dueLabel
import com.useconatus.app.ui.format.durationLabel
import com.useconatus.app.ui.format.timeLabel
import com.useconatus.app.ui.theme.LocalConatusColors
import com.useconatus.app.ui.theme.SheetShape
import com.useconatus.app.ui.theme.priorityColor
import com.useconatus.app.ui.theme.sheetColor
import com.useconatus.app.ui.today.ProjectItem
import com.useconatus.app.ui.today.TodayState
import com.useconatus.app.ui.today.inTreeOrder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale
import com.useconatus.app.ui.components.MAX_TITLE
import com.useconatus.app.ui.components.MAX_DESCRIPTION
import com.useconatus.app.ui.components.tooLong

// Task details pieces shared by the three layouts: what can be edited, the editors themselves,
// and how unsynced changes are shown. Each layout lays the properties out its own way.

enum class Editor { Title, Description, Date, Time, Deadline, Duration, Priority, Labels, Repeat, Move, Subtask, Reminders }

/** One editable property, as a value the layouts can show as a chip, a field or a label and value. */
data class Property(val label: String, val value: String, val icon: ImageVector, val editor: Editor?, val color: Color? = null, val set: Boolean = true)

@Composable
fun taskProperties(detail: TaskDetail, today: LocalDate): List<Property> {
    val task = detail.task ?: return emptyList()
    val row = detail.row ?: return emptyList()
    val colors = LocalConatusColors.current
    val locale = locale()
    val use24 = DateFormat.is24HourFormat(LocalContext.current)
    val project = row.project
    return listOfNotNull(
        Property(
            "Date",
            row.due?.let { dueLabel(it, today, locale) } ?: "No date",
            Icons.Outlined.CalendarToday, Editor.Date,
            row.due?.let { dueColor(it, today, colors) }, set = row.due != null,
        ),
        Property("Time", row.time?.let { timeLabel(it, use24, locale) } ?: "No time", Icons.Outlined.Schedule, Editor.Time, set = row.time != null),
        Property("Repeats", task.recurrence?.replaceFirstChar(Char::uppercase) ?: "Doesn't repeat", Icons.Outlined.Repeat, Editor.Repeat, set = task.recurrence != null),
        Property("Deadline", row.deadline?.let { dueLabel(it, today, locale) } ?: "No deadline", Icons.Outlined.OutlinedFlag, Editor.Deadline, set = row.deadline != null),
        Property("Duration", row.minutes?.let(::durationLabel) ?: "No duration", Icons.Outlined.Timer, Editor.Duration, set = row.minutes != null),
        Property("Priority", "Priority ${row.priority}", Icons.Outlined.Flag, Editor.Priority, priorityColor(row.priority, colors.dark).takeIf { row.priority < 4 }),
        Property("Labels", row.labels.joinToString(", ") { it.name }.ifEmpty { "No labels" }, Icons.Outlined.Sell, Editor.Labels, set = row.labels.isNotEmpty()),
        project?.let { Property("Project", it.name, Icons.Outlined.Folder, Editor.Move) },
        // Only worth a line when the project has sections to choose from.
        detail.sections.filter { it.projectId == task.projectId }.takeIf { it.isNotEmpty() }?.let { sections ->
            val section = sections.firstOrNull { it.id == task.sectionId }
            Property("Section", section?.name ?: "No section", Icons.AutoMirrored.Outlined.Notes, Editor.Move, set = section != null)
        },
        Property("Reminders", remindersText(detail.reminders, detail.zone, today), Icons.Outlined.Alarm, Editor.Reminders, set = detail.reminders.isNotEmpty()),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

/** The dialog for one [Editor]. Every choice becomes a [TaskEdit] (or a labels change) that goes through the queue. */
@Composable
fun EditorDialog(
    editor: Editor,
    detail: TaskDetail,
    today: LocalDate,
    onEdit: (TaskEdit) -> Unit,
    onLabels: (List<String>) -> Unit,
    onMove: (projectId: String, sectionId: String?) -> Unit,
    onSubtask: (String) -> Unit,
    onRemind: (Long) -> Unit,
    onUnremind: (ReminderEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val task = detail.task ?: return
    fun done(edit: TaskEdit) {
        onEdit(edit)
        onDismiss()
    }
    // A time or a repeat needs a date; when there's none, today is the natural one.
    val dateIfMissing = if (task.dueDate == null) TaskEdit.Field(today.toString()) else null
    when (editor) {
        Editor.Title -> TextDialog("Task name", task.content, required = true, problem = { tooLong(it, MAX_TITLE) }, onDismiss = onDismiss) { done(TaskEdit(content = it)) }
        Editor.Description -> TextDialog("Description", task.description, required = false, problem = { tooLong(it, MAX_DESCRIPTION) }, onDismiss = onDismiss) { done(TaskEdit(description = it)) }
        Editor.Date -> DateDialog(task.dueDate, "No date", onDismiss, onClear = { done(TaskEdit.noDate) }) { done(TaskEdit(dueDate = TaskEdit.Field(it.toString()))) }
        Editor.Deadline -> DateDialog(task.deadlineDate, "No deadline", onDismiss, onClear = { done(TaskEdit(deadlineDate = TaskEdit.Field(null))) }) {
            done(TaskEdit(deadlineDate = TaskEdit.Field(it.toString())))
        }
        Editor.Time -> TimeDialog(task.dueTime, onDismiss, onClear = { done(TaskEdit(dueTime = TaskEdit.Field(null))) }) {
            done(TaskEdit(dueDate = dateIfMissing, dueTime = TaskEdit.Field(it)))
        }
        Editor.Duration -> ChoiceDialog(
            "Duration",
            listOf(15, 30, 45, 60, 90, 120, 180, null).map { (it?.let(::durationLabel) ?: "No duration") to it },
            task.durationMinutes, onDismiss,
        ) { done(TaskEdit(durationMinutes = TaskEdit.Field(it))) }
        Editor.Priority -> PriorityDialog(task.priority, onDismiss) { done(TaskEdit(priority = it)) }
        Editor.Labels -> LabelsDialog(detail, onDismiss) { onLabels(it); onDismiss() }
        Editor.Repeat -> TextDialog(
            "Repeats", task.recurrence.orEmpty(), required = false,
            hint = "For example every day, every Monday, every 2 weeks or every 15th. Leave empty to stop repeating.",
            problem = { if (it.isBlank() || parseRecurrence(it) != null) null else "Conatus can't read that as a repeat." },
            onDismiss = onDismiss,
        ) { text ->
            // Saved in the server's own wording; a repeat without a date starts on its first day.
            val rule = parseRecurrence(text)
            val start = if (rule != null && task.dueDate == null) TaskEdit.Field(firstOccurrence(rule, today.toString())) else null
            done(TaskEdit(dueDate = start, recurrence = TaskEdit.Field(rule)))
        }
        Editor.Move -> MoveDialog(detail.projects, detail.sections, task.projectId, task.sectionId, onDismiss) { project, section -> onMove(project, section); onDismiss() }
        Editor.Subtask -> TextDialog(
            "Add sub-task", "", required = true,
            hint = "Dates, repeats, @label and p1 to p4 are read from the text, as in quick add.",
            problem = { tooLong(it, MAX_TITLE) },
            onDismiss = onDismiss,
        ) { onSubtask(it); onDismiss() }
        Editor.Reminders -> RemindersDialog(detail, today, onRemind, onUnremind, onDismiss)
    }
}

/** Every project, each followed by its sections, to move tasks to. [projectId] and [sectionId] are where they are now, if they're together. */
@Composable
internal fun MoveDialog(
    projects: List<ProjectItem>,
    sections: List<SectionEntity>,
    projectId: String?,
    sectionId: String?,
    onDismiss: () -> Unit,
    onPick: (projectId: String, sectionId: String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                projects.inTreeOrder().forEach { (project, depth) ->
                    MoveOption(project.name, depth, project, projectId == project.id && sectionId == null) { onPick(project.id, null) }
                    sections.filter { it.projectId == project.id }.forEach { section ->
                        MoveOption(section.name, depth + 1, null, sectionId == section.id) { onPick(project.id, section.id) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun MoveOption(name: String, depth: Int, project: ProjectItem?, selected: Boolean, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).heightIn(min = 48.dp).padding(start = (depth * 20).dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    RadioButton(selected, onClick = null)
    Spacer(Modifier.width(8.dp))
    project?.let { ProjectIcon(it, 18.dp); Spacer(Modifier.width(8.dp)) }
    Text(name, style = if (project != null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun TextDialog(
    title: String,
    initial: String,
    required: Boolean,
    hint: String? = null,
    problem: (String) -> String? = { null },
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val error = problem(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), isError = error != null, shape = MaterialTheme.shapes.medium, colors = fieldColors())
                Text(error ?: hint.orEmpty(), style = MaterialTheme.typography.bodySmall, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton({ onSave(text.trim()) }, enabled = (!required || text.isNotBlank()) && error == null) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
internal fun DateDialog(initial: String?, clearLabel: String?, onDismiss: () -> Unit, onClear: () -> Unit, onPick: (LocalDate) -> Unit) {
    // The picker works in UTC milliseconds; a date at UTC midnight round-trips exactly.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({ state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } }, enabled = state.selectedDateMillis != null) {
                Text("Set")
            }
        },
        dismissButton = {
            Row {
                clearLabel?.let { TextButton(onClear) { Text(it) } }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    ) { DatePicker(state) }
}

@Composable
internal fun TimeDialog(initial: String?, onDismiss: () -> Unit, onClear: () -> Unit, onPick: (String) -> Unit) {
    val start = initial?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time") },
        text = { TimePicker(state) },
        confirmButton = { TextButton({ onPick(String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute)) }) { Text("Set") } },
        dismissButton = {
            Row {
                TextButton(onClear) { Text("No time") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
        shape = MaterialTheme.shapes.large,
    )
}

/** [current] is the priority the tasks have now, if they share one. */
@Composable
internal fun PriorityDialog(current: Int?, onDismiss: () -> Unit, onPick: (Int) -> Unit) =
    ChoiceDialog("Priority", (1..4).map { "Priority $it" to it }, current, onDismiss) { it?.let(onPick) }

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<String, T>>, current: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(value == current, role = Role.RadioButton) { onPick(value) }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(value == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun LabelsDialog(detail: TaskDetail, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var chosen by remember { mutableStateOf(detail.task?.labelIds.orEmpty().toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Labels") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (detail.labels.isEmpty()) Text("You have no labels yet. You can add one where your projects are listed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail.labels.forEach { label ->
                    val on = label.id in chosen
                    Row(
                        Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox) { chosen = if (it) chosen + label.id else chosen - label.id }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(on, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSave(detail.labels.map { it.id }.filter { it in chosen }) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.large,
    )
}

/** Task details lead with a rejected change: what the server said, and the two ways out. */
@Composable
fun FailureNotice(change: PendingChange, onRetry: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text("Couldn't sync", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            }
            Text(change.summary, style = MaterialTheme.typography.bodyMedium)
            Text(change.failure.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onDiscard, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text("Discard") }
                OutlinedButton(onRetry, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text("Retry") }
            }
        }
    }
}

/**
 * The one place sync speaks up in a list: tasks changed here and elsewhere, and changes the
 * server turned down. Nothing is shown while changes are on their way or the phone is offline.
 */
@Composable
fun ChangesNotice(state: TodayState, onReview: () -> Unit) {
    val conflicts = state.conflicts.size
    val failed = state.failed.size
    if (conflicts + failed == 0) return
    val error = MaterialTheme.colorScheme.error
    val text = when {
        failed == 0 -> if (conflicts == 1) "1 task was changed on another device too" else "$conflicts tasks were changed on another device too"
        conflicts == 0 -> if (failed == 1) "1 change couldn't sync" else "$failed changes couldn't sync"
        else -> "${conflicts + failed} changes need a decision"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Review", onClick = onReview).heightIn(min = 48.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = error)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = error)
        Text("Review", style = MaterialTheme.typography.labelLarge, color = error)
    }
}

private val FIELD_NAMES = mapOf(
    "content" to "Name", "description" to "Description", "priority" to "Priority", "dueDate" to "Date", "dueTime" to "Time",
    "deadlineDate" to "Deadline", "durationMinutes" to "Duration", "recurrence" to "Repeats", "labelIds" to "Labels", "completed" to "Done",
)

/** A field of [task] in words, the way task details show it. */
@Composable
private fun fieldText(task: TaskEntity, field: String, labels: List<LabelEntity>, today: LocalDate): String {
    val locale = locale()
    val use24 = DateFormat.is24HourFormat(LocalContext.current)
    fun date(value: String?, none: String) = value?.let { runCatching { dueLabel(LocalDate.parse(it), today, locale) }.getOrNull() ?: it } ?: none
    return when (field) {
        "content" -> task.content
        "description" -> task.description.ifBlank { "No description" }
        "priority" -> "Priority ${task.priority}"
        "dueDate" -> date(task.dueDate, "No date")
        "dueTime" -> task.dueTime?.let { timeLabel(it, use24, locale) } ?: "No time"
        "deadlineDate" -> date(task.deadlineDate, "No deadline")
        "durationMinutes" -> task.durationMinutes?.let(::durationLabel) ?: "No duration"
        "recurrence" -> task.recurrence?.replaceFirstChar(Char::uppercase) ?: "Doesn't repeat"
        "labelIds" -> task.labelIds.mapNotNull { id -> labels.firstOrNull { it.id == id }?.name }.joinToString(", ").ifEmpty { "No labels" }
        "completed" -> if (task.isCompleted) "Completed" else "Open"
        else -> ""
    }
}

/**
 * Both versions of a task changed here and on another device, and the two ways out. Nothing is
 * sent for this task until one is picked; the phone's version shows meanwhile.
 */
@Composable
fun ConflictNotice(
    conflict: Conflict,
    task: TaskEntity?,
    labels: List<LabelEntity>,
    today: LocalDate,
    onKeepMine: () -> Unit,
    onKeepTheirs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.error), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(
                    if (conflict.deleted) "Deleted on another device" else "Changed on another device too",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            task?.let { Text(it.content, style = MaterialTheme.typography.bodyLarge) }
            if (conflict.deleted) {
                Text("You changed this task here after it was deleted elsewhere.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (task != null) {
                val theirs = jsonObjectOf(conflict.theirs)
                val other = task.withFields(theirs)
                theirs.keys.forEach { field ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(FIELD_NAMES[field] ?: field, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("This phone: ${fieldText(task, field, labels, today)}", style = MaterialTheme.typography.bodyMedium)
                        Text("Other device: ${fieldText(other, field, labels, today)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onKeepMine, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text(if (conflict.deleted) "Keep it" else "Keep this phone's")
                }
                OutlinedButton(onKeepTheirs, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text(if (conflict.deleted) "Delete it here too" else "Keep the other")
                }
            }
        }
    }
}

/** Everything sync needs the user for: conflicts first, then changes the server turned down. */
@Composable
fun ChangesSheet(
    state: TodayState,
    onRetry: (PendingChange) -> Unit,
    onDiscard: (PendingChange) -> Unit,
    onKeepMine: (Conflict) -> Unit,
    onKeepTheirs: (Conflict) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = sheetColor(), shape = SheetShape) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Needs a decision", Modifier.padding(horizontal = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            // Once the last one is settled there's nothing left to show.
            if (state.conflicts.isEmpty() && state.failed.isEmpty()) LaunchedEffect(Unit) { onDismiss() }
            state.conflicts.forEach { (conflict, task) ->
                ConflictNotice(conflict, task, state.labels, state.today, { onKeepMine(conflict) }, { onKeepTheirs(conflict) })
            }
            state.failed.forEach { change -> FailureNotice(change, { onRetry(change) }, { onDiscard(change) }) }
            Spacer(Modifier.size(12.dp))
        }
    }
}

/** Quick choices for moving a task to another day; Thumb's left swipe and the Reschedule buttons. */
@Composable
fun RescheduleSheet(today: LocalDate, onPick: (LocalDate?) -> Unit, onPickDate: () -> Unit, onDismiss: () -> Unit) {
    val locale = locale()
    // The coming Saturday, or the one after if today is the weekend.
    val weekend = generateSequence(today.plusDays(1)) { it.plusDays(1) }.first { it.dayOfWeek.value == 6 }
    val options = listOf("Today" to today, "Tomorrow" to today.plusDays(1), "This weekend" to weekend, "Next week" to today.plusDays(7))
    ModalBottomSheet(onDismiss, containerColor = sheetColor(), shape = SheetShape) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Reschedule", Modifier.padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            options.forEach { (label, date) ->
                OptionRow(Icons.Outlined.CalendarToday, label, dueLabel(date, today, locale).takeIf { it != label }) { onPick(date) }
            }
            OptionRow(Icons.Outlined.CalendarToday, "Pick a date", null, onPickDate)
            OptionRow(Icons.AutoMirrored.Outlined.Notes, "No date", null) { onPick(null) }
        }
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, detail: String?, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
) {
    Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Just the date picker, for "Pick a date" from the reschedule sheet. */
@Composable
fun PickDateDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) =
    DateDialog(initial?.toString(), clearLabel = null, onDismiss, onClear = onDismiss, onPick = onPick)
