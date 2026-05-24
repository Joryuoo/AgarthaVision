plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.agarthavision"
    compileSdk {
        version =
            release(36) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "com.agarthavision"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-mvp"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "SUPABASE_URL",
                "\"${project.findProperty("SUPABASE_URL_DEV") ?: ""}\"",
            )
            buildConfigField(
                "String",
                "SUPABASE_ANON_KEY",
                "\"${project.findProperty("SUPABASE_ANON_KEY_DEV") ?: ""}\"",
            )
            buildConfigField(
                "String",
                "INFERENCE_URL",
                "\"${project.findProperty("INFERENCE_URL_DEV") ?: ""}\"",
            )
            buildConfigField(
                "String",
                "INFERENCE_API_KEY",
                "\"${project.findProperty("INFERENCE_API_KEY") ?: ""}\"",
            )
        }
        release {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "SUPABASE_URL",
                "\"${project.findProperty("SUPABASE_URL_PROD") ?: ""}\"",
            )
            buildConfigField(
                "String",
                "SUPABASE_ANON_KEY",
                "\"${project.findProperty("SUPABASE_ANON_KEY_PROD") ?: ""}\"",
            )
            buildConfigField(
                "String",
                "INFERENCE_URL",
                "\"${project.findProperty("INFERENCE_URL_PROD") ?: ""}\"",
            )
            buildConfigField(
                "String",
                "INFERENCE_API_KEY",
                "\"${project.findProperty("INFERENCE_API_KEY") ?: ""}\"",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.foundation.layout)
    implementation(libs.play.services.location)
    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)
    implementation(libs.camerax.mlkit)
    implementation(libs.camerax.extensions)
    implementation("androidx.compose.material:material-icons-extended")
    // Core
    implementation(libs.core.ktx)
    implementation(libs.exifinterface)

    // Activity
    implementation(libs.activity.compose)

    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)

    // KomoUI
    implementation(libs.komoui)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Supabase (BOM aligns all sub-module versions)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)

    // Background + Async
    implementation(libs.workmanager)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Navigation + Lifecycle
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)

    // DataStore
    implementation(libs.datastore)

    // Image loading
    implementation(libs.coil)

    // Location
    implementation(libs.play.services.location)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.test)
}
