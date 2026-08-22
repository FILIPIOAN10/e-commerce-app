package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class RegisterPage {

    public static final By USERNAME = By.id("username");
    public static final By EMAIL = By.id("email");
    public static final By PASSWORD = By.id("password");
    public static final By PASSWORD_HINT = By.id("passwordHint");
    public static final By SUBMIT = By.cssSelector("button[type='submit']");
    public static final By ROLE_SELECT = By.cssSelector("select");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public RegisterPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }
    public boolean isFieldVisible(By locator) {
        return driver.findElement(locator).isDisplayed();
    }

}

