package com.useconatus.app.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.useconatus.app.ConatusApplication
import com.useconatus.app.R
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.domain.groupToday
import com.useconatus.app.ui.today.datePrefs
import com.useconatus.app.ui.today.days
import com.useconatus.app.quickAddIntent
import com.useconatus.app.ui.AppLink
import com.useconatus.app.ui.format.dueLabel
import com.useconatus.app.ui.format.timeLabel
import com.useconatus.app.ui.theme.DarkColors
import com.useconatus.app.ui.theme.DarkConatusColors
import com.useconatus.app.ui.theme.LightColors
import com.useconatus.app.ui.theme.LightConatusColors
import com.useconatus.app.ui.theme.priorityColor
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Locale

private val TASK = ActionParameters.Key<String>("task")

private val foreground = ColorProvider(day = LightColors.onSurface, night = DarkColors.onSurface)
private val muted = ColorProvider(day = LightConatusColors.muted, night = DarkConatusColors.muted)

private data class Shown(val today: LocalDate, val rows: List<TaskEntity>)

/** Today on the home screen: overdue tasks, then today's, each with a checkbox, and + for quick add. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as ConatusApplication).container
        // Live while the widget's session runs, since an update then only recomposes what's here.
        val tasks = container.days().flatMapLatest { today ->
            container.db.tasks().observeDueBy(today.toString()).map { groupToday(it, today).let { g -> Shown(today, g.overdue + g.today) } }
        }
        val first = tasks.first()
        val use24 = DateFormat.is24HourFormat(context)
        val locale = context.resources.configuration.locales[0]
        val format = container.datePrefs().first().format
        provideContent {
            val shown by tasks.collectAsState(first)
            Content(context, shown.rows, shown.today, use24, locale, format)
        }
    }
}

@Composable
private fun Content(context: Context, rows: List<TaskEntity>, today: LocalDate, use24: Boolean, locale: Locale, format: String) = Column(
    GlanceModifier.fillMaxSize().background(ColorProvider(day = LightColors.surface, night = DarkColors.surface)).cornerRadius(16.dp).padding(vertical = 4.dp),
) {
    Row(GlanceModifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.defaultWeight().clickable(actionStartActivity(AppLink.intent(context, AppLink.Today))), verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = TextStyle(color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            if (rows.isNotEmpty()) Text("  ${rows.size}", style = TextStyle(color = muted, fontSize = 14.sp))
        }
        Image(
            ImageProvider(R.drawable.ic_add), "Add task",
            GlanceModifier.size(48.dp).padding(12.dp).clickable(actionStartActivity(quickAddIntent(context))),
            colorFilter = ColorFilter.tint(foreground),
        )
    }
    if (rows.isEmpty()) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing due today.", style = TextStyle(color = muted, fontSize = 14.sp))
        }
    } else {
        LazyColumn {
            items(rows, itemId = { it.id.hashCode().toLong() }) { task -> TaskLine(context, task, today, use24, locale, format) }
        }
    }
}

@Composable
private fun TaskLine(context: Context, task: TaskEntity, today: LocalDate, use24: Boolean, locale: Locale, format: String) {
    val due = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val time = task.dueTime?.let { timeLabel(it, use24, locale) }
    val overdue = due != null && due < today
    // Space is short, so an overdue task shows only its date; today's show their time.
    val dateText = if (overdue) dueLabel(due, today, locale, format) else time.orEmpty()
    val check = ColorProvider(day = priorityColor(task.priority, false), night = priorityColor(task.priority, true))
    Row(
        GlanceModifier.fillMaxWidth().height(44.dp).padding(start = 4.dp, end = 16.dp).clickable(actionStartActivity(AppLink.intent(context, AppLink.Task(task.id)))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<CompleteTask>(actionParametersOf(TASK to task.id)),
            modifier = GlanceModifier.semantics { contentDescription = "Complete ${task.content}" },
            colors = CheckboxDefaults.colors(checkedColor = check, uncheckedColor = check),
        )
        Text(task.content, GlanceModifier.defaultWeight(), style = TextStyle(color = foreground, fontSize = 14.sp), maxLines = 1)
        if (dateText.isNotEmpty()) {
            val color = if (overdue) ColorProvider(day = LightConatusColors.overdue, night = DarkConatusColors.overdue) else ColorProvider(day = LightConatusColors.today, night = DarkConatusColors.today)
            Text(dateText, style = TextStyle(color = color, fontSize = 12.sp))
        }
    }
}

/** A checkbox in the widget: completes the task as the app would, then redraws. */
class CompleteTask : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as ConatusApplication).container
        val id = parameters[TASK] ?: return
        container.db.tasks().get(id)?.let { container.writes.complete(it) }
        TodayWidget().update(context, glanceId)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TodayWidget()
}
