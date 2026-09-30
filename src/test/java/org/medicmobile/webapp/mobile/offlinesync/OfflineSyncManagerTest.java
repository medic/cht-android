package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.security.GeneralSecurityException;

@RunWith(RobolectricTestRunner.class)
public class OfflineSyncManagerTest {

	private static final String SSID = "AndroidShare_1234";
	private static final String PASSWORD = "a-password";
	private static final String IP = "192.168.49.1";

	private static final String FINGERPRINT = "AB:CD:EF:01:23:45";

	private WifiHotspotManager hotspotManager;
	private LocalHttpServer server;
	private SessionCertificate certificate;
	private OfflineSyncManager manager;
	private OfflineSyncManager.HostingCallback callback;

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
		manager = new OfflineSyncManager(hotspotManager, server, certificate);
		callback = mock(OfflineSyncManager.HostingCallback.class);
	}

	private void hotspotStarts() {
		doAnswer(invocation -> {
			HotspotProvider.HotspotCallback cb = invocation.getArgument(0);
			cb.onStarted(SSID, PASSWORD, IP);
			return null;
		}).when(hotspotManager).startHotspot(any());
	}

	@Test public void constructor_rejectsMissingCollaborators() {
		assertThrows(IllegalArgumentException.class, () -> new OfflineSyncManager(null, server, certificate));
		assertThrows(IllegalArgumentException.class, () -> new OfflineSyncManager(hotspotManager, null, certificate));
		assertThrows(IllegalArgumentException.class, () -> new OfflineSyncManager(hotspotManager, server, null));
	}

	/** The webapp displays this directly, so it must be an image and not the raw payload. */
	@Test @Config(sdk = 26)
	public void startHosting_bringsUpTheHotspotThenTheServerAndReturnsAScannableImage() throws Exception {
		hotspotStarts();

		manager.startHosting(callback);

		verify(server).startServer();
		ArgumentCaptor<String> qr = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> ssid = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
		verify(callback).onReady(qr.capture(), ssid.capture(), password.capture());
		org.junit.Assert.assertTrue(qr.getValue(), qr.getValue().startsWith("data:image/png;base64,"));
		// the same credentials go up as text, so a peer that cannot scan can still join by hand
		assertEquals(SSID, ssid.getValue());
		assertEquals(PASSWORD, password.getValue());
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

		verify(callback).onFailed(eq("hotspot_unsupported"), anyString());
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

		verify(callback).onFailed(eq("no_wifi"), anyString());
		verify(callback, never()).onReady(anyString(), anyString(), anyString());
	}

	/** A hotspot with no server behind it is worse than no hotspot: it advertises a dead network. */
	@Test @Config(sdk = 26)
	public void startHosting_takesTheHotspotBackDownIfTheServerCannotBind() throws Exception {
		hotspotStarts();
		doThrow(new IOException("port in use")).when(server).startServer();

		manager.startHosting(callback);

		verify(hotspotManager, atLeastOnce()).stopHotspot();
		verify(callback).onFailed(eq("server_start_failed"), anyString());
		verify(callback, never()).onReady(anyString(), anyString(), anyString());
	}

	/**
		* A device whose keystore will not serve TLS is a different problem from a port that will not
		* bind: one is worth retrying and the other never will be, so they must not share a code.
		*/
	@Test @Config(sdk = 26)
	public void startHosting_saysSoWhenTheDeviceCannotServeTls() throws Exception {
		hotspotStarts();
		doThrow(new GeneralSecurityException("keystore refused")).when(server).startServer();

		manager.startHosting(callback);

		verify(hotspotManager, atLeastOnce()).stopHotspot();
		verify(callback).onFailed(eq("certificate_failed"), anyString());
		verify(callback, never()).onReady(anyString(), anyString(), anyString());
	}

	/**
		* The phone that fails is never the phone on a cable, so the reason has to leave the device.
		* Keystore failures name the refused attribute several causes down, hence the whole chain.
		*/
	@Test @Config(sdk = 26)
	public void startHosting_sendsTheReasonOnWithTheCode() throws Exception {
		doThrow(new GeneralSecurityException("cannot mint",
						new IllegalStateException("INCOMPATIBLE_PADDING_MODE")))
				.when(certificate).renew();

		manager.startHosting(callback);

		ArgumentCaptor<String> diagnostic = ArgumentCaptor.forClass(String.class);
		verify(callback).onFailed(eq("certificate_failed"), diagnostic.capture());
		assertTrue(diagnostic.getValue().contains("cannot mint"));
		assertTrue(diagnostic.getValue().contains("INCOMPATIBLE_PADDING_MODE"));
	}

	/**
		* The platform allows one local-only hotspot reservation per app, so a session that failed
		* part way through would otherwise hold it and every retry would collide until the platform
		* released it on its own.
		*/
	@Test @Config(sdk = 26)
	public void startHosting_releasesAnyPreviousSessionFirst() {
		hotspotStarts();

		manager.startHosting(callback);

		InOrder order = inOrder(server, hotspotManager);
		order.verify(server).stopServer();
		order.verify(hotspotManager).stopHotspot();
		order.verify(hotspotManager).startHotspot(any());
	}

	@Test @Config(sdk = 26)
	public void stopHosting_stopsBothHalves() {
		manager.stopHosting();

		verify(server).stopServer();
		verify(hotspotManager).stopHotspot();
		// the session identity must not outlive the session
		verify(certificate).destroy();
	}

	@Test @Config(sdk = 25)
	public void isHostSupported_isFalseBelowApi26() {
		assertFalse(OfflineSyncManager.isHostSupported());
	}

	@Test @Config(sdk = 26)
	public void isHostSupported_isTrueFromApi26() {
		assertTrue(OfflineSyncManager.isHostSupported());
	}

	/**
		* stopHosting destroys the session key, so a second session has to mint a new one. Without
		* this the supervisor can share once, stop, and never share again until the app restarts.
		*/
	@Test public void startHosting_renewsTheCertificateForEachSession() throws Exception {
		manager.startHosting(callback);
		manager.stopHosting();
		manager.startHosting(callback);

		verify(certificate, times(2)).renew();
		verify(certificate).destroy();
	}

	@Test public void startHosting_failsWhenNoCertificateCanBeMinted() throws Exception {
		doThrow(new GeneralSecurityException("no keystore")).when(certificate).renew();

		manager.startHosting(callback);

		verify(callback).onFailed(eq("certificate_failed"), anyString());
		verify(hotspotManager, never()).startHotspot(any());
	}








	private HotspotProvider.HotspotCallback hostingStartedThenCaptureCallback() {
		ArgumentCaptor<HotspotProvider.HotspotCallback> captor =
				ArgumentCaptor.forClass(HotspotProvider.HotspotCallback.class);
		manager.startHosting(callback);
		verify(hotspotManager).startHotspot(captor.capture());
		return captor.getValue();
	}

	/**
		* The system can take the hotspot away at any point. Leaving the server listening on a
		* network that is gone would leave the screen showing a code nothing is behind.
		*/
	@Test public void aHotspotThatGoesAwayTakesTheSessionWithIt() {
		HotspotProvider.HotspotCallback hotspot = hostingStartedThenCaptureCallback();
		hotspot.onStarted(SSID, PASSWORD, IP);

		hotspot.onStopped();

		verify(server, atLeastOnce()).stopServer();
		verify(certificate).destroy();
		verify(callback).onLost("hotspot_stopped");
	}

	/** A code, not a sentence: the webapp turns this straight into a translation key. */
	@Test public void aLostSessionIsReportedAsACode() {
		HotspotProvider.HotspotCallback hotspot = hostingStartedThenCaptureCallback();
		hotspot.onStarted(SSID, PASSWORD, IP);
		ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);

		hotspot.onStopped();

		verify(callback).onLost(reason.capture());
		assertTrue(reason.getValue().matches("[a-z0-9_]+"));
	}

	/**
		* Either half can go without the other, and a code that leads nowhere is worse than no code:
		* the user would keep showing it and wonder why nobody can connect.
		*/
	@Test public void hostingNeedsBothTheNetworkAndSomethingListeningOnIt() {
		when(hotspotManager.isActive()).thenReturn(true);
		when(server.isAlive()).thenReturn(true);
		assertTrue(manager.isHosting());

		when(server.isAlive()).thenReturn(false);
		assertFalse(manager.isHosting());

		when(hotspotManager.isActive()).thenReturn(false);
		when(server.isAlive()).thenReturn(true);
		assertFalse(manager.isHosting());
	}
}
