package org.medicmobile.webapp.mobile.migrate2crosswalk;

import android.content.Context;

import fi.iki.elonen.NanoHTTPD;

import org.medicmobile.webapp.mobile.MedicLog;

class FakeCouch {
	private FakeCouchDaemon server;

	public void start(Context ctx) {
		// TODO something with threads?
		// TODO worry about finding a free port?
		trace("Starting server...");
		try {
			server = new FakeCouchDaemon(ctx, 8000);
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

	FakeCouchDaemon(Context ctx, int port) {
		super(port);
		this.couch = new CouchReplicationTarget(ctx);
	}

	@Override public Response serve(IHTTPSession session) {
		// TODO link up to the CouchReplicationTarget
		return newFixedLengthResponse("HUMBUG");
	}
}
