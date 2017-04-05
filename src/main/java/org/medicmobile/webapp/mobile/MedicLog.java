package org.medicmobile.webapp.mobile;

import static android.util.Log.d;
import static android.util.Log.i;
import static android.util.Log.w;
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.LOG_TAG;

public final class MedicLog {
	public static void log(String message, Object... extras) {
		message = String.format(message, extras);

		i(LOG_TAG, message);
	}

	public static void trace(Object caller, String message, Object... extras) {
		if(!DEBUG) return;

		message = String.format(message, extras);
		d(LOG_TAG, caller.getClass().getName() + " :: " + message);
	}

	public static void warn(Exception ex, String message, Object... extras) {
		message = String.format(message, extras);

		w(LOG_TAG, message, ex);
	}
}
