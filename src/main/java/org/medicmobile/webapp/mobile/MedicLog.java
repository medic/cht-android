package org.medicmobile.webapp.mobile;

import static android.util.Log.d;
import static android.util.Log.e;
import static android.util.Log.i;
import static android.util.Log.w;
import static java.lang.String.format;
import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.BuildConfig.LOG_TAG;

public final class MedicLog {
	private MedicLog() {}

	public static void trace(Object caller, String message, Object... extras) {
		if(!DEBUG) return;
		d(LOG_TAG, messageWithCaller(caller, message, extras));
	}

	public static void trace(Exception ex, String message, Object... extras) {
		if(!DEBUG) return;
		d(LOG_TAG, format(message, extras), ex);
	}

	public static void log(Object caller, String message, Object... extras) {
		i(LOG_TAG, messageWithCaller(caller, message, extras));
	}

	public static void log(Exception ex, String message, Object... extras) {
		i(LOG_TAG, format(message, extras), ex);
	}

	public static void warn(Object caller, String message, Object... extras) {
		w(LOG_TAG, messageWithCaller(caller, message, extras));
	}

	public static void warn(Exception ex, String message, Object... extras) {
		w(LOG_TAG, format(message, extras), ex);
	}

	public static void error(Exception ex, String message, Object... extras) {
		e(LOG_TAG, format(message, extras), ex);
	}

	private static String messageWithCaller(Object caller, String message, Object... extras) {
		Class<?> callerClass = caller instanceof Class ? (Class) caller : caller.getClass();
		return format("%s :: %s", callerClass.getName(), format(message, extras));
	}
}
