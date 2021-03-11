package org.medicmobile.webapp.mobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.ArrayMap;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import medic.android.ActivityBackgroundTask;

import static org.medicmobile.webapp.mobile.BuildConfig.DEBUG;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;
import static org.medicmobile.webapp.mobile.MedicLog.trace;

public class SettingsDialogActivity extends LockableActivity {
	private static final int STATE_LIST = 1;
	private static final int STATE_FORM = 2;

	private SettingsStore settings;
	private ServerRepo serverRepo;
	private int state;

	private static class AppUrlVerificationTask extends ActivityBackgroundTask<SettingsDialogActivity, String, Void, AppUrlVerification> {
		AppUrlVerificationTask(SettingsDialogActivity a) {
			super(a);
		}

		protected AppUrlVerification doInBackground(String... appUrl) {
			if(DEBUG && appUrl.length != 1) throw new IllegalArgumentException();
			return new AppUrlVerifier().verify(appUrl[0]);
		}
		protected void onPostExecute(AppUrlVerification result) {
			SettingsDialogActivity ctx = getRequiredCtx("AppUrlVerificationTask.onPostExecute()");

			if(result.isOk) {
				ctx.saveSettings(new WebappSettings(result.appUrl));
				ctx.serverRepo.save(result.appUrl);
			} else {
				ctx.showError(R.id.txtAppUrl, result.failure);
				ctx.submitButton().setEnabled(true);
				ctx.cancelButton().setEnabled(true);
			}
		}
	}

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		trace(this, "onCreate()");

		this.settings = SettingsStore.in(this);
		this.serverRepo = new ServerRepo(this);

		displayServerSelectList();
	}

//> STATE CHANGE HANDLERS
	private void displayServerSelectList() {
		state = STATE_LIST;

		setContentView(R.layout.server_select_list);

		ListView list = (ListView) findViewById(R.id.lstServers);

		List<ServerMetadata> servers = serverRepo.getServers();

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

		new AppUrlVerificationTask(this).execute(appUrl);
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

	@SuppressWarnings("PMD.UseConcurrentHashMap")
	private List<Map<String, ?>> adapt(List<ServerMetadata> servers) {
		List adapted = new ArrayList(servers.size());
		for(ServerMetadata md : servers) {
			Map<String, String> m = new ArrayMap(2);
			m.put("name", md.name);
			m.put("url", md.url);
			adapted.add(m);
		}
		return adapted;
	}

//> INNER CLASSES
	class ServerClickListener implements OnItemClickListener {
		private final List<ServerMetadata> servers;

		public ServerClickListener(List<ServerMetadata> servers) {
			this.servers = servers;
		}

		public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
			if(position == 0) {
				displayCustomServerForm();
			} else {
				saveSettings(new WebappSettings(servers.get(position).url));
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
		trace(this, "ServerMetadata() :: name: %s, url: %s", name, redactUrl(url));
		this.name = name;
		this.url = url;
	}
}

class ServerRepo {
	private final SharedPreferences prefs;

	ServerRepo(Context ctx) {
		prefs = ctx.getSharedPreferences(
				"ServerRepo",
				Context.MODE_PRIVATE);
		save("https://gamma.dev.medicmobile.org");
		save("https://gamma-cht.dev.medicmobile.org");
		save("https://medic.github.io/atp");
	}

	List<ServerMetadata> getServers() {
		List servers = new LinkedList<ServerMetadata>();

		servers.add(new ServerMetadata("Custom"));

		for(Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
			servers.add(new ServerMetadata(
					e.getValue().toString(),
					e.getKey()));
		}

		return servers;
	}

	void save(String url) {
		SharedPreferences.Editor ed = prefs.edit();
		ed.putString(url, friendly(url));
		ed.apply();
	}

	@SuppressLint("DefaultLocale")
	private static String friendly(String url) {
		int slashes = url.indexOf("//");
		if(slashes != -1) {
			url = url.substring(slashes + 2);
		}
		if(url.endsWith(".medicmobile.org")) {
			url = url.substring(0, url.length() - ".medicmobile.org".length());
		}
		if(url.endsWith(".medicmobile.org/")) {
			url = url.substring(0, url.length() - ".medicmobile.org/".length());
		}
		if(url.startsWith("192.168.")) {
			return url.substring("192.168.".length());
		} else {
			String[] parts = url.split("\\.");
			url = "";
			for(String p : parts) {
				url += " ";
				url += p.substring(0, 1).toUpperCase();
				url += p.substring(1);
			}
			return url.substring(1);
		}
	}
}
