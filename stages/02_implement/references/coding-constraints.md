# Coding Constraints — Stage 02 (Implement)

Quick-reference enforcement list. Violations are PR blockers.

## Architecture hard rules (from CONTEXT.md §3)

1. ViewModels never import Room / Retrofit / Supabase — they call Use Cases only.
2. Use Cases are single-responsibility — one `operator fun invoke(...)` or `suspend fun execute(...)`.
3. Use Cases return a sealed `Result<T>`; ViewModels handle both branches.
4. `domain/` has zero Android imports — must be unit-testable without Robolectric.
5. Entities (Room) live in `data/local/entity/`; domain models live in `domain/model/`. Mappers convert.
6. Repository interfaces in `domain/repository/`; implementations in `data/repository/`, bound via Hilt `@Binds`.
7. `@Singleton` only for: `AgarthaDatabase`, `OkHttpClient`, `Retrofit`, `SupabaseClient`,
   `SessionManager`, `FlaggedFrameStore`, `FrameSampler`. Repositories and Use Cases are unscoped.

## Design system hard rules (from CONTEXT.md §4)

- Colors: `AgarthaTheme.colors.*` (mode-aware) in screens; `AppColors.*` only inside the
  palette definition files (`Color.kt`, `Palette.kt`) and for mode-independent on-image
  badges. Never raw hex elsewhere.
- Typography: MaterialTheme typography tokens only. Never raw `fontSize` / `fontWeight`.
- Spacing: 8-px grid — `4, 8, 12, 16, 20, 24, 32, 48` dp. No other values.
- Radius: `sm=8`, `md=12`, `lg=16`, `pill=999` dp.
- Capture screen: always dark/immersive, independent of the light/dark toggle. All other
  screens follow the user's theme preference (`ThemeMode`, persisted via DataStore).
- Species names: always italic (`FontStyle.Italic`): *Ascaris lumbricoides*, *Trichuris trichiura*, *Hookworm*.
- No charting library. No external icon library. Hand-crafted inline SVG / Compose drawing only.
- Buttons: pill shape. Primary = maroon accent fill / on-accent text. Secondary = gray-100 /
  gray-900. Destructive = red / white. Gold is a brand highlight fill only — always paired
  with dark text, never the primary-button color and never adjacent to amber.
- No shadows on plain cards — flat with 1px gray-100 hairline. Shadows only on modal sheets and Active Session hero.

## Kotlin style

- `max_line_length = 120`; 4-space indent; final newline; `android_studio` ktlint profile.
- One public type per file (closely related sealed classes / enum + extensions may share).
- Error markers always name the owner: `// TODO(lastname): …`, `// FIXME(lastname): …`, `// HACK(lastname): …`.
- Never swallow exceptions — log + surface to state.

## Schema / migration discipline

- Room schema v7 is current. If a change is needed, increment to v8 and write a Room migration
  in `AgarthaDatabase`. Export the new schema JSON to `app/schemas/`.
- Next Supabase migration: `0009_<name>.sql`. Commit the file; apply manually in the dashboard SQL editor.
- Never apply Supabase migrations programmatically from app code.
- After changing the data model, update `../../schema.ts`.

## EPG calculation

`EpgCalculator.epg(count) = count * 24` (hardcoded Kato-Katz multiplier — citation deferred).
Count = `CONFIRMED` detections, excluding `is_repeat` samples. Manual captures count.
Do not change the multiplier without an ADR and a `prep_methods` table (Phase 2).
