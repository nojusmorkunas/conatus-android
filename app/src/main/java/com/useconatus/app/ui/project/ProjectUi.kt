@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.useconatus.app.ui.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.useconatus.app.AppContainer
import com.useconatus.app.data.db.LabelEntity
import kotlinx.coroutines.launch
import com.useconatus.app.data.db.ProjectEntity
import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.ui.components.ListMessage
import com.useconatus.app.ui.task.CommentsSheet
import com.useconatus.app.ui.components.projectIcon
import com.useconatus.app.ui.components.projectIconPresets
import com.useconatus.app.ui.components.SectionHeading
import com.useconatus.app.ui.components.TaskCheckbox
import com.useconatus.app.ui.components.TaskDetails
import com.useconatus.app.ui.components.draggableRow
import com.useconatus.app.ui.components.rememberDragState
import com.useconatus.app.ui.components.LocalDragSelects
import com.useconatus.app.ui.components.LocalTaskSelection
import com.useconatus.app.ui.theme.Hue
import com.useconatus.app.ui.theme.LocalConatusColors
import com.useconatus.app.ui.theme.SheetShape
import com.useconatus.app.ui.theme.sheetColor
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.DialogProperties
import com.useconatus.app.ui.components.MAX_NAME
import com.useconatus.app.ui.components.tooLong

// Project and label pieces shared by the three layouts: the dialogs, the menus, the list with
// drag and drop, and the board. Each layout brings its own task row and frame.

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

/** The server's 13 colours as dots; the chosen one is bigger and ringed. */
@Composable
fun ColorPicker(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Hue.entries.forEach { hue ->
            val name = hue.name.lowercase()
            val on = name.equals(selected, ignoreCase = true)
            Box(
                Modifier.size(40.dp).clip(CircleShape).selectable(on, role = Role.RadioButton) { onPick(name) }.semantics { contentDescription = hue.name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(if (on) 28.dp else 22.dp).background(hue.dot, CircleShape)
                        .then(if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                )
            }
        }
    }
}

/** The web's project icons in the project's colour, with the folder first for none. */
@Composable
fun IconPicker(selected: String?, tint: Color, onPick: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (listOf(null to "Folder") + projectIconPresets.map { it.value to it.label }).forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                    .then(if (on) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small) else Modifier)
                    .selectable(on, role = Role.RadioButton) { onPick(value) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { Icon(projectIcon(value), null, Modifier.size(20.dp), tint = tint) }
        }
    }
}

/** The ids of [id]'s sub-projects, however deep. */
fun descendantIds(projects: List<ProjectEntity>, id: String): Set<String> {
    val out = mutableSetOf<String>()
    var level = setOf(id)
    while (level.isNotEmpty()) {
        level = projects.filter { it.parentId in level }.mapTo(HashSet()) { it.id } - out
        out += level
    }
    return out
}

private fun validName(name: String) = name.isNotBlank() && tooLong(name, MAX_NAME) == null

/** The name in every name dialog. The keyboard's Done key saves, when the name can be saved. */
@Composable
private fun NameField(name: String, onChange: (String) -> Unit, enabled: Boolean = true, onDone: () -> Unit) {
    val problem = tooLong(name, MAX_NAME)
    OutlinedTextField(
        name, onChange, Modifier.fillMaxWidth(),
        label = { Text("Name") },
        enabled = enabled,
        singleLine = true,
        isError = problem != null,
        supportingText = problem?.let { { Text(it) } },
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (validName(name)) onDone() }),
    )
}

/** Creates or edits a project: name, colour, icon and the project it sits under. */
@Composable
fun ProjectDialog(initial: ProjectEntity?, projects: List<ProjectEntity>, onSave: (name: String, color: String, icon: String?, parentId: String?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: "gray") }
    var icon by rememberSaveable { mutableStateOf(initial?.icon) }
    var parent by rememberSaveable { mutableStateOf(initial?.parentId) }
    var choosing by remember { mutableStateOf(false) }
    // A project can't go under itself or its own sub-projects, and nothing goes under the Inbox.
    val blocked = initial?.let { descendantIds(projects, it.id) + it.id }.orEmpty()
    val parents = projects.filter { !it.isInbox && !it.isArchived && it.id !in blocked }.sortedBy { it.order }
    val inbox = initial?.isInbox == true
    val save = { onSave(name.trim(), color, icon, parent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        // Sized to the space above the keyboard, so the form scrolls and the buttons stay in view.
        modifier = Modifier.safeDrawingPadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        title = { Text(if (initial == null) "New project" else "Edit project") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NameField(name, { name = it }, enabled = !inbox, onDone = save)
                Text("Colour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorPicker(color) { color = it }
                // The Inbox always shows its own icon.
                if (!inbox) {
                    Text("Icon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconPicker(icon, Hue.of(color).dot) { icon = it }
                    Text("Inside", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton({ choosing = true }, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Text(parents.firstOrNull { it.id == parent }?.name ?: "No parent project", color = MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(choosing, { choosing = false }, shape = MaterialTheme.shapes.medium) {
                            DropdownMenuItem({ Text("No parent project") }, { parent = null; choosing = false })
                            parents.forEach { p -> DropdownMenuItem({ Text(p.name) }, { parent = p.id; choosing = false }) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(save, enabled = validName(name)) { Text(if (initial == null) "Add" else "Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.large,
    )
}

/** Creates or edits a label: name and colour. */
@Composable
fun LabelDialog(initial: LabelEntity?, onSave: (name: String, color: String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: "gray") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New label" else "Edit label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NameField(name, { name = it }, onDone = { onSave(name.trim(), color) })
                Text("Colour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = { TextButton({ onSave(name.trim(), color) }, enabled = validName(name)) { Text(if (initial == null) "Add" else "Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.large,
    )
}

/** One name to type: a new section, or a renamed one. */
@Composable
fun NameDialog(title: String, initial: String, action: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NameField(name, { name = it }, onDone = { onSave(name.trim()); onDismiss() }) },
        confirmButton = { TextButton({ onSave(name.trim()); onDismiss() }, enabled = validName(name)) { Text(action) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
        TextButton({ onConfirm(); onDismiss() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(action) }
    },
    dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    shape = MaterialTheme.shapes.large,
)

/** The options for the project or label a page shows. [onGone] runs when it's archived or deleted. */
@Composable
fun ListActions(state: ListState, vm: ListViewModel, onGone: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var addingSection by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var commenting by rememberSaveable { mutableStateOf(false) }
    val project = state.project
    val label = state.label
    Box {
        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, if (label != null) "Label options" else "Project options") }
        DropdownMenu(menu, { menu = false }, shape = MaterialTheme.shapes.medium) {
            fun item(text: String, action: () -> Unit) = Pair(text, action)
            val items = buildList {
                if (project != null) {
                    add(item("Edit project") { editing = true })
                    add(item("Comments") { vm.refreshComments(); commenting = true })
                    add(item("Add section") { addingSection = true })
                    add(item(if (state.board) "Show as list" else "Show as board") { vm.setBoard(!state.board) })
                    if (!project.isInbox) {
                        add(item(if (project.isFavorite) "Remove from favourites" else "Add to favourites") { vm.favorite(!project.isFavorite) })
                        add(item("Archive") { vm.archive(); onGone() })
                        add(item("Delete project") { deleting = true })
                    }
                }
                if (label != null) {
                    add(item("Edit label") { editing = true })
                    add(item("Delete label") { deleting = true })
                }
            }
            items.forEach { (text, action) ->
                DropdownMenuItem(
                    { Text(text, color = if (text.startsWith("Delete")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                    { menu = false; action() },
                )
            }
        }
    }
    if (editing && project != null) ProjectDialog(project, state.allProjects, { name, color, icon, parent -> vm.editProject(name, color, icon, parent); editing = false }) { editing = false }
    if (editing && label != null) LabelDialog(label, { name, color -> vm.editLabel(name, color); editing = false }) { editing = false }
    if (addingSection) NameDialog("New section", "", "Add", vm::addSection) { addingSection = false }
    if (commenting && project != null) {
        val comments by vm.comments.collectAsStateWithLifecycle()
        val me by vm.me.collectAsStateWithLifecycle()
        val zone by vm.zone.collectAsStateWithLifecycle()
        CommentsSheet("Comments on ${project.name}", comments, me, zone, state.today, vm::addComment, vm::editComment, vm::deleteComment) { commenting = false }
    }
    if (deleting && project != null) {
        ConfirmDialog(
            "Delete “${project.name}”?",
            "It goes to Trash with its sections, tasks and sub-projects. You can restore it from there.",
            "Delete",
            { vm.deleteProject(); onGone() },
        ) { deleting = false }
    }
    if (deleting && label != null) {
        ConfirmDialog("Delete “${label.name}”?", "It comes off every task that has it. The tasks stay.", "Delete", { vm.deleteLabel(); onGone() }) { deleting = false }
    }
}

/** A section heading's options. */
@Composable
fun SectionActions(section: SectionEntity, vm: ListViewModel, first: Boolean, last: Boolean) {
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "Options for ${section.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        DropdownMenu(menu, { menu = false }, shape = MaterialTheme.shapes.medium) {
            DropdownMenuItem({ Text("Rename") }, { menu = false; renaming = true })
            if (!first) DropdownMenuItem({ Text("Move up") }, { menu = false; vm.moveSection(section, up = true) })
            if (!last) DropdownMenuItem({ Text("Move down") }, { menu = false; vm.moveSection(section, up = false) })
            DropdownMenuItem({ Text("Archive") }, { menu = false; vm.archiveSection(section, true) })
            DropdownMenuItem({ Text("Delete section", color = MaterialTheme.colorScheme.error) }, { menu = false; deleting = true })
        }
    }
    if (renaming) NameDialog("Rename section", section.name, "Save", { vm.renameSection(section, it) }) { renaming = false }
    if (deleting) {
        ConfirmDialog("Delete “${section.name}”?", "Its tasks go to Trash.", "Delete", { vm.deleteSection(section) }) { deleting = false }
    }
}

/** Archived sections, each with Unarchive. */
@Composable
fun ArchivedSectionsSheet(sections: List<SectionEntity>, vm: ListViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(sections.isEmpty()) { if (sections.isEmpty()) onDismiss() }
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = sheetColor(), shape = SheetShape) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Archived sections", Modifier.padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            sections.forEach { section ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(section.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton({ vm.archiveSection(section, false) }) { Text("Unarchive") }
                }
            }
        }
    }
}

/** Archived projects, each with Unarchive. With a server, only those archived on this phone show here. */
@Composable
fun ArchivedProjectsSheet(projects: List<ProjectEntity>, onUnarchive: (ProjectEntity) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = sheetColor(), shape = SheetShape) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Archived projects", Modifier.padding(horizontal = 20.dp, vertical = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            if (projects.isEmpty()) {
                Text("Nothing is archived.", Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            projects.forEach { project ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Hue.of(project.color).dot, CircleShape))
                    Text(project.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    TextButton({ onUnarchive(project) }) { Text("Unarchive") }
                }
            }
        }
    }
}

private fun List<ListEntry>.moved(from: Any, to: Any): List<ListEntry> {
    val i = indexOfFirst { it.key == from }
    val j = indexOfFirst { it.key == to }
    if (i < 0 || j < 0) return this
    return toMutableList().apply { add(j, removeAt(i)) }
}

/** The list without the sub-tasks under [key], which follow their parent when it's dropped. */
private fun List<ListEntry>.withoutChildrenOf(key: Any): List<ListEntry> {
    val i = indexOfFirst { it.key == key }
    val depth = (getOrNull(i) as? ListEntry.Task)?.row?.depth ?: return this
    var end = i + 1
    while (end < size && (this[end] as? ListEntry.Task)?.row?.depth?.let { it > depth } == true) end++
    return subList(0, i + 1) + subList(end, size)
}

/**
 * A project or label as a list: section headings with their options, tasks indented under their
 * parents, and long-press drag to move a top-level task within or between sections. [row] draws
 * one task in the layout's own style. [addInSections] adds Parity's "Add task" under each section.
 */
@Composable
fun ProjectList(
    state: ListState,
    vm: ListViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
    rule: Boolean = false,
    addInSections: ((SectionEntity?) -> Unit)? = null,
    header: LazyListScope.() -> Unit = {},
    row: @Composable (TreeRow, Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val entries = state.entries
    // The list as shown: a drag rearranges it, and the drop sends the move.
    var shown by remember(entries) { mutableStateOf(entries) }
    var archivedOpen by remember { mutableStateOf(false) }
    val drag = rememberDragState(
        listState,
        canDrop = { key -> shown.firstOrNull { it.key == key }.let { it is ListEntry.Heading || (it is ListEntry.Task && it.row.depth == 0) } },
        onMove = { from, to -> shown = shown.moved(from, to) },
        onDrop = { key ->
            placement(shown, key as String)?.let { vm.place(key, it) }
            shown = entries
        },
    )
    LaunchedEffect(drag.dragging) { drag.dragging?.let { shown = shown.withoutChildrenOf(it) } }
    val selection = LocalTaskSelection.current
    val sections = state.groups.mapNotNull { it.section }
    LazyColumn(modifier.fillMaxSize(), listState, contentPadding) {
        header()
        if (state.loaded && entries.isEmpty()) item("empty") { ListMessage(if (state.label != null) "No open tasks have this label." else "No tasks yet.", Modifier.heightIn(min = 240.dp)) }
        shown.forEachIndexed { index, entry ->
            when (entry) {
                is ListEntry.Heading -> item(entry.key) {
                    Row(Modifier.animateItem(), verticalAlignment = Alignment.CenterVertically) {
                        SectionHeading(entry.section.name, entry.count, Modifier.weight(1f).padding(top = 8.dp), rule = rule)
                        SectionActions(entry.section, vm, first = sections.firstOrNull()?.id == entry.section.id, last = sections.lastOrNull()?.id == entry.section.id)
                    }
                }
                is ListEntry.Task -> item(entry.key) {
                    val lifted = drag.dragging == entry.key
                    val indent = Modifier.padding(start = (28 * entry.row.depth).dp)
                    val draggable = entry.row.depth == 0 && state.project != null
                    CompositionLocalProvider(LocalDragSelects provides draggable) {
                        row(
                            entry.row,
                            (if (lifted) Modifier else Modifier.animateItem())
                                .draggableRow(drag, entry.key, enabled = draggable) { selection.toggle(entry.row.task.id) }
                                .background(MaterialTheme.colorScheme.surface)
                                .then(indent),
                        )
                    }
                }
            }
            val endOfSection = shown.getOrNull(index + 1).let { it == null || it is ListEntry.Heading }
            if (addInSections != null && endOfSection && state.project != null) {
                val section = shown.subList(0, index + 1).filterIsInstance<ListEntry.Heading>().lastOrNull()?.section
                item("add-" + (section?.id ?: "top")) { AddTaskLine(Modifier.animateItem()) { addInSections(section) } }
            }
        }
        if (state.archivedSections.isNotEmpty()) {
            item("archived") {
                TextButton({ archivedOpen = true }, Modifier.padding(horizontal = 8.dp)) {
                    val n = state.archivedSections.size
                    Text(if (n == 1) "1 archived section" else "$n archived sections", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (archivedOpen) ArchivedSectionsSheet(state.archivedSections, vm) { archivedOpen = false }
}

/** New project, from any layout's project list. [onCreated] gets it, to open it. */
@Composable
fun NewProjectDialog(container: AppContainer, onCreated: (ProjectEntity) -> Unit, onDismiss: () -> Unit) {
    val projects by container.db.projects().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    ProjectDialog(null, projects, { name, color, icon, parent ->
        scope.launch { onCreated(container.lists.createProject(name, color, icon, parent)) }
        onDismiss()
    }, onDismiss)
}

@Composable
fun NewLabelDialog(container: AppContainer, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    LabelDialog(null, { name, color ->
        scope.launch { container.lists.createLabel(name, color) }
        onDismiss()
    }, onDismiss)
}

@Composable
fun ArchivedProjects(container: AppContainer, onDismiss: () -> Unit) {
    val projects by container.db.projects().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    ArchivedProjectsSheet(projects.filter { it.isArchived }, { scope.launch { container.lists.archiveProject(it, false) } }, onDismiss)
}

/** Parity's "Add task" line under a section, as on the web. */
@Composable
fun AddTaskLine(modifier: Modifier = Modifier, onClick: () -> Unit) = Row(
    modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
) {
    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Add task", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A project as a board: one column per section, a card per top-level task, and "Add task" at the
 * end of each column. Moving a task between columns is done from its details.
 */
@Composable
fun ProjectBoard(
    state: ListState,
    vm: ListViewModel,
    onOpen: (String) -> Unit,
    onToggle: (TreeRow, Boolean) -> Unit,
    onAdd: (SectionEntity?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalConatusColors.current
    val sections = state.groups.mapNotNull { it.section }
    // The column for tasks outside any section only shows when it has some, or there are no sections.
    val columns = state.groups.filter { it.section != null || it.rows.isNotEmpty() || sections.isEmpty() }
    LazyRow(modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(columns, key = { it.section?.id ?: "top" }) { group ->
            Column(Modifier.width(280.dp).fillMaxHeight()) {
                // The same height with or without the options button, so the headings line up.
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.section?.name ?: "No section", Modifier.weight(1f).padding(start = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${group.rows.count { it.depth == 0 }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    group.section?.let { SectionActions(it, vm, first = sections.first().id == it.id, last = sections.last().id == it.id) }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(group.rows.filter { it.depth == 0 }, key = { it.task.id }) { tree ->
                        Surface(
                            onClick = { onOpen(tree.task.id) },
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().animateItem(),
                        ) {
                            Row(Modifier.padding(end = 12.dp, bottom = 10.dp)) {
                                TaskCheckbox(tree.row.priority, tree.row.done, tree.row.title) { onToggle(tree, it) }
                                Column(Modifier.weight(1f).padding(top = 12.dp)) {
                                    Text(tree.row.title, style = MaterialTheme.typography.bodyMedium)
                                    if (tree.row.description.isNotBlank()) {
                                        Text(tree.row.description, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    TaskDetails(tree.row, state.today, showProject = false)
                                }
                            }
                        }
                    }
                    item("add") { AddTaskLine { onAdd(group.section) } }
                }
            }
        }
    }
}
