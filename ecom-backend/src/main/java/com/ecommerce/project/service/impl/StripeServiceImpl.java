package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.payload.StripePaymentDto;
import com.ecommerce.project.repository.AddressRepository;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.service.StripeService;
import com.ecommerce.project.service.payment.RefundResult;
import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.pricing.PriceBreakdown;
import com.ecommerce.project.service.pricing.PricingContext;
import com.ecommerce.project.service.pricing.PricingPipeline;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerSearchResult;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Every method here spends most of its wall-clock time inside a blocking Stripe
 * REST call (Stripe's Java client defaults: 30s connect, 80s read). None of them
 * may run inside a transaction: a transaction borrows a HikariCP connection for
 * its whole body, so a class-level {@code @Transactional} here parked a pooled
 * connection for the duration of two-to-four sequential Stripe round-trips. Under
 * a Stripe latency spike, {@code DB_POOL_MAX} concurrent checkouts drained the
 * pool and every other request in the app then failed on
 * {@code hikari.connection-timeout} — a partial Stripe outage became a full-site
 * outage.
 *
 * <ul>
 *   <li>{@link #retrievePaymentIntent(String)} and
 *       {@link #issueRefund(String, long, String)} touch no database at all.</li>
 *   <li>{@link #paymentIntent(StripePaymentDto)} reads the cart and address
 *       first; {@code CartRepository.findCartByEmail} {@code JOIN FETCH}es the
 *       user, items and their products, so every field it reads is initialised
 *       before the Stripe calls begin and no open transaction is needed to keep
 *       lazy proxies alive across them.</li>
 * </ul>
 *
 * <p>Callers that genuinely need Stripe I/O and a DB write in one unit of work
 * (e.g. {@code RefundHandler}) must order it call-Stripe-then-write in their own
 * short transaction, not wrap this service.
 */
@Service
@RequiredArgsConstructor
public class StripeServiceImpl implements StripeService {
    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    private final AuthUtil authUtil;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final PricingPipeline pricingPipeline;

    @PostConstruct
    public void init(){
        Stripe.apiKey = stripeApiKey;
    }


    @Override
    public PaymentIntent paymentIntent(StripePaymentDto stripePaymentDto) throws StripeException {
        String email = authUtil.loggedInEmail();
        Cart cart = cartRepository.findCartByEmail(email);
        if (cart == null || cart.getCartItems().isEmpty()) {
            throw new APIException("Cart is Empty");
        }

        Address address = null;
        if (stripePaymentDto.getAddress() != null && stripePaymentDto.getAddress().getAddressId() != null) {
            address = addressRepository.findById(stripePaymentDto.getAddress().getAddressId())
                    .orElseThrow(() -> new APIException("Address not found"));
            if (address.getUser() == null || !email.equalsIgnoreCase(address.getUser().getEmail())) {
                throw new APIException("Address does not belong to the current user");
            }
        }

        // Same pricing path as checkout, so the PaymentIntent amount and the
        // order total verified later (StripePaymentGateway) agree by construction.
        PriceBreakdown pricing = pricingPipeline.price(
                PricingContext.of(cart.getTotalPrice(), address, stripePaymentDto.getCouponCodes()));

        long serverCalculatedAmountCents = pricing.total().toCents();

        Customer customer;
        CustomerSearchParams searchParams =
                CustomerSearchParams.builder()
                        .setQuery("email:'" + stripePaymentDto.getEmail() + "'")
                        .build();
        CustomerSearchResult customers = Customer.search(searchParams);
        if (customers.getData().isEmpty()) {
            CustomerCreateParams customerParams =
                    CustomerCreateParams.builder()
                            .setEmail(stripePaymentDto.getEmail())
                            .setName(stripePaymentDto.getName())
                            .setAddress(
                                    CustomerCreateParams.Address.builder()
                                            .setLine1(stripePaymentDto.getAddress().getStreet())
                                            .setCity(stripePaymentDto.getAddress().getCity())
                                            .setState(stripePaymentDto.getAddress().getState())
                                            .setPostalCode(stripePaymentDto.getAddress().getPincode())
                                            .setCountry(stripePaymentDto.getAddress().getCountry())
                                            .build()
                            ).build();
            customer = Customer.create(customerParams);
        } else {
            customer = customers.getData().get(0);
        }

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount(serverCalculatedAmountCents)
                        .setCurrency(stripePaymentDto.getCurrency())
                        .setCustomer(customer.getId())
                        .setDescription(stripePaymentDto.getDescription())
                        .setAutomaticPaymentMethods(
                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                        .setEnabled(true)
                                        .build()
                        )
                        .build();

        return PaymentIntent.create(params);
    }

    @Override
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) {
        try {
            return PaymentIntent.retrieve(paymentIntentId);
        } catch (StripeException e) {
            throw new APIException("Failed to retrieve payment: " + e.getMessage());
        }
    }

    @Override
    public RefundResult issueRefund(String paymentIntentId, long amountMinorUnits, String idempotencyKey)
            throws StripeException {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(amountMinorUnits)
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .build();
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();
        Refund refund = Refund.create(params, options);
        return new RefundResult(refund.getId(), refund.getStatus());
    }

}
