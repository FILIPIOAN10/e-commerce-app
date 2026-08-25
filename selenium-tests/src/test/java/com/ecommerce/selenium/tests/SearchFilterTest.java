package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.ProductsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Search & Filter — Selenium")
class SearchFilterTest extends BaseSeleniumTest {

    @Test
    @DisplayName("cautare după keyword afișează rezultate")
    void searchByKeywordShowsResults() {
        open("/products?keyword=mouse");
        ProductsPage page = new ProductsPage(driver, wait);

        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//button[normalize-space()='Add to Cart']")),
                ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[contains(text(),'No products')]"))
        ));

        assertTrue(page.addToCartButtonCount() > 0,
                "Căutarea după 'mouse' ar trebui să returneze cel puțin un produs");
    }
}
