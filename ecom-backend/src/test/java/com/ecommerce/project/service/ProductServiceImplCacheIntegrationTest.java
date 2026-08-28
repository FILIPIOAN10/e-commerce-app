package com.ecommerce.project.service;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.*;
import com.ecommerce.project.service.AdminAuditLogService;
import com.ecommerce.project.service.ProductImageService;
import com.ecommerce.project.service.ProductSearchService;
import com.ecommerce.project.service.ProductSemanticSearchService;
import com.ecommerce.project.service.ProductService;
import com.ecommerce.project.service.impl.ProductServiceImpl;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProductServiceImplCacheIntegrationTest.CacheTestConfig.class)
@DisplayName("ProductServiceImpl — caching behavior")
class ProductServiceImplCacheIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductSemanticSearchService productSemanticSearchService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setProductId(1L);
        product.setProductName("Laptop");
        product.setDescription("Gaming laptop");
        product.setTags("laptop");
        product.setQuantity(5);
        product.setPrice(1000.0);
        product.setDiscount(10.0);
        product.setSpecialPrice(900.0);
        product.setImage("laptop.png");

        reset(productRepository, reviewRepository, productSemanticSearchService);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(reviewRepository.getAverageRatingForProduct(any(Product.class))).thenReturn(4.5);
        when(reviewRepository.countByProduct(any(Product.class))).thenReturn(10L);
    }

    @Test
    @DisplayName("getProductById calls repository once and returns cached result on second call")
    void getProductById_caches() {
        ProductDTO first = productService.getProductById(1L);
        ProductDTO second = productService.getProductById(1L);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals("Laptop", first.getProductName());
        assertEquals("Laptop", second.getProductName());
        verify(productRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getAllProducts is cached for identical parameters")
    void getAllProducts_caches() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product), Pageable.ofSize(10), 1));
        when(reviewRepository.getAverageRatingsForProductIds(anyList())).thenReturn(List.of());
        when(reviewRepository.getReviewCountsForProductIds(anyList())).thenReturn(List.of());

        ProductResponse first = productService.getAllProducts(0, 10, "price", "asc", null, null);
        ProductResponse second = productService.getAllProducts(0, 10, "price", "asc", null, null);

        assertEquals(1, first.getContent().size());
        assertEquals(1, second.getContent().size());
        verify(productRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }


    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        public ProductRepository productRepository() {
            return mock(ProductRepository.class);
        }

        @Bean
        public CategoryRepository categoryRepository() {
            return mock(CategoryRepository.class);
        }

        @Bean
        public ReviewRepository reviewRepository() {
            return mock(ReviewRepository.class);
        }

        @Bean
        public CartRepository cartRepository() {
            return mock(CartRepository.class);
        }

        @Bean
        public ProductImageService productImageService() {
            return mock(ProductImageService.class);
        }

        @Bean
        public CartService cartService() {
            return mock(CartService.class);
        }

        @Bean
        public ProductSemanticSearchService productSemanticSearchService() {
            return mock(ProductSemanticSearchService.class);
        }

        @Bean
        public AuthUtil authUtil() {
            return mock(AuthUtil.class);
        }

        @Bean
        public ModelMapper modelMapper() {
            return new ModelMapper();
        }

        @Bean
        public ProductMapper productMapper(ModelMapper modelMapper) {
            ProductMapper mapper = new ProductMapper(modelMapper);
            ReflectionTestUtils.setField(mapper, "imageBaseUrl", "http://localhost:8080/images");
            return mapper;
        }

        @Bean
        public ProductSearchService productSearchService() {
            return mock(ProductSearchService.class);
        }

        @Bean
        public AdminAuditLogService adminAuditLogService() {
            return mock(AdminAuditLogService.class);
        }

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("product", "publicProducts", "categoryProducts", "productSearch",
                    "adminProducts", "sellerProducts");
        }

        @Bean
        public com.ecommerce.project.cache.TransactionAwareCacheEvictor cacheEvictor(CacheManager cacheManager) {
            return new com.ecommerce.project.cache.TransactionAwareCacheEvictor(
                    cacheManager, new com.ecommerce.project.util.AfterCommitExecutor());
        }

        @Bean
        public ProductService productService(ProductRepository productRepository,
                                             CategoryRepository categoryRepository,
                                             ModelMapper modelMapper,
                                             CartRepository cartRepository,
                                             CartService cartService,
                                             ProductImageService productImageService,
                                             ProductMapper productMapper,
                                             ProductSemanticSearchService productSemanticSearchService,
                                             com.ecommerce.project.cache.TransactionAwareCacheEvictor cacheEvictor,
                                             AuthUtil authUtil,
                                             AdminAuditLogService adminAuditLogService,
                                             com.ecommerce.project.service.stock.StockLedgerService stockLedgerService) {
            return new ProductServiceImpl(
                    productRepository, categoryRepository, modelMapper, cartRepository, cartService,
                    productImageService, productMapper, productSemanticSearchService, cacheEvictor,
                    authUtil, adminAuditLogService, stockLedgerService);
        }

        @Bean
        public com.ecommerce.project.service.stock.StockLedgerService stockLedgerService() {
            return mock(com.ecommerce.project.service.stock.StockLedgerService.class);
        }
    }
}
