package com.ecommerce.project.service;

import org.springframework.web.multipart.MultipartFile;

public interface ProductImportService {
    String importProducts(MultipartFile file);
}
