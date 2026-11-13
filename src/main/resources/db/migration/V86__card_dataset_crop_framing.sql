-- Comment la decoupe a ete cadree dans la photo.
--
-- Une decoupe cadree a la main est, par definition, une decoupe que la segmentation automatique
-- n'a pas su traiter : le cas difficile, designe gratuitement par le geste de l'operateur. Sans
-- cette colonne, l'information est perdue a l'instant de l'envoi.
--
-- Les decoupes deja stockees viennent d'outils qui ne connaissaient pas le parametre, et donc du
-- chemin automatique : le defaut les decrit fidelement. La contrainte est doublee ici parce
-- qu'une decoupe rangee a tort parmi les cadrages automatiques diluerait sans bruit le lot des
-- cas difficiles -- exactement le lot qu'on cherche a constituer.

ALTER TABLE card_dataset_crops
    ADD COLUMN framing VARCHAR(16) NOT NULL DEFAULT 'AUTOMATIC',
    ADD CONSTRAINT ck_card_dataset_crops_framing CHECK (framing IN ('AUTOMATIC', 'MANUAL'));
