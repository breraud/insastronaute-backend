-- Le PDF original reste dans le stockage objet. La colonne est nullable pour conserver
-- dans l'historique les planches generees avant que leur PDF soit archive.
ALTER TABLE card_dataset_sheets
    ADD COLUMN pdf_object_key VARCHAR(512);
