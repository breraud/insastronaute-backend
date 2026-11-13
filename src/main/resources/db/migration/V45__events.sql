CREATE TABLE event_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    slug VARCHAR(140) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    short_description VARCHAR(500) NOT NULL,
    source VARCHAR(30) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    registration_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    capacity INTEGER,
    waitlist_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    article_id UUID REFERENCES articles(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_events_source CHECK (source IN ('TOURNAMENT', 'GLOBAL', 'DEPARTMENT')),
    CONSTRAINT ck_events_capacity CHECK (capacity IS NULL OR capacity >= 0),
    CONSTRAINT ck_events_date_range CHECK (ends_at IS NULL OR ends_at >= starts_at)
);

CREATE TABLE event_categories_join (
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES event_categories(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, category_id)
);

CREATE TABLE event_target_departments (
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, department_id)
);

CREATE TABLE event_registrations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_event_registrations_event_user UNIQUE (event_id, user_id),
    CONSTRAINT ck_event_registrations_status CHECK (status IN ('REGISTERED', 'WAITLISTED'))
);

CREATE INDEX ix_events_starts_at
    ON events (starts_at, created_at);

CREATE INDEX ix_events_published_at
    ON events (published_at);

CREATE INDEX ix_event_registrations_user_created_at
    ON event_registrations (user_id, created_at);

CREATE INDEX ix_event_registrations_event_status_created_at
    ON event_registrations (event_id, status, created_at);
