package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
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

/**
 * Requests runtime permissions needed for P2P WiFi Hotspot Sync:
 * - CAMERA (for QR code scanning)
 * - NEARBY_WIFI_DEVICES (API 33+) or ACCESS_FINE_LOCATION (API < 33) for hotspot
 *
 * Follows the same pattern as RequestLocationPermissionActivity.
 */
public class RequestP2pPermissionsActivity extends FragmentActivity {

	/**
	 * Permissions to request depend on API level:
	 * - API 33+: CAMERA + NEARBY_WIFI_DEVICES
	 * - API < 33: CAMERA + ACCESS_FINE_LOCATION (needed for LocalOnlyHotspot)
	 */
	private static String[] getRequiredPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			return new String[]{ CAMERA, ACCESS_FINE_LOCATION, "android.permission.NEARBY_WIFI_DEVICES" };
		}
		return new String[]{ CAMERA, ACCESS_FINE_LOCATION };
	}

	private final ActivityResultLauncher<String[]> requestPermissionLauncher =
		registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantedMap -> {
			boolean allGranted = true;
			for (Boolean granted : grantedMap.values()) {
				if (!Boolean.TRUE.equals(granted)) {
					allGranted = false;
					break;
				}
			}

			if (allGranted) {
				trace(this, "RequestP2pPermissionsActivity :: All P2P permissions granted.");
				setResult(RESULT_OK);
				finish();
				return;
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				boolean anyNeverAskAgain = false;
				for (String perm : getRequiredPermissions()) {
					if (!shouldShowRequestPermissionRationale(perm)
							&& ContextCompat.checkSelfPermission(this, perm) != PERMISSION_GRANTED) {
						anyNeverAskAgain = true;
						break;
					}
				}

				if (anyNeverAskAgain) {
					trace(
						this,
						"RequestP2pPermissionsActivity :: User selected \"never ask again\"." +
							" Sending user to the app's settings to manually grant permissions."
					);
					Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
					intent.setData(Uri.fromParts("package", getPackageName(), null));
					this.appSettingsLauncher.launch(intent);
					return;
				}
			}

			trace(this, "RequestP2pPermissionsActivity :: User rejected P2P permissions.");
			setResult(RESULT_CANCELED);
			finish();
		});

	private final ActivityResultLauncher<Intent> appSettingsLauncher =
		registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			boolean allGranted = true;
			for (String perm : getRequiredPermissions()) {
				if (ContextCompat.checkSelfPermission(this, perm) != PERMISSION_GRANTED) {
					allGranted = false;
					break;
				}
			}

			if (allGranted) {
				trace(this, "RequestP2pPermissionsActivity :: User granted P2P permissions from app's settings.");
				setResult(RESULT_OK);
				finish();
				return;
			}

			trace(this, "RequestP2pPermissionsActivity :: User didn't grant P2P permissions from app's settings.");
			setResult(RESULT_CANCELED);
			finish();
		});

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// If all permissions already granted, return immediately
		boolean allGranted = true;
		for (String perm : getRequiredPermissions()) {
			if (ContextCompat.checkSelfPermission(this, perm) != PERMISSION_GRANTED) {
				allGranted = false;
				break;
			}
		}
		if (allGranted) {
			trace(this, "RequestP2pPermissionsActivity :: All P2P permissions already granted.");
			setResult(RESULT_OK);
			finish();
			return;
		}

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.request_p2p_permission);

		String appName = getResources().getString(R.string.app_name);
		String message = getResources().getString(R.string.p2pRequestMessage);
		TextView field = findViewById(R.id.p2pMessageText);
		field.setText(String.format(message, appName));
	}

	public void onClickOk(View view) {
		trace(this, "RequestP2pPermissionsActivity :: User agreed with P2P permission disclosure.");
		requestPermissionLauncher.launch(getRequiredPermissions());
	}

	public void onClickNegative(View view) {
		trace(this, "RequestP2pPermissionsActivity :: User declined P2P permissions.");
		setResult(RESULT_CANCELED);
		finish();
	}
}
