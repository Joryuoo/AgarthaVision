# Commands

Everything runnable in this repo. Bun scripts are thin wrappers over Gradle — the Gradle
column is what actually executes, so either form works.

Requires **JDK 21** and the **Android SDK (API 36)**. Without them every command in the first
two tables fails.

## Bun scripts

All defined in `package.json` `scripts`.

| Command | Runs | Purpose |
|---|---|---|
| `bun run build` | `./gradlew assembleDebug` | Build the debug APK |
| `bun run build:release` | `./gradlew assembleRelease` | Build the release APK (unminified — `isMinifyEnabled = false` in `app/build.gradle.kts` `buildTypes`) |
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
| `./gradlew :app:verifyRoborazziDebug` | Run unit tests and compare Roborazzi screenshot goldens |
| `./gradlew :app:recordRoborazziDebug` | Re-record Roborazzi screenshot goldens when UI changes are intentional |
| `./gradlew :app:ktlintCheck :app:detekt` | Lint the app module — the exact pair the pre-commit hook runs |
| `./gradlew :app:connectedDebugAndroidTest` | Instrumented tests (`app/src/androidTest/`: the generated stub and `OnDeviceInferenceParityTest`). **Emulator or spare device only** — see below |
| `./gradlew tasks` | Enumerate what is actually available in this build |

On Windows PowerShell use `.\gradlew.bat …` when the shell does not resolve `./gradlew`.

### Compose UI tests run on the JVM, not a device

`:app:testDebugUnitTest` covers both plain unit tests and the Compose UI tests under
`app/src/test/java/com/agarthavision/ui/` (verify, capture, records, dashboard and others). Those render under
Robolectric with `testOptions.unitTests.isIncludeAndroidResources` (`app/build.gradle.kts` `testOptions`),
so screen-level behaviour is gated by the pre-commit hook without an emulator. Run one suite
with `./gradlew :app:testDebugUnitTest --tests "com.agarthavision.ui.verify.*"`.

One interaction cannot be tested this way and needs an instrumented test instead: the inside
of the species dropdown in `VerificationSheet`. It puts a text field inside a popup window,
which never reaches idle under Robolectric — a lookup after it opens spins until the Espresso
timeout. The suite documents this in its header. (There were two until 86d4ab4tq deleted
`ManualSheet` and its bespoke custom-species dialog; the merged screen uses the dropdown for
both sources.)

### Never run instrumented tests on a phone that holds real samples

`connectedAndroidTest` / `connectedDebugAndroidTest` install the app, run the suite, then
**uninstall the app**. Uninstalling deletes its Room database and its JPEGs, so every sample not
yet synced is gone — including frames still waiting in the inference queue, which exist nowhere
else. Run them on an emulator or a device kept for testing. Listed in
[`non-negotiables.md`](non-negotiables.md).

`ktlint` and `detekt` are applied at both the root project and `:app` (the `plugins {}` block of `build.gradle.kts` and of
`app/build.gradle.kts`), so the unqualified `ktlintCheck` / `detekt` used by
`bun run lint` and the `:app:`-qualified form used by the hook are not identical invocations.
When in doubt, run the `:app:`-qualified pair — that is the one gating commits.

## Git hooks

Installed by Husky into `.git/hooks` via `bun run prepare`.

| Hook | Runs | File |
|---|---|---|
| `pre-commit` | `:app:compileDebugKotlin` → `:app:verifyRoborazziDebug` → `assembleDebug` → `:app:ktlintCheck :app:detekt`, aborting on the first failure | `.husky/pre-commit` |
| `commit-msg` | Validates subject format `[type][ClickUp-ID][Lastname]: Task title` against C9 | `.husky/commit-msg` |
| `pre-push` | `assembleDebug` | `.husky/pre-push` |

`pre-commit` and `pre-push` auto-detect `JAVA_HOME`, falling back to the Android Studio JBR path on Windows.

`verifyRoborazziDebug` stands in for `:app:testDebugUnitTest` in the hook rather than being an
extra step: it runs the same suite with pixel comparison switched on, so a separate unit-test
step would just run everything twice. A failed golden aborts the commit; when the UI change
was intentional, re-record with `./gradlew :app:recordRoborazziDebug` and review the diff
before committing the new image.

`git commit --no-verify` bypasses `pre-commit`. That is the correct move for a change that
touches only Markdown, since the hook gates on a full Android build that such a change cannot
affect. It is not a general-purpose escape hatch — see `constraints.md` C12.

**`commit-msg` hook enforces format.** Restored at `12509f8` to enforce constraint C9
(`[type][ClickUp-ID][Lastname]: Task title`). `commitlint.config.js` and `lint-staged.config.js`
are both committed and remain unreferenced by any hook.

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

`INFERENCE_API_KEY` is required; everything else it reads (weights path, model version, queue
and batching limits, GPUs) is in the environment table of `inference/README.md`. Check it with
`GET /health`, which answers once the model has loaded and stays prompt during inference
(`inference/server.py::health`). Server tests: `inference/README.md`, "Tests".

GPU droplets bill by the second. **Destroy the droplet after every test or demo.**

## Migrations

There is no migration command. Open the Supabase dashboard SQL editor, paste the next
numbered file from `supabase/migrations/`, run it, and commit the file. Promote dev to prod by
pasting the same file into the prod project. See `constraints.md` C6.

## Continuous Integration (CI)

GitHub Actions runs on every pull request (`.github/workflows/build-and-test.yml`).
It sets up JDK 21 and Gradle, and runs `./gradlew :app:verifyRoborazziDebug --stacktrace`
to gate pull requests on clean unit tests and verified screenshot goldens.
Superseded runs are automatically cancelled via workflow concurrency.
