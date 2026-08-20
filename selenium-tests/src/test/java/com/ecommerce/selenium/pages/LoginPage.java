package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class LoginPage {

    private static final By USERNAME = By.id("username");
    private static final By PASSWORD = By.id("password");
    private static final By SUBMIT = By.cssSelector("button[type='submit']");
    private static final By TOAST = By.cssSelector("[role='status']");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public LoginPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public void login(String username, String password) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME)).sendKeys(username);
        driver.findElement(PASSWORD).sendKeys(password);
        driver.findElement(SUBMIT).click();
    }

    public boolean isErrorToastVisible() {
        return !wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(TOAST)).isEmpty();
    }
}
