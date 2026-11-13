CREATE TABLE challenge_categories (
    name VARCHAR(50) PRIMARY KEY,
    default_category BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO challenge_categories (name, default_category, created_at, updated_at)
VALUES ('DEFAULT', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO challenge_categories (name, default_category, created_at, updated_at)
SELECT DISTINCT TRIM(category), FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM challenges
WHERE category IS NOT NULL
  AND TRIM(category) <> ''
  AND TRIM(category) <> 'DEFAULT'
ON CONFLICT (name) DO NOTHING;

UPDATE challenges
SET category = 'DEFAULT'
WHERE category IS NULL OR TRIM(category) = '';

ALTER TABLE challenges
    ALTER COLUMN category SET DEFAULT 'DEFAULT',
    ALTER COLUMN category SET NOT NULL;

ALTER TABLE challenges
    ADD CONSTRAINT fk_challenges_category
        FOREIGN KEY (category)
        REFERENCES challenge_categories(name);
