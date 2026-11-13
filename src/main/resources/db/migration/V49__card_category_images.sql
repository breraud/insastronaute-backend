ALTER TABLE card_categories
    ADD COLUMN image_object_key VARCHAR(512),
    ADD COLUMN thumbnail_object_key VARCHAR(512),
    ADD COLUMN image_url VARCHAR(1024),
    ADD COLUMN thumbnail_url VARCHAR(1024),
    ADD COLUMN content_type VARCHAR(100);
