CREATE TABLE department_leaderboard_settings (
    department_id UUID PRIMARY KEY REFERENCES departments(id) ON DELETE CASCADE,
    theoretical_headcount INTEGER NOT NULL CHECK (theoretical_headcount > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE leaderboard_configs
    ADD COLUMN formula VARCHAR(40) NOT NULL DEFAULT 'RAW_TOTAL',
    ADD CONSTRAINT ck_leaderboard_configs_formula CHECK (formula IN (
        'RAW_TOTAL',
        'PER_REGISTERED_NORMALIZED',
        'BALANCED_SQRT',
        'FULL_PARTICIPATION'
    ));
