package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstants;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.payload.StockMovementDTO;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.StockMovementRepository;
import com.ecommerce.project.service.stock.StockReconciliationService;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.ecommerce.project.util.SortWhitelist;

/**
 * The state of stock: what is running out (low-stock alerts, for admins and
 * sellers) and why a product's stock is the number it is (the movement ledger
 * and its reconciliation report, admin only).
 *
 * <p>The {@code /admin/**} endpoints here carry no {@code @PreAuthorize} of their
 * own — they are gated on ROLE_ADMIN by the security config, which matches on the
 * URL. Their paths must keep the {@code /admin} prefix.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InventoryController extends BaseController {

    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;
    private final StockMovementRepository stockMovementRepository;
    private final StockReconciliationService reconciliationService;

    @Tag(name = "Low Stock Alerts")
    @GetMapping("/admin/low-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> getLowStockProducts(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_PRODUCTS_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder
    ) {
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_PRODUCTS_BY, SortWhitelist.PRODUCT);

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
        Pageable pageDetails = PaginationUtil.buildPageable(pageNumber, pageSize, sortBy, sortOrder,
                AppConstants.SORT_PRODUCTS_BY, SortWhitelist.PRODUCT);

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

    @Tag(name = "Low Stock Alerts")
    @GetMapping("/admin/low-stock/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getLowStockSummary() {
        Pageable pageDetails = PageRequest.of(0, 5, Sort.by("quantity").ascending());
        Page<Product> pageProducts = productRepository.findLowStockProducts(pageDetails);
        List<ProductDTO> productDTOs = pageProducts.getContent().stream()
                .map(p -> modelMapper.map(p, ProductDTO.class))
                .collect(Collectors.toList());
        Map<String, Object> summary = Map.of(
                "count", pageProducts.getTotalElements(),
                "products", productDTOs
        );
        return new ResponseEntity<>(summary, HttpStatus.OK);
    }

    @Tag(name = "Stock")
    @Operation(summary = "Stock movement history for a product",
            description = "Every change to this product's stock, newest first, with the balance each one left.")
    @GetMapping("/admin/products/{productId}/stock-movements")
    public ResponseEntity<Page<StockMovementDTO>> stockMovements(@PathVariable Long productId,
                                                                 @ModelAttribute PaginationParams params) {
        Page<StockMovementDTO> page = stockMovementRepository
                .findByProductIdOrderByCreatedAtDescIdDesc(productId,
                        PaginationUtil.buildPageable(params.getPageNumber(), params.getPageSize(),
                                "createdAt", "desc", "createdAt", SortWhitelist.STOCK_MOVEMENT))
                .map(StockMovementDTO::from);
        return ok(page);
    }

    @Tag(name = "Stock")
    @Operation(summary = "Products whose stock does not match their ledger",
            description = "Empty in a healthy system. A row here means a stock write bypassed the ledger.")
    @GetMapping("/admin/stock/discrepancies")
    public ResponseEntity<List<StockReconciliationService.Discrepancy>> discrepancies() {
        return ok(reconciliationService.findDiscrepancies());
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
