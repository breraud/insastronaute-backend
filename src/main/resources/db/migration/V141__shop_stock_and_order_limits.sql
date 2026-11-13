-- Stock et plafonds de precommande.
--
-- Tout est nullable, et null vaut « pas de limite » : la boutique tourne, des
-- precommandes existent, et poser un plafond par defaut en refuserait d'un coup
-- sans que personne l'ait decide. L'admin choisit produit par produit.

ALTER TABLE shop_products
    ADD COLUMN stock INTEGER,
    ADD COLUMN max_per_order INTEGER;

-- Un stock negatif ne veut rien dire ; zero est une valeur legitime, c'est un
-- produit epuise dont on garde la fiche. Un plafond a zero, en revanche, rendrait
-- le produit incommandable sans le dire : pour ca il y a deja `out_of_stock`.
ALTER TABLE shop_products
    ADD CONSTRAINT ck_shop_products_stock_positive CHECK (stock IS NULL OR stock >= 0),
    ADD CONSTRAINT ck_shop_products_max_per_order_positive CHECK (max_per_order IS NULL OR max_per_order >= 1);

ALTER TABLE shop_settings
    ADD COLUMN max_orders_per_user INTEGER,
    ADD COLUMN max_items_per_order INTEGER;

ALTER TABLE shop_settings
    ADD CONSTRAINT ck_shop_settings_max_orders_positive CHECK (max_orders_per_user IS NULL OR max_orders_per_user >= 1),
    ADD CONSTRAINT ck_shop_settings_max_items_positive CHECK (max_items_per_order IS NULL OR max_items_per_order >= 1);
