package org.medicmobile.webapp.mobile;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.web.assertion.WebViewAssertions.webMatches;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static androidx.test.espresso.web.webdriver.DriverAtoms.findElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.getText;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webClick;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.web.webdriver.Locator;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;


/**
 * Test that when the app is closed and then opened again, the last URL
 * viewed is loaded instead of the app URL.
 */
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4.class)
public class LastUrlTest {

	@Rule
	public ActivityScenarioRule<SettingsDialogActivity> mActivityTestRule =
			new ActivityScenarioRule<>(SettingsDialogActivity.class);

	@Test
	public void testOpenUrlAndRecord() throws InterruptedException {
		onView(withText("Custom")).perform(click());
		ViewInteraction textAppUrl = onView(withId(R.id.txtAppUrl));
		// Load the the Angular "Getting started" guide
		textAppUrl.perform(replaceText("https://angular.io/start"), closeSoftKeyboard());
		onView(withId(R.id.btnSaveSettings)).perform(click());
		Thread.sleep(2000);
		// Check section content is loaded
		onWebView()
				.withNoTimeout()
				.withElement(findElement(Locator.TAG_NAME, "h1"))
				.check(webMatches(getText(), containsString("Getting started with Angular")));
		// Click on the hamburger menu and then in the "Adding navigation" entry
		// to go to "/start/start-routing"
		onWebView()
				.withNoTimeout()
				.withElement(findElement(Locator.XPATH, "//button[contains(@class,'hamburger')]"))
				.perform(webClick());
		Thread.sleep(2000);
		onWebView()
				.withNoTimeout()
				.withElement(findElement(Locator.XPATH,
						"//a[contains(@href,'start/start-routing') and contains(@class,'vertical-menu-item')]"))
				.perform(webClick());
		Thread.sleep(2000);
		// Check that the "Adding navigation" section is loaded
		onWebView()
				.withNoTimeout()
				.withElement(findElement(Locator.TAG_NAME, "h1"))
				.check(webMatches(getText(), containsString("Adding navigation")));
		Thread.sleep(2000);
	}

	@Test
	public void testReopenAppAndCheckLastUrl() throws InterruptedException {
		// Click the 4th option in the Settings Dialog to
		// reload the last URL visited
		DataInteraction linearLayout = onData(anything())
				.inAdapterView(allOf(withId(R.id.lstServers),
						TestUtils.childAtPosition(
								withId(android.R.id.content),
								0)))
				.atPosition(3);
		Thread.sleep(2000);
		linearLayout.perform(click());
		Thread.sleep(2000);
		// Remembered "Adding navigation" section is loaded (last URL)
		// instead of the "Getting started..." section (app URL)
		onWebView()
				.withNoTimeout()
				.withElement(findElement(Locator.TAG_NAME, "h1"))
				.check(webMatches(getText(), containsString("Adding navigation")));
		onWebView()
				.withNoTimeout()
				.withElement(findElement(Locator.TAG_NAME, "h1"))
				.check(webMatches(getText(), not(
						containsString("Getting started with Angular"))));
	}
}
