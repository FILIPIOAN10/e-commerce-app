package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.ApiResponse;
import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.service.ProductImageService;
import com.ecommerce.project.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ProductManagementController extends BaseController {

    private final ProductService productService;
    private final ProductImageService productImageService;

    public ProductManagementController(ProductService productService, ProductImageService productImageService) {
        this.productService = productService;
        this.productImageService = productImageService;
    }

    @Tag(name = "Product")
    @PostMapping({"/admin/categories/{categoryId}/product", "/seller/categories/{categoryId}/product"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<ProductDTO> addProduct(@Valid @RequestBody ProductDTO productDTO,
                                                 @PathVariable Long categoryId) {
        ProductDTO savedProductDto = productService.addProduct(categoryId, productDTO);
        return created(savedProductDto);
    }

    @Tag(name="Product")
    @PostMapping("/admin/products/search/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> reindexProductSearch(){
        int indexedProducts = productService.reindexProductSearch();
        ApiResponse response = new ApiResponse("Reindexed " + indexedProducts +" products",true);
        return ok(response);
    }

    @Tag(name = "Product")
    @PutMapping({"/admin/products/{productId}", "/seller/products/{productId}"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> updateProduct(@Valid @RequestBody ProductDTO productDTO,
                                                    @PathVariable Long productId) {
        ProductDTO updatedProductDTO = productService.updateProduct(productId,productDTO);
        return ok(updatedProductDTO);
    }

    @Tag(name = "Product")
    @DeleteMapping({"/admin/products/{productId}", "/seller/products/{productId}"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public  ResponseEntity<ProductDTO> deleteProduct(@PathVariable Long productId) {
        ProductDTO deletedProduct = productService.deleteProduct(productId);
        return ok(deletedProduct);
    }

    @Tag(name = "Product")
    @PutMapping({"/admin/products/{productId}/image", "/seller/products/{productId}/image"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> updateProductImage(@PathVariable Long productId,
                                                         @RequestParam("image") MultipartFile image) throws IOException {

        ProductDTO updatedProduct = productImageService.updateProductImage(productId, image);
        return ok(updatedProduct);
    }

    @Tag(name = "Product")
    @PostMapping({"/admin/products/{productId}/gallery", "/seller/products/{productId}/gallery"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> uploadGalleryImages(@PathVariable Long productId,
                                                          @RequestParam("images") MultipartFile[] images) throws IOException {
        ProductDTO updatedProduct = productImageService.uploadProductGalleryImages(productId, images);
        return ok(updatedProduct);
    }

    @Tag(name = "Product")
    @DeleteMapping({"/admin/products/{productId}/gallery/{imageId}", "/seller/products/{productId}/gallery/{imageId}"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> deleteGalleryImage(@PathVariable Long productId,
                                                         @PathVariable Long imageId) {
        ProductDTO updatedProduct = productImageService.deleteProductGalleryImage(productId, imageId);
        return ok(updatedProduct);
    }

    @Tag(name = "Product")
    @GetMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> getAllProductsForAdmin(@ModelAttribute PaginationParams params) {
        ProductResponse productResponse = productService.getAllProductsForAdmin(params.getPageNumber(),params.getPageSize(),params.getSortBy(),params.getSortOrder());
        return ok(productResponse);
    }

    @Tag(name = "Product")
    @GetMapping("/seller/products")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductResponse> getAllProductsForSeller(@ModelAttribute PaginationParams params) {
        ProductResponse productResponse = productService.getAllProductsForSeller(params.getPageNumber(),params.getPageSize(),params.getSortBy(),params.getSortOrder());
        return ok(productResponse);
    }
}
