package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import org.medicmobile.webapp.mobile.R;

/**
	* ZXing's own layout is the camera preview alone, so the only way out is the system back gesture.
	* A user who cannot find it is left looking at a camera with no way back.
	*/
@RunWith(RobolectricTestRunner.class)
public class PortraitCaptureActivityTest {

	@Test public void offersAWayOutOfTheScanner() {
		try (ActivityController<PortraitCaptureActivity> controller =
					Robolectric.buildActivity(PortraitCaptureActivity.class)) {
			Activity activity = controller.create().get();

			View cancel = activity.findViewById(R.id.offlineSyncScannerCancel);
			assertNotNull("the scanner must offer a way out", cancel);

			cancel.performClick();

			// finishing without a result is already reported to the webapp as a cancelled scan
			assertTrue(activity.isFinishing());
		}
	}
}
