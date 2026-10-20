package com.gracefultools.astrid

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AATD-73: the inbound-task address and the agent email domain are the brand's, not the strings'. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BrandStringsTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `AATD-73 an unconfigured build uses Astrid's addresses`() {
        assertEquals("remindme@astrid.cc", BuildConfig.ASTRID_INBOUND_TASK_EMAIL)
        assertEquals("remindme@astrid.cc", Brand.inboundTaskEmail)
        assertEquals("astrid.cc", BuildConfig.ASTRID_AGENT_EMAIL_DOMAIN)
        assertEquals("astrid.cc", Brand.agentEmailDomain)
    }

    @Test
    fun `AATD-73 the English copy reads exactly as before`() {
        val email = Brand.inboundTaskEmail
        assertEquals("Create tasks by sending emails to remindme@astrid.cc", context.getString(R.string.settings_appearance_email_to_task_footer, email))
        assertEquals("Self-task: Send TO remindme@astrid.cc", context.getString(R.string.settings_appearance_email_to_task_self, email))
        assertEquals("Assigned task: CC remindme@astrid.cc with one recipient", context.getString(R.string.settings_appearance_email_to_task_assigned, email))
        assertEquals("Group task: CC remindme@astrid.cc with multiple recipients", context.getString(R.string.settings_appearance_email_to_task_group, email))
        assertEquals("buddy.oc@astrid.cc", context.getString(R.string.settings_openclaw_agent_name_preview, "buddy", Brand.agentEmailDomain))
    }

    @Test
    @Config(qualifiers = "de")
    fun `AATD-73 a translation takes the brand's address too`() {
        assertEquals("Erstellen Sie Aufgaben, indem Sie E-Mails an help@example.com senden",
            context.getString(R.string.settings_appearance_email_to_task_footer, "help@example.com"))
        assertEquals("buddy.oc@example.com", context.getString(R.string.settings_openclaw_agent_name_preview, "buddy", "example.com"))
    }

    @Test
    fun `AATD-73 no locale bakes in an astrid_cc address`() {
        val leaks = File("src/main/res").listFiles { f -> f.name.startsWith("values") }.orEmpty()
            .mapNotNull { File(it, "strings.xml").takeIf(File::exists) }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if ("@astrid.cc" in line) "${f.parentFile.name}:${i + 1}" else null } }
        assertEquals(emptyList<String>(), leaks)
    }
}
