package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.RegisterPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Register flow — Selenium")
class RegisterTest extends BaseSeleniumTest {

    @Test
    @DisplayName("register with valid data redirects to /login")
    void registerWithValidDataRedirectsToLogin() {
        open("/register");
        RegisterPage page = new RegisterPage(driver, wait);

        String timestamp = String.valueOf(System.currentTimeMillis()).substring(5);
        String username = "sel_" + timestamp;
        String email = "sel_" + timestamp + "@example.com";

        page.fillForm(username, email, "Password123!", "hint");
        page.selectRole("ROLE_USER");
        page.submit();

        page.waitForUrlToContain("/login");

        String currentUrl = driver.getCurrentUrl();
        assertTrue(currentUrl.contains("/login"), "Expected redirect to /login, but got: " + currentUrl);
    }

    @Test
    @DisplayName("register with duplicate username shows error and stays on /register")
    void registerWithDuplicateUsernameShowsError() {
        open("/register");
        RegisterPage page = new RegisterPage(driver, wait);

        page.fillForm("admin", "unique_email_" + System.currentTimeMillis() + "@example.com", "Password123!", "hint");
        page.selectRole("ROLE_USER");
        page.submit();

        String toastMessage = page.getToastMessage();
        assertTrue(toastMessage.contains("already taken"), "Expected duplicate username error, but got: " + toastMessage);

        String currentUrl = driver.getCurrentUrl();
        assertFalse(currentUrl.contains("/login"), "Should stay on /register, but got: " + currentUrl);
        assertTrue(currentUrl.contains("/register"), "Expected URL to contain /register, but got: " + currentUrl);
    }
}
