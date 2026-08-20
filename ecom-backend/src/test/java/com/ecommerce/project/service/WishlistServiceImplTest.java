package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.model.Wishlist;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.WishlistRepository;
import com.ecommerce.project.service.impl.WishlistServiceImpl;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WishlistServiceImpl tests")
class WishlistServiceImplTest {

    @Mock private WishlistRepository wishlistRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AuthUtil authUtil;
    @Mock private ProductMapper productMapper;

    @InjectMocks
    private WishlistServiceImpl wishlistService;

    private User user;
    private Product product;
    private ProductDTO productDTO;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(1L);
        user.setUserName("user1");
        user.setEmail("user1@test.com");

        product = new Product();
        product.setProductId(1L);
        product.setProductName("Wireless Headphones");

        productDTO = new ProductDTO();
        productDTO.setProductId(1L);
        productDTO.setProductName("Wireless Headphones");
    }

    @Test
    @DisplayName("addToWishlist saves new wishlist item")
    void addToWishlist_success() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(wishlistRepository.existsByUserAndProduct(user, product)).thenReturn(false);

        String result = wishlistService.addToWishlist(1L);

        assertEquals("Product added to wishlist successfully", result);
        verify(wishlistRepository).save(any(Wishlist.class));
    }

    @Test
    @DisplayName("addToWishlist throws when product already in wishlist")
    void addToWishlist_duplicate_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(wishlistRepository.existsByUserAndProduct(user, product)).thenReturn(true);

        APIException exception = assertThrows(APIException.class,
                () -> wishlistService.addToWishlist(1L));
        assertTrue(exception.getMessage().contains("already in wishlist"));
        verify(wishlistRepository, never()).save(any(Wishlist.class));
    }

    @Test
    @DisplayName("addToWishlist throws when product not found")
    void addToWishlist_productNotFound_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> wishlistService.addToWishlist(99L));
    }

    @Test
    @DisplayName("removeFromWishlist deletes existing item")
    void removeFromWishlist_success() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(wishlistRepository.existsByUserAndProduct(user, product)).thenReturn(true);

        String result = wishlistService.removeFromWishlist(1L);

        assertEquals("Product removed from wishlist successfully", result);
        verify(wishlistRepository).deleteByUserAndProduct(user, product);
    }

    @Test
    @DisplayName("removeFromWishlist throws when item not in wishlist")
    void removeFromWishlist_notFound_throws() {
        when(authUtil.loggedInUser()).thenReturn(user);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(wishlistRepository.existsByUserAndProduct(user, product)).thenReturn(false);

        assertThrows(APIException.class, () -> wishlistService.removeFromWishlist(1L));
        verify(wishlistRepository, never()).deleteByUserAndProduct(any(), any());
    }

    @Test
    @DisplayName("getWishlist returns paginated products")
    void getWishlist_success() {
        Wishlist wishlist = Wishlist.builder()
                .id(1L)
                .user(user)
                .product(product)
                .build();

        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<Wishlist> page = new PageImpl<>(List.of(wishlist), pageable, 1);

        when(authUtil.loggedInUser()).thenReturn(user);
        when(wishlistRepository.findByUser(user, pageable)).thenReturn(page);
        when(productMapper.mapProductToDTO(product)).thenReturn(productDTO);

        ProductResponse response = wishlistService.getWishlist(0, 10, "createdAt", "desc");

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(0, response.getPageNumber());
        assertEquals(10, response.getPageSize());
        assertEquals(1L, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertTrue(response.isLastPage());
    }
}
