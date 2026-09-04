package com.ecommerce.project.service.impl;

import com.ecommerce.project.cache.EvictProductCaches;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductImage;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repository.ProductImageRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.FileService;
import com.ecommerce.project.service.ProductImageService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
public class ProductImageServiceImpl implements ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final FileService fileService;
    private final ProductMapper productMapper;

    private final AuthUtil authUtil;

    @Value("${project.image}")
    private String path;

    public ProductImageServiceImpl(ProductRepository productRepository,
                                   ProductImageRepository productImageRepository,
                                   FileService fileService,
                                   ProductMapper productMapper,AuthUtil authUtil) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.fileService = fileService;
        this.productMapper = productMapper;
        this.authUtil=authUtil;
    }

    @Override
    @EvictProductCaches
    @CacheEvict(value = "product", key = "#productId")
    public ProductDTO updateProductImage(Long productId, MultipartFile image) throws IOException {
        Product productFromDb = loadProductForWrite(productId);

        String fileName = fileService.uploadImage(path, image);
        productFromDb.setImage(fileName);

        Product updatedProduct = productRepository.save(productFromDb);
        return productMapper.mapProductToDTO(updatedProduct);
    }

    @Override
    @EvictProductCaches
    @CacheEvict(value = "product", key = "#productId")
    public ProductDTO uploadProductGalleryImages(Long productId, MultipartFile[] images) throws IOException {
        Product product = loadProductForWrite(productId);

        if (product.getProductImages() == null) {
            product.setProductImages(new ArrayList<>());
        }

        for (MultipartFile file : images) {
            if (file != null && !file.isEmpty()) {
                String fileName = fileService.uploadImage(path, file);
                ProductImage productImage = new ProductImage();
                productImage.setImageName(fileName);
                productImage.setProduct(product);
                product.getProductImages().add(productImage);
            }
        }

        Product savedProduct = productRepository.save(product);
        return productMapper.mapProductToDTO(savedProduct);
    }

    @Override
    @EvictProductCaches
    @CacheEvict(value = "product", key = "#productId")
    public ProductDTO deleteProductGalleryImage(Long productId, Long imageId) {
        Product product = loadProductForWrite(productId);

        ProductImage imageToRemove = product.getProductImages().stream()
                .filter(img -> img.getImageId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", "imageId", imageId));

        product.getProductImages().remove(imageToRemove);
        productImageRepository.delete(imageToRemove);

        Product savedProduct = productRepository.save(product);
        return productMapper.mapProductToDTO(savedProduct);
    }

    @Override
    public void deleteProductImages(Product product) {
        if (product == null) {
            return;
        }
        deleteProductImage(product.getImage());
        List<ProductImage> galleryImages = productImageRepository.findByProduct_ProductId(product.getProductId());
        if (galleryImages != null) {
            galleryImages.forEach(img -> deleteProductImage(img.getImageName()));
        }
    }

    private void deleteProductImage(String imageName) {
        if (imageName == null || imageName.isBlank() || "cal.png".equals(imageName) || imageName.endsWith("/cal.png")) {
            return;
        }
        try {
            fileService.deleteImage(path, imageName);
        } catch (IOException e) {
            log.warn("Failed to delete product image {}: {}", imageName, e.getMessage());
        }
    }
    private Product loadProductForWrite(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        User current = authUtil.loggedInUser();
        boolean isAdmin = current.getRoles().stream()
                .anyMatch(r -> r.getRoleName() == AppRole.ROLE_ADMIN);

        if (!isAdmin && (product.getUser() == null
                || !product.getUser().getUserId().equals(current.getUserId()))) {
            throw new AccessDeniedException("You can only modify your own products");
        }

        return product;
    }
}
