package org.medicmobile.webapp.mobile.offlinesync;

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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;
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
	* not meant to be. It is pinned instead: the host shows its public key's SHA-256 fingerprint in
	* the QR code, and the peer refuses any certificate whose key does not match it. That is what
	* stops a second device on the same hotspot impersonating the host.
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
	/**
		* Every session gets its OWN alias, never a shared one.
		*
		* Deleting and regenerating under one alias is not enough: key material is cached per alias
		* inside the process, so the TLS layer can go on serving the retired key while
		* getCertificate() already returns the new one. The peer is then offered a key the QR code
		* does not name and pairing can never succeed. How long anything is cached is not ours to
		* know, and a fresh alias cannot collide with a stale entry either way.
		*/
	private static final String ALIAS_PREFIX = "cht-offline-sync-session";

	/** Every alias prefix this feature has ever used. Add to it when renaming, never replace. */
	private static final String[] OUR_ALIAS_PREFIXES = { ALIAS_PREFIX, "cht-p2p-session" };

	private KeyStore keyStore;
	private final String deviceLabel;
	/** The alias this session generated, or null before renew() and after destroy(). */
	private String alias;

	private SessionCertificate(KeyStore keyStore, String deviceLabel) {
		this.keyStore = keyStore;
		this.deviceLabel = deviceLabel;
	}

	/**
		* Prepares a certificate holder without minting anything yet.
		*
		* Nothing is generated here on purpose. Minting the key runs a real TLS handshake against
		* ourselves to prove the device can serve with it, which is worth doing but belongs to the
		* start of a session, not to app startup: doing it here made a device that cannot serve TLS
		* fail while the app was still opening, and the only visible effect was that the sharing
		* option quietly disappeared from the menu. {@link #renew()} is called when hosting starts,
		* and reports the failure where someone can see it.
		*
		* @param deviceLabel shown as the certificate subject, so a curious peer sees something
		*                    meaningful rather than a placeholder
		*/
	public static SessionCertificate forDevice(String deviceLabel) throws GeneralSecurityException {
		return new SessionCertificate(loadKeyStore(), deviceLabel);
	}

	/**
		* Replaces the key and certificate with a fresh pair.
		*
		* Called at the start of every hosting session: {@link #destroy()} removes the entry when a
		* session ends, so a second session would otherwise find nothing to serve TLS with.
		*/
	public void renew() throws GeneralSecurityException {
		// A reload, not the instance held since construction: that one is a snapshot, and reading a
		// freshly generated entry through it is where the stale key came from.
		keyStore = loadKeyStore();
		discardOldAliases();
		alias = ALIAS_PREFIX + "-" + UUID.randomUUID();

		KeyPairGenerator generator =
				KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_TYPE);
		// Both purposes, because which one the handshake needs depends on the cipher suite the two
		// devices agree on: a modern ECDHE suite has the server sign, an RSA key-transport suite has
		// it decrypt. Allowing only signing would work on most devices and fail on some.
		generator.initialize(new KeyGenParameterSpec.Builder(
						alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT)
				.setCertificateSubject(new X500Principal("CN=" + sanitise(deviceLabel)))
				.setCertificateSerialNumber(BigInteger.ONE)
				.setCertificateNotBefore(notBefore())
				.setCertificateNotAfter(notAfter())
				// NONE alongside the real digests for the same reason as the padding below: the TLS
				// stack hashes the handshake itself and asks the key for a raw operation over the
				// result, which keystore reports as digest NONE.
				.setDigests(
						KeyProperties.DIGEST_NONE,
						KeyProperties.DIGEST_SHA256,
						KeyProperties.DIGEST_SHA1)
				.setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
				// NONE alongside PKCS1 because the TLS stack does its own padding: to sign the
				// handshake it hands the key an already-padded block through a raw
				// RSA/ECB/NoPadding operation, and a key that only authorizes PKCS1 is refused by
				// the keystore, which fails the handshake and so the whole session.
				.setEncryptionPaddings(
						KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
						KeyProperties.ENCRYPTION_PADDING_NONE)
				.build());
		generator.generateKeyPair();

		assertUsableOnThisDevice();
		log(SessionCertificate.class, "Generated a session certificate");
	}

	/**
		* Removes any session key left behind, ours or a previous run's.
		*
		* Aliases are per session now, so a crash between renew() and destroy() would otherwise leave
		* one in the keystore for good.
		*/
	private void discardOldAliases() throws GeneralSecurityException {
		java.util.Enumeration<String> aliases = keyStore.aliases();
		while (aliases.hasMoreElements()) {
			String existing = aliases.nextElement();
			if (isOurs(existing)) {
				keyStore.deleteEntry(existing);
			}
		}
	}

	/**
		* Whether an entry belongs to this feature, under any name it has ever used.
		*
		* Renaming the feature renamed the alias, and the entries written under the old one stayed
		* behind: a keystore survives an upgrade. Retiring a name means adding it here, never
		* removing it, or its entries are orphaned on every device that ran that version.
		*/
	private static boolean isOurs(String existing) {
		for (String prefix : OUR_ALIAS_PREFIXES) {
			if (existing.startsWith(prefix)) {
				return true;
			}
		}
		return false;
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
		if (alias == null) {
			throw new GeneralSecurityException("No session certificate to serve");
		}
		try {
			KeyManagerFactory keyManagers =
					KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			// null, not an empty array: keystore-backed keys are not password protected, and some
			// implementations reject an empty password rather than treating it as none.
			keyManagers.init(keyStore, null);
			return NanoHTTPD.makeSSLSocketFactory(keyStore, serveOnly(keyManagers.getKeyManagers()));
		} catch (IOException e) {
			throw new GeneralSecurityException("Could not build the TLS socket factory", e);
		}
	}

	/**
		* Binds the key managers to this session's entry.
		*
		* A key manager handed the whole keystore picks whichever entry it likes, and the keystore
		* holds more than ours: entries this app wrote under older names outlive an upgrade, since
		* only uninstalling clears them. Choosing any of those serves a key the QR code does not
		* name, and pairing then fails in a way nothing on the device can explain. The peer trusts
		* exactly one key, so the server has to offer exactly that one.
		*/
	private KeyManager[] serveOnly(KeyManager[] managers) {
		KeyManager[] bound = new KeyManager[managers.length];
		for (int i = 0; i < managers.length; i++) {
			bound[i] = managers[i] instanceof X509KeyManager
					? new SessionKeyManager((X509KeyManager) managers[i], alias) : managers[i];
		}
		return bound;
	}

	/** A key manager that answers with this session's entry whatever it is asked for. */
	static final class SessionKeyManager implements X509KeyManager {

		private final X509KeyManager delegate;
		private final String alias;

		SessionKeyManager(X509KeyManager delegate, String alias) {
			this.delegate = delegate;
			this.alias = alias;
		}

		@Override public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
			return alias;
		}

		@Override public String[] getServerAliases(String keyType, Principal[] issuers) {
			return new String[] { alias };
		}

		@Override public X509Certificate[] getCertificateChain(String ignored) {
			return delegate.getCertificateChain(alias);
		}

		@Override public PrivateKey getPrivateKey(String ignored) {
			return delegate.getPrivateKey(alias);
		}

		// This side never acts as a TLS client: the peer verifies the host, not the other way round.
		@Override public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
			return null;
		}

		@Override public String[] getClientAliases(String keyType, Principal[] issuers) {
			return new String[0];
		}
	}

	/**
		* SHA-256 of the session's PUBLIC KEY, as uppercase hex separated by colons.
		*
		* The public key, deliberately, and not the certificate around it. The key is what proves
		* identity: only the device holding the matching private key can complete the handshake.
		* Everything else in the certificate (serial, validity, subject, the self-signature) is
		* self-asserted, adds no security to a self-signed certificate, and some keystores re-derive
		* it when the entry is read back. Hashing all of it would make pairing depend on a keystore
		* encoding its certificate identically twice, which nothing guarantees.
		*
		* This is also what certificate pinning normally means in practice: HPKP and the OWASP
		* guidance both pin the SubjectPublicKeyInfo rather than the certificate.
		*/
	public String fingerprint() throws GeneralSecurityException {
		if (alias == null) {
			throw new GeneralSecurityException("No session certificate to fingerprint");
		}
		Certificate certificate = keyStore.getCertificate(alias);
		if (certificate == null) {
			throw new GeneralSecurityException("No session certificate to fingerprint");
		}
		return fingerprintOfKey(certificate);
	}

	/**
		* The pinned value for a certificate, used by both ends so they cannot drift apart.
		*
		* @throws GeneralSecurityException if the certificate carries no readable public key
		*/
	static String fingerprintOfKey(Certificate certificate) throws GeneralSecurityException {
		if (certificate.getPublicKey() == null || certificate.getPublicKey().getEncoded() == null) {
			throw new GeneralSecurityException("Certificate has no readable public key");
		}
		return formatFingerprint(
				MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded()));
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
					"This device cannot serve TLS with a keystore-backed key " + whatIsBeingServed(), e);
		}
	}

	/**
		* Which entry the key manager is actually serving, for the failure above.
		*
		* The handshake reports a key that does not match the one just generated, and only the
		* keystore's own view can say whose key it is. Best effort by design: this runs while
		* reporting a failure and must not become a second one.
		*/
	private String whatIsBeingServed() {
		StringBuilder report = new StringBuilder("[mine=").append(alias);
		try {
			report.append(" held=");
			java.util.Enumeration<String> held = keyStore.aliases();
			while (held.hasMoreElements()) {
				report.append(held.nextElement()).append(',');
			}
			KeyManagerFactory factory =
					KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			factory.init(keyStore, null);
			for (javax.net.ssl.KeyManager manager : factory.getKeyManagers()) {
				if (manager instanceof javax.net.ssl.X509KeyManager) {
					report.append(" serving=").append(java.util.Arrays.toString(
							((javax.net.ssl.X509KeyManager) manager).getServerAliases("RSA", null)));
				}
			}
			report.append(" algo=").append(KeyManagerFactory.getDefaultAlgorithm());
		} catch (Exception e) {
			report.append(" unreadable:").append(e.getClass().getSimpleName());
		}
		return report.append(']').toString();
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
		}, "offline-sync-cert-selftest");
		thread.setDaemon(true);
		thread.start();
		return thread;
	}

	/** Drops the key, so the session's identity cannot be reused. */
	public void destroy() {
		try {
			if (alias != null && keyStore.containsAlias(alias)) {
				keyStore.deleteEntry(alias);
				log(SessionCertificate.class, "Session certificate destroyed");
			}
			alias = null;
		} catch (Exception e) {
			// The next session overwrites the entry anyway, so this is not fatal, but a key we
			// meant to destroy and did not is worth knowing about.
			warn(e, "Could not destroy the session certificate");
		}
	}
}
