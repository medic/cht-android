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
	private final List<PreconfigOption> options;

	public PreconfigSpinner(Activity ctx, int spinnerId, int targetId,
			final String initialUrl) {
		this.ctx = ctx;
		this.targetId = targetId;

		options = getAppPreconfOptions();
		ArrayAdapter<PreconfigOption> adapter =
				new ArrayAdapter<PreconfigOption>(ctx,
						android.R.layout.simple_spinner_item,
						options);
		adapter.setDropDownViewResource(
				android.R.layout.simple_spinner_dropdown_item);

		Spinner spinner = (Spinner) ctx.findViewById(spinnerId);
		spinner.setOnItemSelectedListener(this);
		spinner.setAdapter(adapter);

		spinner.setSelection(Func.findIndex(options,
				new Func<PreconfigOption, Boolean>() {
			public Boolean apply(PreconfigOption o) {
				return o.url.equals(initialUrl);
			}
		}));
	}

//> SPINNER EVENT HANDLERS
	public void onItemSelected(AdapterView<?> parent, View view,
			int position, long id) {
		if(DEBUG) log("onItemSelected() :: " +
				"parent=%s, view=%s, position=%s, id=%s",
				parent, view, position, id);
		PreconfigOption selected = options.get(position);
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
	private List<PreconfigOption> getAppPreconfOptions() {
		String[] strings = ctx.getResources()
				.getStringArray(R.array.app_preconfigurations);
		return Func.map(strings, new Func<String, PreconfigOption>() {
			public PreconfigOption apply(String s) {
				String[] parts = s.split("\\|", 2);
				return new PreconfigOption(parts[1], parts[0]);
			}
		});
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | PreconfigSpinner :: " +
				String.format(message, extras));
	}
}

class PreconfigOption {
	public final String description;
	public final String url;

	public PreconfigOption(String description, String url) {
		this.description = description;
		this.url = url;
	}

	public String toString() {
		return this.description;
	}
}
