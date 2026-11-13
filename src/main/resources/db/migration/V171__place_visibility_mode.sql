-- Montrer ou cacher Place dans le front, independamment de sa fenetre.
--
-- Jusqu'ici l'onglet n'existait que pendant l'evenement : impossible de le
-- montrer en avance pour l'annoncer, ou de le retirer d'un evenement ouvert
-- sans toucher a ses dates.
--
-- Trois etats plutot qu'un booleen : AUTO garde le comportement actuel — visible
-- pendant la fenetre, absent en dehors. Un simple « visible » par defaut aurait
-- fait apparaitre Place a tout le monde au deploiement, evenement ferme ou non.
ALTER TABLE place_configs
    ADD COLUMN visibility_mode VARCHAR(10) NOT NULL DEFAULT 'AUTO';

ALTER TABLE place_configs
    ADD CONSTRAINT ck_place_configs_visibility_mode
        CHECK (visibility_mode IN ('AUTO', 'SHOWN', 'HIDDEN'));
