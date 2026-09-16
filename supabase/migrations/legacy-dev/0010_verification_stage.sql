-- AgarthaVision — optional egg/parasite stage classification on detections
-- Adds `detections.stage`, populated by the expert-verification stage dropdown
-- (ticket 86d4a6jwy) once a species with a defined stage set is selected. The column
-- is nullable: stage is optional and manual-capture detections leave it null.
--
-- Values are intentionally limited to the four life-cycle stages currently supported
-- by the app (UNFERTILIZED, UNEMBRYONATED, EMBRYONATED, LARVATED). The Ascaris
-- morphology values CORTICATED, DECORTICATED, and FERTILIZED are deliberately
-- excluded for now (product decision, ticket 86d4a6jwy) and may be added in a future
-- migration without breaking already-stored rows, since storage is by string value.
--
-- *** COORDINATION WARNING ***
-- This `0010` slot is also claimed by sibling tickets 86d4ab4tq and 86d4akgmf.
-- Whoever lands one of those tickets next MUST append their `alter table` statement(s)
-- below this one in this SAME file — do NOT fork a new `0010_*.sql` file, and do NOT
-- renumber this file.
-- DO NOT run this migration in the Supabase dashboard until all three of
-- 86d4a6jwy, 86d4ab4tq, and 86d4akgmf have landed their changes here. Once this
-- migration has been run against the dashboard, C6 forbids editing it further — any
-- additional column would then require a brand new migration file instead.
--
-- Apply via: Supabase dashboard → SQL Editor → paste → Run (only once all three
-- tickets above have landed their changes in this file).

alter table public.detections
    add column stage text check (stage in ('UNFERTILIZED', 'UNEMBRYONATED', 'EMBRYONATED', 'LARVATED'));

-- ── Rollback (commented out — uncomment + run if this migration must be reverted)

-- alter table public.detections drop column if exists stage;
