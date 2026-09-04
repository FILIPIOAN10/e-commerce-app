package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.*;
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
import com.ecommerce.project.service.stock.StockLedgerService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.cache.TransactionAwareCacheEvictor;
import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.util.ProductMapper;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.util.ProductSpecifications;
import com.ecommerce.project.util.SortWhitelist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.stream.Collectors;
import com.ecommerce.project.service.pricing.Money;


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
    private final TransactionAwareCacheEvictor cacheEvictor;

    private static final List<String> PRODUCT_CACHE_NAMES = List.of(
            "publicProducts", "categoryProducts", "productSearch", "adminProducts", "sellerProducts");

    private final AuthUtil authUtil;
    private final AdminAuditLogService adminAuditLogService;
    private final StockLedgerService stockLedgerService;



    @Override
    @Transactional
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
        product.setPrice(toCents(product.getPrice()));
        product.setDiscount(toCents(product.getDiscount()));
        product.setSpecialPrice(calculateSpecialPrice(product.getPrice(), product.getDiscount()));

        Product savedProduct = productRepository.save(product);

        // The insert already carries the starting quantity, so the ledger records
        // it rather than applying it — see StockLedgerService.recordOpeningBalance.
        stockLedgerService.recordOpeningBalance(
                savedProduct.getProductId(), savedProduct.getQuantity(), "PRODUCT_CREATE");

        cacheEvictor.evictAllAfterCommit(PRODUCT_CACHE_NAMES);

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
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_PRODUCTS_BY, SortWhitelist.PRODUCT);
        User user = authUtil.loggedInUser();
        Page<Product> pageProducts = productRepository.findByUser(user, pageDetails);

        return productMapper.buildProductResponse(pageProducts);
    }

    /** Page size for the reindex sweep — bounds how many Product entities are held at once. */
    private static final int REINDEX_PAGE_SIZE = 200;

    @Override
    public int reindexProductSearch() {
        // Not @Transactional on purpose: each page is its own repository call and
        // its own short unit of work, so once the page list goes out of scope its
        // entities are collectable. A single unbounded findAll() over the whole
        // catalogue was an OutOfMemoryError waiting to happen.
        int indexed = 0;
        int pageNumber = 0;
        Page<Product> slice;
        do {
            slice = productRepository.findAll(PageRequest.of(pageNumber, REINDEX_PAGE_SIZE, Sort.by("productId")));
            slice.getContent().forEach(productSemanticSearchService::indexProduct);
            indexed += slice.getNumberOfElements();
            log.info("Reindex progress: {} / {} products", indexed, slice.getTotalElements());
            pageNumber++;
        } while (slice.hasNext());
        return indexed;
    }

    @Override
    @Transactional
    public ProductDTO updateProduct(Long productId, ProductDTO productDTO) {

        // Get the existing product from DB
        Product productFromDB = loadProductForWrite(productId);

        BigDecimal oldPrice = productFromDB.getPrice();
        BigDecimal oldSpecialPrice = productFromDB.getSpecialPrice();
        int oldQuantity = productFromDB.getQuantity() == null ? 0 : productFromDB.getQuantity();

        // Update the product info with the one in the request body
        //automatically converts (maps) the productDTO object into a Product object.
        Product product = modelMapper.map(productDTO, Product.class);
        productFromDB.setProductName(product.getProductName());
        productFromDB.setDescription(product.getDescription());
        // Quantity is deliberately NOT set here: a stock change has to go
        // through the ledger, or the ledger stops explaining the number.
        productFromDB.setDiscount(toCents(product.getDiscount()));
        productFromDB.setPrice(toCents(product.getPrice()));
        productFromDB.setSpecialPrice(calculateSpecialPrice(product.getPrice(),product.getDiscount()));
        productFromDB.setTags(product.getTags());
        productFromDB.setImage(productDTO.getImage() != null && !productDTO.getImage().isBlank()
                ? productDTO.getImage() : productFromDB.getImage());

        if (oldPrice.compareTo(productFromDB.getPrice()) != 0
                || oldSpecialPrice.compareTo(productFromDB.getSpecialPrice()) != 0) {
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


        // Save to database. Flushed here so the ledger's raw UPDATE below lands
        // on top of these changes rather than racing them within the same
        // transaction.
        Product savedProduct = productRepository.saveAndFlush(productFromDB);

        int newQuantity = product.getQuantity() == null ? oldQuantity : product.getQuantity();
        int quantityDelta = newQuantity - oldQuantity;
        Integer balanceAfterAdjustment = null;
        if (quantityDelta != 0) {
            balanceAfterAdjustment = stockLedgerService.applyAndRecord(
                    productId, quantityDelta, StockMovementReason.ADJUSTMENT,
                    "ADMIN_EDIT", productId,
                    "Stock corrected from " + oldQuantity + " to " + newQuantity)
                    .getBalanceAfter();
        }

        cacheEvictor.evictAllAfterCommit(PRODUCT_CACHE_NAMES);
        cacheEvictor.evictKeyAfterCommit("product", productId);

        List<Cart> carts = cartRepository.findCartsByProductId(productId);

        List<CartDTO> cartDTOS = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
            cartDTO.setProducts(productMapper.mapCartItemsToProductDTOs(cart.getCartItems()));
            return cartDTO;
        }).collect(Collectors.toList());

        cartDTOS.forEach(cart -> cartService.updateProductsInCarts(cart.getCartId(),productId));
        productSemanticSearchService.indexProduct(savedProduct);

        ProductDTO response = productMapper.mapProductToDTO(savedProduct);
        if (balanceAfterAdjustment != null) {
            // The ledger's update went straight to the row, so the loaded entity
            // still holds the pre-adjustment figure. Correct the response rather
            // than the entity: writing it back would dirty a row whose version
            // the ledger has already moved on, and turn a successful edit into a
            // spurious 409 at commit.
            response.setQuantity(balanceAfterAdjustment);
        }
        return response;
    }

    @Override
    @Transactional
    public ProductDTO deleteProduct(Long productId) {

        Product product = loadProductForWrite(productId);

        productImageService.deleteProductImages(product);

        List<Cart> carts = cartRepository.findCartsByProductId(productId);
        carts.forEach(cart -> cartService.deleteProductFromCart(cart.getCartId(),productId));

        productRepository.delete(product);

        cacheEvictor.evictAllAfterCommit(PRODUCT_CACHE_NAMES);
        cacheEvictor.evictKeyAfterCommit("product", productId);

        productSemanticSearchService.deleteProduct(productId);
        return productMapper.mapProductToDTO(product);
    }


    /**
     * A price arrives from JSON with whatever scale the client wrote — {@code 120},
     * {@code 120.0}, {@code 120.004}. Money pins it to the cent here, so the entity
     * holds in memory the same figure the {@code NUMERIC(12,2)} column would give
     * back, and nothing downstream ends up comparing 120.0 with 120.00.
     */
    private static BigDecimal toCents(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : Money.of(amount).toBigDecimal();
    }

    /**
     * What the customer is actually charged. Taking the complement as a
     * percentage of the list price keeps it exact: 25% off 84.99 is 63.74, not
     * 63.742499999999996 rounded at display time.
     */
    private BigDecimal calculateSpecialPrice(BigDecimal price, BigDecimal discount) {
        BigDecimal percent = discount == null ? BigDecimal.ZERO : discount;
        return Money.of(price).percentage(100.0 - percent.doubleValue()).toBigDecimal();
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "product", key = "#productId")
    public ProductDTO getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        return productMapper.mapProductToDTO(product);
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
