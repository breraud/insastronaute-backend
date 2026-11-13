CREATE TABLE sts_pvp_matchmaking_lock (
    id SMALLINT PRIMARY KEY,
    CONSTRAINT chk_sts_pvp_matchmaking_lock_singleton CHECK (id = 1)
);

INSERT INTO sts_pvp_matchmaking_lock (id) VALUES (1);
