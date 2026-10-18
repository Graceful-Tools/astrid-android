package com.gracefultools.astrid.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AATD-1. The server matches `x-platform` by equality (astrid-web lib/analytics-events.ts), so a
 * typo is indistinguishable from sending nothing — the exact string is pinned here, not in review.
 */
class CorePlatformTest {
    @Test
    fun `AATD-1 the Android app names itself android-app`() {
        assertEquals("android-app", CorePlatform.ANDROID.wire)
    }
}
