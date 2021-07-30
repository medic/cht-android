package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.ACCESS_STORAGE_PERMISSION_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.EmbeddedBrowserActivity.CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class ChtExternalAppLauncherActivity {

	private final Activity ctx;
	private final static String[] PERMISSIONS_STORAGE = { READ_EXTERNAL_STORAGE };
	private Intent lastIntent;

	ChtExternalAppLauncherActivity(Activity ctx) {
		this.ctx = ctx;
	}

	String processActivity(int requestCode, int resultCode, Intent intentData) {
		trace(this, "ChtExternalAppLauncherActivity :: process request code: %s", requestCode);

		if (resultCode != RESULT_OK) {
			throw new RuntimeException("ChtExternalAppLauncherActivity :: Bad result code: " +
					resultCode +
					". The external app either: explicitly returned this result, didn't return any result or crashed during the operation.");
		}

		switch (requestCode) {
			case CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE:
				return ChtExternalAppLauncher.processResponse(intentData);

			default:
				throw new RuntimeException("ChtExternalAppLauncherActivity :: Unsupported request code: " + requestCode);
		}
	}

	void startIntent(ChtExternalApp chtExternalApp) {
		Intent intent = ChtExternalAppLauncher.createIntent(chtExternalApp);

		if (ContextCompat.checkSelfPermission(this.ctx, READ_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
			trace(this, "ChtExternalAppLauncherActivity :: Requesting storage permissions to process image files taken from external apps");
			this.lastIntent = intent; // Saving intent to start it when permission is granted.
			ActivityCompat.requestPermissions(this.ctx, PERMISSIONS_STORAGE, ACCESS_STORAGE_PERMISSION_REQUEST_CODE);
			return;
		}

		startActivity(intent);
	}

	void resumeActivity() {
		if (this.lastIntent == null) {
			return;
		}

		startActivity(this.lastIntent);
		this.lastIntent = null; // Cleaning as we don't need it anymore.
	}

	//> PRIVATE

	private void startActivity(Intent intent) {
		try {
			trace(this, "ChtExternalAppLauncherActivity :: Starting activity %s %s", intent, intent.getExtras());
			this.ctx.startActivityForResult(intent, CHT_EXTERNAL_APP_ACTIVITY_REQUEST_CODE);

		} catch (Exception exception) {
			error(exception, "ChtExternalAppLauncherActivity :: Error when starting the activity %s %s", intent, intent.getExtras());
		}
	}
}
