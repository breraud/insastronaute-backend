-- Les ventes au comptoir.
--
-- Quelqu'un passe, achète, paye, repart avec l'article. Aucun compte derrière, aucun
-- créneau de retrait : rien de ce que la précommande organise ne s'applique.
--
-- D'où une table à part plutôt qu'une commande déguisée. `shop_orders.user_id` est
-- obligatoire, et le rendre nullable pour ce cas affaiblirait la précommande — chaque
-- lecture devrait désormais se demander si elle regarde une commande sans propriétaire.
--
-- La contrepartie est assumée : le stock restant et le récapitulatif des ventes
-- additionnent maintenant deux sources. C'est le prix de ne pas mélanger deux choses qui
-- n'ont en commun que de retirer un exemplaire du carton.
CREATE TABLE shop_manual_sales (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES shop_products(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    -- Le prix du jour, fige a la vente. Le tarif du produit peut bouger ensuite, et le
    -- recapitulatif doit continuer de dire ce qui est reellement entre en caisse.
    unit_price_cents BIGINT NOT NULL,
    note VARCHAR(280),
    -- Qui a encaisse. La ligne est sa propre trace : pas d'entree au registre
    -- d'administration, qui ne consigne que ce qui change ce qu'un joueur possede.
    recorded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    sold_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_shop_manual_sales_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT ck_shop_manual_sales_price_positive CHECK (unit_price_cents >= 0)
);

-- Le recapitulatif regroupe par produit a chaque lecture.
CREATE INDEX idx_shop_manual_sales_product ON shop_manual_sales (product_id);

-- La liste se lit de la plus recente a la plus ancienne.
CREATE INDEX idx_shop_manual_sales_sold_at ON shop_manual_sales (sold_at DESC);
