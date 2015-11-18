package org.medicmobile.webapp.mobile;

import android.webkit.*;

public class MedicAndroidJavascript {
	private SoundAlert soundAlert;

	public void setSoundAlert(SoundAlert soundAlert) {
		this.soundAlert = soundAlert;
	}

	@JavascriptInterface
	public int getAppVersion() {
		return BuildConfig.VERSION_CODE;
	}

	@JavascriptInterface
	public void playAlert() {
		if(soundAlert != null) soundAlert.trigger();
	}
}
