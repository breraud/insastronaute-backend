DROP INDEX IF EXISTS ix_event_registrations_event_status_created_at;

CREATE INDEX ix_event_registrations_event_status_created_at_id
    ON event_registrations (event_id, status, created_at, id);
