package org.medicmobile.webapp.mobile.p2p;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;

/**
	* Joins the host's hotspot on the peer device.
	*
	* Android changed how an app may do this. From Android 10 an app asks the system for a network
	* matching a specifier, the user confirms it, and the app receives a {@link Network} it must
	* send its traffic over explicitly: joining does not become the device's default connection.
	* Before Android 10 an app added the network to the device's saved list and switched to it.
	*
	* The returned {@link Network} is what callers must bind their sockets to, otherwise traffic
	* leaves over mobile data and never reaches the host.
	*/
public class HotspotJoiner {

	/** Long enough for the user to accept the system prompt, short enough to fail visibly. */
	private static final int JOIN_TIMEOUT_MS = 60_000;

	private final ConnectivityManager connectivityManager;
	private ConnectivityManager.NetworkCallback activeCallback;

	public HotspotJoiner(ConnectivityManager connectivityManager) {
		if (connectivityManager == null) {
			throw new IllegalArgumentException("connectivityManager must not be null");
		}
		this.connectivityManager = connectivityManager;
	}

	public static HotspotJoiner create(Context context) {
		return new HotspotJoiner(
				(ConnectivityManager) context.getApplicationContext()
						.getSystemService(Context.CONNECTIVITY_SERVICE));
	}

	/**
		* Whether this device can join through the supported path.
		*
		* The pre-Android-10 way of adding a network was restricted in Android 10 and returns a
		* failure for apps that did not create the network, so it is not a usable fallback. Rather
		* than pretend otherwise, joining is reported as unavailable below Android 10.
		*/
	public static boolean isSupported() {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
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
		if (!isSupported()) {
			warn(HotspotJoiner.class,
					"Joining a hotspot needs Android 10, this device is on API " + Build.VERSION.SDK_INT);
			callback.onFailed("join_unsupported");
			return;
		}
		if (ssid == null || ssid.trim().isEmpty() || password == null || password.isEmpty()) {
			callback.onFailed("invalid_credentials");
			return;
		}

		leave();
		requestNetwork(ssid, password, callback);
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
				callback.onFailed("join_failed");
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
		if (activeCallback == null) {
			return;
		}
		try {
			connectivityManager.unregisterNetworkCallback(activeCallback);
			log(HotspotJoiner.class, "Left the host's network");
		} catch (IllegalArgumentException e) {
			// already unregistered; nothing to undo
			warn(e, "Network callback was already unregistered");
		}
		activeCallback = null;
	}

	public interface JoinCallback {
		/** @param network every socket to the host must be bound to this, or it will not arrive */
		void onJoined(Network network);

		void onFailed(String reason);

		/** The host went away mid-session. */
		void onLost();
	}
}
