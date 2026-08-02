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
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final AuthUtil authUtil;
    private final ModelMapper modelMapper;

    @Override
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
    public ProductResponse getWishlist(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        User user = authUtil.loggedInUser();

        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        Page<Wishlist> wishlistPage = wishlistRepository.findByUser(user, pageDetails);

        List<ProductDTO> productDTOs = wishlistPage.getContent().stream()
                .map(w -> modelMapper.map(w.getProduct(), ProductDTO.class))
                .toList();

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
