package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.article.domain.Article;
import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.shop.domain.ShopOrderStatus;
import fr.insastronaute.webapi.shop.domain.ShopProduct;
import fr.insastronaute.webapi.shop.domain.ShopProductBundleItem;
import fr.insastronaute.webapi.shop.domain.ShopProductPhoto;
import fr.insastronaute.webapi.shop.dto.ShopBundleItemDto;
import fr.insastronaute.webapi.shop.dto.ShopBundleItemRequest;
import fr.insastronaute.webapi.shop.dto.ShopProductDto;
import fr.insastronaute.webapi.shop.dto.ShopProductPhotoDto;
import fr.insastronaute.webapi.shop.dto.ShopProductRequest;
import fr.insastronaute.webapi.shop.dto.ShopProductSalesDto;
import fr.insastronaute.webapi.shop.repository.ShopCartLineRepository;
import fr.insastronaute.webapi.shop.repository.ShopManualSaleRepository;
import fr.insastronaute.webapi.shop.repository.ShopOrderRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductBundleItemRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShopProductService {
    private final ShopProductRepository productRepository;
    private final ArticleRepository articleRepository;
    private final ShopCartLineRepository cartLineRepository;
    private final ShopOrderRepository orderRepository;
    private final ShopManualSaleRepository manualSaleRepository;
    private final ShopProductBundleItemRepository bundleItemRepository;
    private final ShopProductImageService imageService;
    private final ShopStockService stockService;
    public ShopProductService(ShopProductRepository productRepository, ArticleRepository articleRepository, ShopCartLineRepository cartLineRepository, ShopOrderRepository orderRepository, ShopManualSaleRepository manualSaleRepository, ShopProductBundleItemRepository bundleItemRepository, ShopProductImageService imageService, ShopStockService stockService) {
        this.productRepository = productRepository; this.articleRepository = articleRepository; this.cartLineRepository = cartLineRepository; this.orderRepository = orderRepository; this.manualSaleRepository = manualSaleRepository; this.bundleItemRepository = bundleItemRepository; this.imageService = imageService; this.stockService = stockService;
    }
    @Transactional public ShopProductDto create(ShopProductRequest request) {
        ShopProduct product = new ShopProduct();
        apply(product, request);
        ShopProduct saved = productRepository.save(product);
        applyBundleItems(saved, request);
        return toDto(saved, stockService.orderedByProduct(), stockService.bundleItemsByBundle());
    }
    @Transactional public ShopProductDto update(UUID id, ShopProductRequest request) {
        ShopProduct product = find(id);
        apply(product, request);
        ShopProduct saved = productRepository.save(product);
        applyBundleItems(saved, request);
        return toDto(saved, stockService.orderedByProduct(), stockService.bundleItemsByBundle());
    }
    @Transactional(readOnly = true) public List<ShopProductDto> list() {
        Map<UUID, Long> ordered = stockService.orderedByProduct();
        Map<UUID, List<ShopProductBundleItem>> bundles = stockService.bundleItemsByBundle();
        return productRepository.findAllByOrderByCreatedAtDesc().stream().map(product -> toDto(product, ordered, bundles)).toList();
    }
    @Transactional(readOnly = true) public ShopProductDto get(UUID id) {
        return toDto(find(id), stockService.orderedByProduct(), stockService.bundleItemsByBundle());
    }

    /**
     * Remplace le contenu d'un lot par celui demande.
     *
     * <p>Un remplacement complet, et non un ajustement : une modification qui retire un
     * composant doit pouvoir le retirer, et comparer ligne a ligne pour n'ecrire que les
     * differences ne changerait rien au resultat pour beaucoup plus de code.</p>
     */
    private void applyBundleItems(ShopProduct bundle, ShopProductRequest request) {
        bundleItemRepository.deleteByBundleId(bundle.getId());
        List<ShopBundleItemRequest> requested = request.bundleItems() == null ? List.of() : request.bundleItems();
        if (requested.isEmpty()) {
            return;
        }
        // Un lot dont le stock serait declare a la main mentirait : son restant se deduit
        // de ses composants, et les deux nombres finiraient par se contredire.
        if (bundle.getStock() != null) {
            throw new BadRequestException("Un lot n'a pas de stock propre : il se déduit de son contenu.");
        }
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (ShopBundleItemRequest item : requested) {
            if (item.productId().equals(bundle.getId())) {
                throw new BadRequestException("Un lot ne peut pas se contenir lui-même.");
            }
            if (!seen.add(item.productId())) {
                throw new BadRequestException("Le même produit apparaît deux fois dans le lot.");
            }
            ShopProduct component = productRepository.findById(item.productId())
                    .orElseThrow(() -> new NotFoundException("Produit du lot introuvable."));
            // Un lot de lots n'aurait pas de restant calculable : le minimum dependrait
            // d'un autre minimum, et une boucle le rendrait insoluble.
            if (!bundleItemRepository.findByBundleId(component.getId()).isEmpty()) {
                throw new BadRequestException("Un lot ne peut pas contenir un autre lot.");
            }
            ShopProductBundleItem line = new ShopProductBundleItem();
            line.setBundle(bundle);
            line.setComponent(component);
            line.setQuantity(item.quantity());
            bundleItemRepository.save(line);
        }
    }
    /**
     * Supprime un produit du catalogue.
     *
     * <p>Une vente au comptoir le retient : sa ligne porte le prix et la quantite d'un
     * encaissement reel, et la faire disparaitre avec le produit reecrirait la caisse. La
     * contrainte en base le refuse deja ; on le dit ici en francais plutot que de laisser
     * remonter une erreur de contrainte.</p>
     */
    @Transactional public void delete(UUID id) {
        ShopProduct product = find(id);
        if (manualSaleRepository.existsByProductId(id)) {
            throw new BadRequestException(
                    "Des ventes au comptoir portent sur ce produit : le supprimer effacerait ce qui a ete encaisse.");
        }
        // Un lot qui perdrait un composant n'aurait plus de restant calculable, et
        // continuerait pourtant de se vendre.
        if (bundleItemRepository.existsByComponentId(id)) {
            throw new BadRequestException("Ce produit fait partie d'un lot : retire-le du lot avant de le supprimer.");
        }
        bundleItemRepository.deleteByBundleId(id);
        imageService.deleteProductImagesQuietly(id); cartLineRepository.deleteByProductId(id); productRepository.delete(product);
    }

    /**
     * Ce qui a ete commande de chaque produit, par etat de la precommande.
     *
     * <p>Le total seul ne dit pas quoi commander au fournisseur : une precommande en
     * attente peut encore se defaire, une retiree est derriere nous. Les produits sans
     * aucune commande y figurent aussi, a zero — leur absence se lirait comme un oubli
     * plutot que comme une absence de ventes.</p>
     */
    @Transactional(readOnly = true)
    public List<ShopProductSalesDto> sales() {
        Map<UUID, Map<ShopOrderStatus, Long>> byProduct = new HashMap<>();
        for (Object[] row : orderRepository.sumQuantityByProductAndStatus()) {
            byProduct.computeIfAbsent((UUID) row[0], id -> new EnumMap<>(ShopOrderStatus.class))
                    .put((ShopOrderStatus) row[1], ((Number) row[2]).longValue());
        }
        Map<UUID, Long> manualByProduct = new HashMap<>();
        for (Object[] row : manualSaleRepository.sumQuantityByProduct()) {
            manualByProduct.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        // Le chiffre du comptoir se lit a part : chaque vente porte le prix fige au moment
        // ou elle a eu lieu, que le tarif courant du produit ne dit plus.
        Map<UUID, Long> manualRevenueByProduct = new HashMap<>();
        for (Object[] row : manualSaleRepository.sumRevenueByProduct()) {
            manualRevenueByProduct.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return productRepository.findAllByOrderByCreatedAtDesc().stream().map(product -> {
            Map<ShopOrderStatus, Long> counts = byProduct.getOrDefault(product.getId(), Map.of());
            long pending = counts.getOrDefault(ShopOrderStatus.PENDING, 0L);
            long validated = counts.getOrDefault(ShopOrderStatus.VALIDATED, 0L);
            long completed = counts.getOrDefault(ShopOrderStatus.COMPLETED, 0L);
            long cancelled = counts.getOrDefault(ShopOrderStatus.CANCELLED, 0L);
            long ordered = pending + validated + completed;
            long manual = manualByProduct.getOrDefault(product.getId(), 0L);
            Integer remaining = product.getStock() == null
                    ? null : (int) Math.max(0L, product.getStock() - ordered - manual);
            long revenue = Math.addExact(
                    Math.multiplyExact(product.getPriceCents(), ordered),
                    manualRevenueByProduct.getOrDefault(product.getId(), 0L));
            return new ShopProductSalesDto(product.getId(), product.getName(), product.getPriceCents(),
                    product.getStock(), ordered, manual, pending, validated, completed, cancelled, remaining,
                    revenue);
        }).toList();
    }

    private void apply(ShopProduct product, ShopProductRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) throw new BadRequestException("Le nom du produit est requis.");
        if (request.priceCents() == null || request.priceCents() < 0) throw new BadRequestException("Le prix doit être positif ou nul.");
        if (request.stock() != null && request.stock() < 0) throw new BadRequestException("Le stock doit être positif ou nul.");
        if (request.maxPerOrder() != null && request.maxPerOrder() < 1) throw new BadRequestException("La limite par commande doit valoir au moins 1.");
        product.setName(request.name().trim()); product.setPriceCents(request.priceCents()); product.setOutOfStock(Boolean.TRUE.equals(request.outOfStock()));
        product.setStock(request.stock()); product.setMaxPerOrder(request.maxPerOrder());
        product.setArticle(request.articleId() == null ? null : articleRepository.findById(request.articleId()).orElseThrow(() -> new NotFoundException("Article introuvable.")));
    }
    private ShopProduct find(UUID id) { return productRepository.findWithGraphById(id).orElseThrow(() -> new NotFoundException("Produit introuvable.")); }
    private ShopProductDto toDto(ShopProduct product, Map<UUID, Long> ordered,
                                 Map<UUID, List<ShopProductBundleItem>> bundles) {
        Article article = product.getArticle();
        List<ShopProductPhotoDto> photos = product.getPhotos().stream().sorted(Comparator.comparingInt(ShopProductPhoto::getDisplayOrder)).map(p -> new ShopProductPhotoDto(p.getId(), p.getUrl(), p.getDisplayOrder())).toList();
        List<ShopBundleItemDto> items = bundles.getOrDefault(product.getId(), List.of()).stream()
                .map(item -> new ShopBundleItemDto(item.getComponent().getId(), item.getComponent().getName(), item.getQuantity()))
                .toList();
        return new ShopProductDto(product.getId(), product.getName(), product.getPriceCents(), product.isOutOfStock(),
                product.getStock(), product.getMaxPerOrder(), stockService.remaining(product, ordered, bundles), items,
                article == null ? null : article.getId(), article == null ? null : article.getTitle(), photos);
    }
}
