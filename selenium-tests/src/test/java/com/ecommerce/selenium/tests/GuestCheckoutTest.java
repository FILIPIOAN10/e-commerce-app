package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.GuestCheckoutPage;
import com.ecommerce.selenium.pages.ProductsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Guest checkout — Selenium")
class GuestCheckoutTest extends BaseSeleniumTest {

    @Test
    @DisplayName("adaugă produs în coș ca guest → /guest-checkout → formular vizibil")
    void guestCheckoutFormVisible() {
        open("/products");
        new ProductsPage(driver, wait).addFirstProductToCart();

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]")));

        open("/guest-checkout");
        GuestCheckoutPage page = new GuestCheckoutPage(driver, wait);

        assertTrue(page.isHeadingVisible(), "Heading-ul 'Guest Checkout' ar trebui să fie vizibil");
        assertTrue(page.isEmailInputVisible(), "Câmpul email ar trebui să fie vizibil");
        assertTrue(page.isSubmitButtonVisible(), "Butonul de submit ar trebui să fie vizibil");
    }
}
