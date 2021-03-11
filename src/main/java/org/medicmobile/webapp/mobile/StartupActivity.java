package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.Utils.createUseragentFrom;
import static org.medicmobile.webapp.mobile.Utils.startAppActivityChain;

public class StartupActivity extends Activity {
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		trace(this, "onCreate()");

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
		System.setProperty("http.agent", createUseragentFrom(current));
	}
}
