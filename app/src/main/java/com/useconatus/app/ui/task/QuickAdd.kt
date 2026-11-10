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
import androidx.compose.foundation.layout.FlowRow
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
import com.useconatus.app.domain.TokenKind
import com.useconatus.app.domain.parseQuickAddPreview
import com.useconatus.app.ui.components.locale
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

/** What quick add knows about the account: the project and label names it may recognise, and today. */
data class QuickAddContext(val projects: List<String>, val labels: List<String>, val today: LocalDate) {
    /** The same reading the task gets when it's added, so what's highlighted is what's saved. */
    fun preview(text: String) = parseQuickAddPreview(text, today.toString(), projects, labels)

    companion object {
        fun of(state: TodayState) = QuickAddContext(state.projects.map { it.name }, state.labels.map { it.name }, state.today)
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

/** Chips that follow the text as you type: the project, date, repeat and so on the task will get. */
@Composable
fun QuickAddChips(text: String, context: QuickAddContext, inboxName: String, modifier: Modifier = Modifier) {
    val parsed = context.preview(text).parsed
    val locale = locale()
    val use24 = DateFormat.is24HourFormat(LocalContext.current)
    val dark = LocalConatusColors.current.dark
    fun date(value: String?) = value?.let { runCatching { dueLabel(LocalDate.parse(it), context.today, locale) }.getOrNull() }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(Icons.Outlined.Folder, parsed.projectName ?: inboxName)
        Chip(Icons.Outlined.CalendarToday, listOfNotNull(date(parsed.dueDate), parsed.dueTime?.let { timeLabel(it, use24, locale) }).joinToString(" ").ifEmpty { "No date" })
        parsed.recurrence?.let { Chip(Icons.Outlined.Repeat, it.replaceFirstChar(Char::uppercase)) }
        date(parsed.deadlineDate)?.let { Chip(Icons.Outlined.OutlinedFlag, it) }
        parsed.durationMinutes?.let { Chip(Icons.Outlined.Timer, durationLabel(it)) }
        if (parsed.priority < 4) Chip(Icons.Outlined.Flag, "P${parsed.priority}", priorityColor(parsed.priority, dark))
        parsed.labelNames.forEach { Chip(Icons.Outlined.Sell, it) }
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, tint: Color? = null) = Surface(
    shape = MaterialTheme.shapes.small,
    color = Color.Transparent,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
) {
    Row(Modifier.height(32.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/**
 * The quick-add sheet Thumb's add bar and Parity's + button open. It stays open after adding, so
 * several tasks go in one after another. [into] names where a task goes when its text names no
 * project: the Inbox, or the project or section it was opened from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(state: TodayState, into: String? = null, initial: String = "", onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    val context = remember(state.projects, state.labels) { QuickAddContext.of(state) }
    val inbox = into ?: state.projects.firstOrNull { it.isInbox }?.name ?: "Inbox"
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val problem = tooLong(context.preview(value.text).parsed.content, MAX_TITLE)
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val submit = { text: String ->
        if (text.isNotBlank() && problem == null) {
            onAdd(text)
            value = TextFieldValue("")
        }
    }
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
    val shown = maxOf(if (full > 0) imeNow.toFloat() / full else 0f, alone.value)
    val close: () -> Unit = {
        closing = true
        keyboard?.hide()
        scope.launch { alone.animateTo(0f, panelMotion()) }
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        delay(500)
        if (keyboardHeight == 0 && !closing) alone.animateTo(1f, panelMotion())
    }
    // Gone once the keyboard has closed after opening (back closes the keyboard first), or once it has slid away on its own.
    val gone = (full > 0 && imeNow == 0 && imeTarget == 0) || (closing && shown == 0f)
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
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
                QuickAddField(
                    value, { value = it }, context,
                    placeholder = "Task name, e.g. Pay rent tomorrow",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    focus = focus,
                    onSubmit = submit,
                )
                Text(
                    problem ?: "Dates, repeats, #project, @label and p1 to p4 are read from the text.",
                    Modifier.padding(start = 4.dp, top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                    QuickAddChips(value.text, context, inbox, Modifier.weight(1f))
                    Spacer(Modifier.size(8.dp))
                    FilledIconButton({ submit(value.text) }, Modifier.size(48.dp), enabled = value.text.isNotBlank() && problem == null, shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Outlined.ArrowUpward, "Add task")
                    }
                }
            }
        }
    }
}

/** About the length and curve of the keyboard's own animation, for when the panel moves without it. */
private fun <T> panelMotion() = tween<T>(280, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
