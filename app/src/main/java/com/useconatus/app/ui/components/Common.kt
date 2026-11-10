package com.useconatus.app.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImportContacts
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.OutlinedFlag
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.useconatus.app.R
import com.useconatus.app.ui.format.dueColor
import com.useconatus.app.ui.format.dueLabel
import com.useconatus.app.ui.format.durationLabel
import com.useconatus.app.ui.format.timeLabel
import com.useconatus.app.ui.theme.LocalConatusColors
import com.useconatus.app.ui.today.ProjectItem
import com.useconatus.app.ui.today.SyncMark
import com.useconatus.app.ui.today.TaskRow
import java.time.LocalDate
import java.util.Locale
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/** Opens the shared Settings screen from whichever layout is showing. */
val LocalOpenSettings = staticCompositionLocalOf<() -> Unit> { {} }

/** The phone's locale, read so Compose recomposes when it changes. */
// What the server accepts, counted after trimming.
const val MAX_TITLE = 500
const val MAX_DESCRIPTION = 2000
const val MAX_COMMENT = 2000
const val MAX_NAME = 120

/** Why [text] is too long to save, or null when it fits. */
fun tooLong(text: String, limit: Int): String? {
    val over = text.trim().length - limit
    return when {
        over <= 0 -> null
        over == 1 -> "1 character too long. The limit is $limit."
        else -> "$over characters too long. The limit is $limit."
    }
}

private val WEB_ADDRESS = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

/** Where the web addresses are in [text], leaving out punctuation that ends the sentence around them. */
fun linkRanges(text: String): List<IntRange> = WEB_ADDRESS.findAll(text).map {
    it.range.first until it.range.first + it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '\'').length
}.toList()

/** [text] with its web addresses tappable. A tap anywhere else still reaches whatever is under the text. */
@Composable
fun linkified(text: String): AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return remember(text, color) {
        buildAnnotatedString {
            var at = 0
            for (range in linkRanges(text)) {
                append(text, at, range.first)
                withLink(LinkAnnotation.Url(text.substring(range), TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)))) {
                    append(text.substring(range))
                }
                at = range.last + 1
            }
            append(text, at, text.length)
        }
    }
}

@Composable
fun locale(): Locale = LocalConfiguration.current.locales[0]

/** The task's date and time as the row shows them, in the phone's 12 or 24-hour format. */
@Composable
fun dueTexts(row: TaskRow, today: LocalDate): Pair<String?, String?> {
    val locale = locale()
    val use24 = DateFormat.is24HourFormat(LocalContext.current)
    return row.due?.let { dueLabel(it, today, locale) } to row.time?.let { timeLabel(it, use24, locale) }
}

/**
 * The web's task details row: sub-tasks, date, deadline, duration, comments and
 * labels, each a 14dp icon and text-xs, 8dp apart. [showProject] adds the project at the end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskDetails(row: TaskRow, today: LocalDate, showProject: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalConatusColors.current
    val (dueText, timeText) = dueTexts(row, today)
    val project = row.project?.takeIf { showProject && !it.isInbox }
    val any = row.sync != SyncMark.Synced || row.subtasksTotal > 0 || row.due != null || row.deadline != null || row.minutes != null ||
        row.comments > 0 || row.labels.isNotEmpty() || project != null
    if (!any) return
    FlowRow(modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SyncDetail(row.sync)
        if (row.subtasksTotal > 0) Detail(Icons.Outlined.Checklist, "${row.subtasksDone}/${row.subtasksTotal}", colors.muted)
        row.due?.let { due ->
            val color = dueColor(due, today, colors)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (row.recurring) Icon(Icons.Outlined.Repeat, null, Modifier.size(14.dp), tint = color)
                Detail(Icons.Outlined.CalendarToday, listOfNotNull(dueText, timeText).joinToString(" "), color)
            }
        }
        row.deadline?.let { Detail(Icons.Outlined.OutlinedFlag, dueLabel(it, today, locale()), colors.muted) }
        row.minutes?.let { Detail(Icons.Outlined.Timer, durationLabel(it), colors.muted) }
        if (row.comments > 0) Detail(Icons.Outlined.ChatBubbleOutline, "${row.comments}", colors.muted)
        row.labels.forEach { Detail(Icons.Outlined.Sell, it.name, colors.muted, iconTint = it.hue.dot) }
        project?.let { Detail(projectIcon(it.icon), it.name, colors.muted, iconTint = it.hue.dot) }
    }
}

/** Sync state: nothing, unless the user has something to settle. */
@Composable
fun SyncDetail(sync: SyncMark) {
    when (sync) {
        SyncMark.Synced -> {}
        is SyncMark.Failed -> Detail(Icons.Outlined.ErrorOutline, "Couldn't sync", MaterialTheme.colorScheme.error)
        SyncMark.Conflict -> Detail(Icons.Outlined.ErrorOutline, "Changed in two places", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Detail(icon: ImageVector, text: String, color: Color, iconTint: Color = color) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    Icon(icon, null, Modifier.size(14.dp), tint = iconTint)
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
}

/** One of the web's project icons: the name the server stores, what it's called, and the icon. */
data class ProjectIconPreset(val value: String, val label: String, val vector: ImageVector)

/** The web's presets, in its order, with the closest Material icon for each of its Lucide ones. */
val projectIconPresets = listOf(
    ProjectIconPreset("home", "Home", Icons.Outlined.Home),
    ProjectIconPreset("work", "Work", Icons.Outlined.WorkOutline),
    ProjectIconPreset("goals", "Goals", Icons.Outlined.TrackChanges),
    ProjectIconPreset("learning", "Learning", Icons.Outlined.ImportContacts),
    ProjectIconPreset("ideas", "Ideas", Icons.Outlined.Lightbulb),
    ProjectIconPreset("launch", "Launch", Icons.Outlined.RocketLaunch),
    ProjectIconPreset("growth", "Growth", Icons.Outlined.Eco),
    ProjectIconPreset("creative", "Creative", Icons.Outlined.Palette),
    ProjectIconPreset("finance", "Finance", Icons.Outlined.AccountBalanceWallet),
    ProjectIconPreset("tools", "Tools", Icons.Outlined.Build),
    ProjectIconPreset("health", "Health", Icons.Outlined.MonitorHeart),
    ProjectIconPreset("travel", "Travel", Icons.Outlined.Flight),
    ProjectIconPreset("study", "Study", Icons.Outlined.School),
    ProjectIconPreset("fitness", "Fitness", Icons.Outlined.FitnessCenter),
    ProjectIconPreset("music", "Music", Icons.Outlined.MusicNote),
    ProjectIconPreset("shopping", "Shopping", Icons.Outlined.ShoppingCart),
    ProjectIconPreset("gaming", "Gaming", Icons.Outlined.SportsEsports),
)

/** A project's icon as the web draws it: the Inbox's own, its preset, or a folder. */
fun projectIcon(icon: String?, inbox: Boolean = false): ImageVector =
    if (inbox) Icons.Outlined.Inbox else projectIconPresets.firstOrNull { it.value == icon }?.vector ?: Icons.Outlined.Folder

/** A project's icon in its colour. */
@Composable
fun ProjectIcon(project: ProjectItem, size: Dp = 18.dp) = Icon(projectIcon(project.icon, project.isInbox), null, Modifier.size(size), tint = project.hue.dot)

/** The web's section heading: text-sm bold and a muted count. [rule] adds Index's hairline. */
@Composable
fun SectionHeading(title: String, count: Int?, modifier: Modifier = Modifier, rule: Boolean = false) = Row(
    modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp).heightIn(min = 40.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
    count?.let { Text("$it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (rule) HorizontalDivider(Modifier.weight(1f).padding(start = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** One centred sentence for empty lists and screens that aren't built yet. */
@Composable
fun ListMessage(text: String, modifier: Modifier = Modifier) = Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
fun ConatusMark(size: Dp, modifier: Modifier = Modifier) =
    Icon(painterResource(R.drawable.conatus_mark), null, modifier.size(size), tint = MaterialTheme.colorScheme.onSurface)
