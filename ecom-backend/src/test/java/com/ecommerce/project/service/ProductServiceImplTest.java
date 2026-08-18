package com.ecommerce.project.service;

import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.*;
import com.ecommerce.project.service.impl.ProductServiceImpl;
import com.ecommerce.project.service.ProductImageService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ProductMapper;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductServiceImpl — pagination and read operations")
class ProductServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private CartRepository cartRepository;
    @Mock private CartService cartService;
    @Mock private ProductImageService productImageService;
    @Mock private ProductMapper productMapper;
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

        when(modelMapper.map(any(Product.class), eq(ProductDTO.class)))
                .thenAnswer(inv -> mapToDto(inv.getArgument(0)));

        when(productMapper.mapProductToDTO(any(Product.class)))
                .thenAnswer(inv -> mapToDto(inv.getArgument(0)));

        when(productMapper.buildProductResponse(any(Page.class)))
                .thenAnswer(inv -> {
                    Page<Product> page = inv.getArgument(0);
                    ProductResponse response = new ProductResponse();
                    response.setContent(page.getContent().stream().map(this::mapToDto).toList());
                    response.setPageNumber(page.getNumber());
                    response.setPageSize(page.getSize());
                    response.setTotalElements(page.getTotalElements());
                    response.setTotalPages(page.getTotalPages());
                    response.setLastPage(page.isLast());
                    return response;
                });
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

}
