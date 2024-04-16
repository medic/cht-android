package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.Utils.createUseragentFrom;
import static org.medicmobile.webapp.mobile.Utils.startAppActivityChain;

public class StartupActivity extends Activity {

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		trace(this, "onCreate()");
		configureAndStartNextActivity();
		startDomainVerificationActivity();
	}

	private void configureAndStartNextActivity() {
		configureHttpUseragent();

		if (hasEnoughFreeSpace()) {
			startAppActivityChain(this);
			return;
		}

		Intent intent = new Intent(this, FreeSpaceWarningActivity.class);
		startActivity(intent);
		finish();
	}

	private void startDomainVerificationActivity() {
		Context context = getApplicationContext();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Utils.checkIfDomainsAreVerified(context)) {
			Intent intent = new Intent(this, DomainVerificationActivity.class);
			startActivity(intent);
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
