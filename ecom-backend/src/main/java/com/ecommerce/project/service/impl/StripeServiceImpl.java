package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.Coupon;
import com.ecommerce.project.payload.StripePaymentDto;
import com.ecommerce.project.repository.AddressRepository;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.CouponRepository;
import com.ecommerce.project.service.CouponService;
import com.ecommerce.project.service.StripeService;
import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.pricing.ShippingCalculator;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerSearchResult;
import com.stripe.model.PaymentIntent;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class StripeServiceImpl implements StripeService {
    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    private final AuthUtil authUtil;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final CouponRepository couponRepository;
    private final CouponService couponService;
    private final ShippingCalculator shippingCalculator;

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

        double subtotal = cart.getTotalPrice();
        double totalAfterDiscount = subtotal;
        List<String> couponCodes = stripePaymentDto.getCouponCodes();
        if (couponCodes != null) {
            for (String code : couponCodes) {
                if (code == null || code.isBlank()) {
                    continue;
                }
                Coupon coupon = couponRepository.findByCode(code.toUpperCase())
                        .orElseThrow(() -> new APIException("Invalid coupon code: " + code));
                couponService.validateCouponState(coupon, code);
                double discount = totalAfterDiscount * coupon.getDiscountPercent() / 100.0;
                totalAfterDiscount -= discount;
            }
        }

        double shippingCost = shippingCalculator.calculate(address, totalAfterDiscount);
        double totalAmount = totalAfterDiscount + shippingCost;

        long serverCalculatedAmountCents = Money.toCents(totalAmount);

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

}
