package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.shop.domain.ShopProduct;
import fr.insastronaute.webapi.shop.domain.ShopProductBundleItem;
import fr.insastronaute.webapi.shop.domain.ShopSettings;
import fr.insastronaute.webapi.shop.repository.ShopManualSaleRepository;
import fr.insastronaute.webapi.shop.repository.ShopOrderRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductBundleItemRepository;
import fr.insastronaute.webapi.shop.repository.ShopSettingsRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qui reste a vendre, et ce que les reglages autorisent.
 *
 * <p>Le restant se compte a partir des precommandes plutot que de vivre dans une colonne
 * decrementee. Un compteur aurait a etre remis d'aplomb a chaque annulation, chaque
 * changement de quantite, chaque suppression de commande — et il derive au premier oubli,
 * en silence, jusqu'a vendre ce qui n'existe pas ou refuser ce qui reste.</p>
 */
@Service
public class ShopStockService {

    private final ShopOrderRepository orderRepository;
    private final ShopManualSaleRepository manualSaleRepository;
    private final ShopProductBundleItemRepository bundleItemRepository;
    private final ShopSettingsRepository settingsRepository;

    public ShopStockService(ShopOrderRepository orderRepository, ShopManualSaleRepository manualSaleRepository,
                            ShopProductBundleItemRepository bundleItemRepository,
                            ShopSettingsRepository settingsRepository) {
        this.orderRepository = orderRepository;
        this.manualSaleRepository = manualSaleRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.settingsRepository = settingsRepository;
    }

    /**
     * Ce qui est deja sorti du carton pour chaque produit.
     *
     * <p>Trois sources s'additionnent : les precommandes non annulees, les ventes au
     * comptoir, et ce que les lots vendus ont emporte de chacun de leurs composants. Une
     * vente au comptoir retire un exemplaire aussi surement qu'une commande retiree, et un
     * lot vendu vide autant de cartons qu'il contient de produits — oublier l'une des trois
     * ferait promettre a la boutique du stock deja parti.</p>
     */
    public Map<UUID, Long> orderedByProduct() {
        Map<UUID, Long> direct = new HashMap<>();
        for (Object[] row : orderRepository.sumOrderedQuantityByProduct()) {
            direct.merge((UUID) row[0], ((Number) row[1]).longValue(), Long::sum);
        }
        for (Object[] row : manualSaleRepository.sumQuantityByProduct()) {
            direct.merge((UUID) row[0], ((Number) row[1]).longValue(), Long::sum);
        }

        // Un lot vendu compte pour lui-meme -- c'est ce qu'on lit dans le recapitulatif --
        // et se repercute en plus sur chacun de ses composants. Les deux sont vrais en meme
        // temps : trois lots vendus, ce sont trois lignes de lot et trois sweats de moins.
        Map<UUID, Long> total = new HashMap<>(direct);
        for (ShopProductBundleItem item : bundleItemRepository.findAllWithProducts()) {
            long bundlesTaken = direct.getOrDefault(item.getBundle().getId(), 0L);
            if (bundlesTaken > 0) {
                total.merge(item.getComponent().getId(),
                        Math.multiplyExact(bundlesTaken, (long) item.getQuantity()), Long::sum);
            }
        }
        return total;
    }

    /**
     * Ce qu'il reste d'un produit, ou null quand rien ne le borne.
     *
     * <p>Null n'est pas zero : c'est l'absence de reserve declaree. Renvoyer un nombre
     * laisserait croire a un decompte, et un ecran finirait par afficher « 0 restant »
     * pour un produit qu'on peut commander sans fin.</p>
     *
     * <p>Le restant d'un lot ne se declare pas, il se deduit : c'est le minimum de ce que
     * ses composants permettent de constituer. Trois sweats et dix mugs font trois lots,
     * pas dix. Un composant sans stock declare ne borne rien, et un lot dont aucun
     * composant n'est borne ne l'est pas non plus.</p>
     */
    public Integer remaining(ShopProduct product, Map<UUID, Long> ordered) {
        List<ShopProductBundleItem> items = bundleItemRepository.findByBundleId(product.getId());
        if (!items.isEmpty()) {
            return bundleRemaining(items, ordered);
        }
        if (product.getStock() == null) {
            return null;
        }
        return atLeastZero(product.getStock() - ordered.getOrDefault(product.getId(), 0L));
    }

    /**
     * Le restant d'un produit, sachant deja ce que contient chaque lot.
     *
     * <p>Meme calcul, sans reinterroger la base : les ecrans qui listent tous les produits
     * feraient sinon une requete par ligne.</p>
     */
    public Integer remaining(ShopProduct product, Map<UUID, Long> ordered,
                             Map<UUID, List<ShopProductBundleItem>> itemsByBundle) {
        List<ShopProductBundleItem> items = itemsByBundle.get(product.getId());
        if (items != null && !items.isEmpty()) {
            return bundleRemaining(items, ordered);
        }
        if (product.getStock() == null) {
            return null;
        }
        return atLeastZero(product.getStock() - ordered.getOrDefault(product.getId(), 0L));
    }

    /** Le contenu de tous les lots, range par lot. */
    public Map<UUID, List<ShopProductBundleItem>> bundleItemsByBundle() {
        Map<UUID, List<ShopProductBundleItem>> byBundle = new HashMap<>();
        for (ShopProductBundleItem item : bundleItemRepository.findAllWithProducts()) {
            byBundle.computeIfAbsent(item.getBundle().getId(), id -> new ArrayList<>()).add(item);
        }
        return byBundle;
    }

    private Integer bundleRemaining(List<ShopProductBundleItem> items, Map<UUID, Long> ordered) {
        Integer smallest = null;
        for (ShopProductBundleItem item : items) {
            ShopProduct component = item.getComponent();
            if (component.getStock() == null) {
                continue;
            }
            long left = component.getStock() - ordered.getOrDefault(component.getId(), 0L);
            int possible = atLeastZero(left / item.getQuantity());
            smallest = smallest == null ? possible : Math.min(smallest, possible);
        }
        return smallest;
    }

    private static int atLeastZero(long value) {
        return (int) Math.max(0L, value);
    }

    /** Les reglages de la boutique, ou leurs valeurs par defaut si rien n'a ete pose. */
    public ShopSettings settings() {
        return settingsRepository.findById(ShopSettings.SINGLETON_ID).orElseGet(ShopSettings::new);
    }
}
