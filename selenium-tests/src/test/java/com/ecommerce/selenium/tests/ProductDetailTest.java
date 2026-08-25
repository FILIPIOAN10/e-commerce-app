package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.ProductDetailPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Product Detail page — Selenium")
class ProductDetailTest extends BaseSeleniumTest {

    @Test
    @DisplayName("pagina /products/1 afișează nume, preț, descriere")
    void productDetailShowsAllInfo() {
        open("/products/1");
        ProductDetailPage page = new ProductDetailPage(driver, wait);

        assertFalse(page.getProductName().isBlank(), "Numele produsului nu trebuie să fie gol");
        assertTrue(page.isPriceVisible(), "Prețul trebuie să fie vizibil");
        assertTrue(page.isDescriptionVisible(), "Descrierea trebuie să fie vizibilă");
    }
}
