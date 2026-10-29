package com.useconatus.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.useconatus.app.data.prefs.ThemeChoice
import com.useconatus.app.ui.task.QuickAddSheet
import com.useconatus.app.ui.theme.ConatusTheme
import com.useconatus.app.ui.today.TodayState
import com.useconatus.app.ui.today.projectItems
import kotlinx.coroutines.launch

/**
 * Quick add over whatever app is open: text shared to Conatus, the Quick Settings tile, the Add
 * task shortcut and the widget's +. It adds one task, says so, and closes.
 */
class QuickAddActivity : ComponentActivity() {
    private val container get() = (application as ConatusApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_VIEW) ShortcutManagerCompat.reportShortcutUsed(this, "add")
        // A shared page usually comes as its title and its link.
        val shared = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" ")
        setContent {
            val appearance by container.store.appearance.collectAsStateWithLifecycle(initialValue = null)
            val projects by container.projectItems().collectAsStateWithLifecycle(initialValue = emptyList())
            val labels by container.db.labels().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
            val scope = rememberCoroutineScope()
            val theme = appearance?.theme ?: return@setContent
            val dark = when (theme) {
                ThemeChoice.Light -> false
                ThemeChoice.Dark -> true
                ThemeChoice.System -> isSystemInDarkTheme()
            }
            ConatusTheme(dark) {
                QuickAddSheet(TodayState(projects = projects, labels = labels), initial = shared, onAdd = { text ->
                    scope.launch {
                        val task = container.writes.quickAdd(text.trim())
                        Toast.makeText(applicationContext, "Added “${task.content}”", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }, onDismiss = ::finish)
            }
        }
    }
}
