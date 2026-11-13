CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    actor_user_id UUID,
    type VARCHAR(80) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_type VARCHAR(80),
    source_id UUID,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_notifications_recipient_user
        FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_notifications_recipient_created_at
    ON notifications(recipient_user_id, created_at DESC);

CREATE INDEX idx_notifications_recipient_unread
    ON notifications(recipient_user_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE INDEX idx_notifications_source
    ON notifications(source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL;
