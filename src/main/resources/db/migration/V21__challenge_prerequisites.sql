CREATE TABLE challenge_prerequisites (
    challenge_id UUID NOT NULL,
    prerequisite_challenge_id UUID NOT NULL,
    PRIMARY KEY (challenge_id, prerequisite_challenge_id),
    CONSTRAINT fk_challenge_prerequisites_challenge
        FOREIGN KEY (challenge_id)
        REFERENCES challenges(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_challenge_prerequisites_prerequisite
        FOREIGN KEY (prerequisite_challenge_id)
        REFERENCES challenges(id)
        ON DELETE CASCADE,
    CONSTRAINT ck_challenge_prerequisites_distinct
        CHECK (challenge_id <> prerequisite_challenge_id)
);
