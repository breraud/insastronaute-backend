CREATE TABLE articles (
    id UUID PRIMARY KEY,
    title VARCHAR(180) NOT NULL DEFAULT '',
    content_html TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_articles_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE article_media (
    id UUID PRIMARY KEY,
    article_id UUID NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    s3_url VARCHAR(1024) NOT NULL,
    file_type VARCHAR(120) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_article_media_article_created_at
    ON article_media (article_id, created_at);
