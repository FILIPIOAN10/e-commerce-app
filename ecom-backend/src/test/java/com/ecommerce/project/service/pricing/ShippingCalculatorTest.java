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
        assertEquals(Money.ZERO, calculator.calculate(address("US"), Money.of(100.0)));
        assertEquals(Money.ZERO, calculator.calculate(address("US"), Money.of(150.0)));
    }

    @Test
    @DisplayName("domestic shipping is 3.0 when chargeable total is below threshold")
    void domesticShippingBelowThreshold() {
        assertEquals(Money.of(3.0), calculator.calculate(address("RO"), Money.of(84.0)));
        assertEquals(Money.of(3.0), calculator.calculate(address("Romania"), Money.of(50.0)));
    }

    @Test
    @DisplayName("international shipping is 5.0 when chargeable total is below threshold")
    void internationalShippingBelowThreshold() {
        assertEquals(Money.of(5.0), calculator.calculate(address("US"), Money.of(84.0)));
        assertEquals(Money.of(5.0), calculator.calculate(address("Germany"), Money.of(20.0)));
    }

    @Test
    @DisplayName("shipping is calculated on the post-discount total: 105 - 20% = 84 -> 5.0")
    void shippingOnPostDiscountTotal() {
        Money subtotal = Money.of(105.0);
        Money totalAfterDiscount = subtotal.subtract(subtotal.percentage(20));

        Money shippingCost = calculator.calculate(address("US"), totalAfterDiscount);

        assertEquals(Money.of(5.0), shippingCost);
        assertEquals(Money.of(89.0), totalAfterDiscount.add(shippingCost));
        assertEquals(8900L, totalAfterDiscount.add(shippingCost).toCents());
    }
}
