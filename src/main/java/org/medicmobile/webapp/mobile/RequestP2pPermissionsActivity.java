package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.NEARBY_WIFI_DEVICES;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

import android.content.Context;
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

/**
 * Asks for the permissions a P2P sync session needs, with a disclosure first.
 *
 * Which permission that is depends on the Android version. Hosting a local-only hotspot needs
 * NEARBY_WIFI_DEVICES from Android 13, and location before that: the platform treated nearby wifi
 * as a way of inferring where the user is, so it gated it behind location. Neither is optional,
 * and without them startLocalOnlyHotspot throws.
 *
 * Follows RequestLocationPermissionActivity: explain first, then ask, and send the user to the app
 * settings if they have already refused twice.
 */
public class RequestP2pPermissionsActivity extends FragmentActivity {

	private final ActivityResultLauncher<String[]> requestPermissionLauncher =
		registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantedMap -> {
			if (hasP2pPermissions(this)) {
				trace(this, "RequestP2pPermissionsActivity :: User granted the P2P permissions.");
				setResult(RESULT_OK);
				finish();
				return;
			}

			if (shouldSendToAppSettings()) {
				trace(
					this,
					"RequestP2pPermissionsActivity :: User refused twice or selected \"never ask again\"." +
						" Sending user to the app's settings to grant it manually."
				);
				this.appSettingsLauncher.launch(appSettingsIntent());
				return;
			}

			trace(this, "RequestP2pPermissionsActivity :: User refused the P2P permissions.");
			setResult(RESULT_CANCELED);
			finish();
		});

	private final ActivityResultLauncher<Intent> appSettingsLauncher =
		registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			boolean granted = hasP2pPermissions(this);
			trace(this, "RequestP2pPermissionsActivity :: Returned from app settings, granted:%s", granted);
			setResult(granted ? RESULT_OK : RESULT_CANCELED);
			finish();
		});

	/**
	 * The permissions a hotspot needs on this device.
	 *
	 * From Android 13 the platform has a dedicated nearby-wifi permission; before that the same
	 * capability sat behind location.
	 */
	static String[] requiredPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return new String[] { NEARBY_WIFI_DEVICES };
		}
		return new String[] { ACCESS_FINE_LOCATION };
	}

	/** Whether this device already has what a session needs. */
	public static boolean hasP2pPermissions(Context context) {
		for (String permission : requiredPermissions()) {
			if (ContextCompat.checkSelfPermission(context, permission) != PERMISSION_GRANTED) {
				return false;
			}
		}
		return true;
	}

	private boolean shouldSendToAppSettings() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return false;
		}
		for (String permission : requiredPermissions()) {
			if (!shouldShowRequestPermissionRationale(permission)) {
				return true;
			}
		}
		return false;
	}

	private Intent appSettingsIntent() {
		Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		intent.setData(Uri.fromParts("package", getPackageName(), null));
		return intent;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.request_p2p_permission);

		String appName = getResources().getString(R.string.app_name);
		String message = getResources().getString(R.string.p2pRequestMessage);
		TextView field = findViewById(R.id.p2pMessageText);
		field.setText(String.format(message, appName));
	}

	public void onClickOk(View view) {
		trace(this, "RequestP2pPermissionsActivity :: User agreed with the disclosure message.");
		requestPermissionLauncher.launch(requiredPermissions());
	}

	public void onClickNegative(View view) {
		trace(this, "RequestP2pPermissionsActivity :: User disagreed with the disclosure message.");
		setResult(RESULT_CANCELED);
		finish();
	}
}
