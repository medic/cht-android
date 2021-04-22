package org.medicmobile.webapp.mobile;


import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.espresso.DataInteraction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;

@LargeTest
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SettingsDialogActivityTest {

	private static final String WEBAPP_URL = "Webapp URL";
	private static final String SERVER_ONE = "https://medic.github.io/atp";
	private static final String SERVER_TWO = "https://gamma-cht.dev.medicmobile.org";
	private static final String SERVER_THREE = "https://gamma.dev.medicmobile.org";

	@Rule
	public ActivityTestRule<SettingsDialogActivity> mActivityTestRule = new ActivityTestRule<>(SettingsDialogActivity.class);

	@Test
	public void testServerSelectionScreen() {
		onView(withText("Medic Mobile")).check(matches(isDisplayed()));
		onView(withText("Custom")).check(matches(isDisplayed()));
		onView(withId(R.id.lstServers)).check(matches(isDisplayed()));
		// servers
		onView(withText(SERVER_ONE)).check(matches(isDisplayed()));
		onView(withText(SERVER_TWO)).check(matches(isDisplayed()));
		onView(withText(SERVER_THREE)).check(matches(isDisplayed()));
		//wrong server tests
		onView(withText("Custom")).perform(click());
		ViewInteraction textAppUrl = onView(withId(R.id.txtAppUrl));
		textAppUrl.check(matches(withHint(WEBAPP_URL)));

		textAppUrl.perform(replaceText("something"), closeSoftKeyboard());
		onView(withId(R.id.btnSaveSettings)).perform(click());
		textAppUrl.check(matches(hasErrorText("must be a valid URL")));
		pressBack();

	}

	@Test
	public void testValidServerUrl() {
		//select valid instance
		onView(withText(SERVER_ONE)).check(matches(isDisplayed()));
		DataInteraction linearLayout = onData(anything())
				.inAdapterView(allOf(withId(R.id.lstServers),
						childAtPosition(
								withId(android.R.id.content),
								0)))
				.atPosition(2);
		linearLayout.perform(click());

		ViewInteraction webView = onView(
				allOf(withId(R.id.wbvMain),
						withParent(allOf(withId(R.id.lytWebView),
								withParent(withId(android.R.id.content)))),
						isDisplayed()));
		webView.check(matches(isDisplayed()));
	}

	private static Matcher<View> childAtPosition(
			final Matcher<View> parentMatcher, final int position) {

		return new TypeSafeMatcher<View>() {
			@Override
			public void describeTo(Description description) {
				description.appendText("Child at position " + position + " in parent ");
				parentMatcher.describeTo(description);
			}

			@Override
			public boolean matchesSafely(View view) {
				ViewParent parent = view.getParent();
				return parent instanceof ViewGroup && parentMatcher.matches(parent)
						&& view.equals(((ViewGroup) parent).getChildAt(position));
			}
		};
	}
}
