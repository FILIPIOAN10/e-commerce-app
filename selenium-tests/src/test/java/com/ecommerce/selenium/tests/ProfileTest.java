package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.LoginPage;
import com.ecommerce.selenium.pages.ProfilePage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Profile page — Selenium")
class ProfileTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login → /profile → afișează username-ul corect")
    void profileShowsUsername() {
        open("/login");
        new LoginPage(driver, wait).login("user1", "password1");
        wait.until(ExpectedConditions.urlMatches(".*/$"));

        open("/profile");
        ProfilePage profile = new ProfilePage(driver, wait);

        assertTrue(profile.isHeadingVisible(), "Heading-ul 'Profilul meu' ar trebui să fie vizibil");
        assertEquals("user1", profile.getUsername(),
                "Username-ul afișat ar trebui să fie 'user1'");
    }
}
