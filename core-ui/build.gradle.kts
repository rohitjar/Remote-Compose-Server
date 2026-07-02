plugins {
    kotlin("jvm")
}

dependencies {
    // AndroidX Remote Compose — Creation (pure JVM). Exposed as `api` because the
    // authoring types (RcScope, RemoteComposeWriter, RcContentScale, …) appear in the
    // public signatures that :feature-* and :server compile against.
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
