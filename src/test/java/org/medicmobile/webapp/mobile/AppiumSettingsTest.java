package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.WebElement;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import io.appium.java_client.service.local.AppiumDriverLocalService;

public class AppiumSettingsTest {
	private AppiumDriverLocalService service;
	private AndroidDriver driver;

	@Before
	public void before() throws MalformedURLException {
		service = AppiumDriverLocalService.buildDefaultService();
		service.start();

		UiAutomator2Options options = new UiAutomator2Options()
			.setAppPackage("org.medicmobile.webapp.mobile")
			.setAppActivity("org.medicmobile.webapp.mobile.StartupActivity")
			.setApp("/home/jlkuester7/git/cht-android/build/outputs/apk/unbranded/debug/cht-android-SNAPSHOT-unbranded-arm64-v8a-debug.apk");
		driver = new AndroidDriver(new URL("http://127.0.0.1:4723"), options);
		driver.manage().timeouts().implicitlyWait(Duration.ofMillis(10000));
	}

	@After
	public void after() {
		driver.quit();
		service.stop();
	}

	@Test
	public void myTest() {
		btnGammaDev().click();
		String title = dialogTitle().getText();
		assertEquals("Login to Gamma Dev?", title);

		WebElement continueBtn = dialogBtnContinue();
		assertTrue(continueBtn.isDisplayed());
		WebElement cancelBtn = dialogBtnCancel();
		assertTrue(cancelBtn.isDisplayed());

		continueBtn.click();

		WebElement loginButton = btnLogin();
		assertTrue(loginButton.isDisplayed());

		List<String> locales = Arrays.asList(
			"Bamanankan (Bambara)",
			"English",
			"Español (Spanish)",
			"Français (French)",
			"हिन्दी (Hindi)",
			"Bahasa Indonesia (Indonesian)",
			"नेपाली (Nepali)",
			"Kiswahili (Swahili)"
		);
		locales.forEach((locale) -> {
			WebElement element = driver.findElement(AppiumBy.accessibilityId(locale));
			assertTrue(element.isDisplayed());
		});

		driver.findElement(AppiumBy.accessibilityId("English")).click();

		txtBoxUsername().sendKeys("fakename");
		txtBoxPassword().sendKeys("fake_password");
		loginButton.click();

		WebElement loginErr = loginError();
//		WebElement loginErr = driver.findElement(AppiumBy.cssSelector("p.error.incorrect"));
		assertTrue(loginErr.isDisplayed());
	}

	private WebElement btnGammaDev() {
		return driver.findElement(AppiumBy.xpath("//*[@text=\"Gamma Dev\"]"));
	}

	private WebElement dialogTitle() {
		return driver.findElement(AppiumBy.xpath("//android.widget.TextView[@resource-id=\"android:id/message\"]"));
	}

	private WebElement dialogBtnContinue() {
		return driver.findElement(AppiumBy.xpath("//android.widget.Button[@text=\"Continue\"]"));
	}

	private WebElement dialogBtnCancel() {
		return driver.findElement(AppiumBy.xpath("//android.widget.Button[@text=\"Cancel\"]"));
	}

	private WebElement btnLogin() {
		return driver.findElement(AppiumBy.xpath("//android.widget.Button[@resource-id=\"login\"]"));
	}

	private WebElement txtBoxUsername() {
		return driver.findElement(AppiumBy.xpath("//android.widget.EditText[@resource-id=\"user\"]"));
	}

	private WebElement txtBoxPassword() {
		return driver.findElement(AppiumBy.xpath("//android.widget.EditText[@resource-id=\"password\"]"));
	}

	private WebElement loginError() {
		return driver.findElement(AppiumBy.xpath("//android.widget.TextView[@text=\"Incorrect user name or password. Please try again.\"]"));
	}
}
