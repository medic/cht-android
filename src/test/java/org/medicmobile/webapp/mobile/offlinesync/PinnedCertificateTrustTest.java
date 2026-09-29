package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@RunWith(RobolectricTestRunner.class)
public class PinnedCertificateTrustTest {

	private static java.security.PublicKey hostKey;
	private static java.security.PublicKey otherKey;

	@org.junit.BeforeClass public static void generateKeys() throws Exception {
		// Real keys rather than invented bytes: the pin is taken over the encoded public key, so a
		// fixture that is not one would not exercise the thing under test.
		java.security.KeyPairGenerator keys = java.security.KeyPairGenerator.getInstance("RSA");
		hostKey = keys.generateKeyPair().getPublic();
		otherKey = keys.generateKeyPair().getPublic();
	}

	private X509Certificate certificateFor(java.security.PublicKey key) {
		X509Certificate certificate = mock(X509Certificate.class);
		when(certificate.getPublicKey()).thenReturn(key);
		return certificate;
	}

	private String fingerprintOf(java.security.PublicKey key) throws Exception {
		return SessionCertificate.formatFingerprint(
				MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));
	}

	@Test public void refusesToBuildWithoutAFingerprint() {
		assertThrows(GeneralSecurityException.class, () -> PinnedCertificateTrust.socketFactoryFor(null));
		assertThrows(GeneralSecurityException.class, () -> PinnedCertificateTrust.socketFactoryFor(""));
		assertThrows(GeneralSecurityException.class, () -> PinnedCertificateTrust.socketFactoryFor("   "));
	}

	@Test public void buildsASocketFactoryForAPinnedFingerprint() throws Exception {
		assertNotNull(PinnedCertificateTrust.socketFactoryFor(fingerprintOf(hostKey)));
	}

	@Test public void fingerprintOf_matchesTheHostSideFormat() throws Exception {
		String fingerprint = PinnedCertificateTrust.fingerprintOf(certificateFor(hostKey));

		assertTrue(fingerprint, fingerprint.matches("([0-9A-F]{2}:)+[0-9A-F]{2}"));
		org.junit.Assert.assertEquals(fingerprintOf(hostKey), fingerprint);
	}

	@Test public void matches_ignoresCaseAndSeparators() {
		assertTrue(PinnedCertificateTrust.matches("AB:CD:EF", "abcdef"));
		assertTrue(PinnedCertificateTrust.matches("ab cd ef", "AB:CD:EF"));
	}

	@Test public void matches_rejectsADifferentFingerprint() {
		assertFalse(PinnedCertificateTrust.matches("AB:CD:EF", "AB:CD:E0"));
		assertFalse(PinnedCertificateTrust.matches("AB:CD:EF", ""));
		assertFalse(PinnedCertificateTrust.matches("AB:CD:EF", null));
	}

	@Test public void verifyPinned_acceptsTheCertificateFromTheQrCode() throws Exception {
		PinnedCertificateTrust.verifyPinned(
				new X509Certificate[] { certificateFor(hostKey) }, fingerprintOf(hostKey));
	}

	/** The whole point: a different device answering on the host's address must be refused. */
	/**
		* The pin is over the key, so a certificate re-encoded around the same key must still be
		* accepted. Keystores may re-derive the certificate when an entry is read back, and pinning
		* the whole certificate would make pairing depend on that never happening.
		*/
	@Test public void verifyPinned_acceptsTheSameKeyInADifferentCertificate() throws Exception {
		java.security.KeyPair pair = java.security.KeyPairGenerator.getInstance("RSA").generateKeyPair();

		X509Certificate asFingerprinted = mock(X509Certificate.class);
		when(asFingerprinted.getPublicKey()).thenReturn(pair.getPublic());
		when(asFingerprinted.getEncoded()).thenReturn(new byte[] { 1, 1, 1 });

		X509Certificate asPresented = mock(X509Certificate.class);
		when(asPresented.getPublicKey()).thenReturn(pair.getPublic());
		// the same key, wrapped in a certificate the device encoded differently
		when(asPresented.getEncoded()).thenReturn(new byte[] { 2, 2, 2 });

		PinnedCertificateTrust.verifyPinned(
				new X509Certificate[] { asPresented },
				PinnedCertificateTrust.fingerprintOf(asFingerprinted));
	}

	/** The other half: a different key is still refused, so pinning the key is not weaker. */
	@Test public void verifyPinned_stillRefusesADifferentKey() throws Exception {
		java.security.KeyPairGenerator keys = java.security.KeyPairGenerator.getInstance("RSA");
		X509Certificate host = mock(X509Certificate.class);
		when(host.getPublicKey()).thenReturn(keys.generateKeyPair().getPublic());

		X509Certificate impostor = mock(X509Certificate.class);
		when(impostor.getPublicKey()).thenReturn(keys.generateKeyPair().getPublic());
		// identical certificate bytes, so only the key can tell them apart
		when(impostor.getEncoded()).thenReturn(new byte[] { 1, 1, 1 });
		when(host.getEncoded()).thenReturn(new byte[] { 1, 1, 1 });

		assertThrows(CertificateException.class, () -> PinnedCertificateTrust.verifyPinned(
				new X509Certificate[] { impostor }, PinnedCertificateTrust.fingerprintOf(host)));
	}

	/**
		* "Does not match" alone cannot tell a wrong device from a device whose keystore returns a
		* different encoding than the one we fingerprinted, and only the phone in the field can say
		* which it is.
		*/
	@Test public void verifyPinned_saysWhatItExpectedAndWhatItGot() throws Exception {
		CertificateException thrown = assertThrows(CertificateException.class,
				() -> PinnedCertificateTrust.verifyPinned(
						new X509Certificate[] { certificateFor(otherKey) },
						"AA:BB:CC:DD:EE:FF:00:11:22"));

		assertTrue(thrown.getMessage().contains("expected AABBCCDDEEFF0011"));
		assertTrue(thrown.getMessage().contains("got "));
	}

	@Test public void verifyPinned_rejectsAnImpostor() {
		CertificateException thrown = assertThrows(CertificateException.class,
				() -> PinnedCertificateTrust.verifyPinned(
						new X509Certificate[] { certificateFor(otherKey) }, fingerprintOf(hostKey)));

		assertTrue(thrown.getMessage(), thrown.getMessage().contains("does not match"));
	}

	@Test public void verifyPinned_rejectsAnEmptyChain() {
		assertThrows(CertificateException.class,
				() -> PinnedCertificateTrust.verifyPinned(new X509Certificate[0], fingerprintOf(hostKey)));
		assertThrows(CertificateException.class,
				() -> PinnedCertificateTrust.verifyPinned(null, fingerprintOf(hostKey)));
	}

	/** Only the leaf is pinned, so an extra certificate in the chain must not rescue an impostor. */
	@Test public void verifyPinned_onlyTrustsTheLeafCertificate() {
		assertThrows(CertificateException.class,
				() -> PinnedCertificateTrust.verifyPinned(
						new X509Certificate[] { certificateFor(otherKey), certificateFor(hostKey) },
						fingerprintOf(hostKey)));
	}
}
