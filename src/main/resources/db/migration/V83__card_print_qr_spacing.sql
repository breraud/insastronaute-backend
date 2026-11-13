ALTER TABLE card_print_templates
    ADD COLUMN qr_margin_modules INTEGER NOT NULL DEFAULT 4,
    ADD COLUMN qr_inset_pt DOUBLE PRECISION NOT NULL DEFAULT 4;

ALTER TABLE card_print_templates
    ADD CONSTRAINT chk_card_print_qr_margin_modules CHECK (qr_margin_modules BETWEEN 0 AND 8),
    ADD CONSTRAINT chk_card_print_qr_inset_pt CHECK (qr_inset_pt BETWEEN 0 AND 150);
