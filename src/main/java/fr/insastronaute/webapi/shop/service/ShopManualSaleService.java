package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.common.web.NotFoundException;
import fr.insastronaute.webapi.shop.domain.ShopManualSale;
import fr.insastronaute.webapi.shop.domain.ShopProduct;
import fr.insastronaute.webapi.shop.dto.ShopManualSaleDto;
import fr.insastronaute.webapi.shop.dto.ShopManualSaleRequest;
import fr.insastronaute.webapi.shop.repository.ShopManualSaleRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductRepository;
import fr.insastronaute.webapi.user.domain.AppUser;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les ventes au comptoir : ce qui part sans passer par une precommande.
 *
 * <p>Elles retirent du stock comme les autres. Le controle se fait donc au meme endroit
 * et sur le meme calcul — sinon la boutique promettrait en ligne des exemplaires deja
 * partis a la main.</p>
 */
@Service
public class ShopManualSaleService {

    private final ShopManualSaleRepository saleRepository;
    private final ShopProductRepository productRepository;
    private final UserRepository userRepository;
    private final ShopStockService stockService;

    public ShopManualSaleService(ShopManualSaleRepository saleRepository, ShopProductRepository productRepository,
                                 UserRepository userRepository, ShopStockService stockService) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.stockService = stockService;
    }

    @Transactional(readOnly = true)
    public List<ShopManualSaleDto> list() {
        return saleRepository.findAllByOrderBySoldAtDesc().stream().map(ShopManualSaleService::toDto).toList();
    }

    /**
     * Enregistre une vente au comptoir.
     *
     * <p>Le prix est repris du produit maintenant, et fige : le tarif peut bouger ensuite,
     * et le recapitulatif doit continuer de dire ce qui est reellement entre en caisse.</p>
     *
     * <p>Le stock est verifie comme pour une commande. Un produit marque « hors stock »
     * reste vendable ici, en revanche : ce drapeau ferme la boutique en ligne, il ne dit
     * pas que le carton est vide — et refuser une vente qui a materiellement eu lieu
     * empecherait surtout de la consigner.</p>
     */
    @Transactional
    public ShopManualSaleDto record(ShopManualSaleRequest request, String actorKeycloakSub) {
        ShopProduct product = productRepository.findById(request.productId())
                .orElseThrow(() -> new NotFoundException("Produit introuvable."));

        Integer remaining = stockService.remaining(product, stockService.orderedByProduct());
        if (remaining != null && request.quantity() > remaining) {
            throw new BadRequestException(remaining == 0
                    ? "« " + product.getName() + " » n'a plus de stock disponible."
                    : "Il ne reste que " + remaining + " « " + product.getName() + " ».");
        }

        ShopManualSale sale = new ShopManualSale();
        sale.setProduct(product);
        sale.setQuantity(request.quantity());
        sale.setUnitPriceCents(product.getPriceCents());
        sale.setNote(normalizeNote(request.note()));
        sale.setRecordedBy(actor(actorKeycloakSub));
        return toDto(saleRepository.save(sale));
    }

    /**
     * Annule une vente enregistree par erreur.
     *
     * <p>La ligne est supprimee et non marquee : une vente qui n'a pas eu lieu n'a pas a
     * peser sur le stock ni sur la caisse, et il n'y a rien a retrouver plus tard d'un
     * geste qui n'a jamais existe.</p>
     */
    @Transactional
    public void delete(UUID saleId) {
        ShopManualSale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new NotFoundException("Vente introuvable."));
        saleRepository.delete(sale);
    }

    private AppUser actor(String keycloakSub) {
        return keycloakSub == null ? null : userRepository.findByKeycloakSub(keycloakSub).orElse(null);
    }

    private static String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ShopManualSaleDto toDto(ShopManualSale sale) {
        AppUser recorder = sale.getRecordedBy();
        return new ShopManualSaleDto(
                sale.getId(),
                sale.getProduct().getId(),
                sale.getProduct().getName(),
                sale.getQuantity(),
                sale.getUnitPriceCents(),
                Math.multiplyExact(sale.getUnitPriceCents(), sale.getQuantity()),
                sale.getNote(),
                recorder == null ? null : recorder.getDisplayName(),
                sale.getSoldAt()
        );
    }
}
