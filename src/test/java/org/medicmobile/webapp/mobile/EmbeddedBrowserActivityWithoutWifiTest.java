package org.medicmobile.webapp.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.offlinesync.OfflineSyncPeer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
public class EmbeddedBrowserActivityWithoutWifiTest {

	@Before
	public void setUp() {
		Application context = ApplicationProvider.getApplicationContext();
		WorkManagerTestInitHelper.initializeTestWorkManager(context, new Configuration.Builder().build());
		ReflectionHelpers.setStaticField(OfflineSyncPeer.class, "instance", null);
		shadowOf(context).setSystemService(Context.WIFI_SERVICE, null);
	}

	@After
	public void tearDown() {
		ReflectionHelpers.setStaticField(OfflineSyncPeer.class, "instance", null);
	}

	@Test
	public void opens_withoutOfflineSyncOnADeviceWithoutWifi() {
		try (ActivityScenario<EmbeddedBrowserActivity> scenario = ActivityScenario.launch(EmbeddedBrowserActivity.class)) {
			scenario.onActivity(activity -> {
				assertNull(activity.getOfflineSyncPeer());
				assertFalse(new MedicAndroidJavascript(activity).offline_sync_join_available());
			});
		}
	}
}
