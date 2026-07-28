package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.payload.*;

import com.ecommerce.project.security.services.UserDetailsImpl;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.StripeService;
import com.ecommerce.project.util.AuthUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {

    private OrderService orderService;
    private AuthUtil authUtil;
    private StripeService stripeService;

    public OrderController(OrderService orderService, AuthUtil authUtil,StripeService stripeService) {
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
                orderRequestDTO.getPgResponseMessage()
        );
        return new  ResponseEntity<OrderDTO>(order,HttpStatus.CREATED);
    }
    @PostMapping("/order/stripe-client-secret")
    public ResponseEntity<String> createStripeClientSecret(@RequestBody StripePaymentDto stripePaymentDto) throws StripeException {

        PaymentIntent paymentIntent = stripeService.paymentIntent(stripePaymentDto);
        return  new ResponseEntity<>(paymentIntent.getClientSecret(),HttpStatus.CREATED);

    }
    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> getAllOrders(
            @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
            @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_ORDERS_BY, required = false)  String sortBy,
            @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder
    ){
        OrderResponse orderResponse = orderService.getAllOrders(pageNumber,pageSize,sortBy,sortOrder);
        return new ResponseEntity<OrderResponse>(orderResponse,HttpStatus.OK);
    }



    @GetMapping("/seller/orders")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<OrderResponse> getAllSellerOrders(
            @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
            @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_ORDERS_BY, required = false)  String sortBy,
            @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder
    ){
        OrderResponse orderResponse = orderService.getAllSellerOrders(pageNumber,pageSize,sortBy,sortOrder);
        return new ResponseEntity<OrderResponse>(orderResponse,HttpStatus.OK);
    }
    @PutMapping("/admin/orders/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderDTO> updateOrderStatus(@PathVariable Long orderId,
                                                      @RequestBody OrderStatusUpdateDto orderStatusUpdateDto){

       OrderDTO order = orderService.updateOrder(orderId,orderStatusUpdateDto.getStatus());
       return new ResponseEntity<OrderDTO>(order,HttpStatus.OK);
    }


    @PutMapping("/seller/orders/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<OrderDTO> updateOrderStatusSeller(@PathVariable Long orderId,
                                                      @RequestBody OrderStatusUpdateDto orderStatusUpdateDto){

        OrderDTO order = orderService.updateOrder(orderId,orderStatusUpdateDto.getStatus());
        return new ResponseEntity<OrderDTO>(order,HttpStatus.OK);
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
}

