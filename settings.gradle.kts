pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // The Kotlin half of the core's certificate verifier (AATD-100). It is published only here,
        // on the rustls project's own archive, so this repository may serve that one group and nothing else.
        exclusiveContent {
            forRepository {
                maven("https://github.com/rustls/rustls-platform-verifier/raw/maven-archive/android-release-support/maven/")
            }
            filter { includeGroup("org.rustls") }
        }
    }
}

rootProject.name = "astrid-android"
include(":app", ":corebridge", ":baselineprofile")
