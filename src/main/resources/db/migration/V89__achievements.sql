-- Achievements: an administrator composes one from a key phrase plus its
-- parameters, and it pays tokens the first time a player satisfies it.

CREATE TABLE achievements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    icon_url VARCHAR(1024),
    icon_object_key VARCHAR(512),
    token_reward INTEGER NOT NULL DEFAULT 0,
    type_key VARCHAR(100) NOT NULL,
    type_parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    active BOOLEAN NOT NULL DEFAULT true,
    hidden BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_achievements_token_reward CHECK (token_reward >= 0)
);

CREATE INDEX idx_achievements_active_order
    ON achievements (active, display_order ASC, title ASC);

-- The catch-up sweep loads active achievements by key phrase.
CREATE INDEX idx_achievements_active_type
    ON achievements (type_key) WHERE active;

-- The unique constraint is the idempotency guarantee: two concurrent triggers
-- race here, and the loser reports "already unlocked" instead of paying twice.
CREATE TABLE achievement_unlocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    achievement_id UUID NOT NULL REFERENCES achievements(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tokens_awarded INTEGER NOT NULL DEFAULT 0,
    unlocked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    seen_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_achievement_unlocks_achievement_user UNIQUE (achievement_id, user_id),
    CONSTRAINT ck_achievement_unlocks_tokens CHECK (tokens_awarded >= 0)
);

CREATE INDEX idx_achievement_unlocks_user
    ON achievement_unlocks (user_id, unlocked_at DESC);

-- Drives the popup queue: pending toasts for one player.
CREATE INDEX idx_achievement_unlocks_unseen
    ON achievement_unlocks (user_id, unlocked_at ASC) WHERE seen_at IS NULL;

-- The "obtain N cards of category X" phrase filters by user, which the existing
-- ux_card_collections_user_card_type unique index already serves on its leading
-- column. Only the duplicate-count scan, which filters on quantity alone with no
-- user predicate, needs an index of its own.
CREATE INDEX IF NOT EXISTS idx_card_collections_quantity
    ON card_collections (quantity DESC);
