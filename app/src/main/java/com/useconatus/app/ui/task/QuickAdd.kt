@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.useconatus.app.ui.task

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.OutlinedFlag
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.useconatus.app.ConatusApplication
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.db.ReminderEntity
import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.data.sync.QuickAddPicks
import com.useconatus.app.data.sync.TaskEdit
import com.useconatus.app.data.sync.quickAddTask
import com.useconatus.app.domain.QuickAddToken
import com.useconatus.app.domain.repeatLabel
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.listSaver
import kotlinx.serialization.json.Json
import com.useconatus.app.data.sync.localTimeToMillis
import com.useconatus.app.domain.TokenKind
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.ui.text.style.TextOverflow
import com.useconatus.app.ui.today.ProjectItem
import java.time.ZoneId
import com.useconatus.app.domain.parseQuickAddPreview
import com.useconatus.app.ui.components.locale
import com.useconatus.app.ui.format.LocalDatePrefs
import com.useconatus.app.ui.format.dueLabel
import com.useconatus.app.ui.format.durationLabel
import com.useconatus.app.ui.format.timeLabel
import com.useconatus.app.ui.theme.LocalConatusColors
import com.useconatus.app.ui.theme.SheetShape
import com.useconatus.app.ui.theme.priorityColor
import com.useconatus.app.ui.theme.sheetColor
import com.useconatus.app.ui.today.TodayState
import java.time.LocalDate
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.useconatus.app.ui.components.MAX_TITLE
import com.useconatus.app.ui.components.tooLong

/** What quick add knows about the account: the project and label names it may recognise, today and the date format. */
data class QuickAddContext(val projects: List<String>, val labels: List<String>, val today: LocalDate, val dateFormat: String) {
    /** The same reading the task gets when it's added, so what's highlighted is what's saved. */
    fun preview(text: String) = parseQuickAddPreview(text, today.toString(), projects, labels, dateFormat)

    companion object {
        fun of(state: TodayState, dateFormat: String) = QuickAddContext(state.projects.map { it.name }, state.labels.map { it.name }, state.today, dateFormat)
    }
}

/** Quick-add highlighting: recognised text gets a soft background; a priority also takes its colour. */
private class Highlight(private val context: QuickAddContext, private val ink: Color, private val dark: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            context.preview(text.text).tokens.forEach { token ->
                val priority = if (token.kind == TokenKind.Priority) token.value.toIntOrNull() else null
                addStyle(SpanStyle(background = ink.copy(alpha = 0.08f), color = priority?.let { priorityColor(it, dark) } ?: Color.Unspecified), token.start, token.end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** The text field every quick add uses. [onSubmit] gets the text when the keyboard's action key is pressed. */
@Composable
fun QuickAddField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    context: QuickAddContext,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    focus: FocusRequester? = null,
    onSubmit: (String) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val dark = LocalConatusColors.current.dark
    val highlight = remember(context, ink, dark) { Highlight(context, ink, dark) }
    BasicTextField(
        value, onValueChange,
        modifier = modifier.then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .semantics { contentDescription = placeholder }
            // A hardware keyboard's Enter adds the task, as the on-screen send key does. Shift+Enter makes a new line.
            .onPreviewKeyEvent { event ->
                val enter = (event.key == Key.Enter || event.key == Key.NumPadEnter) && !event.isShiftPressed
                if (enter && event.type == KeyEventType.KeyDown) onSubmit(value.text)
                enter
            },
        textStyle = style.copy(color = ink),
        cursorBrush = SolidColor(ink),
        visualTransformation = highlight,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Send),
        keyboardActions = KeyboardActions(onDone = { onSubmit(value.text) }, onSend = { onSubmit(value.text) }),
        decorationBox = { inner ->
            Box { if (value.text.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
        },
    )
}

/**
 * What quick add has so far: the text, and the choices tapped for it. A choice takes the phrases of
 * its kind out of the text, and a phrase typed after a choice replaces it, so the two never disagree.
 */
@Stable
internal class QuickAddInput(value: TextFieldValue, picks: QuickAddPicks = QuickAddPicks()) {
    var value by mutableStateOf(value)
        private set
    var picks by mutableStateOf(picks)
        private set

    fun type(context: QuickAddContext, new: TextFieldValue) {
        picks = picks.without(typedKinds(context.preview(value.text).tokens, context.preview(new.text).tokens))
        value = new
    }

    fun pick(context: QuickAddContext, kinds: Set<TokenKind>, change: (QuickAddPicks) -> QuickAddPicks) {
        value = value.without(context.preview(value.text).tokens.filter { it.kind in kinds })
        picks = change(picks)
    }

    fun problem(context: QuickAddContext) = tooLong(context.preview(value.text).parsed.content, MAX_TITLE)

    fun ready(context: QuickAddContext) = value.text.isNotBlank() && problem(context) == null

    /** Hands the task to [onAdd] and starts the next one. The place stays for it; the rest was this task's own. */
    fun submit(context: QuickAddContext, onAdd: (String, QuickAddPicks) -> Unit) {
        if (!ready(context)) return
        onAdd(value.text, picks)
        value = TextFieldValue("")
        picks = QuickAddPicks(projectId = picks.projectId, sectionId = picks.sectionId)
    }

    companion object {
        val Saver = listSaver<QuickAddInput, Any>(
            save = { listOf(it.value.text, it.value.selection.start, it.value.selection.end, Json.encodeToString(QuickAddPicks.serializer(), it.picks)) },
            restore = { QuickAddInput(TextFieldValue(it[0] as String, TextRange(it[1] as Int, it[2] as Int)), Json.decodeFromString(QuickAddPicks.serializer(), it[3] as String)) },
        )
    }
}

@Composable
internal fun rememberQuickAddInput(initial: String) =
    rememberSaveable(saver = QuickAddInput.Saver) { QuickAddInput(TextFieldValue(initial, TextRange(initial.length))) }

/** The kinds of phrase in [after] that [before] didn't have, so typed just now. */
internal fun typedKinds(before: List<QuickAddToken>, after: List<QuickAddToken>): Set<TokenKind> =
    after.filter { new -> before.none { it.kind == new.kind && it.value == new.value } }.mapTo(HashSet()) { it.kind }

/** These picks without the ones that typing [kinds] replaces. Labels and reminders add up instead. */
internal fun QuickAddPicks.without(kinds: Set<TokenKind>): QuickAddPicks {
    if (kinds.isEmpty()) return this
    fun <T> T.unless(kind: TokenKind): T? = takeIf { kind !in kinds }
    return copy(
        edit = edit.copy(
            priority = edit.priority.unless(TokenKind.Priority),
            dueDate = edit.dueDate.unless(TokenKind.Date),
            dueTime = edit.dueTime.unless(TokenKind.Time),
            deadlineDate = edit.deadlineDate.unless(TokenKind.Deadline),
            durationMinutes = edit.durationMinutes.unless(TokenKind.Duration),
            recurrence = edit.recurrence.unless(TokenKind.Recurrence),
        ),
        projectId = projectId.unless(TokenKind.Project),
        sectionId = sectionId.unless(TokenKind.Project),
    )
}

/** The kinds of phrase that type what [this] sets, which a tapped choice takes out of the text. */
internal fun TaskEdit.kinds(): Set<TokenKind> = setOfNotNull(
    TokenKind.Priority.takeIf { priority != null },
    TokenKind.Date.takeIf { dueDate != null },
    TokenKind.Time.takeIf { dueTime != null },
    TokenKind.Deadline.takeIf { deadlineDate != null },
    TokenKind.Duration.takeIf { durationMinutes != null },
    TokenKind.Recurrence.takeIf { recurrence != null },
)

/** This text without the phrases of [tokens], each with the space before it (or after it, at the start). The cursor keeps its place. */
internal fun TextFieldValue.without(tokens: List<QuickAddToken>): TextFieldValue {
    if (tokens.isEmpty()) return this
    var text = text
    var cursor = selection.end
    for (token in tokens.sortedByDescending { it.start }) {
        val before = token.start > 0 && text[token.start - 1].isWhitespace()
        val start = if (before) token.start - 1 else token.start
        val end = if (!before && token.end < text.length && text[token.end].isWhitespace()) token.end + 1 else token.end
        text = text.removeRange(start, end)
        if (cursor > start) cursor -= minOf(cursor, end) - start
    }
    return TextFieldValue(text, TextRange(cursor))
}

/**
 * Everything quick add asks for: the name, and a chip for each thing a task can have. A chip shows what the task will get and
 * opens the editor task details use, so nothing has to be typed into the name. The task goes in [projectId] and [sectionId],
 * or the Inbox, unless a place is typed or picked; [placeChip] false leaves that choice out. [addButton] false leaves adding
 * to the caller's own button.
 */
@Composable
private fun QuickAddForm(
    input: QuickAddInput,
    context: QuickAddContext,
    projects: List<ProjectItem>,
    labels: List<LabelEntity>,
    sections: List<SectionEntity>,
    projectId: String?,
    sectionId: String?,
    focus: FocusRequester,
    placeChip: Boolean = true,
    addButton: Boolean = true,
    onPicking: (Boolean) -> Unit = {},
    onAdd: (String, QuickAddPicks) -> Unit,
) {
    var editor by remember { mutableStateOf<Editor?>(null) }
    val parsed = context.preview(input.value.text).parsed
    val picks = input.picks
    // The task as it would be added now, which the chips show and the editors start from. Its place follows quick add's own rules.
    val home = projectId ?: projects.firstOrNull { it.isInbox }?.id.orEmpty()
    val named = parsed.projectName?.let { name -> projects.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    val place = picks.projectId ?: named?.id ?: home
    val section = if (picks.projectId != null) picks.sectionId else sectionId.takeIf { place == home }
    val draft = quickAddTask(parsed, picks, labels, context.today.toString(), projectId = place, sectionId = section)
    val typedReminder = parsed.reminderAt?.let { localTimeToMillis(it, ZoneId.systemDefault()) }
    val reminders = (listOfNotNull(typedReminder) + picks.reminders).distinct().map { ReminderEntity("", "", it) }
    val problem = input.problem(context)
    LaunchedEffect(editor != null) { onPicking(editor != null) }
    val chips = rememberScrollState()
    // Each new task starts from the first chips.
    LaunchedEffect(input.value.text.isEmpty()) { if (input.value.text.isEmpty()) chips.animateScrollTo(0) }

    val locale = locale()
    val use24 = DateFormat.is24HourFormat(LocalContext.current)
    val dark = LocalConatusColors.current.dark
    fun date(value: String?) = value?.let { runCatching { dueLabel(LocalDate.parse(it), context.today, locale, context.dateFormat) }.getOrNull() }
    Column {
        QuickAddField(
            input.value, { input.type(context, it) }, context,
            placeholder = "Task name, e.g. Pay rent tomorrow",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            focus = focus,
            onSubmit = { input.submit(context, onAdd) },
        )
        problem?.let { Text(it, Modifier.padding(start = 4.dp, top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // One line that scrolls sideways, so the chips cost the height of one. The ones used most come first.
            Row(Modifier.weight(1f).horizontalScroll(chips), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (placeChip) {
                    val name = sections.firstOrNull { it.id == draft.sectionId }?.name ?: projects.firstOrNull { it.id == draft.projectId }?.name ?: "Inbox"
                    Chip(Icons.Outlined.Folder, name, "Project") { editor = Editor.Move }
                }
                Chip(Icons.Outlined.CalendarToday, date(draft.dueDate), "Date") { editor = Editor.Date }
                Chip(Icons.Outlined.Schedule, draft.dueTime?.let { timeLabel(it, use24, locale) }, "Time") { editor = Editor.Time }
                Chip(Icons.Outlined.Flag, "P${draft.priority}".takeIf { draft.priority < 4 }, "Priority", priorityColor(draft.priority, dark)) { editor = Editor.Priority }
                Chip(Icons.Outlined.Sell, labels.filter { it.id in draft.labelIds }.joinToString(", ") { it.name }.ifEmpty { null }, "Labels") { editor = Editor.Labels }
                Chip(Icons.Outlined.Alarm, if (reminders.isEmpty()) null else remindersText(reminders), "Reminders") { editor = Editor.Reminders }
                Chip(Icons.Outlined.Repeat, draft.recurrence?.let(::repeatLabel), "Repeats") { editor = Editor.Repeat }
                Chip(Icons.Outlined.OutlinedFlag, date(draft.deadlineDate), "Deadline") { editor = Editor.Deadline }
                Chip(Icons.Outlined.Timer, draft.durationMinutes?.let(::durationLabel), "Duration") { editor = Editor.Duration }
                Chip(Icons.AutoMirrored.Outlined.Notes, draft.description.lineSequence().firstOrNull()?.ifBlank { null }, "Description") { editor = Editor.Description }
            }
            if (addButton) {
                Spacer(Modifier.size(8.dp))
                FilledIconButton({ input.submit(context, onAdd) }, Modifier.size(48.dp), enabled = input.ready(context), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Outlined.ArrowUpward, "Add task")
                }
            }
        }
    }
    editor?.let {
        val detail = TaskDetail(task = draft, labels = labels, projects = projects, sections = sections, reminders = reminders)
        EditorDialog(
            it, detail, context.today,
            onEdit = { edit -> input.pick(context, edit.kinds()) { p -> p.copy(edit = p.edit.then(edit)) } },
            onLabels = { ids -> input.pick(context, setOf(TokenKind.Label)) { p -> p.copy(labelIds = ids) } },
            onMove = { project, into -> input.pick(context, setOf(TokenKind.Project)) { p -> p.copy(projectId = project, sectionId = into) } },
            onSubtask = { _, _ -> },
            onRemind = { at -> input.pick(context, emptySet()) { p -> p.copy(reminders = p.reminders + at) } },
            // The one the text asks for goes with its phrase.
            onUnremind = { reminder ->
                input.pick(context, if (reminder.remindAt == typedReminder) setOf(TokenKind.Reminder) else emptySet()) { p -> p.copy(reminders = p.reminders - reminder.remindAt) }
            },
        ) { editor = null }
    }
}

/** [value] is what the task will get, and fills the chip; without one the chip says what it's for, more quietly. */
@Composable
private fun Chip(icon: ImageVector, value: String?, name: String, tint: Color? = null, onClick: () -> Unit) = Surface(
    onClick = onClick,
    modifier = Modifier.semantics { contentDescription = if (value == null) name else "$name, $value" },
    shape = MaterialTheme.shapes.small,
    // The same soft fill as a recognised phrase in the text, which shows on the sheet and in a dialog alike.
    color = if (value != null) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
    border = if (value != null) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
) {
    val color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else tint ?: MaterialTheme.colorScheme.onSurface
    Row(Modifier.height(36.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(value ?: name, Modifier.widthIn(max = 160.dp), style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Adds sub-tasks with the same fields as quick add, one after another. They stay in their parent's project, so there's no place to pick. */
@Composable
internal fun SubtaskDialog(detail: TaskDetail, today: LocalDate, onDismiss: () -> Unit, onAdd: (String, QuickAddPicks) -> Unit) {
    val parent = detail.task ?: return
    val format = LocalDatePrefs.current.format
    val context = remember(detail.projects, detail.labels, today, format) { QuickAddContext(detail.projects.map { it.name }, detail.labels.map { it.name }, today, format) }
    val input = rememberQuickAddInput("")
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add sub-task") },
        text = {
            QuickAddForm(
                input, context, detail.projects, detail.labels, detail.sections, parent.projectId, parent.sectionId, focus,
                placeChip = false, addButton = false, onAdd = onAdd,
            )
        },
        confirmButton = { TextButton({ input.submit(context, onAdd) }, enabled = input.ready(context)) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Close") } },
        shape = MaterialTheme.shapes.large,
    )
}

/**
 * The quick-add sheet Thumb's add bar and Parity's + button open. It stays open after adding, so
 * several tasks go in one after another. A task goes in [projectId] and [sectionId], the project or
 * section it was opened from, or else the Inbox, unless a place is typed or picked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(state: TodayState, projectId: String? = null, sectionId: String? = null, initial: String = "", onAdd: (String, QuickAddPicks) -> Unit, onDismiss: () -> Unit) {
    val format = LocalDatePrefs.current.format
    val context = remember(state.projects, state.labels, format) { QuickAddContext.of(state, format) }
    val app = LocalContext.current.applicationContext as ConatusApplication
    val sections by remember { app.container.db.sections().observeOpen() }.collectAsStateWithLifecycle(emptyList())
    val input = rememberQuickAddInput(initial)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    // The panel follows the keyboard as it opens and closes, so the two move as one. It's hidden
    // until the keyboard starts to open, and comes in on its own when none does (a hardware keyboard).
    val density = LocalDensity.current
    val imeNow = WindowInsets.ime.getBottom(density)
    val imeTarget = WindowInsets.imeAnimationTarget.getBottom(density)
    val navigationBar = WindowInsets.navigationBars.getBottom(density)
    var keyboardHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(imeTarget) { if (imeTarget > keyboardHeight) keyboardHeight = imeTarget }
    val full = maxOf(imeTarget, keyboardHeight)
    val alone = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    // An editor's dialog takes the keyboard away. The panel stays put under it, and until the keyboard is back.
    var picking by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val keyboardUp by rememberUpdatedState(imeTarget > 0 && imeNow >= imeTarget)
    LaunchedEffect(picking) {
        if (picking) {
            held = true
        } else if (held) {
            // A dialog with a text field of its own takes its keyboard with it as it goes, so ask once that has begun.
            delay(300)
            focus.requestFocus()
            keyboard?.show()
            snapshotFlow { keyboardUp }.first { it }
            held = false
        }
    }
    val shown = if (held && !closing) 1f else maxOf(if (full > 0) imeNow.toFloat() / full else 0f, alone.value)
    val close: () -> Unit = {
        keyboard?.hide()
        scope.launch {
            if (held) alone.snapTo(1f)
            closing = true
            alone.animateTo(0f, panelMotion())
        }
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        delay(500)
        if (keyboardHeight == 0 && !closing) alone.animateTo(1f, panelMotion())
    }
    // Gone once the keyboard has closed after opening (back closes the keyboard first), or once it has slid away on its own.
    val gone = (!held && full > 0 && imeNow == 0 && imeTarget == 0) || (closing && shown == 0f)
    LaunchedEffect(gone) { if (gone) onDismiss() }
    BackHandler(onBack = close)
    var panelHeight by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = shown }.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = null, indication = null, onClickLabel = "Close", onClick = close),
        )
        Surface(
            shape = SheetShape,
            color = sheetColor(),
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)).fillMaxWidth()
                .onSizeChanged { panelHeight = it.height }
                .graphicsLayer { translationY = (panelHeight + navigationBar) * (1 - shown) },
        ) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
                QuickAddForm(input, context, state.projects, state.labels, sections, projectId, sectionId, focus, onPicking = { picking = it }, onAdd = onAdd)
            }
        }
    }
}

/** About the length and curve of the keyboard's own animation, for when the panel moves without it. */
private fun <T> panelMotion() = tween<T>(280, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
