/**
 * The app's baseline profile and its cold-start benchmark (AATD-93), run on a device or emulator:
 *
 *   ./gradlew :app:generateBaselineProfile          # regenerate app/src/release/generated/baselineProfiles
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest   # cold start, with and without it
 *
 * Neither runs in predeploy: both need a device, and the profile is committed.
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.gracefultools.astrid.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The dev Mac measures on the astrid35 emulator; numbers from it compare runs, not phones.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

baselineProfile {
    // The connected emulator or phone, not a Gradle-managed device.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
