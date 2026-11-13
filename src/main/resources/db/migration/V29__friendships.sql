CREATE TABLE friendships (
    id UUID PRIMARY KEY,
    requester_user_id UUID NOT NULL,
    addressee_user_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_friendships_requester_user
        FOREIGN KEY (requester_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_friendships_addressee_user
        FOREIGN KEY (addressee_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_friendships_distinct_users
        CHECK (requester_user_id <> addressee_user_id),
    CONSTRAINT ck_friendships_status
        CHECK (status IN ('PENDING', 'ACCEPTED'))
);

CREATE UNIQUE INDEX ux_friendships_user_pair
    ON friendships (
        LEAST(requester_user_id, addressee_user_id),
        GREATEST(requester_user_id, addressee_user_id)
    );

CREATE INDEX idx_friendships_requester_status
    ON friendships(requester_user_id, status, created_at DESC);

CREATE INDEX idx_friendships_addressee_status
    ON friendships(addressee_user_id, status, created_at DESC);
