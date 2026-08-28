package com.ecommerce.project.service.stock;

import com.ecommerce.project.config.TestcontainersConfiguration;
import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.StockMovement;
import com.ecommerce.project.model.StockMovementReason;
import com.ecommerce.project.payload.AddressDTO;
import com.ecommerce.project.payload.CartItemDTO;
import com.ecommerce.project.payload.GuestCheckoutRequestDTO;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.repository.StockMovementRepository;
import com.ecommerce.project.service.OrderService;
import com.ecommerce.project.service.ProductService;
import com.ecommerce.project.service.order.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §3.3: the stock ledger.
 *
 * <p>Two things are being asserted throughout. First, that each kind of stock
 * change is recorded as what it actually was — a sale is not a cancellation is
 * not a hand correction. Second, and more importantly, the invariant:
 * {@code SUM(delta) = products.quantity}, checked after every sequence. That
 * second one is what catches the failure this feature is most exposed to — a
 * stock write that quietly skips the ledger.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StockLedgerTest {

    @Autowired private OrderService orderService;
    @Autowired private ProductService productService;
    @Autowired private StockLedgerService stockLedgerService;
    @Autowired private StockReconciliationService reconciliationService;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager txManager;

    private final String tag = "sl" + Long.toUnsignedString(System.nanoTime(), 36);
    private TransactionTemplate tx;
    private Long productId;

    @BeforeEach
    void seedProductWithOpeningBalance() {
        tx = new TransactionTemplate(txManager);
        productId = tx.execute(status -> {
            Category category = new Category();
            category.setCategoryName(tag + "-cat");
            entityManager.persist(category);

            Product product = new Product();
            product.setProductName(tag + "-widget");
            product.setDescription("stock ledger fixture");
            product.setPrice(new BigDecimal("20.0"));
            product.setSpecialPrice(new BigDecimal("20.0"));
            product.setDiscount(new BigDecimal("0.0"));
            product.setQuantity(10);
            product.setCategory(category);
            entityManager.persist(product);
            entityManager.flush();

            // What V23 does for every product that predates the ledger.
            stockLedgerService.recordOpeningBalance(product.getProductId(), 10, "TEST_FIXTURE");
            return product.getProductId();
        });
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery("DELETE FROM order_items WHERE product_id IN "
                            + "(SELECT product_id FROM products WHERE product_name LIKE :t)")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM invoices WHERE order_id IN "
                            + "(SELECT id FROM orders WHERE email LIKE :t)")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM orders WHERE email LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM outbox_event WHERE payload LIKE :t")
                    .setParameter("t", "%" + tag + "%").executeUpdate();
            // stock_movement rows go with the product (ON DELETE CASCADE).
            entityManager.createNativeQuery("DELETE FROM products WHERE product_name LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM categories WHERE category_name LIKE :t")
                    .setParameter("t", tag + "%").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM addresses WHERE street = :s")
                    .setParameter("s", tag + " Street").executeUpdate();
        });
    }

    @Test
    @DisplayName("a sale is recorded against the order that caused it")
    void saleIsRecordedAgainstItsOrder() {
        OrderDTO order = placeGuestOrder(3);

        List<StockMovement> movements = movements();
        assertThat(movements).hasSize(2);

        StockMovement opening = movements.get(0);
        assertThat(opening.getReason()).isEqualTo(StockMovementReason.OPENING_BALANCE);
        assertThat(opening.getDelta()).isEqualTo(10);
        assertThat(opening.getBalanceAfter()).isEqualTo(10);

        StockMovement sale = movements.get(1);
        assertThat(sale.getReason()).isEqualTo(StockMovementReason.SALE);
        assertThat(sale.getDelta()).as("a sale consumes stock").isEqualTo(-3);
        assertThat(sale.getBalanceAfter()).isEqualTo(7);
        assertThat(sale.getRefType()).isEqualTo("ORDER");
        assertThat(sale.getRefId()).as("which order took it").isEqualTo(order.getOrderId());

        assertThat(stock()).isEqualTo(7);
        assertLedgerBalances();
    }

    @Test
    @DisplayName("cancelling an order puts the stock back, recorded as a cancellation")
    void cancellationReturnsStock() {
        OrderDTO order = placeGuestOrder(4);
        assertThat(stock()).isEqualTo(6);

        orderService.updateOrder(order.getOrderId(), OrderStatus.CANCELLED);

        StockMovement last = lastMovement();
        assertThat(last.getReason()).isEqualTo(StockMovementReason.CANCELLATION);
        assertThat(last.getDelta()).isEqualTo(4);
        assertThat(last.getBalanceAfter()).isEqualTo(10);
        assertThat(last.getRefId()).isEqualTo(order.getOrderId());

        assertThat(stock()).as("back where it started").isEqualTo(10);
        assertLedgerBalances();
    }

    @Test
    @DisplayName("a return is recorded as a return, not as a cancellation")
    void returnIsDistinctFromCancellation() {
        OrderDTO order = placeGuestOrder(2);

        // Goods that went out and came back travel a different road from goods
        // that were never dispatched.
        orderService.updateOrder(order.getOrderId(), OrderStatus.SHIPPED);
        orderService.updateOrder(order.getOrderId(), OrderStatus.DELIVERED);
        orderService.updateOrder(order.getOrderId(), OrderStatus.RETURN_REQUESTED);
        orderService.updateOrder(order.getOrderId(), OrderStatus.RETURNED);

        StockMovement last = lastMovement();
        assertThat(last.getReason()).isEqualTo(StockMovementReason.RETURN);
        assertThat(last.getDelta()).isEqualTo(2);
        assertThat(last.getBalanceAfter()).isEqualTo(10);

        assertThat(stock()).isEqualTo(10);
        assertLedgerBalances();
    }

    @Test
    @DisplayName("an admin correcting the number by hand is recorded as an adjustment")
    void adminEditIsRecordedAsAnAdjustment() {
        ProductDTO edit = new ProductDTO();
        edit.setProductName(tag + "-widget");
        edit.setDescription("stock ledger fixture");
        edit.setPrice(new BigDecimal("20.0"));
        edit.setDiscount(new BigDecimal("0.0"));
        edit.setQuantity(25);

        ProductDTO updated = productService.updateProduct(productId, edit);

        StockMovement last = lastMovement();
        assertThat(last.getReason()).isEqualTo(StockMovementReason.ADJUSTMENT);
        assertThat(last.getDelta()).as("10 -> 25").isEqualTo(15);
        assertThat(last.getBalanceAfter()).isEqualTo(25);
        assertThat(last.getRefType()).isEqualTo("ADMIN_EDIT");
        assertThat(last.getNote()).contains("10").contains("25");

        assertThat(stock()).isEqualTo(25);
        assertThat(updated.getQuantity())
                .as("the response reports the corrected figure, not the stale one").isEqualTo(25);
        assertLedgerBalances();
    }

    @Test
    @DisplayName("a rejected oversell changes nothing and records nothing")
    void rejectedOversellLeavesNoTrace() {
        int movementsBefore = movements().size();

        assertThatThrownBy(() -> placeGuestOrder(11))
                .isInstanceOf(APIException.class)
                .hasMessageContaining("Insufficient stock");

        assertThat(stock()).as("untouched").isEqualTo(10);
        assertThat(movements())
                .as("a movement that did not happen is not written down")
                .hasSize(movementsBefore);
        assertLedgerBalances();
    }

    @Test
    @DisplayName("after a full sale-and-return round trip the ledger still adds up")
    void ledgerSurvivesASequence() {
        OrderDTO first = placeGuestOrder(3);
        OrderDTO second = placeGuestOrder(2);
        orderService.updateOrder(first.getOrderId(), OrderStatus.CANCELLED);

        assertThat(stock()).as("10 - 3 - 2 + 3").isEqualTo(8);
        assertThat(movements()).extracting(StockMovement::getReason).containsExactly(
                StockMovementReason.OPENING_BALANCE,
                StockMovementReason.SALE,
                StockMovementReason.SALE,
                StockMovementReason.CANCELLATION);

        // Each entry's balance_after must be the running total, in order.
        assertThat(movements()).extracting(StockMovement::getBalanceAfter)
                .containsExactly(10, 7, 5, 8);
        assertThat(second.getOrderId()).isNotNull();
        assertLedgerBalances();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** The invariant this whole feature rests on. */
    private void assertLedgerBalances() {
        assertThat(reconciliationService.findDiscrepancies())
                .as("every movement accounted for: SUM(delta) must equal products.quantity")
                .noneMatch(d -> d.productId().equals(productId));

        int ledgerTotal = movements().stream().mapToInt(StockMovement::getDelta).sum();
        assertThat(ledgerTotal).isEqualTo(stock());
    }

    private List<StockMovement> movements() {
        return stockMovementRepository.findByProductIdOrderByIdAsc(productId);
    }

    private StockMovement lastMovement() {
        List<StockMovement> movements = movements();
        return movements.get(movements.size() - 1);
    }

    private int stock() {
        return tx.execute(status -> {
            entityManager.clear();
            return productRepository.findById(productId).orElseThrow().getQuantity();
        });
    }

    private OrderDTO placeGuestOrder(int quantity) {
        GuestCheckoutRequestDTO request = new GuestCheckoutRequestDTO();
        request.setEmail(tag + "@example.com");
        request.setPaymentMethod("CASH");   // blank pgPaymentId -> verification skipped
        request.setPgPaymentId(null);
        request.setCouponCodes(List.of());
        request.setAddress(new AddressDTO(null, tag + " Street", "Block A1",
                "Bucuresti", "Bucuresti", "Romania", "010101"));
        request.setItems(List.of(new CartItemDTO(productId, quantity)));
        return orderService.placeGuestOrder(request);
    }
}
