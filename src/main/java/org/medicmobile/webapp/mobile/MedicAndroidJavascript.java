package org.medicmobile.webapp.mobile;

import android.webkit.*;

public class MedicAndroidJavascript {
	@JavascriptInterface
	public int getAppVersion() {
		return BuildConfig.VERSION_CODE;
	}
}
