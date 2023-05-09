package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
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

public class RequestStoragePermissionActivity extends FragmentActivity {
	/**
	* TRIGGER_CLASS {String} Extra in the request intent to specify the class that trigger this activity,
	*                        it will be passed on to the result intent. It can be used to continue the
	*                        action of the trigger class after the intent is resolved.
	*/
	static final String TRIGGER_CLASS = "TRIGGER_CLASS";

	private final ActivityResultLauncher<String> requestPermissionLauncher =
		registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
			Intent responseIntent = createResponseIntent();

			if (isGranted) {
				trace(this, "RequestStoragePermissionActivity :: User allowed storage permission.");
				setResult(RESULT_OK, responseIntent);
				finish();
				return;
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !shouldShowRequestPermissionRationale(READ_EXTERNAL_STORAGE)) {
				trace(
					this,
					"RequestStoragePermissionActivity :: User rejected storage permission twice or has selected \"never ask again\"." +
						" Sending user to the app's setting to manually grant the permission."
				);
				Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
				intent.setData(Uri.fromParts("package", getPackageName(), null));
				this.appSettingsLauncher.launch(intent);
				return;
			}

			trace(this, "RequestStoragePermissionActivity :: User rejected storage permission.");
			setResult(RESULT_CANCELED, responseIntent);
			finish();
		});

	private final ActivityResultLauncher<Intent> appSettingsLauncher =
		registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
			Intent responseIntent = createResponseIntent();

			if (ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
				trace(this, "RequestStoragePermissionActivity :: User granted storage permission from app's settings.");
				setResult(RESULT_OK, responseIntent);
				finish();
				return;
			}

			trace(this, "RequestStoragePermissionActivity :: User didn't grant storage permission from app's settings.");
			setResult(RESULT_CANCELED, responseIntent);
			finish();
		});

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.request_storage_permission);

		String appName = getResources().getString(R.string.app_name);
		String message = getResources().getString(R.string.storageRequestMessage);
		TextView field = findViewById(R.id.storageMessageText);
		field.setText(String.format(message, appName));
	}

	public void onClickAllow(View view) {
		trace(this, "RequestStoragePermissionActivity :: User agree with prominent disclosure message.");
		this.requestPermissionLauncher.launch(READ_EXTERNAL_STORAGE);
	}

	public void onClickDeny(View view) {
		trace(this, "RequestStoragePermissionActivity :: User disagree with prominent disclosure message.");
		setResult(RESULT_CANCELED, createResponseIntent());
		finish();
	}

	private Intent createResponseIntent() {
		Intent requestIntent = getIntent();
		String triggerClass = requestIntent == null ? null : requestIntent.getStringExtra(this.TRIGGER_CLASS);
		Intent responseIntent = new Intent();
		responseIntent.setPackage(this.getPackageName());
		responseIntent.putExtra(this.TRIGGER_CLASS, triggerClass);
		return responseIntent;
	}
}
