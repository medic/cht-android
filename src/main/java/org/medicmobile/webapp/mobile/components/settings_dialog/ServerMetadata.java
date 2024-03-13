package org.medicmobile.webapp.mobile.components.settings_dialog;

import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.SimpleJsonClient2.redactUrl;

public class ServerMetadata {
	public final String name;
	public final String url;

	ServerMetadata(String name) {
		this(name, null);
	}

	public ServerMetadata(String name, String url) {
		trace(this, "ServerMetadata() :: name: %s, url: %s", name, redactUrl(url));
		this.name = name;
		this.url = url;
	}
}
