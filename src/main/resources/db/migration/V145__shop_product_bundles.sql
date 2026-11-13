-- Les lots : plusieurs produits vendus ensemble, a leur propre prix.
--
-- Un lot est un produit comme un autre — il a un nom, un prix, des photos, il se met au
-- panier et se precommande. Ce qui le distingue est ce qu'il contient, et c'est tout ce
-- que cette table ajoute. Le reste de la boutique continue de fonctionner sans savoir
-- qu'il existe.
--
-- Consequence, et c'est le point delicat : le stock d'un lot ne se declare pas, il se
-- deduit. Ce qu'il en reste est le minimum de ce que ses composants permettent de
-- constituer. Un lot « sweat + mug » dont il reste trois sweats et dix mugs, c'est trois
-- lots — et vendre un lot retire un sweat ET un mug.
CREATE TABLE shop_product_bundle_items (
    bundle_product_id UUID NOT NULL REFERENCES shop_products(id) ON DELETE CASCADE,
    component_product_id UUID NOT NULL REFERENCES shop_products(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    PRIMARY KEY (bundle_product_id, component_product_id),
    CONSTRAINT ck_shop_bundle_items_quantity_positive CHECK (quantity >= 1),
    -- Un lot qui se contient lui-meme n'aurait pas de stock calculable : le minimum
    -- dependrait de lui-meme. La regle est plus large -- un lot ne contient aucun lot --
    -- mais celle-ci se pose en base, et le service refuse l'autre.
    CONSTRAINT ck_shop_bundle_items_not_self CHECK (bundle_product_id <> component_product_id)
);

-- Le calcul du restant part du lot pour lire ses composants ; la garde de suppression
-- part du composant pour trouver les lots qui le retiennent.
CREATE INDEX idx_shop_bundle_items_component ON shop_product_bundle_items (component_product_id);
