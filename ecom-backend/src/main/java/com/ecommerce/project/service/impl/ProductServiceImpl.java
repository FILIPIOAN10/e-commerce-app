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
import com.ecommerce.project.service.CartService;
import com.ecommerce.project.service.ProductImageService;
import com.ecommerce.project.service.ProductSemanticSearchService;
import com.ecommerce.project.service.ProductService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.ProductMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
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



    @Override
    @Caching(evict = {
            @CacheEvict(value = "publicProducts", allEntries = true),
            @CacheEvict(value = "categoryProducts", allEntries = true),
            @CacheEvict(value = "productSearch", allEntries = true),
            @CacheEvict(value = "adminProducts", allEntries = true),
            @CacheEvict(value = "sellerProducts", allEntries = true)
    })
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
    @Cacheable(
            value = "publicProducts",
            key = "#pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder + '/' + (#keyword == null ?'':#keyword.toLowerCase()) + '/' + (#category == null ? '': #category.toLowerCase())"
    )
    public ProductResponse getAllProducts(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String keyword, String category) {

        //Implement pagination
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
        if(keyword !=null && !keyword.isEmpty()) {
            spec = spec.and((root, query, cb) -> {
                String likeKeyword = "%" +keyword.toLowerCase() + "%";
                return cb.or(
                        cb.like(cb.lower(root.get("productName")),likeKeyword),
                        cb.like(cb.lower(root.get("description")),likeKeyword),
                        cb.like(cb.lower(root.get("tags")),likeKeyword)
                );
            });
        }

        if(category !=null && !category.isEmpty()) {
            spec = spec.and((root,query,cb) -> cb.like(root.get("category").get("categoryName"), category));
        }



        Page<Product> pageProducts = productRepository.findAll(spec, pageDetails);
        return productMapper.buildProductResponse(pageProducts);
    }



    @Override
    @Cacheable(
            value = "adminProducts",
            key = "#pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder"
    )
    public ProductResponse getAllProductsForAdmin(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Product> pageProducts = productRepository.findAll(pageDetails);

        return productMapper.buildProductResponse(pageProducts);
    }

    @Override
    @Cacheable(
            value = "sellerProducts",
            key = "@authUtil.loggedInUserId() + '/' + #pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder"
    )
    public ProductResponse getAllProductsForSeller(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
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
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#productId"),
            @CacheEvict(value = "publicProducts", allEntries = true),
            @CacheEvict(value = "categoryProducts", allEntries = true),
            @CacheEvict(value = "productSearch", allEntries = true),
            @CacheEvict(value = "adminProducts", allEntries = true),
            @CacheEvict(value = "sellerProducts", allEntries = true)
    })
    public ProductDTO updateProduct(Long productId, ProductDTO productDTO) {

        // Get the existing product from DB
        Product productFromDB = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
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


        // Save to database
        Product savedProduct = productRepository.save(productFromDB);

        List<Cart> carts = cartRepository.findCartsByProductId(productId);

        List<CartDTO> cartDTOS = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
            List<ProductDTO> products = cart.getCartItems().stream()
                    .map(p -> modelMapper.map(p.getProduct(), ProductDTO.class))
                    .collect(Collectors.toList());
            cartDTO.setProducts(products);
            return cartDTO;
        }).collect(Collectors.toList());

        cartDTOS.forEach(cart -> cartService.updateProductsInCarts(cart.getCartId(),productId));
        productSemanticSearchService.indexProduct(savedProduct);
        return productMapper.mapProductToDTO(savedProduct);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "product", key = "#productId"),
            @CacheEvict(value = "publicProducts", allEntries = true),
            @CacheEvict(value = "categoryProducts", allEntries = true),
            @CacheEvict(value = "productSearch", allEntries = true),
            @CacheEvict(value = "adminProducts", allEntries = true),
            @CacheEvict(value = "sellerProducts", allEntries = true)
    })
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
    @Cacheable(value = "product", key = "#productId")
    public ProductDTO getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        return productMapper.mapProductToDTO(product);
    }

}
