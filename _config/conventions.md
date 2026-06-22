# Conventions (Layer 3 — stable across runs)

Source: CONTEXT.md §7 (Coding Conventions) and §6 (Git Workflow).

## Kotlin style

- ktlint: `android_studio` profile — `max_line_length = 120`, 4-space indent, final newline.
- Detekt applied on `:app`. Run `bun run lint` to check both together.

## Naming

| Kind | Convention | Examples |
|---|---|---|
| Class / Object / Composable | PascalCase | `SyncSampleUseCase`, `CaptureScreen`, `DetectionOverlay` |
| Function / property | camelCase | `captureFrame()`, `sessionId`, `isRepeat` |
| Constant | UPPER_SNAKE_CASE | `MULTIPLIER`, `INFERENCE_TIMEOUT_MS`, `MAX_LINE_LENGTH` |
| Suffixes | per type | `…Entity`, `…Dao`, `…UseCase`, `…Repository`, `…RepositoryImpl`, `…ViewModel`, `…Module`, `…Worker` |

## KDoc

- Required on every public class, interface, function, and property.
- Private functions: only when the WHY is non-obvious.
- No multi-paragraph docstrings. One short sentence is the norm.

## Inline markers

Always include the owner's last name:

```kotlin
// TODO(lastname): explain what is needed
// FIXME(lastname): explain what is broken
// HACK(lastname): explain why this is a workaround
```

## Error handling

- Never swallow exceptions — log + surface to state.
- Use Cases return a sealed `Result<T>`; ViewModels handle both branches explicitly.
- Current logger: `android.util.Log`.

## Resources

- User-facing strings: `strings.xml` only. Never hardcode in Kotlin or Compose.
- Colors: `AppColors.*` or MaterialTheme theme tokens. Never raw hex in app code.

## Branch naming

`feat/<scope>-<desc>`, `fix/…`, `refactor/…`, `docs/…`, `ci/…`, `test/…`
All feature branches cut from `develop`.

## Commit format (enforced by commitlint + husky)

`<type>(<scope>): <description>`

Types: `feat fix refactor docs style test ci chore`
Scopes: `capture inference dashboard reports theme core data ci docs`

## PR rules

- Target: `develop` (never `main` directly).
- Size: ≤ ~400 lines ideal — split if larger.
- Reviewer responds within 24h with specific, actionable feedback.
- 1 approval to merge to `develop`. 2 approvals to merge `release/x.y.z` → `main`.

## Testing

Framework: `mockito-kotlin` 5.4.0 + JUnit + `kotlinx-coroutines-test`.
Coverage targets: Use Cases + ViewModels = high; repositories = medium (fake DAOs); DAOs = instrumented; composables = low (MVP).
Test names read as sentences:

```kotlin
fun `capture sets null GPS when location unavailable`() { ... }
```
