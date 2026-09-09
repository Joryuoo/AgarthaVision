# Commands

Everything runnable in this repo. Bun scripts are thin wrappers over Gradle — the Gradle
column is what actually executes, so either form works.

Requires **JDK 21** and the **Android SDK (API 36)**. Without them every command in the first
two tables fails.

## Bun scripts

All defined in `package.json:5-19`.

| Command | Runs | Purpose |
|---|---|---|
| `bun run build` | `./gradlew assembleDebug` | Build the debug APK |
| `bun run build:release` | `./gradlew assembleRelease` | Build the release APK (unminified — `app/build.gradle.kts:70`) |
| `bun run compile` | `./gradlew :app:compileDebugKotlin` | Kotlin compile only; fastest sanity check |
| `bun run test` | `./gradlew testDebugUnitTest` | JVM unit tests |
| `bun run test:all` | `./gradlew :app:test` | Unit tests across all variants |
| `bun run lint` | `./gradlew ktlintCheck detekt` | ktlint + detekt. Zero violations expected |
| `bun run lint:fix` | `./gradlew ktlintFormat` | Apply ktlint formatting in place |
| `bun run install:device` | `./gradlew :app:installDebug` | Install debug build on a connected device |
| `bun run clean` | `./gradlew clean` | Clean Gradle outputs |
| `bun run sync` | `./gradlew --refresh-dependencies` | Force dependency re-resolution |
| `bun run stop` | `./gradlew --stop` | Stop Gradle daemons |
| `bun run emulator` | `emulator -avd AgarthaVision_Test` | Launch the named AVD. Requires that AVD to exist locally |
| `bun run prepare` | `husky` | Install the git hooks. Runs automatically on `bun install` |

## Gradle directly

| Task | Purpose |
|---|---|
| `./gradlew assembleDebug` | Debug APK |
| `./gradlew :app:compileDebugKotlin` | Compile Kotlin |
| `./gradlew :app:testDebugUnitTest` | Debug unit tests |
| `./gradlew :app:ktlintCheck :app:detekt` | Lint the app module — the exact pair the pre-commit hook runs |
| `./gradlew :app:connectedAndroidTest` | Instrumented tests. Needs a device or emulator. `androidTest/` currently holds only the generated stub |
| `./gradlew tasks` | Enumerate what is actually available in this build |

On Windows PowerShell use `.\gradlew.bat …` when the shell does not resolve `./gradlew`.

### Compose UI tests run on the JVM, not a device

`:app:testDebugUnitTest` covers both plain unit tests and the Compose UI tests for the
verification sheets (`app/src/test/java/com/agarthavision/ui/verify/`). Those render under
Robolectric with `testOptions.unitTests.isIncludeAndroidResources` (`app/build.gradle.kts:99-103`),
so screen-level behaviour is gated by the pre-commit hook without an emulator. Run one suite
with `./gradlew :app:testDebugUnitTest --tests "com.agarthavision.ui.verify.*"`.

Two interactions cannot be tested this way and need `connectedAndroidTest` instead: the
custom-species dialog in `ManualSheet` and the species dropdown in `VerificationSheet`. Both
put a text field inside a popup window, which never reaches idle under Robolectric — a lookup
after either opens spins until the Espresso timeout. Each suite documents this in its header.

`ktlint` and `detekt` are applied at both the root project (`build.gradle.kts:7-8`) and `:app`
(`app/build.gradle.kts:9-10`), so the unqualified `ktlintCheck` / `detekt` used by
`bun run lint` and the `:app:`-qualified form used by the hook are not identical invocations.
When in doubt, run the `:app:`-qualified pair — that is the one gating commits.

## Git hooks

Installed by Husky into `.git/hooks` via `bun run prepare`.

| Hook | Runs | File |
|---|---|---|
| `pre-commit` | `:app:compileDebugKotlin` → `:app:verifyRoborazziDebug` → `assembleDebug` → `:app:ktlintCheck :app:detekt`, aborting on the first failure | `.husky/pre-commit` |
| `pre-push` | `assembleDebug` | `.husky/pre-push` |

Both auto-detect `JAVA_HOME`, falling back to the Android Studio JBR path on Windows.

`verifyRoborazziDebug` stands in for `:app:testDebugUnitTest` in the hook rather than being an
extra step: it runs the same suite with pixel comparison switched on, so a separate unit-test
step would just run everything twice. A failed golden aborts the commit; when the UI change
was intentional, re-record with `./gradlew :app:recordRoborazziDebug` and review the diff
before committing the new image.

`git commit --no-verify` bypasses `pre-commit`. That is the correct move for a change that
touches only Markdown, since the hook gates on a full Android build that such a change cannot
affect. It is not a general-purpose escape hatch — see `constraints.md` C12.

**There is no `commit-msg` hook.** It was removed (commit `172ab4d`), so nothing checks commit
message format. `commitlint.config.js` and `lint-staged.config.js` are both committed and both
unreferenced by any hook.

## Node tooling

`bun install` installs the hook tooling and runs `prepare`. Note the lockfile drift recorded
in `stack.md`: `bun.lock` still lists `husky`, `lint-staged`, and the commitlint packages,
but `package.json` declares no dependencies, so a fresh `bun install` resolves nothing and
`husky` may not be on `PATH`.

## Inference container

Not wired into the Gradle build; run it separately.

```
docker build -t agartha-inference inference/
docker run -p 8000:8000 -e INFERENCE_API_KEY=<secret> agartha-inference
```

Environment it reads: `INFERENCE_API_KEY` (required), `WEIGHTS_PATH`
(default `weights/best.pt`), `MODEL_VERSION` (default `yolov26-efficientnetv2-v1`) —
`inference/server.py:9-11`. Check it with `GET /health`; it returns 200 once the model has
loaded (`inference/server.py:29-31`).

GPU droplets bill by the second. **Destroy the droplet after every test or demo.**

## Migrations

There is no migration command. Open the Supabase dashboard SQL editor, paste the next
numbered file from `supabase/migrations/`, run it, and commit the file. Promote dev to prod by
pasting the same file into the prod project. See `constraints.md` C6.

## Not present

No CI. There is no `.github/` directory in this repository — no workflow runs any of the
above on a push or a pull request.
