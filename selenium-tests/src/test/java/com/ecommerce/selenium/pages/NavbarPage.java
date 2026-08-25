package com.ecommerce.selenium.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class NavbarPage {

    private static final By HOME_LINK = By.xpath("//a[normalize-space()='Home']");
    private static final By PRODUCTS_LINK = By.xpath("//a[normalize-space()='Products']");
    private static final By ABOUT_LINK = By.xpath("//a[normalize-space()='About']");
    private static final By CONTACT_LINK = By.xpath("//a[normalize-space()='Contact']");
    private static final By LOGIN_LINK = By.xpath("//a[.//span[normalize-space()='Login'] or normalize-space()='Login']");
    private static final By USER_AVATAR = By.cssSelector("[data-testid='user-avatar']");
    private static final By USER_MENU_LIST = By.cssSelector(".MuiMenu-list, .MuiMenu-paper");
    private static final By LOGOUT_BUTTON = By.cssSelector("[data-testid='logout-button']");
    private static final By USERNAME_IN_MENU = By.cssSelector(".MuiMenuItem-root .font-bold");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public NavbarPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public void clickHome() {
        wait.until(ExpectedConditions.elementToBeClickable(HOME_LINK)).click();
    }

    public void clickProducts() {
        wait.until(ExpectedConditions.elementToBeClickable(PRODUCTS_LINK)).click();
    }

    public void clickAbout() {
        wait.until(ExpectedConditions.elementToBeClickable(ABOUT_LINK)).click();
    }

    public void clickContact() {
        wait.until(ExpectedConditions.elementToBeClickable(CONTACT_LINK)).click();
    }

    public void clickLogin() {
        wait.until(ExpectedConditions.elementToBeClickable(LOGIN_LINK)).click();
    }

    public void openUserMenu() {
        wait.until(ExpectedConditions.elementToBeClickable(USER_AVATAR)).click();
        // MUI Menu se randează într-un portal; așteptăm să fie vizibil
        wait.until(ExpectedConditions.visibilityOfElementLocated(USER_MENU_LIST));
    }

    public void clickLogout() {
        wait.until(ExpectedConditions.elementToBeClickable(LOGOUT_BUTTON)).click();
    }

    public String getUsernameFromMenu() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME_IN_MENU)).getText();
    }
}
