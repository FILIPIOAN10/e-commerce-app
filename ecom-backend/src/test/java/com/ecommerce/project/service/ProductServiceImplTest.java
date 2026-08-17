package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.*;
import com.ecommerce.project.service.impl.ProductServiceImpl;
import com.ecommerce.project.util.AuthUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductServiceImpl — search, pagination and read operations")
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private FileService fileService;
    @Mock private CartRepository cartRepository;
    @Mock private CartService cartService;
    @Mock private ProductSemanticSearchService productSemanticSearchService;
    @Mock private AuthUtil authUtil;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;
    private Category category;
    private User user;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setCategoryId(1L);
        category.setCategoryName("Electronics");

        user = new User();
        user.setUserId(1L);
        user.setUserName("seller");

        product = new Product();
        product.setProductId(1L);
        product.setProductName("Wireless Headphones");
        product.setDescription("Noise cancelling headphones");
        product.setTags("audio, headphones");
        product.setQuantity(10);
        product.setPrice(100.0);
        product.setDiscount(10.0);
        product.setSpecialPrice(90.0);
        product.setImage("headphones.png");
        product.setCategory(category);
        product.setUser(user);

        ReflectionTestUtils.setField(productService, "imageBaseUrl", "http://localhost:8080/images");

        when(modelMapper.map(any(Product.class), eq(ProductDTO.class)))
                .thenAnswer(inv -> mapToDto(inv.getArgument(0)));

        when(reviewRepository.getAverageRatingForProduct(any(Product.class))).thenReturn(0.0);
        when(reviewRepository.countByProduct(any(Product.class))).thenReturn(0L);
        when(reviewRepository.getAverageRatingsForProductIds(anyList())).thenReturn(List.of());
        when(reviewRepository.getReviewCountsForProductIds(anyList())).thenReturn(List.of());
    }

    private ProductDTO mapToDto(Product p) {
        ProductDTO dto = new ProductDTO();
        dto.setProductId(p.getProductId());
        dto.setProductName(p.getProductName());
        dto.setDescription(p.getDescription());
        dto.setTags(p.getTags());
        dto.setQuantity(p.getQuantity());
        dto.setPrice(p.getPrice());
        dto.setDiscount(p.getDiscount());
        dto.setSpecialPrice(p.getSpecialPrice());
        dto.setImage(p.getImage());
        if (p.getCategory() != null) {
            dto.setCategoryId(p.getCategory().getCategoryId());
            dto.setCategoryName(p.getCategory().getCategoryName());
        }
        return dto;
    }

    private Page<Product> pageOf(Product... products) {
        return new PageImpl<>(List.of(products), PageRequest.of(0, 10), products.length);
    }

    @Test
    @DisplayName("getAllProducts returns paginated results with keyword and category filters")
    void getAllProducts_withKeywordAndCategory() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(product));

        ProductResponse response = productService.getAllProducts(
                0, 10, "price", "asc", "wireless", "Electronics");

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(0, response.getPageNumber());
        assertEquals(10, response.getPageSize());
        assertEquals(1L, response.getTotalElements());
        assertTrue(response.isLastPage());
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("getAllProducts returns empty page without throwing")
    void getAllProducts_empty() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        ProductResponse response = productService.getAllProducts(0, 10, "price", "asc", null, null);

        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    @DisplayName("searchByCategory returns products and throws when none found")
    void searchByCategory_successAndEmpty() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.findByCategoryOrderByPriceAsc(eq(category), any(Pageable.class)))
                .thenReturn(pageOf(product));

        ProductResponse response = productService.searchByCategory(1L, 0, 10, "price", "asc");

        assertEquals(1, response.getContent().size());
        assertEquals("Wireless Headphones", response.getContent().get(0).getProductName());

        when(productRepository.findByCategoryOrderByPriceAsc(eq(category), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        assertThrows(APIException.class,
                () -> productService.searchByCategory(1L, 0, 10, "price", "asc"));
    }

    @Test
    @DisplayName("searchProductByKeyword returns matching products and throws when none found")
    void searchProductByKeyword_successAndEmpty() {
        when(productRepository.findByProductNameLikeIgnoreCase(eq("%laptop%"), any(Pageable.class)))
                .thenReturn(pageOf(product));

        ProductResponse response = productService.searchProductByKeyword("laptop", 0, 10, "price", "asc");

        assertEquals(1, response.getContent().size());

        when(productRepository.findByProductNameLikeIgnoreCase(eq("%unknown%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        assertThrows(APIException.class,
                () -> productService.searchProductByKeyword("unknown", 0, 10, "price", "asc"));
    }

    @Test
    @DisplayName("searchProducts falls back to classic search when semantic search is disabled")
    void searchProducts_classicFallback() {
        when(productSemanticSearchService.isEnabled()).thenReturn(false);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(product));

        ProductResponse response = productService.searchProducts("laptop", 0, 10, "price", "asc", true);

        assertEquals(1, response.getContent().size());
        verify(productSemanticSearchService).isEnabled();
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("searchProducts with null query uses disjunction and returns empty page")
    void searchProducts_nullQuery() {
        when(productSemanticSearchService.isEnabled()).thenReturn(false);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        ProductResponse response = productService.searchProducts(null, 0, 10, "price", "asc", false);

        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    @DisplayName("getProductById returns product DTO when found")
    void getProductById_success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductDTO result = productService.getProductById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getProductId());
        assertEquals("Wireless Headphones", result.getProductName());
    }

    @Test
    @DisplayName("getProductById throws ResourceNotFoundException when not found")
    void getProductById_notFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.getProductById(99L));
    }

    @Test
    @DisplayName("getAllProductsForAdmin returns paginated admin products")
    void getAllProductsForAdmin() {
        when(productRepository.findAll(any(Pageable.class))).thenReturn(pageOf(product));

        ProductResponse response = productService.getAllProductsForAdmin(0, 10, "price", "asc");

        assertEquals(1, response.getContent().size());
        assertEquals(0, response.getPageNumber());
        assertEquals(1L, response.getTotalElements());
        verify(productRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("getAllProductsForSeller returns paginated products for logged in seller")
    void getAllProductsForSeller() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findByUser(eq(user), any(Pageable.class))).thenReturn(pageOf(product));

        ProductResponse response = productService.getAllProductsForSeller(0, 10, "price", "asc");

        assertEquals(1, response.getContent().size());
        assertEquals("Wireless Headphones", response.getContent().get(0).getProductName());
        verify(authUtil).loggedInUser();
        verify(productRepository).findByUser(eq(user), any(Pageable.class));
    }

    @Test
    @DisplayName("searchAutocomplete returns distinct product names")
    void searchAutocomplete() {
        Product p2 = new Product();
        p2.setProductName("Wired Headphones");
        when(productRepository.findByProductNameLikeIgnoreCase(eq("%head%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product, p2)));

        List<String> result = productService.searchAutocomplete("head");

        assertEquals(2, result.size());
        assertTrue(result.contains("Wireless Headphones"));
    }

    @Test
    @DisplayName("getBestSellers, getNewArrivals and getOnSaleProducts return mapped DTOs")
    void getDiscoveryLists() {
        when(productRepository.findBestSellingProducts(any(Pageable.class))).thenReturn(List.of(product));
        when(productRepository.findAllByOrderByProductIdDesc(any(Pageable.class))).thenReturn(List.of(product));
        when(productRepository.findOnSaleProducts(any(Pageable.class))).thenReturn(List.of(product));

        List<ProductDTO> bestSellers = productService.getBestSellers(5);
        List<ProductDTO> newArrivals = productService.getNewArrivals(5);
        List<ProductDTO> onSale = productService.getOnSaleProducts(5);

        assertEquals(1, bestSellers.size());
        assertEquals(1, newArrivals.size());
        assertEquals(1, onSale.size());
    }
}
