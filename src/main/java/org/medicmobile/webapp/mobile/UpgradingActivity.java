package org.medicmobile.webapp.mobile;

import android.os.Bundle;
import android.view.Window;


/**
 * Activity displayed when the upgrade from XWalk to WebView is running.
 */
public class UpgradingActivity extends ClosableAppActivity {

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.upgrading);
	}
}
