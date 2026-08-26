package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.CartPage;
import com.ecommerce.selenium.pages.LoginPage;
import com.ecommerce.selenium.pages.ProductsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Add to cart flow — Selenium")
class AddToCartTest extends BaseSeleniumTest {

    private static final String USER = System.getProperty("test.user", "user1");
    private static final String PASS = System.getProperty("test.pass", "password1");

    @Test
    @DisplayName("user normal poate adăuga primul produs în coș")
    void addFirstProductToCart() {
        open("/login");
        new LoginPage(driver, wait).login(USER, PASS);
        wait.until(ExpectedConditions.urlMatches(".*/en$"));

        open("/products");
        ProductsPage products = new ProductsPage(driver, wait);
        assertTrue(products.addToCartButtonCount() > 0,
                "Nu s-a găsit niciun buton 'Add to Cart' — verifică dacă userul e admin (butonul e ascuns pentru ROLE_ADMIN)");

        products.addFirstProductToCart();

        open("/cart");
        assertTrue(new CartPage(driver, wait).hasItems(), "Coșul ar trebui să conțină produsul adăugat");
    }
}
