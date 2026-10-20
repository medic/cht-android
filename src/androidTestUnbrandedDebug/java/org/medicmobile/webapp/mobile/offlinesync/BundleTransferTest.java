package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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
		certificate.renew();
		server = new LocalHttpServer(LocalHttpServer.EPHEMERAL_PORT, LABEL, certificate, inbox);
		server.startServer();
	}

	@After public void tearDown() {
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

	private String address() {
		return "127.0.0.1";
	}

	private InetSocketAddress host() {
		return InetSocketAddress.createUnresolved(address(), server.getListeningPort());
	}

	@Test public void aPeerReachesTheHostItScanned() throws Exception {
		String label = client(certificate.fingerprint())
				.fetchStatus(address(), server.getListeningPort());

		assertEquals(LABEL, label);
	}

	@Test public void aSecondSessionServesItsOwnKey() throws Exception {
		String firstSession = certificate.fingerprint();
		server.stopServer();

		certificate.renew();
		String secondSession = certificate.fingerprint();
		server = new LocalHttpServer(LocalHttpServer.EPHEMERAL_PORT, LABEL, certificate, inbox);
		server.startServer();

		assertNotEquals("renew() must mint a new identity", firstSession, secondSession);
		assertEquals(LABEL, client(secondSession).fetchStatus(address(), server.getListeningPort()));
		assertThrows(
				IOException.class,
				() -> client(firstSession).fetchStatus(address(), server.getListeningPort()));
	}

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
				host(), ENVELOPE, SIGNATURE, bundleOf(contents));

		JSONArray held = inbox.list();
		assertEquals(1, held.length());
		String id = held.getJSONObject(0).getString("id");
		assertEquals(ENVELOPE, held.getJSONObject(0).getString("envelope"));
		assertEquals(SIGNATURE, held.getJSONObject(0).getString("signature"));
		assertArrayEquals(
				contents.getBytes(StandardCharsets.UTF_8),
				inbox.read(id, 0, contents.length() * 2));
	}

	@Test public void aLargeBundleArrivesWhole() throws Exception {
		StringBuilder large = new StringBuilder();
		while (large.length() < 4 * 1024 * 1024) {
			large.append("{\"_id\":\"filler-").append(large.length()).append("\"}\n");
		}
		String contents = large.toString();

		client(certificate.fingerprint()).postBundle(
				host(), ENVELOPE, SIGNATURE, bundleOf(contents));

		String id = inbox.list().getJSONObject(0).getString("id");
		assertEquals(contents.length(), inbox.size(id));
	}

	@Test public void theSocketIsStillGoodAfterABundle() throws Exception {
		PeerClient peer = client(certificate.fingerprint());
		peer.postBundle(host(), ENVELOPE, SIGNATURE, bundleOf("first"));

		assertEquals(LABEL, peer.fetchStatus(address(), server.getListeningPort()));
		assertTrue(server.isAlive());
	}
}
