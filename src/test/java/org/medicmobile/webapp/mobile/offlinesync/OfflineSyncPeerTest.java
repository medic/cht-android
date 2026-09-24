package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class OfflineSyncPeerTest {

	private HotspotJoiner joiner;
	private BundleSpool outbox;
	private OfflineSyncPeer peer;
	private OfflineSyncPeer.PairCallback callback;

	@Before public void setUp() {
		joiner = mock(HotspotJoiner.class);
		outbox = mock(BundleSpool.class);
		peer = new OfflineSyncPeer(joiner, outbox);
		callback = mock(OfflineSyncPeer.PairCallback.class);
	}

	private String validPayload() throws Exception {
		return QrCodeHelper.buildPayload(new QrCodeHelper.HotspotCredentials(
				"AndroidShare_1234", "a-password", "192.168.49.1", 8443, "AB:CD:EF:01:23:45"));
	}

	@Test public void constructor_rejectsMissingCollaborators() {
		assertThrows(IllegalArgumentException.class, () -> new OfflineSyncPeer(null, outbox));
		assertThrows(IllegalArgumentException.class, () -> new OfflineSyncPeer(joiner, null));
	}

	@Test public void pair_rejectsAMissingCallback() throws Exception {
		String payload = validPayload();

		assertThrows(IllegalArgumentException.class, () -> peer.pair(payload, null));
	}

	@Test public void pair_joinsTheNetworkNamedInTheCode() throws Exception {
		peer.pair(validPayload(), callback);

		verify(joiner).join(org.mockito.ArgumentMatchers.eq("AndroidShare_1234"),
				org.mockito.ArgumentMatchers.eq("a-password"), any());
	}

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

	@Test public void pair_refusesAPayloadOfAnotherVersion() throws Exception {
		JSONObject payload = new JSONObject(validPayload());
		payload.put("v", 99);

		peer.pair(payload.toString(), callback);

		verify(callback).onFailed("unreadable_payload");
		verify(joiner, never()).join(anyString(), anyString(), any());
	}

	@Test public void pair_refusesAnEmptyFingerprint() throws Exception {
		JSONObject payload = new JSONObject(validPayload());
		payload.put("fp", "");

		peer.pair(payload.toString(), callback);

		verify(callback).onFailed("unreadable_payload");
		verify(joiner, never()).join(anyString(), anyString(), any());
	}

	@Test public void aRecreatedScreenHearsHowThePairingEnds() throws Exception {
		ArgumentCaptor<HotspotJoiner.JoinCallback> join = ArgumentCaptor.forClass(HotspotJoiner.JoinCallback.class);
		peer.pair(validPayload(), callback);
		verify(joiner).join(anyString(), anyString(), join.capture());
		OfflineSyncPeer.PairCallback recreated = mock(OfflineSyncPeer.PairCallback.class);

		peer.redirect(recreated);
		join.getValue().onLost();

		verify(recreated).onFailed("network_lost");
		verify(callback, never()).onFailed(anyString());
	}

	@Test public void pair_reportsAJoinFailureAsIs() throws Exception {
		doAnswer(invocation -> {
			HotspotJoiner.JoinCallback cb = invocation.getArgument(2);
			cb.onFailed("join_failed");
			return null;
		}).when(joiner).join(anyString(), anyString(), any());

		peer.pair(validPayload(), callback);

		verify(callback).onFailed("join_failed");
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

	@Test public void openBundle_handsBackTheIdThePiecesBelongTo() throws Exception {
		when(outbox.create()).thenReturn("bundle-1");

		assertEquals("bundle-1", peer.openBundle());
	}

	/** The webapp has to learn it cannot send now, rather than writing pieces into nothing. */
	@Test public void openBundle_isNullWhenThereIsNowhereToPutIt() throws Exception {
		when(outbox.create()).thenThrow(new java.io.IOException("no space"));

		assertNull(peer.openBundle());
	}

	@Test public void writeBundle_decodesThePieceItIsGiven() throws Exception {
		assertTrue(peer.writeBundle("bundle-1", "b25l"));

		verify(outbox).append("bundle-1", "one".getBytes("UTF-8"));
	}

	@Test public void writeBundle_isFalseWhenThePieceCannotBeStored() throws Exception {
		doThrow(new java.io.IOException("no space")).when(outbox).append(any(), any());

		assertFalse(peer.writeBundle("bundle-1", "b25l"));
	}

	/** Sending before a host has proved who it is would be sending to nobody in particular. */
	@Test public void sendBundle_refusesWhenThereIsNoPairedHost() {
		OfflineSyncPeer.SendCallback sendCallback = mock(OfflineSyncPeer.SendCallback.class);

		peer.sendBundle("bundle-1", "envelope", "signature", sendCallback);

		verify(sendCallback).onFailed("not_paired");
		verify(sendCallback, never()).onSent();
	}

	@Test public void sendBundle_rejectsAMissingCallback() {
		assertThrows(
				IllegalArgumentException.class,
				() -> peer.sendBundle("bundle-1", "envelope", "signature", null));
	}

	@Test public void discardBundle_dropsOneThatWillNotBeSent() {
		when(outbox.delete("bundle-1")).thenReturn(true);

		assertTrue(peer.discardBundle("bundle-1"));
	}

	/** Ids come from the webapp, so one that is not a bundle id is refused, not acted on. */
	@Test public void discardBundle_isFalseForSomethingThatIsNotABundleId() {
		when(outbox.delete(any())).thenThrow(new IllegalArgumentException("not a bundle id"));

		assertFalse(peer.discardBundle("../../secrets"));
	}
}
