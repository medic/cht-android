package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Collections;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SessionCertificateDeviceTest {

	private static final String OTHER = "session-certificate-device-test";

	private SessionCertificate certificate;

	@After
	public void tearDown() throws Exception {
		Thread.interrupted();
		if (certificate != null) {
			certificate.destroy();
		}
		keyStore().deleteEntry(OTHER);
	}

	@Test
	public void aFailedHandshake_leavesTheCallingThreadUsable() throws Exception {
		certificate = SessionCertificate.forDevice("test");
		certificate.renew();
		pairOurKeyWithAnotherCertificate();

		assertThrows(GeneralSecurityException.class, certificate::assertUsableOnThisDevice);
		assertFalse(Thread.currentThread().isInterrupted());
	}

	/** The served key no longer matches the certificate the self-test pins, so the handshake fails. */
	private static void pairOurKeyWithAnotherCertificate() throws Exception {
		KeyPairGenerator generator =
				KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
		generator.initialize(new KeyGenParameterSpec.Builder(OTHER, KeyProperties.PURPOSE_SIGN)
				.setDigests(KeyProperties.DIGEST_SHA256)
				.build());
		generator.generateKeyPair();

		KeyStore keyStore = keyStore();
		Certificate other = keyStore.getCertificate(OTHER);
		for (String alias : Collections.list(keyStore.aliases())) {
			if (!alias.equals(OTHER)) {
				PrivateKey ours = (PrivateKey) keyStore.getKey(alias, null);
				keyStore.setKeyEntry(alias, ours, null, new Certificate[] { other });
			}
		}
	}

	private static KeyStore keyStore() throws Exception {
		KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
		keyStore.load(null);
		return keyStore;
	}
}
