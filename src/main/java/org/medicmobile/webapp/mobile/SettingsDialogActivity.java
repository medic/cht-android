package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.ArrayMap;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SettingsDialogActivity extends Activity {
	private static final int STATE_LIST = 1;
	private static final int STATE_FORM = 2;
	private SettingsStore settings;
	private ServerRepo serverRepo;
	private int state;

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

//> EVENT HANDLERS
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
			saveSettings(new WebappSettings(servers.get(position).url));
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
		save("Garissa", "https://garissa-echis.health.go.ke");
		save("Isiolo", "https://isiolo-echis.health.go.ke");
		save("Kakamega", "https://kakamega-echis.health.go.ke");
		save("Kilifi", "https://kilifi-echis.health.go.ke");
		save("Kisumu", "https://kisumu-echis.health.go.ke");
		save("Kitui", "https://kitui-echis.health.go.ke");
		save("Machakos", "https://echis.health.go.ke");
		save("Migori", "https://migori-echis.health.go.ke");
		save("Nairobi", "https://nairobi-echis.health.go.ke");
		save("Nakuru", "https://nakuru-echis.health.go.ke");
		save("Nyeri", "https://nyeri-echis.health.go.ke");
		save("Turkana", "https://turkana-echis.health.go.ke");
		save("Uasin Gishu", "https://uasin-gishu-echis.health.go.ke");
		save("Vihiga", "https://vihiga-echis.health.go.ke");
	}

	List<ServerMetadata> getServers() {
		List servers = new LinkedList<ServerMetadata>();

		for(Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
			servers.add(new ServerMetadata(
					e.getValue().toString(),
					e.getKey()));
		}

		return servers;
	}

	void save(String name, String url) {
		SharedPreferences.Editor ed = prefs.edit();
		ed.putString(url, name);
		ed.apply();
	}
}
