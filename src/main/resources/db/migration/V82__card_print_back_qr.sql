ALTER TABLE card_print_templates
    ADD COLUMN qr_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN qr_corner VARCHAR(20) NOT NULL DEFAULT 'BOTTOM_RIGHT',
    ADD COLUMN qr_size_pt DOUBLE PRECISION NOT NULL DEFAULT 32,
    ADD CONSTRAINT ck_card_print_templates_qr_size CHECK (qr_size_pt >= 8 AND qr_size_pt <= 300),
    ADD CONSTRAINT ck_card_print_templates_qr_corner
        CHECK (qr_corner IN ('TOP_LEFT', 'TOP_RIGHT', 'BOTTOM_LEFT', 'BOTTOM_RIGHT'));

CREATE TABLE card_print_template_back_slots (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES card_print_templates(id) ON DELETE CASCADE,
    slot_index INTEGER NOT NULL,
    x_pt DOUBLE PRECISION NOT NULL,
    y_pt DOUBLE PRECISION NOT NULL,
    width_pt DOUBLE PRECISION NOT NULL,
    height_pt DOUBLE PRECISION NOT NULL,
    CONSTRAINT ux_card_print_template_back_slots_index UNIQUE (template_id, slot_index),
    CONSTRAINT ck_card_print_template_back_slots_index CHECK (slot_index >= 0 AND slot_index < 9),
    CONSTRAINT ck_card_print_template_back_slots_size CHECK (width_pt > 0 AND height_pt > 0),
    CONSTRAINT ck_card_print_template_back_slots_position CHECK (x_pt >= 0 AND y_pt >= 0)
);

CREATE INDEX ix_card_print_template_back_slots_template
    ON card_print_template_back_slots (template_id);
