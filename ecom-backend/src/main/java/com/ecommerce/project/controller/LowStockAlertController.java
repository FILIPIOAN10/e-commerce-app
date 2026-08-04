package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LowStockAlertController {

    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;

    @Tag(name = "Low Stock Alerts")
    @GetMapping("/admin/low-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> getLowStockProducts(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        Page<Product> pageProducts = productRepository.findLowStockProducts(pageDetails);

        return buildResponse(pageProducts);
    }

    @Tag(name = "Low Stock Alerts")
    @GetMapping("/seller/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<ProductResponse> getLowStockProductsForSeller(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        var seller = authUtil.loggedInUser();
        Page<Product> pageProducts = productRepository.findLowStockProductsBySeller(seller, pageDetails);

        return buildResponse(pageProducts);
    }

    @Tag(name = "Low Stock Alerts")
    @GetMapping("/low-stock/count")
    @PreAuthorize("hasAnyRole('ADMIN','SELLER')")
    public ResponseEntity<Long> getLowStockCount() {
        long count;
        boolean isAdmin = authUtil.loggedInUser().getRoles().stream()
                .anyMatch(r -> r.getRoleName().name().equals("ROLE_ADMIN"));

        if (isAdmin) {
            count = productRepository.findLowStockProducts(PageRequest.of(0, 1)).getTotalElements();
        } else {
            var seller = authUtil.loggedInUser();
            count = productRepository.findLowStockProductsBySeller(seller, PageRequest.of(0, 1)).getTotalElements();
        }
        return new ResponseEntity<>(count, HttpStatus.OK);
    }

    private ResponseEntity<ProductResponse> buildResponse(Page<Product> pageProducts) {
        var productDTOs = pageProducts.getContent().stream()
                .map(p -> modelMapper.map(p, ProductDTO.class))
                .toList();

        ProductResponse response = new ProductResponse();
        response.setContent(productDTOs);
        response.setPageNumber(pageProducts.getNumber());
        response.setPageSize(pageProducts.getSize());
        response.setTotalElements(pageProducts.getTotalElements());
        response.setTotalPages(pageProducts.getTotalPages());
        response.setLastPage(pageProducts.isLast());

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
