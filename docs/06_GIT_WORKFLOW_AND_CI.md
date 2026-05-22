# AgarthaVision · Git Workflow and CI/CD

> Branching strategy, PR process, GitHub Actions, and release process for a team of 5.

---

## 1. Repository

**URL:** https://github.com/Joryuoo/AgarthaVision.git

### Initial Setup

```bash
git clone https://github.com/Joryuoo/AgarthaVision.git
cd AgarthaVision
bun install          # Install git hooks + lint-staged
bun run build        # Verify the project compiles
```

---

## 2. Branching Strategy — GitHub Flow (Simplified)

For a 5-person team on a single MVP, keep it simple. Two long-lived branches, feature
branches for everything else.

```
main ─────────────────────────────────────── (production-ready, protected)
  │
  ├─ develop ──────────────────────────────── (integration branch, protected)
  │    │
  │    ├─ feat/capture-camerax-preview ────── (feature branch)
  │    ├─ feat/dashboard-detection-overlay ── (feature branch)
  │    ├─ fix/epg-recalculation-rounding ──── (bugfix branch)
  │    └─ docs/update-environment-setup ───── (docs branch)
  │
  └─ release/0.1.0 ──────────────────────── (cut from develop when MVP is ready)
```

### Branch Naming Convention

```
feat/<scope>-<short-description>     # New feature
fix/<scope>-<short-description>      # Bug fix
refactor/<scope>-<short-description> # Code improvement, no behavior change
docs/<short-description>             # Documentation only
ci/<short-description>               # CI/CD changes
test/<scope>-<short-description>     # Test additions or fixes
```

Scopes match the commitlint config: `capture`, `inference`, `dashboard`, `reports`, `theme`, `core`, `data`, `ci`, `docs`.

Examples:
```
feat/capture-camerax-live-preview
feat/dashboard-hitl-approve-flow
fix/data-sync-queue-retry-logic
docs/update-api-contract
```

### Branch Rules

| Branch    | Protection Rules                                                  |
|-----------|-------------------------------------------------------------------|
| `main`    | Require PR, 2 approvals, CI passing, no force push                |
| `develop` | Require PR, 1 approval, CI passing, no force push                 |
| `feat/*`  | No restrictions — developer's workspace                           |

---

## 3. Commit Convention

Enforced by commitlint via Bun/Husky (see `01_ENVIRONMENT_SETUP.md` §5.4).

### Format

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

| Type       | When to use                                      |
|------------|--------------------------------------------------|
| `feat`     | New feature                                      |
| `fix`      | Bug fix                                          |
| `refactor` | Code change that doesn't fix a bug or add a feature |
| `docs`     | Documentation only                               |
| `style`    | Formatting, missing semicolons (no code change)  |
| `test`     | Adding or correcting tests                       |
| `ci`       | CI configuration changes                         |
| `chore`    | Maintenance tasks (deps, build scripts)          |

### Examples

```
feat(capture): implement CameraX live preview with grid overlay
fix(dashboard): correct EPG recalculation after false positive removal
refactor(data): extract SyncQueueManager from repository
docs(core): add KDoc to all Room entity classes
ci: add ktlint check to PR workflow
test(validation): add unit tests for ApproveSampleUseCase
```

---

## 4. Pull Request Process

### 4.1 Creating a PR

1. Push your feature branch to origin.
2. Open a PR against `develop`.
3. Fill in the PR template (see §4.2).
4. Request review from at least 1 team member (2 for `main`).
5. Ensure CI passes (green check).

### 4.2 PR Template

Create `.github/pull_request_template.md`:

```markdown
## What does this PR do?

<!-- Brief description of the change -->

## SDD Reference

<!-- Which module/section does this implement? e.g. "Module 1, Section 1.1 — Live Image Acquisition" -->

## Type of Change

- [ ] New feature (`feat`)
- [ ] Bug fix (`fix`)
- [ ] Refactoring (`refactor`)
- [ ] Documentation (`docs`)
- [ ] CI/CD (`ci`)

## Screenshots / Recordings

<!-- Attach screenshots or screen recordings for UI changes -->

## Testing

- [ ] Unit tests added/updated
- [ ] Manual testing on device/emulator
- [ ] Edge cases considered (offline, permission denied, etc.)

## Checklist

- [ ] Code follows `07_TEAM_CONVENTIONS.md`
- [ ] KDoc added for public functions and classes
- [ ] No hardcoded colors (uses theme tokens)
- [ ] No hardcoded strings (uses resources where appropriate)
- [ ] `bun run lint` passes locally
- [ ] `bun run build` passes locally
```

### 4.3 Code Review Expectations

- **Reviewer responds within 24 hours** (even if just "will review tomorrow").
- **Approve** if the code is correct, tested, and follows conventions.
- **Request Changes** with specific, actionable feedback. Never just "this looks wrong."
- **Comment** for non-blocking suggestions.
- PRs should be **small and focused** — under 400 lines changed is ideal. If a feature is larger, split into stacked PRs.

---

## 5. GitHub Actions CI

### 5.1 PR Check Workflow

Create `.github/workflows/pr-check.yml`:

```yaml
name: PR Check

on:
  pull_request:
    branches: [develop, main]

concurrency:
  group: pr-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Lint (ktlint + Detekt)
        run: ./gradlew ktlintCheck detekt

      - name: Build Debug
        run: ./gradlew assembleDebug

      - name: Unit Tests
        run: ./gradlew testDebugUnitTest

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: app/build/reports/tests/
```

### 5.2 Commit Lint Workflow

Create `.github/workflows/commitlint.yml`:

```yaml
name: Commit Lint

on:
  pull_request:
    branches: [develop, main]

jobs:
  commitlint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: oven-sh/setup-bun@v2

      - run: bun install

      - name: Lint Commits
        run: bunx commitlint --from ${{ github.event.pull_request.base.sha }} --to ${{ github.event.pull_request.head.sha }}
```

---

## 6. Release Process

### When MVP is Ready

1. Create a release branch from `develop`:
   ```bash
   git checkout develop
   git pull
   git checkout -b release/0.1.0
   ```

2. Bump version in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 1
   versionName = "0.1.0"
   ```

3. Final testing on the release branch.
4. Merge `release/0.1.0` → `main` via PR (2 approvals).
5. Tag the release:
   ```bash
   git tag -a v0.1.0 -m "MVP release"
   git push origin v0.1.0
   ```
6. Merge `main` back into `develop` to keep them in sync.
7. Create a GitHub Release with release notes.

---

## 7. `.gitignore`

Ensure the repo ignores build artifacts but tracks plan docs and config:

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
```

**Do track:** `gradle/libs.versions.toml`, `package.json`, `commitlint.config.js`,
`.github/`, `docs/`, `schemas/` (Room exported schemas).
