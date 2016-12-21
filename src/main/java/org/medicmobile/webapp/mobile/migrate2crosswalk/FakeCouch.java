package org.medicmobile.webapp.mobile.migrate2crosswalk;

import fi.iki.elonen.NanoHTTPD;

import org.medicmobile.webapp.mobile.MedicLog;

class FakeCouch {
	private FakeCouchDaemon server;

	public void start() {
		// TODO something with threads?
		// TODO worry about finding a free port?
		trace("Starting server...");
		try {
			server = new FakeCouchDaemon(8000);
			server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
			trace("Server started.");
		} catch(Exception ex) {
			MedicLog.logException(ex, "Failed to start server.");
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
	FakeCouchDaemon(int port) {
		super(port);
	}
}
