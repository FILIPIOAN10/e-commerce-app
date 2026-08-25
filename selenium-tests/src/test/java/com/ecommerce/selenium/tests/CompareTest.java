package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.ComparePageObj;
import com.ecommerce.selenium.pages.LoginPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
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
        wait.until(ExpectedConditions.urlMatches(".*/$"));

        open("/products");

        List<WebElement> compareButtons = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                By.xpath("//button[@title='Add to compare' and not(@disabled)]")));
        assertTrue(compareButtons.size() >= 2, "Trebuie să existe cel puțin 2 produse");

        compareButtons.get(0).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]")));

        compareButtons = driver.findElements(
                By.xpath("//button[@title='Add to compare' and not(@disabled)]"));
        compareButtons.get(1).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[@role='status'][string-length(normalize-space(.)) > 0]")));

        open("/compare");
        ComparePageObj compare = new ComparePageObj(driver, wait);
        assertTrue(compare.isHeadingVisible(), "Pagina de compare ar trebui să aibă heading");
        assertTrue(compare.hasTable(), "Ar trebui să existe un tabel de comparație");
        assertTrue(compare.productCount() >= 2, "Ar trebui să fie 2 produse în tabel");
    }
}
