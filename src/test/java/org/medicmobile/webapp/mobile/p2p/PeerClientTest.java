package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.net.Network;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

	@Test public void readBounded_readsASmallResponse() throws Exception {
		String body = PeerClient.readBounded(
				new ByteArrayInputStream("{\"device_label\":\"Sup\"}".getBytes(StandardCharsets.UTF_8)));

		assertEquals("{\"device_label\":\"Sup\"}", body);
	}

	/**
		* The read timeout only bounds inactivity, so a host streaming steadily would never trip it.
		* Without a size bound one could exhaust the phone's memory from the other side of the pairing.
		*/
	@Test public void readBounded_refusesAResponseThatNeverEnds() {
		byte[] huge = new byte[200 * 1024];
		Arrays.fill(huge, (byte) 'x');

		assertThrows(IOException.class,
				() -> PeerClient.readBounded(new ByteArrayInputStream(huge)));
	}
}
