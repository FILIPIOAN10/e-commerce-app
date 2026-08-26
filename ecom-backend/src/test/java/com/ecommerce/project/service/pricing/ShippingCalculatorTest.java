package com.ecommerce.project.service.pricing;

import com.ecommerce.project.model.Address;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShippingCalculator")
class ShippingCalculatorTest {

    private final ShippingCalculator calculator = new ShippingCalculator();

    private Address address(String country) {
        Address address = new Address();
        address.setCountry(country);
        return address;
    }

    @Test
    @DisplayName("free shipping when chargeable total is at or above the threshold")
    void freeShippingAtThreshold() {
        assertEquals(0.0, calculator.calculate(address("US"), 100.0));
        assertEquals(0.0, calculator.calculate(address("US"), 150.0));
    }

    @Test
    @DisplayName("domestic shipping is 3.0 when chargeable total is below threshold")
    void domesticShippingBelowThreshold() {
        assertEquals(3.0, calculator.calculate(address("RO"), 84.0));
        assertEquals(3.0, calculator.calculate(address("Romania"), 50.0));
    }

    @Test
    @DisplayName("international shipping is 5.0 when chargeable total is below threshold")
    void internationalShippingBelowThreshold() {
        assertEquals(5.0, calculator.calculate(address("US"), 84.0));
        assertEquals(5.0, calculator.calculate(address("Germany"), 20.0));
    }

    @Test
    @DisplayName("shipping is calculated on the post-discount total: 105 - 20% = 84 -> 5.0")
    void shippingOnPostDiscountTotal() {
        double subtotal = 105.0;
        double discount = subtotal * 0.20;
        double totalAfterDiscount = subtotal - discount;

        double shippingCost = calculator.calculate(address("US"), totalAfterDiscount);

        assertEquals(5.0, shippingCost);
        assertEquals(89.0, totalAfterDiscount + shippingCost);
        assertEquals(8900L, Money.toCents(totalAfterDiscount + shippingCost));
    }
}
