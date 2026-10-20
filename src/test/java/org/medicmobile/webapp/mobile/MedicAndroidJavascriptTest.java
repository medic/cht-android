package org.medicmobile.webapp.mobile;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.offlinesync.OfflineSyncForegroundService;
import org.medicmobile.webapp.mobile.offlinesync.OfflineSyncManager;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MedicAndroidJavascriptTest {

	private EmbeddedBrowserActivity screen;
	private OfflineSyncManager manager;

	@Before
	public void setUp() {
		screen = mock(EmbeddedBrowserActivity.class);
		manager = mock(OfflineSyncManager.class);
		when(screen.getOfflineSyncManager()).thenReturn(manager);
		when(screen.getPackageName()).thenReturn("org.medicmobile.webapp.mobile");
	}

	private OfflineSyncManager.HostingCallback aNewBridgesHostingCallback() {
		new MedicAndroidJavascript(screen);
		ArgumentCaptor<OfflineSyncManager.HostingCallback> redirected =
				ArgumentCaptor.forClass(OfflineSyncManager.HostingCallback.class);
		verify(manager).redirect(redirected.capture());
		return redirected.getValue();
	}

	private static boolean isTheSessionService(Intent intent) {
		return intent.getComponent() != null
				&& OfflineSyncForegroundService.class.getName().equals(intent.getComponent().getClassName());
	}

	@Test
	public void aNewScreen_hearsAboutTheRunningHostingSession() {
		aNewBridgesHostingCallback().onLost("hotspot_stopped");

		verify(screen).callOfflineSyncWebapp(
				eq("resolveOfflineSyncHostingResult"), contains("false, \"hotspot_stopped\""));
	}

	@Test
	public void aReadyHotspot_keepsTheSessionAliveInTheBackground() {
		aNewBridgesHostingCallback().onReady("qr", "ssid", "password");

		verify(screen).startForegroundService(argThat(MedicAndroidJavascriptTest::isTheSessionService));
	}

	@Test
	public void aLostHotspot_releasesTheSession() {
		aNewBridgesHostingCallback().onLost("hotspot_stopped");

		verify(screen).stopService(argThat(MedicAndroidJavascriptTest::isTheSessionService));
	}
}
