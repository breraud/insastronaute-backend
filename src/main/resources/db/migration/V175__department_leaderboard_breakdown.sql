-- Le detail du score de chaque departement, fige a la publication du classement.
--
-- « Je comprends pas pourquoi les points des departements ne sont pas la somme de
-- tous les points. » Le score applique une formule, et le classement publie n'en
-- gardait que le resultat : impossible de montrer d'ou il venait sans le
-- recalculer — donc sans risquer d'afficher d'autres chiffres que ceux publies.
--
-- Nulles pour les entrees de joueurs, et pour les classements publies avant.
ALTER TABLE leaderboard_entries
    ADD COLUMN raw_total BIGINT,
    ADD COLUMN member_count INTEGER,
    ADD COLUMN theoretical_headcount INTEGER,
    ADD COLUMN coefficient DOUBLE PRECISION,
    ADD COLUMN promo_points BIGINT,
    ADD COLUMN formula VARCHAR(40),
    ADD COLUMN reference_headcount INTEGER;
