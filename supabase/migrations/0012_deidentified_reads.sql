-- 0012 · A de-identified read path for super admins
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0001–0011.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- Super admins (`profiles.role = 'admin'`) are the AgarthaVision developers and owners. Each
-- clinic is the controller of its patients' data and AgarthaVision the processor (RA 10173), so
-- running the platform needs no patient's identity (§11, proportionality), and these are health
-- records, most of them about minors (§3(l)). Admin Console decision D19: a super admin sees
-- patients **de-identified** — no name, sex or birthdate (14zcqntjvjw).
--
-- RLS cannot express that. It decides which rows a reader sees, not which columns, and column
-- grants apply to the whole `authenticated` role, which medtechs share. So the de-identified
-- shape is a set of views, each holding only the columns a super admin may read:
--
--   patients_deidentified   every column but lastname, firstname, middle_name, sex, birthdate
--   sessions_deidentified   every column but label — a generated label (`LDNJ-M21-S01`, see
--                           SessionLabelGenerator) carries the name's initials, sex and age
--   samples_deidentified    every column but user_note, free text that can name the patient
--
-- ── Rollout: this is step 1 of 3 ─────────────────────────────────────────────
--   1. This file. Additive: nothing that exists changes, so it is safe to apply any time.
--   2. The Admin Console switches every super admin patient read onto these views (14zcqntjvky).
--   3. 0013_super_admin_reads_deidentified.sql removes the super admin branch from the tables'
--      own SELECT policies. Apply it only after the console from step 2 is deployed: an older
--      console still reads the tables and would show a super admin empty records pages.
--
-- ── How the views are guarded ────────────────────────────────────────────────
-- A view runs with its owner's rights (security definer, the Postgres default), so it reads the
-- base table past RLS. Each view therefore carries its own guard, `is_admin(auth.uid())`, and
-- returns no rows to anyone else — a medtech or an organization admin reads the tables, through
-- the tables' own policies, exactly as before. `security_barrier` stops a caller's filter from
-- being evaluated ahead of that guard. Supabase's linter flags security-definer views; these are
-- deliberate, and the guard is the reason they are safe.
--
-- Plain column lists over one table each, so PostgREST derives the same relationships the tables
-- have: `sessions_deidentified` embeds `patients_deidentified`, `samples_deidentified` embeds
-- `sessions_deidentified`, and both reach `profiles`, `detections`, `sample_species_findings`,
-- `predictions` and the console's `patient_organizations` by their foreign keys.
--
-- ── Room for break-glass support access ──────────────────────────────────────
-- A later time-limited grant (one organization, a stated reason, an expiry, logged in that
-- organization's audit trail) would be honoured by the **tables'** SELECT policies, never by
-- these views. The views stay de-identified for everyone, always.

begin;

create view public.patients_deidentified
with (security_barrier = true)
as
select
    p.id,
    p.psgc_barangay_code,
    p.created_by,
    p.created_at,
    p.updated_at
from public.patients p
where (select public.is_admin(auth.uid()));

create view public.sessions_deidentified
with (security_barrier = true)
as
select
    se.id,
    se.user_id,
    se.patient_id,
    se.device_id,
    se.started_at
from public.sessions se
where (select public.is_admin(auth.uid()));

create view public.samples_deidentified
with (security_barrier = true)
as
select
    sa.id,
    sa.session_id,
    sa.user_id,
    sa.captured_at,
    sa.verified_at,
    sa.storage_path,
    sa.inference_model_version,
    sa.needs_reannotation,
    sa.is_manual,
    sa.deleted_at
from public.samples sa
where (select public.is_admin(auth.uid()));

comment on view public.patients_deidentified is
    'Super admins only (D19): patients without lastname, firstname, middle_name, sex or birthdate. Empty for everyone else.';
comment on view public.sessions_deidentified is
    'Super admins only (D19): sessions without the label, which encodes the patient''s initials, sex and age. Empty for everyone else.';
comment on view public.samples_deidentified is
    'Super admins only (D19): samples without user_note. Empty for everyone else.';

-- Read-only, and only for signed-in callers. Supabase's default privileges would otherwise
-- grant every privilege on a new relation in `public` to anon as well.
revoke all on public.patients_deidentified, public.sessions_deidentified,
              public.samples_deidentified
    from public, anon, authenticated;
grant select on public.patients_deidentified, public.sessions_deidentified,
                public.samples_deidentified
    to authenticated;

commit;
