package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.*;
import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.service.order.OrderStatus;
import jakarta.validation.Valid;

import com.ecommerce.project.service.IdempotencyService;
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
import java.math.BigDecimal;

@RestController
@RequestMapping("/api")
public class OrderController extends BaseController {

    private final OrderService orderService;
    private final AuthUtil authUtil;
    private final StripeService stripeService;
    private final IdempotencyService idempotencyService;

    public OrderController(OrderService orderService, AuthUtil authUtil, StripeService stripeService,
                          IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.authUtil = authUtil;
        this.stripeService = stripeService;
        this.idempotencyService = idempotencyService;
    }


    @Tag(name = "Order")
    @PostMapping("/order/users/payments/{paymentMethod}")
    public ResponseEntity<OrderDTO> orderProducts(@PathVariable String paymentMethod,
                                                  @Valid @RequestBody OrderRequestDTO orderRequestDTO,
                                                  @RequestHeader(value = "Idempotency-Key", required = false)
                                                  String idempotencyKey){

        String emailId = authUtil.loggedInEmail();
        return idempotencyService.runIdempotent(
                idempotencyKey,
                "place-order:" + emailId + ":" + paymentMethod,
                orderRequestDTO,
                OrderDTO.class,
                () -> created(orderService.placeOrder(
                        emailId,
                        orderRequestDTO.getAddressId(),
                        paymentMethod,
                        orderRequestDTO.getPgName(),
                        orderRequestDTO.getPgPaymentId(),
                        orderRequestDTO.getPgStatus(),
                        orderRequestDTO.getPgResponseMessage(),
                        orderRequestDTO.getCouponCodes())));
    }
    @PostMapping("/order/stripe-client-secret")
    public ResponseEntity<String> createStripeClientSecret(@Valid @RequestBody StripePaymentDto stripePaymentDto) throws StripeException {

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
    public ResponseEntity<BigDecimal> estimateShipping(@PathVariable Long addressId,
                                                       @RequestParam(defaultValue = "0.00") BigDecimal cartTotal) {
        return ok(orderService.calculateShippingCost(addressId, cartTotal));
    }

    @Tag(name = "Order")
    @PostMapping("/public/orders/guest")
    public ResponseEntity<OrderDTO> placeGuestOrder(@Valid @RequestBody GuestCheckoutRequestDTO request,
                                                   @RequestHeader(value = "Idempotency-Key", required = false)
                                                   String idempotencyKey) {
        return idempotencyService.runIdempotent(
                idempotencyKey,
                "guest-order:" + request.getEmail(),
                request,
                OrderDTO.class,
                () -> created(orderService.placeGuestOrder(request)));
    }

    @GetMapping("/orders/track/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDTO> trackOrder(@PathVariable Long orderId) {
        String email = authUtil.loggedInEmail();
        OrderDTO order = orderService.getOrderById(orderId, email);
        return ok(order);
    }

    @Tag(name = "Order")
    @PutMapping("/orders/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDTO> cancelOrder(@PathVariable Long orderId) {
        String email = authUtil.loggedInEmail();
        // Verify the order belongs to the caller first; throws if it does not.
        orderService.getOrderById(orderId, email);
        OrderDTO order = orderService.updateOrder(orderId, OrderStatus.CANCELLED);
        return ok(order);
    }

}

