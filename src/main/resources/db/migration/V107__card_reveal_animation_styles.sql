-- Les cinq premières animations réglaient une intensité. Deux catégories
-- également rares peuvent mériter deux mises en scène qui ne se ressemblent pas :
-- on ajoute des caractères, pas des degrés.
ALTER TABLE card_categories
    DROP CONSTRAINT ck_card_categories_reveal_animation;

ALTER TABLE card_categories
    ADD CONSTRAINT ck_card_categories_reveal_animation
        CHECK (reveal_animation IN (
            'AUTO', 'CALM', 'SPARKLE', 'RADIANT', 'SPECTACULAR',
            'HOLOGRAM', 'EMBER', 'FROST', 'COSMIC', 'GOLD', 'GLITCH'
        ));
