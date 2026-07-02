plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

// Standalone JSON→.rc conversion service for the web editor / dashboard.
// Independent of the Kotlin DSL (:core-ui) — it authors documents from JSON config
// directly with the writer.
application {
    mainClass.set("com.remotecompose.dashboard.MainKt")
}

dependencies {
    implementation("androidx.compose.remote:remote-core:1.0.0-alpha14")
    implementation("androidx.compose.remote:remote-creation-core:1.0.0-alpha14")
    implementation("androidx.compose.remote:remote-creation-jvm:1.0.0-alpha14")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
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
