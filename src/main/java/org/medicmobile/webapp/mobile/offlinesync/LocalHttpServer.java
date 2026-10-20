package org.medicmobile.webapp.mobile.offlinesync;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import fi.iki.elonen.NanoHTTPD;

/**
	* The local HTTP server a host device runs for the duration of an offline sync session.
	*
	* It answers a status probe so a peer can confirm it reached the right device, and it accepts
	* the sealed bundles the peer hands over. A bundle is ciphertext addressed to the server: this
	* device stores it and carries it, and has no key to open it.
	*
	* Served over TLS using a {@link SessionCertificate}. The certificate is self-signed and
	* deliberately untrusted by any CA: the peer pins its key against the fingerprint printed in the QR
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
	private static final String BYTES = " bytes";
	private static final String STATUS_PATH = "/_offline-sync/status";
	private static final String BUNDLE_PATH = "/_offline-sync/bundle";
	private static final String ENVELOPE_HEADER = "x-medic-bundle-envelope";
	private static final String SIGNATURE_HEADER = "x-medic-bundle-signature";

	/**
		* The largest bundle this device will take.
		*
		* The peer aims well below this; the cap is here so a peer that is broken, or not a peer at
		* all, cannot fill the phone's storage.
		*/
	private static final long MAX_BUNDLE_BYTES = 32L * 1024 * 1024;
	/** Bumped when the wire contract changes, so a peer can refuse a host it cannot talk to. */
	private static final int PROTOCOL_VERSION = 1;

	private final String deviceLabel;
	private final SessionCertificate certificate;
	private final BundleSpool inbox;

	/**
		* Told when a peer has handed something over.
		*
		* Set after construction, and not required: the server has to be listening before the
		* webapp is in a position to be told anything, and a bundle that lands before then is still
		* stored and collected when the webapp next looks.
		*/
	private volatile BundleListener listener;

	public LocalHttpServer(String deviceLabel, SessionCertificate certificate, BundleSpool inbox) {
		this(EPHEMERAL_PORT, deviceLabel, certificate, inbox);
	}

	public LocalHttpServer(int port, String deviceLabel, SessionCertificate certificate, BundleSpool inbox) {
		super(port);
		if (deviceLabel == null || deviceLabel.trim().isEmpty()) {
			throw new IllegalArgumentException("deviceLabel must not be empty");
		}
		if (certificate == null || inbox == null) {
			throw new IllegalArgumentException("collaborators must not be null");
		}
		this.deviceLabel = deviceLabel;
		this.certificate = certificate;
		this.inbox = inbox;
	}

	public void setBundleListener(BundleListener listener) {
		this.listener = listener;
	}

	/** Starts listening. Safe to call when already running. */
	public void startServer() throws IOException, GeneralSecurityException {
		if (isAlive()) {
			log(this, "Local server already running on port " + getListeningPort());
			return;
		}
		makeSecure(certificate.sslServerSocketFactory(), null);
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
		if (Method.POST.equals(session.getMethod()) && BUNDLE_PATH.equals(uri)) {
			return handleBundle(session);
		}
		warn(this, "Rejected request for " + session.getMethod() + " " + uri);
		return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_JSON, "{\"error\":\"not found\"}");
	}

	/**
		* Takes one sealed bundle from the peer.
		*
		* The envelope and signature travel in headers and are stored untouched alongside the body:
		* this device cannot verify them, only the server can, so passing them on unchanged is the
		* whole job. Nothing here reads the body.
		*/
	private Response handleBundle(IHTTPSession session) {
		String envelope = session.getHeaders().get(ENVELOPE_HEADER);
		String signature = session.getHeaders().get(SIGNATURE_HEADER);
		if (envelope == null || signature == null) {
			warn(this, "Rejected a bundle with no envelope");
			return error(Response.Status.BAD_REQUEST, "missing envelope");
		}

		// The socket carries the next request too, so a body with no declared end cannot be read
		// at all. Checked here with the other headers: it is the caller's mistake, not ours.
		long declared = declaredLength(session);
		if (declared < 0) {
			warn(this, "Rejected a bundle that did not say how large it is");
			return error(Response.Status.BAD_REQUEST, "missing content length");
		}
		if (declared > MAX_BUNDLE_BYTES) {
			warn(this, "Rejected a bundle of " + declared + BYTES);
			return error(Response.Status.PAYLOAD_TOO_LARGE, "bundle too large");
		}

		try {
			return store(envelope, signature, session.getInputStream(), declared);
		} catch (IOException e) {
			warn(e, "Could not store a bundle from a peer");
			return error(Response.Status.INTERNAL_ERROR, "could not store the bundle");
		}
	}

	/**
		* Keeps the bundle only if all of it arrived.
		*
		* The peer deletes its copy as soon as this answers, and the sender moves its position past
		* the data, so accepting a body that stopped early would lose it: what is left here cannot
		* be decrypted and nothing anywhere else still has it.
		*/
	private Response store(String envelope, String signature, InputStream body, long declared)
			throws IOException {
		String id = inbox.store(envelope, signature, bounded(body, declared));
		long stored = inbox.size(id);
		if (stored != declared) {
			warn(this, "Rejected a bundle that stopped after " + stored + " of " + declared + BYTES);
			inbox.delete(id);
			return error(Response.Status.BAD_REQUEST, "bundle was incomplete");
		}

		log(this, "Took a bundle of " + stored + BYTES);
		BundleListener told = listener;
		if (told != null) {
			told.onBundleReceived(id);
		}
		return newFixedLengthResponse(Response.Status.OK, MIME_JSON, "{\"ok\":true}");
	}

	private long declaredLength(IHTTPSession session) {
		String declared = session.getHeaders().get("content-length");
		if (declared == null) {
			return -1;
		}
		try {
			return Long.parseLong(declared);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/**
		* Reads exactly what the peer said it was sending.
		*
		* The socket stays open for the next request, so reading to the end of the stream would
		* block: the declared length is what says where this body stops.
		*/
	private InputStream bounded(InputStream body, long length) {
		return new BoundedInputStream(body, length);
	}

	private static final class BoundedInputStream extends java.io.FilterInputStream {
		private long remaining;

		BoundedInputStream(InputStream body, long length) {
			super(body);
			this.remaining = length;
		}

		@Override public int read() throws IOException {
			if (remaining <= 0) {
				return -1;
			}
			int value = super.read();
			if (value != -1) {
				remaining--;
			}
			return value;
		}

		@Override public int read(byte[] buffer, int offset, int count) throws IOException {
			if (remaining <= 0) {
				return -1;
			}
			int read = super.read(buffer, offset, (int) Math.min(count, remaining));
			if (read != -1) {
				remaining -= read;
			}
			return read;
		}
	}

	private Response error(Response.Status status, String message) {
		return newFixedLengthResponse(status, MIME_JSON, "{\"error\":\"" + message + "\"}");
	}

	/** Told when a bundle has been taken, so the webapp can come and collect it. */
	public interface BundleListener {
		void onBundleReceived(String id);
	}

	/**
		* Answers a peer's probe: confirms this is a CHT host and says who it is, so the peer can
		* show the user which device it paired with.
		*/
	private Response handleStatus() {
		try {
			JSONObject body = new JSONObject();
			body.put("service", "cht-offline-sync");
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
