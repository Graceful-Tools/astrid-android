package com.gracefultools.astrid.core

/**
 * Which client this is, stated on every request (`x-platform`), exactly as the server matches it.
 *
 * A closed set because the server compares by equality (astrid-web `lib/analytics-events.ts`) and
 * astrid-core's `api::Platform` refuses any value it does not know.
 */
enum class CorePlatform(val wire: String) {
    ANDROID("android-app"),
}
