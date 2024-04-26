package org.medicmobile.webapp.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

public class AppUrlIntentActivity extends Activity {
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent appLinkIntent = getIntent();
		Uri appLinkData = appLinkIntent.getData();

		if(DEBUG) trace(this, "onCreate() :: Token Login: %s", appLinkData.toString());

		startActivity(new Intent(Intent.ACTION_VIEW, appLinkData, this, EmbeddedBrowserActivity.class));
		finish();
	}
}
