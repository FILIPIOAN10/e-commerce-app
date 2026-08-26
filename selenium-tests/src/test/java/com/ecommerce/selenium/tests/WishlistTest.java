package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.LoginPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Wishlist flow — Selenium")
class WishlistTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login → butonul de wishlist este vizibil pentru user")
    void wishlistButtonIsAvailableForLoggedInUser() {
        open("/login");
        new LoginPage(driver, wait).login("user1", "password1");
        wait.until(ExpectedConditions.urlMatches(".*/en$"));

        open("/products");

        // găsește primul buton de wishlist (poate fi deja adăugat sau nu)
        List<WebElement> addToWishlist = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                By.xpath("//button[contains(@title, 'wishlist')]")));

        assertFalse(addToWishlist.isEmpty(),
                "Ar trebui să existe cel puțin un buton de wishlist pentru utilizatorul logat");

        WebElement wishlistButton = addToWishlist.get(0);
        assertTrue(wishlistButton.isDisplayed(), "Butonul wishlist ar trebui să fie vizibil");

        // dacă nu e deja în wishlist, încercăm să-l adăugăm
        if ("Add to wishlist".equals(wishlistButton.getAttribute("title"))) {
            wishlistButton.click();
            // așteaptă re-randarea butonului (sau eventual un toast)
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//button[@title='Already in wishlist']")),
                    ExpectedConditions.visibilityOfElementLocated(
                            By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]"))
            ));
        }
    }
}
