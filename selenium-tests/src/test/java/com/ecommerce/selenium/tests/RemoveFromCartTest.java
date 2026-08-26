package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.LoginPage;
import com.ecommerce.selenium.pages.ProductsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Remove from cart — Selenium")
class RemoveFromCartTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login → adaugă produs → șterge din coș → coșul e gol")
    void removeProductFromCart() {
        open("/login");
        new LoginPage(driver, wait).login("user1", "password1");
        wait.until(ExpectedConditions.urlMatches(".*/en$"));

        open("/products");
        new ProductsPage(driver, wait).addFirstProductToCart();

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]")));

        open("/cart");

        wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='remove-item-button']"))).click();

        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[contains(text(),'Your cart is empty')]")),
                ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//h1[contains(.,'Your Cart')]"))
        ));

        boolean isEmpty = !driver.findElements(
                By.xpath("//*[contains(text(),'Your cart is empty')]")).isEmpty();
        assertTrue(isEmpty, "După ștergere, coșul ar trebui să fie gol");
    }
}
