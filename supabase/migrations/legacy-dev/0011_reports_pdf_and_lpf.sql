-- 0011 · reports.pdf_file_path and lpf_per_species
--
-- Adds `pdf_file_path` and `lpf_per_species` to `public.reports`.
-- Philippine medtechs use Direct Smear, so EPG is replaced by LPF density.
-- `epg_per_species` is retired in favor of `lpf_per_species`.
--
-- See schema.ts, docs/map/objects/Report.md, and ticket 86d4a6jxw.

alter table public.reports add column pdf_file_path text;
alter table public.reports add column lpf_per_species jsonb not null default '{}'::jsonb;

-- ── revert (commented) ────────────────────────────────────────────────────────
-- Paste this into the Supabase SQL editor to roll this migration back.
--
-- alter table public.reports drop column if exists pdf_file_path;
-- alter table public.reports drop column if exists lpf_per_species;
