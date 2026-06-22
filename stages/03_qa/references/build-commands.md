# Build Commands — Stage 03 (QA)

All commands run from the repo root using Bun (requires Node + Bun installed).

## Lint (blockers — must be zero violations)

```bash
bun run lint        # ktlintCheck + detekt on :app
bun run lint:fix    # ktlintFormat — auto-fixes style; re-run lint after to confirm
```

## Tests (blockers — must be zero failures)

```bash
bun run test        # :app:testDebugUnitTest (unit tests only, no device required)
```

## Build

```bash
bun run build           # :app:assembleDebug
bun run install:device  # assemble + install on connected device via ADB
```

## Clean

```bash
bun run clean       # ./gradlew clean
```

## Gradle equivalents (if Bun is unavailable)

```bash
./gradlew :app:ktlintCheck :app:detekt
./gradlew :app:testDebugUnitTest
./gradlew assembleDebug
./gradlew :app:compileDebugKotlin     # type-check only, no full build
```

## Prerequisites

- JDK 21 required. Verify: `java -version` → `21.x`.
- `ANDROID_HOME` must be exported (Android SDK API 36 + Build-Tools 36.0.0).
- `local.properties` must exist with dev keys (copy from `local.properties.example`).
  Missing keys default to empty string and cause a loud runtime failure — not a build failure.

## Inference container note (ADR-003)

GPU droplets bill by the second. **Destroy after every test/demo.**
Provider switch = change `INFERENCE_URL_DEV` in `local.properties`, not code.
