package com.ecommerce.project.util;

import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    private final ModelMapper modelMapper;

    @Value("${image.base.url}")
    private String imageBaseUrl;

    public ProductMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public ProductDTO mapProductToDTO(Product product) {
        return mapProductToDTO(product, Map.of(), Map.of());
    }

    /**
     * The rating maps are now only an override: since V22 the average and count
     * live on the product itself, so a caller that has nothing to supply pays
     * nothing. Before that, an empty map here meant two extra queries
     * <em>per product</em> — the N+1 that the denormalised columns exist to kill.
     */
    public ProductDTO mapProductToDTO(Product product, Map<Long, Double> avgRatings, Map<Long, Long> reviewCounts) {
        ProductDTO productDTO = modelMapper.map(product, ProductDTO.class);
        productDTO.setImage(constructImageUrl(product.getImage()));
        productDTO.setTags(product.getTags());
        List<String> imageUrls = product.getProductImages() != null
                ? product.getProductImages().stream()
                    .map(img -> constructImageUrl(img.getImageName()))
                    .toList()
                : List.of();
        productDTO.setImages(imageUrls);

        Long productId = product.getProductId();
        Double avgRating = avgRatings.getOrDefault(productId, product.getAverageRating());
        Long reviewCount = reviewCounts.getOrDefault(productId, (long) product.getReviewCount());
        productDTO.setAverageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        productDTO.setReviewCount(reviewCount);
        productDTO.setCategoryName(product.getCategory() != null ? product.getCategory().getCategoryName() : null);
        productDTO.setCategoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null);

        return productDTO;
    }

    public List<ProductDTO> mapProductsToDTOs(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        return products.stream()
                .map(this::mapProductToDTO)
                .toList();
    }

    public ProductResponse buildProductResponse(Page<Product> pageProducts) {
        List<ProductDTO> productDTOS = mapProductsToDTOs(pageProducts.getContent());
        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }

    public ProductResponse buildProductResponse(List<Product> products, Integer pageNumber, Integer pageSize) {
        int safePageNumber = pageNumber == null ? 0 : Math.max(pageNumber, 0);
        int safePageSize = pageSize == null ? 10 : Math.max(pageSize, 1);
        int fromIndex = Math.min(safePageNumber * safePageSize, products.size());
        int toIndex = Math.min(fromIndex + safePageSize, products.size());
        List<ProductDTO> productDTOS = mapProductsToDTOs(products.subList(fromIndex, toIndex));
        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(safePageNumber);
        productResponse.setPageSize(safePageSize);
        productResponse.setTotalElements((long) products.size());
        productResponse.setTotalPages((int) Math.ceil((double) products.size() / safePageSize));
        productResponse.setLastPage(toIndex >= products.size());
        return productResponse;
    }

    public List<ProductDTO> mapCartItemsToProductDTOs(List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return List.of();
        }
        return cartItems.stream()
                .map(item -> {
                    ProductDTO dto = modelMapper.map(item.getProduct(), ProductDTO.class);
                    dto.setQuantity(item.getQuantity());
                    dto.setCartItemId(item.getCartItemId());
                    dto.setSavedForLater(Boolean.TRUE.equals(item.getSavedForLater()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public String constructImageUrl(String imageName) {
        if (imageName != null && (imageName.startsWith("http://") || imageName.startsWith("https://"))) {
            return imageName;
        }
        return imageBaseUrl.endsWith("/") ? imageBaseUrl + imageName : imageBaseUrl + "/" + imageName;
    }
}
