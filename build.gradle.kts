/**
 * Every resolved version is locked (ADEV-352): `gradle.lockfile` per project, plus
 * `buildscript-gradle.lockfile` for the plugins themselves. A build that would resolve anything
 * else fails, so a transitive version cannot change without a commit, and the Socket gate in
 * astrid-web checks every version the lockfiles name. Upgrading: docs/DEPENDENCIES.md.
 */
buildscript {
    configurations.classpath { resolutionStrategy.activateDependencyLocking() }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

/**
 * The version of the certificate verifier's Kotlin half (AATD-100). It must be exactly the
 * `rustls-platform-verifier-android` crate's in core/Cargo.lock, so it is read from there, not written
 * twice. corebridge depends on it; the app builds its network-security config from it.
 */
val rustlsPlatformVerifierVersion: Provider<String> by extra(
    providers.fileContents(layout.projectDirectory.file("core/Cargo.lock")).asText.map { lock ->
        Regex("""name = "rustls-platform-verifier-android"\s*\nversion = "([^"]+)"""").find(lock)?.groupValues?.get(1)
            ?: error("rustls-platform-verifier-android is not in core/Cargo.lock")
    },
)

allprojects {
    dependencyLocking { lockAllConfigurations() }
}
