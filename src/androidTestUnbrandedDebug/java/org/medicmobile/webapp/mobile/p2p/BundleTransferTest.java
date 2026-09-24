package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
	* The peer-to-peer transfer, run for real on one device.
	*
	* Everything a handover does except bringing up the radio happens here: a session certificate
	* minted in the Android keystore, a TLS server behind it, a client that trusts nothing but the
	* fingerprint from the QR code, and a bundle that has to arrive byte for byte. Those are the
	* parts that can be wrong in ways a mocked test would never notice, and they need a real device
	* because the keystore is hardware-backed and cannot be stood up on a JVM.
	*
	* What is deliberately NOT covered: LocalOnlyHotspot. Creating one needs a WiFi radio and a
	* second device to join it. This runs the server on the address the device already has.
	*/
@RunWith(AndroidJUnit4.class)
public class BundleTransferTest {

	private static final String LABEL = "Supervisor phone";
	private static final String ENVELOPE = "eyJ1c2VyIjoiY2h3In0=";
	private static final String SIGNATURE = "c2lnbmF0dXJl";

	private Context context;
	private SessionCertificate certificate;
	private LocalHttpServer server;
	private BundleSpool inbox;
	private File root;

	@Before public void setUp() throws Exception {
		context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		root = new File(context.getCacheDir(), "transfer-test-" + System.nanoTime());
		inbox = new BundleSpool(root);
		certificate = SessionCertificate.forDevice(LABEL);
		// Minting now rather than at construction, which is what a hosting session does: this is
		// also the step that proves the device's keystore can actually serve TLS.
		certificate.renew();
		server = new LocalHttpServer(LocalHttpServer.EPHEMERAL_PORT, LABEL, certificate, inbox);
		server.startServer();
	}

	@After public void tearDown() {
		// Guarded because a failure in setUp leaves these unset, and an NPE here would replace the
		// real reason in the report with a meaningless one.
		if (server != null) {
			server.stopServer();
		}
		if (certificate != null) {
			certificate.destroy();
		}
	}

	private Network network() {
		ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
		return manager.getActiveNetwork();
	}

	private PeerClient client(String fingerprint) {
		return new PeerClient(network(), fingerprint);
	}

	private File bundleOf(String contents) throws IOException {
		BundleSpool outbox = new BundleSpool(new File(root, "outbox"));
		String id = outbox.create();
		outbox.append(id, contents.getBytes(StandardCharsets.UTF_8));
		return outbox.bundle(id);
	}

	/** The address a peer would have been given in the QR code. */
	private String address() {
		return "127.0.0.1";
	}

	@Test public void aPeerReachesTheHostItScanned() throws Exception {
		String label = client(certificate.fingerprint())
				.fetchStatus(address(), server.getListeningPort());

		assertEquals(LABEL, label);
	}

	/**
		* The pin is the whole identity check: anyone in range can join the network, so a device
		* answering at the right address proves nothing on its own.
		*/
	@Test public void aHostPresentingAnotherCertificateIsRefused() {
		String someoneElse = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:" +
				"AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99";

		assertThrows(
				IOException.class,
				() -> client(someoneElse).fetchStatus(address(), server.getListeningPort()));
	}

	@Test public void aBundleArrivesByteForByte() throws Exception {
		String contents = "{\"_id\":\"report-1\"}\n{\"_id\":\"report-2\"}\n";

		client(certificate.fingerprint()).postBundle(
				address(), server.getListeningPort(), ENVELOPE, SIGNATURE, bundleOf(contents));

		JSONArray held = inbox.list();
		assertEquals(1, held.length());
		String id = held.getJSONObject(0).getString("id");
		assertEquals(ENVELOPE, held.getJSONObject(0).getString("envelope"));
		assertEquals(SIGNATURE, held.getJSONObject(0).getString("signature"));
		assertArrayEquals(
				contents.getBytes(StandardCharsets.UTF_8),
				inbox.read(id, 0, contents.length() * 2));
	}

	/** Several megabytes is the normal size, and it is where a bounded read goes wrong. */
	@Test public void aLargeBundleArrivesWhole() throws Exception {
		StringBuilder large = new StringBuilder();
		while (large.length() < 4 * 1024 * 1024) {
			large.append("{\"_id\":\"filler-").append(large.length()).append("\"}\n");
		}
		String contents = large.toString();

		client(certificate.fingerprint()).postBundle(
				address(), server.getListeningPort(), ENVELOPE, SIGNATURE, bundleOf(contents));

		String id = inbox.list().getJSONObject(0).getString("id");
		assertEquals(contents.length(), inbox.size(id));
	}

	/** The socket is reused, so a body read past its declared end would block on the next request. */
	@Test public void theSocketIsStillGoodAfterABundle() throws Exception {
		PeerClient peer = client(certificate.fingerprint());
		peer.postBundle(address(), server.getListeningPort(), ENVELOPE, SIGNATURE, bundleOf("first"));

		assertEquals(LABEL, peer.fetchStatus(address(), server.getListeningPort()));
		assertTrue(server.isAlive());
	}
}
