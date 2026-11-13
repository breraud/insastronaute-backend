-- La planche de dataset se souvient des codes qu'elle porte.
--
-- Sans cette memoire, une planche scannee n'est pas etiquetable : on a les images et pas les
-- codes. Une position d'une planche est aussi l'identite d'une bande physique -- un bout de
-- papier unique, qui sera photographie plusieurs fois.

CREATE TABLE card_dataset_sheets (
    id UUID PRIMARY KEY,
    sheet_id VARCHAR(16) NOT NULL,
    print_run_id UUID REFERENCES card_print_runs(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_card_dataset_sheets_sheet_id UNIQUE (sheet_id)
);

CREATE TABLE card_dataset_sheet_codes (
    id UUID PRIMARY KEY,
    sheet_id UUID NOT NULL REFERENCES card_dataset_sheets(id) ON DELETE CASCADE,
    strip_position INTEGER NOT NULL,
    code VARCHAR(32) NOT NULL,
    CONSTRAINT ux_card_dataset_sheet_codes_position UNIQUE (sheet_id, strip_position),
    CONSTRAINT ck_card_dataset_sheet_codes_position CHECK (strip_position >= 0)
);

CREATE INDEX ix_card_dataset_sheet_codes_sheet ON card_dataset_sheet_codes (sheet_id);
