package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

/**
	* Manages the WiFi hotspot lifecycle for P2P sync.
	*
	* Wraps a {@link HotspotProvider} with:
	* - Idle timeout detection, so an unused hotspot shuts itself down
	* - Start/stop timing
	* - State tracking to prevent double-start
	*
	* The idle timeout is passed in rather than read from a config object: this class needs one
	* number, and the caller owns where it comes from.
	*/
public class WifiHotspotManager {

	private final HotspotProvider provider;

	private long startedAt;
	private String activeSsid;
	private String activePassword;
	private String activeIpAddress;

	public WifiHotspotManager(HotspotProvider provider) {
		if (provider == null) {
			throw new IllegalArgumentException("provider must not be null");
		}
		this.provider = provider;
	}

	/**
		* Start the hotspot. Wraps the provider callback with lifecycle tracking.
		*
		* @param callback receives hotspot credentials on success, or failure reason
		*/
	public void startHotspot(HotspotProvider.HotspotCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}

		if (provider.isRunning()) {
			warn(this, "Hotspot already active, returning existing credentials");
			callback.onStarted(activeSsid, activePassword, activeIpAddress);
			return;
		}

		provider.start(new HotspotProvider.HotspotCallback() {
			@Override
			public void onStarted(String ssid, String password, String ipAddress) {
				startedAt = System.currentTimeMillis();
				activeSsid = ssid;
				activePassword = password;
				activeIpAddress = ipAddress;

				log(this, "Hotspot started: SSID=" + ssid + ", IP=" + ipAddress);
				callback.onStarted(ssid, password, ipAddress);
			}

			@Override
			public void onFailed(String reason) {
				warn(this, "Hotspot failed to start: " + reason);
				callback.onFailed(reason);
			}
		});
	}

	/**
		* Stop the hotspot and clear all state.
		*/
	public void stopHotspot() {
		if (provider.isRunning()) {
			provider.stop();
			long duration = System.currentTimeMillis() - startedAt;
			log(this, "Hotspot stopped after " + (duration / 1000) + "s");
		}
		activeSsid = null;
		activePassword = null;
		activeIpAddress = null;
		startedAt = 0;
	}

	/**
		* Check if the hotspot is currently active.
		*/
	public boolean isActive() {
		return provider.isRunning();
	}

	// --- Getters for active credentials ---

	public String getActiveSsid() {
		return activeSsid;
	}

	public String getActivePassword() {
		return activePassword;
	}

	public String getActiveIpAddress() {
		return activeIpAddress;
	}

}
