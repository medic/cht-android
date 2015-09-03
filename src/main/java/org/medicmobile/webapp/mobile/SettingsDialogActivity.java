package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static android.view.View.GONE;

public class SettingsDialogActivity extends Activity {
	private SettingsStore settings;

	public void onCreate(Bundle savedInstanceState) {
		if(DEBUG) log("Starting...");
		super.onCreate(savedInstanceState);
		this.settings = SettingsStore.in(this);
		setContentView(R.layout.settings_dialog);

		text(R.id.txtAppUrl, settings.getAppUrl());
	}

//> EVENT HANDLERS
	public void verifyAndSave(View view) {
		if(DEBUG) log("verifyAndSave");

		String appUrl = text(R.id.txtAppUrl);
		submitButton().setEnabled(false);

		new AsyncTask<String, Void, AppUrlVerififcation>() {
			protected AppUrlVerififcation doInBackground(String... appUrl) {
				assert appUrl.length == 1;
				return verify(appUrl[0]);
			}
			protected void onPostExecute(AppUrlVerififcation result) {
				if(result.isOk) {
					saveSettings(new Settings(result.appUrl));
				} else {
					showError(R.id.txtAppUrl, result.failure);
					submitButton().setEnabled(true);
				}
			}
		}.execute(appUrl);
	}

//> PRIVATE HELPERS
	private AppUrlVerififcation verify(String appUrl) {
		AppUrlVerififcation verificationResult =
				new AppUrlVerifier().verify(appUrl);

		if(!verificationResult.isOk) {
			AppUrlVerififcation prefixedVerififcation =
					new AppUrlVerifier().verify(appUrl + "/medic");
			if(prefixedVerififcation.isOk) {
				verificationResult = prefixedVerififcation;
				return verificationResult;
			}
		}

		return verificationResult;
	}

	private void saveSettings(Settings s) {
		try {
			settings.save(s);
			startActivity(new Intent(this, EmbeddedBrowserActivity.class));
			finish();
		} catch(IllegalSettingsException ex) {
			if(DEBUG) ex.printStackTrace();
			for(IllegalSetting error : ex.errors) {
				showError(error);
			}
		} catch(SettingsException ex) {
			if(DEBUG) ex.printStackTrace();
			submitButton().setError(ex.getMessage());
		}
	}

	private Button submitButton() {
		return (Button) findViewById(R.id.btnSaveSettings);
	}

	private String text(int componentId) {
		EditText field = (EditText) findViewById(componentId);
		return field.getText().toString();
	}

	private void text(int componentId, String value) {
		EditText field = (EditText) findViewById(componentId);
		field.setText(value);
	}

	private void removeError(int componentId) {
		EditText field = (EditText) findViewById(componentId);
		field.setError(null);
	}

	private void showError(IllegalSetting error) {
		showError(error.componentId, error.errorStringId);
	}

	private void showError(int componentId, int stringId) {
		TextView field = (TextView) findViewById(componentId);
		field.setError(getString(stringId));
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | SettingsDialogActivity :: " +
				String.format(message, extras));
	}
}
