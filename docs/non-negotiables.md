# Non-negotiables

The terse list. No explanation here — each line points at the constraint that explains it in
[`constraints.md`](constraints.md).

## Clinical

- **Never let a model output count as a finding without human confirmation.** → C7
- **Never hard-delete a verified sample, detection, or its Storage object.** A rejection is a
  labelled `FALSE_POSITIVE` row, not a deletion. A `deleted_at` tombstone is not a delete: it
  hides the sample everywhere a human looks while the corpus keeps everything. → C8
- **Never add a server-side confidence filter.** The expert is the threshold. → C7

## Data

- **Never change schema behaviour without updating both the migration SQL and `schema.ts`.** → C6
- **Never apply a migration programmatically.** Numbered file, committed, run by hand in the
  Supabase dashboard. → C6
- **Never edit a migration that has already been applied.** Add the next number instead. → C6
- **Never write to `storage.objects` directly.** Go through the Supabase Storage API. → C6

## Secrets

- **Never commit `local.properties`, a real Supabase key, or a real inference token.** → C10
- **Never hardcode a key in Kotlin or Gradle.** `BuildConfig` from `local.properties`, or CI
  `-P` properties. → C10

## Architecture

- **Never import Room, Retrofit, or Supabase from a ViewModel.** → C1
- **Never import an Android API into `domain/`.** → C2
- **Never put a repository implementation in `domain/`, or an interface in `data/`.** → C3

## Design

- **Never introduce a second theme or a charting library.** → C11
- **Never hand-draw an icon.** Bottom-bar and brand glyphs are Material Symbols exports in
  `ui/icons/`; in-screen glyphs may come from `Icons.*`. No SVG path data or `ImageVector`
  coordinates written by hand. → C11
- **Never write a raw hex colour outside the palette definition file.** → C11
- **Never hardcode user-facing text.** It goes in `strings.xml`. → C11

## Process

- **Never create or re-introduce a `TODO.md`.** Tasks live in ClickUp.
- **Never over-specify implementation in an AI-assisted ticket.** State the problem, expected behavior, and acceptance criteria; treat code paths and fixes as hints. → C9
- **Never push without a local build and test pass.** → C12
- **Never run `connectedAndroidTest` / `connectedDebugAndroidTest` on a phone that holds real
  samples.** It uninstalls the app afterwards, which wipes every unsynced sample and every frame
  still waiting for inference. Emulator or test device only. → `commands.md`
- **Never open a PR against `main` or `staging` directly.** Target `development`. → C9
- **Never invent a convention without writing it into `docs/`.** → C13
- **Never change code without updating the shelf card that describes it** — in the same PR,
  or mark the card `status: stale`. → `CONTEXT.md` house rule 5
- **Never trust a document over the code.** → C13
- **Never implement against anything in `docs/_archive/`.**
