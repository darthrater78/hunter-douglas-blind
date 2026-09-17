// Root build file. Per-module configuration lives in each module's own
// build.gradle.kts; this file only declares plugin versions once (via
// `apply false`) so every module resolves the same version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
