package org.medicmobile.webapp.mobile;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.medicmobile.webapp.mobile.offlinesync.OfflineSyncManager;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class MedicAndroidJavascriptTest {

	@Test
	public void aNewScreen_hearsAboutTheRunningHostingSession() {
		EmbeddedBrowserActivity screen = mock(EmbeddedBrowserActivity.class);
		OfflineSyncManager manager = mock(OfflineSyncManager.class);
		when(screen.getOfflineSyncManager()).thenReturn(manager);
		ArgumentCaptor<OfflineSyncManager.HostingCallback> redirected =
				ArgumentCaptor.forClass(OfflineSyncManager.HostingCallback.class);

		new MedicAndroidJavascript(screen);
		verify(manager).redirect(redirected.capture());
		redirected.getValue().onLost("hotspot_stopped");

		verify(screen).evaluateJavascript(
				contains("resolveOfflineSyncHostingResult(false, \"hotspot_stopped\""), eq(false));
	}
}
