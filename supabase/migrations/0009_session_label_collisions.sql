-- 0009 · Every smear of a patient has a distinct label, whichever phone minted it
--
-- Run via: Supabase dashboard → SQL Editor → paste → Run. By hand, once, per C6.
-- Target: project `agarthavision` (zxojfpfarhhoxjjicphi).
-- Requires: 0007_patient_shared_history.sql (the phone only sees colleagues' labels after it).
--
-- ── Why ──────────────────────────────────────────────────────────────────────
-- A label like `LDNJ-M21-S01` is minted on the phone: the next sequence after every label the
-- phone holds for that patient (SessionLabelGenerator). It was unique per patient in Room only,
-- and Postgres had no constraint, so two medtechs on one patient could both mint `S01` and the
-- server kept both (14zcqntjph7). 0007 makes the phone see colleagues' sessions, which removes
-- most of the cause while online, but two phones that are both offline still cannot see each
-- other, and no rule on the phone alone can stop them choosing the same number.
--
-- ── What this does ───────────────────────────────────────────────────────────
-- A trigger settles it where both phones' rows meet. When a session arrives, or is renamed, with
-- a label another session of the same patient already holds, the arriving row is renamed
-- `<label>-<first four characters of its own id>`, uppercased. The phone's pull brings the new
-- label back as an ordinary change (FetchRemoteDataUseCase), and uses the same scheme itself for
-- any clash it meets before the server has settled it, so both sides converge on one label.
--
-- **It never rejects a row.** A unique constraint would have refused every existing duplicate at
-- apply time and every clashing push after it, and a refused push leaves a smear stuck on the
-- phone that took it. The first session to reach the server keeps the plain label.
--
-- A transaction-scoped advisory lock per patient makes the check-then-rename safe against two
-- pushes for the same patient landing together. It is released at commit.
--
-- ── Existing duplicates ──────────────────────────────────────────────────────
-- Report first. Read-only; run it before this file to see exactly what the rename below touches:
--
--   select patient_id, label, count(*) as sessions,
--          array_agg(id order by started_at, id) as ids_earliest_first
--   from public.sessions
--   where label is not null and btrim(label) <> ''
--   group by patient_id, label
--   having count(*) > 1;
--
-- Then the one-time rename at the end of this file: in each duplicate group the earliest session
-- (by started_at, then id) keeps its label and every later one gets the suffix. Deterministic, so
-- every phone that already disambiguated a clash locally picked the same survivor whenever it
-- saw the rows in that order, and the next pull corrects any phone that did not. Reports already
-- generated keep the label they printed; a report is a snapshot (0001, the reports table).
-- **This rename is a decision for the app team to confirm (14zcqntjph7).** To report only,
-- delete the block marked "One-time rename" before running this file.

begin;

create or replace function public.dedupe_session_label()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.label is null or btrim(new.label) = '' then
        return new;
    end if;
    if tg_op = 'UPDATE' and new.label is not distinct from old.label then
        return new;
    end if;

    perform pg_advisory_xact_lock(hashtext('session_label:' || new.patient_id::text));

    if exists (
        select 1 from public.sessions s
        where s.patient_id = new.patient_id and s.label = new.label and s.id <> new.id
    ) then
        new.label := upper(btrim(new.label) || '-' || left(new.id::text, 4));
        -- Four characters of a uuid collide about once in 65,536 pairs. The full id cannot.
        if exists (
            select 1 from public.sessions s
            where s.patient_id = new.patient_id and s.label = new.label and s.id <> new.id
        ) then
            new.label := upper(btrim(new.label) || '-' || new.id::text);
        end if;
    end if;
    return new;
end;
$$;

create trigger sessions_dedupe_label
before insert or update of label on public.sessions
for each row execute function public.dedupe_session_label();

-- Admins can list what is still duplicated at any time, e.g. from the console. A medtech gets an
-- exception, not rows, the same guard as barangay_prevalence() in 0001.
create or replace function public.session_label_duplicates()
returns table (patient_id uuid, label text, sessions integer, session_ids uuid[])
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if not public.is_admin(auth.uid()) then
        raise exception 'session_label_duplicates() requires the admin role';
    end if;

    return query
    select s.patient_id, s.label, count(*)::integer, array_agg(s.id order by s.started_at, s.id)
    from public.sessions s
    where s.label is not null and btrim(s.label) <> ''
    group by s.patient_id, s.label
    having count(*) > 1;
end;
$$;

revoke execute on function public.session_label_duplicates() from public;
grant execute on function public.session_label_duplicates() to authenticated;

-- ── One-time rename ──────────────────────────────────────────────────────────
-- Every session but the earliest in each (patient_id, label) group gets the suffix. The label
-- column changes, so the trigger above runs for each row and adds the full-id fallback if a
-- suffixed label is itself taken.
update public.sessions s
set label = upper(btrim(s.label) || '-' || left(s.id::text, 4))
from (
    select id,
           row_number() over (partition by patient_id, label order by started_at, id) as rank
    from public.sessions
    where label is not null and btrim(label) <> ''
) ranked
where ranked.id = s.id and ranked.rank > 1;

commit;
