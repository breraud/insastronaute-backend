-- Un classement Elo du multijoueur, a cote des classements de points.
--
-- Rien a cumuler : le classement est deja sur le profil multijoueur (sts_pvp_profiles.elo), et
-- le cliche le lit au moment ou il est pris, pour les seuls joueurs qui ont dispute un duel
-- classe.
--
-- Ce n'est pas un compte de points, et deux consequences en decoulent ici :
--   * une configuration JOUEURS seulement. La somme des classements d'une promo mesurerait
--     surtout son effectif ; il n'y a pas de classement Elo des departements.
--   * un poids de 0 dans le classement total, pose explicitement. La colonne vaut 20 par defaut
--     (V153) : laisse a sa valeur par defaut, le formulaire des poids aurait affiche un Elo pese a
--     20 % alors que le serveur ne l'additionne jamais.
--
-- Postgres ne sait pas etendre un CHECK en place : on le supprime et on le recree avec la liste
-- complete, dont la derniere version etait celle de V147.
ALTER TABLE leaderboard_configs
    DROP CONSTRAINT ck_leaderboard_configs_metric;

ALTER TABLE leaderboard_configs
    ADD CONSTRAINT ck_leaderboard_configs_metric CHECK (metric IN (
        'TOURNAMENT_POINTS',
        'PHYSICAL_COLLECTION_POINTS',
        'VIRTUAL_COLLECTION_POINTS',
        'CHALLENGE_POINTS',
        'GAME_POINTS',
        'PVP_ELO'
    ));

INSERT INTO leaderboard_configs (scope, metric, total_weight_percent)
VALUES ('USER', 'PVP_ELO', 0)
ON CONFLICT ON CONSTRAINT uk_leaderboard_configs_scope_metric DO NOTHING;
