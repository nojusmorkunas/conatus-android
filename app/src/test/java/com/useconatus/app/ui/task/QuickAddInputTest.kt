package com.useconatus.app.ui.task

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.useconatus.app.data.db.LabelEntity
import com.useconatus.app.data.sync.TaskEdit
import com.useconatus.app.data.sync.TaskEdit.Field
import com.useconatus.app.data.sync.quickAddTask
import com.useconatus.app.domain.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class QuickAddInputTest {
    private val labels = listOf(LabelEntity("l1", "urgent", "", false, "a"), LabelEntity("l2", "home", "", false, "b"))
    private val context = QuickAddContext(listOf("Inbox", "Work"), labels.map { it.name }, LocalDate.parse("2026-11-16"), "dd/MM/yyyy")

    private fun QuickAddInput.task() = quickAddTask(context.preview(value.text).parsed, picks, labels, "2026-11-16")
    private fun QuickAddInput.edit(edit: TaskEdit) = pick(context, edit.kinds()) { it.copy(edit = it.edit.then(edit)) }

    @Test fun `a picked choice takes its typed phrase out, keeping the cursor`() {
        val value = TextFieldValue("Pay rent tomorrow p1", TextRange(20))
        assertEquals(TextFieldValue("Pay rent p1", TextRange(11)), value.without(context.preview(value.text).tokens.filter { it.kind == TokenKind.Date }))
        val leading = TextFieldValue("tomorrow pay rent", TextRange(17))
        assertEquals("pay rent", leading.without(context.preview(leading.text).tokens).text)
    }

    @Test fun `only phrases typed just now count as typed`() {
        val before = context.preview("Pay rent").tokens
        assertEquals(setOf(TokenKind.Date), typedKinds(before, context.preview("Pay rent tomorrow").tokens))
        assertEquals(emptySet<TokenKind>(), typedKinds(context.preview("Pay rent tomorrow").tokens, context.preview("Pay rent tomorrow please").tokens))
    }

    @Test fun `picking a date replaces the typed one`() {
        val input = QuickAddInput(TextFieldValue("Call mom tomorrow"))
        input.edit(TaskEdit(dueDate = Field("2026-11-20")))
        assertEquals("Call mom", input.value.text)
        assertEquals("2026-11-20", input.task().dueDate)
    }

    @Test fun `a date typed after a time was picked wins over the date the time brought`() {
        val input = QuickAddInput(TextFieldValue("Call mom"))
        input.edit(TaskEdit(dueDate = Field("2026-11-16"), dueTime = Field("17:00")))
        input.type(context, TextFieldValue("Call mom tomorrow"))
        assertEquals("2026-11-17" to "17:00", input.task().let { it.dueDate to it.dueTime })
    }

    @Test fun `picked and typed labels add up`() {
        val input = QuickAddInput(TextFieldValue("Call mom @urgent"))
        input.pick(context, setOf(TokenKind.Label)) { it.copy(labelIds = listOf("l2")) }
        assertEquals("Call mom", input.value.text)
        input.type(context, TextFieldValue("Call mom @urgent"))
        assertEquals(listOf("l2", "l1"), input.task().labelIds)
    }

    @Test fun `a repeat picked over a typed one still gets a date`() {
        val input = QuickAddInput(TextFieldValue("Water plants every monday"))
        input.edit(TaskEdit(recurrence = Field("every day")))
        assertEquals("Water plants", input.value.text)
        assertEquals("every day" to "2026-11-16", input.task().let { it.recurrence to it.dueDate })
    }

    @Test fun `adding keeps the picked place for the next task`() {
        val input = QuickAddInput(TextFieldValue("Call mom p1"))
        input.pick(context, setOf(TokenKind.Project)) { it.copy(projectId = "work", sectionId = "s1") }
        input.edit(TaskEdit(priority = 2))
        input.submit(context) { _, _ -> }
        assertEquals("", input.value.text)
        assertEquals(Triple("work", "s1", null), input.picks.let { Triple(it.projectId, it.sectionId, it.edit.priority) })
    }
}
