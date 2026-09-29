package org.medicmobile.webapp.mobile.offlinesync;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.content.Context;
import android.location.LocationManager;
import android.net.wifi.WifiManager;

import org.json.JSONException;

import java.security.GeneralSecurityException;

/**
	* Owns an offline sync pairing session on the host device.
	*
	* Brings up the hotspot, starts the local server behind it, and produces the payload the peer
	* scans. Nothing here knows what will later travel over the link.
	*
	* Hosting needs Android 8.0 for LocalOnlyHotspot while the app supports 5.0, so callers must
	* check {@link #isHostSupported()} first; the webapp uses it to decide whether to offer the
	* option at all.
	*/
public class OfflineSyncManager {


	private final WifiHotspotManager hotspotManager;
	private final LocalHttpServer server;
	private final SessionCertificate certificate;

	public OfflineSyncManager(WifiHotspotManager hotspotManager, LocalHttpServer server,
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
	public static OfflineSyncManager create(Context context, String deviceLabel) throws GeneralSecurityException {
		Context appContext = context.getApplicationContext();
		WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
		LocationManager locationManager =
				(LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
		SessionCertificate certificate = SessionCertificate.forDevice(deviceLabel);
		return new OfflineSyncManager(
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
			callback.onFailed("hotspot_unsupported", "LocalOnlyHotspot needs Android 8.0");
			return;
		}

		// The platform allows one local-only hotspot reservation per app, and a session that failed
		// part way through still holds it. Without this, every attempt after a failure collides with
		// the wreckage of the last one until the platform times the reservation out on its own.
		// Idempotent, so starting from nothing costs nothing.
		server.stopServer();
		hotspotManager.stopHotspot();

		try {
			// A session's identity must not outlive it: stopHosting destroys the key, so without
			// this a second session would find nothing in the keystore to serve TLS with.
			certificate.renew();
		} catch (GeneralSecurityException e) {
			warn(e, "Could not prepare a certificate for this session");
			callback.onFailed("certificate_failed", describe(e));
			return;
		}

		hotspotManager.startHotspot(new HotspotProvider.HotspotCallback() {
			@Override public void onStarted(String ssid, String password, String ipAddress) {
				if (startLocalServer(callback)) {
					publishPairingCode(ssid, password, ipAddress, callback);
				}
			}

			@Override public void onFailed(String reason) {
				callback.onFailed(reason, "");
			}

			@Override public void onStopped() {
				// Nothing is listening on a network that no longer exists, and a peer part way
				// through handing something over has already lost it.
				server.stopServer();
				certificate.destroy();
				callback.onLost("hotspot_stopped");
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
		} catch (GeneralSecurityException e) {
			// The key exists by now, so this is the device refusing to serve TLS with it rather
			// than anything about the server. Reported apart from a bind failure, because the two
			// have nothing in common and only one of them is worth retrying.
			warn(e, "This device could not serve TLS, taking the hotspot back down");
			hotspotManager.stopHotspot();
			callback.onFailed("certificate_failed", describe(e));
			return false;
		} catch (Exception e) {
			warn(e, "Local server failed to start, taking the hotspot back down");
			hotspotManager.stopHotspot();
			callback.onFailed("server_start_failed", describe(e));
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
				callback.onFailed("payload_failed", "the QR image could not be rendered");
				return;
			}
			log(OfflineSyncManager.class, "Hosting session ready on " + ipAddress);
			callback.onReady(qrImage, ssid, password);
		} catch (JSONException | GeneralSecurityException | IllegalArgumentException e) {
			// IllegalArgumentException included deliberately: QrCodeHelper rejects empty credentials
			// that way, and letting it escape would leave the hotspot up with nothing reported.
			warn(e, "Could not build the pairing payload");
			stopHosting();
			callback.onFailed("payload_failed", describe(e));
		}
	}

	/** Tears the session down. Safe to call when nothing is running. */
	public void stopHosting() {
		server.stopServer();
		hotspotManager.stopHotspot();
		certificate.destroy();
		log(OfflineSyncManager.class, "Hosting session stopped");
	}

	/**
		* Whether a peer could reach this device right now.
		*
		* Both halves, because either can go without the other: the system can take the hotspot
		* away, and the local server's listener can die while the network is still up. Reporting
		* hosting when only one of them is left would leave the screen showing a code that leads
		* nowhere.
		*/
	public boolean isHosting() {
		return hotspotManager.isActive() && server.isAlive();
	}

	/**
		* An exception as one line, causes included.
		*
		* Hosting depends on what a particular device's keystore allows, and that varies by OEM, so
		* the reason has to travel off the device: a phone in the field is never on a cable, and the
		* keystore names the attribute it refused only in the cause chain.
		*/
	private static String describe(Throwable error) {
		StringBuilder description = new StringBuilder();
		for (Throwable cause = error; cause != null && description.length() < 400; cause = cause.getCause()) {
			if (description.length() > 0) {
				description.append(" <- ");
			}
			description.append(cause.getClass().getSimpleName());
			if (cause.getMessage() != null) {
				description.append(": ").append(cause.getMessage());
			}
		}
		return description.toString();
	}

	/** What happens to a hosting session, from trying to start it to losing it. */
	public interface HostingCallback {
		/**
			* @param qrImage a PNG data URL of the code, ready for the webapp to display
			* @param ssid the network the peer must join, shown so it can be joined by hand
			* @param password that network's password, for the same reason
			*/
		void onReady(String qrImage, String ssid, String password);

		/**
			* @param reason a stable code the webapp can map to a message
			* @param diagnostic what actually went wrong, for support. Never shown to the user: it
			*        cannot be translated. Empty when there is nothing to add beyond the code.
			*/
		void onFailed(String reason, String diagnostic);

		/**
			* A session that was running is gone, without anyone asking for it to stop.
			*
			* @param reason a stable code the webapp can map to a message
			*/
		void onLost(String reason);
	}
}
