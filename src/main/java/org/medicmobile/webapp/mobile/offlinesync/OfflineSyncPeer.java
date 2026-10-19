package org.medicmobile.webapp.mobile.offlinesync;

import static org.medicmobile.webapp.mobile.MedicLog.log;
import static org.medicmobile.webapp.mobile.MedicLog.warn;

import android.content.Context;
import android.net.Network;

import java.io.IOException;

/**
	* The joining side of a pairing session.
	*
	* Takes the payload from a scanned QR code, joins the network it names, and confirms the device
	* answering there is the one the code came from. Pairing is only complete once that check passes:
	* reaching something at the address proves nothing on a network anyone can join.
	*/
public class OfflineSyncPeer {

	private static OfflineSyncPeer instance;

	private final HotspotJoiner joiner;
	/** Who hears about the pairing. Replaced when the screen that started it is recreated. */
	private volatile PairCallback callback;

	public OfflineSyncPeer(HotspotJoiner joiner) {
		if (joiner == null) {
			throw new IllegalArgumentException("joiner must not be null");
		}
		this.joiner = joiner;
	}

	private static OfflineSyncPeer create(Context context) {
		return new OfflineSyncPeer(HotspotJoiner.create(context));
	}

	/**
		* The peer for this process. The joined network belongs to the process, and the screen is
		* recreated on a theme or language change, so a peer per screen could no longer leave it.
		*/
	public static synchronized OfflineSyncPeer getInstance(Context context) {
		if (instance == null) {
			instance = create(context.getApplicationContext());
		}
		return instance;
	}

	/** Whether this device can join a session. Every supported Android version can. */
	public static boolean isJoinSupported() {
		return HotspotJoiner.SUPPORTED;
	}

	/**
		* Joins the session described by a scanned payload.
		*
		* @param payloadJson the QR contents as scanned, validated here before anything is joined
		* @param callback    reports the host's label once its key has been verified
		*/
	public void pair(String payloadJson, PairCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}
		this.callback = callback;

		QrValidation validation = QrCodeHelper.validateQrPayload(payloadJson);
		if (!validation.isAccepted()) {
			warn(OfflineSyncPeer.class, "Rejected a scanned code: " + validation.getDetail());
			callback.onFailed(validation.getCode());
			return;
		}

		QrCodeHelper.QrPayload payload = QrCodeHelper.parsePayload(payloadJson);
		if (payload == null) {
			callback.onFailed("unreadable_payload");
			return;
		}

		joiner.join(payload.getSsid(), payload.getPassword(), new HotspotJoiner.JoinCallback() {
			@Override public void onJoined(Network network) {
				verifyHost(network, payload, OfflineSyncPeer.this.callback);
			}

			@Override public void onFailed(String reason) {
				OfflineSyncPeer.this.callback.onFailed(reason);
			}

			@Override public void onLost() {
				OfflineSyncPeer.this.callback.onFailed("network_lost");
			}
		});
	}

	/**
		* Confirms the host is the device the QR code came from, then reports success.
		*
		* A failure here is not a networking hiccup to retry quietly: it means something answered
		* that could not prove it was the host, so the session is abandoned.
		*/
	private void verifyHost(Network network, QrCodeHelper.QrPayload payload, PairCallback callback) {
		try {
			String label = new PeerClient(network, payload.getCertFingerprint())
					.fetchStatus(payload.getIpAddress(), payload.getPort());
			log(OfflineSyncPeer.class, "Paired with " + label);
			callback.onPaired(label);
		} catch (IOException e) {
			warn(e, "Could not verify the host, abandoning the session");
			unpair();
			callback.onFailed("host_not_verified");
		}
	}

	/** Sends what happens to the pairing from now on to a new callback, e.g. a recreated screen. */
	public void redirect(PairCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}
		this.callback = callback;
	}

	/** Leaves the session's network. */
	public void unpair() {
		joiner.leave();
	}

	public interface PairCallback {
		/** @param hostLabel what the host calls itself, so the user can confirm the right device */
		void onPaired(String hostLabel);

		void onFailed(String reason);
	}
}
