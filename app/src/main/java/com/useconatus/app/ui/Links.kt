package com.useconatus.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.useconatus.app.MainActivity

/**
 * Somewhere a notification, a shortcut, the widget or a link asks the app to go. Links look like
 * com.useconatus.app:/task/<id>, the scheme the browser sign-in already returns to.
 */
sealed interface AppLink {
    data class Task(val id: String) : AppLink
    data class Project(val id: String) : AppLink
    data object Today : AppLink
    data object Upcoming : AppLink
    data object Search : AppLink

    companion object {
        const val SCHEME = "com.useconatus.app"

        /** The link a path names ("/task/<id>", "/today"), or null for one the app doesn't know, like the sign-in's "/auth". */
        fun parse(path: String): AppLink? {
            val parts = path.trim('/').split('/')
            val id = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            return when (parts.first()) {
                "task" -> id?.let(::Task)
                "project" -> id?.let(::Project)
                "today" -> Today
                "upcoming" -> Upcoming
                "search" -> Search
                else -> null
            }
        }

        fun path(link: AppLink): String = when (link) {
            is Task -> "/task/${link.id}"
            is Project -> "/project/${link.id}"
            Today -> "/today"
            Upcoming -> "/upcoming"
            Search -> "/search"
        }

        /** An intent that opens this build of the app at [link], whichever other builds are installed. */
        fun intent(context: Context, link: AppLink): Intent =
            Intent(Intent.ACTION_VIEW, "$SCHEME:${path(link)}".toUri(), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
