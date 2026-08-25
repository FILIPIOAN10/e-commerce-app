package com.ecommerce.project.service;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderSummaryDTO;
import com.ecommerce.project.repository.*;
import com.ecommerce.project.service.impl.OrderServiceImpl;
import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.pricing.ShippingCalculator;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderServiceImpl — placeOrder tests")
class OrderServiceImplTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartService cartService;
    @Mock private CouponService couponService;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;
    @Mock private CouponRepository couponRepository;
    @Mock private AuthUtil authUtil;
    @Mock private UserActivityLogService userActivityLogService;
    @Mock private InventoryReservationService inventoryReservationService;
    @Mock private ModelMapper modelMapper;
    @Mock private ShippingCalculator shippingCalculator;
    @Mock private StripeService stripeService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final String EMAIL = "user1@test.com";
    private static final Long ADDRESS_ID = 1L;
    private static final Long CART_ID = 10L;

    private Cart cart;
    private Address address;
    private Product product;
    private CartItem cartItem;
    private PaymentIntent paymentIntent;

    @BeforeEach
    void setUp() {
        cart = new Cart();
        cart.setCartId(CART_ID);
        cart.setTotalPrice(100.0);

        product = new Product();
        product.setProductId(1L);
        product.setProductName("Wireless Headphones");
        product.setQuantity(10);

        cartItem = new CartItem();
        cartItem.setCartItemId(1L);
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        cartItem.setDiscount(0.0);
        cartItem.setProductPrice(50.0);

        cart.setCartItems(new ArrayList<>(List.of(cartItem)));

        address = new Address("Strada Test", "Bloc A", "Bucuresti", "Bucuresti", "Romania", "123456");
        address.setAddressId(ADDRESS_ID);
        User user = new User();
        user.setUserId(1L);
        user.setUserName("user1");
        user.setEmail(EMAIL);
        address.setUser(user);
    }

    private OrderDTO buildOrderDTO() {
        OrderDTO dto = new OrderDTO();
        dto.setOrderId(1L);
        dto.setEmail(EMAIL);
        dto.setOrderStatus("Placed");
        dto.setTotalAmount(100.0);
        dto.setOrderDate(LocalDate.now());
        dto.setAddressId(ADDRESS_ID);
        dto.setItems(new ArrayList<>());
        return dto;
    }

    private void stubHappyPath() {
        when(cartRepository.findCartByEmail(EMAIL)).thenReturn(cart);
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address));

        when(paymentRepository.findByPgPaymentId(anyString())).thenReturn(Optional.empty());

        when(shippingCalculator.calculate(any(Address.class), anyDouble()))
                .thenAnswer(invocation -> {
                    double cartTotal = invocation.getArgument(1, Double.class);
                    return cartTotal >= 100.0 ? 0.0 : 3.0;
                });

        // Atomic coupon consumption succeeds by default.
        when(couponRepository.tryConsume(anyLong())).thenReturn(1);

        paymentIntent = mock(PaymentIntent.class);
        when(paymentIntent.getStatus()).thenReturn("succeeded");
        doReturn(paymentIntent).when(stripeService).retrievePaymentIntent(anyString());

        Payment savedPayment = new Payment("STRIPE", "pi_123", "succeeded", "OK", "Stripe");
        savedPayment.setPaymentId(1L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setPaymentId(1L);
            return p;
        });

        Order savedOrder = new Order();
        savedOrder.setId(1L);
        savedOrder.setEmail(EMAIL);
        savedOrder.setOrderStatus("Placed");
        savedOrder.setTotalAmount(100.0);
        savedOrder.setOrderDate(LocalDate.now());
        savedOrder.setAddress(address);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDTO dto = buildOrderDTO();
        when(modelMapper.map(any(Order.class), eq(OrderDTO.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            dto.setTotalAmount(o.getTotalAmount());
            return dto;
        });
        when(modelMapper.map(any(OrderItem.class), eq(com.ecommerce.project.payload.OrderItemDTO.class)))
                .thenReturn(new com.ecommerce.project.payload.OrderItemDTO());

        when(cartService.deleteProductFromCart(anyLong(), anyLong())).thenReturn("");
        doAnswer(invocation -> {
            product.setQuantity(product.getQuantity() - cartItem.getQuantity());
            productRepository.save(product);
            return null;
        }).when(inventoryReservationService).consumeReservationsForCart(CART_ID);

        doNothing().when(emailService).sendOrderConfirmationEmail(anyString(), any(OrderDTO.class));
        doNothing().when(notificationService).notifyAdminNewOrder(anyLong(), anyString(), anyDouble());
    }

    // ─────────────────────────────────────────────
    //  Happy path
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Successful order placement")
    class HappyPath {

        @Test
        @DisplayName("should place order and return OrderDTO with correct fields")
        void placeOrder_success() {
            stubHappyPath();
            when(paymentIntent.getAmount()).thenReturn(10000L);

            OrderDTO result = orderService.placeOrder(
                    EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                    "pi_123", "succeeded", "OK", null);

            assertNotNull(result);
            assertEquals("Placed", result.getOrderStatus());
            assertEquals(EMAIL, result.getEmail());
            assertEquals(ADDRESS_ID, result.getAddressId());
            assertEquals(100.0, result.getTotalAmount());

            verify(orderRepository).save(any(Order.class));
            verify(paymentRepository).save(any(Payment.class));
            verify(orderItemRepository).saveAll(anyList());
            verify(eventPublisher).publishEvent(any(OrderServiceImpl.OrderPlacedEvent.class));
        }

        @Test
        @DisplayName("should deduct inventory for each product in cart")
        void placeOrder_deductsInventory() {
            stubHappyPath();
            when(paymentIntent.getAmount()).thenReturn(10000L);

            orderService.placeOrder(
                    EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                    "pi_123", "succeeded", "OK", null);

            verify(productRepository).save(argThat(p -> p.getQuantity() == 8));
        }

        @Test
        @DisplayName("should remove items from cart after order is placed")
        void placeOrder_clearsCartItems() {
            stubHappyPath();
            when(paymentIntent.getAmount()).thenReturn(10000L);

            orderService.placeOrder(
                    EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                    "pi_123", "succeeded", "OK", null);

            verify(cartItemRepository).deleteAllByCartId(CART_ID);
        }

        @Test
        @DisplayName("should save payment with correct method and status")
        void placeOrder_savesPayment() {
            stubHappyPath();

            orderService.placeOrder(
                    EMAIL, ADDRESS_ID, "PAYPAL", "PayPal",
                    "pi_456", "succeeded", "Payment OK", null);

            verify(paymentRepository).save(argThat(p ->
                    "PAYPAL".equals(p.getPaymentMethod()) &&
                    "pi_456".equals(p.getPgPaymentId()) &&
                    "succeeded".equals(p.getPgStatus())));
        }
    }

    // ─────────────────────────────────────────────
    //  Cart validation
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Cart validation")
    class CartValidation {

        @Test
        @DisplayName("should throw ResourceNotFoundException when cart does not exist")
        void placeOrder_cartNotFound() {
            when(cartRepository.findCartByEmail(EMAIL)).thenReturn(null);

            assertThrows(ResourceNotFoundException.class, () ->
                    orderService.placeOrder(
                            EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                            "pi_123", "succeeded", "OK", null));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw APIException when cart is empty")
        void placeOrder_emptyCart() {
            cart.setCartItems(new ArrayList<>());
            when(cartRepository.findCartByEmail(EMAIL)).thenReturn(cart);

            APIException ex = assertThrows(APIException.class, () ->
                    orderService.placeOrder(
                            EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                            "pi_123", "succeeded", "OK", null));

            assertTrue(ex.getMessage().contains("Cart is Empty"));
            verify(orderRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────
    //  Address validation
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Address validation")
    class AddressValidation {

        @Test
        @DisplayName("should throw ResourceNotFoundException when address does not exist")
        void placeOrder_addressNotFound() {
            when(cartRepository.findCartByEmail(EMAIL)).thenReturn(cart);
            when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                    orderService.placeOrder(
                            EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                            "pi_123", "succeeded", "OK", null));

            verify(orderRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────
    //  Stock validation
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Stock validation")
    class StockValidation {

        @Test
        @DisplayName("should throw APIException when product stock is insufficient")
        void placeOrder_insufficientStock() {
            product.setQuantity(1); // cart requests 2
            when(cartRepository.findCartByEmail(EMAIL)).thenReturn(cart);
            when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address));
            when(paymentRepository.findByPgPaymentId(anyString())).thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setPaymentId(1L);
                return p;
            });
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });
            when(orderItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            when(shippingCalculator.calculate(any(Address.class), anyDouble()))
                    .thenAnswer(invocation -> {
                        double cartTotal = invocation.getArgument(1, Double.class);
                        return cartTotal >= 100.0 ? 0.0 : 3.0;
                    });

            PaymentIntent intent = mock(PaymentIntent.class);
            when(intent.getStatus()).thenReturn("succeeded");
            when(intent.getAmount()).thenReturn(10000L);
            doReturn(intent).when(stripeService).retrievePaymentIntent(anyString());

            doThrow(new APIException(String.format(
                    "Insufficient stock for product: %s. Available: %d, requested: %d",
                    product.getProductName(),
                    product.getQuantity(),
                    cartItem.getQuantity())))
                    .when(inventoryReservationService)
                    .consumeReservationsForCart(CART_ID);

            APIException ex = assertThrows(APIException.class, () ->
                    orderService.placeOrder(
                            EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                            "pi_123", "succeeded", "OK", null));

            assertTrue(ex.getMessage().contains("Insufficient stock"));
            assertTrue(ex.getMessage().contains("Wireless Headphones"));
            assertTrue(ex.getMessage().contains("Available: 1"));
            assertTrue(ex.getMessage().contains("requested: 2"));

            // Stock should NOT have been modified
            assertEquals(1, product.getQuantity());
            verify(productRepository, never()).save(any(Product.class));
        }
    }

    // ─────────────────────────────────────────────
    //  Coupon logic
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("Coupon logic")
    class CouponLogic {

        private Coupon buildActiveCoupon(int discountPercent, int maxUses, int usedCount) {
            return Coupon.builder()
                    .id(1L)
                    .code("SAVE10")
                    .discountPercent(discountPercent)
                    .expiryDate(LocalDate.now().plusDays(30))
                    .maxUses(maxUses)
                    .usedCount(usedCount)
                    .active(true)
                    .build();
        }

        private void stubWithCoupon(Coupon coupon) {
            stubHappyPath();
            when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));
            when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));
            doAnswer(invocation -> {
                Coupon c = invocation.getArgument(0);
                String code = invocation.getArgument(1);
                if (!c.getActive()) throw new APIException("Coupon is not active: " + code);
                if (c.getExpiryDate().isBefore(LocalDate.now())) throw new APIException("Coupon has expired: " + code);
                if (c.getUsedCount() >= c.getMaxUses()) throw new APIException("Coupon usage limit reached: " + code);
                return null;
            }).when(couponService).validateCouponState(any(Coupon.class), anyString());
        }

        @Test
        @DisplayName("should apply valid coupon and reduce total amount")
        void placeOrder_validCoupon() {
            Coupon coupon = buildActiveCoupon(10, 100, 5);
            stubWithCoupon(coupon);
            when(paymentIntent.getAmount()).thenReturn(9300L);

            OrderDTO result = orderService.placeOrder(
                    EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                    "pi_123", "succeeded", "OK", List.of("SAVE10"));

            // 100 - 10% = 90, plus 3.0 domestic shipping (Romania) because post-discount total is below 100
            assertEquals(93.0, result.getTotalAmount());
            // Usage is now consumed atomically in the database, not via a read-modify-write save.
            verify(couponRepository).tryConsume(coupon.getId());
        }

        @Test
        @DisplayName("should throw APIException when coupon code is invalid")
        void placeOrder_invalidCouponCode() {
            stubHappyPath();
            when(couponRepository.findByCode("INVALID")).thenReturn(Optional.empty());

            APIException ex = assertThrows(APIException.class, () ->
                    orderService.placeOrder(
                            EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                            "pi_123", "succeeded", "OK", List.of("INVALID")));

            assertTrue(ex.getMessage().contains("Invalid coupon code"));
        }

        @Test
        @DisplayName("should throw APIException when coupon is inactive")
        void placeOrder_inactiveCoupon() {
            Coupon coupon = buildActiveCoupon(10, 100, 5);
            coupon.setActive(false);
            stubWithCoupon(coupon);

            APIException ex = assertThrows(APIException.class, () ->
                    orderService.placeOrder(
                            EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                            "pi_123", "succeeded", "OK", List.of("SAVE10")));

            assertTrue(ex.getMessage().contains("Coupon is not active"));
        }

        @Test
        @DisplayName("should throw APIException when coupon has expired")
        void placeOrder_expiredCoupon() {
            Coupon coupon = buildActiveCoupon(10, 100, 5);
            coupon.setExpiryDate(LocalDate.now().minusDays(1));
            stubWithCoupon(coupon);

            APIException ex = assertThrows(APIException.class, () ->
                    orderService.placeOrder(
                            EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                            "pi_123", "succeeded", "OK", List.of("SAVE10")));

            assertTrue(ex.getMessage().contains("Coupon has expired"));
        }

        @Test
        @DisplayName("should throw APIException when coupon usage limit is reached")
        void placeOrder_couponUsageLimitReached() {
            Coupon coupon = buildActiveCoupon(10, 100, 100);
            stubWithCoupon(coupon);

            APIException ex = assertThrows(APIException.class, () ->
                    orderService.placeOrder(
                            EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                            "pi_123", "succeeded", "OK", List.of("SAVE10")));

            assertTrue(ex.getMessage().contains("Coupon usage limit reached"));
        }

        @Test
        @DisplayName("should reject the order when atomic coupon consumption loses the race")
        void placeOrder_couponConsumptionRaceLost() {
            Coupon coupon = buildActiveCoupon(10, 100, 99);
            stubWithCoupon(coupon);
            when(paymentIntent.getAmount()).thenReturn(9300L);
            // Another concurrent checkout consumed the final use first.
            when(couponRepository.tryConsume(coupon.getId())).thenReturn(0);

            APIException ex = assertThrows(APIException.class, () ->
                    orderService.placeOrder(
                            EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                            "pi_123", "succeeded", "OK", List.of("SAVE10")));

            assertTrue(ex.getMessage().contains("Coupon usage limit reached"));
            verify(orderRepository, never()).save(any(Order.class));
        }

        @Test
        @DisplayName("previewOrder should not consume coupon usage")
        void previewOrder_doesNotConsumeCoupon() {
            Coupon coupon = buildActiveCoupon(10, 100, 5);
            stubWithCoupon(coupon);

            orderService.previewOrder(EMAIL, ADDRESS_ID, List.of("SAVE10"));
            orderService.previewOrder(EMAIL, ADDRESS_ID, List.of("SAVE10"));
            orderService.previewOrder(EMAIL, ADDRESS_ID, List.of("SAVE10"));

            verify(couponRepository, never()).tryConsume(anyLong());
            verify(couponRepository, never()).save(any(Coupon.class));
        }

        @Test
        @DisplayName("should not apply coupon when couponCode is null")
        void placeOrder_nullCouponCode() {
            stubHappyPath();
            when(paymentIntent.getAmount()).thenReturn(10000L);

            orderService.placeOrder(
                    EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                    "pi_123", "succeeded", "OK", null);

            verify(couponRepository, never()).findByCode(anyString());
            verify(couponRepository, never()).save(any(Coupon.class));
        }

        @Test
        @DisplayName("should not apply coupon when couponCode is blank")
        void placeOrder_blankCouponCode() {
            stubHappyPath();
            when(paymentIntent.getAmount()).thenReturn(10000L);

            orderService.placeOrder(
                    EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                    "pi_123", "succeeded", "OK", List.of("   "));

            verify(couponRepository, never()).findByCode(anyString());
            verify(couponRepository, never()).save(any(Coupon.class));
        }

        @Test
        @DisplayName("should look up coupon with uppercase code regardless of input case")
        void placeOrder_couponCodeCaseInsensitive() {
            Coupon coupon = buildActiveCoupon(20, 100, 0);
            stubWithCoupon(coupon);
            when(paymentIntent.getAmount()).thenReturn(8300L);

            orderService.placeOrder(
                    EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                    "pi_123", "succeeded", "OK", List.of("save10"));

            // The implementation calls findByCode with toUpperCase()
            verify(couponRepository).findByCode("SAVE10");
        }

        @Test
        @DisplayName("shipping is calculated on post-discount total: 105 subtotal, 20% coupon, US address -> 8900 cents")
        void shippingCalculatedOnTotalAfterDiscount() {
            Coupon coupon = buildActiveCoupon(20, 100, 0);
            stubWithCoupon(coupon);

            // US address is not domestic, so shipping is $5 when the chargeable total is below $100.
            address.setCountry("US");

            // Use the real ShippingCalculator for this scenario.
            ShippingCalculator realCalculator = new ShippingCalculator();
            when(shippingCalculator.calculate(any(Address.class), anyDouble()))
                    .thenAnswer(invocation ->
                            realCalculator.calculate(invocation.getArgument(0), invocation.getArgument(1, Double.class)));

            cart.setTotalPrice(105.0);

            OrderSummaryDTO preview = orderService.previewOrder(EMAIL, ADDRESS_ID, List.of("SAVE10"));

            assertEquals(105.0, preview.getSubtotal());
            assertEquals(21.0, preview.getDiscountAmount());
            assertEquals(5.0, preview.getShippingCost());
            assertEquals(89.0, preview.getTotalAmount());

            // Stripe PaymentIntent and placeOrder expectedCents must both be 8900.
            long expectedCents = Money.toCents(preview.getTotalAmount());
            assertEquals(8900L, expectedCents);

            when(paymentIntent.getAmount()).thenReturn(expectedCents);
            OrderDTO placed = orderService.placeOrder(
                    EMAIL, ADDRESS_ID, "STRIPE", "Stripe",
                    "pi_123", "succeeded", "OK", List.of("SAVE10"));

            assertEquals(89.0, placed.getTotalAmount());
            assertEquals(5.0, placed.getShippingCost());
            assertEquals(21.0, placed.getDiscountAmount());
        }
    }
}
