ALTER TABLE proof_uploads
    ADD COLUMN original_file_name VARCHAR(255) NOT NULL DEFAULT 'proof';
