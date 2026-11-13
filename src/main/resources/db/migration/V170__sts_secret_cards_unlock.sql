-- Le pool de cartes secretes de STS s'ouvre une fois, et pour tout le compte.
--
-- Une carte marquee `Secret` ne tombe dans aucune table de recompense tant que le
-- joueur n'a pas joue une carte marquee `SecretUnlock`. La decouverte appartient
-- au compte et non a la run : elle survit a une mort, a un abandon, et vaut pour
-- toutes les runs suivantes -- sans quoi il faudrait la refaire a chaque partie et
-- le secret ne serait qu'une contrainte de plus.
--
-- Une date plutot qu'un booleen : « depuis quand » se repond alors sans autre
-- table, et NULL dit « jamais » sans valeur par defaut a maintenir.
ALTER TABLE users
    ADD COLUMN sts_secret_cards_unlocked_at TIMESTAMP WITH TIME ZONE;
