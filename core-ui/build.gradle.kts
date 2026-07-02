plugins {
    kotlin("jvm")
}

dependencies {
    api("androidx.compose.remote:remote-core:1.0.0-alpha14")
    api("androidx.compose.remote:remote-creation-core:1.0.0-alpha14")
    api("androidx.compose.remote:remote-creation-jvm:1.0.0-alpha14")
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
