package com.ecommerce.project.controller;

import com.ecommerce.project.service.ProductImportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductImportController {

    private final ProductImportService productImportService;

    @Tag(name = "Product Import")
    @PostMapping("/admin/products/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> importProducts(@RequestParam("file") MultipartFile file) {
        String message = productImportService.importProducts(file);
        return new ResponseEntity<>(Map.of("message", message), HttpStatus.CREATED);
    }
}
