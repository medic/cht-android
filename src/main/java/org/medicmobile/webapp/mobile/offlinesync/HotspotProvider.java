package org.medicmobile.webapp.mobile.offlinesync;

/**
	* Abstraction for WiFi hotspot management, so the session logic can be tested without a radio.
	*
	* The only implementation is {@link WifiHotspotProvider}, which brings up a real
	* LocalOnlyHotspot and therefore needs API 26. There is deliberately no stub implementation
	* that reports a hotspot it did not create: a device either has one or is told it has not.
	*/
public interface HotspotProvider {

	/**
		* Start the hotspot asynchronously.
		* On success, callback receives SSID, password, and IP address.
		* On failure, callback receives a reason string.
		*
		* @param callback result callback (never null)
		*/
	void start(HotspotCallback callback);

	/**
		* Stop the hotspot and release all resources.
		* Safe to call even if not running (no-op in that case).
		*/
	void stop();

	/**
		* Check if the hotspot is currently running.
		*
		* @return true if hotspot is active and accepting connections
		*/
	boolean isRunning();

	/**
		* Callback for hotspot start result.
		*/
	interface HotspotCallback {
		/**
			* Called when the hotspot has started successfully.
			*
			* @param ssid	  the network name clients should connect to
			* @param password  the WPA2 password for the network
			* @param ipAddress the IP address of this device on the hotspot network
			*/
		void onStarted(String ssid, String password, String ipAddress);

		/**
			* Called when the hotspot failed to start.
			*
			* @param reason human-readable failure reason
			*/
		void onFailed(String reason);
	}
}
