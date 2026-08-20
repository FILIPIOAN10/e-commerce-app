package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.payload.OrderStatusUpdateDto;
import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AdminOrderController extends BaseController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> getAllOrders(@ModelAttribute PaginationParams params){
        OrderResponse orderResponse = orderService.getAllOrders(params.getPageNumber(),params.getPageSize(),params.getSortBy(),params.getSortOrder());
        return ok(orderResponse);
    }

    @GetMapping("/seller/orders")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<OrderResponse> getAllSellerOrders(@ModelAttribute PaginationParams params){
        OrderResponse orderResponse = orderService.getAllSellerOrders(params.getPageNumber(),params.getPageSize(),params.getSortBy(),params.getSortOrder());
        return ok(orderResponse);
    }

    @PutMapping("/admin/orders/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderDTO> updateOrderStatus(@PathVariable Long orderId,
                                                      @RequestBody OrderStatusUpdateDto orderStatusUpdateDto){
       OrderDTO order = orderService.updateOrder(orderId,orderStatusUpdateDto.getStatus());
       return ok(order);
    }

    @PutMapping("/seller/orders/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<OrderDTO> updateOrderStatusSeller(@PathVariable Long orderId,
                                                      @RequestBody OrderStatusUpdateDto orderStatusUpdateDto){
        OrderDTO order = orderService.updateOrder(orderId,orderStatusUpdateDto.getStatus());
        return ok(order);
    }
}
