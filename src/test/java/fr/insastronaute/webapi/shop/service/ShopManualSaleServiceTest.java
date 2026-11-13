package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.shop.domain.ShopManualSale;
import fr.insastronaute.webapi.shop.domain.ShopProduct;
import fr.insastronaute.webapi.shop.dto.ShopManualSaleRequest;
import fr.insastronaute.webapi.shop.repository.ShopManualSaleRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductRepository;
import fr.insastronaute.webapi.user.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les ventes au comptoir : ce qui part sans passer par une precommande.
 *
 * <p>Elles retirent du stock comme les autres. L'oublier ferait promettre en ligne des
 * exemplaires deja partis a la main.</p>
 */
@ExtendWith(MockitoExtension.class)
class ShopManualSaleServiceTest {

    @Mock private ShopManualSaleRepository saleRepository;
    @Mock private ShopProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private ShopStockService stockService;

    private ShopManualSaleService service() {
        return new ShopManualSaleService(saleRepository, productRepository, userRepository, stockService);
    }

    private ShopProduct product(long priceCents) {
        ShopProduct product = new ShopProduct();
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        product.setName("Sweat INSA");
        product.setPriceCents(priceCents);
        return product;
    }

    /**
     * Le prix est fige a la vente, pas relu ensuite.
     *
     * <p>Le tarif du produit peut bouger : recalculer dessus changerait retroactivement ce
     * qui est entre en caisse.</p>
     */
    @Test
    void freezesTheProductPriceAtTheMomentOfTheSale() {
        ShopProduct product = product(2500L);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(stockService.orderedByProduct()).thenReturn(Map.of());
        when(stockService.remaining(any(), any())).thenReturn(null);
        when(saleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userRepository.findByKeycloakSub(any())).thenReturn(Optional.empty());

        var sale = service().record(new ShopManualSaleRequest(product.getId(), 2, "  espèces  "), "respo-sub");

        assertThat(sale.unitPriceCents()).isEqualTo(2500L);
        assertThat(sale.totalCents()).isEqualTo(5000L);
        // Une note vide ne vaut pas une note : elle ne s'ecrit pas.
        assertThat(sale.note()).isEqualTo("espèces");
    }

    @Test
    void refusesToSellMoreThanWhatIsLeft() {
        ShopProduct product = product(2500L);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(stockService.orderedByProduct()).thenReturn(Map.of());
        when(stockService.remaining(any(), any())).thenReturn(2);

        assertThatThrownBy(() -> service().record(new ShopManualSaleRequest(product.getId(), 3, null), "respo-sub"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Il ne reste que 2 « Sweat INSA ».");
        verify(saleRepository, never()).save(any());
    }

    /** Sans stock declare, rien ne borne : la vente passe. */
    @Test
    void sellsWithoutLimitWhenNoStockIsDeclared() {
        ShopProduct product = product(100L);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(stockService.orderedByProduct()).thenReturn(Map.of());
        when(stockService.remaining(any(), any())).thenReturn(null);
        when(saleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userRepository.findByKeycloakSub(any())).thenReturn(Optional.empty());

        assertThat(service().record(new ShopManualSaleRequest(product.getId(), 500, null), "respo-sub")).isNotNull();
    }

    /**
     * Une vente enregistree par erreur se supprime.
     *
     * <p>Elle n'a pas a peser sur le stock ni sur la caisse, et il n'y a rien a retrouver
     * plus tard d'un geste qui n'a jamais eu lieu.</p>
     */
    @Test
    void removesASaleRecordedByMistake() {
        ShopManualSale sale = new ShopManualSale();
        UUID saleId = UUID.randomUUID();
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));

        service().delete(saleId);

        verify(saleRepository).delete(sale);
    }
}
