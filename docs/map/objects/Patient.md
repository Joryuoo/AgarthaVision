# Patient

**One sentence.** The primary clinical unit a medtech works from — an individual who owns
fecal smear sessions. Table and Room entity are both `patients`.

## Why this shape

A patient owns sessions; a session is one fecal smear (`User -> Patient -> Session -> Sample`).
Before this entity, sessions were unparented smears loosely attributed to users, and medtechs
had no way to track multiple smears from the same person across time.

- **`birthdate` instead of `age`.** Age is recomputed per encounter from the birthdate, so a
  record cannot silently go stale. A stored age is wrong the day after it is written. Stored as
  epoch millis at midnight in `Asia/Manila` (`CLINICAL_ZONE` in `domain/model/Patient.kt`)
  because surveillance is Philippine, making birthdate a calendar fact in local time.
- **Barangay lives here and not on the session.** The barangay is the patient's residence and
  the unit surveillance aggregates on; it does not change between smears.
  `barangay_prevalence()` reaches it through `sessions → patients`, and the admin site's
  choropleth joins the zero-padded 10-digit code against PSGC boundary GeoJSON. The zero-padding
  CHECK (`CHECK (psgc_barangay_code ~ '^[0-9]{10}$')`) is load-bearing — shortened 9-digit forms
  silently fail to match every barangay in regions 01–09.
- **Visibility resolves through `patient_users`, not `created_by`.** `created_by` is provenance
  and grants nothing; the join table `patient_users` is what lets an admin share a patient with a
  second medtech. The `on_patient_created` trigger writes the creator's own row server-side,
  because the SELECT policy reads that table and PostgREST returns the inserted row on insert —
  without the trigger link, the creating medtech cannot read back the patient they just made.
- **There is no delete.** Removing a patient is an admin-side action only; no client path,
  DAO method, or client RLS policy allows deletion.
- **Editing does not cascade.** Existing session labels keep the initials and barangay they were
  minted with, and existing reports keep the details they were generated with. To get updated
  details, generate a new report.

## Shape

**Postgres** (`supabase/migrations/0001_init.sql:91-112`)

| Field | Constraint |
|---|---|
| `id` | PK, default `uuid_generate_v4()` |
| `lastname` | NOT NULL text, `CHECK (length(btrim(lastname)) > 0)` |
| `firstname` | NOT NULL text, `CHECK (length(btrim(firstname)) > 0)` |
| `middle_name` | nullable text |
| `sex` | NOT NULL text, `CHECK (sex in ('M', 'F'))` |
| `birthdate` | NOT NULL date, `CHECK (birthdate <= current_date)` |
| `psgc_barangay_code` | NOT NULL text, `CHECK (psgc_barangay_code ~ '^[0-9]{10}$')` |
| `created_by` | NOT NULL uuid, FK → `profiles(id)` |
| `created_at` | NOT NULL timestamptz, default `now()` |
| `updated_at` | NOT NULL timestamptz, default `now()` |

Indexes on `psgc_barangay_code` (`patients_barangay_idx`) and `(lower(lastname), lower(firstname))`
(`patients_lastname_idx`).

**Room** (`app/src/main/java/com/agarthavision/data/local/entity/PatientEntity.kt:38-82`)

PK column is `patient_id`. Key details:

- `sex` maps through `domain/model/Sex.kt` (`M` / `F`).
- `birthdate` is stored as epoch millis at Philippine midnight.
- `supabase_status` is Room-only (`pending` / `synced` / `sync_failed`).
- The join table is mirrored locally by `PatientUserEntity` (`data/local/entity/PatientUserEntity.kt`),
  written alongside the patient so offline-created patients are visible immediately.

Documented shape: `schema.ts:173-222`.

## Connected to

- **Owns** [`Session`](Session.md), 1 → many (`sessions.patient_id` FK).
- **Joined to** [`Profile`](Profile.md) via `patient_users` join table (`0001_init.sql:117-122`).
- **References** [`PsgcBarangay`](PsgcBarangay.md) by canonical 10-digit code.

## If you change this

**Hits**

- `PatientDao` and `PatientRepositoryImpl` — CRUD and reactive queries.
- `SyncPatientUseCase` and `PatientRemoteDataSource` — remote upserts.
- The New Patient form (`ui/patients/PatientFormScreen.kt`, `PatientFormViewModel.kt`).
- The Patients list (`ui/patients/PatientsScreen.kt`, `PatientsViewModel.kt`). Note that
  the Recent sort (`PatientDao.observePatients`) reads `sessions.started_at` and
  `samples.timestamp`/`verified_at`; `updated_at` alone is only the patient's own last-edit time.
- `barangay_prevalence()` surveillance RPC in Postgres.

**Does not hit**

- **Existing session labels or reports.** A patient edit does not cascade to past smears.
- **Room migration.** Handled by destructive fallback in Phase 1 (`core/di/DatabaseModule.kt:54`).

## Surfaces

Written by `PatientFormScreen` on create/edit. Read by `PatientsScreen`, the New Session flow
(`SessionsViewModel.kt`), and `SyncPatientUseCase`.

## See

`supabase/migrations/0001_init.sql:91-149`,
`app/src/main/java/com/agarthavision/data/local/entity/PatientEntity.kt`,
`domain/model/Patient.kt`, `schema.ts:173-222`.
