-- Le réglage ne concerne plus la seule construction des decks PvP : il fait voir
-- à tout le monde une collection complète, pour tester ce que les cartes
-- débloquent sans avoir à les distribuer. Son nom doit le dire.
ALTER TABLE sts_pvp_settings RENAME TO collection_test_settings;
ALTER TABLE collection_test_settings RENAME COLUMN all_cards_unlocked TO all_cards_owned;
ALTER TABLE collection_test_settings RENAME CONSTRAINT ck_sts_pvp_settings_singleton TO ck_collection_test_settings_singleton;
