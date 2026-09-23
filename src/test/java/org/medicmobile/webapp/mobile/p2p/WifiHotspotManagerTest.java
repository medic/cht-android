package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class WifiHotspotManagerTest {

	private static final String SSID = "CHT-P2P-a3f7";
	private static final String PASSWORD = "a-password";
	private static final String IP = "192.168.49.1";

	private HotspotProvider provider;
	private WifiHotspotManager manager;

	@Before public void setUp() {
		provider = mock(HotspotProvider.class);
		manager = new WifiHotspotManager(provider);
	}

	/** Answers a startHotspot call by reporting success back through the provider's callback. */
	private void providerStartsSuccessfully() {
		doAnswer(invocation -> {
			HotspotProvider.HotspotCallback cb = invocation.getArgument(0);
			cb.onStarted(SSID, PASSWORD, IP);
			return null;
		}).when(provider).start(any());
	}

	@Test public void constructor_rejectsAMissingProvider() {
		assertThrows(IllegalArgumentException.class, () -> new WifiHotspotManager(null));
	}

	@Test public void startHotspot_keepsTheCredentialsTheProviderReported() {
		providerStartsSuccessfully();
		// down before the call, up after, the way a real provider behaves
		when(provider.isRunning()).thenReturn(false, true);

		manager.startHotspot(mock(HotspotProvider.HotspotCallback.class));

		assertEquals(SSID, manager.getActiveSsid());
		assertEquals(PASSWORD, manager.getActivePassword());
		assertEquals(IP, manager.getActiveIpAddress());
		assertTrue(manager.isActive());
	}

	@Test public void startHotspot_passesSuccessOnToTheCaller() {
		providerStartsSuccessfully();
		HotspotProvider.HotspotCallback callback = mock(HotspotProvider.HotspotCallback.class);

		manager.startHotspot(callback);

		verify(callback).onStarted(SSID, PASSWORD, IP);
	}

	@Test public void startHotspot_passesFailureOnToTheCaller() {
		doAnswer(invocation -> {
			HotspotProvider.HotspotCallback cb = invocation.getArgument(0);
			cb.onFailed("no wifi hardware");
			return null;
		}).when(provider).start(any());
		HotspotProvider.HotspotCallback callback = mock(HotspotProvider.HotspotCallback.class);

		manager.startHotspot(callback);

		verify(callback).onFailed("no wifi hardware");
		assertNull(manager.getActiveSsid());
	}

	/** Once the provider reports itself up, a second start must reuse it rather than start again. */
	@Test public void startHotspot_doesNotStartTwice() {
		providerStartsSuccessfully();
		when(provider.isRunning()).thenReturn(false, true);

		manager.startHotspot(mock(HotspotProvider.HotspotCallback.class));
		HotspotProvider.HotspotCallback second = mock(HotspotProvider.HotspotCallback.class);
		manager.startHotspot(second);

		verify(provider).start(any());
		// the second caller still gets the live credentials back
		verify(second).onStarted(SSID, PASSWORD, IP);
	}

	@Test public void stopHotspot_clearsTheCredentials() {
		providerStartsSuccessfully();
		when(provider.isRunning()).thenReturn(false, true);
		manager.startHotspot(mock(HotspotProvider.HotspotCallback.class));

		manager.stopHotspot();

		verify(provider).stop();
		assertNull(manager.getActiveSsid());
		assertNull(manager.getActivePassword());
	}

	@Test public void stopHotspot_doesNothingWhenNeverStarted() {
		manager.stopHotspot();

		verify(provider, never()).stop();
	}

}
