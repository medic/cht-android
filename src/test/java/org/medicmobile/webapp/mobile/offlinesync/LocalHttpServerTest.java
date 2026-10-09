package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
	private BundleSpool inbox;

	@org.junit.Before public void setUp() {
		certificate = mock(SessionCertificate.class);
		inbox = mock(BundleSpool.class);
	}

	private LocalHttpServer server() {
		return new LocalHttpServer(LABEL, certificate, inbox);
	}

	private NanoHTTPD.IHTTPSession request(NanoHTTPD.Method method, String uri) {
		NanoHTTPD.IHTTPSession session = mock(NanoHTTPD.IHTTPSession.class);
		when(session.getMethod()).thenReturn(method);
		when(session.getUri()).thenReturn(uri);
		return session;
	}

	private NanoHTTPD.IHTTPSession bundleRequest(java.util.Map<String, String> headers, byte[] body) {
		NanoHTTPD.IHTTPSession session = request(NanoHTTPD.Method.POST, "/_offline-sync/bundle");
		when(session.getHeaders()).thenReturn(headers);
		when(session.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(body));
		return session;
	}

	private java.util.Map<String, String> bundleHeaders(long declaredLength) {
		java.util.Map<String, String> headers = new java.util.HashMap<>();
		headers.put("x-medic-bundle-envelope", "eyJ1c2VyIjoiY2h3In0=");
		headers.put("x-medic-bundle-signature", "c2lnbmF0dXJl");
		headers.put("content-length", String.valueOf(declaredLength));
		return headers;
	}

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
		assertThrows(IllegalArgumentException.class, () -> new LocalHttpServer("", certificate, inbox));
		assertThrows(IllegalArgumentException.class, () -> new LocalHttpServer("  ", certificate, inbox));
		assertThrows(IllegalArgumentException.class, () -> new LocalHttpServer(null, certificate, inbox));
		assertThrows(IllegalArgumentException.class, () -> new LocalHttpServer(LABEL, null, inbox));
		assertThrows(IllegalArgumentException.class, () -> new LocalHttpServer(LABEL, certificate, null));
	}

	@Test
	public void status_identifiesTheHostSoAPeerCanConfirmWhatItReached() throws Exception {
		LocalHttpServer server = server();

		NanoHTTPD.Response response = server.serve(request(NanoHTTPD.Method.GET, "/_offline-sync/status"));

		assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
		JSONObject body = new JSONObject(bodyOf(response));
		assertEquals("cht-offline-sync", body.getString("service"));
		assertEquals(LABEL, body.getString("device_label"));
		assertEquals(1, body.getInt("protocol_version"));
	}

	@Test
	public void unknownPath_is404() {
		LocalHttpServer server = server();

		NanoHTTPD.Response response = server.serve(request(NanoHTTPD.Method.GET, "/_offline-sync/anything-else"));

		assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, response.getStatus());
	}

	@Test
	public void dataEndpointsFromTheOldProtocolAreGone() {
		LocalHttpServer server = server();

		for (String path : new String[] { "/_offline-sync/auth", "/_offline-sync/get-ids", "/_offline-sync/bulk-get", "/_offline-sync/accept-docs" }) {
			NanoHTTPD.Response response = server.serve(request(NanoHTTPD.Method.POST, path));
			assertEquals(path, NanoHTTPD.Response.Status.NOT_FOUND, response.getStatus());
		}
	}

	@Test
	public void statusOnlyAnswersGet() {
		LocalHttpServer server = server();

		NanoHTTPD.Response response = server.serve(request(NanoHTTPD.Method.POST, "/_offline-sync/status"));

		assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, response.getStatus());
	}

	@Test
	public void stopServer_isSafeWhenNeverStarted() {
		LocalHttpServer server = server();

		server.stopServer();

		assertFalse(server.isAlive());
	}

	@Test
	public void defaultsToAnOsAssignedPort() {
		assertEquals(0, LocalHttpServer.EPHEMERAL_PORT);
	}

	@Test
	public void startServer_refusesToListenIfTheCertificateIsUnusable() throws Exception {
		when(certificate.sslServerSocketFactory())
				.thenThrow(new java.security.GeneralSecurityException("no key"));

		assertThrows(java.security.GeneralSecurityException.class, () -> server().startServer());
	}

	@Test
	public void startServer_asksTheSessionCertificateForItsSocketFactory() throws Exception {
		when(certificate.sslServerSocketFactory())
				.thenThrow(new java.security.GeneralSecurityException("stop before binding"));

		assertThrows(java.security.GeneralSecurityException.class, () -> server().startServer());
		verify(certificate).sslServerSocketFactory();
	}

	@Test
	public void serve_takesABundleAndTellsWhoeverIsListening() throws Exception {
		when(inbox.store(any(), any(), any())).thenReturn("bundle-1");
		when(inbox.size("bundle-1")).thenReturn(10L);
		LocalHttpServer server = server();
		LocalHttpServer.BundleListener listener = mock(LocalHttpServer.BundleListener.class);
		server.setBundleListener(listener);

		NanoHTTPD.Response response = server.serve(bundleRequest(bundleHeaders(10), "ciphertext".getBytes("UTF-8")));

		assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
		verify(inbox).store(eq("eyJ1c2VyIjoiY2h3In0="), eq("c2lnbmF0dXJl"), any());
		verify(listener).onBundleReceived("bundle-1");
	}

	@Test
	public void serve_takesABundleWhenNobodyIsListeningYet() throws Exception {
		when(inbox.store(any(), any(), any())).thenReturn("bundle-1");
		when(inbox.size("bundle-1")).thenReturn(10L);

		NanoHTTPD.Response response = server().serve(bundleRequest(bundleHeaders(10), "ciphertext".getBytes("UTF-8")));

		assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
	}

	@Test
	public void serve_refusesABundleWithNoEnvelope() throws Exception {
		java.util.Map<String, String> headers = bundleHeaders(10);
		headers.remove("x-medic-bundle-envelope");

		NanoHTTPD.Response response = server().serve(bundleRequest(headers, "ciphertext".getBytes("UTF-8")));

		assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.getStatus());
		verify(inbox, never()).store(any(), any(), any());
	}

	@Test
	public void serve_refusesABundleThatWouldFillThePhone() throws Exception {
		NanoHTTPD.Response response = server()
				.serve(bundleRequest(bundleHeaders(33L * 1024 * 1024), "ciphertext".getBytes("UTF-8")));

		assertEquals(NanoHTTPD.Response.Status.PAYLOAD_TOO_LARGE, response.getStatus());
		verify(inbox, never()).store(any(), any(), any());
	}

	@Test
	public void serve_refusesABundleThatDoesNotSayHowLargeItIs() throws Exception {
		java.util.Map<String, String> headers = bundleHeaders(10);
		headers.remove("content-length");

		NanoHTTPD.Response response = server().serve(bundleRequest(headers, "ciphertext".getBytes("UTF-8")));

		assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.getStatus());
		verify(inbox, never()).store(any(), any(), any());
	}

	@Test
	public void serve_refusesABundleWhoseSizeIsNotANumber() throws Exception {
		java.util.Map<String, String> headers = bundleHeaders(10);
		headers.put("content-length", "some");

		NanoHTTPD.Response response = server().serve(bundleRequest(headers, "ciphertext".getBytes("UTF-8")));

		assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.getStatus());
		verify(inbox, never()).store(any(), any(), any());
	}

	private LocalHttpServer serverWithRealSpool(java.io.File root) {
		inbox = new BundleSpool(root);
		return new LocalHttpServer(LABEL, certificate, inbox);
	}

	@Test
	public void serve_readsOnlyTheBodyAndLeavesTheRestOfTheSocketAlone() throws Exception {
		java.io.File root = new java.io.File(System.getProperty("java.io.tmpdir"), "inbox-" + System.nanoTime());
		LocalHttpServer server = serverWithRealSpool(root);
		byte[] wire = "ciphertextGET /_offline-sync/status".getBytes("UTF-8");

		NanoHTTPD.Response response = server.serve(bundleRequest(bundleHeaders(10), wire));

		assertEquals(NanoHTTPD.Response.Status.OK, response.getStatus());
		String id = inbox.list().getJSONObject(0).getString("id");
		assertEquals("ciphertext", new String(inbox.read(id, 0, 64), "UTF-8"));
	}

	@Test
	public void serve_refusesABundleThatStoppedEarlyRatherThanKeepingHalfOfIt() throws Exception {
		java.io.File root = new java.io.File(System.getProperty("java.io.tmpdir"), "inbox-" + System.nanoTime());
		LocalHttpServer server = serverWithRealSpool(root);

		NanoHTTPD.Response response = server.serve(bundleRequest(bundleHeaders(500), "short".getBytes("UTF-8")));

		assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.getStatus());
		assertEquals(0, inbox.list().length());
	}

	@Test
	public void serve_refusesAnythingElse() throws Exception {
		assertEquals(
				NanoHTTPD.Response.Status.NOT_FOUND,
				server().serve(request(NanoHTTPD.Method.POST, "/_offline-sync/status")).getStatus());
	}
}
