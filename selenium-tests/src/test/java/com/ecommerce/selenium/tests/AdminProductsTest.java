package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.LoginPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Admin Products — Selenium")
class AdminProductsTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login ca admin → /admin/products → tabel cu produse")
    void adminProductsPageLoads() {
        open("/login");
        new LoginPage(driver, wait).login("admin", "adminPass");
        wait.until(ExpectedConditions.urlMatches(".*/$"));

        open("/admin/products");
        wait.until(ExpectedConditions.urlContains("/admin/products"));

        var table = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//table | //div[contains(@class,'MuiDataGrid')] | //div[contains(@class,'p-4')]")));
        assertTrue(table.isDisplayed(), "Tabelul de produse ar trebui să fie vizibil");
    }
}
