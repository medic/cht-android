package org.medicmobile.webapp.mobile.p2p;

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
public class P2pPeer {

	private final HotspotJoiner joiner;

	public P2pPeer(HotspotJoiner joiner) {
		if (joiner == null) {
			throw new IllegalArgumentException("joiner must not be null");
		}
		this.joiner = joiner;
	}

	public static P2pPeer create(Context context) {
		return new P2pPeer(HotspotJoiner.create(context));
	}

	/** Whether this device can join a session. Every supported Android version can. */
	public static boolean isJoinSupported() {
		return HotspotJoiner.SUPPORTED;
	}

	/**
		* Joins the session described by a scanned payload.
		*
		* @param payloadJson the QR contents, already validated by the scanner
		* @param callback    reports the host's label once its certificate has been verified
		*/
	public void pair(String payloadJson, PairCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback must not be null");
		}

		QrValidation validation = QrCodeHelper.validateQrPayload(payloadJson);
		if (!validation.isAccepted()) {
			warn(P2pPeer.class, "Rejected a scanned code: " + validation.getDetail());
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
				verifyHost(network, payload, callback);
			}

			@Override public void onFailed(String reason) {
				callback.onFailed(reason);
			}

			@Override public void onLost() {
				callback.onFailed("network_lost");
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
			log(P2pPeer.class, "Paired with " + label);
			callback.onPaired(label);
		} catch (IOException e) {
			warn(e, "Could not verify the host, abandoning the session");
			unpair();
			callback.onFailed("host_not_verified");
		}
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
