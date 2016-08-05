package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.webkit.*;
import android.os.*;

public class StartupActivity extends Activity {
	private static final boolean DEBUG = BuildConfig.DEBUG;

	public void onCreate(Bundle savedInstanceState) {
		if(DEBUG) log("Starting...");
		super.onCreate(savedInstanceState);

		Class newActivity;
		if(true) {
			newActivity = StorageUsageActivity.class;
		} else if(SettingsStore.in(this).hasSettings()) {
			newActivity = EmbeddedBrowserActivity.class;
		} else {
			newActivity = SettingsDialogActivity.class;
		}

		if(DEBUG) log("Starting new activity with class %s", newActivity);

		if(hasEnoughFreeSpace()) {
			startActivity(new Intent(this, newActivity));
		} else {
			Intent i = new Intent(this, FreeSpaceWarningActivity.class);
			i.putExtra(FreeSpaceWarningActivity.NEXT_ACTIVITY, newActivity);
			startActivity(i);
		}

		finish();
	}

	private boolean hasEnoughFreeSpace() {
		long freeSpace = getFilesDir().getFreeSpace();

		return freeSpace > FreeSpaceWarningActivity.MINIMUM_SPACE;
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | StartupActivity :: " +
				String.format(message, extras));
	}
}
