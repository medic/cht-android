package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentActivity;

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
public class RequestLocationPermissionActivity extends FragmentActivity {
	/**
	 * TRIGGER_CLASS {String} Extra in the request intent to specify the class that trigger this activity,
     *                        it will be passed on to the result intent. It can be used to continue the
	 *                        action of the trigger class after the intent is resolved.
	 */
	static final String TRIGGER_CLASS = "TRIGGER_CLASS";
	static final String[] LOCATION_PERMISSIONS = { ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION };

	private ActivityResultLauncher<String[]> requestPermissionLauncher =
		registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantedMap -> {
			boolean allGranted = !grantedMap.containsValue(false);

			if (allGranted) {
				trace(this, "RequestLocationPermissionActivity :: User allowed location permission.");

				Intent requestIntent = getIntent();
				String triggerClass = requestIntent == null ? null : requestIntent.getStringExtra(TRIGGER_CLASS);
				Intent responseIntent = new Intent();

				if (triggerClass != null && !triggerClass.isEmpty()) {
					responseIntent.putExtra(TRIGGER_CLASS, triggerClass);
				}

				setResult(RESULT_OK, responseIntent);
				finish();
				return;
			}

			trace(this, "RequestLocationPermissionActivity :: User rejected location permission.");
			setResult(RESULT_CANCELED);
			finish();
		});

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.request_location_permission);

		String appName = getResources().getString(R.string.app_name);
		String message = getResources().getString(R.string.locRequestMessage);
		TextView field = (TextView) findViewById(R.id.locMessageText);
		field.setText(String.format(message, appName));
	}

	public void onClickOk(View view) {
		trace(this, "RequestLocationPermissionActivity :: User agree with prominent disclosure message.");
		requestPermissionLauncher.launch(LOCATION_PERMISSIONS);
	}

	public void onClickNegative(View view) {
		trace(this, "RequestLocationPermissionActivity :: User disagree with prominent disclosure message.");
		setResult(RESULT_CANCELED);
		finish();
	}
}
