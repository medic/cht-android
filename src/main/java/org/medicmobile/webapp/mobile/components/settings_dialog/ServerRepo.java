package org.medicmobile.webapp.mobile.components.settings_dialog;

import static org.medicmobile.webapp.mobile.MedicLog.error;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import org.medicmobile.webapp.mobile.R;
import org.medicmobile.webapp.mobile.SettingsStore;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ActivityContext;
import dagger.hilt.android.scopes.ActivityScoped;

@ActivityScoped
class ServerRepo {
	private final SharedPreferences prefs;
	private final SettingsStore settingsStore;

	@Inject
	ServerRepo(@ActivityContext Context ctx, SettingsStore settingsStore) {
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

		for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
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

		if (slashes != -1) {
			url = url.substring(slashes + 2);
		}

		if (url.endsWith(".medicmobile.org")) {
			url = url.substring(0, url.length() - ".medicmobile.org".length());
		}

		if (url.endsWith(".medicmobile.org/")) {
			url = url.substring(0, url.length() - ".medicmobile.org/".length());
		}

		if (url.startsWith("192.168.")) {
			return url.substring("192.168.".length());
		} else {
			String[] parts = url.split("\\.");
			StringBuilder stringBuilder = new StringBuilder();
			for (String p : parts) {
				stringBuilder.append(" ");
				stringBuilder.append(p.substring(0, 1).toUpperCase());
				stringBuilder.append(p.substring(1));
			}
			return stringBuilder.toString().substring(1);
		}
	}
}
