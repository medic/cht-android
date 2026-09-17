package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.ConnectivityManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class HotspotJoinerTest {

	private static final String SSID = "CHT-P2P-a3f7";
	private static final String PASSWORD = "a-password";

	private ConnectivityManager connectivityManager;
	private HotspotJoiner joiner;
	private HotspotJoiner.JoinCallback callback;

	@Before public void setUp() {
		connectivityManager = mock(ConnectivityManager.class);
		joiner = new HotspotJoiner(connectivityManager);
		callback = mock(HotspotJoiner.JoinCallback.class);
	}

	@Test public void constructor_rejectsAMissingConnectivityManager() {
		assertThrows(IllegalArgumentException.class, () -> new HotspotJoiner(null));
	}

	@Test @Config(sdk = 28)
	public void isSupported_isFalseBeforeAndroid10() {
		assertFalse(HotspotJoiner.isSupported());
	}

	@Test @Config(sdk = 29)
	public void isSupported_isTrueFromAndroid10() {
		assertTrue(HotspotJoiner.isSupported());
	}

	/**
		* The pre-Android-10 way of adding a network was restricted in Android 10, so there is no
		* working fallback. Saying so is better than attempting it and failing obscurely.
		*/
	@Test @Config(sdk = 28)
	public void join_reportsUnsupportedBeforeAndroid10() {
		joiner.join(SSID, PASSWORD, callback);

		verify(callback).onFailed("join_unsupported");
		verify(connectivityManager, never()).requestNetwork(any(), any(ConnectivityManager.NetworkCallback.class), anyInt());
	}

	@Test @Config(sdk = 29)
	public void join_asksTheSystemForTheNetwork() {
		joiner.join(SSID, PASSWORD, callback);

		verify(connectivityManager).requestNetwork(any(), any(ConnectivityManager.NetworkCallback.class), anyInt());
	}

	@Test @Config(sdk = 29)
	public void join_rejectsCredentialsItCannotUse() {
		joiner.join(null, PASSWORD, callback);
		joiner.join("", PASSWORD, callback);
		joiner.join(SSID, null, callback);
		joiner.join(SSID, "", callback);

		verify(callback, org.mockito.Mockito.times(4)).onFailed("invalid_credentials");
		verify(connectivityManager, never()).requestNetwork(any(), any(ConnectivityManager.NetworkCallback.class), anyInt());
	}

	@Test @Config(sdk = 29)
	public void join_rejectsAMissingCallback() {
		assertThrows(IllegalArgumentException.class, () -> joiner.join(SSID, PASSWORD, null));
	}

	@Test @Config(sdk = 29)
	public void leave_releasesTheNetworkSoTheDeviceCanGoBackToItsUsualConnection() {
		joiner.join(SSID, PASSWORD, callback);

		joiner.leave();

		verify(connectivityManager).unregisterNetworkCallback(any(ConnectivityManager.NetworkCallback.class));
	}

	@Test @Config(sdk = 29)
	public void leave_doesNothingWhenNotJoined() {
		joiner.leave();

		verify(connectivityManager, never()).unregisterNetworkCallback(any(ConnectivityManager.NetworkCallback.class));
	}
}
