package org.medicmobile.webapp.mobile;

import android.webkit.WebResourceError;

import static android.webkit.WebViewClient.ERROR_CONNECT;
import static android.webkit.WebViewClient.ERROR_HOST_LOOKUP;
import static android.webkit.WebViewClient.ERROR_PROXY_AUTHENTICATION;
import static android.webkit.WebViewClient.ERROR_TIMEOUT;

/**
 * Connection util methods.
 */
public abstract class ConnectionUtils {

	public static boolean isConnectionError(WebResourceError error) {
		switch (error.getErrorCode()) {
			case ERROR_HOST_LOOKUP:
			case ERROR_PROXY_AUTHENTICATION:
			case ERROR_CONNECT:
			case ERROR_TIMEOUT:
				return true;
		}
		return false;
	}

	public static String connectionErrorToString(WebResourceError error) {
		return String.format("%s [%s]", error.getDescription(), error.getErrorCode());
	}
}
