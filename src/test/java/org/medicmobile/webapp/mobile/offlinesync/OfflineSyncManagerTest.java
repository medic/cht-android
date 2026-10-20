package org.medicmobile.webapp.mobile.offlinesync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
	private BundleSpool inbox;

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
		inbox = mock(BundleSpool.class);
		manager = new OfflineSyncManager(hotspotManager, server, certificate, inbox);
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
		assertThrows(IllegalArgumentException.class, () -> new OfflineSyncManager(null, server, certificate, inbox));
		assertThrows(IllegalArgumentException.class, () -> new OfflineSyncManager(hotspotManager, null, certificate, inbox));
		assertThrows(IllegalArgumentException.class, () -> new OfflineSyncManager(hotspotManager, server, null, inbox));
		assertThrows(IllegalArgumentException.class, () -> new OfflineSyncManager(hotspotManager, server, certificate, null));
	}

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
		assertEquals(SSID, ssid.getValue());
		assertEquals(PASSWORD, password.getValue());
	}

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

	@Test @Config(sdk = 26)
	public void startHosting_takesTheHotspotBackDownIfTheServerCannotBind() throws Exception {
		hotspotStarts();
		doThrow(new IOException("port in use")).when(server).startServer();

		manager.startHosting(callback);

		verify(hotspotManager, atLeastOnce()).stopHotspot();
		verify(callback).onFailed(eq("server_start_failed"), anyString());
		verify(callback, never()).onReady(anyString(), anyString(), anyString());
	}

	@Test @Config(sdk = 26)
	public void startHosting_saysSoWhenTheDeviceCannotServeTls() throws Exception {
		hotspotStarts();
		doThrow(new GeneralSecurityException("keystore refused")).when(server).startServer();

		manager.startHosting(callback);

		verify(hotspotManager, atLeastOnce()).stopHotspot();
		verify(callback).onFailed(eq("certificate_failed"), anyString());
		verify(callback, never()).onReady(anyString(), anyString(), anyString());
	}

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

	@Test public void receivedBundles_handsTheWebappWhatItIsHolding() throws Exception {
		when(inbox.list()).thenReturn(new org.json.JSONArray().put(new org.json.JSONObject().put("id", "bundle-1")));

		assertEquals("[{\"id\":\"bundle-1\"}]", manager.receivedBundles());
	}

	@Test public void receivedBundles_isEmptyWhenTheListCannotBeBuilt() throws Exception {
		when(inbox.list()).thenThrow(new org.json.JSONException("broken"));

		assertEquals("[]", manager.receivedBundles());
	}

	@Test public void readBundle_encodesThePieceWithNoWrapping() throws Exception {
		when(inbox.read("bundle-1", 0, 3)).thenReturn("one".getBytes("UTF-8"));

		assertEquals("b25l", manager.readBundle("bundle-1", 0, 3));
	}

	@Test public void readBundle_isEmptyWhenThePieceCannotBeRead() throws Exception {
		when(inbox.read(any(), anyLong(), anyInt())).thenThrow(new java.io.IOException("gone"));

		assertEquals("", manager.readBundle("bundle-1", 0, 3));
	}

	@Test public void deleteBundle_dropsWhatTheWebappHasTaken() {
		when(inbox.delete("bundle-1")).thenReturn(true);

		assertTrue(manager.deleteBundle("bundle-1"));
	}

	@Test public void deleteBundle_isFalseForSomethingThatIsNotABundleId() {
		when(inbox.delete(any())).thenThrow(new IllegalArgumentException("not a bundle id"));

		assertFalse(manager.deleteBundle("../../secrets"));
	}

	@Test public void setBundleListener_passesItToTheServer() {
		LocalHttpServer.BundleListener listener = mock(LocalHttpServer.BundleListener.class);

		manager.setBundleListener(listener);

		verify(server).setBundleListener(listener);
	}

	private HotspotProvider.HotspotCallback hostingStartedThenCaptureCallback() {
		ArgumentCaptor<HotspotProvider.HotspotCallback> captor =
				ArgumentCaptor.forClass(HotspotProvider.HotspotCallback.class);
		manager.startHosting(callback);
		verify(hotspotManager).startHotspot(captor.capture());
		return captor.getValue();
	}

	@Test public void aHotspotThatGoesAwayTakesTheSessionWithIt() {
		HotspotProvider.HotspotCallback hotspot = hostingStartedThenCaptureCallback();
		hotspot.onStarted(SSID, PASSWORD, IP);

		hotspot.onStopped();

		verify(server, atLeastOnce()).stopServer();
		verify(certificate).destroy();
		verify(callback).onLost("hotspot_stopped");
	}

	@Test public void aRecreatedScreenHearsAboutTheSessionItDidNotStart() {
		HotspotProvider.HotspotCallback hotspot = hostingStartedThenCaptureCallback();
		OfflineSyncManager.HostingCallback recreated = mock(OfflineSyncManager.HostingCallback.class);

		manager.redirect(recreated);
		hotspot.onStarted(SSID, PASSWORD, IP);
		hotspot.onStopped();

		verify(recreated).onReady(anyString(), eq(SSID), eq(PASSWORD));
		verify(recreated).onLost("hotspot_stopped");
		verify(callback, never()).onReady(anyString(), anyString(), anyString());
		verify(callback, never()).onLost(anyString());
	}

	@Test public void aLostSessionIsReportedAsACode() {
		HotspotProvider.HotspotCallback hotspot = hostingStartedThenCaptureCallback();
		hotspot.onStarted(SSID, PASSWORD, IP);
		ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);

		hotspot.onStopped();

		verify(callback).onLost(reason.capture());
		assertTrue(reason.getValue().matches("[a-z0-9_]+"));
	}

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

	@Test @Config(sdk = 26)
	public void currentSession_isWhatOnReadyHandedOut() {
		hotspotStarts();
		when(hotspotManager.getActiveSsid()).thenReturn(SSID);
		when(hotspotManager.getActivePassword()).thenReturn(PASSWORD);
		when(hotspotManager.isActive()).thenReturn(true);
		when(server.isAlive()).thenReturn(true);
		ArgumentCaptor<String> qr = ArgumentCaptor.forClass(String.class);

		manager.startHosting(callback);

		verify(callback).onReady(qr.capture(), eq(SSID), eq(PASSWORD));
		OfflineSyncManager.Session session = manager.currentSession();
		assertEquals(qr.getValue(), session.qrImage);
		assertEquals(SSID, session.ssid);
		assertEquals(PASSWORD, session.password);
	}

	@Test @Config(sdk = 26)
	public void currentSession_isNullOnceStopped() {
		hotspotStarts();
		when(hotspotManager.isActive()).thenReturn(true);
		when(server.isAlive()).thenReturn(true);
		manager.startHosting(callback);
		assertNotNull(manager.currentSession());

		manager.stopHosting();

		assertNull(manager.currentSession());
	}

	@Test public void currentSession_isNullOnceLost() {
		HotspotProvider.HotspotCallback hotspot = hostingStartedThenCaptureCallback();
		hotspot.onStarted(SSID, PASSWORD, IP);
		when(hotspotManager.isActive()).thenReturn(true);
		when(server.isAlive()).thenReturn(true);
		assertNotNull(manager.currentSession());

		hotspot.onStopped();

		assertNull(manager.currentSession());
	}

	@Test @Config(sdk = 26)
	public void currentSession_isNullOnceANewSessionIsStarting() {
		HotspotProvider.HotspotCallback first = hostingStartedThenCaptureCallback();
		first.onStarted(SSID, PASSWORD, IP);
		when(hotspotManager.isActive()).thenReturn(true);
		when(server.isAlive()).thenReturn(true);
		assertNotNull(manager.currentSession());

		manager.startHosting(callback);

		assertNull(manager.currentSession());
	}

	@Test @Config(sdk = 26)
	public void currentSession_isNullWhenNothingIsListening() {
		hotspotStarts();
		manager.startHosting(callback);
		when(hotspotManager.isActive()).thenReturn(true);
		when(server.isAlive()).thenReturn(false);

		assertNull(manager.currentSession());
	}
}
