package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class CartPage {

    private static final By HEADING = By.xpath("//h1[contains(., 'Your Cart')]");
    private static final By EMPTY_STATE = By.xpath("//*[contains(text(), 'Your cart is empty')]");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public CartPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean hasItems() {
        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(HEADING),
                ExpectedConditions.presenceOfElementLocated(EMPTY_STATE)));
        return !driver.findElements(HEADING).isEmpty();
    }
}
