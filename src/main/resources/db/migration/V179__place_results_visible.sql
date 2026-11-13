-- L'ecran de fin de Place : la fresque figee, les classements, et la suite.
--
-- Un interrupteur a part plutot qu'une deduction de la date de fin : l'evenement
-- se termine a une heure, le palmares se publie sur decision. Les organisateurs
-- relisent la fresque avant de figer un classement, et ranger l'evenement ne doit
-- pas emporter ses resultats.
ALTER TABLE place_configs
    ADD COLUMN results_visible BOOLEAN NOT NULL DEFAULT FALSE;
