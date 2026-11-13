-- Un classement pour le jeu : ce que les parties ont rapporté.
--
-- Le score d'une partie est déjà enregistré sur la partie elle-même
-- (`sts_runs.retirement_score`), il n'y a donc rien à cumuler sur le joueur : le
-- classement additionne les parties terminées au moment où il est pris. Une
-- colonne de plus sur `users` aurait été un second compteur à tenir d'accord avec
-- le premier.
--
-- Postgres ne sait pas étendre un CHECK en place : on le supprime et on le
-- recrée avec la liste complète.
ALTER TABLE leaderboard_configs
    DROP CONSTRAINT ck_leaderboard_configs_metric;

ALTER TABLE leaderboard_configs
    ADD CONSTRAINT ck_leaderboard_configs_metric CHECK (metric IN (
        'TOURNAMENT_POINTS',
        'PHYSICAL_COLLECTION_POINTS',
        'VIRTUAL_COLLECTION_POINTS',
        'CHALLENGE_POINTS',
        'GAME_POINTS'
    ));

INSERT INTO leaderboard_configs (scope, metric)
VALUES ('USER', 'GAME_POINTS'), ('DEPARTMENT', 'GAME_POINTS')
ON CONFLICT ON CONSTRAINT uk_leaderboard_configs_scope_metric DO NOTHING;
