-- La fresque des departements : une seconde fresque, a cote de la libre.
--
-- Sur celle-ci, un joueur ne choisit pas sa couleur : chaque pixel prend celle
-- du departement de son poseur, et la fresque devient un territoire que les
-- departements se disputent. Fermee par defaut : elle n'a de sens que le temps
-- d'un evenement choisi par les organisateurs.
ALTER TABLE place_configs
    ADD COLUMN department_canvas_enabled BOOLEAN NOT NULL DEFAULT FALSE;
