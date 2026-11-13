ALTER TABLE card_print_templates
    ADD COLUMN IF NOT EXISTS page_count INTEGER;

UPDATE card_print_templates
SET page_count = 2
WHERE page_count IS NULL;

ALTER TABLE card_print_templates
    ALTER COLUMN page_count SET NOT NULL;

ALTER TABLE card_print_templates
    ADD COLUMN IF NOT EXISTS bleed_pt DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE card_print_templates
    DROP CONSTRAINT IF EXISTS ck_card_print_templates_page_positive;

ALTER TABLE card_print_templates
    ADD CONSTRAINT ck_card_print_templates_page_positive
        CHECK (page_count >= 2 AND page_width_pt > 0 AND page_height_pt > 0);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_card_print_templates_bleed'
    ) THEN
        ALTER TABLE card_print_templates
            ADD CONSTRAINT ck_card_print_templates_bleed
                CHECK (bleed_pt >= 0 AND bleed_pt <= 20);
    END IF;
END $$;
