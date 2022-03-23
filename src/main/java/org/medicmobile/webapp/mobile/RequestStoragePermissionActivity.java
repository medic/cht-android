package org.medicmobile.webapp.mobile;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentActivity;

public class RequestStoragePermissionActivity extends FragmentActivity {
	/**
	 * TRIGGER_CLASS {String} Extra in the request intent to specify the class that trigger this activity,
	 *                        it will be passed on to the result intent. It can be used to continue the
	 *                        action of the trigger class after the intent is resolved.
	 */
	static final String TRIGGER_CLASS = "TRIGGER_CLASS";

	private ActivityResultLauncher<String> requestPermissionLauncher =
		registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
			if (isGranted) {
				trace(this, "RequestStoragePermissionActivity :: User allowed storage permission.");

				Intent requestIntent = getIntent();
				String triggerClass = requestIntent == null ? null : requestIntent.getStringExtra(TRIGGER_CLASS);
				Intent responseIntent = new Intent();
				responseIntent.putExtra(TRIGGER_CLASS, triggerClass);

				setResult(RESULT_OK, responseIntent);
				finish();
				return;
			}

			trace(this, "RequestStoragePermissionActivity :: User rejected storage permission.");
			setResult(RESULT_CANCELED);
			finish();
		});

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.request_storage_permission);

		String appName = getResources().getString(R.string.app_name);
		String message = getResources().getString(R.string.storageRequestMessage);
		TextView field = (TextView) findViewById(R.id.storageMessageText);
		field.setText(String.format(message, appName));
	}

	public void onClickAllow(View view) {
		trace(this, "RequestStoragePermissionActivity :: User agree with prominent disclosure message.");
		requestPermissionLauncher.launch(READ_EXTERNAL_STORAGE);
	}

	public void onClickDeny(View view) {
		trace(this, "RequestStoragePermissionActivity :: User disagree with prominent disclosure message.");
		setResult(RESULT_CANCELED);
		finish();
	}
}
