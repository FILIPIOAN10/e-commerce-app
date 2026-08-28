package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.ProductImportService;
import com.ecommerce.project.service.stock.StockLedgerService;
import com.ecommerce.project.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class ProductImportServiceImpl implements ProductImportService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final AuthUtil authUtil;
    private final StockLedgerService stockLedgerService;

    @Override
    @Transactional
    public String importProducts(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new APIException("CSV file is required");
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            throw new APIException("Only CSV files are allowed");
        }

        User seller = authUtil.loggedInUser();
        int created = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            if (line == null) {
                throw new APIException("CSV file is empty");
            }

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(",");
                if (cols.length < 8) {
                    throw new APIException("Invalid CSV row: " + line);
                }

                String productName = cols[0].trim();
                String description = cols[1].trim();
                double price = Double.parseDouble(cols[2].trim());
                double discount = Double.parseDouble(cols[3].trim());
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
                product.setSpecialPrice(price - (price * discount / 100.0));
                product.setQuantity(quantity);
                product.setImage(image);
                product.setTags(tags);
                product.setCategory(category);
                product.setUser(seller);
                product.setLowStockThreshold(10);

                productRepository.save(product);
                // The row already carries the imported quantity; the ledger
                // records where it came from so an imported figure is as
                // explicable as a manually entered one.
                stockLedgerService.recordOpeningBalance(
                        product.getProductId(), quantity, "CSV_IMPORT");
                created++;
            }
        } catch (NumberFormatException e) {
            throw new APIException("Invalid number format in CSV: " + e.getMessage());
        } catch (Exception e) {
            throw new APIException("Failed to import CSV: " + e.getMessage());
        }

        return "Imported " + created + " products";
    }
}
