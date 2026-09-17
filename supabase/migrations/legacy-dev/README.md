# legacy-dev — the pre-patient migration history

`0001_init.sql` through `0013_sample_soft_delete.sql` as they were applied to
**agarthavision-dev** (`frnernrlkuedmbedckaf`), which `staging` and `main` still point at.

They are kept here unedited. C6 says an applied migration is never edited, and these are still
the true description of the dev and prod projects — anything reading those databases should read
these files, not the one above.

They are **not** the description of `agarthavision` (`zxojfpfarhhoxjjicphi`), the project the
patient-records work targets. That project was empty, so `../0001_init.sql` consolidates all
fourteen files into one, applies the patient-based changes, and is the only file to run there.
Do not apply anything from this directory to it.

`0010_verification_stage.sql` was already orphaned before this move: ticket 86d4a6jwy was
reverted on staging (9dcfd5d) and the file was never applied anywhere. PB-24 retires it.
