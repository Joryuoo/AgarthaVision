# AgarthaVision · Project Architecture

> MVVM + Clean Architecture layers, package structure, Gradle configuration,
> and dependency injection setup for the Android application.

---

## 1. Architectural Pattern

The app follows **MVVM + Use Cases + Repository** — the same layered approach described
in the SDD. Each layer has a single responsibility and communicates only with the layer
directly below it.

```
┌─────────────────────────────────────────────────────┐
│  Presentation Layer                                 │
│  Jetpack Compose screens + ViewModels               │
│  Reads state via StateFlow, dispatches user intents │
├─────────────────────────────────────────────────────┤
│  Domain Layer                                       │
│  Use Cases (business logic orchestration)           │
│  Pure Kotlin — no Android framework imports         │
├─────────────────────────────────────────────────────┤
│  Data Layer                                         │
│  Repositories, Room DAOs, Retrofit services,        │
│  CameraX wrappers, SyncQueueManager, DataStore     │
├─────────────────────────────────────────────────────┤
│  Core / Platform Services                           │
│  CameraX, WorkManager, Location, Connectivity       │
└─────────────────────────────────────────────────────┘
```

### Key Rules

1. **ViewModels** never import `Room`, `Retrofit`, or any data-layer class directly. They call Use Cases.
2. **Use Cases** are single-responsibility classes with one public `operator fun invoke(...)` or `suspend fun execute(...)`.
3. **Repositories** are the single source of truth. They decide whether to read from Room (local) or Retrofit (remote).
4. **Entities** (Room) live in `data`. **Domain models** (plain Kotlin data classes) live in `domain`. Mappers convert between them.

---

## 2. Gradle Module Structure

A single-module project for MVP simplicity, with clear package boundaries that can be
extracted into Gradle modules in Phase 2 if needed.

```
AgarthaVision/
├── app/                          # The single Android application module
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/agarthavision/
│       │   │   └── (packages — see §3)
│       │   ├── res/
│       │   └── AndroidManifest.xml
│       ├── test/                  # Unit tests (JVM)
│       └── androidTest/           # Instrumented tests (device)
├── build.gradle.kts               # Root build file
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml         # Version catalog (single source for all deps)
├── package.json                   # Bun scripts + hooks (see 01_ENVIRONMENT_SETUP.md)
├── commitlint.config.js
└── docs/                          # This plan + design system + ADRs
    ├── 00_PROJECT_OVERVIEW.md
    ├── ...
    └── adr/                       # Architecture Decision Records
```

---

## 3. Package Structure

Every package maps to either a **feature** (vertical slice) or a **shared layer** (horizontal).

```
com.agarthavision/
│
├── AgarthaVisionApp.kt                  # @HiltAndroidApp Application class
├── MainActivity.kt                      # Single-activity host
│
├── core/                                # Shared utilities — no feature logic
│   ├── di/                              # Hilt modules (AppModule, DatabaseModule, NetworkModule)
│   ├── network/                         # Retrofit client, interceptors, API base
│   ├── database/                        # Room database class, migrations
│   ├── sync/                            # SyncQueueManager, WorkManager workers
│   ├── location/                        # LocationProvider wrapper
│   ├── connectivity/                    # NetworkMonitor (ConnectivityManager wrapper)
│   ├── datastore/                       # DataStore preferences wrapper
│   └── util/                            # Extensions, formatters, UUID generator
│
├── domain/                              # Pure Kotlin — domain models + use cases
│   ├── model/                           # Domain data classes (Sample, Detection, EPGResult, Report, etc.)
│   ├── repository/                      # Repository interfaces (contracts only)
│   └── usecase/                         # Use case classes grouped by feature
│       ├── capture/                     # CaptureSampleUseCase, TransmitPayloadUseCase
│       ├── inference/                   # FetchInferenceResultUseCase
│       ├── validation/                  # ApproveSampleUseCase, EditDetectionUseCase, RejectFindingsUseCase
│       └── reports/                     # GenerateSessionReportUseCase, FetchDetailedRecordsUseCase
│
├── data/                                # Implementations — Room, Retrofit, mappers
│   ├── local/                           # Room entities, DAOs, type converters
│   │   ├── entity/                      # SampleEntity, DetectionEntity, EPGEntity, etc.
│   │   ├── dao/                         # SampleDao, DetectionDao, ValidationDao, etc.
│   │   └── mapper/                      # Entity ↔ Domain model mappers
│   ├── remote/                          # Retrofit API definitions + DTOs
│   │   ├── api/                         # InferenceApi, ReportApi, SyncApi
│   │   ├── dto/                         # InferenceRequestDto, InferenceResultDto, etc.
│   │   └── mapper/                      # DTO ↔ Domain model mappers
│   └── repository/                      # Repository implementations (SampleRepositoryImpl, etc.)
│
├── ui/                                  # Presentation layer — Compose screens + ViewModels
│   ├── theme/                           # AgarthaLightColors, AgarthaRadius, AgarthaTypography, KomoUI setup
│   ├── navigation/                      # NavHost, route definitions, screen graph
│   ├── components/                      # Custom composables (not in KomoUI)
│   │   ├── MicroscopyViewport.kt
│   │   ├── DetectionOverlay.kt
│   │   ├── EpgReadout.kt
│   │   ├── BiologicalWindowChip.kt
│   │   ├── OfflineQueueBadge.kt
│   │   ├── BottomNavBar.kt
│   │   ├── GeoMapMarker.kt
│   │   └── AuditTimeline.kt
│   ├── capture/                         # CaptureScreen, CaptureViewModel
│   ├── queue/                           # QueueScreen, QueueViewModel
│   ├── validate/                        # ValidateScreen, ValidateViewModel
│   ├── reports/                         # ReportsScreen, ReportsViewModel, AdminDashboard
│   └── settings/                        # SettingsScreen, SettingsViewModel
│
└── worker/                              # WorkManager workers
    ├── SyncWorker.kt                    # Upload queued samples to cloud
    └── ReportGenerationWorker.kt        # Background PDF report generation
```

### Why This Structure

- **Vertical slices** (`ui/capture/`, `ui/validate/`) keep each screen's ViewModel and composables together — easy to find.
- **Horizontal layers** (`domain/`, `data/`, `core/`) enforce dependency direction and make testing straightforward.
- **`domain/` has zero Android imports** — pure Kotlin, unit-testable without Robolectric.
- **Custom components** (`ui/components/`) are the 8 composables from the design system that KomoUI doesn't ship (see `05_DESIGN_SYSTEM_KOMOUI.md` §6).

---

## 4. Version Catalog (`gradle/libs.versions.toml`)

A single file controls all dependency versions across the project. Every team member
references versions from here — never hard-code versions in `build.gradle.kts`.

```toml
[versions]
kotlin = "2.2.10-RC"
agp = "9.2.1"
compose-bom = "2026.02.01"
komoui = "0.3.0"
hilt = "2.59.2"
room = "2.7.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
camerax = "1.4.1"
workmanager = "2.10.0"
datastore = "1.1.2"
coroutines = "1.9.0"
navigation = "2.8.5"
lifecycle = "2.8.7"
coil = "2.7.0"
ktlint-plugin = "12.1.2"
detekt = "1.23.7"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }

# KomoUI
komoui = { group = "io.github.derangga", name = "komoui", version.ref = "komoui" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }

# Network
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

# CameraX
camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }

# Background + Async
workmanager = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Lifecycle
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# DataStore
datastore = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Image loading
coil = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
compose-test = { group = "androidx.compose.ui", name = "ui-test-junit4" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.2.10-RC-2.0.2" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint-plugin" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

---

## 5. Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.hilt)                apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}
```

---

## 6. App `build.gradle.kts` (Key Sections)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
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
            buildConfigField("Boolean", "USE_MOCK_API", "true")
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("Boolean", "USE_MOCK_API", "false")
            buildConfigField("String", "API_BASE_URL", "\"https://api.agarthavision.com\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)

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

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

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

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.test)
}
```

---

## 7. Hilt Dependency Injection Setup

### Application Class

```kotlin
// com/agarthavision/AgarthaVisionApp.kt
@HiltAndroidApp
class AgarthaVisionApp : Application()
```

### Hilt Modules (in `core/di/`)

```kotlin
// DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AgarthaDatabase =
        Room.databaseBuilder(ctx, AgarthaDatabase::class.java, "agarthavision.db")
            .build()

    @Provides fun provideSampleDao(db: AgarthaDatabase) = db.sampleDao()
    @Provides fun provideDetectionDao(db: AgarthaDatabase) = db.detectionDao()
    // ... one per DAO
}

// NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = BODY })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // inference can take time
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides fun provideInferenceApi(retrofit: Retrofit): InferenceApi =
        retrofit.create(InferenceApi::class.java)
}
```

---

## 8. State Machine — Sample Transaction States

The SDD defines a strict state machine. Encode it as a sealed class:

```kotlin
// domain/model/SampleStatus.kt
enum class SampleStatus(val value: String) {
    CAPTURE_STARTED("capture_started"),
    CAPTURED("captured"),
    CAPTURE_FAILED("capture_failed"),
    CAPTURE_INVALID("capture_invalid"),
    PAYLOAD_PACKAGED("payload_packaged"),
    QUEUED_FOR_UPLOAD("queued_for_upload"),
    UPLOADED("uploaded"),
    PROCESSING("processing"),
    PROCESSED("processed"),
    INFERENCE_FAILED("inference_failed"),
    PENDING_VALIDATION("pending_validation"),
    VALIDATED("validated"),
    FINDINGS_REJECTED("findings_rejected"),
    UNUSABLE("unusable"),
    REPORT_QUEUED("report_queued"),
    REPORT_GENERATED("report_generated"),
    REPORT_FAILED("report_failed"),
    QUEUED_FOR_SYNC("queued_for_sync"),
    SYNCED("synced");
}
```

Guard transitions in the repository layer — never allow an invalid state jump.

---

## 9. Navigation Graph

```kotlin
// ui/navigation/AgarthaNavGraph.kt
sealed class Screen(val route: String) {
    data object Capture    : Screen("capture")
    data object Queue      : Screen("queue")
    data object Validate   : Screen("validate/{sampleId}") {
        fun createRoute(sampleId: String) = "validate/$sampleId"
    }
    data object Reports    : Screen("reports")
    data object Admin      : Screen("admin")
    data object Settings   : Screen("settings")
}
```

Bottom navigation on phones: Capture · Queue · Validate · Reports · Settings.
Sidebar on tablets (if supported later).
