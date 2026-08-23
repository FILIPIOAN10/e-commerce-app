package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.RegisterPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Register validation — Selenium")
class RegisterValidationTest extends BaseSeleniumTest {

    @Test
    @DisplayName("submit formular gol → afișează mesaje de validare")
    void emptyFormShowsValidationErrors() {
        open("/register");
        RegisterPage page = new RegisterPage(driver, wait);

        page.submit();

        List<WebElement> errorMessages = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                By.xpath("//p[contains(@class,'text-red-600')]")));

        assertTrue(errorMessages.size() >= 2,
                "Ar trebui cel puțin 2 mesaje de validare (username + email), găsite: " + errorMessages.size());
    }
}
