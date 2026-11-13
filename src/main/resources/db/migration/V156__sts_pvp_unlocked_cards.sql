ALTER TABLE sts_pvp_profiles
    ADD COLUMN unlocked_cards jsonb NOT NULL DEFAULT '[]'::jsonb;
