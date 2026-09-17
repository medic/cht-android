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
public class RequestP2pPermissionsActivityTest {

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
				RequestP2pPermissionsActivity.requiredPermissions());
	}

	@Test @Config(sdk = 26)
	public void requiredPermissions_isLocationBeforeAndroid13() {
		assertArrayEquals(new String[] { ACCESS_FINE_LOCATION },
				RequestP2pPermissionsActivity.requiredPermissions());
	}

	@Test @Config(sdk = 26)
	public void hasP2pPermissions_isFalseUntilLocationIsGranted() {
		assertFalse(RequestP2pPermissionsActivity.hasP2pPermissions(app()));

		grant(ACCESS_FINE_LOCATION);

		assertTrue(RequestP2pPermissionsActivity.hasP2pPermissions(app()));
	}

	@Test @Config(sdk = 33)
	public void hasP2pPermissions_isFalseUntilNearbyWifiIsGranted() {
		assertFalse(RequestP2pPermissionsActivity.hasP2pPermissions(app()));

		grant(NEARBY_WIFI_DEVICES);

		assertTrue(RequestP2pPermissionsActivity.hasP2pPermissions(app()));
	}

	/** Location alone is not enough on 13+, which is the version trap this guards against. */
	@Test @Config(sdk = 33)
	public void hasP2pPermissions_isNotSatisfiedByLocationOnAndroid13() {
		grant(ACCESS_FINE_LOCATION);

		assertFalse(RequestP2pPermissionsActivity.hasP2pPermissions(app()));
	}
}
