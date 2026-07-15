plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api("androidx.compose.remote:remote-core:1.0.0-alpha14")
    api("androidx.compose.remote:remote-creation-core:1.0.0-alpha14")
    api("androidx.compose.remote:remote-creation-jvm:1.0.0-alpha14")

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
