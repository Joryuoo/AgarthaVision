-- 0014: STH Egg Stage Classification (86d4a6jwy)
-- Adds nullable stage column to detections table and widens stage CHECK constraint on sample_species_findings.

ALTER TABLE public.detections
    ADD COLUMN IF NOT EXISTS stage text;

COMMENT ON COLUMN public.detections.stage IS
    'Developmental stage for STH species (e.g., CORTICATED_FERTILIZED, EMBRYONATED, UNSEGMENTED, EARLY_CLEAVAGE). Nullable.';

-- Widen check constraint on sample_species_findings if stage is present
ALTER TABLE public.sample_species_findings
    DROP CONSTRAINT IF EXISTS sample_species_findings_stage_check;

ALTER TABLE public.sample_species_findings
    ADD CONSTRAINT sample_species_findings_stage_check
    CHECK (stage IS NULL OR length(btrim(stage)) > 0);
