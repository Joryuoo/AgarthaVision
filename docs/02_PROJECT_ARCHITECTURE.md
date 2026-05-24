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
│   ├── di/                              # Hilt modules (DatabaseModule, SupabaseModule, InferenceModule, LocationModule)
│   ├── camera/                          # CameraManager (CameraX ImageAnalysis wrapper)
│   ├── database/                        # Room database class, migrations
│   ├── location/                        # LocationProvider (Fused), DeviceIdProvider
│   ├── session/                         # SessionManager (active recording session)
│   ├── connectivity/                    # NetworkMonitor (inference container reachability)
│   ├── datastore/                       # DataStore preferences wrapper
│   └── util/                            # Extensions, formatters, UUID generator
│
├── domain/                              # Pure Kotlin — domain models + use cases
│   ├── model/                           # Domain data classes (Sample, Detection, Session, LocationResult)
│   ├── repository/                      # Repository interfaces + LocationProvider contract
│   └── usecase/                         # Use case classes grouped by feature
│       ├── auth/                        # SignInUseCase, SignOutUseCase
│       ├── capture/                     # InferFrameUseCase, VerifySampleUseCase, RejectSampleUseCase
│       └── records/                     # ListSessionsUseCase, ExportSessionUseCase
│
├── data/                                # Implementations — Room, Retrofit, Supabase, mappers
│   ├── local/                           # Room entities, DAOs, type converters
│   │   ├── entity/                      # SessionEntity, SampleEntity, DetectionEntity
│   │   ├── dao/                         # SessionDao, SampleDao, DetectionDao
│   │   └── mapper/                      # Entity ↔ Domain model mappers
│   ├── remote/                          # Inference container API + DTOs (Retrofit)
│   │   ├── InferenceApi.kt
│   │   ├── dto/                         # InferenceResponseDto, PredictionDto, ImageMetaDto
│   │   └── mapper/                      # DTO ↔ Domain mappers
│   ├── supabase/                        # Supabase client wrapper + sync logic
│   │   ├── SupabaseClientProvider.kt
│   │   └── SyncSampleUseCase.kt
│   └── repository/                      # SampleRepositoryImpl, SessionRepositoryImpl, FlaggedFrameStore
│
├── ui/                                  # Presentation layer — Compose screens + ViewModels
│   ├── theme/                           # AgarthaLightColors, AgarthaRadius, AgarthaTypography, KomoUI setup
│   ├── navigation/                      # NavHost, route definitions, screen graph
│   ├── components/                      # Custom composables (not in KomoUI)
│   │   ├── MicroscopyViewport.kt
│   │   ├── DetectionOverlay.kt
│   │   ├── VerificationSheet.kt
│   │   ├── BottomNavBar.kt
│   │   └── DetectionToast.kt
│   ├── auth/                            # LoginScreen, LoginViewModel
│   ├── capture/                         # CaptureScreen, CaptureViewModel
│   ├── records/                         # RecordsScreen, SessionDetailScreen, SampleDetailScreen
│   └── settings/                        # SettingsScreen, SettingsViewModel
│
└── worker/                              # WorkManager workers (Phase 2+; not used in Phase 1)
```

> **Phase 1 MVP note:** the queue/validate/reports vertical slices and the WorkManager
> workers are out of scope for the MVP. Inference is synchronous per frame against a
> self-hosted FastAPI container ([ADR-003](adr/003-self-hosted-inference-container.md)),
> and sync to Supabase is a one-shot call at verify time — no background queue.
> See [03_MOBILE_APP_PLAN.md](03_MOBILE_APP_PLAN.md) and
> [04_CLOUD_BACKEND_PLAN.md](04_CLOUD_BACKEND_PLAN.md).

### Why This Structure

- **Vertical slices** (`ui/auth/`, `ui/capture/`, `ui/records/`) keep each screen's ViewModel and composables together — easy to find.
- **Horizontal layers** (`domain/`, `data/`, `core/`) enforce dependency direction and make testing straightforward.
- **`domain/` has zero Android imports** — pure Kotlin, unit-testable without Robolectric.
- **Custom components** (`ui/components/`) are the composables from the design system that KomoUI doesn't ship (see [05_DESIGN_SYSTEM_KOMOUI.md](05_DESIGN_SYSTEM_KOMOUI.md) §6).

---

## 4. Version Catalog (`gradle/libs.versions.toml`)

A single file controls all dependency versions across the project. Every team member
references versions from here — never hard-code versions in `build.gradle.kts`.

```toml
[versions]
kotlin = "2.2.10"
agp = "9.2.1"
compose-bom = "2026.02.01"
komoui = "0.3.0"
hilt = "2.59.2"
room = "2.7.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
camerax = "1.6.1"
workmanager = "2.10.0"      # Phase 2 only — not used in Phase 1
datastore = "1.1.2"
coroutines = "1.9.0"
navigation = "2.8.5"
lifecycle = "2.8.7"
coil = "2.7.0"
play-services-location = "21.3.0"
supabase = "2.6.0"           # supabase-kt BOM
ktor = "2.3.12"              # transitive dep of supabase-kt; pin explicitly
mockito-kotlin = "5.4.0"
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

# Location
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "play-services-location" }

# Supabase (Phase 1)
supabase-bom         = { group = "io.github.jan-tennert.supabase", name = "bom", version.ref = "supabase" }
supabase-postgrest   = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt" }
supabase-storage     = { group = "io.github.jan-tennert.supabase", name = "storage-kt" }
supabase-auth        = { group = "io.github.jan-tennert.supabase", name = "auth-kt" }
ktor-client-android  = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }

# Testing
junit = { group = "junit", name = "junit", version = "4.13.2" }
mockito-kotlin = { group = "org.mockito.kotlin", name = "mockito-kotlin", version.ref = "mockito-kotlin" }
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
            buildConfigField("String", "SUPABASE_URL",      "\"${project.findProperty("SUPABASE_URL_DEV")      ?: ""}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY_DEV") ?: ""}\"")
            buildConfigField("String", "INFERENCE_URL",     "\"${project.findProperty("INFERENCE_URL_DEV")     ?: ""}\"")
            buildConfigField("String", "INFERENCE_API_KEY", "\"${project.findProperty("INFERENCE_API_KEY")     ?: ""}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "SUPABASE_URL",      "\"${project.findProperty("SUPABASE_URL_PROD")      ?: ""}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.findProperty("SUPABASE_ANON_KEY_PROD") ?: ""}\"")
            buildConfigField("String", "INFERENCE_URL",     "\"${project.findProperty("INFERENCE_URL_PROD")     ?: ""}\"")
            buildConfigField("String", "INFERENCE_API_KEY", "\"${project.findProperty("INFERENCE_API_KEY")      ?: ""}\"")
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

    // Network (used by InferenceApi → self-hosted FastAPI container)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Supabase (Phase 1)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Location
    implementation(libs.play.services.location)

    // Async
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

    @Provides fun provideSessionDao(db: AgarthaDatabase)   = db.sessionDao()
    @Provides fun provideSampleDao(db: AgarthaDatabase)    = db.sampleDao()
    @Provides fun provideDetectionDao(db: AgarthaDatabase) = db.detectionDao()
}

// SupabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}

// InferenceModule.kt — talks to the self-hosted FastAPI container (ADR-003).
@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {
    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer ${BuildConfig.INFERENCE_API_KEY}")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HEADERS })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)   // per-frame inference budget
        .build()

    @Provides @Singleton
    fun provideInferenceApi(client: OkHttpClient): InferenceApi = Retrofit.Builder()
        .baseUrl(BuildConfig.INFERENCE_URL.let { if (it.endsWith("/")) it else "$it/" })
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(InferenceApi::class.java)
}
```

---

## 8. State Machine — Sample Lifecycle (Phase 1)

Phase 1's continuous-capture model collapses the original SDD state machine into a
much smaller set. Only states that result in a Room row are listed; `CANDIDATE`
(pre-inference) and `FLAGGED` (pre-verify, in-memory only) are transient and never
persisted.

```kotlin
// domain/model/SampleStatus.kt
enum class SampleStatus(val value: String) {
    VERIFIED("verified"),         // user accepted the detection; row written to Room
    SYNCED("synced"),             // also uploaded to Supabase
    SYNC_FAILED("sync_failed"),   // upload failed; retry on next session start
}
```

Transitions:

```
FLAGGED (in-memory) → user verifies → VERIFIED  → upload → SYNCED
                                              ╰── upload fails → SYNC_FAILED
FLAGGED (in-memory) → user rejects → discarded (no row)
```

Guard transitions in the repository layer. The Phase 2 SDD-aligned model (with
`CAPTURE_FAILED`, `INFERENCE_FAILED`, `REPORT_QUEUED`, etc.) is documented in
[ADR-002](adr/002-supabase-and-roboflow-for-mvp.md) §Phase 2 schema.

---

## 9. Navigation Graph (Phase 1)

```kotlin
// ui/navigation/AgarthaNavGraph.kt
sealed class Screen(val route: String) {
    data object Login    : Screen("login")
    data object Capture  : Screen("capture")
    data object Records  : Screen("records")
    data object SessionDetail : Screen("records/session/{sessionId}") {
        fun createRoute(sessionId: String) = "records/session/$sessionId"
    }
    data object SampleDetail  : Screen("records/sample/{sampleId}") {
        fun createRoute(sampleId: String) = "records/sample/$sampleId"
    }
    data object Settings : Screen("settings")
}
```

Bottom navigation on phones (visible after login): **Capture · Records · Settings**.
Login is full-screen and has no bottom nav. The Phase 2 sprint plan adds
Validate / Reports / Admin as separate destinations.
