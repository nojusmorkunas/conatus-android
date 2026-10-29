package com.useconatus.app.ui

import com.useconatus.app.ui.theme.Hue
import com.useconatus.app.ui.today.ProjectItem
import com.useconatus.app.ui.today.inTreeOrder
import org.junit.Assert.assertEquals
import org.junit.Test

private fun project(id: String, parent: String? = null, inbox: Boolean = false) = ProjectItem(id, id, Hue.Gray, parent, inbox, 0)

class ProjectTreeTest {
    @Test fun `puts the Inbox first and sub-projects under their parent, keeping the server's order`() {
        val tree = listOf(project("work"), project("site", parent = "work"), project("inbox", inbox = true), project("home"), project("plan", parent = "work"), project("deep", parent = "site"))
            .inTreeOrder()
        assertEquals(listOf("inbox" to 0, "work" to 0, "site" to 1, "deep" to 2, "plan" to 1, "home" to 0), tree.map { it.first.id to it.second })
    }

    @Test fun `a project whose parent isn't visible shows at the top level`() {
        assertEquals(listOf("orphan" to 0), listOf(project("orphan", parent = "archived")).inTreeOrder().map { it.first.id to it.second })
    }
}
