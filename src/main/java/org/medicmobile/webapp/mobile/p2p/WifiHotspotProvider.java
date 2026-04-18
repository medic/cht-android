package org.medicmobile.webapp.mobile.p2p;

import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Production HotspotProvider using WifiManager.startLocalOnlyHotspot() (API 26+).
 *
 * Creates an isolated local network with no internet routing — CHW devices
 * connect directly to the Supervisor's phone over WiFi.
 *
 * Notes:
 * - Requires android.permission.CHANGE_WIFI_STATE
 * - The OS assigns a random SSID and password (we cannot control them)
 * - The hotspot IP varies by OEM (detected at runtime from network interfaces)
 * - Only one LocalOnlyHotspot reservation can be active at a time
 */
public class WifiHotspotProvider implements HotspotProvider {

    private static final String TAG = "WifiHotspotProvider";

    /** Known hotspot interface names by OEM */
    private static final String[] KNOWN_HOTSPOT_INTERFACES = {
        "ap0", "swlan0", "wlan1", "softap0", "wifi_bridge0", "ap_br0"
    };

    private static final int IP_DETECT_MAX_RETRIES = 15;
    private static final long IP_DETECT_RETRY_DELAY_MS = 500; // 15 × 500ms = 7.5s max

    private final WifiManager wifiManager;
    private WifiManager.LocalOnlyHotspotReservation reservation;
    private volatile boolean running = false;

    public WifiHotspotProvider(WifiManager wifiManager) {
        if (wifiManager == null) {
            throw new IllegalArgumentException("wifiManager must not be null");
        }
        this.wifiManager = wifiManager;
    }

    @Override
    public void start(HotspotCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            callback.onFailed("LocalOnlyHotspot requires Android 8.0+ (API 26)");
            return;
        }

        if (running) {
            Log.w(TAG, "Hotspot already running");
            callback.onFailed("Hotspot already running");
            return;
        }

        try {
            wifiManager.startLocalOnlyHotspot(createHotspotCallback(callback),
                    new Handler(Looper.getMainLooper()));
        } catch (SecurityException e) {
            Log.e(TAG, "Hotspot SecurityException", e);
            callback.onFailed("security_exception: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start hotspot", e);
            callback.onFailed("Unexpected error: " + e.getMessage());
        }
    }

    private WifiManager.LocalOnlyHotspotCallback createHotspotCallback(HotspotCallback callback) {
        return new WifiManager.LocalOnlyHotspotCallback() {
            @Override
            public void onStarted(WifiManager.LocalOnlyHotspotReservation hotspotReservation) {
                reservation = hotspotReservation;
                running = true;

                String ssid = extractSsid(hotspotReservation);
                String password = extractPassword(hotspotReservation);
                String ip = detectHotspotIpWithRetry();

                if (ip == null) {
                    handleIpDetectionFailure(hotspotReservation, callback);
                    return;
                }

                Log.i(TAG, "LocalOnlyHotspot started: SSID=" + ssid + ", IP=" + ip);
                callback.onStarted(ssid, password, ip);
            }

            @Override
            public void onStopped() {
                Log.i(TAG, "LocalOnlyHotspot stopped by system");
                running = false;
                reservation = null;
            }

            @Override
            public void onFailed(int reason) {
                running = false;
                String reasonStr = mapFailureReason(reason);
                Log.e(TAG, "LocalOnlyHotspot failed: " + reasonStr);
                callback.onFailed(reasonStr);
            }
        };
    }

    private void handleIpDetectionFailure(WifiManager.LocalOnlyHotspotReservation hotspotReservation,
                                           HotspotCallback callback) {
        Log.e(TAG, "Hotspot started but could not detect IP — aborting");
        running = false;
        closeReservationQuietly(hotspotReservation);
        reservation = null;
        callback.onFailed("Could not detect hotspot IP address. Please restart P2P sync.");
    }

    private static void closeReservationQuietly(WifiManager.LocalOnlyHotspotReservation res) {
        try {
            res.close();
        } catch (Exception e) {
            Log.w(TAG, "Error closing hotspot reservation during cleanup", e);
        }
    }

    @Override
    public void stop() {
        if (reservation != null) {
            try {
                reservation.close();
                Log.i(TAG, "LocalOnlyHotspot reservation closed");
            } catch (Exception e) {
                Log.e(TAG, "Error closing hotspot reservation", e);
            }
            reservation = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    // --- Private helpers ---

    @SuppressWarnings("deprecation")
    private String extractSsid(WifiManager.LocalOnlyHotspotReservation hotspotReservation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SoftApConfiguration config = hotspotReservation.getSoftApConfiguration();
            return config.getSsid();
        }
        // API 26-29: use deprecated WifiConfiguration
        android.net.wifi.WifiConfiguration wifiConfig = hotspotReservation.getWifiConfiguration();
        return wifiConfig != null ? wifiConfig.SSID : "CHT-P2P-unknown";
    }

    @SuppressWarnings("deprecation")
    private String extractPassword(WifiManager.LocalOnlyHotspotReservation hotspotReservation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SoftApConfiguration config = hotspotReservation.getSoftApConfiguration();
            return config.getPassphrase();
        }
        // API 26-29: use deprecated WifiConfiguration
        android.net.wifi.WifiConfiguration wifiConfig = hotspotReservation.getWifiConfiguration();
        return wifiConfig != null ? wifiConfig.preSharedKey : "";
    }

    /**
     * Retry wrapper for detectHotspotIp(). On many devices the hotspot network
     * interface isn't fully configured when onStarted() fires — the OS needs a
     * few hundred milliseconds to assign the IP. We retry up to
     * IP_DETECT_MAX_RETRIES times with IP_DETECT_RETRY_DELAY_MS between attempts.
     */
    private static String detectHotspotIpWithRetry() {
        for (int attempt = 1; attempt <= IP_DETECT_MAX_RETRIES; attempt++) {
            String ip = detectHotspotIp();
            if (ip != null) {
                return ip;
            }
            if (attempt < IP_DETECT_MAX_RETRIES) {
                Log.d(TAG, "Hotspot IP not ready, retry " + attempt + "/" + IP_DETECT_MAX_RETRIES);
                sleepForRetry();
            }
        }
        return null;
    }

    private static void sleepForRetry() {
        try {
            Thread.sleep(IP_DETECT_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "IP detection retry interrupted");
        }
    }

    /**
     * Detect the actual hotspot IP by scanning network interfaces.
     * Phase 1: Check known hotspot interface names.
     * Phase 2: Enumerate all interfaces, skip loopback and wlan0 (regular WiFi).
     * Returns null if no hotspot IP found — caller MUST fail loudly.
     */
    private static String detectHotspotIp() {
        try {
            String ip = detectFromKnownInterfaces();
            if (ip != null) {
                return ip;
            }
            return detectFromAllInterfaces();
        } catch (Exception e) {
            Log.w(TAG, "Error detecting hotspot IP", e);
        }
        Log.e(TAG, "Could not detect hotspot IP from any network interface");
        return null;
    }

    /** Phase 1: Try known hotspot interface names (fast path). */
    private static String detectFromKnownInterfaces() throws Exception {
        for (String ifName : KNOWN_HOTSPOT_INTERFACES) {
            NetworkInterface nif = NetworkInterface.getByName(ifName);
            if (nif != null && nif.isUp()) {
                String ip = getIpv4Address(nif);
                if (ip != null) {
                    Log.i(TAG, "Detected hotspot IP: " + ip + " on " + ifName);
                    return ip;
                }
            }
        }
        return null;
    }

    /** Phase 2: Enumerate all interfaces, prefer non-wlan0. Falls back to wlan0. */
    private static String detectFromAllInterfaces() throws Exception {
        String wlan0Ip = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) {
            return null;
        }

        while (interfaces.hasMoreElements()) {
            NetworkInterface nif = interfaces.nextElement();
            if (!nif.isUp() || nif.isLoopback()) {
                // skip inactive/loopback interfaces
            } else {
                String ip = getIpFromInterface(nif);
                if (ip != null) {
                    return ip;
                }
                wlan0Ip = getWlan0Fallback(nif, wlan0Ip);
            }
        }

        if (wlan0Ip != null) {
            Log.i(TAG, "Using wlan0 fallback IP: " + wlan0Ip);
        }
        return wlan0Ip;
    }

    /** Get IP from a non-wlan0 interface, or null if wlan0 or no IP. */
    private static String getIpFromInterface(NetworkInterface nif) {
        String name = nif.getName();
        if ("wlan0".equals(name)) {
            return null;
        }
        String ip = getIpv4Address(nif);
        if (ip != null) {
            Log.i(TAG, "Detected hotspot IP: " + ip + " on " + name);
        }
        return ip;
    }

    /** Save wlan0 IP as fallback if this is the wlan0 interface. */
    private static String getWlan0Fallback(NetworkInterface nif, String currentFallback) {
        if (!"wlan0".equals(nif.getName())) {
            return currentFallback;
        }
        String ip = getIpv4Address(nif);
        if (ip != null) {
            Log.d(TAG, "Found wlan0 IP: " + ip + " (saving as fallback)");
            return ip;
        }
        return currentFallback;
    }

    private static String getIpv4Address(NetworkInterface nif) {
        Enumeration<InetAddress> addrs = nif.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress addr = addrs.nextElement();
            if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                return addr.getHostAddress();
            }
        }
        return null;
    }

    private String mapFailureReason(int reason) {
        switch (reason) {
            case WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL:
                return "No WiFi channel available";
            case WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC:
                return "Generic hotspot error";
            case WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE:
                return "Incompatible WiFi mode (tethering may be active)";
            case WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED:
                return "Tethering disallowed by device policy";
            default:
                return "Unknown hotspot error (code " + reason + ")";
        }
    }
}
