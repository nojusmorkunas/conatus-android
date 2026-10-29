package com.useconatus.app.ui

import com.useconatus.app.data.db.SectionEntity
import com.useconatus.app.data.db.TaskEntity
import com.useconatus.app.data.sync.nextToSend
import com.useconatus.app.data.db.ChangeKind
import com.useconatus.app.data.db.PendingChange
import com.useconatus.app.ui.project.ListEntry
import com.useconatus.app.ui.project.TreeRow
import com.useconatus.app.ui.project.placement
import com.useconatus.app.ui.project.treeOf
import com.useconatus.app.ui.today.TaskRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun task(id: String, order: String, parent: String? = null, section: String? = null) = TaskEntity(
    id = id, projectId = "p", sectionId = section, parentId = parent, content = id, description = "", priority = 4,
    dueDate = null, dueTime = null, deadlineDate = null, recurrence = null, durationMinutes = null,
    isCompleted = false, order = order, updatedAt = "", labelIds = emptyList(), commentCount = 0,
)

private fun entry(task: TaskEntity, depth: Int = 0) = ListEntry.Task(
    TreeRow(TaskRow(task.id, task.content, "", 4, null, null, false, null, null, 0, 0, 0, emptyList(), null), task, depth),
)

private fun heading(id: String) = ListEntry.Heading(SectionEntity(id, "p", id, "a0"), 0)

class TreeTest {
    @Test fun `sub-tasks follow their parent, in order, one level deeper`() {
        val tasks = listOf(task("b", "a1"), task("a", "a0"), task("a2", "a1", parent = "a"), task("a1", "a0", parent = "a"), task("a1x", "a0", parent = "a1"))
        assertEquals(listOf("a" to 0, "a1" to 1, "a1x" to 2, "a2" to 1, "b" to 0), treeOf(tasks).map { (t, depth) -> t.id to depth })
    }

    @Test fun `a sub-task whose parent isn't listed shows at the top level`() =
        assertEquals(listOf("orphan" to 0), treeOf(listOf(task("orphan", "a0", parent = "gone"))).map { (t, d) -> t.id to d })
}

class PlacementTest {
    private val a = task("a", "a0")
    private val b = task("b", "a1")
    private val c = task("c", "a2", section = "s")

    @Test fun `dropped between two tasks takes a key between them`() {
        val p = placement(listOf(entry(a), entry(c.copy(sectionId = null)), entry(b)), "c")!!
        assertEquals(null, p.sectionId)
        assertEquals("a", p.after)
        assertEquals("a0V", p.order)
    }

    @Test fun `dropped under a heading joins that section, first`() {
        val p = placement(listOf(entry(a), heading("s"), entry(b), entry(c)), "b")!!
        assertEquals("s", p.sectionId)
        assertNull(p.after)
    }

    @Test fun `sub-tasks in between don't count as neighbours`() {
        val child = task("x", "a0", parent = "a")
        val p = placement(listOf(entry(a), entry(child, depth = 1), entry(b)), "b")!!
        assertEquals("a", p.after)
        assertEquals("a1", p.order)
    }
}

class DependencyTest {
    @Test fun `a change naming something the server hasn't created yet waits`() {
        val createProject = PendingChange(seq = 1, taskId = "local:p", kind = ChangeKind.Create, body = """{"name":"Home"}""", summary = "", failure = "Rejected")
        val createTask = PendingChange(seq = 2, taskId = "local:t", kind = ChangeKind.Create, body = """{"projectId":"local:p","content":"x"}""", summary = "")
        val other = PendingChange(seq = 3, taskId = "t2", kind = ChangeKind.Update, body = "{}", summary = "")
        assertEquals(3L, nextToSend(listOf(createProject, createTask, other))?.seq)
    }
}
