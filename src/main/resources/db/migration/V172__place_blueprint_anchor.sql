-- Ou le plan est pose sur la fresque, pour le partager avec lui.
--
-- La position ne vivait que dans la page de celui qui posait le plan. Un ami qui
-- recevait le plan le recevait donc sans elle, et devait deviner ou le mettre —
-- or une fresque de groupe ne tient que si tout le monde pose au meme endroit.
--
-- Nulle tant que l'auteur n'a pas pose le plan : il n'y a pas de position a
-- partager avant.
ALTER TABLE place_blueprints
    ADD COLUMN anchor_x INTEGER,
    ADD COLUMN anchor_y INTEGER;

ALTER TABLE place_blueprints
    ADD CONSTRAINT ck_place_blueprints_anchor
        CHECK ((anchor_x IS NULL AND anchor_y IS NULL) OR (anchor_x >= 0 AND anchor_y >= 0));
