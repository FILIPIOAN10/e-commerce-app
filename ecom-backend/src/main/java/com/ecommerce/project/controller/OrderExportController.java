package com.ecommerce.project.controller;

import com.ecommerce.project.service.InvoiceService;
import com.ecommerce.project.service.OrderExportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderExportController {

    private final OrderExportService orderExportService;
    private final InvoiceService invoiceService;

    public OrderExportController(OrderExportService orderExportService, InvoiceService invoiceService) {
        this.orderExportService = orderExportService;
        this.invoiceService = invoiceService;
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

    @Tag(name = "Order")
    @GetMapping("/orders/invoice/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable Long orderId) {
        byte[] pdf = invoiceService.generateInvoicePdf(orderId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "invoice-" + orderId + ".pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
