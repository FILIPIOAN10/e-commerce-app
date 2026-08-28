package com.ecommerce.project.model;

/**
 * Why stock moved. The vocabulary is deliberately small — every movement must
 * fit one of these, and a change that fits none is a change that has not been
 * thought about.
 */
public enum StockMovementReason {

    /** Sold to a customer. Always negative. */
    SALE,

    /** New stock received from a supplier. Always positive. */
    RESTOCK,

    /** A customer returned goods and they went back on the shelf. Positive. */
    RETURN,

    /** An order was cancelled before dispatch and its hold was released. Positive. */
    CANCELLATION,

    /** A human corrected the number — a stock count, damage, theft. Either sign. */
    ADJUSTMENT,

    /**
     * The quantity a product started with: its creation, an import, or the
     * balance carried in when this ledger was introduced. Positive.
     */
    OPENING_BALANCE
}
