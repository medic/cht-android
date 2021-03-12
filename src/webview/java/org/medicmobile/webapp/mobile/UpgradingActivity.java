package org.medicmobile.webapp.mobile;

import android.os.Bundle;
import android.view.Window;

public class UpgradingActivity extends LockableActivity {

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.upgrading);
	}
}
