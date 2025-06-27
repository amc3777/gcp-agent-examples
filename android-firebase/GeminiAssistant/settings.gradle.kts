// settings.gradle.kts

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // --- HARDCODE ALL VERSIONS HERE ---
        // Versions taken from your libs.versions.toml [versions] section

        // Android Gradle Plugin (AGP)
        id("com.android.application") version "8.8.1"  // Your agp version

        // Kotlin Android Plugin
        id("org.jetbrains.kotlin.android") version "2.0.0" // Your kotlin version

        // Kotlin Serialization Plugin
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" // Your kotlin version

        // Kotlin Compose Compiler Plugin (Add if you apply it explicitly in app/build.gradle.kts)
        // id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" // Your kotlin version
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Gemini Assistant"
include(":app")