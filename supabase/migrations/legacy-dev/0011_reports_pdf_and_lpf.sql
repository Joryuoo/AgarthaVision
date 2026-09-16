-- 0011 · reports.pdf_file_path
--
-- Adds `pdf_file_path` to `public.reports`, mirroring `csv_file_path`
-- (`0008_reports.sql`): a device-local path (or `content://` URI), nullable, never resolvable
-- from another device. Row-only sync — the PDF itself stays on the device that generated it,
-- same as the CSV.
--
-- NOTE: any LPF (Low Power Field density) columns landing in this same numbered slot belong to
-- ticket 86d4a6jxw's separate work on the shared branch and are NOT part of this change. This
-- migration adds pdf_file_path ONLY.
--
-- See schema.ts, docs/map/objects/Report.md, and ticket 86d4a6jyy.

alter table public.reports add column pdf_file_path text;

-- ── revert (commented) ────────────────────────────────────────────────────────
-- Paste this into the Supabase SQL editor to roll this migration back.
--
-- alter table public.reports drop column if exists pdf_file_path;
