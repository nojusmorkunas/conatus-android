package com.useconatus.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.ui.task.MoveDialog
import com.useconatus.app.ui.task.PickDateDialog
import com.useconatus.app.ui.task.PriorityDialog
import com.useconatus.app.ui.task.RescheduleSheet
import com.useconatus.app.ui.today.TodayState
import com.useconatus.app.ui.today.TodayViewModel

/** Tasks picked with a long press, to act on together, as on the web. Empty when nothing is picked. */
@Stable
class TaskSelection {
    var ids by mutableStateOf(emptySet<String>())
        private set
    val active get() = ids.isNotEmpty()

    /** The snackbars of the layout on screen, for Undo after acting on the selection. */
    var undo: UndoSnackbars? = null

    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun clear() {
        ids = emptySet()
    }
}

val LocalTaskSelection = staticCompositionLocalOf { TaskSelection() }

/** True inside a row that drags on a long press: the drag selects it when let go without moving. */
val LocalDragSelects = compositionLocalOf { false }

/** Points the selection's Undo at [undo], the snackbars of what's on screen now. */
@Composable
fun SelectionUndo(undo: UndoSnackbars) {
    val selection = LocalTaskSelection.current
    SideEffect { selection.undo = undo }
}

/** Selecting is for the list on screen, so a change of [place] ends it. */
@Composable
fun ClearSelectionOn(vararg place: Any?) {
    val selection = LocalTaskSelection.current
    LaunchedEffect(*place) { selection.clear() }
}

/**
 * A task row's tap: opens the task, or picks it and lets it go while tasks are selected. A long
 * press starts selecting. Completed tasks can't be picked, so their tap always opens them.
 */
@Composable
fun Modifier.taskClick(id: String, selectable: Boolean, onClickLabel: String = "Open details", onOpen: () -> Unit): Modifier {
    val selection = LocalTaskSelection.current
    val dragSelects = LocalDragSelects.current
    val selected = id in selection.ids
    val picking = selection.active && selectable
    return this
        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
        .combinedClickable(
            onClickLabel = if (picking) (if (selected) "Deselect" else "Select") else onClickLabel,
            onLongClickLabel = "Select",
            // A drag row's long press belongs to the drag, which selects when let go in place.
            onLongClick = if (selectable && !dragSelects) ({ selection.toggle(id) }) else null,
        ) { if (picking) selection.toggle(id) else onOpen() }
        .semantics {
            if (selectable && dragSelects) onLongClick("Select") { selection.toggle(id); true }
            if (selection.active && selectable) this.selected = selected
        }
}

private enum class BulkEdit { Date, PickDate, Move, Priority }

/** Below the layout while tasks are selected: how many, and what can be done to all of them. */
@Composable
fun SelectionBar(selection: TaskSelection, vm: TodayViewModel, state: TodayState, sections: List<SectionEntity>) {
    var editing by remember { mutableStateOf<BulkEdit?>(null) }
    // Composed when selecting starts, so this goes before any back handler already on screen.
    BackHandler { selection.clear() }
    val ids = selection.ids
    fun finish(action: () -> Unit) {
        action()
        editing = null
        selection.clear()
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.navigationBarsPadding().fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(selection::clear) { Icon(Icons.Outlined.Close, "Stop selecting") }
            Text(
                "${ids.size} selected",
                Modifier.weight(1f).padding(start = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton({ finish { selection.undo?.completeAll(ids) } }) { Icon(Icons.Outlined.Check, "Complete") }
            IconButton({ editing = BulkEdit.Date }) { Icon(Icons.Outlined.Event, "Set date") }
            IconButton({ editing = BulkEdit.Move }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, "Move") }
            IconButton({ editing = BulkEdit.Priority }) { Icon(Icons.Outlined.Flag, "Set priority") }
            IconButton({ finish { selection.undo?.deleteAll(ids) } }) { Icon(Icons.Outlined.Delete, "Delete") }
        }
    }
    val dismiss = { editing = null }
    when (editing) {
        BulkEdit.Date -> RescheduleSheet(state.today, onPick = { date -> finish { vm.reschedule(ids, date) } }, onPickDate = { editing = BulkEdit.PickDate }, onDismiss = dismiss)
        BulkEdit.PickDate -> PickDateDialog(null, onDismiss = dismiss) { date -> finish { vm.reschedule(ids, date) } }
        BulkEdit.Move -> MoveDialog(state.projects, sections, null, null, dismiss) { project, section -> finish { vm.move(ids, project, section) } }
        BulkEdit.Priority -> PriorityDialog(null, dismiss) { priority -> finish { vm.prioritize(ids, priority) } }
        null -> {}
    }
}
