package com.gracefultools.astrid.wire

import com.gracefultools.astrid.model.AccountUser
import com.gracefultools.astrid.model.AssigneeOption
import com.gracefultools.astrid.model.ChatMessage
import com.gracefultools.astrid.model.CommentRow
import com.gracefultools.astrid.model.FileRow
import com.gracefultools.astrid.model.ListDetail
import com.gracefultools.astrid.model.ListInvite
import com.gracefultools.astrid.model.ListPick
import com.gracefultools.astrid.model.ListPicks
import com.gracefultools.astrid.model.ListSummary
import com.gracefultools.astrid.model.ListSupportFlags
import com.gracefultools.astrid.model.MonthWeekday
import com.gracefultools.astrid.model.ReminderPick
import com.gracefultools.astrid.model.RepeatPattern
import com.gracefultools.astrid.model.RepeatPreset
import com.gracefultools.astrid.model.TaskFields
import com.gracefultools.astrid.model.UserSummary
import com.gracefultools.astrid.ui.agents.HubAgent
import com.gracefultools.astrid.ui.agents.HubCredential
import com.gracefultools.astrid.ui.filters.FilterGroup
import com.gracefultools.astrid.ui.filters.FilterPick
import com.gracefultools.astrid.ui.sidebar.SidebarSections
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app against the pinned core's wire schema (AATD-94). See [WireSchemaGate] for why.
 *
 * The first three tests prove the gate can fail; the last two run it over the app.
 */
class WireSchemaContractTest {
    private val command = WireSchemaGate.load("command.json")
    private val rule = WireSchemaGate.load("rule.json")
    private val wire = WireSchemaGate.load("wire.json")

    /**
     * Response DTOs paired with the one core type each mirrors. **Adding a pair is the deliberate
     * act**, as in the core's `WireModels`: everything else the app decodes is a projection the core
     * still assembles by hand with `serde_json::json!` (`app/dispatch/row_json.rs`), so there is no
     * type to hold it against yet. The core's task to type those rows widens this map.
     */
    private val pairs: Map<SerialDescriptor, String> = mapOf(
        CommentRow.serializer().descriptor to "CommentRow",
        ChatMessage.serializer().descriptor to "MessageRow",
        FileRow.serializer().descriptor to "FileRow",
        AssigneeOption.serializer().descriptor to "AssigneeOption",
        // The core never sent `isSelected` on a list pick (a list is selected by being in
        // `selected`); the field this gate found on Android's copy was dead, and is gone.
        ListPick.serializer().descriptor to "ListPick",
        ListPicks.serializer().descriptor to "ListPicks",
        ListInvite.serializer().descriptor to "ListInvite",
        FilterGroup.serializer().descriptor to "FilterGroup",
        FilterPick.serializer().descriptor to "FilterPick",
        ReminderPick.serializer().descriptor to "ReminderPick",
        RepeatPreset.serializer().descriptor to "RepeatPreset",
        MonthWeekday.serializer().descriptor to "MonthWeekday",
        RepeatPattern.serializer().descriptor to "CustomRepeatingPattern",
        TaskFields.serializer().descriptor to "Task",
        ListSummary.serializer().descriptor to "TaskList",
        ListDetail.serializer().descriptor to "TaskList",
        ListSupportFlags.serializer().descriptor to "ListSupports",
        AccountUser.serializer().descriptor to "User",
        UserSummary.serializer().descriptor to "User",
        SidebarSections.serializer().descriptor to "Sections",
        HubAgent.serializer().descriptor to "AgentRow",
        HubCredential.serializer().descriptor to "CredentialRow",
    )

    @Test
    fun a_renamed_response_field_is_caught_per_type_aatd_94() {
        // `authorName` is on MessageRow too, so a union of all names would still hold it.
        val renamed = renameProperty(wire, "CommentRow", "authorName", "writtenBy")
        val problems = WireSchemaGate.responseProblems(mapOf(CommentRow.serializer().descriptor to "CommentRow"), renamed)
        assertTrue("got $problems", problems.any { "CommentRow" in it && "authorName" in it })
        assertEquals(emptyList<String>(), WireSchemaGate.responseProblems(mapOf(CommentRow.serializer().descriptor to "CommentRow"), wire))
    }

    @Test
    fun a_renamed_request_field_or_kind_is_caught_aatd_94() {
        val source = """
            core.run(command("list", "listId" to id))
            core.run(command("noSuchKind"))
        """.trimIndent()
        val calls = WireSchemaGate.calls("Fake.kt", source)
        val variants = WireSchemaGate.variants(command, rule)
        assertEquals(listOf("Fake.kt:2: unknown command kind \"noSuchKind\""), WireSchemaGate.requestProblems(calls, variants))

        val renamed = variants + ("list" to (variants.getValue("list") - "listId" + "id"))
        assertEquals(
            listOf("Fake.kt:1: \"list\" has no field \"listId\"", "Fake.kt:2: unknown command kind \"noSuchKind\""),
            WireSchemaGate.requestProblems(calls, renamed),
        )
    }

    @Test
    fun the_scanner_reads_multi_line_calls_with_nested_parens_and_strings_aatd_94() {
        val source = """
            fun command(kind: String, vararg fields: Pair<String, Any?>) = TODO()
            val x = command(
                "updateTask",
                "taskId" to task.id,
                "title" to "a (quoted) \"title\", with commas",
                "dueAt" to listOf(1, 2).map { it(3) },
            )
            val y = command(kindVar, "a" to 1)
            val z = command("lists", *extra)
            val w = command("createTask", "title" to t, *listOfNotNull(a?.let { "assigneeId" to it }).toTypedArray(), *more(d).toTypedArray())
            private fun more(d: D) = listOf("priority" to d.p)
        """.trimIndent()
        assertEquals(
            listOf(
                WireSchemaGate.Call("F.kt:2", "updateTask", listOf("taskId", "title", "dueAt"), spread = false),
                WireSchemaGate.Call("F.kt:8", null, listOf("a"), spread = false),
                WireSchemaGate.Call("F.kt:9", "lists", emptyList(), spread = true),
                WireSchemaGate.Call("F.kt:10", "createTask", listOf("title", "assigneeId", "priority"), spread = false),
            ),
            WireSchemaGate.calls("F.kt", source),
        )
    }

    @Test
    fun every_request_the_app_sends_is_one_the_core_knows_aatd_94() {
        val roots = listOf(File("src/main/java"), File("../corebridge/src/main/java/com"))
        val calls = roots.flatMap { root ->
            root.walkTopDown().filter { it.extension == "kt" }.flatMap { WireSchemaGate.calls(it.path, it.readText()) }
        }
        assertTrue("found only ${calls.size} command( call sites — is the scanner reading the sources?", calls.size > 100)
        assertEquals(emptyList<String>(), WireSchemaGate.requestProblems(calls, WireSchemaGate.variants(command, rule)))
    }

    @Test
    fun every_paired_response_dto_decodes_only_what_its_core_type_has_aatd_94() {
        assertEquals(emptyList<String>(), WireSchemaGate.responseProblems(pairs, wire))
    }

    private fun renameProperty(doc: JsonObject, def: String, from: String, to: String): JsonObject {
        val defs = doc.getValue("\$defs").jsonObject
        val type = defs.getValue(def).jsonObject
        val props = type.getValue("properties").jsonObject
        val newProps = JsonObject(props.mapKeys { (k, _) -> if (k == from) to else k })
        val newType = JsonObject(type + ("properties" to newProps))
        return JsonObject(doc + ("\$defs" to JsonObject(defs + (def to newType))))
    }
}
