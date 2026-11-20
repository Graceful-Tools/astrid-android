package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.model.TaskDetail
import com.gracefultools.astrid.model.TaskFields
import com.gracefultools.astrid.model.UserSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADEV-150 (ADEV-147): task details' leading control is the row's — a finished task is the checkbox. */
class DetailLeadingTest {
    private val someone = UserSummary(id = "u-other", name = "Sam")

    @Test
    fun `ADEV-150 a completed task in details is the checked box whoever holds it`() {
        val open = TaskDetail(task = TaskFields(id = "t1"), assignee = someone, fieldOrder = listOf("when"))
        assertTrue(detailLeadingShowsFace(open))
        assertFalse(detailLeadingShowsFace(open.copy(task = open.task.copy(completed = true))))
    }

    @Test
    fun `ADEV-150 nobody's task, or Who already leading the fields, is the checkbox`() {
        assertFalse(detailLeadingShowsFace(TaskDetail(task = TaskFields(id = "t1"), assignee = null, fieldOrder = listOf("when"))))
        assertFalse(detailLeadingShowsFace(TaskDetail(task = TaskFields(id = "t1"), assignee = someone, fieldOrder = listOf("assignee"))))
    }
}
