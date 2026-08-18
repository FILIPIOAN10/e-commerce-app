package com.ecommerce.project.service;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ProductDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ProductImageService {

    ProductDTO updateProductImage(Long productId, MultipartFile image) throws IOException;

    ProductDTO uploadProductGalleryImages(Long productId, MultipartFile[] images) throws IOException;

    ProductDTO deleteProductGalleryImage(Long productId, Long imageId);

    void deleteProductImages(Product product);
}
