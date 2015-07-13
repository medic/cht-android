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
	private boolean isBranded;
	private String fixedAppUrl;

	public void onCreate(Bundle savedInstanceState) {
		if(DEBUG) log("Starting...");
		super.onCreate(savedInstanceState);
		this.settings = SettingsStore.in(this);
		setContentView(R.layout.settings_dialog);

		text(R.id.txtAppUrl, settings.getAppUrl());

		// TODO if fixed_app_url is set, remove the textfield and force
		// the value in settings
		fixedAppUrl = getResources().
				getString(R.string.fixed_app_url);
		isBranded = fixedAppUrl.length() > 0;
		if(isBranded) findViewById(R.id.txtAppUrl).
				setVisibility(GONE);
	}

//> EVENT HANDLERS
	public void verifyAndSave(View view) {
		log("verifyAndSave");

		String appUrl = isBranded ? fixedAppUrl : text(R.id.txtAppUrl);
		Settings newSettings = new Settings(appUrl);
		try {
			settings.save(newSettings);
			startActivity(new Intent(this, EmbeddedBrowserActivity.class));
			finish();
		} catch(IllegalSettingsException ex) {
			if(DEBUG) ex.printStackTrace();
			for(IllegalSetting error : ex.errors) {
				showError(error);
			}
		} catch(SettingsException ex) {
			if(DEBUG) ex.printStackTrace();
			((Button) findViewById(R.id.btnSaveSettings))
					.setError(ex.getMessage());
		}
	}

//> PRIVATE HELPERS
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
		EditText field = (EditText) findViewById(error.componentId);
		field.setError(error.message);
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | SettingsDialogActivity :: " +
				String.format(message, extras));
	}
}
