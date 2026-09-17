package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.net.Network;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
	* Covers what can be checked off-device. Completing a pinned handshake needs a real host with a
	* keystore-backed certificate, so that is verified on hardware rather than simulated here.
	*/
@RunWith(RobolectricTestRunner.class)
public class PeerClientTest {

	private static final String FINGERPRINT = "AB:CD:EF:01:23:45";

	@Test public void constructor_rejectsAMissingNetwork() {
		assertThrows(IllegalArgumentException.class, () -> new PeerClient(null, FINGERPRINT));
	}

	/** Without a fingerprint there is nothing to pin, so connecting would trust any answer. */
	@Test public void constructor_refusesToConnectWithoutAFingerprint() {
		Network network = mock(Network.class);

		assertThrows(IllegalArgumentException.class, () -> new PeerClient(network, null));
		assertThrows(IllegalArgumentException.class, () -> new PeerClient(network, ""));
		assertThrows(IllegalArgumentException.class, () -> new PeerClient(network, "   "));
	}
}
