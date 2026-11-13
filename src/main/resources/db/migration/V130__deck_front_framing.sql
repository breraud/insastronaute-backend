-- L'image d'un pack est l'emballage à plat : le recto au centre, le verso sur les
-- bords. Affichée entière, elle montrait surtout les bords — un rectangle en
-- longueur posé dans un cadre vertical, avec deux bandes vides autour.
--
-- Deux nombres suffisent à retrouver le recto : où il est dans la largeur, et de
-- combien il faut grossir pour qu'il remplisse le cadre. Les valeurs par défaut
-- (centre, pas d'agrandissement) donnent le recadrage centré, qui convient déjà
-- à un emballage symétrique.
ALTER TABLE decks
    ADD COLUMN front_x_percent DOUBLE PRECISION NOT NULL DEFAULT 50,
    ADD COLUMN front_zoom DOUBLE PRECISION NOT NULL DEFAULT 1;
