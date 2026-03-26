package org.medicmobile.webapp.mobile.p2p;

/**
 * Abstraction for WiFi hotspot management.
 *
 * Enables swapping between real WiFi (production) and localhost (dev/emulator).
 * Production: {@link WifiHotspotProvider} — real LocalOnlyHotspot (API 26+)
 * Development: {@link LoopbackHotspotProvider} — localhost HTTP server
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
         * @param ssid      the network name clients should connect to
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
