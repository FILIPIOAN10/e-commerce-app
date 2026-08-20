package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ProductsPage {

    private static final By ADD_TO_CART_BUTTONS =
            By.xpath("//button[normalize-space()='Add to Cart']");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public ProductsPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public int addToCartButtonCount() {
        return driver.findElements(ADD_TO_CART_BUTTONS).size();
    }

    public void addFirstProductToCart() {
        WebElement button = wait.until(
                ExpectedConditions.elementToBeClickable(ADD_TO_CART_BUTTONS));
        button.click();
    }
}
