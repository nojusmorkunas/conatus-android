package com.useconatus.app.data.sync

import androidx.room.withTransaction
import com.useconatus.app.data.db.ChangeKind
import com.useconatus.app.data.db.ConatusDatabase
import com.useconatus.app.data.db.LOCAL_ID_PREFIX
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.data.db.ProjectEntity
import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.data.db.Target
import com.useconatus.app.domain.keyAfter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

/**
 * Writes to projects, sections and labels, which work offline like task writes: Room first,
 * then (with a server) the queue. They have no conflict check: the last change to reach the
 * server wins, as renames and colours rarely clash.
 */
class ListWrites(
    private val db: ConatusDatabase,
    private val connected: suspend () -> Boolean,
    private val requestSync: () -> Unit,
) {
    private suspend fun write(target: Target, id: String, kind: ChangeKind, body: JsonObject, summary: String, local: suspend () -> Unit) {
        val queue = connected()
        db.withTransaction {
            local()
            if (queue) db.changes().add(change(target, id, kind, body, summary))
        }
        if (queue) requestSync()
    }

    // A create carries a key, so a retry after a lost response can't make a second copy.
    private fun change(target: Target, id: String, kind: ChangeKind, body: JsonObject, summary: String) = PendingChange(
        taskId = id, target = target, kind = kind, body = body.toString(), summary = summary,
        idempotencyKey = if (kind == ChangeKind.Create) UUID.randomUUID().toString() else null,
    )

    private fun newId() = LOCAL_ID_PREFIX + UUID.randomUUID()

    private suspend fun queueCreate(target: Target, id: String, body: JsonObject, summary: String) =
        db.changes().add(change(target, id, ChangeKind.Create, body, summary))

    /**
     * Sends the projects, sections and labels made on this phone before it was connected, ahead
     * of the tasks in them. Parent projects go before their sub-projects.
     */
    suspend fun upload() {
        db.withTransaction {
            val projects = db.projects().unsent()
            val local = projects.mapTo(HashSet()) { it.id }
            fun depth(p: ProjectEntity): Int = p.parentId?.takeIf { it in local }?.let { parent -> 1 + depth(projects.first { it.id == parent }) } ?: 0
            projects.sortedBy(::depth).forEach { queueCreate(Target.Project, it.id, projectBody(it.name, it.color, it.icon, it.parentId), "Add the project “${it.name}”") }
            // A label the account already has is reused, instead of making a second with the same name.
            val existing = db.labels().all().filterNot { it.id.startsWith(LOCAL_ID_PREFIX) }.associateBy { it.name.lowercase() }
            db.labels().unsent().forEach { label ->
                val same = existing[label.name.lowercase()]
                if (same != null) {
                    db.tasks().renameLabel(label.id, same.id)
                    db.labels().delete(label.id)
                } else {
                    queueCreate(Target.Label, label.id, labelBody(label.name, label.color), "Add the label “${label.name}”")
                }
            }
            db.sections().unsent().sortedBy { it.order }.forEach { queueCreate(Target.Section, it.id, sectionBody(it.projectId, it.name), "Add the section “${it.name}”") }
        }
        requestSync()
    }

    /** Or removes them from the phone, with the tasks in those projects. */
    suspend fun dropUnsent() = db.withTransaction {
        db.projects().unsent().forEach { project ->
            db.tasks().delete(db.tasks().inProject(project.id).map { it.id })
            db.sections().delete(db.sections().inProject(project.id).map { it.id })
            db.projects().delete(project.id)
        }
        db.sections().delete(db.sections().unsent().map { it.id })
        db.labels().unsent().forEach { db.labels().delete(it.id) }
    }

    private fun projectBody(name: String, color: String, icon: String?, parentId: String?) = buildJsonObject {
        put("name", name)
        put("color", color)
        icon?.let { put("icon", it) }
        parentId?.let { put("parentId", it) }
    }

    private fun sectionBody(projectId: String, name: String) = buildJsonObject {
        put("projectId", projectId)
        put("name", name)
    }

    private fun labelBody(name: String, color: String) = buildJsonObject {
        put("name", name)
        put("color", color)
    }

    // Projects

    suspend fun createProject(name: String, color: String, icon: String?, parentId: String?): ProjectEntity {
        val project = ProjectEntity(newId(), parentId, name, color, isFavorite = false, isInbox = false, order = keyAfter(db.projects().all().map { it.order }), shared = false, icon = icon)
        write(Target.Project, project.id, ChangeKind.Create, projectBody(name, color, icon, parentId), "Add the project “$name”") { db.projects().upsert(listOf(project)) }
        return project
    }

    suspend fun editProject(project: ProjectEntity, name: String, color: String, icon: String?, parentId: String?) {
        val body = buildJsonObject {
            if (name != project.name) put("name", name)
            if (color != project.color) put("color", color)
            if (icon != project.icon) put("icon", icon)
            if (parentId != project.parentId) put("parentId", parentId)
        }
        if (body.isEmpty()) return
        write(Target.Project, project.id, ChangeKind.Update, body, "Edit the project “${project.name}”") {
            db.projects().upsert(listOf(project.copy(name = name, color = color, icon = icon, parentId = parentId)))
        }
    }

    suspend fun favoriteProject(project: ProjectEntity, favorite: Boolean) =
        write(Target.Project, project.id, ChangeKind.Update, buildJsonObject { put("isFavorite", favorite) }, "Favourite “${project.name}”") {
            db.projects().upsert(listOf(project.copy(isFavorite = favorite)))
        }

    /** Archived projects and their tasks leave every list; the server stops sending them. */
    suspend fun archiveProject(project: ProjectEntity, archived: Boolean) =
        write(Target.Project, project.id, ChangeKind.Update, buildJsonObject { put("isArchived", archived) }, "${if (archived) "Archive" else "Unarchive"} “${project.name}”") {
            db.projects().upsert(listOf(project.copy(isArchived = archived)))
        }

    /** A project with all its sub-projects. */
    private suspend fun subtree(project: ProjectEntity): List<ProjectEntity> {
        val all = db.projects().all()
        val out = mutableListOf(project)
        var level = listOf(project.id)
        while (level.isNotEmpty()) {
            val next = all.filter { it.parentId in level }
            out += next
            level = next.map { it.id }
        }
        return out
    }

    /** Moves a project and its sub-projects to Trash, as the server does; their sections and tasks go with them. */
    suspend fun deleteProject(project: ProjectEntity) {
        val now = Instant.now().toString()
        val doomed = subtree(project).filter { it.deletedAt == null }
        write(Target.Project, project.id, ChangeKind.Delete, JsonObject(emptyMap()), "Delete the project “${project.name}”") {
            db.projects().upsert(doomed.map { it.copy(deletedAt = now) })
        }
    }

    /** Brings a project back from Trash, with the sub-projects that went with it. */
    suspend fun restoreProject(project: ProjectEntity) {
        val back = subtree(project).filter { it.deletedAt == project.deletedAt }.map { it.copy(deletedAt = null) }
        write(Target.Project, project.id, ChangeKind.Restore, JsonObject(emptyMap()), "Restore the project “${project.name}”") {
            db.projects().upsert(back)
        }
    }

    // Sections

    suspend fun createSection(projectId: String, name: String): SectionEntity {
        val section = SectionEntity(newId(), projectId, name, keyAfter(db.sections().inProject(projectId).map { it.order }))
        write(Target.Section, section.id, ChangeKind.Create, sectionBody(projectId, name), "Add the section “$name”") { db.sections().upsert(listOf(section)) }
        return section
    }

    suspend fun renameSection(section: SectionEntity, name: String) {
        if (name == section.name) return
        write(Target.Section, section.id, ChangeKind.Update, buildJsonObject { put("name", name) }, "Rename the section “${section.name}”") {
            db.sections().upsert(listOf(section.copy(name = name)))
        }
    }

    suspend fun archiveSection(section: SectionEntity, archived: Boolean) =
        write(Target.Section, section.id, ChangeKind.Update, buildJsonObject { put("isArchived", archived) }, "${if (archived) "Archive" else "Unarchive"} “${section.name}”") {
            db.sections().upsert(listOf(section.copy(isArchived = archived)))
        }

    /** Puts a section right after [after] (first when null), with [order] its key on the phone. */
    suspend fun placeSection(section: SectionEntity, after: String?, order: String) =
        write(Target.Section, section.id, ChangeKind.Update, buildJsonObject { put("afterId", after) }, "Move the section “${section.name}”") {
            db.sections().upsert(listOf(section.copy(order = order)))
        }

    /** Deletes a section and moves its tasks to Trash, out of the section, as the server does. */
    suspend fun deleteSection(section: SectionEntity) {
        val now = Instant.now().toString()
        write(Target.Section, section.id, ChangeKind.Delete, JsonObject(emptyMap()), "Delete the section “${section.name}”") {
            val tasks = db.tasks().inProject(section.projectId).filter { it.sectionId == section.id }
            db.tasks().upsert(tasks.map { it.copy(sectionId = null, deletedAt = it.deletedAt ?: now) })
            db.sections().delete(listOf(section.id))
        }
    }

    // Labels

    suspend fun createLabel(name: String, color: String): LabelEntity {
        val label = LabelEntity(newId(), name, color, isFavorite = false, order = keyAfter(db.labels().all().map { it.order }))
        write(Target.Label, label.id, ChangeKind.Create, labelBody(name, color), "Add the label “$name”") { db.labels().upsert(listOf(label)) }
        return label
    }

    suspend fun editLabel(label: LabelEntity, name: String, color: String) {
        val body = buildJsonObject {
            if (name != label.name) put("name", name)
            if (color != label.color) put("color", color)
        }
        if (body.isEmpty()) return
        write(Target.Label, label.id, ChangeKind.Update, body, "Edit the label “${label.name}”") {
            db.labels().upsert(listOf(label.copy(name = name, color = color)))
        }
    }

    /** Deletes a label and takes it off every task that has it. */
    suspend fun deleteLabel(label: LabelEntity) =
        write(Target.Label, label.id, ChangeKind.Delete, JsonObject(emptyMap()), "Delete the label “${label.name}”") {
            val tasks = db.tasks().withLabel(label.id)
            db.tasks().upsert(tasks.map { it.copy(labelIds = it.labelIds - label.id) })
            db.labels().delete(label.id)
        }
}
