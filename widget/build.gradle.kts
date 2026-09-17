plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // Glance widgets are Compose: their composables need the Compose compiler
    // plugin exactly as :ui's do, even though nothing here draws a Compose UI
    // on screen in the usual sense.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.scrivtech.powerview.widget"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Kotlin 2.x removed the `kotlinOptions` DSL: assigning `jvmTarget` as a String
// is a hard error, not a deprecation warning, so this module could not configure
// at all under the pinned Kotlin 2.4.20. This is the replacement form, and it
// matches what :protocol has always used.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation(project(":ble"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Declared rather than leaned on transitively through Glance or :data.
    // This module's own code names `stringPreferencesKey` and
    // `MutablePreferences` directly, and Gradle does not put another module's
    // `implementation` dependencies on our compile classpath.
    implementation(libs.androidx.datastore.preferences)

    // Same reasoning for `collectAsState`.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)

    // For the widget configuration activity, which is an ordinary Compose
    // screen rather than a Glance one.
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit4)
}
