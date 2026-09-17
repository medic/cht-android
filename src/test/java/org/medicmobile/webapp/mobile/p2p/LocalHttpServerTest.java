package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;

import fi.iki.elonen.NanoHTTPD;

@RunWith(RobolectricTestRunner.class)
public class LocalHttpServerTest {

	private static final String LABEL = "Supervisor phone";

	private SessionCertificate certificate;

	@org.junit.Before public void setUp() {
		certificate = mock(SessionCertificate.class);
	}

	private LocalHttpServer server() {
		return new LocalHttpServer(LABEL, certificate);
	}

	private NanoHTTPD.IHTTPSession request(NanoHTTPD.Method method, String uri) {
		NanoHTTPD.IHTTPSession session = mock(NanoHTTPD.IHTTPSession.class);
		when(session.getMethod()).thenReturn(method);
		when(session.getUri()).thenReturn(uri);
		return session;
	}

	/** Response.send is protected, so read the body straight off the response's data stream. */
	private String bodyOf(NanoHTTPD.Response response) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int read;
		while ((read = response.getData().read(buffer)) > 0) {
			out.write(buffer, 0, read);
		}
		return out.toString("UTF-8");
	}

	@Test
	public void constructor_rejectsAnEmptyLabel() {
		assertThrows(IllegalArgumentException.class, () -> new LocalHttpServer("", certificate));
		assertThrows(IllegalArgumentException.class, () -> new LocalHttpServer("  ", certificate));
		assertThrows(IllegalArgumentException.class, () -> new LocalHttpServer(null, certificate));
		assertThrows(IllegalArgumentException.class, () -> new LocalHttpServer(LABEL, null));
	}

	@Test
	public void status_identifiesTheHostSoAPeerCanConfirmWhatItReached() throws Exception {
		LocalHttpServer server = server();

		NanoHTTPD.Response response = server.serve(request(NanoHTTPD.Method.GET, "/_p2p/status"));

		assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
		JSONObject body = new JSONObject(bodyOf(response));
		assertEquals("cht-p2p", body.getString("service"));
		assertEquals(LABEL, body.getString("device_label"));
		assertEquals(1, body.getInt("protocol_version"));
	}

	@Test
	public void unknownPath_is404() throws Exception {
		LocalHttpServer server = server();

		NanoHTTPD.Response response = server.serve(request(NanoHTTPD.Method.GET, "/_p2p/anything-else"));

		assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, response.getStatus());
	}

	/** The data endpoints do not exist yet; a peer must not be able to reach one by guessing. */
	@Test
	public void dataEndpointsFromTheOldProtocolAreGone() throws Exception {
		LocalHttpServer server = server();

		for (String path : new String[] { "/_p2p/auth", "/_p2p/get-ids", "/_p2p/bulk-get", "/_p2p/accept-docs" }) {
			NanoHTTPD.Response response = server.serve(request(NanoHTTPD.Method.POST, path));
			assertEquals(path, NanoHTTPD.Response.Status.NOT_FOUND, response.getStatus());
		}
	}

	@Test
	public void statusOnlyAnswersGet() throws Exception {
		LocalHttpServer server = server();

		NanoHTTPD.Response response = server.serve(request(NanoHTTPD.Method.POST, "/_p2p/status"));

		assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, response.getStatus());
	}

	@Test
	public void stopServer_isSafeWhenNeverStarted() {
		server().stopServer();
	}

	/** Port 0 asks the OS for a free port, which is what removes the "port already in use" failure. */
	@Test
	public void defaultsToAnOsAssignedPort() {
		assertEquals(0, LocalHttpServer.EPHEMERAL_PORT);
	}

	/**
		* Proves the server will not listen without TLS. Actually completing a handshake needs a
		* keystore-backed certificate, so that is verified on a device rather than faked here.
		*/
	@Test
	public void startServer_refusesToListenIfTheCertificateIsUnusable() throws Exception {
		when(certificate.sslServerSocketFactory())
				.thenThrow(new java.security.GeneralSecurityException("no key"));

		assertThrows(java.io.IOException.class, () -> server().startServer());
	}

	@Test
	public void startServer_asksTheSessionCertificateForItsSocketFactory() throws Exception {
		when(certificate.sslServerSocketFactory())
				.thenThrow(new java.security.GeneralSecurityException("stop before binding"));

		try {
			server().startServer();
		} catch (java.io.IOException expected) {
			// we only care that TLS was set up before any socket was opened
		}
		verify(certificate).sslServerSocketFactory();
	}
}
