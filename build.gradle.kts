// Root build file. Per-module configuration lives in each module's own
// build.gradle.kts; this file only declares plugin versions once (via
// `apply false`) so every module resolves the same version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Load-bearing beyond :protocol. AGP 9 compiles the Android modules with
    // built-in Kotlin, and AGP 9.4.0 itself only depends on Kotlin 2.2.10. This
    // line puts kotlin-gradle-plugin 2.4.20 on the shared build classpath, so
    // built-in Kotlin compiles with 2.4.20, matching the Compose compiler plugin
    // pinned to the same catalog `kotlin` version. Without it the build does not
    // configure at all: AGP has already put a Kotlin plugin on the classpath, and
    // :protocol's kotlin.jvm request fails with "already on the classpath with an
    // unknown version".
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
