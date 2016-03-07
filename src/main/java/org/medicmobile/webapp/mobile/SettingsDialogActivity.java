package org.medicmobile.webapp.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import java.util.*;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static android.view.View.GONE;

public class SettingsDialogActivity extends Activity {
	private static final int STATE_LIST = 1;
	private static final int STATE_FORM = 2;

	private SettingsStore settings;
	private int state;

	public void onCreate(Bundle savedInstanceState) {
		if(DEBUG) log("Starting...");
		super.onCreate(savedInstanceState);
		this.settings = SettingsStore.in(this);

		displayServerSelectList();
	}

//> STATE CHANGE HANDLERS
	private void displayServerSelectList() {
		state = STATE_LIST;

		setContentView(R.layout.server_select_list);

		ListView list = (ListView) findViewById(R.id.lstServers);

		List<ServerMetadata> servers = getServers();

		list.setAdapter(new SimpleAdapter(this,
				adapt(servers),
				R.layout.server_list_item,
				new String[] { "name", "url" },
				new int[] { R.id.txtName, R.id.txtUrl }));

		list.setOnItemClickListener(new ServerClickListener(servers));
	}

	private void displayCustomServerForm() {
		state = STATE_FORM;

		setContentView(R.layout.custom_server_form);

		if(!this.settings.hasSettings()) {
			cancelButton().setVisibility(View.GONE);
		}

		text(R.id.txtAppUrl, settings.getAppUrl());
	}

//> EVENT HANDLERS
	public void verifyAndSave(View view) {
		if(DEBUG) log("verifyAndSave");

		submitButton().setEnabled(false);
		cancelButton().setEnabled(false);

		String appUrl = text(R.id.txtAppUrl);

		new AsyncTask<String, Void, AppUrlVerififcation>() {
			protected AppUrlVerififcation doInBackground(String... appUrl) {
				assert appUrl.length == 1;
				return new AppUrlVerifier().verify(appUrl[0]);
			}
			protected void onPostExecute(AppUrlVerififcation result) {
				if(result.isOk) {
					saveSettings(new Settings(result.appUrl));
				} else {
					showError(R.id.txtAppUrl, result.failure);
					submitButton().setEnabled(true);
					cancelButton().setEnabled(true);
				}
			}
		}.execute(appUrl);
	}

	public void onBackPressed() {
		switch(state) {
			case STATE_LIST:
				if(this.settings.hasSettings()) {
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

	public void cancelSettingsEdit(View view) {
		if(DEBUG) log("cancelSettingsEdit");
		backToWebview();
	}

//> PRIVATE HELPERS
	private void backToWebview() {
		startActivity(new Intent(this, EmbeddedBrowserActivity.class));
		finish();
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

	private List<Map<String, ?>> adapt(List<ServerMetadata> servers) {
		List adapted = new ArrayList(servers.size());
		for(ServerMetadata md : servers) {
			Map<String, String> m = new HashMap();
			m.put("name", md.name);
			m.put("url", md.url);
			adapted.add(m);
		}
		return adapted;
	}

	private List<ServerMetadata> getServers() {
		return Arrays.asList(new ServerMetadata[] {
			new ServerMetadata("Custom"),
			new ServerMetadata("Alpha Dev", "https://alpha.dev.medicmobile.org"),
		});
	}

	private void log(String message, Object...extras) {
		if(DEBUG) System.err.println("LOG | SettingsDialogActivity :: " +
				String.format(message, extras));
	}

//> INNER CLASSES
	class ServerClickListener implements OnItemClickListener {
		private final List<ServerMetadata> servers;

		public ServerClickListener(List<ServerMetadata> servers) {
			this.servers = servers;
		}

		public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
			ServerMetadata md = servers.get(position);
			if(position == 0) {
				displayCustomServerForm();
			} else {
				saveSettings(new Settings(servers.get(position).url));
			}
		}
	}
}

class ServerMetadata {
	public final String name;
	public final String url;

	ServerMetadata(String name) {
		this(name, null);
	}

	ServerMetadata(String name, String url) {
		if(DEBUG) log("ServerMetadata() :: name:%s, url:%s", name, url);
		this.name = name;
		this.url = url;
	}

	public String toString() {
		return name + "[" + url + "]";
	}

	private void log(String message, Object... extras) {
		if(DEBUG) System.err.println("LOG | ServerMetadata :: " +
				String.format(message, extras));
	}
}
