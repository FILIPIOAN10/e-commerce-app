package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.AdminAuditLogService;
import com.ecommerce.project.service.CartService;
import com.ecommerce.project.service.ProductImageService;
import com.ecommerce.project.service.ProductSemanticSearchService;
import com.ecommerce.project.service.ProductService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.cache.EvictProductCaches;
import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.util.ProductMapper;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.util.ProductSpecifications;
import com.ecommerce.project.util.SortWhitelist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ModelMapper modelMapper;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final ProductImageService productImageService;
    private final ProductMapper productMapper;
    private final ProductSemanticSearchService productSemanticSearchService;

    private final AuthUtil authUtil;
    private final AdminAuditLogService adminAuditLogService;



    @Override
    @EvictProductCaches
    public ProductDTO addProduct(Long categoryId, ProductDTO productDTO) {
        // 1. Găsim categoria sau aruncăm excepție
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "categoryId", categoryId));

        // 2. Verificare optimizată direct în baza de date (fără buclă for/încărcare în memorie)
        boolean productExists = productRepository.existsByCategoryAndProductName(category, productDTO.getProductName());

        if (productExists) {
            throw new APIException("Product already exists !!!");
        }

        // 3. Mapare și salvare produs nou
        Product product = modelMapper.map(productDTO, Product.class);
        product.setImage(productDTO.getImage() != null && !productDTO.getImage().isBlank() ? productDTO.getImage() : "cal.png");
        product.setTags(productDTO.getTags());
        product.setCategory(category);
        product.setUser(authUtil.loggedInUser());
        product.setSpecialPrice(calculateSpecialPrice(product.getPrice(), product.getDiscount()));

        Product savedProduct = productRepository.save(product);

        // 4. Indexare semantică și returnare DTO
        productSemanticSearchService.indexProduct(savedProduct);
        return modelMapper.map(savedProduct, ProductDTO.class);
    }



    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "publicProducts",
            key = "#pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder + '/' + (#keyword == null ?'':#keyword.toLowerCase()) + '/' + (#category == null ? '': #category.toLowerCase())"
    )
    public ProductResponse getAllProducts(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String keyword, String category) {

        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_PRODUCTS_BY, SortWhitelist.PRODUCT);

        Specification<Product> spec = ProductSpecifications.withKeyword(keyword)
                .and(ProductSpecifications.withCategory(category));



        Page<Product> pageProducts = productRepository.findAll(spec, pageDetails);
        return productMapper.buildProductResponse(pageProducts);
    }



    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "adminProducts",
            key = "#pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder"
    )
    public ProductResponse getAllProductsForAdmin(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_PRODUCTS_BY, SortWhitelist.PRODUCT);
        Page<Product> pageProducts = productRepository.findAll(pageDetails);

        return productMapper.buildProductResponse(pageProducts);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "sellerProducts",
            key = "@authUtil.loggedInUserId() + '/' + #pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder"
    )
    public ProductResponse getAllProductsForSeller(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder, AppConstants.SORT_PRODUCTS_BY);
        User user = authUtil.loggedInUser();
        Page<Product> pageProducts = productRepository.findByUser(user, pageDetails);

        return productMapper.buildProductResponse(pageProducts);
    }

    @Override
    public int reindexProductSearch() {
        List<Product> products = productRepository.findAll();
        products.forEach(productSemanticSearchService::indexProduct);
        return products.size();
    }

    @Override
    @Transactional
    @EvictProductCaches
    @CacheEvict(value = "product", key = "#productId")
    public ProductDTO updateProduct(Long productId, ProductDTO productDTO) {

        // Get the existing product from DB
        Product productFromDB = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        double oldPrice = productFromDB.getPrice();
        double oldSpecialPrice = productFromDB.getSpecialPrice();

        // Update the product info with the one in the request body
        //automatically converts (maps) the productDTO object into a Product object.
        Product product = modelMapper.map(productDTO, Product.class);
        productFromDB.setProductName(product.getProductName());
        productFromDB.setDescription(product.getDescription());
        productFromDB.setQuantity(product.getQuantity());
        productFromDB.setDiscount(product.getDiscount());
        productFromDB.setPrice(product.getPrice());
        productFromDB.setSpecialPrice(calculateSpecialPrice(product.getPrice(),product.getDiscount()));
        productFromDB.setTags(product.getTags());
        productFromDB.setImage(productDTO.getImage() != null && !productDTO.getImage().isBlank()
                ? productDTO.getImage() : productFromDB.getImage());

        if (Double.compare(oldPrice, productFromDB.getPrice()) != 0
                || Double.compare(oldSpecialPrice, productFromDB.getSpecialPrice()) != 0) {
            User admin = authUtil.loggedInUser();
            adminAuditLogService.logPriceChange(
                    admin.getUserId(),
                    admin.getUserName(),
                    productId,
                    oldPrice,
                    productFromDB.getPrice(),
                    oldSpecialPrice,
                    productFromDB.getSpecialPrice()
            );
        }


        // Save to database
        Product savedProduct = productRepository.save(productFromDB);

        List<Cart> carts = cartRepository.findCartsByProductId(productId);

        List<CartDTO> cartDTOS = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
            cartDTO.setProducts(productMapper.mapCartItemsToProductDTOs(cart.getCartItems()));
            return cartDTO;
        }).collect(Collectors.toList());

        cartDTOS.forEach(cart -> cartService.updateProductsInCarts(cart.getCartId(),productId));
        productSemanticSearchService.indexProduct(savedProduct);
        return productMapper.mapProductToDTO(savedProduct);
    }

    @Override
    @Transactional
    @EvictProductCaches
    @CacheEvict(value = "product", key = "#productId")
    public ProductDTO deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        productImageService.deleteProductImages(product);

        List<Cart> carts = cartRepository.findCartsByProductId(productId);
        carts.forEach(cart -> cartService.deleteProductFromCart(cart.getCartId(),productId));

        productRepository.delete(product);
        productSemanticSearchService.deleteProduct(productId);
        return productMapper.mapProductToDTO(product);
    }


    private double calculateSpecialPrice(double price, double discount) {
        return price - ((discount * 0.01) * price);
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "product", key = "#productId")
    public ProductDTO getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        return productMapper.mapProductToDTO(product);
    }

}
