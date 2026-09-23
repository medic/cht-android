package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.x500.X500Principal;

import fi.iki.elonen.NanoHTTPD;

/**
	* A throwaway TLS identity for one pairing session.
	*
	* The peer has no way to know this certificate in advance, so it is not trusted by a CA and is
	* not meant to be. It is pinned instead: the host shows the certificate's SHA-256 fingerprint in
	* the QR code, and the peer refuses any certificate that does not match it. That is what stops a
	* second device on the same hotspot impersonating the host.
	*
	* A fresh key is generated per session and dropped afterwards, so a captured fingerprint is
	* worthless once the session ends.
	*
	* Keys live in the Android keystore and never leave it. Generating them there also produces the
	* self-signed certificate for us, which is why no certificate library is needed.
	*/
@android.annotation.TargetApi(26)
public class SessionCertificate {

	private static final String KEYSTORE_TYPE = "AndroidKeyStore";
	private static final String DEFAULT_LABEL = "CHT device";
	private static final String LOOPBACK = "127.0.0.1";
	private static final int HANDSHAKE_TIMEOUT_MS = 5000;
	private static final String ALIAS = "cht-p2p-session";

	private final KeyStore keyStore;

	private SessionCertificate(KeyStore keyStore) {
		this.keyStore = keyStore;
	}

	/**
		* Generates a new key and certificate, replacing any left over from a previous session.
		*
		* @param deviceLabel shown as the certificate subject, so a curious peer sees something
		*                    meaningful rather than a placeholder
		*/
	public static SessionCertificate generate(String deviceLabel) throws GeneralSecurityException {
		KeyStore keyStore = loadKeyStore();
		if (keyStore.containsAlias(ALIAS)) {
			keyStore.deleteEntry(ALIAS);
		}

		KeyPairGenerator generator =
				KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_TYPE);
		// Both purposes, because which one the handshake needs depends on the cipher suite the two
		// devices agree on: a modern ECDHE suite has the server sign, an RSA key-transport suite has
		// it decrypt. Allowing only signing would work on most devices and fail on some.
		generator.initialize(new KeyGenParameterSpec.Builder(
						ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT)
				.setCertificateSubject(new X500Principal("CN=" + sanitise(deviceLabel)))
				.setCertificateSerialNumber(BigInteger.ONE)
				.setCertificateNotBefore(notBefore())
				.setCertificateNotAfter(notAfter())
				.setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
				.setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
				.build());
		generator.generateKeyPair();

		SessionCertificate certificate = new SessionCertificate(keyStore);
		certificate.assertUsableOnThisDevice();
		log(SessionCertificate.class, "Generated a session certificate");
		return certificate;
	}

	/** Backdated a little, so a peer whose clock runs slightly behind still accepts it. */
	private static Date notBefore() {
		return new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
	}

	/** A session lasts minutes; a day is generous and keeps a leaked certificate short-lived. */
	private static Date notAfter() {
		return new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
	}

	private static KeyStore loadKeyStore() throws GeneralSecurityException {
		try {
			KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
			keyStore.load(null);
			return keyStore;
		} catch (Exception e) {
			throw new GeneralSecurityException("Could not open the Android keystore", e);
		}
	}

	/** A subject is not a security boundary, but it should not be able to break the DN either. */
	static String sanitise(String deviceLabel) {
		if (deviceLabel == null) {
			return DEFAULT_LABEL;
		}
		// Build.MODEL is whatever the vendor set, so it can be entirely outside this set and strip
		// to nothing. Falling through with an empty subject would leave the certificate with a bare
		// "CN=".
		String cleaned = deviceLabel.replaceAll("[^A-Za-z0-9 ._-]", "").trim();
		return cleaned.isEmpty() ? DEFAULT_LABEL : cleaned;
	}

	/** The socket factory NanoHTTPD serves through. */
	public SSLServerSocketFactory sslServerSocketFactory() throws GeneralSecurityException {
		try {
			KeyManagerFactory keyManagers =
					KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			// null, not an empty array: keystore-backed keys are not password protected, and some
			// implementations reject an empty password rather than treating it as none.
			keyManagers.init(keyStore, null);
			return NanoHTTPD.makeSSLSocketFactory(keyStore, keyManagers);
		} catch (IOException e) {
			throw new GeneralSecurityException("Could not build the TLS socket factory", e);
		}
	}

	/**
		* SHA-256 of the certificate, as uppercase hex separated by colons.
		*
		* This is the value the peer pins against, so it is taken over the DER encoding: the bytes
		* actually presented during the handshake, not a parsed or re-encoded form of them.
		*/
	public String fingerprint() throws GeneralSecurityException {
		Certificate certificate = keyStore.getCertificate(ALIAS);
		if (certificate == null) {
			throw new GeneralSecurityException("No session certificate to fingerprint");
		}
		return formatFingerprint(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
	}

	static String formatFingerprint(byte[] digest) {
		// Indexed rather than for-each so the separator can key off the position. Checking the
		// builder's own emptiness instead would need CharSequence.isEmpty, which is API 35.
		StringBuilder hex = new StringBuilder(digest.length * 3);
		for (int i = 0; i < digest.length; i++) {
			if (i > 0) {
				hex.append(':');
			}
			hex.append(String.format(Locale.US, "%02X", digest[i]));
		}
		return hex.toString();
	}

	/**
		* Completes a TLS handshake against ourselves, to prove this device can actually serve with
		* this key before a hotspot is advertised.
		*
		* Building the socket factory only proves the key loads. Keystore keys are held by hardware
		* on many devices, and whether one can sign a handshake is something only that device can
		* answer. Doing it here costs a moment at session start and turns a mysterious failure later
		* into a clear one now.
		*/
	void assertUsableOnThisDevice() throws GeneralSecurityException {
		SSLServerSocketFactory serverFactory = sslServerSocketFactory();
		String fingerprint = fingerprint();

		try (SSLServerSocket serverSocket = (SSLServerSocket) serverFactory.createServerSocket(
					0, 1, InetAddress.getByName(LOOPBACK))) {
			serverSocket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
			Thread server = acceptOneConnection(serverSocket);

			// the peer's own pinning code, so this checks both halves of the handshake
			SSLSocketFactory clientFactory = PinnedCertificateTrust.socketFactoryFor(fingerprint);
			try (SSLSocket client =
						(SSLSocket) clientFactory.createSocket(LOOPBACK, serverSocket.getLocalPort())) {
				client.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
				client.startHandshake();
			}
			server.join(HANDSHAKE_TIMEOUT_MS);
		} catch (IOException | InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new GeneralSecurityException(
					"This device cannot serve TLS with a keystore-backed key", e);
		}
	}

	/** Accepts a single handshake in the background so the client above has something to talk to. */
	private static Thread acceptOneConnection(SSLServerSocket serverSocket) {
		Thread thread = new Thread(() -> {
			try (SSLSocket accepted = (SSLSocket) serverSocket.accept()) {
				accepted.startHandshake();
			} catch (IOException e) {
				// the client reports the failure; this side only has to not hang
				warn(e, "Loopback handshake failed on the serving side");
			}
		}, "p2p-cert-selftest");
		thread.setDaemon(true);
		thread.start();
		return thread;
	}

	/** Drops the key, so the session's identity cannot be reused. */
	public void destroy() {
		try {
			if (keyStore.containsAlias(ALIAS)) {
				keyStore.deleteEntry(ALIAS);
				log(SessionCertificate.class, "Session certificate destroyed");
			}
		} catch (Exception e) {
			// The next session overwrites the entry anyway, so this is not fatal, but a key we
			// meant to destroy and did not is worth knowing about.
			warn(e, "Could not destroy the session certificate");
		}
	}
}
