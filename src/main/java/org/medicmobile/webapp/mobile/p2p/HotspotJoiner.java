package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;

/**
	* Joins the host's hotspot on the peer device.
	*
	* Android changed how an app may do this, so there are two paths and both are needed.
	*
	* From Android 10 an app asks the system for a network matching a specifier, the user confirms
	* it, and the app receives a {@link Network} it must send traffic over explicitly: joining does
	* not become the device's default connection. Android 10 also broke the older way, where an app
	* saved the network and switched to it, so neither path works on both sides of that line.
	*
	* Callers get a {@link Network} either way and must bind their sockets to it. On the older path
	* the hotspot does become the default connection, but binding is still correct and keeps the
	* calling code identical across versions.
	*/
public class HotspotJoiner {

	private static final String JOIN_FAILED = "join_failed";

	/** Every supported version can join, by one route or the other. */
	public static final boolean SUPPORTED = true;

	/** Long enough for the user to accept the system prompt, short enough to fail visibly. */
	private static final int JOIN_TIMEOUT_MS = 60_000;

	private final ConnectivityManager connectivityManager;
	private final WifiManager wifiManager;
	private ConnectivityManager.NetworkCallback activeCallback;
	/** Set only on the pre-Android-10 path, where the network is saved to the device and must be
		* taken back off it afterwards. */
	private int savedNetworkId = -1;

	public HotspotJoiner(ConnectivityManager connectivityManager, WifiManager wifiManager) {
		if (connectivityManager == null || wifiManager == null) {
			throw new IllegalArgumentException("connectivityManager and wifiManager must not be null");
		}
		this.connectivityManager = connectivityManager;
		this.wifiManager = wifiManager;
	}

	public static HotspotJoiner create(Context context) {
		Context appContext = context.getApplicationContext();
		return new HotspotJoiner(
				(ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE),
				(WifiManager) appContext.getSystemService(Context.WIFI_SERVICE));
	}


	/**
		* Asks the system to join the named network. The user sees a confirmation prompt.
		*
		* @param callback receives the network to send traffic over, or a failure reason
		*/
	public void join(String ssid, String password, JoinCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}
		if (ssid == null || ssid.trim().isEmpty() || password == null || password.isEmpty()) {
			callback.onFailed("invalid_credentials");
			return;
		}

		leave();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			requestNetwork(ssid, password, callback);
		} else {
			joinSavedNetwork(ssid, password, callback);
		}
	}

	/**
		* The route for Android 9 and below: save the network, switch to it, and watch for the
		* system reporting it connected.
		*
		* Android 10 made addNetwork fail for apps, which is why this is not used above it.
		*/
	@SuppressWarnings("deprecation")
	private void joinSavedNetwork(String ssid, String password, JoinCallback callback) {
		android.net.wifi.WifiConfiguration config = new android.net.wifi.WifiConfiguration();
		// the platform expects these quoted
		config.SSID = "\"" + ssid + "\"";
		config.preSharedKey = "\"" + password + "\"";

		int networkId = wifiManager.addNetwork(config);
		if (networkId == -1) {
			warn(HotspotJoiner.class, "The device would not save the host's network");
			callback.onFailed(JOIN_FAILED);
			return;
		}
		savedNetworkId = networkId;

		// watch first, so a fast connection is not missed between enabling and registering
		watchForWifi(callback);
		if (!wifiManager.enableNetwork(networkId, true)) {
			leave();
			warn(HotspotJoiner.class, "The device would not switch to the host's network");
			callback.onFailed(JOIN_FAILED);
		}
	}

	/** Reports the wifi network once the system says it is up. */
	private void watchForWifi(JoinCallback callback) {
		NetworkRequest request = new NetworkRequest.Builder()
				.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
				.build();

		activeCallback = new ConnectivityManager.NetworkCallback() {
			@Override public void onAvailable(Network network) {
				log(HotspotJoiner.class, "Joined the host's network");
				callback.onJoined(network);
			}

			@Override public void onLost(Network network) {
				warn(HotspotJoiner.class, "Lost the host's network");
				callback.onLost();
			}
		};
		connectivityManager.registerNetworkCallback(request, activeCallback);
	}

	@android.annotation.TargetApi(29)
	private void requestNetwork(String ssid, String password, JoinCallback callback) {
		NetworkRequest request = new NetworkRequest.Builder()
				.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
				// the hotspot has no upstream, so the system must not discard it for lacking one
				.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
				.setNetworkSpecifier(new WifiNetworkSpecifier.Builder()
						.setSsid(ssid)
						.setWpa2Passphrase(password)
						.build())
				.build();

		activeCallback = new ConnectivityManager.NetworkCallback() {
			@Override public void onAvailable(Network network) {
				log(HotspotJoiner.class, "Joined the host's network");
				callback.onJoined(network);
			}

			@Override public void onUnavailable() {
				warn(HotspotJoiner.class, "Could not join the host's network");
				callback.onFailed(JOIN_FAILED);
			}

			@Override public void onLost(Network network) {
				warn(HotspotJoiner.class, "Lost the host's network");
				callback.onLost();
			}
		};
		connectivityManager.requestNetwork(request, activeCallback, JOIN_TIMEOUT_MS);
	}

	/** Releases the network, letting the device return to its usual connection. */
	public void leave() {
		if (activeCallback != null) {
			try {
				connectivityManager.unregisterNetworkCallback(activeCallback);
			} catch (IllegalArgumentException e) {
				// already unregistered; nothing to undo
				warn(e, "Network callback was already unregistered");
			}
			activeCallback = null;
		}
		forgetSavedNetwork();
		log(HotspotJoiner.class, "Left the host's network");
	}

	/**
		* Takes the host's network back off the device.
		*
		* Only the pre-Android-10 path saves it. Leaving it behind would strand the user on a
		* hotspot with no internet, and the device would keep rejoining it whenever it is in range.
		*/
	@SuppressWarnings("deprecation")
	private void forgetSavedNetwork() {
		if (savedNetworkId == -1) {
			return;
		}
		wifiManager.removeNetwork(savedNetworkId);
		// back to whichever network the device would normally use
		wifiManager.reconnect();
		savedNetworkId = -1;
	}

	public interface JoinCallback {
		/** @param network every socket to the host must be bound to this, or it will not arrive */
		void onJoined(Network network);

		void onFailed(String reason);

		/** The host went away mid-session. */
		void onLost();
	}
}
