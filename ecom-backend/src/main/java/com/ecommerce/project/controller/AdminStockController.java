package com.ecommerce.project.controller;

import com.ecommerce.project.payload.PaginationParams;
import com.ecommerce.project.payload.StockMovementDTO;
import com.ecommerce.project.repository.StockMovementRepository;
import com.ecommerce.project.service.stock.StockReconciliationService;
import com.ecommerce.project.util.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The answer to "why is this product's stock the number it is". Admin-only —
 * {@code /api/admin/**} is gated on ROLE_ADMIN by the security config.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Stock")
public class AdminStockController extends BaseController {

    private final StockMovementRepository stockMovementRepository;
    private final StockReconciliationService reconciliationService;

    @Operation(summary = "Stock movement history for a product",
            description = "Every change to this product's stock, newest first, with the balance each one left.")
    @GetMapping("/products/{productId}/stock-movements")
    public ResponseEntity<Page<StockMovementDTO>> stockMovements(@PathVariable Long productId,
                                                                 @ModelAttribute PaginationParams params) {
        Page<StockMovementDTO> page = stockMovementRepository
                .findByProductIdOrderByCreatedAtDescIdDesc(productId,
                        PaginationUtil.buildPageable(params.getPageNumber(), params.getPageSize(),
                                "createdAt", "desc"))
                .map(StockMovementDTO::from);
        return ok(page);
    }

    @Operation(summary = "Products whose stock does not match their ledger",
            description = "Empty in a healthy system. A row here means a stock write bypassed the ledger.")
    @GetMapping("/stock/discrepancies")
    public ResponseEntity<List<StockReconciliationService.Discrepancy>> discrepancies() {
        return ok(reconciliationService.findDiscrepancies());
    }
}
