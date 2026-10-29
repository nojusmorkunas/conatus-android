package com.useconatus.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    @Test fun `every link reads back from its own path`() {
        listOf(AppLink.Task("t1"), AppLink.Project("p1"), AppLink.Today, AppLink.Upcoming, AppLink.Search).forEach {
            assertEquals(it, AppLink.parse(AppLink.path(it)))
        }
    }

    @Test fun `the sign-in redirect, unknown paths and missing ids aren't links`() {
        assertNull(AppLink.parse("/auth"))
        assertNull(AppLink.parse("/settings"))
        assertNull(AppLink.parse("/task/"))
        assertNull(AppLink.parse("/project"))
    }
}
