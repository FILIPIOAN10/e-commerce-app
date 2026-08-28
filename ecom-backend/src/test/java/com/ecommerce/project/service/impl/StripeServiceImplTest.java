package com.ecommerce.project.service.impl;

import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.StripePaymentDto;
import com.ecommerce.project.repository.AddressRepository;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.CouponRepository;
import com.ecommerce.project.service.CouponService;
import com.ecommerce.project.service.pricing.PricingContext;
import com.ecommerce.project.service.pricing.PricingPipeline;
import com.ecommerce.project.service.pricing.ShippingCalculator;
import com.ecommerce.project.service.pricing.rule.CouponDiscountRule;
import com.ecommerce.project.service.pricing.rule.ShippingRule;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerSearchResult;
import com.stripe.model.PaymentIntent;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StripeServiceImpl")
class StripeServiceImplTest {

    @Mock private AuthUtil authUtil;
    @Mock private CartRepository cartRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private CouponService couponService;
    @Mock private ShippingCalculator shippingCalculator;
    @Mock private PricingPipeline pricingPipeline;

    @InjectMocks
    private StripeServiceImpl stripeService;

    private static final String EMAIL = "user1@test.com";
    private static final Long ADDRESS_ID = 1L;

    @BeforeEach
    void setUp() {
        when(authUtil.loggedInEmail()).thenReturn(EMAIL);

        // Exercise the real pricing pipeline; its leaf deps stay mocked.
        PricingPipeline realPipeline = new PricingPipeline(List.of(
                new CouponDiscountRule(couponRepository, couponService),
                new ShippingRule(shippingCalculator)));
        when(pricingPipeline.price(any()))
                .thenAnswer(inv -> realPipeline.price(inv.getArgument(0, PricingContext.class)));
    }

    @Test
    @DisplayName("PaymentIntent amount equals 8900 cents for subtotal 105, 20% coupon, US address")
    void paymentIntentAmountMatchesOrderTotal() throws StripeException {
        Address address = new Address("Street", "Block", "City", "State", "US", "12345");
        address.setAddressId(ADDRESS_ID);
        User user = new User();
        user.setUserId(1L);
        user.setEmail(EMAIL);
        address.setUser(user);

        Product product = new Product();
        product.setProductId(1L);
        product.setProductName("Widget");
        product.setQuantity(10);

        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(1);
        cartItem.setProductPrice(105.0);

        Cart cart = new Cart();
        cart.setCartId(10L);
        cart.setTotalPrice(105.0);
        cart.setCartItems(new ArrayList<>(List.of(cartItem)));

        Coupon coupon = Coupon.builder()
                .id(1L)
                .code("SAVE20")
                .discountPercent(20)
                .build();

        StripePaymentDto dto = new StripePaymentDto();
        dto.setAddress(address);
        dto.setEmail(EMAIL);
        dto.setName("Test User");
        dto.setCurrency("usd");
        dto.setDescription("Order");
        dto.setCouponCodes(List.of("save20"));

        when(cartRepository.findCartByEmail(EMAIL)).thenReturn(cart);
        when(addressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address));
        when(couponRepository.findByCode("SAVE20")).thenReturn(Optional.of(coupon));
        doNothing().when(couponService).validateCouponState(any(Coupon.class), anyString());

        // Real ShippingCalculator for the scenario: 84 after discount, US -> 5.0 shipping.
        ShippingCalculator real = new ShippingCalculator();
        when(shippingCalculator.calculate(any(Address.class), anyDouble()))
                .thenAnswer(inv -> real.calculate(inv.getArgument(0), inv.getArgument(1, Double.class)));

        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn("cus_123");

        CustomerSearchResult searchResult = mock(CustomerSearchResult.class);
        when(searchResult.getData()).thenReturn(List.of(customer));

        PaymentIntent createdIntent = mock(PaymentIntent.class);

        try (MockedStatic<Customer> customerStatic = mockStatic(Customer.class);
             MockedStatic<PaymentIntent> paymentIntentStatic = mockStatic(PaymentIntent.class)) {

            customerStatic.when(() -> Customer.search(any(CustomerSearchParams.class))).thenReturn(searchResult);

            paymentIntentStatic.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                    .thenAnswer(invocation -> {
                        PaymentIntentCreateParams params = invocation.getArgument(0);
                        when(createdIntent.getAmount()).thenReturn(params.getAmount());
                        return createdIntent;
                    });

            PaymentIntent result = stripeService.paymentIntent(dto);

            assertNotNull(result);
            assertEquals(8900L, result.getAmount());

            ArgumentCaptor<PaymentIntentCreateParams> captor = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
            paymentIntentStatic.verify(() -> PaymentIntent.create(captor.capture()));
            assertEquals(8900L, captor.getValue().getAmount());
        }
    }
}
