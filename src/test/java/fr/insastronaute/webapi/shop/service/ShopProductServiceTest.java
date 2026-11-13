package fr.insastronaute.webapi.shop.service;

import fr.insastronaute.webapi.article.repository.ArticleRepository;
import fr.insastronaute.webapi.common.web.BadRequestException;
import fr.insastronaute.webapi.shop.dto.ShopProductRequest;
import fr.insastronaute.webapi.shop.repository.ShopCartLineRepository;
import fr.insastronaute.webapi.shop.repository.ShopManualSaleRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductBundleItemRepository;
import fr.insastronaute.webapi.shop.repository.ShopOrderRepository;
import fr.insastronaute.webapi.shop.repository.ShopProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ShopProductServiceTest {
    @Mock private ShopProductRepository productRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private ShopCartLineRepository cartLineRepository;
    @Mock private ShopProductImageService imageService;
    @Mock private ShopOrderRepository orderRepository;
    @Mock private ShopManualSaleRepository manualSaleRepository;
    @Mock private ShopProductBundleItemRepository bundleItemRepository;
    @Mock private ShopStockService stockService;

    @Test
    void createRejectsNegativePrice() {
        ShopProductService service = new ShopProductService(productRepository, articleRepository, cartLineRepository, orderRepository, manualSaleRepository, bundleItemRepository, imageService, stockService);
        long negativePrice = new AtomicLong(-1).get();

        assertThatThrownBy(() -> service.create(new ShopProductRequest("Tote bag", negativePrice, false, null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Le prix doit être positif ou nul.");
    }
}
