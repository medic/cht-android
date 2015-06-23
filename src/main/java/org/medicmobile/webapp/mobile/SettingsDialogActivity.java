package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class SettingsDialogActivity extends Activity {
	private static final boolean DEBUG = BuildConfig.DEBUG;

	private SettingsStore settings;

	public void onCreate(Bundle savedInstanceState) {
		if(DEBUG) log("Starting...");
		super.onCreate(savedInstanceState);

		setContentView(R.layout.settings_dialog);

		this.settings = SettingsStore.in(this);

		text(R.id.txtAppUrl, settings.getAppUrl());
		text(R.id.txtUsername, settings.getUsername());
		text(R.id.txtPassword, settings.getPassword());
	}

	public void verifyAndSave(View view) {
		log("verifyAndSave");

		Settings s = new Settings(
				text(R.id.txtAppUrl),
				text(R.id.txtUsername),
				text(R.id.txtPassword));
		try {
			settings.save(s);
			startActivity(new Intent(this,
					EmbeddedBrowserActivity.class));
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
