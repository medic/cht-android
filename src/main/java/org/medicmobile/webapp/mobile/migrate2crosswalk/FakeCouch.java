package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.Context;
import android.net.Uri;

import fi.iki.elonen.NanoHTTPD;

import org.medicmobile.webapp.mobile.MedicLog;
import org.medicmobile.webapp.mobile.SettingsStore;

class FakeCouch {
	private final String appHost;

	private FakeCouchDaemon server;

	public FakeCouch(SettingsStore settings) {
		this.appHost = Uri.parse(settings.getAppUrl()).buildUpon()
				.path("")
				.clearQuery()
				.build()
				.toString();
	}

	public void start(Context ctx) {
		// TODO something with threads?
		// TODO worry about finding a free port?
		trace("Starting server...");
		try {
			server = new FakeCouchDaemon(ctx, 8000, appHost);
			server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
			trace("Server started.");
		} catch(Exception ex) {
			// TODO perhaps this should be more than a warning!
			MedicLog.warn(ex, "Failed to start server.");
		}
	}

	public void stop() {
		server.stop();
		server = null;
	}

	private void trace(String message, String... extras) {
		MedicLog.trace(this, message, extras);
	}
}

class FakeCouchDaemon extends NanoHTTPD {
	private final CouchReplicationTarget couch;
	private final String appHost;

	FakeCouchDaemon(Context ctx, int port, String appHost) {
		super(port);
		this.couch = new CouchReplicationTarget(ctx);
		this.appHost = appHost;
	}

	@Override public Response serve(IHTTPSession session) {
		// TODO link up to the CouchReplicationTarget
		Response response = newFixedLengthResponse("HUMBUG");
		response.addHeader("Access-Control-Allow-Origin", appHost);
		response.addHeader("Access-Control-Allow-Credentials", "true");
		return response;
	}
}
