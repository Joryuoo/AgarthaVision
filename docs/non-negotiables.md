# Non-negotiables

The terse list. No explanation here — each line points at the constraint that explains it in
[`constraints.md`](constraints.md).

## Clinical

- **Never let a model output count as a finding without human confirmation.** → C7
- **Never delete a verified sample, detection, or its Storage object.** A rejection is a
  labelled `FALSE_POSITIVE` row, not a deletion. → C8
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
- **Never give the bottom bar or a brand mark a Material icon.** Those are hand-drawn
  outline drawables; Material glyphs stay inside screens. → C11
- **Never write a raw hex colour outside the palette definition file.** → C11
- **Never hardcode user-facing text.** It goes in `strings.xml`. → C11

## Process

- **Never create or re-introduce a `TODO.md`.** Tasks live in ClickUp.
- **Never push without a local build and test pass.** → C12
- **Never open a PR against `main`.** Target `staging`. → C9
- **Never invent a convention without writing it into `docs/`.** → C13
- **Never trust a document over the code.** → C13
- **Never implement against anything in `docs/_archive/`.**
