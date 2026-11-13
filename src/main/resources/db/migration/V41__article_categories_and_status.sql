CREATE TABLE article_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    slug VARCHAR(140) NOT NULL UNIQUE
);

ALTER TABLE articles
    ADD COLUMN category_id UUID REFERENCES article_categories(id);

ALTER TABLE articles
    DROP CONSTRAINT ck_articles_status;

UPDATE articles
SET status = 'WRITING'
WHERE status = 'DRAFT';

ALTER TABLE articles
    ADD CONSTRAINT ck_articles_status CHECK (status IN ('WRITING', 'PUBLISHED'));

CREATE INDEX ix_articles_category_id
    ON articles (category_id);
