package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.Optional;

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
	static final String[] LOCATION_PERMISSIONS = { ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION };

	private final ActivityResultLauncher<String[]> requestPermissionLauncher =
		registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantedMap -> {
			boolean fineLocationGranted = Optional
				.ofNullable(grantedMap.get(ACCESS_FINE_LOCATION))
				.orElse(false);
			boolean coarseLocationGranted = Optional
				.ofNullable(grantedMap.get(ACCESS_COARSE_LOCATION))
				.orElse(false);

			if (fineLocationGranted || coarseLocationGranted) {
				trace(
					this,
					"RequestLocationPermissionActivity :: User allowed at least one location permission. Fine:%s, Coarse:%s",
					fineLocationGranted,
					coarseLocationGranted
				);
				setResult(RESULT_OK);
				finish();
				return;
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				boolean rationalFineLocation = !shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION);
				boolean rationalCoarseLocation = !shouldShowRequestPermissionRationale(ACCESS_COARSE_LOCATION);

				if (rationalFineLocation || rationalCoarseLocation) {
					trace(
						this,
						"RequestLocationPermissionActivity :: User rejected all location permissions twice or has selected \"never ask again\"." +
							" Sending user to the app's setting to manually grant the permission."
					);
					Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
					intent.setData(Uri.fromParts("package", getPackageName(), null));
					this.appSettingsLauncher.launch(intent);
					return;
				}
			}

			trace(this, "RequestLocationPermissionActivity :: User rejected location permission.");
			setResult(RESULT_CANCELED);
			finish();
		});

	private final ActivityResultLauncher<Intent> appSettingsLauncher =
		registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			boolean hasFineLocation = ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED;
			boolean hasCoarseLocation = ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED;

			if (hasFineLocation || hasCoarseLocation) {
				trace(
					this,
					"RequestLocationPermissionActivity :: User granted at least one location permission from app's settings. Fine:%s, Coarse:%s",
					hasFineLocation,
					hasCoarseLocation
				);
				setResult(RESULT_OK);
				finish();
				return;
			}

			trace(this, "RequestLocationPermissionActivity :: User didn't grant location permission from app's settings.");
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
		TextView field = findViewById(R.id.locMessageText);
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
