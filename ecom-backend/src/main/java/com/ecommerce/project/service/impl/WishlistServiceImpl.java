package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.model.Wishlist;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.WishlistRepository;
import com.ecommerce.project.service.WishlistService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.PaginationUtil;
import com.ecommerce.project.util.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.ecommerce.project.util.SortWhitelist;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final AuthUtil authUtil;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public String addToWishlist(Long productId) {
        User user = authUtil.loggedInUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (wishlistRepository.existsByUserAndProduct(user, product)) {
            throw new APIException("Product already in wishlist");
        }

        Wishlist wishlist = Wishlist.builder()
                .user(user)
                .product(product)
                .build();

        wishlistRepository.save(wishlist);
        return "Product added to wishlist successfully";
    }

    @Override
    @Transactional
    public String removeFromWishlist(Long productId) {
        User user = authUtil.loggedInUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (!wishlistRepository.existsByUserAndProduct(user, product)) {
            throw new APIException("Product not found in wishlist");
        }

        wishlistRepository.deleteByUserAndProduct(user, product);
        return "Product removed from wishlist successfully";
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getWishlist(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();

        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                "createdAt", SortWhitelist.WISHLIST);

        Page<Wishlist> wishlistPage = wishlistRepository.findByUser(user, pageDetails);

        // Batch mapper: two review-aggregate queries for the whole page instead
        // of two per row.
        List<ProductDTO> productDTOs = productMapper.mapProductsToDTOs(
                wishlistPage.getContent().stream().map(Wishlist::getProduct).toList());

        ProductResponse response = new ProductResponse();
        response.setContent(productDTOs);
        response.setPageNumber(wishlistPage.getNumber());
        response.setPageSize(wishlistPage.getSize());
        response.setTotalElements(wishlistPage.getTotalElements());
        response.setTotalPages(wishlistPage.getTotalPages());
        response.setLastPage(wishlistPage.isLast());
        return response;
    }
}
