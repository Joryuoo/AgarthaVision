# Commands

Bun is the default installer and script runner. Run `bun install --frozen-lockfile`; `bun.lock`
is the single supported installer lockfile. Do not add npm/yarn lockfiles. npm script execution
may work, but is not a second supported installer workflow.

All scripts require Python 3.11+ on PATH (`python`); Android commands additionally need JDK 21
and Android SDK API 36. Activate a test environment first:

```sh
python -m venv .venv-qa
# POSIX: . .venv-qa/bin/activate
# PowerShell: .\.venv-qa\Scripts\Activate.ps1
python -m pip install -r inference/requirements-test.txt
```

`package.json::scripts` delegates to `tools/qa/run.py::commands` and
`tools/qa/gradle.py` selects `gradlew.bat` on Windows and `gradlew` on POSIX.
The Android Studio Windows JBR is detected when JAVA_HOME is unset.
For direct Gradle commands in PowerShell, replace `./gradlew` with `.\gradlew.bat`.

## Testing and QA
| Bun command | Purpose / prerequisites | Equivalent direct invocation |
|---|---|---|
| `bun run test:unit` (also `test`) | All debug JVM tests, including Compose/Robolectric and local integration; JDK 21 + SDK API 36 | `./gradlew :app:testDebugUnitTest` |
| `bun run test:screenshots` | The same full debug suite **once**, with Roborazzi golden verification | `./gradlew :app:verifyRoborazziDebug` |
| `bun run screenshots:record` | Manually update references; review every changed image | `./gradlew :app:recordRoborazziDebug` |
| `bun run test:api` | GPU-free API contracts, JWT, queue and failures; Python test requirements | `python -m pytest inference/tests -q --junitxml=build/qa/api.xml` |
| `bun run test:integration` | Classified local Room/repository and sync tests; not deployed Supabase | `python tools/qa/run.py integration` (Gradle filters below) |
| `bun run test:device` | Instrumentation; **emulator or dedicated test device only**, JDK + SDK + adb | `./gradlew :app:connectedDebugAndroidTest` |
| `bun run test:smoke --local` | Starts/stops localhost fake-model API, real JWT validation with ephemeral test key; Python test requirements | `python tools/qa/run.py smoke --local` |
| `bun run test:smoke` | Designated staging; requires `QA_SMOKE_URL`, `QA_SMOKE_TOKEN`, `QA_DEPLOYED_REVISION` | `python tools/qa/run.py smoke` |
| `bun run check` | Tooling tests, compile, lint, debug APK, one full screenshot/unit pass, GPU-free API | `python tools/qa/run.py check` |
| `bun run qa:evidence` | Execute `check` + localhost smoke; collect reports, logs, summary and explicit gaps | `python tools/qa/run.py evidence` |
| `bun run test:all` | Android JVM tests across variants **only**, not API/device/smoke | `./gradlew :app:test` |
### Classification and focused selection

`app/src/test/` contains pure JVM/domain tests, Room/repository integration under Robolectric,
Supabase client requests against fake HTTP, and Compose UI/screenshot tests. These all belong to
the debug JVM suite. `test:unit` includes these categories; it does not assert screenshot goldens.
`test:screenshots` enables comparison while executing that same suite, so aggregate checks never
run a second full `test:unit` pass. Recording is manual and requires review of changed references.

`tools/qa/run.py::INTEGRATION` selects these existing local integration packages:

```sh
./gradlew :app:testDebugUnitTest --tests 'com.agarthavision.data.local.dao.*' --tests 'com.agarthavision.data.repository.*' --tests 'com.agarthavision.domain.usecase.sync.*' --tests 'com.agarthavision.domain.sync.*'
```

These are a conservative superset (some mapper/repository tests use fakes), not proof of deployed
Supabase permissions, migrations or real cloud/model operation. No tests were moved or renamed.

Pre-commit compiles Kotlin, runs `:app:ktlintCheck :app:detekt`, then focused debug JVM tests.
If the staged changes contain only `app/src/test/java/**/*Test.kt` and Markdown, it selects
those exact test classes. Any other change uses the documented broader fast fallback:
`./gradlew :app:testDebugUnitTest --tests 'com.agarthavision.domain.*'`. This deliberately does
not guess production dependency relationships; the full regression/screenshot gate is pre-push.
The fallback excludes UI/data packages to keep commit feedback bounded.

### Device safety

Never run `connectedAndroidTest` / `connectedDebugAndroidTest` on a phone holding real samples.
Instrumentation uninstalls the app afterwards and can erase its database, images and unsynced work.
Use an emulator or dedicated test device with synthetic profiles/fixtures. `test:device` is explicit
and excluded from `check`/`qa:evidence`. The species-dropdown popup interaction requires device
coverage: it does not reach idle under Robolectric (`VerificationSheet` test suite header).

### Hooks and CI

| Placement | Checks |
|---|---|
| `.husky/pre-commit` | Compile + app ktlint/detekt + focused tests/fallback above; abort on failure |
| `.husky/pre-push` | One full `:app:verifyRoborazziDebug` pass, then GPU-free API tests; no deployed smoke |
| PR CI (`.github/workflows/build-and-test.yml`) | `bun run check`, then shared `bun run test:smoke --local`; required failures fail job |
| Staging after successful deployment | Dispatch `build-and-test` with exact confirmed `deployed_revision`; shared `bun run test:smoke` |

The staging job uses the GitHub `staging` environment variable `QA_SMOKE_URL` (HTTPS) and
secret `QA_SMOKE_TOKEN` (dedicated synthetic test-user access token). The deployer must trigger
it only after a successful deployment and supply the confirmed deployed revision; this repository
has no automatic inference deployment workflow. Missing inputs/credentials fail explicitly.
PR smoke runs a localhost fake model, not deployed-model evidence. Production `verify_user`
remains active: only the model and JWKS source use test fixtures in the isolated smoke process.
Health readiness is bounded to 30 seconds (individual requests timeout at 10 seconds).
Smoke asserts health 200, missing bearer 401 and signed synthetic-image inference/schema 200.

CI always uploads only sanitized `build/qa/` evidence, including copied Gradle XML/HTML reports
and Roborazzi outputs/differences, even
on failure. The upload includes the tested source revision and staging smoke includes the
separate deployed revision. References are verified, never recorded by hooks or CI.
`commit-msg` still enforces C9. Install hooks with `bun run prepare` (also run by `bun install`).

### Actual evidence versus STD

Every QA command produces `build/qa/<UTC-run-id>-<command>/summary.json` and numbered redacted
logs. API XML is in that directory. `tools/qa/run.py::main` copies existing Gradle reports into
`artifacts/`; each file records whether it was updated during this run. Prior/cached output never
overrides a failed command. JUnit records preserve pass/fail/skip, class/method case IDs,
requirement category IDs, expected/actual outcomes and artifact references. These category IDs
are QA traceability, not a claim that an evaluator has approved the product requirements.
The summary includes commit, dirty-tree flag, date, environment and process exit codes.
All requested steps execute even after another step fails, and the command returns a nonzero
status if any step fails. Missing Python dependencies are actionable failures, never passes.

Smoke records three scenarios and a bounded sequential workload (default 10 authenticated
requests, `--requests 1..100`), duration, throughput, error rate and p50/p95/p99 request latency.
Local fake-model timings are a smoke microbenchmark, not real-model or device performance.
No GPU/device/server telemetry is fabricated. Synthetic uniform JPEGs contain no patient data.
Never put real credentials or patient data in tests; tokens are environment-only and logs redact
JWTs/configured secret values in streamed logs, copied text reports and directly generated Python XML.
Raw Gradle reports stay local and are never uploaded. Unsupported opaque binary reports are excluded
with reasons; screenshot PNGs are preserved unchanged. Review artifacts before sharing them.

The summary explicitly lists unavailable device/system, deployed integration, real performance,
usability and evaluator-acceptance evidence. Completing those requires an actual designated
fixture/device/deployment, participant observations and evaluator sign-off. Link any genuine
product defect to its failed case in ClickUp, including expected/actual result, status, fix revision
and retest artifact; setup failures are not invented product defects. The `defects` array starts
empty until real linked defects exist. Do not describe planned cases as executed.
STD plans/procedures/expected cases remain separate from these actual execution artifacts.

Focused tooling verification: `python -m unittest discover -s tools/qa -p test_run.py -v`.

## Other commands

| Bun command | Equivalent direct invocation |
|---|---|
| `bun run build` / `build:release` | `./gradlew assembleDebug` / `assembleRelease` |
| `bun run compile` | `./gradlew :app:compileDebugKotlin` |
| `bun run lint` / `lint:fix` | `./gradlew ktlintCheck detekt` / `ktlintFormat` |
| `bun run install:device` | `./gradlew :app:installDebug` |
| `bun run clean` / `sync` / `stop` | `./gradlew clean` / `--refresh-dependencies` / `--stop` |
| `bun run emulator` | `emulator -avd AgarthaVision_Test` (requires configured AVD) |

## Inference container

```sh
docker build -t agartha-inference inference/
docker run -p 8000:8000 -e SUPABASE_URL=https://<project-ref>.supabase.co agartha-inference
```

Real service auth requires the project's signed-in user token (`inference/server.py::verify_user`).
GPU-free API tests substitute a model and public test JWKS, not clinical inference.
`inference/README.md` owns deployed configuration and real-model checks.
## Model export and offline assets

Run by hand; their output is committed. Each folder's `README.md` owns the details.

| What | Entry point |
|---|---|
| Export a checkpoint to the on-device TFLite models, then check parity and accuracy | `inference/export/export_mobile.py`, `parity_check.py`, `accuracy_report.py`, `make_parity_fixture.py` (`inference/export/README.md`) |
| Rebuild the bundled PSGC barangay asset | `tools/psgc/build-psgc-asset.py`, then `build-psgc-db.py` (`tools/psgc/README.md`) |
| Rebuild the offline boundary assets for My coverage | `tools/geo/build-town-keys.py`, `build-geo-shapes.sh`, `build-geo-asset.py` (`tools/geo/README.md`) |

GPU droplets bill by the second. **Destroy the droplet after every test or demo.**

## Migrations

There is no migration command. Open the Supabase dashboard SQL editor, paste the next
numbered file from `supabase/migrations/`, run it, and commit the file. Promote dev to prod by
pasting the same file into the prod project. See `constraints.md` C6.
