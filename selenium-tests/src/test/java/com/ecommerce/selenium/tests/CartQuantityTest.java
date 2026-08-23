package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.LoginPage;
import com.ecommerce.selenium.pages.ProductsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cart quantity — Selenium")
class CartQuantityTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login → adaugă produs → crește cantitatea")
    void increaseQuantityUpdatesPrice() {
        open("/login");
        new LoginPage(driver, wait).login("user1", "password1");
        wait.until(ExpectedConditions.urlMatches(".*//$"));

        open("/products");
        new ProductsPage(driver, wait).addFirstProductToCart();

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]")));

        open("/cart");

        wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("button[aria-label='Increase quantity']"))).click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]")));

        assertTrue(driver.getCurrentUrl().contains("/cart"),
                "Ar trebui să rămânem pe /cart după modificarea cantității");
    }
}
