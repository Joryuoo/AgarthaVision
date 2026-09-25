-- 0010 · add sessions.psgc_barangay_code + the admin surveillance aggregation
--
-- Serves the 4th general objective (DOH-compliant surveillance reports and geospatial
-- maps) and the SRS adjustment "samples should have location to allow for geospatial
-- mapping".
--
-- Why a barangay code and not the GPS fix. Every sample already carries one
-- (samples.gps_latitude/longitude/accuracy, 0001_init.sql), taken at the moment of
-- capture — which is the medtech at the microscope. It records where the smear was read,
-- not where the infection came from, so mapping it would produce a map of laboratories.
-- The GPS fix is retained as provenance for the audit trail; it is not the mapping key.
--
-- Barangay level only. A barangay code resolves upward to city/municipality, province and
-- region through the code itself, so one column is enough — do not denormalise four.
--
-- PostGIS is deliberately NOT enabled. Because the map keys on PSGC rather than on
-- coordinates, the choropleth is a GROUP BY, not a spatial query. PostGIS earns its place
-- only if provenance questions like "samples within 5 km" are wanted later.
--
-- APPLY ORDER. This file is numbered 0010, but 0011_reports_pdf_and_lpf.sql reached staging
-- first, so on any project that tracked staging 0011 is already applied. The slot was
-- reserved while this branch was out; the number is kept so the 0010 citations in schema.ts,
-- docs/map/objects/Session.md and docs/map/objects/PsgcBarangay.md stay true. Either order is
-- safe — this migration touches only public.sessions and adds one function, while 0011 adds a
-- column to public.reports. There is no dependency in either direction.
--
-- Applied to dev on 2026-09-11, together with 0011. Prod has had neither.
--
-- Apply via: Supabase dashboard → SQL Editor → paste → Run.
-- Prerequisites:
--   - 0001_init.sql (sessions, samples, detections)
--   - 0002_verification_fields.sql (detections.verdict)
--   - 0004_fix_profiles_rls_recursion.sql (public.is_admin(uuid))
-- Reversible: yes (see "Rollback" block at the bottom — commented out by default).

-- ── sessions ─────────────────────────────────────────────────────────────────
-- The patient's barangay, which medtechs already have permission to record whether they
-- are working in-clinic or out on field collection.
--
-- Nullable: sessions created before this migration have no barangay, and a smear is still
-- clinically valid without one. The mobile picker makes it required for new sessions.
--
-- Canonical zero-padded 10-digit PSGC ('0102801001'). The CHECK is what keeps the join to
-- the Admin Website's boundary GeoJSON honest — the same code stored in the numerically
-- shortened form some sources publish ('102801001') would silently fail to match.
alter table public.sessions
    add column psgc_barangay_code text
        check (psgc_barangay_code ~ '^[0-9]{10}$');

create index if not exists sessions_psgc_barangay_idx
    on public.sessions(psgc_barangay_code)
    where psgc_barangay_code is not null;


-- ── admin surveillance aggregation ───────────────────────────────────────────
-- Returns per-barangay prevalence so the admin site never pulls raw sample rows.
--
-- An RPC rather than a view, because access has to be decided per row-owner and GRANT
-- cannot tell an admin from a medtech: both hold the `authenticated` role. The function is
-- security definer with an explicit is_admin() guard.
--
-- Unit of observation is the session, not the sample: one session = one fecal smear = one
-- patient specimen (ADR-005). A smear counts as positive when it carries at least one
-- detection that was not rejected — `verdict <> 'FALSE_POSITIVE'`, matching the rule the
-- app's own egg count uses (DetectionDao). Verdicts reach Postgres uppercase.
--
-- Small-cell suppression is not a parameter. A barangay with a couple of smears is
-- effectively an identified patient, so counts below the threshold are withheld and the
-- row is returned with `suppressed = true` — which lets the map distinguish "too few to
-- report" from "no data at all" without disclosing the figure.
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
    -- Minimum cell size before a figure may be published.
    min_examined constant integer := 5;
begin
    if not public.is_admin(auth.uid()) then
        raise exception 'barangay_prevalence() requires the admin role';
    end if;

    return query
    with smear as (
        select
            se.psgc_barangay_code as code,
            exists (
                select 1
                from public.samples sa
                join public.detections d on d.sample_id = sa.id
                where sa.session_id = se.id
                  and d.verdict <> 'FALSE_POSITIVE'
            ) as is_positive
        from public.sessions se
        where se.psgc_barangay_code is not null
          -- Only smears that were actually read: a session with no synced sample was
          -- opened but never verified, and must not dilute the denominator.
          and exists (select 1 from public.samples sa where sa.session_id = se.id)
    ),
    unit as (
        select
            smear.code,
            count(*)::integer as examined,
            count(*) filter (where smear.is_positive)::integer as positive
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


-- ── Rollback (commented out — uncomment + run if this migration must be reverted)

-- drop function if exists public.barangay_prevalence();
-- drop index if exists sessions_psgc_barangay_idx;
-- alter table public.sessions drop column if exists psgc_barangay_code;
