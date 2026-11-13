package fr.insastronaute.webapi.shop.dto;

import java.util.UUID;

/**
 * Ce qui a ete commande d'un produit, par etat de la precommande.
 *
 * <p>Le detail par statut est la parce que le total seul ne dit pas quoi commander au
 * fournisseur : une precommande en attente peut encore se defaire, une retiree est
 * derriere nous. Les annulees figurent aussi — savoir combien se sont defaites en dit
 * autant que le total.</p>
 *
 * @param ordered   ce qui tient encore par precommande : en attente, validees et retirees
 * @param manual    ce qui est parti au comptoir, sans precommande
 * @param remaining le stock moins tout ce qui est sorti, ou null quand aucun stock n'est
 *                  pose. Les deux sources s'y additionnent : un exemplaire vendu a la
 *                  main manque au carton autant qu'un exemplaire retire.
 */
public record ShopProductSalesDto(
        UUID productId,
        String name,
        long priceCents,
        Integer stock,
        long ordered,
        long manual,
        long pending,
        long validated,
        long completed,
        long cancelled,
        Integer remaining,
        long revenueCents
) {
}
