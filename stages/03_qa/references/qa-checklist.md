# QA Checklist — Stage 03

Complete this against the changed files from `implementation_notes.md`.

## Architecture layer compliance

- [ ] No ViewModel imports Room / Retrofit / Supabase — only Use Cases
- [ ] No Android import in any file under `domain/`
- [ ] All (new/changed) Use Cases have exactly one public `invoke` / `execute` entry point
- [ ] All (new/changed) Use Cases return a sealed `Result<T>`
- [ ] Repository interfaces live in `domain/repository/`; impls live in `data/repository/`
- [ ] New entities in `data/local/entity/`; domain models in `domain/model/`; mappers in `data/local/mapper/`
- [ ] `@Singleton` used only for the approved list (database, HTTP, SupabaseClient, SessionManager, FlaggedFrameStore, FrameSampler)

## Design system compliance

- [ ] No raw hex colors in app code (only in palette definition file)
- [ ] No raw `fontSize` or `fontWeight` — MaterialTheme typography tokens only
- [ ] All spacing values are 8-px multiples: 4, 8, 12, 16, 20, 24, 32, 48 dp
- [ ] Radius values: sm=8, md=12, lg=16, pill=999 dp only
- [ ] Species names are italic (`FontStyle.Italic`)
- [ ] Capture screen is dark/immersive; all other screens are light
- [ ] No new charting library or icon library added to Gradle dependencies
- [ ] No hardcoded user-facing strings — all in `strings.xml`

## Code style

- [ ] `bun run lint` passes: 0 ktlint violations, 0 Detekt findings
- [ ] `bun run test` passes: 0 failures
- [ ] One public type per file (unless sealed class group or enum + extensions)
- [ ] KDoc on every public class / interface / function / property
- [ ] Test method names read as sentences
- [ ] Error markers include owner name: `// TODO(lastname)`, `// FIXME(lastname)`, `// HACK(lastname)`

## Schema / migrations (only if data model changed)

- [ ] Room migration written and registered in `AgarthaDatabase` (new schema version exported to `app/schemas/`)
- [ ] Supabase SQL migration file committed to `supabase/migrations/` with correct number prefix
- [ ] Migration NOT applied programmatically — requires manual run in Supabase dashboard SQL editor
- [ ] `schema.ts` updated to reflect new columns / tables / enums
- [ ] `CONTEXT.md` updated if the migration adds a new ADR or changes an existing one

## Git hygiene

- [ ] Branch name: `feat/<scope>-<desc>` / `fix/…` / `refactor/…` / `docs/…` / `ci/…` / `test/…`
- [ ] All commits follow conventional format: `<type>(<scope>): <desc>`
- [ ] PR targets `develop` (never `main` directly)
- [ ] PR diff is ≤ ~400 lines (split into multiple PRs if larger)
- [ ] No `local.properties`, `.idea/`, or build artifacts committed
- [ ] No secrets committed

## Documentation sync

- [ ] `CONTEXT.md` updated if any architecture rule, design rule, tech-stack detail, or ADR changed
- [ ] `schema.ts` updated if the data model changed
- [ ] `TODO.md` updated: backlog item marked ✅; known issues updated or closed
- [ ] `AGENTS.md` updated if a new non-negotiable or source-of-truth rule was introduced
