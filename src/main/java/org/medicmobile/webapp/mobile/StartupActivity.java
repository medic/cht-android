package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.APPLICATION_ID;
import static org.medicmobile.webapp.mobile.BuildConfig.VERSION_NAME;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.Utils.startAppActivityChain;

public class StartupActivity extends Activity {
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(DEBUG) trace(this, "Starting...");

		configureAndStartNextActivity();

		if(LockScreen.isCodeSet(this)) {
			LockScreen.showFrom(this);
		}
	}

	private void configureAndStartNextActivity() {
		configureHttpUseragent();

		if(hasEnoughFreeSpace()) startAppActivityChain(this);
		else {
			Intent i = new Intent(this, FreeSpaceWarningActivity.class);
			startActivity(i);
			finish();
		}
	}

	private boolean hasEnoughFreeSpace() {
		long freeSpace = getFilesDir().getFreeSpace();

		return freeSpace > FreeSpaceWarningActivity.MINIMUM_SPACE;
	}

	private void configureHttpUseragent() {
		String current = System.getProperty("http.agent");
		if(current.contains(APPLICATION_ID)) return;
		System.setProperty("http.agent", String.format("%s %s/%s",
				current, APPLICATION_ID, VERSION_NAME));
	}
}
