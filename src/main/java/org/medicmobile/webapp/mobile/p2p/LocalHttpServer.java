package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;

import fi.iki.elonen.NanoHTTPD;

/**
	* The local HTTP server a host device runs for the duration of a P2P session.
	*
	* Pairing only (#11281): it answers a status probe so a peer can confirm it reached the right
	* device, and nothing else. The endpoints that carry data arrive with the transfer work.
	*
	* Served over TLS using a {@link SessionCertificate}. The certificate is self-signed and
	* deliberately untrusted by any CA: the peer pins it against the fingerprint printed in the QR
	* code. Anyone else on the hotspot can reach this port, so the handshake is what identifies the
	* host, not the network.
	*/
public class LocalHttpServer extends NanoHTTPD {

	/**
		* Bind an OS-assigned port rather than competing for a fixed one.
		*
		* The peer is told the port by the QR payload, so a hardcoded number buys nothing and costs
		* a whole class of failure: another app holding it, or our own previous session not yet
		* released. NanoHTTPD sets SO_REUSEADDR, which covers a socket lingering in TIME_WAIT, but
		* not a live listener. Asking for 0 sidesteps both.
		*/
	public static final int EPHEMERAL_PORT = 0;

	private static final String MIME_JSON = "application/json";
	private static final String STATUS_PATH = "/_p2p/status";
	/** Bumped when the wire contract changes, so a peer can refuse a host it cannot talk to. */
	private static final int PROTOCOL_VERSION = 1;

	private final String deviceLabel;
	private final SessionCertificate certificate;

	public LocalHttpServer(String deviceLabel, SessionCertificate certificate) {
		this(EPHEMERAL_PORT, deviceLabel, certificate);
	}

	public LocalHttpServer(int port, String deviceLabel, SessionCertificate certificate) {
		super(port);
		if (deviceLabel == null || deviceLabel.trim().isEmpty()) {
			throw new IllegalArgumentException("deviceLabel must not be empty");
		}
		if (certificate == null) {
			throw new IllegalArgumentException("certificate must not be null");
		}
		this.deviceLabel = deviceLabel;
		this.certificate = certificate;
	}

	/** Starts listening. Safe to call when already running. */
	public void startServer() throws IOException {
		if (isAlive()) {
			log(this, "Local server already running on port " + getListeningPort());
			return;
		}
		try {
			makeSecure(certificate.sslServerSocketFactory(), null);
		} catch (GeneralSecurityException e) {
			throw new IOException("Could not enable TLS on the local server", e);
		}
		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
		// the real port is only known once bound, and it is what goes into the QR payload
		log(this, "Local server listening on port " + getListeningPort());
	}

	/** Stops listening. Safe to call when already stopped. */
	public void stopServer() {
		if (!isAlive()) {
			return;
		}
		stop();
		log(this, "Local server stopped");
	}

	@Override public Response serve(IHTTPSession session) {
		String uri = session.getUri();
		if (Method.GET.equals(session.getMethod()) && STATUS_PATH.equals(uri)) {
			return handleStatus();
		}
		warn(this, "Rejected request for " + session.getMethod() + " " + uri);
		return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{\"error\":\"not found\"}");
	}

	/**
		* Answers a peer's probe: confirms this is a CHT host and says who it is, so the peer can
		* show the user which device it paired with.
		*/
	private Response handleStatus() {
		try {
			JSONObject body = new JSONObject();
			body.put("service", "cht-p2p");
			body.put("protocol_version", PROTOCOL_VERSION);
			body.put("device_label", deviceLabel);
			return newFixedLengthResponse(Response.Status.OK, MIME_JSON, body.toString());
		} catch (JSONException e) {
			warn(e, "Could not build the status response");
			return newFixedLengthResponse(
					Response.Status.INTERNAL_ERROR, MIME_JSON, "{\"error\":\"status unavailable\"}");
		}
	}
}
