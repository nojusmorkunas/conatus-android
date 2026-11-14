package com.useconatus.app.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.ui.format.LocalDatePrefs
import com.useconatus.app.ui.format.dueLabel
import com.useconatus.app.data.sync.Completion
import com.useconatus.app.ui.today.TodayState
import com.useconatus.app.ui.today.TodayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * A full page that slides in over the current screen and follows predictive back: the page
 * moves with the back gesture and only closes when the gesture completes.
 */
@Composable
fun SlideOverPage(visible: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    AnimatedVisibility(visible, enter = slideInHorizontally(tween(280)) { it }, exit = slideOutHorizontally(tween(240)) { it }) {
        Box(Modifier.graphicsLayer { translationX = progress * size.width * 0.25f; alpha = 1f - progress * 0.3f }) { content() }
    }
    PredictiveBackHandler(enabled = visible) { events ->
        try {
            events.collect { progress = it.progress }
            onBack()
        } finally {
            progress = 0f
        }
    }
}

/** Pull down on any list to sync. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToSync(vm: TodayViewModel, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    PullToRefreshBox(refreshing, vm::refresh, modifier, content = content)
}

/** Says where a quick-added task went when it doesn't land in Today, the list on screen. */
@Composable
fun AddedMessages(added: Flow<TaskEntity>, state: TodayState, snackbar: SnackbarHostState) {
    val locale = locale()
    val format = LocalDatePrefs.current.format
    val current by rememberUpdatedState(state)
    LaunchedEffect(added) {
        added.collect { task ->
            val state = current
            if (task.dueDate != null && task.dueDate <= state.today.toString()) return@collect
            val project = state.projects.firstOrNull { it.id == task.projectId }?.name ?: "Inbox"
            val due = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { " for ${dueLabel(it, state.today, locale, format)}" }.orEmpty()
            snackbar.showSnackbar("Added “${task.content}” to $project$due")
        }
    }
}

/** Snackbars with Undo after every completion and every delete. Runs in the layout's scope, so it outlives a closing sheet. */
class UndoSnackbars(private val vm: TodayViewModel, private val snackbar: SnackbarHostState, private val scope: CoroutineScope) {
    private suspend fun undoTapped(message: String) = snackbar.showSnackbar(message, "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed

    fun completed(completion: Completion, message: String = "Task completed") = scope.launch { if (undoTapped(message)) vm.undo(completion) }

    fun complete(id: String, message: String = "Task completed", keep: Boolean = false) = scope.launch {
        vm.complete(id, keep)?.let { if (undoTapped(message)) vm.undo(it) }
    }

    fun delete(id: String) = scope.launch {
        val deletion = vm.delete(id) ?: return@launch
        if (undoTapped("Moved to Trash")) vm.undo(deletion)
    }

    fun completeAll(ids: Set<String>) = scope.launch {
        val completions = vm.complete(ids)
        if (completions.isEmpty()) return@launch
        if (undoTapped(if (completions.size == 1) "Task completed" else "${completions.size} tasks completed")) completions.forEach { vm.undo(it) }
    }

    fun deleteAll(ids: Set<String>) = scope.launch {
        val deletions = vm.delete(ids)
        if (deletions.isEmpty()) return@launch
        val n = deletions.size
        if (undoTapped(if (n == 1) "Moved to Trash" else "Moved $n tasks to Trash")) deletions.forEach { vm.undo(it) }
    }
}

@Composable
fun rememberUndoSnackbars(vm: TodayViewModel, snackbar: SnackbarHostState): UndoSnackbars {
    val scope = rememberCoroutineScope()
    return remember(vm, snackbar) { UndoSnackbars(vm, snackbar, scope) }
}
