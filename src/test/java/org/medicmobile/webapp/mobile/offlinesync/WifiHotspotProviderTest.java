package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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

@RunWith(RobolectricTestRunner.class)
public class WifiHotspotProviderTest {

	private LocationManager locationManager() {
		return locationManager(true);
	}

	private LocationManager locationManager(boolean on) {
		LocationManager locationManager = mock(LocationManager.class);
		when(locationManager.isProviderEnabled(org.mockito.ArgumentMatchers.anyString())).thenReturn(on);
		return locationManager;
	}

	@Test @Config(sdk = 25)
	public void isSupported_isFalseJustBelowApi26() {
		assertFalse(WifiHotspotProvider.isSupported());
	}

	@Test @Config(sdk = 26)
	public void isSupported_isTrueFromApi26() {
		assertTrue(WifiHotspotProvider.isSupported());
	}

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

		assertThrows(IllegalArgumentException.class, () -> provider.start(null));
	}

	@Test @Config(sdk = 26)
	public void start_saysWhenLocationIsSwitchedOff() {
		WifiManager wifiManager = mock(WifiManager.class);
		HotspotProvider.HotspotCallback callback = mock(HotspotProvider.HotspotCallback.class);

		new WifiHotspotProvider(wifiManager, locationManager(false)).start(callback);

		verify(callback).onFailed("location_services_off");
		verifyNoInteractions(wifiManager);
	}

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
