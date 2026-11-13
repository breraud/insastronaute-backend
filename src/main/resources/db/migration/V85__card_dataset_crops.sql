-- Les decoupes photographiees et leurs etiquettes.
--
-- L'image n'est pas ici : seule sa cle d'objet l'est. Ce qui est stocke est la decoupe
-- brute, jamais un tenseur pretraite, pour que le jour ou la normalisation d'echelle change
-- tout se re-derive sans re-scanner de papier.
--
-- strip_id est l'identite de la bande physique. C'est sur elle que se fera la separation
-- entrainement/validation : deux photos d'une meme bande de part et d'autre feraient mesurer
-- de la memorisation a la validation.

CREATE TABLE card_dataset_crops (
    id UUID PRIMARY KEY,
    sheet_id UUID NOT NULL REFERENCES card_dataset_sheets(id) ON DELETE CASCADE,
    strip_id UUID NOT NULL REFERENCES card_dataset_sheet_codes(id) ON DELETE CASCADE,
    label VARCHAR(32),
    status VARCHAR(24) NOT NULL,
    image_object_key VARCHAR(512) NOT NULL,
    image_content_type VARCHAR(100),
    image_byte_size BIGINT,
    image_width_px INTEGER,
    image_height_px INTEGER,
    capture_device VARCHAR(200),
    capture_width_px INTEGER,
    capture_height_px INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_card_dataset_crops_status CHECK (status IN ('LABELLED', 'PENDING_ARBITRATION'))
);

CREATE INDEX ix_card_dataset_crops_sheet ON card_dataset_crops (sheet_id);
CREATE INDEX ix_card_dataset_crops_strip ON card_dataset_crops (strip_id);
