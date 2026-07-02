plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.remotecompose.server.MainKt")
}

dependencies {
    // Server compiles ONLY against the Screen/RenderContext contract in :core-ui.
    implementation(project(":core-ui"))

    // Feature modules are runtime plugins: discovered via ServiceLoader, never imported.
    // Every :feature-* subproject is wired in automatically, so adding a feature needs
    // no change here.
    rootProject.subprojects
        .filter { it.name.startsWith("feature-") }
        .forEach { runtimeOnly(project(it.path)) }
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
