// Pure Kotlin/JVM — no Android dependency. This is deliberate (see the
// project README, "Why :protocol has no Android dependency"): it keeps the
// frame math unit-testable on any JVM and reusable outside the Android app
// (e.g. a future Home Assistant / Python bridge, per the build spec §4 step 12).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Deliberately no `kotlin { jvmToolchain(...) }` here: that forces Gradle to
// provision a specific JDK, which needs network access to a toolchain
// repository the first time. Instead just pin the Kotlin compiler's bytecode
// target directly so it matches `java.targetCompatibility` above, and the
// build runs on whatever JDK launched Gradle (17+).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
