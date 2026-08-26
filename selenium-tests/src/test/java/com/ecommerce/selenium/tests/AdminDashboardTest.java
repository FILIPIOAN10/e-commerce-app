package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.LoginPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Admin Dashboard — Selenium")
class AdminDashboardTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login ca admin → /admin → dashboard se încarcă")
    void adminDashboardLoads() {
        open("/login");
        new LoginPage(driver, wait).login("admin", "adminPass");
        wait.until(ExpectedConditions.urlMatches(".*/en$"));

        open("/admin");

        wait.until(ExpectedConditions.urlContains("/admin"));
        assertTrue(driver.getCurrentUrl().contains("/admin"),
                "Ar trebui să fim pe /admin, URL actual: " + driver.getCurrentUrl());

        var content = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//main | //div[contains(@class,'p-4')]")));
        assertTrue(content.isDisplayed(), "Dashboard-ul ar trebui să aibă conținut vizibil");
    }
}
