-- La couleur d'un pixel est un varchar, pas un char.
--
-- V132 a créé `color CHAR(7)` là où l'entité déclare `@Column(length = 7)` sur un
-- String, ce que Hibernate traduit en `varchar(7)`. Sous `ddl-auto=validate`, l'écart
-- n'est pas un avertissement : la validation du schéma échoue, la fabrique de sessions
-- ne se construit pas, et le backend refuse de démarrer. Toute l'application tombe pour
-- une colonne.
--
-- La conversion ne perd rien. Un code couleur `#RRGGBB` fait exactement sept caractères,
-- donc CHAR(7) ne complétait jamais par des espaces : les valeurs déjà écrites passent
-- telles quelles.
ALTER TABLE place_pixel_placements
    ALTER COLUMN color TYPE VARCHAR(7);
