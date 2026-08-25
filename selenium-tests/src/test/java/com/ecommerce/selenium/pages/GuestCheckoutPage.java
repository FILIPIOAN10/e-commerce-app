package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class GuestCheckoutPage {

    private static final By HEADING = By.xpath("//h1[contains(.,'Guest Checkout')]");
    private static final By EMAIL_INPUT = By.cssSelector("input[name='email']");
    private static final By STREET_INPUT = By.cssSelector("input[name='street']");
    private static final By CITY_INPUT = By.cssSelector("input[name='city']");
    private static final By COUNTRY_INPUT = By.cssSelector("input[name='country']");
    private static final By PINCODE_INPUT = By.cssSelector("input[name='pincode']");
    private static final By SUBMIT_BUTTON = By.cssSelector("button[type='submit']");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public GuestCheckoutPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isHeadingVisible() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(HEADING)).isDisplayed();
    }

    public boolean isEmailInputVisible() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(EMAIL_INPUT)).isDisplayed();
    }

    public boolean isSubmitButtonVisible() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(SUBMIT_BUTTON)).isDisplayed();
    }

    public void fillForm(String email, String street, String city, String country, String pincode) {
        driver.findElement(EMAIL_INPUT).sendKeys(email);
        driver.findElement(STREET_INPUT).sendKeys(street);
        driver.findElement(CITY_INPUT).sendKeys(city);
        driver.findElement(COUNTRY_INPUT).sendKeys(country);
        driver.findElement(PINCODE_INPUT).sendKeys(pincode);
    }
}
