package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.*;
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
public class OrderController {

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
        return new  ResponseEntity<OrderDTO>(order,HttpStatus.CREATED);
    }
    @PostMapping("/order/stripe-client-secret")
    public ResponseEntity<String> createStripeClientSecret(@RequestBody StripePaymentDto stripePaymentDto) throws StripeException {

        PaymentIntent paymentIntent = stripeService.paymentIntent(stripePaymentDto);
        return  new ResponseEntity<>(paymentIntent.getClientSecret(),HttpStatus.CREATED);

    }
    @GetMapping("/orders/my-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> getMyOrders(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_ORDERS_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ){
        String email = authUtil.loggedInEmail();

        OrderResponse orderResponse = orderService.getLoggedInUserOrders(email, pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<OrderResponse>(orderResponse, HttpStatus.OK);
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
        return new ResponseEntity<>(summary, HttpStatus.OK);
    }

    @Tag(name = "Order")
    @GetMapping("/order/shipping/{addressId}")
    public ResponseEntity<Double> estimateShipping(@PathVariable Long addressId,
                                                   @RequestParam(defaultValue = "0.0") Double cartTotal) {
        double shipping = orderService.calculateShippingCost(addressId, cartTotal);
        return new ResponseEntity<>(shipping, HttpStatus.OK);
    }

    @Tag(name = "Order")
    @PostMapping("/public/orders/guest")
    public ResponseEntity<OrderDTO> placeGuestOrder(@Valid @RequestBody GuestCheckoutRequestDTO request) {
        OrderDTO order = orderService.placeGuestOrder(request);
        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    @GetMapping("/orders/track/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDTO> trackOrder(@PathVariable Long orderId) {
        String email = authUtil.loggedInEmail();
        OrderDTO order = orderService.getOrderById(orderId, email);
        return new ResponseEntity<OrderDTO>(order, HttpStatus.OK);
    }

}

