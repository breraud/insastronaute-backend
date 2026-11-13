-- Une decoupe que l'operateur a jugee inutilisable est ecartee, jamais supprimee.
--
-- Floue, mal cadree, la mauvaise bande : ces photos-la doivent sortir du jeu de donnees. Les
-- effacer serait plus simple et serait une erreur. L'operateur travaille au pouce sur des
-- dizaines de decoupes a la suite ; a ce rythme, une suppression definitive finit par emporter
-- ce qu'on voulait garder. Le statut se change, l'image reste, le geste se defait.
--
-- Le compte des decoupes ecartees est lui-meme une mesure du jeu de donnees : combien de
-- captures ont ete jetees dit quelque chose de la qualite de la collecte. Une ligne effacee ne
-- dit plus rien.
--
-- La contrainte nommee est reecrite plutot qu'abandonnee : sans elle, la base accepterait un
-- statut que le code refuse, et l'ecart ne se verrait qu'au chargement.

ALTER TABLE card_dataset_crops
    DROP CONSTRAINT ck_card_dataset_crops_status;

ALTER TABLE card_dataset_crops
    ADD CONSTRAINT ck_card_dataset_crops_status
        CHECK (status IN ('LABELLED', 'PENDING_ARBITRATION', 'DISCARDED'));
