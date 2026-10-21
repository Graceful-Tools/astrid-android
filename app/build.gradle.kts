import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The brand this build is (AATD-72): `-PastridBrand=<name>` reads `brands/<name>/brand.properties`
 * (keys as the `-P` properties below) and layers `brands/<name>/res` over the app's own resources. A
 * `-P` property beats the file; with neither, every value is hosted Astrid's and the build is
 * exactly what it was. See brands/README.md.
 */
val brandName: String? = providers.gradleProperty("astridBrand").orNull?.trim()?.takeIf { it.isNotEmpty() }
val brandDir: File? = brandName?.let { name ->
    rootProject.file("brands/$name").also { require(it.isDirectory) { "astridBrand=$name: no brands/$name directory" } }
}
val brandProfile = Properties().apply {
    brandDir?.resolve("brand.properties")?.takeIf { it.isFile }?.reader()?.use(::load)
}

/** A brand value: the Gradle property, else the brand's profile, else [default]; blank counts as unset. */
fun brand(property: String, default: String): String =
    (providers.gradleProperty(property).orNull ?: brandProfile.getProperty(property))?.trim()?.takeIf { it.isNotEmpty() } ?: default

val brandBaseUrl = brand("astridBaseUrl", "https://astrid.cc").trimEnd('/')
val brandUrlScheme = brand("astridUrlScheme", "astrid").lowercase().also {
    require(Regex("[a-z][a-z0-9+.-]*").matches(it)) { "astridUrlScheme=$it is not a URL scheme" }
}
val brandBaseHost: String = URI(brandBaseUrl).host ?: error("astridBaseUrl=$brandBaseUrl has no host")

/**
 * The https hosts whose links open the app: `astridLinkHosts` (comma-separated), else the server's
 * host and, for an apex domain, its `www.` twin — astrid.cc and www.astrid.cc for hosted Astrid.
 * The manifest has room for two.
 */
val brandLinkHosts: List<String> = brand("astridLinkHosts", "").split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    .ifEmpty { listOf(brandBaseHost) + if (brandBaseHost.count { it == '.' } == 1) listOf("www.$brandBaseHost") else emptyList() }
    .let { (listOf(brandBaseHost) + it).distinct() }
    .also { require(it.size <= 2) { "astridLinkHosts: at most two hosts, the server's own included (got $it)" } }

/**
 * Whether Android verifies those hosts (AATD-76): only true once every host serves
 * `/.well-known/assetlinks.json` naming this package and its signing key (brands/README.md).
 * Astrid's site does not yet, so Astrid's is false and its links still offer a chooser.
 */
val brandLinkAutoVerify: Boolean = brand("astridLinkAutoVerify", "false").lowercase().let {
    require(it == "true" || it == "false") { "astridLinkAutoVerify=$it: true or false" }
    it == "true"
}

android {
    namespace = "com.gracefultools.astrid"
    compileSdk = 35

    defaultConfig {
        applicationId = brand("astridApplicationId", "com.gracefultools.astrid")
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.gracefultools.astrid.AstridTestRunner"

        // The brand's identity (iOS Brand.swift's Info.plist keys), set per build with Gradle
        // properties — `./gradlew assembleDebug -PastridBaseUrl=https://tasks.example.com`. Unset,
        // every value is hosted Astrid's, so the default build is unchanged (AATD-71).
        brandField("ASTRID_BASE_URL", brandBaseUrl)
        brandField("ASTRID_SUPPORT_EMAIL", brand("astridSupportEmail", "support@astrid.cc"))
        brandField("ASTRID_INBOUND_TASK_EMAIL", brand("astridInboundTaskEmail", "remindme@astrid.cc"))
        brandField("ASTRID_AGENT_EMAIL_DOMAIN", brand("astridAgentEmailDomain", "astrid.cc"))

        // The app's identity (AATD-72). The scheme must be the one the core accepts callbacks on and
        // the server delivers to (astrid-web BRAND.appUrlScheme); the hand-off client is the id the
        // server's DesktopClient registry knows this app by, the core's "android" when blank.
        brandField("ASTRID_URL_SCHEME", brandUrlScheme)
        brandField("ASTRID_LINK_HOSTS", brandLinkHosts.joinToString(","))
        brandField("ASTRID_HANDOFF_CLIENT_ID", brand("astridHandoffClientId", ""))

        // The in-app accent (AATD-75), iOS's three: BrandAccentColor, -HoverColor, -TextColor. Passed
        // raw; Brand checks each and falls back to Astrid's, so a typo is Astrid blue, not a crash.
        brandField("ASTRID_ACCENT_COLOR", brand("astridAccentColor", ""))
        brandField("ASTRID_ACCENT_HOVER_COLOR", brand("astridAccentHoverColor", ""))
        brandField("ASTRID_ACCENT_TEXT_COLOR", brand("astridAccentTextColor", ""))
        resValue("string", "app_name", brand("astridAppName", "Astrid"))
        resValue("string", "brand_wordmark", brand("astridWordmark", "astrid"))
        manifestPlaceholders += mapOf(
            "astridUrlScheme" to brandUrlScheme,
            "astridLinkHost" to brandLinkHosts.first(),
            "astridLinkHostAlt" to brandLinkHosts.last(),
            "astridLinkAutoVerify" to brandLinkAutoVerify.toString(),
        )
    }

    // The brand's icons and colours override the app's: build-type source sets outrank main, and
    // using them (not a product flavor) keeps every task name, so CI and predeploy are unchanged.
    brandDir?.resolve("res")?.takeIf { it.isDirectory }?.let { res ->
        sourceSets.getByName("debug").res.srcDir(res)
        sourceSets.getByName("release").res.srcDir(res)
    }

    // A real key comes from the environment (CI secrets, or keystore.properties kept out of git).
    // Without one, a release build is signed with the debug key so it can be installed and tested
    // — never uploaded (Play refuses a debug-signed bundle).
    signingConfigs {
        create("release") {
            val path = System.getenv("ASTRID_UPLOAD_KEYSTORE")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("ASTRID_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ASTRID_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("ASTRID_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (System.getenv("ASTRID_UPLOAD_KEYSTORE") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        // A string not yet translated falls back to English, on purpose, until AATD-23 covers it.
        warning += "MissingTranslation"
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

/** A `String` BuildConfig field holding [value]. */
fun com.android.build.api.dsl.DefaultConfig.brandField(field: String, value: String) {
    buildConfigField("String", field, "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
}

/**
 * The launcher shortcut (res/xml/shortcuts.xml) names the package and the scheme, and a resource
 * cannot take manifest placeholders, so it is generated from app/brand/shortcuts.xml (AATD-72).
 */
abstract class BrandShortcuts : DefaultTask() {
    @get:InputFile abstract val template: RegularFileProperty
    @get:Input abstract val applicationId: Property<String>
    @get:Input abstract val urlScheme: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun write() {
        val xml = template.get().asFile.readText()
            .replace("@APPLICATION_ID@", applicationId.get())
            .replace("@URL_SCHEME@", urlScheme.get())
        outputDir.get().asFile.resolve("xml").apply { mkdirs() }.resolve("shortcuts.xml").writeText(xml)
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<BrandShortcuts>("generate${variant.name.replaceFirstChar(Char::titlecase)}BrandShortcuts") {
            template.set(layout.projectDirectory.file("brand/shortcuts.xml"))
            applicationId.set(variant.applicationId)
            urlScheme.set(brandUrlScheme)
        }
        variant.sources.res?.addGeneratedSourceDirectory(task, BrandShortcuts::outputDir)
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":corebridge"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.coil.compose)
    implementation(libs.play.review.ktx)
    implementation(libs.play.app.update.ktx)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

tasks.withType<Test>().configureEach {
    systemProperty("jna.library.path", rootProject.file("corebridge/src/test/resources/native").absolutePath)
}
