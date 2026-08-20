package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.LoginPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Login flow — Selenium")
class LoginTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login cu credentiale valide duce pe home")
    void loginWithValidCredentials() {
        open("/login");
        new LoginPage(driver, wait).login("admin", "adminPass");

        wait.until(ExpectedConditions.urlMatches(".*/$"));
        assertTrue(driver.getCurrentUrl().endsWith("/"),
                "Dupa login ar trebui redirect pe home, URL actual: " + driver.getCurrentUrl());
    }

    @Test
    @DisplayName("parola greșită rămâne pe /login și afișează toast de eroare")
    void loginWithWrongPassword() {
        open("/login");
        LoginPage loginPage = new LoginPage(driver, wait);
        loginPage.login("admin", "parola-greșită");

        assertTrue(loginPage.isErrorToastVisible(), "Ar trebui afișat un toast de eroare");
        assertTrue(driver.getCurrentUrl().contains("/login"), "Ar trebui să rămână pe /login");
    }
}
