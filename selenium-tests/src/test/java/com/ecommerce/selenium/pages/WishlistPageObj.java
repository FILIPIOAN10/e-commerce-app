package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class WishlistPageObj {

    private static final By HEADING = By.xpath("//h1[contains(.,'My Wishlist')]");
    private static final By EMPTY_STATE = By.xpath("//*[contains(text(),'Your wishlist is empty')]");
    private static final By PRODUCT_CARDS = By.xpath("//div[contains(@class,'border') and .//h2]");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public WishlistPageObj(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isHeadingVisible() {
        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(HEADING),
                ExpectedConditions.presenceOfElementLocated(EMPTY_STATE)
        ));
        return !driver.findElements(HEADING).isEmpty();
    }

    public boolean hasProducts() {
        return !driver.findElements(PRODUCT_CARDS).isEmpty();
    }

    public int productCount() {
        return driver.findElements(PRODUCT_CARDS).size();
    }

    public boolean isEmptyStateVisible() {
        return !driver.findElements(EMPTY_STATE).isEmpty();
    }
}
