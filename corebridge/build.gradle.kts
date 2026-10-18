// The door into astrid-core: the UniFFI-generated Kotlin (never edited by hand), the native
// libraries scripts/build-core.sh puts in src/main/jniLibs, and the small Kotlin facade the app calls.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.gracefultools.astrid.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(libs.kotlinx.coroutines.android)
    // The generated bindings mark the API-34 cleaner @RequiresApi.
    implementation(libs.androidx.annotation)
    api(libs.kotlinx.serialization.json)
    implementation("${libs.jna.aar.get().module}:${libs.versions.jna.get()}@aar")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.jna.jvm)
}

// The JVM unit tests load the host build of the crate (scripts/build-core.sh puts it there), so the
// bridge is tested against the real core, not a stand-in.
tasks.withType<Test>().configureEach {
    systemProperty("jna.library.path", file("src/test/resources/native").absolutePath)
}
