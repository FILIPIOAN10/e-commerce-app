package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.LoginPage;
import com.ecommerce.selenium.pages.NavbarPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Logout flow — Selenium")
class LogoutTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login → logout → redirect la /login")
    void logoutRedirectsToLogin() {
        open("/login");
        new LoginPage(driver, wait).login("user1", "password1");
        wait.until(ExpectedConditions.urlMatches(".*/en$"));

        NavbarPage navbar = new NavbarPage(driver, wait);
        navbar.openUserMenu();
        navbar.clickLogout();

        wait.until(ExpectedConditions.urlContains("/en/login"));
        assertTrue(driver.getCurrentUrl().contains("/en/login"),
                "Dupa logout ar trebui redirect la /en/login, URL actual: " + driver.getCurrentUrl());
    }
}
