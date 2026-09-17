package org.medicmobile.webapp.mobile.p2p;

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

	private static final byte[] HOST_CERT = "the host's certificate bytes".getBytes();
	private static final byte[] OTHER_CERT = "somebody else's certificate".getBytes();

	private X509Certificate certificateOf(byte[] encoded) throws Exception {
		X509Certificate certificate = mock(X509Certificate.class);
		when(certificate.getEncoded()).thenReturn(encoded);
		return certificate;
	}

	private String fingerprintOf(byte[] encoded) throws Exception {
		return SessionCertificate.formatFingerprint(MessageDigest.getInstance("SHA-256").digest(encoded));
	}

	@Test public void refusesToBuildWithoutAFingerprint() {
		assertThrows(GeneralSecurityException.class, () -> PinnedCertificateTrust.socketFactoryFor(null));
		assertThrows(GeneralSecurityException.class, () -> PinnedCertificateTrust.socketFactoryFor(""));
		assertThrows(GeneralSecurityException.class, () -> PinnedCertificateTrust.socketFactoryFor("   "));
	}

	@Test public void buildsASocketFactoryForAPinnedFingerprint() throws Exception {
		assertNotNull(PinnedCertificateTrust.socketFactoryFor(fingerprintOf(HOST_CERT)));
	}

	@Test public void fingerprintOf_matchesTheHostSideFormat() throws Exception {
		String fingerprint = PinnedCertificateTrust.fingerprintOf(certificateOf(HOST_CERT));

		assertTrue(fingerprint, fingerprint.matches("([0-9A-F]{2}:)+[0-9A-F]{2}"));
		org.junit.Assert.assertEquals(fingerprintOf(HOST_CERT), fingerprint);
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
				new X509Certificate[] { certificateOf(HOST_CERT) }, fingerprintOf(HOST_CERT));
	}

	/** The whole point: a different device answering on the host's address must be refused. */
	@Test public void verifyPinned_rejectsAnImpostor() throws Exception {
		CertificateException thrown = assertThrows(CertificateException.class,
				() -> PinnedCertificateTrust.verifyPinned(
						new X509Certificate[] { certificateOf(OTHER_CERT) }, fingerprintOf(HOST_CERT)));

		assertTrue(thrown.getMessage(), thrown.getMessage().contains("does not match"));
	}

	@Test public void verifyPinned_rejectsAnEmptyChain() throws Exception {
		assertThrows(CertificateException.class,
				() -> PinnedCertificateTrust.verifyPinned(new X509Certificate[0], fingerprintOf(HOST_CERT)));
		assertThrows(CertificateException.class,
				() -> PinnedCertificateTrust.verifyPinned(null, fingerprintOf(HOST_CERT)));
	}

	/** Only the leaf is pinned, so an extra certificate in the chain must not rescue an impostor. */
	@Test public void verifyPinned_onlyTrustsTheLeafCertificate() throws Exception {
		assertThrows(CertificateException.class,
				() -> PinnedCertificateTrust.verifyPinned(
						new X509Certificate[] { certificateOf(OTHER_CERT), certificateOf(HOST_CERT) },
						fingerprintOf(HOST_CERT)));
	}
}
