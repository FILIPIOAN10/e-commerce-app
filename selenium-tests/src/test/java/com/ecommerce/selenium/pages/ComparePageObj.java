package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ComparePageObj {

    private static final By HEADING = By.xpath("//h1[contains(.,'Compare Products')]");
    private static final By EMPTY_STATE = By.xpath("//*[contains(text(),'No products to compare')]");
    private static final By TABLE = By.tagName("table");
    private static final By PRODUCT_HEADERS = By.xpath("//th[.//span[@class or contains(@class,'font-semibold')]]");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public ComparePageObj(WebDriver driver, WebDriverWait wait) {
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

    public boolean hasTable() {
        return !driver.findElements(TABLE).isEmpty();
    }

    public int productCount() {
        return driver.findElements(PRODUCT_HEADERS).size();
    }

    public boolean isEmptyStateVisible() {
        return !driver.findElements(EMPTY_STATE).isEmpty();
    }
}
