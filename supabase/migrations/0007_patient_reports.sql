-- 0007 · Patient-scoped reports
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0001_init.sql, 0006_drop_species_touched.sql.
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- A report so far always belonged to exactly one session. A patient report pools every
-- included session's findings into one document, so `reports` needs a second scope: a row is
-- either a session report (session_id set, patient_id null) or a patient report (patient_id
-- set, session_id null). `session_ids` records which sessions a patient report pooled, so the
-- report stays reproducible even if the patient's session list changes later (Report.md: a
-- report is a snapshot, not a view).
--
-- ── Rollout ──────────────────────────────────────────────────────────────────
-- Apply BEFORE shipping the build that can generate patient reports: supabase-kt sends an
-- explicit `session_id: null` / `patient_id: <uuid>` payload on insert, and an unmigrated
-- table rejects it (`session_id` is still NOT NULL, `patient_id` does not exist). Apply this
-- before that build reaches any device. Once applied, an OLDER build that still decodes
-- `session_id` as non-null will crash decoding the pull the moment a single patient report
-- exists in `public.reports` — treat this as a hard cutover, not a soft one.

begin;

alter table public.reports alter column session_id drop not null;
alter table public.reports add column patient_id uuid references public.patients(id);
alter table public.reports add column session_ids uuid[];

alter table public.reports drop constraint reports_report_type_check;
alter table public.reports add constraint reports_report_type_check
    check (report_type in ('session', 'patient'));

alter table public.reports add constraint reports_scope_check check (
    (report_type = 'session' and session_id is not null and patient_id is null)
    or (report_type = 'patient' and patient_id is not null and session_id is null)
);

create index reports_patient_generated_idx
    on public.reports(patient_id, generated_at desc)
    where patient_id is not null;

drop policy "reports_insert_own" on public.reports;

-- A patient report additionally requires the inserting user to hold a patient_users link to
-- the patient it reports on — the same tenancy check patient/session writes already use
-- elsewhere. Session reports are unaffected: patient_id is null for them, so the second half
-- of the OR is vacuously true.
create policy "reports_insert_own"
on public.reports for insert
with check (
    auth.uid() = user_id
    and (
        patient_id is null
        or exists (
            select 1 from public.patient_users pu
            where pu.patient_id = reports.patient_id and pu.user_id = auth.uid()
        )
    )
);

commit;
