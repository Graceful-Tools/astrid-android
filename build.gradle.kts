/**
 * Every resolved version is locked (ADEV-352): `gradle.lockfile` per project, plus
 * `buildscript-gradle.lockfile` for the plugins themselves. A build that would resolve anything
 * else fails, so a transitive version cannot change without a commit, and the Socket gate in
 * astrid-web checks every version the lockfiles name. Upgrading: docs/DEPENDENCIES.md.
 */
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
        // AGP 9.5.1 brings Bouncy Castle 1.80.2, which Socket refuses for a critical CVE. It is
        // build tooling (the plugins and lint), never in the APK, and 1.x keeps its API, so every
        // Bouncy Castle artifact is moved up to the version in gradle.properties. Drop this once
        // AGP's own is at least that (ADEV-352).
        val bouncyCastle = providers.gradleProperty("astrid.bouncyCastle").get()
        resolutionStrategy.eachDependency {
            if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) useVersion(bouncyCastle)
        }
    }
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
    // The same Bouncy Castle floor for lint's classpath (androidLintTool); see buildscript above.
    val bouncyCastle = providers.gradleProperty("astrid.bouncyCastle").get()
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on")) useVersion(bouncyCastle)
        }
    }
}
