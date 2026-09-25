# legacy-dev — the pre-patient migration history

`0001_init.sql` through `0013_sample_soft_delete.sql` as they were applied to
**agarthavision-dev** (`frnernrlkuedmbedckaf`), which `staging` and `main` still point at.

They are kept here unedited. C6 says an applied migration is never edited, and these are still
the true description of the dev and prod projects — anything reading those databases should read
these files, not the one above.

They are **not** the description of `agarthavision` (`zxojfpfarhhoxjjicphi`), the project the
patient-records work targets. That project was empty, so `../0001_init.sql` consolidates all
thirteen applied files into one, applies the patient-based changes, and is the only file to run there.
Do not apply anything from this directory to it.

## Retired: `0010_verification_stage.sql`

`0010_verification_stage.sql` was an orphan created alongside `0010_session_psgc_barangay.sql`.
It was **never applied anywhere**: ticket 86d4a6jwy was reverted on staging (`9dcfd5d`) and deprioritised,
leaving the file behind with a collision on number `0010`. PB-24 retires it by removing the file.

The work it represents can return cleanly because two hooks were deliberately preserved for it:
1. The `stage` column **still exists**, nullable, in `sample_species_findings` in the consolidated `0001_init.sql` (`stage text check (stage in ('UNFERTILIZED', 'UNEMBRYONATED', 'EMBRYONATED', 'LARVATED'))`).
2. **Room version 11 is still left free** (`core/database/AgarthaDatabase.kt:78-86`) so returning stage classification will not cause a version/hash collision.
