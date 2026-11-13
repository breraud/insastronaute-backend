-- Une liste ordonnée ne supporte pas qu'on lui retire une ligne par en dessous.
--
-- Les deux tables de paliers portent `card_order` dans leur clé primaire, et JPA les lit
-- par @OrderColumn : Hibernate reconstruit la liste par indice, et **comble les trous
-- avec des null**. Une carte retirée par la base laisse donc un trou, et la première
-- lecture du pack part en NullPointerException — sur GET /api/decks/{id}, qui n'a rien à
-- voir avec la suppression.
--
-- V134 avait mis `deck_drop_tier_cards` en ON DELETE CASCADE, en croyant aligner une
-- exception sur la règle de V102. C'était l'inverse : le RESTRICT était porteur, et il
-- protégeait aussi `deck_tier_template_cards` — dont le CASCADE dormait depuis V102 sans
-- jamais se déclencher, aucune carte ne pouvant être supprimée.
--
-- Les deux repassent donc en RESTRICT, et c'est le service qui détache la carte de
-- chaque palier avant de la supprimer : en passant par JPA, la renumérotation des indices
-- se fait toute seule et il ne reste aucun trou.

-- 1. Réparer ce qui a déjà été troué : renuméroter chaque palier de 0 à n-1, en gardant
--    l'ordre existant. Sans ça, les paquets déjà abîmés le restent après le correctif.
WITH renumbered AS (
    SELECT drop_tier_id,
           card_order,
           ROW_NUMBER() OVER (PARTITION BY drop_tier_id ORDER BY card_order) - 1 AS new_order
    FROM deck_drop_tier_cards
)
UPDATE deck_drop_tier_cards AS target
SET card_order = renumbered.new_order + 1000000
FROM renumbered
WHERE target.drop_tier_id = renumbered.drop_tier_id
  AND target.card_order = renumbered.card_order
  AND renumbered.new_order <> renumbered.card_order;

-- Le décalage temporaire évite de heurter la clé primaire pendant la réécriture.
UPDATE deck_drop_tier_cards SET card_order = card_order - 1000000 WHERE card_order >= 1000000;

WITH renumbered AS (
    SELECT template_tier_id,
           card_order,
           ROW_NUMBER() OVER (PARTITION BY template_tier_id ORDER BY card_order) - 1 AS new_order
    FROM deck_tier_template_cards
)
UPDATE deck_tier_template_cards AS target
SET card_order = renumbered.new_order + 1000000
FROM renumbered
WHERE target.template_tier_id = renumbered.template_tier_id
  AND target.card_order = renumbered.card_order
  AND renumbered.new_order <> renumbered.card_order;

UPDATE deck_tier_template_cards SET card_order = card_order - 1000000 WHERE card_order >= 1000000;

-- 2. Refermer les deux portes.
ALTER TABLE deck_drop_tier_cards
    DROP CONSTRAINT deck_drop_tier_cards_card_id_fkey;
ALTER TABLE deck_drop_tier_cards
    ADD CONSTRAINT deck_drop_tier_cards_card_id_fkey
        FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE RESTRICT;

ALTER TABLE deck_tier_template_cards
    DROP CONSTRAINT deck_tier_template_cards_card_id_fkey;
ALTER TABLE deck_tier_template_cards
    ADD CONSTRAINT deck_tier_template_cards_card_id_fkey
        FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE RESTRICT;
