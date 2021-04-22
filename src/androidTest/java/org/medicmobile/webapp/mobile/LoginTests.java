/*
 * Copyright 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.medicmobile.webapp.mobile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.test.espresso.web.webdriver.DriverAtoms;
import androidx.test.espresso.web.webdriver.Locator;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.Locale;

import static androidx.test.espresso.web.assertion.WebViewAssertions.webContent;
import static androidx.test.espresso.web.assertion.WebViewAssertions.webMatches;
import static androidx.test.espresso.web.matcher.DomMatchers.hasElementWithId;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static androidx.test.espresso.web.webdriver.DriverAtoms.clearElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.findElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.getText;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webClick;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.Matchers.containsString;

@LargeTest
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LoginTests {


	//TODO: user/pass to be encrypted
	public static final String USERNAME = BuildConfig.TEST_USERNAME;
	public static final String PASSWORD = BuildConfig.TEST_PASSWORD;
	public static final String SERVER_URL = BuildConfig.SERVER_URL;

	@Rule
	public ActivityTestRule<StartupActivity> mActivityRule = new ActivityTestRule<>(
			StartupActivity.class);
	SharedPreferences.Editor preferencesEditor;

	@Before
	public void setUp() {
		Context targetContext = getInstrumentation().getTargetContext();
		preferencesEditor = PreferenceManager.getDefaultSharedPreferences(targetContext).edit();
		preferencesEditor.putString("api-url", SERVER_URL);
		preferencesEditor.commit();
	}

	@Test
	public void checkLoginPageObjects() throws Exception {
//		preferencesEditor.putString("api-url", SERVER_URL);
//		preferencesEditor.commit();

		// Launch activity
		mActivityRule.launchActivity(new Intent());
		Thread.sleep(5000);//TODO: use better ways to handle delays
		onWebView()
				.check(webContent(hasElementWithId("form")))
				.withElement(findElement(Locator.ID, "locale"))
				// Verify that the response page contains the entered text
				.check(webMatches(getText(), containsString("English")));
		String[] codes = {"es", "en", "fr", "sw"};
		for (String code : codes) {

			onWebView().withElement(findElement(Locator.NAME, code))
					.check(webMatches(getText(), containsString(GetLanguage(code))));
		}
	}

	@Test
	public void loginAsRestrictedUser() throws Exception {

		onWebView().withElement(findElement(Locator.ID, "user"))
				.perform(clearElement())
				.perform(DriverAtoms.webKeys(USERNAME))	//to be created first
				.withElement(findElement(Locator.ID, "password"))
				.perform(clearElement())
				.perform(DriverAtoms.webKeys(PASSWORD))
				.withElement(findElement(Locator.ID, "login"))
				.perform(webClick());
		Thread.sleep(20000);//TODO: use better ways, takes some time to load depending on docs and whether it is first replication
		onWebView()
				.check(webContent(hasElementWithId("message-list")))
				.check(webContent(hasElementWithId("messages-tab")))
				.check(webContent(hasElementWithId("tasks-tab")))
				.check(webContent(hasElementWithId("contacts-tab")))
				.check(webContent(hasElementWithId("analytics-tab")))
				.check(webContent(hasElementWithId("reports-tab")));
	}

	private String GetLanguage(String code) {
		Locale aLocale = new Locale(code);
		return aLocale.getDisplayName();
	}
}
