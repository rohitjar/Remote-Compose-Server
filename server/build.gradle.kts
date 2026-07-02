plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("com.remotecompose.server.MainKt")
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":feature-profile"))
    implementation(project(":feature-samples"))

    // DocumentBuilder authors documents directly with the writer, so depend explicitly.
    implementation("androidx.compose.remote:remote-core:1.0.0-alpha14")
    implementation("androidx.compose.remote:remote-creation-core:1.0.0-alpha14")
    implementation("androidx.compose.remote:remote-creation-jvm:1.0.0-alpha14")

    // JSON parsing (LayoutConfig / editor payloads)
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
