-- Le type de preuve attendu par un defi.
--
-- Il n'existait que sur la soumission : le joueur annoncait lui-meme s'il
-- deposait un texte ou des fichiers, et rien ne verifiait que c'etait bien ce
-- que le defi demandait. Cote application, le formulaire lisait un champ que
-- l'API ne renvoyait pas — toujours indefini, donc toujours le depot de
-- fichiers, meme pour un defi qui n'attend qu'une phrase.
--
-- MEDIA par defaut : c'est ce que tous les defis existants attendent, et ce
-- que le formulaire proposait de toute facon.
ALTER TABLE challenges
    ADD COLUMN proof_type VARCHAR(20) NOT NULL DEFAULT 'MEDIA';

ALTER TABLE challenges
    ADD CONSTRAINT ck_challenges_proof_type
        CHECK (proof_type IN ('MEDIA', 'TEXT'));
