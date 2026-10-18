package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.Manifest;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.offlinesync.OfflineSyncManager;

import java.util.concurrent.atomic.AtomicReference;

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
}
