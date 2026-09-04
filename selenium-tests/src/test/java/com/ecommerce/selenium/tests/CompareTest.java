package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.ComparePageObj;
import com.ecommerce.selenium.pages.LoginPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Compare products — Selenium")
class CompareTest extends BaseSeleniumTest {

    @Test
    @DisplayName("login → adaugă 2 produse la compare → verifică tabelul pe /compare")
    void compareTwoProducts() {
        open("/login");
        new LoginPage(driver, wait).login("user1", "password1");
        wait.until(ExpectedConditions.urlMatches(".*/en$"));

        open("/products");

        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(ADD_TO_COMPARE));
        assertTrue(driver.findElements(ADD_TO_COMPARE).size() >= 2,
                "Trebuie să existe cel puțin 2 produse");

        addFirstAvailableToCompare();
        addFirstAvailableToCompare();

        open("/compare");
        ComparePageObj compare = new ComparePageObj(driver, wait);
        assertTrue(compare.isHeadingVisible(), "Pagina de compare ar trebui să aibă heading");
        assertTrue(compare.hasTable(), "Ar trebui să existe un tabel de comparație");
        assertTrue(compare.productCount() >= 2, "Ar trebui să fie 2 produse în tabel");
    }

    // Located by data-testid, not by title: the title is rendered through i18next
    // (t("addToCompare")), so an English-only XPath breaks the moment the suite
    // runs against another locale.
    private static final By ADD_TO_COMPARE =
            By.cssSelector("[data-testid='compare-button']:not([disabled])");

    /**
     * Adds the first product that is not already being compared.
     *
     * <p>The button carries {@code disabled={isInCompare}}, so a successful click
     * changes Redux state and React re-renders the whole product grid — which
     * invalidates every {@link WebElement} handle taken before the click. The
     * list is therefore re-found on every poll, and a stale handle costs one
     * retry instead of failing the run. Waiting for the enabled count to drop is
     * the completion signal; the toast can appear before the grid re-renders.
     *
     * <p>The URL check is not incidental. The button sits inside the card's own
     * navigate-to-detail click handler, and when it did not stop propagation,
     * adding to compare also bounced the user to the product page — which is what
     * made this test fail in CI rather than any timing issue.
     */
    private void addFirstAvailableToCompare() {
        int before = driver.findElements(ADD_TO_COMPARE).size();
        assertTrue(before > 0, "Nu mai există produse disponibile pentru comparare");

        wait.until(d -> {
            try {
                List<WebElement> buttons = d.findElements(ADD_TO_COMPARE);
                if (buttons.isEmpty()) {
                    return false;
                }
                buttons.get(0).click();
                return true;
            } catch (StaleElementReferenceException | ElementClickInterceptedException e) {
                return false;
            }
        });

        wait.until(ExpectedConditions.numberOfElementsToBeLessThan(ADD_TO_COMPARE, before));

        // Deliberately matches the detail route, not just "/products": the failure
        // mode is a bounce to /products/{id}, which "contains /products" too.
        assertFalse(driver.getCurrentUrl().matches(".*/products/[0-9]+.*"),
                "Adăugarea la comparare nu trebuie să navigheze la pagina produsului, dar URL-ul este "
                        + driver.getCurrentUrl());
    }
}
