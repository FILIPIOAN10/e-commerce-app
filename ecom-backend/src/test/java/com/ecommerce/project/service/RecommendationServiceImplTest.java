package com.ecommerce.project.service;

import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repository.OrderItemRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.impl.RecommendationServiceImpl;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceImplTest {

    @Mock private ProductSemanticSearchService semanticSearchService;
    @Mock private RecentlyViewedService recentlyViewedService;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AuthUtil authUtil;
    @Mock private ProductMapper productMapper;

    @InjectMocks
    private RecommendationServiceImpl recommendationService;

    private final Map<Long, Product> products = new HashMap<>();
    private final Category electronics = new Category(1L, "Electronics", null);

    @BeforeEach
    void setUp() {
        products.put(1L, createProduct(1L, "Wireless Headphones", electronics, "100.0"));
        products.put(2L, createProduct(2L, "Phone Case", electronics, "20.0"));
        products.put(3L, createProduct(3L, "Screen Protector", electronics, "15.0"));
        products.put(4L, createProduct(4L, "Bluetooth Speaker", electronics, "80.0"));

        when(productRepository.findAllById(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            List<Product> result = new ArrayList<>();
            for (Long id : ids) {
                Product p = products.get(id);
                if (p != null) {
                    result.add(p);
                }
            }
            return result;
        });

        when(productMapper.mapProductToDTO(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            ProductDTO dto = new ProductDTO();
            dto.setProductId(p.getProductId());
            dto.setProductName(p.getProductName());
            return dto;
        });
    }

    @Test
    void getFrequentlyBoughtTogether_returnsCoPurchasedProductsInOrder() {
        when(orderItemRepository.findFrequentlyBoughtTogether(eq(1L), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{2L, 5L, 10L},
                        new Object[]{3L, 2L, 4L}
                ));

        List<ProductDTO> result = recommendationService.getFrequentlyBoughtTogether(1L, 4);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getProductId());
        assertEquals("Phone Case", result.get(0).getProductName());
        assertEquals(3L, result.get(1).getProductId());
    }

    @Test
    void getFrequentlyBoughtTogether_fallsBackToSimilarProductsWhenNotEnoughData() {
        when(orderItemRepository.findFrequentlyBoughtTogether(eq(1L), any(Pageable.class)))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 1L, 1L}));

        when(productRepository.findById(1L)).thenReturn(java.util.Optional.of(products.get(1L)));
        when(semanticSearchService.searchProductIds(anyString(), eq(5))).thenReturn(List.of(4L));

        List<ProductDTO> result = recommendationService.getFrequentlyBoughtTogether(1L, 4);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getProductId());
        assertEquals(4L, result.get(1).getProductId());
    }

    @Test
    void getFrequentlyBoughtTogether_returnsEmptyForInvalidInput() {
        assertTrue(recommendationService.getFrequentlyBoughtTogether(null, 4).isEmpty());
        assertTrue(recommendationService.getFrequentlyBoughtTogether(1L, 0).isEmpty());
    }

    private Product createProduct(Long id, String name, Category category, String price) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductName(name);
        p.setDescription("Test description");
        p.setTags("test");
        p.setQuantity(10);
        p.setPrice(new BigDecimal(price));
        p.setDiscount(new BigDecimal("0"));
        p.setSpecialPrice(new BigDecimal(price));
        p.setCategory(category);
        return p;
    }
}
