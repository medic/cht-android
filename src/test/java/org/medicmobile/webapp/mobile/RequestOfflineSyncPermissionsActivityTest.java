package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.NEARBY_WIFI_DEVICES;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

@RunWith(RobolectricTestRunner.class)
public class RequestOfflineSyncPermissionsActivityTest {

	private Application app() {
		return RuntimeEnvironment.getApplication();
	}

	private void grant(String permission) {
		ShadowApplication shadow = Shadows.shadowOf(app());
		shadow.grantPermissions(permission);
	}

	/** Android 13 introduced a dedicated permission; before it the capability sat behind location. */
	@Test @Config(sdk = 33)
	public void requiredPermissions_isNearbyWifiFromAndroid13() {
		assertArrayEquals(new String[] { NEARBY_WIFI_DEVICES },
				RequestOfflineSyncPermissionsActivity.requiredPermissions());
	}

	@Test @Config(sdk = 26)
	public void requiredPermissions_isLocationBeforeAndroid13() {
		assertArrayEquals(new String[] { ACCESS_FINE_LOCATION },
				RequestOfflineSyncPermissionsActivity.requiredPermissions());
	}

	@Test @Config(sdk = 26)
	public void hasOfflineSyncPermissions_isFalseUntilLocationIsGranted() {
		assertFalse(RequestOfflineSyncPermissionsActivity.hasOfflineSyncPermissions(app()));

		grant(ACCESS_FINE_LOCATION);

		assertTrue(RequestOfflineSyncPermissionsActivity.hasOfflineSyncPermissions(app()));
	}

	@Test @Config(sdk = 33)
	public void hasOfflineSyncPermissions_isFalseUntilNearbyWifiIsGranted() {
		assertFalse(RequestOfflineSyncPermissionsActivity.hasOfflineSyncPermissions(app()));

		grant(NEARBY_WIFI_DEVICES);

		assertTrue(RequestOfflineSyncPermissionsActivity.hasOfflineSyncPermissions(app()));
	}

	/** Location alone is not enough on 13+, which is the version trap this guards against. */
	@Test @Config(sdk = 33)
	public void hasOfflineSyncPermissions_isNotSatisfiedByLocationOnAndroid13() {
		grant(ACCESS_FINE_LOCATION);

		assertFalse(RequestOfflineSyncPermissionsActivity.hasOfflineSyncPermissions(app()));
	}
}
