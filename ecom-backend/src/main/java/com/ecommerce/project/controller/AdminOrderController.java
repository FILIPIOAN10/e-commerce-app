package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.payload.OrderStatusUpdateDto;
import com.ecommerce.project.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
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
}
