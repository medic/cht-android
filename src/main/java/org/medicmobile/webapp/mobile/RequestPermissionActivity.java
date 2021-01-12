package org.medicmobile.webapp.mobile;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import static org.medicmobile.webapp.mobile.MedicLog.trace;

/**
 * Shows a confirmation view that displays a "prominent" disclosure about how
 * the user geolocation data is used, asking to confirm whether to allow the app to
 * access the location or not.
 *
 * If the user accepts, a request to the API to access the location is made by the main activity,
 * but Android will show another confirmation dialog. If the user decline the first
 * confirmation, the request to the API is omitted and the decision recorded to avoid
 * requesting the same next time.
 */
public class RequestPermissionActivity extends LockableActivity {

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.request_permission);
		String message = getResources().getString(R.string.locRequestMessage);
		String appName = getResources().getString(R.string.app_name);
		TextView field = (TextView) findViewById(R.id.locMessageText);
		field.setText(String.format(message, appName));
	}

	public void onClickOk(View view) {
		trace(this, "onClickOk() :: user accepted to share the location");
		setResult(RESULT_OK);
		finish();
	}

	public void onClickNegative(View view) {
		trace(this, ":: onClickNegative() :: user denied to share the location");
		setResult(RESULT_CANCELED);
		finish();
	}
}
