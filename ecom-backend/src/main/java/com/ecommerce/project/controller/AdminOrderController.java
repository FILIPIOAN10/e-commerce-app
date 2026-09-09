package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderResponse;
import com.ecommerce.project.payload.OrderStatusUpdateDto;
import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.service.OrderExportService;
import com.ecommerce.project.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AdminOrderController extends BaseController {

    private final OrderService orderService;
    private final OrderExportService orderExportService;

    public AdminOrderController(OrderService orderService, OrderExportService orderExportService) {
        this.orderService = orderService;
        this.orderExportService = orderExportService;
    }

    @Tag(name = "Order")
    @GetMapping("/admin/orders/export/csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportOrdersCsv() {
        byte[] csv = orderExportService.exportOrdersToCsv();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "orders.csv");
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @Tag(name = "Order")
    @GetMapping("/admin/orders/export/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportOrdersPdf() {
        byte[] pdf = orderExportService.exportOrdersToPdf();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "orders.pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
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
                                                      @Valid @RequestBody OrderStatusUpdateDto orderStatusUpdateDto){
       OrderDTO order = orderService.updateOrder(orderId,orderStatusUpdateDto.getStatus());
       return ok(order);
    }

    @PutMapping("/seller/orders/{orderId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<OrderDTO> updateOrderStatusSeller(@PathVariable Long orderId,
                                                      @Valid @RequestBody OrderStatusUpdateDto orderStatusUpdateDto){
        OrderDTO order = orderService.updateOrder(orderId,orderStatusUpdateDto.getStatus());
        return ok(order);
    }
}
