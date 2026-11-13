-- Existing null fingerprints deliberately mark pre-policy department snapshots as
-- stale. The service preserves them and releases a replacement on the next read once
-- the Keycloak role directory is known.
ALTER TABLE leaderboard_snapshots
    ADD COLUMN eligibility_fingerprint VARCHAR(64);
