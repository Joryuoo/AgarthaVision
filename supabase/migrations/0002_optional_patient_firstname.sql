-- 0002 · Drop the non-blank CHECK on patients.firstname
--
-- Anonymous / codenamed patients store firstname = '' (an empty string, not SQL NULL).
-- PatientFormViewModel.persist() writes "" for every codename path; PatientRemoteDataSource
-- toRow() passes that value straight through.  The CHECK constraint added in 0001 —
--   check (length(btrim(firstname)) > 0)
-- — rejects that insert, causing Supabase sync to silently fail for every codenamed
-- patient.  firstname stays NOT NULL (the app never writes an actual SQL NULL; only ""),
-- so only the blank-value check is relaxed here.

alter table public.patients drop constraint patients_firstname_check;
