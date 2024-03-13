package org.medicmobile.webapp.mobile.components.settings_dialog;

import static org.medicmobile.webapp.mobile.MedicLog.trace;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import org.medicmobile.webapp.mobile.AppUrlVerifier;
import org.medicmobile.webapp.mobile.EmbeddedBrowserActivity;
import org.medicmobile.webapp.mobile.R;
import org.medicmobile.webapp.mobile.SettingsStore;
import org.medicmobile.webapp.mobile.SettingsStore.IllegalSetting;
import org.medicmobile.webapp.mobile.SettingsStore.IllegalSettingsException;
import org.medicmobile.webapp.mobile.SettingsStore.SettingsException;
import org.medicmobile.webapp.mobile.SettingsStore.WebappSettings;
import org.medicmobile.webapp.mobile.adapters.ServerMetadataAdapter;
import org.medicmobile.webapp.mobile.dialogs.ConfirmServerSelectionDialog;
import org.medicmobile.webapp.mobile.listeners.TextChangedListener;
import org.medicmobile.webapp.mobile.util.AsyncExecutor;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingsDialogActivity extends FragmentActivity {
	private static final int STATE_LIST = 1;
	private static final int STATE_FORM = 2;

	@Inject
	SettingsStore settings;

	@Inject
	ServerRepo serverRepo;

	private int state;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		trace(this, "onCreate()");
		displayServerSelectList();
	}

//> STATE CHANGE HANDLERS
	private void displayServerSelectList() {
		state = STATE_LIST;

		setContentView(R.layout.server_select_list);

		ListView list = findViewById(R.id.lstServers);

		List<ServerMetadata> servers = serverRepo.getServers();
		ServerMetadataAdapter adapter = ServerMetadataAdapter.createInstance(this, servers);
		list.setAdapter(adapter);
		list.setOnItemClickListener(new ServerClickListener(adapter));

		TextView seachBox = findViewById(R.id.instanceSearchBox);
		seachBox.addTextChangedListener(new TextChangedListener() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				adapter.getFilter().filter(s.toString());
			}
		});
	}

	private void displayCustomServerForm() {
		state = STATE_FORM;

		setContentView(R.layout.custom_server_form);

		if(!this.settings.hasWebappSettings()) {
			cancelButton().setVisibility(View.GONE);
		}

		text(R.id.txtAppUrl, settings.getAppUrl());
	}

//> EVENT HANDLERS
	public void verifyAndSave(View view) {
		trace(this, "verifyAndSave()");

		submitButton().setEnabled(false);
		cancelButton().setEnabled(false);

		String appUrl = text(R.id.txtAppUrl);

		AsyncExecutor asyncExecutor = new AsyncExecutor();
		asyncExecutor.executeAsync(new AppUrlVerifier(appUrl), (result) -> {
			trace(
				this,
				"SettingsDialogActivity :: Executing verification callback, result isOkay=%s, appUrl=%s",
				result.isOk, result.appUrl
			);

			if (result.isOk) {
				saveSettings(new WebappSettings(result.appUrl));
				serverRepo.save(result.appUrl);
				return;
			}
			showError(R.id.txtAppUrl, result.failure);
			submitButton().setEnabled(true);
			cancelButton().setEnabled(true);
		});
	}

	public void cancelSettingsEdit(View view) {
		trace(this, "cancelSettingsEdit()");
		backToWebview();
	}

	@Override public void onBackPressed() {
		switch(state) {
			case STATE_LIST:
				if(this.settings.hasWebappSettings()) {
					backToWebview();
					return;
				}
				break;
			case STATE_FORM:
				displayServerSelectList();
				return;
		}
		super.onBackPressed();
	}

//> PRIVATE HELPERS
	private void backToWebview() {
		startActivity(new Intent(this, EmbeddedBrowserActivity.class));
		finish();
	}

	private void saveSettings(WebappSettings s) {
		try {
			settings.updateWith(s);
			this.backToWebview();
		} catch(IllegalSettingsException ex) {
			trace(ex, "Tried to save illegal setting.");
			for(IllegalSetting error : ex.errors) {
				showError(error);
			}
		} catch(SettingsException ex) {
			trace(ex, "Problem saving settings.");
			submitButton().setError(ex.getMessage());
		}
	}

	private Button cancelButton() {
		return (Button) findViewById(R.id.btnCancelSettings);
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

	private void showError(IllegalSetting error) {
		showError(error.componentId, error.errorStringId);
	}

	private void showError(int componentId, int stringId) {
		TextView field = (TextView) findViewById(componentId);
		field.setError(getString(stringId));
	}

//> INNER CLASSES
	class ServerClickListener implements OnItemClickListener {
		private final ServerMetadataAdapter serverMetadataAdapter;

		public ServerClickListener(ServerMetadataAdapter serverMetadataAdapter) {
			this.serverMetadataAdapter = serverMetadataAdapter;
		}

		public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
			ServerMetadata server = serverMetadataAdapter.getServerMetadata(position);
			if (server.url == null) {
				displayCustomServerForm();
			} else {
				new ConfirmServerSelectionDialog(
					server.name,
					() -> saveSettings(new WebappSettings(server.url))
				).show(getSupportFragmentManager());
			}
		}
	}
}
