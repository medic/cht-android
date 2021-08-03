package org.medicmobile.webapp.mobile;

import android.net.Uri;

import org.json.JSONObject;

public class ChtExternalApp {
	private final String action;
	private final String category;
	private final String type;
	private final JSONObject extras;
	private final Uri uri;
	private final String packageName;
	private final Integer flags;

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
