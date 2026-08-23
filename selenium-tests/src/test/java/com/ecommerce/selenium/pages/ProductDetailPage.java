package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ProductDetailPage {

    private static final By PRODUCT_NAME = By.xpath("//h1[contains(@class,'text-3xl')]");
    private static final By PRICE = By.xpath("//span[contains(@class,'text-4xl')]");
    private static final By DESCRIPTION = By.xpath("//p[contains(@class,'leading-relaxed')]");
    private static final By ADD_TO_CART = By.xpath("//button[normalize-space()='Add to Cart']");
    private static final By WISHLIST_BTN = By.xpath("//button[normalize-space()='Wishlist' or normalize-space()='Saved']");
    private static final By COMPARE_BTN = By.xpath("//button[normalize-space()='Compare' or normalize-space()='In Compare']");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public ProductDetailPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public String getProductName() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(PRODUCT_NAME)).getText();
    }

    public boolean isPriceVisible() {
        return !driver.findElements(PRICE).isEmpty()
                && driver.findElement(PRICE).getText().contains("$");
    }

    public boolean isDescriptionVisible() {
        return !driver.findElements(DESCRIPTION).isEmpty()
                && !driver.findElement(DESCRIPTION).getText().isBlank();
    }

    public boolean isAddToCartVisible() {
        return !driver.findElements(ADD_TO_CART).isEmpty();
    }

    public boolean isWishlistButtonVisible() {
        return !driver.findElements(WISHLIST_BTN).isEmpty();
    }

    public boolean isCompareButtonVisible() {
        return !driver.findElements(COMPARE_BTN).isEmpty();
    }

    public void clickAddToCart() {
        wait.until(ExpectedConditions.elementToBeClickable(ADD_TO_CART)).click();
    }

    public void clickWishlist() {
        wait.until(ExpectedConditions.elementToBeClickable(WISHLIST_BTN)).click();
    }

    public void clickCompare() {
        wait.until(ExpectedConditions.elementToBeClickable(COMPARE_BTN)).click();
    }
}
