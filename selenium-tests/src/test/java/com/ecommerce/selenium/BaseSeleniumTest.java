package com.ecommerce.selenium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseSeleniumTest {

    protected WebDriver driver;
    protected WebDriverWait wait;
    protected String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = System.getProperty("base.url", "http://localhost:5173");
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "true"));

        String chromeDriverPath = System.getProperty("webdriver.chrome.driver");
        if (chromeDriverPath != null && !chromeDriverPath.isBlank()) {
            System.setProperty("webdriver.chrome.driver", chromeDriverPath);
        }

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--incognito");
        options.addArguments("--disable-features=PasswordCheckup,PasswordManager,PasswordBreachUpdate,PasswordCheck,PasswordCheckupSuggestion,PasswordLeakDetection,SafeBrowsingEnhanced,PasswordProtection");

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("safebrowsing.password_protection", 0);
        prefs.put("safebrowsing.enabled", false);
        prefs.put("browser.password_protection_for_breached_sites", false);
        prefs.put("password_manager_enabled", false);
        options.setExperimentalOption("prefs", prefs);

        driver = new ChromeDriver(options);
        driver.manage().window().setSize(new Dimension(1440, 900));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected void open(String path) {
        if (!path.startsWith("/en")) {
            path = "/en" + (path.startsWith("/") ? path : "/" + path);
        }
        driver.get(baseUrl + path);
    }
}
