package com.ecommerce.project.service;

import org.springframework.web.multipart.MultipartFile;

public interface ProductImportService {

    /**
     * Parse the CSV and persist one row at a time. The outer call runs outside
     * any transaction — see {@link #importRow(String[], Long)}. Returns a short
     * summary that names any rows the import skipped.
     */
    String importProducts(MultipartFile file);

    /**
     * Persist one CSV row in its own transaction so a bad row rejects only
     * itself. Exposed on the interface because the implementation reaches this
     * method through its Spring proxy — a direct call from {@code importProducts}
     * would bypass the {@code REQUIRES_NEW} boundary.
     */
    void importRow(String[] cols, Long sellerId);
}
