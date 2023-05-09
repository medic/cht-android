package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.SEND_SMS;
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

public class RequestSendSmsPermissionActivity extends FragmentActivity {

	private final ActivityResultLauncher<String> requestPermissionLauncher =
		registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
			if (isGranted) {
				trace(this, "RequestSendSmsPermissionActivity :: User allowed Send SMS permission.");
				setResult(RESULT_OK);
				finish();
				return;
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !shouldShowRequestPermissionRationale(SEND_SMS)) {
				trace(
					this,
					"RequestSendSmsPermissionActivity :: User rejected Send SMS permission twice or has selected \"never ask again\"." +
						" Sending user to the app's setting to manually grant the permission."
				);
				Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
				intent.setData(Uri.fromParts("package", getPackageName(), null));
				this.appSettingsLauncher.launch(intent);
				return;
			}

			trace(this, "RequestSendSmsPermissionActivity :: User rejected Send SMS permission.");
			setResult(RESULT_CANCELED);
			finish();
		});

	private final ActivityResultLauncher<Intent> appSettingsLauncher =
		registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			if (ContextCompat.checkSelfPermission(this, SEND_SMS) == PERMISSION_GRANTED) {
				trace(this, "RequestSendSmsPermissionActivity :: User granted Send SMS permission from app's settings.");
				setResult(RESULT_OK);
				finish();
				return;
			}

			trace(this, "RequestSendSmsPermissionActivity :: User didn't grant Send SMS permission from app's settings.");
			setResult(RESULT_CANCELED);
			finish();
		});

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.request_send_sms_permission);

		String appName = getResources().getString(R.string.app_name);
		String message = getResources().getString(R.string.sendSmsRequestMessage);
		TextView field = findViewById(R.id.sendSmsMessageText);
		field.setText(String.format(message, appName));
	}

	public void onClickAllow(View view) {
		trace(this, "RequestSendSmsPermissionActivity :: User agree with prominent disclosure message.");
		this.requestPermissionLauncher.launch(SEND_SMS);
	}

	public void onClickDeny(View view) {
		trace(this, "RequestSendSmsPermissionActivity :: User disagree with prominent disclosure message.");
		setResult(RESULT_CANCELED);
		finish();
	}
}
