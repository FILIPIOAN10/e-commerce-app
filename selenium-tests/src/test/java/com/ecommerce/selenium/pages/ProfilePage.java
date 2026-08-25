package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ProfilePage {

    private static final By HEADING = By.xpath("//h1[contains(.,'Profilul meu')]");
    private static final By USERNAME = By.xpath("//p[contains(@class,'font-semibold')]");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public ProfilePage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isHeadingVisible() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(HEADING)).isDisplayed();
    }

    public String getUsername() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME)).getText();
    }
}
