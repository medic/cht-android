package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Looper;

import java.time.Duration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class HotspotJoinerTest {

	private static final String SSID = "CHT-P2P-a3f7";
	private static final String PASSWORD = "a-password";

	private ConnectivityManager connectivityManager;
	private WifiManager wifiManager;
	private HotspotJoiner joiner;
	private HotspotJoiner.JoinCallback callback;

	@Before public void setUp() {
		connectivityManager = mock(ConnectivityManager.class);
		wifiManager = mock(WifiManager.class);
		joiner = new HotspotJoiner(connectivityManager, wifiManager);
		callback = mock(HotspotJoiner.JoinCallback.class);
	}

	@Test public void constructor_rejectsMissingSystemServices() {
		assertThrows(IllegalArgumentException.class, () -> new HotspotJoiner(null, wifiManager));
		assertThrows(IllegalArgumentException.class, () -> new HotspotJoiner(connectivityManager, null));
	}

	@Test @Config(sdk = 28)
	public void isSupported_isTrueOnOlderVersionsToo() {
		assertTrue(HotspotJoiner.SUPPORTED);
	}

	@Test @Config(sdk = 29)
	public void isSupported_isTrueFromAndroid10() {
		assertTrue(HotspotJoiner.SUPPORTED);
	}

	/** Android 9 and below save the network and switch to it; requestNetwork is not used there. */
	@Test @Config(sdk = 28)
	public void join_savesAndEnablesTheNetworkBeforeAndroid10() {
		when(wifiManager.addNetwork(any())).thenReturn(7);
		when(wifiManager.enableNetwork(anyInt(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);

		joiner.join(SSID, PASSWORD, callback);

		verify(wifiManager).addNetwork(any());
		verify(wifiManager).enableNetwork(7, true);
		verify(connectivityManager).registerNetworkCallback(any(), any(ConnectivityManager.NetworkCallback.class));
	}

	@Test @Config(sdk = 28)
	public void join_reportsFailureWhenTheDeviceWillNotSaveTheNetwork() {
		when(wifiManager.addNetwork(any())).thenReturn(-1);

		joiner.join(SSID, PASSWORD, callback);

		verify(callback).onFailed("join_failed");
		verify(wifiManager, never()).enableNetwork(anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
	}

	/**
		* Below Android 10 the platform gives requestNetwork's timeout no equivalent, so the join is
		* bounded here. Without it the user sits on "connecting" forever with nothing to act on.
		*/
	@Test @Config(sdk = 28)
	public void join_failsWhenTheNetworkNeverArrivesBeforeAndroid10() {
		when(wifiManager.addNetwork(any())).thenReturn(7);
		when(wifiManager.enableNetwork(anyInt(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);

		joiner.join(SSID, PASSWORD, callback);
		verify(callback, never()).onFailed(anyString());

		shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60_000));

		verify(callback).onFailed("join_failed");
	}

	/**
		* The legacy path can only watch wifi in general, so another network coming up must not be
		* mistaken for the host's.
		*/
	@Test @Config(sdk = 28)
	public void join_ignoresADifferentNetworkBeforeAndroid10() {
		when(wifiManager.addNetwork(any())).thenReturn(7);
		when(wifiManager.enableNetwork(anyInt(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);
		WifiInfo info = mock(WifiInfo.class);
		when(info.getSSID()).thenReturn("\"someone-elses-wifi\"");
		when(wifiManager.getConnectionInfo()).thenReturn(info);

		joiner.join(SSID, PASSWORD, callback);

		ArgumentCaptor<ConnectivityManager.NetworkCallback> captor =
				ArgumentCaptor.forClass(ConnectivityManager.NetworkCallback.class);
		verify(connectivityManager).registerNetworkCallback(any(), captor.capture());
		captor.getValue().onAvailable(mock(Network.class));

		verify(callback, never()).onJoined(any());
	}

	/** Android 10 broke addNetwork for apps, so it must not be attempted there. */
	@Test @Config(sdk = 29)
	public void join_doesNotUseTheLegacyPathFromAndroid10() {
		joiner.join(SSID, PASSWORD, callback);

		verify(wifiManager, never()).addNetwork(any());
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

		verify(callback, times(4)).onFailed("invalid_credentials");
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

	/**
		* Leaving a saved network behind would strand the user on a hotspot with no internet, and
		* the device would keep rejoining it whenever the supervisor is nearby.
		*/
	@Test @Config(sdk = 28)
	public void leave_takesTheSavedNetworkBackOffTheDevice() {
		when(wifiManager.addNetwork(any())).thenReturn(7);
		when(wifiManager.enableNetwork(anyInt(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);
		joiner.join(SSID, PASSWORD, callback);

		joiner.leave();

		verify(wifiManager).removeNetwork(7);
		verify(wifiManager).reconnect();
	}

	@Test @Config(sdk = 29)
	public void leave_hasNoSavedNetworkToRemoveFromAndroid10() {
		joiner.join(SSID, PASSWORD, callback);

		joiner.leave();

		verify(wifiManager, never()).removeNetwork(anyInt());
	}
}
