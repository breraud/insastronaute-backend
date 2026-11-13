CREATE TABLE challenge_favorites (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    challenge_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_challenge_favorites_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_challenge_favorites_challenge
        FOREIGN KEY (challenge_id)
        REFERENCES challenges(id)
        ON DELETE CASCADE,
    CONSTRAINT ux_challenge_favorites_user_challenge
        UNIQUE (user_id, challenge_id)
);

CREATE INDEX idx_challenge_favorites_user
    ON challenge_favorites (user_id);
