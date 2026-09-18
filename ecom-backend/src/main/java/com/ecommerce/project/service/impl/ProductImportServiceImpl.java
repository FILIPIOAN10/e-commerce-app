package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.ProductImportService;
import com.ecommerce.project.service.pricing.Money;
import com.ecommerce.project.service.stock.StockLedgerService;
import com.ecommerce.project.util.AuthUtil;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bulk product upload from a seller-supplied CSV.
 *
 * <p>Two things about the previous version made this endpoint unfit to ship:
 * <ol>
 *   <li>{@code line.split(",")} broke every RFC 4180 CSV that had a comma inside
 *       a quoted description or category name. The row silently shifted columns —
 *       the price cell would parse the wrong text and either throw or store
 *       nonsense — so a real spreadsheet export could not round-trip.
 *   <li>The whole loop ran inside a single {@code @Transactional}. A 10k-row
 *       upload held one Hikari connection open for the full parse plus N inserts
 *       plus N ledger inserts, starving the outbox dispatcher and subscription
 *       renewal jobs of connections. One bad row on line 9,999 rolled back the
 *       other 9,998.
 * </ol>
 *
 * <p>Parsing now goes through opencsv (RFC 4180: quoted fields, embedded commas,
 * escaped quotes). Each row is persisted in its own {@code REQUIRES_NEW}
 * transaction, so a bad row rejects only itself and the outer method holds no
 * DB connection while the file is being parsed. The response reports how many
 * rows succeeded, and lists the line numbers that failed with the reason.
 */
@Service
@RequiredArgsConstructor
public class ProductImportServiceImpl implements ProductImportService {

    private static final int EXPECTED_COLUMNS = 8;
    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 10;
    private static final int MAX_FAILURES_REPORTED = 20;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuthUtil authUtil;
    private final StockLedgerService stockLedgerService;

    /**
     * Self-reference so per-row calls go through the Spring proxy — without it,
     * {@code REQUIRES_NEW} on {@link #importRow} would be silently ignored and
     * every row would join a non-existent outer transaction.
     */
    private final @Lazy ProductImportService self;

    @Override
    public String importProducts(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new APIException("CSV file is required");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new APIException("Only CSV files are allowed");
        }

        // The current user has to be resolved on the request thread — the per-row
        // helpers below only see the user id we pass in.
        Long sellerId = authUtil.loggedInUserId();

        int created = 0;
        List<String> failures = new ArrayList<>();
        int lineNumber = 1; // header

        try (Reader in = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(in)) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new APIException("CSV file is empty");
            }

            String[] cols;
            while ((cols = reader.readNext()) != null) {
                lineNumber++;
                if (isBlankRow(cols)) continue;
                if (cols.length < EXPECTED_COLUMNS) {
                    recordFailure(failures, lineNumber,
                            "row has " + cols.length + " columns, expected " + EXPECTED_COLUMNS);
                    continue;
                }
                try {
                    self.importRow(cols, sellerId);
                    created++;
                } catch (RuntimeException e) {
                    recordFailure(failures, lineNumber, describe(e));
                }
            }
        } catch (IOException | CsvValidationException e) {
            throw new APIException("Failed to read CSV: " + e.getMessage());
        }

        return summary(created, failures);
    }

    /**
     * Persists one CSV row in its own transaction. Public and Spring-proxied so
     * {@link Propagation#REQUIRES_NEW} takes effect; call via {@link #self}.
     *
     * <p>The seller is passed by id and re-attached inside this transaction —
     * handing a User entity across transaction boundaries would leave the
     * association detached.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void importRow(String[] cols, Long sellerId) {
        String productName = cols[0].trim();
        String description = cols[1].trim();
        // The CSV carries decimal text; parse it into an exact decimal
        // rather than through a double that cannot hold most cent values.
        BigDecimal price = new BigDecimal(cols[2].trim());
        BigDecimal discount = new BigDecimal(cols[3].trim());
        int quantity = Integer.parseInt(cols[4].trim());
        String image = cols[5].trim();
        String tags = cols[6].trim();
        String categoryName = cols[7].trim();

        Category category = categoryRepository.findByCategoryName(categoryName);
        if (category == null) {
            throw new APIException("Category not found: " + categoryName);
        }

        Product product = new Product();
        product.setProductName(productName);
        product.setDescription(description);
        product.setPrice(price);
        product.setDiscount(discount);
        product.setSpecialPrice(Money.of(price).percentage(ONE_HUNDRED.subtract(discount)).toBigDecimal());
        product.setQuantity(quantity);
        product.setImage(image);
        product.setTags(tags);
        product.setCategory(category);
        product.setUser(new User());
        product.getUser().setUserId(sellerId);
        product.setLowStockThreshold(DEFAULT_LOW_STOCK_THRESHOLD);

        productRepository.save(product);
        // The row already carries the imported quantity; the ledger
        // records where it came from so an imported figure is as
        // explicable as a manually entered one.
        stockLedgerService.recordOpeningBalance(
                product.getProductId(), quantity, "CSV_IMPORT");
    }

    private static boolean isBlankRow(String[] cols) {
        for (String c : cols) {
            if (c != null && !c.trim().isEmpty()) return false;
        }
        return true;
    }

    private static void recordFailure(List<String> failures, int line, String reason) {
        if (failures.size() < MAX_FAILURES_REPORTED) {
            failures.add("line " + line + ": " + reason);
        } else if (failures.size() == MAX_FAILURES_REPORTED) {
            failures.add("… further failures truncated");
        }
    }

    private static String describe(RuntimeException e) {
        if (e instanceof NumberFormatException) return "invalid number: " + e.getMessage();
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    private static String summary(int created, List<String> failures) {
        if (failures.isEmpty()) {
            return "Imported " + created + " products";
        }
        return "Imported " + created + " products; "
             + failures.size() + " row(s) failed: "
             + String.join("; ", failures);
    }
}
