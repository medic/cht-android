package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QrScannerActivityTest {

	@Rule
	public GrantPermissionRule cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA);

	@Test
	public void opensTheScannerScreen() {
		Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
		Instrumentation.ActivityMonitor scanner = instrumentation.addMonitor(
				PortraitCaptureActivity.class.getName(), null, false);

		Activity opened;
		try (ActivityScenario<QrScannerActivity> ignored = ActivityScenario.launch(QrScannerActivity.class)) {
			opened = instrumentation.waitForMonitorWithTimeout(scanner, 10_000);
			assertNotNull("the scanner screen never opened", opened);
		} finally {
			instrumentation.removeMonitor(scanner);
		}
		instrumentation.runOnMainSync(opened::finish);
	}
}
