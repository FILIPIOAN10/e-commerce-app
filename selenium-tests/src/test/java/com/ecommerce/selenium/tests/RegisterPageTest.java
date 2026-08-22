package com.ecommerce.selenium.tests;

import com.ecommerce.selenium.BaseSeleniumTest;
import com.ecommerce.selenium.pages.RegisterPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.Select;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Register page — Selenium")
class RegisterPageTest extends BaseSeleniumTest {

    @Test
    @DisplayName("pagina de register are toate câmpurile vizibile")
    void allFieldsAreVisible() {
        open("/register");
        RegisterPage page = new RegisterPage(driver, wait);

        assertTrue(page.isFieldVisible(RegisterPage.USERNAME));
        assertTrue(page.isFieldVisible(RegisterPage.EMAIL));
        assertTrue(page.isFieldVisible(RegisterPage.PASSWORD));
        assertTrue(page.isFieldVisible(RegisterPage.PASSWORD_HINT));
        assertTrue(page.isFieldVisible(RegisterPage.SUBMIT));


        //Verifică dropdown-ul de rol
        Select roleDropdown = new Select(driver.findElement(RegisterPage.ROLE_SELECT));
        assertTrue(roleDropdown.getOptions().size() == 3);

        assertEquals("ROLE_USER", roleDropdown.getOptions().get(1).getAttribute("value"));
        assertEquals("ROLE_SELLER", roleDropdown.getOptions().get(2).getAttribute("value"));

    }
}