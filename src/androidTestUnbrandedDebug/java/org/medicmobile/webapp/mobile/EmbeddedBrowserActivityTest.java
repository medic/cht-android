package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.Manifest;
import android.os.SystemClock;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.offlinesync.OfflineSyncManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONTokener;

@RunWith(AndroidJUnit4.class)
public class EmbeddedBrowserActivityTest {

	@Rule
	public GrantPermissionRule permissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS);

	@Test
	public void recreating_keepsTheSameManager() {
		AtomicReference<OfflineSyncManager> manager = new AtomicReference<>();
		try (ActivityScenario<EmbeddedBrowserActivity> scenario = ActivityScenario.launch(EmbeddedBrowserActivity.class)) {
			scenario.onActivity(activity -> manager.set(activity.getOfflineSyncManager()));

			scenario.recreate();

			assertNotNull(manager.get());
			scenario.onActivity(activity -> assertSame(manager.get(), activity.getOfflineSyncManager()));
		}
	}

	@Test
	public void aHostLabel_reachesTheWebappAsText() throws Exception {
		String label = "x%22);}}catch(e){}window.injected=true;try{if(1){(%22";
		try (ActivityScenario<EmbeddedBrowserActivity> scenario = ActivityScenario.launch(EmbeddedBrowserActivity.class)) {
			scenario.onActivity(activity -> activity.evaluateJavascript(
					"window.CHTCore = { AndroidApi: { v1: { resolveOfflineSyncPairing: (ok, detail) => { window.received = detail; } } } };",
					false));

			scenario.onActivity(activity -> activity.resolveOfflineSyncPairing(true, label));

			assertEquals(label, new JSONTokener(waitFor(scenario, "window.received")).nextValue());
			assertEquals("\"undefined\"", read(scenario, "typeof window.injected"));
		}
	}

	private static String waitFor(ActivityScenario<EmbeddedBrowserActivity> scenario, String expression) throws Exception {
		long deadline = SystemClock.uptimeMillis() + 5000;
		String value = read(scenario, expression);
		while ("null".equals(value) && SystemClock.uptimeMillis() < deadline) {
			SystemClock.sleep(100);
			value = read(scenario, expression);
		}
		return value;
	}

	private static String read(ActivityScenario<EmbeddedBrowserActivity> scenario, String expression) throws Exception {
		AtomicReference<String> value = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		scenario.onActivity(activity -> ((WebView) activity.findViewById(R.id.wbvMain))
				.evaluateJavascript(expression, result -> {
					value.set(result);
					done.countDown();
				}));
		done.await(5, TimeUnit.SECONDS);
		return value.get();
	}
}
