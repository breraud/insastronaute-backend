ALTER TABLE challenge_categories
    ADD COLUMN system_category BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE challenge_categories
SET system_category = TRUE
WHERE name = 'DEFAULT';

INSERT INTO challenge_categories (name, default_category, system_category, created_at, updated_at)
VALUES ('DAILY', FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO UPDATE SET system_category = TRUE;

CREATE OR REPLACE FUNCTION protect_system_challenge_categories()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.system_category = TRUE THEN
        RAISE EXCEPTION 'System challenge categories cannot be updated or deleted';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_system_challenge_categories
BEFORE UPDATE OR DELETE
ON challenge_categories
FOR EACH ROW
EXECUTE FUNCTION protect_system_challenge_categories();

CREATE OR REPLACE FUNCTION enforce_daily_challenge_end_date()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.category = 'DAILY' AND NEW.end_date IS NULL THEN
        RAISE EXCEPTION 'DAILY challenges require an end_date';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_daily_challenge_end_date
BEFORE INSERT OR UPDATE OF category, end_date
ON challenges
FOR EACH ROW
EXECUTE FUNCTION enforce_daily_challenge_end_date();
