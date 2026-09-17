package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Locale;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLServerSocketFactory;
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

	private static final String KEYSTORE = "AndroidKeyStore";
	private static final String ALIAS = "cht-p2p-session";
	private static final char[] NO_PASSWORD = new char[0];

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
				KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE);
		generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
				.setCertificateSubject(new X500Principal("CN=" + sanitise(deviceLabel)))
				.setCertificateSerialNumber(BigInteger.ONE)
				.setDigests(KeyProperties.DIGEST_SHA256)
				.setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
				.build());
		generator.generateKeyPair();

		log(SessionCertificate.class, "Generated a session certificate");
		return new SessionCertificate(keyStore);
	}

	private static KeyStore loadKeyStore() throws GeneralSecurityException {
		try {
			KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
			keyStore.load(null);
			return keyStore;
		} catch (Exception e) {
			throw new GeneralSecurityException("Could not open the Android keystore", e);
		}
	}

	/** A subject is not a security boundary, but it should not be able to break the DN either. */
	private static String sanitise(String deviceLabel) {
		if (deviceLabel == null || deviceLabel.trim().isEmpty()) {
			return "CHT device";
		}
		return deviceLabel.replaceAll("[^A-Za-z0-9 ._-]", "").trim();
	}

	/** The socket factory NanoHTTPD serves through. */
	public SSLServerSocketFactory sslServerSocketFactory() throws GeneralSecurityException {
		try {
			KeyManagerFactory keyManagers =
					KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagers.init(keyStore, NO_PASSWORD);
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
		StringBuilder hex = new StringBuilder(digest.length * 3);
		for (byte b : digest) {
			if (hex.length() > 0) {
				hex.append(':');
			}
			hex.append(String.format(Locale.US, "%02X", b));
		}
		return hex.toString();
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
