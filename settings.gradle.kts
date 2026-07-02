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
    }
}

rootProject.name = "remote-compose-server"

include(":core-ui", ":feature-profile", ":feature-samples", ":dashboard-server", ":server")
