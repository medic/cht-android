package org.medicmobile.webapp.mobile;

import static android.webkit.WebViewClient.ERROR_CONNECT;
import static android.webkit.WebViewClient.ERROR_HOST_LOOKUP;
import static android.webkit.WebViewClient.ERROR_PROXY_AUTHENTICATION;
import static android.webkit.WebViewClient.ERROR_TIMEOUT;

/**
 * Connection util methods.
 */
public abstract class ConnectionUtils {

	public static boolean isConnectionError(int errorCode) {
		switch (errorCode) {
			case ERROR_HOST_LOOKUP:
			case ERROR_PROXY_AUTHENTICATION:
			case ERROR_CONNECT:
			case ERROR_TIMEOUT:
				return true;
		}
		return false;
	}

	public static String connectionErrorToString(int errorCode, String errorDescription) {
		return String.format("%s [%s]", errorDescription, errorCode);
	}
}
