-- Un défi peut porter plusieurs catégories.
--
-- La colonne `category` reste la catégorie principale : c'est elle qui s'affiche
-- sur la carte du défi, et c'est sur elle que reposent les comportements liés à
-- DAILY. Les autres s'ajoutent ici, et servent au filtrage — un défi photo qui
-- est aussi un défi de groupe doit se trouver dans les deux onglets.
--
-- Deux tables plutôt qu'une seule liste ordonnée : la principale est déjà là,
-- référencée par des requêtes et par deux fronts déployés séparément. La
-- déplacer d'un coup casserait tout ce qui la lit avant d'être redéployé.
CREATE TABLE challenge_extra_categories (
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL REFERENCES challenge_categories(name) ON DELETE CASCADE,
    PRIMARY KEY (challenge_id, category)
);

-- Le filtrage part de la catégorie pour trouver les défis : c'est ce sens-là que
-- l'index doit servir.
CREATE INDEX ix_challenge_extra_categories_category
    ON challenge_extra_categories (category);
