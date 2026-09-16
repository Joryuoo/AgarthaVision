-- 0001 · AgarthaVision patient-based schema — consolidated init
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi), which was empty when this was
-- written — zero public tables, zero migrations, no storage buckets.
--
-- ── Why one file and not fourteen ────────────────────────────────────────────
-- The patient-records work inserts a Patient between User and Session and drops enough
-- columns that it cannot share a database with `staging`. Rather than replay
-- 0001–0013 against a fresh project and then alter them, this consolidates all fourteen
-- into the shape the app actually wants. The originals are kept unedited under
-- `legacy-dev/`; they remain the true description of agarthavision-dev and
-- agarthavision-prod, which staging and main still point at.
--
-- Once this file is applied it is never edited (C6). The next change is 0002.
--
-- ── What is deliberately absent ──────────────────────────────────────────────
-- Each omission is commented at its table so nobody "restores" it:
--   * samples.gps_latitude / gps_longitude / gps_accuracy
--   * sessions.notes, sessions.ended_at, sessions.psgc_barangay_code
--   * reports.epg_per_species
--   * a Storage DELETE policy on the `samples` bucket
--
-- See schema.ts, docs/constraints.md C6 and C8, and tickets PB-02 (86d4be3hn) and
-- PB-16 (86d4be3wz).


-- ── Extensions ───────────────────────────────────────────────────────────────
create extension if not exists "uuid-ossp";


-- ── profiles ─────────────────────────────────────────────────────────────────
-- One row per authenticated user. From legacy-dev/0001_init.sql, unchanged.
create table public.profiles (
    id         uuid primary key references auth.users(id) on delete cascade,
    full_name  text,
    role       text not null default 'medtech' check (role in ('medtech', 'admin')),
    created_at timestamptz not null default now()
);

-- Auto-create a profile row on first sign-in.
create or replace function public.handle_new_user()
returns trigger as $$
begin
    insert into public.profiles (id, role) values (new.id, 'medtech');
    return new;
end;
$$ language plpgsql security definer;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- The non-recursive admin check from legacy-dev/0004_fix_profiles_rls_recursion.sql.
-- Every admin branch below calls this. Do NOT inline `select role from public.profiles`
-- into a policy on public.profiles — that is the recursion 0004 existed to fix, and
-- legacy-dev/0008 and /0012 still carry the old form in their own policies.
create or replace function public.is_admin(user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.profiles
        where id = user_id and role = 'admin'
    );
$$;

grant execute on function public.is_admin(uuid) to authenticated;


-- ── patients ─────────────────────────────────────────────────────────────────
-- New in this schema. The unit the medtech works from: a patient owns sessions, a
-- session is one fecal smear.
create table public.patients (
    id                 uuid primary key default uuid_generate_v4(),

    lastname           text not null check (length(btrim(lastname)) > 0),
    firstname          text not null check (length(btrim(firstname)) > 0),

    -- Nullable on purpose. Many patients do not supply one, and a required field
    -- would just collect junk.
    middle_name        text,

    -- Male / Female only. Matches how DOH and WHO STH surveillance data is stratified.
    sex                text not null check (sex in ('M', 'F')),

    -- Birthdate, not age. Age is recomputed per encounter from this, so a record does
    -- not silently go stale as time passes.
    birthdate          date not null,

    -- Canonical zero-padded 10-digit PSGC ('0102801001'), the same rule as
    -- legacy-dev/0010_session_psgc_barangay.sql. The CHECK is load-bearing: the same code
    -- in the numerically shortened form some sources publish ('102801001') would silently
    -- fail to join against the Admin Website's boundary GeoJSON, and every barangay in
    -- regions 01–09 would vanish from the map with no error anywhere.
    psgc_barangay_code text not null check (psgc_barangay_code ~ '^[0-9]{10}$'),

    created_by         uuid not null references public.profiles(id),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now()
);

create index patients_barangay_idx on public.patients(psgc_barangay_code);
create index patients_lastname_idx on public.patients(lower(lastname), lower(firstname));

-- No delete path. Removing a patient is an admin-side action and no client policy grants it.


-- ── patient_users ────────────────────────────────────────────────────────────
-- A patient links to many users. This is what patient visibility resolves through —
-- NOT patients.created_by, which is provenance only. A medtech who did not create a
-- patient can still be given access by an admin inserting a row here.
create table public.patient_users (
    patient_id uuid not null references public.patients(id) on delete cascade,
    user_id    uuid not null references public.profiles(id) on delete cascade,
    linked_at  timestamptz not null default now(),
    primary key (patient_id, user_id)
);

create index patient_users_user_idx on public.patient_users(user_id);

-- Auto-link the creator. This is a trigger rather than a second client call because the
-- patients SELECT policy reads this table: without the row the inserting medtech cannot
-- read back the patient they just created, and PostgREST returns the inserted row on
-- insert. A client-side second call leaves a window where the patient is invisible to
-- its own author, and a failed second call leaves it invisible permanently.
create or replace function public.link_patient_creator()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.patient_users (patient_id, user_id)
    values (new.id, new.created_by)
    on conflict do nothing;
    return new;
end;
$$;

create trigger on_patient_created
after insert on public.patients
for each row execute function public.link_patient_creator();


-- ── sessions ─────────────────────────────────────────────────────────────────
-- One fecal smear, now owned by a patient.
create table public.sessions (
    id         uuid primary key default uuid_generate_v4(),
    user_id    uuid not null references public.profiles(id),

    -- New. A session is always created from a patient's session list, so this is known
    -- at creation and is never null.
    patient_id uuid not null references public.patients(id),

    device_id  text not null,                        -- Settings.Secure.ANDROID_ID
    started_at timestamptz not null default now(),
    label      text                                  -- legacy-dev/0005_session_label.sql
);

-- Deliberately absent, all three:
--   * notes      — gone by product decision. It was being used as an ad-hoc patient
--                  identifier (SessionDetailScreen's patientIdOrNote); the Patient entity
--                  is what replaces it.
--   * ended_at   — sessions never end (86d4ab4vm). Nothing has written it since that
--                  ticket, and a column no writer sets is a trap for the next query that
--                  filters on it: `ended_at IS NULL` silently matches everything.
--   * psgc_barangay_code — the barangay moved to the patient, which is the unit
--                  surveillance actually aggregates on, and which does not change per smear.

create index sessions_user_started_idx  on public.sessions(user_id, started_at desc);
create index sessions_patient_idx       on public.sessions(patient_id, started_at desc);


-- ── samples ──────────────────────────────────────────────────────────────────
-- One verified frame. Unverified frames live only on-device and are never pushed.
create table public.samples (
    id                      uuid primary key default uuid_generate_v4(),
    session_id              uuid not null references public.sessions(id) on delete cascade,
    user_id                 uuid not null references public.profiles(id),
    captured_at             timestamptz not null,               -- captured on-device
    verified_at             timestamptz not null default now(), -- synced to cloud
    storage_path            text not null,                      -- {user_id}/{sample_id}.jpg
    inference_model_version text not null,
    user_note               text,

    -- Frame-level false-negative flag, set by the verification screen's Q4.
    needs_reannotation      boolean not null default false,

    -- A snapshot taken without inference (legacy-dev/0006). Its detection rows carry null
    -- bbox columns.
    is_manual               boolean not null default false,

    -- The C8 tombstone (legacy-dev/0013). A duplicate frame is hidden everywhere a human
    -- or a report looks while its detections, its findings rows and its JPEG stay exactly
    -- where they are. Every query that lists or counts samples must filter
    -- `deleted_at is null` — miss one and a deleted duplicate reappears in a report.
    deleted_at              timestamptz
);

-- Deliberately absent: gps_latitude, gps_longitude, gps_accuracy.
-- The fix was taken at the microscope, so it recorded where the smear was read, not where
-- the infection came from — plotted, it mapped laboratories. Geospatial mapping now keys on
-- the patient's barangay. Nothing ever read these three columns.

create index samples_session_idx on public.samples(session_id);
create index samples_user_idx    on public.samples(user_id);
create index samples_session_manual_idx on public.samples(session_id, is_manual);

-- Partial index: every list and count query filters on this, and live rows are all any of
-- them want.
create index samples_live_session_idx
    on public.samples (session_id)
    where deleted_at is null;


-- ── detections ───────────────────────────────────────────────────────────────
-- One row per detected egg. Doubles as the retraining corpus, which is why a rejection is
-- a labelled row here and never a delete.
create table public.detections (
    id              uuid primary key default uuid_generate_v4(),
    sample_id       uuid not null references public.samples(id) on delete cascade,
    class_label     text not null,                       -- e.g. 'ascaris_lumbricoides'
    confidence      real not null check (confidence between 0 and 1),

    -- Nullable (legacy-dev/0007): a medtech can record an egg without drawing a box, and a
    -- manual capture has no model box at all.
    bbox_x          real,                                -- normalized 0–1
    bbox_y          real,
    bbox_w          real,
    bbox_h          real,

    verdict         text not null default 'CONFIRMED'
        check (verdict in ('CONFIRMED', 'FALSE_POSITIVE', 'WRONG_CLASS', 'BOX_INCORRECT')),

    -- The expert's corrected class. Populated for BOX_INCORRECT as well as WRONG_CLASS:
    -- an egg with a misplaced box is still an egg and still has to be counted, so the
    -- species question is asked whenever the medtech says the box contains an egg.
    expert_class    text,

    -- Provenance, not a verdict. Input fields are pre-filled from model output so the
    -- medtech edits only what is wrong; that pre-fill means an untouched submission yields
    -- verdict = CONFIRMED. Without this column, "a human did not object" would be stored
    -- as "a human confirmed this" in the table used to retrain the model. True iff the
    -- medtech made a deliberate species selection on this box, including re-picking the
    -- pre-filled value.
    species_touched boolean not null default false
);

create index detections_sample_idx  on public.detections(sample_id);
create index detections_class_idx   on public.detections(class_label);
create index detections_verdict_idx on public.detections(verdict);

comment on column public.detections.species_touched is
    'True when the medtech made a deliberate species selection on this box, including re-picking the pre-filled model class. False means the model-assisted pre-fill was submitted untouched: a non-objection, not a confirmation. Training-weight signal only; verdict is unaffected.';


-- ── sample_species_findings ──────────────────────────────────────────────────
-- One row per (species, stage) the medtech logged on one frame, with that pair's
-- low-power-field egg count. One field can hold eggs of more than one species —
-- Dr. Bayron, 2026-09-07: "It is normal to see different egg types within the same field."
--
-- Two shapes were rejected and must not be re-proposed:
--   * a single samples.lpf_count integer — cannot represent a mixed field at all.
--   * a count column on detections — that table is one row per egg, so the per-species
--     count is already COUNT(*) GROUP BY species, and a manual capture has no detection
--     rows to hang a count on.
create table public.sample_species_findings (
    id        uuid primary key default uuid_generate_v4(),
    sample_id uuid not null references public.samples(id) on delete cascade,

    -- Canonical class name, or free text when the dropdown does not cover the species.
    -- Same convention as detections.class_label / expert_class so the two group together.
    species   text not null check (length(btrim(species)) > 0),

    -- Null when the species has no defined stage set. Nothing writes it since 86d4a6jwy
    -- was reverted (9dcfd5d), but the column is how that ticket returns without a
    -- migration. Keep it, and keep it nullable.
    stage     text check (stage in ('UNFERTILIZED', 'UNEMBRYONATED', 'EMBRYONATED', 'LARVATED')),

    -- Eggs of this species and stage in this one low-power field. Always positive: a count
    -- of zero is the absence of a row, not a row holding zero. There is deliberately no
    -- "no egg" species value — a sentinel would have to be excluded from every
    -- species-keyed aggregation forever.
    egg_count integer not null check (egg_count > 0)
);

-- Two partial indexes rather than one `unique nulls not distinct`, which needs PG15.
-- Nothing upserts this table: the client deletes a sample's rows and reinserts them,
-- because a species the medtech removed on re-open must actually disappear.
create unique index sample_species_findings_unique_staged
    on public.sample_species_findings (sample_id, species, stage)
    where stage is not null;

create unique index sample_species_findings_unique_unstaged
    on public.sample_species_findings (sample_id, species)
    where stage is null;

create index sample_species_findings_sample_idx  on public.sample_species_findings (sample_id);
create index sample_species_findings_species_idx on public.sample_species_findings (species);


-- ── reports ──────────────────────────────────────────────────────────────────
-- One row per report generation event. Multiple reports per session are allowed.
create table public.reports (
    id                   uuid primary key default uuid_generate_v4(),
    session_id           uuid not null references public.sessions(id) on delete cascade,
    user_id              uuid not null references public.profiles(id),
    report_type          text not null default 'session' check (report_type in ('session')),
    generated_at         timestamptz not null default now(),
    total_samples        integer not null,
    total_eggs_confirmed integer not null,
    positive_species     text[] not null default '{}',
    csv_file_path        text,
    pdf_file_path        text,
    created_at           timestamptz not null default now()
);

-- Deliberately absent: epg_per_species.
-- EPG is eggs-per-gram via Kato-Katz. Philippine medtechs use direct smear, so the ×24
-- multiplier the app applied was wrong for the method in use, and WHO's light/moderate/
-- heavy bands are defined only against EPG — there is no published intensity table for
-- direct smear to rescale them to. The per-species min–max LPF range replaces it
-- (PB-17 / PB-18). Do not add an epg column back without clinical sign-off.

create index reports_session_generated_idx on public.reports(session_id, generated_at desc);
create index reports_user_generated_idx    on public.reports(user_id, generated_at desc);


-- ── Row Level Security ───────────────────────────────────────────────────────
alter table public.profiles                enable row level security;
alter table public.patients                enable row level security;
alter table public.patient_users           enable row level security;
alter table public.sessions                enable row level security;
alter table public.samples                 enable row level security;
alter table public.detections              enable row level security;
alter table public.sample_species_findings enable row level security;
alter table public.reports                 enable row level security;

-- profiles ───────────────────────────────────────────────────────────────────
create policy "profiles_select_own"
on public.profiles for select
using ( auth.uid() = id or public.is_admin(auth.uid()) );

-- patients ───────────────────────────────────────────────────────────────────
-- Visibility resolves through patient_users. A medtech sees a patient if a link row
-- exists, or if they are an admin. created_by is provenance and grants nothing.
create policy "patients_select_linked"
on public.patients for select
using (
    exists (
        select 1 from public.patient_users pu
        where pu.patient_id = id and pu.user_id = auth.uid()
    )
    or public.is_admin(auth.uid())
);

create policy "patients_insert_own"
on public.patients for insert
with check ( auth.uid() = created_by );

-- Editable: a patient's details can be corrected. Editing does not retroactively change
-- session labels already minted or reports already generated.
create policy "patients_update_linked"
on public.patients for update
using (
    exists (
        select 1 from public.patient_users pu
        where pu.patient_id = id and pu.user_id = auth.uid()
    )
)
with check (
    exists (
        select 1 from public.patient_users pu
        where pu.patient_id = id and pu.user_id = auth.uid()
    )
);

-- No delete policy. Patient deletion is admin-side.

-- patient_users ──────────────────────────────────────────────────────────────
-- A user reads their own links. Inserting a link for someone else is an admin action;
-- the creator's own row is written by the on_patient_created trigger, which is
-- security definer and bypasses this.
create policy "patient_users_select_own"
on public.patient_users for select
using ( auth.uid() = user_id or public.is_admin(auth.uid()) );

-- sessions ───────────────────────────────────────────────────────────────────
create policy "sessions_select_own"
on public.sessions for select
using ( auth.uid() = user_id or public.is_admin(auth.uid()) );

create policy "sessions_insert_own"
on public.sessions for insert
with check ( auth.uid() = user_id );

create policy "sessions_update_own"
on public.sessions for update
using ( auth.uid() = user_id );

-- samples ────────────────────────────────────────────────────────────────────
create policy "samples_select_own"
on public.samples for select
using ( auth.uid() = user_id or public.is_admin(auth.uid()) );

create policy "samples_insert_own"
on public.samples for insert
with check ( auth.uid() = user_id );

-- Verified samples are re-editable, so a sample syncs more than once and every remote
-- write is an upsert. An upsert that conflicts performs an UPDATE; without this policy
-- every re-sync of an edited sample is silently rejected by RLS.
create policy "samples_update_own"
on public.samples for update
using ( auth.uid() = user_id )
with check ( auth.uid() = user_id );

-- detections ─────────────────────────────────────────────────────────────────
create policy "detections_select_via_sample"
on public.detections for select
using (
    exists (
        select 1 from public.samples s
        where s.id = sample_id
          and ( s.user_id = auth.uid() or public.is_admin(auth.uid()) )
    )
);

create policy "detections_insert_via_sample"
on public.detections for insert
with check (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

create policy "detections_update_via_sample"
on public.detections for update
using (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
)
with check (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

-- sample_species_findings ────────────────────────────────────────────────────
create policy "findings_select_via_sample"
on public.sample_species_findings for select
using (
    exists (
        select 1 from public.samples s
        where s.id = sample_id
          and ( s.user_id = auth.uid() or public.is_admin(auth.uid()) )
    )
);

create policy "findings_insert_via_sample"
on public.sample_species_findings for insert
with check (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

create policy "findings_update_via_sample"
on public.sample_species_findings for update
using (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
)
with check (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

-- A count is a current statement, not evidence. When a medtech re-opens a verified sample
-- and removes a species they logged in error, the superseded row must go. This is not a
-- breach of C8: the things C8 protects — the JPEG and the detection rows — are still
-- never deleted.
create policy "findings_delete_via_sample"
on public.sample_species_findings for delete
using (
    exists (select 1 from public.samples s where s.id = sample_id and s.user_id = auth.uid())
);

-- reports ────────────────────────────────────────────────────────────────────
create policy "reports_select_own"
on public.reports for select
using ( auth.uid() = user_id or public.is_admin(auth.uid()) );

create policy "reports_insert_own"
on public.reports for insert
with check ( auth.uid() = user_id );


-- ── Storage: the private `samples` bucket ────────────────────────────────────
-- Path convention: {user_id}/{sample_id}.jpg
-- The bucket itself is created outside this file (PB-01).

create policy "samples: insert own folder"
on storage.objects for insert to authenticated
with check (
    bucket_id = 'samples'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

create policy "samples: select own folder"
on storage.objects for select to authenticated
using (
    bucket_id = 'samples'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

-- Needed for overwrite/upsert flows.
create policy "samples: update own folder"
on storage.objects for update to authenticated
using (
    bucket_id = 'samples'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
)
with check (
    bucket_id = 'samples'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

-- Admins read every object so the admin console can render any medtech's frames.
-- Postgres ORs permissive policies together, so this sits alongside the own-folder SELECT.
create policy "samples: admin read all"
on storage.objects for select to authenticated
using ( bucket_id = 'samples' and public.is_admin(auth.uid()) );

-- NO DELETE POLICY, deliberately. A tombstoned sample keeps its JPEG: detections double as
-- the retraining corpus and deleting the image would destroy it (C8). Read-only for admins
-- too — no admin INSERT/UPDATE/DELETE policy is added.


-- ── Admin surveillance aggregation ───────────────────────────────────────────
-- Per-barangay prevalence, so the admin site never pulls raw sample rows. Serves the 4th
-- general objective (DOH-compliant surveillance reports and geospatial maps); the map is
-- rendered on the Admin Website.
--
-- Changed from legacy-dev/0010_session_psgc_barangay.sql: the barangay code now comes from
-- the patient, not the session. Same unit of observation, same positivity rule, same
-- suppression threshold — only the source of the code moved. Because patients.
-- psgc_barangay_code is NOT NULL, every session now has a barangay, where previously
-- sessions created before 0010 had none and were skipped.
--
-- An RPC rather than a view, because access is decided per row-owner and GRANT cannot tell
-- an admin from a medtech: both hold the `authenticated` role.
--
-- PostGIS is deliberately NOT enabled. The choropleth keys on PSGC, so it is a GROUP BY,
-- not a spatial query.
create or replace function public.barangay_prevalence()
returns table (
    psgc_barangay_code text,
    examined_smears    integer,
    positive_smears    integer,
    prevalence_percent numeric,
    suppressed         boolean
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    -- Minimum cell size before a figure may be published. A barangay with a couple of
    -- smears is effectively an identified patient, so counts below this are withheld and
    -- the row comes back with suppressed = true — which lets the map distinguish "too few
    -- to report" from "no data at all" without disclosing the figure.
    min_examined constant integer := 5;
begin
    if not public.is_admin(auth.uid()) then
        raise exception 'barangay_prevalence() requires the admin role';
    end if;

    return query
    with smear as (
        -- Unit of observation is the session, not the sample: one session = one fecal
        -- smear = one patient specimen (ADR-005). A smear is positive when it carries at
        -- least one detection that was not rejected, matching the rule the app's own egg
        -- count uses. Verdicts reach Postgres uppercase.
        select
            p.psgc_barangay_code as code,
            exists (
                select 1
                from public.samples sa
                join public.detections d on d.sample_id = sa.id
                where sa.session_id = se.id
                  and sa.deleted_at is null
                  and d.verdict <> 'FALSE_POSITIVE'
            ) as is_positive
        from public.sessions se
        join public.patients p on p.id = se.patient_id
        -- Only smears that were actually read: a session with no synced sample was opened
        -- but never verified, and must not dilute the denominator.
        where exists (
            select 1 from public.samples sa
            where sa.session_id = se.id and sa.deleted_at is null
        )
    ),
    unit as (
        select
            smear.code,
            count(*)::integer                                   as examined,
            count(*) filter (where smear.is_positive)::integer   as positive
        from smear
        group by smear.code
    )
    select
        unit.code,
        case when unit.examined >= min_examined then unit.examined end,
        case when unit.examined >= min_examined then unit.positive end,
        case
            when unit.examined >= min_examined
            then round((unit.positive::numeric / unit.examined) * 100, 1)
        end,
        unit.examined < min_examined
    from unit
    order by unit.code;
end;
$$;

-- The is_admin() guard inside the function is the access control; a medtech calling this
-- gets an exception, not rows.
grant execute on function public.barangay_prevalence() to authenticated;
