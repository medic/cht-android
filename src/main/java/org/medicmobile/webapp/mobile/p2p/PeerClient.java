package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.log;

import android.net.Network;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
	* Talks to the host over the joined network, accepting only the pinned certificate.
	*
	* Two things have to be right for this to reach the intended device and nothing else. The socket
	* is bound to the {@link Network} the join returned, because the hotspot is not the device's
	* default route and unbound traffic would leave over mobile data. And the TLS handshake trusts
	* only the certificate whose fingerprint came from the QR code, because everyone in range can
	* join the same hotspot.
	*/
public class PeerClient {

	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 10_000;
	// The status response is a few dozen bytes. The read timeout only bounds inactivity, so a
	// host that streams steadily would never trip it: this bounds the total instead.
	private static final int MAX_BODY_CHARS = 64 * 1024;

	/**
		* Accepts any hostname, on purpose, because the pin decides identity here.
		*
		* The host's certificate is issued to a device label and we connect to an IP address that
		* changes every session, so name-based verification could never pass and would only stop
		* pairing from working. What replaces it is stricter, not weaker: the trust manager accepts
		* exactly one certificate, the one whose fingerprint was read from the QR code, and rejects
		* every other including any a certificate authority would vouch for.
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
		* @throws IOException if the host cannot be reached, or presents a different certificate
		*/
	public String fetchStatus(String ipAddress, int port) throws IOException {
		JSONObject body = get(ipAddress, port, "/_p2p/status");
		try {
			String label = body.getString("device_label");
			log(PeerClient.class, "Reached host: " + label);
			return label;
		} catch (JSONException e) {
			throw new IOException("The host's status response was not what we expect", e);
		}
	}

	private JSONObject get(String ipAddress, int port, String path) throws IOException {
		HttpsURLConnection connection = open(ipAddress, port, path);
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

	private HttpsURLConnection open(String ipAddress, int port, String path) throws IOException {
		URL url = new URL("https://" + ipAddress + ":" + port + path);
		// through the joined network, not whatever the device would otherwise use
		HttpsURLConnection connection = (HttpsURLConnection) network.openConnection(url);
		try {
			connection.setSSLSocketFactory(PinnedCertificateTrust.socketFactoryFor(certFingerprint));
		} catch (GeneralSecurityException e) {
			throw new IOException("Could not pin the host's certificate", e);
		}
		connection.setHostnameVerifier(PIN_IS_THE_IDENTITY_CHECK);
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setRequestMethod("GET");
		return connection;
	}

	private String readBody(HttpsURLConnection connection) throws IOException {
		return readBounded(connection.getInputStream());
	}

	/** Package-private so the bound can be tested without standing up a server. */
	static String readBounded(InputStream stream) throws IOException {
		StringBuilder body = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (body.length() + line.length() > MAX_BODY_CHARS) {
					throw new IOException("The host's response is larger than we will read");
				}
				body.append(line);
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
