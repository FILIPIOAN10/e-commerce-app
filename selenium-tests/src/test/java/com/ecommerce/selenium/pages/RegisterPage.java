package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class RegisterPage {

    public static final By USERNAME = By.id("username");
    public static final By EMAIL = By.id("email");
    public static final By PASSWORD = By.id("password");
    public static final By PASSWORD_HINT = By.id("passwordHint");
    public static final By SUBMIT = By.cssSelector("button[type='submit']");
    public static final By ROLE_SELECT = By.cssSelector("select");
    public static final By TOAST = By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public RegisterPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public void fillForm(String username, String email, String password, String passwordHint) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME)).sendKeys(username);
        wait.until(ExpectedConditions.visibilityOfElementLocated(EMAIL)).sendKeys(email);
        wait.until(ExpectedConditions.visibilityOfElementLocated(PASSWORD)).sendKeys(password);
        wait.until(ExpectedConditions.visibilityOfElementLocated(PASSWORD_HINT)).sendKeys(passwordHint);
    }

    public void selectRole(String roleValue) {
        new Select(wait.until(ExpectedConditions.visibilityOfElementLocated(ROLE_SELECT))).selectByValue(roleValue);
    }

    public void submit() {
        wait.until(ExpectedConditions.elementToBeClickable(SUBMIT)).click();
    }

    public boolean isFieldVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator)).isDisplayed();
    }

    public String getToastMessage() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(TOAST)).getText();
    }

    public void waitForUrlToContain(String fragment) {
        wait.until(ExpectedConditions.urlContains(fragment));
    }
}

