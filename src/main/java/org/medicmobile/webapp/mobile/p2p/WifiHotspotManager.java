package org.medicmobile.webapp.mobile.p2p;

import android.util.Log;

/**
 * Manages the WiFi hotspot lifecycle for P2P sync.
 *
 * Wraps a {@link HotspotProvider} with:
 * - Idle timeout detection (using P2pConfig.getWifiHotspotIdleTimeoutSec())
 * - Start/stop timing for telemetry
 * - State tracking to prevent double-start
 *
 * Guard: G12 — Session timeout after idle period (delegated to P2pSession,
 * but this class tracks hotspot-level idle for auto-shutdown).
 */
public class WifiHotspotManager {

    private static final String TAG = "WifiHotspotManager";

    private final HotspotProvider provider;
    private final P2pConfig config;

    private long startedAt;
    private volatile long lastActivityAt;
    private String activeSsid;
    private String activePassword;
    private String activeIpAddress;

    public WifiHotspotManager(HotspotProvider provider, P2pConfig config) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.provider = provider;
        this.config = config;
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
            Log.w(TAG, "Hotspot already active, returning existing credentials");
            callback.onStarted(activeSsid, activePassword, activeIpAddress);
            return;
        }

        provider.start(new HotspotProvider.HotspotCallback() {
            @Override
            public void onStarted(String ssid, String password, String ipAddress) {
                long now = System.currentTimeMillis();
                startedAt = now;
                lastActivityAt = now;
                activeSsid = ssid;
                activePassword = password;
                activeIpAddress = ipAddress;

                Log.i(TAG, "Hotspot started: SSID=" + ssid + ", IP=" + ipAddress);
                callback.onStarted(ssid, password, ipAddress);
            }

            @Override
            public void onFailed(String reason) {
                Log.e(TAG, "Hotspot failed to start: " + reason);
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
            Log.i(TAG, "Hotspot stopped after " + (duration / 1000) + "s");
        }
        activeSsid = null;
        activePassword = null;
        activeIpAddress = null;
        startedAt = 0;
        lastActivityAt = 0;
    }

    /**
     * Check if the hotspot is currently active.
     */
    public boolean isActive() {
        return provider.isRunning();
    }

    /**
     * Record activity to reset the idle timeout clock.
     * Call this whenever a sync operation occurs (doc transfer, auth, etc.).
     */
    public void recordActivity() {
        lastActivityAt = System.currentTimeMillis();
    }

    /**
     * Check if the hotspot has exceeded the idle timeout from config.
     * Used to auto-shutdown the hotspot when no sync activity occurs.
     *
     * @return true if idle time exceeds wifi_hotspot_idle_timeout_sec
     */
    public boolean isIdleTimedOut() {
        if (!provider.isRunning() || lastActivityAt == 0) {
            return false;
        }
        long idleMs = System.currentTimeMillis() - lastActivityAt;
        long timeoutMs = (long) config.getWifiHotspotIdleTimeoutSec() * 1000;
        return idleMs > timeoutMs;
    }

    /**
     * Get how long the hotspot has been running in milliseconds.
     *
     * @return uptime in ms, or 0 if not running
     */
    public long getUptimeMs() {
        if (!provider.isRunning() || startedAt == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startedAt;
    }

    /**
     * Get how long the hotspot has been idle in milliseconds.
     *
     * @return idle time in ms, or 0 if not running
     */
    public long getIdleMs() {
        if (!provider.isRunning() || lastActivityAt == 0) {
            return 0;
        }
        return System.currentTimeMillis() - lastActivityAt;
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

    public long getStartedAt() {
        return startedAt;
    }
}
