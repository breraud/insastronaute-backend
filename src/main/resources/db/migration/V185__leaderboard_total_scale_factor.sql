-- Ce par quoi les points d'une metrique sont multiplies avant leur poids, dans le
-- classement total seulement.
--
-- Les cartes se comptent en milliers quand les defis se comptent en centaines de
-- milliers : a 50 %, les cartes physiques pesaient moins de 5 % du total. Le facteur
-- les met a l'echelle sans toucher aux points de collection, ni aux jetons qu'ils
-- paient, ni aux classements par categorie.
--
-- Il vit, comme le poids, sur la configuration joueur ; les departements reprennent
-- le meme.
ALTER TABLE leaderboard_configs
    ADD COLUMN total_scale_factor INTEGER NOT NULL DEFAULT 1;

ALTER TABLE leaderboard_configs
    ADD CONSTRAINT ck_leaderboard_configs_total_scale_factor
    CHECK (total_scale_factor >= 1);

UPDATE leaderboard_configs
SET total_scale_factor = 10
WHERE scope = 'USER'
  AND metric IN ('PHYSICAL_COLLECTION_POINTS', 'VIRTUAL_COLLECTION_POINTS');
