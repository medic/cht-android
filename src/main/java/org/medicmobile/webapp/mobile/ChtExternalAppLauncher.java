package org.medicmobile.webapp.mobile;

import static org.medicmobile.webapp.mobile.JavascriptUtils.safeFormat;
import static org.medicmobile.webapp.mobile.MedicLog.error;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;

public class ChtExternalAppLauncher {

	private ChtExternalAppLauncher() { }

	static Intent createIntent(ChtExternalApp chtExternalApp) {
		ChtExternalAppIntentBuilder builder = new ChtExternalAppIntentBuilder();

		return builder
				.setAction(chtExternalApp.getAction())
				.setCategory(chtExternalApp.getCategory())
				.setExtras(chtExternalApp.getExtras())
				.setPackageName(chtExternalApp.getPackageName())
				.setUri(chtExternalApp.getUri())
				.setFlags(chtExternalApp.getFlags())
				.setType(chtExternalApp.getType())
				.build();
	}

	static String processResponse(Intent intent) {
		try {
			if (intent == null || intent.getExtras() == null) {
				return makeJavaScript(null);
			}

			JSONObject json = parseBundleToJson(intent.getExtras());
			return makeJavaScript(json);

		} catch (Exception exception) {
			error(exception, "ChtExternalAppLauncher :: Problem serialising the intent response");
			return safeFormat("console.error('Problem serialising the intent response: %s')", exception);
		}
	}

	//> PRIVATE

	private static String makeJavaScript(Object response) {
		String javaScript = "try {" +
				"const api = window.CHTCore.AndroidApi;" +
				"if (api && api.resolveCHTExternalAppResponse) {" +
				"  api.resolveCHTExternalAppResponse(%s);" +
				"}" +
				"} catch (error) { " +
				"  console.error('ChtExternalAppLauncher :: Error on sending intent response to CHT-Core webapp', error);" +
				"}";

		return safeFormat(javaScript, response);
	}

	private static JSONObject parseBundleToJson(Bundle bundle) {
		try {
			JSONObject json = new JSONObject();
			bundle
					.keySet()
					.forEach(key -> setValueInJson(key, json, bundle));
			return json;

		} catch (Exception exception) {
			error(exception, "ChtExternalAppLauncher :: Problem parsing bundle to json. Bundle=%s", bundle);
		}

		return null;
	}

	private static void setValueInJson(String key, JSONObject json, Bundle bundle) {
		try {
			Object value = bundle.get(key);

			if (value instanceof Bundle) {
				json.put(key, parseBundleToJson((Bundle) value));
				return;
			}

			if (isBundleList(value)) {
				JSONArray jsonArray = ((List<Bundle>) value)
						.stream()
						.map(item -> parseBundleToJson(item))
						.collect(Collector.of(JSONArray::new, JSONArray::put, JSONArray::put));

				json.put(key, jsonArray);
				return;
			}

			json.put(key, JSONObject.wrap(value));

		} catch (Exception exception) {
			error(exception, "ChtExternalAppLauncher :: Problem parsing bundle to json. Key=%s, Bundle=%s", key, bundle);
		}
	}

	private static boolean isBundleList(Object value) {
		return value instanceof List
				&& ((List<?>) value).size() > 0
				&& ((List<?>) value).get(0) instanceof Bundle;
	}
}

class ChtExternalAppIntentBuilder {
	private Intent intent;

	//> PUBLIC

	public ChtExternalAppIntentBuilder() {
		this.intent = new Intent();
	}

	public Intent build() {
		return this.intent;
	}

	public ChtExternalAppIntentBuilder setAction(String action) {
		if (action != null) {
			this.intent.setAction(action);
		}

		return this;
	}

	public ChtExternalAppIntentBuilder setCategory(String category) {
		if (category != null) {
			this.intent.addCategory(category);
		}

		return this;
	}

	public ChtExternalAppIntentBuilder setExtras(JSONObject extras) {
		if (extras != null) {
			extras
					.keys()
					.forEachRemaining(key -> setIntentExtras(key, extras));
		}

		return this;
	}

	public ChtExternalAppIntentBuilder setUri(Uri uri) {
		if (uri != null) {
			this.intent.setDataAndNormalize(uri);
		}

		return this;
	}

	public ChtExternalAppIntentBuilder setPackageName(String packageName) {
		if (packageName != null) {
			this.intent.setPackage(packageName);
		}

		return this;
	}

	public ChtExternalAppIntentBuilder setType(String type) {
		if (type != null) {
			this.intent.setType(type);
		}

		return this;
	}

	public ChtExternalAppIntentBuilder setFlags(Integer flags) {
		if (flags != null) {
			this.intent.setFlags(flags);
		}

		return this;
	}

	//> PRIVATE

	private void setIntentExtras(String key, JSONObject data) {
		try {
			Object value = data.get(key);

			if (value instanceof JSONObject) {
				this.intent.putExtra(key, parseJsonToBundle((JSONObject) value));
				return;
			}

			if (value instanceof JSONArray) {
				this.intent.putExtra(key, (Serializable) parseJsonArrayToList((JSONArray) value));
				return;
			}

			this.intent.putExtra(key, (Serializable) value);

		} catch (Exception exception) {
			error(exception, "ChtExternalAppLauncher :: Problem setting intent extras. Key=%s, Data=%s", key, data);
		}
	}

	private Bundle parseJsonToBundle(JSONObject json) {
		Bundle bundle = new Bundle();
		json
				.keys()
				.forEachRemaining(key -> setBundleAttribute(key, json, bundle));

		return bundle;
	}

	private List<?> parseJsonArrayToList(JSONArray jsonArray) throws JSONException {
		List<?> list = new ArrayList<>();

		if (jsonArray.length() > 0) {
			list = jsonArray.get(0) instanceof JSONObject
					? parseJsonArrayToBundleList(jsonArray) : parseJsonArrayToSerializableList(jsonArray);
		}

		return list;
	}

	private List<Bundle> parseJsonArrayToBundleList(JSONArray jsonArray) throws JSONException {
		List<Bundle> list = new ArrayList<>();

		for (int i = 0; i < jsonArray.length(); i++) {
			list.add(parseJsonToBundle(jsonArray.getJSONObject(i)));
		}

		return list;
	}

	private List<Serializable> parseJsonArrayToSerializableList(JSONArray jsonArray) throws JSONException {
		List<Serializable> list = new ArrayList<>();

		for (int i = 0; i < jsonArray.length(); i++) {
			list.add((Serializable) jsonArray.get(i));
		}

		return list;
	}

	private void setBundleAttribute(String key, JSONObject json, Bundle bundle) {
		try {
			Object value = json.get(key);

			if (value instanceof JSONObject) {
				bundle.putBundle(key, parseJsonToBundle((JSONObject) value));
				return;
			}

			if (value instanceof JSONArray) {
				bundle.putParcelableArrayList(key, (ArrayList) parseJsonArrayToList((JSONArray) value));
				return;
			}

			bundle.putSerializable(key, (Serializable) value);

		} catch (Exception exception) {
			error(exception, "ChtExternalAppIntentBuilder :: Problem converting from JSON to Bundle. Key=%s, JSON=%s", key, json);
		}
	}
}

class ChtExternalApp {
	private String action;
	private String category;
	private String type;
	private JSONObject extras;
	private Uri uri;
	private String packageName;
	private Integer flags;

	public ChtExternalApp(String action, String category, String type, JSONObject extras, Uri uri, String packageName, Integer flags) {
		this.action = action;
		this.category = category;
		this.type = type;
		this.extras = extras;
		this.uri = uri;
		this.packageName = packageName;
		this.flags = flags;
	}

	public String getAction() {
		return this.action;
	}

	public String getCategory() {
		return this.category;
	}

	public String getType() {
		return this.type;
	}

	public JSONObject getExtras() {
		return this.extras;
	}

	public Uri getUri() {
		return this.uri;
	}

	public String getPackageName() {
		return this.packageName;
	}

	public Integer getFlags() {
		return this.flags;
	}
}
