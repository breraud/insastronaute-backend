CREATE TABLE card_print_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    pdf_object_key VARCHAR(512) NOT NULL,
    pdf_url VARCHAR(1024) NOT NULL,
    page_width_pt DOUBLE PRECISION NOT NULL,
    page_height_pt DOUBLE PRECISION NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    fit_mode VARCHAR(20) NOT NULL DEFAULT 'COVER',
    code_x_pt DOUBLE PRECISION NOT NULL DEFAULT 0,
    code_y_pt DOUBLE PRECISION NOT NULL DEFAULT 0,
    code_width_pt DOUBLE PRECISION NOT NULL DEFAULT 120,
    code_height_pt DOUBLE PRECISION NOT NULL DEFAULT 14,
    code_font_family VARCHAR(30) NOT NULL DEFAULT 'HELVETICA',
    code_font_weight VARCHAR(20) NOT NULL DEFAULT 'BOLD',
    code_font_size_pt DOUBLE PRECISION NOT NULL DEFAULT 9,
    code_color VARCHAR(7) NOT NULL DEFAULT '#111111',
    code_background_color VARCHAR(7),
    code_background_opacity DOUBLE PRECISION NOT NULL DEFAULT 0,
    code_align VARCHAR(20) NOT NULL DEFAULT 'CENTER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_card_print_templates_page_positive
        CHECK (page_width_pt > 0 AND page_height_pt > 0),
    CONSTRAINT ck_card_print_templates_code_size_positive
        CHECK (code_width_pt > 0 AND code_height_pt > 0 AND code_font_size_pt > 0),
    CONSTRAINT ck_card_print_templates_code_opacity
        CHECK (code_background_opacity >= 0 AND code_background_opacity <= 1),
    CONSTRAINT ck_card_print_templates_code_color
        CHECK (code_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_card_print_templates_code_background_color
        CHECK (code_background_color IS NULL OR code_background_color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE TABLE card_print_template_slots (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES card_print_templates(id) ON DELETE CASCADE,
    slot_index INTEGER NOT NULL,
    x_pt DOUBLE PRECISION NOT NULL,
    y_pt DOUBLE PRECISION NOT NULL,
    width_pt DOUBLE PRECISION NOT NULL,
    height_pt DOUBLE PRECISION NOT NULL,
    CONSTRAINT ux_card_print_template_slots_index UNIQUE (template_id, slot_index),
    CONSTRAINT ck_card_print_template_slots_index CHECK (slot_index >= 0 AND slot_index < 9),
    CONSTRAINT ck_card_print_template_slots_size CHECK (width_pt > 0 AND height_pt > 0),
    CONSTRAINT ck_card_print_template_slots_position CHECK (x_pt >= 0 AND y_pt >= 0)
);

CREATE TABLE card_print_runs (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES card_print_templates(id) ON DELETE RESTRICT,
    sheet_count INTEGER NOT NULL,
    instance_count INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    pdf_object_key VARCHAR(512),
    pdf_url VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_card_print_runs_sheet_count CHECK (sheet_count > 0),
    CONSTRAINT ck_card_print_runs_instance_count CHECK (instance_count >= 0)
);

CREATE TABLE card_print_run_slots (
    id UUID PRIMARY KEY,
    print_run_id UUID NOT NULL REFERENCES card_print_runs(id) ON DELETE CASCADE,
    page_index INTEGER NOT NULL,
    slot_index INTEGER NOT NULL,
    card_id UUID NOT NULL REFERENCES cards(id) ON DELETE RESTRICT,
    card_instance_id UUID NOT NULL REFERENCES card_instances(id) ON DELETE RESTRICT,
    CONSTRAINT ux_card_print_run_slots_position UNIQUE (print_run_id, page_index, slot_index),
    CONSTRAINT ux_card_print_run_slots_instance UNIQUE (card_instance_id),
    CONSTRAINT ck_card_print_run_slots_position CHECK (page_index >= 0 AND slot_index >= 0 AND slot_index < 9)
);

CREATE INDEX ix_card_print_template_slots_template
    ON card_print_template_slots (template_id);

CREATE INDEX ix_card_print_runs_template
    ON card_print_runs (template_id);

CREATE INDEX ix_card_print_run_slots_run
    ON card_print_run_slots (print_run_id);

CREATE INDEX ix_card_print_run_slots_card
    ON card_print_run_slots (card_id);
