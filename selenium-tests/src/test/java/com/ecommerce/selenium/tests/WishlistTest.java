package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.LoginPage;
import com.ecommerce.selenium.pages.WishlistPageObj;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Wishlist flow — Selenium")
class WishlistTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login → adaugă produs la wishlist → verifică pe /wishlist")
    void addProductToWishlist() {
        open("/login");
        new LoginPage(driver, wait).login("user1", "password1");
        wait.until(ExpectedConditions.urlMatches(".*//$"));

        open("/products");

        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[@title='Add to wishlist' and not(@disabled)]"))).click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]")));

        open("/wishlist");
        WishlistPageObj wishlist = new WishlistPageObj(driver, wait);
        assertTrue(wishlist.isHeadingVisible(), "Pagina de wishlist ar trebui să aibă heading");
        assertTrue(wishlist.hasProducts(), "Wishlist-ul ar trebui să conțină produsul adăugat");
    }
}
