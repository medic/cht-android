package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class P2pPeerTest {

	private HotspotJoiner joiner;
	private P2pPeer peer;
	private P2pPeer.PairCallback callback;

	@Before public void setUp() {
		joiner = mock(HotspotJoiner.class);
		peer = new P2pPeer(joiner);
		callback = mock(P2pPeer.PairCallback.class);
	}

	private String validPayload() throws Exception {
		return QrCodeHelper.buildPayload(new QrCodeHelper.HotspotCredentials(
				"CHT-P2P-a3f7", "a-password", "192.168.49.1", 8443, "AB:CD:EF:01:23:45"));
	}

	@Test public void constructor_rejectsAMissingJoiner() {
		assertThrows(IllegalArgumentException.class, () -> new P2pPeer(null));
	}

	@Test public void pair_rejectsAMissingCallback() throws Exception {
		String payload = validPayload();

		assertThrows(IllegalArgumentException.class, () -> peer.pair(payload, null));
	}

	@Test public void pair_joinsTheNetworkNamedInTheCode() throws Exception {
		peer.pair(validPayload(), callback);

		verify(joiner).join(org.mockito.ArgumentMatchers.eq("CHT-P2P-a3f7"),
				org.mockito.ArgumentMatchers.eq("a-password"), any());
	}

	/** A code that fails validation must not reach the radio at all. */
	@Test public void pair_refusesAnInvalidPayloadWithoutJoining() {
		peer.pair("not a qr payload", callback);

		verify(callback).onFailed(anyString());
		verify(joiner, never()).join(anyString(), anyString(), any());
	}

	@Test public void pair_refusesAPayloadWithoutAFingerprint() throws Exception {
		JSONObject payload = new JSONObject(validPayload());
		payload.remove("fp");

		peer.pair(payload.toString(), callback);

		verify(callback).onFailed("unreadable_payload");
		verify(joiner, never()).join(anyString(), anyString(), any());
	}

	@Test public void pair_reportsAJoinFailureAsIs() throws Exception {
		doAnswer(invocation -> {
			HotspotJoiner.JoinCallback cb = invocation.getArgument(2);
			cb.onFailed("join_unsupported");
			return null;
		}).when(joiner).join(anyString(), anyString(), any());

		peer.pair(validPayload(), callback);

		verify(callback).onFailed("join_unsupported");
	}

	@Test public void pair_treatsLosingTheNetworkAsAFailure() throws Exception {
		doAnswer(invocation -> {
			HotspotJoiner.JoinCallback cb = invocation.getArgument(2);
			cb.onLost();
			return null;
		}).when(joiner).join(anyString(), anyString(), any());

		peer.pair(validPayload(), callback);

		verify(callback).onFailed("network_lost");
	}

	@Test public void unpair_leavesTheNetwork() {
		peer.unpair();

		verify(joiner).leave();
	}
}
