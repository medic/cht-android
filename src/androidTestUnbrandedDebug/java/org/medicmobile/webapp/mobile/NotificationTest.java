package org.medicmobile.webapp.mobile;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static androidx.test.espresso.web.webdriver.DriverAtoms.clearElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.findElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webClick;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.web.webdriver.DriverAtoms;
import androidx.test.espresso.web.webdriver.Locator;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RunWith(AndroidJUnit4.class)
public class NotificationTest {
	private Context context;
	private static final String TEST_SERVER = "";
	private static final String TEST_USERNAME = "";
	private static final String TEST_PASSWORD = "";

	@Rule
	public ActivityScenarioRule<SettingsDialogActivity> mActivityTestRule =
			new ActivityScenarioRule<>(SettingsDialogActivity.class);
	@Rule
	public GrantPermissionRule permissionRule =
			GrantPermissionRule.grant(
					Manifest.permission.POST_NOTIFICATIONS
			);

	@After
	public void cleanServerRepo() {
		context.getSharedPreferences("ServerRepo", Context.MODE_PRIVATE)
				.edit().clear().apply();
	}

	@Ignore("TODO: add TEST_SERVER")
	@SuppressLint("CheckResult")
	@Test
	public void startsNotificationWorkers() throws InterruptedException, ExecutionException {
		context = ApplicationProvider.getApplicationContext();
		onView(withText("Custom")).perform(click());
		ViewInteraction textAppUrl = onView(withId(R.id.txtAppUrl));
		textAppUrl.perform(replaceText(TEST_SERVER), closeSoftKeyboard());
		onView(withId(R.id.btnSaveSettings)).perform(click());

		Thread.sleep(4000);

		assertEquals("expect no worker running at login page", 0, getRunningWorkers());

		onWebView()
				.withNoTimeout()
				.withElement(findElement(Locator.ID, "user"))
				.perform(clearElement())
				.perform(DriverAtoms.webKeys(TEST_USERNAME))    //to be created first
				.withElement(findElement(Locator.ID, "password"))
				.perform(clearElement())
				.perform(DriverAtoms.webKeys(TEST_PASSWORD))
				.withElement(findElement(Locator.ID, "login"))
				.perform(webClick());

		Thread.sleep(10 * 1000);

		ActivityScenario<EmbeddedBrowserActivity> embeddedScenario = ActivityScenario.launch(EmbeddedBrowserActivity.class);
		AppNotificationManager ap = AppNotificationManager.getInstance(context, "");
		assertTrue("foreground handler running", ap.foregroundNotificationHandler.isRunning());
		assertEquals("expect no work manager while app is in foreground", 0, getRunningWorkers());

		//close app
		embeddedScenario.onActivity(EmbeddedBrowserActivity::onBackPressed);
		Thread.sleep(1000);
		assertFalse("foreground handler stops running", ap.foregroundNotificationHandler.isRunning());
		assertEquals("expect work manager enqueued while app is in background", 1, getRunningWorkers());

		//reopen app
		embeddedScenario = ActivityScenario.launch(EmbeddedBrowserActivity.class);
		assertEquals("no work manager on app restart", 0, getRunningWorkers());
		assertTrue("foreground handler running on restart", ap.foregroundNotificationHandler.isRunning());
	}

	private long getRunningWorkers() throws ExecutionException, InterruptedException {
		List<WorkInfo> workInfos = WorkManager.getInstance(context)
				.getWorkInfosByTag(NotificationWorker.NOTIFICATION_WORK_REQUEST_TAG)
				.get();
		return workInfos.stream()
				.filter(info -> info.getState() == WorkInfo.State.ENQUEUED || info.getState() == WorkInfo.State.RUNNING)
				.count();
	}
}