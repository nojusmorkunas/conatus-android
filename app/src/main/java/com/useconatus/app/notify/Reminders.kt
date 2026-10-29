package com.useconatus.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.useconatus.app.ConatusApplication
import com.useconatus.app.ui.AppLink
import com.useconatus.app.R
import com.useconatus.app.data.db.ConatusDatabase
import com.useconatus.app.data.db.ReminderEntity
import com.useconatus.app.data.sync.NoteWrites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shows reminders as notifications at their time, from the phone's own copy, so they work offline
 * and without a server. One alarm is set at a time, for the next reminder, and it moves whenever
 * reminders or their tasks change.
 */
class Reminders(private val context: Context, private val db: ConatusDatabase, private val notes: NoteWrites) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Task reminders at the time you set"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Keeps the alarm on the next reminder while the app's process runs. */
    fun watch(scope: CoroutineScope) = scope.launch { db.reminders().observeNext().collect(::schedule) }

    /** Sets the alarm once, for a receiver that may be stopped before [watch] gets going. */
    suspend fun reschedule() = schedule(db.reminders().observeNext().first())

    private fun schedule(next: ReminderEntity?) {
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderReceiver::class.java).setAction(ACTION_SHOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        when {
            next == null -> alarms.cancel(intent)
            exact(context) -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.remindAt, intent)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.remindAt, intent)
        }
    }

    /** Shows every reminder whose time has come, once each. */
    suspend fun showDue() {
        for (reminder in db.reminders().due(System.currentTimeMillis())) {
            val task = db.tasks().get(reminder.taskId) ?: continue
            if (notificationsAllowed(context)) {
                post(reminder, task.id, task.content, db.projects().get(task.projectId)?.name)
                notes.shown(reminder)
            } else {
                // Not shown, so the web's reminder bell should still ring for it; only the alarm moves on.
                db.reminders().setNotified(reminder.id)
            }
        }
    }

    @Suppress("MissingPermission") // notificationsAllowed checked it
    private fun post(reminder: ReminderEntity, taskId: String, title: String, project: String?) {
        val id = reminder.id.hashCode()
        val open = PendingIntent.getActivity(
            context, id, AppLink.intent(context, AppLink.Task(taskId)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val complete = PendingIntent.getBroadcast(
            context, id, Intent(context, ReminderReceiver::class.java).setAction(ACTION_COMPLETE).putExtra(EXTRA_TASK, taskId).putExtra(EXTRA_NOTIFICATION, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.conatus_mark)
            .setContentTitle(title)
            .setContentText(project)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(reminder.remindAt)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Complete", complete)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        /** Without the exact-alarm permission Android can hold a reminder back by a few minutes. */
        fun exact(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        fun notificationsAllowed(context: Context): Boolean = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        const val CHANNEL = "reminders"
        const val ACTION_SHOW = "com.useconatus.app.SHOW_REMINDERS"
        const val ACTION_COMPLETE = "com.useconatus.app.COMPLETE_TASK"
        /** The task Complete completes. */
        const val EXTRA_TASK = "task"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

/** The alarm going off, Complete on a notification, and a restart or update, which clears alarms. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as ConatusApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == Reminders.ACTION_COMPLETE) {
                    intent.getStringExtra(Reminders.EXTRA_TASK)?.let { id -> container.db.tasks().get(id)?.let { container.writes.complete(it) } }
                    NotificationManagerCompat.from(context).cancel(intent.getIntExtra(Reminders.EXTRA_NOTIFICATION, 0))
                } else {
                    container.reminders.showDue()
                }
                container.reminders.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
