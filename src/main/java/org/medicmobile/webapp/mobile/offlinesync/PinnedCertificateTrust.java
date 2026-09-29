package org.medicmobile.webapp.mobile.offlinesync;

import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
	* Trusts exactly one key: the one whose fingerprint the peer read from the QR code.
	*
	* The host's certificate is self-signed and generated seconds earlier, so no certificate
	* authority can vouch for it and normal validation is useless here. Pinning replaces that trust:
	* the QR code travelled over a channel an attacker cannot forge (a human looking at a screen),
	* so the fingerprint on it is the anchor.
	*
	* Everyone within range can join the hotspot, so without this a second device could answer on
	* the host's address and the peer would never know.
	*/
public final class PinnedCertificateTrust {

	private PinnedCertificateTrust() {}

	/**
		* A socket factory that completes a handshake only with the pinned certificate.
		*
		* @param expectedFingerprint SHA-256 of the host certificate, as read from the QR code
		*/
	public static SSLSocketFactory socketFactoryFor(String expectedFingerprint)
			throws GeneralSecurityException {
		if (expectedFingerprint == null || expectedFingerprint.trim().isEmpty()) {
			throw new GeneralSecurityException("Refusing to connect without a pinned fingerprint");
		}

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(null, new TrustManager[] { new PinningTrustManager(expectedFingerprint) }, null);
		return context.getSocketFactory();
	}

	/** Compares two fingerprints, ignoring case and separators. */
	static boolean matches(String expected, String actual) {
		return normalise(expected).equals(normalise(actual));
	}

	private static String normalise(String fingerprint) {
		return fingerprint == null ? "" : fingerprint.replaceAll("[^A-Fa-f0-9]", "").toUpperCase(java.util.Locale.US);
	}

	static String fingerprintOf(X509Certificate certificate) throws GeneralSecurityException {
		return SessionCertificate.fingerprintOfKey(certificate);
	}

	/**
		* The check itself, kept separate from the TLS plumbing so it can be tested directly.
		*
		* @throws CertificateException if the host is not the device whose QR code was scanned
		*/
	static void verifyPinned(X509Certificate[] chain, String expectedFingerprint)
			throws CertificateException {
		if (chain == null || chain.length == 0) {
			throw new CertificateException("The host presented no certificate");
		}

		String actual;
		try {
			actual = fingerprintOf(chain[0]);
		} catch (GeneralSecurityException e) {
			// CertificateException extends GeneralSecurityException, so the mismatch below is
			// thrown outside this block: catching it here would hide why the host was refused.
			throw new CertificateException("Could not read the host's certificate", e);
		}

		if (!matches(expectedFingerprint, actual)) {
			// Both values, because "does not match" alone cannot tell a wrong device from a device
			// whose keystore hands back a different encoding than the one we fingerprinted. Neither
			// is a secret: the expected one is printed in the QR code and the other was just sent
			// over the wire.
			throw new CertificateException(
					"The host's certificate does not match the one in the QR code (expected " +
							shorten(expectedFingerprint) + ", got " + shorten(actual) +
							" subject " + subjectOf(chain[0]) + ")");
		}
	}

	/**
		* The certificate's subject, for the message above.
		*
		* Null-safe because this runs while rejecting a certificate we have already decided not to
		* trust: anything about it may be missing or malformed, and a diagnostic must never be the
		* thing that throws.
		*/
	private static String subjectOf(X509Certificate certificate) {
		try {
			return certificate.getSubjectX500Principal() == null
					? "unknown" : certificate.getSubjectX500Principal().getName();
		} catch (RuntimeException e) {
			return "unreadable";
		}
	}

	/** Enough of a fingerprint to compare by eye, in a message that has to stay readable. */
	private static String shorten(String fingerprint) {
		String normalised = normalise(fingerprint);
		return normalised.length() <= 16 ? normalised : normalised.substring(0, 16);
	}

	/**
		* Lint objects to custom trust managers because they are usually written to accept
		* everything. This one is the opposite: it accepts a single certificate and rejects all
		* others, including any a certificate authority would vouch for. Delegating to the platform
		* default is not an option, since the host's certificate is self-signed and seconds old, so
		* the default would reject it outright and pairing could never work.
		*/
	@android.annotation.SuppressLint("CustomX509TrustManager")
	private static final class PinningTrustManager implements X509TrustManager {

		private final String expectedFingerprint;

		PinningTrustManager(String expectedFingerprint) {
			this.expectedFingerprint = expectedFingerprint;
		}

		@Override public void checkServerTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
			verifyPinned(chain, expectedFingerprint);
		}

		/** This side never presents a certificate, so there is nothing for the host to check. */
		@Override public void checkClientTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
			throw new CertificateException("This trust manager is for connecting, not serving");
		}

		@Override public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}
}
