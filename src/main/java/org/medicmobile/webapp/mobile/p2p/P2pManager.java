package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.content.Context;
import android.location.LocationManager;
import android.net.wifi.WifiManager;

import org.json.JSONException;

import java.security.GeneralSecurityException;

/**
	* Owns a P2P pairing session on the host device.
	*
	* Brings up the hotspot, starts the local server behind it, and produces the payload the peer
	* scans. Nothing here knows what will later travel over the link.
	*
	* Hosting needs Android 8.0 for LocalOnlyHotspot while the app supports 5.0, so callers must
	* check {@link #isHostSupported()} first; the webapp uses it to decide whether to offer the
	* option at all.
	*/
public class P2pManager {


	private final WifiHotspotManager hotspotManager;
	private final LocalHttpServer server;
	private final SessionCertificate certificate;

	public P2pManager(WifiHotspotManager hotspotManager, LocalHttpServer server,
						SessionCertificate certificate) {
		if (hotspotManager == null || server == null || certificate == null) {
			throw new IllegalArgumentException("collaborators must not be null");
		}
		this.hotspotManager = hotspotManager;
		this.server = server;
		this.certificate = certificate;
	}

	/**
		* Builds a manager wired to the real WiFi radio, with a fresh TLS identity for the session.
		*/
	public static P2pManager create(Context context, String deviceLabel) throws GeneralSecurityException {
		Context appContext = context.getApplicationContext();
		WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
		LocationManager locationManager =
				(LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
		SessionCertificate certificate = SessionCertificate.generate(deviceLabel);
		return new P2pManager(
				new WifiHotspotManager(
						new WifiHotspotProvider(wifiManager, locationManager)),
				new LocalHttpServer(deviceLabel, certificate),
				certificate);
	}

	/** Whether this device can host at all. False on Android below 8.0. */
	public static boolean isHostSupported() {
		return WifiHotspotProvider.isSupported();
	}

	/**
		* Starts hosting: hotspot first, then the local server, then hands back the code a peer scans.
		*
		* Reports failure rather than partial success. If the server cannot bind, the hotspot is
		* taken back down so the device is not left advertising a network with nothing behind it.
		*/
	public void startHosting(HostingCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}
		if (!isHostSupported()) {
			callback.onFailed("hotspot_unsupported");
			return;
		}

		hotspotManager.startHotspot(new HotspotProvider.HotspotCallback() {
			@Override public void onStarted(String ssid, String password, String ipAddress) {
				if (startLocalServer(callback)) {
					publishPairingCode(ssid, password, ipAddress, callback);
				}
			}

			@Override public void onFailed(String reason) {
				callback.onFailed(reason);
			}
		});
	}

	/**
		* Brings the local server up. False means the caller has already been told why, and the
		* hotspot is back down: a network advertising nothing is worse than no network.
		*/
	private boolean startLocalServer(HostingCallback callback) {
		try {
			server.startServer();
			return true;
		} catch (Exception e) {
			warn(e, "Local server failed to start, taking the hotspot back down");
			hotspotManager.stopHotspot();
			callback.onFailed("server_start_failed");
			return false;
		}
	}

	/** Builds the code a peer scans, tearing the session down if it cannot be produced. */
	private void publishPairingCode(String ssid, String password, String ipAddress, HostingCallback callback) {
		try {
			String payload = QrCodeHelper.buildPayload(new QrCodeHelper.HotspotCredentials(
					ssid, password, ipAddress, server.getListeningPort(), certificate.fingerprint()));
			String qrImage = QrCodeHelper.generateQrDataUrl(payload);
			if (qrImage == null) {
				stopHosting();
				callback.onFailed("payload_failed");
				return;
			}
			log(P2pManager.class, "Hosting session ready on " + ipAddress);
			callback.onReady(qrImage);
		} catch (JSONException | GeneralSecurityException e) {
			warn(e, "Could not build the pairing payload");
			stopHosting();
			callback.onFailed("payload_failed");
		}
	}

	/** Tears the session down. Safe to call when nothing is running. */
	public void stopHosting() {
		server.stopServer();
		hotspotManager.stopHotspot();
		certificate.destroy();
		log(P2pManager.class, "Hosting session stopped");
	}

	public boolean isHosting() {
		return hotspotManager.isActive();
	}

	/** Result of trying to start hosting. */
	public interface HostingCallback {
		/** @param qrImage a PNG data URL of the code, ready for the webapp to display */
		void onReady(String qrImage);

		/** @param reason a stable code the webapp can map to a message */
		void onFailed(String reason);
	}
}
