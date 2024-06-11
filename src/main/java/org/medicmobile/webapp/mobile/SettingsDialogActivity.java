package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;

import org.medicmobile.webapp.mobile.adapters.FilterableListAdapter;
import org.medicmobile.webapp.mobile.dialogs.ConfirmServerSelectionDialog;
import org.medicmobile.webapp.mobile.listeners.TextChangedListener;
import org.medicmobile.webapp.mobile.util.AsyncExecutor;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SettingsDialogActivity extends FragmentActivity {
	private static final int STATE_LIST = 1;
	private static final int STATE_FORM = 2;
	private SettingsStore settings;
	private ServerRepo serverRepo;
	private int state;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		trace(this, "onCreate()");

		this.settings = SettingsStore.in(this);
		this.serverRepo = new ServerRepo(this, this.settings);

		displayServerSelectList();
	}

//> STATE CHANGE HANDLERS
	private void displayServerSelectList() {
		state = STATE_LIST;

		setContentView(R.layout.server_select_list);

		// TODO: replace `UPSIDE_DOWN_CAKE` with `VANILLA_ICE_CREAM` when SDK 35 comes out of preview
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			View view = findViewById(R.id.serverSelectListLayout);
			ViewCompat.requestApplyInsets(view.getRootView());
//			((View) view.getParent()).requestApplyInsets();
		}

		List<ServerMetadata> servers = serverRepo.getServers();
		ServerMetadataAdapter adapter = ServerMetadataAdapter.createInstance(this, servers);
		ListView list = findViewById(R.id.lstServers);
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
		// TODO: replace `UPSIDE_DOWN_CAKE` with `VANILLA_ICE_CREAM` when SDK 35 comes out of preview
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			View view = findViewById(R.id.customServerFormLayout);
			ViewCompat.requestApplyInsets(view.getRootView());
//			((View) view.getParent()).requestApplyInsets();
		}

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

	static class ServerMetadataAdapter extends FilterableListAdapter {
		private ServerMetadataAdapter(Context context, List<Map<String, ?>> data) {
			super(
				context,
				data,
				R.layout.server_list_item,
				new String[]{ "name", "url" },
				new int[]{ R.id.txtName, R.id.txtUrl },
				"name", "url"
			);
		}

		static ServerMetadataAdapter createInstance(Context context, List<ServerMetadata> servers) {
			return new ServerMetadataAdapter(context, adapt(servers));
		}

		@SuppressWarnings("unchecked")
		ServerMetadata getServerMetadata(int position) {
			Map<String, String> dataMap = (Map<String, String>) this.getItem(position);
			return new ServerMetadata(dataMap.get("name"), dataMap.get("url"));
		}

		private static List<Map<String, ?>> adapt(List<ServerMetadata> servers) {
			return servers
				.stream()
				.map(server -> {
					Map<String, String> serverProperties = new HashMap<>();
					serverProperties.put("name", server.name);
					serverProperties.put("url", server.url);
					return serverProperties;
				})
				.collect(Collectors.toList());
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

final class ServerRepo {
	private final SharedPreferences prefs;
	private final SettingsStore settingsStore;

	ServerRepo(Context ctx, SettingsStore settingsStore) {
		prefs = ctx.getSharedPreferences(
			"ServerRepo",
			Context.MODE_PRIVATE);

		this.settingsStore = settingsStore;

		Map<String, String> instances = parseInstanceXML(ctx);
		for (Map.Entry<String, String> entry : instances.entrySet()) {
			String instanceName = entry.getValue();
			String instanceUrl = entry.getKey();

			save(instanceName, instanceUrl);
		}
	}

	List<ServerMetadata> getServers() {
		List servers = new LinkedList<ServerMetadata>();

		for(Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
			servers.add(new ServerMetadata(
				e.getValue().toString(),
				e.getKey()));
		}

		Collections.sort(servers, Comparator.<ServerMetadata, String>comparing(server -> server.name));

		if (this.settingsStore.allowCustomHosts()) {
			servers.add(0, new ServerMetadata("Custom"));
		}

		return servers;
	}

	void save(String url) {
		save(friendly(url), url);
	}

	void save(String name, String url) {
		SharedPreferences.Editor ed = prefs.edit();
		ed.putString(url, name);
		ed.apply();
	}

	private static Map<String, String> parseInstanceXML(Context context) {
		try {
			HashMap<String, String> result = new HashMap<>();

			Resources resources = context.getResources();
			XmlResourceParser xmlParser = resources.getXml(R.xml.instances);

			while (xmlParser.next() != XmlPullParser.END_TAG) {
				if (xmlParser.getEventType() != XmlPullParser.START_TAG
					|| !"instance".equals(xmlParser.getName())) {
					continue;
				}
				String name = xmlParser.getAttributeValue(null, "name");
				String url = xmlParser.nextText();
				if (name == null) {
					name = friendly(url);
				}
				result.put(url, name);
			}

			return result;
		} catch (XmlPullParserException | IOException e) {
			error(e, "Failed to load instances data from xml.");
			return Collections.emptyMap();
		}
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
			StringBuilder stringBuilder = new StringBuilder();
			for(String p : parts) {
				stringBuilder.append(" ");
				stringBuilder.append(p.substring(0, 1).toUpperCase());
				stringBuilder.append(p.substring(1));
			}
			return stringBuilder.toString().substring(1);
		}
	}
}
