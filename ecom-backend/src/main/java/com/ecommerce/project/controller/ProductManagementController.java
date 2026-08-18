package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.ApiResponse;
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
public class ProductManagementController {

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
        return new ResponseEntity<>(savedProductDto, HttpStatus.CREATED);
    }

    @Tag(name="Product")
    @PostMapping("/admin/products/search/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> reindexProductSearch(){
        int indexedProducts = productService.reindexProductSearch();
        ApiResponse response = new ApiResponse("Reindexed " + indexedProducts +" products",true);
        return new ResponseEntity<>(response,HttpStatus.OK);
    }

    @Tag(name = "Product")
    @PutMapping({"/admin/products/{productId}", "/seller/products/{productId}"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> updateProduct(@Valid @RequestBody ProductDTO productDTO,
                                                    @PathVariable Long productId) {
        ProductDTO updatedProductDTO = productService.updateProduct(productId,productDTO);
        return new ResponseEntity<>(updatedProductDTO,HttpStatus.OK);
    }

    @Tag(name = "Product")
    @DeleteMapping({"/admin/products/{productId}", "/seller/products/{productId}"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public  ResponseEntity<ProductDTO> deleteProduct(@PathVariable Long productId) {
        ProductDTO deletedProduct = productService.deleteProduct(productId);
        return new ResponseEntity<>(deletedProduct,HttpStatus.OK);
    }

    @Tag(name = "Product")
    @PutMapping({"/admin/products/{productId}/image", "/seller/products/{productId}/image"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> updateProductImage(@PathVariable Long productId,
                                                         @RequestParam("image") MultipartFile image) throws IOException {

        ProductDTO updatedProduct = productImageService.updateProductImage(productId, image);
        return new ResponseEntity<>(updatedProduct,HttpStatus.OK);
    }

    @Tag(name = "Product")
    @PostMapping({"/admin/products/{productId}/gallery", "/seller/products/{productId}/gallery"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> uploadGalleryImages(@PathVariable Long productId,
                                                          @RequestParam("images") MultipartFile[] images) throws IOException {
        ProductDTO updatedProduct = productImageService.uploadProductGalleryImages(productId, images);
        return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    }

    @Tag(name = "Product")
    @DeleteMapping({"/admin/products/{productId}/gallery/{imageId}", "/seller/products/{productId}/gallery/{imageId}"})
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> deleteGalleryImage(@PathVariable Long productId,
                                                         @PathVariable Long imageId) {
        ProductDTO updatedProduct = productImageService.deleteProductGalleryImage(productId, imageId);
        return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    }

    @Tag(name = "Product")
    @GetMapping("/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> getAllProductsForAdmin(
            @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
            @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false)  String sortBy,
            @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder

    ) {
        ProductResponse productResponse = productService.getAllProductsForAdmin(pageNumber,pageSize,sortBy,sortOrder);
        return new ResponseEntity<>(productResponse, HttpStatus.OK);
    }

    @Tag(name = "Product")
    @GetMapping("/seller/products")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductResponse> getAllProductsForSeller(
            @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
            @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false)  String sortBy,
            @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder

    ) {
        ProductResponse productResponse = productService.getAllProductsForSeller(pageNumber,pageSize,sortBy,sortOrder);
        return new ResponseEntity<>(productResponse, HttpStatus.OK);
    }
}
