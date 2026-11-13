-- Memorise la carte tiree sous chaque code d'une planche de dataset.
--
-- Sans cette colonne, une planche generee ne peut pas etre re-rendue : l'affectation
-- carte -> emplacement venait d'un tirage aleatoire que rien ne conservait. La colonne
-- est nullable parce que les planches anterieures n'ont pas cette information ; elles
-- restent telechargeables une par une, mais ne peuvent pas rejoindre le rendu groupe.
--
-- ON DELETE SET NULL : supprimer une carte ne doit pas effacer l'historique d'une planche
-- deja imprimee, et l'import d'archive de cartes vide la table cards.
ALTER TABLE card_dataset_sheet_codes
    ADD COLUMN card_id UUID REFERENCES cards(id) ON DELETE SET NULL;

CREATE INDEX ix_card_dataset_sheet_codes_card ON card_dataset_sheet_codes (card_id);
