plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    application
}

application {
    mainClass.set("com.remotecompose.server.MainKt")
}

dependencies {
    // AndroidX Remote Compose — Creation (pure JVM, no Android SDK required)
    implementation("androidx.compose.remote:remote-core:1.0.0-alpha13")

    implementation("androidx.compose.remote:remote-creation-core:1.0.0-alpha13")
    implementation("androidx.compose.remote:remote-creation-jvm:1.0.0-alpha13")

    // JSON parsing
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
