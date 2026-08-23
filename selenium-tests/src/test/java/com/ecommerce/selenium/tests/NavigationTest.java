package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.NavbarPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Navigation — Selenium")
class NavigationTest extends BaseSeleniumTest {

    @Test
    @DisplayName("click pe link-urile din navbar schimbă URL-ul")
    void navbarLinksNavigate() {
        open("/");
        NavbarPage navbar = new NavbarPage(driver, wait);

        navbar.clickProducts();
        wait.until(ExpectedConditions.urlContains("/products"));
        assertTrue(driver.getCurrentUrl().contains("/products"));

        navbar.clickAbout();
        wait.until(ExpectedConditions.urlContains("/about"));
        assertTrue(driver.getCurrentUrl().contains("/about"));

        navbar.clickContact();
        wait.until(ExpectedConditions.urlContains("/contact"));
        assertTrue(driver.getCurrentUrl().contains("/contact"));

        navbar.clickHome();
        wait.until(ExpectedConditions.urlMatches(".*/$"));
        assertTrue(driver.getCurrentUrl().endsWith("/"));
    }
}
