# AgarthaVision · Team Conventions

> Coding standards, documentation requirements, and naming conventions for a team of 5.
> Consistency matters more than any individual preference — follow these rules even when
> you disagree, and propose changes via PR to this document.

---

## 1. Kotlin Code Style

### 1.1 Enforced by Tooling

ktlint and Detekt run on every PR via CI. Configure them once and never argue about formatting again.

ktlint follows the official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) with these additions:

```
# .editorconfig (project root)
[*.kt]
ktlint_code_style = android_studio
max_line_length = 120
insert_final_newline = true
indent_size = 4
```

### 1.2 Naming Conventions

| Kind               | Convention         | Example                          |
|--------------------|--------------------|----------------------------------|
| Package            | lowercase          | `com.agarthavision.ui.capture`   |
| Class / Object     | PascalCase         | `CaptureViewModel`               |
| Function           | camelCase          | `captureSample()`                |
| Property / Variable| camelCase          | `sampleId`                       |
| Constant           | UPPER_SNAKE_CASE   | `MAX_RETRY_COUNT`                |
| Composable         | PascalCase         | `CaptureScreen()`                |
| Room Entity        | PascalCase + `Entity` suffix | `SampleEntity`          |
| Room DAO           | PascalCase + `Dao` suffix    | `SampleDao`             |
| Use Case           | PascalCase + `UseCase` suffix| `CaptureSampleUseCase`  |
| Repository Interface| PascalCase + `Repository`   | `SampleRepository`      |
| Repository Impl    | PascalCase + `RepositoryImpl`| `SampleRepositoryImpl`  |
| ViewModel          | PascalCase + `ViewModel`     | `CaptureViewModel`      |
| Hilt Module        | PascalCase + `Module`        | `DatabaseModule`        |
| Worker             | PascalCase + `Worker`        | `SyncWorker`            |

### 1.3 File Organization

Each Kotlin file should contain **one public class/object/interface** (plus private helpers).
Exception: closely related sealed classes/enums can share a file (e.g., `SampleStatus.kt` containing the enum and its extension functions).

### 1.4 Composable Function Rules

```kotlin
// Good: stateless composable with parameters
@Composable
fun EpgReadout(
    value: Int,
    species: String,
    modifier: Modifier = Modifier,   // always accept modifier as last default param
) { /* ... */ }

// Good: stateful screen composable with ViewModel
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CaptureScreenContent(state = state, onCapture = viewModel::capture)
}

// Good: separate content composable for previews
@Composable
private fun CaptureScreenContent(
    state: CaptureState,
    onCapture: () -> Unit,
) { /* ... */ }
```

Rules:
- Screen composables take a ViewModel. Content composables take plain state and callbacks.
- Always provide a `@Preview` for content composables.
- `Modifier` is always the last parameter with a default of `Modifier`.
- Use `collectAsStateWithLifecycle()` (not `collectAsState()`) to respect lifecycle.

---

## 2. Documentation Requirements

### 2.1 KDoc

Every **public** class, interface, function, and property must have KDoc:

```kotlin
/**
 * Orchestrates the sample capture workflow:
 * generates a UUID, binds metadata, saves the image locally,
 * and creates the initial Room record with [SampleStatus.CAPTURED].
 *
 * @param imageFile The captured JPEG file from CameraX.
 * @param sessionId The current diagnostic session identifier.
 * @return The created [Sample] domain model.
 * @throws ImageQualityException if the image fails the precheck.
 */
class CaptureSampleUseCase @Inject constructor(
    private val sampleRepository: SampleRepository,
    private val locationProvider: LocationProvider,
) { /* ... */ }
```

Private functions don't require KDoc unless the logic is non-obvious.

### 2.2 TODO / FIXME / HACK

Use these markers consistently. CI can be configured to fail on `HACK` in `main`.

```kotlin
// TODO(novabos): Add GPS accuracy validation — see SRS §3.1.1
// FIXME(escolano): Bounding box coordinates are normalized but renderer expects pixels
// HACK(tabada): Temporary workaround for Room migration — remove after schema stabilizes
```

Always include the author's last name so the right person gets pinged.

### 2.3 Architecture Decision Records (ADRs)

When making a non-obvious architectural choice, write a short ADR in `docs/adr/`:

```
docs/adr/
├── 001-single-module-for-mvp.md
├── 002-room-over-realm.md
├── 003-komoui-over-material3-raw.md
└── 004-celery-over-fastapi-background-tasks.md
```

Format:

```markdown
# ADR-001: Single Module for MVP

## Status
Accepted

## Context
We considered splitting into :core, :data, :domain, :ui Gradle modules...

## Decision
Use a single :app module with package-level boundaries.

## Consequences
Faster build times, simpler setup. We'll extract modules in Phase 2 if needed.
```

---

## 3. ViewModel Patterns

### State

Every ViewModel exposes a single `StateFlow<ScreenState>`:

```kotlin
data class CaptureState(
    val isCapturing: Boolean = false,
    val recentThumbnail: String? = null,
    val bioWindowRemainingMinutes: Int = 60,
    val error: String? = null,
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val captureSampleUseCase: CaptureSampleUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    fun capture() {
        viewModelScope.launch {
            _state.update { it.copy(isCapturing = true, error = null) }
            try {
                val sample = captureSampleUseCase(/* ... */)
                _state.update { it.copy(
                    isCapturing = false,
                    recentThumbnail = sample.imagePath,
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(isCapturing = false, error = e.message) }
            }
        }
    }
}
```

### Events (one-shot)

For navigation events or snackbar triggers, use `SharedFlow`:

```kotlin
private val _events = MutableSharedFlow<CaptureEvent>()
val events: SharedFlow<CaptureEvent> = _events.asSharedFlow()

sealed class CaptureEvent {
    data class NavigateToValidate(val sampleId: String) : CaptureEvent()
    data class ShowToast(val message: String) : CaptureEvent()
}
```

---

## 4. Testing Standards

### What to Test

| Layer      | Test Type        | Coverage Target | Framework          |
|------------|------------------|-----------------|--------------------|
| Use Cases  | Unit test (JVM)  | High            | JUnit + Coroutines Test |
| ViewModels | Unit test (JVM)  | High            | JUnit + Turbine    |
| Repositories| Unit test (JVM) | Medium          | JUnit + Fake DAOs  |
| DAOs       | Instrumented     | Medium          | Room Testing       |
| Composables| Screenshot/UI    | Low (MVP)       | Compose Test        |

### Naming Convention

```kotlin
class CaptureSampleUseCaseTest {

    @Test
    fun `capture saves sample with CAPTURED status`() { /* ... */ }

    @Test
    fun `capture fails when image quality precheck rejects`() { /* ... */ }

    @Test
    fun `capture binds GPS when location permission granted`() { /* ... */ }

    @Test
    fun `capture sets null GPS when location unavailable`() { /* ... */ }
}
```

Use backtick names that read as sentences. Group by behavior, not by method.

---

## 5. Dependency Injection Rules

- **Do not** call constructors directly in production code. Let Hilt inject everything.
- **Interfaces in `domain/`**, implementations in `data/`. Bind them in Hilt modules:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindSampleRepository(impl: SampleRepositoryImpl): SampleRepository
    @Binds abstract fun bindDetectionRepository(impl: DetectionRepositoryImpl): DetectionRepository
}
```

- Use `@Singleton` only for database, OkHttp, and Retrofit instances. Repositories and Use Cases are unscoped (new instance per injection) unless there's a specific reason.

---

## 6. Error Handling

### Never Swallow Exceptions

```kotlin
// BAD
try { doThing() } catch (_: Exception) { }

// GOOD
try {
    doThing()
} catch (e: Exception) {
    Timber.e(e, "Failed to do thing for sample $sampleId")
    _state.update { it.copy(error = e.message) }
}
```

### Use Sealed Results for Domain Layer

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
}
```

Use Cases return `Result<T>`. ViewModels handle both branches.

---

## 7. Resource Management

### Strings

Use `strings.xml` for user-facing text. Never hardcode strings in composables:

```xml
<string name="capture_button_start">Start Capture</string>
<string name="validate_approve">Validate Sample</string>
<string name="epg_unit">EPG · %1$s</string>
```

### Colors

**Never** use `Color(0xFF...)` in composables. Always reference theme tokens:

```kotlin
// BAD
Text(color = Color(0xFF1F5BFF))

// GOOD
Text(color = MaterialTheme.styles.primary)
```

The only place raw hex values appear is in `AgarthaLightColors.kt`.

---

## 8. Code Review Checklist (for Reviewers)

When reviewing a PR, check:

- [ ] Does it follow the naming conventions in §1.2?
- [ ] Are public APIs documented with KDoc?
- [ ] Are theme tokens used (no hardcoded colors/dimensions)?
- [ ] Does the ViewModel expose a single StateFlow?
- [ ] Are Use Cases injected (not instantiated manually)?
- [ ] Is error handling present (no swallowed exceptions)?
- [ ] Are strings in `strings.xml`?
- [ ] Is there a `@Preview` for new composables?
- [ ] Do test names read as sentences?
- [ ] Is the commit message conventional?
