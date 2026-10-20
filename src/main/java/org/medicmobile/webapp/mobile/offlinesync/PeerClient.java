package org.medicmobile.webapp.mobile.offlinesync;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.net.Network;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
	* Talks to the host over the joined network, accepting only the pinned key.
	*
	* Two things have to be right for this to reach the intended device and nothing else. The socket
	* is bound to the {@link Network} the join returned, because the hotspot is not the device's
	* default route and unbound traffic would leave over mobile data. And the TLS handshake trusts
	* only the key whose fingerprint came from the QR code, because everyone in range can
	* join the same hotspot.
	*/
public class PeerClient {

	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 10_000;
	private static final int UPLOAD_BUFFER_BYTES = 64 * 1024;
	// The status response is a few dozen bytes. The read timeout only bounds inactivity, so a
	// host that streams steadily would never trip it: this bounds the total instead.
	private static final int MAX_BODY_CHARS = 64 * 1024;

	/**
		* Accepts any hostname, on purpose, because the pin decides identity here.
		*
		* The host's certificate is issued to a device label and we connect to an IP address that
		* changes every session, so name-based verification could never pass and would only stop
		* pairing from working. What replaces it is stricter, not weaker: the trust manager accepts
		* exactly one key, the one whose fingerprint was read from the QR code, and rejects every
		* other including any a certificate authority would vouch for.
		*
		* Written as a named field rather than a lambda so it is visible to review and to lint,
		* which only inspects classes for this and would otherwise never see it.
		*/
	@android.annotation.SuppressLint("BadHostnameVerifier")
	private static final HostnameVerifier PIN_IS_THE_IDENTITY_CHECK = new HostnameVerifier() {
		@Override public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	};

	private final Network network;
	private final String certFingerprint;

	public PeerClient(Network network, String certFingerprint) {
		if (network == null) {
			throw new IllegalArgumentException("network must not be null");
		}
		if (certFingerprint == null || certFingerprint.trim().isEmpty()) {
			throw new IllegalArgumentException("certFingerprint must not be empty");
		}
		this.network = network;
		this.certFingerprint = certFingerprint;
	}

	/**
		* Confirms the device at this address is the host from the QR code.
		*
		* @return the host's reported label
		* @throws IOException if the host cannot be reached, or presents a different key
		*/
	public String fetchStatus(String ipAddress, int port) throws IOException {
		JSONObject body = get(ipAddress, port, "/_offline-sync/status");
		try {
			String label = body.getString("device_label");
			log(PeerClient.class, "Reached host: " + label);
			return label;
		} catch (JSONException e) {
			throw new IOException("The host's status response was not what we expect", e);
		}
	}

	/**
		* Hands one sealed bundle to the host.
		*
		* The envelope and signature ride in headers and the body is the raw ciphertext, which is
		* the shape the server's own endpoint expects, so the host can pass both on untouched.
		*
		* @throws IOException if the host could not be reached, or would not take the bundle
		*/
	public void postBundle(InetSocketAddress host, String envelope, String signature, File body)
			throws IOException {
		HttpsURLConnection connection = open(host.getHostString(), host.getPort(), "/_offline-sync/bundle", "POST");
		try {
			connection.setDoOutput(true);
			// Streamed rather than buffered: a bundle is megabytes, and buffering would hold a second
			// copy of it just to send the first.
			connection.setFixedLengthStreamingMode(body.length());
			connection.setRequestProperty("Content-Type", "application/octet-stream");
			connection.setRequestProperty("X-Medic-Bundle-Envelope", envelope);
			connection.setRequestProperty("X-Medic-Bundle-Signature", signature);

			try (InputStream in = new FileInputStream(body); OutputStream out = connection.getOutputStream()) {
				byte[] buffer = new byte[UPLOAD_BUFFER_BYTES];
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
				}
			}

			int status = connection.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				throw new BundleRejectedException("The host answered " + status + " for the bundle");
			}
			log(PeerClient.class, "Handed over a bundle of " + body.length() + " bytes");
		} finally {
			connection.disconnect();
		}
	}

	private JSONObject get(String ipAddress, int port, String path) throws IOException {
		HttpsURLConnection connection = open(ipAddress, port, path, "GET");
		try {
			int status = connection.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				throw new IOException("The host answered " + status + " for " + path);
			}
			return parse(readBody(connection));
		} finally {
			connection.disconnect();
		}
	}

	private HttpsURLConnection open(String ipAddress, int port, String path, String method) throws IOException {
		URL url = new URL("https://" + ipAddress + ":" + port + path);
		// through the joined network, not whatever the device would otherwise use
		HttpsURLConnection connection = (HttpsURLConnection) network.openConnection(url);
		try {
			connection.setSSLSocketFactory(PinnedCertificateTrust.socketFactoryFor(certFingerprint));
		} catch (GeneralSecurityException e) {
			throw new IOException("Could not pin the host's key", e);
		}
		connection.setHostnameVerifier(PIN_IS_THE_IDENTITY_CHECK);
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setRequestMethod(method);
		return connection;
	}

	/**
		* The host was reached and would not take the bundle.
		*
		* Separate from any other failure because it means something different to the user: the
		* link worked, so retrying the same bundle on the same host will fail the same way.
		*/
	public static class BundleRejectedException extends IOException {
		private static final long serialVersionUID = 1L;

		public BundleRejectedException(String message) {
			super(message);
		}
	}

	private String readBody(HttpsURLConnection connection) throws IOException {
		return readBounded(connection.getInputStream());
	}

	/** Package-private so the bound can be tested without standing up a server. */
	static String readBounded(InputStream stream) throws IOException {
		StringBuilder body = new StringBuilder();
		char[] buffer = new char[4096];
		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			int read;
			while ((read = reader.read(buffer)) != -1) {
				if (body.length() + read > MAX_BODY_CHARS) {
					throw new IOException("The host's response is larger than we will read");
				}
				body.append(buffer, 0, read);
			}
		}
		return body.toString();
	}

	private JSONObject parse(String body) throws IOException {
		try {
			return new JSONObject(body);
		} catch (JSONException e) {
			throw new IOException("The host's response was not valid json", e);
		}
	}
}
