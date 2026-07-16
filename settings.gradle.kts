pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // AndroidX snapshot repo (androidx-main) — pinned build for reproducibility.
        // Provides androidx.compose.remote:*:1.0.0-SNAPSHOT with the JSON document
        // creation API (androidx.compose.remote.creation.json.RemoteComposeJsonParser).
        // Bump the build id to pick up a newer snapshot: https://androidx.dev/snapshots/builds
        maven {
            url = uri("https://androidx.dev/snapshots/builds/15872938/artifacts/repository")
            content { includeGroupByRegex("androidx\\.compose\\.remote.*") }
        }
    }
}

rootProject.name = "remote-compose-server"

include(":core-ui", ":feature-profile", ":feature-samples", ":dashboard-server", ":server")
