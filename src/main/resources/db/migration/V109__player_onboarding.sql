ALTER TABLE users
    ADD COLUMN onboarding_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN onboarding_gender_chosen_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN onboarding_rejection_reason TEXT,
    ADD COLUMN onboarding_submitted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN onboarding_decided_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN onboarding_decided_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE users
    ADD CONSTRAINT ck_users_onboarding_status
        CHECK (onboarding_status IN ('NOT_STARTED', 'PENDING', 'VALIDATED', 'REJECTED'));

CREATE INDEX ix_users_onboarding_pending
    ON users (onboarding_submitted_at)
    WHERE onboarding_status = 'PENDING';
