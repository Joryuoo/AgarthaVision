---
type: process
status: verified
verified: 2026-09-29
commit: feaa4803
---

# register-patient

Recording a patient, named or codenamed, so smears have someone to belong to.

**Input** — the New Patient form: a name or a codename, sex, birthdate, barangay.
**Output** — a `patients` row and its `patient_users` link, queued for sync.

**consumes** [`PsgcBarangay`](../objects/PsgcBarangay.md), the cached identity from
[`sign-in`](sign-in.md)
**produces** [`Patient`](../objects/Patient.md)

## Movement

1. **Fill the form.** `ui/patients/PatientFormScreen.kt`, driven by `PatientFormViewModel`. A new
   patient starts in **codename mode** (`useCustomCodename = true`). The barangay comes from the
   offline picker (`BarangayPickerDelegate` → `SearchBarangaysUseCase`).
2. **Check for duplicates — named patients only.** `PatientRepository.findDuplicates` matches the
   medtech's patients on surname, first name, middle name, birthdate and sex. A match in the same
   barangay stops the save and shows it; matches elsewhere are offered as a choice
   (`PatientDuplicateDialog.kt`). A failed check logs and proceeds. Codenamed patients skip it.
3. **Name the codenamed.** With no name, `PatientFormViewModel::persist` keeps a typed codename,
   keeps an existing one on edit, or generates one — `CodenameGenerator.generate` from sex and
   age, stacking words (`ALPHA-M24`, `ALPHATANGO-M24`) past the medtech's existing codenames in
   that bucket. The codename goes in `lastname`; `firstname` is `''`.
4. **Write the patient and the link together.** `PatientRepositoryImpl::insert` calls
   `PatientDao::insertPatientWithCreatorLink`, one transaction that upserts the patient and its
   `patient_users` row stamped with the patient's own `createdAt`. Every patient read goes
   through `patient_users`, so a patient without its link would be invisible to its creator.
   Server-side the `on_patient_created` trigger writes the same link
   (`supabase/migrations/0001_init.sql:131-147`).
5. **Queue the push.** `syncScheduler.requestSync()`, fire-and-forget: the save never fails
   because the network did. An edit (`::update`) resets `supabase_status` to `pending` and
   requests sync the same way; the remote write is an upsert.

## Why this shape

A patient is the first row a session needs on the server, so it is saved locally at once and
pushed as soon as possible. The creator link is written with it, in one transaction, because
visibility resolves through `patient_users`, not `created_by`. Codenames exist because many
patients will not, or should not, give a name; `0002_optional_patient_firstname.sql` dropped the
non-blank CHECK that rejected them.

## If you change this

**Hits**
- The form's ViewModel writes through `PatientRepository` directly — there is no create or update
  use case (C1 as-built, `../../constraints.md`).
- `CodenameGenerator.isCodename`, which every screen uses to tell a codename from a surname.
  Changing the codename shape means the regex still has to accept every codename already stored.
- `PatientRemoteDataSource`'s insert row, or a new column never reaches Postgres.
- The session label, which is generated from the patient's name, sex and age
  ([`session-lifecycle`](session-lifecycle.md)) — but only for new sessions.

**Does not hit**
- Existing session labels and reports. A patient edit does not cascade to past smears.
- Deletion. No client path deletes a patient; that is an admin action.

## Surfaces

`ui/patients/PatientsScreen.kt` (the list, search and filters, `ObservePatientsUseCase`) and
`ui/patients/PatientFormScreen.kt` (create and edit).

## See

`ui/patients/PatientFormViewModel.kt`, `data/repository/PatientRepositoryImpl.kt`,
`domain/patient/CodenameGenerator.kt`, `data/local/dao/PatientDao.kt`.
