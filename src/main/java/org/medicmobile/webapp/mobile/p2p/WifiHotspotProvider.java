package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.error;
import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.trace;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.net.wifi.SoftApConfiguration;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

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


	/** Known hotspot interface names by OEM */
	private static final String[] KNOWN_HOTSPOT_INTERFACES = {
		"ap0", "swlan0", "wlan1", "softap0", "wifi_bridge0", "ap_br0"
	};

	private static final int IP_DETECT_MAX_RETRIES = 15;
	private static final long IP_DETECT_RETRY_DELAY_MS = 500; // 15 × 500ms = 7.5s max

	private final WifiManager wifiManager;
	private final LocationManager locationManager;
	private WifiManager.LocalOnlyHotspotReservation reservation;
	private volatile boolean running = false;

	public WifiHotspotProvider(WifiManager wifiManager, LocationManager locationManager) {
		if (wifiManager == null) {
			throw new IllegalArgumentException("wifiManager must not be null");
		}
		this.wifiManager = wifiManager;
		this.locationManager = locationManager;
	}

	/**
		* Whether this device can host a local-only hotspot.
		*
		* LocalOnlyHotspot arrived in API 26 and the app supports 21, so hosting is genuinely
		* unavailable on older devices. Peers are unaffected: joining a network needs no such API.
		*/
	public static boolean isSupported() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
	}

	@Override
	public void start(HotspotCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}

		if (!isSupported()) {
			warn(WifiHotspotProvider.class,
					"Local-only hotspot needs Android 8.0, this device is on API " + Build.VERSION.SDK_INT);
			callback.onFailed("hotspot_unsupported");
			return;
		}

		// Granting the permission is not enough: the platform also refuses to start a hotspot while
		// location is switched off, and says so in a way that looks like any other failure.
		if (!isLocationEnabled()) {
			warn(WifiHotspotProvider.class, "Location services are off, cannot start a hotspot");
			callback.onFailed("location_services_off");
			return;
		}

		if (running) {
			warn(this, "Hotspot already running");
			callback.onFailed("hotspot_already_running");
			return;
		}

		try {
			startLocalOnlyHotspot(callback);
		} catch (SecurityException e) {
			error(e, "Hotspot SecurityException");
			callback.onFailed("permissions_required");
		} catch (Exception e) {
			error(e, "Failed to start hotspot");
			callback.onFailed("hotspot_error");
		}
	}

	/**
		* The only place the API-26 call is made. Reached solely through {@link #start}, which
		* returns early via {@link #isSupported()} on older devices, so the annotation records a
		* precondition the caller has already enforced rather than assuming it for the whole class.
		*/
	@android.annotation.TargetApi(26)
	private void startLocalOnlyHotspot(HotspotCallback callback) {
		wifiManager.startLocalOnlyHotspot(createHotspotCallback(callback),
				new Handler(Looper.getMainLooper()));
	}

	@android.annotation.TargetApi(26)
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

				log(this, "LocalOnlyHotspot started: SSID=" + ssid + ", IP=" + ip);
				callback.onStarted(ssid, password, ip);
			}

			@Override
			public void onStopped() {
				log(this, "LocalOnlyHotspot stopped by system");
				running = false;
				reservation = null;
			}

			@Override
			public void onFailed(int reason) {
				running = false;
				String reasonStr = mapFailureReason(reason);
				warn(this, "LocalOnlyHotspot failed: " + reasonStr);
				callback.onFailed(reasonStr);
			}
		};
	}

	private void handleIpDetectionFailure(WifiManager.LocalOnlyHotspotReservation hotspotReservation,
											HotspotCallback callback) {
		warn(this, "Hotspot started but could not detect IP — aborting");
		running = false;
		closeReservationQuietly(hotspotReservation);
		reservation = null;
		callback.onFailed("hotspot_no_address");
	}

	/**
		* Whether the device's location setting is on, which the platform requires for a hotspot.
		*
		* Separate from the permission: a user can grant location access and still have the setting
		* switched off, and the hotspot then fails in a way that looks like any other error.
		*/
	private boolean isLocationEnabled() {
		if (locationManager == null) {
			return false;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			return locationManager.isLocationEnabled();
		}
		return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
				|| locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
	}

	@android.annotation.TargetApi(26)
	private static void closeReservationQuietly(WifiManager.LocalOnlyHotspotReservation res) {
		try {
			res.close();
		} catch (Exception e) {
			warn(e, "Error closing hotspot reservation during cleanup");
		}
	}

	@Override
	public void stop() {
		if (!isSupported()) {
			return;
		}
		if (reservation != null) {
			try {
				reservation.close();
				log(this, "LocalOnlyHotspot reservation closed");
			} catch (Exception e) {
				error(e, "Error closing hotspot reservation");
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
	@android.annotation.TargetApi(26)
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
	@android.annotation.TargetApi(26)
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
				trace(WifiHotspotProvider.class, "Hotspot IP not ready, retry " + attempt + "/" + IP_DETECT_MAX_RETRIES);
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
			warn(WifiHotspotProvider.class, "IP detection retry interrupted");
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
			warn(e, "Error detecting hotspot IP");
		}
		warn(WifiHotspotProvider.class, "Could not detect hotspot IP from any network interface");
		return null;
	}

	/** Phase 1: Try known hotspot interface names (fast path). */
	private static String detectFromKnownInterfaces() throws java.net.SocketException {
		for (String ifName : KNOWN_HOTSPOT_INTERFACES) {
			String ip = tryInterface(ifName);
			if (ip != null) {
				return ip;
			}
		}
		return null;
	}

	private static String tryInterface(String ifName) throws java.net.SocketException {
		NetworkInterface nif = NetworkInterface.getByName(ifName);
		if (nif == null || !nif.isUp()) {
			return null;
		}
		String ip = getIpv4Address(nif);
		if (ip != null) {
			log(WifiHotspotProvider.class, "Detected hotspot IP: " + ip + " on " + ifName);
		}
		return ip;
	}

	/** Phase 2: Enumerate all interfaces, prefer non-wlan0. Falls back to wlan0. */
	private static String detectFromAllInterfaces() throws java.net.SocketException {
		Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
		if (interfaces == null) {
			return null;
		}

		String wlan0Ip = null;
		while (interfaces.hasMoreElements()) {
			NetworkInterface nif = interfaces.nextElement();
			String result = processInterface(nif);
			if (result != null) {
				return result;
			}
			wlan0Ip = getWlan0Fallback(nif, wlan0Ip);
		}

		if (wlan0Ip != null) {
			log(WifiHotspotProvider.class, "Using wlan0 fallback IP: " + wlan0Ip);
		}
		return wlan0Ip;
	}

	private static String processInterface(NetworkInterface nif) throws java.net.SocketException {
		if (!nif.isUp() || nif.isLoopback()) {
			return null;
		}
		return getIpFromInterface(nif);
	}

	/** Get IP from a non-wlan0 interface, or null if wlan0 or no IP. */
	private static String getIpFromInterface(NetworkInterface nif) {
		String name = nif.getName();
		if ("wlan0".equals(name)) {
			return null;
		}
		String ip = getIpv4Address(nif);
		if (ip != null) {
			log(WifiHotspotProvider.class, "Detected hotspot IP: " + ip + " on " + name);
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
			trace(WifiHotspotProvider.class, "Found wlan0 IP: " + ip + " (saving as fallback)");
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

	/**
		* Turns a platform failure into a stable code for the webapp, and logs the detail.
		*
		* The code is what crosses to the webapp, which maps it to a translated message; the prose
		* and the raw value stay in the log, where they are what lets support tell an OEM that
		* forbids hotspots from a device that is merely busy.
		*/
	private String mapFailureReason(int reason) {
		switch (reason) {
			case WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL:
				warn(WifiHotspotProvider.class, "No wifi channel available (code " + reason + ")");
				return "hotspot_no_channel";
			case WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE:
				warn(WifiHotspotProvider.class,
						"Incompatible wifi mode, tethering may be active (code " + reason + ")");
				return "hotspot_incompatible_mode";
			case WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED:
				warn(WifiHotspotProvider.class,
						"Tethering disallowed by this device (code " + reason + ")");
				return "hotspot_tethering_disallowed";
			default:
				warn(WifiHotspotProvider.class, "Hotspot failed (code " + reason + ")");
				return "hotspot_error";
		}
	}
}
