package com.gracefultools.astrid.ui.account

import com.gracefultools.astrid.FakeCore
import com.gracefultools.astrid.core.CoreFailure
import com.gracefultools.astrid.core.FailureKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonProfileViewModelTest {
    private val dana = """{"id":"u-them","name":"Dana","image":"https://astrid.cc/dana.png","createdAt":"2025-03-04T10:00:00.000Z",
        "isAIAgent":false,"stats":{"completed":3,"inspired":1,"supported":2},"isOwnProfile":false,"count":1,
        "rows":[{"id":"t1","title":"Plant the bulbs"}]}"""

    @Test
    fun `AATD-69 another person's profile is the core's one answer for their id`() = runTest {
        val core = FakeCore().apply { answer("profileTasks", dana) }
        val vm = PersonProfileViewModel(core, "u-them", UnconfinedTestDispatcher())
        assertEquals(JsonPrimitive("u-them"), core.last("profileTasks")!!["userId"])
        val person = vm.state.value.person!!
        assertEquals("Dana", person.name)
        assertEquals("2025-03-04T10:00:00.000Z", person.createdAt)
        assertEquals(3, person.stats?.completed)
        assertEquals(listOf("Plant the bulbs"), person.rows.map { it.title })
        assertFalse(person.isOwnProfile)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `AATD-69 a profile that cannot load says so rather than showing an empty person`() = runTest {
        val core = FakeCore().apply { answer("profileTasks") { throw CoreFailure(FailureKind.NOT_FOUND, "User not found") } }
        val vm = PersonProfileViewModel(core, "u-gone", UnconfinedTestDispatcher())
        assertNull(vm.state.value.person)
        assertEquals(FailureKind.NOT_FOUND, vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `AATD-69 your own id is recognised so the screen can show your editable profile`() = runTest {
        val core = FakeCore().apply { answer("profileTasks", dana.replace("\"isOwnProfile\":false", "\"isOwnProfile\":true")) }
        val vm = PersonProfileViewModel(core, "u-me", UnconfinedTestDispatcher())
        assertTrue(vm.state.value.person!!.isOwnProfile)
    }
}
