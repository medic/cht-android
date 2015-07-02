package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;

import java.util.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;

public class SettingsDialogActivity extends Activity {
	private SettingsStore settings;

	public void onCreate(Bundle savedInstanceState) {
		if(DEBUG) log("Starting...");
		super.onCreate(savedInstanceState);
		this.settings = SettingsStore.in(this);
		setContentView(R.layout.settings_dialog);

		text(R.id.txtAppUrl, settings.getAppUrl());
		text(R.id.txtUsername, settings.getUsername());
		text(R.id.txtPassword, settings.getPassword());

		new PreconfigSpinner(this, R.id.spnAppPreconf, R.id.txtAppUrl,
				settings.getAppUrl());
	}

//> EVENT HANDLERS
	public void verifyAndSave(View view) {
		log("verifyAndSave");

		String appUrl = text(R.id.txtAppUrl);
		Settings oldSettings = settings.get();
		Settings newSettings = new Settings(
				appUrl,
				text(R.id.txtUsername),
				text(R.id.txtPassword));
		try {
			settings.save(newSettings);
			new IconChangerTask().execute(getApplicationContext(),
					oldSettings, newSettings);
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

class PreconfigSpinner
		implements android.widget.AdapterView.OnItemSelectedListener {
	private final Activity ctx;
	private final int targetId;
	private final PreconfigProvider preconfig;

	public PreconfigSpinner(Activity ctx, int spinnerId, int targetId,
			final String initialUrl) {
		this.ctx = ctx;
		this.targetId = targetId;

		preconfig = new PreconfigProvider(ctx);
		ArrayAdapter<PreconfigOption> adapter =
				new ArrayAdapter<PreconfigOption>(ctx,
						android.R.layout.simple_spinner_item,
						preconfig.options);
		adapter.setDropDownViewResource(
				android.R.layout.simple_spinner_dropdown_item);

		Spinner spinner = (Spinner) ctx.findViewById(spinnerId);
		spinner.setOnItemSelectedListener(this);
		spinner.setAdapter(adapter);

		spinner.setSelection(preconfig.indexOf(initialUrl));
	}

//> SPINNER EVENT HANDLERS
	public void onItemSelected(AdapterView<?> parent, View view,
			int position, long id) {
		if(DEBUG) log("onItemSelected() :: " +
				"parent=%s, view=%s, position=%s, id=%s",
				parent, view, position, id);
		PreconfigOption selected = preconfig.options.get(position);
		EditText target = (EditText) ctx.findViewById(targetId);

		// Allow editing of custom URLs.  When switching from preconfig
		// to custom, preserve the value so that preconfig URLs can be
		// modified.
		boolean isCustom = position == 0;
		target.setEnabled(isCustom);
		if(!isCustom) target.setText(selected.url);
	}

	public void onNothingSelected(AdapterView<?> parent) {}

//> PRIVATE HELPERS
	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | PreconfigSpinner :: " +
				String.format(message, extras));
	}
}
