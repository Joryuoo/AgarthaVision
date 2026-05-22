# AgarthaVision · Environment & Project Setup

> From a fresh machine to a working project on GitHub, ready for the team to clone.
>
> **Phase A** (done once by one person): Create the project, configure it, push to remote.
> **Phase B** (done by every team member): Clone, set up IDE, verify build.

---

## Phase A — Project Creation (One Person)

This phase is done once. The person who does it will have the project running
locally and pushed to GitHub. Everyone else follows Phase B.

---

### A.1 Prerequisites

Install these before creating the project.

#### JDK 21

KomoUI 0.3.0 requires Kotlin 2.2.x which targets JDK 21.

```bash
# macOS (Homebrew)
brew install openjdk@21

# Linux (SDKMAN — recommended for team consistency)
curl -s "https://get.sdkman.io" | bash
sdk install java 21.0.3-tem

# Windows (winget)
winget install EclipseAdoptium.Temurin.21.JDK
```

Verify: `java -version` should print 21.x.

#### Android SDK

Since you already have **Android Studio with SDK 36** installed, your SDK is
ready. Confirm your `ANDROID_HOME` path:

```bash
# Typical locations:
# macOS/Linux: ~/Android/Sdk
# Windows:     %LOCALAPPDATA%\Android\Sdk

# Add to your shell profile (~/.bashrc or ~/.zshrc):
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
```

If team members **don't** have Android Studio, they can install the SDK
standalone (see Phase B, §B.2).

#### Bun

Used as the project's task runner for scripts, git hooks, and linting orchestration.

```bash
# macOS / Linux
curl -fsSL https://bun.sh/install | bash

# Windows
powershell -c "irm bun.sh/install.ps1 | iex"
```

Verify: `bun --version` should print 1.x.

#### Git

Confirm git is installed and your GitHub credentials are configured:

```bash
git --version
git config user.name   # Should show your name
git config user.email  # Should show your email
```

---

### A.2 Create the Android Project

Open **Android Studio** and create a new project:

1. `File` → `New` → `New Project`
2. Select **Empty Activity** (Compose)
3. Configure:

| Field            | Value                  |
|------------------|------------------------|
| Name             | `AgarthaVision`        |
| Package name     | `com.agarthavision`    |
| Save location    | Wherever you keep projects (e.g. `~/Projects/AgarthaVision`) |
| Minimum SDK      | **API 26** (Android 8.0) — required by KomoUI's Calendar (uses `java.time`) |
| Build config language | **Kotlin DSL (build.gradle.kts)** |

4. Click **Finish**. Wait for the initial Gradle sync to complete.

At this point you have a working "Hello World" Compose project. Verify by
clicking ▶ and confirming it runs on an emulator or device.

---

### A.3 Set Up the Version Catalog

Android Studio generates a `gradle/libs.versions.toml` by default. Replace
its contents entirely with the project's version catalog.

Open `gradle/libs.versions.toml` and replace with:

```toml
[versions]
kotlin = "2.2.10-RC"
agp = "8.7.0"
compose-bom = "2025.05.00"
komoui = "0.3.0"
hilt = "2.51.1"
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
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.2.10-RC-2.0.2" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint-plugin" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
```

> **Note on SDK version:** The project targets `compileSdk = 36` (your installed
> SDK) with `minSdk = 26` (for KomoUI compatibility). Android Studio may have
> generated different values — we fix them in A.5.

---

### A.4 Configure Root `build.gradle.kts`

Open the **root** `build.gradle.kts` (the one in the project root, not inside `app/`)
and replace its contents:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.hilt)                apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}
```

---

### A.5 Configure App `build.gradle.kts`

Open `app/build.gradle.kts` and replace its contents:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.agarthavision"
    compileSdk = 36

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

    kotlinOptions {
        jvmTarget = "21"
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

### A.6 Configure `gradle.properties`

Open `gradle.properties` in the project root and ensure these lines are present:

```properties
org.gradle.jvmargs=-Xmx3072M -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
```

---

### A.7 Sync and Verify Build

1. In Android Studio, click **Sync Now** (banner at the top after editing Gradle files).
2. Wait for sync to complete. Fix any version conflicts that appear.
3. Click ▶ to build and run. You should see the default Compose screen.

If sync fails, the most common issues are:
- **AGP version mismatch** — check that `agp` in the version catalog matches what
  Android Studio supports. You may need to update it to the version AS bundled.
- **KSP version must match Kotlin** — `ksp = "2.2.10-RC-2.0.2"` pairs with
  `kotlin = "2.2.10-RC"`. If your Kotlin differs, update KSP accordingly.
- **JDK mismatch** — `Settings` → `Build` → `Gradle` → Gradle JDK must be 21.

> **Important:** Don't spend time debugging version conflicts alone. If sync
> fails after 15 minutes, post the error in the team chat.

---

### A.8 Create the Package Structure

Android Studio generated a flat `com.agarthavision` package with `MainActivity.kt`
and a default theme. Now create the full package structure.

In the Project view, right-click `com.agarthavision` under `app/src/main/java/`
and create these packages:

```
com.agarthavision/
├── core/
│   ├── di/
│   ├── network/
│   ├── database/
│   ├── sync/
│   ├── location/
│   ├── connectivity/
│   ├── datastore/
│   └── util/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
│       ├── capture/
│       ├── inference/
│       ├── validation/
│       └── reports/
├── data/
│   ├── local/
│   │   ├── entity/
│   │   ├── dao/
│   │   └── mapper/
│   ├── remote/
│   │   ├── api/
│   │   ├── dto/
│   │   └── mapper/
│   └── repository/
├── ui/
│   ├── theme/
│   ├── navigation/
│   ├── components/
│   ├── capture/
│   ├── queue/
│   ├── validate/
│   ├── reports/
│   └── settings/
└── worker/
```

> **Tip:** Empty packages disappear in Android Studio's "Android" view. Switch to
> **Project** view to see them. Or add a placeholder file in each — these can be
> deleted once real files exist.

---

### A.9 Create the Application Class

Create `com/agarthavision/AgarthaVisionApp.kt`:

```kotlin
package com.agarthavision

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AgarthaVisionApp : Application()
```

Register it in `AndroidManifest.xml` — add the `android:name` attribute:

```xml
<application
    android:name=".AgarthaVisionApp"
    ...>
```

---

### A.10 Update MainActivity for Hilt

Open `MainActivity.kt` and add `@AndroidEntryPoint`:

```kotlin
package com.agarthavision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // KomoUI theme goes here once the design system is set up.
            // For now the default Material theme is fine.
            Text("AgarthaVision")
        }
    }
}
```

---

### A.11 Verify the Scaffold

Build and run again. The app should launch showing "AgarthaVision". This confirms:
- Hilt is wired correctly (`@HiltAndroidApp` + `@AndroidEntryPoint`)
- The version catalog resolves all dependencies
- KomoUI is on the classpath (imported but not yet used)
- Room, Retrofit, CameraX, WorkManager are all available

---

### A.12 Initialize Git and Push to GitHub

Your project directory already has a `.git` folder from Android Studio. Now
connect it to the remote repository.

#### Create `.gitignore`

Android Studio generates one, but verify it includes at least:

```gitignore
# Gradle
.gradle/
build/
local.properties

# IDE
.idea/
*.iml
.vscode/settings.json

# Android
app/release/
*.apk
*.aab

# Bun / Node
node_modules/
bun.lockb

# OS
.DS_Store
Thumbs.db

# Secrets
.env
*.keystore

# Local TDD tests (from the TDD skill)
.local-tests/
```

#### Push

```bash
cd /path/to/AgarthaVision

git add .
git commit -m "chore(core): initial project scaffold with KomoUI 0.3.0, Hilt, Room, CameraX"

git remote add origin https://github.com/Joryuoo/AgarthaVision.git
git branch -M main
git push -u origin main
```

> **If the GitHub repo already has a README or LICENSE**, use:
> `git pull origin main --rebase` before pushing.

---

### A.13 Set Up Bun (Task Runner + Git Hooks)

Still in the project root:

```bash
bun init -y

bun add -d husky @commitlint/cli @commitlint/config-conventional lint-staged
```

Create `package.json` (replace what `bun init` generated):

```json
{
  "name": "agarthavision",
  "private": true,
  "scripts": {
    "build": "./gradlew assembleDebug",
    "build:release": "./gradlew assembleRelease",
    "install:device": "./gradlew installDebug",
    "test": "./gradlew testDebugUnitTest",
    "test:all": "./gradlew test",
    "lint": "./gradlew ktlintCheck detekt",
    "lint:fix": "./gradlew ktlintFormat",
    "clean": "./gradlew clean",
    "sync": "./gradlew --refresh-dependencies",
    "emulator": "emulator -avd AgarthaVision_Test",
    "prepare": "husky"
  },
  "lint-staged": {
    "**/*.kt": [
      "./gradlew ktlintFormat",
      "git add"
    ]
  }
}
```

Set up git hooks:

```bash
bunx husky init
```

Create `.husky/pre-commit`:

```bash
#!/usr/bin/env sh
bunx lint-staged
```

Create `.husky/commit-msg`:

```bash
#!/usr/bin/env sh
bunx --no-install commitlint --edit "$1"
```

Create `commitlint.config.js`:

```js
export default {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "scope-enum": [2, "always", [
      "capture",
      "inference",
      "dashboard",
      "reports",
      "theme",
      "core",
      "data",
      "ci",
      "docs",
    ]],
  },
};
```

Commit and push:

```bash
git add .
git commit -m "ci(core): add Bun task runner, Husky hooks, commitlint"
git push
```

---

### A.14 Set Up GitHub Branch Protection

Go to https://github.com/Joryuoo/AgarthaVision/settings/branches and create rules:

**`main` branch:**
- Require pull request before merging
- Require 2 approvals
- Require status checks to pass (add CI later)
- No force push

**`develop` branch** (create it first):

```bash
git checkout -b develop
git push -u origin develop
```

Then in GitHub settings:
- Require pull request before merging
- Require 1 approval
- Require status checks to pass
- No force push

---

### A.15 Create an Emulator (optional)

Through the terminal:

```bash
avdmanager create avd \
  --name "AgarthaVision_Test" \
  --package "system-images;android-36;google_apis;x86_64" \
  --device "pixel_6"

emulator -avd AgarthaVision_Test
```

Or through Android Studio: `Tools` → `Device Manager` → `Create Virtual Device`.

---

## Phase B — Team Onboarding (Every Team Member)

Once the project is on GitHub, every other team member follows these steps.

---

### B.1 Prerequisites

Install the same prerequisites as Phase A:
- **JDK 21** (see A.1)
- **Bun** (see A.1)
- **Android SDK** (see B.2 below)

### B.2 Android SDK

**If you have Android Studio:** Open it → `Settings` → `Languages & Frameworks`
→ `Android SDK` → install **Android 36 (API 36)** and **Build-Tools 36.0.0**.
Confirm `ANDROID_HOME` points to the SDK location.

**If you don't have Android Studio:** Install the command-line tools standalone:

```bash
# macOS
brew install --cask android-commandlinetools

# Linux / Windows — download from:
# https://developer.android.com/studio#command-line-tools-only
```

Then:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

sdkmanager --licenses
sdkmanager "platforms;android-36" \
           "build-tools;36.0.0" \
           "platform-tools" \
           "system-images;android-36;google_apis;x86_64" \
           "emulator"
```

Add `ANDROID_HOME` to your shell profile permanently.

---

### B.3 Clone and Build

```bash
git clone https://github.com/Joryuoo/AgarthaVision.git
cd AgarthaVision
bun install
bun run build
```

If `bun run build` produces `app/build/outputs/apk/debug/app-debug.apk`, you're set.

---

### B.4 Choose Your IDE

#### Option A — IntelliJ IDEA (Recommended)

1. Download from https://www.jetbrains.com/idea/download/ (Community is free).
2. Install the **Android** plugin: `Settings` → `Plugins` → search "Android".
3. Configure SDK: `Settings` → `Languages & Frameworks` → `Android SDK` → set to `ANDROID_HOME`.
4. Set Gradle JDK: `Settings` → `Build` → `Gradle` → Gradle JDK → **21**.
5. Open: `File` → `Open` → select the `AgarthaVision` folder.
6. Run config: `Run` → `Edit Configurations` → `+` → `Android App` → Module: `app`.

#### Option B — VSCode (Lightweight)

Install extensions:

| Extension                | Publisher       | Purpose                       |
|--------------------------|-----------------|-------------------------------|
| Kotlin                   | fwcd            | Syntax, completion, debugging |
| Android iOS Emulator     | nickmitchell    | Launch emulators              |
| Gradle for Java          | Microsoft       | Gradle task runner            |

Create `.vscode/settings.json`:

```json
{
  "java.jdt.ls.java.home": "/path/to/jdk-21",
  "android.sdk.path": "/path/to/Android/Sdk",
  "kotlin.languageServer.enabled": true
}
```

Build from terminal: `bun run build` and `bun run install:device`.

#### Option C — Android Studio

Full Compose Preview, Layout Inspector, device profiler. For constrained
machines, reduce IDE heap to 2048 MB and use offline Gradle mode when
not adding new dependencies.

---

### B.5 First Build Verification

Every team member should pass all three:

```bash
bun run build    # Should produce the debug APK
bun run test     # Should pass (even with 0 tests)
bun run lint     # Should pass
```

If any fail:

1. `java -version` → must be 21
2. `echo $ANDROID_HOME` → must point to SDK with API 36
3. `bun --version` → must be installed
4. Gradle sync in IDE → must complete without errors

---

### B.6 Recommended IDE Plugins (Optional)

| Plugin / Extension        | Available In       | Why                                         |
|---------------------------|--------------------|---------------------------------------------|
| Rainbow Brackets          | IntelliJ / VSCode  | Easier nesting readability in Compose       |
| GitLens                   | VSCode             | Inline git blame, PR context                |
| TODO Highlight            | Both               | Track TODO, FIXME, HACK consistently        |
| Markdown Preview Enhanced | VSCode             | Preview plan docs                           |
| Database Navigator        | IntelliJ           | Inspect Room SQLite during debugging        |
| HTTP Client               | IntelliJ Ultimate  | Test backend API endpoints inline           |
