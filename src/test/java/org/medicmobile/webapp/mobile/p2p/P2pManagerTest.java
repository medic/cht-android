package org.medicmobile.webapp.mobile.p2p;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
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
import org.robolectric.annotation.Config;

import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class P2pManagerTest {

	private static final String SSID = "CHT-P2P-a3f7";
	private static final String PASSWORD = "a-password";
	private static final String IP = "192.168.49.1";

	private static final String FINGERPRINT = "AB:CD:EF:01:23:45";

	private WifiHotspotManager hotspotManager;
	private LocalHttpServer server;
	private SessionCertificate certificate;
	private P2pManager manager;
	private P2pManager.HostingCallback callback;

	@Before public void setUp() {
		hotspotManager = mock(WifiHotspotManager.class);
		server = mock(LocalHttpServer.class);
		when(server.getListeningPort()).thenReturn(8443);
		certificate = mock(SessionCertificate.class);
		try {
			when(certificate.fingerprint()).thenReturn(FINGERPRINT);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		manager = new P2pManager(hotspotManager, server, certificate);
		callback = mock(P2pManager.HostingCallback.class);
	}

	private void hotspotStarts() {
		doAnswer(invocation -> {
			HotspotProvider.HotspotCallback cb = invocation.getArgument(0);
			cb.onStarted(SSID, PASSWORD, IP);
			return null;
		}).when(hotspotManager).startHotspot(any());
	}

	@Test public void constructor_rejectsMissingCollaborators() {
		assertThrows(IllegalArgumentException.class, () -> new P2pManager(null, server, certificate));
		assertThrows(IllegalArgumentException.class, () -> new P2pManager(hotspotManager, null, certificate));
		assertThrows(IllegalArgumentException.class, () -> new P2pManager(hotspotManager, server, null));
	}

	/** The webapp displays this directly, so it must be an image and not the raw payload. */
	@Test @Config(sdk = 26)
	public void startHosting_bringsUpTheHotspotThenTheServerAndReturnsAScannableImage() throws Exception {
		hotspotStarts();

		manager.startHosting(callback);

		verify(server).startServer();
		ArgumentCaptor<String> qr = ArgumentCaptor.forClass(String.class);
		verify(callback).onReady(qr.capture());
		org.junit.Assert.assertTrue(qr.getValue(), qr.getValue().startsWith("data:image/png;base64,"));
	}

	/** Whatever is encoded must still carry what a peer needs, including the certificate to pin. */
	@Test @Config(sdk = 26)
	public void startHosting_encodesTheDetailsAPeerNeeds() throws Exception {
		JSONObject json = new JSONObject(
				QrCodeHelper.buildPayload(
						new QrCodeHelper.HotspotCredentials(SSID, PASSWORD, IP, 8443, FINGERPRINT)));

		org.junit.Assert.assertEquals(SSID, json.getString("ssid"));
		org.junit.Assert.assertEquals(IP, json.getString("ip"));
		org.junit.Assert.assertEquals(8443, json.getInt("port"));
		org.junit.Assert.assertEquals(FINGERPRINT, json.getString("fp"));
	}

	@Test @Config(sdk = 25)
	public void startHosting_refusesOnADeviceThatCannotHost() {
		manager.startHosting(callback);

		verify(callback).onFailed("hotspot_unsupported");
		verify(hotspotManager, never()).startHotspot(any());
	}

	@Test @Config(sdk = 26)
	public void startHosting_passesAHotspotFailureStraightBack() {
		doAnswer(invocation -> {
			HotspotProvider.HotspotCallback cb = invocation.getArgument(0);
			cb.onFailed("no_wifi");
			return null;
		}).when(hotspotManager).startHotspot(any());

		manager.startHosting(callback);

		verify(callback).onFailed("no_wifi");
		verify(callback, never()).onReady(anyString());
	}

	/** A hotspot with no server behind it is worse than no hotspot: it advertises a dead network. */
	@Test @Config(sdk = 26)
	public void startHosting_takesTheHotspotBackDownIfTheServerCannotBind() throws Exception {
		hotspotStarts();
		doThrow(new IOException("port in use")).when(server).startServer();

		manager.startHosting(callback);

		verify(hotspotManager).stopHotspot();
		verify(callback).onFailed("server_start_failed");
		verify(callback, never()).onReady(anyString());
	}

	@Test @Config(sdk = 26)
	public void stopHosting_stopsBothHalves() {
		manager.stopHosting();

		verify(server).stopServer();
		verify(hotspotManager).stopHotspot();
		// the session identity must not outlive the session
		verify(certificate).destroy();
	}

	@Test @Config(sdk = 26)
	public void isHosting_followsTheHotspot() {
		when(hotspotManager.isActive()).thenReturn(true);
		assertTrue(manager.isHosting());

		when(hotspotManager.isActive()).thenReturn(false);
		assertFalse(manager.isHosting());
	}

	@Test @Config(sdk = 25)
	public void isHostSupported_isFalseBelowApi26() {
		assertFalse(P2pManager.isHostSupported());
	}

	@Test @Config(sdk = 26)
	public void isHostSupported_isTrueFromApi26() {
		assertTrue(P2pManager.isHostSupported());
	}

	/**
		* stopHosting destroys the session key, so a second session has to mint a new one. Without
		* this the supervisor can share once, stop, and never share again until the app restarts.
		*/
	@Test public void startHosting_renewsTheCertificateForEachSession() throws Exception {
		manager.startHosting(callback);
		manager.stopHosting();
		manager.startHosting(callback);

		verify(certificate, org.mockito.Mockito.times(2)).renew();
		verify(certificate).destroy();
	}

	@Test public void startHosting_failsWhenNoCertificateCanBeMinted() throws Exception {
		doThrow(new java.security.GeneralSecurityException("no keystore")).when(certificate).renew();

		manager.startHosting(callback);

		verify(callback).onFailed("server_start_failed");
		verify(hotspotManager, never()).startHotspot(any());
	}
}
