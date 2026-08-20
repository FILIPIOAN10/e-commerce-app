package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.*;
import com.ecommerce.project.payload.PaginationParams;
import jakarta.validation.Valid;

import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.StripeService;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController extends BaseController {

    private final OrderService orderService;
    private final AuthUtil authUtil;
    private final StripeService stripeService;

    public OrderController(OrderService orderService, AuthUtil authUtil, StripeService stripeService) {
        this.orderService = orderService;
        this.authUtil = authUtil;
        this.stripeService = stripeService;
    }


    @Tag(name = "Order")
    @PostMapping("/order/users/payments/{paymentMethod}")
    public ResponseEntity<OrderDTO> orderProducts(@PathVariable String paymentMethod,
                                                  @RequestBody OrderRequestDTO orderRequestDTO){

        String emailId = authUtil.loggedInEmail();
        OrderDTO order = orderService.placeOrder(
                emailId,
                orderRequestDTO.getAddressId(),
                paymentMethod,
                orderRequestDTO.getPgName(),
                orderRequestDTO.getPgPaymentId(),
                orderRequestDTO.getPgStatus(),
                orderRequestDTO.getPgResponseMessage(),
                orderRequestDTO.getCouponCodes()
        );
        return created(order);
    }
    @PostMapping("/order/stripe-client-secret")
    public ResponseEntity<String> createStripeClientSecret(@RequestBody StripePaymentDto stripePaymentDto) throws StripeException {

        PaymentIntent paymentIntent = stripeService.paymentIntent(stripePaymentDto);
        return  created(paymentIntent.getClientSecret());

    }
    @GetMapping("/orders/my-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> getMyOrders(@ModelAttribute PaginationParams params){
        String email = authUtil.loggedInEmail();
        OrderResponse orderResponse = orderService.getLoggedInUserOrders(email, params.getPageNumber(), params.getPageSize(), params.getSortBy(), params.getSortOrder());
        return ok(orderResponse);
    }

    @Tag(name = "Order")
    @PostMapping("/order/preview")
    public ResponseEntity<OrderSummaryDTO> previewOrder(@Valid @RequestBody OrderRequestDTO orderRequestDTO) {
        String emailId = authUtil.loggedInEmail();
        OrderSummaryDTO summary = orderService.previewOrder(
                emailId,
                orderRequestDTO.getAddressId(),
                orderRequestDTO.getCouponCodes()
        );
        return ok(summary);
    }

    @Tag(name = "Order")
    @GetMapping("/order/shipping/{addressId}")
    public ResponseEntity<Double> estimateShipping(@PathVariable Long addressId,
                                                   @RequestParam(defaultValue = "0.0") Double cartTotal) {
        double shipping = orderService.calculateShippingCost(addressId, cartTotal);
        return ok(shipping);
    }

    @Tag(name = "Order")
    @PostMapping("/public/orders/guest")
    public ResponseEntity<OrderDTO> placeGuestOrder(@Valid @RequestBody GuestCheckoutRequestDTO request) {
        OrderDTO order = orderService.placeGuestOrder(request);
        return created(order);
    }

    @GetMapping("/orders/track/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDTO> trackOrder(@PathVariable Long orderId) {
        String email = authUtil.loggedInEmail();
        OrderDTO order = orderService.getOrderById(orderId, email);
        return ok(order);
    }

}

