# AgarthaVision · Context

> The single context file for the AgarthaVision codebase — tech stack, architecture
> rules, design system, backend/security model, git workflow, conventions, and ADR
> history. Companion files: [`schema.ts`](schema.ts) (data model) and
> [`TODO.md`](TODO.md) (state + roadmap).
>
> **Team code:** 2526-sem2-cs342-02 · **Repo:** https://github.com/Joryuoo/AgarthaVision.git

---

## 1. Project Overview

AgarthaVision is a **Digital Information and Diagnostic System for Soil-Transmitted
Helminth (STH) surveillance** — an Android clinical-microscopy assistant for medical
technologists examining parasitic stool smears.

**The core loop:** a medtech logs in, starts a session (= one fecal smear / Kato-Katz
slide), and points the phone at a microscope. The app samples one frame every 2 s and
sends each to a cloud AI model. On a positive detection a toast appears
(*"egg detected — tap to review"*); the medtech verifies flagged frames through a
human-in-the-loop questionnaire (confirm / reject / reclassify per bounding box).
Verified samples + bounding-box metadata sync to the cloud, EPG (eggs-per-gram) is
computed per session, and the medtech can generate a CSV report. No manual counting,
no paper forms mid-session.

**Primary user:** medical technologists in clinical labs and field sites.
**Voice:** clinical, restrained, scientifically precise (italic binomials, exact units).

### Phase 1 (current MVP) — managed services

| SRS Module | Name | Phase 1? |
|---|---|---|
| 1A | Mobile Capture Client (Android) | ✅ Yes |
| 2A | Cloud Backend | ✅ Supabase (Auth + Postgres + Storage) |
| 2A (AI) | Inference | ✅ Self-hosted FastAPI container on a rented GPU droplet |
| 3 | In-app Verification | ✅ Yes (`VerificationSheet`) |
| 4 | Reporting & Admin | ⚠ Partial — session CSV reports ship; admin dashboard deferred |
| 1B | Dedicated Hardware Capture (IoT) | ❌ Phase 2 |
| 2B | On-Device AI Processor | ❌ Phase 2 |

No dedicated backend to operate in Phase 1 — the app talks directly to Supabase and the
inference container.

### Phase 2 (deferred) — self-hosted stack

Triggered by a funding/support commitment **or** the first deployment with real patient
PHI, whichever comes first. Self-hosted FastAPI + PostgreSQL (Philippine region for DPA
compliance) + MinIO + YOLO on owned GPU hardware; EPG backed by a DOH-validated
`prep_methods` table (replacing the hardcoded `EpgCalculator.MULTIPLIER = 24`);
DOH-formatted PDF reports; offline-first `SyncQueue` + WorkManager; per-account persistent
flagged-frame queue.

**Phase 2 capture-model change:** the phone camera is removed. Frames originate from an
external modular capture device attached to dedicated on-prem inference hardware (IoT node,
SRS Module 1B); the phone receives frame + detection over **USB OTG** and becomes a
verification + reporting client only. UI / data flow are unchanged — only
`CameraManager.captureLatestFrame()` is reimplemented.

### Objectives (GO → MVP)

- **GO-1 Standardized optical input pipeline** — the transactional pipeline (UUID gen,
  metadata binding, payload encapsulation, state machine) is built in MVP and inherited by
  the Phase 2 IoT node.
- **GO-2 Traceability** — UUID, timestamp, GPS, device ID, session ID bound at capture;
  direct upload on verify (≥99.9% sync rate is a Phase 2 offline-queue target).
- **GO-3 Diagnostic workflow efficiency (HITL)** — inference flags eggs in ~1-2 s/frame;
  the medtech verifies flagged frames only.
- **GO-4 Standardized reporting** — Phase 1 ships EPG (hardcoded Kato-Katz multiplier 24)
  + per-session CSV; DOH PDF reports are Phase 2.

### Team (point-of-contact ownership; everyone touches all modules)

| GitHub | Primary | Secondary |
|---|---|---|
| Beansman | Module 3 — VerificationSheet + Login | Design system / theming |
| Joryuoo | Supabase schema migrations + `SyncSampleUseCase` (RLS) | API contracts |
| IgnisFrostburn | Module 1A — CameraX + `FrameSampler` + `CaptureScreen` | Detection overlay |
| jojseph | Module 4 — Records + CSV export | Sync queue / WorkManager (Phase 2) |
| DMKuZu | Architecture + DevOps + CI/CD + inference container + GPU droplet ops + Model training (custom Ultralytics: YOLOv26 + EfficientNetV2) + `best.pt` handoff| Git workflow, code review, Model versioning |

---

## 2. Tech Stack

### Android app (the primary deliverable)

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2.x (JDK 21 target) |
| UI | Jetpack Compose (BOM) + Material 3 + Agartha components |
| Architecture | MVVM + Use Cases + Repository |
| DI | Hilt |
| Local DB | Room / SQLite (schema v7; exported to `app/schemas/`) |
| Camera | CameraX (`ImageAnalysis`, no `ImageCapture`) |
| Networking | Retrofit + OkHttp (inference) · supabase-kt + Ktor (Supabase) |
| Async | Kotlin Coroutines + Flow / StateFlow / SharedFlow |
| Navigation | Compose Navigation |
| Images | Coil |
| Location | Play Services `FusedLocationProviderClient` (ADR-001) |
| JSON | Gson (Room JSON columns) · Kotlinx.serialization (Supabase rows) |
| Min / Target SDK | 26 / 36 |

### Build / tooling

- **JDK 21** (Kotlin 2.2.x targets it). Verify `java -version` → 21.x.
- **Android SDK** — API 36 + Build-Tools 36.0.0; `ANDROID_HOME` exported.
- **Bun** — task runner + git-hook orchestration. Common scripts (`package.json`):
  `bun run build` (assembleDebug), `bun run test` (testDebugUnitTest),
  `bun run lint` (`ktlintCheck detekt`), `bun run lint:fix` (ktlintFormat),
  `bun run clean`, `bun run install:device`.
- **Gradle** — direct equivalents: `./gradlew assembleDebug`,
  `./gradlew :app:compileDebugKotlin`, `./gradlew :app:testDebugUnitTest`,
  `./gradlew :app:ktlintCheck :app:detekt`.
- **ktlint** + **Detekt** — applied on `:app`; enforced locally via husky/lint-staged.
- **commitlint** + **husky** — conventional commits enforced on every commit.
- Single-module project (`:app`); package-level boundaries can be extracted into Gradle
  modules in Phase 2.

### Secrets (`local.properties`, gitignored)

`BuildConfig` fields are populated at build time via Gradle `project.findProperty(...)`.
A missing property defaults to an empty string and the app fails loudly at runtime
("Missing API key") rather than silently. For CI, pass via `-P` flags.

```properties
SUPABASE_URL_DEV=https://<dev-ref>.supabase.co
SUPABASE_ANON_KEY_DEV=sb_publishable_<dev-anon-key>
SUPABASE_URL_PROD=https://<prod-ref>.supabase.co
SUPABASE_ANON_KEY_PROD=sb_publishable_<prod-anon-key>
INFERENCE_URL_DEV=http://<droplet-ip>:8000
INFERENCE_URL_PROD=http://<droplet-ip>:8000
INFERENCE_API_KEY=<shared-secret>
```

> ⚠ Historical drift: the original environment setup guide referenced `ROBOFLOW_*`
> BuildConfig fields. Those were superseded by `INFERENCE_*` per ADR-003 — the
> Roboflow path is dead.

---

## 3. Architecture Rules

**MVVM + Use Cases + Repository**, four layers, each talking only to the one below:

```
Presentation  — Compose screens + ViewModels (StateFlow out, intents in)
Domain        — Use Cases (pure Kotlin, ZERO Android imports), domain models, repo interfaces
Data          — Repository impls, Room DAOs, Retrofit/Supabase data sources, mappers
Core/Platform — CameraX, Location, Connectivity, SessionManager
```

### Hard rules

1. **ViewModels never import Room / Retrofit / Supabase.** They call Use Cases.
2. **Use Cases are single-responsibility** with one public `operator fun invoke(...)` /
   `suspend fun execute(...)`, and return `Result<T>` (see Conventions §7).
3. **Repositories are the single source of truth** — they decide Room (local) vs remote.
4. **`domain/` has zero Android imports** — unit-testable without Robolectric.
5. **Entities (Room) live in `data/`; domain models live in `domain/`.** Mappers convert.
6. **Interfaces in `domain/`, implementations in `data/`**, bound via Hilt `@Binds`.
7. **`@Singleton` only for** database, OkHttp, Retrofit, SupabaseClient, and app-scoped
   singletons (`SessionManager`, `FlaggedFrameStore`, `FrameSampler`). Repositories and
   Use Cases are unscoped.

### Package map (`com.agarthavision`)

```
core/      camera · connectivity · database · di · location · session · util
domain/    model · repository · usecase/{auth,capture,inference,verify,records,reports}
data/      local/{dao,entity,mapper} · remote/{InferenceApi,dto} · repository · supabase
ui/        capture · components · dashboard · login · navigation · records · sessions · settings · theme · verify
```

### Sample lifecycle (Phase 1 state machine)

```
CANDIDATE (in-memory)         — current frame being analyzed; discarded if no detection
   └─ predictions non-empty → FLAGGED (FlaggedFrameStore, in-memory + disk cache; transient)
         └─ medtech submits  → VERIFIED (Room: 1 SampleEntity + N DetectionEntity w/ verdicts)
               └─ sync        → SYNCED   (Room + Supabase)
                     └─ fail  → SYNC_FAILED (retried on next session start)
```

There is **no `REJECTED` state** — a rejection is a persisted `DetectionEntity` with
`verdict = FALSE_POSITIVE` (ADR-004). The "positive finding?" question is answered at read
time from child detections. FLAGGED frames are **transient** in Phase 1 (lost on logout /
process death); a persistent per-account queue is Phase 2.

### Navigation (Phase 1)

`Login → SessionPicker → Capture` (with `Verify Queue`, `Records`, `SessionDetail`,
`SampleDetail`, `Settings`). Inference auto-pauses whenever any sheet / picker / child
screen is foregrounded; only explicit **End Session** writes `ended_at`.

---

## 4. Design System

> **Source of truth for all UI:** the Clinical Microscopy design spec (Inter font, cobalt
> `#1E3FD9`). The prior KomoUI / "Clinical Pulse" guide (`#1F5BFF`, Geist + JetBrains Mono,
> `ShadcnTheme`/`KomoTheme`) is **SUPERSEDED** and has been removed from the app code and
> Gradle dependency graph. Where the two disagree, the spec below wins.

### Design principles

1. **Clinical clean, not playful** — restrained whitespace, hairline borders, no ornament.
2. **Single accent color** — cobalt blue, used sparingly for primary actions, active states,
   focus rings, and AI bounding boxes. Semantic colors (red/green/amber) for specific states only.
3. **Tabular numerals for all data** — IDs, timestamps, GPS, confidence, EPG align in columns.
4. **Italicized binomial nomenclature** — *Ascaris lumbricoides*, never upright.
5. **Tool mode vs browse mode** — Capture is dark and immersive; every other screen is light.
6. **No heavy shadows** — flat; hairline borders separate. Real shadows only on modal sheets
   and the Active Session hero.
7. **No charting library / no icon library** — small dataviz is hand-crafted inline SVG;
   icons are lucide-style inline SVG at 1.5–1.8 stroke. One external font (Inter).

### Color tokens

```
Brand
  --blue          #1E3FD9   Primary accent — CTAs, active states, focus rings, AI bboxes
  --blue-hover    #1A36BF
  --blue-pressed  #15309F
  --blue-tint     #E6EBFC   Active card backgrounds
  --blue-tint-2   #F1F4FE   Hint / info banner backgrounds
Neutrals
  --white #FFFFFF · --off-white #FAFBFC · --gray-50 #F7F8FA · --gray-100 #EEF0F4
  --gray-200 #E2E5EB · --gray-300 #CBD0DA · --gray-400 #9CA3AF · --gray-500 #6B7280
  --gray-700 #374151 · --gray-900 #0F172A
Semantic
  --red   #DC2626 / --red-tint   #FEE2E2    Destructive, REC indicator, errors
  --green #16A34A / --green-tint #DCFCE7    Sync OK, confirmed detections
  --amber #D97706 / --amber-tint #FEF3C7    Manual captures, pending review, sync warning
```

In code these live as `AppColors.*` (`AppColors.Blue`, `AppColors.Gray300`, etc.). The
logo uses a brighter `#036BFC` than the app accent `#1E3FD9` — kept distinct on purpose.

### Typography — Inter (Google Fonts), system fallback

Features `cv11`, `ss01`, `ss03`; tabular numerals (`tnum`) on every data field.

| Style | Size / line | Weight | Used for |
|---|---|---|---|
| Display | 32 / 40 | 700 | Hero numbers (EPG count, session IDs) |
| Headline | 22 / 28 | 700 | App-bar titles |
| Subhead | 17 / 22 | 700 | Card titles, greeting |
| Body | 15 / 22 | 500 | Default text |
| Label | 13 / 18 | 500 | Form labels |
| Caption | 12 / 16 | 500 | Metadata, timestamps |
| Micro / eyebrow | 10–11 / 14 | 600 | Uppercase section labels |

Italic for *binomial species names*.

### Spacing · Radius · Elevation · Motion

- **Spacing** — 8-px grid: `4 · 8 · 12 · 16 · 20 · 24 · 32 · 48`.
- **Radius** — `sm 8` (inputs) · `md 12` (cards, list items) · `lg 16` (hero cards, sheet
  top corners) · `pill 999` (buttons, badges, chips).
- **Elevation** — plain cards: no shadow, 1px `--gray-100` hairline. Active Session hero:
  `0 10px 28px -10px rgba(30,63,217,.45)`. Modal sheet: `0 -16px 32px -8px rgba(15,23,42,.12)`.
  Floating glass (Capture): `0 4px 14px -4px rgba(0,0,0,.35)`.
- **Motion** — default UI 150ms ease · sheet slide-up 320ms `cubic-bezier(.32,.72,0,1)` ·
  scrim fade 250ms · toast slide-down 400ms · REC pulse 1500ms infinite.

### Screens (9)

Login · Dashboard · Session Picker · Capture (dark/immersive) · Verify Queue ·
Records · Session Detail (+ empty variant) · Sample Detail · Settings.
Bottom tab bar (Home · Sessions · Records · Settings) on every screen **except** Login and
Capture. Top app bar: `[← Back?] Title / sub-meta(caption, tabular) [icon actions]`.

### Modal sheets (3)

New Session · Verify (AI) · Manual. Slide up over screen + tab bar; drag handle; scrim
`rgba(15,23,42,.4)` + 2px blur; sticky 1–2 button footer with top hairline; max height 86%.

### Component conventions

- **Buttons** — pill; primary = `--blue` fill / white; secondary = `--gray-100` / `--gray-900`;
  destructive = `--red` / white. Icon buttons 40×40, `lg` radius (utility chrome).
- **Inputs** — white, `--gray-200` border, `sm` radius; focus = `--blue` border + 3px glow;
  error = red border + light-red tint.
- **Badges** — pill, 11px / 600. Status colors map to the semantic palette.
- **Toggle / Switch** — 44×26 pill; off `--gray-200`, on `--blue`; white thumb. (The Boxes
  toggle on `VerificationSheet` uses a Material 3 `Switch` with a box icon in the thumb when on.)
- **Glass UI (Capture only)** — `rgba(20,28,42,.55)` + `blur(20px) saturate(160%)` +
  `1px rgba(255,255,255,.08)` border, white text.
- **Detection bbox** — 2px solid `--blue`, 4px radius, soft blue glow, white L-shaped corner
  brackets + label chip.

### Custom composables (not from any component lib)

`MicroscopyViewport`, `DetectionOverlay` / `FrameWithBoxes`, `EpgReadout`, `BottomNavBar`,
detection toast. Build against the tokens above — no new colors, no new radii.

### Accessibility

Touch targets ≥ 44px; body-on-white ≥ 4.5:1, large text ≥ 3:1; required fields marked
visually + textually; 3px focus rings; icon buttons carry content descriptions.
*Future:* full ARIA, keyboard nav, `prefers-reduced-motion`.

---

## 5. Supabase & Inference

### Supabase (managed; owned by DMKuZu via the web dashboard — no CLI in Phase 1)

- **Auth** — email/password, JWT auto-refresh via `supabase-kt`. No sign-up flow; accounts
  are provisioned manually in the dashboard. A Postgres trigger (`handle_new_user`) inserts a
  `profiles` row with `role='medtech'` on first sign-in. Cold start reads the persisted
  session and skips Login if valid.
- **Postgres** — tables `profiles`, `sessions`, `samples`, `detections`, `reports`. Full
  column/constraint detail in [`schema.ts`](schema.ts). Migrations are SQL files in
  `supabase/migrations/`, **run manually** in the dashboard SQL editor and committed to git.
  Promote dev→prod by pasting the same file into the prod project.
- **Storage** — single private bucket `samples`, path `{user_id}/{sample_id}.jpg`. JPEGs are
  resized to 640×640 (~80 KB) before upload.

### RLS policy intent

Owner-scoped everywhere via `auth.uid()`: a medtech reads/writes only their own
`sessions` / `samples` / `reports`; `detections` are scoped through the parent sample;
admins can read all (via the `public.is_admin(uuid)` SECURITY DEFINER helper added in `0004`
to break policy recursion). Storage policies scope INSERT/SELECT/UPDATE to the user's own
folder; **no DELETE policy** by design — samples persist as training data (ADR-004).

> ⚠ `0008_reports.sql` reintroduced an inline `(select role from profiles …) = 'admin'`
> subquery instead of `is_admin()` — functionally fine, stylistically inconsistent. Tracked
> in [`TODO.md`](TODO.md).

### Inference container (self-hosted; owned by DMKuZu — ADR-003)

- FastAPI + Uvicorn + **custom Ultralytics fork (YOLOv26 head + EfficientNetV2 backbone)** +
  PyTorch. Public image `ghcr.io/dmkuzu/agartha-inference:<tag>`; weights baked in via Git LFS.
- Default host: DigitalOcean MI300X (AMD/ROCm, validated); NVIDIA fallback (RunPod A5000 /
  Lambda A10). Provider switch = a `local.properties` change, not code.
- **Contract:**
  - `POST /infer` — `Authorization: Bearer <key>`, `Content-Type: image/jpeg`, raw JPEG body.
    Returns `{ predictions: [{ class, confidence, x, y, width, height }], image: { width, height } }`.
    Coordinates are in original-image pixel space. Empty `predictions[]` → client discards the
    frame (no toast, no persistence).
  - `GET /health` — 200 once the model is loaded. `NetworkMonitor` polls every 10 s while
    recording; two consecutive failures stop recording and show "Cloud connection lost."
- Model classes (verbatim): `Ascaris lumbricoides`, `Trichuris trichiura`, `Hookworm`. Any
  other string is forward-incompat — preserved raw in `class_label`, treated as a
  `WRONG_CLASS` candidate. **No server-side confidence filter** (ADR-004) — the human is the
  threshold.
- **Cost discipline:** GPU droplets bill by the second — **destroy after every test/demo**.
  Sampling is 1 frame / 2 s; if an upload is in flight when the next tick fires, skip (don't queue).

### Sync (Phase 1)

`SyncSampleUseCase` runs on verify: resize JPEG → upload to Storage → insert `samples` row →
insert `detections` rows → set Room status `SYNCED` (else `SYNC_FAILED`, retried next session
start). `SyncReportUseCase` mirrors the report row only (no file upload). No WorkManager in
Phase 1. Losing Supabase mid-session does **not** stop recording (writes queue); only
inference-container loss stops it.

---

## 6. Git Workflow

- **Branches** — GitHub Flow: long-lived `main` (production, protected: PR + 2 approvals +
  CI + no force-push) and `develop` (integration, protected: PR + 1 approval + CI). Feature
  branches off `develop`: `feat/<scope>-<desc>`, `fix/…`, `refactor/…`, `docs/…`, `ci/…`,
  `test/…`.
- **Conventional commits** (commitlint-enforced): `<type>(<scope>): <desc>`.
  Types: `feat fix refactor docs style test ci chore`.
  Scopes: `capture inference dashboard reports theme core data ci docs`.
- **PRs** — open against `develop`, ≤ ~400 lines ideal (split if larger); reviewer responds
  within 24h; request changes with specific, actionable feedback.
- **Release** — cut `release/x.y.z` from `develop`, bump `versionCode`/`versionName`, test,
  PR → `main` (2 approvals), tag `vX.Y.Z`, merge `main` back to `develop`.
- **Status:** branch/commit conventions are live. The `.github/` CI workflows
  (`pr-check.yml`, `commitlint.yml`) and the PR template are **proposed, not yet created** —
  part of the first CI-setup PR. See [`TODO.md`](TODO.md).
- `.gitignore` tracks `gradle/libs.versions.toml`, `package.json`, `bun.lock`,
  `commitlint.config.js`, `.github/`, and `schemas/`; ignores `local.properties`, build
  artifacts, `.idea/`, secrets.

---

## 7. Coding Conventions

- **ktlint** (`android_studio` style, `max_line_length = 120`, 4-space indent, final newline)
  + **Detekt**. Run `bun run lint`.
- **Naming** — Class/Object/Composable: PascalCase. Function/property: camelCase. Constant:
  `UPPER_SNAKE_CASE`. Suffixes: `…Entity`, `…Dao`, `…UseCase`, `…Repository` (interface) /
  `…RepositoryImpl`, `…ViewModel`, `…Module`, `…Worker`.
- **One public type per file** (closely related sealed classes / an enum + its extensions may
  share a file).
- **Composables** — screen composables take a ViewModel (`hiltViewModel()`) and read
  `collectAsStateWithLifecycle()`; content composables take plain state + callbacks and have a
  `@Preview`. `Modifier` is always the last param, defaulting to `Modifier`.
- **ViewModels** — expose a single `StateFlow<ScreenState>`; one-shot events (nav, snackbar)
  via `SharedFlow`.
- **KDoc** on every public class / interface / function / property. Private functions only
  when non-obvious.
- **Markers** — `// TODO(lastname): …`, `// FIXME(lastname): …`, `// HACK(lastname): …`
  (always name the owner).
- **Error handling** — never swallow exceptions (log + surface to state). Use Cases return a
  sealed `Result<T>`; ViewModels handle both branches. Logging is `android.util.Log` today.
- **Resources** — user-facing text in `strings.xml`; never hardcode colors — reference design
  tokens (`AppColors.*` / theme tokens). Raw hex appears only in the palette definition file.
- **Testing** — `mockito-kotlin` 5.4.0 + JUnit + coroutines-test. Use Cases & ViewModels: high
  coverage; repositories: medium (fake DAOs); DAOs: instrumented; composables: low (MVP).
  Test names read as sentences (`fun \`capture sets null GPS when location unavailable\`()`).
- **EPG** — `EpgCalculator.epg(count) = count * 24` (hardcoded Kato-Katz multiplier; citation
  TODO). Count = `CONFIRMED` detections in a session, **excluding** `is_repeat` samples;
  manual captures **do** count.

---

## 8. ADR Summaries

ADR history was consolidated from the former ADR files. Status as written: 001–003 Accepted;
004–006 Proposed (implemented in code).

- **ADR-001 — Fused location + `Instant`.** Use `FusedLocationProviderClient`
  (`getCurrentLocation` + `withTimeoutOrNull(5s)` + `CancellationTokenSource`) behind a
  `LocationProvider` interface; permission-checked, returns `null` on denial/timeout (never
  throws). Timestamps are `java.time.Instant` (available at `minSdk 26`, Room-stored as epoch
  millis). Cost: adds a hard Google Play Services dependency (no GMS = no GPS), mitigated by
  the swappable interface.

- **ADR-002 — Supabase + (originally Roboflow) for the MVP; self-hosted stack deferred to
  Phase 2.** A 5-person team can't build FastAPI + Celery + Postgres + MinIO + YOLO in the
  demo window, and continuous per-frame inference makes a queued self-hosted backend
  untenable. Ship on managed services (Supabase Auth + Postgres + Storage + RLS via one SDK);
  defer the self-hosted stack. Accepts vendor lock-in and PHI data-residency risk (tolerable
  for synthetic / research-consent samples only; **must be revisited before real PHI**). The
  layered architecture means a Phase 2 migration touches only the data-layer boundary.

- **ADR-003 — Self-hosted inference container replaces Roboflow Hosted.** The team's actual
  model is a custom Ultralytics fork (YOLOv26 + EfficientNetV2) that Roboflow's hosted runtime
  cannot construct. Run inference in a FastAPI container (Roboflow-compatible response shape),
  published to GHCR with weights baked in, on a rented GPU droplet (DO MI300X default, NVIDIA
  fallback). Bearer-token auth; provider switch = config, not code. Supabase decision
  unchanged. Costs: GPU rental (destroy droplets after use), ROCm validation gate, manual
  deploy.

- **ADR-004 — Verification as human-in-the-loop correction (rejections persist as labeled
  data).** Replaces "Reject = delete". Each detection carries a per-box `verdict`
  (`CONFIRMED` / `FALSE_POSITIVE` / `WRONG_CLASS` / `BOX_INCORRECT`) via a stepped 3-question
  flow (+ optional frame-level Q4 "missed eggs" → `needs_reannotation`). The server drops its
  confidence post-filter — the expert is the threshold. Every submitted frame syncs regardless
  of verdict mix, making `detections` the centralized retraining corpus and enabling precision
  metrics + audit trail. In-app box editing is deferred (offline CVAT/Label Studio). Requires
  Room + Supabase (`0002`) migrations.

- **ADR-005 — Session = fecal smear, manual capture, repeat flag, nullable bbox.** A session
  represents one Kato-Katz slide (adds `sessions.label`; SessionPicker + End-Session UX;
  "Stop Recording" removed — inference auto-pauses). Adds manual capture (`samples.is_manual`,
  one `CONFIRMED` detection with `confidence=1.0` and null bbox → `0006`/`0007`); a Room-only
  `is_repeat` flag (excluded from EPG, never synced); ships EPG in Phase 1 via
  `EpgCalculator.MULTIPLIER = 24` + `SessionEggCountUseCase` (no durable EPG entity); and wires
  the long-dormant `samples.user_note`. Rejects a separate `smears` table (one-slide-per-session
  is the operational norm).

- **ADR-006 — Persisted session reports (Phase 1).** Replaces the write-and-forget CSV export
  with first-class `reports` rows in Room + Supabase (`0008`), row-only sync (CSV stays local;
  cloud mirrors metadata + aggregates only). Multiple reports per session, ordered by
  `generated_at DESC`. `GenerateSessionReportUseCase` returns `Result<Report>`; the CSV gains a
  comment-prefixed header block + `model_class` / `expert_class` / `verdict` / `model_version`
  columns. Surfaced on `SessionDetailScreen` (Reports card, FileProvider share). Excludes
  (Phase 2): administrative cross-session reports, PDF, CSV upload to Storage, auto-retry of
  failed syncs.
