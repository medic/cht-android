package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509KeyManager;

/**
	* The server must offer the key the QR code names, whatever else the keystore holds.
	*
	* Entries written under a name the feature used in an earlier version outlive an upgrade, and a
	* key manager handed the whole keystore is free to pick one of them. It then serves a key the
	* peer was never told to trust, so pairing fails on an upgraded device while working on one
	* installed fresh.
	*/
@RunWith(RobolectricTestRunner.class)
public class SessionKeyManagerTest {

	private static final String OURS = "cht-offline-sync-session-1234";
	private static final String SOMEONE_ELSE = "aaa-left-over-from-an-older-version";

	private X509KeyManager delegateChoosing(String alias, X509Certificate[] chain) {
		X509KeyManager delegate = mock(X509KeyManager.class);
		when(delegate.chooseServerAlias("RSA", null, null)).thenReturn(alias);
		when(delegate.getServerAliases("RSA", null)).thenReturn(new String[] { alias, OURS });
		when(delegate.getCertificateChain(OURS)).thenReturn(chain);
		return delegate;
	}

	@Test public void servesOurAliasEvenWhenTheDelegateWouldPickAnother() throws Exception {
		X509Certificate[] ourChain = { mock(X509Certificate.class) };

		X509KeyManager served = new SessionCertificate.SessionKeyManager(
				delegateChoosing(SOMEONE_ELSE, ourChain), OURS);
		assertEquals(OURS, served.chooseServerAlias("RSA", null, null));
		assertArrayEquals(new String[] { OURS }, served.getServerAliases("RSA", null));
		// asked for someone else's entry, it still answers with ours
		assertArrayEquals(ourChain, served.getCertificateChain(SOMEONE_ELSE));
	}
}
