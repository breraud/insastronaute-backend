-- L'animation d'ouverture se choisissait sur la rareté mesurée du palier. Une
-- catégorie porte une identité que la statistique ignore : une carte « légendaire »
-- mérite son grand jeu même dans un pack qui en donne souvent.
ALTER TABLE card_categories
    ADD COLUMN reveal_animation VARCHAR(24) NOT NULL DEFAULT 'AUTO';

ALTER TABLE card_categories
    ADD CONSTRAINT ck_card_categories_reveal_animation
        CHECK (reveal_animation IN ('AUTO', 'CALM', 'SPARKLE', 'RADIANT', 'SPECTACULAR'));
