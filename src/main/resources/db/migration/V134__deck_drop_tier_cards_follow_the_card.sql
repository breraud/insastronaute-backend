-- Un palier de butin suit la carte qu'il distribue.
--
-- V38 posait `ON DELETE RESTRICT` sur ce lien, sans que ce soit un arbitrage : une
-- carte retirée du catalogue ne peut plus tomber, donc la ligne qui la fait tomber n'a
-- plus d'objet. Elle bloquait pourtant la suppression, et l'erreur remontait en 500
-- illisible jusqu'à l'écran d'administration.
--
-- V102 a d'ailleurs tranché l'inverse pour deck_tier_template_cards, qui décrit
-- exactement la même chose côté modèle : c'est cette table-ci qui est l'exception.
--
-- Les trois autres RESTRICT sur cards ne bougent pas, et c'est délibéré :
-- card_instances et card_print_run_slots représentent des cartes physiques imprimées,
-- en circulation. Supprimer la définition d'une carte que quelqu'un tient en main
-- laisserait un objet du monde réel sans rien en face.
ALTER TABLE deck_drop_tier_cards
    DROP CONSTRAINT deck_drop_tier_cards_card_id_fkey;

ALTER TABLE deck_drop_tier_cards
    ADD CONSTRAINT deck_drop_tier_cards_card_id_fkey
        FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE;
