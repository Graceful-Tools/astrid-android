package com.gracefultools.astrid.ui.detail

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import com.gracefultools.astrid.model.CommentSuggestion
import com.gracefultools.astrid.model.RepeatPattern
import com.gracefultools.astrid.model.MonthWeekday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The detail's remaining iOS parity (AATD-50/51/52/53), as core commands. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailExtrasTest {
    private fun detail(extra: String = "", task: String = "") = """{"task":{"id":"t1","title":"Plan","listIds":["l1"]$task},"fieldOrder":[],
        "descriptionBlocks":[],"comments":[],"subtasks":[],"listChips":[{"id":"l1","name":"Home","color":"#fff"}]$extra}"""

    private fun core(detail: String = detail()) = FakeCore().apply { answer("taskDetail", detail) }
    private fun vm(core: FakeCore) = TaskDetailViewModel(core, "t1", UnconfinedTestDispatcher())
    private fun TestScope.timedVm(core: FakeCore) = TaskDetailViewModel(core, "t1", UnconfinedTestDispatcher(testScheduler))
    private fun changes(core: FakeCore) = core.last("updateTask")!!["changes"] as JsonObject

    // ── AATD-50: the custom repeat editor ─────────────────────────────────────────────────────

    @Test
    fun `AATD-50 a monthly same-weekday pattern sends the weekday and week, not a stale day, and repeatFrom`() = runTest {
        val core = core()
        vm(core).setCustomRepeat(
            RepeatPattern(unit = "months", interval = 1, monthRepeatType = "same_weekday", monthDay = 12,
                monthWeekday = MonthWeekday("tuesday", 2), weekdays = listOf("monday")),
            repeatFrom = "DUE_DATE",
        )
        val sent = changes(core)
        assertEquals(JsonPrimitive("DUE_DATE"), sent["repeatFrom"])
        val pattern = sent["repeatingData"] as JsonObject
        assertEquals(JsonPrimitive("same_weekday"), pattern["monthRepeatType"])
        val weekday = pattern["monthWeekday"] as JsonObject
        assertEquals(JsonPrimitive("tuesday"), weekday["weekday"])
        assertEquals(JsonPrimitive(2), weekday["weekOfMonth"])
        assertFalse(pattern.containsKey("monthDay"))
        assertFalse(pattern.containsKey("weekdays"))
    }

    @Test
    fun `AATD-50 a monthly same-date pattern sends its day, a yearly one its month and day`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.setCustomRepeat(RepeatPattern(unit = "months", interval = 2, monthRepeatType = "same_date", monthDay = 15), repeatFrom = "COMPLETION_DATE")
        val monthly = changes(core)["repeatingData"] as JsonObject
        assertEquals(JsonPrimitive(15), monthly["monthDay"])
        assertFalse(monthly.containsKey("monthWeekday"))
        assertEquals(JsonPrimitive("COMPLETION_DATE"), changes(core)["repeatFrom"])

        vm.setCustomRepeat(RepeatPattern(unit = "years", interval = 1, month = 3, day = 4, monthDay = 9), repeatFrom = "COMPLETION_DATE")
        val yearly = changes(core)["repeatingData"] as JsonObject
        assertEquals(JsonPrimitive(3), yearly["month"])
        assertEquals(JsonPrimitive(4), yearly["day"])
        assertFalse(yearly.containsKey("monthDay"))
    }

    @Test
    fun `AATD-50 the interval and counts stay within 1 to 99`() = runTest {
        val core = core()
        vm(core).setCustomRepeat(RepeatPattern(unit = "days", interval = 250, endCondition = "after_occurrences", endAfterOccurrences = 0), repeatFrom = null)
        val pattern = changes(core)["repeatingData"] as JsonObject
        assertEquals(JsonPrimitive(99), pattern["interval"])
        assertEquals(JsonPrimitive(1), pattern["endAfterOccurrences"])
        assertFalse("no mode chosen, none sent", changes(core).containsKey("repeatFrom"))
    }

    @Test
    fun `AATD-50 the editor opens on the task's own mode, from the core's repeatOptions`() = runTest {
        val core = core().apply {
            answer("repeatOptions", """{"repeating":"custom","repeatFrom":"DUE_DATE","presets":[],
                "pattern":{"unit":"months","interval":1,"monthRepeatType":"same_weekday","monthWeekday":{"weekday":"friday","weekOfMonth":3}}}""")
        }
        val vm = vm(core)
        vm.loadRepeats()
        val options = vm.state.value.repeats!!
        assertEquals("DUE_DATE", options.repeatFrom)
        assertEquals(MonthWeekday("friday", 3), options.pattern!!.monthWeekday)
    }

    // ── AATD-51: Who — someone else ───────────────────────────────────────────────────────────

    @Test
    fun `AATD-51 assigning by email adds them to the task's list, then assigns the member`() = runTest {
        val core = core().apply { answer("inviteToList", """{"queued":false,"member":{"userId":"u-new","role":"member"}}""") }
        val vm = vm(core)
        vm.assignByEmail("  new@example.com ")
        val invite = core.last("inviteToList")!!
        assertEquals(JsonPrimitive("l1"), invite["listId"])
        assertEquals(JsonPrimitive("new@example.com"), invite["email"])
        assertEquals(JsonPrimitive("member"), invite["role"])
        assertEquals(JsonPrimitive("u-new"), changes(core)["assigneeId"])
        assertTrue(vm.state.value.someoneElse.assigned)
    }

    @Test
    fun `AATD-51 a task in no list cannot be assigned to someone new, and says so`() = runTest {
        val core = core(detail().replace(""""listChips":[{"id":"l1","name":"Home","color":"#fff"}]""", """"listChips":[]"""))
        val vm = vm(core)
        vm.assignByEmail("new@example.com")
        assertFalse(core.kinds().contains("inviteToList"))
        assertTrue(vm.state.value.someoneElse.needsList)
    }

    @Test
    fun `AATD-51 a refused invitation is shown, not swallowed`() = runTest {
        val core = core().apply { answer("inviteToList") { throw CoreFailure(FailureKind.REFUSED, "Not allowed") } }
        val vm = vm(core)
        vm.assignByEmail("new@example.com")
        assertEquals("Not allowed", vm.state.value.someoneElse.error)
        assertFalse(core.kinds().contains("updateTask"))
    }

    @Test
    fun `AATD-51 people are suggested by searchUsers for this task, from two characters and not for an email`() = runTest {
        val core = core().apply { answer("searchUsers", """[{"id":"u-di","name":"Di","email":"di@example.com"},{"id":"u-x","name":"X"}]""") }
        val vm = timedVm(core)
        vm.searchPeople("d")
        vm.searchPeople("di@ex")
        advanceTimeBy(TaskDetailViewModel.SEARCH_DEBOUNCE_MS + 1)
        assertFalse(core.kinds().contains("searchUsers"))
        vm.searchPeople("di")
        advanceTimeBy(TaskDetailViewModel.SEARCH_DEBOUNCE_MS + 1)
        assertEquals(JsonPrimitive("t1"), core.last("searchUsers")!!["taskId"])
        assertEquals(listOf("u-di"), vm.state.value.someoneElse.suggestions.map { it.id })
    }

    // ── AATD-52: attachments, occurrences, project fields, timer ──────────────────────────────

    @Test
    fun `AATD-52 the attachments strip lists every file the core knows on the task`() = runTest {
        val core = core().apply {
            answer("attachments", """{"files":[{"id":"f1","name":"a.png","size":10,"mimeType":"image/png","isCached":true,"path":"/x/a.png"},
                {"id":"f2","name":"b.pdf","mimeType":"application/pdf","isCached":false,"path":"/x/b.pdf"}]}""")
        }
        val vm = vm(core)
        assertEquals(listOf("f1", "f2"), vm.state.value.attachments.map { it.id })
        assertTrue(vm.state.value.attachments[0].isImage)
        assertFalse(vm.state.value.attachments[1].isImage)
    }

    @Test
    fun `AATD-52 occurrences come from the task the core sends`() = runTest {
        val vm = vm(core(detail(task = ""","previousOccurrenceId":"t0","nextOccurrenceId":"t2"""")))
        val task = vm.state.value.detail!!.task
        assertEquals("t0", task.previousOccurrenceId)
        assertEquals("t2", task.nextOccurrenceId)
    }

    @Test
    fun `AATD-52 project fields are read from the core and a set sends the field's value`() = runTest {
        val rows = """{"projectId":"p1","rows":[{"id":"fd1","name":"Size","dataType":"single_select",
            "options":[{"id":"o1","name":"S"},{"id":"o2","name":"L"}],"value":"o1"}]}"""
        val core = core().apply {
            answer("taskProjectFields", rows)
            answer("setTaskProjectField", rows.replace("\"value\":\"o1\"", "\"value\":\"o2\""))
        }
        val vm = vm(core)
        assertEquals("Size", vm.state.value.projectFields.single().name)
        vm.setProjectField("fd1", JsonPrimitive("o2"))
        val sent = core.last("setTaskProjectField")!!
        assertEquals(JsonPrimitive("fd1"), sent["fieldId"])
        assertEquals(JsonPrimitive("o2"), sent["value"])
        assertEquals(JsonPrimitive("o2"), vm.state.value.projectFields.single().value)
        vm.setProjectField("fd1", null)
        assertEquals(JsonNull, core.last("setTaskProjectField")!!["value"])
    }

    @Test
    fun `AATD-52 a refused project field names the field and iOS's reason`() = runTest {
        val core = core().apply {
            answer("taskProjectFields", """{"rows":[{"id":"fd1","name":"Size","dataType":"text"}]}""")
            answer("setTaskProjectField") { throw CoreFailure(FailureKind.REFUSED, "auth_required") }
        }
        val vm = vm(core)
        vm.setProjectField("fd1", JsonPrimitive("x"))
        assertEquals(ProjectFieldError("fd1", ProjectFieldError.Reason.AUTH_REQUIRED), vm.state.value.projectFieldError)
    }

    @Test
    fun `AATD-52 the timer starts and stops through the core`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.startTimer()
        assertEquals(JsonPrimitive("t1"), core.last("startTimer")!!["taskId"])
        vm.stopTimer()
        assertEquals(JsonPrimitive("t1"), core.last("stopTimer")!!["taskId"])
    }

    @Test
    fun `AATD-52 completing from the timer stops it first, then completes through completeTask`() = runTest {
        val core = core()
        vm(core).completeFromTimer()
        val order = core.kinds().filter { it == "stopTimer" || it == "completeTask" }
        assertEquals(listOf("stopTimer", "completeTask"), order)
    }

    // ── AATD-53: typing, autocomplete, queued attachments ─────────────────────────────────────

    @Test
    fun `AATD-53 an agent thinking about this task shows, and stops showing when it stops`() = runTest {
        val core = core()
        val vm = vm(core)
        core.emit("agentTyping", """{"change":"agentTyping","taskId":"t9","agentName":"Claude","active":true}""")
        assertNull(vm.state.value.typingAgent)
        core.emit("agentTyping", """{"change":"agentTyping","taskId":"t1","agentName":"Claude","active":true}""")
        assertEquals("Claude", vm.state.value.typingAgent?.name)
        core.emit("agentTyping", """{"change":"agentTyping","taskId":"t1","agentName":"Claude","active":false}""")
        assertNull(vm.state.value.typingAgent)
        core.emit("agentTyping", """{"change":"agentTyping","taskId":"t1","active":true}""")
        assertNull("an unnamed agent is still typing", vm.state.value.typingAgent?.name)
        assertTrue(vm.state.value.typingAgent != null)
    }

    @Test
    fun `AATD-53 the composer's popup is the core's suggestions, and a pick is put in by the core`() = runTest {
        val core = core().apply {
            answer("commentSuggestions", """{"trigger":{"kind":"mention","start":4,"query":"cl"},
                "items":[{"kind":"mention","id":"u-c","label":"Claude","secondary":null,"isAgent":true,"completed":false}]}""")
            answer("applyCommentSuggestion", """{"text":"Hey @[Claude](u-c) ","caret":19}""")
        }
        val vm = vm(core)
        vm.commentChanged("Hey @cl", 7)
        assertEquals(JsonPrimitive(7), core.last("commentSuggestions")!!["caret"])
        val item = vm.state.value.suggestions.single()
        assertTrue(item.isAgent)
        var applied: Pair<String, Int>? = null
        vm.applySuggestion("Hey @cl", 7, item) { text, caret -> applied = text to caret }
        val sent = core.last("applyCommentSuggestion")!!
        assertEquals(JsonPrimitive("mention"), sent["triggerKind"])
        assertEquals(JsonPrimitive("u-c"), sent["id"])
        assertEquals(JsonPrimitive("Claude"), sent["label"])
        assertEquals("Hey @[Claude](u-c) " to 19, applied)
        assertTrue("the popup closes", vm.state.value.suggestions.isEmpty())
    }

    @Test
    fun `AATD-53 text with no trigger closes the popup`() = runTest {
        val core = core().apply { answer("commentSuggestions", """{"trigger":null,"items":[]}""") }
        val vm = vm(core)
        vm.commentChanged("plain", 5)
        assertTrue(vm.state.value.suggestions.isEmpty())
        assertEquals(CommentSuggestion(kind = "mention", id = "x", label = "X"), CommentSuggestion(kind = "mention", id = "x", label = "X"))
    }

    @Test
    fun `AATD-53 queued files go with the text when Send is pressed, the text riding the first`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.queueFile("/c/1-a.png", "a.png", "image/png")
        vm.queueFile("/c/2-b.pdf", "b.pdf", "application/pdf")
        vm.queueFile("/c/3-c.txt", "c.txt", "text/plain")
        vm.unqueueFile("/c/3-c.txt")
        assertEquals(2, vm.state.value.queued.size)
        assertFalse("nothing is uploaded before Send", core.kinds().contains("attachFile"))

        vm.postComment("  Here they are ", replyTo = "c7")
        val sent = core.sent.filter { (it["kind"] as JsonPrimitive).content == "attachFile" }
        assertEquals(listOf("a.png", "b.pdf"), sent.map { (it["name"] as JsonPrimitive).content })
        assertEquals(JsonPrimitive("Here they are"), sent[0]["content"])
        assertFalse(sent[1].containsKey("content"))
        assertEquals(JsonPrimitive("c7"), sent[0]["parentCommentId"])
        assertFalse("the text went with the file", core.kinds().contains("postComment"))
        assertTrue(vm.state.value.queued.isEmpty())
    }

    @Test
    fun `AATD-53 files alone can be sent, and the same file is not queued twice`() = runTest {
        val core = core()
        val vm = vm(core)
        vm.queueFile("/c/1-a.png", "a.png", "image/png")
        vm.queueFile("/c/1-a.png", "a.png", "image/png")
        assertEquals(1, vm.state.value.queued.size)
        vm.postComment("   ")
        assertEquals(1, core.kinds().count { it == "attachFile" })
        assertFalse(core.last("attachFile")!!.containsKey("content"))
    }
}
