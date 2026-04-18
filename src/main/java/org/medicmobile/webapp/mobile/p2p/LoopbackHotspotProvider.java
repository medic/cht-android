package org.medicmobile.webapp.mobile.p2p;

import android.util.Log;

/**
	* Dev/emulator HotspotProvider that uses localhost instead of real WiFi.
	*
	* Useful for testing on emulators that don't support LocalOnlyHotspot.
	* Immediately reports success with loopback credentials — no actual
	* WiFi hotspot is created.
	*/
public class LoopbackHotspotProvider implements HotspotProvider {

	private static final String TAG = "LoopbackHotspot";
	private static final String LOOPBACK_SSID = "CHT-P2P-loopback";
	private static final String LOOPBACK_PASSWORD = "dev-password";
	private static final String LOOPBACK_IP = "127.0.0.1";

	private volatile boolean running = false;

	@Override
	public void start(HotspotCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}

		if (running) {
			Log.w(TAG, "Loopback hotspot already running");
			callback.onStarted(LOOPBACK_SSID, LOOPBACK_PASSWORD, LOOPBACK_IP);
			return;
		}

		running = true;
		Log.i(TAG, "Loopback hotspot started (dev mode)");
		callback.onStarted(LOOPBACK_SSID, LOOPBACK_PASSWORD, LOOPBACK_IP);
	}

	@Override
	public void stop() {
		if (running) {
			running = false;
			Log.i(TAG, "Loopback hotspot stopped");
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}
}
