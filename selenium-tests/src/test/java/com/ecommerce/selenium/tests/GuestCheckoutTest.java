package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.ProductsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Guest checkout — Selenium")
class GuestCheckoutTest extends BaseSeleniumTest {

    @Test
    @DisplayName("guest adaugă produs în coș și vede opțiunea 'Checkout as guest'")
    void guestCheckoutOptionIsVisible() {
        open("/products");
        new ProductsPage(driver, wait).addFirstProductToCart();

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]")));

        open("/cart");

        // așteaptă ca coșul să se încarce cu produse (guest = coșul e în localStorage)
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//h1[contains(.,'Your Cart')]")));

        // verifică că există butonul/link "Checkout as guest"
        var guestCheckoutButton = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//button[contains(.,'Checkout as guest')]")));

        assertTrue(guestCheckoutButton.isDisplayed(),
                "Butonul 'Checkout as guest' ar trebui să fie vizibil pentru guest");
        assertTrue(guestCheckoutButton.isEnabled(),
                "Butonul 'Checkout as guest' ar trebui să fie activ");
    }
}
