package com.ecommerce.project.controller;


import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.payload.ApiResponse;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
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

// Controller pentru operații CRUD pe produse (public și admin)

public class ProductController {

    private ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    /**
     * Adaugă un produs nou într-o anumită categorie (admin only).
     */
    @Tag(name = "Product")
    @PostMapping("/admin/categories/{categoryId}/product")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> addProduct(@Valid @RequestBody ProductDTO productDTO,
                                                 @PathVariable Long categoryId) {
        ProductDTO savedProductDto = productService.addProduct(categoryId, productDTO);
        return new ResponseEntity<>(savedProductDto, HttpStatus.CREATED);
    }

    @Tag(name = "Product")
    @PostMapping("/seller/categories/{categoryId}/product")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<ProductDTO> addProductSeller(@Valid @RequestBody ProductDTO productDTO,
                                                 @PathVariable Long categoryId) {
        ProductDTO savedProductDto = productService.addProduct(categoryId, productDTO);
        return new ResponseEntity<>(savedProductDto, HttpStatus.CREATED);
    }
    /**
     * Returnează toate produsele cu paginare și sortare.
     */
    @Tag(name = "Product")
    @GetMapping("/public/products")
    public ResponseEntity<ProductResponse> getAllProducts(
            @RequestParam(name = "keyword",required = false)  String keyword,
            @RequestParam(name = "category",required = false) String category,
            @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
            @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false)  String sortBy,
            @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder

    ) {
        ProductResponse productResponse = productService.getAllProducts(pageNumber,pageSize,sortBy,sortOrder,keyword,category);
        return new ResponseEntity<>(productResponse, HttpStatus.OK);
    }





    /**
     * Returnează produsele care aparțin unei anumite categorii.
     */
    @Tag(name = "Product")
    @GetMapping("/public/categories/{categoryId}/products")
    public ResponseEntity<ProductResponse> getProductsByCategory(@PathVariable Long categoryId,
                                                                 @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
                                                                 @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
                                                                 @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false)  String sortBy,
                                                                 @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder) {
        ProductResponse productResponse = productService.searchByCategory(categoryId,pageNumber,pageSize,sortBy,sortOrder);

        return new ResponseEntity<>(productResponse, HttpStatus.OK);
    }
    /**
     * Caută produse pe baza unui keyword (în nume / descriere).
     */
    @Tag(name = "Product")
    @GetMapping("/public/products/keyword/{keyword}")
    public ResponseEntity<ProductResponse> getProductByKeyword(@PathVariable String keyword,
                                                               @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
                                                               @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false)Integer  pageSize,
                                                               @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false)  String sortBy,
                                                               @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String  sortOrder
                                                               ) {
        ProductResponse productResponse= productService.searchProductByKeyword(keyword,pageNumber,pageSize,sortBy,sortOrder);
        return new ResponseEntity<>(productResponse, HttpStatus.FOUND);

    }
    @Tag(name="Product")
    @GetMapping("/public/products/search")
    public ResponseEntity<ProductResponse> searchProducts(@RequestParam(name = "q") String query,
                                                          @RequestParam(name = "semantic",defaultValue = "true",required = false) Boolean semantic,
                                                          @RequestParam(name = "pageNumber",defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
                                                          @RequestParam(name = "pageSize",defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
                                                          @RequestParam(name = "sortBy",defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false) String sortBy,
                                                          @RequestParam(name = "sortOrder",defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder){
        ProductResponse productResponse = productService.searchProducts(query,pageNumber,pageSize,sortBy,sortOrder,semantic);
        return new ResponseEntity<>(productResponse,HttpStatus.OK);
    }


    @Tag(name="Product")
    @PostMapping("/admin/products/search/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> reindexProductSearch(){
        int indexedProducts = productService.reindexProductSearch();
        ApiResponse response = new ApiResponse("Reindexed " + indexedProducts +" products",true);
        return new ResponseEntity<>(response,HttpStatus.OK);
    }

    /**
     * Actualizează un produs existent.
     */
    @Tag(name = "Product")
    @PutMapping("/admin/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> updateProduct(@Valid @RequestBody ProductDTO productDTO,
                                                    @PathVariable Long productId) {
        ProductDTO updatedProductDTO = productService.updateProduct(productId,productDTO);
        return new ResponseEntity<>(updatedProductDTO,HttpStatus.OK);
    }

    /**
     * Șterge un produs după ID (admin only).
     */
    @Tag(name = "Product")
    @DeleteMapping("/admin/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public  ResponseEntity<ProductDTO> deleteProduct(@PathVariable Long productId) {
        ProductDTO deletedProduct = productService.deleteProduct(productId);
        return new ResponseEntity<>(deletedProduct,HttpStatus.OK);
    }

    /**
     * Actualizează imaginea unui produs.
     */
    @Tag(name = "Product")
    @PutMapping("/admin/products/{productId}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> updateProductImage(@PathVariable Long productId,
                                                         @RequestParam("image") MultipartFile image) throws IOException {

        ProductDTO updatedProduct = productService.updateProductImage(productId,image);
        return new ResponseEntity<>(updatedProduct,HttpStatus.OK);
    }

    @Tag(name = "Product")
    @PostMapping("/admin/products/{productId}/gallery")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> uploadGalleryImages(@PathVariable Long productId,
                                                          @RequestParam("images") MultipartFile[] images) throws IOException {
        ProductDTO updatedProduct = productService.uploadProductGalleryImages(productId, images);
        return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    }

    @Tag(name = "Product")
    @DeleteMapping("/admin/products/{productId}/gallery/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> deleteGalleryImage(@PathVariable Long productId,
                                                         @PathVariable Long imageId) {
        ProductDTO updatedProduct = productService.deleteProductGalleryImage(productId, imageId);
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
    @PutMapping("/seller/products/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> updateProductSeller(@Valid @RequestBody ProductDTO productDTO,
                                                          @PathVariable Long productId){
        ProductDTO updatedProductDTO = productService.updateProduct(productId, productDTO);
        return new ResponseEntity<>(updatedProductDTO, HttpStatus.OK);
    }

    @DeleteMapping("/seller/products/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> deleteProductSeller(@PathVariable Long productId){
        ProductDTO deletedProduct = productService.deleteProduct(productId);
        return new ResponseEntity<>(deletedProduct, HttpStatus.OK);
    }

    @PutMapping("/seller/products/{productId}/image")
    @PreAuthorize("hasRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> updateProductImageSeller(@PathVariable Long productId,
                                                               @RequestParam("image")MultipartFile image) throws IOException {
        ProductDTO updatedProduct = productService.updateProductImage(productId, image);
        return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    }

    @PostMapping("/seller/products/{productId}/gallery")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> uploadGalleryImagesSeller(@PathVariable Long productId,
                                                                @RequestParam("images") MultipartFile[] images) throws IOException {
        ProductDTO updatedProduct = productService.uploadProductGalleryImages(productId, images);
        return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    }

    @DeleteMapping("/seller/products/{productId}/gallery/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductDTO> deleteGalleryImageSeller(@PathVariable Long productId,
                                                               @PathVariable Long imageId) {
        ProductDTO updatedProduct = productService.deleteProductGalleryImage(productId, imageId);
        return new ResponseEntity<>(updatedProduct, HttpStatus.OK);
    }
}
