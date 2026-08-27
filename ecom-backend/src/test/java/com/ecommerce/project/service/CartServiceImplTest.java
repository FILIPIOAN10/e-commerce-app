package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repository.CartItemRepository;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.impl.CartServiceImpl;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CartServiceImpl tests")
class CartServiceImplTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AuthUtil authUtil;
    @Mock private ModelMapper modelMapper;
    @Mock private ProductMapper productMapper;

    @InjectMocks
    private CartServiceImpl cartService;

    private User user;
    private Cart cart;
    private Product product;
    private CartItem cartItem;
    private CartDTO cartDTO;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("user1@test.com");

        cart = new Cart();
        cart.setCartId(1L);
        cart.setTotalPrice(0.0);
        cart.setUser(user);
        cart.setCartItems(new ArrayList<>());

        product = new Product();
        product.setProductId(1L);
        product.setProductName("Wireless Headphones");
        product.setQuantity(10);
        product.setSpecialPrice(99.99);
        product.setDiscount(0.0);

        cartItem = new CartItem();
        cartItem.setCartItemId(1L);
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        cartItem.setProductPrice(99.99);
        cartItem.setDiscount(0.0);

        ProductDTO productDTO = new ProductDTO();
        productDTO.setProductId(1L);
        productDTO.setProductName("Wireless Headphones");
        productDTO.setQuantity(2);

        cartDTO = new CartDTO();
        cartDTO.setCartId(1L);
        cartDTO.setProducts(List.of(productDTO));
    }

    @Test
    @DisplayName("addProductToCart creates new cart item")
    void addProductToCart_success() {
        when(authUtil.loggedInEmail()).thenReturn("user1@test.com");
        when(authUtil.loggedInUser()).thenReturn(user);
        when(cartRepository.findCartByEmail("user1@test.com")).thenReturn(null, cart);
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findCartItemByProductProductIdAndCartId(1L, 1L)).thenReturn(null);
        doAnswer(inv -> {
            CartItem ci = inv.getArgument(0);
            cart.getCartItems().add(ci);
            return ci;
        }).when(cartItemRepository).save(any(CartItem.class));
        when(cartItemRepository.findByCartCartId(1L)).thenReturn(List.of(cartItem));
        when(cartRepository.save(cart)).thenReturn(cart);
        when(modelMapper.map(cart, CartDTO.class)).thenReturn(cartDTO);
        when(productMapper.mapCartItemsToProductDTOs(any())).thenReturn(cartDTO.getProducts());

        CartDTO result = cartService.addProductToCart(1L, 2);

        assertNotNull(result);
        assertEquals(1L, result.getCartId());
        assertEquals(1, result.getProducts().size());
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    @DisplayName("addProductToCart throws when product already in cart")
    void addProductToCart_duplicate_throws() {
        when(authUtil.loggedInEmail()).thenReturn("user1@test.com");
        when(authUtil.loggedInUser()).thenReturn(user);
        when(cartRepository.findCartByEmail("user1@test.com")).thenReturn(cart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findCartItemByProductProductIdAndCartId(1L, 1L)).thenReturn(cartItem);

        APIException exception = assertThrows(APIException.class,
                () -> cartService.addProductToCart(1L, 1));
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("addProductToCart throws when product out of stock")
    void addProductToCart_outOfStock_throws() {
        product.setQuantity(0);

        when(authUtil.loggedInEmail()).thenReturn("user1@test.com");
        when(authUtil.loggedInUser()).thenReturn(user);
        when(cartRepository.findCartByEmail("user1@test.com")).thenReturn(cart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        APIException exception = assertThrows(APIException.class,
                () -> cartService.addProductToCart(1L, 1));
        assertTrue(exception.getMessage().contains("not available"));
    }

    @Test
    @DisplayName("getAllCarts returns all carts")
    void getAllCarts_success() {
        Cart secondCart = new Cart();
        secondCart.setCartId(2L);
        secondCart.setCartItems(new ArrayList<>());

        when(cartRepository.findAll()).thenReturn(List.of(cart, secondCart));
        when(modelMapper.map(cart, CartDTO.class)).thenReturn(cartDTO);
        when(modelMapper.map(secondCart, CartDTO.class)).thenReturn(new CartDTO());
        when(productMapper.mapCartItemsToProductDTOs(any())).thenReturn(List.of());

        List<CartDTO> result = cartService.getAllCarts();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getAllCarts returns an empty list when no carts exist")
    void getAllCarts_empty_returnsEmptyList() {
        when(cartRepository.findAll()).thenReturn(List.of());

        // An empty collection is a valid result, not an error condition.
        assertTrue(cartService.getAllCarts().isEmpty());
    }

    @Test
    @DisplayName("getCart returns cart by email and id")
    void getCart_success() {
        cart.setCartItems(List.of(cartItem));
        when(cartRepository.findCartByEmailAndCartId("user1@test.com", 1L)).thenReturn(cart);
        when(modelMapper.map(cart, CartDTO.class)).thenReturn(cartDTO);
        when(productMapper.mapCartItemsToProductDTOs(any())).thenReturn(cartDTO.getProducts());

        CartDTO result = cartService.getCart("user1@test.com", 1L);

        assertNotNull(result);
        assertEquals(1L, result.getCartId());
    }

    @Test
    @DisplayName("getCart throws when cart not found")
    void getCart_notFound_throws() {
        when(cartRepository.findCartByEmailAndCartId("user1@test.com", 99L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> cartService.getCart("user1@test.com", 99L));
    }

    @Test
    @DisplayName("deleteProductFromCart removes item and returns message")
    void deleteProductFromCart_success() {
        cart.setTotalPrice(199.98);
        cart.setCartItems(new ArrayList<>(List.of(cartItem)));

        when(authUtil.loggedInEmail()).thenReturn("user1@test.com");
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findCartItemByProductProductIdAndCartId(1L, 1L)).thenReturn(cartItem);

        String result = cartService.deleteProductFromCart(1L, 1L);

        assertNotNull(result);
        assertTrue(result.contains("removed from cart"));
        verify(cartItemRepository).deleteCartItemByProductIdAndCartId(1L, 1L);
    }

    @Test
    @DisplayName("createOrUpdateCartWithItems replaces cart contents")
    void createOrUpdateCartWithItems_success() {
        CartItemDTO itemDTO = new CartItemDTO();
        itemDTO.setProductId(1L);
        itemDTO.setQuantity(3);

        when(authUtil.loggedInEmail()).thenReturn("user1@test.com");
        when(authUtil.loggedInUser()).thenReturn(user);
        when(cartRepository.findCartByEmail("user1@test.com")).thenReturn(cart);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(cartItem);
        when(cartRepository.save(cart)).thenReturn(cart);

        String result = cartService.createOrUpdateCartWithItems(List.of(itemDTO));

        assertNotNull(result);
        assertTrue(result.contains("successfully"));
        verify(cartItemRepository).deleteAllByCartId(1L);
    }
}
