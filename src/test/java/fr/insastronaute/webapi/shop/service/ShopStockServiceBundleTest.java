package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.shop.domain.ShopProduct;
import fr.insastronaute.webapi.shop.domain.ShopProductBundleItem;
import fr.insastronaute.webapi.shop.repository.ShopManualSaleRepository;
import fr.insastronaute.webapi.shop.repository.ShopOrderRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductBundleItemRepository;
import fr.insastronaute.webapi.shop.repository.ShopSettingsRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Le stock d'un lot ne se declare pas, il se deduit.
 *
 * <p>Ce qu'il en reste est le minimum de ce que ses composants permettent de constituer, et
 * vendre un lot vide autant de cartons qu'il contient de produits. C'est le calcul le plus
 * facile a rendre faux de toute la boutique : deux sources qui s'additionnent d'un cote,
 * un minimum de l'autre.</p>
 */
@ExtendWith(MockitoExtension.class)
class ShopStockServiceBundleTest {

    @Mock private ShopOrderRepository orderRepository;
    @Mock private ShopManualSaleRepository manualSaleRepository;
    @Mock private ShopProductBundleItemRepository bundleItemRepository;
    @Mock private ShopSettingsRepository settingsRepository;

    private ShopStockService service() {
        return new ShopStockService(orderRepository, manualSaleRepository, bundleItemRepository, settingsRepository);
    }

    private ShopProduct product(String name, Integer stock) {
        ShopProduct product = new ShopProduct();
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        product.setName(name);
        product.setStock(stock);
        return product;
    }

    private ShopProductBundleItem item(ShopProduct bundle, ShopProduct component, int quantity) {
        ShopProductBundleItem line = new ShopProductBundleItem();
        line.setBundle(bundle);
        line.setComponent(component);
        line.setQuantity(quantity);
        return line;
    }

    /** Trois sweats et dix mugs font trois lots, pas dix : c'est le plus rare qui borne. */
    @Test
    void aBundleIsBoundedByItsScarcestComponent() {
        ShopProduct bundle = product("Pack Inté", null);
        ShopProduct sweat = product("Sweat", 3);
        ShopProduct mug = product("Mug", 10);
        List<ShopProductBundleItem> items = List.of(item(bundle, sweat, 1), item(bundle, mug, 1));
        when(bundleItemRepository.findByBundleId(bundle.getId())).thenReturn(items);

        assertThat(service().remaining(bundle, java.util.Map.of())).isEqualTo(3);
    }

    /** Un lot qui prend deux mugs par exemplaire epuise les mugs deux fois plus vite. */
    @Test
    void dividesByHowManyEachBundleTakes() {
        ShopProduct bundle = product("Pack duo", null);
        ShopProduct mug = product("Mug", 7);
        when(bundleItemRepository.findByBundleId(bundle.getId())).thenReturn(List.of(item(bundle, mug, 2)));

        assertThat(service().remaining(bundle, java.util.Map.of())).isEqualTo(3);
    }

    /** Un composant sans stock declare ne borne rien : il ne doit pas faire tomber a zero. */
    @Test
    void ignoresComponentsWithoutDeclaredStock() {
        ShopProduct bundle = product("Pack", null);
        ShopProduct sweat = product("Sweat", 4);
        ShopProduct sticker = product("Sticker", null);
        when(bundleItemRepository.findByBundleId(bundle.getId()))
                .thenReturn(List.of(item(bundle, sweat, 1), item(bundle, sticker, 5)));

        assertThat(service().remaining(bundle, java.util.Map.of())).isEqualTo(4);
    }

    /** Aucun composant borne : le lot ne l'est pas non plus. Null, pas zero. */
    @Test
    void staysUnboundedWhenNothingBoundsIt() {
        ShopProduct bundle = product("Pack", null);
        ShopProduct sticker = product("Sticker", null);
        when(bundleItemRepository.findByBundleId(bundle.getId())).thenReturn(List.of(item(bundle, sticker, 1)));

        assertThat(service().remaining(bundle, java.util.Map.of())).isNull();
    }

    /**
     * Un lot vendu compte deux fois, et c'est voulu.
     *
     * <p>Pour lui-meme — c'est ce qu'on lit dans le recapitulatif — et sur chacun de ses
     * composants. Trois lots vendus, ce sont trois lignes de lot et trois sweats de moins
     * dans le carton des sweats.</p>
     */
    @Test
    void aSoldBundleEmptiesItsComponentsToo() {
        ShopProduct bundle = product("Pack", null);
        ShopProduct sweat = product("Sweat", 100);
        when(orderRepository.sumOrderedQuantityByProduct())
                .thenReturn(List.<Object[]>of(new Object[]{bundle.getId(), 3L}));
        when(manualSaleRepository.sumQuantityByProduct()).thenReturn(List.of());
        when(bundleItemRepository.findAllWithProducts()).thenReturn(List.of(item(bundle, sweat, 2)));

        var ordered = service().orderedByProduct();

        assertThat(ordered.get(bundle.getId())).isEqualTo(3L);
        // Deux sweats par lot, trois lots : six sweats partis.
        assertThat(ordered.get(sweat.getId())).isEqualTo(6L);
    }

    /** Un produit ordinaire garde son calcul : stock declare moins ce qui est parti. */
    @Test
    void anOrdinaryProductStillCountsItsOwnStock() {
        ShopProduct sweat = product("Sweat", 10);
        lenient().when(bundleItemRepository.findByBundleId(sweat.getId())).thenReturn(List.of());

        assertThat(service().remaining(sweat, java.util.Map.of(sweat.getId(), 4L))).isEqualTo(6);
    }
}
