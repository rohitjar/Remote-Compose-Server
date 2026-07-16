plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    // androidx-main snapshot (repo pinned in settings.gradle.kts) — brings the JSON
    // document creation API (androidx.compose.remote.creation.json.RemoteComposeJsonParser).
    api("androidx.compose.remote:remote-core:1.0.0-SNAPSHOT")
    api("androidx.compose.remote:remote-creation-core:1.0.0-SNAPSHOT")
    api("androidx.compose.remote:remote-creation-jvm:1.0.0-SNAPSHOT")

    // api: ScreenRequest.args is a kotlinx JsonObject, part of this module's public surface.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
