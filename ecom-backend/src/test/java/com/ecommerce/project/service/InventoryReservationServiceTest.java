package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ReservationResponse;
import com.ecommerce.project.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InventoryReservationService tests")
class InventoryReservationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ProductRepository productRepository;

    @Mock
    @SuppressWarnings("unchecked")
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private InventoryReservationService inventoryReservationService;

    private Product product;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inventoryReservationService, "reservationTtlMinutes", 10L);

        product = new Product();
        product.setProductId(1L);
        product.setProductName("Headphones");
        product.setQuantity(10);

        cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2);

        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    @DisplayName("reserveCartItems creates reservation for available stock")
    void reserveCartItems_success() {
        when(setOps.members("product_reservations:1")).thenReturn(Collections.emptySet());
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        List<ReservationResponse> responses = inventoryReservationService.reserveCartItems(1L, List.of(cartItem));

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(1L, responses.get(0).productId());
        assertEquals(2, responses.get(0).quantity());
        assertNotNull(responses.get(0).reservationId());

        verify(hashOps).putAll(startsWith("reservation:"), anyMap());
        verify(setOps).add(eq("product_reservations:1"), anyString());
        verify(setOps).add(eq("cart_reservations:1"), anyString());
        verify(redisTemplate).expire(startsWith("reservation:"), any(Duration.class));
    }

    @Test
    @DisplayName("reserveCartItems throws when over-reserving quantity")
    void reserveCartItems_overReservation_throws() {
        product.setQuantity(10);
        cartItem.setQuantity(2);

        when(setOps.members("product_reservations:1")).thenReturn(Set.of("res-123"));
        when(redisTemplate.hasKey("reservation:res-123")).thenReturn(true);
        when(hashOps.get("reservation:res-123", "quantity")).thenReturn("9");

        APIException ex = assertThrows(APIException.class,
                () -> inventoryReservationService.reserveCartItems(1L, List.of(cartItem)));
        assertTrue(ex.getMessage().contains("Insufficient stock"));
    }

    @Test
    @DisplayName("reserveCartItems clears previous cart reservations before reserving")
    void reserveCartItems_clearsPreviousReservations() {
        when(setOps.members("cart_reservations:1")).thenReturn(Set.of("old-res"));
        when(setOps.members("product_reservations:1")).thenReturn(Collections.emptySet());
        when(hashOps.entries("reservation:old-res")).thenReturn(Map.of("productId", "1"));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        inventoryReservationService.reserveCartItems(1L, List.of(cartItem));

        verify(redisTemplate).delete("reservation:old-res");
        verify(redisTemplate).delete("cart_reservations:1");
    }

    @Test
    @DisplayName("consumeReservationsForCart decrements product quantity")
    void consumeReservationsForCart_success() {
        when(setOps.members("cart_reservations:1")).thenReturn(Set.of("res-1"));
        when(redisTemplate.hasKey("reservation:res-1")).thenReturn(true);
        when(hashOps.entries("reservation:res-1")).thenReturn(Map.of(
                "productId", "1",
                "quantity", "2",
                "cartId", "1",
                "expiresAt", String.valueOf(System.currentTimeMillis() + 60000)
        ));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        inventoryReservationService.consumeReservationsForCart(1L);

        assertEquals(8, product.getQuantity());
        verify(productRepository).save(product);
        verify(redisTemplate).delete("reservation:res-1");
        verify(setOps).remove("product_reservations:1", "res-1");
        verify(redisTemplate).delete("cart_reservations:1");
    }

    @Test
    @DisplayName("consumeReservationsForCart throws when no active reservation")
    void consumeReservationsForCart_noActive_throws() {
        when(setOps.members("cart_reservations:1")).thenReturn(Collections.emptySet());

        APIException ex = assertThrows(APIException.class,
                () -> inventoryReservationService.consumeReservationsForCart(1L));
        assertTrue(ex.getMessage().contains("No active stock reservation"));
    }

    @Test
    @DisplayName("consumeReservationsForCart throws when insufficient stock to fulfill")
    void consumeReservationsForCart_insufficientStock_throws() {
        product.setQuantity(1);
        when(setOps.members("cart_reservations:1")).thenReturn(Set.of("res-1"));
        when(redisTemplate.hasKey("reservation:res-1")).thenReturn(true);
        when(hashOps.entries("reservation:res-1")).thenReturn(Map.of(
                "productId", "1",
                "quantity", "2",
                "cartId", "1",
                "expiresAt", String.valueOf(System.currentTimeMillis() + 60000)
        ));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        APIException ex = assertThrows(APIException.class,
                () -> inventoryReservationService.consumeReservationsForCart(1L));
        assertTrue(ex.getMessage().contains("Reservation could not be fulfilled"));
    }

    @Test
    @DisplayName("getActiveReservationsForCart filters out expired reservations")
    void getActiveReservationsForCart_expiredSkipped() {
        when(setOps.members("cart_reservations:1")).thenReturn(Set.of("res-1"));
        when(redisTemplate.hasKey("reservation:res-1")).thenReturn(false);

        List<ReservationResponse> active = inventoryReservationService.getActiveReservationsForCart(1L);

        assertTrue(active.isEmpty());
        verify(setOps).remove("cart_reservations:1", "res-1");
    }

    @Test
    @DisplayName("getActiveReservationsForCart returns valid reservations")
    void getActiveReservationsForCart_success() {
        long expiresAt = System.currentTimeMillis() + 60000;
        when(setOps.members("cart_reservations:1")).thenReturn(Set.of("res-1"));
        when(redisTemplate.hasKey("reservation:res-1")).thenReturn(true);
        when(hashOps.entries("reservation:res-1")).thenReturn(Map.of(
                "productId", "1",
                "quantity", "2",
                "cartId", "1",
                "createdAt", String.valueOf(System.currentTimeMillis()),
                "expiresAt", String.valueOf(expiresAt)
        ));

        List<ReservationResponse> active = inventoryReservationService.getActiveReservationsForCart(1L);

        assertEquals(1, active.size());
        assertEquals(1L, active.get(0).productId());
        assertEquals(2, active.get(0).quantity());
        assertEquals(expiresAt, active.get(0).expiresAt());
    }

    @Test
    @DisplayName("releaseReservationsForCart removes reservations and their references")
    void releaseReservationsForCart_success() {
        when(setOps.members("cart_reservations:1")).thenReturn(Set.of("res-1"));
        when(hashOps.entries("reservation:res-1")).thenReturn(Map.of("productId", "1"));

        inventoryReservationService.releaseReservationsForCart(1L);

        verify(setOps).remove("product_reservations:1", "res-1");
        verify(redisTemplate).delete("reservation:res-1");
        verify(redisTemplate).delete("cart_reservations:1");
    }

    @Test
    @DisplayName("getReservedForProduct returns total reserved quantity")
    void getReservedForProduct_success() {
        when(setOps.members("product_reservations:1")).thenReturn(Set.of("res-1", "res-2"));
        when(redisTemplate.hasKey("reservation:res-1")).thenReturn(true);
        when(redisTemplate.hasKey("reservation:res-2")).thenReturn(false);
        when(hashOps.get("reservation:res-1", "quantity")).thenReturn("3");

        int reserved = inventoryReservationService.getReservedForProduct(1L);

        assertEquals(3, reserved);
        verify(setOps).remove("product_reservations:1", "res-2");
    }

    @Test
    @DisplayName("reserveCartItems ignores items with null or zero quantity")
    void reserveCartItems_zeroQuantity_ignored() {
        cartItem.setQuantity(0);

        List<ReservationResponse> responses = inventoryReservationService.reserveCartItems(1L, List.of(cartItem));

        assertTrue(responses.isEmpty());
    }
}
