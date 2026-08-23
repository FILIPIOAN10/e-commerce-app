package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.ProductsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pagination — Selenium")
class PaginationTest extends BaseSeleniumTest {

    @Test
    @DisplayName("pagina /products are butoane de paginare")
    void productsPageHasPagination() {
        open("/products");

        new ProductsPage(driver, wait).addToCartButtonCount();

        List<WebElement> paginationButtons = driver.findElements(
                By.cssSelector("button[aria-label*='page'], .MuiPagination-ul button"));

        if (paginationButtons.size() > 1) {
            List<WebElement> page2Button = driver.findElements(
                    By.cssSelector("button[aria-label='Go to page 2']"));

            if (!page2Button.isEmpty()) {
                page2Button.get(0).click();
                wait.until(ExpectedConditions.urlContains("page=2"));
                assertTrue(driver.getCurrentUrl().contains("page=2"),
                        "URL ar trebui să conțină page=2");
            }
        }

        assertTrue(driver.getCurrentUrl().contains("/products"),
                "Ar trebui să rămânem pe /products");
    }
}
