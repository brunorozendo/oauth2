package com.brunorozendo.oauth2.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E test for OAuth2 login flow
 * Tests: Full login flow from clicking login button through Google consent to dashboard
 */
@Testcontainers
public class OAuth2LoginFlowTest {

    @Container
    public BrowserWebDriverContainer<?> chrome = new BrowserWebDriverContainer<>()
        .withCapabilities(new ChromeOptions());

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    public void setup() {
        org.testcontainers.Testcontainers.exposeHostPorts(8631);
        driver = chrome.getWebDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    public void testLoginRedirect() {
        // Access frontend running on host
        driver.get("http://host.testcontainers.internal:8631/");

        // Verify title
        assertEquals("OAuth2 Login", driver.getTitle());

        // Find login button
        WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("login-button")));
        assertTrue(loginButton.isDisplayed());

        // Click login button
        loginButton.click();

        // Verify redirect to Google
        wait.until(ExpectedConditions.urlContains("accounts.google.com"));
        assertTrue(driver.getCurrentUrl().startsWith("https://accounts.google.com"));
    }
}
