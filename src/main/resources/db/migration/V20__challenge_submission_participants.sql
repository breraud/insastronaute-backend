CREATE TABLE challenge_submission_participants (
    submission_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (submission_id, user_id),
    CONSTRAINT fk_challenge_submission_participants_submission
        FOREIGN KEY (submission_id)
        REFERENCES challenge_submissions(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_challenge_submission_participants_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);
