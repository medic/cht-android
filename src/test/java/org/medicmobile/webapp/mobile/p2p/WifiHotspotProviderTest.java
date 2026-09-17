package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import android.location.LocationManager;
import android.net.wifi.WifiManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
	* LocalOnlyHotspot needs API 26 while the app supports 21, so the gate is exercised on both
	* sides of that line. @TargetApi only silences lint, it does not stop the call at runtime.
	*/
@RunWith(RobolectricTestRunner.class)
public class WifiHotspotProviderTest {

	/**
		* Location switched on, which is the normal case; the off case has its own test.
		*
		* Only the provider check is stubbed: isLocationEnabled arrived in API 28 and these run
		* below it, where even stubbing the method throws because it does not exist yet.
		*/
	private LocationManager locationManager() {
		return locationManager(true);
	}

	private LocationManager locationManager(boolean on) {
		LocationManager locationManager = mock(LocationManager.class);
		when(locationManager.isProviderEnabled(org.mockito.ArgumentMatchers.anyString())).thenReturn(on);
		return locationManager;
	}

	// Robolectric 4.16 ships no image below API 25, so the "old device" cases run at the highest
	// SDK that still lacks LocalOnlyHotspot. The guard is a single >= 26 comparison, so this
	// exercises the same branch a real API 21 device would take.
	@Test @Config(sdk = 25)
	public void isSupported_isFalseJustBelowApi26() {
		assertFalse(WifiHotspotProvider.isSupported());
	}

	@Test @Config(sdk = 26)
	public void isSupported_isTrueFromApi26() {
		assertTrue(WifiHotspotProvider.isSupported());
	}

	/**
		* The whole point of the gate: an old device must be told, not crashed.
		*
		* The assertion cannot name startLocalOnlyHotspot here, because below API 26 the method does
		* not exist on WifiManager at all and even referencing it throws NoSuchMethodError. That is
		* exactly the crash this guard prevents, so "the provider touched nothing" is the assertion.
		*/
	@Test @Config(sdk = 25)
	public void start_failsCleanlyBelowApi26AndNeverTouchesTheWifiApi() {
		WifiManager wifiManager = mock(WifiManager.class);
		HotspotProvider.HotspotCallback callback = mock(HotspotProvider.HotspotCallback.class);

		new WifiHotspotProvider(wifiManager, locationManager()).start(callback);

		verify(callback).onFailed("hotspot_unsupported");
		verifyNoInteractions(wifiManager);
	}

	@Test @Config(sdk = 26)
	public void start_reachesTheWifiApiFromApi26() {
		WifiManager wifiManager = mock(WifiManager.class);
		HotspotProvider.HotspotCallback callback = mock(HotspotProvider.HotspotCallback.class);

		new WifiHotspotProvider(wifiManager, locationManager()).start(callback);

		verify(wifiManager).startLocalOnlyHotspot(any(), any());
		verify(callback, never()).onFailed("hotspot_unsupported");
	}

	@Test @Config(sdk = 26)
	public void start_rejectsAMissingCallback() {
		WifiHotspotProvider provider = new WifiHotspotProvider(mock(WifiManager.class), locationManager());

		try {
			provider.start(null);
			org.junit.Assert.fail("expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			// the contract says the callback is required
		}
	}

	/**
		* Granting location access is not the same as having it switched on, and the platform
		* refuses either way. Saying which one it is saves the user guessing.
		*/
	@Test @Config(sdk = 26)
	public void start_saysWhenLocationIsSwitchedOff() {
		WifiManager wifiManager = mock(WifiManager.class);
		HotspotProvider.HotspotCallback callback = mock(HotspotProvider.HotspotCallback.class);

		new WifiHotspotProvider(wifiManager, locationManager(false)).start(callback);

		verify(callback).onFailed("location_services_off");
		verifyNoInteractions(wifiManager);
	}

	/** From API 28 the platform has a single answer, so that path is checked separately. */
	@Test @Config(sdk = 28)
	public void start_usesThePlatformLocationCheckFromApi28() {
		WifiManager wifiManager = mock(WifiManager.class);
		LocationManager locationManager = mock(LocationManager.class);
		when(locationManager.isLocationEnabled()).thenReturn(false);
		HotspotProvider.HotspotCallback callback = mock(HotspotProvider.HotspotCallback.class);

		new WifiHotspotProvider(wifiManager, locationManager).start(callback);

		verify(callback).onFailed("location_services_off");
		verifyNoInteractions(wifiManager);
	}

	@Test @Config(sdk = 26)
	public void isRunning_isFalseBeforeStarting() {
		assertFalse(new WifiHotspotProvider(mock(WifiManager.class), locationManager()).isRunning());
	}
}
